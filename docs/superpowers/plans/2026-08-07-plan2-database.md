# Plan 2 — `:core:database` (hoist de `AppDatabase` + Room safety)

Parte del plan maestro `2026-08-07-plan-maestro-multimodulo.md` (spec fuente:
`docs/superpowers/specs/2026-08-07-migracion-arquitectura-msp-app-kt.md`, §9.3 y §12; recon:
`.superpowers/research/current-architecture.md`). Continúa donde terminó **Plan 1**
(`2026-08-07-plan1-cimiento.md`): ya existen `build-logic` (convention plugins
`msp.android.library/compose/hilt/test/kover`), `:core:common`, `:core:testing`, Hilt encendido en
`:app` (`@HiltAndroidApp`, `HiltWorkerFactory`, `NetworkModule`/`ConnectivityModule` `@Provides`), y el
gate anti-`Double` + pre-push. Este plan **hoistea la `AppDatabase` monolítica (v27) a su propio módulo
`:core:database`**, instala la **red de seguridad de esquema** (`exportSchema` + `schemas/` versionado +
harness de migración que prueba que **no se pierden pagos no subidos**), expone **DB + DAOs por Hilt**, y
migra la capa de datasources a **DAOs inyectados** — todo dejando la **app idéntica** y `msp_db` intacto.

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`): implementador TDD →
> gate real → 2 revisores (uno adversarial: verifica que los tests asserten de verdad) → fix-loop, una
> tarea a la vez. Reglas comunes de despacho: `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`.

---

## Global Constraints (vinculan a TODA tarea de este plan)

- **Toolchain FIJA, no cambiar:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11 (`jvmTarget=11`, desugaring on), Compose BOM 2024.09.00, Gradle wrapper 8.11.1.
- **`JAVA_HOME` en CADA comando gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  Correr **UN** solo comando gradle a la vez (build lock).
- **Variante de gate:** `devlocalDebug` (unit test de `:app` = `testDevlocalDebugUnitTest`). `:core:*` son
  librerías sin flavors → su unit test es `:core:<x>:testDebugUnitTest`.
- **Paquete/`applicationId` `com.example.msp_app` NO se toca** (UpdateChecker/Firestore atados al package).
  El módulo nuevo usa namespace **`com.example.msp_app.core.database`**; el package Kotlin de la DB pasa de
  `com.example.msp_app.data.local.*` a `com.example.msp_app.core.database.*`. El `applicationId` de `:app`
  **no** cambia.
- **ROOM SCHEMA = CONTRATO DE LA DB — INTOCABLE EN EL MOVE.** No se sube la versión (v27 sigue v27), no se
  altera **ninguna** entidad, `@DatabaseView`, índice, ni objeto `Migration`. El move debe ser
  **idéntico en comportamiento y esquema** (DDL byte-idéntico). El único cambio permitido a `AppDatabase.kt`
  es el package + activar `exportSchema = true`. **Si algo parece exigir un cambio de esquema, STOP →
  reportar BLOCKED**, no adivinar.
- **Money-path (crux del plan):** el harness de migración debe **probar que las filas de pago no subidas
  sobreviven** (oracle real: `PaymentDao.getPendingPayments()` = `WHERE GUARDADO_EN_MICROSIP = 0`). Cero
  pérdida de datos. Cualquier cambio que altere el pipeline de pagos/outbox/WorkManager → BLOCKED.
- **Una sola conexión a `msp_db`.** Debe existir **exactamente una** instancia de `AppDatabase` en runtime.
  El `@Provides` de Hilt **delega en `AppDatabase.getInstance(...)`** (no construye un `Room.databaseBuilder`
  nuevo) para no abrir una segunda conexión al mismo archivo y para preservar `setInstanceForTesting`.
- **`getInstance`/`setInstanceForTesting`/`clearInstance` se PRESERVAN** con semántica idéntica. Son el
  puente legacy mientras las features migran a Hilt en planes posteriores; **NO se eliminan en Plan 2**
  (ver Task 9 y "Decisiones resueltas (orquestador)" abajo).
- **Hilt:** módulo nuevo usa el convention plugin `id("msp.hilt")` (aplica KSP + hilt-android + compiler).
  Room añade sus propias deps + `ksp(libs.androidx.room.compiler)`. `DatabaseModule` provee DB
  (`@Provides @Singleton`) + cada DAO (`@Provides`). ViewModels/repos migrados reciben DAOs inyectados.
- **Tests:** dobles = **fakes únicamente** (estado + recording/spy). **CERO MockK/Mockito.** + Turbine +
  `kotlinx-coroutines-test`. Robolectric para DAO/migración JVM; `MigrationTestHelper` (`room-testing`,
  ya en el catálogo). Kover en el módulo nuevo (piso placeholder de `msp.kover`; subir cuando entre dominio).
- **Commits por tarea**, conventional commits, subject en **español**, **SIN atribución de Claude**,
  **SIN `--no-verify`**, **sin push**. Rama: `feat/multimodulo-cimiento`.
- **Código en inglés; strings de usuario en español**, minimalistas. Datos de test con nombres realistas
  mexicanos (ej. `NOMBRE_CLIENTE = "María López"`).

### Orden y su justificación (leer antes de empezar)
El grafo físico obliga este orden. Primero se levanta el **esqueleto** de `:core:database` (Task 1) para
aislar "¿el módulo compila?" de "¿el move es correcto?". Luego el **hoist atómico** de la DB (Task 2): la
`@Database` no se puede mover a medias, así que el traslado + reescritura de imports (~110 archivos main +
~36 de test) va en **una** tarea verificada por el compilador, conservando `getInstance` como puente. Recién
con la DB en su módulo se cablea **`DatabaseModule`** (Task 3) y se instala el **harness de esquema/dinero**
(Task 4) — la red que este plan existe para plantar. Después se **mueve `RoomTestBase`** (Task 5, desbloqueado
por Task 2) y se **migran los datasources a DAOs inyectados en 3 lotes por área** (Tasks 6-8), empezando por
el dinero, cada lote un diff revisable. El cierre (Task 9) documenta los `getInstance` residuales que
pertenecen a features aún no migradas, corre el gate completo + el **smoke en dispositivo**. Cada tarea deja
el build **verde** y la app **idéntica**.

### Comando de gate (por tarea, ajustando el alcance)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew ktlintCheck
./gradlew :core:database:testDebugUnitTest        # cuando exista
./gradlew testDevlocalDebugUnitTest               # :app (imports re-apuntados)
./gradlew :core:common:testDebugUnitTest :core:testing:testDebugUnitTest
./gradlew :app:assembleDevlocalDebug
```

