# Plan — Migración de fechas a `AppTime` (java.time, zona de negocio)

Fuente de verdad: `.superpowers/sdd/2026-08-07-plan2-database/date-lib-audit.md` (auditoría completa:
inventario función-por-función, catálogo de 10 bugs, tabla de reemplazo old→`AppTime`, contrato con
`msp-api` verificado, y la lista exhaustiva de casos borde). Estándar ya escrito:
`docs/standards/timezones.md`. Convenciones de despacho: `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`
(POLÍTICA DE MIGRACIÓN: **auditar + reescribir** con cobertura y robustez **SUPREMA** + **verificar el
contrato de la API**; **schema Room v27 inmutable**).

**Hallazgo central (auditoría):** no se diseña librería nueva. Ya existe `core/time/AppTime.kt` +
`AppClock.kt` (100% `java.time`, `BUSINESS_ZONE = America/Mexico_City` fijo, wire format correcto),
adoptada hoy en solo 5 archivos. El 90% de la app —incluido TODO el flujo de cobranza/pagos legacy—
sigue en `core/utils/DateUtils.kt` (envoltura con bug de zona-del-dispositivo) más su **copia
byte-idéntica** en `:core:database` (`PaymentDateGrouping.kt`) y ~12 sitios sueltos de
`SimpleDateFormat`/`Calendar`/`LocalDateTime.now()`. El trabajo es **promover `AppTime` a `:core:common`,
agregarle 2 funciones que faltan, y migrar los call sites en lotes ordenados por riesgo de dinero**,
fijando el comportamiento correcto con tests supremos.

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`): implementador
> TDD → gate real → revisor → fix-loop, una tarea a la vez. Rama `feat/multimodulo-cimiento`.

---

## Global Constraints (vinculan a TODA tarea de este plan)

- **Toolchain FIJA:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24, targetSdk 35,
  Java 11 (desugaring **on** — `java.time` disponible en minSdk 24), Compose BOM 2024.09.00.
- **`JAVA_HOME` en CADA comando gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
- **Variante de gate:** `:app` = `testDevlocalDebugUnitTest` + `assembleDevlocalDebug`; los `:core:*` son
  librerías sin flavors → `:core:<x>:testDebugUnitTest`. UN solo comando gradle a la vez (build lock).
- **Paquete/`applicationId` `com.example.msp_app` NO se toca.** El código promovido a `:core:common` vive
  bajo el namespace del módulo: `com.example.msp_app.core.common.*`.
- **`schema Room v27 es inmutable** (contrato de datos en teléfonos reales). Se reescribe la LÓGICA
  alrededor (queries de DAO, agrupamiento, mappers), NUNCA el DDL. Ninguna tarea de este plan toca
  `AppDatabase`, entities, ni el `schemas/27.json`.
- **Testing:** fakes-only (estado + recording/spy), **CERO MockK/Mockito**; Turbine para Flows;
  `kotlinx-coroutines-test`; `AppClock` fake para fijar `now()`. `:core:common` corre `detekt` estricto +
  `koverVerify` **90%** sobre todo `com.example.msp_app.core.common.*` — el código promovido debe pasar
  ambos. `:app` sigue exento de detekt estricto y de gate de cobertura (legacy), pero todo test nuevo de
  `:app` corre en `testDevlocalDebugUnitTest`.
- **Money-path (regla de `DISPATCH-CONVENTIONS.md`):** antes de reescribir una pieza de dinero, caracterizar
  el comportamiento correcto contra el **contrato real del API** (no el viejo si estaba mal). Todo cambio de
  comportamiento de dinero es **conscientemente documentado** (es corregir un bug), nunca accidental. Si no
  se puede verificar el contrato → reportar **BLOCKED**.
- **Commits por tarea**, conventional commits, subject en **español**, **SIN atribución de Claude**, **SIN
  `--no-verify`**, **sin push**.
- **Contrato de reporte:** ver `DISPATCH-CONVENTIONS.md` §"Contrato de reporte".

### Orden y su justificación (leer antes de empezar)
Primero se levanta la **fundación verificada** (Task 1 promueve `AppTime` a `:core:common` con la suite
suprema; Task 2 fija el **contrato con `msp-api`** con tests dedicados) para que todo lo demás se apoye en
una base ya probada contra el backend — esto cumple la regla de "verificar el contrato ANTES de reescribir
money-path". Luego se migran los sitios **ordenados por riesgo de dinero**: agrupamiento de pagos por día
(Task 3), cálculo de liquidación (Task 4), rangos de reportes de cobranza (Task 5), escritura de timestamps
de pago (Task 6) y el gate de horario del worker de clientes (Task 7). Después los lotes de **display puro**
sin riesgo (Tasks 8–9), los módulos rezagados `transfers` (Task 10) y garantías (Task 11), los sitios de
`SimpleDateFormat`/`Locale` (Task 12), y finalmente el **borrado de `DateUtils` + blindaje detekt** (Task 13).
Cada tarea deja el build verde; `DateUtils.kt` solo se borra cuando ya no tiene referencias.

### Comando de gate (por tarea, ajustando el alcance)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:testDebugUnitTest
./gradlew :core:common:detekt
./gradlew :core:common:koverVerify
./gradlew :core:database:testDebugUnitTest        # cuando la tarea toque :core:database
./gradlew testDevlocalDebugUnitTest               # :app
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

---

## Task 1 — Promover `AppTime`/`AppClock` a `:core:common` + 2 funciones faltantes + suite suprema

**Meta:** que `AppTime`/`AppClock` vivan en `:core:common` (única fuente, `java.time` puro), con las 2
funciones que la auditoría (§4.1, §5) identificó como faltantes, y con la **suite de casos borde suprema**
que hoy no existe para el código legacy. `:core:common` ya aplica Kover 90% sobre `com.example.msp_app.core.common.*`
y detekt estricto → el código promovido queda blindado por el gate desde el día uno. La app corre idéntica
(solo cambian imports).

