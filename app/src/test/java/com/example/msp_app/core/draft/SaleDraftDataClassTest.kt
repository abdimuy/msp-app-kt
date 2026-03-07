package com.example.msp_app.core.draft

import org.junit.Assert.*
import org.junit.Test

class SaleDraftDataClassTest {

    @Test
    fun `default tipoVenta is CREDITO`() {
        val draft = SaleDraft()
        assertEquals("CREDITO", draft.tipoVenta)
    }

    @Test
    fun `hasData returns false for empty draft`() {
        val draft = SaleDraft()
        assertFalse(draft.hasData())
    }

    @Test
    fun `hasData returns true when clientName set`() {
        val draft = SaleDraft(clientName = "Juan")
        assertTrue(draft.hasData())
    }

    @Test
    fun `hasData returns true when phone set`() {
        val draft = SaleDraft(phone = "5512345678")
        assertTrue(draft.hasData())
    }

    @Test
    fun `hasData returns true when productsJson set`() {
        val draft = SaleDraft(productsJson = "[{\"articuloId\":1}]")
        assertTrue(draft.hasData())
    }

    @Test
    fun `isFieldRelevant CONTADO excludes credit fields`() {
        val draft = SaleDraft(tipoVenta = "CONTADO")
        assertFalse(draft.isFieldRelevant("downpayment"))
        assertFalse(draft.isFieldRelevant("installment"))
        assertFalse(draft.isFieldRelevant("guarantor"))
        assertFalse(draft.isFieldRelevant("collectionDay"))
        assertFalse(draft.isFieldRelevant("paymentFrequency"))
        assertTrue(draft.isFieldRelevant("clientName"))
        assertTrue(draft.isFieldRelevant("street"))
    }

    @Test
    fun `isFieldRelevant CREDITO includes all fields`() {
        val draft = SaleDraft(tipoVenta = "CREDITO")
        assertTrue(draft.isFieldRelevant("downpayment"))
        assertTrue(draft.isFieldRelevant("installment"))
        assertTrue(draft.isFieldRelevant("guarantor"))
        assertTrue(draft.isFieldRelevant("collectionDay"))
        assertTrue(draft.isFieldRelevant("paymentFrequency"))
        assertTrue(draft.isFieldRelevant("clientName"))
    }
}
