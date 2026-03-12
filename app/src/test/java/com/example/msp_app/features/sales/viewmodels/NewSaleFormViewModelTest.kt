package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.`test-fixtures`.RobolectricTestBase
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
    fun `updatePhone skips validation for CONTADO`() {
        viewModel.updateTipoVenta("CONTADO")
        viewModel.updatePhone("123")
        assertFalse(viewModel.formState.value.errors.phone)
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
    fun `validateFields valid CONTADO returns true`() {
        viewModel.updateClientName("Juan Perez")
        viewModel.updateStreet("Calle Principal 123")
        viewModel.updateLocation(19.432608, -99.133209)
        viewModel.updateTipoVenta("CONTADO")
        viewModel.addImageUri(Uri.parse("content://test/image.jpg"))
        val result = viewModel.validateFields(hasProducts = true)
        assertTrue(result)
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
    fun `buildSaleData generates unique saleId`() {
        val data1 = viewModel.buildSaleData()
        val data2 = viewModel.buildSaleData()
        assertNotEquals(data1.saleId, data2.saleId)
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
