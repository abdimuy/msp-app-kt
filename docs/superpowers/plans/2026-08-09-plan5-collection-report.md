# Plan 5 — `:feature:collectionReport` (EL piloto: reporte de cobranza rediseñado)

Parte del plan maestro `2026-08-07-plan-maestro-multimodulo.md`. Specs fuente:
`docs/superpowers/specs/2026-08-07-reporte-cobranza-rediseno-design.md` (§4 = la pantalla del reporte; el doc
entero para tokens/componentes) y el **mockup, FUENTE DE VERDAD VISUAL**:
`docs/design/reporte-cobranza-mockup.html`. La pantalla del piloto debe verse **EXACTAMENTE** como el mockup
(RESUME-HERE §Plan 5 fidelidad; memoria `feedback_reporte_cobranza_fidelidad_mockup`).

Este plan **crea el módulo `:feature:collectionReport`** (namespace `com.example.msp_app.feature.collectionreport`):
dominio (rangos Día/Semana = ciclo del cobrador, agregados, meta sugerida, VOs de dinero **sin `Double`**), datos
(puertos pagos/visitas/condonaciones sobre Room vía `:core:database`, transfers por contrato), y la pantalla
Compose `@HiltViewModel` que reproduce el mockup pixel-a-idea (toggle Día/Semana, hero + sparkline embebida, duo
Efectivo/Transferencia, chips, lista/resumen, bottom sheets, barra difuminada, ocultar cifras, reveal de tema,
Tier 1/2). Extrae la pantalla de `payments/screens/`, **re-apunta `Screen.DailyReport.route` conservando el
literal `"daily_reports"`**, absorbe `WeeklyReport`, y **NO toca `features/dailyReport`** (el reporte de
inventario del carrito, módulo distinto y homónimo). Cartera/zona = **Fase 2** (no se cablea aquí).

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`): implementador TDD →
> gate real → revisores (uno adversarial que verifica que los tests asserten de verdad; money/UI-fidelidad =
> 2 revisores) → fix-loop, una tarea a la vez. Reglas comunes de despacho:
> `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`.

## Dependencias de secuencia (leer antes de empezar)

**Plan 5 NO arranca hasta que Plan 3 y Plan 4 estén 100% verdes y mergeados a `feat/multimodulo-cimiento`.**
El piloto consume:
- `:core:designsystem` (Plan 3): `MspTheme`, `MspHeroTodayCard`, `MspBentoTile`, `MspWeeklyBarsCard`,
  `MspMoneyText`/`formatMoneyMxn`/`MASKED_MONEY`, `MspPrivacyEyeToggle`, `MspThemeToggle`+`ThemeRevealController`
  (+`LocalThemeReveal`, `maxDistanceToCorner`), `MspSegmentChips`, `MspSyncBand`/`MspPaymentSyncPill`,
  `MspInitialsAvatar`, `MspPrimaryFieldButton`, `BrandGradient`/`brandGradientBackground`,
  `MspProgressBar`/`MspProgressRing`, `MspStatusChip`/`ChipStatus`, `MspSurface`/`MspCard`,
  `rememberReducedMotionEnabled()`, `MspTheme.{colors,type,spacing,shapes,motion}`. **Se referencian por nombre;
  no se redefinen aquí.**
- `:core:common` (Plan 1/fechas): `AppClock`, `AppTime` (zona negocio `America/Mexico_City`), utilidades de
  rango de fecha half-open. Es la ÚNICA fuente de fechas.
- `:core:database` (Plan 2): DAOs de pagos/visitas inyectados por Hilt (esquema Room v27 **INMUTABLE**).
- `:core:network` (Plan 4): base Retrofit/OkHttp inyectada (solo si transfers-por-contrato toca red).
- `:core:telemetry` (Plan 4): puerto `Telemetry` + `Modifier.trackClick`/`ScreenScope`.
- `:core:testing`: fakes, `RobolectricTestBase`, `RoomTestBase`, `MainDispatcherRule`, `FakeClock`,
  `RoborazziConfig.CHANGE_THRESHOLD`.

---

## Global Constraints (vinculan a TODA tarea de este plan)

- **Toolchain FIJA, no cambiar:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11 (`jvmTarget=11`, desugaring on), Compose BOM 2024.09.00, Gradle wrapper 8.11.1.
- **`JAVA_HOME` en CADA comando gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  Correr **UN** solo comando gradle a la vez (build lock).
- **Variante de gate:** `:feature:collectionReport` es Android-library sin flavors → sus unit test/screenshot
  corren en `debug`: `:feature:collectionReport:testDebugUnitTest`,
  `:feature:collectionReport:recordRoborazziDebug` / `:feature:collectionReport:verifyRoborazziDebug`. El e2e de
  dispositivo corre sobre `:app` en `devlocalDebug` (`connectedDevlocalDebugAndroidTest`).
- **Paquete/`applicationId` `com.example.msp_app` NO se toca** (UpdateChecker/Firestore atados al package). El
  módulo nuevo usa namespace **`com.example.msp_app.feature.collectionreport`**; todo el código bajo
  `com.example.msp_app.feature.collectionreport.*` (subpaquetes `domain/`, `data/`, `ui/`, `di/`).
- **Nombre del módulo = `:feature:collectionReport`. NUNCA `dailyReport`** — `features/dailyReport` YA existe y es
  el **reporte de inventario del carrito** (`GenerateDailyReportUseCase`, `DailyReportRepository`), módulo
  distinto que **no se toca**. Confirmado: `app/src/main/java/com/example/msp_app/features/dailyReport/{data,domain,presentation}`.
- **Anti-`Double` para dinero (detekt `NoDoubleForMoney`, activo vía `msp.detekt`):** ningún parámetro,
  propiedad o retorno de dinero es `Double`/`Float`. El dominio introduce el **VO `Money` (envuelve
  `java.math.BigDecimal`)** — es la representación money del proyecto. `MspMoneyText`/`formatMoneyMxn` reciben
  `BigDecimal` (vía `Money.amount`). El `IMPORTE: Double` del `PaymentEntity` (schema v27 inmutable) se lee y se
  **convierte a `BigDecimal` en el adapter de datos** (`BigDecimal.valueOf(importe)` con escala 2), nunca se
  propaga `Double` al dominio/UI. **NO reusar `getAdjustedPaymentPercentage(): Double`** para montos.
- **POLÍTICA DE MIGRACIÓN (AUDITAR + REESCRIBIR, no mover a ciegas):** el código de `payments/screens/` NO se
  confía. Al absorberlo: (1) auditar bugs, (2) verificar el contrato del API/Room (formatos, **fechas RFC3339
  UTC**; cruzar el backend Go en `/Volumes/M2-1TB/Developer/msp-api` si algún request/response se toca — el
  piloto es casi todo lectura Room-local, así que probablemente NO toca red), (3) reescribir limpio con **tests
  de robustez SUPREMA**, (4) review. Money-path (montos/rangos/settlement) que cambie de comportamiento =
  **char-test old→new documentado** (corrección de bug consciente), no accidente. Si no puedes verificar un
  contrato → reportar **BLOCKED**.
- **Rango half-open `[desde, hasta)` en zona negocio (bug crítico del plan de fechas — honrarlo):** todos los
  rangos de pagos/visitas/condonaciones usan fin **exclusivo** (`< :end`), con `end = startOfNextDay(hoy)` en
  `America/Mexico_City` vía `AppClock`/`AppTime`. Los DAO ya filtran `< :end` (hecho en fechas). Un `now()` crudo
  como fin **subcuenta** transitoriamente (el pago recién guardado, truncado a segundos, no es `< now.millis`
  bajo colación BINARY de SQLite). Reusar el patrón existente `ReportFormatters.dateRangeFor(...).endIso` /
  `startOfNextDay(today)` — reescrito limpio en el dominio del módulo.
- **Kill-switch baseURL:** NO `@Singleton` sobre nada que sostenga un API service de `ApiProvider.create()`
  (congela y rompe el kill-switch de baseURL de Firestore). ViewModels `@HiltViewModel`; puertos/repos/datasources
  de red SIN scope (o `Provider<T>`). `ConnectivityMonitor` sí puede ser `@Singleton`.
- **`msp.hilt` para el módulo nuevo** (aplica KSP + hilt-android + compiler).
- **Testing:** dobles = **fakes únicamente** (estado + recording/spy). **CERO MockK/Mockito.** + Turbine (Flows)
  + `kotlinx-coroutines-test`. JVM-first: Robolectric (Compose behavior, DAO) + **Roborazzi** (screenshots). UN
  emulador headless (`Pixel_9_Pro`) solo para e2e dispositivo. Cobertura Kover: **dominio ~90%**, app ~80%,
  ui/infra pragmático.
- **Screenshot por Tier × escala:** Tier 1 y Tier 2 × escala de fuente {1.0, 1.3, 2.0} × {light, dark}. "Terminado
  es imposible si el dinero se corta en grande" — el dinero **reflowea, no trunca**.
- **Accesibilidad (spec §5):** contraste AAA en hero/estados críticos, targets 48–56dp, `tnum` en dinero, **nunca
  solo color** (color+ícono+texto), honrar tamaño de fuente/negrita del SO, layouts que refluyen; Tier 1/2 sobre
  el **mismo estado/ViewModel** (solo cambia `ui/`). Todas las animaciones **desactivables** por
  `rememberReducedMotionEnabled()`.
- **Commits por tarea**, conventional commits, subject en **español**, **SIN atribución de Claude**, **SIN
  `--no-verify`**, **sin push**. Rama: `feat/multimodulo-cimiento`.
- **Código en inglés; strings de usuario en español**, minimalistas (2–4 palabras). Datos de test con nombres
  realistas mexicanos (`"María López Hernández"`, `"Gabriel Roque"`, importes reales).

### Contrato de datos actual (auditado — punto de partida del AUDIT+REWRITE)
- Reporte actual (PDF-first) vive en: `features/payments/screens/DailyReportScreen.kt`,
  `screens/WeeklyReportScreen.kt`, `components/weeklyreportcontent/WeeklyReportContent.kt`,
  `components/reportactions/ReportActions.kt`, `components/paymentslist/`, `components/paymentitem/`,
  `models/ReportModels.kt`, `utils/ReportFormatters.kt`, `viewmodels/PaymentsViewModel.kt`.
- **Fuentes de datos (Room vía `PaymentsLocalDataSource` / `VisitStore`):**
  - `getPaymentsByDate(start, end)` — pagos del rango (half-open `< end`).
  - `getForgivenessByDate(start, end)` — condonaciones (método `CONDONACION`).
  - `VisitsViewModel.getVisitsByDate(start, end)` → `VisitStore.getVisitsByDate`.
  - `getPaymentsGroupedByDaySince(startDate)` / `observePaymentsGroupedByDaySince(...)` — tendencia por día del
    ciclo.
  - `getPendingPayments()` = `WHERE GUARDADO_EN_MICROSIP = 0` — **conteo de la sync pill "N por subir"**.
  - `getAdjustedPaymentPercentage(startDate): Double` — **NO usar para dinero** (es `Double`; es porcentaje).
- **`PaymentMethod` (`core/models/PaymentMethod.kt`), por `FORMA_COBRO_ID`:** `PAGO_EN_EFECTIVO`=**157**,
  `PAGO_CON_CHEQUE`=**158**, `PAGO_CON_TRANSFERENCIA`=**52569**, `CONDONACION`=**137026**, `SIN_METODO`=0.
  Efectivo = 157 (+cheque 158 si el revisor confirma que cuenta como efectivo; **parked**, ver Task 3).
- **Ciclo del cobrador (Semana):** `FECHA_CARGA_INICIAL` del doc de usuario Firestore
  (`authViewModel.userData` → `ResultState.Success.data.FECHA_CARGA_INICIAL`;
  `Constants.START_OF_WEEK_DATE_FIELD = "FECHA_CARGA_INICIAL"`). Rango Semana =
  `[díaInicio(FECHA_CARGA_INICIAL), startOfNextDay(hoy))`, variable en días.
- **Ruta:** `Screen.DailyReport("daily_reports")` (AppNavigation.kt:79) + `Screen.WeeklyReport("weekly_reports")`
  (:80); `composable(...)` en :366/:370; drawer navega a ambos (`DrawerContainer.kt:160`/`:173`).
- **Acciones (barra difuminada):** Compartir (share intent) · Imprimir (`ThermalPrinting`) · PDF (`PdfGenerator`
  + `FileProvider`). Preservar comportamiento al reescribir.

### Enumeración del mockup (FUENTE DE VERDAD — ningún elemento se pierde)
Cada elemento debe existir en la pantalla real. El revisor de fidelidad (Task 11) cruza esta lista.

1. **Header (`.hdr`):** ícono menú (☰, abre drawer) · bloque título "**Cobranza**" (21sp/800) + subtítulo
   "Reporte · <cobrador>" (ej. "Gabriel Roque") · `MspPrivacyEyeToggle` (👁, 40dp) · `MspThemeToggle` (🌙, 40dp).
2. **Selector de periodo (`.period`):** pill segmentada "Día" · "Semana" (`MspSegmentChips`); activa = fondo
   surface + texto brand.
3. **Subrow (`.subrow`):** range pill (ícono calendario + texto: Día "viernes 7 ago 2026" / Semana
   "semana · lun 3 – vie 7 ago · 5 días") + `MspPaymentSyncPill` a la derecha (dot ámbar con pulse + "N por subir").
4. **HERO (`.hero`, gradiente 150° plano, radio 22, tappable → sheet "hero"):**
   - overline uppercase "Cobrado · <fecha/ciclo>" (Día "Cobrado · vie 7 ago" / Semana "Cobrado · ciclo actual").
   - **delta chip** a la derecha (Día "▲ 12% vs ayer" / Semana "▲ 6% vs ciclo").
   - **monto grande** display (Día "$18,300" / Semana "$118,400").
   - **frase-insight** (Día "32 pagos · vas al **91%** de tu meta · a este ritmo cierras en **$19,800**" /
     Semana "214 pagos · vas al **91%** de la meta · día 5 de 5 del ciclo").
   - **barra de progreso** mint (`heroProgressFill`), 8dp, fracción = bar% (91%).
   - caption de meta (Día "meta del día $20,000" / Semana "meta de la semana $130,000").
   - **sparkline embebida** (`.spark`): barras por hora en Día (8h–16h: `[30,100,72,58,44,30,22,26,18]`,
     hoy/pico resaltado mint) / por día en Semana (lun–vie: `[74,87,100,89,64]`, hoy=vie index 4); label pequeña
     bajo cada barra; en Semana cada barra es tappable → sheet "day".
   - **wells** (`.wells`): "Efectivo en mano" (Día "$12,100" / Semana "$79,900") · "Ticket prom."
     (Día "$572" / Semana "$553").
5. **Duo de tiles (`.duo`, `MspBentoTile`, tappable → sheet):** Efectivo (dot verde `statusPaid` + "Efectivo" +
   monto "$12,100" + "22 pagos") · Transferencia (dot brand + "Transferencia" + "$6,200" + "10 pagos").
6. **Chips (`.chips`, tappable → sheet):** Condonado (dot ámbar `statusPartial` + "Condonado" + valor ámbar
   "$1,400") · Visitas (dot pending + "Visitas" + "14").
7. **Detail header (`.lhdr`):** label (Día "Pagos del día · 32" / Semana "Resumen por día · 5 días") + segment
   **solo en Día** "Hora" · "Nombre" (`MspSegmentChips`).
8. **Detail block (`.detailBlock`, `.rows` en `MspCard`):**
   - **Día → lista de pagos** (`.prow`, tappable → sheet "pay"): `MspInitialsAvatar` ("ML") + nombre
     ("María López Hernández", 15sp/700) + subline "09:12 · Muebles Bahía" (hora · venta) + a la derecha monto
     ("$1,200") + method pill (`.m.cash` verde "Efectivo" / `.m.tr` brand "Transfer.") + dot "por subir" ámbar si
     no sincronizado. Orden por Hora / Nombre según segment.
   - **Semana → resumen por día** (`.prow`, tappable → sheet "day"): avatar iniciales día ("L3") + nombre del día
     ("lun 3 ago"/"vie 7 ago (hoy)") + subline "39 pagos" + monto del día ("$21,300").
9. **Barra difuminada de acciones (`.actions`, `verticalGradient` transparente→fondo, NO sólida):** Compartir
   (ghost, ícono share) · Imprimir (ghost, ícono printer) · **PDF** (solid brand, ícono file). El scroll lleva
   padding inferior para que el contenido suba sobre la barra.
10. **Bottom sheets (`ModalBottomSheet` + scrim + handle):** por cada card/fila (progressive disclosure nivel 2):
    - **hero** → "Resumen del día/ciclo" · sub "vie 7 ago · meta $20,000" / "lun 3 – vie 7 ago · meta $130,000";
      filas: 📊 Cobrado "91% de la meta" $18,300/$118,400 · ⚡ Ritmo "proyección a cierre" $19,800/— · 🕘 Mejor
      momento "9–10 h"/"miércoles" $4,200 · 🎯 Falta para meta $1,700/$11,600.
    - **efectivo** → "Efectivo" · sub monto·conteo; lista de pagos efectivo (+ "…y más").
    - **transfer** → "Transferencia" · sub monto·conteo; lista de transferencias (con "ref …") (+ "…y más").
    - **condon** → "Condonado" · sub monto; filas nombre / motivo / monto (ej. "Ana Ruiz" "saldo mínimo ·
      autorizado" $600).
    - **visitas** → "Visitas" · sub "N visitas"; filas cliente / nota (ej. "Carlos Vega" "No estaba — dejé
      recado") (+ "…y más").
    - **day** (Semana) → nombre del día · sub monto·conteo; pagos de ese día (+ "…y más").
    - **pay** → "Detalle de pago" · sub "cliente · hora"; filas Importe $1,200 · Forma Efectivo · Venta Muebles
      Bahía · Folio A-10482 · Estado "✔ Sincronizado".
11. **Interacciones firma:** transición de tab Día↔Semana (crossfade + slide direccional ~300ms
    `cubic-bezier(.2,.7,.2,1)`; Día→Semana entra desde la derecha, Semana→Día desde la izquierda); **reveal
    circular de tema** (Telegram-style); **ocultar cifras** (enmascara TODOS los montos a `$••••`); **entrada
    escalonada** de tarjetas (fade+rise ≤500ms). **Todas desactivables** por reduce-motion.

### Comando de gate (por tarea, ajustando el alcance)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:verifyRoborazziDebug      # cuando existan goldens (Task 6+)
./gradlew :feature:collectionReport:detekt
./gradlew ktlintCheck
./gradlew :app:assembleDevlocalDebug                          # :app compila (idéntico salvo el reporte)
```
Grabar/regrabar goldens (cuando cambian a propósito): `./gradlew :feature:collectionReport:recordRoborazziDebug`.

