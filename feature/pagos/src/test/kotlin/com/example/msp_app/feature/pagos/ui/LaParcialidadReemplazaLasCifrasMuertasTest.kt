package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ResumenDelCliente
import com.example.msp_app.feature.pagos.ui.components.CifrasDelCliente
import com.example.msp_app.feature.pagos.ui.components.PARCIALIDAD_DEL_CLIENTE_TAG
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El bloque de saldo del detalle de cliente pinta la parcialidad, no "suele
 * dar" ni "pídele hoy".**
 *
 * El dueño pidió quitar esas dos cifras porque "no sirven para nada" y poner
 * la parcialidad en su lugar — ver el KDoc de
 * [com.example.msp_app.feature.pagos.ui.components.CifrasDelCliente]. Lo que
 * este test afirma es lo que un rediseño futuro podría deshacer en silencio:
 * que la cifra viva se pinta con SU etiqueta, y que las dos muertas ya no
 * aparecen aunque el [ResumenDelCliente] siga trayéndolas (se quedan en el
 * modelo por otros consumidores, no en la pantalla).
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaParcialidadReemplazaLasCifrasMuertasTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun cifras(ocultos: Boolean = false) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                CifrasDelCliente(
                    resumen = ResumenDelCliente(
                        // Con dato en las dos cifras muertas: si alguna
                        // siguiera pintándose, este valor la delataría.
                        promedioDeMicrosip = Money.of(BigDecimal("150")),
                        pideleHoy = Money.of(BigDecimal("440")),
                        parcialidad = PARCIALIDAD
                    ),
                    ocultos = ocultos
                )
            }
        }
    }

    @Test
    fun `la parcialidad se pinta con su etiqueta y el monto sumado de las cuentas`() {
        cifras()

        composeTestRule.onNodeWithText("PARCIALIDAD").assertIsDisplayed()
        assertEquals(
            "la parcialidad no se pintó bajo su testTag con el monto sumado",
            1,
            dentroDeLaParcialidad("$570").size
        )
    }

    @Test
    fun `con el ojo prendido la parcialidad tambien se tapa`() {
        cifras(ocultos = true)

        assertEquals(
            "con \"esconder cantidades\" puesto la parcialidad se quedó legible",
            0,
            dentroDeLaParcialidad("$570").size
        )
        assertEquals(
            "con el ojo prendido la parcialidad tiene que pintar la máscara",
            1,
            dentroDeLaParcialidad(MASCARA).size
        )
    }

    @Test
    fun `control positivo - las cifras muertas ya no se pintan aunque el resumen las traiga`() {
        cifras()

        composeTestRule.onNodeWithText("SUELE DAR", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("PÍDELE HOY", substring = true).assertDoesNotExist()
    }

    /** Cuántos nodos dicen [texto] colgando de la celda de parcialidad. */
    private fun dentroDeLaParcialidad(texto: String) = composeTestRule.onAllNodes(
        hasText(texto, substring = true) and
            hasAnyAncestor(hasTestTag(PARCIALIDAD_DEL_CLIENTE_TAG)),
        useUnmergedTree = true
    ).fetchSemanticsNodes()

    private companion object {
        /** Un monto arbitrario — lo que se afirma es que SE pinta, no de dónde sale. */
        val PARCIALIDAD: Money = Money.of(BigDecimal("570"))

        /** Lo que `MspMoneyText` pinta enmascarado — `MASKED_MONEY`. */
        const val MASCARA = "$••••"
    }
}
