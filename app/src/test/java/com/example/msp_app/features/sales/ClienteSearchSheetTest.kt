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
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.busqueda.TextosDeLista
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.local.datasource.ClienteDataSource
import com.example.msp_app.data.repository.ClienteRepository
import com.example.msp_app.data.repository.FakeClientesApi
import com.example.msp_app.features.sales.screens.ClienteSearchBottomSheet
import com.example.msp_app.features.sales.viewmodels.ClienteSearchViewModel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
class ClienteSearchSheetTest : RoomTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private val api = FakeClientesApi()
    private var fecha = 0L

    private companion object {
        const val DEBOUNCE_MS = 300L
    }

    private fun vm(): ClienteSearchViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return ClienteSearchViewModel(
            app,
            ClienteRepository(
                dataSource = ClienteDataSource(app),
                api = api,
                avisar = { _, _, _ -> },
                guardarFechaDeSync = { fecha = it },
                leerFechaDeSync = { fecha },
                ahora = { 1_700_000_000_000L }
            )
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
        esperarTexto(TextosDeLista.PARECIDOS)
        compose.onNodeWithText("GUADALUPE HERNANDEZ").assertExists()
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
}
