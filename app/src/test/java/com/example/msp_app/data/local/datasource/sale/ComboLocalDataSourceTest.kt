package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [ComboLocalDataSource] construido por el **constructor
 * de DAOs inyectados** (la forma Hilt) con
 * [com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao] de la
 * DB in-memory de [RoomTestBase]. Prueba además que la forma inyectada es
 * EQUIVALENTE al puente `context` que usan `NewLocalSaleViewModel`/
 * `EditLocalSaleViewModel`/`PendingLocalSalesWorker` (ambos resuelven a la
 * misma DB via [com.example.msp_app.core.database.AppDatabase.getInstance]).
 *
 * `local_sale_combos` tiene FK a `local_sale` (`LOCAL_SALE_ID`) — cada test
 * primero da de alta la venta padre por [ensureSale] antes de insertar
 * combos, o la constraint truena.
 */
class ComboLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: ComboLocalDataSource

    @Before
    fun setUpStore() {
        store = ComboLocalDataSource(db.localSaleComboDao())
    }

    private suspend fun ensureSale(saleId: String) {
        db.localSaleDao().insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
    }

    // ─── insertCombo / getCombosForSale round-trip ─────────────────────────────

    @Test
    fun insertCombo_roundTripsViaGetCombosForSale() = runTest {
        ensureSale("sale-1")
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "combo-1",
                saleId = "sale-1",
                nombreCombo = "Combo Recamara"
            )
        )

        val combos = store.getCombosForSale("sale-1")

        assertEquals(1, combos.size)
        assertEquals("Combo Recamara", combos.first().NOMBRE_COMBO)
    }

    @Test
    fun getCombosForSale_emptyWhenNoCombosForThatSale() = runTest {
        ensureSale("sale-1")
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1")
        )

        assertTrue(store.getCombosForSale("sale-otra").isEmpty())
    }

    @Test
    fun insertCombos_batchInsertsAll() = runTest {
        ensureSale("sale-1")
        store.insertCombos(
            listOf(
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1"),
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-2", saleId = "sale-1")
            )
        )

        assertEquals(
            listOf("combo-1", "combo-2").sorted(),
            store.getCombosForSale("sale-1").map { it.COMBO_ID }.sorted()
        )
    }

    @Test
    fun deleteCombosForSale_removesOnlyThatSalesCombos() = runTest {
        ensureSale("sale-1")
        ensureSale("sale-2")
        store.insertCombos(
            listOf(
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1"),
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-2")
            )
        )

        store.deleteCombosForSale("sale-1")

        assertTrue(store.getCombosForSale("sale-1").isEmpty())
        assertEquals(1, store.getCombosForSale("sale-2").size)
    }

    @Test
    fun updateServerUuid_updatesOnlyMatchingCombo() = runTest {
        ensureSale("sale-1")
        store.insertCombos(
            listOf(
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-1"),
                TestDataFactory.createLocalSaleComboEntity(comboId = "combo-2", saleId = "sale-1")
            )
        )

        store.updateServerUuid(
            comboId = "combo-1",
            saleId = "sale-1",
            serverUuid = "server-combo-1"
        )

        val combos = store.getCombosForSale("sale-1").associateBy { it.COMBO_ID }
        assertEquals("server-combo-1", combos["combo-1"]!!.SERVER_UUID)
        assertNull(combos["combo-2"]!!.SERVER_UUID)
    }

    // ─── replaceCombosForSale: DELETE + INSERT transaccional ──────────────────

    @Test
    fun replaceCombosForSale_swapsOldForNew() = runTest {
        ensureSale("sale-1")
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "viejo", saleId = "sale-1")
        )

        store.replaceCombosForSale(
            "sale-1",
            listOf(TestDataFactory.createLocalSaleComboEntity(comboId = "nuevo", saleId = "sale-1"))
        )

        assertEquals(listOf("nuevo"), store.getCombosForSale("sale-1").map { it.COMBO_ID })
    }

    @Test
    fun replaceCombosForSale_emptyListClearsCombos() = runTest {
        ensureSale("sale-1")
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "viejo", saleId = "sale-1")
        )

        store.replaceCombosForSale("sale-1", emptyList())

        assertTrue(store.getCombosForSale("sale-1").isEmpty())
    }

    @Test
    fun replaceCombosForSale_doesNotTouchOtherSales() = runTest {
        ensureSale("sale-1")
        ensureSale("sale-2")
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "otro-combo", saleId = "sale-2")
        )
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "viejo", saleId = "sale-1")
        )

        store.replaceCombosForSale(
            "sale-1",
            listOf(TestDataFactory.createLocalSaleComboEntity(comboId = "nuevo", saleId = "sale-1"))
        )

        assertEquals(listOf("otro-combo"), store.getCombosForSale("sale-2").map { it.COMBO_ID })
    }

    // ─── equivalencia inyectado ⇔ puente context ──────────────────────────────

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        ensureSale("eq-1")
        store.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "eq-1")
        )

        // Tipo explicito: los dos constructores de un arg (DAO vs Context)
        // hacen ambigua la inferencia de `getApplicationContext<T>()`.
        val contextForm = ComboLocalDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getCombosForSale("eq-1"),
            contextForm.getCombosForSale("eq-1")
        )
    }
}
