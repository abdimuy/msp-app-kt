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

## PRÓXIMA ACCIÓN — impresión + piloto pulido en device. Sigue confirmación del usuario + prueba de campo.

**2026-08-10 (madrugada):** se adoptó impresión térmica de kollect-app (`:core:printing`, hexagonal +
DantSu ESC/POS + picker Bluetooth + última-impresora-usada) cableada al piloto de reporte de cobranza
("Imprimir" imprime directo + "Cambiar impresora" siempre disponible), dinero pasó a PESOS ENTEROS en todo el
stack nuevo, se corrigieron 6 issues encontrados por el usuario probando en un Galaxy A25 físico (insets de
status/nav bar, lista de pagos scrollable, orden-z del reveal de tema, filas enriquecidas con folio+saldo,
botones Compartir/Imprimir opacos). **HEAD `b664390e`**, rama `feat/multimodulo-cimiento`, todo commiteado,
**SIN push**. Último build `devserverDebug` instalado en el teléfono del usuario. Detalle completo en
`NIGHT-REPORT.md`, sección `### 2026-08-10 (madrugada) — Impresión + piloto pulido en device`.

**LO QUE SIGUE — es TU decisión / tus manos:**
1. **Confirmar visualmente en el teléfono** los cuatro fixes de device del último commit (`b664390e`) — el
   app-lock de huella bloqueó screenshots automatizados, así que no hay evidencia visual automatizada.
2. **Prueba de campo con impresora térmica real** — Bluetooth no se puede probar en emulador/gate; es el
   release-gate de impresión.
3. **Backlog de pulido** (no bloqueante): test automatizado de scroll Compose para la lista de 23 filas; el
   saldo mostrado es el saldo ACTUAL de la venta, no el histórico-al-momento-del-pago (confirmar intención);
   más pulido de fila/diseño si aplica.
4. **Decisiones parqueadas de antes siguen abiertas** (sección B de `NIGHT-REPORT.md`): contraste del azul de
   marca (3 pares bajo AA), pagos con `forma_cobro_id` NULL en los totales (se dejan = legacy), fórmula de
   projection del hero (hoy `null`), glyph del theme toggle.
5. **Revisión humana** de la rama completa + **decisión de merge** de `feat/multimodulo-cimiento` cuando lo
   anterior esté resuelto.

No hay siguiente plan de ejecución encolado — impresión + pulido de device fue trabajo adicional sobre el
charter ya cerrado (Planes 0-5).

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
