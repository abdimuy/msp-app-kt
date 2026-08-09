# Plan 3 — `:core:designsystem` (tema Msp azul + componentes firma + Roborazzi)

Parte del plan maestro `2026-08-07-plan-maestro-multimodulo.md`. Specs fuente:
`docs/superpowers/specs/2026-08-07-reporte-cobranza-rediseno-design.md` (design system Msp + rediseño del
piloto) y el **mockup fuente de verdad** `docs/design/reporte-cobranza-mockup.html`. Recon 1:1 del sistema
de kollect: `.superpowers/research/kollect-app-designsystem.md` (transcripción verbatim de `CampoColors.kt`,
`CampoType.kt`, `CampoShapes.kt`, `CampoTheme.kt`, componentes). Continúa donde queda **Plan 2**
(`2026-08-07-plan2-database.md`): ya existen `build-logic` (convention plugins
`msp.android.library/compose/hilt/test/kover/detekt`), `:core:common`, `:core:testing` (con
`RobolectricTestBase`, `RoomTestBase`, `MainDispatcherRule`, `FakeClock`, `RoborazziConfig.CHANGE_THRESHOLD`
= `0.01f`, y roborazzi expuesto como `api`), la regla detekt `NoDoubleForMoney` en
`:build-tools:detekt-rules`, y el gate agregado `prePushCheck` en el `build.gradle.kts` raíz.

Este plan crea el módulo **`:core:designsystem`** (namespace `com.example.msp_app.core.designsystem`): tokens
Msp (Azul A + mint-teal + Manrope), `MspTheme` sobre M3, `ThemeRevealController`, los componentes firma
renombrados `Msp*`, y el **gate visual Roborazzi** (goldens por componente, Tier 1/2 × escala {1.0, 1.3, 2.0}
× {light, dark}) + validación de contraste AAA. Al terminar, el catálogo tiene goldens grabados,
`verifyRoborazzi` verde, y `:app` sigue **idéntico** (nadie consume el DS todavía — el piloto lo consume en
Plan 5). El DS **no** trae red ni Room; es UI pura.

> Ejecución orquestada por subagentes (skill `superpowers:subagent-driven-development`): implementador TDD →
> gate real → revisores (uno adversarial que verifica que los tests asserten de verdad) → fix-loop, una tarea
> a la vez. Reglas comunes de despacho: `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`.

---

## Global Constraints (vinculan a TODA tarea de este plan)

- **Toolchain FIJA, no cambiar:** AGP 8.10.1, Kotlin 2.0.21, KSP 2.0.21-1.0.27, compileSdk 35, minSdk 24,
  targetSdk 35, Java 11 (`jvmTarget=11`, desugaring on), Compose BOM 2024.09.00, Gradle wrapper 8.11.1.
- **`JAVA_HOME` en CADA comando gradle:** `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.
  Correr **UN** solo comando gradle a la vez (build lock).
- **Variante de gate:** `:core:designsystem` es librería sin flavors → sus unit test/screenshot corren en
  `debug`: `:core:designsystem:testDebugUnitTest`, `:core:designsystem:recordRoborazziDebug` /
  `:core:designsystem:verifyRoborazziDebug`.
- **Paquete/`applicationId` `com.example.msp_app` NO se toca.** El módulo nuevo usa namespace
  **`com.example.msp_app.core.designsystem`**; todo el código bajo `com.example.msp_app.core.designsystem.*`
  (subpaquetes `theme/`, `component/`, `icon/`, `text/`).
- **DS naming — prefijo `Msp` (obligatorio, master):** `MspTheme`, `MspTheme.colors.*`, `MspTheme.type.*`,
  `MspTheme.spacing.*`, `MspTheme.shapes.*`, `MspTheme.motion.*`, `MspColors`, `MspTypography`, `MspShapes`,
  `MspSpacing`, `MspMotion`, `MspSurface`, `MspCard`, `MspIcons`, y cada componente `Msp*` donde el nombre de
  kollect no lo lleva (p.ej. kollect `HeroTodayCard` → `MspHeroTodayCard`; `MoneyText` → `MspMoneyText`;
  `StatusChip` → `MspStatusChip`; `BentoTile` → `MspBentoTile`; etc.). **`formatMoneyMxn`** y
  **`MASKED_MONEY`** conservan ESE nombre exacto (contrato citado por spec).
- **Anti-`Double` para dinero (detekt `NoDoubleForMoney`, ya activo vía `msp.detekt`):** ningún parámetro,
  propiedad o retorno de dinero es `Double`/`Float`. `MspMoneyText` y `formatMoneyMxn` reciben un **tipo money
  dedicado** — usar `java.math.BigDecimal` (es la representación money del proyecto; el piloto Plan 5 introduce
  el VO de dominio). Nunca formatear un `Double` crudo. El nombre del parámetro money evita el matcher de la
  regla solo si el tipo NO es `Double`/`Float`; usar `BigDecimal` lo satisface.
- **Kill-switch baseURL:** N/A en este módulo (el DS no sostiene ningún API service — no hay red). Se deja la
  regla visible: NO introducir `@Singleton` sobre nada con estado de red aquí (no aplica: no hay red).
- **Testing:** dobles = **fakes únicamente** (estado + recording/spy). **CERO MockK/Mockito.** + Turbine
  (Flows) + `kotlinx-coroutines-test`. Screenshot = **Roborazzi** sobre Robolectric (JVM, sin device).
  Cobertura por **Kover**: el DS es UI (pragmático); el piso placeholder de `msp.kover` basta. La lógica pura
  del módulo (`formatMoneyMxn`, enmascarado, `lerpMspColors`, helper de contraste, `maxDistanceToCorner`) SÍ
  se cubre a fondo con unit tests JVM, pero NO se impone el umbral ~90% de domain (ese gate es de
  `:core:common`). No hay gate de cobertura repo-wide.
- **Accesibilidad horneada (spec §5, master):** contraste **AAA** en hero/estados críticos; nunca solo color
  (color + ícono + texto) en todo lo que codifique estado; targets 48–56dp (`MspSpacing.touchTarget = 56.dp`);
  cifras tabulares/lining en dinero; el dinero **refluye, no se trunca** hasta ~200% de escala de fuente.
  Todas las animaciones **desactivables** por reduce-motion (`rememberReducedMotionEnabled()`).
- **Commits por tarea**, conventional commits, subject en **español**, **SIN atribución de Claude**,
  **SIN `--no-verify`**, **sin push**. Rama: `feat/multimodulo-cimiento`.
- **Código en inglés; strings de usuario en español**, minimalistas (2–4 palabras). Datos de test con nombres
  realistas mexicanos (`"María López Hernández"`, `"Gabriel Roque"`, importes reales).

### Regla de tokens (decisión de diseño, no re-litigar)
El spec §2.1 es explícito: **"se adopta el sistema de tokens de kollect, remapeado SOLO en la marca a azul;
todos los tokens semánticos y neutros se heredan 1:1 de kollect (`CampoColors.kt`)"**. Por tanto, en este
plan **solo cambian 6 hex de marca + el `heroProgressFill`**; TODO lo demás (neutros, status, promise, danger,
info, teal, tracks) se copia **verbatim** de la tabla de `.superpowers/research/kollect-app-designsystem.md`
§1.2 (light) y §1.3 (dark). Los valores exactos están inline en la Task 2.

> **Parked for user (tokens neutros vs mockup):** el mockup (`reporte-cobranza-mockup.html`, declarado "fuente
> de verdad") usa neutros dark **azul-grisáceos** (`surface #0F1520`, `outline #1E2836`, `muted #8B99AC`,
> `track #182230`) mientras kollect usa neutros dark **verde-grisáceos** (`surface #141917`, `outline #28322C`,
> `muted #8B968F`). Este plan sigue la instrucción escrita del spec (**neutros 1:1 de kollect**) porque es el
> deliverable autoritativo y el reskin definido es "solo la marca a azul". Si en el piloto (Plan 5) el revisor
> de fidelidad visual dedicado determina que los neutros dark deben virar al azul del mockup, ese es el lugar
> de decisión (el DS expone los tokens en un solo archivo; cambiarlos ahí recalcula todo). No bloquear por
> esto: implementar kollect 1:1 y dejar la nota.

### Bring-up de Roborazzi (cómo cada componente se verifica)
El **plugin** Roborazzi y las deps de screenshot se instalan en la **Task 1** (build wiring). La **base de
screenshot** (`MspScreenshotTest` + host de preview + primer golden que prueba el pipeline light/dark/escala)
se crea en la **Task 5** (junto al tema, primer render posible). De la Task 6 en adelante, **cada tarea de
componente graba su golden baseline** (light + dark a escala 1.0 como su verificación TDD). La **Task 10**
expande al **matriz completa** Tier 1/2 × {1.0, 1.3, 2.0} × {light, dark}, agrega la validación de contraste
AAA y la aserción de **no-truncación de dinero** a escala grande, y cablea `verifyRoborazziDebug` +
`:core:designsystem:*` al `prePushCheck`.

