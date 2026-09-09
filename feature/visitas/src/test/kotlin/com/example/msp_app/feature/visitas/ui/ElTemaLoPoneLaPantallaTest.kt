package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.printing.application.EvaluatePrintPermission
import com.example.msp_app.core.printing.application.PrintTicketUseCase
import com.example.msp_app.core.printing.application.PrinterDirectory
import com.example.msp_app.core.printing.application.TicketPrinting
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.CargarTicketDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.data.fake.FakeComprobantesDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeImpresoraPreferida
import com.example.msp_app.feature.visitas.data.fake.FakePrintLog
import com.example.msp_app.feature.visitas.data.fake.FakePrinterPort
import com.example.msp_app.feature.visitas.data.fake.FakeRecomendacionesPort
import com.example.msp_app.feature.visitas.data.fake.FakeRegistroDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.FakeVisitaImpresaPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * **El punto de entrada real, montado SIN tema** — el gemelo de
 * `com.example.msp_app.feature.pagos.ui.ElTemaLoPoneLaPantallaTest`, con el
 * mismo razonamiento entero en su KDoc (por qué el `*Screen` y no el `NavHost`,
 * y por qué las pantallas no se listan a mano).
 *
 * En corto: todo lo demás de la suite monta `*Content` y le REGALA un
 * `MspTheme { }`, o sea prueba la premisa que producción viola. Aquí no se
 * aporta tema, que es el árbol que `:app` construye alrededor de un destino del
 * `NavHost`; si la pantalla no se envuelve a sí misma, esto revienta con
 * `IllegalStateException("MspTheme ausente")`, la excepción exacta del crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElTemaLoPoneLaPantallaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Puertos vacíos a propósito: el crash del emulador ocurrió con la base SIN
    // sincronizar. La lectura del tema está en el modificador más externo.
    private val io = StandardTestDispatcher()
    private val clock = FakeClock(TicketDeVisitaFixtures.REGISTRADA_EN)
    private val telemetria = RecordingTelemetry(clock)

    private val contextoPort = FakeContextoDeVisitaPort()
    private val recomendacionesPort = FakeRecomendacionesPort()
    private val registroPort = FakeRegistroDeVisitaPort()
    private val ubicacionPort = FakeUbicacionPort()
    private val camaraPort = FakeComprobantesDeVisitaPort()
    private val visitaImpresaPort = FakeVisitaImpresaPort()
    private val printer = FakePrinterPort()
    private val impresoraPreferida = FakeImpresoraPreferida()
    private val printLog = FakePrintLog()

    /**
     * Monta [pantalla] **sin `MspTheme` y sin ningún `CompositionLocalProvider`
     * de andamiaje**: el entorno del teléfono, no el del harness.
     */
    private fun montarSinTema(pantalla: @Composable () -> Unit) {
        composeTestRule.setContent { pantalla() }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertExists()
    }

    private fun pantallas(): Map<String, @Composable () -> Unit> = mapOf(
        "RegistrarVisitaScreen" to {
            RegistrarVisitaScreen(
                viewModel = RegistrarVisitaViewModel(
                    savedStateHandle = SavedStateHandle(
                        mapOf(
                            VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA,
                            VisitasRutas.ARG_VENTA_ID to VisitasFixtures.REFRIGERADOR
                        )
                    ),
                    abrirRegistro = AbrirRegistroDeVisita(
                        contextoPort,
                        recomendacionesPort,
                        telemetria
                    ),
                    registrarVisita = RegistrarVisita(registroPort, ubicacionPort, telemetria),
                    camara = camaraPort,
                    telemetry = telemetria,
                    clock = clock,
                    io = io
                ),
                onAtras = {},
                onRegistrada = {}
            )
        },
        "TicketDeVisitaScreen" to {
            TicketDeVisitaScreen(
                viewModel = TicketDeVisitaViewModel(
                    savedStateHandle = SavedStateHandle(
                        mapOf(VisitasRutas.ARG_VISITA_ID to TicketDeVisitaFixtures.VISITA_ID)
                    ),
                    cargarTicket = CargarTicketDeVisita(visitaImpresaPort, contextoPort),
                    impresion = TicketPrinting(
                        directorio = PrinterDirectory(printer, impresoraPreferida),
                        evaluar = EvaluatePrintPermission(printLog, clock),
                        imprimirYRegistrar = PrintTicketUseCase(printer, printLog, clock)
                    ),
                    telemetry = telemetria,
                    io = io
                ),
                onAtras = {}
            )
        }
    )

    private fun montar(nombre: String) = montarSinTema(pantallas().getValue(nombre))

    @Test
    fun `registrar visita se monta sin que nadie le de el tema`() {
        montar("RegistrarVisitaScreen")
    }

    @Test
    fun `el ticket de visita se monta sin que nadie le de el tema`() {
        montar("TicketDeVisitaScreen")
    }

    /**
     * **La compuerta.** Las pantallas cubiertas no son una lista escrita a mano:
     * tienen que ser EXACTAMENTE las que declara `src/main`. Una pantalla nueva
     * en este módulo pone rojo este test el día que se escribe.
     *
     * Control positivo: el escaneo tiene que encontrar algo, o no probaría nada.
     */
    @Test
    fun cadaPantallaDelModuloTieneSuPrueba() {
        val declaradas = pantallasDeclaradasEn(File("src/main/kotlin"))
        assertTrue(
            "el escaneo no encontró ninguna pantalla: el método no probaría nada",
            declaradas.isNotEmpty()
        )
        assertEquals(declaradas, pantallas().keys.toSortedSet())
    }

    private companion object {

        /** `fun XxxScreen(` de nivel superior, que es la forma que el grafo monta. */
        val PANTALLA = Regex("""^(?:internal )?fun ([A-Z][A-Za-z0-9]*Screen)\(""")

        fun pantallasDeclaradasEn(raiz: File) = raiz.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { archivo ->
                archivo.readLines().mapNotNull { PANTALLA.find(it)?.groupValues?.get(1) }
            }
            .toSortedSet()
    }
}