### Orden y su justificación (leer antes de empezar)
El grafo físico obliga: primero el **esqueleto** del módulo + build wiring (Task 1), para aislar "¿el módulo se
levanta con Compose + Hilt + Roborazzi + detekt?" del contenido. Luego el **dominio puro** (Tasks 2–3: VOs de
dinero + rangos/ciclo, después agregados + meta sugerida) porque es JVM-testeable sin Android y fija la verdad de
los números. Luego los **datos** (Task 4: puertos + adapters sobre Room + fakes), después el **ViewModel** (Task
5) que orquesta dominio+datos con estado observable. Recién con estado real se construye la **UI por secciones**
(Tasks 6–8: hero/header, tiles/chips/detalle, sheets/barra) montada sobre los `Msp*` del DS, con Roborazzi por
sección. Después las **interacciones/Tier 2** (Task 9). El **cableado destructivo** (Task 10: re-apuntar la ruta,
absorber WeeklyReport, borrar lo viejo) va tarde, cuando la pantalla nueva existe y compila. El **cierre de
fidelidad** (Task 11) es el gate visual dedicado. Cada tarea deja el build verde y `:app` idéntico salvo lo
migrado.

---

## Task 1 — Crear módulo `:feature:collectionReport` (esqueleto verde + build wiring + prePushCheck)

