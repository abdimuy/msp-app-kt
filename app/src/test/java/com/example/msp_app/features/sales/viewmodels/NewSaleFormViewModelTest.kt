package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewSaleFormViewModelTest : RobolectricTestBase() {

    private lateinit var viewModel: NewSaleFormViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        viewModel = NewSaleFormViewModel(app)
    }

    // --- Field updates ---

    @Test
    fun `updateClientName updates state`() {
        viewModel.updateClientName("Juan Perez")
        assertEquals("Juan Perez", viewModel.formState.value.clientName)
    }

    @Test
    fun `updateSelectedClienteId updates state`() {
        viewModel.updateSelectedClienteId(42)
        assertEquals(42, viewModel.formState.value.selectedClienteId)
    }

    @Test
    fun `updatePhone updates state and validates for CREDITO`() {
        viewModel.updatePhone("55123")
        assertEquals("55123", viewModel.formState.value.phone)
    }

    @Test
    fun `updatePhone sets error for invalid CREDITO phone`() {
        viewModel.updatePhone("123")
        assertTrue(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `updatePhone clears error for valid CREDITO phone`() {
        viewModel.updatePhone("123") // set error
        viewModel.updatePhone("5512345678") // fix it
        assertFalse(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `updatePhone acepta vacio en CONTADO`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("")
        assertFalse(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `incidente - updatePhone marca error para 000000 en CONTADO`() {
        // Sustituye al test histórico `updatePhone skips validation for CONTADO`,
        // que fijaba el defecto del incidente del 2026-08-13: en contado se
        // aceptaba cualquier cadena y "000000" viajaba como "+52000000".
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("000000")
        assertTrue(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `updatePhone acepta telefono valido en CONTADO`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("2381202772")
        assertFalse(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `updateTipoVenta a CONTADO apaga el error de telefono vacio`() {
        viewModel.updatePhone("") // en CRÉDITO el vacío es error…
        viewModel.updatePhone("123")
        assertTrue(viewModel.formState.value.errors.phone)
        viewModel.updatePhone("")
        viewModel.updateTipoVenta("CONTADO") // …y en CONTADO deja de serlo
        assertFalse(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `updateTipoVenta no enciende errores en un campo intacto`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updateTipoVenta("CREDITO")
        assertFalse(viewModel.formState.value.errors.phone)
    }

    // --- Colonia / Población / Ciudad (incidente 2026-08-13) ---

    @Test
    fun `incidente - updateCiudad en blanco marca error`() {
        viewModel.updateCiudad("   ")
        assertTrue(viewModel.formState.value.errors.ciudad)
    }

    @Test
    fun `incidente - updateColonia en blanco marca error`() {
        viewModel.updateColonia("   ")
        assertTrue(viewModel.formState.value.errors.colonia)
    }

    @Test
    fun `updatePoblacion en blanco marca error`() {
        viewModel.updatePoblacion("   ")
        assertTrue(viewModel.formState.value.errors.poblacion)
    }

    @Test
    fun `updateColonia con valor limpia el error`() {
        viewModel.updateColonia("   ")
        viewModel.updateColonia("Centro")
        assertFalse(viewModel.formState.value.errors.colonia)
    }

    @Test
    fun `updateCiudad con valor limpia el error`() {
        viewModel.updateCiudad("   ")
        viewModel.updateCiudad("Puebla")
        assertFalse(viewModel.formState.value.errors.ciudad)
    }

    @Test
    fun `un formulario intacto no pinta en rojo colonia poblacion ni ciudad`() {
        val errors = viewModel.formState.value.errors
        assertFalse(errors.colonia)
        assertFalse(errors.poblacion)
        assertFalse(errors.ciudad)
    }

    @Test
    fun `updateStreet sets error for short street`() {
        viewModel.updateStreet("Cal")
        assertTrue(viewModel.formState.value.errors.location)
    }

    @Test
    fun `updateStreet clears error for valid street`() {
        viewModel.updateStreet("Cal") // set error
        viewModel.updateStreet("Calle Principal")
        assertFalse(viewModel.formState.value.errors.location)
    }

    @Test
    fun `updateNumero updates state`() {
        viewModel.updateNumero("123")
        assertEquals("123", viewModel.formState.value.numero)
    }

    @Test
    fun `updateColonia updates state`() {
        viewModel.updateColonia("Centro")
        assertEquals("Centro", viewModel.formState.value.colonia)
    }

    @Test
    fun `updatePoblacion updates state`() {
        viewModel.updatePoblacion("CDMX")
        assertEquals("CDMX", viewModel.formState.value.poblacion)
    }

    @Test
    fun `updateCiudad updates state`() {
        viewModel.updateCiudad("CDMX")
        assertEquals("CDMX", viewModel.formState.value.ciudad)
    }

    @Test
    fun `updateLocation sets coords and permission`() {
        viewModel.updateLocation(19.432608, -99.133209)
        val state = viewModel.formState.value
        assertEquals(19.432608, state.latitude, 0.001)
        assertEquals(-99.133209, state.longitude, 0.001)
        assertTrue(state.locationPermissionGranted)
        assertTrue(state.hasValidLocation)
    }

    @Test
    fun `updateTipoVenta to CONTADO clears zone`() {
        viewModel.updateZone(1, "Zona Norte")
        viewModel.updateTipoVenta("CONTADO")
        val state = viewModel.formState.value
        assertEquals("CONTADO", state.tipoVenta)
        assertNull(state.selectedZoneId)
        assertEquals("", state.selectedZoneName)
        assertFalse(state.errors.zone)
    }

    @Test
    fun `updateTipoVenta to CREDITO preserves state`() {
        viewModel.updateTipoVenta("CREDITO")
        assertEquals("CREDITO", viewModel.formState.value.tipoVenta)
    }

    @Test
    fun `updateZone sets id and name and clears error`() {
        viewModel.updateZone(1, "Zona Norte")
        val state = viewModel.formState.value
        assertEquals(1, state.selectedZoneId)
        assertEquals("Zona Norte", state.selectedZoneName)
        assertFalse(state.errors.zone)
    }

    @Test
    fun `updateDownpayment validates on change`() {
        viewModel.updateDownpayment("-100")
        assertTrue(viewModel.formState.value.errors.downpayment)
    }

    @Test
    fun `updateDownpayment clears error for valid value`() {
        viewModel.updateDownpayment("-100")
        viewModel.updateDownpayment("200")
        assertFalse(viewModel.formState.value.errors.downpayment)
    }

    @Test
    fun `updateInstallment validates on change`() {
        viewModel.updateInstallment("abc")
        assertTrue(viewModel.formState.value.errors.installment)
    }

    @Test
    fun `updateCollectionDay validates`() {
        viewModel.updateCollectionDay("")
        assertTrue(viewModel.formState.value.errors.collectionDay)
    }

    @Test
    fun `updatePaymentFrequency validates`() {
        viewModel.updatePaymentFrequency("")
        assertTrue(viewModel.formState.value.errors.paymentFrequency)
    }

    @Test
    fun `updateGuarantor updates state`() {
        viewModel.updateGuarantor("Maria")
        assertEquals("Maria", viewModel.formState.value.guarantor)
    }

    @Test
    fun `updateNote updates state`() {
        viewModel.updateNote("Test note")
        assertEquals("Test note", viewModel.formState.value.note)
    }

    // --- validateFields ---

    @Test
    fun `validateFields empty CREDITO form returns false with all errors`() {
        val result = viewModel.validateFields(hasProducts = false)
        assertFalse(result)
        val errors = viewModel.formState.value.errors
        assertTrue(errors.clientName)
        assertTrue(errors.phone)
        assertTrue(errors.location)
        assertTrue(errors.installment)
        assertTrue(errors.paymentFrequency)
        assertTrue(errors.collectionDay)
        assertTrue(errors.image)
        assertTrue(errors.products)
        assertTrue(errors.zone)
    }

    @Test
    fun `validateFields empty CREDITO form marca colonia poblacion y ciudad`() {
        viewModel.validateFields(hasProducts = false)
        val errors = viewModel.formState.value.errors
        assertTrue(errors.colonia)
        assertTrue(errors.poblacion)
        assertTrue(errors.ciudad)
    }

    @Test
    fun `validateFields valid CONTADO returns true`() {
        viewModel.updateClientName("Juan Perez")
        viewModel.updateStreet("Calle Principal 123")
        viewModel.updateColonia("Centro")
        viewModel.updatePoblacion("Tehuacán")
        viewModel.updateCiudad("Puebla")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.addImageUri(Uri.parse("content://test/image.jpg"))
        val result = viewModel.validateFields(hasProducts = true)
        assertTrue(result)
    }

    @Test
    fun `incidente - validateFields bloquea una venta de CONTADO con ciudad vacia`() {
        // Reproduce las 6 ventas del 2026-08-13 que se fueron sin ciudad.
        viewModel.updateClientName("Juan Hernández Cruz")
        viewModel.updateStreet("Avenida Independencia 45")
        viewModel.updateColonia("Centro")
        viewModel.updatePoblacion("Tehuacán")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.addImageUri(Uri.parse("content://test/image.jpg"))

        assertFalse(viewModel.validateFields(hasProducts = true))
        assertTrue(viewModel.formState.value.errors.ciudad)
    }

    @Test
    fun `incidente - validateFields bloquea una venta de CONTADO con telefono 000000`() {
        viewModel.updateClientName("Juan Hernández Cruz")
        viewModel.updateStreet("Avenida Independencia 45")
        viewModel.updateColonia("Centro")
        viewModel.updatePoblacion("Tehuacán")
        viewModel.updateCiudad("Puebla")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("000000")
        viewModel.addImageUri(Uri.parse("content://test/image.jpg"))

        assertFalse(viewModel.validateFields(hasProducts = true))
        assertTrue(viewModel.formState.value.errors.phone)
    }

    @Test
    fun `telefono vacio pasa en CONTADO de punta a punta`() {
        viewModel.updateClientName("Juan Hernández Cruz")
        viewModel.updateStreet("Avenida Independencia 45")
        viewModel.updateColonia("Centro")
        viewModel.updatePoblacion("Tehuacán")
        viewModel.updateCiudad("Puebla")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("")
        viewModel.addImageUri(Uri.parse("content://test/image.jpg"))

        assertTrue(viewModel.validateFields(hasProducts = true))
        assertEquals("", viewModel.buildSaleData().phone)
    }

    @Test
    fun `validateFields products error when no products`() {
        viewModel.updateClientName("Juan Perez")
        viewModel.updateStreet("Calle Principal 123")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.addImageUri(Uri.parse("content://test/image.jpg"))
        viewModel.validateFields(hasProducts = false)
        assertTrue(viewModel.formState.value.errors.products)
    }

    @Test
    fun `validateFields image error when no images`() {
        viewModel.updateClientName("Juan Perez")
        viewModel.updateStreet("Calle Principal 123")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.validateFields(hasProducts = true)
        assertTrue(viewModel.formState.value.errors.image)
    }

    // --- buildSaleData ---

    @Test
    fun `buildSaleData maps all fields`() {
        viewModel.updateClientName("Juan Perez")
        viewModel.updatePhone("5512345678")
        viewModel.updateStreet("Calle Principal")
        viewModel.updateNumero("123")
        viewModel.updateColonia("Centro")
        viewModel.updatePoblacion("CDMX")
        viewModel.updateCiudad("CDMX")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CREDITO")
        viewModel.updateInstallment("500")
        viewModel.updateDownpayment("200")
        viewModel.updatePaymentFrequency("SEMANAL")
        viewModel.updateCollectionDay("LUNES")
        viewModel.updateGuarantor("Maria")
        viewModel.updateNote("Test")
        viewModel.updateZone(1, "Zona Norte")
        viewModel.updateSelectedClienteId(42)

        val data = viewModel.buildSaleData()
        assertEquals("Juan Perez", data.clientName)
        assertEquals("5512345678", data.phone)
        assertEquals("Calle Principal", data.address)
        assertEquals("123", data.numero)
        assertEquals("Centro", data.colonia)
        assertEquals("CDMX", data.poblacion)
        assertEquals("CDMX", data.ciudad)
        assertEquals(19.432608, data.latitude, 0.001)
        assertEquals(-99.133209, data.longitude, 0.001)
        assertEquals("CREDITO", data.tipoVenta)
        assertEquals(500.0, data.installment, 0.001)
        assertEquals(200.0, data.downpayment, 0.001)
        assertEquals("SEMANAL", data.paymentFrequency)
        assertEquals("LUNES", data.collectionDay)
        assertEquals("Maria", data.guarantor)
        assertEquals("Test", data.note)
        assertEquals(1, data.zonaClienteId)
        assertEquals("Zona Norte", data.zonaClienteNombre)
        assertEquals(42, data.clienteId)
        assertNotNull(data.saleId)
        assertNotNull(data.saleDate)
    }

    @Test
    fun `buildSaleData CONTADO zeroes credit fields`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updateInstallment("500")
        viewModel.updateDownpayment("200")
        viewModel.updatePaymentFrequency("SEMANAL")
        viewModel.updateCollectionDay("LUNES")
        viewModel.updateGuarantor("Maria")

        val data = viewModel.buildSaleData()
        assertEquals(0.0, data.installment, 0.001)
        assertEquals(0.0, data.downpayment, 0.001)
        assertEquals("", data.paymentFrequency)
        assertEquals("", data.collectionDay)
        assertEquals("", data.guarantor)
    }

    @Test
    fun `buildSaleData blank optional fields become null`() {
        viewModel.updateNumero("")
        viewModel.updateColonia("")
        viewModel.updatePoblacion("")
        viewModel.updateCiudad("")
        val data = viewModel.buildSaleData()
        assertNull(data.numero)
        assertNull(data.colonia)
        assertNull(data.poblacion)
        assertNull(data.ciudad)
    }

    @Test
    fun `buildSaleData recorta espacios de la direccion`() {
        // El servidor valida DESPUÉS de trim: si persistiéramos "  Centro  ",
        // "   " pasaría el `ifBlank` de la app y llegaría vacío al API.
        viewModel.updateNumero("  45  ")
        viewModel.updateColonia("  Centro  ")
        viewModel.updatePoblacion("  Tehuacán  ")
        viewModel.updateCiudad("  Puebla  ")
        viewModel.updateStreet("  Avenida Independencia 45  ")
        val data = viewModel.buildSaleData()
        assertEquals("45", data.numero)
        assertEquals("Centro", data.colonia)
        assertEquals("Tehuacán", data.poblacion)
        assertEquals("Puebla", data.ciudad)
        assertEquals("Avenida Independencia 45", data.address)
    }

    @Test
    fun `buildSaleData blancos en la direccion terminan en null`() {
        viewModel.updateColonia("   ")
        viewModel.updateCiudad("\t ")
        val data = viewModel.buildSaleData()
        assertNull(data.colonia)
        assertNull(data.ciudad)
    }

    @Test
    fun `buildSaleData generates unique saleId`() {
        val data1 = viewModel.buildSaleData()
        val data2 = viewModel.buildSaleData()
        assertNotEquals(data1.saleId, data2.saleId)
    }

    // --- buildSaleData edge cases ---

    @Test
    fun `buildSaleData CREDITO preserves all credit fields`() {
        viewModel.updateTipoVenta("CREDITO")
        viewModel.updateInstallment("750")
        viewModel.updateDownpayment("300")
        viewModel.updatePaymentFrequency("QUINCENAL")
        viewModel.updateCollectionDay("MIERCOLES")
        viewModel.updateGuarantor("Pedro Garcia")

        val data = viewModel.buildSaleData()
        assertEquals("CREDITO", data.tipoVenta)
        assertEquals(750.0, data.installment, 0.001)
        assertEquals(300.0, data.downpayment, 0.001)
        assertEquals("QUINCENAL", data.paymentFrequency)
        assertEquals("MIERCOLES", data.collectionDay)
        assertEquals("Pedro Garcia", data.guarantor)
    }

    @Test
    fun `buildSaleData CONTADO zeroes installment even with valid value`() {
        viewModel.updateInstallment("999")
        viewModel.updateTipoVenta("CONTADO")
        val data = viewModel.buildSaleData()
        assertEquals(0.0, data.installment, 0.001)
    }

    @Test
    fun `buildSaleData CONTADO zeroes downpayment even with valid value`() {
        viewModel.updateDownpayment("500")
        viewModel.updateTipoVenta("CONTADO")
        val data = viewModel.buildSaleData()
        assertEquals(0.0, data.downpayment, 0.001)
    }

    @Test
    fun `buildSaleData CREDITO with invalid installment defaults to zero`() {
        viewModel.updateTipoVenta("CREDITO")
        viewModel.updateInstallment("abc")
        val data = viewModel.buildSaleData()
        assertEquals(0.0, data.installment, 0.001)
    }

    @Test
    fun `buildSaleData CREDITO with empty installment defaults to zero`() {
        viewModel.updateTipoVenta("CREDITO")
        viewModel.updateInstallment("")
        val data = viewModel.buildSaleData()
        assertEquals(0.0, data.installment, 0.001)
    }

    @Test
    fun `buildSaleData CREDITO with invalid downpayment defaults to zero`() {
        viewModel.updateTipoVenta("CREDITO")
        viewModel.updateDownpayment("xyz")
        val data = viewModel.buildSaleData()
        assertEquals(0.0, data.downpayment, 0.001)
    }

    @Test
    fun `buildSaleData blank phone becomes empty string`() {
        viewModel.updatePhone("   ")
        val data = viewModel.buildSaleData()
        assertEquals("", data.phone)
    }

    @Test
    fun `buildSaleData CONTADO preserves phone`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("5512345678")
        val data = viewModel.buildSaleData()
        assertEquals("5512345678", data.phone)
    }

    @Test
    fun `buildSaleData CONTADO preserves note`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updateNote("Entregar por la tarde")
        val data = viewModel.buildSaleData()
        assertEquals("Entregar por la tarde", data.note)
    }

    @Test
    fun `buildSaleData CONTADO preserves zonaClienteId as null after switching`() {
        viewModel.updateZone(1, "Zona Norte")
        viewModel.updateTipoVenta("CONTADO") // clears zone
        val data = viewModel.buildSaleData()
        assertNull(data.zonaClienteId)
    }

    @Test
    fun `buildSaleData CREDITO preserves zonaClienteId`() {
        viewModel.updateTipoVenta("CREDITO")
        viewModel.updateZone(3, "Zona Sur")
        val data = viewModel.buildSaleData()
        assertEquals(3, data.zonaClienteId)
        assertEquals("Zona Sur", data.zonaClienteNombre)
    }

    // --- applyDraft ---

    @Test
    fun `applyDraft restores all common fields to formState`() {
        val draft = SaleDraft(
            clientName = "Ana Garcia",
            phone = "5598765432",
            street = "Av Reforma 100",
            numero = "456",
            colonia = "Juarez",
            poblacion = "CDMX",
            ciudad = "CDMX",
            note = "Nota importante",
            latitude = 19.5,
            longitude = -99.2,
            tipoVenta = "CREDITO",
            downpayment = "300",
            installment = "600",
            guarantor = "Pedro",
            collectionDay = "VIERNES",
            paymentFrequency = "QUINCENAL",
            zonaClienteId = 2,
            zonaClienteNombre = "Zona Centro"
        )
        viewModel.applyDraft(draft)

        val state = viewModel.formState.value
        assertEquals("Ana Garcia", state.clientName)
        assertEquals("5598765432", state.phone)
        assertEquals("Av Reforma 100", state.street)
        assertEquals("456", state.numero)
        assertEquals("Juarez", state.colonia)
        assertEquals("CDMX", state.poblacion)
        assertEquals("CDMX", state.ciudad)
        assertEquals("Nota importante", state.note)
        assertEquals(19.5, state.latitude, 0.001)
        assertEquals(-99.2, state.longitude, 0.001)
        assertEquals("CREDITO", state.tipoVenta)
        assertEquals("300", state.downpayment)
        assertEquals("600", state.installment)
        assertEquals("Pedro", state.guarantor)
        assertEquals("VIERNES", state.collectionDay)
        assertEquals("QUINCENAL", state.paymentFrequency)
        assertEquals(2, state.selectedZoneId)
        assertEquals("Zona Centro", state.selectedZoneName)
    }

    @Test
    fun `applyDraft CONTADO restores tipoVenta and credit fields as-is`() {
        val draft = SaleDraft(
            clientName = "Test",
            tipoVenta = "CONTADO",
            downpayment = "",
            installment = "",
            guarantor = "",
            collectionDay = "",
            paymentFrequency = ""
        )
        viewModel.applyDraft(draft)

        val state = viewModel.formState.value
        assertEquals("CONTADO", state.tipoVenta)
        assertEquals("", state.downpayment)
        assertEquals("", state.installment)
        assertEquals("", state.guarantor)
    }

    @Test
    fun `applyDraft with nonexistent image paths filters them out`() {
        val draft = SaleDraft(
            clientName = "Test",
            imageUris = listOf("/nonexistent/path/image.jpg", "/also/nonexistent.jpg")
        )
        viewModel.applyDraft(draft)

        val state = viewModel.formState.value
        assertTrue(state.imageUris.isEmpty())
    }

    // --- Existing-cliente link survives the draft cycle (regression) ---

    @Test
    fun `applyDraft restores selectedClienteId for an existing cliente`() {
        val draft = SaleDraft(clientName = "EDITH FLORES MORENO", clienteId = 11513)
        viewModel.applyDraft(draft)
        assertEquals(11513, viewModel.formState.value.selectedClienteId)
    }

    @Test
    fun `applyDraft clears selectedClienteId for a new-cliente draft`() {
        // Stale in-memory value from a previous selection must be overwritten by
        // the draft (null = new cliente), so the sale is not sent with a wrong id.
        viewModel.updateSelectedClienteId(999)
        val draft = SaleDraft(clientName = "CLIENTE NUEVO", clienteId = null)
        viewModel.applyDraft(draft)
        assertNull(viewModel.formState.value.selectedClienteId)
    }

    // --- clearAllFields ---

    @Test
    fun `clearAllFields resets to default`() {
        viewModel.updateClientName("Juan")
        viewModel.updatePhone("5512345678")
        viewModel.clearAllFields()
        val state = viewModel.formState.value
        assertEquals("", state.clientName)
        assertEquals("", state.phone)
        assertEquals("CREDITO", state.tipoVenta)
        assertFalse(state.saleCompleted)
    }

    // --- markSaleCompleted ---

    @Test
    fun `markSaleCompleted sets flag`() {
        viewModel.markSaleCompleted()
        assertTrue(viewModel.formState.value.saleCompleted)
    }
}
