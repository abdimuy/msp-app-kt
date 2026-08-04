package com.example.msp_app.features.productsInventory.viewmodels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.services.productInventory.ProductInventoryApi
import com.example.msp_app.data.api.services.productInventory.ProductInventoryResponse
import com.example.msp_app.data.local.datasource.productInventory.ProductInventoryLocalDataSource
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.data.models.productInventory.toEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailsViewModelTest : RoomTestBase() {

    /**
     * API falsa hecha a mano (mismo patrón que CobranzaSyncManagerTest): el
     * comportamiento se controla con un lambda para poder devolver un catálogo
     * o lanzar una excepción que simula una falla de red.
     */
    private class FakeProductInventoryApi(
        var response: () -> ProductInventoryResponse
    ) : ProductInventoryApi {
        override suspend fun getProductInventory(): ProductInventoryResponse = response()
    }

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    private fun localDataSource() = ProductInventoryLocalDataSource(app().applicationContext)

    // Se inyecta el mismo TestDispatcher que MainDispatcherRule fija como Main y se
    // corre runTest sobre su scheduler (runVmTest). Así el viewModelScope y el
    // withContext(io) comparten UN solo scheduler; el estado terminal se espera con
    // awaitTerminal(), que suspende el cuerpo de test cediendo el scheduler para que
    // corra la reanudación pendiente del camino de excepción (determinista).
    private fun viewModel(api: ProductInventoryApi) =
        ProductDetailsViewModel(app(), api, localDataSource(), mainDispatcherRule.testDispatcher)

    private fun runVmTest(body: suspend TestScope.() -> Unit) =
        runTest(mainDispatcherRule.testDispatcher.scheduler, testBody = body)

    private suspend fun ProductDetailsViewModel.awaitTerminal(): ResultState<ProductInventory> =
        productState.first { it is ResultState.Success || it is ResultState.Error }

    // (a) Online OK → estado Success remoto + Room cacheado.
    @Test
    fun onlineOkReturnsRemoteAndCachesLocally() = runVmTest {
        val id = 501
        val remote = TestDataFactory.createProductInventory(
            id = id,
            name = "Sala Berlin",
            stock = 7
        )
        val api = FakeProductInventoryApi {
            ProductInventoryResponse(body = listOf(remote))
        }
        val vm = viewModel(api)

        vm.loadProductById(id)
        val state = vm.awaitTerminal()

        assertTrue("se espera Success", state is ResultState.Success)
        assertEquals(remote, (state as ResultState.Success).data)
        assertEquals(remote, vm.product.value)
        // Room quedó cacheado (path online refresca el catálogo).
        assertTrue("el producto debe existir en Room", db.productInventoryDao().existsById(id))
    }

    // (b) Sin red → fallback local con el producto sembrado.
    @Test
    fun networkFailsFallsBackToLocal() = runVmTest {
        val id = 502
        val local = TestDataFactory.createProductInventory(
            id = id,
            name = "Comedor Oslo",
            stock = 3
        )
        localDataSource().insertAll(listOf(local.toEntity()))

        val api = FakeProductInventoryApi { throw RuntimeException("network down") }
        val vm = viewModel(api)

        vm.loadProductById(id)
        val state = vm.awaitTerminal()

        assertTrue("se espera Success desde local", state is ResultState.Success)
        assertEquals(id, (state as ResultState.Success).data.ARTICULO_ID)
        assertEquals("Comedor Oslo", state.data.ARTICULO)
        assertEquals(id, vm.product.value?.ARTICULO_ID)
    }

    // (c) Ambos fallan → Error, NUNCA loader infinito (guarda de regresión).
    @Test
    fun bothFailYieldsErrorNotInfiniteLoader() = runVmTest {
        val id = 503 // Room vacío para este id.
        val api = FakeProductInventoryApi { throw RuntimeException("network down") }
        val vm = viewModel(api)

        vm.loadProductById(id)
        val state = vm.awaitTerminal()

        assertTrue("se espera Error", state is ResultState.Error)
        // Guarda explícita contra la regresión del loader infinito.
        assertTrue("NO debe quedarse en Loading", vm.productState.value !is ResultState.Loading)
        assertEquals(null, vm.product.value)
    }

    // (d) Online pisa el local viejo → se muestran los datos remotos y Room se sobreescribe.
    @Test
    fun onlineOverwritesStaleLocal() = runVmTest {
        val id = 504
        val stale = TestDataFactory.createProductInventory(
            id = id,
            name = "Recamara Praga",
            stock = 1
        )
        localDataSource().insertAll(listOf(stale.toEntity()))

        val fresh = TestDataFactory.createProductInventory(
            id = id,
            name = "Recamara Praga",
            stock = 42
        )
        val api = FakeProductInventoryApi {
            ProductInventoryResponse(body = listOf(fresh))
        }
        val vm = viewModel(api)

        vm.loadProductById(id)
        val state = vm.awaitTerminal()

        assertTrue("se espera Success remoto", state is ResultState.Success)
        assertEquals(42, (state as ResultState.Success).data.EXISTENCIAS)
        assertEquals(42, vm.product.value?.EXISTENCIAS)
        // Room fue sobreescrito (REPLACE) con las existencias frescas.
        assertEquals(42, db.productInventoryDao().getProductInventoryById(id).EXISTENCIAS)
    }
}
