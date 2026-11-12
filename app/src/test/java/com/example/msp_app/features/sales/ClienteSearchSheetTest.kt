package com.example.msp_app.features.sales

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.busqueda.TextosDeLista
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.repository.ClienteRepository
import com.example.msp_app.data.repository.FakeClientesApi
import com.example.msp_app.features.sales.screens.ClienteSearchBottomSheet
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * E-APP-157/158: lo que ve el cobrador en la hoja "Buscar cliente" — la misma en ventas y en
 * garantías. Textos exactos del brief, con Room real y un servidor falso.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class ClienteSearchSheetTest : RobolectricTestBase() {

    /**
     * Room con ejecutores DIRECTOS (corren en el hilo que llama) y la búsqueda del ViewModel en
     * `Dispatchers.Unconfined`: nada depende de hilos reales ni de tiempo de pared, así la
     * prueba es determinista (antes fallaba ~1 de 4 en la suite completa).
     */
    private lateinit var db: AppDatabase

    @Before fun abrirBaseDirecta() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(Runnable::run)
            .setTransactionExecutor(Runnable::run)
            .build()
        AppDatabase.setInstanceForTesting(db)
    }

    @After fun cerrarBase() {
        AppDatabase.clearInstance()
        db.close()
    }

    @get:Rule
    val compose = createComposeRule()

    private val api = FakeClientesApi()
    private var fecha = 0L

    private companion object {
        const val DEBOUNCE_MS = 300L
    }

    private var falla: Throwable? = null

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
        return ClienteSearchViewModel(
            app,
            repo,
            buscar = { texto ->
                falla?.let { throw it }
                repo.buscar(texto)
            },
            dispatcherDeBusqueda = Dispatchers.Unconfined
        )
    }

    private fun abrir(vm: ClienteSearchViewModel) = compose.setContent {
        ClienteSearchBottomSheet(viewModel = vm, onDismiss = {
        }, onClienteSelected = { _, _ -> }, onNewCliente = {})
    }

    /** El debounce de 300 ms corre en el reloj virtual del Main de pruebas: hay que avanzarlo a mano. */
    private fun escribir(texto: String) {
        compose.onNodeWithText("Nombre del cliente").performTextInput(texto)
        compose.waitForIdle()
        val planificador = mainDispatcherRule.testDispatcher.scheduler
        planificador.advanceTimeBy(DEBOUNCE_MS + 1)
        planificador.runCurrent()
    }

    private fun esperar(condicion: () -> Boolean) = compose.waitUntil(timeoutMillis = 60_000) {
        // Room contesta desde hilos reales: hay que vaciar el looper principal y el planificador virtual.
        shadowOf(Looper.getMainLooper()).idle()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        condicion()
    }

    private fun esperarTexto(texto: String) = esperar {
        compose.onAllNodes(
            hasText(texto, substring = true)
        ).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun `con el padron vacio dice que aun no se ha descargado`() {
        abrir(vm())
        esperarTexto(TextosDeLista.PADRON_VACIO)
        compose.onNodeWithText("Actualizar lista").assertIsEnabled()
    }

    @Test fun `sin resultados muestra la fecha de la ultima actualizacion y el boton de actualizar`() =
        runTest {
            db.clienteDao().insertAll(listOf(ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz")))
            fecha = 1_700_000_000_000L
            abrir(vm())
            esperarTexto("1 clientes disponibles")
            escribir("zzzzzzz")
            esperarTexto(TextosDeLista.SIN_RESULTADOS)
            compose.onNodeWithText(
                TextosDeLista.ultimaActualizacion("14/11/2023 16:13")
            ).assertExists()
            compose.onNodeWithText("Actualizar lista").assertIsEnabled()
        }

    @Test fun `mas de 20 resultados muestra Mostrando 20 de N`() = runTest {
        db.clienteDao().insertAll(
            (1..45).map {
                ClienteEntity(it, "MARIA LOPEZ $it", "A", null, "maria lopez $it")
            }
        )
        abrir(vm())
        escribir("maria lopez")
        esperarTexto("MARIA LOPEZ")
        compose.onNode(
            hasScrollAction()
        ).performScrollToNode(hasText(TextosDeLista.mostrando(20, 45)))
        compose.onNodeWithText(TextosDeLista.mostrando(20, 45)).assertExists()
    }

    @Test fun `un error de dedo muestra la seccion Parecidos`() = runTest {
        db.clienteDao().insertAll(
            listOf(ClienteEntity(1, "GUADALUPE HERNANDEZ", "A", null, "guadalupe hernandez"))
        )
        abrir(vm())
        escribir("guadalupe hernandes")
        esperarTexto(TextosDeLista.PARECIDOS_AVISO)
        compose.onNodeWithText("GUADALUPE HERNANDEZ").assertExists()
    }

    @Test fun `con parecidos tambien dice No se encontraron clientes arriba y avisa que revise`() =
        runTest {
            db.clienteDao().insertAll(
                listOf(ClienteEntity(1, "GUADALUPE HERNANDEZ", "A", null, "guadalupe hernandez"))
            )
            abrir(vm())
            escribir("guadalupe hernandes")
            esperarTexto(TextosDeLista.PARECIDOS_AVISO)
            compose.onNodeWithText(TextosDeLista.SIN_RESULTADOS).assertExists()
            compose.onNodeWithText("Parecidos (revisa que sea la persona correcta)").assertExists()
        }

    @Test fun `con resultados exactos no dice No se encontraron clientes`() = runTest {
        db.clienteDao().insertAll(listOf(ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz")))
        abrir(vm())
        escribir("ana ruiz")
        esperarTexto("ANA RUIZ")
        compose.onNodeWithText(TextosDeLista.SIN_RESULTADOS).assertDoesNotExist()
    }

    @Test fun `mientras actualiza muestra Actualizando y el boton queda deshabilitado en el vacio`() =
        runTest {
            api.duracionMs = 60_000
            val vm = vm()
            abrir(vm)
            esperarTexto(TextosDeLista.PADRON_VACIO)
            vm.syncFromServer()
            esperarTexto(TextosDeLista.ACTUALIZANDO)
            compose.onNode(
                hasText(TextosDeLista.ACTUALIZANDO) and hasClickAction()
            ).assertIsNotEnabled()
        }

    @Test fun `con una lista incompleta ofrece Actualizar de todos modos y al tocarlo la aplica`() =
        runTest {
            db.clienteDao().insertAll(
                (1..2000).map { ClienteEntity(it, "CLIENTE $it", "A", null, "cliente $it") }
            )
            api.respuesta = (1..10).map { FakeClientesApi.cliente(100_000 + it, "NUEVO $it") }
            val vm = vm()
            abrir(vm)
            esperarTexto("2000 clientes disponibles")
            vm.syncFromServer()
            esperarTexto(TextosDeLista.ERROR_LISTA_INCOMPLETA)
            compose.onNodeWithText(TextosDeLista.FORZAR_ACTUALIZACION).performClick()
            esperarTexto("10 clientes disponibles")
            compose.onNodeWithText(TextosDeLista.FORZAR_ACTUALIZACION).assertDoesNotExist()
        }

    @Test fun `si la busqueda falla dice que no se pudo buscar y no dice que no hay clientes`() =
        runTest {
            db.clienteDao().insertAll(listOf(ClienteEntity(1, "ANA RUIZ", "A", null, "ana ruiz")))
            abrir(vm())
            esperarTexto("1 clientes disponibles")
            falla = IllegalStateException("room cerrado")
            escribir("ana ruiz")
            esperarTexto(TextosDeLista.ERROR_BUSQUEDA)
            compose.onNodeWithText(TextosDeLista.SIN_RESULTADOS).assertDoesNotExist()
            compose.onNodeWithText(TextosDeLista.PADRON_VACIO).assertDoesNotExist()
            falla = null
            compose.onNodeWithText(TextosDeLista.REINTENTAR).performClick()
            esperarTexto("ANA RUIZ")
            compose.onNodeWithText(TextosDeLista.ERROR_BUSQUEDA).assertDoesNotExist()
        }
}
