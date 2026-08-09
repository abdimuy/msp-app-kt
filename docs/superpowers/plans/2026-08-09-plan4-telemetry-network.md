# Plan 4 — `:core:telemetry` + `:core:network`

Parte del plan maestro `2026-08-07-plan-maestro-multimodulo.md` (spec fuente:
`docs/superpowers/specs/2026-08-07-migracion-arquitectura-msp-app-kt.md`, §6-§7, §12; recon:
`.superpowers/research/{current-architecture,observability-self-hosted}.md`). Continúa donde terminaron
**Plan 1** (`build-logic` con convention plugins `msp.android.library/compose/hilt/test/kover/detekt`,
`:core:common`, `:core:testing`, Hilt encendido en `:app`, gate `prePushCheck`, lint anti-`Double`),
**Plan 2** (`:core:database` hoisteada) y **Plan 3** (`:core:designsystem`). Este plan levanta dos módulos de
cimiento que el piloto (Plan 5) consume:

- **`:core:telemetry`** — puerto `Telemetry` + **cola durable en Room** (store PROPIO, no `msp_db`) +
  `Modifier.trackClick` + `ScreenScope`/`LocalScreenName` + adapter **stub** (el sink real = spec de
  observabilidad aparte). **Lo testeado a fondo = la cola durable** (encola sobrevive, drena en orden, nunca
  tira errores).
- **`:core:network`** — base Retrofit/OkHttp + interceptores (**auth bearer**, **app-version header**) +
  `NetworkConfig` **inyectado**; reubicación de `ConnectivityMonitor` y del wiring de red hoy en `:app`,
  **preservando el kill-switch de baseURL por Firestore**.

**Done del plan:** cola de telemetría durable testeada; red inyectable con `NetworkConfig`; **sin regresión**
(la app corre idéntica); kill-switch intacto; ambos módulos en `prePushCheck`.

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`): implementador TDD →
> gate real → revisores (money/kill-switch → 2, uno adversarial; behavior-neutral/tests-only → 1) → fix-loop,
> una tarea a la vez. Reglas comunes de despacho: `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`.

---

## Global Constraints (vinculan a TODA tarea de este plan)

- **Toolchain FIJA, no cambiar:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11 (`jvmTarget=11`, desugaring on), Compose BOM 2024.09.00, Gradle wrapper 8.11.1.
- **`JAVA_HOME` en CADA comando gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  Correr **UN** solo comando gradle a la vez (build lock).
- **Variante de gate:** `devlocalDebug` (unit de `:app` = `testDevlocalDebugUnitTest`). Los `:core:*` son
  librerías sin flavors → su unit test es `:core:<x>:testDebugUnitTest`.
- **Paquete/`applicationId` `com.example.msp_app` NO se toca** (UpdateChecker/Firestore atados al package).
  Módulos nuevos: namespace **`com.example.msp_app.core.telemetry`** y **`com.example.msp_app.core.network`**.
  El `applicationId` de `:app` **no** cambia.
- **KILL-SWITCH baseURL (decisión VIGENTE, no re-litigar — ver `RESUME-HERE.md`):** NUNCA `@Singleton` sobre
  nada que sostenga un API service devuelto por `ApiProvider.create(...)` (congelaría el proxy para todo el
  proceso y el flip de baseURL por Firestore dejaría de alcanzar a los consumidores inyectados). La cadena de
  red/repo va **sin scope**; un consumidor que necesite reactividad dentro de su propio ciclo de vida inyecta
  `Provider<T>` o el propio `ApiProvider`. **`ConnectivityMonitor` SÍ es `@Singleton`** (no sostiene un
  Retrofit mutable). El listener Firestore que reconstruye el Retrofit interno de `ApiProvider` **se preserva
  intacto** — se puede mover de dónde sale el *código de construcción del cliente*, jamás desactivar el
  listener ni congelar el proxy.
- **Convention plugins para módulos NUEVOS:** `msp.android.library` (o `msp.android.compose`, que lo aplica),
  `msp.hilt` (KSP + hilt-android + `ksp(hilt-compiler)`), `msp.test`, `msp.kover`, `msp.detekt`, `alias(libs.plugins.ktlint)`.
  **detekt ESTRICTO** (ruleset completo vía `msp.detekt`) aplica a ambos módulos nuevos; `:app` sigue solo con
  ktlint. **Cablear ambos módulos en `prePushCheck`** (Task 8).
- **POLÍTICA DE MIGRACIÓN (usuario 2026-08-08): AUDITAR + REESCRIBIR, no mover a ciegas.** El código de red de
  `:app` NO se confía: al reubicarlo, (1) auditarlo por bugs, (2) **VERIFICAR el contrato del API** (headers,
  auth, formatos; fechas RFC3339 UTC; cruzar con el backend Go en `/Volumes/M2-1TB/Developer/msp-api` si algún
  request/response se toca), (3) reescribir limpio con **tests de robustez SUPREMA** (MockWebServer + fakes,
  casos borde exhaustivos), (4) review. Un test verde solo cuenta si es exhaustivo Y el formato casa con el API.
- **Tests: fakes ÚNICAMENTE** (estado + recording/spy), compartidos en `:core:testing`. **CERO MockK/Mockito.**
  + **Turbine** (Flows) + `kotlinx-coroutines-test`. Room/Compose en JVM vía **Robolectric**; red vía
  **MockWebServer** (`libs.okhttp.mockwebserver`, ya en el catálogo) o fake. **Anti-`Double` para dinero**
  (regla del gate) — no aplica a este plan salvo que se introduzca un VO monetario (no se prevé).
- **Cobertura (Kover):** dominio del módulo ~**90%** (`:core:telemetry`: taxonomía de eventos + política de
  drenado/reintento de la cola). Infra (interceptores, factory, DAO) = pragmática (cubierta por MockWebServer/
  Robolectric, sin gate estricto — como `:core:database` en Plan 2).
- **Código en inglés; strings de usuario en español**, minimalistas (2-4 palabras). **Etiquetas de telemetría
  SIEMPRE estáticas del desarrollador, JAMÁS derivadas de texto/contentDescription/PII (LFPDPPP).** Datos de
  test con nombres realistas mexicanos (ej. `"María López"`).
- **Commits por tarea**, conventional commits, subject en **español**, **SIN atribución de Claude**,
  **SIN `--no-verify`**, **sin push**. Rama: `feat/multimodulo-cimiento`.
- **YAGNI + hexagonal:** puerto/abstracción solo si cruza módulo o tiene ≥2 impl reales. Sin triple-map ritual.

### Orden y su justificación (leer antes de empezar)
Dos sub-módulos independientes; se hace **telemetría primero** (greenfield puro, sin tocar código vivo de red)
y **red después** (reubicación con riesgo de regresión + kill-switch). Dentro de telemetría: esqueleto (T1) →
puerto + taxonomía de dominio (T2) → **cola durable en Room** (T3, el corazón testeado) → helpers de captura
Compose + adapter stub (T4). Dentro de red: esqueleto + reubicar `ConnectivityMonitor` (T5, behavior-neutral) →
`NetworkConfig` inyectado + interceptores + factory reescritos con tests supremos (T6, greenfield en el módulo
nuevo, aún sin cablear a `:app`) → **refactor de `ApiProvider`/`V2ApiProvider`/`ApiProviderImages` en `:app`
para delegar en el factory preservando el kill-switch** (T7, el punto de riesgo). Cierre (T8): `prePushCheck`
con ambos módulos, adapter stub en el composition root, verificación de no-regresión, auditoría de conformidad.
Cada tarea deja el build **verde** y la app **idéntica**.

### Comando de gate (por tarea, ajustando el alcance)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew ktlintCheck
./gradlew :core:telemetry:testDebugUnitTest      # cuando exista
./gradlew :core:network:testDebugUnitTest        # cuando exista
./gradlew :core:telemetry:detekt :core:network:detekt   # módulos nuevos, detekt estricto
./gradlew testDevlocalDebugUnitTest              # :app (cuando la tarea lo toque)
./gradlew :app:assembleDevlocalDebug
```

