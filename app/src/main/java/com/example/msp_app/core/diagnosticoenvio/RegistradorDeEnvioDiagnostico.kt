package com.example.msp_app.core.diagnosticoenvio

import android.util.Log
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.enviodiagnostico.EnvioDiagnosticoDao
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Escribe en `envio_diagnostico` lo que observan el interceptor de pagos y el
 * worker de visitas (E-APP-063, E-APP-064, E-APP-065).
 *
 * **No bloquea a quien llama**: [registrar] solo hace `trySend` a un canal
 * acotado y regresa; un único consumidor escribe en orden de llegada. Si el
 * canal está lleno se descarta lo más viejo (es diagnóstico, no dinero). Toda
 * falla —Room, telemetría, lo que sea— se traga aquí: ni el interceptor ni el
 * worker pueden enterarse.
 *
 * Telemetría: a lo más un evento por (fila, código), con tope de
 * [EnvioDiagnosticoDao.MAX_CODIGOS_REPORTADOS] por fila (lo decide
 * [EnvioDiagnosticoDao.registrarFallo]); el evento lleva solo tipo, id de la
 * huella de la fila ([huellaDeFila], nunca el UUID crudo), código y estado
 * HTTP — ningún dato personal.
 *
 * Poda: a lo más una vez al día (por proceso), el mismo consumidor borra las
 * filas sin intentos en [RETENCION] — así la tabla queda acotada también para
 * lo que se soltó sin un 2xx.
 */
class RegistradorDeEnvioDiagnostico(
    private val dao: () -> EnvioDiagnosticoDao,
    private val clock: AppClock = AppClock.System,
    private val reportar: (Map<String, Any>) -> Unit = {},
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : EnvioDiagnosticoSink {

    @Volatile
    private var ultimaPoda: Instant? = null

    private val pendientes = Channel<EnvioObservado>(
        capacity = CAPACIDAD,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        scope.launch {
            for (observado in pendientes) {
                escribir(observado)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override fun registrar(observado: EnvioObservado) {
        try {
            pendientes.trySend(observado)
        } catch (_: Throwable) {
            // Diagnóstico: jamás afecta al envío.
        }
    }

    /** La escritura en sí. `internal` para probarla sin el canal. */
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun escribir(observado: EnvioObservado) {
        try {
            val ahora = clock.now().truncatedTo(ChronoUnit.SECONDS)
            val ahoraCable = AppTime.toWireFormat(ahora)
            val fallo = observado.fallo
            if (fallo == null) {
                dao().registrarExito(observado.tipo, observado.filaId, ahoraCable)
                podarSiToca(ahora)
                return
            }
            val esNuevo = dao().registrarFallo(
                tipo = observado.tipo,
                filaId = observado.filaId,
                codigo = fallo.codigo,
                mensaje = fallo.mensaje,
                http = fallo.http,
                ahora = ahoraCable
            )
            podarSiToca(ahora)
            if (esNuevo) {
                reportar(
                    mapOf(
                        "tipo" to observado.tipo,
                        "fila_huella" to huellaDeFila(observado.filaId),
                        "codigo" to fallo.codigo,
                        "http" to fallo.http
                    )
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo anotar el diagnóstico de envío", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun podarSiToca(ahora: Instant) {
        val previa = ultimaPoda
        if (previa != null && Duration.between(previa, ahora) < CADA_PODA) return
        ultimaPoda = ahora
        try {
            dao().podarIntentosAnterioresA(AppTime.toWireFormat(ahora.minus(RETENCION)))
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo podar el diagnóstico de envío", e)
        }
    }

    private companion object {
        const val TAG = "EnvioDiagnostico"
        const val CAPACIDAD = 256
        val RETENCION: Duration = Duration.ofDays(30)
        val CADA_PODA: Duration = Duration.ofDays(1)
    }
}

/**
 * Huella de la fila para la telemetría `envio_fallido` (E-APP-063; revisión de
 * la Tarea 14, decisión del coordinador 2026-10-17, opción b): los primeros
 * 12 hex de SHA-256 del id en minúsculas (UTF-8). Estable y reproducible fuera
 * del teléfono: la oficina encuentra la fila hasheando con la misma función
 * los UUID candidatos del servidor (procedimiento y vector en
 * `docs/evidencia/app-android.md`, E-APP-063, de msp-api).
 *
 * No es anonimización criptográfica: el espacio de UUID de una zona es chico y
 * se puede recorrer. Evita que el id crudo salga del teléfono en el evento.
 *
 * Vector: `6f1c2a3b-0000-4000-8000-00000000000a` → `2bc2aa3fac32`.
 * Pendiente: si algún día se calcula también en Go, hace falta un caso
 * compartido Go↔Kotlin con este mismo vector.
 */
internal fun huellaDeFila(filaId: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(filaId.lowercase(java.util.Locale.ROOT).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }
        .take(HUELLA_HEX)

private const val HUELLA_HEX = 12
