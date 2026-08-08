# Spec: Migración progresiva de arquitectura — msp-app-kt

> **Fecha:** 2026-08-07
> **Estado:** diseño aprobado (brainstorm). Este es el spec del **cimiento + piloto (`dailyReport`)**, y traza el orden completo de migración (**cobranza → ventas → periféricos**). El sistema de diseño (detalle de tokens), el backend de observabilidad y el corte-del-día servidor tienen specs-compañeros.
> **Repo:** `msp-app-kt` (un solo repo git; multi-módulo Gradle). NO es un sub-repo/submódulo.

## 1. Contexto y objetivo

`msp-app-kt` (app Android de cobradores, Kotlin + Jetpack Compose, offline-first) es hoy **un solo módulo Gradle `:app`** con 19 features (~37k LOC), **sin framework de DI** (todo `ApiProvider` manual + ViewModels auto-cableados), `NavHost` plano, y una `AppDatabase` Room monolítica. Funciona, pero: el look es Material default (sin identidad), depurar en campo ha sido el mayor dolor histórico (no había forma de saber qué build trae un teléfono ni de reconstruir escenarios), y el DI manual + la falta de fronteras dificultan iterar.

**Objetivo:** migrar **progresivamente, en el mismo repo** (patrón *strangler-fig*), a una arquitectura **hexagonal multi-módulo** — el patrón que el equipo ya domina (com.bonanza.campo, msp-api, sistema-cobro-web) pero **mejor calibrado** contra sus dolores — donde **cada módulo nuevo nace con identidad visual propia (azul, estilo kollect-app) y observabilidad incorporada**. Nada se reescribe de golpe; se migra pantalla por pantalla sin romper lo existente.

Este trabajo es independiente del sistema Firestore/deploy y del fix de pagos ya desplegado; construye el cimiento sobre el que el resto de features se migran una por una.

## 2. Principios (las mejoras sobre el hexagonal anterior)

1. **Menos ceremonia, YAGNI agresivo.** Un `interface`/puerto **solo** cuando hay ≥2 implementaciones reales o se cruza un borde de módulo — nada de "port por si acaso" con una sola impl. Mappers dto↔domain **solo** cuando el shape difiere de verdad; nada de triple-map ritual. El mejor precedente interno ya existente es `core/sync/pendingwork/` — esa es la plantilla, no el hexagonal recargado.
2. **DI real (Hilt).** Se elimina el `ApiProvider` manual y los ViewModels que se auto-cablean. KSP ya está en el proyecto.
3. **Fronteras por módulo Gradle**, no por convención. Cada feature aislada: no puede alcanzar el interior de otra; comparte solo vía `:core:*`.
4. **Observabilidad por construcción.** Cada módulo nace instrumentado (puerto `Telemetry` + helpers del UI-kit); la PII nunca entra en las etiquetas (LFPDPPP).
5. **Identidad visual por construcción.** Cada módulo consume `:core:designsystem` (tema azul); sus pantallas nunca hardcodean colores.
6. **Reversible por feature.** El `NavHost` enruta viejo↔nuevo; migrar una feature es un cambio acotado y reversible.

## 3. Topología de módulos (target)

```
msp-app-kt/                     (mismo repo git)
├── app/                        shell legacy (se encoge) + composition root (Hilt) + NavHost (puente viejo↔nuevo)
├── core/
│   ├── designsystem/           tema azul (tokens Color/Type/Shapes/Spacing/Motion sobre M3) + UI-kit + reveal oscuro/claro
│   ├── database/               AppDatabase (hoisteada) + DAOs + schema export + tests de migración
│   ├── telemetry/              puerto Telemetry + Modifier.trackClick + ScreenScope/LocalScreenName
│   ├── network/                Retrofit/OkHttp base + interceptores (auth, versión)
│   ├── common/                 utilidades verdaderamente compartidas + outbox endurecido + widget salud de sync
│   └── testing/                fakes compartidos (estado + recording) + reglas de test + config Roborazzi
└── feature/
    ├── dailyReport/            PILOTO (extrae DailyReportScreen de payments/; lee transfers por contrato)
    └── <feature>/              cada feature migrada = su módulo (domain/ · data/ · ui/)
```

