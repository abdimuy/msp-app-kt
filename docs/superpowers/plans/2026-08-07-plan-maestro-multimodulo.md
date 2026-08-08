# Plan maestro — Migración multi-módulo + Hilt + Reporte de cobranza (piloto)

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`).
> La sesión principal orquesta; los subagentes implementan/revisan/corrigen.
> Repo de trabajo: `msp-app-kt` (app Android real de cobradores). Rama: `feat/multimodulo-cimiento`.

## Context (por qué)
`msp-app-kt` es hoy **un solo módulo `:app`** (paquete `com.example.msp_app`, 19 features,
`AppDatabase` Room v27, **sin DI framework**, look Material default). Migramos progresivamente
(strangler-fig, mismo repo) a **hexagonal multi-módulo con Hilt**, donde cada módulo nace con
**identidad azul (design system "Msp", derivado de kollect)**, **observabilidad** y **tests máximos**.
El piloto es el **reporte de cobranza** rediseñado (tablero, no PDF-first).

Specs fuente (en `docs/superpowers/specs/`):
- `2026-08-07-migracion-arquitectura-msp-app-kt.md` (arquitectura, orden, testing, ideas de calidad)
- `2026-08-07-reporte-cobranza-rediseno-design.md` (design system Msp + rediseño del piloto).
  Mockup: `docs/design/reporte-cobranza-mockup.html`.
Recon: `.superpowers/research/{current-architecture,kollect-app-designsystem,observability-self-hosted}.md`.

---

## Global Constraints (aplican a TODA tarea)
- **Paquete/applicationId `com.example.msp_app` NO se toca** (UpdateChecker/Firestore atados al package).
  Módulos nuevos: `com.example.msp_app.core.*` / `com.example.msp_app.feature.*`.
- **Toolchain fija:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11 (`jvmTarget=11`, desugaring on), Compose BOM 2024.09.00.
  `JAVA_HOME`=JBR de Android Studio (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
- **Flavors:** `devlocal|devserver|prod` × `debug|release` (habilitados: devlocalDebug, devserverDebug,
  devserverRelease, prodRelease). Gate de tests unit = variante **devlocalDebug**.
- **`MAPS_API_KEY` debe existir en `local.properties`** o el build falla (ya está).
- **Código en inglés; strings de usuario en español**. **UI text minimalista** (2-4 palabras).
  Nombres realistas mexicanos en datos de test.
- **Sin push. Sin `--no-verify`. Sin atribución de Claude en commits.** Commits por tarea,
  conventional commits.
- **Rama de trabajo:** `feat/multimodulo-cimiento`.
- **DS naming:** prefijo **`Msp`** (`MspTheme`, `MspTheme.colors.*`, `MspCard`, `MspColors`,
  `MspType`, `MspShapes`, `MspMotion`, `MspSpacing`).
- **Nombre del módulo del piloto:** `:feature:collectionReport` (NUNCA `dailyReport` — ese ya existe
  y es el reporte de inventario del carrito; no romperlo).

---

## Design System "Msp" (resumen operativo; detalle en el spec de diseño)
- **Azul A:** light `brand #2563EB` / `brand2 #1D4ED8` / `brandTint #EAF0FE`; dark `brand #3B82F6` /
  `brand2 #1D5FB0` / `brandTint #0E2440`. Resto de tokens = **iguales a kollect** (`CampoColors.kt`).
  `heroProgressFill = mint-teal #6FE3C2`; verde `statusPaid` se conserva para "efectivo/pagado".
- **Manrope** variable (TTF en `res/font/`, pesos 400/500/600/700/800), cifras `tnum,lnum`.
  Escala = `CampoType.kt`.
- Formas: card 20 (hero 22), tile 16, control 12, button/field 16/14, chip 999. Sombra hairline + 1dp.
- **Componentes firma (portar de kollect, renombrar a Msp):** HeroTodayCard, BentoTile, WeeklyBarsCard,
  CarteraCard (Fase 2), MoneyText (+`formatMoneyMxn` es-MX, `MASKED_MONEY="$••••"`), PrivacyEyeToggle,
  ThemeToggle + ThemeRevealController, SegmentChips, SyncBand/PaymentSyncPill, InitialsAvatar,
  PrimaryFieldButton, BrandGradient, ProgressBar/ProgressRing, StatusChip.
- **Interacciones firma (TODAS desactivables vía reduce-motion):** reveal circular de tema (Telegram-style),
  ocultar cifras ($••••), **barra inferior con difuminado** (`verticalGradient` transparente→fondo, NO sólida),
  transición de tab (crossfade+slide direccional ~300ms), entrada escalonada ≤500ms.
