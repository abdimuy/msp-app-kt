package com.example.msp_app.core.avisos

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.database.entities.AvisoProgramadoEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/** E-APP-124 · Los receptores (plan P3, Tarea 13). */
class ReceptoresTest : RoomTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun iniciarWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
    }

    private fun encolados(): List<WorkInfo> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(ReprogramarAvisosWorker.NOMBRE_UNICO).get()

    @Test
    fun `receptoresReprogramanTodo - E-APP-124`() {
        val acciones = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
        assertEquals(acciones.toSet(), ReprogramarAlarmasReceiver.ACCIONES)
        for (accion in acciones) {
            WorkManagerTestInitHelper.initializeTestWorkManager(context)
            assertTrue("antes de $accion", encolados().isEmpty())
            ReprogramarAlarmasReceiver().onReceive(context, Intent(accion))
            assertEquals("tras $accion", 1, encolados().size)
        }
    }

    @Test
    fun `una accion ajena no encola nada - E-APP-124`() {
        ReprogramarAlarmasReceiver().onReceive(context, Intent("com.otra.accion"))
        assertTrue(encolados().isEmpty())
    }

    @Test
    fun `dos disparos seguidos dejan un solo trabajo - E-APP-124`() {
        repeat(
            2
        ) { ReprogramarAlarmasReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED)) }
        assertEquals(1, encolados().size)
    }

    private fun notificaciones() =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Test
    fun `la alarma de un aviso vigente notifica y la de uno que ya no esta no - E-APP-124`() =
        runTest {
            shadowOf(context as android.app.Application)
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            db.avisosDao().reemplazarProgramados(
                listOf(AvisoProgramadoEntity("A-1", "2026-10-28T14:00:00Z", "promesa", "V-1"))
            )

            AvisoAlarmReceiver.notificarSiVigente(context, "A-OTRO")
            assertTrue(notificaciones().isEmpty())

            AvisoAlarmReceiver.notificarSiVigente(context, "A-1")
            assertEquals(1, notificaciones().size)
        }

    /** E-APP-134 (revision de T15): el receptor cuenta el disparo; si deja de llamarlo, esta prueba se pone roja. */
    @Test
    fun `la alarma que notifica queda contada en las ultimas 24 horas - E-APP-134`() = runTest {
        shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        db.avisosDao().reemplazarProgramados(
            listOf(AvisoProgramadoEntity("A-1", "2026-10-28T14:00:00Z", "promesa", "V-1"))
        )
        val antes = ContadorDeDisparos.de(context).ultimas24h(java.time.Instant.now())

        AvisoAlarmReceiver.notificarSiVigente(context, "A-OTRO")
        assertEquals(antes, ContadorDeDisparos.de(context).ultimas24h(java.time.Instant.now()))

        AvisoAlarmReceiver.notificarSiVigente(context, "A-1")
        assertEquals(antes + 1, ContadorDeDisparos.de(context).ultimas24h(java.time.Instant.now()))
    }

    /** E-APP-134 (revision de T15): el trabajo emite `visitas_avisos`; si quitan reportarAvisos, se pone roja. */
    @Test
    fun `reprogramar por omision emite la telemetria de avisos - E-APP-134`() = runTest {
        val emitidos = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, d -> emitidos += e to d }
        try {
            ReprogramarAvisosWorker.reprogramarPorOmision(context)
        } finally {
            TelemetriaDeVisitas.sinkParaPrueba = null
        }
        val avisos = emitidos.filter { it.first == TelemetriaDeVisitas.EVENTO_AVISOS }
        // Al menos uno: otro trabajo de avisos puede correr a la vez en la suite completa.
        assertTrue(avisos.isNotEmpty())
        assertTrue(avisos.all { it.second.containsKey(TelemetriaDeVisitas.AVISOS_PROGRAMADOS) })
    }
}
