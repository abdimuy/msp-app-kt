package com.example.msp_app.data.local.dao

import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for LocalSaleComboDao.replaceCombosForSale() — the atomic
 * delete-then-insert transaction used during sale editing.
 */
class ReplaceCombosForSaleDaoTest : RoomTestBase() {

    private val saleDao get() = db.localSaleDao()
    private val comboDao get() = db.localSaleComboDao()

    private suspend fun insertParentSale(saleId: String = "sale-1") {
        saleDao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
    }

    // ========================
    // Basic replace
    // ========================

    @Test
    fun `replaces existing combos with new ones`() = runTest {
        insertParentSale()
        comboDao.insertAllCombos(
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "old-1",
                    saleId = "sale-1",
                    nombreCombo = "Viejo 1"
                ),
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "old-2",
                    saleId = "sale-1",
                    nombreCombo = "Viejo 2"
                )
            )
        )

        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "new-1",
                    saleId = "sale-1",
                    nombreCombo = "Nuevo 1"
                )
            )
        )

        val combos = comboDao.getCombosForSale("sale-1")
        assertEquals(1, combos.size)
        assertEquals("new-1", combos[0].COMBO_ID)
        assertEquals("Nuevo 1", combos[0].NOMBRE_COMBO)
    }

    @Test
    fun `replace with empty list removes all combos`() = runTest {
        insertParentSale()
        comboDao.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "c-1", saleId = "sale-1")
        )

        comboDao.replaceCombosForSale("sale-1", emptyList())

        assertTrue(comboDao.getCombosForSale("sale-1").isEmpty())
    }

    @Test
    fun `replace on sale with no combos inserts new ones`() = runTest {
        insertParentSale()
        assertTrue(comboDao.getCombosForSale("sale-1").isEmpty())

        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "new-1",
                    saleId = "sale-1"
                )
            )
        )

        assertEquals(1, comboDao.getCombosForSale("sale-1").size)
    }

    // ========================
    // Isolation between sales
    // ========================

    @Test
    fun `replace does not affect other sales`() = runTest {
        insertParentSale("sale-1")
        insertParentSale("sale-2")

        comboDao.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "c-1",
                saleId = "sale-1",
                nombreCombo = "Sale1 Combo"
            )
        )
        comboDao.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "c-2",
                saleId = "sale-2",
                nombreCombo = "Sale2 Combo"
            )
        )

        // Replace only sale-1 combos
        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "c-1-new",
                    saleId = "sale-1",
                    nombreCombo = "Replaced"
                )
            )
        )

        // sale-2 combos untouched
        val sale2Combos = comboDao.getCombosForSale("sale-2")
        assertEquals(1, sale2Combos.size)
        assertEquals("Sale2 Combo", sale2Combos[0].NOMBRE_COMBO)

        // sale-1 has new combo
        val sale1Combos = comboDao.getCombosForSale("sale-1")
        assertEquals(1, sale1Combos.size)
        assertEquals("Replaced", sale1Combos[0].NOMBRE_COMBO)
    }

    // ========================
    // Price preservation
    // ========================

    @Test
    fun `replace preserves all price fields`() = runTest {
        insertParentSale()
        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "c-1",
                    saleId = "sale-1",
                    precioLista = 9999.99,
                    precioCortoplazo = 8888.88,
                    precioContado = 7777.77
                )
            )
        )

        val combo = comboDao.getCombosForSale("sale-1")[0]
        assertEquals(9999.99, combo.PRECIO_LISTA, 0.001)
        assertEquals(8888.88, combo.PRECIO_CORTO_PLAZO, 0.001)
        assertEquals(7777.77, combo.PRECIO_CONTADO, 0.001)
    }

    @Test
    fun `replace preserves zero prices`() = runTest {
        insertParentSale()
        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "c-1",
                    saleId = "sale-1",
                    precioLista = 0.0,
                    precioCortoplazo = 0.0,
                    precioContado = 3800.0
                )
            )
        )

        val combo = comboDao.getCombosForSale("sale-1")[0]
        assertEquals(0.0, combo.PRECIO_LISTA, 0.001)
        assertEquals(0.0, combo.PRECIO_CORTO_PLAZO, 0.001)
        assertEquals(3800.0, combo.PRECIO_CONTADO, 0.001)
    }

    // ========================
    // Multiple replacements
    // ========================

    @Test
    fun `consecutive replacements keep only latest`() = runTest {
        insertParentSale()

        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "v1",
                    saleId = "sale-1",
                    nombreCombo = "Version 1"
                )
            )
        )
        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "v2",
                    saleId = "sale-1",
                    nombreCombo = "Version 2"
                )
            )
        )
        comboDao.replaceCombosForSale(
            "sale-1",
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "v3",
                    saleId = "sale-1",
                    nombreCombo = "Version 3"
                )
            )
        )

        val combos = comboDao.getCombosForSale("sale-1")
        assertEquals(1, combos.size)
        assertEquals("Version 3", combos[0].NOMBRE_COMBO)
    }

    @Test
    fun `replace many combos with many combos`() = runTest {
        insertParentSale()
        comboDao.insertAllCombos(
            (1..5).map {
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "old-$it",
                    saleId = "sale-1",
                    nombreCombo = "Old $it"
                )
            }
        )

        comboDao.replaceCombosForSale(
            "sale-1",
            (1..3).map {
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "new-$it",
                    saleId = "sale-1",
                    nombreCombo = "New $it"
                )
            }
        )

        val combos = comboDao.getCombosForSale("sale-1")
        assertEquals(3, combos.size)
        assertTrue(combos.all { it.COMBO_ID.startsWith("new-") })
    }

    // ========================
    // Edge: nonexistent sale
    // ========================

    @Test
    fun `replace on nonexistent sale with empty list does not crash`() = runTest {
        // Should not throw
        comboDao.replaceCombosForSale("nonexistent", emptyList())
        assertTrue(comboDao.getCombosForSale("nonexistent").isEmpty())
    }
}
