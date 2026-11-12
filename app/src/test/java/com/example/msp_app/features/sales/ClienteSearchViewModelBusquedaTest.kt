package com.example.msp_app.features.sales

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.clientes.ResultadoDeClientes
import com.example.msp_app.core.database.dao.ClienteDao
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.repository.ClienteRepository
import com.example.msp_app.data.repository.FakeClientesApi
import com.example.msp_app.data.repository.FakeClientesApi.Companion.cliente
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-157: la búsqueda NO corre en el hilo principal, una consulta vieja se cancela al teclear
 * otra y el indicador "buscando" no lo apaga una búsqueda que ya no es la vigente.
 */
class ClienteSearchViewModelBusquedaTest : RoomTestBase() {

    private val api = FakeClientesApi()
    private val hilos = CopyOnWriteArrayList<String>()
    private val llamadas = AtomicInteger()
    private val cancelaciones = AtomicInteger()

    private val unCliente =
        ResultadoDeClientes(listOf(ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz")), 1)

    private fun vm(
        dispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
        buscar: suspend (String) -> ResultadoDeClientes
    ): ClienteSearchViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ClienteRepository(
            dataSource = ClienteDataSource(app),
            api = api,
            avisar = { _, _, _ -> },
            guardarFechaDeSync = {},
            leerFechaDeSync = { 0L },
            ahora = { 0L }
        )
        return ClienteSearchViewModel(app, repo, buscar = buscar, dispatcherDeBusqueda = dispatcher)
    }

    private fun TestScope.hasta(condicion: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (condicion()) return
            Thread.sleep(10)
        }
        runCurrent()
        assertTrue("la condición no se cumplió en 5 s", condicion())
    }

    @Test fun `la busqueda corre en el despachador de busqueda y no en el principal`() = runTest {
        val vm = vm { _ ->
            hilos += Thread.currentThread().name
            unCliente
        }
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        hasta { hilos.isNotEmpty() }
        assertTrue("hilos: $hilos", hilos.all { it.startsWith("DefaultDispatcher") })
    }

    @Test fun `con otro despachador la busqueda no avanza hasta que ese despachador corre`() = runTest {
        val planificadorAparte = TestCoroutineScheduler()
        val vm = vm(StandardTestDispatcher(planificadorAparte)) { _ ->
            llamadas.incrementAndGet()
            unCliente
        }
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        runCurrent()
        assertEquals("el principal no debe ejecutar la búsqueda", 0, llamadas.get())
        planificadorAparte.advanceUntilIdle()
        hasta { vm.resultado.value.exactos.isNotEmpty() }
        assertEquals(1, llamadas.get())
    }

    @Test fun `teclear otra consulta cancela la busqueda vieja`() = runTest {
        val colgada = CompletableDeferred<Unit>()
        val vm = vm { texto ->
            llamadas.incrementAndGet()
            if (texto == "ana") {
                try {
                    colgada.await()
                } catch (e: CancellationException) {
                    cancelaciones.incrementAndGet()
                    throw e
                }
            }
            unCliente
        }
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        hasta { llamadas.get() == 1 }
        vm.onQueryChange("beto")
        advanceTimeBy(301)
        hasta { cancelaciones.get() == 1 && vm.resultado.value.exactos.isNotEmpty() }
        assertFalse(vm.isSearching.value)
    }

    @Test fun `la busqueda por reintento no deja prendida ni apaga mal el indicador de la otra`() =
        runTest {
            val colgada = CompletableDeferred<Unit>()
            api.respuesta = listOf(cliente(10, "ANA LUNA"))
            val vm = vm { _ ->
                if (llamadas.incrementAndGet() == 1) {
                    try {
                        colgada.await()
                    } catch (e: CancellationException) {
                        cancelaciones.incrementAndGet()
                        throw e
                    }
                }
                unCliente
            }
            vm.onQueryChange("ana")
            advanceTimeBy(301)
            hasta { llamadas.get() == 1 && vm.isSearching.value }
            vm.syncFromServer() // al terminar bien se repite la búsqueda en curso
            hasta { llamadas.get() == 2 && !vm.isSyncing.value }
            hasta { !vm.isSearching.value }
            assertEquals(
                "la búsqueda vieja debe cancelarse, no seguir corriendo",
                1,
                cancelaciones.get()
            )
            assertEquals(1, vm.resultado.value.exactos.size)
        }

    @Test fun `si la busqueda falla hay un error propio y no un resultado vacio que parezca sin clientes`() =
        runTest {
            val vm = vm { _ -> throw IllegalStateException("room cerrado") }
            vm.onQueryChange("ana")
            advanceTimeBy(301)
            hasta { vm.errorDeBusqueda.value }
            assertFalse(vm.isSearching.value)
        }

    @Test fun `tras un error una busqueda buena limpia el error`() = runTest {
        val vm = vm { texto -> if (texto == "ana") throw IllegalStateException("x") else unCliente }
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        hasta { vm.errorDeBusqueda.value }
        vm.onQueryChange("beto")
        advanceTimeBy(301)
        hasta { !vm.errorDeBusqueda.value && vm.resultado.value.exactos.isNotEmpty() }
    }

    @Test fun `si falla leer el padron hay error y el conteo no se inventa como cero`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val daoRoto = java.lang.reflect.Proxy.newProxyInstance(
            ClienteDao::class.java.classLoader,
            arrayOf(ClienteDao::class.java)
        ) { _, _, _ -> throw IllegalStateException("room cerrado") } as ClienteDao
        val repo = ClienteRepository(
            dataSource = ClienteDataSource(daoRoto),
            api = api,
            avisar = { _, _, _ -> },
            guardarFechaDeSync = {},
            leerFechaDeSync = { 0L },
            ahora = { 0L }
        )
        val vm = ClienteSearchViewModel(app, repo, buscar = { _ -> unCliente })
        hasta { vm.errorDeBusqueda.value }
        assertEquals(null, vm.clienteCount.value)
    }
}
