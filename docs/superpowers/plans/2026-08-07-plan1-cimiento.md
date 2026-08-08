# Plan 1 — Cimiento: `:core:common` + `:core:testing` + Hilt en `:app`

Parte del plan maestro `2026-08-07-plan-maestro-multimodulo.md` (spec fuente:
`docs/superpowers/specs/2026-08-07-migracion-arquitectura-msp-app-kt.md`, §9). Continúa donde terminó
**Plan 0** (rama + `build-logic` con los 5 convention plugins `msp.android.library/compose/hilt/test/kover`
+ `gradle/libs.versions.toml` completo, incl. Hilt/Roborazzi/Turbine/Kover/detekt/hilt-work). Este plan
levanta los DOS primeros módulos (`:core:common`, `:core:testing`), mete **Hilt en `:app` envolviendo lo
existente sin reescribir su interior**, cablea el primer feature (`Warehouse*`) a Hilt, y planta el gate
(pre-push + regla anti-`Double`). Al terminar, **la app corre idéntica**; solo cambia el andamiaje interno.

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`): implementador TDD →
> gate real → 2 revisores → fix-loop, una tarea a la vez.

---

## Global Constraints (vinculan a TODA tarea de este plan)

- **Toolchain FIJA, no cambiar:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11 (`jvmTarget=11`, desugaring on), Compose BOM 2024.09.00, Gradle wrapper 8.11.1.
- **`JAVA_HOME` en CADA comando gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Variante de gate para compilar/probar:** `devlocalDebug` (unit tests = `testDevlocalDebugUnitTest`).
  Los módulos `:core:*` son librerías sin flavors → su unit test es `:core:<x>:testDebugUnitTest`.
- **Paquete/`applicationId` `com.example.msp_app` NO se toca** (UpdateChecker/Firestore atados al package).
  Módulos nuevos bajo `com.example.msp_app.core.*` (namespace `com.example.msp_app.core.common` /
  `com.example.msp_app.core.testing`).
- **Aplicar los convention plugins de Plan 0** a los módulos nuevos (`msp.android.library`, `msp.hilt`,
  `msp.test`, `msp.kover` según aplique). **`msp.test` se aplica DESPUÉS de un plugin Android library**
  (toma `CommonExtension.testOptions`). Cada módulo nuevo aplica además `alias(libs.plugins.ktlint)`
  explícito y fija su `namespace` — el `msp.android.library` NO aplica ktlint ni pone namespace, y el
  `ktlintCheck` raíz **no cascada** dentro de `build-logic` (ese ya trae su propio ktlint).
- **Testing:** dobles = **fakes únicamente** (estado + recording/spy). **CERO MockK/Mockito.** + Turbine +
  `kotlinx-coroutines-test`. Robolectric para DAO/Compose. Cobertura por capa vía **Kover** (domain ~90%).
- **DI:** Hilt **envuelve** `ApiProvider`/`V2ApiProvider`/`ApiProviderImages`/`ConnectivityMonitor` sin
  tocar su interior (los `object` siguen existiendo; las 29 llamadas `*.create(...)` legacy siguen
  compilando). Se preserva el **rebuild de baseURL por Firestore** de `ApiProvider`.
- **Commits por tarea**, conventional commits, subject en **español**, **SIN atribución de Claude**,
  **SIN `--no-verify`**, **sin push**. Rama: `feat/multimodulo-cimiento`.
- **Código en inglés; strings de usuario en español**, minimalistas. Datos de test con nombres realistas
  mexicanos.

### Orden y su justificación (leer antes de empezar)
El grafo real obliga este orden: `:core:testing` porta `MainDispatcherRule`/`RobolectricTestBase` que los
tests de `:core:common` consumen → `:core:testing` debe existir antes de llenar `:core:common`; pero
`:core:testing` **depende** de `:core:common` (referencia los tipos de puerto que fakea) → por eso primero
se crea el **esqueleto** de `:core:common` (Task 1), luego `:core:testing` (Task 2), y recién entonces se
**llena** `:core:common` con el outbox y la salud de sync (Tasks 3–4). Después Hilt en `:app` en pasos
verificables (Application → Activity → WorkerFactory → módulos `@Provides` → feature Warehouse), y al final
el gate (anti-`Double` + pre-push). Cada tarea deja el build **verde** y la app **idéntica**.

### Comando de gate (por tarea, ajustando el alcance)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew ktlintCheck
./gradlew testDevlocalDebugUnitTest            # :app
./gradlew :core:common:testDebugUnitTest :core:testing:testDebugUnitTest   # cuando existan
./gradlew :app:assembleDevlocalDebug
```

---

## Task 1 — Crear módulo `:core:common` (esqueleto verde)

**Meta:** existir como módulo Gradle Android-library, aplicando los convention plugins de Plan 0, incluido en
`settings.gradle.kts`, compilando y con un test unitario trivial que pruebe el andamiaje. Sin contenido de
negocio todavía (el outbox llega en Task 3). El objetivo es aislar el "¿el módulo se levanta?" del "¿el
contenido es correcto?".

**Archivos a crear:**
- `settings.gradle.kts` (raíz) → añadir `include(":core:common")` junto a `include(":app")`.
- `core/common/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.library")
      id("msp.test")               // DESPUÉS de msp.android.library
      id("msp.kover")
      alias(libs.plugins.ktlint)   // para que el ktlintCheck raíz cubra el módulo
  }
  android { namespace = "com.example.msp_app.core.common" }
  ```
