#!/usr/bin/env python3
"""Pruebas de `scripts/release_apk.py`.

Sin red, sin Firestore, sin Gradle, sin GitHub: todo por dobles. Lo que se
prueba no es el camino feliz sino las **propiedades de seguridad** — las cinco
formas en que este procedimiento se sabe que sale mal:

- nunca se escribe `baseURL` (el kill-switch de la flota),
- nunca se publica un APK firmado con la llave de debug,
- los `MIN_VERSION_*` no aparecen sin pedirlos, y con pedirlos aparecen los 7,
- `LATEST_VERSION` va sin sufijo,
- el simulacro no escribe nada.

Cada prueba está escrita para ponerse ROJA si se revierte la protección que
cubre; el mapeo prueba → protección está en `docs/RELEASE.md`.

Correr:

    python3 -m unittest discover -s scripts -p 'test_*.py' -v
"""

from __future__ import annotations

import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from typing import Any, Sequence

sys.path.insert(0, str(Path(__file__).resolve().parent))

import release_apk as rel  # noqa: E402

# ── Fixtures tomadas de salidas reales ───────────────────────────────────────

# `keytool -list -v` contra app/msp-app-release.keystore (alias msp-app-key).
KEYTOOL_RELEASE_OUTPUT = """
Alias name: msp-app-key
Owner: CN=MSP App, OU=Development, O=MSP Company, L=Ciudad, ST=Estado, C=MX
Certificate fingerprints:
\t SHA1: 11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:00:11:22:33
\t SHA256: 39:80:11:FB:54:B1:2F:3D:93:B0:B0:40:88:87:9D:F8:83:B1:2C:12:6B:C2:35:C7:EC:71:B2:B7:D8:FD:B2:0D
Signature algorithm name: SHA256withRSA
"""

RELEASE_FINGERPRINT = "398011fb54b12f3d93b0b04088879df883b12c126bc235c7ec71b2b7d8fdb20d"

# `apksigner verify --print-certs` sobre un APK firmado con la llave de release.
APKSIGNER_RELEASE_OUTPUT = (
    "Signer #1 certificate DN: CN=MSP App, OU=Development, O=MSP Company, "
    "L=Ciudad, ST=Estado, C=MX\n"
    f"Signer #1 certificate SHA-256 digest: {RELEASE_FINGERPRINT}\n"
    "Signer #1 certificate SHA-1 digest: 1122334455667788990011223344556677889900\n"
)

# Salida REAL de apksigner sobre app/build/outputs/apk/devlocal/debug/
# app-devlocal-debug.apk de este repo. Este es exactamente el APK que se
# produce cuando falta keystore.properties.
APKSIGNER_DEBUG_OUTPUT = (
    "Signer #1 certificate DN: C=US, O=Android, CN=Android Debug\n"
    "Signer #1 certificate SHA-256 digest: "
    "bc1f33cad7fa5f97968ac84b571a8a3892467dd0dcdf194ab486e9a5567c3ccd\n"
    "Signer #1 certificate SHA-1 digest: 28c163039e10e27ea79547b7e0f832ad3e335c5c\n"
)

GRADLE_TEMPLATE = """\
android {{
    defaultConfig {{
        applicationId = "com.example.msp_app"
        minSdk = 24
        targetSdk = 35
        versionCode = {code}
        versionName = "{name}"
    }}
}}
"""

# El documento de producción tal cual, con el kill-switch adentro.
FIRESTORE_DOC_BEFORE: dict[str, Any] = {
    "baseURL": "https://msp2025.loclx.io/",
    "LATEST_VERSION": "2.16.0",
    "APK_URL": "https://github.com/abdimuy/msp-app-kt/releases/download/v2.16.0/app-prod-release.apk",
}


# ── Dobles ───────────────────────────────────────────────────────────────────


