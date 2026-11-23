package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.feature.garantias.application.EstadoDeLaLectura
import com.example.msp_app.feature.garantias.application.LeerDetalleDeFolio
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.FotoPendienteDeGarantia
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
 * El detalle de un folio con su línea de tiempo (prueba 7 del brief).
 *
 * Lo que estas pruebas protegen es la promesa de D5: el detalle **nunca** deja
 * la pantalla en blanco. Un folio sin señal, uno que todavía no salió, uno
 * rechazado y uno que no existe se pintan igual, con sus pendientes locales.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleDeFolioViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Suppress("LongParameterList") // cada pieza del mundo es un eje a variar por prueba.
    private fun viewModel(
        folioRef: String = FOLIO,
        foliosIniciales: List<FolioSeguido> = emptyList(),
        comandos: List<ComandoDeGarantia> = emptyList(),
        fotosIniciales: List<FotoPendienteDeGarantia> = emptyList(),
        detalle: DetalleDeGarantia? = null,
        sinSenal: Boolean = false,
        fallaAlLeer: Boolean = false
    ): DetalleDeFolioViewModel {
        val folios = FoliosFalsos(foliosIniciales.toMutableList()).apply { falla = fallaAlLeer }
        val cola = ColaFalsa(comandos.toMutableList())
        val fotos = FotosFalsas(fotosIniciales.toMutableList())
        return DetalleDeFolioViewModel(
            leerDetalle = LeerDetalleDeFolio(
                folios = folios,
                cola = cola,
                fotos = fotos,
                remotas = RemotasFalsas(detalle = detalle, sinSenal = sinSenal)
            ),
            savedStateHandle = SavedStateHandle(mapOf(GarantiasV2Rutas.ARG_FOLIO to folioRef)),
            io = testDispatcher
        )
    }

    @Test
    fun `arranca cargando y luego pinta el detalle disponible`() = runTest(testDispatcher) {
        val vm = viewModel(
            foliosIniciales = listOf(
                GarantiasFixtures.folio(
                    folioId = "folio-servidor-1",
                    estadoLocal = EstadoLocalDeFolio.ABIERTO
                )
            ),
            comandos = listOf(
                GarantiasFixtures.comando(
                    id = 1,
                    articuloRef = "articulo-local-1",
                    articuloId = "articulo-servidor-1"
                )
            ),
            detalle = GarantiasFixtures.detalle(
                articulos = listOf(GarantiasFixtures.articulo()),
                eventos = listOf(GarantiasFixtures.evento(id = "evento-1"))
            )
        )

        vm.cargar()
        assertTrue(vm.state.value.cargando)

        advanceUntilIdle()

        val estado = vm.state.value
        assertFalse(estado.cargando)
        val pintado = checkNotNull(estado.detalle)
        assertEquals(EstadoDeLaLectura.DISPONIBLE, pintado.estado)
        assertEquals(1, checkNotNull(pintado.detalle).eventos.size)
        assertTrue(pintado.articulos.single().sePuedeRecolectar)
    }

    @Test
    fun `sin senal con pendientes no deja la pantalla en blanco`() = runTest(testDispatcher) {
        val vm = viewModel(
            foliosIniciales = listOf(
                GarantiasFixtures.folio(
                    folioId = "folio-servidor-1",
                    estadoLocal = EstadoLocalDeFolio.ABIERTO
                )
            ),
            comandos = listOf(GarantiasFixtures.comando(id = 1), GarantiasFixtures.comando(id = 2)),
            fotosIniciales = listOf(GarantiasFixtures.fotoPendiente()),
            sinSenal = true
        )

        vm.cargar()
        advanceUntilIdle()

        val pintado = checkNotNull(vm.state.value.detalle)
        assertEquals(EstadoDeLaLectura.SIN_SENAL, pintado.estado)
        assertEquals(2, pintado.comandosPendientes)
        assertEquals(1, pintado.fotosPendientes)
        assertTrue(pintado.tienePendientes)
        assertNull(pintado.detalle)
    }

    @Test
    fun `un folio rechazado se pinta con su motivo`() = runTest(testDispatcher) {
        val vm = viewModel(
            foliosIniciales = listOf(
                GarantiasFixtures.folio(
                    estadoLocal = EstadoLocalDeFolio.RECHAZADO,
                    ultimoError = "Sin permiso"
                )
            )
        )

        vm.cargar()
        advanceUntilIdle()

        val pintado = checkNotNull(vm.state.value.detalle)
        assertEquals(EstadoDeLaLectura.RECHAZADO, pintado.estado)
        assertEquals("Sin permiso", pintado.motivo)
    }

    @Test
    fun `un folio sin id remoto esta pendiente de envio`() = runTest(testDispatcher) {
        val vm = viewModel(
            foliosIniciales = listOf(
                GarantiasFixtures.folio(estadoLocal = EstadoLocalDeFolio.PENDIENTE_ENVIO)
            )
        )

        vm.cargar()
        advanceUntilIdle()

        val pintado = checkNotNull(vm.state.value.detalle)
        assertEquals(EstadoDeLaLectura.PENDIENTE_ENVIO, pintado.estado)
        assertNull(pintado.detalle)
    }

    @Test
    fun `una referencia local que no existe se pinta como no encontrado`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()

        vm.cargar()
        advanceUntilIdle()

        val pintado = checkNotNull(vm.state.value.detalle)
        assertEquals(EstadoDeLaLectura.NO_ENCONTRADO, pintado.estado)
    }

    @Test
    fun `una base que no abre deja el detalle en fallo`() = runTest(testDispatcher) {
        val vm = viewModel(fallaAlLeer = true)

        vm.cargar()
        advanceUntilIdle()

        assertTrue(vm.state.value.fallo)
        assertNull(vm.state.value.detalle)
    }

    private companion object {
        const val FOLIO = "folio-local-1"
    }
}
