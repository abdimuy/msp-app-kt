package com.example.msp_app.core.avisos

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/** E-APP-124 · La tabla se proyecta a AlarmManager sin duplicar (plan P3, Tarea 13). */
class ReprogramadorDeAlarmasTest : RoomTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val ahora = Instant.parse("2026-10-27T15:00:00Z")

    private fun aviso(origen: String, instante: String, tipo: String = "tarea") = Instant.parse(
        instante
    ).let {
        AvisoPlaneado(PlanDeAvisos.idDe(tipo, origen, it), it, tipo, origen)
    }

    private fun reprogramador(
        encendido: Boolean = true,
        reportes: MutableList<Boolean> = mutableListOf()
    ) = ReprogramadorDeAlarmas(context, db.avisosDao(), { encendido }, { reportes += it })

    private val alarmas get() = shadowOf(alarmManager).scheduledAlarms

    @Test
    fun `reprogramarEsIdempotente - E-APP-124`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val plan = listOf(
            aviso("T-1", "2026-10-28T14:00:00Z"),
            aviso("T-2", "2026-10-28T15:00:00Z")
        )
        val r = reprogramador()
        r.reprogramarTodo(plan)
        val primera = alarmas.map { it.triggerAtMs }
        r.reprogramarTodo(plan)
        r.reprogramarTodo(plan)

        assertEquals(2, alarmas.size)
        assertEquals(primera, alarmas.map { it.triggerAtMs })
        assertEquals(plan.map { it.id }, db.avisosDao().programados().map { it.ID })
    }

    @Test
    fun `cancelaLasQueYaNoEstan - E-APP-124`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val a = aviso("T-1", "2026-10-28T14:00:00Z")
        val b = aviso("T-2", "2026-10-28T15:00:00Z")
        val r = reprogramador()
        r.reprogramarTodo(listOf(a, b))
        r.reprogramarTodo(listOf(b))

        assertEquals(listOf(b.instante.toEpochMilli()), alarmas.map { it.triggerAtMs })
        assertEquals(listOf(b.id), db.avisosDao().programados().map { it.ID })
    }

    @Test
    fun `sinExactasDegradaAVentanaYReporta - E-APP-124`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val reportes = mutableListOf<Boolean>()
        reprogramador(
            reportes = reportes
        ).reprogramarTodo(listOf(aviso("T-1", "2026-10-28T14:00:00Z")))

        val alarma = alarmas.single()
        assertEquals(ReprogramadorDeAlarmas.VENTANA_MS, alarma.windowLengthMs)
        assertEquals(listOf(false), reportes)
    }

    @Test
    fun `con exactas pone la alarma exacta y no reporta - E-APP-124`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val reportes = mutableListOf<Boolean>()
        reprogramador(
            reportes = reportes
        ).reprogramarTodo(listOf(aviso("T-1", "2026-10-28T14:00:00Z")))

        assertEquals(0L, alarmas.single().windowLengthMs)
        assertTrue(alarmas.single().allowWhileIdle)
        assertTrue(reportes.isEmpty())
    }

    @Test
    fun `killSwitchApagadoCancelaTodo - E-APP-124`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val plan = listOf(aviso("T-1", "2026-10-28T14:00:00Z"))
        reprogramador().reprogramarTodo(plan)
        assertEquals(1, alarmas.size)

        reprogramador(encendido = false).reprogramarTodo(plan)

        assertTrue(alarmas.isEmpty())
        assertTrue(db.avisosDao().programados().isEmpty())
    }

    @Test
    fun `tras un reinicio la tabla reconstruye las alarmas sin recalcular - E-APP-124`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val futuro = aviso("T-1", "2026-10-28T14:00:00Z")
        val pasado = aviso("T-2", "2026-10-26T14:00:00Z")
        reprogramador().reprogramarTodo(listOf(futuro, pasado))
        // El reinicio borra las alarmas del sistema; la tabla queda.
        shadowOf(alarmManager).scheduledAlarms.toList().forEach {
            it.operation?.let { op -> alarmManager.cancel(op) }
        }
        assertFalse(alarmas.isNotEmpty())

        val desdeTabla = PlanDeAvisos.desdeTabla(db.avisosDao().programados(), ahora)
        reprogramador().reprogramarTodo(desdeTabla)

        assertEquals(listOf(futuro.instante.toEpochMilli()), alarmas.map { it.triggerAtMs })
    }
}