- **`:app`** deja de contener features poco a poco; retiene el composition root (Hilt `@HiltAndroidApp`), el `NavHost`, y las pantallas legacy aún no migradas.
- Cada **`:feature:<name>`**: `domain/` (entidades + casos de uso, cero framework) · `data/` (repos + adapters Room/Retrofit; puerto solo si cruza módulo) · `ui/` (Compose + `@HiltViewModel`). Sin capas que solo reenvían.
- **`settings.gradle.kts`** incluye cada módulo. La `libs.versions.toml` (hoy delgada) se completa con las ~30 deps hoy hardcodeadas en `app/build.gradle.kts`, y se crean *convention plugins* (`buildSrc` o `build-logic`) para no repetir configuración por módulo.

## 4. DI con Hilt (migración gradual)

- `:app` → `@HiltAndroidApp`; `@AndroidEntryPoint` en la Activity.
- Envolver lo existente **sin reescribir su interior**: `@Provides @Singleton` para el Retrofit de `ApiProvider`/`V2ApiProvider`, la `AppDatabase`, `ConnectivityMonitor`, etc. — módulos Hilt en `:core:network`/`:core:database`.
- Convertir ViewModels a `@HiltViewModel` con `@Inject constructor(...)` **feature por feature**, retirando cada `ApiProvider.create()` a medida que se migra. Workers → `@HiltWorker` (requiere `androidx.hilt:hilt-work`).
- Regla: mientras una feature siga en `:app` legacy, puede seguir usando el DI viejo; al migrarla a su módulo, pasa a Hilt. No hay "big bang" de DI.

## 5. Estrategia de migración (strangler-fig)

- **Seam de navegación:** el `NavHost` en `:app` enruta cada ruta a la pantalla vieja (en `:app`) o a la nueva (en `:feature:*`). Migrar = crear el módulo, mover la feature, apuntar su `composable(route){}` a la versión nueva, borrar la vieja. Un bloque por ruta.
- **Criterio de orden:** **lo core del negocio primero** (la herramienta diaria del cobrador), no lo periférico. Dentro de cada bloque se ordena por **riesgo interno ascendente**, de modo que el flujo de dinero (pagos) se migre **al final**, ya con el template probado. El piloto (`dailyReport`) es una **excepción deliberada**: se aceptó su acoplamiento (lee `transfers`; su pantalla vive hoy en `payments/`) a cambio de un primer entregable 100% core — el detalle en §9.

**Ola 0 — Cimiento** (este spec): `:core:designsystem`, `:core:database` (con schema export + tests de migración Room), `:core:telemetry`, `:core:network`, `:core:common` (incl. outbox endurecido + widget salud de sync), `:core:testing`, Hilt en `:app`, convention plugins + version catalog, lint anti-`Double`, gate de tests + cobertura. `:core:database` va primero porque la `AppDatabase` monolítica compartida es el mayor obstáculo físico para partir módulos (se hoistea a un módulo compartido, no se parte por feature).

**Bloque 1 — COBRANZA** (lo más core; primero), por riesgo interno ascendente:

| Paso | Feature | Pantalla(s) | Ruta(s) | Notas |
|---|---|---|---|---|
| 1a **(piloto)** | dailyReport | `DailyReportScreen` | `daily_reports` | read-heavy core; **acopla a `transfers`** (~23 refs) y su composable hoy vive en `payments/screens/` → el piloto (i) **extrae la pantalla a su módulo** y (ii) **fija el patrón de lectura cross-módulo por contrato** (`:core:network`/contrato). Riesgo asumido a cambio de un primer entregable 100% core |
| 1b | home | `Home` | `home` | dashboard; su rol de navegación se mantiene **puenteado** mientras el resto migra |
| 1c | visit | `VisitTicketScreen` + captura | `visit_ticket/{saleId}` | **primera captura-pesada**; su migración corrige el bug vivo `RETRY_THEN_DONE` vía el outbox endurecido. La captura se dispara desde `sales` en algunos puntos → trazar ese seam con cuidado |
| 1d | payments | `PaymentTicket` + `WeeklyReportScreen` + captura de pago | `payment_ticket/{paymentId}`, `weekly_reports` | **dinero; último del bloque**, protegido por el outbox ya probado en visitas. Incluye `WeeklyReportScreen` (hoy en `payments/`, lee datos de pagos) |

*(Primer plan = Ola 0 + paso 1a; ver §9.)*

**Bloque 2 — VENTAS** (el otro grande y complicado; ~16k LOC, hub de varias features):

| Feature | Pantallas |
|---|---|
| sales | `Sales`, `SaleDetails`, `SaleMap`, `SaleHome`, `SaleDetailsList`, `SaleDescripction`, `EditSale`, `NewSale` |
| cart | `Cart` |
| products / productsInventory / productsInventoryImages | `ProductsCatalog`, `ProductDetails` |

