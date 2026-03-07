package com.example.msp_app.features.sales.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSaleFormStateTest {

    @Test
    fun `default state has empty client name`() {
        val state = NewSaleFormState()
        assertEquals("", state.clientName)
    }

    @Test
    fun `default tipoVenta is CREDITO`() {
        val state = NewSaleFormState()
        assertEquals("CREDITO", state.tipoVenta)
    }

    @Test
    fun `default imageUris is empty`() {
        val state = NewSaleFormState()
        assertTrue(state.imageUris.isEmpty())
    }

    @Test
    fun `default saleCompleted is false`() {
        val state = NewSaleFormState()
        assertFalse(state.saleCompleted)
    }

    @Test
    fun `default errors are all false`() {
        val state = NewSaleFormState()
        assertEquals(FormErrors(), state.errors)
    }

    @Test
    fun `default location is zero`() {
        val state = NewSaleFormState()
        assertEquals(0.0, state.latitude, 0.001)
        assertEquals(0.0, state.longitude, 0.001)
        assertFalse(state.hasValidLocation)
        assertFalse(state.locationPermissionGranted)
    }

    @Test
    fun `copy preserves fields`() {
        val state = NewSaleFormState(
            clientName = "Test",
            phone = "1234567890",
            tipoVenta = "CONTADO",
            street = "Calle Test"
        )
        val copied = state.copy(clientName = "Changed")
        assertEquals("Changed", copied.clientName)
        assertEquals("1234567890", copied.phone)
        assertEquals("CONTADO", copied.tipoVenta)
        assertEquals("Calle Test", copied.street)
    }
}