class FakeRunner:
    """Responde a los comandos por prefijo y graba todo lo que se le pidió."""

    def __init__(self, repo_root: Path, **overrides: Any) -> None:
        self.repo_root = repo_root
        self.calls: list[list[str]] = []
        self.status = overrides.get("status", "")
        self.head = overrides.get("head", "5d28e4de1234567890abcdef1234567890abcdef")
        self.remote_branches = overrides.get("remote_branches", "  origin/main\n")
        self.local_tags = overrides.get("local_tags", ["v2.15.2", "v2.16.0"])
        self.gh_tags = overrides.get("gh_tags", ["v2.15.2", "v2.16.0"])
        self.tag_gradle = overrides.get(
            "tag_gradle", GRADLE_TEMPLATE.format(code=56, name="2.16.0")
        )
        self.keytool_output = overrides.get("keytool_output", KEYTOOL_RELEASE_OUTPUT)
        self.apksigner_output = overrides.get("apksigner_output", APKSIGNER_RELEASE_OUTPUT)
        self.repo = overrides.get("repo", "abdimuy/msp-app-kt")

    def run(self, argv: Sequence[str], check: bool = True) -> rel.CommandResult:
        argv = list(argv)
        self.calls.append(argv)
        joined = " ".join(argv)

        if argv[:3] == ["git", "status", "--porcelain"]:
            return rel.CommandResult(0, self.status, "")
        if argv[:3] == ["git", "rev-parse", "HEAD"]:
            return rel.CommandResult(0, self.head + "\n", "")
        if argv[:3] == ["git", "branch", "-r"]:
            return rel.CommandResult(0, self.remote_branches, "")
        if argv[:3] == ["git", "tag", "--list"]:
            return rel.CommandResult(0, "\n".join(self.local_tags) + "\n", "")
        if argv[:2] == ["git", "show"]:
            return rel.CommandResult(0, self.tag_gradle, "")
        if argv[:3] == ["gh", "release", "list"]:
            return rel.CommandResult(
                0, json.dumps([{"tagName": t} for t in self.gh_tags]), ""
            )
        if argv[:3] == ["gh", "repo", "view"]:
            return rel.CommandResult(0, json.dumps({"nameWithOwner": self.repo}), "")
        if argv[0].endswith("keytool"):
            return rel.CommandResult(0, self.keytool_output, "")
        if argv[0].endswith("apksigner"):
            return rel.CommandResult(0, self.apksigner_output, "")
        if argv[0].endswith("gradlew"):
            return rel.CommandResult(0, "BUILD SUCCESSFUL", "")
        if argv[:3] == ["gh", "release", "create"]:
            return rel.CommandResult(0, "https://github.com/x/releases/tag/v", "")
        raise AssertionError(f"comando inesperado en la prueba: {joined}")

    def ran(self, *prefix: str) -> list[list[str]]:
        n = len(prefix)
        return [c for c in self.calls if c[:n] == list(prefix)]


class FakeFirestore:
    """Documento en memoria. Graba cada escritura para poder inspeccionarla."""

    def __init__(self, doc: dict[str, Any] | None = None) -> None:
        self.doc = dict(FIRESTORE_DOC_BEFORE if doc is None else doc)
        self.writes: list[dict[str, Any]] = []

    def read(self) -> dict[str, Any]:
        return dict(self.doc)

    def write(self, payload: dict[str, Any]) -> None:
        rel.assert_no_forbidden_fields(payload)
        self.writes.append(dict(payload))
        self.doc.update(payload)  # `merge=True`: no borra lo que no viene


# ── Ayudas ───────────────────────────────────────────────────────────────────


class RepoFixture:
    """Un repo de mentira en disco: gradle, keystore.properties y un APK."""

    def __init__(self, tmp: Path, code: int = 57, name: str = "2.17.1") -> None:
        self.root = tmp
        (self.root / "app").mkdir(parents=True, exist_ok=True)
        self.gradle = self.root / "app/build.gradle.kts"
        self.gradle.write_text(GRADLE_TEMPLATE.format(code=code, name=name), encoding="utf-8")
        self.keystore_properties = self.root / "keystore.properties"
        self.keystore_properties.write_text(
            "storePassword=secreto\nkeyPassword=secreto\n"
            "keyAlias=msp-app-key\nstoreFile=msp-app-release.keystore\n",
            encoding="utf-8",
        )
        (self.root / "app/msp-app-release.keystore").write_bytes(b"keystore-de-mentira")
        self.apk_dir = self.root / "app/build/outputs/apk/prod/release"
        self.apk_dir.mkdir(parents=True, exist_ok=True)
        self.apk = self.apk_dir / "app-prod-release.apk"
        self.apk.write_bytes(b"PK\x03\x04" + b"contenido del apk de release" * 100)

    def options(self, **kwargs: Any) -> rel.Options:
        defaults: dict[str, Any] = {
            "environment": rel.ENVIRONMENTS["dev"],
            "repo_root": self.root,
            "credentials_path": self.root / "key.json",
            "apksigner": "/fake/build-tools/apksigner",
            "keytool": "/fake/keytool",
            # Las pruebas no necesitan ver el reporte; el ruido esconde fallos.
            "verbose": False,
        }
        defaults.update(kwargs)
        return rel.Options(**defaults)


def credentials(tmp: Path, project_id: str, filename: str = "key.json") -> Path:
    path = tmp / filename
    path.write_text(
        json.dumps({"type": "service_account", "project_id": project_id}), encoding="utf-8"
    )
    return path


class ReleaseTestCase(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.tmp = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)
        self.repo = RepoFixture(self.tmp)


# ── Propiedad 1: nunca se escribe baseURL ────────────────────────────────────


