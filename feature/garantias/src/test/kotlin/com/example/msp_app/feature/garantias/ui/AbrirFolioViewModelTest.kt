package com.example.msp_app.feature.garantias.ui

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.garantias.application.AbrirFolioDeGarantia
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.domain.model.ClienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeCuenta
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El alta de un folio (prueba 7 del brief).
 *
 * Lo que estas pruebas protegen: que la búsqueda de cliente no bloquee la
 * pantalla ni se deje pisar por una consulta vieja, que el domicilio se prellene
 * con lo que el teléfono ya sabe, y que el contrato del servidor se valide
 * **antes** de encolar para que el cobrador corrija en pantalla y no en un 422.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AbrirFolioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        clientes: List<ClienteDeGarantia> = emptyList(),
        clientesFallan: Boolean = false,
        cola: ColaFalsa = ColaFalsa()
    ): AbrirMundo {
        val folios = FoliosFalsos()
        val vm = AbrirFolioViewModel(
            abrirFolio = AbrirFolioDeGarantia(
                folios = folios,
                cola = cola,
                cuerpos = CuerposFalsos(),
                claves = ClavesFalsas(),
                encolado = EncoladoFalso(),
                reloj = FakeClock(GarantiasFixtures.AHORA)
            ),
            clientes = ClientesFalsos(clientes = clientes, falla = clientesFallan),
            io = testDispatcher
        )
        return AbrirMundo(vm = vm, folios = folios, cola = cola)
    }

    private fun formularioListo(vm: AbrirFolioViewModel) {
        vm.cambiarDescripcion("bocina que no enciende")
        vm.cambiarClaveDelArticulo("K-123")
        vm.cambiarDescripcionDelArticulo("bocina")
    }

    @Test
    fun `al elegir un cliente se prellena el domicilio que el telefono conoce`() =
        runTest(testDispatcher) {
            val mundo = viewModel(clientes = listOf(cliente(nombre = "Juan Pérez")))

            mundo.vm.cambiarBusqueda("Juan")
            advanceUntilIdle()
            assertEquals(1, mundo.vm.state.value.clientes.size)

            mundo.vm.elegirCliente(mundo.vm.state.value.clientes.single())

            val estado = mundo.vm.state.value
            assertEquals("Juan Pérez", checkNotNull(estado.clienteElegido).nombre)
            assertEquals("Av. Reforma", estado.domicilio.calle)
            assertEquals("CDMX", estado.domicilio.ciudad)
        }

    @Test
    fun `una busqueda vieja no pisa a la nueva`() = runTest(testDispatcher) {
        val mundo = viewModel(
            clientes = listOf(cliente(nombre = "Ana López"), cliente(nombre = "Beto Ruiz"))
        )

        mundo.vm.cambiarBusqueda("Ana")
        mundo.vm.cambiarBusqueda("Beto")
        advanceUntilIdle()

        val encontrados = mundo.vm.state.value.clientes
        assertEquals(1, encontrados.size)
        assertEquals("Beto Ruiz", encontrados.single().nombre)
    }

    @Test
    fun `guardar sin descripcion queda bloqueado y no encola`() = runTest(testDispatcher) {
        val mundo = viewModel()

        mundo.vm.guardar()
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertEquals(FalloDeCaptura.SIN_DESCRIPCION, estado.fallo)
        assertNull(estado.folioCreado)
        assertFalse(estado.guardando)
        assertTrue(mundo.cola.encolados.isEmpty())
    }

    @Test
    fun `guardar encola el folio y su primer articulo`() = runTest(testDispatcher) {
        val mundo = viewModel()
        formularioListo(mundo.vm)

        mundo.vm.guardar()
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertNull(estado.fallo)
        assertFalse(estado.guardando)
        assertEquals("clave-1", estado.folioCreado)
        assertEquals(2, mundo.cola.encolados.size)
        assertEquals(1, mundo.folios.guardados.size)
    }

    @Test
    fun `una base que no abre deja fallo tecnico y no navega`() = runTest(testDispatcher) {
        val mundo = viewModel(cola = ColaFalsa().apply { fallaAlEncolar = true })
        formularioListo(mundo.vm)

        mundo.vm.guardar()
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertTrue(estado.falloTecnico)
        assertNull(estado.folioCreado)
        assertFalse(estado.guardando)
    }

    private fun cliente(nombre: String): ClienteDeGarantia = ClienteDeGarantia(
        clienteId = 7,
        nombre = nombre,
        ventaId = 21,
        estadoDeCuenta = EstadoDeCuenta.LIQUIDADA,
        calle = "Av. Reforma",
        ciudad = "CDMX"
    )

    /** El ViewModel y las piezas que la prueba necesita para afirmar sobre él. */
    private data class AbrirMundo(
        val vm: AbrirFolioViewModel,
        val folios: FoliosFalsos,
        val cola: ColaFalsa
    )
}