---

## Task 1 — Crear módulo `:core:telemetry` (esqueleto verde: Compose + Room + Hilt)

**Meta:** que `:core:telemetry` exista como Android-library, aplicando los convention plugins, con Room
(`exportSchema`/`schemaLocation` para su **propio** store) y Compose (para `trackClick`/`ScreenScope` en T4) ya
configurados, compilando con una clase placeholder — **sin lógica todavía**. Aísla "¿el módulo se levanta con
Compose + Room + Hilt + KSP conviviendo?" del contenido real (T2-T4).

**Archivos a crear:**
- `settings.gradle.kts` (raíz) → añadir `include(":core:telemetry")` junto a los otros `include(...)`.
- `core/telemetry/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.compose")    // aplica msp.android.library + habilita Compose
      id("msp.hilt")               // KSP + hilt-android + ksp(hilt-compiler)
      id("msp.test")               // DESPUÉS de la library
      id("msp.kover")
      id("msp.detekt")
      alias(libs.plugins.ktlint)
  }
  android {
      namespace = "com.example.msp_app.core.telemetry"
  }
  // La cola durable de telemetría es un store Room PROPIO, independiente de msp_db.
  // Exporta su schema a un dir versionado (contrato de su DB), como :core:database.
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  dependencies {
      implementation(libs.bundles.room)            // room-runtime + room-ktx
      ksp(libs.androidx.room.compiler)
      implementation(project(":core:common"))      // AppClock/AppTime (java.time) para timestamps de eventos
      testImplementation(libs.androidx.room.testing)
      testImplementation(project(":core:testing"))
      testImplementation(libs.bundles.unit.test)   // JUnit + Turbine + coroutines-test + robolectric + mockwebserver (según bundle)
  }
  ```
- `core/telemetry/src/main/kotlin/com/example/msp_app/core/telemetry/.gitkeep` (o el placeholder del test).
- `core/telemetry/src/main/AndroidManifest.xml` — solo si el build lo pide (mínimo `<manifest/>`).

**Test primero (TDD):** un `ModuleSmokeTest` mínimo (JUnit vía `msp.test`) que arranque la toolchain de test
del módulo. Rojo→verde. El contenido real llega en T2-T4.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:telemetry:testDebugUnitTest
./gradlew :core:telemetry:detekt
./gradlew :app:assembleDevlocalDebug     # :app sigue idéntico; aún no depende de :core:telemetry
./gradlew ktlintCheck
```
Los cuatro `BUILD SUCCESSFUL`. `:app` intacto.

**Gotchas:**
- `msp.android.compose` **ya aplica** `msp.android.library` (ver `AndroidComposeConventionPlugin`): NO
  aplicar `msp.android.library` también (doble-apply). `msp.hilt` aplica KSP; Room añade solo
  `ksp(room.compiler)` como dependencia, no re-aplica el alias de KSP.
- `msp.test` va DESPUÉS de la library (toma `CommonExtension.testOptions`).
- Con `exportSchema` activo pero sin `@Database` todavía, Room no genera JSON aún — normal, no falla.
- `msp.kover` deja piso placeholder; el umbral ~90% se activa cuando entre el dominio (T2-T3), no aquí.
- Verificar que el `.gitignore` NO excluya `core/telemetry/schemas/`.

**Commit:** `feat(core-telemetry): crear modulo base :core:telemetry con Compose, Room y schema export`

---

## Task 2 — Puerto `Telemetry` + taxonomía de eventos (dominio, sin PII)

**Meta:** definir el **contrato** de observabilidad que cada módulo consume: el puerto `Telemetry` y el modelo
de dominio de eventos (VOs), con la disciplina **anti-PII** horneada. Es dominio puro (sin Android, sin Room,
sin red): testeable en JVM y con cobertura alta. Un **fake** `RecordingTelemetry` va a `:core:testing` para que
el resto de módulos afirmen "se emitió el evento X una vez".

**Archivos a crear (en `:core:telemetry`, package `com.example.msp_app.core.telemetry`):**
- `Telemetry.kt` — el puerto (interface). Taxonomía alineada al spec §7 (`screenView` / `tap` / `event` /
  `error`):
  ```kotlin
  interface Telemetry {
      fun screenView(screen: String)
      fun tap(screen: String, element: String)
      fun event(name: String, props: Map<String, String> = emptyMap())
      fun error(code: String, message: String, props: Map<String, String> = emptyMap())
  }
  ```
  (Los nombres/props son **estáticos del desarrollador**; el KDoc debe advertir explícitamente: prohibido pasar
  texto de usuario, contentDescription, nombres de cliente, montos exactos o cualquier PII — LFPDPPP.)
- `TelemetryEvent.kt` — VO inmutable del evento encolado (lo que la cola persiste): `type` (enum
  `SCREEN_VIEW/TAP/EVENT/ERROR`), `name`, `props: Map<String,String>`, `occurredAt: Instant` (de
  `AppClock` de `:core:common`, java.time — NUNCA `System.currentTimeMillis()` crudo ni `Date`), y campos de
  contexto que el adapter rellena (`appVersion`, `screen`, `sessionId` — pueden ir en `props` para YAGNI, o
  como campos; decidir en implementación, documentando). Sin `Double`.
- **VALIDACIÓN anti-PII (dominio):** una función/VO que **rechace o sanee** props con claves/valores sospechosos
  no es factible genéricamente, pero SÍ se puede: (a) exigir `name`/`element`/`code` no vacíos y de un alfabeto
  estático (`^[a-z0-9_.]+$`), (b) documentar en KDoc la regla. Implementar (a) como invariante del VO
  (lanzar/`require` sobre nombres no conformes) — esto es dominio testeable.

**Archivos a crear (en `:core:testing`):**
- `RecordingTelemetry.kt` — fake in-memory que implementa `Telemetry` y **graba** cada llamada en una lista
  consultable (`recorded: List<TelemetryEvent>`), para aserciones de interacción en features. `:core:testing`
  añade `api(project(":core:telemetry"))` si hace falta el tipo (verificar que no introduce ciclo:
  `:core:telemetry` main NO depende de `:core:testing`).

**Test primero (TDD):** `TelemetryEventTest` (invariantes del VO: nombre válido/ inválido, props inmutables,
timestamp desde un `FakeClock`), `RecordingTelemetryTest` (graba en orden, cuenta llamadas, distingue tipos).
Rojo→verde. Cobertura del dominio ~90% (activar el piso Kover del módulo aquí).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:telemetry:testDebugUnitTest
./gradlew :core:testing:testDebugUnitTest
./gradlew :core:telemetry:koverVerify
./gradlew :core:telemetry:detekt
./gradlew ktlintCheck
```