`sales` probablemente se descompone en su propio esfuerzo; es lo último por tamaño y acoplamiento.

**Después — periféricos** (no son la herramienta diaria): guarantees (`GuaranteeList`/`CreateGuarantee`/`GuaranteeDetail`/`Guarantee`), transfers (`TransfersList`/`TransferDetail`/`NewTransfer`), `RouteMap`, `camionetaAssignment`, warehouses, zones, deviceProtection, forgiveness (condonaciones; sin ruta propia, migra con su host).

## 6. Sistema de diseño — identidad azul (`:core:designsystem`)

Basado 1:1 en el design system de kollect-app (tokens `Colors/Type/Shapes/Spacing/Motion` sobre M3, leídos vía `AppTheme.colors.*`; fuente **Manrope**; formas 20/16dp; dos springs `standard`/`emphasized`).

- **Verde→azul = exactamente 6 valores hex** (el resto — estados, promise, danger, info — es semántico y NO cambia):
  | token | kollect (verde) | propuesta azul |
  |---|---|---|
  | light `brand` | `#0D4A45` | `#0D3B66` |
  | light `brand2` | `#0A3B37` | `#0A2E52` |
  | light `brandTint` | `#EEF5F4` | `#EAF1F9` |
  | dark `brand` | `#1E9E86` | `#3B82F6` |
  | dark `brand2` | `#14705C` | `#1D5FB0` |
  | dark `brandTint` | `#123029` | `#0E2440` |
  (Propuesta, ajustable en implementación o vía mockup visual antes de fijar.)
- **Animación oscuro/claro:** el *reveal circular estilo Telegram* de kollect-app (graphics-layer snapshot del tema viejo + `Animatable` de radio con `tween(380ms, FastOutSlowInEasing)` desde el punto tocado, revelando el tema nuevo; crossfade de 300ms como respaldo). Se porta tal cual.
- Componentes firma (CampoCard/hairline, PrimaryFieldButton con haptic, StatusChip nunca-solo-color, micro-interacciones ≤300ms, el único `emphasized` reservado a "pago confirmado") se replican en el UI-kit.
- (El detalle exhaustivo de tokens/componentes va en el **spec-compañero de design system**; la referencia completa está en `.superpowers/research/kollect-app-designsystem.md`.)

## 7. Observabilidad self-hosted (`:core:telemetry` + backend)

Recomendación híbrida (ver `.superpowers/research/observability-self-hosted.md`), todo en infra propia:

- **App-side (en este spec, agnóstico del backend):**
  - Puerto `Telemetry` en `:core:telemetry` (`screenView` / `tap` / `event` / `error`); el adapter concreto (GlitchTip SDK + cola propia) se cablea SOLO en el composition root — las features nunca importan un SDK de vendor.
  - **Cola offline durable en Room, espejo del outbox de pagos** (estados pending/uploading/sent/failed, batch por N eventos o T segundos o al reconectar, backoff, tope con "nunca tirar errores, best-effort tirar el resto", sampling de eventos ruidosos).
  - Captura Compose: `screenView` automático vía `navController.currentBackStackEntryFlow`; `Modifier.trackClick("nombre-estático")` + `ScreenScope`/`LocalScreenName` en el UI-kit → cada módulo observable por construcción. **Etiquetas siempre estáticas del desarrollador, jamás derivadas de texto/contentDescription** (LFPDPPP).
  - **Crashes/errores:** SDK de Sentry (`io.sentry:sentry-android`) con DSN apuntando a GlitchTip (offline cache incluido).
- **Backend (spec-compañero, cuando se defina el box):** **VM Linux chica** con **GlitchTip** (errores; ~1GB RAM, 4 contenedores, mismo protocolo Sentry) + **Postgres** (tabla `telemetry_events` particionada por día; ingest vía endpoint Go; retención corta LFPDPPP + rollups) + **Metabase** (dashboards/embudos/"qué no se usa"). **SQLite NO** para el store servidor (es solo para la cola en el teléfono).
- **"Recrear cualquier escenario" — expectativa honesta:** en Android nativo no hay video-replay tipo web sin consentimiento por sesión + riesgo LFPDPPP. Lo realista y mejor para depurar es una **línea de tiempo estructurada por sesión** (pantalla→acción→estado→red→error, en orden) — grepeable/diffeable y captura el estado del dominio (saldo mostrado, pendientes de sync) que ninguna herramienta genérica capturaría. Opcional: grabación de pantalla **bajo demanda con consentimiento** (soporte en vivo), nunca siempre-encendida.

