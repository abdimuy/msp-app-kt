package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.repository.ClienteRepository
import com.example.msp_app.data.repository.FakeClientesApi
import com.example.msp_app.data.repository.FakeClientesApi.Companion.cliente
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-158: el worker periódico de clientes tiene su propio tope TOTAL, en segundo plano. */
class ClienteSyncWorkerTopeTest : RoomTestBase() {

    private val api = FakeClientesApi()
    private val errores = mutableListOf<Exception>()
    private val exitos = mutableListOf<Int>()

    private fun repo() = ClienteRepository(
        dataSource = ClienteDataSource(ApplicationProvider.getApplicationContext<Context>()),
        api = api,
        avisar = { _, _, _ -> },
        guardarFechaDeSync = {},
        leerFechaDeSync = { 0L },
        ahora = { 0L }
    )

    private suspend fun correr(topeMs: Long = TopeDeDescarga.SEGUNDO_PLANO_MS) =
        ejecutarSyncDeClientes(
            repository = repo(),
            intento = 0,
            alExito = { exitos += it },
            alError = { errores += it },
            topeMs = topeMs
        )

    @Test fun `exito termina en success y avisa el conteo`() = runTest {
        api.respuesta = listOf(cliente(1, "ANA RUIZ"), cliente(2, "BETO SOTO"))
        assertEquals(ListenableWorker.Result.success(), correr())
        assertEquals(listOf(2), exitos)
    }

    @Test fun `vence a los 300 segundos exactos y pide reintento`() = runTest {
        api.duracionMs = Long.MAX_VALUE / 4
        val trabajo = async { correr() }
        runCurrent()
        advanceTimeBy(TopeDeDescarga.SEGUNDO_PLANO_MS - 1)
        runCurrent()
        assertTrue("a un ms del tope sigue", trabajo.isActive)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(ListenableWorker.Result.retry(), trabajo.await())
        assertEquals(TopeDeDescarga.SEGUNDO_PLANO_MS, currentTime)
        assertEquals(1, errores.size)
        assertEquals(1, api.cancelaciones)
    }

    @Test fun `vencer deja el padron guardado intacto`() = runTest {
        db.clienteDao().insertAll(listOf(ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz")))
        api.duracionMs = Long.MAX_VALUE / 4
        correr(topeMs = 1_000)
        assertEquals(1, db.clienteDao().getCount())
    }

    @Test fun `un error de red pide reintento`() = runTest {
        api.falla = IOException("sin red")
        assertEquals(ListenableWorker.Result.retry(), correr())
        assertEquals(1, errores.size)
    }

    @Test fun `una lista vacia del servidor termina en success sin avisar exito ni tocar el padron`() =
        runTest {
            db.clienteDao().insertAll(listOf(ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz")))
            api.respuesta = emptyList()
            assertEquals(ListenableWorker.Result.success(), correr())
            assertTrue(exitos.isEmpty())
            assertEquals(1, db.clienteDao().getCount())
        }

    @Test fun `una lista incompleta del servidor NO pide reintento, conserva el padron y no es error`() =
        runTest {
            db.clienteDao().insertAll(
                (1..2000).map { ClienteEntity(it, "CLIENTE $it", "A", null, "cliente $it") }
            )
            api.respuesta = (1..10).map { cliente(100_000 + it, "NUEVO $it") }
            assertEquals(ListenableWorker.Result.success(), correr())
            assertTrue(exitos.isEmpty())
            assertTrue("no es un error de red", errores.isEmpty())
            assertEquals(2000, db.clienteDao().getCount())
        }

    @Test fun `la misma lista reducida 3 periodos seguidos se acepta y avisa exito`() = runTest {
        db.clienteDao().insertAll(
            (1..2000).map { ClienteEntity(it, "CLIENTE $it", "A", null, "cliente $it") }
        )
        api.respuesta = (1..900).map { cliente(100_000 + it, "NUEVO $it") }
        val repositorio = repo()
        suspend fun periodo() = ejecutarSyncDeClientes(
            repository = repositorio,
            intento = 0,
            alExito = { exitos += it },
            alError = { errores += it }
        )
        assertEquals(ListenableWorker.Result.success(), periodo())
        assertEquals(ListenableWorker.Result.success(), periodo())
        assertTrue(exitos.isEmpty())
        assertEquals(ListenableWorker.Result.success(), periodo())
        assertEquals(listOf(900), exitos)
        assertEquals(900, db.clienteDao().getCount())
        assertEquals(3, api.llamadas)
    }

    @Test fun `la cancelacion de WorkManager se propaga y no se vuelve retry`() = runTest {
        api.duracionMs = Long.MAX_VALUE / 4
        val trabajo = async { correr() }
        runCurrent()
        trabajo.cancel()
        runCurrent()
        assertTrue(trabajo.isCancelled)
        assertTrue("cancelar no es un error de red", errores.isEmpty())
        assertEquals(1, api.cancelaciones)
        assertFalse(trabajo.isCompleted && !trabajo.isCancelled)
    }
}
