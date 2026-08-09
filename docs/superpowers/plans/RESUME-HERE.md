# RESUME-HERE — reanudar la migración en una sesión nueva

> Punto de entrada para una sesión FRESCA (tras reset/compactación de contexto). Léelo primero.
> Rama: `feat/multimodulo-cimiento` (repo `msp-app-kt`). SIN push. Un commit por tarea = el registro duro.

## Cómo reanudar (método)
1. Skill: `superpowers:subagent-driven-development`. Esta sesión = ORQUESTADOR; todo (implementar/revisar/planear)
   va a SUBAGENTES para no gastar contexto. Reglas de despacho compartidas: `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`
   (léelo — trae toolchain, gate, contrato de salida CORTA de subagentes, política de migración).
2. Estado fino por plan en los ledgers `.superpowers/sdd/<plan-basename>/progress.md` (gitignored, en disco).
   Reanudá en la primera `## Task N` SIN línea `Task N: complete`. Scripts SDD en el dir del skill:
   `sdd-workspace <planfile>`, `task-brief <planfile> N`, `review-package <planfile> BASE HEAD`.
3. Progreso legible + decisiones: `docs/superpowers/plans/NIGHT-REPORT.md`.
4. Verificá HEAD/rama antes de nada: `git rev-parse --short HEAD`, `git branch --show-current`.

## Orden de ejecución (charter nocturno 2026-08-09)
`Plan 0 ✅ → Plan 1 ✅ → (detekt estricto ✅) → Plan fechas ✅ CERRADO CONFORME (11/11) → Plan 2 ✅ CERRADO CONFORME (10/10, HEAD 79e3d46) →
Deuda money-robustez ✅ CERRADO CONFORME (6/6, HEAD e5fbc4b) → Plan 3 (DS) ✅ CERRADO CONFORME (11/11, HEAD 4c0b51d) →
Plan 4 (telemetría/network) ✅ CERRADO CONFORME (11/11, HEAD 013f422) →
**Plan 5 (piloto reporte cobranza, SIGUIENTE)**`.
Planes 3 y 4 CERRADOS ⇒ dependencias de Plan 5 satisfechas (`:core:designsystem` + `:core:network` + `:core:telemetry`).
Planes 3/4/5 YA PLANEADOS (archivos `2026-08-09-plan3/4/5-*.md`). Deuda money = `2026-08-09-deuda-money-robustez.md` (4 tareas, char-test + 2 revisores).

## Planes (archivos con `## Task N`)
- `docs/superpowers/plans/2026-08-07-plan-maestro-multimodulo.md` — maestro (constraints + checklist).
- `2026-08-07-plan0-preparacion.md` — ✅ CERRADO (catálogo + build-logic).
- `2026-08-07-plan1-cimiento.md` — ✅ CERRADO CONFORME (Hilt + :core:common + :core:testing).
- `2026-08-07-plan2-database.md` — EN CURSO. T1-4 ✅; **T5-9 pendientes** (mover RoomTestBase; datasources inyectados
  lotes 6-8 = AUDIT+REWRITE; cierre 9 con e2e dispositivo + wire :core:database ktlint/test en prePushCheck).
- `2026-08-08-fechas-appointime-migracion.md` — ✅ CERRADO CONFORME (11/11, HEAD `55f905f`).
- `2026-08-09-plan3-designsystem.md` — ✅ CERRADO CONFORME (11/11, HEAD `4c0b51d`, commit range `cd1d141..4c0b51d`).
  ⚠️ Contraste de 3 pares de color parqueado para decisión del usuario en Plan 5 (ver `NIGHT-REPORT.md`).
- `2026-08-09-plan4-telemetry-network.md` — ✅ CERRADO CONFORME (11/11, HEAD `013f422`, commit range `8f77135..013f422`).
  8 tareas (`:core:telemetry` + `:core:network`).
- `2026-08-09-plan5-collection-report.md` — **SIGUIENTE**. Piloto reporte de cobranza, 11 tareas; gateado en
  Planes 3+4 (ambos CERRADOS).

## Decisiones/políticas VIGENTES (no re-litigar)
- **Política de migración (usuario): AUDITAR+REESCRIBIR el código viejo con tests de robustez SUPREMA + verificar
  contrato del API (formatos, fechas RFC3339 UTC; backend Go en `/Volumes/M2-1TB/Developer/msp-api`).** NO mover a ciegas.
  EXCEPCIÓN dura: **schema Room v27 = INMUTABLE** (datos en prod); solo se reescribe la lógica alrededor.
- **Cambios money-output de fechas AUTORIZADOS** por el usuario (día de pago/rangos/settlement/gate horas): aplicar con
  test de caracterización old→new cada uno.
- **detekt ESTRICTO** solo en módulos nuevos (`:core:*`,`:feature:*`,`:build-tools`) vía convention plugin `msp.detekt`;
  `:app` FUERA (sin baseline). Cablear módulos nuevos en `prePushCheck`.
- **Kill-switch baseURL:** NO `@Singleton` en nada que sostenga un API service de `ApiProvider.create()` (congela y
  rompe el kill-switch). Cadena de red/repo sin scope. `ConnectivityMonitor` sí `@Singleton`.
- **AppTime/AppClock** (java.time, zona negocio `America/Mexico_City`) vive en `:core:common.time`; es la ÚNICA fuente
  de fechas. `FakeClock` en `:core:testing`. `DateUtils` viejo se borra en Task 13 de fechas.