**Gotchas:**
- Usar `AppClock`/`AppTime` de `:core:common` (java.time, zona negocio) para timestamps — es la ÚNICA fuente de
  fechas (decisión vigente); `FakeClock` de `:core:testing` en los tests. NO `Date`/`SimpleDateFormat`/
  `System.currentTimeMillis` directo (el guard `checkNoLegacyDateApi` es `:app`-scoped, pero la disciplina
  aplica a todo módulo nuevo).
- YAGNI: NO agregar `performance`/`lifecycle`/`funnel` como tipos ahora (el spec §7.3 los menciona como roadmap
  de analítica). Los 4 tipos base bastan para el piloto; se extienden cuando un consumidor real los pida.

**Commit:** `feat(core-telemetry): puerto Telemetry + taxonomia de eventos sin PII + fake grabador`

---

## Task 3 — Cola durable en Room (store PROPIO) — enqueue sobrevive, drena en orden, nunca tira errores

**Meta:** **el corazón del módulo** (spec §7.1). Una **cola offline durable en Room**, store **PROPIO** e
independiente de `msp_db` (la regla dura: **el schema v27 de `:core:database` es INMUTABLE**; telemetría NO lo
toca — tiene su propia `TelemetryDatabase` v1). Espejo del outbox de pagos endurecido (`:core:common`
`sync/pendingwork` + `OutboxAckInvariantTest`): disco-primero, ack-based, **nunca se descarta un error**. Es lo
que este plan existe para plantar; se testea a fondo.

**Archivos a crear (en `:core:telemetry`, package `com.example.msp_app.core.telemetry.queue`):**
- `TelemetryEventEntity.kt` — `@Entity(tableName = "telemetry_events")`: `id` (String UUID, generado en Kotlin
  vía `UUID.randomUUID()` — NO autogen de Room), `type`, `name`, `propsJson` (serializado), `occurredAt` (epoch
  millis o ISO string desde `AppClock`), `state` (`PENDING/UPLOADING/SENT/FAILED`), `attemptCount`,
  `lastAttemptAt` (nullable), `createdAt`. Índice por `(state, createdAt)` para drenar FIFO.
- `TelemetryEventDao.kt` — `@Dao`: `insert(event)` (síncrono/suspend), `nextBatch(limit): List` (orden FIFO por
  `createdAt`, estados drenables), `markUploading(ids)`, `markSent(ids)`, `markFailed(ids, nextAttemptAt)`,
  `pendingCount()`, `deleteSent(olderThan)` (TTL de ruido; **los errores NUNCA se borran por TTL** — ver spec
  §7.1 "nunca tirar errores"). Observabilidad: `observePendingCount(): Flow<Int>` para el widget de salud.
- `TelemetryDatabase.kt` — `@Database(entities=[TelemetryEventEntity::class], version=1, exportSchema=true)`.
  **Companion `getInstance`/`setInstanceForTesting`/`clearInstance`** con la misma semántica que `AppDatabase`
  (single-source, puente para tests) — NO abrir dos conexiones a `telemetry_db`.
- `DurableTelemetryQueue.kt` — la **política** (dominio testeable) sobre el DAO: `enqueue(event)` (insert
  síncrono disco-primero), `drain(sink, batchSize, maxAttempts)` (toma batch → marca `UPLOADING` → llama al
  sink → `SENT` si ok, `FAILED` + backoff si falla; **errores nunca se descartan**, best-effort tirar el resto
  con sampling), backoff exponencial con tope. El **sink** es un puerto (`TelemetrySink`) — su impl real es
  stub (T4).
- `DatabaseModule` de telemetría (`di/TelemetryDatabaseModule.kt`) — `@Provides @Singleton` la
  `TelemetryDatabase` (delegando en `getInstance`) + `@Provides` el DAO (sin `@Singleton`, Room lo memoiza).
  (La DB SÍ es `@Singleton`: no sostiene un Retrofit mutable — la regla del kill-switch NO aplica aquí.)