class BaseUrlNeverWritten(ReleaseTestCase):
    """El kill-switch de la flota no se toca, ni con el mismo valor."""

    def test_payload_sin_force_no_menciona_baseurl(self) -> None:
        payload = rel.build_firestore_payload("2.17.1", "https://x/app.apk")
        self.assertNotIn("baseURL", payload)
        self.assertEqual({"LATEST_VERSION", "APK_URL"}, set(payload))

    def test_payload_con_force_tampoco_menciona_baseurl(self) -> None:
        payload = rel.build_firestore_payload(
            "2.17.1",
            "https://x/app.apk",
            rel.ForceUpdate(58, "2.17.1", "vie 22", ()),
            rel.ApkFacts(Path("a.apk"), "ab" * 32, 10),
        )
        self.assertNotIn("baseURL", payload)

    def test_la_guarda_lanza_si_alguien_mete_baseurl(self) -> None:
        for key in ("baseURL", "baseurl", "BASEURL"):
            with self.subTest(key=key):
                with self.assertRaises(rel.ReleaseError) as ctx:
                    rel.assert_no_forbidden_fields({"LATEST_VERSION": "2.17.1", key: "https://x/"})
                self.assertIn("kill-switch", str(ctx.exception))

    def test_flujo_completo_escribe_solo_dos_campos_y_conserva_baseurl(self) -> None:
        firestore = FakeFirestore()
        runner = FakeRunner(self.repo.root)
        options = self.repo.options(apply=True, skip_build=True)
        rel.run_release(options, runner, lambda: firestore)

        self.assertEqual(1, len(firestore.writes))
        self.assertNotIn("baseURL", firestore.writes[0])
        self.assertEqual({"LATEST_VERSION", "APK_URL"}, set(firestore.writes[0]))
        # Y el documento resultante conserva el kill-switch intacto.
        self.assertEqual(FIRESTORE_DOC_BEFORE["baseURL"], firestore.doc["baseURL"])


# ── Propiedad 2: aborta si falta keystore.properties ─────────────────────────


class KeystoreRequired(ReleaseTestCase):
    """Sin keystore.properties el build de release cae a la firma de debug."""

    def test_check_keystore_lanza_si_falta(self) -> None:
        self.repo.keystore_properties.unlink()
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.check_keystore(self.repo.root)
        self.assertIn("keystore.properties", str(ctx.exception))

    def test_el_flujo_aborta_antes_de_compilar(self) -> None:
        self.repo.keystore_properties.unlink()
        runner = FakeRunner(self.repo.root)
        with self.assertRaises(rel.ReleaseError):
            rel.run_release(self.repo.options(apply=True), runner, FakeFirestore)
        self.assertEqual([], [c for c in runner.calls if c[0].endswith("gradlew")])
        self.assertEqual([], runner.ran("gh", "release", "create"))


# ── Propiedad 3: nunca se publica un APK firmado con la llave de debug ───────


class DebugSignatureRejected(ReleaseTestCase):
    """La firma de debug produce INSTALL_FAILED_UPDATE_INCOMPATIBLE en campo."""

    def test_verify_signature_rechaza_la_llave_de_debug(self) -> None:
        certs = rel.parse_apksigner_certs(APKSIGNER_DEBUG_OUTPUT)
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.verify_signature(certs, RELEASE_FINGERPRINT)
        self.assertIn("DEBUG", str(ctx.exception))

    def test_verify_signature_rechaza_la_debug_aunque_la_huella_esperada_sea_la_debug(
        self,
    ) -> None:
        # keystore.properties mal apuntado: la huella "esperada" saldría de la
        # misma llave equivocada y el match positivo pasaria vacio.
        certs = rel.parse_apksigner_certs(APKSIGNER_DEBUG_OUTPUT)
        debug_fingerprint = certs[0].sha256
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.verify_signature(certs, debug_fingerprint)
        self.assertIn("DEBUG", str(ctx.exception))

    def test_verify_signature_rechaza_otra_llave_de_release(self) -> None:
        certs = [rel.SignerCert(dn="CN=Otra Empresa", sha256="ff" * 32)]
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.verify_signature(certs, RELEASE_FINGERPRINT)
        self.assertIn("no corresponde", str(ctx.exception))

    def test_verify_signature_acepta_la_llave_de_release(self) -> None:
        certs = rel.parse_apksigner_certs(APKSIGNER_RELEASE_OUTPUT)
        rel.verify_signature(certs, RELEASE_FINGERPRINT)  # no lanza

    def test_apk_sin_firma_no_pasa(self) -> None:
        with self.assertRaises(rel.ReleaseError):
            rel.parse_apksigner_certs("DOES NOT VERIFY\n")

    def test_el_flujo_aborta_y_no_publica_nada(self) -> None:
        firestore = FakeFirestore()
        runner = FakeRunner(self.repo.root, apksigner_output=APKSIGNER_DEBUG_OUTPUT)
        with self.assertRaises(rel.ReleaseError):
            rel.run_release(
                self.repo.options(apply=True, skip_build=True), runner, lambda: firestore
            )
        self.assertEqual([], runner.ran("gh", "release", "create"))
        self.assertEqual([], firestore.writes)

    def test_keytool_y_apksigner_hablan_del_mismo_numero(self) -> None:
        self.assertEqual(
            rel.parse_keytool_fingerprint(KEYTOOL_RELEASE_OUTPUT),
            rel.parse_apksigner_certs(APKSIGNER_RELEASE_OUTPUT)[0].sha256,
        )


