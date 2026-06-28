package com.example.msp_app.data.models.sale.localsale

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatTest {

    @Test
    fun `redondea suma con basura de punto flotante a 2 decimales`() {
        // 100.1 + 200.2 == 300.29999999999995 en Double; el backend rechaza >2 decimales.
        val total = 100.1 + 200.2
        assertEquals("300.30", total.toMoneyString())
    }

    @Test
    fun `siempre emite exactamente 2 decimales`() {
        assertEquals("3400.00", 3400.0.toMoneyString())
        assertEquals("0.00", 0.0.toMoneyString())
        assertEquals("1234.50", 1234.5.toMoneyString())
    }

    @Test
    fun `redondea HALF_UP el tercer decimal`() {
        assertEquals("10.13", 10.125.toMoneyString())
        assertEquals("10.12", 10.124.toMoneyString())
    }

    @Test
    fun `no usa notacion cientifica para montos grandes`() {
        assertEquals("1000000.00", 1_000_000.0.toMoneyString())
    }
}