**Test primero (TDD) — robustez SUPREMA (Robolectric + room-testing, JVM):**
- `DurableTelemetryQueueTest` / `TelemetryEventDaoTest`:
  1. **Encolar sobrevive:** insertar N eventos, **cerrar y reabrir** la DB (archivo temporal), afirmar que los
     N siguen ahí en estado `PENDING` (disco-primero).
  2. **Drena en ORDEN (FIFO):** encolar e1,e2,e3; drenar con un `RecordingSink`; afirmar orden `e1,e2,e3` y que
     quedan `SENT`.
  3. **Falla de red → FAILED + reintento:** sink que lanza/`falla`; afirmar `FAILED`, `attemptCount++`,
     `nextAttemptAt` con backoff; re-drenar reintenta.
  4. **Nunca tira errores:** eventos `type=ERROR` NO se borran por `deleteSent`/TTL aunque envejezcan; solo los
     `SENT` de ruido se purgan.
  5. **Ack-based:** un evento no pasa a `SENT` hasta que el sink confirma; si el proceso "muere" a mitad
     (simular: marcar `UPLOADING` y reabrir), el evento vuelve a ser drenable (no se pierde, no se duplica-envía
     sin ack). Definir la recuperación de `UPLOADING` colgado y testearla.
  6. **Concurrencia/idempotencia:** encolar desde varios coroutines (`kotlinx-coroutines-test`) no corrompe la
     cola; `id` UUID evita duplicados.
  7. `observePendingCount()` emite correctamente vía **Turbine**.
- `SchemaIntegrityTest` (opcional, análogo a `:core:database`): `MigrationTestHelper` valida v1 contra el JSON
  exportado — guard de drift futuro. **Commitear** `core/telemetry/schemas/.../1.json`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:telemetry:testDebugUnitTest
./gradlew :core:telemetry:koverVerify
./gradlew :core:telemetry:detekt
./gradlew ktlintCheck
git status --porcelain core/telemetry/schemas   # el 1.json debe aparecer y commitearse
```

**Gotchas:**
- **NO tocar `msp_db`/v27** — store completamente separado (`telemetry_db`). Confirmar que `:core:telemetry`
  **NO** depende de `:core:database` (son DBs independientes; si el build lo insinúa, algo está mal).
- IDs y timestamps se generan en **Kotlin** (`UUID.randomUUID()`, `AppClock`), NO defaults de Room/SQLite
  (coherente con la regla "sin lógica en la DB" del lado Go, y con determinismo de tests).
- Backoff con tope; sampling de eventos ruidosos, pero **tier "nunca tirar errores"** para `type=ERROR`
  (spec §7.1). El único caso de pérdida legítima = teléfono destruido antes de subir (inherente offline-first).
- Robolectric para room-testing en JVM (como `:core:database`); NADA de device en este plan.

**Commit:** `feat(core-telemetry): cola durable en Room (store propio) con drenado ack-based y anti-perdida`

---

## Task 4 — Captura Compose (`Modifier.trackClick` + `ScreenScope`) + adapter `DurableTelemetry` con sink stub

**Meta:** completar el módulo con (a) los helpers de captura del UI-kit que hacen a cada módulo "observable por
construcción" y (b) el **adapter** `Telemetry` que escribe a la cola durable (T3) drenando hacia un **sink
stub** (el sink real GlitchTip/ingest = spec de observabilidad aparte; aquí el stub satisface el contrato). Lo
testeado a fondo ya está (la cola, T3); esto lo cablea.

**Archivos a crear (en `:core:telemetry`, package `...core.telemetry.compose` / `...adapter`):**
- `ScreenScope.kt` — `LocalScreenName: ProvidableCompositionLocal<String>` + un composable `ScreenScope(name){}`
  que provee el nombre estático de pantalla al árbol y emite `screenView(name)` una vez (vía el `Telemetry`
  del árbol, expuesto por otro `CompositionLocal` `LocalTelemetry`). El `screenView` automático desde el
  `NavHost` (spec §7) se cablea en `:app` en T8/Plan 5; aquí se provee el mecanismo.
- `TrackClick.kt` — `Modifier.trackClick(element: String)` (~10 líneas): envuelve el click emitiendo
  `tap(LocalScreenName.current, element)` con **etiqueta estática** antes de delegar. KDoc: prohibido derivar
  `element` de texto/estado/PII.
- `DurableTelemetry.kt` — impl de `Telemetry` que traduce cada llamada a un `TelemetryEvent` y hace
  `queue.enqueue(...)` (disco-primero, síncrono para `error`). El drenado corre en un scope/worker (cablear el
  disparo real en `:app` T8; aquí basta el enqueue + una función `drain()` invocable).
- `TelemetrySink.kt` — el puerto del sink. `StubTelemetrySink.kt` — impl **stub** (no-op que marca "enviado"
  localmente, o loguea) para que el drenado tenga a dónde ir sin backend. Documentar que el sink real llega en
  el spec de observabilidad (GlitchTip + endpoint Go).
- `di/TelemetryModule.kt` — `@Provides` `Telemetry` → `DurableTelemetry` y `TelemetrySink` → `StubTelemetrySink`
  (`@Singleton` permitido: no sostienen un Retrofit del kill-switch; el sink stub no hace red real todavía).

**Test primero (TDD):**
- `TrackClickTest` / `ScreenScopeTest` (Compose ui-test vía **Robolectric**): montar un árbol con
  `LocalTelemetry` = `RecordingTelemetry`; hacer click en un nodo con `Modifier.trackClick("guardar_pago")`;
  afirmar que se grabó `tap(screen, "guardar_pago")` **una vez** con etiqueta estática; `ScreenScope("reporte")`
  emite `screenView("reporte")` una vez. Afirmar que NO se filtra texto de usuario.
- `DurableTelemetryTest`: cada método del puerto encola el `TelemetryEvent` correcto (tipo/nombre/props); un
  `error(...)` encola síncrono; `drain()` con `StubTelemetrySink` marca `SENT`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:telemetry:testDebugUnitTest
./gradlew :core:telemetry:koverVerify
./gradlew :core:telemetry:detekt
./gradlew ktlintCheck
```

**Gotchas:**
- **Etiquetas SIEMPRE estáticas** (LFPDPPP): el revisor adversarial verifica que ningún helper derive labels de
  `contentDescription`/texto/estado. Este es el punto de mayor riesgo de PII.
