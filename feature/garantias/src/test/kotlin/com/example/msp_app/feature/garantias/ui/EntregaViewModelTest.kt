package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.garantias.application.EntregarGarantia
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La entrega con firma (prueba 7 del brief).
 *
 * Lo que estas pruebas protegen es la parte de D5 que más duele: **sin GPS no
 * se encola nada**, porque el servidor responde 422 para siempre y el cobrador
 * ya no puede corregirlo desde el teléfono. También que la firma se escriba
 * antes de encolar y que un encolado fallido no deje un PNG huérfano.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntregaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Los bytes de la firma dibujada: la prueba no compara el PNG, sólo que viaje. */
    private val png = ByteArray(128)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        ubicacion: Ubicacion? = Ubicacion(latitud = 19.4326, longitud = -99.1332),
        cola: ColaFalsa = ColaFalsa()
    ): EntregaMundo {
        val folios = FoliosFalsos(
            mutableListOf(
                GarantiasFixtures.folio(id = FOLIO, estadoLocal = EstadoLocalDeFolio.ABIERTO)
            )
        )
        val firmas = FirmasFalsas()
        val encolado = EncoladoFalso()
        val vm = EntregaViewModel(
            entregar = EntregarGarantia(
                folios = folios,
                cola = cola,
                cuerpos = CuerposFalsos(),
                firmas = firmas,
                claves = ClavesFalsas(),
                encolado = encolado,
                reloj = FakeClock(GarantiasFixtures.AHORA)
            ),
            ubicacionPort = UbicacionFalsa(ubicacion = ubicacion),
            savedStateHandle = SavedStateHandle(mapOf(GarantiasV2Rutas.ARG_FOLIO to FOLIO)),
            io = testDispatcher
        )
        return EntregaMundo(vm = vm, cola = cola, firmas = firmas, encolado = encolado)
    }

    @Test
    fun `pide el GPS al abrir la entrega`() = runTest(testDispatcher) {
        val mundo = viewModel()

        assertTrue("abre pidiendo ubicación", mundo.vm.state.value.pidiendoUbicacion)

        advanceUntilIdle()

        assertFalse(mundo.vm.state.value.pidiendoUbicacion)
        assertNotNull("el proveedor contestó", mundo.vm.state.value.ubicacion)
    }

    @Test
    fun `sin GPS la entrega se bloquea y no encola`() = runTest(testDispatcher) {
        val mundo = viewModel(ubicacion = null)
        advanceUntilIdle()

        mundo.vm.guardar(png)
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertEquals(FalloDeCaptura.SIN_UBICACION, estado.fallo)
        assertFalse(estado.guardado)
        assertTrue("no se encoló nada", mundo.cola.encolados.isEmpty())
        assertTrue("la firma no se llegó a escribir", mundo.firmas.guardadas.isEmpty())
    }

    @Test
    fun `sin firma la entrega se bloquea y no encola`() = runTest(testDispatcher) {
        val mundo = viewModel()
        advanceUntilIdle()

        mundo.vm.guardar(null)
        advanceUntilIdle()

        assertEquals(FalloDeCaptura.SIN_FIRMA, mundo.vm.state.value.fallo)
        assertTrue("no se encoló nada", mundo.cola.encolados.isEmpty())
    }

    @Test
    fun `con GPS y firma encola la entrega`() = runTest(testDispatcher) {
        val mundo = viewModel()
        advanceUntilIdle()

        mundo.vm.guardar(png)
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertNull(estado.fallo)
        assertFalse(estado.falloTecnico)
        assertTrue("la pantalla navega al detalle", estado.guardado)
        assertEquals(1, mundo.cola.encolados.size)
        assertEquals(1, mundo.firmas.guardadas.size)
        assertEquals(1, mundo.encolado.veces)
    }

    @Test
    fun `si el encolado falla se borra la firma y hay fallo tecnico`() = runTest(testDispatcher) {
        val mundo = viewModel(cola = ColaFalsa().apply { fallaAlEncolar = true })
        advanceUntilIdle()

        mundo.vm.guardar(png)
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertTrue("una base que no abre no cuelga la entrega", estado.falloTecnico)
        assertFalse(estado.guardado)
        assertEquals(
            "no se deja un PNG huérfano",
            listOf("firmas/firma.png"),
            mundo.firmas.borradas
        )
    }

    /** El ViewModel y las piezas que la prueba necesita para afirmar sobre él. */
    private data class EntregaMundo(
        val vm: EntregaViewModel,
        val cola: ColaFalsa,
        val firmas: FirmasFalsas,
        val encolado: EncoladoFalso
    )

    private companion object {
        const val FOLIO = "folio-local-1"
    }
}
