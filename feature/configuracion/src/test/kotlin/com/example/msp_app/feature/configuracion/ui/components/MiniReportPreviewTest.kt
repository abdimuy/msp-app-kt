package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cobertura JVM plana (sin Robolectric) de [previewDensity] — la pieza pura que hace que el
 * preview de "Así se verá" reaccione al nivel elegido: pisa [Density.fontScale] con
 * `level.nominalScale`, preservando [Density.density] (px/dp) del ambiente. Deliberadamente NO
 * es un compose-test que mida tamaños de layout en píxeles: el modo gráfico no-nativo de
 * [com.example.msp_app.core.testing.RobolectricTestBase] (el que usan los compose-tests de este
 * módulo que no son golden Roborazzi) no garantiza que el motor de texto shadow refleje
 * `fontScale` con precisión de píxel al medir — verificado empíricamente al escribir este test:
 * apilar dos `MiniReportPreview` (Normal/Muy grande) y comparar `fetchSemanticsNode().size.height`
 * dio el MISMO alto para ambos. Esta función es el mecanismo real que consume `MiniReportPreview`/
 * `FontSizeOptionCard` — probarla aquí, determinista, es más confiable que perseguir píxeles.
 */
class MiniReportPreviewTest {

    @Test
    fun `NORMAL usa fontScale 1`() {
        val result = previewDensity(Density(density = 2.75f, fontScale = 1f), FontSizeLevel.NORMAL)

        assertEquals(1f, result.fontScale)
        assertEquals(2.75f, result.density)
    }

    @Test
    fun `GRANDE usa fontScale 1_5`() {
        val result = previewDensity(Density(density = 2.75f, fontScale = 1f), FontSizeLevel.GRANDE)

        assertEquals(1.5f, result.fontScale)
        assertEquals(2.75f, result.density)
    }

    @Test
    fun `MUY_GRANDE usa fontScale 2`() {
        val result =
            previewDensity(Density(density = 2.75f, fontScale = 1f), FontSizeLevel.MUY_GRANDE)

        assertEquals(2f, result.fontScale)
        assertEquals(2.75f, result.density)
    }

    @Test
    fun `preserva la densidad ambiente sin importar el fontScale del ambiente`() {
        // El ambiente puede ya traer un fontScale distinto de 1 (accesibilidad del SO, o la
        // raíz de composición aplicando Opción C) — previewDensity SIEMPRE lo reemplaza por el
        // del nivel elegido, nunca lo combina/multiplica (si no, el preview mostraría un tamaño
        // distinto al que ese nivel produce de verdad en el resto de la app).
        val result = previewDensity(Density(density = 3f, fontScale = 1.8f), FontSizeLevel.NORMAL)

        assertEquals(1f, result.fontScale)
        assertEquals(3f, result.density)
    }
}
