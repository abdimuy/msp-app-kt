package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.RarezaDelAbono
import com.example.msp_app.feature.pagos.ui.components.ABONO_CORTO_TAG
import com.example.msp_app.feature.pagos.ui.components.AFIRMAR_EL_MONTO
import com.example.msp_app.feature.pagos.ui.components.AVISO_DE_LA_HOJA_TAG
import com.example.msp_app.feature.pagos.ui.components.AVISO_TAG
import com.example.msp_app.feature.pagos.ui.components.CHIP_SUGERIDO_TAG
import com.example.msp_app.feature.pagos.ui.components.CONFIRMAR_TAG
import com.example.msp_app.feature.pagos.ui.components.CUOTA_DUDOSA_TAG
import com.example.msp_app.feature.pagos.ui.components.DUPLICADO_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Los avisos escalonados, donde el cobrador los ve.**
 *
 * Dos lugares y dos trabajos distintos:
 *
 *  1. **En vivo, mientras teclea el monto** (la captura, no un eco): el más
 *     importante de los dos, porque atrapar un cero de más en el teclado
 *     cuesta un borrón, atraparlo en la hoja cuesta salir del paso dos,
 *     corregir y volver a entrar.
 *  2. **En la hoja**, encabezando. Desde el 2026-09-29 los niveles 2 y 3 piden
 *     lo MISMO —un toque— y sólo se distinguen por el color de esta banda
 *     (ámbar/rojo) y su texto; ningún nivel exige teclear nada.
 *
 * Todo se prueba con **control positivo y negativo sobre el mismo aparato**: el
 * mismo composable, con un monto normal, no pinta nada — sin eso, una banda que
 * se pintara siempre pasaría estas pruebas igual.
 */
