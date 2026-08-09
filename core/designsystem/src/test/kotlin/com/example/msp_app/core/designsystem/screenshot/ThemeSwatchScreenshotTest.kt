package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Primer golden real del design system Msp: prueba que el pipeline entero
 * (tema + fuente + tokens + Roborazzi + Robolectric) rinde y compara,
 * capturando un swatch de tokens en light y dark a escala 1.0. Tasks 6-10
 * amplían la matriz Tier×escala×tema sobre la base sentada aquí — esta
 * clase NO es un componente del design system, es un fixture de diagnóstico.
 */
class ThemeSwatchScreenshotTest : MspScreenshotTest() {

    @Test
    fun `swatch light`() {
        capture(name = "theme_swatch_light", dark = false) { MspThemeSwatch() }
    }

    @Test
    fun `swatch dark`() {
        capture(name = "theme_swatch_dark", dark = true) { MspThemeSwatch() }
    }
}

/**
 * Pinta una muestra de `brand`, `heroProgressFill`, `surface`, `onSurface`
 * y los tres tonos de estado más comunes (pagado/parcial/vencido), más un
 * `Text` de muestra con `MspTheme.type.amountHero` — ejercita colores,
 * tipografía, espaciado y formas en una sola captura.
 */
@Composable
private fun MspThemeSwatch() {
    Column(
        modifier = Modifier.padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = "$1,234.00",
            style = MspTheme.type.amountHero,
            color = MspTheme.colors.onSurface
        )
        ColorSwatch(label = "brand", color = MspTheme.colors.brand)
        ColorSwatch(label = "heroProgressFill", color = MspTheme.colors.heroProgressFill)
        ColorSwatch(label = "surface", color = MspTheme.colors.surface)
        ColorSwatch(label = "onSurface", color = MspTheme.colors.onSurface)
        ColorSwatch(label = "statusPaid", color = MspTheme.colors.statusPaid)
        ColorSwatch(label = "statusPartial", color = MspTheme.colors.statusPartial)
        ColorSwatch(label = "statusOverdue", color = MspTheme.colors.statusOverdue)
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color, MspTheme.shapes.control)
        )
        Text(text = label, style = MspTheme.type.body, color = MspTheme.colors.onSurface)
    }
}