- **Bug learnings → reglas del DS:** `flex-shrink`/`min-height` equivalentes en Compose (no colapsar
  contenido); hero = gradiente plano (sin glow); cifras tabulares siempre.

---

## Accesibilidad (horneada en el template, TODA la app)
- **Default:** contraste **AAA** en hero/estados críticos, número héroe grande, pesos medios/semibold,
  targets 48–56dp, `tnum`, **nunca solo color** (color+ícono+texto).
- **Escala con el SO:** honrar tamaño de fuente/pantalla/negrita; layouts que **refluyen** el dinero hasta ~200%.
- **Opt-in:** preferencia por-usuario "Tamaño de texto" (Normal/Grande/Muy grande); respetar "reducir movimiento".
- **Layouts Tier 1 / Tier 2 en TODAS las pantallas:** Tier 1 (Normal/Grande)=denso responsivo;
  Tier 2 (Muy grande)=layout alterno curado sobre el **mismo estado/ViewModel** (solo cambia `ui/`).

---

## Testing — máximo, "state of the art 2026" (no negociable)
- **Dobles: fakes únicamente** (estado + recording/spy), compartidos en `:core:testing`.
  **Cero MockK/Mockito.** + **Turbine** (Flows) + `kotlinx-coroutines-test`.
- **JVM-first:** Robolectric (Room DAO, migración vía `MigrationTestHelper`, Compose behavior) +
  **Roborazzi** (screenshots con interacción). **Un** emulador headless para instrumentado real (e2e).
- **Taxonomía por capa (obligatorios):** domain=unit puro; app=unit con fakes+Turbine;
  infra-DAO=Robolectric room-testing; infra-migración=MigrationTestHelper; infra-red=MockWebServer/fake;
  ui-pantalla=compose-test (Robolectric); ui-designsystem=Roborazzi; telemetría=fake+aserción; e2e=device.
- **Cobertura por capa (gate):** domain ~90%, app ~80%, infra/ui pragmático. Herramienta: **Kover**.
- **Screenshot por tier × escala de fuente:** 1.0x / 1.3x / 2.0x, Tier 1 y Tier 2, light+dark.
- **Contract test app↔API** en lecturas de dinero; **anti-`Double` para dinero** (regla lint/detekt).
- **Gate (por tarea y pre-push):** `ktlintCheck` + `test<Variant>UnitTest` (todos los módulos) +
  cobertura Kover + Roborazzi verify + build. E2e device cuando aplique.

---

## Secuencia de planes (cada uno 100% verde antes del siguiente)
Detalle bite-sized/TDD en `docs/superpowers/plans/2026-08-07-planN-*.md`.

### Plan 0 — Preparación (rama + andamiaje mínimo)
Rama + `build-logic` (convention plugins `msp.android.library/compose/hilt/test/kover`) +
`libs.versions.toml` completo (todas las deps hoy hardcodeadas + Hilt/Roborazzi/Turbine/Kover/detekt).
**Done:** `./gradlew help` y `:app` compilan igual; catálogo y convention plugins existen; nada roto.

### Plan 1 — Cimiento Gradle + Hilt + `:core:common` + `:core:testing`
`:core:common` (utilidades + outbox endurecido + widget salud de sync). `:core:testing` (fakes,
RobolectricTestBase/RoomTestBase/MainDispatcherRule/TestDataFactory, config Roborazzi).
Hilt en `:app` (@HiltAndroidApp, @AndroidEntryPoint, HiltWorkerFactory, módulos @Provides envolviendo
ApiProvider/V2ApiProvider/ApiProviderImages/ConnectivityMonitor preservando rebuild de baseURL).
Primer feature a Hilt: `Warehouse*`. Hook pre-push (lint+tests+cobertura de todos los módulos).
Lint anti-`Double`. **Done:** app idéntica; tests+cobertura+ktlint verdes; Hilt inyecta sin romper.

### Plan 2 — `:core:database` (hoist + Room safety)
Mover `AppDatabase` (v27) a `:core:database`. `exportSchema=true` + `schemas/` versionado +
KSP `room.schemaLocation`. DB+DAOs por Hilt; reemplazar ~21 `AppDatabase.getInstance`. Harness
MigrationTestHelper (7 migraciones) + test "abrir v27 desde schema" (no pierde pagos no-subidos).
**Done:** migración Room verde JVM; DAOs inyectados; `msp_db` intacto; e2e pagos verde.