### Comando de gate (por tarea, ajustando el alcance)
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:verifyRoborazziDebug      # cuando existan goldens (Task 5+)
./gradlew :core:designsystem:detekt
./gradlew ktlintCheck
./gradlew :app:assembleDevlocalDebug                   # :app sigue idéntico
```
Grabar/regrabar goldens (cuando cambian a propósito): `./gradlew :core:designsystem:recordRoborazziDebug`.

---

## Task 1 — Crear módulo `:core:designsystem` (esqueleto verde + Manrope + Roborazzi wiring)

**Meta:** que `:core:designsystem` exista como Android-library con Compose, aplicando los convention plugins +
ktlint + el plugin Roborazzi, con la fuente **Manrope** empaquetada y una clase placeholder que compile —
**sin tokens ni componentes todavía**. Aísla "¿el módulo se levanta con Compose + Roborazzi + detekt?" del
contenido real (Tasks 2+).

**Archivos a crear / tocar:**
- `settings.gradle.kts` (raíz) → añadir `include(":core:designsystem")` junto a los `include(...)` existentes
  (`:app`, `:core:common`, `:core:database`, `:core:testing`, `:build-tools:detekt-rules`).
- `core/designsystem/build.gradle.kts`:
  ```kotlin
  plugins {
      id("msp.android.library")
      id("msp.android.compose")     // Compose + bundle compose-ui (incluye material3) + tooling debug
      id("msp.detekt")
      id("msp.kover")
      alias(libs.plugins.ktlint)
      alias(libs.plugins.roborazzi)
  }
  android {
      namespace = "com.example.msp_app.core.designsystem"
      testOptions { unitTests.isIncludeAndroidResources = true }   // Robolectric ve res/font + qualifiers
  }
  dependencies {
      implementation(libs.androidx.compose.foundation)
      implementation(libs.androidx.compose.material.icons.core)
      // material3 y compose-ui ya vienen del bundle en msp.android.compose.
      testImplementation(project(":core:testing"))  // trae RobolectricTestBase + roborazzi (api) + junit + turbine
      testImplementation(libs.androidx.ui.test.junit4)   // createComposeRule en test JVM (Roborazzi)
      // Si el bundle no expone ui-test/roborazzi en el sourceSet test, añadir explícito:
      // testImplementation(libs.roborazzi); testImplementation(libs.roborazzi.compose); testImplementation(libs.roborazzi.junit.rule)
  }
  ```
- **Manrope font asset:** copiar `manrope_variable.ttf` desde kollect
  (`/Users/aldrichcortero/developer/kollect-app/core/designsystem/src/main/res/font/manrope_variable.ttf`,
  165420 bytes, ya verificado accesible) a
  `core/designsystem/src/main/res/font/manrope_variable.ttf`. Es una fuente TTF variable (pesos 400/500/600/
  700/800 derivados por `FontVariation`). Verificar que `.gitignore` NO excluya `*.ttf` bajo `res/font`.
- `core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/.gitkeep` (o el placeholder del test).
- `core/designsystem/src/main/AndroidManifest.xml` — solo si el build lo pide (mínimo `<manifest/>`).

**prePushCheck (wiring parcial en esta tarea):** añadir al `tasks.register("prePushCheck")` del
`build.gradle.kts` raíz las tareas que YA existen para este módulo:
`:core:designsystem:ktlintCheck`, `:core:designsystem:testDebugUnitTest`, `:core:designsystem:detekt`.
(`verifyRoborazziDebug` se suma en la Task 10, cuando ya haya goldens que verificar — agregarlo antes haría
fallar el gate por falta de goldens.)

**Test primero (TDD):** un `ModuleSmokeTest` mínimo en `:core:designsystem`
(`core/designsystem/src/test/kotlin/.../ModuleSmokeTest.kt`) que afirme `2 + 2 == 4` — prueba que la toolchain
de test (JUnit vía `msp.test` traído por `:core:testing`) arranca. Rojo→verde. El contenido real llega en
Task 2+.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:detekt
./gradlew ktlintCheck
./gradlew :app:assembleDevlocalDebug
```
Los cuatro `BUILD SUCCESSFUL`. `:app` sigue idéntico (aún no depende de `:core:designsystem`).

**Gotchas:**
- `msp.android.compose` **presupone y aplica** `msp.android.library` — no aplicar library dos veces (el compose
  plugin hace `pluginManager.apply("msp.android.library")`). Aplicar `id("msp.android.compose")` basta para lo
  Android; igual dejar `id("msp.android.library")` explícito es idempotente (patrón del repo). Confirmar que no
  choquen.
- El plugin Roborazzi (`libs.plugins.roborazzi`) registra `recordRoborazziDebug`/`verifyRoborazziDebug` solo si
  hay tests con capturas; en esta tarea aún no hay goldens → no invocar `verifyRoborazzi` todavía.
- `unitTests.isIncludeAndroidResources = true` es necesario para que Robolectric cargue `res/font` y honre
  qualifiers de `fontScale` (Tasks 5/10).
- No aplicar `msp.hilt` — el DS no usa DI (YAGNI; UI pura sin inyección).

**Commit:** `feat(core-designsystem): crear modulo base con Compose, Manrope y Roborazzi`

---

## Task 2 — Tokens de color: `MspColors` + light/dark (Azul A + mint-teal) + lerp + M3

**Meta:** el archivo de color único del DS: la `data class MspColors` (mismos 30 tokens que `CampoColors`),
las factories `mspLightColors()` / `mspDarkColors()` (neutros/status 1:1 de kollect, **solo marca a azul** +
`heroProgressFill` mint-teal), la interpolación `lerpMspColors` (crossfade de tema token-a-token) y el mapeo
`toColorScheme(darkTheme)` a un M3 `ColorScheme` (para que los componentes stock de M3 hereden valores
sanos). Es la fuente única de color; ningún otro archivo hardcodea un `Color(0xFF…)` de marca.

**Archivo:** `core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/theme/MspColors.kt`.

**`data class MspColors`** (`@Immutable`), con exactamente estos campos `Color`:
`brand, brand2, onBrand, brandTint, background, surface, surface2, onSurface, onSurfaceMuted, outline,`
`statusPaid, statusPaidTint, statusPartial, statusPartialTint, statusOverdue, statusOverdueTint, statusPending,`
`statusPendingTint, statusInfo, statusInfoTint, statusTeal, statusTealTint, danger, dangerTint, onDanger,`
`promise, promiseTint, navSurface, heroProgressFill, progressTrack, chartTrack`.

**`mspLightColors()` — valores EXACTOS (Azul A en marca; resto 1:1 kollect):**
| token | hex | token | hex |
|---|---|---|---|
| `brand` | `0xFF2563EB` **(azul)** | `statusOverdue` | `0xFFB42318` |
| `brand2` | `0xFF1D4ED8` **(azul)** | `statusOverdueTint` | `0xFFFBE7E4` |
| `onBrand` | `0xFFFFFFFF` | `statusPending` | `0xFF5C6863` |
| `brandTint` | `0xFFEAF0FE` **(azul)** | `statusPendingTint` | `0xFFEDF0EF` |
| `background` | `0xFFF4F6F5` | `statusInfo` | `0xFF2F5EA8` |
| `surface` | `0xFFFFFFFF` | `statusInfoTint` | `0xFFE9EEF8` |
| `surface2` | `0xFFFBFCFC` | `statusTeal` | `0xFF0E7C8A` |
| `onSurface` | `0xFF141A18` | `statusTealTint` | `0xFFDFF0F2` |
| `onSurfaceMuted` | `0xFF5C6863` | `danger` | `0xFF9F1239` |
| `outline` | `0xFFE4E8E6` | `dangerTint` | `0xFFFCE7EF` |
| `statusPaid` | `0xFF177245` | `onDanger` | `0xFFFFFFFF` |
| `statusPaidTint` | `0xFFE4F1E9` | `promise` | `0xFF7A5AF8` |
| `statusPartial` | `0xFFB26A00` | `promiseTint` | `0xFFEEEAFD` |
| `statusPartialTint` | `0xFFFBEEDC` | `navSurface` | `0xFFFFFFFF` |
| `heroProgressFill` | `0xFF6FE3C2` **(mint-teal)** | `progressTrack` | `0xFFE2E8E5` |
| | | `chartTrack` | `0xFFDDE7E3` |

