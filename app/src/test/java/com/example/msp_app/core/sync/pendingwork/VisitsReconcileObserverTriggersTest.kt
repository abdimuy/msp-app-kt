package com.example.msp_app.core.sync.pendingwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Fix round 1, finding 1: proves the app-open and periodic `WorkManager`
 * enqueues are independent — one throwing must NOT prevent the other from
 * registering. [registerVisitsReconcileWorkManagerTriggers] takes both
 * enqueues as lambdas specifically so this never touches Android/WorkManager
 * at all — Robolectric here is only to give `android.util.Log.w` (used on the
 * `onFailure` path) a working stub instead of the unmocked-Android throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class VisitsReconcileObserverTriggersTest {

    @Test
    fun `si el disparador de apertura lanza, el periodico se registra igual`() {
        var periodicoRegistrado = false

        registerVisitsReconcileWorkManagerTriggers(
            registerNow = { throw IllegalStateException("WorkManager no disponible") },
            registerPeriodic = { periodicoRegistrado = true }
        )

        assertTrue(
            "el backstop de 15 min no puede perderse solo porque el disparador de " +
                "apertura fallo",
            periodicoRegistrado
        )
    }

    @Test
    fun `si el periodico lanza, el de apertura ya corrio y no se deshace`() {
        var aperturaRegistrado = false

        registerVisitsReconcileWorkManagerTriggers(
            registerNow = { aperturaRegistrado = true },
            registerPeriodic = { throw IllegalStateException("WorkManager no disponible") }
        )

        assertTrue(aperturaRegistrado)
    }

    @Test
    fun `ninguno lanzando, los dos se registran`() {
        var llamadas = 0

        registerVisitsReconcileWorkManagerTriggers(
            registerNow = { llamadas++ },
            registerPeriodic = { llamadas++ }
        )

        assertEquals(2, llamadas)
    }
}