- `core/common/src/main/kotlin/com/example/msp_app/core/common/.gitkeep` (o una util trivial real, ver test).
- `core/common/src/main/AndroidManifest.xml` — NO es necesario en AGP 8.x para librerías con namespace en DSL;
  si el build lo pide, uno mínimo (`<manifest/>`).

**Test primero (TDD):** una util pequeña y genuinamente compartida para dar contenido no vacío al módulo, con
su test. Sugerido: `MoneyText`-agnostic no — el dinero es del designsystem (Plan 3). Usar algo neutro: p.ej.
`fun Long.orZero()`-style **no**; mejor sembrar el módulo con el **modelo de salud de sync vacío** ya no —
eso es Task 4. Para no adelantar Task 3/4, sembrar con una util verdaderamente compartida y sin dependencias:
`core/common/src/test/kotlin/com/example/msp_app/core/common/text/TruncateTest.kt` que ejercite
`String.ellipsize(max: Int)` (util de UI-agnóstica). Escribir el test rojo → implementar `Truncate.kt` → verde.
(Si el revisor prefiere no introducir una util nueva, aceptar un test-placebo `ModuleSmokeTest` que solo
afirme `2 + 2 == 4` para probar la toolchain de test; documentar la elección.)

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:testDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```
Los tres `BUILD SUCCESSFUL`. `:app` no cambió: sigue ensamblando igual.

**Gotchas:**
- `msp.test` falla si se aplica antes que un plugin Android → respetar el orden del bloque `plugins {}`.
- No añadir `msp.android.compose` aquí: `:core:common` es lógica/domino, sin Compose (el widget de sync es
  estado, no UI — la UI del pill vive en `:core:designsystem`, Plan 3).
- El `koverVerify` del módulo NO debe exigir umbral todavía (Task 4 lo sube al 90% cuando entre el domain).

**Commit:** `feat(core-common): crear modulo base :core:common`

---

## Task 2 — Crear `:core:testing` + mover `MainDispatcherRule` y `RobolectricTestBase`

**Meta:** módulo de infraestructura de test **consumible como main source set** por los demás módulos vía
`testImplementation(project(":core:testing"))`. Mueve las reglas de test **sin dependencias de `:app`** desde
`app/src/test/.../test-fixtures/` a `:core:testing`, y deja los fakes compartidos crecer aquí. Depende de
`:core:common` (para tipar los fakes de sus puertos cuando lleguen en Task 3).

**Archivos:**
- `settings.gradle.kts` → `include(":core:testing")`.
- `core/testing/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.library")
      alias(libs.plugins.ktlint)
  }
  android { namespace = "com.example.msp_app.core.testing" }
  dependencies {
      api(project(":core:common"))
      // Las libs de test se exponen como `api` porque este módulo ES la infra de test:
      api(libs.junit); api(libs.kotlinx.coroutines.test); api(libs.turbine); api(libs.robolectric)
      api(libs.androidx.arch.core.testing)
      api(libs.roborazzi); api(libs.roborazzi.compose); api(libs.roborazzi.junit.rule)
  }
  ```
  (Nota: aquí las reglas viven en `src/main`, no en `src/test`, para que otros módulos las importen.)
- **Mover** `app/src/test/java/com/example/msp_app/test-fixtures/MainDispatcherRule.kt` y
  `RobolectricTestBase.kt` → `core/testing/src/main/kotlin/com/example/msp_app/core/testing/`
  (renombrar package a `com.example.msp_app.core.testing`; el nombre de package con backticks
  `` `test-fixtures` `` desaparece).
- `app/build.gradle.kts` → añadir `testImplementation(project(":core:testing"))`.
- Actualizar imports en `:app`: los tests que usaban `com.example.msp_app.`test-fixtures`.MainDispatcherRule`
  / `RobolectricTestBase` pasan a `com.example.msp_app.core.testing.*`. **`RoomTestBase` y `TestDataFactory`
  NO se mueven** (dependen de `AppDatabase`/entities de `:app`, que migran en Plan 2) — solo se re-apunta su
  `import` de `RobolectricTestBase` al nuevo package. Ver "Gotcha / desviación" abajo.
- Sembrar `core/testing/src/main/.../roborazzi/RoborazziConfig.kt` (placeholder de config: opciones de
  comparación/umbral) para que Plan 3 lo consuma; con un test que solo instancie la config.

**Test primero (TDD):** un test dentro de `:core:testing` que ejercite `MainDispatcherRule` (arranca una
coroutine en `Dispatchers.Main` bajo la regla y verifica que corre) → prueba que la regla movida funciona
desde su nuevo hogar. Correr luego TODA la suite de `:app` para probar que los ~69 tests siguen verdes con
los imports re-apuntados.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:testing:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest      # :app, con imports re-apuntados
./gradlew ktlintCheck
```

**Gotcha / desviación deliberada del brief:** el brief pide mover también `RoomTestBase` y `TestDataFactory`
a `:core:testing`. **No se puede en Plan 1**: `RoomTestBase` referencia `com.example.msp_app.data.local.AppDatabase`
y `TestDataFactory` referencia entities (`LocalSaleEntity`, `LocalSaleComboEntity`, `ProductInventory`,
`SaleDraft`) que viven en `:app` hasta que Plan 2 cree `:core:database`. Un `:core:testing` que dependa de
`:app` sería un ciclo. → **Se mueven solo `MainDispatcherRule` y `RobolectricTestBase` ahora; `RoomTestBase`
y `TestDataFactory` migran en Plan 2** junto con `AppDatabase`. Documentar esto en el reporte de la tarea.

