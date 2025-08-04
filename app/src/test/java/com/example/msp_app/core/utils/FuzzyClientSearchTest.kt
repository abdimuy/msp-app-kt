package com.example.msp_app.core.utils

import org.junit.Test
import org.junit.Assert.*

class FuzzyClientSearchTest {

    data class TestClient(val name: String)

    private val testClients = listOf(
        TestClient("Juan Pérez"),
        TestClient("María González"), 
        TestClient("Carlos Rodríguez"),
        TestClient("Ana López"),
        TestClient("Pedro Martínez")
    )

    @Test
    fun `searchSimilarItems should return empty list for empty query`() {
        val result = searchSimilarItems(
            query = "",
            items = testClients,
            selectText = { it.name }
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchSimilarItems should return empty list for whitespace query`() {
        val result = searchSimilarItems(
            query = "   ",
            items = testClients,
            selectText = { it.name }
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `searchSimilarItems should find exact match`() {
        val result = searchSimilarItems(
            query = "Juan",
            items = testClients,
            threshold = 50,
            selectText = { it.name }
        )
        assertTrue(result.isNotEmpty())
        assertEquals("Juan Pérez", result.first().name)
    }

    @Test
    fun `searchSimilarItems should handle accents`() {
        val result = searchSimilarItems(
            query = "Maria", // sin acento
            items = testClients,
            threshold = 50,
            selectText = { it.name }
        )
        assertTrue(result.any { it.name.contains("María") })
    }

    @Test
    fun `searchSimilarItems should be case insensitive`() {
        val result = searchSimilarItems(
            query = "juan",
            items = testClients,
            threshold = 50,
            selectText = { it.name }
        )
        assertTrue(result.any { it.name.contains("Juan") })
    }

    @Test
    fun `searchSimilarItems should respect threshold`() {
        val highThresholdResult = searchSimilarItems(
            query = "xyz",
            items = testClients,
            threshold = 90,
            selectText = { it.name }
        )
        assertTrue(highThresholdResult.isEmpty())

        val lowThresholdResult = searchSimilarItems(
            query = "an", // partial match
            items = testClients,
            threshold = 20,
            selectText = { it.name }
        )
        assertTrue(lowThresholdResult.isNotEmpty())
    }

    @Test
    fun `searchSimilarItems should work with empty items list`() {
        val result = searchSimilarItems(
            query = "test",
            items = emptyList<TestClient>(),
            selectText = { it.name }
        )
        assertTrue(result.isEmpty())
    }
}