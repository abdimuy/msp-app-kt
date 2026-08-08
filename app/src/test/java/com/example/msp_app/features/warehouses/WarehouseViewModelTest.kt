package com.example.msp_app.features.warehouses

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.database.entities.ProductInventoryEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.utils.Constants.ALMACEN_GENERAL_ID
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.services.warehouses.TransferRequest
import com.example.msp_app.data.api.services.warehouses.TransferResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.cache.ProductsCache
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import com.example.msp_app.data.local.repository.WarehouseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 8 (Plan 1 — cimiento): [WarehouseViewModel] ya no construye a mano su
 * `WarehousesApi`/`WarehouseRemoteDataSource`/`WarehouseRepository` — los
 * recibe por `@Inject constructor`. Este test NO pasa por Hilt (eso lo cubre
 * [WarehouseHiltGraphTest]): fija el comportamiento observable del ViewModel
 * usando una fake `WarehousesApi` hecha a mano (mismo patrón que
 * `FakeProductInventoryApi` en `ProductDetailsViewModelTest`) — es el punto de
 * fake más limpio de la cadena porque `WarehouseRepository` y
 * `WarehouseRemoteDataSource` son clases concretas sin interfaz.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WarehouseViewModelTest : RobolectricTestBase() {

    private class FakeWarehousesApi(
        private val allWarehouses: () -> WarehouseListResponse = {
            WarehouseListResponse(body = emptyList(), error = null)
        },
        private val warehouseProducts: (Int) -> WarehouseResponse = {
            throw UnsupportedOperationException("getWarehouseProducts no programado en este test")
        }
    ) : WarehousesApi {
        override suspend fun getAllWarehouses(): WarehouseListResponse = allWarehouses()

        override suspend fun getWarehouseProducts(warehouseId: Int): WarehouseResponse =
            warehouseProducts(warehouseId)

        override suspend fun createTransfer(transferRequest: TransferRequest): TransferResponse =
            throw UnsupportedOperationException("createTransfer no usado en este test")
    }

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    private fun viewModel(api: WarehousesApi): WarehouseViewModel {
        val repository = WarehouseRepository(WarehouseRemoteDataSource(api))
        return WarehouseViewModel(app(), repository)
    }

    @Test
    fun `loadAllWarehouses filtra el almacen general`() = runTest {
        val general = WarehouseListResponse.Warehouse(
            ALMACEN_ID = ALMACEN_GENERAL_ID,
            ALMACEN = "Almacen General",
            EXISTENCIAS = 500
        )
        val sucursal = WarehouseListResponse.Warehouse(
            ALMACEN_ID = 7,
            ALMACEN = "Sucursal Centro",
            EXISTENCIAS = 42
        )
        val api = FakeWarehousesApi(
            allWarehouses = {
                WarehouseListResponse(body = listOf(general, sucursal), error = null)
            }
        )
        val vm = viewModel(api)

        vm.warehouseList.test {
            assertEquals(emptyList<WarehouseListResponse.Warehouse>(), awaitItem())

            vm.loadAllWarehouses()

            val filtered = awaitItem()
            assertEquals(listOf(sucursal), filtered)
            assertFalse(filtered.any { it.ALMACEN_ID == ALMACEN_GENERAL_ID })
        }
    }

    @Test
    fun `getWarehouseProducts cae a cache cuando la red falla`() = runTest {
        val warehouseId = 7
        val cachedEntity = ProductInventoryEntity(
            ARTICULO_ID = 501,
            ARTICULO = "Sala Berlin",
            EXISTENCIAS = 3,
            LINEA_ARTICULO_ID = 1,
            LINEA_ARTICULO = "Salas",
            PRECIOS = null
        )
        // Sembrado con el mismo Context (filesDir) que usará internamente el
        // ProductsCache que WarehouseViewModel construye para sí mismo.
        ProductsCache(app().applicationContext).saveProducts(listOf(cachedEntity))

        val api = FakeWarehousesApi(
            warehouseProducts = { throw RuntimeException("network down") }
        )
        val vm = viewModel(api)

        vm.warehouseProducts.test {
            assertEquals(ResultState.Idle, awaitItem())

            vm.selectWarehouse(warehouseId)

            assertEquals(ResultState.Loading, awaitItem())
            val result = awaitItem()
            assertTrue("se espera Success desde cache", result is ResultState.Success)
            val body = (result as ResultState.Success).data.body
            assertEquals(1, body.ARTICULOS.size)
            assertEquals("Sala Berlin", body.ARTICULOS.first().ARTICULO)
        }
        assertTrue("debe marcar modo offline", vm.isOfflineMode.value)
    }
}
