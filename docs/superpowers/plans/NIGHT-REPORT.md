# NIGHT-REPORT — Migración multi-módulo + Hilt + Reporte de cobranza

> Reporte de progreso para leer en la mañana. Se actualiza continuamente.
> Rama: `feat/multimodulo-cimiento` (repo `msp-app-kt`). Sin push.

## Estado global
- **Inicio:** 2026-08-07
- **Plan actual:** Plan 2 — `:core:database` (hoist Room + migración). Plan 1 CERRADO.
- **Plan 0:** ✅ CERRADO CONFORME (auditoría 7/7) en `c52590c`
- **Plan 1:** ✅ CERRADO en `8eeb984` (13 commits). Gate de código CONFORME (auditoría 8/8, `prePushCheck` verde).
  Smoke emulador: app bootea limpia (sin crash Hilt/WM, `ClienteSyncWorker` corre). Los 5 fallos de
  `connectedDevlocalDebugAndroidTest` son **PRE-EXISTENTES** (fallan IDÉNTICO en `main` e48f4bb; nunca se corría
  ese suite) → NO son regresión de Plan 1. Ver "⚠️ Deuda pre-existente" abajo.

## ⚠️ Deuda pre-existente descubierta (money-path, NO introducida por esta migración)
El suite instrumentado de worker-e2e (`PendingPaymentsWorkerE2ETest`, `PendingVisitsWorkerE2ETest`,
`CobranzaDurableQueueE2ETest` — pipeline de subida de pagos/visitas) tiene **5 tests rotos que también fallan en
`main`** (`e48f4bb`): los workers quedan atascados en ENQUEUED en el harness de test (nunca llegan a SUCCEEDED).
Nunca se corrían en un gate, por eso nadie lo notó. **Impacto:** el money-path NO tiene hoy una red e2e de
dispositivo funcional. Diagnóstico: es problema del HARNESS de test (producción corre bien por logcat), probable
interacción `WorkManagerTestInitHelper` vs init on-demand. Detalle en `.superpowers/sdd/2026-08-07-plan1-cimiento/`
(`e2e-worker-diagnosis.md` + `e2e-baseline-main.md`).
**✅ RESUELTO (commit `27cc246`):** la causa REAL no era WorkManager — era el bloqueo de Android a HTTP CLEARTEXT
hacia `localhost` (el MockWebServer del test) → el worker lo veía como fallo de red → retry → ENQUEUED. Fix
TEST-only: `app/src/debug/res/xml/network_security_config.xml` (superset de main + localhost); prod/release
intactos. **connectedDevlocalDebugAndroidTest = 10/10 VERDE.** Revisado por 2. El money-path ya tiene red e2e.
- **App corre idéntica:** sí (aún sin cambios de comportamiento)

