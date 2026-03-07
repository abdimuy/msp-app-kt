package com.example.msp_app.features.sales.viewmodels

import org.junit.Assert.*
import org.junit.Test

class FormErrorsTest {

    @Test
    fun `default FormErrors has all fields false`() {
        val errors = FormErrors()
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
    fun `single error field`() {
        val errors = FormErrors(clientName = true)
        assertTrue(errors.clientName)
        assertFalse(errors.phone)
    }

    @Test
    fun `all errors set`() {
        val errors = FormErrors(
            clientName = true,
            phone = true,
            location = true,
            installment = true,
            paymentFrequency = true,
            collectionDay = true,
            image = true,
            products = true,
            downpayment = true,
            zone = true
        )
        assertTrue(errors.clientName)
        assertTrue(errors.phone)
        assertTrue(errors.location)
        assertTrue(errors.installment)
        assertTrue(errors.paymentFrequency)
        assertTrue(errors.collectionDay)
        assertTrue(errors.image)
        assertTrue(errors.products)
        assertTrue(errors.downpayment)
        assertTrue(errors.zone)
    }

    @Test
    fun `copy preserves unchanged fields`() {
        val original = FormErrors(clientName = true, phone = true)
        val copied = original.copy(phone = false)
        assertTrue(copied.clientName)
        assertFalse(copied.phone)
    }

    @Test
    fun `equality works correctly`() {
        val a = FormErrors(clientName = true)
        val b = FormErrors(clientName = true)
        assertEquals(a, b)
        assertNotEquals(a, FormErrors())
    }
}
