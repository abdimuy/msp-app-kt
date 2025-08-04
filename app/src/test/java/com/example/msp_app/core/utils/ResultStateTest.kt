package com.example.msp_app.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultStateTest {

    @Test
    fun `idle state should be instance of Idle`() {
        val idleState = ResultState.Idle
        assertTrue(idleState is ResultState.Idle)
    }

    @Test
    fun `loading state should be instance of Loading`() {
        val loadingState = ResultState.Loading
        assertTrue(loadingState is ResultState.Loading)
    }

    @Test
    fun `success state should contain data`() {
        val testData = "test data"
        val successState = ResultState.Success(testData)

        assertTrue(successState is ResultState.Success)
        assertEquals(testData, successState.data)
    }

    @Test
    fun `error state should contain message`() {
        val errorMessage = "Error occurred"
        val errorState = ResultState.Error(errorMessage)

        assertTrue(errorState is ResultState.Error)
        assertEquals(errorMessage, errorState.message)
    }

    @Test
    fun `success state with different data types`() {
        val intSuccess = ResultState.Success(42)
        val listSuccess = ResultState.Success(listOf(1, 2, 3))

        assertEquals(42, intSuccess.data)
        assertEquals(listOf(1, 2, 3), listSuccess.data)
    }
}