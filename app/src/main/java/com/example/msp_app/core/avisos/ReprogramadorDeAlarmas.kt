package com.example.msp_app.core.avisos

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.msp_app.core.database.dao.avisos.AvisosDao

/**
 * Proyecta la tabla `aviso_programado` a `AlarmManager` (spec visitas §6.6; plan
 * P3, Tarea 13; E-APP-124). La tabla es la fuente de verdad; las alarmas del
 * sistema se reconstruyen y reprogramar es idempotente: mismo id, misma alarma.
 *
 * - Exactas con `setExactAndAllowWhileIdle` solo si el permiso está; si no,
 *   `setWindow` de 10 min y se reporta `permiso.alarmas_exactas = false`.
 *   Nunca pide el permiso (lo maneja T15).
 * - Interruptor apagado: cancela todo y no pone nada.
 * - Una alarma que no se pudo poner no tumba a las demás: el compromiso sigue en
 *   la lista; la alarma es comodidad.
 */
class ReprogramadorDeAlarmas(
    private val context: Context,
    private val dao: AvisosDao,
    private val killSwitch: () -> Boolean,
    private val reportarExactas: (Boolean) -> Unit = {}
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    @Suppress("TooGenericExceptionCaught")
    suspend fun reprogramarTodo(plan: List<AvisoPlaneado>) {
        val deseado = if (killSwitch()) plan else emptyList()
        val anteriores = dao.reemplazarProgramados(deseado.map { it.aEntidad() })
        anteriores.forEach { cancelar(it.ID) }
        if (deseado.isEmpty()) return
        val exactas = alarmManager.canScheduleExactAlarms()
        for (a in deseado) {
            try {
                cancelar(a.id)
                poner(a, exactas)
            } catch (e: SecurityException) {
                Log.w(TAG, "el sistema nego una alarma; el compromiso sigue en la lista", e)
            }
        }
        if (!exactas) reportarExactas(false)
    }

    private fun poner(a: AvisoPlaneado, exactas: Boolean) {
        val pi = pendingIntent(context, a.id, crear = true) ?: return
        val ms = a.instante.toEpochMilli()
        if (exactas) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, ms, VENTANA_MS, pi)
        }
    }

    private fun cancelar(id: String) {
        pendingIntent(context, id, crear = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        private const val TAG = "ReprogramadorDeAlarmas"
        const val VENTANA_MS = 10L * 60L * 1000L
        const val EXTRA_ID = "aviso_id"

        /** Mismo intent para el mismo id: `data` lo distingue (`filterEquals` ignora extras). */
        internal fun pendingIntent(context: Context, id: String, crear: Boolean): PendingIntent? {
            val intent = Intent(context, AvisoAlarmReceiver::class.java)
                .setData(Uri.parse("msp-aviso://$id"))
                .putExtra(EXTRA_ID, id)
            val flags = PendingIntent.FLAG_IMMUTABLE or
                if (crear) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
            return PendingIntent.getBroadcast(context, id.hashCode(), intent, flags)
        }
    }
}
