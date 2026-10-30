package com.example.msp_app.core.avisos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.visitas.domain.TextoDeCita
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.sync.cobranza.CobranzaSyncProvider
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
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
            val (titulo, texto) = if (aviso.TIPO == PlanDeAvisos.TIPO_CITA) {
                textoDeCita(context, aviso.ORIGEN_ID)
            } else {
                textoDe(aviso.TIPO)
            }
            notificador.mostrar(
                titulo,
                texto,
                id.hashCode(),
                CanalesDeNotificacion.AVISOS_COMPROMISOS
            )
            ContadorDeDisparos.de(context).registrar(AppClock.System.now())
        }

        /** Título y texto por tipo, sin datos del cliente (promesas y tareas). */
        fun textoDe(tipo: String): Pair<String, String> = when (tipo) {
            PlanDeAvisos.TIPO_PROMESA -> "Promesa de pago" to "Por visitar"
            PlanDeAvisos.TIPO_CITA -> textoDeCita(null, null)
            else -> "Visita por hacer" to "Por visitar"
        }

        /**
         * `Cita con <cliente> a las <hora>` (decisión del dueño, 2026-10-30; el
         * nombre sí se ve en la pantalla bloqueada, a petición suya). Sin nombre
         * o sin hora no se inventa: queda `Cita con el cliente` o sin la hora.
         */
        fun textoDeCita(cliente: String?, hora: LocalTime?): Pair<String, String> =
            TextoDeCita.avisoDeCita(cliente?.takeIf { it.isNotBlank() } ?: "el cliente", hora) to
                "Por visitar"

        /** Nombre y hora de la cita [visitaId] leídos de Room (`Visit`, o la copia de la zona). */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun textoDeCita(context: Context, visitaId: String): Pair<String, String> =
            try {
                val db = AppDatabase.getInstance(context)
                val propia = db.visitDao().findVisitById(visitaId)
                val deZona = if (propia == null) {
                    CobranzaSyncProvider.userContextFlow.value?.zona
                        ?.let { db.visitaZonaDao().deZona(it) }
                        ?.firstOrNull { it.ID == visitaId }
                } else {
                    null
                }
                val clienteId = propia?.CLIENTE_ID ?: deZona?.CLIENTE_ID
                val nombre = clienteId?.let {
                    db.saleDao().getByClientId(it).firstOrNull()?.CLIENTE
                }
                val hora = (propia?.CITA_HORA ?: deZona?.CITA_HORA)?.let {
                    runCatching { LocalTime.parse(it) }.getOrNull()
                }
                textoDeCita(nombre, hora)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "no se pudo leer la cita del aviso; va sin nombre", e)
                textoDeCita(null, null)
            }
    }
}
