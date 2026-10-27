package com.example.msp_app.features.cities

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.cache.CitiesCache
import com.example.msp_app.data.models.city.City
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El selector de ciudad se apoya en que este ViewModel distinga cuatro
 * desenlaces, porque cada uno lleva a la pantalla a un lugar distinto: catálogo
 * disponible (elegir), catálogo cacheado (elegir sin red), catálogo vacío
 * (ofrecer texto libre) y sin datos (ofrecer texto libre y dejar reintentar).
 * Confundir el vacío con el error es lo que dejaría al vendedor sin salida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CitiesViewModelTest : RobolectricTestBase() {

    /**
     * Doble hecho a mano —sin MockK, como el resto del repo— aprovechando que
     * [CitiesRepository] es `open` y su fábrica de API es lazy: el
     * `V2ApiProvider.create` por omisión nunca se evalúa aquí.
     */
    private class FakeCitiesRepository(
        var resultado: () -> Result<List<City>>
    ) : CitiesRepository() {
        // StateFlow y no un Int suelto: getCities corre en Dispatchers.IO y la
        // prueba lo lee desde otro hilo, así que tiene que poder esperarlo.
        private val _llamadas = MutableStateFlow(0)
        val llamadasFlow: StateFlow<Int> = _llamadas.asStateFlow()
        val llamadas: Int get() = _llamadas.value

        override suspend fun getCities(): Result<List<City>> {
            _llamadas.update { it + 1 }
            return resultado()
        }
    }

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    private lateinit var cache: CitiesCache

    @Before
    fun limpiarCache() {
        // El cache vive en un archivo bajo filesDir y se comparte entre pruebas
        // del mismo JVM; sin esto una prueba heredaría el catálogo de la anterior.
        cache = CitiesCache(app())
        runBlocking { cache.clear() }
    }

    private fun viewModel(repositorio: CitiesRepository) =
        CitiesViewModel(app(), repositorio, cache)

    private suspend fun CitiesViewModel.esperarDesenlace(): ResultState<List<City>> = state.first {
        it is ResultState.Success || it is ResultState.Offline || it is ResultState.Error
    }

    @Test
    fun `carga el catalogo y lo deja disponible`() = runTest {
        val vm = viewModel(FakeCitiesRepository { Result.success(CATALOGO) })

        vm.fetch()
        val estado = vm.esperarDesenlace()

        assertTrue("estado inesperado: $estado", estado is ResultState.Success)
        assertEquals(CATALOGO, estado.dataOrNull())
        assertEquals(false, vm.isOfflineMode.value)
    }

    @Test
    fun `sin red cae al cache y se marca offline`() = runTest {
        // Primero se llena el cache con una carga buena…
        val repositorio = FakeCitiesRepository { Result.success(CATALOGO) }
        viewModel(repositorio).also { it.fetch() }.esperarDesenlace()

        // …y luego se simula la red caída: el catálogo tiene que seguir ahí, que es
        // lo que permite capturar del catálogo en una colonia sin cobertura.
        repositorio.resultado = { Result.failure(IOException("sin red")) }
        val vm = viewModel(repositorio)
        vm.fetch()
        val estado = vm.esperarDesenlace()

        assertTrue("estado inesperado: $estado", estado is ResultState.Offline)
        assertEquals(CATALOGO, estado.dataOrNull())
        assertEquals(true, vm.isOfflineMode.value)
    }

    @Test
    fun `error de red sin cache deja estado de error`() = runTest {
        val vm = viewModel(FakeCitiesRepository { Result.failure(IOException("sin red")) })

        vm.fetch()
        val estado = vm.esperarDesenlace()

        assertTrue("estado inesperado: $estado", estado is ResultState.Error)
        assertNull(estado.dataOrNull())
    }

    @Test
    fun `un catalogo vacio es exito con lista vacia`() = runTest {
        // No es un error: la pantalla debe ofrecer el texto libre, no reintentar.
        val vm = viewModel(FakeCitiesRepository { Result.success(emptyList()) })

        vm.fetch()
        val estado = vm.esperarDesenlace()

        assertTrue("estado inesperado: $estado", estado is ResultState.Success)
        assertEquals(emptyList<City>(), estado.dataOrNull())
    }

    @Test
    fun `refresh vuelve a pedir el catalogo`() = runTest {
        val repositorio = FakeCitiesRepository { Result.success(CATALOGO) }
        val vm = viewModel(repositorio)
        vm.fetch()
        vm.esperarDesenlace()
        val trasLaCarga = repositorio.llamadas

        vm.refresh()
        // refresh() no pasa por Loading: el estado se queda en el Success de la
        // carga anterior, así que esperar un Success volvía al instante, antes de
        // que la petición —que corre en Dispatchers.IO— llegara al repositorio, y
        // la prueba fallaba según el planificador. Se espera la llamada misma; si
        // refresh no la hace, runTest corta por tiempo y la prueba falla.
        repositorio.llamadasFlow.first { it > trasLaCarga }
        // Y que el refresh termine (cache.save incluido) antes de que el @Before
        // de la siguiente prueba limpie el mismo archivo de cache.
        vm.isRefreshing.first { !it }

        assertTrue(
            "refresh no volvió a pedir el catálogo",
            repositorio.llamadas > trasLaCarga
        )
    }

    @Test
    fun `findByName ignora acentos y mayusculas`() = runTest {
        val vm = viewModel(FakeCitiesRepository { Result.success(CATALOGO) })
        vm.fetch()
        vm.esperarDesenlace()

        // Mismo criterio de normalización que el servidor: si el texto capturado
        // resuelve aquí, resuelve allá.
        val encontrada = vm.findByName("  tehuacán ")

        assertEquals(338, encontrada?.ciudadId)
        // Y la fila trae su estado — nunca se busca el estado por separado.
        assertEquals("PUEBLA", encontrada?.estado)
    }

    @Test
    fun `findByName no inventa coincidencias`() = runTest {
        val vm = viewModel(FakeCitiesRepository { Result.success(CATALOGO) })
        vm.fetch()
        vm.esperarDesenlace()

        assertNull(vm.findByName("SAN MIGUEL DE LA NADA"))
        assertNull(vm.findByName(""))
    }

    @Test
    fun `el cache sobrevive a otra instancia del viewmodel`() = runTest {
        viewModel(FakeCitiesRepository { Result.success(CATALOGO) }).also { it.fetch() }
            .esperarDesenlace()

        val recargadas = CitiesCache(app()).searchByName("orizaba")

        assertEquals(listOf("ORIZABA"), recargadas.map { it.ciudad })
    }

    private companion object {
        // Las tres ciudades cubren los tres estados que el catálogo real abarca:
        // es justo la razón por la que el estado no se elige aparte.
        val CATALOGO = listOf(
            City(ciudadId = 338, ciudad = "TEHUACAN", estadoId = 337, estado = "PUEBLA"),
            City(ciudadId = 11523, ciudad = "OAXACA", estadoId = 11522, estado = "OAXACA"),
            City(ciudadId = 11751, ciudad = "ORIZABA", estadoId = 11750, estado = "VERACRUZ")
        )
    }
}