- YAGNI: NO integrar Sentry (`io.sentry:sentry-android`) ni el listener `debug_targets` de Firestore (spec §7.2)
  en este plan — son del spec de observabilidad/backend. El stub basta para el piloto.
- El `screen_view` automático por `navController.currentBackStackEntryFlow` se cablea en `:app` (composition
  root) cuando Plan 5 monte pantallas; aquí solo se entrega `ScreenScope`/`LocalScreenName`.

**Commit:** `feat(core-telemetry): Modifier.trackClick + ScreenScope + adapter durable con sink stub`

---

## Task 5 — Crear módulo `:core:network` (esqueleto verde) + reubicar `ConnectivityMonitor`

**Meta:** que `:core:network` exista como Android-library (con Hilt/test/kover/detekt) y **mover
`ConnectivityMonitor`** — que hoy vive en el **package** `com.example.msp_app.core.network` pero dentro del
**módulo `:app`** — a su nuevo módulo. Como el package NO cambia (`com.example.msp_app.core.network`), **ningún
import de consumidor se reescribe**; solo cambia el módulo dueño. `ConnectivityModule` (el `@Provides
@Singleton` de Hilt) se **relocaliza** a `:core:network`. Comportamiento **idéntico**.

**Archivos a crear/mover:**
- `settings.gradle.kts` → añadir `include(":core:network")`.
- `core/network/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.library")
      id("msp.hilt")
      id("msp.test")
      id("msp.kover")
      id("msp.detekt")
      alias(libs.plugins.ktlint)
  }
  android { namespace = "com.example.msp_app.core.network" }
  dependencies {
      implementation(libs.retrofit)
      implementation(libs.retrofit.converter.gson)
      implementation(libs.gson)
      implementation(libs.okhttp)
      // NO firebase-auth aquí: el token bearer entra por el puerto AuthTokenProvider (T6),
      // cuya impl Firebase vive en :app — :core:network queda vendor-free y testeable con fake.
      testImplementation(libs.okhttp.mockwebserver)
      testImplementation(project(":core:testing"))
      testImplementation(libs.bundles.unit.test)
  }
  ```
- **Mover** `app/src/main/java/com/example/msp_app/core/network/ConnectivityMonitor.kt` →
  `core/network/src/main/kotlin/com/example/msp_app/core/network/ConnectivityMonitor.kt` (con `git mv` para
  preservar historial; **package idéntico**).
- **Mover** `app/src/main/java/com/example/msp_app/di/ConnectivityModule.kt` →
  `core/network/src/main/kotlin/com/example/msp_app/core/network/di/ConnectivityModule.kt` (o dejarlo en `:app`
  si mover el `@InstallIn(SingletonComponent)` complica el grafo Hilt — decidir; lo natural es que el módulo
  Hilt viva junto a lo que provee, en `:core:network`). `@Provides @Singleton` **se conserva** (regla:
  `ConnectivityMonitor` SÍ es singleton).
- `app/build.gradle.kts` → `implementation(project(":core:network"))`.
- Mover tests de `ConnectivityMonitor` (si existen en `:app`) a `:core:network`; re-apuntar nada (package
  igual). Verificar consumidores de `ConnectivityMonitor` en `:app` siguen compilando (imports intactos).

**Test primero (TDD):** correr los tests existentes de `ConnectivityMonitor` desde su nuevo hogar (o, si no
había, escribir uno mínimo Robolectric que afirme `getInstance` devuelve la misma instancia + el estado inicial
de conectividad). `:app` compila y sus tests siguen verdes (baseline antes/después).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:network:testDebugUnitTest
./gradlew :core:network:detekt
./gradlew testDevlocalDebugUnitTest        # :app: ConnectivityMonitor ahora viene del módulo
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Gotchas:**
- El package de `ConnectivityMonitor` **NO cambia** (`com.example.msp_app.core.network`), así que los ~N
  consumidores en `:app` no requieren edición de imports — solo la dependencia de módulo. Confirmar con
  `git grep -n "core.network.ConnectivityMonitor" app/src` que todos resuelven vía el módulo.
- No introducir ciclo: `:core:network` main NO depende de `:app`.
- `:core:network` aún NO provee Retrofit/interceptores — eso es T6. Este esqueleto solo levanta el módulo +
  reubica `ConnectivityMonitor`.

**Commit:** `refactor(core-network): crear modulo :core:network y reubicar ConnectivityMonitor`

---

## Task 6 — `NetworkConfig` inyectado + interceptores (bearer vía puerto, app-version header) + factory de clientes

**Meta:** construir en `:core:network` los **primitivos reutilizables** de red — greenfield en el módulo nuevo,
**aún sin cablear a `:app`** (eso es T7) — reescritos limpios con **tests supremos** (MockWebServer + fakes),
tras **auditar** el código de red de `:app` (`BaseApi`/`V2BaseApi`/`FirebaseBearerInterceptor`/`TokenCache`) y
**verificar el contrato del API** (bearer, timeouts, Gson; header de versión NUEVO). Todo `NetworkConfig`
**inyectado** (resuelve por qué el módulo no puede leer `BuildConfig` de `:app`).

**Archivos a crear (en `:core:network`, package `com.example.msp_app.core.network`):**
- `NetworkConfig.kt` — data class inmutable con lo que hoy sale de `BuildConfig` de `:app` (que un módulo
  library NO puede leer): `legacyBaseUrl` (v1 Node), `v2BaseUrl` (Go), `imagesBaseUrl`, `appVersion`. Se
  **inyecta** (lo provee `:app` en T7 leyendo su `BuildConfig`). Esta es la razón hexagonal de la inyección.
- `AuthTokenProvider.kt` — **puerto** (justificado: cruza módulo + habilita fakes): `suspend fun
  token(forceRefresh: Boolean = false): String?`. La impl Firebase (`FirebaseAuthTokenProvider`, con el
  `TokenCache` de 50 min reescrito) vive en `:app` (T7) — así `:core:network` no depende de Firebase y se
  testea con un fake.