**`mspDarkColors()` — valores EXACTOS (OLED; marca azul; `heroProgressFill` = mismo mint):**
| token | hex | token | hex |
|---|---|---|---|
| `brand` | `0xFF3B82F6` **(azul)** | `statusOverdue` | `0xFFF26A5C` |
| `brand2` | `0xFF1D5FB0` **(azul)** | `statusOverdueTint` | `0xFF2E1613` |
| `onBrand` | `0xFFFFFFFF` | `statusPending` | `0xFF96A19A` |
| `brandTint` | `0xFF0E2440` **(azul)** | `statusPendingTint` | `0xFF1D2521` |
| `background` | `0xFF000000` **(OLED puro)** | `statusInfo` | `0xFF74A2E8` |
| `surface` | `0xFF141917` | `statusInfoTint` | `0xFF14243A` |
| `surface2` | `0xFF1C2320` | `statusTeal` | `0xFF33B6C9` |
| `onSurface` | `0xFFE9EFEC` | `statusTealTint` | `0xFF0B2A30` |
| `onSurfaceMuted` | `0xFF8B968F` | `danger` | `0xFFFB7185` |
| `outline` | `0xFF28322C` | `dangerTint` | `0xFF2E1119` |
| `statusPaid` | `0xFF40CB84` | `onDanger` | `0xFF210A07` |
| `statusPaidTint` | `0xFF0F2A1C` | `promise` | `0xFFAD9BFB` |
| `statusPartial` | `0xFFE3AC4E` | `promiseTint` | `0xFF1E1A33` |
| `statusPartialTint` | `0xFF2C220F` | `navSurface` | `0xFF111614` |
| `heroProgressFill` | `0xFF6FE3C2` **(mint-teal, igual que light)** | `progressTrack` | `0xFF28322C` |
| | | `chartTrack` | `0xFF28322C` |

**`lerpMspColors(start, stop, fraction)`** — devuelve `MspColors` con **cada** propiedad interpolada vía
`androidx.compose.ui.graphics.lerp(a, b, fraction)`. Es el motor del crossfade de tema (fallback) en Task 5.

**`internal fun MspColors.toColorScheme(darkTheme: Boolean): ColorScheme`** — copia base
`darkColorScheme()`/`lightColorScheme()` mapeando (idéntico a kollect §1.5): `primary=brand, onPrimary=onBrand,`
`primaryContainer=brandTint, onPrimaryContainer=brand, secondary=brand2, onSecondary=onBrand,`
`secondaryContainer=brandTint, onSecondaryContainer=brand, tertiary=promise, onTertiary=onBrand,`
`tertiaryContainer=promiseTint, onTertiaryContainer=promise, background=background, onBackground=onSurface,`
`surface=surface, onSurface=onSurface, surfaceVariant=surface2, onSurfaceVariant=onSurfaceMuted,`
`surfaceContainer=surface, surfaceContainerLow=surface2, surfaceContainerLowest=background,`
`surfaceContainerHigh=surface2, surfaceContainerHighest=surface2, error=statusOverdue, onError=onBrand,`
`errorContainer=statusOverdueTint, onErrorContainer=statusOverdue, outline=outline, outlineVariant=outline`.
**Sin dynamic color, sin Material purple.**

**Test primero (TDD):** `MspColorsTest` (JVM puro, sin Compose runtime — usar `androidx.compose.ui.graphics.Color`
que es value class, no requiere Android):
1. `mspLightColors().brand == Color(0xFF2563EB)` y `mspDarkColors().brand == Color(0xFF3B82F6)` (marca azul).
2. `heroProgressFill == Color(0xFF6FE3C2)` en ambos temas (mint-teal, NO el verde `0xFF7FE0A6` de kollect).
3. `statusPaid` light `== Color(0xFF177245)` (verde semántico conservado, distinto de la marca).
4. `lerpMspColors(light, dark, 0f) == light`, `lerpMspColors(light, dark, 1f) == dark`, y un token intermedio a
   `0.5f` cae entre ambos (aserción de que interpola de verdad, no snapea).
5. Muestreo anti-regresión de 3–4 neutros/status contra la tabla (p.ej. `outline` light `0xFFE4E8E6`,
   `danger` light `0xFF9F1239`, `onDanger` dark `0xFF210A07`) para congelar "1:1 kollect".