**Meta:** que `:feature:collectionReport` exista como Android-library con Compose + Hilt, aplicando convention
plugins + ktlint + Roborazzi + detekt, dependiendo de los `:core:*`, con una clase placeholder que compile —
**sin dominio/UI todavía**. Aísla "¿el módulo se levanta?" del contenido (Tasks 2+). `:app` NO depende aún de él.

**Archivos a crear / tocar:**
- `settings.gradle.kts` (raíz) → añadir `include(":feature:collectionReport")` junto a los `include(...)`
  existentes.
- `feature/collectionReport/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.library")
      id("msp.android.compose")
      id("msp.hilt")
      id("msp.detekt")
      id("msp.kover")
      alias(libs.plugins.ktlint)
      alias(libs.plugins.roborazzi)
  }
  android {
      namespace = "com.example.msp_app.feature.collectionreport"
      testOptions { unitTests.isIncludeAndroidResources = true }   // Robolectric ve res + fontScale
  }
  dependencies {
      implementation(project(":core:designsystem"))
      implementation(project(":core:common"))
      implementation(project(":core:database"))
      implementation(project(":core:network"))
      implementation(project(":core:telemetry"))
      implementation(libs.androidx.compose.foundation)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.navigation.compose)      // si el screen expone NavController
      testImplementation(project(":core:testing"))          // fakes + Turbine + Robolectric + roborazzi (api)
      testImplementation(libs.androidx.ui.test.junit4)
  }
  ```
- `feature/collectionReport/src/main/kotlin/com/example/msp_app/feature/collectionreport/.gitkeep`
  (o el placeholder del test).
- `feature/collectionReport/src/main/AndroidManifest.xml` — solo si el build lo pide (mínimo `<manifest/>`).

**prePushCheck (wiring parcial):** añadir al `tasks.register("prePushCheck")` del `build.gradle.kts` raíz las
tareas que YA existen para este módulo: `:feature:collectionReport:ktlintCheck`,
`:feature:collectionReport:testDebugUnitTest`, `:feature:collectionReport:detekt`. (`verifyRoborazziDebug` +
`koverVerify` se suman en la Task 11, cuando ya haya goldens — agregarlo antes haría fallar el gate por falta de
goldens.)

