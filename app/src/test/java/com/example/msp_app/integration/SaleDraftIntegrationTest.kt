package com.example.msp_app.integration

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.draft.DraftCombo
import com.example.msp_app.core.draft.DraftProduct
import com.example.msp_app.core.draft.SaleDraftManager
import com.example.msp_app.`test-fixtures`.RobolectricTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SaleDraftIntegrationTest : RobolectricTestBase() {

    private lateinit var draftManager: SaleDraftManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        draftManager = SaleDraftManager(context)
    }

    @Test
    fun `save and load roundtrip restores all fields`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            clientName = "Juan Perez",
            phone = "5512345678",
            street = "Calle Principal",
            tipoVenta = "CREDITO",
            installment = "500",
            downpayment = "200",
            paymentFrequency = "SEMANAL",
            collectionDay = "LUNES",
            guarantor = "Maria",
            zonaClienteId = 1,
            zonaClienteNombre = "Zona Norte"
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals("Juan Perez", loaded!!.clientName)
        assertEquals("5512345678", loaded.phone)
        assertEquals("Calle Principal", loaded.street)
        assertEquals("CREDITO", loaded.tipoVenta)
        assertEquals("500", loaded.installment)
        assertEquals("200", loaded.downpayment)
        assertEquals("SEMANAL", loaded.paymentFrequency)
        assertEquals("LUNES", loaded.collectionDay)
        assertEquals("Maria", loaded.guarantor)
        assertEquals(1, loaded.zonaClienteId)
        assertEquals("Zona Norte", loaded.zonaClienteNombre)
    }

    @Test
    fun `save with combos and load restores combo JSON`() = runTest {
        val combos = listOf(
            DraftCombo("c1", "Combo Recamara", 5000.0, 4500.0, 4000.0),
            DraftCombo("c2", "Combo Sala", 3000.0, 2700.0, 2500.0)
        )
        val combosJson = draftManager.combosToJson(combos)
        val draft = TestDataFactory.createSaleDraft(combosJson = combosJson)
        draftManager.saveDraft(draft)

        val loaded = draftManager.loadDraft()
        assertNotNull(loaded)
        val loadedCombos = draftManager.jsonToCombos(loaded!!.combosJson)
        assertEquals(2, loadedCombos.size)
        assertEquals("Combo Recamara", loadedCombos[0].nombreCombo)
        assertEquals(5000.0, loadedCombos[0].precioLista, 0.001)
    }

    @Test
    fun `save then clear then load returns null`() = runTest {
        val draft = TestDataFactory.createSaleDraft()
        draftManager.saveDraft(draft)
        draftManager.clearDraft()
        val loaded = draftManager.loadDraft()
        assertNull(loaded)
    }

    @Test
    fun `clearOldDrafts removes expired draft`() = runTest {
        val oldTimestamp = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
        val draft = TestDataFactory.createSaleDraft(timestamp = oldTimestamp)
        draftManager.saveDraft(draft)
        draftManager.clearOldDrafts(maxAgeDays = 7)
        val loaded = draftManager.loadDraft()
        assertNull(loaded)
    }

    @Test
    fun `clearOldDrafts keeps recent draft`() = runTest {
        val draft = TestDataFactory.createSaleDraft(timestamp = System.currentTimeMillis())
        draftManager.saveDraft(draft)
        draftManager.clearOldDrafts(maxAgeDays = 7)
        val loaded = draftManager.loadDraft()
        assertNotNull(loaded)
    }

    @Test
    fun `products JSON roundtrip preserves IDs and quantities`() {
        val products = listOf(
            DraftProduct(articuloId = 100, quantity = 2, comboId = "combo-1"),
            DraftProduct(articuloId = 101, quantity = 3, comboId = null)
        )
        val json = com.google.gson.Gson().toJson(products)
        val restored = draftManager.jsonToDraftProducts(json)
        assertEquals(2, restored.size)
        assertEquals(100, restored[0].articuloId)
        assertEquals(2, restored[0].quantity)
        assertEquals("combo-1", restored[0].comboId)
        assertEquals(101, restored[1].articuloId)
        assertEquals(3, restored[1].quantity)
        assertNull(restored[1].comboId)
    }
}
