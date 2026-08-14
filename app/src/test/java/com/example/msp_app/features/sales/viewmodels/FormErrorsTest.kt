package com.example.msp_app.features.sales.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormErrorsTest {

    @Test
    fun `default FormErrors has all fields false`() {
        val errors = FormErrors()
        assertFalse(errors.clientName)
        assertFalse(errors.phone)
        assertFalse(errors.location)
        assertFalse(errors.colonia)
        assertFalse(errors.poblacion)
        assertFalse(errors.ciudad)
        assertFalse(errors.installment)
        assertFalse(errors.paymentFrequency)
        assertFalse(errors.collectionDay)
        assertFalse(errors.image)
        assertFalse(errors.products)
        assertFalse(errors.downpayment)
        assertFalse(errors.zone)
        assertFalse(errors.hasAny)
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
            colonia = true,
            poblacion = true,
            ciudad = true,
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
        assertTrue(errors.colonia)
        assertTrue(errors.poblacion)
        assertTrue(errors.ciudad)
        assertTrue(errors.installment)
        assertTrue(errors.paymentFrequency)
        assertTrue(errors.collectionDay)
        assertTrue(errors.image)
        assertTrue(errors.products)
        assertTrue(errors.downpayment)
        assertTrue(errors.zone)
        assertTrue(errors.hasAny)
    }

    /**
     * `hasAny` debe reaccionar a CADA bandera por separado. Si alguien agrega un
     * campo nuevo a [FormErrors] y olvida sumarlo a `hasAny`, el formulario
     * dejaría pasar una venta con ese error — que es la forma exacta en que
     * colonia/población/ciudad se quedaron sin validar hasta el 2026-08-13.
     */
    @Test
    fun `hasAny se enciende con cualquier bandera por separado`() {
        val banderas = listOf<(FormErrors) -> FormErrors>(
            { it.copy(clientName = true) },
            { it.copy(phone = true) },
            { it.copy(location = true) },
            { it.copy(colonia = true) },
            { it.copy(poblacion = true) },
            { it.copy(ciudad = true) },
            { it.copy(installment = true) },
            { it.copy(paymentFrequency = true) },
            { it.copy(collectionDay = true) },
            { it.copy(image = true) },
            { it.copy(products = true) },
            { it.copy(downpayment = true) },
            { it.copy(zone = true) }
        )
        banderas.forEach { encender ->
            assertTrue(encender(FormErrors()).hasAny)
        }
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
