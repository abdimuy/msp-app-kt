package com.example.msp_app.feature.ubicacion.ui

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.AHORA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.MapasFalsos
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PUERTA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PuntosFalsos
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.desplazado
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.medicion
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.rutaCompartida
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.VentanaDelFiltro
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val reloj = AppClock { AHORA }

    @Before fun antes() = Dispatchers.setMain(dispatcher)

    @After fun despues() = Dispatchers.resetMain()

    @Test
    fun `carga los lugares y reparte el titulo`() = runTest(dispatcher) {
        val vm = UbicacionViewModel(
            PuntosFalsos(
                (0 until 6).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) }
            ),
            MapasFalsos(),
            reloj
        )
        vm.cargar(clienteId = 1, direccion = "Calle 5 de Mayo 12", pagoResaltado = null)
        runCurrent()
        val state = vm.state.value
        assertFalse(state.cargando)
        assertEquals(1, state.mapa.todos.size)
        assertEquals(ClaseDeLugar.DONDE_MAS_PAGA, state.mapa.todos.single().clase)
        assertTrue(state.mapa.principal != null)
    }

    @Test
    fun `un punto compartido deja al cliente sin puerta y se anuncia`() = runTest(dispatcher) {
        val compartido = desplazado(PUERTA, 500.0)
        val vm = UbicacionViewModel(
            PuntosFalsos(
                mediciones = (0 until 8).map {
                    medicion(desplazado(compartido, it * 0.5), diasAtras = it.toLong())
                },
                ruta = rutaCompartida(compartido, clientes = 40, cobradores = 6)
            ),
            MapasFalsos(),
            reloj
        )
        vm.cargar(1, "", null)
        runCurrent()
        assertTrue(vm.state.value.mapa.principal == null)
        assertEquals(ClaseDeLugar.COMPARTIDO, vm.state.value.mapa.todos.single().clase)
        assertEquals(1, vm.state.value.mapa.todos.size) // se dibuja, no se esconde
    }

    @Test
    fun `la mudanza se anuncia SIN que nadie filtre`() = runTest(dispatcher) {
        val viejo = (0 until 5).map {
            medicion(desplazado(PUERTA, it * 2.0), diasAtras = 700L + it, id = "v$it")
        }
        val nuevo = (0 until 5).map {
            medicion(
                desplazado(desplazado(PUERTA, 900.0), it * 2.0),
                diasAtras = 20L + it,
                id = "n$it"
            )
        }
        val vm = UbicacionViewModel(PuntosFalsos(viejo + nuevo), MapasFalsos(), reloj)
        vm.cargar(1, "", null)
        runCurrent()
        assertTrue("la mudanza no salió sola", vm.state.value.mapa.cambioDeLugar)
        assertEquals(
            "el filtro por omisión no debe recortar nada",
            VentanaDelFiltro.TODO,
            vm.state.value.filtro.ventana
        )
    }

    @Test
    fun `el filtro de tiempo recorta sin volver a leer la ruta`() = runTest(dispatcher) {
        val puerto = PuntosFalsos(
            (0 until 4).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) } +
                (0 until 4).map {
                    medicion(desplazado(desplazado(PUERTA, 900.0), it * 2.0), diasAtras = 500L + it, id = "viejo$it")
                }
        )
        val vm = UbicacionViewModel(puerto, MapasFalsos(), reloj)
        vm.cargar(1, "", null)
        runCurrent()
        assertEquals(2, vm.state.value.mapa.todos.size)

        vm.cambiarFiltro(FiltroDeLugares(ventana = VentanaDelFiltro.TRES_MESES))
        runCurrent()
        assertEquals("los viejos debían salir", 1, vm.state.value.mapa.todos.size)
        assertEquals(
            "el índice de compartidos se releyó al filtrar",
            1,
            puerto.vecesQueLeyoLaRuta
        )
    }

    @Test
    fun `ver cada punto NO cambia que lugar es cual`() = runTest(dispatcher) {
        // "Ver cada punto" dibuja cada medición; la clasificación sigue saliendo
        // de los 30 m, así que el principal no se pierde al encenderlo.
        val vm = UbicacionViewModel(
            PuntosFalsos(
                (0 until 6).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) }
            ),
            MapasFalsos(),
            reloj
        )
        vm.cargar(1, "", null)
        runCurrent()
        vm.cambiarFiltro(FiltroDeLugares(sinAgrupar = true))
        runCurrent()
        assertEquals(1, vm.state.value.mapa.todos.size)
        assertEquals(ClaseDeLugar.DONDE_MAS_PAGA, vm.state.value.mapa.todos.single().clase)
        assertEquals(6, vm.state.value.mapa.todos.single().lugar.mediciones.size)
    }

    @Test
    fun `las visitas entran APAGADAS y se encienden con el filtro`() = runTest(dispatcher) {
        val visita = com.example.msp_app.feature.ubicacion.domain.VisitaMedida(
            id = "v1",
            punto = PUERTA,
            fecha = AHORA,
            cobrador = "Rocío Manzano",
            tipo = "No se encontraba",
            esPromesa = false
        )
        val vm = UbicacionViewModel(
            PuntosFalsos(listOf(medicion(PUERTA)), visitas = listOf(visita)),
            MapasFalsos(),
            reloj
        )
        vm.cargar(1, "", null)
        runCurrent()
        assertEquals(1, vm.state.value.totalVisitas)
        assertTrue("las visitas salieron sin filtro", vm.state.value.visitas.isEmpty())
        vm.cambiarFiltro(FiltroDeLugares(verVisitas = true))
        runCurrent()
        assertEquals(listOf("v1"), vm.state.value.visitas.map { it.id })
    }

    @Test
    fun `el filtro por cobrador y por venta recorta`() = runTest(dispatcher) {
        val vm = UbicacionViewModel(
            PuntosFalsos(
                listOf(
                    medicion(PUERTA, id = "a", ventaId = 10, cobrador = "Rocío Manzano"),
                    medicion(
                        desplazado(PUERTA, 800.0),
                        id = "b",
                        ventaId = 20,
                        cobrador = "Elías Mota"
                    )
                )
            ),
            MapasFalsos(),
            reloj
        )
        vm.cargar(1, "", null)
        runCurrent()
        assertEquals(listOf(10, 20), vm.state.value.ventasDisponibles)
        assertEquals(
            setOf("Elías Mota", "Rocío Manzano"),
            vm.state.value.cobradoresDisponibles.toSet()
        )

        vm.cambiarFiltro(FiltroDeLugares(ventaId = 10))
        runCurrent()
        assertEquals(1, vm.state.value.mapa.todos.size)

        vm.cambiarFiltro(FiltroDeLugares(cobrador = "Elías Mota"))
        runCurrent()
        assertEquals(1, vm.state.value.mapa.todos.size)
        assertEquals("b", vm.state.value.mapa.todos.single().lugar.mediciones.single().pagoId)
    }

    @Test
    fun `como llegar sale al CENTRO del grupo, no a la ultima medicion`() = runTest(dispatcher) {
        // El centro promedia las mediciones de domicilio, así que está más cerca
        // de la puerta que cualquiera de ellas por separado — que es el punto
        // entero de agrupar. Mandar la última sería tirar ese trabajo.
        val mapas = MapasFalsos()
        val mediciones = (0 until 6).map {
            medicion(desplazado(PUERTA, it * 4.0), diasAtras = it.toLong())
        }
        val vm = UbicacionViewModel(PuntosFalsos(mediciones), mapas, reloj)
        vm.cargar(1, "Av. 5 Poniente 1204", null)
        runCurrent()
        val lugar = vm.state.value.mapa.todos.single().lugar
        vm.comoLlegar(lugar, "Av. 5 Poniente 1204")
        runCurrent()
        val destino = mapas.ultimoDestino
        assertTrue("no se abrió la app de mapas", destino != null)
        assertEquals(lugar.centro.lat, destino!!.first, 1e-9)
        assertEquals(lugar.centro.lon, destino.second, 1e-9)
        assertEquals("Av. 5 Poniente 1204", destino.third)
        // Y el centro NO coincide con la medición más reciente ni con la más
        // vieja: si coincidiera, la prueba pasaría por accidente.
        assertTrue(
            "el centro cayó justo sobre una medición: la prueba no distingue",
            mediciones.none { kotlin.math.abs(it.punto.lat - lugar.centro.lat) < 1e-9 }
        )
    }

    @Test
    fun `el detalle de venta resuelve el cliente por la venta`() = runTest(dispatcher) {
        // Este destino conoce la cuenta y no al cliente. Si la resolución se
        // rompiera, la pantalla abriría vacía en vez de fallar ruidosamente.
        val vm = UbicacionViewModel(
            PuntosFalsos(
                (0 until 4).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) }
            ),
            MapasFalsos(),
            reloj
        )
        vm.cargar(clienteId = 0, direccion = "", pagoResaltado = null, ventaId = 4477)
        runCurrent()
        assertEquals(1, vm.state.value.mapa.todos.size)
    }

    @Test
    fun `un cliente sin coordenadas no inventa nada`() = runTest(dispatcher) {
        val vm = UbicacionViewModel(PuntosFalsos(emptyList()), MapasFalsos(), reloj)
        vm.cargar(1, "Calle 5 de Mayo 12", null)
        runCurrent()
        assertTrue(vm.state.value.sinNingunPunto)
        assertTrue(vm.state.value.mapa.todos.isEmpty())
    }
}

/** Un `AppClock` de prueba, sin MockK: la interfaz tiene un solo método. */
private fun AppClock(bloque: () -> Instant): AppClock = object : AppClock {
    override fun now(): Instant = bloque()
}
