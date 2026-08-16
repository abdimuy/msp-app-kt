#!/usr/bin/env python3
"""Resume los resultados JUnit XML de Gradle en un reporte legible.

Motivo: un `./gradlew ... --continue` en CI escupe cientos de lineas de
`> Task :modulo:tarea`; encontrar *que* prueba de *que* modulo trono ahi es
buscar una aguja en un pajar. Este script recorre todos los
`**/build/test-results/**/TEST-*.xml` (unit tests JVM/Robolectric) y
`**/build/outputs/androidTest-results/**/TEST-*.xml` (instrumentadas, que AGP
deposita en otro arbol pero en el mismo formato JUnit) y emite:

  - una tabla por modulo/tarea con total / fallos / errores / omitidos, y
  - el detalle de CADA prueba fallida: modulo, clase, nombre del metodo,
    tipo de excepcion, mensaje y las primeras lineas del stack trace.

Salida a stdout y, si existe la variable de entorno GITHUB_STEP_SUMMARY, a
ese archivo en Markdown (aparece en la portada del job, sin abrir logs).

Codigo de salida: 0 siempre. El script NO es la compuerta -- la compuerta es
el exit code de Gradle. Reportar mal no debe tumbar un build que si paso, ni
salvar uno que no.

Uso:
    python3 scripts/ci-test-summary.py [raiz-del-repo]
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

# El XML lo genera Gradle en el mismo runner (entrada de confianza), pero si
# `defusedxml` esta disponible se usa: cuesta cero y cierra billion-laughs /
# XXE por si algun dia esto llega a leer un artefacto descargado. Sin
# `defusedxml` instalado (el caso normal en el runner) cae al parser estandar.
try:  # pragma: no cover - depende del entorno
    from defusedxml.ElementTree import parse as _safe_parse
except ImportError:  # pragma: no cover
    _safe_parse = ET.parse

# Cuantas lineas de stack trace se imprimen por prueba fallida. Suficiente
# para ubicar el archivo/linea sin volcar 200 frames de Robolectric.
STACK_TRACE_LINES = 12

# Tope de pruebas fallidas detalladas. Si un cambio rompe 300 pruebas, el
# detalle de las primeras 40 ya dice que paso; el resto es ruido.
MAX_FAILURE_DETAILS = 40


@dataclass
class Failure:
    module: str
    task: str
    classname: str
    name: str
    kind: str  # "failure" | "error"
    type_name: str
    message: str
    stack: str


@dataclass
class Bucket:
    """Un `build/test-results/<tarea>/` de un modulo."""

    module: str
    task: str
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    detail: list[Failure] = field(default_factory=list)

    @property
    def bad(self) -> int:
        return self.failures + self.errors


def _module_and_task(xml_path: Path, root: Path) -> tuple[str, str]:
    """Deriva (`:modulo:ruta`, `tarea`) de la ruta del XML.

    Unit tests JVM/Robolectric:
        `core/designsystem/build/test-results/testDebugUnitTest/TEST-x.xml`
            -> (":core:designsystem", "testDebugUnitTest")

    Pruebas instrumentadas (`connectedAndroidTest`), que AGP escribe en OTRO
    arbol -- `build/outputs/androidTest-results/` -- y con el flavor y el build
    type como subdirectorios en vez de un nombre de tarea:
        `app/build/outputs/androidTest-results/connected/devlocal/debug/TEST-y.xml`
            -> (":app", "connected/devlocal/debug")
    """
    rel = xml_path.relative_to(root)
    parts = list(rel.parts)
    try:
        build_at = parts.index("build")
    except ValueError:
        return (str(rel.parent), "?")

    results_at = None
    for marker in ("test-results", "androidTest-results"):
        try:
            results_at = parts.index(marker, build_at)
            break
        except ValueError:
            continue
    if results_at is None:
        return (str(rel.parent), "?")

    module_parts = parts[:build_at]
    module = ":" + ":".join(module_parts) if module_parts else ":"
    # Todo lo que hay entre el marcador y el archivo describe la corrida: una
    # sola carpeta (`testDebugUnitTest`) para unit tests, varias
    # (`connected/devlocal/debug`) para instrumentadas.
    task = "/".join(parts[results_at + 1 : -1]) or "?"
    return (module, task)


def _text_of(node: ET.Element) -> str:
    return (node.text or "").strip()


def collect(root: Path) -> list[Bucket]:
    buckets: dict[tuple[str, str], Bucket] = {}

    xml_paths = sorted(
        set(root.glob("**/build/test-results/**/TEST-*.xml"))
        | set(root.glob("**/build/outputs/androidTest-results/**/TEST-*.xml"))
    )
    for xml_path in xml_paths:
        module, task = _module_and_task(xml_path, root)
        key = (module, task)
        bucket = buckets.setdefault(key, Bucket(module=module, task=task))

        try:
            tree = _safe_parse(xml_path)
        except ET.ParseError as exc:
            # Un XML truncado suele significar que la JVM de test murio a
            # media corrida (OOM de Robolectric, por ejemplo). Vale la pena
            # decirlo en vez de ignorarlo.
            bucket.errors += 1
            bucket.detail.append(
                Failure(
                    module=module,
                    task=task,
                    classname=xml_path.name,
                    name="(XML ilegible)",
                    kind="error",
                    type_name="ParseError",
                    message=f"{exc} -- la JVM de test pudo haber muerto a media corrida",
                    stack="",
                )
            )
            continue

        suite = tree.getroot()
        bucket.tests += int(suite.get("tests", 0))
        bucket.failures += int(suite.get("failures", 0))
        bucket.errors += int(suite.get("errors", 0))
        bucket.skipped += int(suite.get("skipped", 0))

        for case in suite.iter("testcase"):
            for kind in ("failure", "error"):
                for node in case.findall(kind):
                    stack = _text_of(node)
                    bucket.detail.append(
                        Failure(
                            module=module,
                            task=task,
                            classname=case.get("classname", "?"),
                            name=case.get("name", "?"),
                            kind=kind,
                            type_name=node.get("type", "") or "",
                            message=(node.get("message", "") or "").strip(),
                            stack=stack,
                        )
                    )

    return sorted(buckets.values(), key=lambda b: (b.module, b.task))


def render(buckets: list[Bucket]) -> str:
    out: list[str] = []
    total = sum(b.tests for b in buckets)
    bad = sum(b.bad for b in buckets)
    skipped = sum(b.skipped for b in buckets)

    if not buckets:
        return (
            "## Pruebas\n\n"
            "No se encontro ningun `build/test-results/**/TEST-*.xml`. "
            "Si el job fallo antes de compilar, el error real esta en el log de Gradle.\n"
        )

    head = f"## Pruebas: {total} ejecutadas, {bad} con fallo, {skipped} omitidas"
    out.append(head)
    out.append("")
    out.append("| Modulo | Tarea | Total | Fallos | Errores | Omitidas |")
    out.append("| --- | --- | ---: | ---: | ---: | ---: |")
    for b in buckets:
        mark = " :x:" if b.bad else ""
        out.append(
            f"| `{b.module}`{mark} | `{b.task}` | {b.tests} | {b.failures} | {b.errors} | {b.skipped} |"
        )
    out.append("")

    failures = [f for b in buckets for f in b.detail]
    if not failures:
        out.append("Sin pruebas fallidas.")
        out.append("")
        return "\n".join(out)

    out.append(f"### Pruebas fallidas ({len(failures)})")
    out.append("")
    for f in failures[:MAX_FAILURE_DETAILS]:
        out.append(f"#### `{f.module}` &rsaquo; `{f.classname}.{f.name}`")
        out.append("")
        out.append(f"- tarea: `{f.task}` ({f.kind})")
        if f.type_name:
            out.append(f"- tipo: `{f.type_name}`")
        if f.message:
            out.append(f"- mensaje: {f.message.splitlines()[0][:500]}")
        trimmed = "\n".join(f.stack.splitlines()[:STACK_TRACE_LINES])
        if trimmed:
            out.append("")
            out.append("```")
            out.append(trimmed)
            out.append("```")
        out.append("")
    if len(failures) > MAX_FAILURE_DETAILS:
        out.append(
            f"_... y {len(failures) - MAX_FAILURE_DETAILS} fallos mas. "
            "El detalle completo esta en el artefacto `reportes-de-prueba`._"
        )
        out.append("")

    return "\n".join(out)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    report = render(collect(root))
    sys.stdout.write(report + "\n")

    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as handle:
            handle.write(report + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