- `BearerAuthInterceptor.kt` — reescritura limpia de `FirebaseBearerInterceptor`: usa `AuthTokenProvider`;
  adjunta `Authorization: Bearer <token>`; si no hay token, pasa sin header (backend responde 401 explícito);
  en 401 con token, refresca (`forceRefresh=true`) y **reintenta una vez**. Idéntico al comportamiento actual
  verificado.
- `AppVersionInterceptor.kt` — **NUEVO**: adjunta `X-App-Version: <NetworkConfig.appVersion>` a cada request.
  Aditivo (backends ignoran headers desconocidos). **Parked for user:** confirmar que el backend Go
  (`/Volumes/M2-1TB/Developer/msp-api`) y el Node v1 toleran/registran el header; si el Go quiere un nombre
  canónico distinto, ajustarlo. Cruzar el backend antes de habilitarlo en v1 si hay duda; por defecto se aplica
  al factory compartido (v1, v2, imágenes).
- `RetrofitClientFactory.kt` — reemplazo de `BaseApi.createClient`/`V2BaseApi.createClient`. Función/clase que,
  dado un `baseUrl` y flags (`auth: Boolean`, timeouts), construye un `OkHttpClient` (con `AppVersionInterceptor`
  siempre; `BearerAuthInterceptor` si `auth=true`) + `Retrofit` (Gson). Preserva los timeouts actuales por
  perfil (v1: 300s; v2: 60s — documentar la diferencia auditada).
- `di/NetworkClientsModule.kt` (opcional) — `@Provides` de `RetrofitClientFactory` (**sin `@Singleton`** si va a
  producir servicios del kill-switch; el factory en sí es stateless, pero los servicios que produzca NO se
  scopean — ver T7 y regla global). Los interceptores son baratos; proveerlos sin scope o dejarlos internos al
  factory. Documentar el razonamiento del scope (como hace el `NetworkModule` legacy).

**Test primero (TDD) — robustez SUPREMA (MockWebServer + fakes, JVM):**
- `BearerAuthInterceptorTest`: (1) con `FakeAuthTokenProvider` que devuelve token → request lleva
  `Authorization: Bearer …`; (2) sin token (null) → request SIN header, pasa; (3) respuesta 401 con token →
  refresca y **reintenta una vez** con el token fresco (afirmar 2 requests al MockWebServer, el 2º con el nuevo
  token); (4) 401 sin token previo → no reintenta en bucle. Verifica el contrato exacto de auth.
- `AppVersionInterceptorTest`: cada request lleva `X-App-Version` con el valor de `NetworkConfig.appVersion`.
- `RetrofitClientFactoryTest`: `auth=true` incluye ambos interceptores; `auth=false` solo el de versión; baseUrl
  y converter Gson correctos; timeouts por perfil. (Deserialización de un DTO simple contra MockWebServer para
  probar Gson + el pipeline.)
- Fakes (`FakeAuthTokenProvider`) en `:core:testing` o en el test source del módulo.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:network:testDebugUnitTest
./gradlew :core:network:detekt
./gradlew ktlintCheck
```

**Gotchas:**
- **NO** cablear esto a `:app` todavía (T7). Aquí el módulo se prueba en aislamiento con MockWebServer.
- `runBlocking` dentro del interceptor (como el legacy) es correcto en el thread de OkHttp; mantener el patrón,
  pero el `TokenCache` (TTL 50 min) vive en la impl `:app` del puerto, no en `:core:network` (Firebase-específico).
- **AUDITAR el legacy antes de reescribir:** verificar que el comportamiento 401-retry, el passthrough sin token
  y los timeouts casan con lo que el backend espera. Si algo del legacy estaba mal, corregirlo conscientemente y
  documentarlo (política de migración). Si NO se puede verificar el contrato → reportar BLOCKED.
- **Anti-PII:** el header de versión no lleva PII; OK. No loguear tokens.

**Commit:** `feat(core-network): NetworkConfig inyectado, interceptores bearer/version y factory de Retrofit`

---

## Task 7 — Cablear `:app` al factory de `:core:network` preservando el KILL-SWITCH (sin regresión)

**Meta:** hacer que `ApiProvider` (v1), `V2ApiProvider` (v2) y `ApiProviderImages` de `:app` **deleguen** en
`RetrofitClientFactory` de `:core:network` para construir sus clientes, **eliminando** `BaseApi`/`V2BaseApi`/
`FirebaseBearerInterceptor`/`TokenCache` duplicados de `:app`, **sin desactivar el kill-switch de baseURL por
Firestore** y **sin cambiar comportamiento**. Proveer `NetworkConfig` + la impl Firebase de `AuthTokenProvider`
desde `:app`. Este es el punto de riesgo del plan — money-path indirecto (todas las llamadas de red).

**Cambios en `:app`:**
- `data/api/ApiProvider.kt` (**el del kill-switch — se PRESERVA su listener Firestore y su `_baseURL`
  StateFlow**): en lugar de `createClient(url)` de `BaseApi`, llama
  `retrofitClientFactory.create(baseUrl = url, auth = false)`. El `init{}` que escucha Firestore y reconstruye
  `retrofitInstance` **queda intacto** (sigue siendo un `object` con Retrofit mutable). `create(service)` sigue
  devolviendo un proxy del Retrofit vigente.
- `data/api/V2ApiProvider.kt`: baseUrl desde `NetworkConfig.v2BaseUrl` (ya no `BuildConfig.V2_BASE_URL`
  directo); cliente vía `factory.create(v2BaseUrl, auth = true)`.
- `data/api/ApiProviderImages.kt`: baseUrl desde `NetworkConfig.imagesBaseUrl`; cliente vía
  `factory.create(imagesBaseUrl, auth = false)`.
- **Borrar** `BaseApi.kt`, `V2BaseApi.kt` (y `FirebaseBearerInterceptor`/`TokenCache` de ahí) una vez migrados —
  su lógica ahora vive reescrita+testeada en `:core:network`. La impl Firebase del puerto
  (`FirebaseAuthTokenProvider` con el `TokenCache` de 50 min) va a `:app` (p.ej. `data/api/FirebaseAuthTokenProvider.kt`).
- `di/NetworkConfigModule.kt` (nuevo en `:app`) — `@Provides` `NetworkConfig` leyendo `BuildConfig`
  (`LEGACY_BASE_URL`, `V2_BASE_URL`, `IMAGES_BASE_URL`, `VERSION_NAME`→`appVersion` vía
  `substringBefore("-")` como `Constants.APP_VERSION`) y `@Provides` `AuthTokenProvider` →
  `FirebaseAuthTokenProvider`. (`NetworkConfig` puede ser `@Singleton` — es data inmutable, NO un servicio del
  kill-switch.)
- `di/NetworkModule.kt` (el `provideWarehousesApi`, etc.): **se queda en `:app`** — NO puede ir a `:core:network`
  porque referencia `ApiProvider.create()` (iría `:core:network → :app`, backwards). Sus `@Provides` de API
  service **siguen SIN `@Singleton`** (kill-switch). Actualizar sus KDoc si hace falta pero conservar el
  razonamiento del scope.
- **`CobranzaSseProvider`** reusa hoy `FirebaseBearerInterceptor` (internal). **Auditar y repuntarlo** al nuevo
  `BearerAuthInterceptor`/`AuthTokenProvider` de `:core:network` (o mantener su propio OkHttp para SSE pero con
  el mismo puerto de token) — que siga adjuntando el bearer idéntico. No romper el SSE de cobranza.

**Test primero (TDD) / no-regresión:**
- Los tests existentes de `:app` que ejercen red (repos/viewmodels/workers con MockWebServer, si los hay) deben
  quedar **verdes** sin cambios de comportamiento (baseline antes de tocar).
- **Kill-switch (crux):** test que afirme que tras un cambio de `_baseURL` (simular el flip), un nuevo
  `ApiProvider.create(...)` apunta al nuevo host — que la delegación al factory NO congeló el proxy. Si el
  arnés Firestore es pesado, como mínimo: afirmar que `ApiProvider.create()` invoca al factory con el
  `_baseURL.value` vigente en cada llamada (no memoiza un proxy viejo tras rebuild), y que `NetworkModule`
  sigue sin `@Singleton`.
- Test de que `NetworkConfig` provisto trae las URLs de `BuildConfig` del flavor `devlocalDebug`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:network:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew assembleDevserverRelease         # release: donde el kill-switch está ACTIVO — debe compilar
./gradlew ktlintCheck
git grep -n "class BaseApi\|class V2BaseApi\|FirebaseBearerInterceptor" app/src   # deben haber desaparecido de :app
```
Revisión: **2 revisores** (uno adversarial verifica el kill-switch y que los tests asserten de verdad) — es el
punto de mayor riesgo (afecta toda la red).