**Archivos:**
- **Mover** `app/src/main/java/com/example/msp_app/core/time/AppTime.kt` y `AppClock.kt`
  → `core/common/src/main/kotlin/com/example/msp_app/core/common/time/AppTime.kt` + `AppClock.kt`.
  **Renombrar el package** `com.example.msp_app.core.time` → `com.example.msp_app.core.common.time`
  (obligatorio: el filtro de Kover de `:core:common` incluye `packages("com.example.msp_app.core.common")`;
  fuera de ese prefijo la suite no contaría para el gate 90%).
- **Mover** el test existente `app/src/test/java/com/example/msp_app/core/time/AppTimeTest.kt`
  → `core/common/src/test/kotlin/com/example/msp_app/core/common/time/AppTimeTest.kt` (re-apuntar package/imports).
- **Agregar a `AppTime`** (auditoría §4.1, §5):
  - `fun plusOnWire(iso: String, amount: Long, unit: ChronoUnit): String = toWireFormat(parseWireFormat(iso).plus(amount, unit))`
    — opera sobre `Instant`, sin viajes por `LocalDateTime` (reemplazo de `DateUtils.addToIsoDate`, elimina bug #3).
  - Confirmar/documentar `startOfDay(date)` y `startOfNextDay(date)` (ya existen en `AppTime`) como el
    patrón oficial de rango de un día `[desde, hasta)` — se usarán en Task 5 en vez de `+1 día −1 segundo`.
    Agregar KDoc que lo declare el reemplazo del patrón frágil.
- **Actualizar imports** en los 5 adoptantes actuales de `AppTime` (auditoría §1.5): `PaymentV2Mappers.kt`,
  `VisitV2Mappers.kt`, `DailyReportRepository.kt`, `SaleDateFilter.kt`, `TransferDateFilter.kt` — cambian
  `import com.example.msp_app.core.time.*` → `...core.common.time.*`. `:app` ya depende de `:core:common`
  (Plan 1 Task 3), así que no hay cambio de `build.gradle.kts`.

**Test primero (TDD) — suite suprema en `:core:common` (`AppTimeTest.kt` + `AppClockTest.kt`):** cubrir la
lista exhaustiva de la auditoría §4.4 / `docs/standards/timezones.md` §"Tests obligatorios". Casos mínimos:
1. Evento **23:00 CDMX** (día siguiente en UTC) → `toBusinessDate` da el día correcto (regresión ya existente).
2. Evento **01:00 CDMX** (mismo día en UTC) → idem.
3. **Medianoche exacta CDMX** (`00:00:00` local = `06:00:00Z`) → `startOfDay`.
4. **23:59:59.999 CDMX** vs **00:00:00.001 CDMX** del día siguiente → caen en días/grupos distintos; el
   rango `[startOfDay, startOfNextDay)` incluye el primero y excluye el segundo.
5. **Transición DST histórica** (México pre-2022): fechas alrededor del último domingo de octubre / primer
   domingo de abril con `America/Mexico_City` → `atZone`/`ZonedDateTime` no rompe ni duplica.
6. **Fin de mes / fin de año** en agrupamiento por día (clave de cobranza) y en round-trips wire.
7. **`parseWireFormat`:** `Z`, `.123Z` (millis), fracción de 6–9 dígitos (Go `RFC3339Nano`), `-06:00`
   explícito, `+00:00`, sin offset (asume negocio, legacy), solo fecha (`yyyy-MM-dd`, medianoche negocio).
8. **`parseWireFormatOrNull`:** `null`, `""`, `"   "`, `"garbage"`, `"31/13/2026"` → `null` sin lanzar.
9. **`plusOnWire`:** sumar/restar DAYS/SECONDS/MONTHS preserva el instante exacto sin drift de offset.
10. **`toWireFormat`(round-trip):** `parseWireFormat(toWireFormat(i)) == i` para instantes con y sin fracción.
11. **Independencia de la zona del dispositivo:** correr con `TimeZone.setDefault(UTC)` y con
    `America/Tijuana` → **el resultado de `AppTime.*` no cambia** (este es el test que prueba que el bug de
    fondo quedó eliminado — contrastar con `DateUtils`, que sí cambiaría).
12. **`AppClock` fake:** `nowInBusinessZone`/`todayInBusinessZone` con un `AppClock` de instante fijo dan
    exactamente el día/hora de negocio esperado (no el del host de CI).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:testDebugUnitTest
./gradlew :core:common:detekt
./gradlew :core:common:koverVerify
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige / cambio de comportamiento:** ninguno funcional aún (solo mueve código y agrega
funciones+tests). Prepara la corrección de #1/#2/#3. **Nota:** el renombre de package
`core.time`→`core.common.time` toca los 5 adoptantes actuales; verificar que `TransferDateFilterTest`
(que ya prueba el formato real `-06:00` del backend) siga verde tras el movimiento.

**Commit:** `refactor(core-common): promover AppTime/AppClock y suite suprema de fechas`

---

## Task 2 — Contract test: serialización/parseo de la app ↔ `msp-api`

**Meta:** fijar con tests dedicados que lo que la app **emite y acepta** coincide byte-a-byte con el
contrato real de `msp-api`, incluido el caso legacy `-06:00`. Esto se hace **antes** de tocar cualquier
call site de dinero (regla de `DISPATCH-CONVENTIONS.md`: verificar contrato antes de reescribir money-path).
Cross-check obligatorio contra `/Volumes/M2-1TB/Developer/msp-api/docs/module-standards/DATETIME_HANDLING.md`
y la auditoría §3.

**Archivos:**
- `core/common/src/test/kotlin/com/example/msp_app/core/common/time/WireContractTest.kt` (nuevo).

**Test primero (TDD) — casos derivados del contrato (auditoría §3.1–§3.3):**
1. **Salida hacia el API (lo que emitimos):** `AppTime.toWireFormat(instant)` produce siempre
   `yyyy-MM-ddTHH:mm:ss[.SSS]Z` (UTC con `Z`) — parseable por `time.Parse(time.RFC3339, raw)` de Go.
   Verificar con un instante con fracción y sin fracción.
2. **Entrada estándar V2 (lo que recibimos):** `t.UTC().Format(RFC3339Nano)` → `Z`-UTC con fracción
   variable 0–9 dígitos (a veces ausente). `parseWireFormat` normaliza todos a `Instant`.
   - Muestras exactas: `"2026-05-13T18:00:00Z"`, `"2026-05-13T18:05:23.142Z"`,
     `"2026-04-22T06:00:00.000Z"` (fecha calendario = medianoche CDMX).
3. **Entrada legacy no-`Z` (caso real documentado):** `FECHA_HORA_CREACION` =
   `"2026-04-22T19:43:56.000-06:00"` → `parseWireFormat` lo acepta (branch `OffsetDateTime.parse`) y da el
   instante correcto (`2026-04-23T01:43:56Z`). **Este caso HOY revienta/degrada con `DateUtils`** (bug #2):
   `parseIsoToDateTime` lanza `DateTimeParseException` en `isAfterIso`/`isBeforeIso`, y `formatIsoDate` lo
   degrada al string crudo. El test pin-ea el comportamiento correcto de `AppTime`.
4. **Fecha calendario pura:** `AppTime.toWireDate(LocalDate)` → `yyyy-MM-dd` y `parseWireDateOrNull` inverso;
   coincide con `fechaInicio`/`fechaFin`/`DIA_COBRANZA` del backend.
5. **Semántica de rango `[desde, hasta)`** (inclusivo/exclusivo, `DATETIME_HANDLING.md` de msp-api): un
   instante en `startOfNextDay(date)` **no** cae dentro del rango de `date`; `startOfDay(date)` sí. (Base del
   fix de Task 5.)

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:common:testDebugUnitTest
./gradlew :core:common:detekt
./gradlew ktlintCheck
```

**Bug que corrige / cambio de comportamiento:** documenta y blinda el contrato; no cambia comportamiento de
producción. Deja evidencia ejecutable de que `AppTime` cubre el caso `-06:00` que `DateUtils` no cubre (#2).

**Commit:** `test(core-common): contract test de fechas contra msp-api`

---

## Task 3 — [MONEY] Dedup `:core:database` + agrupamiento de pagos por día en zona de negocio

**Meta:** eliminar la **copia byte-idéntica** `PaymentDateGrouping.kt` y hacer que el agrupamiento por día
de la pantalla de pagos use `AppTime` en **zona de negocio** (no zona del dispositivo). Money-path: un pago
cerca de medianoche se agrupaba en el día equivocado si el teléfono del cobrador tenía otra zona (viaje,
roaming, config errónea).

**Resolución de dependencia de módulo (CLAVE — ver "Ambigüedad" del reporte):** el KDoc de
`PaymentDateGrouping.kt` afirma que `:core:database` "no puede depender de `:core:common`". **Esa afirmación
está desactualizada:** `:core:common` NO depende de `:core:database` (ver `core/common/build.gradle.kts`:
solo `kotlinx-coroutines-core` + test), por lo que `:core:database → :core:common` es **acíclico y
permitido**. La tarea lo aprovecha: agrega la dependencia y borra la copia. (Si el gate revela un ciclo
inesperado, reportar BLOCKED y caer al plan B: computar la clave de día en la capa `:app`/repositorio en vez
del DAO — no reintroducir la copia.)

**Archivos:**
- `core/database/build.gradle.kts` → agregar `implementation(project(":core:common"))`.
- **Borrar** `core/database/src/main/kotlin/com/example/msp_app/core/database/dao/payment/PaymentDateGrouping.kt`.
- `core/database/src/main/kotlin/.../dao/payment/PaymentDao.kt` → `getPaymentsGroupedByDaySince` (línea ~179)
  y `observePaymentsGroupedByDaySince` (~241): reemplazar `formatIsoDateForGrouping(iso, pattern, locale)`
  por la clave de día de negocio: `AppTime.toBusinessDate(AppTime.parseWireFormat(fecha)).toString()`
  (o `AppTime.formatIsoForDisplay` si la clave debe conservar el patrón de display — decidir según qué
  consume el agrupamiento; la clave de ordenamiento/agrupado debe ser estable, preferir `yyyy-MM-dd`).
  **No tocar el DDL ni la query SQL del schema v27** — solo la transformación en Kotlin sobre el resultado.

**Test primero (TDD) — Robolectric DAO test en `:core:database` (usa `:core:testing`):**
1. Dos pagos: uno `2026-04-16T04:30:00Z` (= 22:30 del 15-abr CDMX) y otro `2026-04-16T07:00:00Z` (= 01:00
   del 16-abr CDMX) → deben caer en **días de negocio distintos** (15 vs 16), NO en el mismo día UTC.
2. Pago a `2026-04-16T05:59:59Z` (23:59:59 del 15-abr CDMX) vs `2026-04-16T06:00:00Z` (00:00 del 16) →
   grupos distintos (límite de medianoche CDMX).
3. **Independencia de zona del dispositivo:** correr con `TimeZone.setDefault(UTC)` y `America/Tijuana` → el
   agrupamiento no cambia (contrasta con el bug actual).
4. Fila con `FECHA_HORA_PAGO` legacy sin `Z` → agrupa sin lanzar (parseWireFormat legacy branch).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:database:testDebugUnitTest
./gradlew :core:common:detekt
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** #1 (zona del dispositivo → negocio) y #7 (copia duplicada con drift).
**⚠️ CAMBIO DE COMPORTAMIENTO CONSCIENTE (money):** el agrupamiento por día de la pantalla de cobranza pasa
de la zona del dispositivo a `America/Mexico_City`. En un teléfono en otra zona, algunos pagos cercanos a
medianoche cambian de día — es la **corrección** del bug, no un efecto colateral. Test #1/#2 fijan el
comportamiento nuevo correcto.

**Commit:** `fix(core-database): agrupar pagos por dia en zona de negocio y eliminar copia de DateUtils`

---

## Task 4 — [MONEY] `SettlementCalculator`: "ahora" en zona de negocio

**Meta:** que el cálculo de categoría/monto de liquidación (precio de contado / a N meses / total) use la
**hora de negocio** para "meses transcurridos", no la del dispositivo. Cerca de un límite de mes, un
teléfono con zona/reloj distinto a CDMX podía ofrecer una categoría de pago equivocada al cliente.

**Archivos:**
- `app/src/main/java/com/example/msp_app/features/sales/domain/models/SettlementCalculator.kt` (función
  `calculatePaymentResult`, línea 33-35): cambiar el default `now: LocalDateTime = LocalDateTime.now()` por
  `now: LocalDateTime = AppTime.nowInBusinessZone()`. El parámetro `now` **sigue siendo inyectable** (ya lo
  era) → los tests fijan `now` sin tocar reloj. `settlement.date` sigue parseándose como `LocalDate` con
  patrón `dd/MM/yyyy` (es una fecha calendario de negocio, correcto; no es wire format).
- Confirmar callers (auditoría: `SaleDetailsViewModel`/`SaleDetailsScreen`): si construyen `now`
  explícitamente con `LocalDateTime.now()`, cambiarlos a `AppTime.nowInBusinessZone(clock)` (inyectar
  `AppClock` en el ViewModel si ya lo tiene; si no, usar el default). Reportar cualquier caller que pase
  un `now` device-local.

**Test primero (TDD) — `SettlementCalculatorTest` en `:app` (`src/test`):**
1. **Caracterización primero:** con `now` explícito (p.ej. `2026-06-15T10:00`), fijar la categoría/monto
   esperado por cada tramo (`elapsedMonths` ≤1, 2–3, 4–5, 6–12, >12, y el período de gracia). Pin-ear los
   valores ACTUALES para no alterar la matemática.
2. **Límite de mes con zona:** una venta cuyo `elapsedMonths` cambia según si "ahora" se evalúa en UTC vs
   CDMX (p.ej. venta 30/06, "ahora" 31/07 00:30 CDMX = 31/07 06:30 UTC — mismo día; pero 01/07 vs 30/06 cerca
   de medianoche) → verificar que el resultado usa el día de negocio. Test con `TimeZone.setDefault(UTC)` y
   `America/Tijuana`: la categoría no cambia (elimina el bug).
3. **Período de gracia** (`DEFAULT_GRACE_PERIOD_DAYS`) y `elapsedMonths <= 0` → "Precio de contado".
4. **Fin de mes** (`ChronoUnit.MONTHS.between` con día 31 vs mes de 30).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** #6.
**⚠️ CAMBIO DE COMPORTAMIENTO CONSCIENTE (money):** "ahora" pasa de la zona del dispositivo a
`America/Mexico_City`. En un teléfono en otra zona, cerca de un límite de mes la categoría/monto ofrecidos
pueden cambiar — es la corrección del bug. La matemática de tramos NO cambia (fijada por los tests de
caracterización). Si algún test de caracterización revela que el cambio altera un monto en el caso base
(zona = CDMX), reportar BLOCKED.

**Commit:** `fix(sales): calcular liquidacion con hora de negocio en SettlementCalculator`

---

## Task 5 — [MONEY] Rangos de reportes de cobranza `[desde, hasta)`

**Meta:** reemplazar el patrón frágil `addToIsoDate(addToIsoDate(iso, 1, DAYS), -1, SECONDS)` (fin de rango
= "+1 día −1 segundo") por el rango medio-abierto `[startOfDay(date), startOfNextDay(date))`, que coincide
byte-a-byte con la semántica `[desde, hasta)` de `msp-api` (verificada en Task 2). Corrige el bug #3 y
alinea la query al contrato del backend.

**Archivos (auditoría §1.6, bug #3):**
- `app/src/main/java/com/example/msp_app/features/payments/screens/DailyReportScreen.kt` (líneas 91-100, 118,
  149, 322, 542, 573, 585): rango del día → `AppTime.startOfDay(date)` (desde) y `AppTime.startOfNextDay(date)`
  (hasta **exclusivo**). Migrar los `DateUtils.formatIsoDate`/`addToIsoDate` restantes del archivo.
- `app/src/main/java/com/example/msp_app/features/payments/screens/WeeklyReportScreen.kt` (51-52) +
  `components/weeklyreportcontent/WeeklyReportContent.kt` (52,56,77) +
  `components/reportactions/ReportActions.kt` (70,72): mismo patrón de rango + display via `AppTime`.
- `app/src/main/java/com/example/msp_app/features/sales/screens/SaleMapScreen.kt` /
  `features/routemap/RouteMapScreen.kt` (71,78,80,83-87,135): rango del día → `startOfDay`/`startOfNextDay`.
- `app/src/main/java/com/example/msp_app/features/payments/utils/ReportFormatters.kt` (20,65,94,116,138):
  `DateUtils.formatIsoDate` → `AppTime.formatIsoForDisplay`.

**Test primero (TDD) — en `:app` (`src/test`), fakes-only:**
1. **Límites del rango:** pago a `23:59:59.999 CDMX` del día D **entra** en el reporte de D; pago a
   `00:00:00.000 CDMX` de D+1 **NO** entra en D y **sí** en D+1 (medio-abierto). Contrastar con el patrón
   viejo `+1d −1s` (que incluía `23:59:59` pero podía perder el milisegundo final / incluir el borde).
2. **Independencia de zona del dispositivo** (UTC / Tijuana) → el conjunto de pagos del día no cambia.
3. **Reporte semanal:** lunes 00:00 CDMX ≤ t < lunes siguiente 00:00 CDMX (usar `AppTime.isThisWeek` o el
   rango explícito).
4. Extraer la lógica de rango a una función pura testeable si hoy vive inline en el `@Composable` (no probar
   Compose; probar la función de filtrado/rango).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** #3 (dependencia implícita del offset-cero) + #1 (zona) en los rangos.
**⚠️ CAMBIO DE COMPORTAMIENTO CONSCIENTE (money):** el fin de rango pasa de inclusivo (`23:59:59`) a
exclusivo (`00:00:00` del día siguiente) y de zona del dispositivo a CDMX. Esto **alinea** la consulta con
lo que `msp-api` espera (`[desde, hasta)`) y arregla el borde de medianoche; en la práctica el reporte del
día puede ganar/perder pagos exactamente en el límite — es la corrección. Documentar en el reporte de la
tarea qué endpoints/queries locales cambian de "ambos extremos inclusivos" a "hasta exclusivo".

**Commit:** `fix(payments): rangos de reporte medio-abiertos en zona de negocio`

---

## Task 6 — [MONEY] Escritura de `FECHA_HORA_PAGO` vía `AppClock`

**Meta:** que los timestamps de pago/condonación se generen por `AppClock` (testable, determinista) en vez
de `Instant.now().toString()` directo. El formato ya es correcto (`Z`-UTC); el objetivo es testeabilidad +
blindaje contra que alguien lo cambie a algo incorrecto sin que un test lo detecte.

**Archivos (auditoría §1.4):**
- `app/src/main/java/com/example/msp_app/features/payments/components/newpaymentdialog/NewPaymentDialog.kt`
  (161): `Instant.now().toString()` → `AppTime.toWireFormat(clock.now())`.
- `app/src/main/java/com/example/msp_app/features/forgiveness/components/NewForgivenessDialog.kt` (106): idem.
- Inyectar `AppClock` donde se construye el valor (ViewModel del diálogo si existe; si el `@Composable` lo
  genera inline, mover la generación al ViewModel/estado y pasar `AppClock` — mínimo viable sin rediseñar el
  flujo de pago). Si no hay punto de inyección limpio sin tocar el money-path de guardado, reportar el
  hallazgo y usar `AppTime.toWireFormat(AppClock.System.now())` documentando por qué.

**Test primero (TDD) — en `:app`:** con un `AppClock` fake de instante fijo, verificar que el `FECHA_HORA_PAGO`
generado es **exactamente** `AppTime.toWireFormat(fixedInstant)` (`Z`-UTC), y que coincide con el formato que
`msp-api` acepta (reusar aserción de Task 2). Caso de instante con y sin fracción de segundo.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** #10 (no testeable, riesgo de regresión silenciosa).
**⚠️ Cambio de comportamiento:** **ninguno funcional** — el formato emitido es idéntico (`Z`-UTC). Solo gana
testeabilidad vía `FakeClock`. Es money-path, así que se caracteriza y se fija, pero no altera el valor
enviado al backend.

**Commit:** `refactor(payments): generar FECHA_HORA_PAGO via AppClock`

---

## Task 7 — `ClienteSyncWorker`: gate de horario laboral en zona de negocio

**Meta:** que el gate 7–18h del worker de sincronización de clientes se evalúe en hora de `America/Mexico_City`,
no en la hora del dispositivo. El worker no está inyectado por Hilt (Plan 1 lo dejó legacy), así que se
**extrae una función pura testeable** en vez de convertir el worker.

**Archivos:**
- `app/src/main/java/com/example/msp_app/workers/ClienteSyncWorker.kt` (línea 19): reemplazar
  `java.util.Calendar.getInstance().get(Calendar.HOUR_OF_DAY)` por `AppTime.nowInBusinessZone(AppClock.System).hour`.
- Extraer el predicado a una función pura testeable, p.ej. en el mismo módulo:
  `fun isWithinWorkingHours(now: LocalDateTime): Boolean = now.hour in 7 until 18` (o donde encaje según el
  estándar del repo), y el worker la invoca con `AppTime.nowInBusinessZone()`. La constante 7/18 documentada.

**Test primero (TDD) — en `:app`:**
1. `isWithinWorkingHours`: 06:59 → false, 07:00 → true, 17:59 → true, 18:00 → false, 23:00 → false.
2. **Zona:** un instante que es 17:30 CDMX pero 23:30 en la zona del dispositivo (Tijuana/UTC) → el gate lo
   evalúa como dentro de horario (CDMX), no fuera. Correr con `TimeZone.setDefault` distinto a CDMX y fijar
   `AppClock`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** #5.
**⚠️ CAMBIO DE COMPORTAMIENTO CONSCIENTE:** el gate horario pasa de la zona del dispositivo a CDMX. Un
cobrador con el teléfono en otra zona activará/omitirá el sync en el horario correcto de negocio. Es la
corrección del bug.

**Commit:** `fix(workers): evaluar horario laboral de ClienteSyncWorker en zona de negocio`

---

## Task 8 — Lote display A: pagos (sin riesgo de dinero)

**Meta:** migrar los call sites de **display puro** del flujo de pagos de `DateUtils.formatIsoDate` a
`AppTime.formatIsoForDisplay` (mismo fallback a string crudo, pero en zona de negocio y locale fijo). Sin
cambio de lógica de dinero — solo texto en pantalla, que además queda consistente entre dispositivos.

**Archivos (auditoría §1.6):**
- `features/payments/components/paymentitem/PaymentItem.kt` (82,165)
- `features/payments/components/paymentcard/PaymentCard.kt`? → **no**: `PaymentCard.kt` (54-58,118) vive en
  `features/sales/components/paymentcard/` → va en Task 9. Aquí solo lo de `features/payments/`.
- `features/payments/screens/PaymentTicketScreen.kt` (137,148,165)
- `features/home/screens/Home.kt` (139,273)

**Test primero (TDD):** para cada patrón de formateo migrado, un test de la función/helper (extraer si está
inline) que verifique: (a) un ISO `Z` conocido → string esperado en `dd/MM/yyyy [hh:mm a]` en CDMX/es-MX;
(b) el caso `23:00 CDMX` (día siguiente UTC) muestra el día de negocio correcto; (c) ISO malformado → devuelve
el string crudo (fallback documentado), sin crash. Independencia de zona del dispositivo.

**Verificación:** `testDevlocalDebugUnitTest` + `:app:assembleDevlocalDebug` + `ktlintCheck`.

**Bug que corrige:** #1 en display de pagos.
**⚠️ Cambio de comportamiento:** el texto de fecha usa CDMX + `es-MX` fijo en vez de la zona/locale del
dispositivo. Visible solo si el dispositivo estaba en otra zona/locale.

**Commit:** `refactor(payments): mostrar fechas de pagos via AppTime`

---

## Task 9 — Lote display B: ventas, garantías, visitas, misceláneos

**Meta:** terminar los call sites de display puro de `DateUtils` fuera de pagos.

**Archivos (auditoría §1.6):**
- `features/sales/components/paymentcard/PaymentCard.kt` (54-58,118),
  `features/sales/components/paymentshistorysection/PaymentsHistorySection.kt` (57),
  `features/sales/screens/SaleMapScreen.kt` (56, si no se cerró en Task 5),
  `features/sales/screens/SalesListScreen.kt` (56, import),
  `features/sales/screens/UnifiedSalesScreen.kt` (70, import),
  `features/sales/viewmodels/SaleDetailsViewModel.kt` (32),
  `features/sales/screens/SaleDetailsScreen.kt` (375, `LocalDateTime.now()` → `AppTime.nowInBusinessZone(clock)`).
- `features/guarantees/screens/GuaranteesScreen.kt` (143),
  `features/guarantees/screens/GuaranteeDetailScreen.kt` (90),
  `features/guarantees/screens/components/GuaranteeListItem.kt` (48).
- `features/visit/components/NewVisitDialog.kt` (123, `DateUtils.formatLocalDateTime` →
  `AppTime.formatForDisplay(instant)` con el instante de negocio correspondiente).

**Test primero (TDD):** helpers de formateo extraídos + probados igual que Task 8 (ISO conocido → display
esperado; borde 23:00 CDMX; malformado → fallback). Para `SaleDetailsScreen`/`SaleDetailsViewModel`,
`AppClock` fake fija el "ahora".

**Verificación:** `testDevlocalDebugUnitTest` + `:app:assembleDevlocalDebug` + `ktlintCheck`.

**Bug que corrige:** #1 en display de ventas/garantías/visitas.
**⚠️ Cambio de comportamiento:** display en CDMX + `es-MX` fijo (visible solo con dispositivo en otra
zona/locale).

**Commit:** `refactor(sales,guarantees,visit): mostrar fechas via AppTime`

---

## Task 10 — Módulo `transfers`: `LocalDateTime.now()` → `AppTime`/`AppClock`

**Meta:** migrar el módulo de traspasos, hoy entero fuera del estándar, con `LocalDateTime.now()` repetido en
6 sitios para `createdAt`/`updatedAt`/`fecha`.

**Archivos (auditoría §1.4):**
- `features/transfers/.../TransfersRepository.kt` (118-119,159-160,262),
  `features/transfers/.../TransferMappers.kt` (197),
  `features/transfers/.../CreateTransferData.kt` (12),
  `features/transfers/.../NewTransferViewModel.kt` (412).
- Persistencia/wire → `AppTime.toWireFormat(clock.now())`; si algún punto necesita wall-clock de negocio para
  UI, `AppTime.toBusinessDateTime`. Inyectar `AppClock` en repo/viewmodel.

**Test primero (TDD):** con `AppClock` fake, los timestamps generados son `Z`-UTC exactos
(`AppTime.toWireFormat(fixedInstant)`); round-trip parse estable; borde de medianoche CDMX si algún campo se
muestra como día.

**Verificación:** `testDevlocalDebugUnitTest` + `:app:assembleDevlocalDebug` + `ktlintCheck`.

**Bug que corrige:** patrón de riesgo de zona en `transfers` (auditoría §1.4).
**⚠️ Cambio de comportamiento:** timestamps ahora `Z`-UTC vía `AppClock` (antes `LocalDateTime.now()` sin
zona explícita); el instante persistido queda bien definido. Verificar que ningún consumidor dependía del
formato viejo sin `Z`.

**Commit:** `refactor(transfers): timestamps via AppTime/AppClock`

---

## Task 11 — Garantías: `FECHA_EVENTO` con `Z` + escrituras vía `AppClock`

**Meta:** corregir el bug de formato #4 — `GuaranteesLocalDataSource` escribe `FECHA_EVENTO` **sin zona ni
offset** (`LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)` → `"2026-08-08T14:30:00"`), rompiendo el wire
format. Pasa a `AppTime.toWireFormat(clock.now())` (`Z`-UTC). Además migrar los `Instant.now().toString()` de
garantías a `AppClock` (testabilidad).

**Archivos (auditoría §1.4):**
- `features/guarantees/.../GuaranteesLocalDataSource.kt` (99): `LocalDateTime.now().format(ISO_LOCAL_DATE_TIME)`
  → `AppTime.toWireFormat(clock.now())`.
- `features/guarantees/screens/GuaranteesScreen.kt` (468),
  `features/guarantees/.../CreateGuaranteeViewModel.kt` (81):
  `DateTimeFormatter.ISO_INSTANT.format(Instant.now())` → `AppTime.toWireFormat(clock.now())`.

**Test primero (TDD):**
1. Con `AppClock` fake, `FECHA_EVENTO` generado es `Z`-UTC (`AppTime.toWireFormat(fixed)`), NO
   `ISO_LOCAL_DATE_TIME` sin offset.
2. **Backward-compat de lectura:** una fila legacy con `FECHA_EVENTO` sin `Z` (`"2026-08-08T14:30:00"`) sigue
   parseable por `AppTime.parseWireFormat` (branch legacy → asume zona de negocio) sin lanzar — no se
   corrompen filas viejas ya escritas por el código anterior. **Room v27 no cambia** (la columna es TEXT).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** #4.
**⚠️ CAMBIO DE COMPORTAMIENTO CONSCIENTE:** el formato **persistido** de `FECHA_EVENTO` cambia de local-sin-zona
a `Z`-UTC. Filas nuevas quedan bien; filas viejas (sin `Z`) se siguen leyendo vía el branch legacy de
`parseWireFormat` (asumen zona de negocio). Documentar que no se requiere migración de datos Room (el schema
v27 no cambia; solo el contenido del string de aquí en adelante).

**Commit:** `fix(guarantees): escribir FECHA_EVENTO en wire format UTC`

---

## Task 12 — Sitios `SimpleDateFormat` / `Locale.getDefault()`

**Meta:** eliminar los usos sueltos de `SimpleDateFormat`/`Locale.getDefault()` (prohibidos por
`docs/standards/timezones.md`), incluido el round-trip frágil de "última sincronización".

**Archivos (auditoría §1.3):**
- `core/utils/PdfGenerator.kt` (57-58 DateUtils + 294,428 SimpleDateFormat): TODO el archivo migra aquí
  (evitar tocarlo dos veces) → `AppTime.formatForDisplay(clock.now(), Formats.DATE_TIME_24H)` / `formatIsoForDisplay`.
- `core/debug/DbExportManager.kt` (97): naming de archivo → `AppTime.formatForDisplay(clock.now(), "yyyyMMdd_HHmmss")`.
- `core/logging/RemoteLogger.kt` (79-81): timestamp legible → `AppTime.formatForDisplay(...)` (el `Timestamp.now()`
  de Firebase se conserva como fuente real).
- `features/transfers/.../WarehouseProductsBottomSheet.kt` (414): reporte de almacén → `AppTime.formatForDisplay`.
- `features/sales/viewmodels/SalesViewModel.kt` (163) + `features/home/.../HomeStartWeekSection.kt` (67-74):
  romper el acoplamiento del round-trip `Locale.getDefault()` doble (#8) — persistir la "última sync" como
  wire format (`AppTime.toWireFormat(clock.now())`) y leerla con `AppTime.formatIsoForDisplay` (o guardar el
  instante y formatear al mostrar). Ambos lados en el mismo commit.

**Test primero (TDD):** para el par `SalesViewModel`↔`HomeStartWeekSection`, un test que escribe con reloj fijo
y lee → muestra el string esperado, **independiente del `Locale.getDefault()`** del dispositivo (cambiar
`Locale.setDefault` en el test y verificar que no se rompe el parseo — reproduce y elimina #8). Para los
display-only, un test de que el helper formatea un instante fijo al string esperado (es-MX, CDMX).

**Verificación:** `testDevlocalDebugUnitTest` + `:app:assembleDevlocalDebug` + `ktlintCheck`.

**Bug que corrige:** #8 (round-trip locale) + #9 (patrón `SimpleDateFormat` prohibido).
**⚠️ Cambio de comportamiento:** "última sincronización" y textos de PDF/almacén usan `es-MX`/CDMX fijos y
formato wire estable en persistencia; ya no fallan silenciosamente si cambia el locale del sistema.

**Commit:** `refactor(core,sales): reemplazar SimpleDateFormat por AppTime`

---

## Task 13 — Borrar `DateUtils.kt` + blindaje detekt en `:app`

**Meta:** eliminar la fuente del bug y cerrar la puerta a regresiones. Solo se ejecuta cuando Tasks 3–12
dejaron cero referencias a `DateUtils`.

**Archivos:**
- Verificar cero referencias: `grep -rn "DateUtils" app/src core` → vacío (salvo el propio archivo).
  Si queda algún residuo, migrarlo aquí o documentar por qué permanece (con `TODO(tz)` y motivo).
- **Borrar** `app/src/main/java/com/example/msp_app/core/utils/DateUtils.kt` (y su test si existe).
- Confirmar que `PaymentDateGrouping.kt` ya fue borrado en Task 3.
- **Blindaje detekt (auditoría §5 punto 7):** activar `ForbiddenImport` (que ya prohíbe `Instant.now()`,
  `LocalDate.now()`, `LocalDateTime.now()`, `SimpleDateFormat`, `java.util.Date`) también en `:app`, que hoy
  está exento. Como `:app` es legacy, **con baseline**: aplicar `msp.detekt` a `:app` con
  `config/detekt/baseline-app.xml` que capture los sitios preexistentes que NO se migraron en este plan (si
  los hay), de modo que el gate falle solo ante **nuevas** violaciones, no ante deuda no cubierta por este
  plan. **Decisión de alcance a confirmar con el orquestador** (ver Ambigüedad): si aplicar detekt a todo
  `:app` genera demasiado ruido legacy fuera del dominio de fechas, alternativa = una tarea Gradle
  `checkNoLegacyDateApi` con grep acotado a `Instant.now(`/`LocalDate*.now(`/`SimpleDateFormat`/`java.util.Date`
  en `app/src/main`, fallando el build. El implementador usa el que efectivamente pase y lo documenta.

**Test primero (TDD):** N/A unitario; la prueba es el gate. Añadir un fixture negativo que confirme que el
guard elegido (detekt o tarea grep) **falla** ante un `LocalDateTime.now()` nuevo introducido a propósito, y
**pasa** tras quitarlo.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
grep -rn "DateUtils\|formatIsoDateForGrouping" app/src core || echo "sin referencias"
./gradlew detekt                              # con baseline en :app, o la tarea grep equivalente
./gradlew :core:common:testDebugUnitTest :core:database:testDebugUnitTest
./gradlew testDevlocalDebugUnitTest
./gradlew :app:assembleDevlocalDebug
./gradlew ktlintCheck
```

**Bug que corrige:** cierra #1–#10 al eliminar la fuente y blindar contra regresión.
**⚠️ Cambio de comportamiento:** ninguno en runtime (solo se borra código muerto y se agrega un gate).

**Commit:** `chore(app): borrar DateUtils legacy y blindar uso directo de java.time`

---

## Cierre (auditoría de conformidad)

- [ ] `AppTime`/`AppClock` viven en `:core:common` (`com.example.msp_app.core.common.time`), con
      `plusOnWire` agregado y `startOfDay`/`startOfNextDay` documentados como el patrón de rango oficial;
      los 5 adoptantes previos re-apuntan imports; app corre idéntica.
- [ ] **Suite suprema** de `AppTime` (UTC↔CDMX, medianoche, DST México, fin de mes/año, RFC3339 `Z`/`-06:00`/
      millis/nanos/sin-offset, null/blank/malformado, independencia de zona del dispositivo) verde bajo
      `koverVerify` 90% + `detekt` estricto en `:core:common`.
- [ ] **Contract test** vs `msp-api` (emisión `Z`-UTC, parseo `RFC3339Nano`, caso legacy `-06:00`
      `FECHA_HORA_CREACION`, fechas calendario `yyyy-MM-dd`, rango `[desde, hasta)`) verde.
- [ ] **Money-path corregido:** agrupamiento de pagos por día en zona de negocio (copia `:core:database`
      eliminada, `:core:database → :core:common` acíclico); `SettlementCalculator` con hora de negocio;
      rangos de reportes `[desde, hasta)`; `FECHA_HORA_PAGO` vía `AppClock`. Cada uno con test de borde de
      medianoche y de independencia de zona del dispositivo.
- [ ] `ClienteSyncWorker` evalúa el horario 7–18h en CDMX.
- [ ] Display migrado (pagos, ventas, garantías, visitas, Home) a `AppTime.formatIsoForDisplay` (CDMX/es-MX).
- [ ] `transfers` y `FECHA_EVENTO` de garantías en wire format `Z`-UTC vía `AppClock`; lectura legacy
      (sin `Z`) sigue funcionando; **schema Room v27 intacto**.
- [ ] `SimpleDateFormat`/`Locale.getDefault()` eliminados; round-trip de "última sync" (#8) blindado.
- [ ] `DateUtils.kt` y `PaymentDateGrouping.kt` borrados, cero referencias; guard (detekt baseline o tarea
      grep) que falla ante un `*.now()`/`SimpleDateFormat`/`java.util.Date` nuevo en `:app`.
- [ ] Cada cambio de comportamiento money (zona de agrupamiento, `now` de liquidación, rango medio-abierto,
      horario del worker, formato persistido de `FECHA_EVENTO`) está **fijado por un test que pin-ea el
      comportamiento NUEVO correcto** y documentado como corrección consciente vs. el viejo.
- [ ] Commits por tarea, conventional, en español, sin atribución de Claude, sin push, rama
      `feat/multimodulo-cimiento`; ningún gate saltado con `--no-verify`.

### Ambigüedades para el orquestador
1. **Dep `:core:database → :core:common`** (Task 3): el KDoc de `PaymentDateGrouping.kt` afirma que es
   imposible por ciclo; **verifiqué que `:core:common` NO depende de `:core:database`**, así que la
   dependencia es acíclica y permitida. El plan procede con ella. Solo requiere confirmación si el
   orquestador sabe de un plan futuro donde `:core:common` deba depender de `:core:database` (p.ej. una
   `SyncHealthSource` respaldada por Room) — en ese caso, plan B: computar la clave de día en la capa
   `:app`/repositorio, no en el DAO.
2. **Renombre de package** `core.time` → `core.common.time` (Task 1): obligatorio para que la suite quede
   bajo el gate Kover de `:core:common`. Toca los imports de los 5 adoptantes actuales. Se asume aprobado;
   flag por si se prefiere conservar el package viejo (implicaría excluirlo del gate — no recomendado).
3. **Alcance del blindaje detekt en `:app`** (Task 13): activar `ForbiddenImport` en todo `:app` legacy
   puede generar ruido fuera del dominio de fechas → se propone baseline, o el fallback de tarea grep
   acotada a las 4 APIs prohibidas. Confirmar preferencia (o dejar que el implementador use la que pase).