### Plan 3 — `:core:designsystem` (tema Msp + componentes + Roborazzi)
Tokens Msp (Azul A + mint-teal + Manrope), MspTheme+M3, ThemeRevealController, BrandGradient,
componentes firma renombrados `Msp*`. Roborazzi por componente Tier 1/2 × 1.0/1.3/2.0 (light+dark).
**Done:** catálogo con goldens grabados; screenshot-verify verde; contraste AAA validado.

### Plan 4 — `:core:telemetry` + `:core:network`
`:core:telemetry`: puerto Telemetry + cola Room durable + Modifier.trackClick + ScreenScope; adapter stub.
`:core:network`: Retrofit/OkHttp base + interceptores (auth bearer, versión) inyectados; NetworkConfig inyectado.
**Done:** telemetría con cola durable testeada; network inyectable; sin regresión.

### Plan 5 — `:feature:collectionReport` (EL piloto)
Dominio (rangos Día/Semana=ciclo, agregados, meta sugerida, VOs sin Double). Data (puertos pagos/visitas/
condonaciones Room + transfers por contrato). UI Compose @HiltViewModel (mockup: toggle Día/Semana, hero+
sparkline, duo Efectivo/Transferencia, chips, lista/resumen, bottom sheets, barra difuminada, ocultar cifras,
reveal tema, Tier 1/2). Extracción de `payments/screens/`, re-apuntar `Screen.DailyReport.route` conservando
literal `"daily_reports"`, absorber WeeklyReport. NO tocar `features/dailyReport` (inventario).
Cartera/zona = Fase 2. Tests dominio/app/UI/e2e + contract transfers.
**Done:** app idéntica salvo el reporte; gate completo verde; WeeklyReport viejo eliminado sin romper drawer.

---

## Protocolo de revisión (parte del gate)
Por TAREA (ciclo SDD): implementador TDD → gate real (ktlint+tests+Kover+Roborazzi+build) →
2 revisores independientes (uno adversarial que verifica que los tests asserten de verdad) → fix-loop.
Por PLAN: auditoría de conformidad contra el Checklist antes de cerrar.

## Checklist de acuerdos (se audita por plan)
- [ ] Hexagonal + YAGNI (puerto solo si ≥2 impl o cruza módulo); sin triple-map ritual.
- [ ] Hilt envolviendo ApiProvider/AppDatabase/Connectivity; ViewModels `@HiltViewModel`; `@HiltWorker`.
- [ ] DS Msp, Azul A + mint-teal, Manrope, tokens de kollect, componentes firma, formas 20/16/12.
- [ ] Interacciones: reveal de tema, ocultar cifras, barra difuminada, transición de tab, todas desactivables.
- [ ] Reporte unificado Día/Semana; Semana = ciclo `FECHA_CARGA_INICIAL`→now; módulo `collectionReport`;
      ruta `daily_reports` conservada; WeeklyReport absorbido; inventario dailyReport intacto.
- [ ] Datos del piloto sin backend nuevo + meta sugerida; Cartera/zona = Fase 2.
- [ ] Tests: fakes-only, Turbine, Robolectric+Roborazzi, migración Room, cobertura 90/80,
      screenshot por tier×escala, anti-`Double`, contract test.
- [ ] Accesibilidad: AAA, targets 48-56, no-color-only, honrar SO, pref tamaño de texto, Tier1/2.
- [ ] Room safety: exportSchema + schemas/ + MigrationTestHelper (no pierde pagos no-subidos).
- [ ] `:core:common` outbox endurecido + widget salud de sync.
- [ ] Gate: sin bypass, commits por tarea, sin push, rama correcta. App corre idéntica salvo lo migrado.

## Orquestación nocturna
Rama `feat/multimodulo-cimiento`; commits por tarea; sin push. Emulador: UN headless (`Pixel_9_Pro`)
solo en e2e device. Autonomía alineada a specs, documentar decisiones; nunca bypass del gate; muro de
entorno → aislar y seguir con trabajo independiente. Progreso en `NIGHT-REPORT.md`.

## Verificación end-to-end (al terminar cada plan)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew ktlintCheck
./gradlew testDevlocalDebugUnitTest        # + test<Module>UnitTest de cada módulo
./gradlew koverVerify
./gradlew verifyRoborazziDevlocalDebug
./gradlew assembleDevserverRelease
./gradlew connectedDevlocalDebugAndroidTest   # Plan 2/5, un emulador
```
