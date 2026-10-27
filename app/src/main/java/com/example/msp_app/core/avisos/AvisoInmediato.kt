package com.example.msp_app.core.avisos

import android.util.Log
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.visitas.domain.EstadoTarea
import com.example.msp_app.core.database.dao.avisos.AvisosDao
import com.example.msp_app.core.database.entities.TareaZonaEntity
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * Aviso inmediato "Visita asignada" (spec visitas §6.5; plan P3, Tarea 12;
 * E-APP-123).
 *
 * Cuelga de `TareasZonaSyncManager.alAplicar`: la tarea puede llegar por el
 * push, por el periódico de 15 min o por un resync, y en los tres casos el
 * cobrador ve UNA notificación. La llave es `aviso_emitido`
 * ([AvisosDao.marcarEmitido], `INSERT OR IGNORE`): el que inserta primero
 * notifica; los demás no.
 *
 * - Solo avisa una tarea `PENDIENTE`. Si el cobrador ya la atendió (visita o
 *   pago, `ATENDIDA`), si la oficina la canceló o si ya venció, no hay nada
 *   que avisar y **no** se marca: no ocupa la tabla ni se pierde nada. Una
 *   tarea sin estado en [alAplicar] tampoco avisa.
 * - **Marca primero, notifica después.** Si la notificación revienta o el
 *   permiso está negado, la tarea queda marcada y no se reintenta: el aviso es
 *   comodidad, nunca fuente de verdad (la lista sigue mostrando la tarea). El
 *   permiso negado se reporta en [reportarPermiso] (`permiso.notificaciones`).
 * - Nunca lanza salvo cancelación: una tarea mala no tumba a las demás ni el
 *   sync. No toca Payment, sales ni ninguna tabla de dinero.
 * - El texto no lleva la nota de la oficina (se vería en la pantalla bloqueada).
 */
class AvisoInmediato(
    private val dao: AvisosDao,
    private val notificar: (titulo: String, texto: String, id: Int) -> Unit,
    private val permitido: () -> Boolean,
    private val ahora: () -> Instant,
    private val hoy: () -> LocalDate,
    /** Folio o cliente de la venta de la tarea; vacío si no se sabe. */
    private val etiqueta: suspend (TareaZonaEntity) -> String = { "" },
    private val reportarPermiso: (Boolean) -> Unit = {}
) {

    @Suppress("TooGenericExceptionCaught")
    suspend fun alAplicar(tareas: List<TareaZonaEntity>, estados: Map<String, EstadoTarea>) {
        var huboAviso = false
        val permiso = permitido()
        for (t in tareas) {
            if (estados[t.ID] != EstadoTarea.PENDIENTE) continue
            try {
                if (!dao.marcarEmitido(t.ID, AppTime.toWireFormat(ahora()))) continue
                huboAviso = true
                if (permiso) {
                    notificar(TITULO, textoDeAviso(etiqueta(t), t, hoy()), idDeNotificacion(t.ID))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "el aviso de una tarea fallo; no se reintenta", e)
            }
        }
        if (huboAviso) reportarPermiso(permiso)
    }

    companion object {
        const val TITULO = "Visita asignada"
        private const val TAG = "AvisoInmediato"
    }
}

/**
 * Id de la notificación: estable por tarea, así una repetición reemplaza en
 * vez de apilar. `hashCode` de `String` está especificado (no cambia entre
 * versiones ni procesos).
 */
fun idDeNotificacion(tareaId: String): Int = tareaId.hashCode()

/** `<folio o cliente> · <día>`; el día es Hoy, Mañana o dd/MM/yyyy, y la hora si hay. */
fun textoDeAviso(etiqueta: String, t: TareaZonaEntity, hoy: LocalDate): String {
    val fecha = AppTime.parseWireDateOrNull(t.FECHA)
    val dia = when (fecha) {
        null -> t.FECHA
        hoy -> "Hoy"
        hoy.plusDays(1) -> "Mañana"
        else -> "%02d/%02d/%d".format(fecha.dayOfMonth, fecha.monthValue, fecha.year)
    }
    val cuando = if (t.HORA.isNullOrBlank()) dia else "$dia ${t.HORA}"
    return if (etiqueta.isBlank()) cuando else "$etiqueta · $cuando"
}
