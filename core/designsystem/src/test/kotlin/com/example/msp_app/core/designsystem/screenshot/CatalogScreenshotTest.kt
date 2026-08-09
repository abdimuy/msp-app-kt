package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.runtime.Composable
import org.junit.Test

/**
 * Gate visual del catálogo completo (Task 10, spec §5 "Screenshot por tier ×
 * escala"): cada componente firma de [MspCatalog] se captura en la matriz
 * Tier 1 × [SCALES] (`1.0`/`1.3`/`2.0`) × [THEMES] (`light`/`dark`) — 6
 * goldens por componente. `hero` y `bento` (los únicos con un modo alterno
 * natural, task-10-brief.md "Alcance Tier 2" parked) suman además la misma
 * matriz de 6 en su variante Tier 2 curada.
 *
 * Matriz manual (no `@ParameterizedRobolectricTestRunner`, task-10-brief.md
 * ofrece ambas opciones): cada `@Test` es un componente del catálogo,
 * [captureTier1]/[captureTier2] iteran dentro del método sobre las 6
 * combinaciones escala×tema — mismo mecanismo `capture()` (Task 5) que ya usa
 * cada `*ScreenshotTest` de Tasks 6-9, solo con más invocaciones por método.
 * Nombre de golden determinista: `catalog_<componente>_<tema>_<escala>[_tier2]`
 * (el prefijo `catalog_` evita colisión con los goldens baseline `msp_*` de
 * Tasks 6-9, que se conservan intactos).
 *
 * Determinismo: [MspScreenshotTest] fuerza `ANIMATOR_DURATION_SCALE = 0` en
 * su `@Before` — el mismo mecanismo anti-cuelgue que ya protege
 * `MspPaymentSyncPill` (ver `PaymentSyncPillScreenshotTest`) cubre también
 * [CatalogSync] aquí, que reutiliza ese mismo componente animado.
 */
class CatalogScreenshotTest : MspScreenshotTest() {

    @Test
    fun `catalogo hero tier1`() = captureTier1("hero") { CatalogHero() }

    @Test
    fun `catalogo hero tier2`() = captureTier2("hero") { CatalogHeroTier2() }

    @Test
    fun `catalogo bento tier1`() = captureTier1("bento") { CatalogBentoDuo() }

    @Test
    fun `catalogo bento tier2`() = captureTier2("bento") { CatalogBentoTier2() }

    @Test
    fun `catalogo status chips tier1`() = captureTier1("status_chips") { CatalogStatusChips() }

    @Test
    fun `catalogo weekly bars tier1`() = captureTier1("weekly_bars") { CatalogWeeklyBars() }

    @Test
    fun `catalogo cartera tier1`() = captureTier1("cartera") { CatalogCartera() }

    @Test
    fun `catalogo toggles tier1`() {
        for (theme in THEMES) {
            for (scale in SCALES) {
                capture(
                    name = goldenName("toggles", theme.name, scale.label),
                    dark = theme.dark,
                    fontScale = scale.value
                ) { CatalogToggles(darkTheme = theme.dark) }
            }
        }
    }

    @Test
    fun `catalogo segment chips tier1`() = captureTier1("segment_chips") { CatalogSegmentChips() }

    @Test
    fun `catalogo sync tier1`() = captureTier1("sync") { CatalogSync() }

    @Test
    fun `catalogo cta tier1`() = captureTier1("cta") { CatalogCta() }

    @Test
    fun `catalogo money text tier1`() = captureTier1("money_text") { CatalogMoneyText() }

    @Test
    fun `catalogo avatar tier1`() = captureTier1("avatar") { CatalogAvatar() }

    @Test
    fun `catalogo progress tier1`() = captureTier1("progress") { CatalogProgress() }

    private fun captureTier1(component: String, content: @Composable () -> Unit) {
        for (theme in THEMES) {
            for (scale in SCALES) {
                capture(
                    name = goldenName(component, theme.name, scale.label),
                    dark = theme.dark,
                    fontScale = scale.value
                ) { content() }
            }
        }
    }

    private fun captureTier2(component: String, content: @Composable () -> Unit) {
        for (theme in THEMES) {
            for (scale in SCALES) {
                capture(
                    name = goldenName(component, theme.name, scale.label, tier2 = true),
                    dark = theme.dark,
                    fontScale = scale.value
                ) { content() }
            }
        }
    }
}

private data class CatalogTheme(val name: String, val dark: Boolean)
private data class CatalogScale(val label: String, val value: Float)

private val THEMES = listOf(CatalogTheme("light", dark = false), CatalogTheme("dark", dark = true))
private val SCALES = listOf(
    CatalogScale("1_0", 1.0f),
    CatalogScale("1_3", 1.3f),
    CatalogScale("2_0", 2.0f)
)

private fun goldenName(
    component: String,
    theme: String,
    scale: String,
    tier2: Boolean = false
): String {
    val suffix = if (tier2) "_tier2" else ""
    return "catalog_${component}_${theme}_$scale$suffix"
}
