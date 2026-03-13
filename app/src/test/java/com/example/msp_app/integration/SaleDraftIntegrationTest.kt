package com.example.msp_app.integration

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.draft.DraftCombo
import com.example.msp_app.core.draft.DraftProduct
import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.core.draft.SaleDraftManager
import com.example.msp_app.`test-fixtures`.RobolectricTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaleDraftIntegrationTest : RobolectricTestBase() {

    private lateinit var draftManager: SaleDraftManager

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        draftManager = SaleDraftManager(context)
        draftManager.clearDraft()
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

    // ========================
    // CONTADO draft roundtrip
    // ========================

    @Test
    fun `CONTADO draft roundtrip preserves tipoVenta and common fields`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            clientName = "Maria Lopez",
            phone = "",
            street = "Av Insurgentes 500",
            tipoVenta = "CONTADO",
            installment = "",
            downpayment = "",
            paymentFrequency = "",
            collectionDay = "",
            guarantor = ""
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals("CONTADO", loaded!!.tipoVenta)
        assertEquals("Maria Lopez", loaded.clientName)
        assertEquals("Av Insurgentes 500", loaded.street)
        // Credit fields saved as empty
        assertEquals("", loaded.installment)
        assertEquals("", loaded.downpayment)
        assertEquals("", loaded.paymentFrequency)
        assertEquals("", loaded.collectionDay)
        assertEquals("", loaded.guarantor)
    }

    // ========================
    // Address fields roundtrip
    // ========================

    @Test
    fun `save and load preserves all address fields`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            street = "Calle 5 de Mayo",
            numero = "789",
            colonia = "Centro Historico",
            poblacion = "Cuauhtemoc",
            ciudad = "CDMX"
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals("Calle 5 de Mayo", loaded!!.street)
        assertEquals("789", loaded.numero)
        assertEquals("Centro Historico", loaded.colonia)
        assertEquals("Cuauhtemoc", loaded.poblacion)
        assertEquals("CDMX", loaded.ciudad)
    }

    @Test
    fun `save and load preserves coordinates`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            latitude = 20.659698,
            longitude = -103.349609
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals(20.659698, loaded!!.latitude, 0.0001)
        assertEquals(-103.349609, loaded.longitude, 0.0001)
    }

    // ========================
    // Overwrite behavior
    // ========================

    @Test
    fun `saving new draft overwrites previous draft`() = runTest {
        val draft1 = TestDataFactory.createSaleDraft(clientName = "Juan")
        draftManager.saveDraft(draft1)

        val draft2 = TestDataFactory.createSaleDraft(clientName = "Pedro")
        draftManager.saveDraft(draft2)

        val loaded = draftManager.loadDraft()
        assertNotNull(loaded)
        assertEquals("Pedro", loaded!!.clientName)
    }

    // ========================
    // Empty/blank edge cases
    // ========================

    @Test
    fun `empty draft is not saved`() = runTest {
        val draft = SaleDraft() // all defaults, hasData() = false
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()
        assertNull(loaded)
    }

    @Test
    fun `draft with only street is saved`() = runTest {
        val draft = SaleDraft(street = "Calle Principal")
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()
        assertNotNull(loaded)
        assertEquals("Calle Principal", loaded!!.street)
    }

    // ========================
    // Combos detailed roundtrip
    // ========================

    @Test
    fun `combo prices survive JSON roundtrip with precision`() {
        val combos = listOf(
            DraftCombo("c1", "Combo A", 12345.67, 9876.54, 7777.77)
        )
        val json = draftManager.combosToJson(combos)
        val restored = draftManager.jsonToCombos(json)

        assertEquals(1, restored.size)
        assertEquals("c1", restored[0].comboId)
        assertEquals("Combo A", restored[0].nombreCombo)
        assertEquals(12345.67, restored[0].precioLista, 0.001)
        assertEquals(9876.54, restored[0].precioCortoPlazo, 0.001)
        assertEquals(7777.77, restored[0].precioContado, 0.001)
    }

    @Test
    fun `empty combos JSON returns empty list`() {
        val restored = draftManager.jsonToCombos("")
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `invalid combos JSON returns empty list`() {
        val restored = draftManager.jsonToCombos("not valid json")
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `empty products JSON returns empty list`() {
        val restored = draftManager.jsonToDraftProducts("")
        assertTrue(restored.isEmpty())
    }

    @Test
    fun `invalid products JSON returns empty list`() {
        val restored = draftManager.jsonToDraftProducts("{broken}")
        assertTrue(restored.isEmpty())
    }

    // ========================
    // Image URIs roundtrip
    // ========================

    @Test
    fun `save and load preserves image URI paths`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            clientName = "Test",
            imageUris = listOf("/data/draft_images/img1.jpg", "/data/draft_images/img2.jpg")
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals(2, loaded!!.imageUris.size)
        assertEquals("/data/draft_images/img1.jpg", loaded.imageUris[0])
        assertEquals("/data/draft_images/img2.jpg", loaded.imageUris[1])
    }

    @Test
    fun `save and load preserves empty image list`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            clientName = "Test",
            imageUris = emptyList()
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertTrue(loaded!!.imageUris.isEmpty())
    }

    // ========================
    // Zone roundtrip
    // ========================

    @Test
    fun `save and load preserves zone fields`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            zonaClienteId = 5,
            zonaClienteNombre = "Zona Poniente"
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals(5, loaded!!.zonaClienteId)
        assertEquals("Zona Poniente", loaded.zonaClienteNombre)
    }

    @Test
    fun `save and load with null zonaClienteId`() = runTest {
        val draft = TestDataFactory.createSaleDraft(
            zonaClienteId = null,
            zonaClienteNombre = ""
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertNull(loaded!!.zonaClienteId)
        assertEquals("", loaded.zonaClienteNombre)
    }

    // ========================
    // Old draft boundary
    // ========================

    @Test
    fun `clearOldDrafts keeps draft at 6 days`() = runTest {
        val sixDaysAgo = System.currentTimeMillis() - (6 * 24 * 60 * 60 * 1000L)
        val draft = TestDataFactory.createSaleDraft(timestamp = sixDaysAgo)
        draftManager.saveDraft(draft)
        draftManager.clearOldDrafts(maxAgeDays = 7)
        val loaded = draftManager.loadDraft()
        assertNotNull(loaded)
    }

    @Test
    fun `clearOldDrafts removes draft at 8 days`() = runTest {
        val eightDaysAgo = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)
        val draft = TestDataFactory.createSaleDraft(timestamp = eightDaysAgo)
        draftManager.saveDraft(draft)
        draftManager.clearOldDrafts(maxAgeDays = 7)
        val loaded = draftManager.loadDraft()
        assertNull(loaded)
    }

    // ========================
    // Full CREDITO draft with products + combos
    // ========================

    @Test
    fun `full CREDITO draft with products and combos roundtrip`() = runTest {
        val products = listOf(
            DraftProduct(articuloId = 1, quantity = 2, comboId = null),
            DraftProduct(articuloId = 2, quantity = 1, comboId = "combo-1"),
            DraftProduct(articuloId = 3, quantity = 1, comboId = "combo-1")
        )
        val combos = listOf(
            DraftCombo("combo-1", "Combo Recamara", 5000.0, 4500.0, 3800.0)
        )
        val productsJson = com.google.gson.Gson().toJson(products)
        val combosJson = draftManager.combosToJson(combos)

        val draft = TestDataFactory.createSaleDraft(
            clientName = "Juan Carlos",
            phone = "5512345678",
            street = "Calle Norte 200",
            numero = "15",
            colonia = "Industrial",
            poblacion = "Monterrey",
            ciudad = "Monterrey",
            tipoVenta = "CREDITO",
            installment = "800",
            downpayment = "1000",
            paymentFrequency = "SEMANAL",
            collectionDay = "MARTES",
            guarantor = "Ana Perez",
            zonaClienteId = 2,
            zonaClienteNombre = "Zona Norte",
            latitude = 25.686613,
            longitude = -100.316116,
            productsJson = productsJson,
            combosJson = combosJson,
            imageUris = listOf("/data/img1.jpg")
        )
        draftManager.saveDraft(draft)
        val loaded = draftManager.loadDraft()

        assertNotNull(loaded)
        assertEquals("Juan Carlos", loaded!!.clientName)
        assertEquals("5512345678", loaded.phone)
        assertEquals("Calle Norte 200", loaded.street)
        assertEquals("15", loaded.numero)
        assertEquals("Industrial", loaded.colonia)
        assertEquals("Monterrey", loaded.poblacion)
        assertEquals("CREDITO", loaded.tipoVenta)
        assertEquals("800", loaded.installment)
        assertEquals("1000", loaded.downpayment)
        assertEquals("SEMANAL", loaded.paymentFrequency)
        assertEquals("MARTES", loaded.collectionDay)
        assertEquals("Ana Perez", loaded.guarantor)
        assertEquals(2, loaded.zonaClienteId)
        assertEquals("Zona Norte", loaded.zonaClienteNombre)
        assertEquals(25.686613, loaded.latitude, 0.0001)
        assertEquals(-100.316116, loaded.longitude, 0.0001)
        assertEquals(1, loaded.imageUris.size)

        // Verify products
        val restoredProducts = draftManager.jsonToDraftProducts(loaded.productsJson)
        assertEquals(3, restoredProducts.size)
        assertEquals(1, restoredProducts[0].articuloId)
        assertEquals(2, restoredProducts[0].quantity)
        assertNull(restoredProducts[0].comboId)
        assertEquals("combo-1", restoredProducts[1].comboId)

        // Verify combos
        val restoredCombos = draftManager.jsonToCombos(loaded.combosJson)
        assertEquals(1, restoredCombos.size)
        assertEquals("Combo Recamara", restoredCombos[0].nombreCombo)
        assertEquals(5000.0, restoredCombos[0].precioLista, 0.001)
        assertEquals(4500.0, restoredCombos[0].precioCortoPlazo, 0.001)
        assertEquals(3800.0, restoredCombos[0].precioContado, 0.001)
    }
}
