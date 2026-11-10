package com.example.msp_app.feature.compartirproductos.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-186: nada se lanza con la app en segundo plano, y el envío no se pierde. */
@OptIn(ExperimentalCoroutinesApi::class)
class HojaEnSegundoPlanoTest : RobolectricTestBase() {

    /** LifecycleRegistry solo guarda una referencia DÉBIL a su dueño: se retiene aquí para que no lo recoja el GC. */
    private val duenos = mutableListOf<LifecycleOwner>()

    private fun ciclo(estado: Lifecycle.State): LifecycleRegistry {
        val dueno = object : LifecycleOwner {
            lateinit var r: LifecycleRegistry
            override val lifecycle: Lifecycle get() = r
        }
        duenos += dueno
        return LifecycleRegistry.createUnsafe(dueno).also {
            dueno.r = it
            it.currentState = estado
        }
    }

    @Test fun `con la app en segundo plano el evento espera y se entrega al volver`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val registro = ciclo(Lifecycle.State.CREATED) // en segundo plano: no STARTED
        val canal = Channel<String>(Channel.BUFFERED)
        val recibidos = mutableListOf<String>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            recolectarMientrasEsteVisible(registro, canal.receiveAsFlow()) { recibidos += it }
        }
        canal.send("lanzar")
        testScheduler.advanceUntilIdle()
        assertTrue("en segundo plano no se lanza nada", recibidos.isEmpty())

        registro.currentState = Lifecycle.State.STARTED // la app vuelve
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("lanzar"), recibidos)
    }

    @Test fun `con la app a la vista el evento se entrega de inmediato y solo una vez`() = runTest(
        mainDispatcherRule.testDispatcher
    ) {
        val registro = ciclo(Lifecycle.State.RESUMED)
        val canal = Channel<String>(Channel.BUFFERED)
        val recibidos = mutableListOf<String>()
        backgroundScope.launch(mainDispatcherRule.testDispatcher) {
            recolectarMientrasEsteVisible(registro, canal.receiveAsFlow()) { recibidos += it }
        }
        canal.send("uno")
        testScheduler.advanceUntilIdle()
        registro.currentState = Lifecycle.State.CREATED
        registro.currentState = Lifecycle.State.RESUMED
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("uno"), recibidos)
    }

    @Test fun `mientras genera la hoja no se deja cerrar arrastrando`() {
        assertFalse(puedeCambiarLaHoja(generando = true))
        assertTrue(puedeCambiarLaHoja(generando = false))
    }
}