(`toColorScheme` puede requerir runtime Compose/Robolectric; si se testea, hacerlo en un test Robolectric
mínimo que afirme `primary == brand`. Si resulta pesado, basta con los tests de tokens + lerp; documentar.)

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:detekt
./gradlew ktlintCheck
```

**Gotchas:**
- **Solo 6 hex de marca + heroProgressFill cambian** respecto a kollect; cualquier otro valor distinto de la
  tabla es un bug (revisor adversarial: cruzar cada línea contra `kollect-app-designsystem.md` §1.2/§1.3).
- `Color(0xFF……)` con alfa `FF` explícito siempre (no `0x……` sin alfa → negro transparente).
- No introducir `MaterialTheme.colorScheme` como fuente de verdad: los componentes leen `MspTheme.colors.*`
  (Task 5); `toColorScheme` es solo para que M3 stock no se vea roto.

**Commit:** `feat(core-designsystem): tokens de color Msp (Azul A + mint-teal, resto 1:1 kollect)`

---

## Task 3 — Tipografía: Manrope + `MspTypography` (escala 1:1 kollect) + M3

**Meta:** la `FontFamily` Manrope (variable, 5 pesos por `FontVariation`), la escala tipográfica completa
`MspTypography` (1:1 de `CampoType.kt`, con cifras `tnum,lnum` tabulares y `lnum` proporcionales donde
corresponde), y el mapeo `toMaterialTypography()`. Line-height fijo **1.4×** el tamaño en cada estilo.

**Archivo:** `core/designsystem/src/main/kotlin/com/example/msp_app/core/designsystem/theme/MspType.kt`.

**Font family (exacto):**
```kotlin
@OptIn(ExperimentalTextApi::class)
private fun manropeFont(weight: FontWeight): Font =
    Font(R.font.manrope_variable, weight = weight,
         variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
val Manrope: FontFamily = FontFamily(
    manropeFont(FontWeight.Normal), manropeFont(FontWeight.Medium), manropeFont(FontWeight.SemiBold),
    manropeFont(FontWeight.Bold), manropeFont(FontWeight.ExtraBold),
)
```

**Constantes de cifras (feature settings):** `TABULAR_FIGURES = "tnum, lnum"` (dinero en columna alineada),
`PROPORTIONAL_FIGURES = "lnum"` (dinero display/hero: la coma de miles kerne pegada). Un helper
`campoStyle(size, weight, trackingEm = 0.0, figures = NONE)` que arma `TextStyle(fontFamily = Manrope,`
`fontSize = size.sp, fontWeight = weight, letterSpacing = trackingEm.em, lineHeight = (size*1.4).sp,`
`fontFeatureSettings = …)`.

**Escala `MspTypography` — TODAS las entradas (size sp / weight, tracking em, figuras), 1:1 kollect §2.3:**

*Dinero / numerales (ExtraBold):* `heroAmount` 46/ExtraBold −0.02 prop · `amountDisplay` 44/ExtraBold −0.03
prop · `amountHero` 36/ExtraBold −0.03 prop · `amountLarge` 34/ExtraBold −0.03 prop · `amountCard` 21/ExtraBold
−0.02 tab · `amountMedium` 20/ExtraBold −0.02 prop · `amountSale` 19/ExtraBold −0.02 tab · `amountRow`
18/ExtraBold −0.02 prop · `amountInline` 14/ExtraBold prop · `amountSplit` 12/ExtraBold tab · `metricLarge`
26/ExtraBold −0.02 tab · `metricSmall` 22/ExtraBold −0.02 tab · `kvValue` 16/ExtraBold tab · `heroStatValue`
15/ExtraBold tab · `keypadKey` 22/Bold tab · `keypadKeyAlt` 18/Bold tab · `ringValue` 16/ExtraBold tab.

*Títulos:* `greeting` 22/ExtraBold −0.02 · `detailTitle` 18/ExtraBold −0.02 · `screenTitle` 16.5/Bold −0.01 ·
`cardTitle` 17/ExtraBold −0.01 · `listTitle` 15/Bold −0.01 · `saleTitle` 14/ExtraBold −0.01 · `name`
15.5/ExtraBold −0.01.

*Botones / inputs:* `buttonLarge` 16/ExtraBold −0.01 · `buttonSmall` 14/ExtraBold · `input` 15/Normal.

*Body / captions:* `body` 13/Normal · `bodyStrong` 13/SemiBold · `methodLabel` 13/Bold · `subtitle`
12.5/Normal · `contextNote` 12.5/SemiBold · `segmentLabel` 12.5/Bold · `chipLabel` 12/Bold +0.01 ·
`sectionHeader` 12/Bold +0.04 · `sectionLabel` 11/ExtraBold +0.08 · `overline` 12/SemiBold +0.05 · `eyebrow`
11/Bold +0.09 (el caller aplica uppercase) · `syncLabel` 12/Bold · `trendLabel` 12/Bold tab · `tileLabel`
11.5/SemiBold +0.02 · `nextStopLabel` 11.5/Bold +0.05 · `saleMeta` 11.5/Normal tab · `caption` 11/Normal tab ·
`captionStrong` 11/Bold tab · `kvLabel` 11/SemiBold · `navLabel` 10.5/Bold · `ringCaption` 9/Normal.

**`internal fun MspTypography.toMaterialTypography(): Typography`** (1:1 kollect §2.4):
`displayLarge=amountDisplay, displayMedium=amountHero, displaySmall=amountLarge, headlineLarge=greeting,`
`headlineMedium=detailTitle, headlineSmall=cardTitle, titleLarge=listTitle, titleMedium=saleTitle,`
`titleSmall=sectionHeader, bodyLarge=input, bodyMedium=body, bodySmall=subtitle, labelLarge=buttonLarge,`
`labelMedium=chipLabel, labelSmall=caption`.

**Test primero (TDD):** `MspTypographyTest` (Robolectric, necesita `R.font` + contexto):
1. `amountHero.fontSize == 36.sp`, `fontWeight == FontWeight.ExtraBold`, `letterSpacing == (-0.03).em`,
   `lineHeight == (36*1.4).sp`, y `fontFeatureSettings` contiene `"lnum"` sin `"tnum"` (proporcional).
2. `amountCard.fontFeatureSettings` contiene `"tnum"` **y** `"lnum"` (tabular — dinero en columna).
3. `Manrope` resuelve sin excepción (la fuente carga desde `R.font.manrope_variable`).
4. Muestreo de 3 estilos no-dinero (`overline` 12/SemiBold +0.05em, `caption` 11/Normal tab, `buttonLarge`
   16/ExtraBold) contra la tabla.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:detekt
./gradlew ktlintCheck
```

**Gotchas:**
- La escala es **verbatim kollect** — el spec §2.2 dice "hero 36–37sp/800": `amountHero` = **36sp** cubre ese
  rango (el 37px del mockup es afinado del piloto, no del DS). No inventar tamaños.
- `letterSpacing` negativo se expresa en `em` (`(-0.02).em`), no en sp.
- `fontFeatureSettings` es un `String` CSS-like; respetar exactamente `"tnum, lnum"` vs `"lnum"`.
- Manrope es UNA TTF variable: NO agregar 5 archivos por peso — todos salen de `manrope_variable.ttf` vía
  `FontVariation`.

**Commit:** `feat(core-designsystem): tipografia Manrope y escala MspTypography (1:1 kollect)`

---

## Task 4 — Formas, espaciado y motion: `MspShapes` + `MspSpacing` + `MspMotion` + reduce-motion

**Meta:** los tres archivos de token restantes, 1:1 de kollect, con los radios que el master fija
(card 20 / hero 22 / tile 16 / control 12 / chip 999), los targets táctiles (56dp), y las dos springs de
motion + el helper `rememberReducedMotionEnabled()` que apaga animaciones según el ajuste del SO.

**Archivos:**
- `theme/MspShapes.kt` — `class MspShapes internal constructor()` con `RoundedCornerShape`:
  `card` 20dp · `tile` 16dp · `chip` = `RoundedCornerShape(percent = 50)` (pill 999) · `button` 16dp ·
  `field` 14dp · `control` 12dp · `heroCard` 22dp · `sectionCard` 18dp · `payIcon` 11dp · `chip9` 9dp.
- `theme/MspSpacing.kt` — `class MspSpacing internal constructor()`: `xs = 4.dp`, `sm = 8.dp`, `md = 16.dp`,
  `lg = 24.dp`, `touchTarget = 56.dp` (mínimo para targets primarios: uso a una mano, caminando, al sol).
- `theme/MspMotion.kt` — `class MspMotion internal constructor()` con exactamente dos springs:
  ```kotlin
  fun <T> standard(): SpringSpec<T> =
      spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
  // RESERVADO al único beat celebratorio ("pago confirmado") — no usar en otro lugar:
  fun <T> emphasized(): SpringSpec<T> =
      spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
  ```
  Más `@Composable fun rememberReducedMotionEnabled(): Boolean` que lee
  `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` ("Eliminar/Reducir animaciones" de Android) vía
  `LocalContext.current.contentResolver` y lo `remember`ea. Es el interruptor que TODO componente animado
  consulta (spec §5: todas las animaciones desactivables → crossfade/instantáneo).

**Test primero (TDD):**
1. `MspShapesTest` — `card == RoundedCornerShape(20.dp)`, `heroCard == RoundedCornerShape(22.dp)`,
   `tile == RoundedCornerShape(16.dp)`, `control == RoundedCornerShape(12.dp)`, `chip == RoundedCornerShape(50)`.
2. `MspSpacingTest` — `touchTarget == 56.dp`, `md == 16.dp` (defensa del acuerdo de accesibilidad 48–56dp).
3. `MspMotionTest` — `standard<Float>()` es `DampingRatioNoBouncy` y `emphasized<Float>()` es
   `DampingRatioMediumBouncy` (aserción sobre los campos del `SpringSpec`).
4. `ReducedMotionTest` (Robolectric) — con `Settings.Global.ANIMATOR_DURATION_SCALE = 0f`,
   `rememberReducedMotionEnabled()` = `true`; con `1f` = `false`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:detekt
./gradlew ktlintCheck
```

**Gotchas:**
- `chip` es pill completa → `RoundedCornerShape(percent = 50)`, NO un dp fijo.
- `MspMotion.emphasized()` es SOLO para "pago confirmado" (Plan 5); documentar con KDoc, no usarlo en el DS.
- Constructores `internal` — las clases de token se instancian solo dentro de `MspTheme` (Task 5).

**Commit:** `feat(core-designsystem): formas, espaciado y motion Msp + helper de reduce-motion`

---

## Task 5 — `MspTheme` + CompositionLocals + M3 wiring + base Roborazzi (primer golden)

**Meta:** el composable `MspTheme` que provee los 5 grupos de token vía `CompositionLocal` y envuelve M3
(`colorScheme = colors.toColorScheme(...)`, `typography = type.toMaterialTypography()`), con el **crossfade de
paleta** integrado (mecanismo A, fallback) y el escape hatch `animateColors` que la reveal (Task 9) usará. Y
**el bring-up de Roborazzi**: la base de screenshot test + host de preview + el primer golden (swatch de
tokens/tema) que prueba el pipeline light/dark/escala end-to-end.

**Archivos (tema):** `theme/MspTheme.kt`:
```kotlin
@Composable
fun MspTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animateColors: Boolean = true,
    content: @Composable () -> Unit,
) {
    val light = remember { mspLightColors() }
    val dark = remember { mspDarkColors() }
    val progress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 300), label = "themeCrossfade",
    )
    val darkResolved = if (animateColors) progress >= 0.5f else darkTheme
    val colors =
        if (animateColors) remember(progress, light, dark) { lerpMspColors(light, dark, progress) }
        else remember(darkTheme, light, dark) { if (darkTheme) dark else light }
    val type = remember { MspTypography() }
    val spacing = remember { MspSpacing() }
    val shapes = remember { MspShapes() }
    val motion = remember { MspMotion() }
    CompositionLocalProvider(
        LocalMspColors provides colors, LocalMspTypography provides type,
        LocalMspSpacing provides spacing, LocalMspShapes provides shapes, LocalMspMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkResolved),
            typography = type.toMaterialTypography(), content = content,
        )
    }
}
object MspTheme {
    val colors: MspColors @Composable @ReadOnlyComposable get() = LocalMspColors.current
    val type: MspTypography @Composable @ReadOnlyComposable get() = LocalMspTypography.current
    val spacing: MspSpacing @Composable @ReadOnlyComposable get() = LocalMspSpacing.current
    val shapes: MspShapes @Composable @ReadOnlyComposable get() = LocalMspShapes.current
    val motion: MspMotion @Composable @ReadOnlyComposable get() = LocalMspMotion.current
}
```
Definir los `staticCompositionLocalOf` (`LocalMspColors`, etc.) con default que arroje error claro si se lee
fuera de `MspTheme` (o defaults a `mspLightColors()` — elegir el patrón de kollect: `error("no MspTheme")`).
Notas 1:1 kollect §6: **sin dynamic color, sin Material purple**; `animateFloatAsState` settlea instantáneo en
la primera composición → un screenshot/`@Preview` estático siempre rinde la paleta destino `0f`/`1f`, nunca un
frame intermedio.

**Archivos (base Roborazzi):** en `core/designsystem/src/test/kotlin/.../screenshot/`:
- `MspScreenshotTest.kt` — base abierta que extiende `RobolectricTestBase` (de `:core:testing`), expone
  `@get:Rule val roborazzi = RoborazziRule(...)` o el patrón `captureRoboImage(...)`, usa
  `createComposeRule()` (ui-test-junit4), y un helper `fun capture(name: String, dark: Boolean, fontScale: Float, content: @Composable () -> Unit)`
  que: (a) fija `fontScale` (vía `CompositionLocalProvider(LocalDensity provides Density(density, fontScale))`
  o Robolectric `RuntimeEnvironment.setFontScale`; el implementador usa el que funcione con esta toolchain),
  (b) envuelve el `content` en `MspTheme(darkTheme = dark, animateColors = false)` sobre un `Surface`/`Box`
  con `background`, (c) captura a `src/test/screenshots/<name>.png` con tolerancia
  `RoborazziConfig.CHANGE_THRESHOLD` (0.01f). `animateColors = false` para render estático determinista.
- `ThemeSwatchScreenshotTest.kt` — el primer golden real: un `MspThemeSwatch` (composable de test, o un
  `internal` en main si aporta) que pinta muestras de `brand`, `heroProgressFill`, `surface`, `onSurface`,
  `statusPaid/Partial/Overdue`, y un `Text` de muestra con `MspTheme.type.amountHero`. Capturar en
  **light + dark a escala 1.0** (2 goldens) → prueba que el pipeline entero (tema + fuente + tokens + Roborazzi
  + Robolectric) rinde y compara.

**Test primero (TDD):**
1. `MspThemeTest` (Robolectric compose-test) — dentro de `MspTheme { }` afirmar que `MspTheme.colors.brand`
   resuelve al azul del tema activo (light → `0xFF2563EB`) y que leer un local fuera de `MspTheme` arroja el
   error esperado.
2. `ThemeSwatchScreenshotTest` — grabar (`recordRoborazziDebug`) los 2 goldens light/dark, luego
   `verifyRoborazziDebug` verde. Rojo→verde = el pipeline funciona.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:recordRoborazziDebug     # graba los primeros goldens
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:detekt
./gradlew ktlintCheck
```
Commitear los PNG generados bajo `core/designsystem/src/test/screenshots/` (son el contrato visual).

