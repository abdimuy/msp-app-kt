package com.example.msp_app.features.sales

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.busqueda.TextosDeLista
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.repository.ClienteRepository
import com.example.msp_app.data.repository.FakeClientesApi
import com.example.msp_app.data.repository.FakeClientesApi.Companion.cliente
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: el estado de la hoja de búsqueda (ventas y garantías la comparten). */
class ClienteSearchViewModelTest : RoomTestBase() {

    private val api = FakeClientesApi()
    private var fecha = 0L

    private fun vm(): ClienteSearchViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = ClienteRepository(
            dataSource = ClienteDataSource(app),
            api = api,
            avisar = { _, _, _ -> },
            guardarFechaDeSync = { fecha = it },
            leerFechaDeSync = { fecha },
            ahora = { 1_700_000_000_000L }
        )
        return ClienteSearchViewModel(app, repo)
    }

    /**
     * Room corre sus consultas en hilos REALES: el reloj virtual de `runTest` no los espera.
     * Avanza el planificador y espera (tiempo real, hasta 5 s) a que la condición se cumpla.
     */
    private fun TestScope.hasta(condicion: () -> Boolean) {
        repeat(500) {
            runCurrent()
            if (condicion()) return
            Thread.sleep(10)
        }
        runCurrent()
        assertTrue("la condición no se cumplió en 5 s", condicion())
    }

    private suspend fun sembrar(vararg nombres: String) = db.clienteDao().insertAll(
        nombres.mapIndexed { i, n -> ClienteEntity(i + 1, n, "A", null, n.lowercase()) }
    )

    @Test fun `escribir y esperar el debounce trae resultados`() = runTest {
        sembrar("ANA RUIZ", "BETO SOTO")
        val vm = vm()
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.isNotEmpty() }
        assertEquals(listOf("ANA RUIZ"), vm.resultado.value.exactos.map { it.NOMBRE })
    }

    @Test fun `borrar la consulta limpia los resultados`() = runTest {
        sembrar("ANA RUIZ")
        val vm = vm()
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.isNotEmpty() }
        vm.onQueryChange("")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.isEmpty() }
    }

    @Test fun `una letra no busca`() = runTest {
        sembrar("ANA RUIZ")
        val vm = vm()
        vm.onQueryChange("a")
        advanceTimeBy(301)
        runCurrent()
        assertTrue(vm.resultado.value.exactos.isEmpty())
    }

    @Test fun `con padron vacio el conteo es cero y no hay fecha`() = runTest {
        val vm = vm()
        hasta { vm.clienteCount.value != null }
        assertEquals(0, vm.clienteCount.value)
        assertNull(vm.ultimaActualizacion.value)
    }

    @Test fun `la fecha de la ultima actualizacion se muestra con formato`() = runTest {
        sembrar("ANA RUIZ")
        fecha = 1_700_000_000_000L // 2023-11-14 16:13 UTC = 10:13 CDMX
        val vm = vm()
        hasta { vm.ultimaActualizacion.value != null }
        assertEquals("14/11/2023 16:13", vm.ultimaActualizacion.value)
        assertEquals(1, vm.clienteCount.value)
    }

    @Test fun `actualizar con exito reemplaza la lista y repite la busqueda en curso`() = runTest {
        sembrar("ANA RUIZ")
        api.duracionMs = 5_000
        api.respuesta = listOf(cliente(10, "ANA LUNA"), cliente(11, "ANA PAZ"))
        val vm = vm()
        vm.onQueryChange("ana")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.size == 1 }
        vm.syncFromServer()
        runCurrent()
        assertTrue(vm.isSyncing.value)
        advanceTimeBy(5_001)
        hasta { !vm.isSyncing.value && vm.resultado.value.exactos.size == 2 }
        assertEquals(2, vm.clienteCount.value)
        assertEquals(listOf("ANA PAZ", "ANA LUNA"), vm.resultado.value.exactos.map { it.NOMBRE })
        assertNotNull(vm.ultimaActualizacion.value)
        assertNull(vm.syncError.value)
    }

    @Test fun `mientras actualiza se puede seguir buscando en la lista guardada`() = runTest {
        sembrar("ANA RUIZ", "BETO SOTO")
        api.duracionMs = 60_000
        api.respuesta = listOf(cliente(10, "ZOE VEGA"))
        val vm = vm()
        vm.syncFromServer()
        runCurrent()
        assertTrue(vm.isSyncing.value)
        vm.onQueryChange("beto")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.isNotEmpty() }
        assertTrue("sigue actualizando", vm.isSyncing.value)
        assertEquals(listOf("BETO SOTO"), vm.resultado.value.exactos.map { it.NOMBRE })
    }

    @Test fun `un segundo toque no duplica la descarga`() = runTest {
        sembrar("ANA RUIZ")
        api.duracionMs = 10_000
        api.respuesta = listOf(cliente(10, "ZOE VEGA"))
        val vm = vm()
        repeat(5) { vm.syncFromServer() }
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(1, api.llamadas)
    }

    @Test fun `al vencer el tope manual sale el mensaje y la lista anterior queda intacta`() = runTest {
        sembrar("ANA RUIZ", "BETO SOTO")
        api.duracionMs = Long.MAX_VALUE / 4
        val vm = vm()
        advanceUntilIdle()
        vm.syncFromServer()
        runCurrent()
        advanceTimeBy(TopeDeDescarga.MANUAL_MS - 1)
        runCurrent()
        assertTrue(vm.isSyncing.value)
        advanceTimeBy(1)
        runCurrent()
        assertFalse(vm.isSyncing.value)
        assertEquals(TextosDeLista.ERROR_LENTA, vm.syncError.value)
        assertEquals(1, api.cancelaciones)
        assertEquals(2, db.clienteDao().getCount())
        assertEquals(2, vm.clienteCount.value)
    }

    @Test fun `tras fallar se puede volver a tocar el boton`() = runTest {
        sembrar("ANA RUIZ")
        api.duracionMs = Long.MAX_VALUE / 4
        val vm = vm()
        vm.syncFromServer()
        advanceTimeBy(TopeDeDescarga.MANUAL_MS + 1)
        runCurrent()
        api.duracionMs = 100
        api.respuesta = listOf(cliente(10, "ZOE VEGA"))
        vm.syncFromServer()
        assertNull(vm.syncError.value)
        advanceTimeBy(101)
        hasta { !vm.isSyncing.value && vm.clienteCount.value == 1 }
        assertEquals(2, api.llamadas)
    }

    @Test fun `una lista vacia del servidor avisa y conserva el padron`() = runTest {
        sembrar("ANA RUIZ", "BETO SOTO")
        api.respuesta = emptyList()
        val vm = vm()
        vm.syncFromServer()
        hasta { vm.syncError.value != null }
        assertEquals(TextosDeLista.ERROR_LISTA_VACIA, vm.syncError.value)
        assertEquals(2, db.clienteDao().getCount())
    }

    @Test fun `cerrar la pantalla en plena descarga cancela la peticion y no deja error`() = runTest {
        sembrar("ANA RUIZ")
        api.duracionMs = Long.MAX_VALUE / 4
        val vm = vm()
        vm.syncFromServer()
        runCurrent()
        vm.viewModelScope.cancel()
        runCurrent()
        assertEquals(1, api.cancelaciones)
        assertNull(vm.syncError.value)
    }

    @Test fun `limpiar el error lo borra`() = runTest {
        api.falla = java.io.IOException("sin red")
        val vm = vm()
        vm.syncFromServer()
        advanceUntilIdle()
        assertEquals(TextosDeLista.ERROR_SIN_CONEXION, vm.syncError.value)
        vm.clearSyncError()
        assertNull(vm.syncError.value)
    }

    @Test fun `mas de 20 resultados se informan con el total`() = runTest {
        db.clienteDao().insertAll(
            (1..45).map {
                ClienteEntity(it, "MARIA LOPEZ $it", "A", null, "maria lopez $it")
            }
        )
        val vm = vm()
        vm.onQueryChange("maria lopez")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.size == 20 }
        assertEquals(20, vm.resultado.value.exactos.size)
        assertEquals(45, vm.resultado.value.totalExactos)
        assertEquals(
            "Mostrando 20 de 45 · escribe más para acotar",
            TextosDeLista.mostrando(20, 45)
        )
    }

    @Test fun `buscar por numero de cliente`() = runTest {
        sembrar("ANA RUIZ", "BETO SOTO")
        val vm = vm()
        vm.onQueryChange("2")
        advanceTimeBy(301)
        runCurrent()
        assertTrue("un digito solo no busca", vm.resultado.value.exactos.isEmpty())
        db.clienteDao().insertAll(
            listOf(ClienteEntity(2345, "CARLA DIAZ", "A", null, "carla diaz"))
        )
        vm.onQueryChange("2345")
        advanceTimeBy(301)
        hasta { vm.resultado.value.exactos.isNotEmpty() }
        assertEquals(listOf("CARLA DIAZ"), vm.resultado.value.exactos.map { it.NOMBRE })
    }

    @Test fun `un error de dedo muestra parecidos`() = runTest {
        sembrar("GUADALUPE HERNANDEZ")
        val vm = vm()
        vm.onQueryChange("guadalupe hernandes")
        advanceTimeBy(301)
        hasta { vm.resultado.value.parecidos.isNotEmpty() }
        assertTrue(vm.resultado.value.exactos.isEmpty())
        assertEquals(listOf("GUADALUPE HERNANDEZ"), vm.resultado.value.parecidos.map { it.NOMBRE })
    }
}