## Setup / pre-flight
- Rama `feat/multimodulo-cimiento` creada desde `main` (e48f4bb), árbol limpio.
- Entorno verificado: JBR 21 (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`),
  Gradle wrapper 8.11.1, `MAPS_API_KEY` presente en `local.properties`, `:app` único módulo con
  version catalog (`libs`).
- Recon ya existente reutilizada: `.superpowers/research/{current-architecture,kollect-app-designsystem,
  observability-self-hosted}.md`.
- **Escaneo de conflictos del plan (pre-flight SDD):** sin contradicciones internas que bloqueen.
  El gate de tests/cobertura/screenshot escala con lo que existe en cada plan (Plan 0 solo exige
  `./gradlew help` + compilar `:app`). Decisión de trabajar in-place en la rama (no worktree) por
  `local.properties`/`keystore.properties`/caché de gradle gitignored y por la naturaleza strangler-fig
  (mismo repo) que el plan pide explícitamente.

## Decisiones tomadas
- (setup) Trabajo in-place en la rama, no en worktree: gradle necesita `local.properties`+`keystore.properties`
  (gitignored) y el plan manda "mismo repo".
- (Plan 0) Cubrir `build-logic` en el gate de pre-commit (ktlint + compile), ya que `pluginManagement.includeBuild`
  no lo alcanza con `ktlintCheck`/`help` a secas. (fix round Tarea 2)
- (Plan 1 · decisión 1) `RoomTestBase`/`TestDataFactory` NO se mueven a `:core:testing` en Plan 1 — referencian
  tipos de `:app` (`AppDatabase`, entities) que no salen hasta `:core:database` (Plan 2); moverlos ahora crearía
  ciclo. Solo se mueven `MainDispatcherRule` y `RobolectricTestBase` (framework-only). RoomTestBase/TestDataFactory → Plan 2.
- (Plan 1 · decisión 2) Cobertura Kover: el "~80% app" no aplica al `:app` legacy (sin tests). El gate ~90% se
  limita al dominio de `:core:common`; nada de gate de cobertura repo-wide sobre `:app`. App coverage = por feature migrada.
- (Plan 1 · decisión 3) Anti-`Double`: implementar guard funcional ya (regla detekt custom si detekt 1.23.7 es
  compatible con Kotlin 2.0.21; si no, task Gradle grep-based scoped a paquetes de dominio/money). Enforcement pleno
  sobre VOs de dinero = Plan 5 (ahí aparece el primer código de dinero).
- (Plan 1 · decisión 4) `HiltWorkerFactory` + `Configuration.Provider` en `MspApplication` y remover el
  `WorkManagerInitializer` por defecto EN LA MISMA tarea (si no, doble init). NO convertir los 7 workers
  (fallback por reflexión → comportamiento idéntico).
- (Plan 1 · decisión 5) `:app` recibe Hilt DIRECTO en `app/build.gradle.kts` (plugin Hilt + `hilt-android` +
  `ksp(hilt-compiler)` + `hilt-work` + `ksp(androidx-hilt-compiler)`), reusando el KSP ya aplicado (Room).
  NO aplicar el convention plugin `msp.hilt` a `:app` (evita doble-apply de KSP). `msp.hilt` queda para
  módulos nuevos `:core:*`/`:feature:*`. Se inyecta en el dispatch de la Tarea 5.
- (Plan 1 · decisión 6) **[REVISAR EN LA MAÑANA]** El brief de la Tarea 7 pedía `@Singleton` Y "preservar el
  rebuild de baseURL por Firestore" — están en conflicto: `@Singleton` congela el proxy del API service por
  TODA la vida del proceso, así el kill-switch de baseURL no alcanzaría a un consumidor inyectado (revisor
  adversarial). Resuelto a favor del kill-switch (requisito de seguridad de app de dinero): el provision del
  API service queda SIN scope (re-resuelve `ApiProvider.create(...)` por inyección, como el patrón legacy de
  `val` en ViewModel); `ConnectivityMonitor` sí `@Singleton` (singleton real, sin baseURL). Si algún feature
  necesita reactividad por-llamada, inyectar el factory `ApiProvider` o `Provider<T>`. Revierte trivial si no
  estás de acuerdo.

## Decisiones Plan 2 (adjudicadas, a foldear en el archivo de Plan 2 antes de ejecutar)
- (Plan 2 · dec 7) NO reconstruir schema JSONs históricos v20-v26 (exportSchema estaba off; prod ya en v27, bajo
  valor). Money-safety = `PaymentSurvivalMigrationTest` (filas no-subidas `GUARDADO_EN_MICROSIP=0` sobreviven) +
  `MigrationSmokeTest` (seed execSQL, corre las 7 migraciones). exportSchema ON de v27 en adelante.
- (Plan 2 · dec 8) `getInstance` NO se elimina del todo en Plan 2 (strangler-fig). `DatabaseModule.provideAppDatabase`
  DELEGA a `AppDatabase.getInstance` (1 sola conexión a `msp_db`, `setInstanceForTesting` sigue alcanzando el grafo
  Hilt → el e2e de pagos sigue verde). ~7 callers legacy residuales = deuda trackeada para sus planes futuros.
- (Plan 2 · dec 9) Tests de migración en Robolectric/JVM; device solo para el e2e de cierre.

## ⚠️ Requisito de fidelidad visual (Plan 5 — piloto reporte de cobranza)
La pantalla `:feature:collectionReport` debe verse **EXACTAMENTE** como `docs/design/reporte-cobranza-mockup.html`.
Verificación **como imagen** (no leyendo código): renderizar el mockup a imagen + capturar el render Compose real
(PNGs Roborazzi, Tier1/2 × 1.0/1.3/2.0, light+dark) y compararlos lado a lado. Si falta cualquier elemento del
mockup, **agregarlo**: prioridad = completo, sin que falten cosas (aunque el pulido final no sea 100% idéntico).
Plan 5 llevará un **revisor de fidelidad visual dedicado** y esa comparación es parte del gate de cierre del plan.

## Bitácora por plan/tarea
### Plan 0 — Preparación
- **Tarea 1 — Version catalog completo:** ✅ completa (commit `78033f9`). Todas las deps hardcodeadas
  movidas al catálogo + tooling futuro añadido (Hilt 2.52, AndroidX Hilt 1.2.0, Turbine 1.1.0,
  Roborazzi 1.26.0, Kover 0.8.3, Detekt 1.23.7), versiones resueltas idénticas (diff byte-idéntico).
  Revisada limpia por 2 revisores. Minors diferidos: `composeFoundation` version.ref compartido por
  foundation+material-icons-core; alias `hilt-android`/`roborazzi` reusado en [libraries] y [plugins] (válido).
- **Tarea 2 — build-logic + convention plugins:** ✅ completa (commit `2814cfb` + fix `c52590c`). 5 plugins:
  `msp.android.library/compose/hilt/test/kover`. Fix round: el gate de pre-commit ahora cubre `build-logic`
  (`:build-logic:ktlintCheck` + `:build-logic:compileKotlin`). Revisada por 2 revisores + re-review scoped.
  **Notas para Plan 1+:** (a) el `ktlintCheck` raíz del pre-commit NO cascada a `build-logic` (incluido vía
  `pluginManagement.includeBuild`); editar `scripts/pre-commit` para lintar `:build-logic:ktlintCheck` cuando
  se toque build-logic. (b) `msp.test` requiere que el proyecto ya tenga extensión Android (aplicar después
  de `msp.android.library`).

### Plan 1 — Cimiento (10 tareas)
- **Tarea 1 — `:core:common` esqueleto:** ✅ (commit `b24be09`). Módulo verde + test no-vacuo. Incluyó fix
  repo-wide en `TestConventionPlugin` (AGP 8.10.1: `getByType(CommonExtension::class.java)` en vez del
  reified `configure<CommonExtension<...>>`). Revisado por 2 (mutation-test confirmó test real).
- **Tarea 2 — `:core:testing`:** ✅ (commit `a63ade9`). Movidos `MainDispatcherRule`/`RobolectricTestBase`
  (framework-only) a `src/main` de `:core:testing`, deps de test expuestas como `api`; `:app` re-cableado
  (`testImplementation project(:core:testing)`); 768 tests de `:app` verdes. `RoomTestBase`/`TestDataFactory`
  se quedan en `:app` (→ Plan 2). Revisado por 2 (dep 1-dir, move real).
- **Tarea 3 — promover outbox (pendingwork domain) a `:core:common`:** ✅ (commit `634f376`). Move byte-fiel
  de 39 archivos (solo package/imports); `SyncContext`/`SyncResult`/7 ports/`SyncAllPendingWorkUseCase` →
  `:core:common`; adapters WorkManager quedan en `:app`. Money-path intacto (verificado a nivel byte por
  revisor adversarial). 768 tests `:app` verdes. Kover routeado a Task 4.
- **Tarea 4 — endurecer outbox + sync-health + kover:** ✅ (commits `b1e124e` + fix `c70fdb6`). Dominio puro
  `SyncHealth/SyncStatus/SyncHealthSource/SyncHealthReducer` + fakes recording en `:core:testing` +
  `OutboxAckInvariantTest`. Kover `:core:common` subido a `minBound(90)` sobre TODO el dominio
  `com.example.msp_app.core.common.**` (medido 100%, BuildConfig excluido; enforce verificado). Money-path
  intacto. Revisado por 2 + re-review.
- **Tarea 5 — Hilt en `:app` (@HiltAndroidApp + @AndroidEntryPoint):** ✅ (commit `697be1d`). Hilt DIRECTO,
  sin `msp.hilt`, sin double-KSP; init byte-idéntico; 755 tests (=768-13 movidos). Smoke runtime → cierre Plan 1.
- **Tarea 6 — HiltWorkerFactory + quitar WorkManagerInitializer:** ✅ (commit `462d29a`). `Configuration.Provider`
  + `HiltWorkerFactory`; manifest MERGED verificado sin `WorkManagerInitializer` (otros initializers sobreviven);
  7 workers sin convertir (fallback reflexivo, bytecode-verificado). 759 tests. Boot real → emulador cierre.
- **Tarea 7 — módulos Hilt red/conectividad:** ✅ (commits `157061e` + fix `bbd66f8`). `NetworkModule`/`ConnectivityModule`.
  Fix: API service SIN scope para preservar kill-switch baseURL (decisión 6); `ConnectivityMonitor` @Singleton. 762 tests.
- **Tarea 8 — Warehouse a Hilt (primer feature):** ✅ (commit `9e554fe` + fix `76a402b`). `@HiltViewModel` +
  `hiltViewModel()` en 4 pantallas de ventas; scoping per-screen = igual que antes (sin regresión de carrito).
  **CRITICAL cazado por 2 revisores:** `WarehouseRepository @Singleton` congelaba el API y rompía el kill-switch;
  fix = quitar `@Singleton` (cadena unscoped) + guard test. Regla persistida en memoria para futuras migraciones.
- **Tarea 9 — regla anti-`Double` para dinero (detekt):** ✅ (commit `c2277d3`). Regla custom `NoDoubleForMoney`
  en `:build-tools:detekt-rules` (ServiceLoader), armada en `:core:common`; probe verificado (dispara/limpia),
  7/7 tests. Enforcement pleno de VOs de dinero → Plan 5.
- **Tarea 10 — hook pre-push + gate agregado:** ✅ (commit `8eeb984`). `prePushCheck` (ktlint todos + tests todos
  + `:core:common:detekt` + `koverVerify` + assemble) + hook `scripts/pre-push` con teeth (broken→exit1). pre-commit intacto.
- **Cierre Plan 1:** ✅ CONFORME (auditoría 8/8). Falta solo el smoke en emulador (T5-8 runtime): boot sin
  double-init, workers (cliente/pagos/visitas/ventas) enqueue+run, login/Home/Warehouse render.

## Bloqueos / muros de entorno
- (ninguno por ahora)

## Qué queda
- Plan 0 → Plan 5 según secuencia del plan maestro.

## 2026-08-08 — Plan FECHAS/AppTime CERRADO CONFORME (14 tareas)
Migración completa de fechas a `AppTime`/`AppClock` (zona negocio `America/Mexico_City`, java.time). HEAD `55f905f`.
Auditoría de conformidad (opus): **11/11 PASS**, `prePushCheck` verde en daemon limpio, Room v27 intacto, 27 commits attrib correcta / no push.
- **T4** SettlementCalculator zona negocio (char-test + mutation-kill del default). **T5** rangos reporte medio-abiertos `[desde,hasta)`
  (adversarial cazó un Critical: fecha por defecto en zona del dispositivo). **T5b** (insertada) truncar `FECHA_HORA_PAGO` a segundos
  + DAOs `>=AND<` (Robolectric SQLite; adversarial cazó regresión de undercount en reporte semanal). **T6** escritura vía AppClock.
  **T7** gate horario worker en CDMX. **T8/T9** display pagos/ventas/garantías/visitas. **T10** timestamps transfers (formato naive
  preservado ante backend Node no verificable). **T11** `FECHA_EVENTO`→Z-UTC (verifiqué el contrato Node real: `Date.parse` acepta,
  guarda raw, sin shift). **T12** SimpleDateFormat/#8 locale (repro Thai calendar). **T12b** (insertada) day-grouping Home zona negocio
  (cierra deuda T3) + últimas llamadas DateUtils. **T13** borrar `DateUtils` + guardrail `checkNoLegacyDateApi` (content-level para money VMs).
- **Método**: orquestación SDD, revisores money atraparon 2 bugs reales que el implementador no vio (Critical T5, regresión T5b).
- **DIFERIDOS**: (a) allowlist forbidden-API residual (~14 archivos legacy) = plan de limpieza futuro; (b) **RELEASE-GATE manual**:
  garantías `FECHA_EVENTO` ahora viaja Z-UTC al backend Node — smoke test de campo del orden de eventos antes de desplegar garantías.
- **SIGUIENTE**: Plan 2 (database) T5-9.
