package com.example.msp_app.data.local.dao

import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSaleComboDaoTest : RoomTestBase() {

    private val saleDao get() = db.localSaleDao()
    private val comboDao get() = db.localSaleComboDao()

    private suspend fun insertParentSale(saleId: String = "sale-1") {
        saleDao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
    }

    @Test
    fun `insert single combo`() = runTest {
        insertParentSale()
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = "combo-1",
            saleId = "sale-1"
        )
        comboDao.insertCombo(combo)
        val combos = comboDao.getCombosForSale("sale-1")
        assertEquals(1, combos.size)
        assertEquals("combo-1", combos[0].COMBO_ID)
    }

    @Test
    fun `insert batch of combos`() = runTest {
        insertParentSale()
        val combos = listOf(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1"),
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-2", saleId = "sale-1")
        )
        comboDao.insertAllCombos(combos)
        val result = comboDao.getCombosForSale("sale-1")
        assertEquals(2, result.size)
    }

    @Test
    fun `getCombosForSale returns empty for unknown sale`() = runTest {
        val result = comboDao.getCombosForSale("nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deleteCombosForSale clears all combos`() = runTest {
        insertParentSale()
        comboDao.insertAllCombos(
            listOf(
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1"),
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-2", saleId = "sale-1")
            )
        )
        comboDao.deleteCombosForSale("sale-1")
        val result = comboDao.getCombosForSale("sale-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `FK cascade deletes combos when sale deleted`() = runTest {
        insertParentSale()
        comboDao.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1")
        )
        // REPLACE triggers cascade
        saleDao.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-1", clientName = "New")
        )
        val combos = comboDao.getCombosForSale("sale-1")
        assertTrue(combos.isEmpty())
    }

    @Test
    fun `REPLACE updates existing combo`() = runTest {
        insertParentSale()
        comboDao.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "combo-1",
                saleId = "sale-1",
                nombreCombo = "Original"
            )
        )
        comboDao.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "combo-1",
                saleId = "sale-1",
                nombreCombo = "Updated"
            )
        )
        val combos = comboDao.getCombosForSale("sale-1")
        assertEquals(1, combos.size)
        assertEquals("Updated", combos[0].NOMBRE_COMBO)
    }
}
