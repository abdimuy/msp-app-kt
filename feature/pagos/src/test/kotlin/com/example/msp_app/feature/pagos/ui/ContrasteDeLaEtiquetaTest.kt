package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.msp_app.core.designsystem.theme.mspDarkColors
import com.example.msp_app.core.designsystem.theme.mspLightColors
import com.example.msp_app.feature.pagos.ui.components.TextoDeLaEtiquetaPrincipal
import com.example.msp_app.feature.pagos.ui.components.verdeDeLaEtiqueta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El texto blanco de la etiqueta principal tiene contraste AA (≥ 4.5:1) en los dos temas. */
class ContrasteDeLaEtiquetaTest {

    private fun contraste(a: Color, b: Color): Float {
        val (claro, oscuro) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (claro + 0.05f) / (oscuro + 0.05f)
    }

    @Test
    fun `el texto es blanco y el verde es el de claro en ambos temas`() {
        assertEquals(Color.White, TextoDeLaEtiquetaPrincipal)
        assertEquals(mspLightColors().statusPaid, verdeDeLaEtiqueta())
    }

    @Test
    fun `blanco sobre el verde de la etiqueta pasa 4_5 a 1 en claro y en oscuro`() {
        // La etiqueta usa el MISMO verde en los dos temas, así que el contraste es uno.
        val c = contraste(TextoDeLaEtiquetaPrincipal, verdeDeLaEtiqueta())
        assertTrue("contraste $c", c >= 4.5f)
    }

    @Test
    fun `control positivo, el verde menta del tema oscuro NO pasa con blanco`() {
        // Sin esto, una función de contraste rota daría verde a todo.
        val c = contraste(Color.White, mspDarkColors().statusPaid)
        assertTrue("contraste $c", c < 4.5f)
    }
}