**Test primero (TDD):** un `ModuleSmokeTest` mínimo (`src/test/kotlin/.../ModuleSmokeTest.kt`) que afirme
`2 + 2 == 4` — prueba que la toolchain de test arranca. Rojo→verde.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:detekt
./gradlew ktlintCheck
./gradlew :app:assembleDevlocalDebug
```
Los cuatro `BUILD SUCCESSFUL`. `:app` sigue idéntico (aún no depende del módulo).

**Gotchas:**
- `msp.android.compose` presupone y aplica `msp.android.library`; dejar ambos es idempotente (patrón del repo).
- El plugin Roborazzi registra `record/verifyRoborazziDebug` solo si hay tests con capturas; no invocar
  `verifyRoborazzi` en esta tarea (no hay goldens).
- `unitTests.isIncludeAndroidResources = true` es necesario para que Robolectric honre `fontScale` (Tasks 6+).
- **Parked for user (nombre físico del dir):** Gradle acepta `:feature:collectionReport` con dir
  `feature/collectionReport`. Se usa camelCase en el path para casar el nombre del módulo del máster; si el
  equipo prefiere `feature/collection-report`, es cosmético — no bloquea.

**Commit:** `feat(collection-report): crear modulo :feature:collectionReport con Compose, Hilt y Roborazzi`

---

## Task 2 — Dominio: VO `Money` + rangos Día/Semana (ciclo) half-open

**Meta:** el fundamento numérico y temporal del reporte, JVM-puro y testeable sin Android: el VO de dinero
**sin `Double`**, el enum de periodo, y el cálculo de rangos half-open en zona negocio (incluida la Semana =
ciclo del cobrador `[FECHA_CARGA_INICIAL, ahora]`).

**Archivos (en `domain/`):**
- `domain/model/Money.kt` — `@JvmInline value class Money(val amount: java.math.BigDecimal)` (o `data class` si el
  value class choca con nullability/operadores) con:
  - `companion` `ZERO = Money(BigDecimal.ZERO)`, `fun of(raw: Double): Money = Money(BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP))`
    (**único puente `Double`→money, SOLO en el borde de datos**; documentar con KDoc que el `Double` viene del
    `IMPORTE` del schema v27 inmutable y no puede propagarse).
  - `operator fun plus(o: Money)`, `operator fun minus(o: Money)`, `fun sum(list) `, comparables.
  - **Nunca** expone aritmética en `Double`. `formatMoneyMxn(money.amount)` es el render.
- `domain/model/ReportPeriod.kt` — `enum class ReportPeriod { DIA, SEMANA }`.
- `domain/model/DateRange.kt` — `data class DateRange(val startIso: String, val endExclusiveIso: String)` (wire
  RFC3339 UTC) con helpers de etiqueta.
- `domain/RangeCalculator.kt` — funciones puras (inyectan `AppClock` de `:core:common`):
  - `fun dayRange(clock: AppClock): DateRange` = `[startOfDay(hoy), startOfNextDay(hoy))` en
    `America/Mexico_City`, serializado a wire UTC vía `AppTime.toWireFormat`.
  - `fun cycleRange(clock: AppClock, fechaCargaInicial: Instant?): DateRange` = `[startOfDay(fechaCargaInicial ?:`
    `hoy), startOfNextDay(hoy))`. **Fin EXCLUSIVO** = `startOfNextDay(hoy)` (bug crítico de fechas: `now()` crudo
    subcuenta). Si `fechaCargaInicial` es null → fallback a `dayRange` (ciclo de 1 día), documentado.
  - `fun cycleInfo(...)`: nº de días del ciclo + etiqueta ("semana · lun 3 – vie 7 ago · 5 días") + etiqueta Día
    ("viernes 7 ago 2026") vía `AppTime` (formato es-MX, zona negocio).

**Test primero (TDD, JVM puro con `FakeClock`):** `MoneyTest` (aritmética exacta, escala 2, `of(18300.0) ==`
`Money(BigDecimal("18300.00"))`, sum de lista, sin pérdida de precisión, negativos); `RangeCalculatorTest`
(fin **exclusivo** = `startOfNextDay`; ciclo de N días con `FECHA_CARGA_INICIAL` fija; fallback null; cruce de
medianoche en zona negocio; wire UTC correcto — cruzar con el contrato RFC3339 del backend Go si el wire se
compara). **Char-test:** un pago guardado a las 23:59:59 zona negocio SÍ cae dentro de su propio Día/ciclo (el
bug que motivó el fin exclusivo).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- **Zona negocio SIEMPRE `America/Mexico_City`** vía `AppClock`/`AppTime` de `:core:common`; NUNCA `LocalDate.now()`
  del sistema ni `SimpleDateFormat`.
- Escala 2 fija en `Money` (centavos); `RoundingMode.HALF_UP` en el puente `of(Double)`.
- `value class Money` no puede tener `init{}` con validación pesada; si necesitás validar escala, usá factory.

**Commit:** `feat(collection-report): dominio de dinero (Money/BigDecimal) y rangos Dia/Semana half-open`

---

## Task 3 — Dominio: agregados del reporte + meta sugerida + insight/ritmo

**Meta:** las funciones puras que convierten listas de pagos/visitas/condonaciones (modelos de dominio) en el
estado numérico del tablero: totales, splits, ticket, timeline, delta, meta sugerida y frase-insight. Todo con
`Money` (sin `Double`).

**Archivos (en `domain/`):**
- `domain/model/CollectionPayment.kt` — modelo de dominio del pago (id, cliente, ventaLabel, `Money`, método
  `PaymentMethod`, `Instant`, `synced: Boolean`). `domain/model/CollectionVisit.kt`,
  `domain/model/Forgiveness.kt` (nombre, motivo, `Money`).
- `domain/ReportAggregator.kt` — puro:
  - `total(payments): Money`, `count`.
  - split por método: `efectivo(payments)` = filtro `PAGO_EN_EFECTIVO` (157) → (Money, nº); `transferencia` =
    filtro `PAGO_CON_TRANSFERENCIA` (52569) → (Money, nº); `condonado(forgiveness)` → (Money, nº).
  - `ticketPromedio(payments): Money` = `total / count` (guardas contra count 0; redondeo HALF_UP).
  - `efectivoEnMano(payments): Money` (= total efectivo; documentar si difiere de "en mano").
  - `timeline(payments, period)`: Día → buckets por hora (labels "8h".."16h"); Semana → buckets por día del ciclo
    (labels "lun".."vie"), con índice de "hoy"/pico resaltado.
  - `dailyTrend(groupedByDay)`: para el resumen semanal por día (nombre, total, conteo, iniciales "L3").
  - `delta(current, prior): DeltaChip` = "▲/▼ X% vs ayer" (Día) / "vs ciclo" (Semana). Prior = día anterior /
    ciclo anterior (o del oracle disponible — ver Parked).
  - `progressFraction(total, goal): Float` (0..1, clamp).
  - `insight(period, count, progress, projection)`: frase "N pagos · vas al X% de tu meta · a este ritmo cierras
    en $Y" (Día) / "N pagos · vas al X% de la meta · día D de T del ciclo" (Semana).
  - `mejorMomento(timeline)`: hora/día pico + su Money (para el sheet hero).
- `domain/SuggestedGoal.kt` — `fun suggest(historicalDailyTotals: List<Money>, window: Int = 14): Money` =
  promedio/mediana de los últimos ~14 días (elegir mediana por robustez a outliers; documentar). Meta Semana =
  meta diaria × días del ciclo (o suma de metas — documentar). **Fase 2:** meta fijada por oficina
  (Firestore/msp-api) reemplaza la sugerida — dejar el puerto abierto, no cablear.

**Test primero (TDD, JVM puro):** `ReportAggregatorTest` exhaustivo — totales/splits/ticket con `Money` exacto;
timeline por hora y por día; delta con signo y %; `progressFraction` clamp; insight strings exactas (es-MX);
listas vacías (count 0, ticket $0.00, sin crash). `SuggestedGoalTest` — mediana/promedio de 14 días, ventana <14,
lista vacía. **Char-test money-path:** reproducir los agregados del `DailyReportScreen`/`WeeklyReportContent`
viejos (efectivo/transfer/condon/total) sobre un set fijo de pagos y comparar old (Double `.toInt()`) → new
(`Money`); donde el nuevo corrija truncación/redondeo, documentar el cambio como corrección de bug consciente.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- El viejo hace `sumOf { it.IMPORTE }.toInt()` — **truncaba centavos**. El nuevo usa `Money` exacto; el char-test
  documenta la diferencia (es corrección, no regresión).
- Efectivo = 157. **Parked for user (cheque 158):** el mockup solo muestra "Efectivo"/"Transferencia"; ¿el cheque
  (158) cuenta como efectivo, como su propia línea, o se ignora? Lectura fiel: contar cheque dentro de "Efectivo
  en mano" NO (no es efectivo físico); mostrarlo NO en el duo (el mockup no lo tiene) pero SÍ sumarlo al total
  cobrado. Dejar la clasificación en una sola función testeable; el usuario confirma en review.
- **Parked for user (delta "vs ayer/ciclo"):** el oracle del periodo anterior (día previo / ciclo previo)
  requiere una segunda consulta de rango. Lectura fiel: calcularlo con `getPaymentsByDate(rangoPrevio)`; si el
  costo es alto, degradar a "—" sin romper. No bloquea.

**Commit:** `feat(collection-report): agregados del reporte, meta sugerida e insight (Money)`

---

## Task 4 — Datos: puertos + adapters Room + fakes (AUDIT del contrato)

**Meta:** los puertos outbound del módulo y sus adapters sobre los DAOs de `:core:database`, mapeando entidades
Room (`IMPORTE: Double`) a modelos de dominio (`Money`). AUDITAR las queries actuales; verificar half-open + wire
RFC3339 UTC. Fakes en `:core:testing`-style para tests de app/UI.

**Archivos:**
- `domain/port/CollectionReportPorts.kt` (puertos outbound):
  - `interface PaymentsPort { suspend fun paymentsIn(range: DateRange): List<CollectionPayment>;`
    `suspend fun forgivenessIn(range: DateRange): List<Forgiveness>;`
    `suspend fun paymentsGroupedByDaySince(startIso: String): Map<String, List<CollectionPayment>>;`
    `suspend fun pendingCount(): Int }`
  - `interface VisitsPort { suspend fun visitsIn(range: DateRange): List<CollectionVisit> }`
  - `interface UserCyclePort { suspend fun fechaCargaInicial(): java.time.Instant? ;`
    `suspend fun cobradorNombre(): String }` (lee del auth/userData; **sin `@Singleton` si sostiene red**).
  - `interface HistoricalTotalsPort { suspend fun dailyTotals(days: Int): List<Money> }` (para meta sugerida).
  - **`interface TransfersPort`** (transfers por contrato, cross-módulo `:feature:transfers`) — **ver Parked**;
    definir la interfaz pero cablear solo si el mockup lo exige (no lo exige).
- `data/adapter/RoomPaymentsAdapter.kt`, `RoomVisitsAdapter.kt`, `AuthUserCycleAdapter.kt`,
  `RoomHistoricalTotalsAdapter.kt` — implementan los puertos sobre `PaymentsLocalDataSource`
  (`getPaymentsByDate`/`getForgivenessByDate`/`getPaymentsGroupedByDaySince`/`getPendingPayments`), `VisitStore`
  (`getVisitsByDate`), y `userData`. **Convierten `IMPORTE: Double` → `Money.of(...)` en el borde** (único puente
  Double). Filtran método por `FORMA_COBRO_ID`.
- `di/CollectionReportDataModule.kt` — `@Module @InstallIn(...)` que `@Binds`/`@Provides` los puertos a sus
  adapters. Adapters de red/repo **SIN `@Singleton`** (kill-switch); adapters puramente Room pueden ser sin scope
  también (YAGNI).
- Fakes: `FakePaymentsPort`, `FakeVisitsPort`, `FakeUserCyclePort`, `FakeHistoricalTotalsPort` (estado + spy) —
  en el sourceSet de test del módulo (o `:core:testing` si se comparten).

**AUDIT obligatorio (política de migración):**
- Confirmar que los DAO filtran `< :end` (half-open) — ya arreglado en fechas; si algún query es inclusivo,
  **BLOCKED**.
- Verificar que los strings de fecha que se pasan a los DAO son wire UTC (`AppTime.toWireFormat`) y que la
  comparación SQLite BINARY se comporta con el fin exclusivo. Cruzar con el backend Go
  (`/Volumes/M2-1TB/Developer/msp-api`) SOLO si algún request/response se toca (el piloto es lectura Room-local;
  probablemente no toca red → documentar "sin cambio de contrato de red").
- `getPendingPayments()` = `WHERE GUARDADO_EN_MICROSIP = 0` → `pendingCount()` para la sync pill.

**Test primero (TDD):** Robolectric/`RoomTestBase` para los adapters (insertar pagos con `IMPORTE`,
`FORMA_COBRO_ID`, `FECHA_HORA_PAGO`, `GUARDADO_EN_MICROSIP`; leer por rango; verificar mapeo a `Money` exacto,
filtro de método, half-open en el borde 23:59:59, `pendingCount`). Los fakes se testean vía su propio
comportamiento en Task 5.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- **NO reescribir el schema Room** (v27 inmutable): se reescribe la LÓGICA (mapeo, filtros), no las entidades.
- El `IMPORTE: Double` de Room es el ÚNICO `Double` tolerado, y solo dentro del adapter → `Money.of(...)`
  inmediatamente. Detekt `NoDoubleForMoney` no debe dispararse en dominio/UI.
- **Parked for user (transfers por contrato):** el máster lista "transfers por contrato" separado de
  "pagos/visitas/condonaciones Room". El mockup solo muestra **Transferencia como método de pago** (id 52569,
  Room-local) — NO un traspaso de efectivo a oficina/almacén. Lectura fiel: el duo "Transferencia" = método de
  pago desde Room; se define `TransfersPort` (cross-módulo `:feature:transfers`) por si el reporte debe mostrar
  traspasos, pero **no se cablea** (el mockup no lo pide). Si el usuario quiere el traspaso en el reporte, es una
  extensión documentada.

**Commit:** `feat(collection-report): puertos y adapters de datos sobre Room (mapeo a Money)`

---

## Task 5 — `@HiltViewModel` + estado observable

**Meta:** el `CollectionReportViewModel` que orquesta dominio + puertos en un `StateFlow<CollectionReportUiState>`
que la UI (Tasks 6–9) consume, con eventos (periodo, máscara, tema, sort, abrir sheet). Fakes-only + Turbine.

**Archivos (en `ui/`):**
- `ui/CollectionReportUiState.kt`:
  ```kotlin
  data class CollectionReportUiState(
      val period: ReportPeriod = ReportPeriod.DIA,
      val loading: Boolean = true,
      val cobrador: String = "",
      val rangeLabel: String = "",                 // "viernes 7 ago 2026" / "semana · lun 3 – vie 7 ago · 5 días"
      val pendingCount: Int = 0,                    // sync pill
      val masked: Boolean = false,                  // ocultar cifras
      val darkTheme: Boolean = false,
      val sort: DetailSort = DetailSort.HORA,       // Hora / Nombre (solo Día)
      val hero: HeroUi,                             // overline, delta, monto(Money), insight, progress, goalCap, sparkline, wells
      val efectivo: TileUi, val transferencia: TileUi,
      val condonado: ChipUi, val visitas: ChipUi,
      val detail: DetailUi,                         // Día: List<PaymentRowUi>; Semana: List<DayRowUi>
      val sheet: SheetUi? = null,                   // sheet abierto (hero/efectivo/transfer/condon/visitas/day/pay)
  )
  ```
  (Todos los montos = `Money`, nunca `Double`. Sub-modelos `HeroUi/TileUi/ChipUi/PaymentRowUi/DayRowUi/SheetUi`.)
- `ui/CollectionReportViewModel.kt` — `@HiltViewModel class ... @Inject constructor(paymentsPort, visitsPort,`
  `userCyclePort, historicalTotalsPort, clock: AppClock, telemetry: Telemetry)`:
  - En init / `setPeriod(p)`: calcula el `DateRange` (Task 2) para el periodo, carga pagos/visitas/condonaciones
    (puertos), agrega (Task 3), arma el `UiState`. Semana usa `cycleRange(clock, userCyclePort.fechaCargaInicial())`.
  - `toggleMask()`, `toggleTheme()`, `setSort(sort)`, `openSheet(kind, arg)`, `closeSheet()`.
  - Telemetría: `trackScreen("collection_report")`, eventos de tap (via `:core:telemetry`).
- `ui/CollectionReportEvent.kt` (si se separa) — sealed de intents.

**Test primero (TDD, fakes + Turbine + `MainDispatcherRule`/`FakeClock`):** `CollectionReportViewModelTest` —
estado inicial Día carga hero/tiles/chips/detalle desde fakes; `setPeriod(SEMANA)` recalcula rango de ciclo con
`FakeUserCyclePort.fechaCargaInicial` y cambia labels/detalle a "resumen por día"; `toggleMask()` no toca los
`Money` (la máscara es de render, no de datos — el estado conserva los montos, la UI enmascara); `setSort(NOMBRE)`
reordena; `openSheet/closeSheet` setea/limpia `sheet`; `pendingCount` refleja el fake; lista vacía → estado vacío
sano. Verificar que el `Money` viaja intacto (sin `Double`).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- **Kill-switch:** el ViewModel es `@HiltViewModel` (no `@Singleton`); ningún puerto que sostenga un API service
  lleva `@Singleton`. `UserCyclePort`/auth: sin scope o `Provider<T>`.
- La **máscara** vive en el estado como flag; los `Money` NO se mutan al enmascarar (la UI decide render
  `MASKED_MONEY`). Esto permite des-enmascarar sin recargar.
- El `darkTheme` inicial = `isSystemInDarkTheme()` al componer; el toggle lo maneja el reveal (Task 9). Decidir si
  el tema vive en ViewModel o en el composition root — **preferir el root** (el reveal necesita el frame). El
  ViewModel puede solo reflejar/no dueño; documentar la frontera con Task 9.

**Commit:** `feat(collection-report): CollectionReportViewModel y estado observable (Hilt)`

---

## Task 6 — UI: scaffold + header + periodo + subrow + HERO con sparkline

**Meta:** la mitad superior de la pantalla, montada sobre los `Msp*` del DS, fiel al mockup: header, selector de
periodo, subrow (range pill + sync pill), y el HERO completo (overline + delta + monto + insight + barra + goal +
sparkline embebida + wells). Con transición de tab y entrada escalonada (desactivables).

**Archivos (en `ui/`):**
- `ui/CollectionReportScreen.kt` — `@Composable fun CollectionReportScreen(navController, viewModel: hiltViewModel())`:
  `Scaffold`/`Box` sin TopAppBar M3 (el header es propio). `Column` con `verticalScroll` + `contentPadding`
  inferior (para subir contenido sobre la barra difuminada de Task 8). Fondo `MspTheme.colors.background`.
- `ui/components/ReportHeader.kt` — fila: ícono menú (`MspSurface` 40dp, abre drawer) + bloque
  título `"Cobranza"` (`MspTheme.type.screenTitle`/greeting 21sp) + subtítulo `"Reporte · $cobrador"`
  (`type.subtitle`, muted) + `MspPrivacyEyeToggle(masked, onToggle)` + `MspThemeToggle(darkTheme, onToggle)`.
- `ui/components/PeriodSelector.kt` — `MspSegmentChips(listOf("Día","Semana"), selectedIndex, onSelect)`.
- `ui/components/RangeSubRow.kt` — range pill (`MspTheme.colors.brandTint` bg, ícono calendario + `rangeLabel`) +
  `MspPaymentSyncPill(pendingCount)` alineada a la derecha.
- `ui/components/HeroSection.kt` — `MspHeroTodayCard(...)` (DS) rellenado con `HeroUi`: overline, delta chip,
  monto (`MspMoneyText` `type.amountHero`, `masked`), insight, `MspProgressBar` mint 91%, goalCap, **sparkline**
  (composable propio pasado al slot del hero) y wells. `onClick` → `viewModel.openSheet(HERO)`.
- `ui/components/Sparkline.kt` — la sparkline embebida (el DS deja el contenedor/estilo; el piloto construye las
  barras): `Row` de barras (alto ∝ valor, `rgba(255,255,255,.22)` idle / `heroProgressFill` activo), label
  pequeña bajo cada una; en Semana cada barra `clickable` → `openSheet(DAY, index)`. Respeta reduce-motion (sin
  animación de crecimiento si reducido).
- `ui/components/TabTransition.kt` — wrapper `AnimatedContent`/crossfade + slide direccional ~300ms
  `cubic-bezier(.2,.7,.2,1)`: Día→Semana entra desde la derecha, Semana→Día desde la izquierda. **Desactivable**
  por `rememberReducedMotionEnabled()` (crossfade/instantáneo).
- `ui/components/StaggeredEntrance.kt` — fade+rise ≤500ms escalonado por índice; desactivable.

**Test primero (TDD):**
- Compose-test (Robolectric): el header muestra "Cobranza" + subtítulo; tocar periodo llama `setPeriod`; el hero
  muestra el monto formateado y la barra a la fracción; con `masked=true` el monto es `MASKED_MONEY`.
- Roborazzi baseline (Tier 1, light + dark @1.0): la sección superior (header+periodo+subrow+hero) en Día y en
  Semana. Grabar + verify.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:recordRoborazziDebug
./gradlew :feature:collectionReport:testDebugUnitTest :feature:collectionReport:verifyRoborazziDebug
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- **Regla anti-colapso (spec §6):** las tarjetas en la `Column` con scroll NO reciben `weight` que las comprima;
  usar `wrapContentHeight`. El hero desaparecía en el mockup por eso.
- **Hero = gradiente plano 150°** (`brandGradientBackground`), sin glow.
- La sparkline es del **piloto**, no del DS (el DS solo da el slot/estilo del hero). Alturas del mockup:
  `Math.max(valor*0.4, 6)px` → traducir a dp proporcional.
- Datos del mockup para goldens: Día monto `$18,300`, delta "▲ 12% vs ayer", insight con "$19,800", barra 91%,
  meta `$20,000`, wells `$12,100`/`$572`, spark horas `[30,100,72,58,44,30,22,26,18]`, hoy idx 1.

**Commit:** `feat(collection-report): UI header, selector de periodo, subrow y hero con sparkline`

---

## Task 7 — UI: duo tiles + chips + detalle (lista Día / resumen Semana)

**Meta:** la mitad media del tablero: el duo Efectivo/Transferencia, los chips Condonado/Visitas, el detail header
con segment Hora·Nombre (solo Día), y el bloque de detalle (filas de pago en Día / resumen por día en Semana).

**Archivos (en `ui/components/`):**
- `DuoTiles.kt` — `Row` de dos `MspBentoTile`: Efectivo (dot `statusPaid` verde + "Efectivo" + `MspMoneyText`
  `amountCard` `masked` + "22 pagos") · Transferencia (dot `brand` + "Transferencia" + monto + "10 pagos"). Cada
  tile `onClick` → `openSheet(EFECTIVO/TRANSFER)`.
- `SecondaryChips.kt` — `Row` de dos chips (`MspCard`/`MspSurface` pill-ish): Condonado (dot `statusPartial` ámbar
  + "Condonado" + valor ámbar `masked`) · Visitas (dot `statusPending` + "Visitas" + conteo). `onClick` →
  `openSheet(CONDON/VISITAS)`.
- `DetailHeader.kt` — label (`type.sectionLabel`, "Pagos del día · 32" / "Resumen por día · 5 días") + segment
  **solo en Día** `MspSegmentChips(listOf("Hora","Nombre"), ...)` → `setSort`.
- `DetailList.kt` — `MspCard` con `.rows`:
  - **Día:** por pago, `PaymentRow`: `MspInitialsAvatar(iniciales)` + nombre (`type.name`) + subline
    "HH:mm · <venta>" (`type.subtitle`) + a la derecha `MspMoneyText` `amountRow` `masked` + method pill
    (`MspStatusChip`/pill: verde "Efectivo" / brand "Transfer.") + dot ámbar "por subir" si `!synced`. `onClick` →
    `openSheet(PAY, id)`. Orden por `sort` (Hora/Nombre).
  - **Semana:** por día, `DayRow`: avatar iniciales día ("L3") + nombre del día + subline "N pagos" + monto.
    `onClick` → `openSheet(DAY, index)`.

**Test primero (TDD):**
- Compose-test: el duo muestra montos formateados y conteos; tocar un tile llama `openSheet`; en Día la lista
  ordena por Hora/Nombre según segment; en Semana muestra filas por día; `masked` enmascara todos los montos.
- Roborazzi baseline (Tier 1, light+dark @1.0): duo+chips+detalle en Día (lista) y en Semana (resumen). Grabar +
  verify.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:recordRoborazziDebug
./gradlew :feature:collectionReport:testDebugUnitTest :feature:collectionReport:verifyRoborazziDebug
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- **Nunca solo color** (accesibilidad): el method pill lleva color+texto ("Efectivo"/"Transfer."); el dot "por
  subir" va acompañado de semántica (contentDescription "por subir"). El estado sincronizado usa color+ícono+texto
  donde aplique.
- El `MspStatusChip` es color+ícono+texto (estado semántico); el "dot de método" del mockup (`.tk .dot`) es un
  `Box` de color simple en el tile, distinto del StatusChip — no confundirlos.
- Iniciales: el cálculo nombre→iniciales es del piloto (el DS recibe iniciales ya calculadas). "María López
  Hernández" → "ML".

**Commit:** `feat(collection-report): UI duo de tiles, chips y detalle (lista Dia / resumen Semana)`

---

## Task 8 — UI: bottom sheets + barra difuminada de acciones (Compartir/Imprimir/PDF)

**Meta:** el progressive disclosure (todos los `ModalBottomSheet`) y la barra inferior difuminada con las tres
acciones, reescribiendo (AUDIT+REWRITE) la generación de PDF / impresión térmica / compartir del reporte viejo.

**Archivos (en `ui/components/`):**
- `ReportSheets.kt` — un `ModalBottomSheet` (M3) manejado por `uiState.sheet`, con handle + título + subtítulo +
  filas (`srow`): despacha por `SheetUi` a cada contenido (hero/efectivo/transfer/condon/visitas/day/pay) según la
  enumeración del mockup §10. Cada fila enmascara montos si `masked`.
- `BlurredActionBar.kt` — barra anclada abajo con `Modifier.background(Brush.verticalGradient(listOf(
  colors.background.copy(alpha=0f), colors.background)))` (transparente→fondo, **NO sólida**; copiar el estilo de
  la `ActionBar` de `VentaDetalleHistorial.kt`, solo el estilo). Tres botones: Compartir (`MspPrimaryFieldButton`
  Ghost, share) · Imprimir (Ghost, printer) · **PDF** (Primary/solid brand, file).
- `ui/actions/ReportActionsController.kt` (o funciones en el screen) — reescritura limpia de:
  - **PDF:** `PdfGenerator` (auditar el uso viejo en `DailyReportScreen`/`ReportActions`), generar el PDF del
    reporte del rango actual + `FileProvider` para abrir/compartir.
  - **Imprimir:** `ThermalPrinting` + selección de dispositivo Bluetooth (auditar `SelectBluetoothDevice`).
  - **Compartir:** `Intent.ACTION_SEND` con el PDF/resumen.
  - **AUDIT:** verificar que los montos del PDF/impresión usan `Money`/`formatMoneyMxn` (no `Double .toInt()`
    truncado); char-test si cambia el output money del PDF. El contenido del PDF debe casar con el tablero.

**Test primero (TDD):**
- Compose-test: abrir cada sheet (via `openSheet`) muestra su título/subtítulo/filas correctos; cerrar lo limpia;
  con `masked` los montos del sheet son `MASKED_MONEY`.
- Unit: el armado del texto/resumen del PDF/impresión produce los montos correctos (char-test old→new si cambia).
- Roborazzi baseline (Tier 1, light+dark @1.0): cada sheet abierto (hero/efectivo/transfer/condon/visitas/day/pay)
  y la barra difuminada sobre el scroll. Grabar + verify.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:recordRoborazziDebug
./gradlew :feature:collectionReport:testDebugUnitTest :feature:collectionReport:verifyRoborazziDebug
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- Barra difuminada = `verticalGradient` con `pointer-events` equivalente: el contenedor no intercepta toques
  (solo los botones), y el scroll lleva `contentPadding` inferior para que el contenido se desvanezca detrás.
- **AUDIT del PDF/impresión:** no confiar el código viejo; verificar que los montos casan con el tablero
  (`Money`), reescribir con tests. Si el output money del PDF cambia respecto al viejo, es corrección consciente
  documentada.
- `ModalBottomSheet` M3 (BOM 2024.09.00) — usar el del DS/M3; el reveal de tema (Task 9) y el sheet coexisten.
- **Parked for user (contenido real de sheets condon/visitas/pay):** el mockup usa datos de ejemplo (motivos,
  refs, folio A-10482). Mapear a los campos reales de dominio disponibles (`Forgiveness.motivo`, `Visit.nota`,
  detalle de pago); donde un campo del mockup no exista en datos (ej. "ref 4821", "mejor hora $4,200"), mostrar el
  equivalente real o omitir la línea — **completo > inventado**. Confirmar en review.

**Commit:** `feat(collection-report): bottom sheets y barra difuminada (compartir/imprimir/PDF)`

---

## Task 9 — Interacciones: ocultar cifras + reveal de tema (root) + Tier 2

**Meta:** cerrar las interacciones firma: máscara de cifras integral, el **`ThemeRevealRoot`** (reveal circular
Telegram-style, que vive en el piloto per Plan 3), y el **layout Tier 2 curado** sobre el mismo ViewModel. Todo
desactivable por reduce-motion.

**Archivos (en `ui/`):**
- `ui/theme/ThemeRevealRoot.kt` — el composable root que Plan 3 dejó fuera del DS (necesita el `content` de la
  pantalla): graba un snapshot del frame viejo + `Animatable` de radio + `clipPath` (Difference), origen desde
  `ThemeRevealController.origin` (provisto por `MspThemeToggle` vía `LocalThemeReveal`), duración
  `tween(380, FastOutSlowInEasing)`; **fallback crossfade** (`MspTheme(animateColors=true)`) si reduce-motion.
  Usa `maxDistanceToCorner(origin, w, h)` (DS). Envuelve `CollectionReportScreen` en `MspTheme`.
- Máscara: verificar que **TODOS** los montos del screen + sheets pasan `masked = uiState.masked` a `MspMoneyText`
  (auditoría de completitud — ningún monto crudo sin `masked`). El insight del hero también se enmascara (mockup:
  `masked ? "•••" : insight`).
- `ui/tier2/CollectionReportScreenTier2.kt` — layout Tier 2 (Muy grande): **una idea por vista**, targets mayores,
  tipografía escalada, columna única; **mismo `CollectionReportViewModel`/estado** (solo cambia `ui/`). Selección
  Tier 1/2 por la preferencia "Tamaño de texto" (Normal/Grande = Tier 1; Muy grande = Tier 2) — leer la pref de
  `:core:common`/settings (o `fontScale` del SO como proxy si la pref aún no existe; documentar).

**Test primero (TDD):**
- Compose-test: `toggleMask` enmascara todos los montos (aserción sobre múltiples nodos, incluido hero e insight);
  `MspThemeToggle` con `LocalThemeReveal` provisto dispara el controller (no `onToggle` directo); con reduce-motion
  el reveal cae a crossfade instantáneo (sin crash).
- Unit: `maxDistanceToCorner` (ya en DS) se usa correcto; la selección Tier 1/2 por pref/escala.
- Roborazzi: Tier 2 (light+dark @2.0) del screen completo. Grabar + verify.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:recordRoborazziDebug
./gradlew :feature:collectionReport:testDebugUnitTest :feature:collectionReport:verifyRoborazziDebug
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- El `ThemeRevealRoot` es del **piloto/composition root**, no del DS (Plan 3 Task 9 lo dejó explícito). El DS solo
  da el bridge (`ThemeRevealController`/`LocalThemeReveal`/`MspThemeToggle`/`maxDistanceToCorner`).
- **Toda** animación consulta `rememberReducedMotionEnabled()`: reveal, tab transition, entrada escalonada,
  crecimiento de sparkline, pulse de sync pill.
- **Parked for user (pref "Tamaño de texto" propia):** la preferencia por-usuario (Normal/Grande/Muy grande) del
  spec §5 puede no existir aún en `:core:common`. Lectura fiel: usar `fontScale`/tamaño del SO como disparador de
  Tier 1/2 ahora, y cablear la pref propia cuando exista (es transversal a toda la app, no solo del piloto). No
  bloquea la fidelidad del piloto.

**Commit:** `feat(collection-report): ocultar cifras, reveal de tema (root) y layout Tier 2`

---

## Task 10 — Cableado: re-apuntar ruta `"daily_reports"`, absorber WeeklyReport, borrar lo viejo

**Meta:** conectar la pantalla nueva a la app conservando el literal de ruta, colapsar el reporte semanal dentro
del unificado, y eliminar el código viejo del reporte **sin romper** el drawer ni tocar `features/dailyReport`
(inventario). `:app` idéntico salvo el reporte.

**Archivos a tocar:**
- `app/build.gradle.kts` → `implementation(project(":feature:collectionReport"))`.
- `navigation/AppNavigation.kt`:
  - `composable(Screen.DailyReport.route) { CollectionReportScreen(navController) }` — **conservar el literal
    `"daily_reports"`** en `object DailyReport : Screen("daily_reports")` (línea 79). Re-apuntar el `composable`
    (línea 366) al nuevo `CollectionReportScreen`.
  - **Eliminar** `object WeeklyReport : Screen("weekly_reports")` (línea 80) y su `composable(...)` (línea 370) +
    el import de `WeeklyReportScreen` (línea 48). El semanal ahora es el toggle "Semana" del reporte unificado.
- `components/DrawerContainer.kt` — **eliminar** la entrada de drawer que navega a `"weekly_reports"` (línea 173);
  conservar la de `"daily_reports"` (línea 160) → ahora abre el reporte unificado. Ajustar el label del ítem si
  procede ("Reporte de cobranza" → el drawer mantiene texto corto).
- **Borrar (absorbidos):** `features/payments/screens/DailyReportScreen.kt`,
  `features/payments/screens/WeeklyReportScreen.kt`, `features/payments/components/weeklyreportcontent/`,
  `features/payments/components/reportactions/` (si solo lo usaba el reporte), `features/payments/models/ReportModels.kt`,
  `features/payments/utils/ReportFormatters.kt` (**tras** confirmar que su lógica útil se reescribió en el módulo
  y que nadie más los usa — `git grep` cada símbolo antes de borrar).
- **NO borrar del todo `PaymentsViewModel`:** es usado por otras pantallas (detalle de venta, nuevo pago, sync).
  Solo se dejan de usar sus métodos de reporte desde el módulo nuevo (que usa sus propios puertos). Auditar qué
  queda huérfano; borrar solo lo verificablemente muerto.

**AUDIT antes de borrar:** `git grep` cada clase/función candidata a borrar en TODO `app/` para confirmar cero
referencias vivas (excepto el propio reporte). Si algo lo usa otra feature → NO borrar, dejar nota.

**Test primero (TDD):**
- Compose/nav test: navegar a `"daily_reports"` renderiza `CollectionReportScreen`; la ruta `"weekly_reports"` ya
  no existe (o redirige) sin crash; el drawer abre el reporte unificado.
- `:app:assembleDevlocalDebug` compila; `testDevlocalDebugUnitTest` de `:app` verde (imports huérfanos limpiados).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew :feature:collectionReport:detekt ktlintCheck
```

