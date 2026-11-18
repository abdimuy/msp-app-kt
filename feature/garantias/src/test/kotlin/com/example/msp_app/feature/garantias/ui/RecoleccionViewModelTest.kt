package com.example.msp_app.feature.garantias.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.garantias.application.FalloDeCaptura
import com.example.msp_app.feature.garantias.application.LeerDetalleDeFolio
import com.example.msp_app.feature.garantias.application.RecolectarGarantia
import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.EtapaYOrigen
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.FotoDeCaptura
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
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
 * La recolección de un artículo (prueba 7 del brief).
 *
 * Lo que estas pruebas protegen: que las acciones salgan del catálogo del
 * servidor y no de una tabla paralela, que el respaldo visible exista sólo en
 * las etapas que el servidor admite, y que la recolección se guarde **igual**
 * sin GPS, con sus fotos y su clave generada una sola vez.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecoleccionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Suppress("LongParameterList") // el mundo de la prueba: cada pieza es un eje a variar.
    private fun viewModel(
        foliosIniciales: List<FolioSeguido> = listOf(folioAbierto()),
        comandos: List<ComandoDeGarantia> = emptyList(),
        detalle: DetalleDeGarantia? = null,
        catalogo: CatalogoDeGarantias = CatalogoDeGarantias.SIN_CATALOGO,
        ubicacion: Ubicacion? = Ubicacion(latitud = 19.4326, longitud = -99.1332),
        foto: FotoDeCaptura? = FotoDeCaptura("foto-1", "fotos/foto-1.jpg", "image/jpeg")
    ): RecoleccionMundo {
        val folios = FoliosFalsos(foliosIniciales.toMutableList())
        val fotos = FotosFalsas()
        val cola = ColaFalsa(comandos.toMutableList(), fotos)
        val archivos = ArchivosFalsos()
        val camara = CamaraFalsa(foto = foto)
        val vm = RecoleccionViewModel(
            leerDetalle = LeerDetalleDeFolio(
                folios = folios,
                cola = cola,
                fotos = fotos,
                remotas = RemotasFalsas(detalle = detalle)
            ),
            catalogo = CatalogoFalso(catalogo = catalogo),
            recolectar = RecolectarGarantia(
                folios = folios,
                cola = cola,
                cuerpos = CuerposFalsos(),
                claves = ClavesFalsas(),
                encolado = EncoladoFalso(),
                reloj = FakeClock(GarantiasFixtures.AHORA)
            ),
            camara = camara,
            ubicacionPort = UbicacionFalsa(ubicacion = ubicacion),
            archivos = archivos,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    GarantiasV2Rutas.ARG_FOLIO to FOLIO,
                    GarantiasV2Rutas.ARG_ARTICULO to ARTICULO
                )
            ),
            io = testDispatcher
        )
        return RecoleccionMundo(vm = vm, cola = cola, fotos = fotos, archivos = archivos)
    }

    @Test
    fun `sin catalogo ofrece el respaldo y lo preselecciona`() = runTest(testDispatcher) {
        // El folio por defecto es de piso: en `registrado` el respaldo lo manda a
        // revisión, no a recolección (el tronco de cliente no aplica).
        val mundo = viewModel()

        mundo.vm.cargar()
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertFalse(estado.cargando)
        assertEquals("en_revision", estado.acciones.single().etapaDestino)
        assertEquals("en_revision", estado.accionElegida)
        assertTrue(estado.catalogoVacio)
    }

    @Test
    fun `un folio de cliente en registrado ofrece programar la recoleccion`() =
        runTest(testDispatcher) {
            val mundo = viewModel(
                foliosIniciales = listOf(folioAbierto(origen = OrigenDeFolio.CLIENTE))
            )

            mundo.vm.cargar()
            advanceUntilIdle()

            val estado = mundo.vm.state.value
            assertEquals("pendiente_recoleccion", estado.acciones.single().etapaDestino)
            assertEquals("pendiente_recoleccion", estado.accionElegida)
        }

    @Test
    fun `con catalogo solo se ofrece lo que publica el servidor`() = runTest(testDispatcher) {
        val catalogo = CatalogoDeGarantias(
            porEtapaYOrigen = mapOf(
                EtapaYOrigen("registrado", OrigenDeFolio.PISO) to listOf(
                    AccionDeGarantia(etapaDestino = "enviar_proveedor", etiqueta = "Enviar a proveedor")
                )
            )
        )
        val mundo = viewModel(catalogo = catalogo)

        mundo.vm.cargar()
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertEquals("enviar_proveedor", estado.acciones.single().etapaDestino)
        assertEquals("enviar_proveedor", estado.accionElegida)
        assertFalse(estado.catalogoVacio)
    }

    @Test
    fun `una etapa ya recolectada no ofrece ninguna accion`() = runTest(testDispatcher) {
        val mundo = viewModel(
            comandos = listOf(
                GarantiasFixtures.comando(
                    id = 1,
                    articuloRef = ARTICULO,
                    articuloId = "articulo-servidor-1"
                )
            ),
            detalle = GarantiasFixtures.detalle(
                articulos = listOf(GarantiasFixtures.articulo(etapa = "recolectado"))
            )
        )

        mundo.vm.cargar()
        advanceUntilIdle()

        val estado = mundo.vm.state.value
        assertEquals("recolectado", estado.etapa)
        assertTrue(estado.acciones.isEmpty())
        assertNull(estado.accionElegida)
        assertFalse(estado.sePuedeGuardar)
    }

    @Test
    fun `guardar sin accion elegida no encola`() = runTest(testDispatcher) {
        val mundo = viewModel(
            comandos = listOf(
                GarantiasFixtures.comando(
                    id = 1,
                    articuloRef = ARTICULO,
                    articuloId = "articulo-servidor-1"
                )
            ),
            detalle = GarantiasFixtures.detalle(
                articulos = listOf(GarantiasFixtures.articulo(etapa = "recolectado"))
            )
        )

        mundo.vm.cargar()
        advanceUntilIdle()
        mundo.vm.guardar()
        advanceUntilIdle()

        assertEquals(FalloDeCaptura.SIN_ACCION, mundo.vm.state.value.fallo)
        assertTrue(mundo.cola.encolados.isEmpty())
    }

    @Test
    fun `guardar encola la recoleccion con sus fotos`() = runTest(testDispatcher) {
        val mundo = viewModel()

        mundo.vm.cargar()
        advanceUntilIdle()

        mundo.vm.tomarFoto()
        assertNotNull("la ruta publica el destino de la camara", mundo.vm.state.value.destinoDeFoto)
        mundo.vm.fotoTomada()
        advanceUntilIdle()
        assertEquals(1, mundo.vm.state.value.fotos.size)

        mundo.vm.guardar()
        advanceUntilIdle()

        assertTrue(mundo.vm.state.value.guardado)
        assertEquals(1, mundo.cola.encolados.size)
        assertEquals(1, mundo.fotos.guardadas.size)
        assertEquals("foto-1", mundo.fotos.guardadas.single().clave)
    }

    @Test
    fun `sin ubicacion la recoleccion se guarda igual`() = runTest(testDispatcher) {
        val mundo = viewModel(ubicacion = null)

        mundo.vm.cargar()
        advanceUntilIdle()
        mundo.vm.guardar()
        advanceUntilIdle()

        assertNull(mundo.vm.state.value.ubicacion)
        assertTrue(mundo.vm.state.value.guardado)
        assertEquals(1, mundo.cola.encolados.size)
    }

    @Test
    fun `una foto cancelada no se suma y borra el crudo`() = runTest(testDispatcher) {
        val mundo = viewModel()

        mundo.vm.cargar()
        advanceUntilIdle()
        mundo.vm.tomarFoto()
        mundo.vm.fotoCancelada()
        advanceUntilIdle()

        assertTrue(mundo.vm.state.value.fotos.isEmpty())
        assertEquals(listOf("fotos/foto-1.jpg"), mundo.archivos.borrados)
    }

    @Test
    fun `una foto que la camara no pudo guardar se reporta`() = runTest(testDispatcher) {
        val mundo = viewModel(foto = null)

        mundo.vm.cargar()
        advanceUntilIdle()
        mundo.vm.tomarFoto()
        mundo.vm.fotoTomada()
        advanceUntilIdle()

        assertTrue(mundo.vm.state.value.fotos.isEmpty())
        assertEquals(FalloDeCaptura.FOTO_NO_GUARDADA, mundo.vm.state.value.fallo)
    }

    private fun folioAbierto(origen: OrigenDeFolio = OrigenDeFolio.PISO): FolioSeguido =
        GarantiasFixtures.folio(
            id = FOLIO,
            folioId = "folio-servidor-1",
            origen = origen,
            estadoLocal = EstadoLocalDeFolio.ABIERTO
        )

    /** El ViewModel y las piezas que la prueba necesita para afirmar sobre él. */
    private data class RecoleccionMundo(
        val vm: RecoleccionViewModel,
        val cola: ColaFalsa,
        val fotos: FotosFalsas,
        val archivos: ArchivosFalsos
    )

    private companion object {
        const val FOLIO = "folio-local-1"
        const val ARTICULO = "articulo-local-1"
    }
}
