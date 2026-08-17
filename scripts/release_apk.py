#!/usr/bin/env python3
"""Publica un APK de producción a la flota de cobradores.

Reemplaza un procedimiento manual que vivía en la cabeza de una persona:
compilar, subir a GitHub Releases, calcular hash y tamaño a mano, y editar
Firestore. Cada uno de esos pasos tiene una forma silenciosa de salir mal, y
todas cuestan lo mismo: teléfonos de cobradores que no se pueden actualizar,
o —peor— que se quedan sin API.

Las cinco trampas que este script existe para cerrar:

1. **Firma degradada en silencio.** `app/build.gradle.kts` cae a la config de
   firma de *debug* cuando `keystore.properties` no existe, sin avisar. El APK
   compila, se sube, y la flota descubre el problema una semana después con
   `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Aquí se aborta antes de compilar, y
   además se verifica la firma del APK ya construido con `apksigner`.

2. **`baseURL` es el kill-switch de la flota.** Vive en el MISMO documento de
   Firestore (`config/api_settings`). Una escritura que lo pise deja a todos
   los teléfonos sin API. Este script **nunca** lo incluye en el payload; hay
   una guarda que lanza si alguna vez apareciera.

3. **El sufijo del `versionName`.** `Constants.APP_VERSION` recorta en el
   primer `-`, así que lo que se publique en `LATEST_VERSION` tiene que ser la
   base sin sufijo o el update-check entra en bucle.

4. **`versionCode` y `versionName` van juntos.** Los dos mecanismos de
   actualización comparan cosas distintas (ver `docs/RELEASE.md`); subir uno
   solo deja el otro roto.

5. **Colisión de versión.** No se republica un tag que ya existe, ni se baja el
   `versionCode`.

Simulacro por defecto: sin `--apply` no escribe nada — ni Firestore, ni el
release, ni `app/build.gradle.kts`.

Uso típico:

    # ver qué pasaría (no escribe nada)
    python3 scripts/release_apk.py --dev

    # publicar de verdad a dev
    python3 scripts/release_apk.py --dev --apply

    # producción, con confirmación explícita
    python3 scripts/release_apk.py --prod --apply --confirm-prod 2.17.1

Ver `docs/RELEASE.md` para el procedimiento completo y para cuándo se usan los
`MIN_VERSION_*` (respuesta corta: casi nunca).
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable, Sequence

# ── Constantes del contrato con Firestore ────────────────────────────────────

COLLECTION_CONFIG = "config"
DOCUMENT_API_SETTINGS = "api_settings"

FIELD_LATEST_VERSION = "LATEST_VERSION"
FIELD_APK_URL = "APK_URL"

# Los siete campos del bloqueo duro. Nombres tomados literalmente de
# `core/appgate/.../MinVersionConfig.kt` (`object MinVersionFields`).
FIELD_MIN_VERSION_CODE = "MIN_VERSION_CODE"
FIELD_MIN_VERSION_NAME = "MIN_VERSION_NAME"
FIELD_MIN_VERSION_DEADLINE = "MIN_VERSION_DEADLINE"
FIELD_MIN_VERSION_EXEMPT_DEVICES = "MIN_VERSION_EXEMPT_DEVICES"
FIELD_MIN_VERSION_APK_URL = "MIN_VERSION_APK_URL"
FIELD_MIN_VERSION_APK_SIZE = "MIN_VERSION_APK_SIZE"
FIELD_MIN_VERSION_APK_SHA256 = "MIN_VERSION_APK_SHA256"

MIN_VERSION_FIELDS = (
    FIELD_MIN_VERSION_CODE,
    FIELD_MIN_VERSION_NAME,
    FIELD_MIN_VERSION_DEADLINE,
    FIELD_MIN_VERSION_EXEMPT_DEVICES,
    FIELD_MIN_VERSION_APK_URL,
    FIELD_MIN_VERSION_APK_SIZE,
    FIELD_MIN_VERSION_APK_SHA256,
)

# Campos que este script NO escribe jamás, ni con el mismo valor. `baseURL` es
# el kill-switch de la flota entera: si se pisa o se borra, todos los teléfonos
# se quedan sin API. Se compara en minúsculas para atrapar también `baseurl` o
# `BASEURL` si alguien los tipea distinto algún día.
FORBIDDEN_FIELDS = frozenset({"baseurl"})

# ── Entornos ─────────────────────────────────────────────────────────────────

# El `project_id` esperado es el candado: la llave se lee, se compara, y si no
# coincide con el entorno pedido se aborta. Es el mismo candado que ya evitó un
# accidente en msp-api.
ENVIRONMENTS: dict[str, "Environment"] = {}


@dataclass(frozen=True)
class Environment:
    name: str
    project_id: str
    credentials_filename: str
    requires_confirmation: bool


ENVIRONMENTS["dev"] = Environment(
    name="dev",
    project_id="msp-dev-96ff5",
    credentials_filename="serviceAccountKey.json",
    requires_confirmation=False,
)
ENVIRONMENTS["prod"] = Environment(
    name="prod",
    project_id="msp-db-1c2ce",
    credentials_filename="serviceAccountKeyProduction.json",
    requires_confirmation=True,
)

DEFAULT_CREDENTIALS_DIR = "/Volumes/M2-1TB/Developer/msp-api"

# Huella de la llave de *debug* de Android. Cualquier APK firmado con ella es
# un APK que la flota no puede instalar encima del que ya tiene. Se comprueba
# además del match positivo contra la llave de release, porque un
# `keystore.properties` mal apuntado haría que el match positivo pasara
# vacíamente.
ANDROID_DEBUG_DN_MARKER = "cn=android debug"

GRADLE_TASK = ":app:assembleProdRelease"
APK_RELATIVE_PATH = "app/build/outputs/apk/prod/release/app-prod-release.apk"


class ReleaseError(Exception):
    """Aborto controlado. El script imprime el mensaje y sale con código 1."""


# ── Funciones puras: aquí viven las propiedades de seguridad ─────────────────


def base_version_name(version_name: str) -> str:
    """La versión sin sufijo de sabor/compilación.

    `Constants.APP_VERSION` hace `BuildConfig.VERSION_NAME.substringBefore("-")`
    a propósito, para que un APK `devlocal` (`2.17.0-local+5d28e4de`) no vea
    "2.17.0" como novedad y entre en bucle de actualización. Lo que se publique
    en `LATEST_VERSION` tiene que ser esa misma base, o el bucle vuelve por el
    otro lado. Se recorta también en `+` porque `UpdateChecker.parseVersion`
    lo hace.
    """
    return version_name.strip().split("-", 1)[0].split("+", 1)[0].strip()


def parse_semver(version: str) -> tuple[int, int, int]:
    """`"2.17.1"` → `(2, 17, 1)`. Lanza si no se puede leer."""
    base = base_version_name(version)
    parts = base.split(".")
    if len(parts) != 3:
        raise ReleaseError(f"versión ilegible (se esperaba mayor.menor.parche): {version!r}")
    try:
        numbers = tuple(int(p) for p in parts)
    except ValueError as exc:
        raise ReleaseError(f"versión ilegible (componente no numérico): {version!r}") from exc
    if any(n < 0 for n in numbers):
        raise ReleaseError(f"versión ilegible (componente negativo): {version!r}")
    return numbers  # type: ignore[return-value]


def tag_for(version_name: str) -> str:
    return "v" + base_version_name(version_name)


VERSION_CODE_RE = re.compile(r"^(?P<pre>\s*versionCode\s*=\s*)(?P<value>\d+)\s*$", re.MULTILINE)
VERSION_NAME_RE = re.compile(r'^(?P<pre>\s*versionName\s*=\s*")(?P<value>[^"]+)"\s*$', re.MULTILINE)


def read_gradle_version(gradle_text: str) -> tuple[int, str]:
    """Extrae `(versionCode, versionName)` de `app/build.gradle.kts`."""
    code_match = VERSION_CODE_RE.search(gradle_text)
    name_match = VERSION_NAME_RE.search(gradle_text)
    if code_match is None or name_match is None:
        raise ReleaseError(
            "no se encontraron versionCode/versionName en app/build.gradle.kts; "
            "¿cambió el formato del archivo?"
        )
    return int(code_match.group("value")), name_match.group("value")


def bump_gradle_version(gradle_text: str, version_code: int, version_name: str) -> str:
    """Devuelve el texto con la versión nueva. No toca disco.

    Exige exactamente una ocurrencia de cada declaración: si aparecieran dos
    (un flavor que la sobreescribe, por ejemplo) el reemplazo ciego dejaría el
    archivo incoherente, que es justo el estado que este script existe para
    evitar.
    """
    if version_code <= 0:
        raise ReleaseError(f"versionCode inválido: {version_code}")
    parse_semver(version_name)

    if len(VERSION_CODE_RE.findall(gradle_text)) != 1:
        raise ReleaseError("se esperaba exactamente un `versionCode =` en app/build.gradle.kts")
    if len(VERSION_NAME_RE.findall(gradle_text)) != 1:
        raise ReleaseError("se esperaba exactamente un `versionName =` en app/build.gradle.kts")

    out = VERSION_CODE_RE.sub(lambda m: f"{m.group('pre')}{version_code}", gradle_text, count=1)
    out = VERSION_NAME_RE.sub(lambda m: f'{m.group("pre")}{version_name}"', out, count=1)
    return out


@dataclass(frozen=True)
class ApkFacts:
    """Hash y tamaño de UN archivo concreto, junto con su ruta.

    La ruta viaja pegada al hash a propósito: el error clásico es calcular el
    hash de un APK y subir otro (el de la compilación anterior, el de otro
    flavor). Quien sube el archivo y quien arma el payload leen el mismo
    `ApkFacts`, así que no pueden divergir.
    """

    path: Path
    sha256: str
    size_bytes: int


def apk_facts(path: Path) -> ApkFacts:
    digest = hashlib.sha256()
    size = 0
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
            size += len(chunk)
    if size == 0:
        raise ReleaseError(f"el APK está vacío: {path}")
    return ApkFacts(path=path, sha256=digest.hexdigest(), size_bytes=size)


@dataclass(frozen=True)
class ForceUpdate:
    """Los datos del bloqueo duro. Solo se construye con `--force-update`."""

    version_code: int
    version_name: str
    deadline_label: str
    exempt_device_ids: tuple[str, ...]


def assert_no_forbidden_fields(payload: dict[str, Any]) -> None:
    """Guarda de último momento antes de cualquier escritura.

    Vive separada de `build_firestore_payload` para que también cubra a quien
    algún día construya un payload por otro camino. Si `baseURL` llegara aquí,
    el script se cae en vez de tocar el kill-switch de la flota.
    """
    for key in payload:
        if key.strip().lower() in FORBIDDEN_FIELDS:
            raise ReleaseError(
                f"el payload incluye {key!r}: ese campo es el kill-switch de la flota "
                "y este script no lo escribe nunca, ni con el mismo valor"
            )


def build_firestore_payload(
    latest_version: str,
    apk_url: str,
    force: ForceUpdate | None = None,
    apk: ApkFacts | None = None,
) -> dict[str, Any]:
    """El documento parcial que se escribe con `merge`.

    Sin `force` son exactamente dos campos: `LATEST_VERSION` y `APK_URL`. Con
    `force` se suman los siete `MIN_VERSION_*`, todos o ninguno — la lectura
    del cliente (`FirestoreMinVersionConfigSource.readUpdatePackage`) descarta
    el paquete completo si falta la URL, el sha256 o el tamaño, así que medio
    bloqueo es un bloqueo sin APK que descargar.
    """
    if not apk_url:
        raise ReleaseError("APK_URL vacío")
    published = base_version_name(latest_version)
    if not published:
        raise ReleaseError(f"LATEST_VERSION vacío tras recortar el sufijo: {latest_version!r}")

    payload: dict[str, Any] = {
        FIELD_LATEST_VERSION: published,
        FIELD_APK_URL: apk_url,
    }

    if force is not None:
        if apk is None:
            raise ReleaseError("el bloqueo duro necesita el sha256 y el tamaño del APK")
        if not force.deadline_label.strip():
            raise ReleaseError(
                "el bloqueo duro necesita --deadline (el texto que ve el cobrador, ej. 'vie 22')"
            )
        payload[FIELD_MIN_VERSION_CODE] = force.version_code
        payload[FIELD_MIN_VERSION_NAME] = base_version_name(force.version_name)
        payload[FIELD_MIN_VERSION_DEADLINE] = force.deadline_label.strip()
        payload[FIELD_MIN_VERSION_EXEMPT_DEVICES] = list(force.exempt_device_ids)
        payload[FIELD_MIN_VERSION_APK_URL] = apk_url
        payload[FIELD_MIN_VERSION_APK_SIZE] = apk.size_bytes
        payload[FIELD_MIN_VERSION_APK_SHA256] = apk.sha256

    assert_no_forbidden_fields(payload)
    return payload


@dataclass(frozen=True)
class SignerCert:
    dn: str
    sha256: str


APKSIGNER_DN_RE = re.compile(r"^Signer #(\d+) certificate DN:\s*(.+)$", re.MULTILINE)
APKSIGNER_SHA_RE = re.compile(
    r"^Signer #(\d+) certificate SHA-256 digest:\s*([0-9a-fA-F]{64})\s*$", re.MULTILINE
)


def parse_apksigner_certs(output: str) -> list[SignerCert]:
    """Lee la salida de `apksigner verify --print-certs`."""
    dns = {int(n): dn.strip() for n, dn in APKSIGNER_DN_RE.findall(output)}
    shas = {int(n): sha.strip().lower() for n, sha in APKSIGNER_SHA_RE.findall(output)}
    if not shas:
        raise ReleaseError(
            "apksigner no reportó ninguna huella SHA-256; el APK podría no estar firmado"
        )
    return [SignerCert(dn=dns.get(n, ""), sha256=shas[n]) for n in sorted(shas)]


KEYTOOL_SHA_RE = re.compile(r"^\s*SHA256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})\s*$", re.MULTILINE)


def parse_keytool_fingerprint(output: str) -> str:
    """Lee la huella SHA-256 de `keytool -list -v` y la normaliza a hex plano.

    `keytool` la imprime con dos puntos y en mayúsculas; `apksigner` la imprime
    plana y en minúsculas. Son el mismo número (SHA-256 del certificado DER).
    """
    match = KEYTOOL_SHA_RE.search(output)
    if match is None:
        raise ReleaseError("no se pudo leer la huella SHA-256 del keystore de release")
    return match.group(1).replace(":", "").lower()


def verify_signature(certs: Sequence[SignerCert], expected_sha256: str) -> None:
    """Aborta si el APK no está firmado con la llave de release.

    Dos comprobaciones, no una:

    - la huella tiene que coincidir con la del keystore de release, y
    - ninguna firma puede ser la de debug de Android.

    La segunda no es redundante: si `keystore.properties` apuntara por error al
    debug keystore, la primera pasaría vacíamente porque ambas huellas saldrían
    de la misma llave equivocada.
    """
    if not certs:
        raise ReleaseError("el APK no trae ninguna firma")
    expected = expected_sha256.strip().lower()
    actual = {cert.sha256 for cert in certs}

    for cert in certs:
        if ANDROID_DEBUG_DN_MARKER in cert.dn.lower():
            raise ReleaseError(
                f"el APK está firmado con la llave de DEBUG de Android ({cert.dn}). "
                "La flota no puede instalarlo encima del APK que ya tiene "
                "(INSTALL_FAILED_UPDATE_INCOMPATIBLE). Revise keystore.properties."
            )

    if expected not in actual:
        raise ReleaseError(
            "la firma del APK no corresponde al keystore de release.\n"
            f"  esperada: {expected}\n"
            f"  encontrada: {', '.join(sorted(actual))}\n"
            "Un APK con otra firma NO se puede instalar sobre el que trae la flota."
        )


def newest_build_tools(names: Iterable[str]) -> str | None:
    """El directorio de build-tools con el número más alto."""

    def key(name: str) -> tuple[int, ...]:
        return tuple(int(p) if p.isdigit() else -1 for p in name.split("."))

    candidates = [n for n in names if re.fullmatch(r"\d+(\.\d+)*", n)]
    return max(candidates, key=key) if candidates else None


def pick_newest_tag(tags: Iterable[str]) -> str | None:
    """El tag `vX.Y.Z` más alto por semver. Ignora lo que no parezca versión."""
    parsed: list[tuple[tuple[int, int, int], str]] = []
    for tag in tags:
        try:
            parsed.append((parse_semver(tag.lstrip("vV")), tag))
        except ReleaseError:
            continue
    return max(parsed)[1] if parsed else None


def check_no_collision(
    version_name: str,
    version_code: int,
    existing_tags: Iterable[str],
    previous_version_code: int | None,
) -> None:
    """Aborta si la versión que se va a publicar ya existe o va para atrás."""
    tag = tag_for(version_name)
    existing = set(existing_tags)
    if tag in existing:
        raise ReleaseError(
            f"el tag {tag} ya existe (release o tag local). "
            "Publicar encima reemplazaría un APK que ya está en teléfonos. "
            "Elija la siguiente versión."
        )
    newest = pick_newest_tag(existing)
    if newest is not None and parse_semver(newest.lstrip("vV")) >= parse_semver(version_name):
        raise ReleaseError(
            f"{tag} no es posterior a {newest}, que ya está publicado. "
            "El versionName tiene que subir."
        )
    if previous_version_code is not None and version_code <= previous_version_code:
        raise ReleaseError(
            f"versionCode {version_code} no es mayor que {previous_version_code}, "
            f"el de {newest}. El bloqueo por versión compara versionCode como entero: "
            "si no sube, la compuerta no distingue el APK nuevo del viejo."
        )


def release_asset_name(version_name: str) -> str:
    return f"app-prod-release-{base_version_name(version_name)}.apk"


def download_url(repo: str, version_name: str, asset_name: str) -> str:
    return f"https://github.com/{repo}/releases/download/{tag_for(version_name)}/{asset_name}"


# ── Puertos (se falsean en las pruebas) ──────────────────────────────────────


@dataclass
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


class CommandRunner:
    """Ejecuta procesos. En pruebas se sustituye por un doble que graba."""

    def __init__(self, cwd: Path, env: dict[str, str] | None = None) -> None:
        self.cwd = cwd
        self.env = env

    def run(self, argv: Sequence[str], check: bool = True) -> CommandResult:
        completed = subprocess.run(
            list(argv),
            cwd=str(self.cwd),
            env=self.env,
            capture_output=True,
            text=True,
        )
        result = CommandResult(completed.returncode, completed.stdout, completed.stderr)
        if check and result.returncode != 0:
            raise ReleaseError(
                f"falló `{' '.join(argv)}` (código {result.returncode})\n"
                f"{result.stdout}\n{result.stderr}".strip()
            )
        return result


class FirestoreGateway:
    """Lectura/escritura de `config/api_settings` con una llave de servicio.

    Se construye tarde y con importación perezosa: las pruebas no deben
    necesitar `google-cloud-firestore` ni red.
    """

    def __init__(self, credentials_path: Path, project_id: str) -> None:
        self.credentials_path = credentials_path
        self.project_id = project_id
        self._document = None

    def _doc(self):  # pragma: no cover - requiere red
        if self._document is None:
            from google.cloud import firestore  # noqa: PLC0415
            from google.oauth2 import service_account  # noqa: PLC0415

            credentials = service_account.Credentials.from_service_account_file(
                str(self.credentials_path)
            )
            client = firestore.Client(project=self.project_id, credentials=credentials)
            self._document = client.collection(COLLECTION_CONFIG).document(DOCUMENT_API_SETTINGS)
        return self._document

    def read(self) -> dict[str, Any]:  # pragma: no cover - requiere red
        snapshot = self._doc().get()
        return snapshot.to_dict() or {} if snapshot.exists else {}

    def write(self, payload: dict[str, Any]) -> None:  # pragma: no cover - requiere red
        assert_no_forbidden_fields(payload)
        self._doc().set(payload, merge=True)


# ── Orquestación ─────────────────────────────────────────────────────────────


@dataclass
class Options:
    environment: Environment
    repo_root: Path
    credentials_path: Path
    apply: bool = False
    force: ForceUpdate | None = None
    confirm_prod: str | None = None
    expect_version_name: str | None = None
    expect_version_code: int | None = None
    apk_override: Path | None = None
    skip_build: bool = False
    notes: str | None = None
    prerelease: bool = False
    allow_dirty: bool = False
    allow_unpushed: bool = False
    allow_downgrade: bool = False
    apksigner: str | None = None
    keytool: str = "keytool"
    verbose: bool = True


def _silent(_: str) -> None:
    """Sumidero de salida. Las pruebas no necesitan ver el reporte."""


@dataclass
class Report:
    """Lo que pasó, para imprimirlo al final y para que las pruebas lo miren."""

    echo: Callable[[str], None] = print
    lines: list[str] = field(default_factory=list)
    payload: dict[str, Any] | None = None
    apk: ApkFacts | None = None
    uploaded_path: Path | None = None
    apk_url: str | None = None
    firestore_after: dict[str, Any] | None = None

    def say(self, text: str) -> None:
        self.lines.append(text)
        self.echo(text)


def load_credentials_project_id(path: Path) -> str:
    if not path.exists():
        raise ReleaseError(f"no existe la llave de servicio: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ReleaseError(f"la llave de servicio no es JSON válido: {path}") from exc
    project_id = data.get("project_id")
    if not project_id:
        raise ReleaseError(f"la llave de servicio no trae project_id: {path}")
    return str(project_id)


def check_credentials_match_environment(path: Path, environment: Environment) -> None:
    """El candado que ya evitó un accidente: la llave decide, no el nombre.

    Un `serviceAccountKey.json` copiado encima del de dev, o un `--credentials`
    escrito de prisa, apuntarían al proyecto equivocado sin que nada se queje.
    Aquí se lee el `project_id` de la llave misma y se compara.
    """
    actual = load_credentials_project_id(path)
    if actual != environment.project_id:
        raise ReleaseError(
            f"la llave {path} es del proyecto {actual!r}, pero se pidió el entorno "
            f"{environment.name!r} ({environment.project_id!r}). Abortado antes de escribir."
        )


def check_keystore(repo_root: Path) -> Path:
    """`keystore.properties` tiene que existir. No se degrada a debug."""
    properties = repo_root / "keystore.properties"
    if not properties.exists():
        raise ReleaseError(
            "falta keystore.properties en la raíz del repo.\n"
            "app/build.gradle.kts cae SIN AVISAR a la firma de debug cuando no está, y el APK "
            "resultante no se puede instalar sobre el que trae la flota "
            "(INSTALL_FAILED_UPDATE_INCOMPATIBLE).\n"
            "El keystore vive en una sola laptop: consígalo antes de publicar."
        )
    return properties


def read_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def resolve_store_file(repo_root: Path, store_file: str) -> Path:
    """Resuelve `storeFile` como lo hace Gradle: relativo al módulo `app/`.

    El `file(...)` del bloque `signingConfigs` corre en el proyecto `:app`, así
    que una ruta relativa cuelga de `app/`. Se acepta también la raíz del repo
    porque hoy el archivo existe en las dos.
    """
    candidate = Path(store_file)
    if candidate.is_absolute():
        if not candidate.exists():
            raise ReleaseError(f"no existe el keystore de release: {candidate}")
        return candidate
    for base in (repo_root / "app", repo_root):
        resolved = base / candidate
        if resolved.exists():
            return resolved
    raise ReleaseError(
        f"no se encontró el keystore de release {store_file!r} ni en app/ ni en la raíz"
    )


def expected_release_fingerprint(
    runner: CommandRunner, repo_root: Path, keytool: str
) -> str:
    """Huella de la llave de release, leída del keystore mismo.

    No se codifica una huella en el script: si algún día se rota el keystore,
    un valor fijo convertiría una verificación en un estorbo y alguien la
    borraría. Leerla del keystore que Gradle va a usar mantiene la comprobación
    honesta.
    """
    properties = read_properties(check_keystore(repo_root))
    missing = [k for k in ("storeFile", "storePassword", "keyAlias") if not properties.get(k)]
    if missing:
        raise ReleaseError(f"keystore.properties incompleto, faltan: {', '.join(missing)}")
    store = resolve_store_file(repo_root, properties["storeFile"])
    result = runner.run(
        [
            keytool,
            "-list",
            "-v",
            "-keystore",
            str(store),
            "-alias",
            properties["keyAlias"],
            "-storepass",
            properties["storePassword"],
        ]
    )
    return parse_keytool_fingerprint(result.stdout)


def find_apksigner(repo_root: Path) -> str:
    """Busca `apksigner` en el SDK. No suele estar en el PATH."""
    on_path = shutil.which("apksigner")
    if on_path:
        return on_path
    candidates: list[Path] = []
    for env_var in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        value = os.environ.get(env_var)
        if value:
            candidates.append(Path(value))
    local_properties = repo_root / "local.properties"
    if local_properties.exists():
        sdk_dir = read_properties(local_properties).get("sdk.dir")
        if sdk_dir:
            candidates.append(Path(sdk_dir))
    candidates.append(Path.home() / "Library/Android/sdk")
    for sdk in candidates:
        build_tools = sdk / "build-tools"
        if not build_tools.is_dir():
            continue
        newest = newest_build_tools(p.name for p in build_tools.iterdir() if p.is_dir())
        if newest and (build_tools / newest / "apksigner").exists():
            return str(build_tools / newest / "apksigner")
    raise ReleaseError(
        "no se encontró apksigner. Instale build-tools del SDK de Android o pase --apksigner."
    )


def check_working_tree(runner: CommandRunner, options: Options, report: Report) -> str:
    """Árbol limpio y commit conocido.

    "Conocido" = alcanzable desde alguna rama remota. Un release cuyo tag
    apunta a un commit que solo existe en esta laptop no se puede reproducir ni
    revisar; si además la laptop es la única que tiene el keystore, no queda
    ningún rastro de qué se compiló.
    """
    status = runner.run(["git", "status", "--porcelain"])
    if status.stdout.strip() and not options.allow_dirty:
        raise ReleaseError(
            "el árbol de trabajo no está limpio. Se compila y se etiqueta lo que está "
            "commiteado, así que un cambio suelto produciría un APK que no corresponde a "
            "ningún commit.\n" + status.stdout.strip()
        )
    head = runner.run(["git", "rev-parse", "HEAD"]).stdout.strip()
    remotes = runner.run(["git", "branch", "-r", "--contains", head], check=False)
    if remotes.returncode != 0 or not remotes.stdout.strip():
        if not options.allow_unpushed:
            raise ReleaseError(
                f"el commit {head[:8]} no está en ninguna rama remota. Súbalo antes de publicar "
                "(o use --allow-unpushed si sabe lo que hace)."
            )
        report.say(f"  aviso: {head[:8]} no está en ninguna rama remota")
    return head


def existing_release_tags(runner: CommandRunner) -> list[str]:
    """Tags de releases publicados en GitHub, más los tags locales.

    Los dos: hoy `v2.17.0` existe como pre-release en GitHub y el tag NO está
    en esta copia local. Mirar solo `git tag` diría que la versión está libre.
    """
    tags: set[str] = set()
    local = runner.run(["git", "tag", "--list"], check=False)
    if local.returncode == 0:
        tags.update(t.strip() for t in local.stdout.splitlines() if t.strip())
    remote = runner.run(
        ["gh", "release", "list", "--limit", "100", "--json", "tagName"], check=False
    )
    if remote.returncode != 0:
        raise ReleaseError(
            "no se pudo listar los releases de GitHub (`gh release list`). Sin esa lista no se "
            "puede descartar una colisión de versión.\n" + (remote.stderr or remote.stdout)
        )
    try:
        tags.update(item["tagName"] for item in json.loads(remote.stdout or "[]"))
    except (json.JSONDecodeError, KeyError, TypeError) as exc:
        raise ReleaseError("respuesta inesperada de `gh release list`") from exc
    return sorted(tags)


def version_code_at_tag(runner: CommandRunner, tag: str) -> int | None:
    """`versionCode` que tenía el repo en ese tag, o `None` si no está local."""
    result = runner.run(["git", "show", f"{tag}:app/build.gradle.kts"], check=False)
    if result.returncode != 0:
        return None
    try:
        return read_gradle_version(result.stdout)[0]
    except ReleaseError:
        return None


def gh_repo(runner: CommandRunner) -> str:
    result = runner.run(["gh", "repo", "view", "--json", "nameWithOwner"])
    try:
        return str(json.loads(result.stdout)["nameWithOwner"])
    except (json.JSONDecodeError, KeyError) as exc:
        raise ReleaseError("no se pudo determinar el repositorio de GitHub") from exc


def confirm_production(options: Options, version_name: str, stdin_reader: Callable[[], str]) -> None:
    """Producción exige teclear la versión. Un `--apply` no alcanza."""
    if not options.environment.requires_confirmation or not options.apply:
        return
    expected = base_version_name(version_name)
    if options.confirm_prod is not None:
        if base_version_name(options.confirm_prod) != expected:
            raise ReleaseError(
                f"--confirm-prod dice {options.confirm_prod!r} pero se iba a publicar {expected!r}"
            )
        return
    if options.verbose:
        print(
            f"\n  Va a publicar {expected} a PRODUCCIÓN ({options.environment.project_id}).\n"
            f"  Escriba la versión exacta para continuar: ",
            end="",
        )
    typed = stdin_reader().strip()
    if base_version_name(typed) != expected:
        raise ReleaseError("confirmación de producción no coincide; no se escribió nada")


def run_release(
    options: Options,
    runner: CommandRunner,
    firestore_factory: Callable[[], Any],
    stdin_reader: Callable[[], str] = lambda: sys.stdin.readline(),
) -> Report:
    """El procedimiento completo. Devuelve lo que hizo (o lo que haría)."""
    report = Report(echo=print if options.verbose else _silent)
    mode = "APLICANDO" if options.apply else "SIMULACRO (no escribe nada)"
    report.say(f"== release msp-app · entorno {options.environment.name} · {mode} ==")

    # 1. Árbol limpio y commit conocido.
    head = check_working_tree(runner, options, report)
    report.say(f"  commit: {head[:8]}")

    # 2. keystore.properties o nada. Antes de compilar: compilar sin él produce
    #    un APK inservible y tarda minutos en descubrirse.
    check_keystore(options.repo_root)
    report.say("  keystore.properties: presente")

    # 3. Versión y colisiones.
    gradle_path = options.repo_root / "app/build.gradle.kts"
    gradle_text = gradle_path.read_text(encoding="utf-8")
    version_code, version_name = read_gradle_version(gradle_text)
    if options.expect_version_name and base_version_name(
        options.expect_version_name
    ) != base_version_name(version_name):
        raise ReleaseError(
            f"--version-name dice {options.expect_version_name!r} pero app/build.gradle.kts "
            f"tiene {version_name!r}. Use --bump para cambiarlo y commitee el cambio."
        )
    if options.expect_version_code is not None and options.expect_version_code != version_code:
        raise ReleaseError(
            f"--version-code dice {options.expect_version_code} pero app/build.gradle.kts "
            f"tiene {version_code}."
        )
    published_version = base_version_name(version_name)
    report.say(f"  versión: {version_name} (código {version_code}) → publica {published_version}")

    tags = existing_release_tags(runner)
    newest = pick_newest_tag(tags)
    previous_code = version_code_at_tag(runner, newest) if newest else None
    if newest is not None and previous_code is None:
        raise ReleaseError(
            f"el release más reciente es {newest} pero ese tag no está en esta copia local, así "
            "que no se puede leer su versionCode ni descartar una colisión. Corra "
            "`git fetch --tags` y vuelva a intentar."
        )
    check_no_collision(version_name, version_code, tags, previous_code)
    report.say(f"  sin colisión: {tag_for(version_name)} libre (previo {newest or 'ninguno'})")

    # 4. Confirmación de producción, antes de gastar minutos compilando.
    confirm_production(options, version_name, stdin_reader)

    # 5. Compilar.
    apk_path = options.apk_override or (options.repo_root / APK_RELATIVE_PATH)
    if options.apply and not options.skip_build and options.apk_override is None:
        report.say(f"  compilando {GRADLE_TASK} …")
        runner.run(["./gradlew", GRADLE_TASK])
    elif not options.apply:
        report.say(f"  [simulacro] compilaría {GRADLE_TASK}")

    # 6. Firma. Si no hay APK que mirar (simulacro sin --apk) se dice, no se
    #    finge que se verificó.
    apk: ApkFacts | None = None
    if apk_path.exists():
        apksigner = options.apksigner or find_apksigner(options.repo_root)
        expected = expected_release_fingerprint(runner, options.repo_root, options.keytool)
        certs = parse_apksigner_certs(
            runner.run([apksigner, "verify", "--print-certs", str(apk_path)]).stdout
        )
        verify_signature(certs, expected)
        report.say(f"  firma verificada: {expected[:16]}… ({certs[0].dn})")

        # 7. Hash y tamaño DEL APK QUE SE VA A SUBIR, leídos una sola vez.
        apk = apk_facts(apk_path)
        report.say(f"  apk: {apk.path.name} · {apk.size_bytes} bytes · sha256 {apk.sha256}")
    elif options.apply:
        raise ReleaseError(f"no existe el APK esperado: {apk_path}")
    else:
        report.say(f"  [simulacro] no hay APK en {apk_path}; firma y sha256 sin verificar")
    report.apk = apk

    # 8. Release de GitHub.
    repo = gh_repo(runner)
    asset_name = release_asset_name(version_name)
    url = download_url(repo, version_name, asset_name)
    report.apk_url = url
    report.say(f"  url: {url}")

    if options.apply:
        assert apk is not None
        staged = Path(tempfile.mkdtemp(prefix="msp-release-")) / asset_name
        shutil.copy2(apk.path, staged)
        # El archivo subido es una copia byte a byte del que se midió: si no
        # coincidiera, el sha256 del payload describiría otro archivo.
        staged_facts = apk_facts(staged)
        if (staged_facts.sha256, staged_facts.size_bytes) != (apk.sha256, apk.size_bytes):
            raise ReleaseError("la copia del APK no coincide con el original; abortado")
        report.uploaded_path = staged
        argv = [
            "gh",
            "release",
            "create",
            tag_for(version_name),
            str(staged),
            "--title",
            f"{tag_for(version_name)} (código {version_code})",
            "--target",
            head,
        ]
        argv += ["--notes", options.notes] if options.notes else ["--generate-notes"]
        if options.prerelease:
            argv.append("--prerelease")
        runner.run(argv)
        report.say(f"  release publicado: {tag_for(version_name)}")
    else:
        report.say(f"  [simulacro] crearía el release {tag_for(version_name)} con {asset_name}")

    # 9. Firestore: solo LATEST_VERSION y APK_URL, salvo bloqueo explícito.
    payload = build_firestore_payload(published_version, url, options.force, apk)
    assert_no_forbidden_fields(payload)
    report.payload = payload
    report.say("  payload para config/api_settings (merge):")
    for key, value in payload.items():
        report.say(f"    {key} = {value!r}")
    if options.force is None:
        report.say("    (sin MIN_VERSION_*: la actualización se ofrece, no se obliga)")

    firestore = firestore_factory()
    before = firestore.read()
    if FIELD_LATEST_VERSION in before and not options.allow_downgrade:
        try:
            announced = parse_semver(str(before[FIELD_LATEST_VERSION]))
        except ReleaseError:
            announced = None  # un valor ilegible en Firestore no es razón para abortar
        if announced is not None and announced >= parse_semver(published_version):
            raise ReleaseError(
                f"Firestore ya anuncia {before[FIELD_LATEST_VERSION]!r} y se iba a escribir "
                f"{published_version!r}: eso ofrecería a la flota una versión que no es "
                "posterior. Use --allow-downgrade si de verdad es una reversión."
            )
    if before.get("baseURL"):
        report.say(f"  baseURL actual (NO se toca): {before['baseURL']!r}")

    if options.apply:
        firestore.write(payload)
        report.say("  Firestore escrito")
    else:
        report.say("  [simulacro] no se escribió Firestore")

    # 10. Releer y mostrar.
    after = firestore.read()
    report.firestore_after = after
    report.say("  documento config/api_settings tras la operación:")
    for key in sorted(after):
        report.say(f"    {key} = {after[key]!r}")
    if "baseURL" not in after and before.get("baseURL"):
        raise ReleaseError("baseURL desapareció del documento; revise Firestore ya mismo")

    return report


def run_bump(options: Options, version_code: int, version_name: str) -> Report:
    """Sube la versión en `app/build.gradle.kts` y para.

    Deliberadamente no sigue al release: el tag apunta a un commit, y si el
    bump no está commiteado el release quedaría etiquetando un árbol que
    todavía dice la versión vieja. Que el humano revise y commitee.
    """
    report = Report(echo=print if options.verbose else _silent)
    gradle_path = options.repo_root / "app/build.gradle.kts"
    text = gradle_path.read_text(encoding="utf-8")
    current_code, current_name = read_gradle_version(text)
    if version_code <= current_code:
        raise ReleaseError(
            f"versionCode {version_code} no sube respecto de {current_code}"
        )
    if parse_semver(version_name) <= parse_semver(current_name):
        raise ReleaseError(f"versionName {version_name!r} no sube respecto de {current_name!r}")
    updated = bump_gradle_version(text, version_code, version_name)
    report.say(f"  {current_name} ({current_code}) → {version_name} ({version_code})")
    if options.apply:
        gradle_path.write_text(updated, encoding="utf-8")
        report.say("  app/build.gradle.kts actualizado. Revise, commitee y vuelva a correr.")
    else:
        report.say("  [simulacro] no se escribió app/build.gradle.kts")
    return report


# ── CLI ──────────────────────────────────────────────────────────────────────


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="release_apk.py",
        description="Publica un APK de producción a la flota. Simulacro por defecto.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--dev", action="store_true", help="Firebase dev (msp-dev-96ff5)")
    target.add_argument("--prod", action="store_true", help="Firebase producción (msp-db-1c2ce)")

    parser.add_argument(
        "--apply",
        action="store_true",
        help="escribe de verdad. Sin este flag no toca nada.",
    )
    parser.add_argument(
        "--bump",
        metavar="CODE:NAME",
        help="solo sube la versión en app/build.gradle.kts y termina (ej. 58:2.17.1)",
    )
    parser.add_argument("--version-name", help="asegura que build.gradle.kts diga esta versión")
    parser.add_argument("--version-code", type=int, help="asegura este versionCode")
    parser.add_argument(
        "--force-update",
        action="store_true",
        help="además de ofrecer, BLOQUEA la app (escribe los 7 MIN_VERSION_*). Palanca de "
        "emergencia: ver docs/RELEASE.md",
    )
    parser.add_argument("--deadline", default="", help="texto de la fecha límite, ej. 'vie 22'")
    parser.add_argument(
        "--exempt",
        default="",
        help="ids de dispositivo exentos del bloqueo, separados por coma",
    )
    parser.add_argument("--confirm-prod", help="versión exacta, para confirmar producción sin tty")
    parser.add_argument("--apk", type=Path, help="usa este APK en vez de compilar")
    parser.add_argument("--skip-build", action="store_true", help="no corre gradle")
    parser.add_argument("--notes", help="notas del release (por defecto --generate-notes)")
    parser.add_argument("--prerelease", action="store_true", help="marca el release como borrador")
    parser.add_argument("--credentials", type=Path, help="ruta de la llave de servicio")
    parser.add_argument(
        "--credentials-dir",
        type=Path,
        default=Path(os.environ.get("MSP_API_DIR", DEFAULT_CREDENTIALS_DIR)),
        help="dónde viven serviceAccountKey.json / serviceAccountKeyProduction.json",
    )
    parser.add_argument("--repo-root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--apksigner", help="ruta de apksigner")
    parser.add_argument("--keytool", default="keytool", help="ruta de keytool")
    parser.add_argument("--allow-dirty", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--allow-unpushed", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument("--allow-downgrade", action="store_true", help=argparse.SUPPRESS)
    return parser


def options_from_args(args: argparse.Namespace) -> Options:
    environment = ENVIRONMENTS["prod" if args.prod else "dev"]
    credentials = args.credentials or (args.credentials_dir / environment.credentials_filename)
    force: ForceUpdate | None = None
    if args.force_update:
        exempt = tuple(d.strip() for d in args.exempt.split(",") if d.strip())
        force = ForceUpdate(
            version_code=0,  # se rellena con el del build.gradle.kts
            version_name="",
            deadline_label=args.deadline,
            exempt_device_ids=exempt,
        )
    return Options(
        environment=environment,
        repo_root=args.repo_root.resolve(),
        credentials_path=Path(credentials),
        apply=args.apply,
        force=force,
        confirm_prod=args.confirm_prod,
        expect_version_name=args.version_name,
        expect_version_code=args.version_code,
        apk_override=args.apk,
        skip_build=args.skip_build,
        notes=args.notes,
        prerelease=args.prerelease,
        allow_dirty=args.allow_dirty,
        allow_unpushed=args.allow_unpushed,
        allow_downgrade=args.allow_downgrade,
        apksigner=args.apksigner,
        keytool=args.keytool,
    )


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        options = options_from_args(args)

        if args.bump:
            if ":" not in args.bump:
                raise ReleaseError("--bump espera CODE:NAME, por ejemplo 58:2.17.1")
            raw_code, raw_name = args.bump.split(":", 1)
            run_bump(options, int(raw_code), raw_name.strip())
            return 0

        # El candado de la llave corre siempre, también en simulacro: si la
        # llave está mal, más vale enterarse antes de compilar.
        check_credentials_match_environment(options.credentials_path, options.environment)

        if options.force is not None:
            gradle = (options.repo_root / "app/build.gradle.kts").read_text(encoding="utf-8")
            code, name = read_gradle_version(gradle)
            options.force = ForceUpdate(
                version_code=code,
                version_name=name,
                deadline_label=options.force.deadline_label,
                exempt_device_ids=options.force.exempt_device_ids,
            )

        runner = CommandRunner(cwd=options.repo_root)
        gateway = FirestoreGateway(options.credentials_path, options.environment.project_id)
        run_release(options, runner, lambda: gateway)
        if not options.apply:
            print("\nSimulacro. Nada se escribió. Agregue --apply para publicar.")
        return 0
    except ReleaseError as exc:
        sys.stdout.flush()  # el reporte va a stdout; el aborto, a stderr
        print(f"\nABORTADO: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