### 7.1 Durabilidad de errores — nunca perder uno (mismo principio que el outbox de pagos)
Un error solo puede estar **en el teléfono (pendiente)** o **en el servidor (confirmado)**; nunca se descarta. Dos mecanismos disco-primero:
- **Crashes / ANR / NDK:** el SDK de Sentry serializa el crash **a disco de forma síncrona ANTES de que el proceso muera** (`UncaughtExceptionHandler`, watchdog de ANR, signal handlers nativos) y lo envía en el siguiente arranque → sobrevive a que el proceso se mate.
- **Errores de app + eventos:** `Telemetry.error(...)` → insert síncrono en la cola Room durable → subida en batch → sobrevive a que cierren la app.
- **Ack-based:** nunca se marca "enviado" hasta que el servidor confirma (respuesta HTTP = lo tiene; error de red = reintenta por siempre; WorkManager reintenta tras reinicios). Tope con tier "**nunca tirar errores**". Único caso de pérdida = teléfono destruido/borrado antes de subir (inherente a offline-first, igual que los pagos).

### 7.2 Siempre consultable + debug por-dispositivo a demanda
- **Consultable:** errores en **GlitchTip** (agrupados por issue, stack trace, breadcrumbs, release, device) + **Postgres/Metabase** (SQL transversal). Cada error etiquetado con `appVersion` (git-SHA), `device_id`, cobrador, `screen`, `session_id`, secuencia. **Retención larga para errores** (pocos y valiosos); eventos ruidosos con TTL corto + rollups.
- **Debug de CUALQUIER dispositivo:** (a) por defecto, breadcrumbs + la línea de tiempo de la sesión reconstruyen el contexto sin pedirle nada al cobrador; (b) **switch remoto `debug_targets`** — doc/colección Firestore que el adapter de `Telemetry` **observa** (snapshot listener, como `UpdateChecker`/`ApiProvider`); al listar un `device_id`/cobrador, ese teléfono sube en **verbose** + opcional **grabación de pantalla bajo consentimiento** (acotada), sin desplegar un build especial; se quita de la lista y vuelve a normal.

### 7.3 Analítica de uso — entender cómo usan la app para mejorarla
Reusa el mismo puerto/cola/Postgres. **Taxonomía:** `screen_view` (pantalla+duración → detecta lo que NO se usa), `tap` (pantalla+elemento estático), **pasos de embudo** en procesos clave (p.ej. cobro: abrir cliente→Agregar Pago→monto→confirmar→enviado), **eventos de negocio** (`pago_capturado`/`venta_creada`/`visita_registrada`/`condonación` con props no-PII: rango de monto, forma de cobro, offline/online, duración), `lifecycle`, `performance`. **Responde:** embudos (dónde abandonan), adopción de features (qué % de cobradores; qué **no** se usa), pantallas/botones muertos, fricción (tiempo+reintentos+errores), tiempo por tarea comparado **antes/después por SHA**, segmentación por cobrador/zona/versión. **Disciplina:** eventos **atados a preguntas de producto** (no "todo por capturar"); un dashboard por objetivo; etiquetas sin PII.

### 7.4 Librería vs propio (dependencias)
- **Librería/OSS (no se reinventa):** `io.sentry:sentry-android` (crashes/ANR/NDK + offline-cache a disco) → **GlitchTip** (backend de errores, Docker) ; **Metabase** (dashboards).
- **Propio pero simple (reusa lo existente):** puerto `Telemetry`, `Modifier.trackClick` (~10 líneas — ninguna lib captura clicks de Compose de forma confiable), `screen_view` desde el NavHost, **cola offline Room** (copia del outbox de pagos), endpoint de ingest Go + tabla `telemetry_events`.
- **Por qué no Firebase Analytics / Amplitude / PostHog:** o mandan datos a la nube del vendor (rompe self-hosted/LFPDPPP), o (PostHog self-hosted) arrastran Kafka+ClickHouse+7 servicios, desproporcionado a la escala. El pipeline propio es menos que operar y con control total.

## 8. Versionado / trazabilidad (ya implementado, se conserva)

`versionName` en `build.gradle` es la única fuente; los flavors de test estampan git-SHA (`2.12.2-dev+<sha>`) visible en footer/drawer y en el `appVersion` de los logs. `Constants.APP_VERSION` se deriva de `BuildConfig.VERSION_NAME.substringBefore("-")` (base de release, para el update-check). Cada módulo/feature reporta a `Telemetry` con ese `appVersion`, así cualquier evento/error es atribuible al commit exacto.

