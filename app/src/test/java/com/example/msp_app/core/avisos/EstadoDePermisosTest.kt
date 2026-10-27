package com.example.msp_app.core.avisos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** E-APP-134 · Qué permisos tiene el teléfono y cómo se reporta (plan P3, Tarea 15). */
@RunWith(RobolectricTestRunner::class)
class EstadoDePermisosTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [28])
    fun `apiMenorA31AlarmasExactasEsTrue - E-APP-134`() {
        assertTrue(EstadoDePermisos.leer(context).alarmasExactas)
    }

    @Test
    @Config(sdk = [33])
    fun `desde API 31 refleja el permiso de alarmas exactas - E-APP-134`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertEquals(false, EstadoDePermisos.leer(context).alarmasExactas)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertEquals(true, EstadoDePermisos.leer(context).alarmasExactas)
    }

    @Test
    fun `telemetriaIncluyeLosTresPermisosYDosConteosYNadaMas - E-APP-134`() {
        val mapa = TelemetriaDeVisitas.avisos(
            EstadoDePermisos(
                notificaciones = true,
                alarmasExactas = false,
                bateriaSinOptimizar = true
            ),
            programados = 4,
            disparados24h = 2
        )
        assertEquals(
            mapOf(
                "permiso.notificaciones" to 1,
                "permiso.alarmas_exactas" to 0,
                "bateria.sin_optimizar" to 1,
                "avisos.programados" to 4,
                "avisos.disparados_24h" to 2
            ),
            mapa
        )
    }

    @Test
    fun `la telemetria de avisos sale por su evento y pasa la lista blanca - E-APP-134`() {
        val salidas = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, d -> salidas += e to d }
        try {
            val d = TelemetriaDeVisitas.avisos(EstadoDePermisos(false, false, false), 0, 0)
            TelemetriaDeVisitas.emitir(TelemetriaDeVisitas.EVENTO_AVISOS, d)
        } finally {
            TelemetriaDeVisitas.sinkParaPrueba = null
        }
        assertEquals(1, salidas.size)
        assertEquals(5, salidas.single().second.size)
    }
}
