package com.example.msp_app.core.utils

import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

class CurrencyUtilsTest {

    @Test
    fun `Double toCurrency formats Mexican pesos correctly with decimals`() {
        val amount = 1234.56
        val result = amount.toCurrency()
        
        assertEquals("$1,234.56", result)
    }

    @Test
    fun `Double toCurrency formats Mexican pesos correctly without decimals`() {
        val amount = 1234.56
        val result = amount.toCurrency(noDecimals = true)
        
        assertEquals("$1,235", result)
    }

    @Test
    fun `Int toCurrency formats Mexican pesos correctly`() {
        val amount = 1500
        val result = amount.toCurrency()
        
        assertEquals("$1,500.00", result)
    }

    @Test
    fun `Int toCurrency formats without decimals`() {
        val amount = 1500
        val result = amount.toCurrency(noDecimals = true)
        
        assertEquals("$1,500", result)
    }

    @Test
    fun `toCurrency handles zero correctly`() {
        val doubleZero = 0.0
        val intZero = 0
        
        assertEquals("$0.00", doubleZero.toCurrency())
        assertEquals("$0.00", intZero.toCurrency())
        assertEquals("$0", doubleZero.toCurrency(noDecimals = true))
    }

    @Test
    fun `toCurrency handles negative numbers`() {
        val negativeDouble = -999.99
        val negativeInt = -1000
        
        assertEquals("-$999.99", negativeDouble.toCurrency())
        assertEquals("-$1,000.00", negativeInt.toCurrency())
    }

    @Test
    fun `toCurrency works with US locale`() {
        val amount = 1234.56
        val usLocale = Locale.US
        val result = amount.toCurrency(locale = usLocale)
        
        assertEquals("$1,234.56", result)
    }

    @Test
    fun `toCurrency handles large numbers`() {
        val largeAmount = 1000000.0
        val result = largeAmount.toCurrency()
        
        assertEquals("$1,000,000.00", result)
    }

    @Test
    fun `toCurrency rounds correctly when removing decimals`() {
        val amount1 = 99.49
        val amount2 = 99.50
        
        assertEquals("$99", amount1.toCurrency(noDecimals = true))
        assertEquals("$100", amount2.toCurrency(noDecimals = true))
    }
}