**Gotchas:**
- **Conservar el literal `"daily_reports"`** (deep links / drawer / hábito de usuarios). Solo cambia el destino,
  no el string.
- **NO tocar `features/dailyReport/`** (inventario del carrito) — módulo homónimo distinto.
- Borrado con cuidado: `PaymentsViewModel`, `PaymentItem`, `PaymentsList` pueden ser compartidos; `git grep` antes
  de cada borrado. Un borrado que rompe otra feature = regresión.
- **Parked for user (entrada de drawer semanal):** al quitar "Reporte semanal" del drawer, el acceso al semanal es
  el toggle "Semana" dentro del reporte. Si el usuario quiere conservar un acceso directo al semanal desde el
  drawer (que abra el reporte ya en modo Semana vía argumento de ruta), es una extensión de una línea —
  documentada, no bloqueante.

**Commit:** `feat(collection-report): re-apuntar ruta daily_reports al reporte unificado y absorber WeeklyReport`

---

## Task 11 — Cierre: gate de fidelidad visual (mockup-imagen vs Roborazzi) + contraste + no-truncación + smoke e2e

**Meta:** el **gate obligatorio de fidelidad**: la pantalla real debe verse **EXACTAMENTE** como el mockup,
verificado **como imagen** (render del mockup HTML vs PNGs Roborazzi de la matriz completa), con un **revisor de
fidelidad visual dedicado**; más contraste AAA, no-truncación de dinero a escala grande, y un smoke en dispositivo
de que el reporte renderiza y navega. Cablear `verifyRoborazziDebug` + `koverVerify` al `prePushCheck`.

