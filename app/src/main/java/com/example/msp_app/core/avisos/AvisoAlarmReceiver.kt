package com.example.msp_app.core.avisos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.msp_app.core.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Suena una alarma de `AlarmManager` (plan visitas P3, Tarea 13; E-APP-124).
 *
 * Solo notifica si el aviso **sigue en `aviso_programado`**: si la tarea se
 * atendió o se canceló, o el compromiso cambió, la reprogramación ya lo sacó de
 * la tabla y esta alarma tardía no dice nada. Sin permiso de notificaciones no
 * hace nada. No toca ninguna tabla de dinero.
 */
class AvisoAlarmReceiver : BroadcastReceiver() {

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReprogramadorDeAlarmas.EXTRA_ID) ?: return
        val app = context.applicationContext
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificarSiVigente(app, id)
            } catch (e: Exception) {
                Log.w(TAG, "el aviso no se pudo mostrar; la lista sigue mostrando el compromiso", e)
            } finally {
                pendiente.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AvisoAlarmReceiver"

        internal suspend fun notificarSiVigente(context: Context, id: String) {
            val dao = AppDatabase.getInstance(context).avisosDao()
            val aviso = dao.programados().firstOrNull { it.ID == id } ?: return
            val notificador = NotificadorDeVisitaAsignada(context)
            if (!notificador.permitido()) return
            val (titulo, texto) = textoDe(aviso.TIPO)
            notificador.mostrar(
                titulo,
                texto,
                id.hashCode(),
                CanalesDeNotificacion.AVISOS_COMPROMISOS
            )
            ContadorDeDisparos.de(context).registrar(java.time.Instant.now())
        }

        /** Título y texto por tipo; sin datos del cliente (se verían en la pantalla bloqueada). */
        fun textoDe(tipo: String): Pair<String, String> = when (tipo) {
            PlanDeAvisos.TIPO_PROMESA -> "Promesa de pago" to "Por visitar"
            PlanDeAvisos.TIPO_CITA -> "Cita con el cliente" to "Por visitar"
            else -> "Visita por hacer" to "Por visitar"
        }
    }
}
