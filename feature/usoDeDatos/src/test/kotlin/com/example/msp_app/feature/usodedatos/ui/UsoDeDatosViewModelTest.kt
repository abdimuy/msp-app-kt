package com.example.msp_app.feature.usodedatos.ui

import com.example.msp_app.core.datos.UsoDelDia
import com.example.msp_app.core.testing.time.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsoDeDatosViewModelTest {

    private class Fuente(var dias: List<UsoDelDia>) : FuenteDeUso {
        var lecturas = 0
        override suspend fun dias(): List<UsoDelDia> {
            lecturas++
            return dias
        }
    }

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `carga al abrir y refresca cada intervalo`() = runTest(dispatcher) {
        val fuente = Fuente(emptyList())
        val vm = UsoDeDatosViewModel(fuente, FakeClock.at("2026-11-10T18:00:00Z"), 5_000)
        val job = launch { vm.estado.collect { } }
        runCurrent()
        assertTrue(vm.estado.value.vacio)
        fuente.dias = listOf(UsoDelDia.vacio("2026-11-10").copy(rxTotal = 2_000_000, wifi = 2_000_000))
        advanceTimeBy(5_001)
        runCurrent()
        assertNotNull(vm.estado.value.hoy)
        assertEquals(2_000_000L, vm.estado.value.hoy!!.total)
        assertTrue(fuente.lecturas >= 2)
        job.cancel()
    }
}
