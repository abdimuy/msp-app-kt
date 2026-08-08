package com.example.msp_app.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainDispatcherRuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `una corrutina lanzada en Dispatchers Main corre bajo el test dispatcher de la regla`() =
        runTest {
            var executed = false

            launch(Dispatchers.Main) {
                executed = true
            }

            assertEquals(true, executed)
        }
}
