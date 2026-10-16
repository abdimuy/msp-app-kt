package com.example.msp_app.core.diagnosticoenvio

import android.util.Log
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.enviodiagnostico.EnvioDiagnosticoDao
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import java.time.Duration
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
 * Telemetría: a lo más un evento por fila y por código distinto (lo decide
 * [EnvioDiagnosticoDao.registrarFallo]); el evento lleva solo tipo, id de la
 * fila, código y estado HTTP — ningún dato personal.
 */
class RegistradorDeEnvioDiagnostico(
    private val dao: () -> EnvioDiagnosticoDao,
    private val clock: AppClock = AppClock.System,
    private val reportar: (Map<String, Any>) -> Unit = {},
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : EnvioDiagnosticoSink {

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
                dao().podarExitosAnterioresA(AppTime.toWireFormat(ahora.minus(RETENCION_EXITOS)))
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
            if (esNuevo) {
                reportar(
                    mapOf(
                        "tipo" to observado.tipo,
                        "fila_id" to observado.filaId,
                        "codigo" to fallo.codigo,
                        "http" to fallo.http
                    )
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "No se pudo anotar el diagnóstico de envío", e)
        }
    }

    private companion object {
        const val TAG = "EnvioDiagnostico"
        const val CAPACIDAD = 256
        val RETENCION_EXITOS: Duration = Duration.ofDays(30)
    }
}
