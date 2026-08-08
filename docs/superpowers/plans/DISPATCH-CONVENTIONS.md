# DISPATCH-CONVENTIONS — reglas comunes para subagentes (msp-app-kt)

Todo subagente de esta migración (rama `feat/multimodulo-cimiento`) debe seguir esto. El despacho solo añade
lo específico de la tarea.

## Entorno / toolchain (FIJO — no cambiar)
- `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` en CADA comando gradle.
- AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24, targetSdk 35, Java 11, Compose BOM 2024.09.00.
- Variante de gate: `devlocalDebug`. Correr UN solo comando gradle a la vez (build lock).
- Paquete/applicationId `com.example.msp_app` NO se toca. Módulos nuevos bajo `com.example.msp_app.core.*` / `.feature.*`.

## Reglas de código / commits
- Código en inglés; strings de usuario en español; UI text minimalista (2-4 palabras); datos de test = nombres mexicanos.
- Commits por tarea, conventional commits, subject en español, SIN atribución de Claude / SIN `Co-Authored-By`.
- NUNCA `--no-verify`. NUNCA push. El pre-commit corre ktlint + `:build-logic` ktlint/compile + `testDevlocalDebugUnitTest` y DEBE pasar.
- Hexagonal + YAGNI: puerto/abstracción solo si hay consumidor real (≥2 impl o cruza módulo). Sin triple-map ritual.
- Tests: fakes-only (estado + recording/spy), NO MockK/Mockito NUNCA; Turbine para Flows; `kotlinx-coroutines-test`.
- **POLÍTICA DE MIGRACIÓN (instrucción del usuario 2026-08-08): AUDITAR + REESCRIBIR, no mover a ciegas.** El
  código de `:app` NO se confía: al migrar una pieza, (1) auditarla por bugs, (2) VERIFICAR el contrato de la API
  (formatos de request/response, SOBRE TODO fechas — estándar RFC3339 UTC; cruzar con el backend Go en
  `/Volumes/M2-1TB/Developer/msp-api` si aplica), (3) reescribir limpio con **tests de cobertura y robustez SUPREMA**
  (casos borde exhaustivos), (4) review. Un test verde solo cuenta si es exhaustivo Y el formato casa con el API.
- **EXCEPCIÓN dura:** el **schema de Room (v27, DDL en disco)** es un contrato de datos de producción — NO se
  reescribe (corrompería datos en teléfonos reales). Se reescribe la LÓGICA alrededor (queries de DAO, mappers/DTOs,
  utils, outbox, sync, repos), no el schema. Fechas custom → `java.time` (desugaring on).
- Money-path (outbox/pagos/WorkManager/red): si REESCRIBES, primero caracteriza el comportamiento correcto contra el
  contrato real del API (no el comportamiento viejo si estaba mal); un cambio de comportamiento money = conscientemente
  documentado (es corregir un bug), NO accidental. Si no puedes verificar el contrato, reportar BLOCKED.
- `msp.hilt` convention plugin es para módulos NUEVOS; `:app` usa Hilt directo (KSP ya aplicado por Room).

## Contrato de reporte (IMPORTANTE para ahorrar contexto)
- IMPLEMENTADOR: escribe el reporte COMPLETO en el archivo que se te indique. Devuelve al orquestador SOLO:
  `status (DONE/DONE_WITH_CONCERNS/BLOCKED/NEEDS_CONTEXT) | commit(s) | 1 línea de build/test | concerns (≤2 líneas)`.
  NO pegar el reporte ni contenidos de archivos en la respuesta.
- REVISOR: revisá desde el diff/reporte/archivos; NO re-corras gradle salvo que un hallazgo REQUIERA ejecución
  (p.ej. probar que un gate dispara). El implementador y el pre-commit ya corrieron el gate. Si DEBÉS correr
  gradle, asumí que puede haber otro gradle corriendo en el mismo checkout (contención de daemon → fallos
  transitorios); reintentá en daemon limpio antes de reportar un fallo como defecto.
- REVISOR: escribe la revisión COMPLETA en el archivo que se te indique. Devuelve al orquestador SOLO:
  `Spec ✅/❌ | Approved/Needs work | Findings: Critical/Important como one-liners (o "none") | ⚠️ clave (≤1 línea)`.
  Máximo ~6 líneas. NO pegar diffs ni análisis largo en la respuesta; ese detalle va al archivo.