---

## Task 1 — Crear módulo `:core:database` (esqueleto verde)

**Meta:** que `:core:database` exista como Android-library, aplicando los convention plugins, con Room y
`exportSchema`/`schemaLocation` ya configurados, compilando con una clase placeholder — **sin mover la DB
todavía**. Aísla "¿el módulo se levanta con Room + Hilt + KSP conviviendo?" del hoist real (Task 2).

**Archivos a crear:**
- `settings.gradle.kts` (raíz) → añadir `include(":core:database")` junto a los otros `include(...)`.
- `core/database/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.library")
      id("msp.hilt")               // KSP + hilt-android + ksp(hilt-compiler)
      id("msp.test")               // DESPUÉS de msp.android.library
      id("msp.kover")
      alias(libs.plugins.ktlint)
  }
  android {
      namespace = "com.example.msp_app.core.database"
  }
  // Room exporta el esquema a un dir versionado (contrato de la DB). KSP recibe
  // el room.schemaLocation; el `schemas/` se commitea (Task 2 genera el 27.json).
  ksp {
      arg("room.schemaLocation", "$projectDir/schemas")
  }
  dependencies {
      implementation(libs.bundles.room)          // room-runtime + room-ktx
      ksp(libs.androidx.room.compiler)
      // room-testing (MigrationTestHelper) para los tests del propio módulo:
      testImplementation(libs.androidx.room.testing)
      testImplementation(project(":core:testing"))
  }
  ```
- `core/database/src/main/kotlin/com/example/msp_app/core/database/.gitkeep` (o el placeholder del test).
- `core/database/src/main/AndroidManifest.xml` — solo si el build lo pide (mínimo `<manifest/>`).