## 9. Alcance del PRIMER plan (cimiento + piloto `dailyReport`)

1. Version catalog completo + convention plugins (`build-logic`) para módulos Android/Compose/Hilt.
2. `:core:designsystem` con el tema azul (6 hex) + el reveal oscuro/claro + UI-kit base (Card, Button, StatusChip, ProgressBar, Money) — con tests de screenshot (Roborazzi).
3. `:core:database` (hoist de `AppDatabase` + DAOs) **con `exportSchema=true`, carpeta `schemas/` versionada y harness de tests de migración (`MigrationTestHelper`)** — la red que impide borrar pagos no-subidos al mover esquema (ver §12). El esquema no cambia en este plan; se instala la red.
4. `:core:telemetry` (puerto + cola Room offline + `Modifier.trackClick`/`ScreenScope`) apuntando a un adapter stub/GlitchTip (backend real = spec aparte).
5. `:core:common` (incl. **outbox endurecido** §13 #1 + **widget de salud de sync** §13 #2) + `:core:testing` (fakes compartidos, reglas de test, config Roborazzi).
6. Hilt en `:app` (`@HiltAndroidApp`, `@Provides` envolviendo lo existente).
7. Lint **anti-`Double` para dinero** (detekt/regla) + gate **pre-push** (tests JVM + ktlint) + **cobertura por capa** (domain ~90% / app ~80% / infra-ui pragmático) vía tarea Gradle.
8. **Migrar `:feature:dailyReport` (`DailyReportScreen`)** end-to-end como prueba del template: módulo Gradle, hexagonal (domain/data/ui), `@HiltViewModel`, tema azul, `trackClick`, ruta `daily_reports` re-apuntada en el `NavHost`, pantalla vieja borrada. Como la pantalla vive hoy en `payments/screens/` y su data acopla a `transfers` (~23 refs), este paso además (a) **extrae `DailyReportScreen` de `payments/`** y (b) **fija el patrón de lectura cross-módulo por contrato** (`:core:network` o contrato de solo-lectura hacia transfers/ventas) — la primera prueba real de fronteras entre módulos. `WeeklyReportScreen` **NO entra** (vive en `payments/`, migra en el paso 1d). Tests por capa (§12).
9. Gate: `./gradlew :app:testDevlocalDebugUnitTest` + `connectedDevlocalDebugAndroidTest` (migración Room) + `ktlintCheck` + `assembleDevserverRelease` verdes; la app corre idéntica salvo la pantalla de reporte diario (ahora en su módulo, con tema azul + telemetría).

## 10. Specs-compañeros (después del cimiento)
- **Design system (detalle):** tokens completos, todos los componentes firma, dark/light, guía de uso.
- **Backend de observabilidad:** VM Linux, docker-compose (GlitchTip + Postgres + Metabase), esquema `telemetry_events`, endpoint de ingest Go, retención/rollups LFPDPPP, dashboards.
- **Migración de features restantes:** orden, y el plan específico para `payments` y la descomposición de `sales`.
- **Corte del día reconciliador (msp-api):** endpoint/servicio que concilia capturado-vs-servidor y marca discrepancias (la mitad servidor de §13 #2).
- **Cifrado en reposo (SQLCipher):** tras estabilizar `:core:database`; su builder queda abstraído desde ya para que entre sin reescribir (§13 #9).

## 11. Riesgos y mitigaciones
- **`AppDatabase` monolítica:** se hoistea a `:core:database` (módulo compartido), no se parte por feature — evita romper 10 features a la vez.
- **`sales` (hub de 11 features):** intocable hasta el final; se descompone en su propio esfuerzo.
- **Los paquetes no coinciden con las fronteras de feature:** hallazgo del análisis — `DailyReportScreen` y `WeeklyReportScreen` viven en `features/payments/` pese a ser reportes; `dailyReport` acopla a `transfers`. Cada ola debe **verificar la ubicación real de cada pantalla** (no asumir por nombre de ruta) y reubicarla al módulo correcto como parte de su migración.
- **Regresiones al migrar:** cada feature migrada mantiene paridad de comportamiento; el `NavHost` permite volver a la versión vieja si algo falla.
- **Sobre-ingeniería:** el principio YAGNI (§2) es explícito y se verifica en review (nada de puertos de una sola impl).
- **Churn de Compose/Hilt entre versiones:** convention plugins centralizan versiones; se evita instrumentación por reflexión frágil (se prefiere `trackClick` explícito).

## 12. Modelo de testing (horneado en el template)

Cada módulo migrado nace con un esqueleto de tests por capa; "terminado" es imposible sin los obligatorios (red de AI-safety equivalente al lefthook de msp-api). Hoy el repo tiene **69 tests unit / 5 instrumentados** y **ninguna** librería más allá de JUnit4 + Espresso + compose-ui-test; este plan instala el stack.

- **Dobles de prueba: fakes únicamente.** Cada puerto trae un fake in-memory de referencia; para verificar interacción ("se encoló aunque falle la red", "se emitió el evento una vez") se usa un **recording/spy fake**. Los fakes compartidos viven en `:core:testing`. **Sin MockK/Mockito** — más robusto (afirma resultado, no llamadas) y coherente con el lado Go. + Turbine (Flows) + `kotlinx-coroutines-test`.
- **JVM-first.** Todo lo que se pueda corre en JVM vía **Robolectric** (Room DAO, Compose de comportamiento) y **Roborazzi** (screenshots con interacción). Solo van a **device**: los tests de **migración Room** (`MigrationTestHelper`) y el **golden-path e2e** (capturar-pago-offline→sync).
- **Taxonomía por capa:**

| Capa | Tipo | Corre en | Herramienta | Nivel |
|---|---|---|---|---|
| domain (VOs, máquinas de estado, dinero, invariantes) | unit puro | JVM | JUnit + kotlin.test/Truth | Obligatorio, cobertura alta |
| app (casos de uso) | unit con fakes | JVM | JUnit + fakes + Turbine + coroutines-test | Obligatorio |
| infra: Room DAO | integración | JVM (Robolectric) | room-testing in-memory | Obligatorio si persiste |
| infra: Room migración | migración | device | `MigrationTestHelper` + schema export | Obligatorio en `:core:database` |
| infra: red | serialización/errores/ack | JVM | MockWebServer o fake | Obligatorio si hay red |
| ui: pantalla | comportamiento | JVM (Robolectric) | compose ui-test | Por feature (piloto sí) |
| ui: design system | screenshot/golden | JVM | Roborazzi | Obligatorio en `:core:designsystem` |
| telemetría | contrato de eventos | JVM | fake `Telemetry` + aserción de taxonomía | Obligatorio (baked) |
| e2e camino crítico | golden-path | device | compose + Room real | Pocos, alto valor |

- **Enforcement:** gate **pre-push** (tests JVM + ktlint) + **cobertura por capa** (domain ~90% / app ~80% / infra-ui pragmático) vía tarea Gradle. Es la red que hace imposible "terminar" un módulo sin sus obligatorios.

## 13. Ideas de calidad y roadmap

Decisiones de brainstorm sobre "saltos de calidad", con su disposición:

**Dentro del alcance de migración (horneadas en el template o como infra `:core`):**
- **Tier A (casi gratis por módulo):** ergonomía de campo (targets/contraste/haptics, vía el design system), screenshot/golden tests del design system, invariantes auto-diagnosticables → telemetría (gemelo UUID, pago sin folio, saldo negativo), **regla anti-`Double` para dinero**.
- **Tier B (infra de una sola vez):** release health + **rollout escalonado** (crash-free por SHA en GlitchTip; escalonar el flip de Firestore `LATEST_VERSION` cohorte 10%→todos), **feature flags / kill-switch** (reusa el patrón config Firestore), **modo soporte in-app** (cola de sync + últimos errores + versión).
- **#1 — Outbox endurecido a TODO:** el patrón disk-first/ack-based de pagos se vuelve **infra del template** (`:core:common`); toda feature de captura (visitas, ventas, garantías, condonaciones) lo hereda. El bug vivo `RETRY_THEN_DONE` de visitas se corrige **al adelantar su migración** (Bloque 1, paso 1d).
- **#2 — Salud de sync + corte del día:** el **widget app-side** (pendientes vs confirmados) se hornea en `:core`; el **reconciliador servidor** (corte del día) es spec-compañero en msp-api (§10).

**Roadmap (después; no urgen):**
- **#9 — SQLCipher (cifrado en reposo):** no se apila con la migración Room del cimiento (dos cambios de DB riesgosos a la vez). El builder de `:core:database` queda abstraído para que entre sin reescribir; spec aparte tras estabilizar.
- **#10 — Baseline profiles (moto g24):** optimización guiada por la taxonomía `performance` de telemetría (medir primero, optimizar después).
