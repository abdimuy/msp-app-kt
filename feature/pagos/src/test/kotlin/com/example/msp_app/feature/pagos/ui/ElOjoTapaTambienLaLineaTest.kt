package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Con el ojo prendido, la línea de contactos del detalle también se tapa.**
 *
 * El defecto que esto cierra, visto en el detalle de cliente: la pantalla le
 * pasaba `ocultos` a `HojaDeDinero` y a `HojaDeVentas`, pero **no** a
 * `HojaDeContactos`. Adentro, `ContactoEnLinea` y `EncabezadoDeGrupo` caían a su
 * default `ocultos = false`, así que con "esconder cantidades" puesto el saldo
 * de arriba salía enmascarado y el `Cobré · $350` de abajo seguía a la vista
 * **en la misma pantalla y en el mismo scroll**.
 *
 * Eso es peor que no tener el ojo: el cobrador lo prende justo cuando alguien
 * está mirando el teléfono, y la pantalla le dice que ya tapó lo que no tapó.
 * `BitacoraScreen` ya lo cableaba bien y aquí se copió su cableado.
 *
 * ## Por qué se busca DENTRO de la fila y no en la pantalla entera
 *
 * "$350" también es la parcialidad de una de las cuentas, y ésa sí se
 * enmascaraba desde antes. Contar la pantalla completa dejaría pasar el defecto
 * si alguien tapara una de las dos mitades. La consulta se ancla al
 * `testTag` de la fila de contacto: lo que se afirma es el importe **de la
 * línea**.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElOjoTapaTambienLaLineaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * Con el ojo prendido no queda un solo importe legible colgando de una fila
     * de la línea.
     */
    @Test
    fun `con el ojo prendido el importe del contacto no se lee`() {
        detalleCon(ocultos = true)

        assertEquals(
            "la línea de contactos enseñó $IMPORTE_DEL_COBRO con “esconder cantidades” " +
                "puesto: el ojo tapa el saldo de arriba y deja el cobro a la vista en la " +
                "misma pantalla",
            0,
            enLaLinea(IMPORTE_DEL_COBRO)
        )
    }

    /**
     * **Control positivo.** Con el ojo apagado, la MISMA consulta encuentra el
     * importe. Sin esto, la ausencia de arriba no prueba nada: una fila que
     * dejara de pintar el importe —o un `testTag` mal escrito— daría cero en los
     * dos casos y el test se quedaría verde para siempre.
     */
    @Test
    fun `control positivo - con el ojo apagado el importe SI se ve`() {
        detalleCon(ocultos = false)

        assertTrue(
            "la consulta no encuentra el importe ni donde SÍ tiene que estar: " +
                "entonces el test de arriba no prueba nada",
            enLaLinea(IMPORTE_DEL_COBRO) > 0
        )
    }

    /**
     * Y con el ojo prendido lo que sí aparece es la máscara — o sea que el
     * importe no desapareció de la fila, se tapó. Sin esto, una fila que
     * escondiera el renglón entero pasaría el primer test.
     */
    @Test
    fun `con el ojo prendido el contacto pinta la mascara en lugar de la cifra`() {
        detalleCon(ocultos = true)

        assertTrue(
            "la fila no pintó la máscara: el importe no se tapó, se fue",
            enLaLinea(MASCARA) > 0
        )
    }

    // --- Montaje -------------------------------------------------------------

    private fun detalleCon(ocultos: Boolean) {
        composeTestRule.setContent {
            Tema {
                DetalleClienteContent(
                    state = DetalleClienteUiState(
                        cargando = false,
                        detalle = PagosFixtures.detalleCliente(),
                        montosOcultos = ocultos
                    ),
                    onAtras = {},
                    onAbrirVenta = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onVerContactos = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {}
                )
            }
        }
    }

    @Composable
    private fun Tema(contenido: @Composable () -> Unit) {
        MspTheme(darkTheme = false, animateColors = false, content = contenido)
    }

    /**
     * Cuántos nodos dicen [texto] **colgando de una fila de la línea**. Es *el*
     * método que comparten la aserción de ausencia y su control positivo: si los
     * dos no usan exactamente esta búsqueda, el control no controla nada.
     */
    private fun enLaLinea(texto: String): Int = composeTestRule.onAllNodes(
        hasText(texto, substring = true) and
            hasAnyAncestor(hasTestTag(CONTACTO_EN_LINEA_TAG)),
        useUnmergedTree = true
    ).fetchSemanticsNodes().size

    private companion object {
        /** `$#,##0` sobre el abono de la fixture. */
        const val IMPORTE_DEL_COBRO = "$350"

        /** Lo que `MspMoneyText` pinta enmascarado — `MASKED_MONEY`. */
        const val MASCARA = "$••••"
    }
}
