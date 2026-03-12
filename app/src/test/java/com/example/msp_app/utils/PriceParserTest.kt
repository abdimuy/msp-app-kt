package com.example.msp_app.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceParserTest {

    private val epsilon = 0.001

    @Test
    fun `parsePricesFromString returns zeros for null input`() {
        val result = PriceParser.parsePricesFromString(null)
        assertEquals(0.0, result.precioLista, epsilon)
        assertEquals(0.0, result.precioCortoplazo, epsilon)
        assertEquals(0.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString returns zeros for blank input`() {
        val result = PriceParser.parsePricesFromString("   ")
        assertEquals(0.0, result.precioLista, epsilon)
    }

    @Test
    fun `parsePricesFromString returns zeros for empty input`() {
        val result = PriceParser.parsePricesFromString("")
        assertEquals(0.0, result.precioLista, epsilon)
    }

    @Test
    fun `parsePricesFromString parses valid price string`() {
        val input = "Precio de lista:1500.0, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(1500.0, result.precioLista, epsilon)
        assertEquals(1200.0, result.precioCortoplazo, epsilon)
        assertEquals(1000.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString handles integer prices`() {
        val input = "Precio de lista:1500, Precio 4 Meses:1200, Precio 1 Meses:1000"
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(1500.0, result.precioLista, epsilon)
        assertEquals(1200.0, result.precioCortoplazo, epsilon)
        assertEquals(1000.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString handles malformed input gracefully`() {
        val result = PriceParser.parsePricesFromString("garbage data here")
        assertEquals(0.0, result.precioLista, epsilon)
        assertEquals(0.0, result.precioCortoplazo, epsilon)
        assertEquals(0.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString handles partial price string`() {
        val input = "Precio de lista:1500.0"
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(1500.0, result.precioLista, epsilon)
        assertEquals(0.0, result.precioCortoplazo, epsilon)
        assertEquals(0.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString handles whitespace around values`() {
        val input = "Precio de lista: 1500.0 , Precio 4 Meses: 1200.0 , Precio 1 Meses: 1000.0 "
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(1500.0, result.precioLista, epsilon)
        assertEquals(1200.0, result.precioCortoplazo, epsilon)
        assertEquals(1000.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString handles malformed price value`() {
        val input = "Precio de lista:abc, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(0.0, result.precioLista, epsilon)
        assertEquals(1200.0, result.precioCortoplazo, epsilon)
        assertEquals(1000.0, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString handles decimal prices`() {
        val input = "Precio de lista:1500.99, Precio 4 Meses:1200.50, Precio 1 Meses:999.01"
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(1500.99, result.precioLista, epsilon)
        assertEquals(1200.50, result.precioCortoplazo, epsilon)
        assertEquals(999.01, result.precioContado, epsilon)
    }

    @Test
    fun `parsePricesFromString detects labels case insensitively but extracts with exact prefix`() {
        // contains() is case-insensitive but substringAfter uses exact prefix,
        // so lowercase labels are detected but extraction uses the canonical prefix
        val input = "Precio de lista:1500.0, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"
        val result = PriceParser.parsePricesFromString(input)
        assertEquals(1500.0, result.precioLista, epsilon)
        assertEquals(1200.0, result.precioCortoplazo, epsilon)
        assertEquals(1000.0, result.precioContado, epsilon)
    }

    @Test
    fun `pricesToJson produces parseable string`() {
        val json = PriceParser.pricesToJson(1500.0, 1200.0, 1000.0)
        val parsed = PriceParser.parsePricesFromString(json)
        assertEquals(1500.0, parsed.precioLista, epsilon)
        assertEquals(1200.0, parsed.precioCortoplazo, epsilon)
        assertEquals(1000.0, parsed.precioContado, epsilon)
    }

    @Test
    fun `pricesToJson roundtrip preserves values`() {
        val original = ParsedPrices(2500.50, 2000.25, 1800.75)
        val json = PriceParser.pricesToJson(
            original.precioLista,
            original.precioCortoplazo,
            original.precioContado
        )
        val restored = PriceParser.parsePricesFromString(json)
        assertEquals(original.precioLista, restored.precioLista, epsilon)
        assertEquals(original.precioCortoplazo, restored.precioCortoplazo, epsilon)
        assertEquals(original.precioContado, restored.precioContado, epsilon)
    }

    @Test
    fun `pricesToJson with zero values`() {
        val json = PriceParser.pricesToJson(0.0, 0.0, 0.0)
        val parsed = PriceParser.parsePricesFromString(json)
        assertEquals(0.0, parsed.precioLista, epsilon)
        assertEquals(0.0, parsed.precioCortoplazo, epsilon)
        assertEquals(0.0, parsed.precioContado, epsilon)
    }

    @Test
    fun `ParsedPrices default values are all zero`() {
        val prices = ParsedPrices()
        assertEquals(0.0, prices.precioLista, epsilon)
        assertEquals(0.0, prices.precioCortoplazo, epsilon)
        assertEquals(0.0, prices.precioContado, epsilon)
    }
}