**Trabajo:**
- **Render del mockup como imagen:** renderizar `docs/design/reporte-cobranza-mockup.html` a PNG (headless Chrome
  / herramienta de navegador) en sus estados clave: Día light, Día dark, Semana light, Semana dark, + cada bottom
  sheet abierto, + estado enmascarado. Guardar como referencia de comparación (NO como golden Roborazzi;
  referencia visual para el revisor).
- **Matriz Roborazzi completa** del screen real: **Tier 1 y Tier 2 × escala {1.0, 1.3, 2.0} × {light, dark}** para
  la pantalla completa + estados (Día/Semana, cada sheet, enmascarado). Nombre determinista
  `collection_<estado>_<tier>_<tema>_<escala>`.
- **Comparación lado a lado** mockup-imagen vs Roborazzi: el revisor de fidelidad cruza la **enumeración del
  mockup** (Global Constraints §"Enumeración del mockup", los 11 grupos) elemento por elemento. **Si falta algún
  elemento, se AGREGA** (completo > pixel-idéntico).
- `ContrastAAATest` — pares críticos (hero `onBrand`/gradiente, `onSurface`/`surface`, chips) en ambos temas
  (umbrales per Plan 3 Task 10 parked: AAA-large 4.5:1 hero, AAA-normal 7:1 neutros).
