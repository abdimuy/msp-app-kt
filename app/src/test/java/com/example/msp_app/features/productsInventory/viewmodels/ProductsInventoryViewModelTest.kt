package com.example.msp_app.features.productsInventory.viewmodels

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.time.FechaDelCatalogo
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.services.productInventory.ProductInventoryApi
import com.example.msp_app.data.api.services.productInventory.ProductInventoryResponse
import com.example.msp_app.data.local.datasource.productInventory.ProductInventoryLocalDataSource
import com.example.msp_app.data.local.fechaDelCatalogo
import com.example.msp_app.`test-fixtures`.TestDataFactory
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La fecha del catálogo solo se mueve cuando una descarga completa quedó guardada en el teléfono. */
class ProductsInventoryViewModelTest : RoomTestBase() {

    private class FakeApi(var response: () -> ProductInventoryResponse) : ProductInventoryApi {
        override suspend fun getProductInventory(): ProductInventoryResponse = response()
    }

    private val hora = Instant.parse("2026-11-10T15:00:00Z")
    private var marca = 0L
    private val fecha = FechaDelCatalogo({ marca }, { marca = it }, FakeClock(hora))

    private fun app(): Application = ApplicationProvider.getApplicationContext()

    private fun vm(api: ProductInventoryApi) = ProductsInventoryViewModel(
        app(),
        ProductInventoryLocalDataSource(app().applicationContext),
        { api },
        fecha
    )

    private fun runVmTest(body: suspend () -> Unit) =
        runTest(mainDispatcherRule.testDispatcher.scheduler) { body() }

    @Test fun `fetchRemoteInventory exitoso registra la fecha`() = runVmTest {
        val p = TestDataFactory.createProductInventory(id = 601, name = "Sala", stock = 2)
        val vm = vm(FakeApi { ProductInventoryResponse(body = listOf(p)) })
        vm.fetchRemoteInventory()
        vm.productsLoaded.first { it }
        assertEquals(hora, fecha.ultima())
    }

    @Test fun `loadProductsWithFallback exitoso registra la fecha`() = runVmTest {
        val p = TestDataFactory.createProductInventory(id = 602, name = "Sala", stock = 2)
        val vm = vm(FakeApi { ProductInventoryResponse(body = listOf(p)) })
        vm.loadProductsWithFallback()
        vm.productsLoaded.first { it }
        assertEquals(hora, fecha.ultima())
    }

    @Test fun `una descarga fallida no mueve la fecha`() = runVmTest {
        val vm = vm(FakeApi { throw IOException("sin red") })
        vm.fetchRemoteInventory()
        vm.productInventoryState.first { it is ResultState.Error }
        assertNull(fecha.ultima())
    }

    @Test fun `las preferencias guardan y devuelven la fecha, y empiezan vacias`() {
        val reloj = FakeClock(hora)
        assertNull(fechaDelCatalogo(app(), reloj).ultima())
        fechaDelCatalogo(app(), reloj).registrar()
        assertEquals(hora, fechaDelCatalogo(app(), reloj).ultima())
    }
}
