package com.example.msp_app.data.local.dao

import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSaleProductDaoTest : RoomTestBase() {

    private val saleDao get() = db.localSaleDao()
    private val productDao get() = db.localSaleProduct()

    private suspend fun insertParentSale(saleId: String = "sale-1") {
        saleDao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
    }

    @Test
    fun `insert single product`() = runTest {
        insertParentSale()
        val product = TestDataFactory.createLocalSaleProductEntity(
            saleId = "sale-1",
            articuloId = 100
        )
        productDao.insertSaleProduct(product)
        val products = productDao.getProductsForSale("sale-1")
        assertEquals(1, products.size)
        assertEquals(100, products[0].ARTICULO_ID)
    }

    @Test
    fun `insert batch of products`() = runTest {
        insertParentSale()
        val products = listOf(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 100),
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 101),
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 102)
        )
        productDao.insertAllSaleProducts(products)
        val result = productDao.getProductsForSale("sale-1")
        assertEquals(3, result.size)
    }

    @Test
    fun `getProductsForSale returns empty for unknown sale`() = runTest {
        val result = productDao.getProductsForSale("nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteProductsForSale clears all products`() = runTest {
        insertParentSale()
        productDao.insertAllSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 100),
                TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 101)
            )
        )
        productDao.deleteProductsForSale("sale-1")
        val result = productDao.getProductsForSale("sale-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `composite key isolates across sales`() = runTest {
        insertParentSale("sale-1")
        insertParentSale("sale-2")
        productDao.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 100)
        )
        productDao.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-2", articuloId = 100)
        )
        assertEquals(1, productDao.getProductsForSale("sale-1").size)
        assertEquals(1, productDao.getProductsForSale("sale-2").size)
    }

    @Test
    fun `REPLACE updates existing product`() = runTest {
        insertParentSale()
        productDao.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-1",
                articuloId = 100,
                cantidad = 2
            )
        )
        productDao.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-1",
                articuloId = 100,
                cantidad = 5
            )
        )
        val products = productDao.getProductsForSale("sale-1")
        assertEquals(1, products.size)
        assertEquals(5, products[0].CANTIDAD)
    }
}