**Test primero (TDD):** un test-placebo mínimo en `:core:database` (`ModuleSmokeTest` afirmando `2 + 2 == 4`,
o mejor una util neutra si el revisor la prefiere) que pruebe que la toolchain de test del módulo (JUnit vía
`msp.test`) arranca. Rojo→verde. El contenido real llega con la DB en Task 2.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```
Los tres `BUILD SUCCESSFUL`. `:app` sigue idéntico (aún no depende de `:core:database`).

**Gotchas:**
- `msp.hilt` + Room ambos usan KSP: `msp.hilt` hace `pluginManager.apply("com.google.devtools.ksp")`; NO
  volver a aplicar el alias de KSP en este módulo (doble-apply). Room añade solo `ksp(room.compiler)` como
  dependencia, no el plugin. (Es el caso inverso a `:app`, donde se aplicó Hilt directo por el conflicto —
  aquí `msp.hilt` es el camino intencionado para módulos nuevos.)
- `msp.test` va DESPUÉS de `msp.android.library` (toma `CommonExtension.testOptions`).
- Con `exportSchema` activo pero sin `@Database` todavía, Room no genera JSON aún — normal; no falla.
- El `msp.kover` deja piso placeholder; no exigir umbral en este módulo hasta que entre dominio (no aplica
  aquí: la DB es infra Room, no dominio con reglas — cobertura pragmática, ver Task 4).

**Commit:** `feat(core-database): crear modulo base :core:database con Room y schema export`

---

## Task 2 — Hoist atómico de `AppDatabase` (v27) a `:core:database` + reescribir imports

**Meta:** mover `AppDatabase`, las **15 entidades**, la **`@DatabaseView`** (`OverduePaymentsEntity` +
`OVERDUE_PAYMENTS_VIEW_SQL`), los **12 DAOs** y las **7 migraciones** desde `app/.../data/local/` a
`:core:database` (package `com.example.msp_app.core.database.*`), hacer que `:app` **dependa** de
`:core:database`, y **reescribir los ~110 imports de main + ~36 de test**. `getInstance`/
`setInstanceForTesting`/`clearInstance` se conservan intactos. Esquema **byte-idéntico** (v27→v27). Al
terminar se **genera y commitea** el `27.json` exportado.

**Qué se MUEVE** (a `core/database/src/main/kotlin/com/example/msp_app/core/database/`):
- `data/local/AppDatabase.kt` → `core/database/AppDatabase.kt` (package nuevo; `exportSchema = true` en la
  anotación `@Database`).
- `data/local/entities/**` (15 archivos, incl. `OverduePaymentsEntity.kt` con su `@DatabaseView` y la
  const `OVERDUE_PAYMENTS_VIEW_SQL`) → `core/database/entities/**`.
- `data/local/dao/**` (12 DAOs en subpaquetes `cobranzasync/guarantee/localsale/payment/product/
  productInventory/productInventoryImage/sale/visit` + `ClienteDao.kt`) → `core/database/dao/**`.
- `data/local/migrations/**` (`Migration20to21`…`Migration26to27`, 7 archivos) → `core/database/migrations/**`.

**Qué NO se mueve (se queda en `:app`, son consumidores de la DB, no la DB):**
- `data/local/datasource/**` (los `*LocalDataSource` — se migran a DAOs inyectados en Tasks 6-8).
- `data/local/repository/**` (`ClienteRepository`, `UsersRepository`).
- Sus imports de `data.local.dao.*` / `data.local.entities.*` pasan a `core.database.*` como parte de este
  move (reescritura de imports).

**Cambios de wiring:**
- `app/build.gradle.kts` → `implementation(project(":core:database"))`.
- **Reescribir imports** en todo `:app` (main+test) y en `:core:common`/`:core:testing` si referencian la DB:
  `com.example.msp_app.data.local.entities.` → `com.example.msp_app.core.database.entities.`,
  `...data.local.dao.` → `...core.database.dao.`,
  `...data.local.migrations.` → `...core.database.migrations.`,
  `...data.local.AppDatabase` → `...core.database.AppDatabase`.
  (Recetar con `git mv` para preservar historial + `grep -rl` + `sed -i ''` acotado a esas 4 rutas de
  import; el compilador es el verificador — si compila y los tests pasan, la reescritura es correcta.)

**Test primero (TDD):** este move NO añade lógica; los tests que lo prueban **ya existen** y deben quedar
verdes tras la reescritura de imports: los DAO tests (`LocalSaleDaoTest`, `PaymentDaoCollapseTest`,
`VisitDaoDeleteUploadedRegressionTest`, etc. — vía `RoomTestBase`), los de integración (`ContadoSaleE2ETest`,
`CreditoSaleE2ETest`, …) y la suite completa de `:app`. Antes de mover: correr la suite para baseline verde;
después: correr en `:app` + `:core:database`. **No** reescribir lógica de la DB ni de las migraciones.
Adicional: un test trivial en `:core:database` que instancie `AppDatabase` in-memory y afirme `version == 27`
(prueba que la `@Database` compila y vive en su nuevo hogar).

**Generar + commitear el schema:** tras el primer build exitoso del módulo con `@Database`, Room escribe
`core/database/schemas/com.example.msp_app.core.database.AppDatabase/27.json`. **Commitear ese JSON** (es el
contrato). Verificar que el `.gitignore` NO lo excluya.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :core:common:testDebugUnitTest :core:testing:testDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
git status --porcelain core/database/schemas   # el 27.json debe aparecer y commitearse
```

**Gotchas:**
- `OVERDUE_PAYMENTS_VIEW_SQL` es la **fuente única** de la SQL de la vista, usada por el `@DatabaseView` **y**
  por migraciones — deben moverse **juntos** y seguir apuntando a la misma const (si se separan, drift de
  whitespace rompe la validación de esquema).
- `fallbackToDestructiveMigrationFrom(1..19)` + `addMigrations(20→27)` deben copiarse **exactos** en el
  `getInstance` movido. Un cambio aquí = riesgo de pérdida de datos → BLOCKED.
- El package con backticks NO aplica aquí (la DB no vivía en `test-fixtures`); los subpaquetes de DAO se
  conservan (`dao/localsale`, `dao/payment`, …) bajo `core.database.dao.*`.
- Verificar que ningún archivo que se queda en `:app` sea importado desde `:core:database` (rompería el
  grafo `:app → :core:database`, nunca al revés).
- El worktree `.worktrees/test-suite/` tiene una copia vieja de estos archivos — **ignorarlo**, no está en
  el build de la rama.

**Commit:** `refactor(core-database): hoist de AppDatabase v27 (entidades, DAOs, migraciones, vista)`

---

## Task 3 — `DatabaseModule` de Hilt (DB `@Singleton` + los 12 DAOs)

**Meta:** exponer por Hilt la `AppDatabase` y sus 12 DAOs, **delegando en `getInstance`** para no abrir una
segunda conexión a `msp_db` y preservar `setInstanceForTesting`. Es el binding que las Tasks 6-8 (datasources
inyectados) y los futuros `@HiltViewModel`/`@HiltWorker` consumen.

**Decisión del orquestador (YA RESUELTA — strangler-fig, no reabrir):** `getInstance` **NO se elimina** en
Plan 2. `DatabaseModule.provideAppDatabase` **DELEGA** en `AppDatabase.getInstance(context)` — nunca un
`Room.databaseBuilder` nuevo — de modo que exista **exactamente una** conexión a `msp_db` y
`setInstanceForTesting` siga alcanzando el grafo de Hilt (mantiene verde el e2e de pagos existente). El
criterio de cierre "DAOs inyectados" se da por **cumplido** con la capa de datasource/repositorio +
`DatabaseModule` inyectados (Tasks 6-8); los ~7 callers residuales de `getInstance` (ViewModels legacy vía
`viewModel()`, un worker aún no `@HiltWorker`, providers de sesión/cobranza) quedan **documentados como deuda
rastreada** propiedad de sus planes futuros por feature — no se fuerza su migración en este plan.

**Archivos a crear (en `:core:database`, package `com.example.msp_app.core.database.di`):**
- `DatabaseModule.kt` — `@Module @InstallIn(SingletonComponent::class) object DatabaseModule`:
  ```kotlin
  @Provides
  @Singleton
  fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
      AppDatabase.getInstance(context)   // MISMA instancia que el puente legacy; NO databaseBuilder nuevo

  @Provides fun providePaymentDao(db: AppDatabase): PaymentDao = db.paymentDao()
  @Provides fun provideSaleDao(db: AppDatabase): SaleDao = db.saleDao()
  // … los 12: productDao, visitDao, guaranteeDao, productInventoryDao,
  //   productInventoryImageDao, localSaleDao, localSaleProduct (→ LocalSaleProductDao),
  //   localSaleComboDao, clienteDao, cobranzaSyncStateDao
  ```
  Los `@Provides` de DAO **sin** `@Singleton` (el `@Singleton` está en la DB; los DAOs son baratos y Room ya
  los memoiza internamente — replicar el patrón de `NetworkModule` que razona el scoping explícitamente).

**Test primero (TDD):** `@HiltAndroidTest` (Robolectric, `HiltAndroidRule`) que:
1. Inyecte `AppDatabase` + un par de DAOs (`PaymentDao`, `SaleDao`) y afirme que **no son nulos** (los
   bindings resuelven).
2. **Money-safety de instancia única:** afirme que la `AppDatabase` inyectada es **la misma referencia** que
   `AppDatabase.getInstance(context)` (`assertSame`) — prueba que Hilt no abre una segunda conexión.
3. Con `setInstanceForTesting(inMemory)` **antes** de inyectar, afirme que la DB inyectada **es** la
   in-memory (prueba que el override de test sigue alcanzando al grafo Hilt).
   (Requiere `HiltTestApplication` + `hilt-android-testing` — ya en el catálogo/`:app`; para `:core:database`
   añadir `testImplementation(libs.hilt.android.testing)` + `kspTest(libs.hilt.compiler)` si el arnés JVM lo
   necesita. Si el arnés Hilt-en-JVM resulta pesado, aceptar como mínimo un test que construya el módulo y
   llame los `@Provides` a mano sobre una DB in-memory, afirmando que devuelven DAOs no-nulos.)

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Gotchas:**
- **NO** construir un `Room.databaseBuilder` en el `@Provides`: abriría una segunda conexión al archivo
  `msp_db` (dos rutas de escritura al dinero → locking/corrupción) y `setInstanceForTesting` no lo
  interceptaría. Delegar siempre en `getInstance`.
- El DAO de productos-de-venta se llama `localSaleProduct()` (no `localSaleProductDao()`) en `AppDatabase` —
  respetar el nombre exacto del método al proveerlo.
- Este módulo aún NO tiene consumidores en runtime (los datasources se inyectan en Tasks 6-8); el binding se
  valida por el test, no por uso todavía.

**Commit:** `feat(core-database): DatabaseModule de Hilt (DB singleton + 12 DAOs)`

---

## Task 4 — Harness de migración + prueba money-safety (no se pierden pagos no subidos)

**Meta:** instalar la **red de seguridad** que este plan existe para plantar (spec §12, master Checklist
"Room safety"): (a) guardia de **drift de esquema** sobre v27 usando el `27.json` exportado; (b) **la prueba
crux**: las filas de pago **no subidas** (`GUARDADO_EN_MICROSIP = 0`) **sobreviven** al pasar por el pipeline
de migraciones/reapertura tras el move de módulo; (c) **smoke de las 7 migraciones** 20→27. Esto es lo más
importante del plan — el dinero no se pierde.

**Archivos a crear (en `core/database/src/test/kotlin/.../database/migration/`):**
- `SchemaIntegrityTest.kt` — con `MigrationTestHelper` apuntando al `schemas/` del módulo:
  `helper.createDatabase(TEST_DB, 27).close()` + `helper.runMigrationsAndValidate(TEST_DB, 27, true)` →
  falla si el esquema real de v27 diverge del `27.json` commiteado. **Guarda contra drift futuro** (y prueba
  que el move no alteró el esquema).
- `PaymentSurvivalMigrationTest.kt` — **LA prueba crux (money-path):**
  1. Construir la DB **real** con la config de producción (`Room.databaseBuilder` con las 7 migraciones y el
     `fallbackToDestructiveMigrationFrom(1..19)`, o `MigrationTestHelper` desde la primera versión con schema
     disponible — sin reconstruir JSONs históricos v20–v26, ver "Decisión del orquestador" abajo), en un
     archivo temporal.
  2. Insertar N filas de `PaymentEntity` con `GUARDADO_EN_MICROSIP = false` (pagos capturados **sin subir**),
     nombres realistas (`NOMBRE_CLIENTE = "María López"`, importes reales), cerrar.
  3. Reabrir a través de la misma ruta de migración/config y afirmar: `PaymentDao.getPendingPayments()`
     devuelve **exactamente** esas N filas, con `ID`, `IMPORTE`, `PAGO_RECIBIDO_ID` y `GUARDADO_EN_MICROSIP`
     intactos. Cero pérdida, cero mutación.
- `MigrationSmokeTest.kt` — aplica `MIGRATION_20_21`…`MIGRATION_26_27` **en secuencia** sobre una DB sembrada
  por `execSQL` crudo al esquema de partida y afirma que cada una ejecuta **sin error SQL** y que una tabla
  portadora de pagos sigue consultable al final.

**Decisión del orquestador (YA RESUELTA — no reabrir):** los JSONs de esquema históricos v20–v26 **NO se
reconstruyen** (`exportSchema` estuvo apagado en esas versiones; prod ya corre v27; el valor de una
reconstrucción cara/propensa a error para esquemas que ya no se escriben es bajo). La red de seguridad de
dinero para las 7 migraciones existentes se logra con estos dos tests, sin JSONs históricos:
`PaymentSurvivalMigrationTest` (DB real, prueba que las filas no subidas sobreviven el move/reapertura desde
v27) + `MigrationSmokeTest` (semilla por `execSQL` crudo, corre las 7 migraciones sin error). `exportSchema`
queda **ON desde v27 en adelante**, así que las migraciones **futuras** (28+) sí tendrán validación
por-versión estricta con `MigrationTestHelper` contra el JSON commiteado. No se invierte en reconstruir
JSONs pasados.

**Test primero (TDD):** los tres archivos SON los tests (rojo→verde); no hay código de producción nuevo salvo
utilidades de test. Escribir primero `PaymentSurvivalMigrationTest` (la de mayor valor) y hacerla pasar antes
de las otras dos.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest        # Robolectric/JVM (decisión del orquestador: sin device aquí)
./gradlew ktlintCheck
```

**Gotchas (decisión del orquestador ya resuelta — ver "Decisiones resueltas (orquestador)" al cierre):**
- **No existen JSONs de esquema históricos (v20–v26):** `exportSchema` estuvo apagado hasta este plan, así que
  solo se puede exportar el **v27 actual**. `MigrationTestHelper.createDatabase(name, startVersion)` y
  `runMigrationsAndValidate(name, targetVersion, ...)` **requieren el JSON de esa versión** — por eso la
  validación por-versión con helper solo es posible de v27 en adelante (protege migraciones **futuras** 28+).
  Para las **7 migraciones existentes**, el orquestador **RESOLVIÓ no reconstruir los JSONs históricos**
  (caro, propenso a error, bajo valor con prod ya en v27) y cubrir la money-safety con dos caminos:
  (i) `PaymentSurvivalMigrationTest` construye la DB real y prueba **supervivencia de datos** de punta a punta
  (el gate duro de money-safety), y (ii) `MigrationSmokeTest` siembra el esquema de partida por `execSQL`
  crudo y corre los `Migration` en secuencia afirmando que no lanzan. Esto se considera **suficiente**; no
  reabrir esta decisión dentro de Plan 2.
- **Las pruebas de migración corren en Robolectric/JVM, NO en device** (decisión del orquestador): usar el
  runner Robolectric de room-testing para `SchemaIntegrityTest`, `PaymentSurvivalMigrationTest` y
  `MigrationSmokeTest`. El device se reserva **exclusivamente** para el smoke e2e de pagos al cierre
  (Task 9), no para el harness de migración.
- **Anti-`Double` (regla del gate de Plan 1):** `PaymentEntity.IMPORTE` es `Double` legacy y **no se toca** en
  este plan (schema intocable); la regla anti-`Double` aplica a **money VOs nuevos** (Plan 5), no a las
  entidades Room existentes. Si detekt marca el fixture de test, cubrir con el baseline de `:app`/config, no
  cambiar la entidad.
- No usar INSERTs a la DB compartida real: los tests usan archivos temporales/in-memory que se limpian.

**Commit:** `test(core-database): harness de migracion + prueba de supervivencia de pagos no subidos`

---

## Task 5 — Mover `RoomTestBase` a `:core:testing` (desbloqueado por el hoist)

**Meta:** ahora que `AppDatabase` vive en `:core:database`, `RoomTestBase` (que Plan 1 dejó deliberadamente en
`:app` por depender de `AppDatabase`) puede subir a `:core:testing` y ser compartido por los tests de `:app`
**y** de `:core:database`. **`TestDataFactory` se queda en `:app`** (justificación abajo).

**Decisión (grafo de dependencias) y justificación:**
- **`RoomTestBase` → `:core:testing`.** Solo depende de `RobolectricTestBase` (ya en `:core:testing`),
  `androidx.room.Room` y `AppDatabase` + `setInstanceForTesting`/`clearInstance` (ahora en `:core:database`).
  `:core:testing` puede añadir `api(project(":core:database"))` sin ciclo (`:core:database` main NO depende de
  `:core:testing`). Es infra de test genérica que pertenece con las otras bases ya centralizadas ahí.
- **`TestDataFactory` se QUEDA en `:app`.** Referencia `com.example.msp_app.core.draft.SaleDraft` y
  `com.example.msp_app.data.models.productInventory.ProductInventory`, tipos que viven en `:app` y **no** son
  parte de `:core:database`. Moverlo obligaría a arrastrar tipos de `:app` a un módulo core (ciclo /
  scope-creep). Solo re-apunta su import de entidades a `core.database.entities.*`.
- **Alternativa rechazada:** `testFixtures` de `:core:database`. AGP 8.x soporta `testFixtures` pero añade
  fricción de configuración y `RoomTestBase` es infra de test transversal (no específica de la DB), así que
  encaja mejor junto a `RobolectricTestBase`/`MainDispatcherRule` en `:core:testing`.

**Archivos:**
- **Mover** `app/src/test/java/com/example/msp_app/test-fixtures/RoomTestBase.kt` →
  `core/testing/src/main/kotlin/com/example/msp_app/core/testing/RoomTestBase.kt` (package
  `com.example.msp_app.core.testing`; desaparece el package con backticks `` `test-fixtures` ``).
- `core/testing/build.gradle.kts` → `api(project(":core:database"))` + (si hace falta en compilación)
  `api(libs.androidx.room.ktx)` para `androidx.room.Room` en el source set main.
- **Re-apuntar imports** en los ~25 tests de `:app` que hacen
  `import com.example.msp_app.`test-fixtures`.RoomTestBase` → `com.example.msp_app.core.testing.RoomTestBase`
  (lista: DAO tests, `CobranzaReconcilerTest`/`CobranzaSyncManagerTest`/`CobranzaSyncReenqueueTest`,
  `integration/*E2ETest`, `workers/Pending*V2Test`, `ProductDetailsViewModelTest`,
  `RoomUploadFailureRepositoryTest`, etc.).
- `TestDataFactory` **no se mueve**; solo se re-apunta su import de entidades a `core.database.entities.*`
  (ya hecho en Task 2 si se incluyó en la reescritura; verificar).

**Test primero (TDD):** un test en `:core:testing` que ejercite `RoomTestBase` desde su nuevo hogar (una
subclase mínima que construya la DB in-memory, inserte y lea una fila vía un DAO, y afirme el round-trip) →
prueba que la base movida funciona. Luego correr la suite completa de `:app` con los imports re-apuntados.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:testing:testDebugUnitTest
./gradlew :core:database:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew ktlintCheck
```

**Gotchas:**
- `:core:testing` depender de `:core:database` significa que `:core:common` (que usa `:core:testing` en test)
  ve `:core:database` transitivamente — inofensivo, pero confirmar que no introduce ciclo
  (`:core:database` main NO debe depender de `:core:common`/`:core:testing`).
- `RoomTestBase` sigue llamando `AppDatabase.setInstanceForTesting`/`clearInstance` — companion functions de
  la clase movida; import desde `core.database`.
- No mover `TestDataFactory` "de paso": rompería el grafo. Documentar la desviación deliberada en el reporte
  (igual que Plan 1 documentó el diferimiento).

**Commit:** `refactor(core-testing): mover RoomTestBase a :core:testing (post-hoist de la DB)`

---

## Task 6 — Datasources inyectados: LOTE 1 (dinero / captura)

**Meta:** migrar los datasources del **camino de dinero/captura** a **DAOs inyectados** (`@Inject
constructor(dao)`), preservando un constructor `context` secundario que delega en `getInstance` para los
callers legacy (`viewModel()`/workers aún no-Hilt). Comportamiento **idéntico**; e2e de pagos **verde**.
Primero el dinero, con la mayor cautela.

**Archivos a migrar (2 archivos, 4 sitios `getInstance`):**
- `data/local/datasource/payment/PaymentsLocalDataSource.kt` (`paymentDao` + `saleDao`).
- `data/local/datasource/visit/VisitsLocalDataSource.kt` (`visitDao` + `saleDao`).

**Patrón de migración (behavior-idéntico):**
```kotlin
class PaymentsLocalDataSource @Inject constructor(
    private val paymentDao: PaymentDao,
    private val saleDao: SaleDao,
) {
    // Puente legacy: los callers `viewModel()`/worker no-Hilt siguen construyendo
    // con context sin cambios. Delega en la MISMA instancia (getInstance).
    constructor(context: Context) : this(
        AppDatabase.getInstance(context).paymentDao(),
        AppDatabase.getInstance(context).saleDao(),
    )
    // … cuerpo intacto (mismas queries, misma lógica)
}
```

**Test primero (TDD):** un test JVM (Robolectric, `RoomTestBase` desde `:core:testing`) que construya
`PaymentsLocalDataSource` **por el constructor de DAOs** con DAOs de una DB in-memory, inserte pagos con
`GUARDADO_EN_MICROSIP = false` y afirme que `getPendingPayments()`/la operación real devuelve lo esperado —
prueba que la forma inyectable es equivalente. Los tests existentes que construyen por `context`
(`PaymentDaoCollapseTest`, workers V2, e2e) deben seguir verdes sin cambios (prueban el puente).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```
Los `workers/PendingPaymentsWorkerV2Test`, `PendingVisitsWorkerV2Test` y (en device, Task 9) los e2e de
pagos deben permanecer verdes — es el gate de que el camino de dinero no cambió.

**Gotchas:**
- **Decisión del orquestador (YA RESUELTA):** `getInstance` no se elimina en Plan 2 (strangler-fig); **no**
  convertir aquí los ViewModels/workers que construyen estos datasources a `@HiltViewModel`/`@HiltWorker` —
  eso es per-feature, planes posteriores. El constructor `context` los mantiene funcionando y satisface el
  criterio "DAOs inyectados" ya en la capa de datasource.
- Ambos constructores deben resolver a la **misma** instancia de DB (getInstance) — no introducir un builder
  nuevo en el secundario.
- `@Inject` requiere que `:app` tenga Hilt (ya, Plan 1) y que `DatabaseModule` (Task 3) provea los DAOs — sin
  eso el grafo no resuelve cuando un `@HiltViewModel` futuro lo pida (hoy nadie lo inyecta aún; el test lo
  construye directo, así que basta con que compile).

**Commit:** `refactor(app): PaymentsLocalDataSource y VisitsLocalDataSource con DAOs inyectados`

---

## Task 7 — Datasources inyectados: LOTE 2 (ventas / localsale)

**Meta:** mismo patrón que Task 6 para los datasources del área de **ventas locales**.

**Archivos a migrar (4 archivos, 4 sitios):**
- `data/local/datasource/sale/SalesLocalDataSource.kt` (`saleDao`).
- `data/local/datasource/sale/LocalSaleDataSource.kt` (`localSaleDao`).
- `data/local/datasource/sale/SaleProductLocalDataSource.kt` (`localSaleProduct()`).
- `data/local/datasource/sale/ComboLocalDataSource.kt` (`localSaleComboDao`).

**Patrón:** `@Inject constructor(dao)` + `constructor(context) : this(AppDatabase.getInstance(context).xDao())`.

**Test primero (TDD):** para cada datasource, un test (o extender los existentes) que lo construya por el
constructor de DAO con una DB in-memory y afirme una operación real (insert+read). Los tests de integración
de ventas (`ContadoSaleE2ETest`, `CreditoSaleE2ETest`, `EditSaleE2ETest`, `SaleCreationIntegrationTest`,
`AddProductToSaleWithComboTest`, etc.) siguen verdes por el puente `context`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Gotchas:**
- **Decisión del orquestador (YA RESUELTA):** `getInstance` no se elimina en Plan 2 (strangler-fig); los
  residuales que requieran `@HiltViewModel`/`@HiltWorker` son deuda documentada de planes futuros, no de
  este lote.
- `SaleProductLocalDataSource` usa el método `localSaleProduct()` (nombre sin sufijo `Dao`) — respetar.
- `EditLocalSaleViewModel` construye `localSaleDao` **directo** vía `getInstance` (no vía datasource) — ese
  sitio NO se toca en este lote (es un `getInstance` residual documentado en Task 9; convertirlo exige
  `@HiltViewModel`, plan posterior).

**Commit:** `refactor(app): datasources de ventas locales con DAOs inyectados`

---

## Task 8 — Datasources inyectados: LOTE 3 (catálogo / clientes / garantías)

**Meta:** mismo patrón para los datasources restantes (lectura-pesada, menor riesgo).

**Archivos a migrar (5 archivos, 5 sitios):**
- `data/local/datasource/product/ProductsLocalDataSource.kt` (`productDao`).
- `data/local/datasource/productInventory/ProductsInventoryLocalDataSource.kt` (`productInventoryDao`).
- `data/local/datasource/productInventoryImage/ProductInventoryImageLocalDataSource.kt`
  (`productInventoryImageDao`).
- `data/local/datasource/ClienteDataSource.kt` (`clienteDao`).
- `data/local/datasource/guarantee/GuaranteesLocalDataSource.kt` (`guaranteeDao`).

**Patrón:** idéntico a Tasks 6-7 (`@Inject constructor(dao)` + puente `context`).

**Test primero (TDD):** por datasource, construirlo con DAO de DB in-memory y afirmar una operación real.
`ProductDetailsViewModelTest` y demás existentes siguen verdes por el puente.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Gotchas:**
- **Decisión del orquestador (YA RESUELTA):** `getInstance` no se elimina en Plan 2 (strangler-fig); los
  residuales de este lote quedan documentados como deuda de planes futuros, no se fuerza su conversión aquí.
- `ProductsInventoryImagesViewModel` construye `productInventoryDao` directo vía `getInstance` — **residual**,
  no se toca (Task 9).
- Confirmar con `grep -rn "AppDatabase.getInstance" app/src/main` que, tras este lote, los únicos
  `getInstance` restantes son los **residuales documentados** (ver Task 9) + los constructores `context`
  secundarios de los datasources.

**Commit:** `refactor(app): datasources de catalogo/clientes/garantias con DAOs inyectados`

---

## Task 9 — Cierre: `getInstance` residual documentado + smoke en dispositivo + gate completo

**Meta:** cerrar el plan de forma **honesta**: `getInstance` **NO se elimina** (varios callers pertenecen a
features/workers/session aún no migrados a Hilt — su conversión es de planes posteriores), pero se **reduce a
la superficie mínima** (constructores `context` de datasources + residuales explícitos) y se **documenta**.
Correr el **gate completo de todos los módulos** + el **smoke en dispositivo** (`connectedDevlocalDebug…`).

**Decisiones del orquestador (YA RESUELTAS — no reabrir en esta tarea):**
- `getInstance` se preserva por diseño (strangler-fig): el criterio "DAOs inyectados" del plan se cumple con
  `DatabaseModule` + la capa de datasource/repositorio (Tasks 3, 6-8); los ~7 callers residuales listados
  abajo son deuda **rastreada y aceptada**, propiedad de sus planes futuros por feature — no bloquean el
  cierre de Plan 2.
- Las pruebas de migración/esquema (Task 4) corrieron en Robolectric/JVM; el **único** uso de dispositivo en
  todo Plan 2 es el smoke e2e de pagos de esta tarea de cierre (`connectedDevlocalDebugAndroidTest`).

**`getInstance` residual (se DEJA en pie en Plan 2, cada uno con su plan dueño):**
- `features/productsInventoryImages/viewmodels/ProductsInventoryImagesViewModel.kt` → requiere `@HiltViewModel`
  (migración de su feature).
- `features/sales/viewmodels/EditLocalSaleViewModel.kt` → requiere `@HiltViewModel` (Bloque 2 VENTAS).
- `features/dailyReport/data/repository/DailyReportRepository.kt` (x2) → feature `dailyReport` de inventario
  (NO el `collectionReport` de Plan 5); su plan dueño.
- `workers/PendingLocalSalesWorker.kt` → requiere `@HiltWorker` (Plan 1 difirió conversión de workers).
- `core/sync/cobranza/CobranzaSyncProvider.kt`, `CobranzaReconcilerProvider.kt` → bootstrap de sesión cableado
  en `AppNavigation` (composition root / módulo `:session` futuro).
- `core/debug/DebugCommandExecutor.kt` → herramienta de debug.
- Constructores `context` secundarios de los 11 datasources (Tasks 6-8) — puente legacy intencional.

**Acciones:**
- Añadir a `docs/superpowers/plans/2026-08-07-plan2-database.md` (o al `NIGHT-REPORT.md`) la lista anterior
  como **deuda rastreada** ("residual getInstance callers, deferred to owning plan"), para que no se pierda.
- Sumar `:core:database:testDebugUnitTest` (y, si aplica, `:core:database:connectedDebugAndroidTest` de
  migración) a la tarea agregada `prePushCheck` del gate (Plan 1, Task 10).
- **Smoke en dispositivo** (master §Verificación E2E + Plan 2 Done): correr `connectedDevlocalDebugAndroidTest`
  en UN emulador headless (`Pixel_9_Pro`), incluyendo los e2e de pagos (`PendingPaymentsWorkerE2ETest`,
  `CobranzaDurableQueueE2ETest`) que ejercen `setInstanceForTesting` + el camino de dinero de punta a punta.

**Test primero (TDD):** no aplica test unitario nuevo; la "prueba" es el gate agregado + el smoke device en
verde, y un `grep` que confirme que no quedan `getInstance` **directos** fuera de la lista residual.

**Verificación (gate completo del plan):**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew ktlintCheck
./gradlew testDevlocalDebugUnitTest
./gradlew :core:common:testDebugUnitTest :core:testing:testDebugUnitTest :core:database:testDebugUnitTest
./gradlew :core:common:koverVerify
./gradlew detekt
./gradlew :app:assembleDevlocalDebug
./gradlew assembleDevserverRelease
./gradlew connectedDevlocalDebugAndroidTest        # un emulador; e2e pagos verde
git grep -n "AppDatabase.getInstance" app/src/main # solo residuales documentados + ctor context
```

**Gotchas:**
- **NO** forzar la eliminación de `getInstance`: hacerlo arrastraría conversión a Hilt de ViewModels/workers/
  session que son alcance de planes posteriores y rompería la regla "app idéntica". Si alguien pide "quitar el
  shim del todo" en Plan 2 → responder que está fuera de alcance por diseño (documentado aquí).
- El smoke device puede fallar por contención de daemon si otro gradle corre en el mismo checkout — reintentar
  en daemon limpio antes de reportar defecto (ver DISPATCH-CONVENTIONS).
- `connected*` NO va en `prePushCheck` (device); se corre a mano al cierre del plan (como Plan 1 acordó).

**Commit:** `chore(core-database): cierre Plan 2 — gate agregado, smoke device y deuda de getInstance`

---

## Cierre de Plan 2 (auditoría de conformidad)

- [ ] `:core:database` existe (namespace `com.example.msp_app.core.database`), aplica `msp.android.library/
      hilt/test/kover` + ktlint, con Room + `ksp room.schemaLocation`.
- [ ] `AppDatabase` v27 (15 entidades, `@DatabaseView` `OverduePaymentsEntity` + `OVERDUE_PAYMENTS_VIEW_SQL`,
      12 DAOs, 7 migraciones) **movida** a `:core:database`; `:app → :core:database` (sin ciclo). Esquema
      **byte-idéntico** (v27→v27); `fallbackToDestructiveMigrationFrom(1..19)` + `addMigrations(20→27)`
      intactos.
- [ ] `getInstance`/`setInstanceForTesting`/`clearInstance` **preservados**; una **sola** conexión a `msp_db`.
- [ ] **Room safety:** `exportSchema = true` + `schemas/…/27.json` **commiteado** + `SchemaIntegrityTest`
      (drift guard sobre v27).
- [ ] **Money-safety (crux):** `PaymentSurvivalMigrationTest` prueba que los pagos **no subidos**
      (`GUARDADO_EN_MICROSIP = 0`) sobreviven la migración/reapertura y siguen en `getPendingPayments()`;
      `MigrationSmokeTest` corre las 7 migraciones sin error.
- [ ] `DatabaseModule` de Hilt provee la DB (`@Singleton`, delegando en `getInstance`) + los 12 DAOs, con
      test de que la DB inyectada `assertSame` a `getInstance` y que `setInstanceForTesting` sigue
      alcanzando el grafo.
- [ ] **DAOs inyectados:** los 11 datasources migrados a `@Inject constructor(dao)` + puente `context` (Lotes
      dinero → ventas → catálogo), comportamiento idéntico.
- [ ] `RoomTestBase` movido a `:core:testing` (`api(project(":core:database"))`, sin ciclo);
      `TestDataFactory` justificadamente en `:app` (deps de `:app`); ~25 tests re-apuntados y verdes.
- [ ] `getInstance` residual documentado como deuda rastreada (features/workers/session de planes
      posteriores); ningún `getInstance` directo nuevo fuera de esa lista.
- [ ] **App corre idéntica**; e2e de pagos verde en device (`connectedDevlocalDebugAndroidTest`); `msp_db`
      intacto.
- [ ] Gate: `ktlintCheck` + unit de todos los módulos + `koverVerify` (`:core:common`) + `detekt` +
      `assembleDevserverRelease` verdes; commits por tarea, conventional, en español, sin atribución de
      Claude, sin push, rama `feat/multimodulo-cimiento`.

### Decisiones resueltas (orquestador)
1. **Los JSONs de esquema históricos v20–v26 NO se reconstruyen** (`exportSchema` estuvo apagado, prod ya en
   v27, bajo valor); la money-safety para las 7 migraciones existentes se cubre con
   `PaymentSurvivalMigrationTest` (DB real, prueba supervivencia de filas no subidas) +
   `MigrationSmokeTest` (siembra por `execSQL` crudo, corre las 7 migraciones sin error) — ver Task 4;
   `exportSchema` queda ON desde v27 en adelante para validar por-versión las migraciones futuras.
2. **`getInstance` NO se elimina en Plan 2** (strangler-fig): `DatabaseModule.provideAppDatabase` delega en
   `AppDatabase.getInstance` (nunca un `databaseBuilder` nuevo) para mantener una sola conexión a `msp_db` y
   que `setInstanceForTesting` siga alcanzando el grafo Hilt; "DAOs inyectados" se cumple con la capa de
   datasource/repositorio + `DatabaseModule`, y los ~7 callers residuales (ViewModels legacy `viewModel()`,
   un worker aún no `@HiltWorker`, providers de sesión/cobranza) quedan documentados como deuda rastreada de
   sus planes futuros por feature — ver Tasks 3, 6-8 y 9.
3. **Las pruebas de migración corren en Robolectric/JVM, no en device**; el dispositivo se reserva
   exclusivamente para el smoke e2e de pagos de cierre — ver Tasks 4 y 9.

## Cierre Task 9 (ejecutado 2026-08-09)

### `getInstance` residual — deuda rastreada, propiedad de planes futuros por feature

`git grep -n "AppDatabase.getInstance" app/src/main` a HEAD (`72fe6eb` + este commit) devuelve **solo** la
lista siguiente — ningún `getInstance` directo nuevo fuera de ella:

- `features/productsInventoryImages/viewmodels/ProductsInventoryImagesViewModel.kt` — requiere
  `@HiltViewModel` (migración de su feature).
- `features/sales/viewmodels/EditLocalSaleViewModel.kt` — requiere `@HiltViewModel` (Bloque 2 VENTAS).
- `features/dailyReport/data/repository/DailyReportRepository.kt` (x2 usos) — feature `dailyReport` de
  inventario (NO el `collectionReport` de Plan 5); su plan dueño.
- `workers/PendingLocalSalesWorker.kt` — requiere `@HiltWorker` (Plan 1 difirió conversión de workers).
- `core/sync/cobranza/CobranzaSyncProvider.kt`, `CobranzaReconcilerProvider.kt` — bootstrap de sesión
  cableado en `AppNavigation` (composition root / módulo `:session` futuro).
- `core/debug/DebugCommandExecutor.kt` — herramienta de debug.
- Constructores `context` secundarios de los 11 datasources migrados en Tasks 6-8 (`ClienteDataSource`,
  `GuaranteesLocalDataSource`, `PaymentsLocalDataSource`, `ProductsLocalDataSource`,
  `ProductsInventoryLocalDataSource`, `ProductInventoryImageLocalDataSource`, `ComboLocalDataSource`,
  `LocalSaleDataSource`, `SaleProductLocalDataSource`, `SalesLocalDataSource`, `VisitsLocalDataSource`) —
  puente legacy intencional, documentado en el KDoc de cada uno.

Ninguno se elimina en Plan 2: hacerlo forzaría migración a Hilt de ViewModels/workers/session, fuera de
alcance (ver decisión resuelta #2 arriba). Cada ítem queda propiedad de su plan por feature futuro.

### Gate agregado — `:core:database` en `prePushCheck`

Ya estaba resuelto: la tarea de cierre de Plan 1/fechas (Task 13) sumó `:core:database:ktlintCheck` y
`:core:database:testDebugUnitTest` (y `:core:database:detekt`) a `tasks.register("prePushCheck")` en
`build.gradle.kts` (raíz), con el comentario explícito referenciando este plan. No se duplicó nada; se
verificó por lectura directa del bloque `dependsOn(...)`.

### Gate completo — resultado real (2026-08-09)

Todos verdes, corridos uno a la vez (build lock), `JAVA_HOME` = Android Studio JBR:

| Comando | Resultado |
|---|---|
| `ktlintCheck` | BUILD SUCCESSFUL |
| `testDevlocalDebugUnitTest` | BUILD SUCCESSFUL |
| `:core:common:testDebugUnitTest :core:testing:testDebugUnitTest :core:database:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:core:common:koverVerify` | BUILD SUCCESSFUL |
| `detekt` | BUILD SUCCESSFUL |
| `:app:assembleDevlocalDebug` | BUILD SUCCESSFUL |
| `assembleDevserverRelease` | BUILD SUCCESSFUL |

### Smoke en dispositivo — `connectedDevlocalDebugAndroidTest`

Un solo emulador headless `Pixel_9_Pro` (`emulator -no-window -no-audio -no-boot-anim -gpu
swiftshader_indirect`), boot confirmado por `sys.boot_completed=1`. Resultado: **10/10 tests, 0
failures, 0 errors** en el primer intento (sin necesidad de reintento por contención de daemon):

- `CobranzaDurableQueueE2ETest.durableQueueSurvivesZoneChangeThenDrainsToServer`
- `CobranzaSelfHealTwinE2ETest` (3 tests)
- `PendingPaymentsWorkerE2ETest` (3 tests: happy path, idempotencia, red caída nunca marca done)
- `PendingVisitsWorkerE2ETest` (3 tests: happy path, idempotencia, red caída nunca marca done)

Emulador apagado al terminar (`adb emu kill`), confirmado sin `qemu-system` corriendo y `adb devices`
vacío. `msp_db` de producción no se tocó (el smoke usa `setInstanceForTesting` con Room in-memory /
`RoomTestBase`, no el archivo real del dispositivo).

Cierre de Plan 2: completo.