@Config(qualifiers = "w360dp-h800dp-mdpi")
class ElAvisoEscalonadoSeVeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var registros = 0

    // --- En vivo, bajo el monto ----------------------------------------------

    @Test
    fun `un monto normal no pinta ninguna banda de aviso`() {
        // El control negativo. El 68 % de los abonos de la ruta es exactamente
        // esto, y aquí la pantalla se calla.
        pinta(AbonoFixtures.enCaptura())

        assertEquals(0, composeTestRule.onAllNodesWithTag(AVISO_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `nivel 2 avisa en vivo con las cifras reales`() {
        pinta(AbonoFixtures.enAvisoDeCuotas())

        composeTestRule.onNodeWithTag(AVISO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Son 4 cuotas de \$220").assertIsDisplayed()
    }

    @Test
    fun `nivel 3 avisa en vivo tambien`() {
        pinta(AbonoFixtures.enAvisoDeAfirmar())

        composeTestRule.onNodeWithTag(AVISO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Son 6 cuotas de \$220").assertIsDisplayed()
    }

    @Test
    fun `un monto bloqueado no trae aviso encima del bloqueo`() {
        // Un monto prohibido ya tiene su banda roja con el máximo registrable.
        // Decirle además "son 1,363 cuotas" sería ruido sobre un hecho cerrado.
        pinta(AbonoFixtures.enBloqueo())

        assertEquals(0, composeTestRule.onAllNodesWithTag(AVISO_TAG).fetchSemanticsNodes().size)
    }

    // --- La parcialidad que se ve mal ----------------------------------------

    /**
     * **El defecto que el dueño vio**, al revés.
     *
     * Antes: *"abono corto · esperado $3,000 · este abono $600"* y un chip
     * ofreciendo $3,000. Ahora la pantalla dice que hay que revisar el dato de
     * la venta —que es lo accionable— y **no ofrece** ese esperado por ningún
     * lado. Lo segundo importa tanto como lo primero: si el chip siguiera
     * ofreciéndolo, la pantalla propondría una cifra y después la cuestionaría.
     */
    @Test
    fun `una parcialidad que se ve mal se dice, y no se ofrece`() {
        pinta(AbonoFixtures.conCuotaDudosa())

        composeTestRule.onNodeWithTag(CUOTA_DUDOSA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Revisa la parcialidad").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("La venta dice \$3,000 · en esta ruta nadie paga tanto")
            .assertIsDisplayed()

        assertEquals(
            "no se ofrece el esperado que no se sostiene",
            0,
            composeTestRule
                .onAllNodesWithTag(CHIP_SUGERIDO_TAG + "esperado_hoy")
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(CHIP_SUGERIDO_TAG + "esperado_por_costumbre")
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun `control positivo - con la cuota sana, el chip del esperado SI esta`() {
        // Sin esto, un chip que nunca se pintara dejaría el test de arriba en
        // verde sin medir nada.
        pinta(AbonoFixtures.enCaptura())

        composeTestRule.onNodeWithTag(CHIP_SUGERIDO_TAG + "esperado_hoy").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(CUOTA_DUDOSA_TAG).fetchSemanticsNodes().size
        )
    }

    // --- En la hoja: un toque basta en los dos niveles -----------------------

    @Test
    fun `nivel 2 encabeza la hoja en ambar y se confirma con un toque`() {
        pinta(AbonoFixtures.confirmandoConUnToque())

        composeTestRule.onNodeWithTag(AVISO_DE_LA_HOJA_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        assertEquals("un toque basta", 1, registros)
    }

    /**
     * **Nivel 3, desde el 2026-09-29**: la MISMA ruta que el nivel 2 — un solo
     * toque, sin teclear nada — con su propia banda roja (control positivo
     * indirecto: [ElAbonoRaroCuestaMasTest] ya mide el color exacto vía
     * `EncabezadoDelAviso`; aquí sólo se prueba que un toque registra).
     */
    @Test
    fun `nivel 3 tambien confirma con un solo toque`() {
        pinta(AbonoFixtures.afirmandoElMonto())

        composeTestRule.onNodeWithTag(AVISO_DE_LA_HOJA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(AFIRMAR_EL_MONTO).assertIsDisplayed()

        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        assertEquals("un solo toque registra, igual que en nivel 2", 1, registros)
    }

    @Test
    fun `un chip por debajo de lo esperado no se reclama, pero el duplicado si sale`() {
        // $100 es el redondo más común de la ruta y la fila lo está ofreciendo:
        // tocarlo no puede abrir una hoja que le reclame al cobrador haberlo
        // tocado. Lo que SÍ sale es el duplicado, que no es un juicio sobre el
        // monto sino un hecho sobre la cuenta.
        pinta(AbonoFixtures.enChipCortoYDuplicado())

        assertEquals(
            "la app no interroga lo que propuso",
            0,
            composeTestRule.onAllNodesWithTag(ABONO_CORTO_TAG).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(DUPLICADO_TAG).assertIsDisplayed()
    }

    @Test
    fun `control positivo - el mismo abono corto que NO esta en la fila si se reclama`() {
        // $50 es igual de corto y no lo ofrece nadie. Sin este control, una
        // banda que nunca se pintara dejaría el test de arriba en verde.
        pinta(AbonoFixtures.enAbonoCortoYDuplicado())

        composeTestRule.onNodeWithTag(ABONO_CORTO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DUPLICADO_TAG).assertIsDisplayed()
    }

    // --- Una sola banda por monto, y ninguna regaña ---------------------------

    /**
     * **La hoja nunca se pone roja sin una banda que lo explique.**
     *
     * Es el invariante que se rompió cuando la alerta vieja
     * (`MUY_ARRIBA_DE_LO_ESPERADO`) dejó de pintarse pero seguía contando para
     * `esRaro`: CTA de peligro, cifra en rojo, y ni una línea diciendo por qué.
     * Un rojo sin explicación es peor que no escalar.
     */
    @Test
    fun `si la hoja escala, hay al menos una banda que lo explica`() {
        // `afirmandoElMonto` es el caso desnudo: escala SÓLO por el aviso, sin
        // duplicado ni abono corto que puedan tapar el hueco. Con `enMontoRaro`
        // este test pasaría aunque el encabezado desapareciera, porque la banda
        // del duplicado seguiría ahí — medido, no supuesto.
        pinta(AbonoFixtures.afirmandoElMonto())

        composeTestRule.onNodeWithText(AFIRMAR_EL_MONTO).assertIsDisplayed()
        assertTrue(
            "la hoja está en rojo: algo tiene que decir por qué",
            bandasVisibles() > 0
        )
    }

    /**
     * **Un solo aviso sobre el monto**, no dos redacciones del mismo hecho.
     *
     * `enMontoRaro` enciende `MUY_ARRIBA_DE_LO_ESPERADO` en el veredicto —se
     * afirma abajo, para que esto no pase en verde por no haber nada que
     * suprimir— y aun así la hoja pinta **una** banda sobre el monto, la del
     * aviso escalonado. La vieja decía lo mismo con otras palabras, en rojo,
     * justo encima: así se enseña a ignorar el rojo.
     */
    @Test
    fun `el monto se comenta una sola vez, y sin regaño`() {
        val state = AbonoFixtures.enMontoRaro()
        assertTrue(
            "control positivo: la rareza vieja SÍ está encendida",
            RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in state.veredicto.rarezas
        )
        pinta(state)

        assertEquals(
            "una sola banda habla del monto",
            1,
            composeTestRule.onAllNodesWithTag(AVISO_DE_LA_HOJA_TAG).fetchSemanticsNodes().size
        )
        listOf("monto inusual — verifica", "monto poco común — verifica").forEach { regaño ->
            assertEquals(
                "'$regaño' es adjetivo y regaño: no vuelve",
                0,
                composeTestRule.onAllNodesWithText(regaño).fetchSemanticsNodes().size
            )
        }
        // Y el dato que aquella banda sí aportaba sigue en pie, dentro de ésta.
        composeTestRule.onNodeWithText("Esperado $120 · este abono $1,200").assertIsDisplayed()
    }

    @Test
    fun `una confirmacion normal no pide encabezado`() {
        // Control negativo de los dos de arriba, sobre la MISMA hoja.
        pinta(AbonoFixtures.enConfirmacion())

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(AVISO_DE_LA_HOJA_TAG).fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag(CONFIRMAR_TAG).performClick()
        assertEquals(1, registros)
    }

    /** Cuántas bandas hay arriba de la hoja, de cualquiera de los tres tipos. */
    private fun bandasVisibles(): Int = listOf(
        AVISO_DE_LA_HOJA_TAG,
        ABONO_CORTO_TAG,
        DUPLICADO_TAG
    ).sumOf { composeTestRule.onAllNodesWithTag(it).fetchSemanticsNodes().size }

    private fun pinta(state: RegistrarAbonoUiState) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) { Pantalla(state) }
        }
    }

    @Composable
    private fun Pantalla(state: RegistrarAbonoUiState) {
        RegistrarAbonoContent(
            state = state,
            onAtras = {},
            onDigito = {},
            onPunto = {},
            onBorrar = {},
            onMetodo = {},
            onSugerido = {},
            onRegistrar = {},
            onConfirmar = { registros += 1 },
            onEditar = {},
            onRevisar = {},
            onAgregarFoto = {},
            onOrigen = {},
            onCerrarOrigenes = {},
            onQuitarFoto = {}
        )
    }
}