# ── Propiedad 4: los MIN_VERSION_* solo con el flag, y completos ─────────────


class MinVersionOnlyOnDemand(ReleaseTestCase):
    """Escribir MIN_VERSION_* sin querer deja a la flota sin poder trabajar."""

    def test_sin_flag_no_aparece_ningun_min_version(self) -> None:
        payload = rel.build_firestore_payload("2.17.1", "https://x/app.apk")
        for field_name in rel.MIN_VERSION_FIELDS:
            self.assertNotIn(field_name, payload)

    def test_flujo_completo_sin_flag_no_escribe_ningun_min_version(self) -> None:
        firestore = FakeFirestore()
        rel.run_release(
            self.repo.options(apply=True, skip_build=True),
            FakeRunner(self.repo.root),
            lambda: firestore,
        )
        for field_name in rel.MIN_VERSION_FIELDS:
            self.assertNotIn(field_name, firestore.writes[0])

    def test_con_flag_aparecen_los_siete_completos(self) -> None:
        firestore = FakeFirestore()
        force = rel.ForceUpdate(57, "2.17.1", "vie 22", ("a@msp.com", "b@msp.com"))
        rel.run_release(
            self.repo.options(apply=True, skip_build=True, force=force),
            FakeRunner(self.repo.root),
            lambda: firestore,
        )
        written = firestore.writes[0]
        self.assertEqual(7, len(rel.MIN_VERSION_FIELDS))
        for field_name in rel.MIN_VERSION_FIELDS:
            self.assertIn(field_name, written)
        self.assertEqual(57, written["MIN_VERSION_CODE"])
        self.assertEqual("2.17.1", written["MIN_VERSION_NAME"])
        self.assertEqual("vie 22", written["MIN_VERSION_DEADLINE"])
        self.assertEqual(["a@msp.com", "b@msp.com"], written["MIN_VERSION_EXEMPT_DEVICES"])
        self.assertEqual(written["APK_URL"], written["MIN_VERSION_APK_URL"])

    def test_los_tipos_son_los_que_lee_el_cliente(self) -> None:
        # FirestoreMinVersionConfigSource lee CODE/SIZE como Long, el resto
        # como String, y EXEMPT_DEVICES como List<String>. Un tipo distinto se
        # descarta en silencio y la compuerta queda apagada creyendo estar viva.
        payload = rel.build_firestore_payload(
            "2.17.1",
            "https://x/app.apk",
            rel.ForceUpdate(57, "2.17.1", "vie 22", ("a@msp.com",)),
            rel.ApkFacts(Path("a.apk"), "ab" * 32, 11729264),
        )
        self.assertIsInstance(payload["MIN_VERSION_CODE"], int)
        self.assertIsInstance(payload["MIN_VERSION_APK_SIZE"], int)
        self.assertIsInstance(payload["MIN_VERSION_EXEMPT_DEVICES"], list)
        for field_name in ("MIN_VERSION_NAME", "MIN_VERSION_DEADLINE", "MIN_VERSION_APK_SHA256"):
            self.assertIsInstance(payload[field_name], str)

    def test_bloqueo_sin_deadline_no_se_publica(self) -> None:
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.build_firestore_payload(
                "2.17.1",
                "https://x/app.apk",
                rel.ForceUpdate(57, "2.17.1", "   ", ()),
                rel.ApkFacts(Path("a.apk"), "ab" * 32, 10),
            )
        self.assertIn("deadline", str(ctx.exception))

    def test_bloqueo_sin_apk_no_se_publica(self) -> None:
        # Sin sha256/tamaño el cliente descarta el UpdatePackage entero y
        # muestra "todavía no hay APK" con la app ya bloqueada.
        with self.assertRaises(rel.ReleaseError):
            rel.build_firestore_payload(
                "2.17.1", "https://x/app.apk", rel.ForceUpdate(57, "2.17.1", "vie 22", ()), None
            )


# ── Propiedad 5: LATEST_VERSION se publica sin sufijo ────────────────────────


