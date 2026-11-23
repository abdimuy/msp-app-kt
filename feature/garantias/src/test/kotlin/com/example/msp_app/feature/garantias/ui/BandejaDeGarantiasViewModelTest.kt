package com.example.msp_app.feature.garantias.ui

import com.example.msp_app.feature.garantias.application.LeerMisFolios
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La bandeja de "mis folios" (prueba 7 del brief).
 *
 * Lo que estas pruebas protegen: que la lectura local no deje la pantalla en
 * blanco ni mienta con una lista vacía, que un folio sin enviar diga cuánto le
 * falta y que un rechazo se vea con su motivo. Nunca se toca la red: la bandeja
 * es la memoria del teléfono.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BandejaDeGarantiasViewModelTest {

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
        foliosIniciales: List<FolioSeguido> = emptyList(),
        comandos: List<ComandoDeGarantia> = emptyList(),
        fotosIniciales: List<FotoPendienteDeGarantia> = emptyList(),
        fallaAlLeer: Boolean = false
    ): BandejaDeGarantiasViewModel {
        val folios = FoliosFalsos(foliosIniciales.toMutableList()).apply { falla = fallaAlLeer }
        val cola = ColaFalsa(comandos.toMutableList())
        val fotos = FotosFalsas(fotosIniciales.toMutableList())
        return BandejaDeGarantiasViewModel(
            leerMisFolios = LeerMisFolios(folios = folios, cola = cola, fotos = fotos),
            io = testDispatcher
        )
    }

    @Test
    fun `arranca cargando y luego queda con los folios`() = runTest(testDispatcher) {
        val vm = viewModel(foliosIniciales = listOf(GarantiasFixtures.folio()))

        vm.cargar()
        assertTrue("la primera lectura pinta Cargando", vm.state.value.cargando)

        advanceUntilIdle()

        val estado = vm.state.value
        assertFalse(estado.cargando)
        assertFalse(estado.fallo)
        assertEquals(1, estado.folios.size)
    }

    @Test
    fun `una base que no abre deja la bandeja en fallo`() = runTest(testDispatcher) {
        val vm = viewModel(fallaAlLeer = true)

        vm.cargar()
        advanceUntilIdle()

        assertTrue(vm.state.value.fallo)
        assertTrue(vm.state.value.folios.isEmpty())
    }

    @Test
    fun `un folio sin enviar cuenta sus comandos y fotos pendientes`() = runTest(testDispatcher) {
        val vm = viewModel(
            foliosIniciales = listOf(GarantiasFixtures.folio()),
            comandos = listOf(GarantiasFixtures.comando(id = 1), GarantiasFixtures.comando(id = 2)),
            fotosIniciales = listOf(GarantiasFixtures.fotoPendiente())
        )

        vm.cargar()
        advanceUntilIdle()

        val folio = vm.state.value.folios.single()
        assertEquals(2, folio.comandosPendientes)
        assertEquals(1, folio.fotosPendientes)
        assertTrue(folio.tienePendientes)
        assertTrue(folio.pendienteDeEnvio)
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

        val folio = vm.state.value.folios.single()
        assertTrue(folio.rechazado)
        assertEquals("Sin permiso", folio.ultimoError)
    }
}
