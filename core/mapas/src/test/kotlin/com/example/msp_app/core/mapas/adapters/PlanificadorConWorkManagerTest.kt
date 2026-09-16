package com.example.msp_app.core.mapas.adapters

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * **"Solo con wifi", medido y no prometido.**
 *
 * Es la única de las tres frases de la pantalla de descarga que se puede
 * incumplir en silencio: un trabajo sin la restricción correría con datos
 * móviles y el cobrador se enteraría por el recibo. Acá se mira el trabajo
 * encolado de verdad, con la cola real de WorkManager.
 */
class PlanificadorConWorkManagerTest : RobolectricTestBase() {

    private lateinit var context: Context

    @Before
    fun levantarLaCola() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    private fun trabajos() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(PlanificadorConWorkManager.TRABAJO)
        .get()

    @Test
    fun `la descarga se encola con red NO MEDIDA`() {
        PlanificadorConWorkManager(context).encolarSoloConWifi()

        val trabajo = trabajos().single()
        assertEquals(NetworkType.UNMETERED, trabajo.constraints.requiredNetworkType)
    }

    /**
     * `KEEP` y no `REPLACE`: tocar "Descargar" dos veces no puede tirar los 12 MB
     * que ya bajaron. Con `REPLACE` el trabajo viejo se cancela y el parcial se
     * queda huérfano hasta el siguiente intento.
     */
    @Test
    fun `encolar dos veces deja UN solo trabajo`() {
        val planificador = PlanificadorConWorkManager(context)

        planificador.encolarSoloConWifi()
        val primero = trabajos().single().id
        planificador.encolarSoloConWifi()

        assertEquals(primero, trabajos().single().id)
    }

    @Test
    fun `cancelar deja el trabajo cancelado`() {
        val planificador = PlanificadorConWorkManager(context)
        planificador.encolarSoloConWifi()

        planificador.cancelar()

        assertEquals(WorkInfo.State.CANCELLED, trabajos().single().state)
    }
}