- `MoneyNoTruncationTest` — montos grandes (`$1,234,567.89`) a `fontScale 2.0` en el hero/tiles/filas: el dinero
  **reflowea, no se trunca** (sin ellipsis). "Terminado imposible si el dinero se corta en grande."
- **Smoke e2e (dispositivo, UN emulador `Pixel_9_Pro`):** `connectedDevlocalDebugAndroidTest` que abre el drawer →
  "daily_reports" → el reporte renderiza (hero visible, toggle Día/Semana funciona, un sheet abre). Apagar el
  emulador al terminar.
- **prePushCheck (wiring final):** añadir `:feature:collectionReport:verifyRoborazziDebug` +
  `:feature:collectionReport:koverVerify` al `dependsOn(...)` del `prePushCheck` raíz.

**Test primero (TDD):** los tests SON el gate (rojo→verde): grabar la matriz con `recordRoborazziDebug`, luego
`verifyRoborazziDebug` pasa; `ContrastAAATest` y `MoneyNoTruncationTest` pasan; el e2e device pasa. Escribir
primero `MoneyNoTruncationTest` (mayor valor de accesibilidad) y hacerlo pasar.

**Revisión dedicada (parte del gate):** un **revisor de fidelidad visual dedicado** (subagente) compara
mockup-imagen vs Roborazzi lado a lado y firma la enumeración completa. money/UI = 2 revisores (uno adversarial).