class VersionSuffixStripped(ReleaseTestCase):
    """`Constants.APP_VERSION` recorta en el primer `-`; el anuncio también."""

    def test_base_version_name(self) -> None:
        casos = {
            "2.17.0-local+5d28e4de": "2.17.0",
            "2.17.0-dev": "2.17.0",
            "2.17.0+123": "2.17.0",
            "  2.17.0  ": "2.17.0",
            "2.17.0": "2.17.0",
        }
        for entrada, esperado in casos.items():
            with self.subTest(entrada=entrada):
                self.assertEqual(esperado, rel.base_version_name(entrada))

    def test_payload_publica_la_base(self) -> None:
        payload = rel.build_firestore_payload("2.17.1-local+5d28e4de", "https://x/app.apk")
        self.assertEqual("2.17.1", payload["LATEST_VERSION"])

    def test_min_version_name_tambien_va_sin_sufijo(self) -> None:
        payload = rel.build_firestore_payload(
            "2.17.1",
            "https://x/app.apk",
            rel.ForceUpdate(57, "2.17.1-local+abc", "vie 22", ()),
            rel.ApkFacts(Path("a.apk"), "ab" * 32, 10),
        )
        self.assertEqual("2.17.1", payload["MIN_VERSION_NAME"])

    def test_el_tag_y_la_url_tambien(self) -> None:
        self.assertEqual("v2.17.1", rel.tag_for("2.17.1-local+abc"))
        self.assertEqual("app-prod-release-2.17.1.apk", rel.release_asset_name("2.17.1-local"))
        self.assertIn(
            "/download/v2.17.1/",
            rel.download_url("abdimuy/msp-app-kt", "2.17.1-local", "app-prod-release-2.17.1.apk"),
        )

    def test_flujo_completo_con_sufijo_en_el_gradle(self) -> None:
        self.repo.gradle.write_text(
            GRADLE_TEMPLATE.format(code=57, name="2.17.1-rc1"), encoding="utf-8"
        )
        firestore = FakeFirestore()
        rel.run_release(
            self.repo.options(apply=True, skip_build=True),
            FakeRunner(self.repo.root),
            lambda: firestore,
        )
        self.assertEqual("2.17.1", firestore.writes[0]["LATEST_VERSION"])


# ── Propiedad 6: el simulacro no escribe nada ────────────────────────────────


class DryRunWritesNothing(ReleaseTestCase):
    """Sin `--apply` no hay Firestore, ni release, ni archivo tocado."""

    def test_ni_firestore_ni_release_ni_gradle(self) -> None:
        firestore = FakeFirestore()
        runner = FakeRunner(self.repo.root)
        antes = self.repo.gradle.read_bytes()

        report = rel.run_release(self.repo.options(apply=False), runner, lambda: firestore)

        self.assertEqual([], firestore.writes)
        self.assertEqual([], runner.ran("gh", "release", "create"))
        self.assertEqual([], [c for c in runner.calls if c[0].endswith("gradlew")])
        self.assertEqual(antes, self.repo.gradle.read_bytes())
        self.assertIsNone(report.uploaded_path)
        # Pero sí dice qué habría escrito.
        self.assertEqual({"LATEST_VERSION", "APK_URL"}, set(report.payload or {}))

    def test_el_simulacro_del_bump_no_toca_el_archivo(self) -> None:
        antes = self.repo.gradle.read_bytes()
        rel.run_bump(self.repo.options(apply=False), 58, "2.18.0")
        self.assertEqual(antes, self.repo.gradle.read_bytes())

    def test_el_bump_con_apply_si_lo_toca(self) -> None:
        rel.run_bump(self.repo.options(apply=True), 58, "2.18.0")
        self.assertEqual((58, "2.18.0"), rel.read_gradle_version(self.repo.gradle.read_text()))

    def test_el_bump_no_baja_la_version(self) -> None:
        with self.assertRaises(rel.ReleaseError):
            rel.run_bump(self.repo.options(apply=True), 56, "2.16.0")
        self.assertEqual((57, "2.17.1"), rel.read_gradle_version(self.repo.gradle.read_text()))

    def test_bump_gradle_version_conserva_el_resto_del_archivo(self) -> None:
        original = self.repo.gradle.read_text()
        actualizado = rel.bump_gradle_version(original, 58, "2.18.0")
        self.assertIn('applicationId = "com.example.msp_app"', actualizado)
        self.assertIn("minSdk = 24", actualizado)
        self.assertEqual((58, "2.18.0"), rel.read_gradle_version(actualizado))

    def test_bump_gradle_version_se_niega_si_hay_dos_declaraciones(self) -> None:
        doble = self.repo.gradle.read_text() + "\nversionCode = 99\n"
        with self.assertRaises(rel.ReleaseError):
            rel.bump_gradle_version(doble, 58, "2.18.0")


