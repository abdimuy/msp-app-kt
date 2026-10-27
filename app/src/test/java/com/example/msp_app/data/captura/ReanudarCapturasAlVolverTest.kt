package com.example.msp_app.data.captura

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.msp_app.feature.pagos.domain.port.ReanudarCapturasPort
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * E-APP-119 (D15): cada regreso de la app a primer plano reanuda las visitas y
 * garantías capturadas sin escribir; sin primer plano no reanuda nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ReanudarCapturasAlVolverTest {

    @get:Rule
    val compose = createComposeRule()

    private class Dueno : LifecycleOwner {
        val registro = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registro
    }

    @Test
    fun `E-APP-119 cada regreso a primer plano reanuda y sin primer plano no`() {
        var llamadas = 0
        val vm = ReanudarCapturasViewModel(ReanudarCapturasPort { llamadas++ })
        val dueno = Dueno().apply { registro.currentState = Lifecycle.State.CREATED }

        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides dueno) {
                ReanudarCapturasAlVolver(vm)
            }
        }
        compose.waitForIdle()
        assertEquals("control: en segundo plano no reanuda", 0, llamadas)

        compose.runOnIdle { dueno.registro.currentState = Lifecycle.State.STARTED }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, llamadas)

        compose.runOnIdle {
            dueno.registro.currentState = Lifecycle.State.CREATED
            dueno.registro.currentState = Lifecycle.State.STARTED
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("el segundo regreso reanuda otra vez", 2, llamadas)
    }
}