- **Review scaling:** tareas behavior-neutral/tests-only/display → 1 revisor adversarial; money-output/logic → 2 revisores.
- **Plan 5 (piloto) fidelidad visual:** la pantalla del reporte debe verse EXACTAMENTE como
  `docs/design/reporte-cobranza-mockup.html` — verificar COMO IMAGEN (render mockup vs PNG Roborazzi, comparar), con un
  revisor de fidelidad visual dedicado en el cierre. Ver memoria `feedback_reporte_cobranza_fidelidad_mockup`.

## Hechos/estado clave
- **Room v27** movido a `:core:database` byte-idéntico (schema JSON exportado y commiteado); `getInstance` es el bridge
  (single-source `AppDatabase.buildDatabase`); money-path e2e **10/10 verde** post-hoist.
- **e2e money-path arreglado** (era pre-existente en `main`): la causa era bloqueo cleartext a localhost del MockWebServer;
  fix TEST-only `app/src/debug/res/xml/network_security_config.xml`. `connectedDevlocalDebugAndroidTest` = 10/10.
- **Emulador:** UNO headless (`Pixel_9_Pro`), nunca dos; apagar al terminar. Solo para e2e dispositivo.
- **Duplicación pendiente ya resuelta por Task 3 fechas** (DateUtils dup en `:core:database` → usar `:core:common` AppTime).

## Memorias relevantes (en el dir de memoria del proyecto msp-api)
`feedback_msp_app_kt_audit_rewrite_supreme_tests`, `reference_msp_app_kt_hilt_baseurl_killswitch`,
`feedback_reporte_cobranza_fidelidad_mockup`, `feedback_delegate_to_sonnet`, `feedback_no_claude_attribution`.

## PRÓXIMA ACCIÓN — TODO el charter nocturno CERRADO CONFORME. Sigue revisión humana.

**TODO el charter nocturno del 2026-08-09 está CERRADO CONFORME:** Plan 2 (`:core:database`, 10/10) + deuda
money-robustez (6/6) + Plan 3 (`:core:designsystem`, 11/11) + Plan 4 (`:core:telemetry`+`:core:network`, 11/11)
+ **Plan 5 (`:feature:collectionReport`, EL PILOTO, 12/12)**. Cinco auditorías de conformidad opus, las cinco
CONFORME. **HEAD `ac695185`.** Rama `feat/multimodulo-cimiento`, todo commiteado, **SIN push** (origin no tiene
la rama).

Plan 5 (el piloto) cerró con: dominio Money/rangos/agregados, `@HiltViewModel`, UI completa desde `Msp*`, ruta
`"daily_reports"` conservada + `WeeklyReport` absorbido, matriz de 56 goldens, **fidelidad visual HIGH** contra
el mockup, **device smoke 10/10**, y un **crash de producción real cazado y arreglado** (pantalla sin
`MspTheme`/`ThemeRevealRoot`). Detalle completo (todos los planes + parqueados + release-gates + deuda menor)
en `NIGHT-REPORT.md`, sección `## 🌙 CIERRE DE LA NOCHE — 5 planes CONFORME`.

**LO QUE SIGUE no es más ejecución de plan — es TU decisión:**
1. **Revisión humana** de la rama completa antes de considerar merge.
2. **Parqueados de producto/gusto** (sección B de `NIGHT-REPORT.md`): centavos vs. pesos enteros en el reporte,
   contraste del azul de marca (3 pares bajo AA), fórmula de projection del hero (hoy `null`), si pagos con
   `forma_cobro_id` NULL deben contar en el total, glyph del theme toggle.
3. **Release-gates manuales** (sección C): impresión térmica Bluetooth con hardware real, smoke de campo del
   orden de eventos de garantías `FECHA_EVENTO` (Z-UTC a Node) antes de desplegar garantías.
4. **Decisión de merge** de `feat/multimodulo-cimiento` cuando lo anterior esté resuelto.

No hay siguiente plan encolado — Planes 0 a 5 son el charter completo.

---
### (histórico) checkpoint previo
**Plan fechas CERRADO CONFORME** (HEAD `55f905f`; auditoría opus 11/11 PASS, `prePushCheck` verde, Room v27 intacto,
`DateUtils` borrado + guardrail `checkNoLegacyDateApi` en `prePushCheck`). Ledger + `CONFORMANCE-AUDIT.md` en
`.superpowers/sdd/2026-08-08-fechas-appointime-migracion/`.
2 DIFERIDOS documentados: (a) allowlist forbidden-API residual (~14 archivos) = plan de limpieza futuro; (b) **RELEASE-GATE
manual**: garantías `FECHA_EVENTO` cambió a wire Z-UTC hacia el backend Node — smoke test de campo del orden de eventos antes de desplegar garantías.

Reanudar en **Plan 2 (`2026-08-07-plan2-database.md`) Task 5 — mover `RoomTestBase` a `:core:testing`** (behavior-neutral,
1 revisor adversarial). Luego T6-8 (datasources inyectados por lotes money→ventas→catálogo, AUDIT+REWRITE con tests supremos),
T9 (cierre; NOTA: `:core:database` ktlint/test YA cableado a `prePushCheck` por fechas T13; queda documentar `getInstance`
residual + smoke e2e dispositivo). Después Planes 3-5. Ledger de Plan 2 (`.superpowers/sdd/2026-08-07-plan2-database/`) tiene T1-4 ✅.
FOLLOW-UP Plan 2 pendiente: dedup de Constants payment-form IDs → `:core:common` (el dup de `DateUtils` en `:core:database` YA se borró en fechas T3).