# ── Propiedad 7: la llave tiene que ser la del entorno pedido ────────────────


class CredentialsMustMatchEnvironment(ReleaseTestCase):
    """El candado que ya evitó un accidente: manda el project_id de la llave."""

    def test_llave_de_dev_contra_entorno_prod_aborta(self) -> None:
        path = credentials(self.tmp, "msp-dev-96ff5")
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.check_credentials_match_environment(path, rel.ENVIRONMENTS["prod"])
        self.assertIn("msp-dev-96ff5", str(ctx.exception))

    def test_llave_de_prod_contra_entorno_dev_aborta(self) -> None:
        path = credentials(self.tmp, "msp-db-1c2ce")
        with self.assertRaises(rel.ReleaseError):
            rel.check_credentials_match_environment(path, rel.ENVIRONMENTS["dev"])

    def test_llave_correcta_pasa(self) -> None:
        rel.check_credentials_match_environment(
            credentials(self.tmp, "msp-dev-96ff5"), rel.ENVIRONMENTS["dev"]
        )
        rel.check_credentials_match_environment(
            credentials(self.tmp, "msp-db-1c2ce", "prod.json"), rel.ENVIRONMENTS["prod"]
        )

    def test_llave_ausente_aborta(self) -> None:
        with self.assertRaises(rel.ReleaseError):
            rel.check_credentials_match_environment(self.tmp / "no-existe.json", rel.ENVIRONMENTS["dev"])

    def test_los_project_id_esperados_son_los_reales(self) -> None:
        self.assertEqual("msp-dev-96ff5", rel.ENVIRONMENTS["dev"].project_id)
        self.assertEqual("msp-db-1c2ce", rel.ENVIRONMENTS["prod"].project_id)
        self.assertTrue(rel.ENVIRONMENTS["prod"].requires_confirmation)

    def test_main_exige_elegir_entorno(self) -> None:
        # argparse escupe el `usage:` a stderr; silenciarlo evita que el ruido
        # tape un fallo real en el reporte de las pruebas.
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            rel.main([])


# ── Propiedad 8: colisión de versión ─────────────────────────────────────────


class VersionCollision(ReleaseTestCase):
    """No se republica un tag existente ni se repite/baja el versionCode."""

    def test_tag_ya_publicado_aborta(self) -> None:
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.check_no_collision("2.16.0", 58, ["v2.15.2", "v2.16.0"], 56)
        self.assertIn("ya existe", str(ctx.exception))

    def test_tag_que_solo_existe_en_github_tambien_aborta(self) -> None:
        # Caso real: v2.17.0 es un pre-release en GitHub y el tag NO está en la
        # copia local. Mirar solo `git tag` diría que la versión está libre.
        with self.assertRaises(rel.ReleaseError):
            rel.check_no_collision("2.17.0", 58, ["v2.16.0", "v2.17.0"], 57)

    def test_version_name_que_no_sube_aborta(self) -> None:
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.check_no_collision("2.15.9", 60, ["v2.16.0"], 56)
        self.assertIn("no es posterior", str(ctx.exception))

    def test_version_code_que_no_sube_aborta(self) -> None:
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.check_no_collision("2.17.1", 56, ["v2.16.0"], 56)
        self.assertIn("versionCode", str(ctx.exception))

    def test_version_nueva_pasa(self) -> None:
        rel.check_no_collision("2.17.1", 58, ["v2.15.2", "v2.16.0"], 56)

    def test_el_flujo_junta_tags_locales_y_de_github(self) -> None:
        runner = FakeRunner(self.repo.root, local_tags=["v2.16.0"], gh_tags=["v2.17.1"])
        with self.assertRaises(rel.ReleaseError):
            rel.run_release(self.repo.options(apply=True), runner, FakeFirestore)
        self.assertEqual([], runner.ran("gh", "release", "create"))

    def test_tag_previo_ausente_en_local_aborta_en_vez_de_adivinar(self) -> None:
        class SinTagLocal(FakeRunner):
            def run(self, argv: Sequence[str], check: bool = True) -> rel.CommandResult:
                if list(argv)[:2] == ["git", "show"]:
                    self.calls.append(list(argv))
                    return rel.CommandResult(128, "", "fatal: invalid object name")
                return super().run(argv, check)

        runner = SinTagLocal(self.repo.root)
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.run_release(self.repo.options(apply=True), runner, FakeFirestore)
        self.assertIn("git fetch --tags", str(ctx.exception))

    def test_pick_newest_tag_compara_como_numeros(self) -> None:
        self.assertEqual("v2.10.0", rel.pick_newest_tag(["v2.9.0", "v2.10.0"]))
        self.assertEqual("v2.16.0", rel.pick_newest_tag(["v2.16.0", "no-es-version", "v2.15.2"]))
        self.assertIsNone(rel.pick_newest_tag(["basura"]))

    def test_no_se_anuncia_una_version_que_no_es_posterior(self) -> None:
        firestore = FakeFirestore({"LATEST_VERSION": "2.18.0", "baseURL": "https://x/"})
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.run_release(
                self.repo.options(apply=True, skip_build=True),
                FakeRunner(self.repo.root),
                lambda: firestore,
            )
        self.assertIn("2.18.0", str(ctx.exception))
        self.assertEqual([], firestore.writes)