**Verificación (gate completo del plan):**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :feature:collectionReport:recordRoborazziDebug     # graba la matriz completa
./gradlew ktlintCheck
./gradlew :feature:collectionReport:testDebugUnitTest
./gradlew :feature:collectionReport:verifyRoborazziDebug
./gradlew :feature:collectionReport:detekt
./gradlew :feature:collectionReport:koverVerify
./gradlew testDevlocalDebugUnitTest
./gradlew prePushCheck                                        # ahora incluye el módulo del piloto
./gradlew :app:assembleDevlocalDebug
./gradlew connectedDevlocalDebugAndroidTest                   # smoke e2e, un emulador
```
Commitear TODOS los PNG de `feature/collectionReport/src/test/screenshots/` (el contrato visual del piloto).

**Gotchas:**
- La matriz genera muchos PNG; el piso obligatorio es Tier 1 × 3 escalas × 2 temas para la pantalla + estados
  clave; Tier 2 donde el layout curado aplica. No subir el threshold para "arreglar" flicker — congelar
  animaciones (`animateColors=false`, sin random/tiempo).
- El render del mockup es **referencia para el ojo humano/revisor**, no un golden byte-a-byte (HTML ≠ Compose). El
  golden Roborazzi es contra sí mismo; la fidelidad al mockup la firma el revisor dedicado.
- Si el revisor determina que los **neutros dark** deben virar al azul del mockup (`surface #0F1520` etc.) en vez
  de los verde-grisáceos de kollect (Plan 3 Task 2 parked #1): ese es el lugar de decisión. El DS expone los
  tokens en un archivo; cambiarlos ahí recalcula todo. Documentar y, si el usuario aprueba, aplicar en el DS.

**Commit:** `test(collection-report): gate de fidelidad (mockup vs Roborazzi), contraste, no-truncacion y smoke e2e`

---

## Cierre de Plan 5 (auditoría de conformidad)

- [ ] `:feature:collectionReport` existe (namespace `com.example.msp_app.feature.collectionreport`), aplica
      `msp.android.library/compose/hilt/detekt/kover` + ktlint + Roborazzi; incluido en `settings.gradle.kts`;
      depende de `:core:designsystem/common/database/network/telemetry/testing`.
- [ ] **Dominio sin `Double`:** VO `Money` (BigDecimal, escala 2); único puente `Double`→money en el adapter de
      datos (`IMPORTE` del schema v27); detekt `NoDoubleForMoney` limpio en dominio/UI.
- [ ] **Rangos half-open `[desde, hasta)` en zona negocio** (`America/Mexico_City` vía `AppClock`/`AppTime`);
      Semana = ciclo `[FECHA_CARGA_INICIAL, startOfNextDay(hoy))`; fin exclusivo (char-test 23:59:59).
- [ ] **Agregados + meta sugerida + insight** con `Money`; char-test money-path old→new documentado donde corrige
      truncación.
- [ ] **Datos:** puertos pagos/visitas/condonaciones sobre Room (`:core:database`) + `pendingCount` (sync pill) +
      histórico (meta); transfers-por-contrato definido y parked; AUDIT del contrato (half-open, wire UTC, sin
      cambio de red) firmado.
- [ ] **`@HiltViewModel`** + estado observable; fakes-only + Turbine; kill-switch preservado (sin `@Singleton` en
      cadena de red).
- [ ] **UI fiel al mockup** (los 11 grupos de la enumeración): header ("Cobranza" + subtítulo + ojo + tema),
      selector Día/Semana, range pill + sync pill, HERO (overline+delta+monto+insight+barra mint+goal+sparkline+
      wells), duo Efectivo/Transferencia, chips Condonado/Visitas, detalle lista(Día)/resumen(Semana), barra
      difuminada Compartir·Imprimir·PDF, todos los bottom sheets.
- [ ] **Interacciones desactivables:** reveal circular de tema (root del piloto), ocultar cifras (todos los
      montos + insight), transición de tab (crossfade+slide direccional), entrada escalonada; todas gated por
      `rememberReducedMotionEnabled()`.
- [ ] **Ruta `"daily_reports"` conservada** (literal), destino re-apuntado; `WeeklyReport` absorbido (ruta +
      drawer eliminados); código viejo del reporte borrado sin romper nada; **`features/dailyReport` (inventario)
      intacto**.
- [ ] **Accesibilidad:** AAA (large hero / normal neutros), targets 56dp, `tnum`, nunca solo color, honra SO,
      Tier 1/2 sobre el mismo ViewModel; **dinero reflowea, no trunca** a escala 2.0.
- [ ] **Gate visual:** matriz Roborazzi Tier 1/2 × {1.0,1.3,2.0} × {light,dark}, mockup renderizado a imagen y
      comparado lado a lado por **revisor de fidelidad dedicado**; PNG commiteados.
- [ ] **`:app` idéntico salvo el reporte**; `assembleDevlocalDebug` + `testDevlocalDebugUnitTest` verdes; smoke
      e2e dispositivo verde; `prePushCheck` incluye `:feature:collectionReport:{ktlintCheck,testDebugUnitTest,
      detekt,koverVerify,verifyRoborazziDebug}`.
- [ ] **Cartera/zona = Fase 2** (no cableado); commits por tarea, conventional, español, sin atribución de Claude,
      sin push, rama `feat/multimodulo-cimiento`, sin `--no-verify`.

### Decisiones resueltas / parked (para el orquestador)
1. **Transfers por contrato:** el duo "Transferencia" = método de pago (id 52569) desde Room; el traspaso
   cross-módulo (`:feature:transfers`) NO se surfacea (el mockup no lo pide). `TransfersPort` definido, no
   cableado. **Parked** en Task 4.
2. **Cheque (158):** cuenta al total cobrado, NO al efectivo-en-mano ni al duo (el mockup no lo tiene). **Parked**
   en Task 3; confirmar en review.
3. **Delta "vs ayer/ciclo":** requiere consulta del periodo anterior; degradar a "—" si costoso. **Parked** Task 3.
4. **Meta sugerida vs meta de oficina:** el piloto usa meta sugerida (mediana ~14 días); la meta fijada por
   oficina es **Fase 2** (Firestore/msp-api). Puerto abierto, no cableado.
5. **Contenido real de sheets** (motivos/refs/folio del mockup son ejemplo): mapear a campos reales; omitir líneas
   sin dato antes que inventar. **Parked** Task 8.
6. **Neutros dark azul del mockup vs 1:1 kollect** (heredado de Plan 3 Task 2 parked): si el revisor de fidelidad
   lo exige, se cambian los tokens en el DS. **Parked** Task 11.
7. **Pref propia "Tamaño de texto" (Tier 1/2):** usar `fontScale`/SO como disparador ahora; cablear la pref propia
   cuando exista en `:core:common`. **Parked** Task 9.
8. **`ThemeRevealRoot` vive en el piloto** (Plan 3 lo dejó fuera del DS); el DS aporta el bridge. Task 9.