**Gotchas:**
- **`animateColors = false` en todos los screenshot tests** — si no, el crossfade puede capturar un frame
  intermedio y el golden se vuelve no determinista.
- Confirmar que Roborazzi escribe/lee en la ruta esperada; fijar `roborazzi.output.dir` o usar el default y
  commitear esa ruta. Verificar que `.gitignore` NO excluya `*.png` bajo `src/test/screenshots`.
- El default de los `CompositionLocal` de token: preferir `error("MspTheme ausente")` para que un componente
  usado sin tema falle ruidoso en test (no pinte con basura).
- El bring-up de fontScale se afina aquí para que Tasks 6–10 lo reutilicen; dejar el helper `capture(...)`
  parametrizado por `fontScale` desde ya.

**Commit:** `feat(core-designsystem): MspTheme + CompositionLocals y base de screenshot Roborazzi`

---

## Task 6 — Sustrato de superficie + gradiente + progreso: `MspSurface`/`MspCard`, `BrandGradient`, `ProgressBar`/`ProgressRing`

**Meta:** las primitivas base sobre las que se montan todas las tarjetas: el sustrato `MspSurface`/`MspCard`
(fill `surface` + hairline 1dp `outline` + shape token), el gradiente de marca 150° (`brandGradientBackground`
+ roles de alfa `OnBrandAlpha`), y las primitivas de progreso (`MspProgressBar` recto + `MspProgressRing`).

**Archivos (en `component/`):**
- `MspSurface.kt` — `internal @Composable fun MspSurface(modifier, shape = MspTheme.shapes.tile,`
  `color = MspTheme.colors.surface, shadowElevation: Dp = 0.dp, onClick: (() -> Unit)? = null, content)`:
  envuelve M3 `Surface`, agrega `BorderStroke(1.dp, MspTheme.colors.outline)` automáticamente, y elige el
  overload clickable/no-clickable según `onClick != null` (1:1 kollect §8.1).
- `MspCard.kt` — wrapper público, default shape `tile` (16dp). (`MspBentoTile`/`MspCarteraCard` en Task 8
  agregan `shadowElevation = 1.dp` encima del hairline.)
- `BrandGradient.kt` — `HERO_GRADIENT_ANGLE_DEG = 150.0` y
  `fun Modifier.brandGradientBackground(colors: MspColors, shape: Shape): Modifier` que calcula el gradiente
  lineal por ángulo a mano (1:1 kollect §8.2: `clip(shape).drawBehind { … Brush.linearGradient(listOf(brand,`
  `brand2), center - half, center + half) }`) — **gradiente plano, sin glow radial** (regla del spec §6). Más
  `object OnBrandAlpha { const val OVERLINE = 0.72f; const val BODY = 0.82f; const val LABEL = 0.75f;`
  `const val WELL = 0.12f }` (alfas translúcidos consistentes sobre el gradiente).
- `MspProgressBar.kt` — dos `Box` anidados (track full-width + `fillMaxWidth(fraction = progress)` fill),
  ambos `clip(MspTheme.shapes.chip)` (pill). Params: `progress: Float`, `height: Dp` (usos: 8–9dp hero con
  `heroProgressFill`; 6dp filas de plan con `brand` sobre `progressTrack`), `fillColor`, `trackColor`. Sin
  animación interna del fraction (el caller la envuelve si quiere).
- `MspProgressRing.kt` — anillo 74dp, stroke 7dp `StrokeCap.Round`, dos `drawArc` (track completo +
  `360 * fraction` desde `-90°`), porcentaje centrado con `MspTheme.type.ringValue`.

**Test primero (TDD):**
- Unit: `BrandGradientMathTest` — para un tamaño dado, `brandGradientBackground` produce start/end offsets
  coherentes con 150° (verificar el cálculo de `direction`/`length` puro, extraído a función testeable).
- Screenshot (baseline light+dark @1.0 vía `MspScreenshotTest`): `MspCard` con contenido de muestra;
  un `Box` con `brandGradientBackground`; `MspProgressBar` al 91% (hero, `heroProgressFill`, 9dp) y al 40%
  (fila, `brand`/`progressTrack`, 6dp); `MspProgressRing` al 91%. Grabar y `verifyRoborazziDebug`.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:recordRoborazziDebug
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:detekt ktlintCheck
```

**Gotchas:**
- Gradiente **150° plano** calculado a mano (no `Brush.horizontalGradient`, no glow) — preservar el ángulo CSS
  exacto del mockup (`.hero{background:linear-gradient(150deg,brand,brand2)}`).
- `MspProgressBar` clipa AMBOS box a `shapes.chip` (si solo el fill, el track sobresale en las esquinas).
- El hairline 1dp de `MspSurface` va SIEMPRE (define las tarjetas casi-planas del sistema, sombra hairline +
  1dp del spec §2.3).

**Commit:** `feat(core-designsystem): sustrato MspSurface/MspCard, BrandGradient y progreso`

---

## Task 7 — Dinero, estado y avatar: `MspMoneyText` (+`formatMoneyMxn`/`MASKED_MONEY`), `MspStatusChip`, `MspInitialsAvatar`

**Meta:** las primitivas de contenido que codifican dinero y estado (accesibilidad crítica): el formateo de
dinero es-MX con máscara de privacidad, el chip de estado **color + ícono + texto** (nunca solo color), y el
avatar de iniciales de las filas de pago.

**Archivos (en `component/` salvo el formatter):**
- `MoneyText.kt` (paquete `…designsystem.component`):
  - `const val MASKED_MONEY = "$••••"` (glifo universal de privacidad; nombre exacto por contrato del spec).
  - `fun formatMoneyMxn(amount: java.math.BigDecimal): String` — locale `es-MX`, `DecimalFormat("$#,##0.00")`
    (nombre exacto por contrato). **Recibe `BigDecimal`, NUNCA `Double`** (regla anti-`Double`).
  - `@Composable fun MspMoneyText(amount: BigDecimal, masked: Boolean = false, style: TextStyle =`
    `MspTheme.type.amountRow, color: Color = MspTheme.colors.onSurface, modifier: Modifier = Modifier)` que
    pinta `if (masked) MASKED_MONEY else formatMoneyMxn(amount)`. **Debe reflowear, no truncar**: usar
    `softWrap = true`, sin `maxLines = 1` con `TextOverflow.Ellipsis` sobre el número (la aserción de no-truncar
    a escala grande vive en Task 10). El display/hero usa estilos con figuras **proporcionales**
    (`amountHero`/`amountDisplay`); los montos en columna usan estilos **tabulares** (`amountCard`, etc.).
- `ChipStatus.kt` — `enum class ChipStatus { Paid, Partial, Overdue, Pending, Promise }` con, por cada uno, su
  color de contenido, su tint de fondo (desde `MspTheme.colors`) y su **ícono por defecto** (`Paid`→check,
  `Partial`→media-luna/half-circle, `Overdue`→warning, `Pending`→círculo, `Promise`→reloj — de
  `MspIcons`/material-icons-core).
- `StatusChip.kt` — `@Composable fun MspStatusChip(status: ChipStatus, text: String, modifier)`: pill
  (`shapes.chip`) con fondo tint + color de contenido + ícono + texto. **Regla dura (KDoc):** el estado es
  **color + ícono + texto** juntos, nunca solo color (spec §2.1, accesibilidad §5).
- `InitialsAvatar.kt` — `@Composable fun MspInitialsAvatar(initials: String, modifier, size: Dp = 38.dp)`:
  cuadro redondeado `shapes.control` (12dp) fondo `brandTint`, texto `brand` centrado, peso ExtraBold ~13sp
  (mockup `.ava`: 38×38, radius 12, `background:var(--tint)`, `color:var(--brand)`, `font-weight:800`).
  Recibe iniciales ya calculadas (YAGNI: el cálculo nombre→iniciales es del caller/piloto).

**Test primero (TDD):**
- Unit `FormatMoneyMxnTest` (JVM puro, exhaustivo — este es el logic-path del módulo):
  `formatMoneyMxn(BigDecimal("1200")) == "$1,200.00"`; `("18300.5") == "$18,300.50"`; `("0") == "$0.00"`;
  negativo `("-850") == "-$850.00"` (o el formato es-MX real — caracterizar contra `DecimalFormat` es-MX y
  documentar); miles/millones con coma correcta; `MASKED_MONEY == "$••••"`. Nombres realistas donde aplique.
- Screenshot (baseline light+dark @1.0): `MspMoneyText` normal y `masked`; los 5 `MspStatusChip`
  (Paid/Partial/Overdue/Pending/Promise con texto español corto: "Pagado", "Parcial", "Vencido", "Pendiente",
  "Promesa"); `MspInitialsAvatar("ML")`. Grabar + verify.
- Compose-test: `MspStatusChip(Paid, "Pagado")` expone el ícono (semántica) además del texto — aserción de que
  el nodo tiene ícono + texto (prueba "nunca solo color").

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:recordRoborazziDebug
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:detekt ktlintCheck
```