**Resolución (orquestador):** `RoomTestBase` y `TestDataFactory` NO se mueven a `:core:testing` en Plan 1.
Referencian tipos de `:app` (`AppDatabase`, entities) que no salen de `:app` hasta que Plan 2 cree
`:core:database` — moverlos ahora crearía un ciclo de dependencias. Solo `MainDispatcherRule` y
`RobolectricTestBase` (framework-only, sin dependencias de `:app`) se mueven en esta tarea.
`RoomTestBase`/`TestDataFactory` quedan diferidos a Plan 2. Esto ya NO es una decisión abierta: es la
implementación a seguir en esta tarea.

**Commit:** `feat(core-testing): crear :core:testing y mover reglas de test sin deps de app`

---

## Task 3 — Promover el outbox (pendingwork `domain`) a `:core:common`

**Meta:** el patrón disk-first/ack-based de sincronización pendiente se vuelve **infra reutilizable del
template** (spec §13 #1). Se mueve SOLO el **dominio** (`domain/{models,ports,usecases}`) de
`core/sync/pendingwork/` a `:core:common`; **los adapters WorkManager, synchronizers, gates, observers y el
factory se quedan en `:app`** (dependen de los workers de `:app`, los `LocalDataSource`, y `RemoteLogger` —
no pueden subir sin arrastrar `:app`). `:app` pasa a depender de `:core:common`.

**Mover a `:core:common`** (package nuevo `com.example.msp_app.core.common.sync.pendingwork.domain.*`):
- `domain/models/SyncContext.kt`, `domain/models/SyncResult.kt`
- `domain/ports/PendingWorkSynchronizer.kt`, `SessionSyncGate.kt`, `SessionSyncObserver.kt`,
  `PaymentsWorkEnqueuer.kt`, `VisitsWorkEnqueuer.kt`, `GuaranteesWorkEnqueuer.kt`,
  `GuaranteeEventsWorkEnqueuer.kt`, `LocalSalesWorkEnqueuer.kt`
- `domain/usecases/SyncAllPendingWorkUseCase.kt`
- **Tests de dominio:** mover `app/src/test/java/com/example/msp_app/core/sync/pendingwork/domain/**` (models
  + usecases) a `core/common/src/test/.../sync/pendingwork/domain/**`; usan `MainDispatcherRule` desde
  `:core:testing` → añadir `testImplementation(project(":core:testing"))` a `:core:common/build.gradle.kts`.

**Quedan en `:app`** (solo se actualiza su `import` al nuevo package del dominio):
- `data/enqueuers/*WorkManagerEnqueuer.kt` (referencian `workers/` de `:app`),
  `data/synchronizers/*PendingSynchronizer.kt`, `data/gates/InMemorySessionSyncGate.kt`,
  `data/observers/RemoteLoggerSessionSyncObserver.kt`, `di/PendingWorkSyncFactory.kt`.
- El caller `navigation/AppNavigation.kt` (`PendingWorkSyncFactory.createUseCase(app).execute(...)`) — su
  import de `SyncContext`/`SyncResult`/use-case pasa al nuevo package.
- Los tests de `:app` sobre synchronizers/gate (`.../pendingwork/data/**`) se quedan; re-apuntan imports.

**Test primero (TDD):** los tests de `SyncAllPendingWorkUseCase` ya existen y cubren idempotencia por gate,
paralelismo, `runCatching` por-synchronizer y timeout global — al moverlos deben **seguir verdes** en
`:core:common`. Antes de mover código, correr la suite en `:app` para tener el baseline verde; después de
mover, correr en `:core:common`. **No** reescribir la lógica del use-case en esta tarea.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Gotchas:**
- `:app` → `:core:common` es la única dirección permitida; NUNCA al revés. Verificar que ningún archivo que
  quede en `:app` sea importado desde `:core:common` (rompería el grafo).
- `SyncAllPendingWorkUseCase` tiene `companion object { MAX_ITEMS_PER_SYNC }`; los synchronizers en `:app`
  que lo lean deben importarlo del nuevo package.
- Cuidar el `enqueueClienteSyncWorker(this)` de `MspApplication.onCreate` — no se toca aquí.

**Commit:** `refactor(core-common): promover dominio del outbox pendingwork a :core:common`

---

## Task 4 — Endurecer outbox + estado de "salud de sync" en `:core:common`

**Meta:** cerrar el spec §13 (#1 outbox endurecido, #2 salud de sync app-side, base del "sync pill"). Como el
dominio ya está promovido (Task 3), "endurecer" aquí = (a) **modelo de salud de sync** puro (pendientes vs
confirmados) con su fuente por puerto, (b) **tests de invariantes** del ciclo ack/retry con un **recording
fake**, (c) subir el umbral **Kover a ~90%** en `:core:common` (domain). **Sin UI** — el pill Compose es del
designsystem (Plan 3); aquí solo el estado.

**Archivos a crear en `:core:common`:**
- `sync/health/SyncHealth.kt` — value object: `data class SyncHealth(val pending: Int, val confirmed: Int)`
  con derivadas puras (`hasBacklog: Boolean`, `total`, quizá un `enum SyncStatus { HEALTHY, SYNCING, BACKLOG }`
  calculado). Sin Double, sin framework.
- `sync/health/SyncHealthSource.kt` — puerto: `fun observe(): Flow<SyncHealth>` (una sola definición; la impl
  real que cuenta filas Room vive en `:app`/`:core:database` en planes siguientes — YAGNI, no crear impl aquí
  salvo un default que sume los `fetchPending` existentes si es trivial y sin deps de `:app`).
- (Opcional, si aporta) `sync/health/SyncHealthReducer.kt` — función pura que combina los conteos de los 5
  tipos de trabajo pendiente en un `SyncHealth`.

**Test primero (TDD):**
1. `SyncHealthTest` — tabla de casos: 0/0 → HEALTHY; pending>0 → BACKLOG; invariantes de `total`/`hasBacklog`.
2. `SyncHealthSourceTest` con Turbine — un **fake** `RecordingSyncHealthSource` (en `:core:testing`) emite una
   secuencia y el test afirma la transición de estados.
3. `OutboxAckInvariantTest` — usando un **recording fake** de `PendingWorkSynchronizer`/`SessionSyncObserver`,
   afirmar el invariante duro del spec §7.1/§13: un item **nunca** se marca confirmado sin ack; un
   synchronizer que falla NO tumba a los otros; el observer recibe un `SyncResult` por cada synchronizer.
   (Refuerza lo que Task 3 movió, ahora como contrato explícito del template.)

**Fakes nuevos en `:core:testing`:** `RecordingSyncHealthSource`, y (si no existen ya) recording fakes de los
puertos del outbox (`RecordingSessionSyncObserver`, `FakePendingWorkSynchronizer` con resultado programable).
Cero MockK.

**Kover:** en `core/common/build.gradle.kts`, subir el umbral del `msp.kover`/`koverVerify` a **90%** de líneas
sobre el package `...core.common.sync..**` (domain). Ajustar la config de Kover para excluir placeholders sin
lógica. Correr `:core:common:koverVerify` y que pase.

**Resolución (orquestador):** el objetivo "~80% app" del plan maestro NO aplica al `:app` legacy (no tiene
tests hoy; imponerle un gate de cobertura repo-wide bloquearía todo sin aportar nada). El umbral ~90% de
Kover que se aplica en esta tarea está **acotado exclusivamente al domain de `:core:common`**
(`...core.common.sync..**`). NO se agrega ningún gate de cobertura repo-wide sobre `:app` en Plan 1; la
cobertura de `:app` se resuelve por feature migrado en planes posteriores.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:testDebugUnitTest
./gradlew :core:common:koverVerify
./gradlew :core:testing:testDebugUnitTest
./gradlew ktlintCheck
```

**Gotchas:**
- No inventar un "outbox nuevo": el brief dice **endurecer**, no reescribir. El endurecimiento es
  test-cobertura + estado de salud + umbral, no un rediseño del pipeline de pagos (que sigue vivo en `:app`).
- `SyncHealthSource` es un puerto de UNA impl potencial → por YAGNI, mantener la interfaz mínima y NO crear
  adaptadores especulativos; la impl real (contando Room) llega cuando `:core:database` exista (Plan 2) o con
  el piloto (Plan 5).

**Commit:** `feat(core-common): estado de salud de sync + invariantes del outbox`

---

## Task 5 — Hilt en `:app`: `@HiltAndroidApp` + `@AndroidEntryPoint`

**Meta:** encender Hilt en `:app` sin cambiar comportamiento. `MspApplication` pasa a `@HiltAndroidApp`
**preservando `applicationScope` y todo su `onCreate`** (logging remoto, db-debugger, `enqueueClienteSyncWorker`).
`MainActivity` pasa a `@AndroidEntryPoint`. Todavía **no** se inyecta nada real; es el andamiaje del grafo.

**Archivos:**
- `app/build.gradle.kts` → aplicar Hilt. Usar el convention plugin `id("msp.hilt")` (aplica KSP + hilt-android
  + `ksp(hilt-compiler)`). **Cuidado:** `:app` ya aplica `alias(libs.plugins.ksp)` en su bloque `plugins`;
  `msp.hilt` también hace `pluginManager.apply("com.google.devtools.ksp")` → aplicar KSP dos veces es
  idempotente en Gradle, pero verificar que no haya conflicto de `alias` + `pluginManager.apply`. Si choca,
  aplicar Hilt directo (`alias(libs.plugins.hilt.android)` + deps `implementation(libs.hilt.android)` +
  `ksp(libs.hilt.compiler)`) en vez del convention plugin, y documentarlo.
- `MspApplication.kt` → anotar `@HiltAndroidApp`; **no** tocar `applicationScope` ni los `initialize*()`.
- `MainActivity.kt` → anotar `@AndroidEntryPoint` (es `FragmentActivity`, compatible).

**Test primero (TDD):** test Robolectric que arranca la app bajo un `HiltTestApplication` **o** un test que
verifique que el componente de Hilt se construye sin faltantes de binding. Mínimo realista para Plan 1: un
`@HiltAndroidTest` con `HiltAndroidRule` que haga `inject()` de un módulo vacío y pase (prueba que el grafo
compila y arranca). Si el arnés de test instrumentado-en-JVM de Hilt resulta pesado para esta tarea, aceptar
como verificación que `:app:assembleDevlocalDebug` compile con el procesador de Hilt activo + un test unit que
afirme que `MspApplication` sigue exponiendo `applicationScope` no-nulo.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDevlocalDebug
./gradlew testDevlocalDebugUnitTest
./gradlew ktlintCheck
```
Instalar el APK devlocalDebug y confirmar **arranque idéntico** (login/biometría, Home) — smoke manual o e2e
existente.

**Gotchas:**
- `@HiltAndroidApp` genera una `Application` base; `MspApplication` debe seguir siendo la del manifest
  (`android:name=".MspApplication"`) — no crear otra.
- No convertir ViewModels ni workers aquí (eso es Task 6/8). Solo el andamiaje raíz.

**Commit:** `feat(app): activar Hilt en Application y MainActivity`

---

## Task 6 — `HiltWorkerFactory` + quitar el inicializador por defecto de WorkManager

**Meta:** que WorkManager use la fábrica de Hilt, **sin convertir aún los 7 workers a `@HiltWorker`** (eso es
per-feature, planes siguientes). Se preserva el comportamiento: `HiltWorkerFactory` devuelve `null` para
workers no anotados y WorkManager **cae al constructor reflexivo por defecto** — los workers actuales
(`(Context, WorkerParameters)`) siguen funcionando igual.

**Resolución (orquestador):** los dos cambios — agregar `Configuration.Provider` con `HiltWorkerFactory` en
`MspApplication` Y remover el `WorkManagerInitializer` por defecto del manifest — van en la **misma tarea**,
como ya describe este brief. Hacer uno sin el otro produce doble inicialización de WorkManager (uno de los
dos falla en runtime). Confirmado además: **no se convierten los 7 workers existentes** a `@HiltWorker` en
esta tarea; mantienen su constructor por defecto, `HiltWorkerFactory` devuelve `null` para ellos y
WorkManager cae a reflexión — comportamiento idéntico al actual. La conversión per-worker queda para planes
siguientes.

**Archivos:**
- `app/build.gradle.kts` → añadir `implementation(libs.androidx.hilt.work)` + `ksp(libs.androidx.hilt.compiler)`.
- `MspApplication.kt` → implementar `Configuration.Provider`:
  ```kotlin
  @Inject lateinit var workerFactory: HiltWorkerFactory
  override val workManagerConfiguration: Configuration
      get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
  ```
  (mantener `applicationScope` y `onCreate`; ojo: con inicialización on-demand, `WorkManager.getInstance` se
  auto-inicializa desde `Configuration.Provider`).
- `app/src/main/AndroidManifest.xml` → **remover el inicializador por defecto** para que Hilt provea la config:
  ```xml
  <provider
      android:name="androidx.startup.InitializationProvider"
      android:authorities="${applicationId}.androidx-startup"
      android:exported="false"
      tools:node="merge">
      <meta-data
          android:name="androidx.work.WorkManagerInitializer"
          tools:node="remove" />
  </provider>
  ```
  (añadir `xmlns:tools` al `<manifest>` si no está).

**Test primero (TDD):** test Robolectric/JVM que construya `MspApplication.workManagerConfiguration` y afirme
que su `workerFactory` no es nulo (via `HiltAndroidRule` + inject), **y** un test que encole uno de los workers
existentes (p.ej. `ClienteSyncWorker`) con `WorkManagerTestInitHelper`/`work-testing` y verifique que corre —
probando que el fallback al constructor por defecto sigue vivo para workers no-`@HiltWorker`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDevlocalDebug
./gradlew testDevlocalDebugUnitTest
./gradlew ktlintCheck
```
Smoke: arrancar la app y confirmar que la sync de clientes periódica (`enqueueClienteSyncWorker`) y los
"Enviar Pendientes" siguen encolando/corriendo.

**Gotchas:**
- Si NO se remueve `WorkManagerInitializer` pero SÍ se declara `Configuration.Provider`, WorkManager tira
  `IllegalStateException` (doble init). Los dos cambios van juntos, en esta misma tarea.
- `HiltWorkerFactory` para un worker **no** anotado devuelve `null` → WorkManager usa `DefaultWorkerFactory`
  (reflexión). Confirmar en el test que los workers actuales NO necesitan `@HiltWorker` todavía.

**Commit:** `feat(app): HiltWorkerFactory y remover inicializador WorkManager por defecto`

---

## Task 7 — Módulos Hilt `@Provides` envolviendo los providers de red + conectividad

**Meta:** exponer por Hilt lo que hoy es global, **sin reescribir su interior**: `ApiProvider`,
`V2ApiProvider`, `ApiProviderImages`, `ConnectivityMonitor`. Se **preserva el rebuild de baseURL por Firestore**
de `ApiProvider` (kill-switch remoto en release + `StateFlow<String> baseURL`): los `@Provides` **delegan** en
los `object` existentes (`ApiProvider.create(...)`, etc.), no construyen un Retrofit nuevo. Las 29 llamadas
legacy `*.create(...)` siguen compilando intactas (los `object` no se borran).

**Archivos a crear (en `:app`, package `com.example.msp_app.di`):**
- `NetworkModule.kt` — `@Module @InstallIn(SingletonComponent::class)`:
  - `@Provides @Singleton fun provideWarehousesApi(): WarehousesApi = ApiProvider.create(WarehousesApi::class.java)`
    (patrón para servicios legacy v1; delega en `ApiProvider` → hereda su baseURL reactiva).
  - Proveer, según necesidad de Task 8, los servicios v2 vía `V2ApiProvider.create(...)` y de imágenes vía
    `ApiProviderImages.create(...)`. **YAGNI:** proveer SOLO los servicios que Task 8 (Warehouse) consume;
    los demás se agregan cuando su feature migre. NO proveer 29 servicios de golpe.
  - Exponer también el `StateFlow<String>` de baseURL si algún consumidor lo requiere:
    `@Provides fun provideLegacyBaseUrl(): StateFlow<String> = ApiProvider.baseURL` (opcional; solo si se usa).
- `ConnectivityModule.kt` — `@Provides @Singleton fun provideConnectivityMonitor(@ApplicationContext ctx: Context): ConnectivityMonitor = ConnectivityMonitor.getInstance(ctx)` (respeta el singleton existente; `ConnectivityMonitor` es `open` y ya tiene `getInstance`).

**Test primero (TDD):** un `@HiltAndroidTest` que inyecte `WarehousesApi` y `ConnectivityMonitor` y afirme que
no son nulos (prueba que los bindings resuelven). Además, un test unit que afirme que `provideWarehousesApi()`
devuelve una instancia creada por `ApiProvider` (mismo camino que legacy) — garantiza paridad de baseURL.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDevlocalDebug
./gradlew testDevlocalDebugUnitTest
./gradlew ktlintCheck
```

**Gotchas:**
- **No** reimplementar el Retrofit de `ApiProvider` en el módulo: perdería el listener de Firestore y el
  `synchronized`/rebuild. Delegar siempre en el `object`.
- `ApiProviderImages` usa `by lazy` sin kill-switch (base estática por flavor) — proveerlo tal cual.
- `V2ApiProvider.BASE_URL` es `internal` y estático; su `@Provides` solo delega en `create(...)`.

**Commit:** `feat(app): modulos Hilt que envuelven ApiProvider/V2/Images/Connectivity`

---

## Task 8 — Cablear el feature `Warehouse*` a Hilt (primer feature real)

**Meta:** primer ViewModel a `@HiltViewModel`, aprovechando que `WarehouseRepository` y
`WarehouseRemoteDataSource` **ya tienen `@Inject constructor`** (hoy no-op, sin contenedor). Al terminar, la
cadena `WarehousesApi (Hilt) → WarehouseRemoteDataSource (@Inject) → WarehouseRepository (@Inject) →
WarehouseViewModel (@HiltViewModel)` se resuelve por Hilt, y las 6 pantallas que hoy hacen `viewModel()`
pasan a `hiltViewModel()`.

**Archivos:**
- `features/warehouses/WarehousesViewModel.kt` (clase `WarehouseViewModel`) → `@HiltViewModel class
  WarehouseViewModel @Inject constructor(application: Application, private val repository: WarehouseRepository)
  : AndroidViewModel(application)`. Mantener `ProductsCache(application.applicationContext)` construido
  internamente (o inyectarlo si es trivial — YAGNI: dejarlo si complica). Quitar la construcción manual de
  `warehousesApi`/`remoteDataSource`/`repository` (ahora inyectados).
- `data/local/repository/WarehousesRepository.kt` y
  `data/local/datasource/warehouseRemoteDataSource/WarehousesRemoteDataSource.kt` → ya `@Inject`; verificar que
  sus dependencias (`WarehousesApi`) las provea `NetworkModule` (Task 7). Si `WarehouseRemoteDataSource`
  recibe `WarehousesApi` por constructor, Hilt lo resuelve gracias al `@Provides` de Task 7.
- **6 call sites** que hoy hacen `val vm: WarehouseViewModel = viewModel()` → `hiltViewModel()`:
  `EditSaleScreen`, `NewSaleScreen`, `CartScreen`, `ProductDetailsScreen`, y los selectores
  `SimpleProductSelector` / `ModernProductSelector` (confirmar rutas exactas con
  `grep -rn "WarehouseViewModel" app/src/main`). Añadir `implementation(libs.androidx.hilt.navigation.compose)`
  a `:app` para `hiltViewModel()`.

**Test primero (TDD):** `WarehouseViewModelTest` (JVM, con `MainDispatcherRule` de `:core:testing` + Turbine) —
construir el ViewModel con un **fake `WarehouseRepository`** (recording/state fake en `:core:testing` o local
al test; el repo hoy es clase concreta → para fakearlo sin MockK, extraer/usar su forma inyectable: como
`WarehouseRepository` es `@Inject constructor(remoteDataSource)` y `WarehouseRemoteDataSource` envuelve
`WarehousesApi`, el punto de fake más limpio es un **fake `WarehousesApi`** que devuelva respuestas
programadas). Test: `loadAllWarehouses()` filtra `ALMACEN_GENERAL_ID`; `getWarehouseProducts()` cae a cache al
fallar la red. Afirmar estados vía Turbine sobre los `StateFlow`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDevlocalDebug
./gradlew testDevlocalDebugUnitTest
./gradlew ktlintCheck
```
Smoke: en pantallas de venta/carrito que usan almacenes (`NewSale`, `Cart`, `ProductDetails`) verificar que
listan almacenes y productos igual que antes.

**Gotchas / riesgo:**
- **Semántica de instancia compartida:** hoy `viewModel()` da una instancia por `ViewModelStoreOwner` más
  cercano; `hiltViewModel()` scoping equivalente (nav backstack entry / activity). Verificar que las 6
  pantallas sigan compartiendo/aislando el `WarehouseViewModel` como antes (p.ej. que el selector dentro de
  `NewSaleScreen` vea el mismo estado que la pantalla). Si el scoping difiere, anclar con `hiltViewModel()` al
  backStackEntry correcto. **Este es el riesgo principal de la tarea** — cubrir con smoke manual.
- `WarehouseViewModel` sigue siendo `AndroidViewModel`: `@HiltViewModel` lo permite si el constructor recibe
  `Application` (Hilt la provee). No convertir a `ViewModel` plano (usa `getApplication` para conectividad).
- No migrar OTROS features aquí; Warehouse es el único de Plan 1.

**Commit:** `feat(warehouses): cablear WarehouseViewModel a Hilt`

---

## Task 9 — Regla lint anti-`Double` para dinero (detekt)

**Meta:** hornear la regla del spec §13 (Tier A) / master ("anti-`Double` para dinero"): prohibir `Double`/
`Float` como tipo de dinero en el código de dominio de los módulos nuevos. detekt ya está en el catálogo
(lib `detekt-formatting`, plugin `detekt`) pero **no aplicado** todavía.

**Resolución (orquestador):** se implementa un guard FUNCIONANDO en Plan 1, no un placeholder. Preferencia:
regla custom de detekt (`NoDoubleForMoney`) SI detekt 1.23.7 resulta compatible con Kotlin 2.0.21 en la
práctica. Si da fricción real de compatibilidad, usar el fallback documentado abajo: una tarea Gradle de
verificación basada en grep, acotada a los packages de dominio/dinero. El enforcement completo sobre value
objects de dinero llega en Plan 5 (cuando el código de dinero aparece por primera vez) — aquí basta con que
el mecanismo elegido quede verde. El implementador decide cuál de los dos mecanismos usar según cuál
efectivamente compile y pase, y **documenta la elección y el motivo en el reporte de la tarea**. Esto ya no
es una decisión abierta: ambos caminos están aprobados, se usa el que funcione.

**Enfoque (decisión a confirmar por el orquestador — ver más abajo):**
- **Primario:** aplicar detekt a `:core:common` (y a `:app` con baseline para no explotar con legacy) vía un
  convention plugin nuevo `msp.detekt` en `build-logic` **o** `alias(libs.plugins.detekt)` por módulo, con una
  **regla custom `NoDoubleForMoney`** (extender `io.gitlab.arturbosch.detekt.api.Rule`; visitar `KtProperty`/
  `KtNamedFunction`/`KtParameter` cuyo nombre matchee `/(monto|importe|saldo|precio|total|abono|pago|money|amount)/i`
  y cuyo tipo sea `Double`/`Float` → reportar). Registrar el `RuleSetProvider` en un pequeño artefacto de
  build o en `:core:common` con `detektPlugins(...)`.
- **Fallback documentado** (si el ruleset custom con detekt 1.23.7 + toolchain fija da fricción): una **tarea
  Gradle de verificación** que `grep`ee `: Double`/`: Float` en los packages de dinero de los módulos nuevos y
  falle el build. Menos elegante pero cero riesgo de incompatibilidad de API de detekt.

**Archivos:**
- `build-logic/.../DetektConventionPlugin.kt` (id `msp.detekt`) **o** aplicación directa por módulo.
- `config/detekt/detekt.yml` (config base) + baseline para `:app` (`config/detekt/baseline.xml`) para que el
  legacy no bloquee.
- La regla custom + su registro.

**Test primero (TDD):** un archivo-fixture de test que declare `val monto: Double` y afirmar (test JVM de la
regla detekt, o un test de la tarea Gradle) que **detekt/tarea FALLA** sobre ese fixture, y que un `BigDecimal`/
tipo money dedicado **pasa**. La regla debe tener su propio test (rojo→verde).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:detekt        # verde sobre el código real
./gradlew detekt                     # con baseline en :app
./gradlew ktlintCheck testDevlocalDebugUnitTest
```

**Gotchas:**
- Alcance: aplicar la regla-dura a **módulos nuevos**; en `:app` usar baseline (hay Double legacy). No romper
  el build por deuda preexistente.
- Verificar compatibilidad detekt 1.23.7 ↔ Kotlin 2.0.21 (Plan 0 dejó la versión; si hay warning de versión de
  Kotlin no soportada, documentarlo — detekt suele funcionar con un warning).

**Commit:** `build: regla detekt anti-Double para dinero`

---

## Task 10 — Hook pre-push + tarea Gradle agregada de gate (todos los módulos)

**Meta:** un gate **pre-push** que corra lint+tests+cobertura+detekt **sobre TODOS los módulos**, conservando
el `pre-commit` existente (ktlint + `testDevlocalDebugUnitTest`). El repo usa **git hooks planos** en
`.git/hooks/` (no lefthook): el `pre-commit` actual es un shell script ahí. Como `.git/hooks/` **no se versiona**,
se añade una fuente **versionada** + paso de instalación + una **tarea Gradle agregada** que el hook invoca.

**Archivos:**
- `gradle` (raíz `build.gradle.kts` o un `check`-aggregate): tarea `prePushCheck` que dependa de:
  `ktlintCheck`, `testDevlocalDebugUnitTest` (:app), `:core:common:testDebugUnitTest`,
  `:core:testing:testDebugUnitTest`, `:core:common:koverVerify`, `detekt`, y `:app:assembleDevlocalDebug`.
  (Cuando lleguen más módulos en planes siguientes, se suman aquí.)
- `scripts/git-hooks/pre-push` (versionado):
  ```sh
  #!/bin/sh
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  echo "pre-push: gate completo (todos los modulos)…"
  ./gradlew prePushCheck --quiet || { echo "pre-push FALLÓ. Corregí antes de push."; exit 1; }
  echo "pre-push OK"
  ```
- `scripts/install-git-hooks.sh` (versionado) que copie/enlace `scripts/git-hooks/*` a `.git/hooks/` y haga
  `chmod +x`. Documentar en el reporte que hay que correrlo una vez (y que el `pre-commit` existente se
  conserva).
- Instalar el hook en `.git/hooks/pre-push` para esta máquina.

**Test primero (TDD):** no aplica test unitario; la "prueba" es ejecutar el gate y el hook:
- Correr `./gradlew prePushCheck` y confirmar `BUILD SUCCESSFUL`.
- Simular el hook: `sh scripts/git-hooks/pre-push` (con el árbol limpio) → OK; y con un test roto temporal →
  el hook aborta con código ≠ 0 (revertir el break después).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew prePushCheck
sh scripts/git-hooks/pre-push
```

**Gotchas:**
- **NUNCA** `--no-verify`. El hook es defensa; si estorba, se arregla el código, no se saltea.
- `koverVerify` global aún no aplica a `:app` (legacy sin cobertura) — el gate exige Kover **solo** donde hay
  código nuevo (`:core:common`).
- Recordar que `prePushCheck` NO debe incluir tareas `connected*` (device) — el e2e device es Plan 2/5.

**Resolución (orquestador):** confirmado — `prePushCheck` NO agrega un gate de cobertura repo-wide sobre
`:app`. El `koverVerify` que entra al gate agregado es únicamente `:core:common:koverVerify` (~90% domain).
El `:app` legacy queda fuera de cualquier umbral de cobertura hasta que se migre feature por feature.

**Commit:** `build: hook pre-push con gate agregado de todos los modulos`

---

## Cierre de Plan 1 (auditoría de conformidad)

- [ ] `:core:common` existe, aplica `msp.android.library/test/kover` + ktlint, con namespace propio.
- [ ] `:core:testing` existe, expone `MainDispatcherRule`/`RobolectricTestBase` + fakes compartidos + config
      Roborazzi; `:app` y `:core:common` lo consumen por `testImplementation(project(":core:testing"))`.
- [ ] **Outbox endurecido:** dominio `pendingwork` (models/ports/usecases) promovido a `:core:common`;
      adapters WorkManager/synchronizers/gate/observer/factory siguen en `:app`; tests de invariante ack/retry
      verdes; `:app → :core:common` (sin ciclo).
- [ ] **Salud de sync:** `SyncHealth` + `SyncHealthSource` (estado, sin UI) con tests; base del sync pill.
- [ ] Hilt: `@HiltAndroidApp` en `MspApplication` (preserva `applicationScope` + `onCreate`),
      `@AndroidEntryPoint` en `MainActivity`, `HiltWorkerFactory` + inicializador WorkManager por defecto
      removido (workers legacy siguen por fallback), módulos `@Provides` envolviendo
      `ApiProvider`/`V2ApiProvider`/`ApiProviderImages`/`ConnectivityMonitor` **preservando el rebuild de
      baseURL por Firestore**.
- [ ] Feature `Warehouse*` inyectado por Hilt (`@HiltViewModel`), 6 call sites en `hiltViewModel()`, con test
      de ViewModel usando fake (sin MockK) + smoke de paridad de instancia compartida.
- [ ] Regla **anti-`Double`** para dinero activa en módulos nuevos (baseline en `:app`).
- [ ] **Gate pre-push** (ktlint + tests de todos los módulos + `koverVerify` de `:core:common` + detekt +
      assemble) versionado + instalado; `pre-commit` conservado; sin `--no-verify`.
- [ ] **App corre idéntica** (login/biometría, Home, ventas/carrito con almacenes, sync de pendientes, sync
      periódica de clientes) — solo cambió el andamiaje interno.
- [ ] Commits por tarea, conventional, en español, sin atribución de Claude, sin push, rama
      `feat/multimodulo-cimiento`.

### Decisiones resueltas (orquestador)
1. **`RoomTestBase`/`TestDataFactory` NO se mueven a `:core:testing` en Plan 1** — dependen de `AppDatabase`/
   entities de `:app` que no salen hasta Plan 2 (`:core:database`); moverlos ahora crea un ciclo. Solo
   `MainDispatcherRule`/`RobolectricTestBase` (framework-only) se mueven ahora; ver Task 2.
2. **Gate de cobertura Kover:** el ~80% del master NO aplica al `:app` legacy (sin tests); el umbral ~90% de
   Kover en este plan queda acotado al domain de `:core:common`. NO se agrega gate de cobertura repo-wide
   sobre `:app`; la cobertura de `:app` se resuelve por feature migrado más adelante. Ver Task 4 y Task 10.
3. **Anti-`Double`:** se implementa un guard funcionando ya en Plan 1 — regla custom detekt
   (`NoDoubleForMoney`) si detekt 1.23.7 es compatible con Kotlin 2.0.21, o si no, el fallback de tarea
   Gradle con grep acotado a domain/dinero; el implementador usa el que efectivamente pase y lo documenta.
   Enforcement completo sobre value objects de dinero llega en Plan 5. Ver Task 9.
4. **`HiltWorkerFactory`:** agregar `Configuration.Provider` en `MspApplication` y remover el
   `WorkManagerInitializer` por defecto van en la MISMA tarea (uno sin el otro duplica la inicialización de
   WorkManager). Los 7 workers existentes NO se convierten a `@HiltWorker` en Plan 1 — mantienen su
   constructor por defecto y siguen funcionando por fallback reflexivo. Ver Task 6.

**Nota:** la pregunta original sobre `msp.hilt` re-aplicando KSP en `:app` (¿convive con el
`alias(libs.plugins.ksp)` ya presente, o conviene aplicar Hilt directo?) no forma parte de este lote de
rulings y sigue sin resolución explícita del orquestador. Task 5 ya trae una salida contingente documentada
("si choca, aplicar Hilt directo... y documentarlo") que el implementador puede seguir sin bloquearse; no se
elimina esta pregunta, queda pendiente de confirmación si se quiere fijar por adelantado.