**Gotchas:**
- **NUNCA `@Singleton`** sobre nada que devuelva un servicio de `ApiProvider.create()` (regla dura). El factory
  puede proveerse, pero los **servicios** producidos van sin scope.
- **El listener Firestore de `ApiProvider` NO se toca** — solo se cambia de dónde sale el `Retrofit`
  (del factory en vez de `BaseApi.createClient`). En debug el flavor URL sigue ganando (el `if
  (!BuildConfig.DEBUG)` se preserva).
- El `appVersion` debe salir de la MISMA fuente que hoy (`BuildConfig.VERSION_NAME.substringBefore("-")` =
  `Constants.APP_VERSION`) para que headers y telemetría sean atribuibles al SHA (spec §8).
- Contrato del API: si al reescribir cambia CUALQUIER formato de request/response (headers incluidos), cruzar el
  backend Go en `/Volumes/M2-1TB/Developer/msp-api`. Un cambio de comportamiento de red = documentado, no
  accidental. Si no se puede verificar → BLOCKED.
- `V2_BASE_URL` lo reusa `CobranzaSseProvider` (era `internal val BASE_URL`); al mover a `NetworkConfig`,
  darle a `CobranzaSseProvider` acceso a `NetworkConfig.v2BaseUrl` (inyectado) sin duplicar la URL.

**Commit:** `refactor(app): red delegada a :core:network (factory + NetworkConfig) preservando kill-switch`

---

## Task 8 — Cierre: `prePushCheck` con ambos módulos, adapter stub en composition root, gate + auditoría

**Meta:** cerrar el plan: sumar `:core:telemetry` y `:core:network` a `prePushCheck`, cablear el adapter stub de
telemetría en el composition root de `:app` (para que el `screenView`/`error` reales fluyan a la cola durable
cuando el piloto los emita — sink stub), correr el **gate completo de todos los módulos**, y auditar
conformidad. **Sin regresión: la app corre idéntica.**

**Acciones:**
- **`prePushCheck`** (`build.gradle.kts`): añadir a `dependsOn(...)`:
  `:core:telemetry:ktlintCheck`, `:core:network:ktlintCheck`,
  `:core:telemetry:testDebugUnitTest`, `:core:network:testDebugUnitTest`,
  `:core:telemetry:detekt`, `:core:network:detekt`,
  `:core:telemetry:koverVerify` (dominio ~90%). (`:core:network` koverVerify NO estricto — infra pragmática,
  como `:core:database`.) Actualizar el comentario del bloque `prePushCheck` para reflejar los dos módulos.
- **Composition root** (`:app`): proveer `Telemetry` (= `DurableTelemetry`) y `TelemetrySink` (= stub) por Hilt
  para toda la app; exponer `LocalTelemetry` en el árbol Compose raíz (`AppNavigation`/Activity) para que
  `ScreenScope`/`trackClick` tengan un `Telemetry` real. **Aún sin sink de red** (stub) — no cambia
  comportamiento visible. El `screenView` automático desde el `NavHost` puede quedar como **deuda para Plan 5**
  (cuando monte pantallas) o cablearse mínimamente aquí — decidir y documentar.
- **Verificación de no-regresión:** `assembleDevserverRelease` (release con kill-switch activo) verde; app
  idéntica salvo la infra nueva (telemetría escribe a su cola local; red sale del factory).
- **Documentar** en `NIGHT-REPORT.md` + ledger SDD: sink real de telemetría (GlitchTip/Sentry/endpoint Go) y el
  `screen_view` automático del NavHost quedan como **deuda rastreada** (spec de observabilidad / Plan 5).

**Test primero (TDD):** no hay unidad nueva; la "prueba" es el gate agregado verde + un test de humo de que
`Telemetry` inyectado en un `@HiltViewModel`/composable de prueba resuelve a `DurableTelemetry` y encola.