**Gotchas:**
- **`formatMoneyMxn` toma `BigDecimal`** — si un llamador pasa `Double`, detekt `NoDoubleForMoney` debe
  dispararse; NO agregar un overload `Double` "por conveniencia".
- Caracterizar el formato es-MX real (símbolo `$`, coma de miles, signo negativo) contra `DecimalFormat` con
  `Locale("es", "MX")`; documentar el string exacto que produce (evita un golden de texto equivocado).
- `MspStatusChip` SIEMPRE ícono + texto (accesibilidad): sin variante "solo dot" para estado semántico (el dot
  suelto de "método efectivo/transfer" es otra cosa — un `Box` de color en la fila, no un StatusChip).

> **Parked for user (mapeo de estilos de dinero):** el spec/máster no lista qué `MspTypography.amount*` usa
> cada slot del piloto; se dejan defaults sensatos (`MspMoneyText` default `amountRow`; hero usa `amountHero`;
> tiles usan `amountCard`). El piloto (Plan 5) fija el estilo por slot contra el mockup. No bloquea el DS.

**Commit:** `feat(core-designsystem): MspMoneyText/formatMoneyMxn, MspStatusChip y MspInitialsAvatar`

---

## Task 8 — Tarjetas compuestas: `MspHeroTodayCard`, `MspBentoTile`, `MspWeeklyBarsCard`, `MspCarteraCard`

**Meta:** las 4 tarjetas compuestas del tablero, montadas sobre las primitivas de Tasks 6–7. Son la "cara" del
piloto; deben verse como el mockup y refluir sin truncar el dinero.

**Archivos (en `component/`):**
- `HeroTodayCard.kt` — `@Composable fun MspHeroTodayCard(...)`: `MspCard`/`Box` con
  `brandGradientBackground(MspTheme.colors, MspTheme.shapes.heroCard)` (radio **22dp**), padding **18dp**
  (kollect §8.2; el mockup usa 17px — 18dp es el valor DS), color de contenido `onBrand`. Estructura (mockup
  `.hero`): overline uppercase (`type.overline`, alfa `OnBrandAlpha.OVERLINE=0.72`) + **delta chip** a la
  derecha (pill `rgba(255,255,255,.16)`, `type.captionStrong`); **monto grande** `MspMoneyText` con
  `type.amountHero` (36sp/800 proporcional); frase-insight (`type.body`, alfa `OnBrandAlpha.BODY`); barra de
  progreso `MspProgressBar` 8–9dp con `heroProgressFill`; caption de meta (`type.caption`, alfa 0.75); slot de
  **sparkline embebida** (recibida como composable/param — la sparkline concreta es del piloto, el DS deja el
  contenedor y el estilo de barra `rgba(255,255,255,.22)` idle / `heroProgressFill` activo); fila de **wells**
  (Efectivo en mano / Ticket prom.), cada well fondo `OnBrandAlpha.WELL=0.12`, radio ~13dp, label
  `type.caption`, valor `type.heroStatValue`. Params con `BigDecimal` para todo monto. `onClick` opcional
  (abre sheet en el piloto).
- `BentoTile.kt` — `@Composable fun MspBentoTile(...)`: `MspSurface` shape `tile` (16dp) +
  `shadowElevation = 1.dp`, con header (dot de color + label `type.tileLabel`/`bodyStrong` muted), valor
  `MspMoneyText` `type.amountCard` (tabular), y sub-línea (`type.caption`, "N pagos"). Es el tile del duo
  Efectivo/Transferencia. `onClick` opcional, `Modifier` para `pressScale` externo.
- `WeeklyBarsCard.kt` — `@Composable fun MspWeeklyBarsCard(bars: List<...>, todayIndex: Int, ...)`: tarjeta con
  barras por día del ciclo (alto proporcional al valor, la de hoy resaltada con `brand`/`heroProgressFill`, las
  demás `chartTrack`), etiqueta por barra (`type.trendLabel` tabular). Sin animación gated en tiempo/random (el
  caller anima el "grow"). (Mockup: la tendencia va embebida en el hero como spark; `MspWeeklyBarsCard` es el
  componente autónomo reutilizable — spec §3 lo lista.)
- `CarteraCard.kt` — `@Composable fun MspCarteraCard(...)`: la tarjeta "cobrado vs pendiente" (el movimiento de
  transparencia). **Fase 2** (requiere backend de saldos por zona) — el DS la implementa como componente puro
  con datos por parámetro (`BigDecimal` cobrado/pendiente + split visual con `MspProgressBar`), pero el piloto
  Plan 5 NO la cablea (Cartera/zona = Fase 2). Se entrega para completar el catálogo del DS.