# ── Propiedad 9: el sha256 y el tamaño son los del archivo que se subió ──────


class HashDescribesTheUploadedFile(ReleaseTestCase):
    """El error clásico: medir un APK y subir otro."""

    def test_sha256_y_tamano_del_apk_construido(self) -> None:
        firestore = FakeFirestore()
        runner = FakeRunner(self.repo.root)
        report = rel.run_release(
            self.repo.options(apply=True, skip_build=True), runner, lambda: firestore
        )

        subida = runner.ran("gh", "release", "create")[0]
        rutas = [Path(a) for a in subida if a.endswith(".apk")]
        self.assertEqual(1, len(rutas))
        real = rel.apk_facts(rutas[0])

        self.assertEqual(real.sha256, (report.apk or real).sha256)
        self.assertEqual(real.size_bytes, (report.apk or real).size_bytes)
        # Y es el mismo contenido que el APK que se compiló, no otro.
        self.assertEqual(self.repo.apk.read_bytes(), rutas[0].read_bytes())

    def test_el_payload_de_bloqueo_lleva_ese_mismo_hash(self) -> None:
        firestore = FakeFirestore()
        runner = FakeRunner(self.repo.root)
        force = rel.ForceUpdate(57, "2.17.1", "vie 22", ())
        rel.run_release(
            self.repo.options(apply=True, skip_build=True, force=force),
            runner,
            lambda: firestore,
        )
        subida = runner.ran("gh", "release", "create")[0]
        ruta = next(Path(a) for a in subida if a.endswith(".apk"))
        real = rel.apk_facts(ruta)
        written = firestore.writes[0]
        self.assertEqual(real.sha256, written["MIN_VERSION_APK_SHA256"])
        self.assertEqual(real.size_bytes, written["MIN_VERSION_APK_SIZE"])

    def test_otro_apk_da_otro_hash(self) -> None:
        uno = self.tmp / "uno.apk"
        otro = self.tmp / "otro.apk"
        uno.write_bytes(b"a" * 100)
        otro.write_bytes(b"b" * 100)
        self.assertNotEqual(rel.apk_facts(uno).sha256, rel.apk_facts(otro).sha256)

    def test_apk_vacio_no_se_publica(self) -> None:
        vacio = self.tmp / "vacio.apk"
        vacio.write_bytes(b"")
        with self.assertRaises(rel.ReleaseError):
            rel.apk_facts(vacio)

    def test_el_nombre_del_asset_lleva_la_version(self) -> None:
        runner = FakeRunner(self.repo.root)
        rel.run_release(
            self.repo.options(apply=True, skip_build=True), runner, FakeFirestore
        )
        subida = runner.ran("gh", "release", "create")[0]
        ruta = next(a for a in subida if a.endswith(".apk"))
        self.assertTrue(ruta.endswith("app-prod-release-2.17.1.apk"), ruta)


# ── Árbol limpio, commit conocido y confirmación de producción ───────────────


class WorkingTreeAndConfirmation(ReleaseTestCase):
    def test_arbol_sucio_aborta(self) -> None:
        runner = FakeRunner(self.repo.root, status=" M app/build.gradle.kts\n")
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.run_release(self.repo.options(apply=True), runner, FakeFirestore)
        self.assertIn("no está limpio", str(ctx.exception))

    def test_commit_no_publicado_aborta(self) -> None:
        runner = FakeRunner(self.repo.root, remote_branches="")
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.run_release(self.repo.options(apply=True), runner, FakeFirestore)
        self.assertIn("rama remota", str(ctx.exception))

    def test_produccion_exige_teclear_la_version(self) -> None:
        options = self.repo.options(
            apply=True, skip_build=True, environment=rel.ENVIRONMENTS["prod"]
        )
        with self.assertRaises(rel.ReleaseError):
            rel.confirm_production(options, "2.17.1", lambda: "sí\n")
        rel.confirm_production(options, "2.17.1", lambda: "2.17.1\n")

    def test_confirm_prod_equivocado_aborta(self) -> None:
        options = self.repo.options(
            apply=True, environment=rel.ENVIRONMENTS["prod"], confirm_prod="2.16.0"
        )
        with self.assertRaises(rel.ReleaseError):
            rel.confirm_production(options, "2.17.1", lambda: "")

    def test_dev_no_pide_confirmacion(self) -> None:
        rel.confirm_production(self.repo.options(apply=True), "2.17.1", lambda: "")

    def test_el_simulacro_de_produccion_no_pide_nada(self) -> None:
        options = self.repo.options(apply=False, environment=rel.ENVIRONMENTS["prod"])
        rel.confirm_production(options, "2.17.1", lambda: "")

    def test_produccion_sin_confirmar_no_publica(self) -> None:
        firestore = FakeFirestore()
        runner = FakeRunner(self.repo.root)
        options = self.repo.options(
            apply=True, skip_build=True, environment=rel.ENVIRONMENTS["prod"]
        )
        with self.assertRaises(rel.ReleaseError):
            rel.run_release(options, runner, lambda: firestore, lambda: "no\n")
        self.assertEqual([], runner.ran("gh", "release", "create"))
        self.assertEqual([], firestore.writes)

    def test_version_esperada_que_no_coincide_aborta(self) -> None:
        runner = FakeRunner(self.repo.root)
        options = self.repo.options(apply=True, expect_version_name="2.99.0")
        with self.assertRaises(rel.ReleaseError) as ctx:
            rel.run_release(options, runner, FakeFirestore)
        self.assertIn("2.99.0", str(ctx.exception))


