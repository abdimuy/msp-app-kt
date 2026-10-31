package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-153 (H7 de la compuerta DESPUES, solo diagnostico): mide el backoff de WorkManager del
 * trabajo REAL que encola `enqueuePendingPaymentsWorker`. El encolado no fija `setBackoffCriteria`,
 * asi que rige el valor por defecto de la biblioteca; cada `Result.retry()` del worker (red caida,
 * timeout, 5xx) lo hace esperar el doble que la vez anterior. Si el worker devuelve retry N veces
 * seguidas (p. ej. dentro de App Standby, donde el POST se agota por tiempo), la siguiente corrida
 * tarda lo que dice esta tabla aunque la red vuelva antes.
 */
class PendingPaymentsBackoffTest : RoomTestBase() {

    @Test
    fun `E-APP-153 el trabajo de pagos usa el backoff exponencial por defecto de 30 s con red como unica restriccion`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        enqueuePendingPaymentsWorker(context, "pago-backoff")
        val id = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork("sync_pending_payments_pago-backoff").get().single().id
        val spec = WorkManagerImpl.getInstance(context).workDatabase.workSpecDao()
            .getWorkSpec(id.toString())!!

        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(30_000L, spec.backoffDelayDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)

        // Espera previa a la corrida N tras N-1 retry seguidos (runAttemptCount = N-1).
        val esperas = (1..8).map { intentos ->
            val s = spec.copy(runAttemptCount = intentos, lastEnqueueTime = 0L)
            s.calculateNextRunTime() / 1000
        }
        println("E-APP-153 backoff (s) tras 1..8 retry seguidos: $esperas")
        assertEquals(listOf(30L, 60L, 120L, 240L, 480L, 960L, 1920L, 3840L), esperas)
        // Acumulado hasta la 5.a corrida: 30+60+120+240 s = 7.5 min sin ningun POST.
        assertTrue(esperas.take(4).sum() == 450L)
    }
}
