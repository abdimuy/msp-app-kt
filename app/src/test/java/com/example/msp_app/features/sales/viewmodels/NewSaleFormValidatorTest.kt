package com.example.msp_app.features.sales.viewmodels

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class NewSaleFormValidatorTest {

    // --- Phone validation ---

    @Test
    fun `validatePhone CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validatePhone("", "CONTADO"))
        assertTrue(NewSaleFormValidator.validatePhone("123", "CONTADO"))
    }

    @Test
    fun `validatePhone valid 10 digit`() {
        assertTrue(NewSaleFormValidator.validatePhone("5512345678", "CREDITO"))
    }

    @Test
    fun `validatePhone empty is invalid for CREDITO`() {
        assertFalse(NewSaleFormValidator.validatePhone("", "CREDITO"))
    }

    @Test
    fun `validatePhone 9 digits invalid`() {
        assertFalse(NewSaleFormValidator.validatePhone("551234567", "CREDITO"))
    }

    @Test
    fun `validatePhone 11 digits invalid`() {
        assertFalse(NewSaleFormValidator.validatePhone("55123456789", "CREDITO"))
    }

    @Test
    fun `validatePhone blank invalid for CREDITO`() {
        assertFalse(NewSaleFormValidator.validatePhone("   ", "CREDITO"))
    }

    // --- Installment validation ---

    @Test
    fun `validateInstallment CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validateInstallment("", "CONTADO"))
    }

    @Test
    fun `validateInstallment positive int valid`() {
        assertTrue(NewSaleFormValidator.validateInstallment("500", "CREDITO"))
    }

    @Test
    fun `validateInstallment zero invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("0", "CREDITO"))
    }

    @Test
    fun `validateInstallment negative invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("-100", "CREDITO"))
    }

    @Test
    fun `validateInstallment non-numeric invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("abc", "CREDITO"))
    }

    @Test
    fun `validateInstallment decimal invalid`() {
        assertFalse(NewSaleFormValidator.validateInstallment("100.5", "CREDITO"))
    }

    // --- PaymentFrequency validation ---

    @Test
    fun `validatePaymentFrequency CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validatePaymentFrequency("", "CONTADO"))
    }

    @Test
    fun `validatePaymentFrequency non-blank valid`() {
        assertTrue(NewSaleFormValidator.validatePaymentFrequency("SEMANAL", "CREDITO"))
    }

    @Test
    fun `validatePaymentFrequency blank invalid`() {
        assertFalse(NewSaleFormValidator.validatePaymentFrequency("", "CREDITO"))
    }

    @Test
    fun `validatePaymentFrequency whitespace invalid`() {
        assertFalse(NewSaleFormValidator.validatePaymentFrequency("   ", "CREDITO"))
    }

    // --- CollectionDay validation ---

    @Test
    fun `validateCollectionDay CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validateCollectionDay("", "CONTADO"))
    }

    @Test
    fun `validateCollectionDay non-blank valid`() {
        assertTrue(NewSaleFormValidator.validateCollectionDay("LUNES", "CREDITO"))
    }

    @Test
    fun `validateCollectionDay blank invalid`() {
        assertFalse(NewSaleFormValidator.validateCollectionDay("", "CREDITO"))
    }

    // --- ClientName validation ---

    @Test
    fun `validateClientName 3 chars valid`() {
        assertTrue(NewSaleFormValidator.validateClientName("Ana"))
    }

    @Test
    fun `validateClientName long name valid`() {
        assertTrue(NewSaleFormValidator.validateClientName("Juan Carlos Perez"))
    }

    @Test
    fun `validateClientName 2 chars invalid`() {
        assertFalse(NewSaleFormValidator.validateClientName("AB"))
    }

    @Test
    fun `validateClientName blank invalid`() {
        assertFalse(NewSaleFormValidator.validateClientName("   "))
    }

    @Test
    fun `validateClientName empty invalid`() {
        assertFalse(NewSaleFormValidator.validateClientName(""))
    }

    // --- Street validation ---

    @Test
    fun `validateStreet 5 chars valid`() {
        assertTrue(NewSaleFormValidator.validateStreet("Calle"))
    }

    @Test
    fun `validateStreet long address valid`() {
        assertTrue(NewSaleFormValidator.validateStreet("Calle Principal 123"))
    }

    @Test
    fun `validateStreet 4 chars invalid`() {
        assertFalse(NewSaleFormValidator.validateStreet("Call"))
    }

    @Test
    fun `validateStreet blank invalid`() {
        assertFalse(NewSaleFormValidator.validateStreet("   "))
    }

    // --- Downpayment validation ---

    @Test
    fun `validateDownpayment blank valid`() {
        assertTrue(NewSaleFormValidator.validateDownpayment(""))
    }

    @Test
    fun `validateDownpayment zero valid`() {
        assertTrue(NewSaleFormValidator.validateDownpayment("0"))
    }

    @Test
    fun `validateDownpayment positive valid`() {
        assertTrue(NewSaleFormValidator.validateDownpayment("200"))
    }

    @Test
    fun `validateDownpayment negative invalid`() {
        assertFalse(NewSaleFormValidator.validateDownpayment("-100"))
    }

    @Test
    fun `validateDownpayment non-numeric invalid`() {
        assertFalse(NewSaleFormValidator.validateDownpayment("abc"))
    }

    // --- Zone validation ---

    @Test
    fun `validateZone CONTADO always valid`() {
        assertTrue(NewSaleFormValidator.validateZone("CONTADO", null, ""))
    }

    @Test
    fun `validateZone CREDITO with valid zone`() {
        assertTrue(NewSaleFormValidator.validateZone("CREDITO", 1, "Zona Norte"))
    }

    @Test
    fun `validateZone CREDITO null zoneId invalid`() {
        assertFalse(NewSaleFormValidator.validateZone("CREDITO", null, "Zona Norte"))
    }

    @Test
    fun `validateZone CREDITO blank zoneName invalid`() {
        assertFalse(NewSaleFormValidator.validateZone("CREDITO", 1, ""))
    }

    // --- Location validation ---

    @Test
    fun `validateLocation valid coords and permission`() {
        assertTrue(NewSaleFormValidator.validateLocation(19.432608, -99.133209, true))
    }

    @Test
    fun `validateLocation zero latitude invalid`() {
        assertFalse(NewSaleFormValidator.validateLocation(0.0, -99.133209, true))
    }

    @Test
    fun `validateLocation no permission invalid`() {
        assertFalse(NewSaleFormValidator.validateLocation(19.432608, -99.133209, false))
    }

    // --- validateAll ---

    @Test
    fun `validateAll empty CREDITO form has all errors`() {
        val state = NewSaleFormState()
        val errors = NewSaleFormValidator.validateAll(state, hasProducts = false)
        assertTrue(errors.clientName)
        assertTrue(errors.phone)
        assertTrue(errors.location)
        assertTrue(errors.installment)
        assertTrue(errors.paymentFrequency)
        assertTrue(errors.collectionDay)
        assertTrue(errors.image)
        assertTrue(errors.products)
        assertFalse(errors.downpayment)
        assertTrue(errors.zone)
    }

    @Test
    fun `validateAll valid CREDITO form has no errors`() {
        val state = NewSaleFormState(
            clientName = "Juan Perez",
            phone = "5512345678",
            street = "Calle Principal 123",
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true,
            hasValidLocation = true,
            tipoVenta = "CREDITO",
            installment = "500",
            paymentFrequency = "SEMANAL",
            collectionDay = "LUNES",
            selectedZoneId = 1,
            selectedZoneName = "Zona Norte",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
        val errors = NewSaleFormValidator.validateAll(state, hasProducts = true)
        assertFalse(errors.clientName)
        assertFalse(errors.phone)
        assertFalse(errors.location)
        assertFalse(errors.installment)
        assertFalse(errors.paymentFrequency)
        assertFalse(errors.collectionDay)
        assertFalse(errors.image)
        assertFalse(errors.products)
        assertFalse(errors.downpayment)
        assertFalse(errors.zone)
    }

    @Test
    fun `validateAll CONTADO skips credit fields`() {
        val state = NewSaleFormState(
            clientName = "Juan Perez",
            phone = "",
            street = "Calle Principal 123",
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true,
            hasValidLocation = true,
            tipoVenta = "CONTADO",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
        val errors = NewSaleFormValidator.validateAll(state, hasProducts = true)
        assertFalse(errors.clientName)
        assertFalse(errors.phone)
        assertFalse(errors.location)
        assertFalse(errors.installment)
        assertFalse(errors.paymentFrequency)
        assertFalse(errors.collectionDay)
        assertFalse(errors.image)
        assertFalse(errors.products)
        assertFalse(errors.downpayment)
        assertFalse(errors.zone)
    }

    // --- isAllValid ---

    @Test
    fun `isAllValid returns false for empty state`() {
        assertFalse(NewSaleFormValidator.isAllValid(NewSaleFormState(), hasProducts = false))
    }

    @Test
    fun `isAllValid returns true for complete CREDITO`() {
        val state = NewSaleFormState(
            clientName = "Juan Perez",
            phone = "5512345678",
            street = "Calle Principal 123",
            latitude = 19.432608,
            longitude = -99.133209,
            locationPermissionGranted = true,
            tipoVenta = "CREDITO",
            installment = "500",
            paymentFrequency = "SEMANAL",
            collectionDay = "LUNES",
            selectedZoneId = 1,
            selectedZoneName = "Zona Norte",
            imageUris = listOf(Uri.parse("content://test/image.jpg"))
        )
        assertTrue(NewSaleFormValidator.isAllValid(state, hasProducts = true))
    }
}