# ── Detalles de parseo ───────────────────────────────────────────────────────


class Parsing(unittest.TestCase):
    def test_read_gradle_version(self) -> None:
        code, name = rel.read_gradle_version(GRADLE_TEMPLATE.format(code=57, name="2.17.0"))
        self.assertEqual((57, "2.17.0"), (code, name))

    def test_read_gradle_version_sin_versiones_lanza(self) -> None:
        with self.assertRaises(rel.ReleaseError):
            rel.read_gradle_version("android { }")

    def test_parse_semver(self) -> None:
        self.assertEqual((2, 17, 1), rel.parse_semver("2.17.1"))
        self.assertEqual((2, 17, 1), rel.parse_semver("2.17.1-local+abc"))
        for malo in ("2.17", "dos.diecisiete.uno", "", "2.17.1.1"):
            with self.subTest(malo=malo), self.assertRaises(rel.ReleaseError):
                rel.parse_semver(malo)

    def test_newest_build_tools(self) -> None:
        self.assertEqual("36.1.0", rel.newest_build_tools(["35.0.0", "36.0.0", "36.1.0"]))
        self.assertEqual("36.0.0", rel.newest_build_tools(["36.0.0", "9.0.0"]))
        self.assertIsNone(rel.newest_build_tools(["source.properties"]))

    def test_parse_keytool_fingerprint(self) -> None:
        self.assertEqual(RELEASE_FINGERPRINT, rel.parse_keytool_fingerprint(KEYTOOL_RELEASE_OUTPUT))

    def test_parse_keytool_fingerprint_sin_sha256_lanza(self) -> None:
        with self.assertRaises(rel.ReleaseError):
            rel.parse_keytool_fingerprint("Alias name: x\nSHA1: 11:22\n")

    def test_parse_apksigner_varios_firmantes(self) -> None:
        salida = (
            "Signer #1 certificate DN: CN=MSP App\n"
            f"Signer #1 certificate SHA-256 digest: {RELEASE_FINGERPRINT}\n"
            "Signer #2 certificate DN: C=US, O=Android, CN=Android Debug\n"
            "Signer #2 certificate SHA-256 digest: " + "cd" * 32 + "\n"
        )
        certs = rel.parse_apksigner_certs(salida)
        self.assertEqual(2, len(certs))
        # Basta con que UNA firma sea la de debug para rechazar.
        with self.assertRaises(rel.ReleaseError):
            rel.verify_signature(certs, RELEASE_FINGERPRINT)

    def test_resolve_store_file_busca_en_app_primero(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "app").mkdir()
            (root / "app/msp-app-release.keystore").write_bytes(b"x")
            self.assertEqual(
                root / "app/msp-app-release.keystore",
                rel.resolve_store_file(root, "msp-app-release.keystore"),
            )

    def test_resolve_store_file_inexistente_lanza(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(rel.ReleaseError):
                rel.resolve_store_file(Path(tmp), "no-existe.keystore")

    def test_expected_release_fingerprint_lee_el_keystore(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            repo = RepoFixture(Path(tmp))
            runner = FakeRunner(repo.root)
            huella = rel.expected_release_fingerprint(runner, repo.root, "/fake/keytool")
            self.assertEqual(RELEASE_FINGERPRINT, huella)
            invocacion = next(c for c in runner.calls if c[0].endswith("keytool"))
            self.assertIn(str(repo.root / "app/msp-app-release.keystore"), invocacion)


if __name__ == "__main__":
    unittest.main(verbosity=2)
