package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.PrintPhase
import com.example.msp_app.feature.collectionreport.ui.PrintSheetUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Cobertura de [PrintSheet]/[PrintSheetBody] (P2): el picker muestra las impresoras y al
 * tocarlas invoca la selección; el estado de error muestra el mensaje + "Reintentar" +
 * "Cambiar impresora" (siempre disponible); el éxito ofrece "Cambiar impresora"; sin flujo no
 * renderiza nada.
 *
 * El comportamiento (mostrar/tocar) se prueba contra [PrintSheetBody] renderizado directo —
 * `performClick` NO cruza a la ventana `Popup` donde M3 monta el `ModalBottomSheet` (mismo
 * gotcha que documenta [SheetBody]/`ReportSheetsTest`); el open/cierre del `ModalBottomSheet`
 * real se prueba contra [PrintSheet] con queries de semántica, que sí cruzan ventanas.
 */
@OptIn(ExperimentalMaterial3Api::class)
class PrintSheetTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val printerA = PrinterDevice(address = "00:11:22:33:44:55", name = "Impresora A")
    private val printerB = PrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "Impresora B")

    private fun setBody(
        printSheet: PrintSheetUi,
        onPrint: () -> Unit = {},
        onSelectPrinter: (PrinterDevice) -> Unit = {},
        onChangePrinter: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                PrintSheetBody(
                    printSheet = printSheet,
                    onPrint = onPrint,
                    onSelectPrinter = onSelectPrinter,
                    onChangePrinter = onChangePrinter
                )
            }
        }
    }

    @Test
    fun `sin flujo de impresion el ModalBottomSheet no renderiza nada`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                PrintSheet(
                    state = MockupFixtures.stateDia(),
                    onDismiss = {},
                    onPrint = {},
                    onSelectPrinter = {},
                    onChangePrinter = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Elige una impresora").assertDoesNotExist()
    }

    @Test
    fun `el ModalBottomSheet abierto en el picker muestra el titulo`() {
        val state = MockupFixtures.stateDia().copy(
            printSheet = PrintSheetUi(PrintPhase.SELECTING, printers = listOf(printerA))
        )
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                PrintSheet(
                    state = state,
                    onDismiss = {},
                    onPrint = {},
                    onSelectPrinter = {},
                    onChangePrinter = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Elige una impresora").assertExists()
        composeTestRule.onNodeWithText("Impresora A").assertExists()
    }

    @Test
    fun `el picker muestra las impresoras emparejadas`() {
        setBody(PrintSheetUi(PrintPhase.SELECTING, printers = listOf(printerA, printerB)))

        composeTestRule.onNodeWithText("Elige una impresora").assertIsDisplayed()
        composeTestRule.onNodeWithText("Impresora A").assertIsDisplayed()
        composeTestRule.onNodeWithText("Impresora B").assertIsDisplayed()
    }

    @Test
    fun `tocar una impresora del picker invoca la seleccion con ese dispositivo`() {
        var selected: PrinterDevice? = null
        setBody(
            PrintSheetUi(PrintPhase.SELECTING, printers = listOf(printerA, printerB)),
            onSelectPrinter = { selected = it }
        )

        composeTestRule.onNodeWithText("Impresora B").performClick()

        assertEquals(printerB, selected)
    }

    @Test
    fun `el picker sin impresoras muestra la guia para emparejar`() {
        setBody(PrintSheetUi(PrintPhase.SELECTING, printers = emptyList()))

        composeTestRule
            .onNodeWithText("Empareja una impresora en los ajustes de Bluetooth")
            .assertIsDisplayed()
    }

    @Test
    fun `el estado de error muestra el mensaje, Reintentar y Cambiar impresora`() {
        setBody(
            PrintSheetUi(
                phase = PrintPhase.ERROR,
                target = printerA,
                printers = listOf(printerA),
                message = "no se pudo conectar con la impresora"
            )
        )

        composeTestRule.onNodeWithText("no se pudo conectar con la impresora").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reintentar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cambiar impresora").assertIsDisplayed()
    }

    @Test
    fun `Cambiar impresora esta disponible en exito e invoca abrir el picker`() {
        var changed = false
        setBody(
            PrintSheetUi(PrintPhase.SUCCESS, target = printerA, printers = listOf(printerA)),
            onChangePrinter = { changed = true }
        )

        composeTestRule.onNodeWithText("Imprimir de nuevo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cambiar impresora").performClick()

        assertEquals(true, changed)
    }
}