**Test primero (TDD):**
- Screenshot baseline (light+dark @1.0): `MspHeroTodayCard` con datos del mockup (`"$18,300"`, delta "▲ 12% vs
  ayer", insight "32 pagos · vas al 91% de tu meta", barra 91%, meta "$20,000", wells "$12,100"/"$572");
  `MspBentoTile` Efectivo `$12,100`/"22 pagos" y Transferencia `$6,200`/"10 pagos"; `MspWeeklyBarsCard` con el
  ciclo de 5 días del mockup (lun–vie, hoy=vie); `MspCarteraCard` con datos de ejemplo. Grabar + verify.
- Compose-test: el hero renderiza el monto formateado (`MspMoneyText` con `amountHero`) y la barra a la
  fracción dada; el `MspBentoTile` muestra dot + label + valor.

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:recordRoborazziDebug
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:detekt ktlintCheck
```

**Gotchas:**
- **Regla anti-colapso (spec §6, `flex-shrink:0`):** en Compose, las tarjetas dentro de una `Column` con scroll
  NO deben recibir un `weight` que las comprima bajo su contenido; el hero desaparecía en el mockup por eso. El
  DS usa `wrapContentHeight`/altura por contenido; documentarlo en KDoc para el piloto (el `verticalScroll`
  maneja el overflow, no la compresión).
- **Hero = gradiente plano** (Task 6), sin glow.
- Todo monto entra como `BigDecimal` y sale por `MspMoneyText` (anti-`Double`); nada de `String` de dinero
  precomputado dentro del DS salvo labels.
- `MspCarteraCard` es Fase 2 por DATOS, pero el componente existe y se testea ahora (catálogo completo).

**Commit:** `feat(core-designsystem): tarjetas compuestas Hero/BentoTile/WeeklyBars/Cartera`

---

## Task 9 — Interactivos: `MspPrivacyEyeToggle`, `MspThemeToggle`+`ThemeRevealController`, `MspSegmentChips`, `MspSyncBand`/`MspPaymentSyncPill`, `MspPrimaryFieldButton`

**Meta:** los componentes con interacción/estado del DS: ocultar cifras, toggle de tema con el bridge de reveal
circular, selector segmentado (Día·Semana), banda/pill de sync, y el CTA primario. Todas las animaciones
**desactivables** por `rememberReducedMotionEnabled()`.

**Archivos (en `component/`):**
- `ThemeState.kt` — `@Stable class ThemeState(darkTheme: Boolean) { var darkTheme by mutableStateOf(...); fun toggle() }`.
- `ThemeRevealController.kt` — 1:1 kollect §7.2 (bridge origen→dueño del flip):
  ```kotlin
  @Stable class ThemeRevealController {
      var origin: Offset? by mutableStateOf(null); private set
      fun requestRevealFrom(origin: Offset) { this.origin = origin }
      fun consume() { origin = null }
  }
  fun maxDistanceToCorner(origin: Offset, width: Float, height: Float): Float {
      val dx = maxOf(origin.x, width - origin.x); val dy = maxOf(origin.y, height - origin.y)
      return kotlin.math.hypot(dx, dy)
  }
  val LocalThemeReveal = staticCompositionLocalOf<ThemeRevealController?> { null }
  ```
  (El `ThemeRevealRoot` que graba el frame viejo + `Animatable` de radio + `clipPath(Difference)` es del
  **composition root de la app / piloto Plan 5**, no del DS — el DS provee el bridge + `MspThemeToggle`.
  Timing de referencia: `tween(380, FastOutSlowInEasing)` para la reveal; `tween(300)` para el crossfade
  fallback de `MspTheme`.)
- `ThemeToggle.kt` — `@Composable fun MspThemeToggle(darkTheme: Boolean, onToggle: () -> Unit, modifier)`:
  `MspSurface` `minimumInteractiveComponentSize().size(40.dp)`, shape `control`, reporta su
  `boundsInRoot().center`; si `LocalThemeReveal.current != null` y el centro es válido → `requestRevealFrom`,
  si no → `onToggle()` (fallback crossfade). Ícono `MspIcons.Moon`/`Sun` según `darkTheme`.
- `PrivacyEyeToggle.kt` — `@Composable fun MspPrivacyEyeToggle(masked: Boolean, onToggle: () -> Unit, modifier)`:
  mismo patrón 40dp icon-surface (shape `control`), ícono ojo/ojo-tachado, color `brand` cuando `masked`
  / `onSurfaceMuted` cuando no. Sin animación especial — swap de glifo (kollect §8.10). El estado `masked` lo
  sostiene el caller; el DS solo enmascara vía `MspMoneyText(masked = ...)`.
- `SegmentChips.kt` — `@Composable fun MspSegmentChips(options: List<String>, selectedIndex: Int, onSelect)`:
  contenedor pill `track`/`chip`, cada opción `type.segmentLabel`; la activa = fondo `surface` + texto `brand`
  + sombra sutil (mockup `.period button.on`). Usado para Día·Semana y para Hora·Nombre.
- `SyncBand.kt` + `SyncBandState.kt` — `enum SyncBandState { Pending, Ok }` (amber/green) y
  `@Composable fun MspSyncBand(state, message, hint, modifier)`: strip full-width no bloqueante (dot 8dp +
  mensaje + hint de tranquilidad), tint por estado (1:1 kollect §8.5).
- `PaymentSyncPill.kt` — `@Composable fun MspPaymentSyncPill(pendingCount: Int, modifier)`: pill discreta "N
  por subir" (mockup `.syncpill`: color `statusPartial`/amber, dot con pulse). La animación de pulse respeta
  reduce-motion (sin pulse si reducido).
- `PrimaryFieldButton.kt` — `@Composable fun MspPrimaryFieldButton(text, onClick, variant = Primary, ...)`:
  CTA ≥56dp, tres variantes (kollect §8.4): `Primary` (fill `brand` + drop shadow 8dp tintada al brand
  `alpha 0.55`), `Ghost` (outlined, texto `brand`, sin sombra), `Danger` (fill `statusOverdue`, misma sombra —
  reservado a confirmaciones riesgosas, nunca default). Estado disabled = fill plano `outline`, sin sombra.
  Haptic `LongPress` en cada tap ("las acciones de dinero deben sentirse físicas"). Texto `type.buttonLarge`.

**Test primero (TDD):**
- Unit `ThemeRevealControllerTest` — `requestRevealFrom(o)` setea `origin`, `consume()` lo limpia;
  `maxDistanceToCorner` con origen en centro y esquinas da la hipotenusa correcta (casos borde: origen en
  esquina, origen fuera → clamp/valor esperado).
- Compose-test: `MspThemeToggle` con `LocalThemeReveal` provisto NO llama `onToggle` (llama al controller);
  sin host, SÍ llama `onToggle`. `MspSegmentChips` cambia `selectedIndex` al tap. `MspPrivacyEyeToggle` invoca
  `onToggle`.
- Screenshot baseline (light+dark @1.0): toggles ojo (masked/normal) y tema (moon/sun), `MspSegmentChips`
  Día·Semana, `MspSyncBand` Pending/Ok, `MspPaymentSyncPill` "3 por subir", `MspPrimaryFieldButton`
  Primary/Ghost/Danger + disabled. Grabar + verify. (Render estático → sin pulse/sombra animada; determinista.)
- Reduce-motion: un test que con `ANIMATOR_DURATION_SCALE = 0` la pill no anima (o el composable no arranca el
  `InfiniteTransition`).

**Verificación:**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:recordRoborazziDebug
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:detekt ktlintCheck
```

**Gotchas:**
- El DS provee el **bridge** de reveal (`ThemeRevealController` + `LocalThemeReveal` + `MspThemeToggle` +
  `maxDistanceToCorner`); el `ThemeRevealRoot` (grabar frame + `clipPath` + `Animatable`) es del piloto/app
  root (Plan 5) — no meterlo en el DS (necesita el `content` de toda la app). Documentar la frontera.
- **Todas** las animaciones consultan `rememberReducedMotionEnabled()`: pulse de la sync pill, cualquier
  press-scale, la reveal (fallback a snap). Sin excepción (spec §5).
- `MspPrimaryFieldButton` disabled: `Surface` clickable NO aplica alfa disabled solo → pintar el fill plano
  `outline` a mano (kollect §8.4).
- Iconos: usar `material-icons-core` (ya en deps) o un `MspIcons` con `ImageVector` propios; mantener el set
  mínimo (Moon, Sun, Eye, EyeOff, Check, Warning, Clock, Circle, HalfCircle).

**Commit:** `feat(core-designsystem): interactivos (toggles, segment chips, sync, CTA) + bridge de reveal`

---

## Task 10 — Gate visual: catálogo Tier 1/2 × escala {1.0,1.3,2.0} × {light,dark} + contraste AAA + no-truncación + prePushCheck

**Meta:** cerrar el plan con el **gate visual completo** (spec §5, master "Screenshot por tier × escala"): para
cada componente firma, goldens en **Tier 1 y Tier 2** × escala de fuente **{1.0, 1.3, 2.0}** × **{light,
dark}**; validación de **contraste AAA** en hero/estados críticos; y la aserción dura de que **el dinero
reflowea, no se trunca** a escala grande. Cablear `verifyRoborazziDebug` + tareas `:core:designsystem:*` al
`prePushCheck`.

**Concepto Tier 1 / Tier 2 (spec §5, aplicado al catálogo del DS):**
- **Tier 1** (Normal/Grande) = el layout denso responsivo del componente (el que Tasks 6–9 ya rinden).
- **Tier 2** (Muy grande) = layout alterno **curado** (una idea por vista, targets mayores) sobre el mismo
  estado. En el DS, Tier 2 se materializa como variantes de composición de las tarjetas que refluyen a columna
  única / tipografía mayor. **Parked for user (alcance Tier 2 en el DS):** el spec dice que Tier 2 es "solo la
  capa `ui/`" y vive **por pantalla** (Plan 5), no necesariamente como un segundo layout de cada componente del
  DS. Lectura fiel adoptada aquí: el DS **prueba que cada componente resiste la escala 2.0 sin romper/truncar**
  (Tier 1 a las 3 escalas), y aporta las variantes Tier 2 solo donde un componente firma tiene un modo alterno
  natural (hero, tiles). El layout Tier 2 curado **por pantalla** es responsabilidad del piloto (Plan 5). Si el
  usuario quiere un Tier 2 explícito por-componente en el DS, es una extensión — documentada, no bloqueante.

**Archivos (en `component`/`screenshot`):**
- `MspCatalog.kt` (o composables de catálogo en test) — un catálogo que agrupa cada componente firma con datos
  realistas (mockup): hero, duo de tiles, chips de estado, weekly bars, cartera, toggles, segment chips, sync
  band/pill, CTA, money text, avatar, progreso.
- `CatalogScreenshotTest.kt` — parametrizado (`@ParameterizedRobolectricTestRunner` o matriz manual) sobre
  `{light, dark} × {1.0f, 1.3f, 2.0f}` (Tier 1) + los modos Tier 2 donde apliquen; captura un golden por
  combinación usando el `capture(name, dark, fontScale, content)` de la base (Task 5). Nombre de golden
  determinista: `<componente>_<tema>_<escala>[_tier2]`.
- `ContrastAAATest.kt` — helper `wcagContrastRatio(fg: Color, bg: Color): Double` (fórmula WCAG: luminancia
  relativa sRGB linealizada → `(L1+0.05)/(L2+0.05)`), y aserciones sobre pares críticos en **ambos temas**:
  - `onBrand` sobre `brand` y sobre el promedio del gradiente `brand→brand2` (texto hero: es **texto grande**
    ≥18sp/700 → umbral **AAA-large 4.5:1**).
  - `heroProgressFill` sobre el gradiente de marca (la barra es elemento gráfico → **3:1** contraste UI).
  - `onSurface` sobre `surface` y sobre `background` (**7:1**, texto normal AAA).
  - cada `status*` sobre su `status*Tint` (chips: texto normal pequeño → **7:1** deseable; ver Parked).
- `MoneyNoTruncationTest.kt` — renderiza `MspMoneyText` con un monto grande (`"$1,234,567.89"`) a **fontScale
  2.0** en un ancho de teléfono típico y afirma que el nodo de texto **no está truncado** (sin ellipsis; el
  layout crece en alto / hace wrap). "Terminado es imposible si el dinero se corta en grande" (spec §5).

**prePushCheck (wiring final):** en `build.gradle.kts` raíz, agregar al `dependsOn(...)` de `prePushCheck`:
`:core:designsystem:ktlintCheck` (si no se agregó en Task 1), `:core:designsystem:testDebugUnitTest`,
`:core:designsystem:detekt`, `:core:designsystem:koverVerify` (piso placeholder), y
**`:core:designsystem:verifyRoborazziDebug`**. Actualizar el comentario del bloque para listar el módulo.

**Test primero (TDD):** los archivos de test SON el gate (rojo→verde): grabar toda la matriz con
`recordRoborazziDebug`, luego `verifyRoborazziDebug` debe pasar; `ContrastAAATest` y `MoneyNoTruncationTest`
pasan. Escribir primero `MoneyNoTruncationTest` (mayor valor de accesibilidad) y hacerlo pasar.

**Verificación (gate completo del plan):**
```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core:designsystem:recordRoborazziDebug     # graba la matriz completa de goldens
./gradlew ktlintCheck
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :core:designsystem:verifyRoborazziDebug
./gradlew :core:designsystem:detekt
./gradlew :core:designsystem:koverVerify
./gradlew prePushCheck                                # el gate agregado ahora incluye el DS
./gradlew :app:assembleDevlocalDebug                  # :app sigue idéntico
```
Commitear TODOS los PNG de `core/designsystem/src/test/screenshots/` (el contrato visual del catálogo).

**Gotchas:**
- La matriz puede generar muchos PNG (12 combinaciones/componente si Tier2 en todos); acotar Tier 2 a los
  componentes con modo alterno real (hero/tiles) para no explotar el repo (ver Parked arriba). Tier 1 × 3
  escalas × 2 temas es el piso obligatorio para TODO componente.
- `fontScale` en Robolectric: fijar por `RuntimeEnvironment.setFontScale(scale)` o proveyendo
  `LocalDensity` con `fontScale` — usar el que la toolchain 2024.09.00 + Roborazzi 1.26.0 respete en el render
  (el implementador confirma con un golden a 2.0 visiblemente mayor).
- Determinismo: `animateColors = false`, sin animaciones gated en tiempo/random; si un golden parpadea entre
  corridas, la causa es una animación no congelada — congelarla, no subir el threshold.
- **Contraste AAA vs realidad del azul (ver Parked):** no todos los pares llegan a 7:1.

> **Parked for user (umbrales AAA):** el spec pide "contraste AAA en hero/estados críticos". Con Azul A
> (`brand #2563EB`), `onBrand` blanco sobre `brand` da ≈5.8:1 — **AA**, y **AAA-large** (4.5:1) para el texto
> grande del hero (36sp/800), que sí cumple. Un umbral **AAA-normal 7:1** sobre el overline 12sp translúcido
> (alfa 0.72) del hero NO es alcanzable sin cambiar la marca. Lectura fiel adoptada: **AAA-large (4.5:1) para
> el monto hero** y textos ≥18sp/700; **AAA-normal (7:1) para texto normal sobre superficies neutras**
> (`onSurface`/`surface`); overlines/captions translúcidos sobre marca se validan a **AA (4.5:1) large**. El
> test codifica estos umbrales; si el usuario exige 7:1 estricto en TODO el hero, eso obliga a re-oscurecer la
> marca o quitar la translucidez — decisión de diseño para el piloto, no un blocker del DS.

**Commit:** `test(core-designsystem): catalogo Roborazzi tier×escala, contraste AAA y no-truncacion de dinero`

---

## Cierre de Plan 3 (auditoría de conformidad)

- [ ] `:core:designsystem` existe (namespace `com.example.msp_app.core.designsystem`), aplica
      `msp.android.library/compose/detekt/kover` + ktlint + plugin Roborazzi; **Manrope** empaquetada en
      `res/font/manrope_variable.ttf`; incluido en `settings.gradle.kts`.
- [ ] **Tokens Msp:** `MspColors` con Azul A (light `brand #2563EB`/`brand2 #1D4ED8`/`brandTint #EAF0FE`; dark
      `brand #3B82F6`/`brand2 #1D5FB0`/`brandTint #0E2440`), `heroProgressFill = #6FE3C2` (mint-teal, ambos
      temas), dark `background` OLED `#000000`, resto **1:1 kollect**; `lerpMspColors` + `toColorScheme` (sin
      dynamic color, sin Material purple).
- [ ] **Tipografía:** Manrope variable (5 pesos por `FontVariation`), `MspTypography` escala completa 1:1
      kollect, `tnum,lnum` tabular en dinero en columna / `lnum` proporcional en display; line-height 1.4×;
      hero 36sp/ExtraBold; `toMaterialTypography`.
- [ ] **Formas/espaciado/motion:** radios card 20 / hero 22 / tile 16 / control 12 / button 16 / field 14 /
      chip 999; `touchTarget 56dp`; springs `standard`/`emphasized` + `rememberReducedMotionEnabled()`.
- [ ] **`MspTheme`** provee los 5 grupos de token vía CompositionLocal + M3, con crossfade de paleta
      (`animateColors`) y render estático determinista para screenshots.
- [ ] **Componentes firma `Msp*`:** `MspSurface`/`MspCard`, `BrandGradient` (150° plano + `OnBrandAlpha`),
      `MspProgressBar`/`MspProgressRing`, `MspMoneyText` (+`formatMoneyMxn` es-MX + `MASKED_MONEY = "$••••"`,
      **BigDecimal, sin `Double`**), `MspStatusChip` (+`ChipStatus`, color+ícono+texto), `MspInitialsAvatar`,
      `MspHeroTodayCard`, `MspBentoTile`, `MspWeeklyBarsCard`, `MspCarteraCard` (Fase 2 por datos),
      `MspPrivacyEyeToggle`, `MspThemeToggle` + `ThemeRevealController` (+`LocalThemeReveal`,
      `maxDistanceToCorner`), `MspSegmentChips`, `MspSyncBand`/`MspPaymentSyncPill`, `MspPrimaryFieldButton`.
- [ ] **Interacciones desactivables:** toda animación consulta `rememberReducedMotionEnabled()`; el DS aporta
      el bridge de reveal circular (el `ThemeRevealRoot` completo es del piloto Plan 5).
- [ ] **Gate visual:** goldens Roborazzi por componente en Tier 1 × {1.0, 1.3, 2.0} × {light, dark} (+ Tier 2
      donde aplica), `verifyRoborazziDebug` verde, PNG commiteados.
- [ ] **Accesibilidad validada:** contraste (AAA-large hero / AAA-normal neutros — ver Parked de Task 10);
      dinero **reflowea, no trunca** a escala 2.0 (`MoneyNoTruncationTest`); estado siempre color+ícono+texto;
      targets 56dp.
- [ ] **`:app` idéntico** (nadie consume el DS aún; el piloto lo cablea en Plan 5). `assembleDevlocalDebug`
      verde.
- [ ] **Gate:** `prePushCheck` incluye `:core:designsystem:{ktlintCheck,testDebugUnitTest,detekt,koverVerify,`
      `verifyRoborazziDebug}`; commits por tarea, conventional, en español, sin atribución de Claude, sin push,
      rama `feat/multimodulo-cimiento`; sin `--no-verify`.

### Decisiones resueltas / parked (para el orquestador)
1. **Neutros 1:1 de kollect, no del mockup** (spec §2.1 manda "solo marca a azul"). Discrepancia con los
   neutros dark azul-grisáceos del mockup **parked** en Task 2 → la resuelve, si hace falta, el revisor de
   fidelidad visual del piloto (Plan 5). No bloquea.
2. **Umbrales de contraste:** AAA-large (4.5:1) para el monto hero y texto ≥18sp/700; AAA-normal (7:1) para
   texto normal sobre neutros; overlines/captions translúcidos sobre marca a AA-large. **Parked** en Task 10
   (Azul A no alcanza 7:1 con `onBrand` blanco sobre marca).
3. **Alcance Tier 2 en el DS:** el DS prueba resistencia a escala 2.0 (Tier 1 × 3 escalas) + variantes Tier 2
   solo donde el componente tiene modo alterno natural; el layout Tier 2 curado **por pantalla** es de Plan 5.
   **Parked** en Task 10.
4. **`ThemeRevealRoot` (grabar frame + `clipPath` + `Animatable` de radio) NO va en el DS** — necesita el
   `content` de toda la app; el DS provee el bridge (`ThemeRevealController`/`LocalThemeReveal`/`MspThemeToggle`
   /`maxDistanceToCorner`). El root es del composition root/piloto (Plan 5). Ver Task 9.
5. **`MspCarteraCard` se implementa y testea ahora** (catálogo completo del DS) aunque su cableado con datos de
   saldos por zona sea **Fase 2** (Plan 5 no la conecta). Ver Task 8.
