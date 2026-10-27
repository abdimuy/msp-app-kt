package com.example.msp_app.core.sync.visitas

import android.util.Log
import com.example.msp_app.core.common.visitas.domain.CompromisoVigente
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.envio.ConciliacionAgendaConteos
import com.example.msp_app.data.api.services.visits.ConciliacionAgendaRequest
import com.example.msp_app.data.api.services.visits.ItemConciliacionDto
import com.example.msp_app.data.api.services.visits.VisitasAgendaApi
import java.time.Instant
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/** Por qué la conciliación directa no se hizo sin que sea una falla. */
enum class MotivoOmitida {
    /** Más ventas de las que el servidor acepta en un cuerpo (5,000): no se manda un cuerpo que se sabe 422. */
    DEMASIADAS_VENTAS,

    /** 404/405: un servidor sin la ruta (API vieja); no hay nada que reintentar. */
    SERVIDOR_SIN_LA_RUTA
}

sealed interface ResultadoConciliacionDeAgenda {
    /**
     * El servidor contestó. [diferencias] > 0 es la alerta del mismo día (spec
     * §12-2). [ventasConDiferencia] son solo ids numéricos de venta.
     */
    data class Ok(
        val items: Int,
        val diferencias: Int,
        val ventasConDiferencia: List<Int>
    ) : ResultadoConciliacionDeAgenda

    data class Omitida(val motivo: MotivoOmitida, val items: Int = 0) :
        ResultadoConciliacionDeAgenda

    /** No se pudo conciliar; se reintenta en la siguiente corrida. Nada se escribió en ningún lado. */
    data class Fallida(val causa: String, val codigoHttp: Int? = null) :
        ResultadoConciliacionDeAgenda
}

/** Solo conteos, para la telemetría (E-APP-131). */
fun ResultadoConciliacionDeAgenda.conteos(): ConciliacionAgendaConteos = when (this) {
    is ResultadoConciliacionDeAgenda.Ok ->
        ConciliacionAgendaConteos(items, diferencias, fallida = false, omitida = false)
    is ResultadoConciliacionDeAgenda.Omitida ->
        ConciliacionAgendaConteos(items, 0, fallida = false, omitida = true)
    is ResultadoConciliacionDeAgenda.Fallida ->
        ConciliacionAgendaConteos(0, 0, fallida = true, omitida = false)
}

/**
 * Conciliación directa diaria de la agenda (plan visitas P2, Tarea 18; spec
 * §4.6-2; E-APP-131): resume la agenda que [agenda] deriva para la zona y el
 * "hoy" del negocio, la manda al servidor y cuenta las diferencias que éste
 * devuelve.
 *
 * ## Qué lee y qué escribe
 *
 * - **Solo lee dinero** (E-APP-131): [AgendaLocal] lee `Payment` y `sales` con
 *   SELECT; esta clase no tiene DAO ni escribe en Room. Lo único que sale del
 *   teléfono es el cuerpo de [VisitasAgendaApi.conciliar] (ids numéricos de
 *   venta, UUID de visita, estado).
 * - **No usa la bandeja de dinero** (E-VIS-116): la ruta del servidor está
 *   fuera del prefijo capturado, así que un 4xx/5xx no deja fila en
 *   `MSP_FAILED_INTENTS`. El reintento es propio: [Fallida] no se anota y la
 *   siguiente corrida del worker diario vuelve a intentar.
 * - **Nunca lanza** salvo cancelación.
 *
 * ## Qué diferencias son esperables
 *
 * El abono no aplicado (`-2`) cumple la promesa en el teléfono y el servidor no
 * lo ve (límite conocido E-APP-125); una cuenta podada del teléfono o una visita
 * recién capturada que aún no llegó también difieren. Por eso el juego
 * compartido `conciliacion-agenda` trae esos casos como control positivo.
 */
class ConciliacionDeAgenda(
    private val api: VisitasAgendaApi,
    private val agenda: AgendaLocal
) {

    @Suppress("TooGenericExceptionCaught")
    suspend fun conciliar(zona: Int, ahora: Instant): ResultadoConciliacionDeAgenda {
        return try {
            val hoy = AgendaLocal.hoyDe(ahora)
            val items = itemsDe(agenda.derivar(zona, hoy))
            if (items.size > VisitasAgendaApi.MAX_ITEMS) {
                return ResultadoConciliacionDeAgenda.Omitida(
                    MotivoOmitida.DEMASIADAS_VENTAS,
                    items.size
                )
            }
            val diferencias = api.conciliar(ConciliacionAgendaRequest(zona, hoy.toString(), items))
                .diferencias
                ?: return ResultadoConciliacionDeAgenda.Fallida("respuesta_sin_diferencias")
            val ventas = diferencias.mapNotNull { it.venta_id }.sorted()
            Log.i(
                TAG,
                "conciliacion de la agenda: items=${items.size} diferencias=${diferencias.size} ventas=$ventas"
            )
            ResultadoConciliacionDeAgenda.Ok(items.size, diferencias.size, ventas)
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val motivo = if (e.code() in SIN_RUTA) MotivoOmitida.SERVIDOR_SIN_LA_RUTA else null
            if (motivo != null) {
                ResultadoConciliacionDeAgenda.Omitida(motivo)
            } else {
                ResultadoConciliacionDeAgenda.Fallida("http", e.code())
            }
        } catch (e: Exception) {
            Log.w(TAG, "conciliacion de la agenda fallo", e)
            ResultadoConciliacionDeAgenda.Fallida(e::class.simpleName ?: "error")
        }
    }

    companion object {
        private const val TAG = "ConciliacionDeAgenda"
        private val SIN_RUTA = setOf(404, 405)

        /** `(venta, visita, estado)` por venta, ordenado por venta: lo que el servidor compara. */
        fun itemsDe(derivada: Map<Int, CompromisoVigente>): List<ItemConciliacionDto> = derivada
            .values
            .sortedBy { it.ventaId }
            .map { ItemConciliacionDto(it.ventaId, it.visitaId, it.estado.codigo) }
    }
}