**Verificación (gate completo del plan):**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew prePushCheck
./gradlew assembleDevserverRelease
```
`prePushCheck` incluye ktlint + unit de todos los módulos + detekt (nuevos) + kover (`:core:common` +
`:core:telemetry`) + `:app:assembleDevlocalDebug`. NADA de `connected*` (device) en este plan.

**Gotchas:**
- NO habilitar el sink de red real (Sentry/GlitchTip) — es spec aparte; el stub es lo correcto ahora.
- Verificar que agregar los módulos a `prePushCheck` no rompe el hook (build compuesto `build-logic` ya está).
- La app debe seguir **idéntica** al usuario: telemetría es silenciosa (cola local + stub); red idéntica.

**Commit:** `chore(core): cierre Plan 4 — prePushCheck con telemetry/network y adapter stub en el root`

---

## Cierre de Plan 4 (auditoría de conformidad)

- [ ] `:core:telemetry` existe (namespace `com.example.msp_app.core.telemetry`), aplica `msp.android.compose/
      hilt/test/kover/detekt` + ktlint, con Room (`ksp room.schemaLocation` a su propio `schemas/`).
- [ ] Puerto `Telemetry` (screenView/tap/event/error) + VOs de evento con **invariantes anti-PII** (etiquetas
      estáticas, alfabeto restringido); fake `RecordingTelemetry` en `:core:testing`.
- [ ] **Cola durable en Room, store PROPIO** (`telemetry_db` v1, `exportSchema` + `1.json` commiteado) — NO
      toca `msp_db`/v27. Tests: **encolar sobrevive** (reabrir), **drena en orden FIFO**, estados
      pending/uploading/sent/failed, backoff, **nunca tira errores**, ack-based (recupera `UPLOADING` colgado),
      concurrencia; `observePendingCount` vía Turbine. Cobertura dominio ~90%.
- [ ] `Modifier.trackClick` + `ScreenScope`/`LocalScreenName` (etiquetas estáticas, sin PII) + adapter
      `DurableTelemetry` drenando a `StubTelemetrySink` (sink real = spec aparte).
- [ ] `:core:network` existe (namespace `com.example.msp_app.core.network`), mismos convention plugins;
      `ConnectivityMonitor` **reubicado** (package idéntico, `@Singleton` conservado) + `ConnectivityModule`.
- [ ] `NetworkConfig` **inyectado** (baseURLs + appVersion provistos por `:app` desde `BuildConfig`);
      `AuthTokenProvider` puerto (impl Firebase en `:app`, `:core:network` vendor-free); `BearerAuthInterceptor`
      + `AppVersionInterceptor` (X-App-Version) + `RetrofitClientFactory` reescritos con tests MockWebServer
      supremos (bearer, 401-retry, passthrough sin token, header de versión, timeouts por perfil).
- [ ] `:app` delega en el factory: `BaseApi`/`V2BaseApi`/`FirebaseBearerInterceptor`/`TokenCache` eliminados de
      `:app`; `ApiProvider`/`V2ApiProvider`/`ApiProviderImages` usan `RetrofitClientFactory`; `CobranzaSseProvider`
      repunteado al puerto de token.
- [ ] **KILL-SWITCH intacto:** listener Firestore de `ApiProvider` preservado; **ningún `@Singleton`** sobre
      servicios de `ApiProvider.create()`; `NetworkModule` (API services) permanece en `:app` sin scope.
      Test/aserción de que el flip de baseURL alcanza a nuevos `create(...)`.
- [ ] **Sin regresión:** app idéntica; `assembleDevserverRelease` (release, kill-switch activo) verde.
- [ ] `prePushCheck` incluye ktlint + unit + detekt + kover de `:core:telemetry` y `:core:network`; adapter
      stub cableado en el composition root.
- [ ] Gate: commits por tarea, conventional, en español, sin atribución de Claude, sin push, rama
      `feat/multimodulo-cimiento`. Fakes-only, cero MockK/Mockito.

### Decisiones resueltas (orquestador)
1. **Telemetría = store Room PROPIO** (`telemetry_db` v1), independiente de `msp_db` (v27 INMUTABLE): la cola no
   es una entidad nueva en `:core:database`, es su propia `TelemetryDatabase` en `:core:telemetry` — así se
   respeta la regla dura del schema de producción. `exportSchema` ON desde v1.
2. **`AuthTokenProvider` como puerto** (no una dependencia directa a Firebase en `:core:network`): justificado
   porque cruza módulo y habilita fakes-only (MockWebServer + `FakeAuthTokenProvider`); la impl Firebase con el
   `TokenCache` de 50 min vive en `:app`. `:core:network` queda vendor-free.
3. **El listener Firestore del kill-switch NO se mueve a `:core:network`** — se queda en `ApiProvider` (`:app`),
   que sigue siendo el `object` con Retrofit mutable; solo se sustituye el *código de construcción del cliente*
   por el `RetrofitClientFactory`. Mover el listener arrastraría Firebase + `Constants` de `:app` a un core y no
   aporta (YAGNI); lo esencial es no congelar el proxy (regla `@Singleton`), y eso se preserva.
4. **`NetworkModule` (los `@Provides` de API service) se queda en `:app`**: referencia `ApiProvider.create()`;
   moverlo a `:core:network` crearía una dependencia backwards `:core:network → :app`. Solo lo genuinamente
   cross-módulo (`ConnectivityMonitor`, `RetrofitClientFactory`, interceptores, `NetworkConfig`, puertos) vive
   en `:core:network`.
5. **`X-App-Version` header** se aplica en el factory compartido (v1/v2/imágenes) como header aditivo. **Parked
   for user:** confirmar el nombre canónico y tolerancia en el backend Go (`/Volumes/M2-1TB/Developer/msp-api`)
   y el Node v1 antes de depender de él server-side; si el Go prefiere otro nombre, ajustar en T6.
6. **Sink real de telemetría (GlitchTip/Sentry/endpoint Go) + `screen_view` automático del NavHost = fuera de
   alcance** (spec de observabilidad / Plan 5). Aquí: cola durable testeada + adapter con **sink stub**. No
   reabrir en Plan 4.
7. **Sin device en Plan 4:** todo corre en JVM/Robolectric + MockWebServer; el emulador se reserva para los e2e
   de Plan 2/5.
