package com.example.msp_app.core.common.location.captura

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Reloj monótono (`SystemClock.elapsedRealtimeNanos`). Inyectable para pruebas deterministas. */
fun interface RelojMonotono {
    fun elapsedNanos(): Long
}

/** Si la app puede pedir ubicación en este momento. */
enum class AccesoAUbicacion { OK, SIN_PERMISO, GPS_APAGADO }

/**
 * De dónde salen los fixes. En producción es Fused; en pruebas, un doble que decide qué llega y cuándo.
 * Los errores se lanzan como excepción: [TomadorDeUbicacion] los atrapa y nunca los propaga.
 */
interface ProveedorDeUbicacion {
    fun acceso(): AccesoAUbicacion

    /** La última ubicación conocida del sistema, o `null`. */
    suspend fun ultimaConocida(): FixDeUbicacion?

    /** Fixes nuevos mientras alguien escuche; al cancelarse debe quitar la petición al sistema. */
    fun actualizaciones(): Flow<FixDeUbicacion>
}

/** Qué ve la pantalla de la toma anticipada. */
sealed interface EstadoDeUbicacion {
    /** Pidiendo; aún no hay un fix que sirva. */
    data object Esperando : EstadoDeUbicacion

    /** Hay un fix que cumple la regla ANTES al momento de recibirlo. */
    data class Disponible(val fix: FixDeUbicacion) : EstadoDeUbicacion

    data object SinPermiso : EstadoDeUbicacion

    data object GpsApagado : EstadoDeUbicacion

    /** Se acabó el tiempo (o el flujo) sin ningún fix que sirva. */
    data object SinFix : EstadoDeUbicacion
}

/** Por qué no hubo ubicación; nombres estables, viajan a telemetría. */
enum class MotivoSinUbicacion { SIN_PERMISO, GPS_APAGADO, SIN_FIX, ERROR }

/** Lo que el tomador reporta para telemetría. Sin datos personales: no lleva coordenadas. */
interface ReporteDelTomador {
    /** Primer fix que sirve desde `iniciar`. */
    fun primerFix(msDesdeInicio: Long, precisionM: Int, deUltimaConocida: Boolean)

    fun sinUbicacion(motivo: MotivoSinUbicacion)

    companion object {
        val Nulo: ReporteDelTomador = object : ReporteDelTomador {
            override fun primerFix(
                msDesdeInicio: Long,
                precisionM: Int,
                deUltimaConocida: Boolean
            ) = Unit

            override fun sinUbicacion(motivo: MotivoSinUbicacion) = Unit
        }
    }
}

/**
 * Toma la ubicación por anticipado (E-APP-142; decisión del dueño 2026-10-30, punto 2): se inicia al
 * abrir la pantalla del cliente / captura de abono / diálogo de condonación y deja disponible
 * [mejorUbicacionAntesDeGuardar] para el momento de guardar.
 *
 * - **Nunca bloquea guardar:** [mejorUbicacionAntesDeGuardar] no suspende, lee un buffer en memoria.
 * - **Sin trabajo colgado:** corre en el `scope` que se le pasa (el de la pantalla); al cancelarse
 *   el alcance se cancela la colección y el proveedor quita su petición. No usa foreground service.
 * - **Tope de tiempo:** la ventana de actualizaciones dura [ventanaMs]; pasada, termina sola.
 * - Una nueva llamada a [iniciar] reemplaza a la anterior y conserva el buffer (los fixes viejos
 *   los descarta la regla por edad).
 */
class TomadorDeUbicacion(
    private val proveedor: ProveedorDeUbicacion,
    private val reloj: RelojMonotono,
    private val config: () -> ConfigDeAceptacion = { ConfigDeAceptacion.POR_DEFECTO },
    private val ventanaMs: Long = VENTANA_POR_DEFECTO_MS,
    private val reporte: ReporteDelTomador = ReporteDelTomador.Nulo,
    /**
     * E-APP-146 (K5): se avisa en CUANTO llega un fix al buffer (no al cerrar la ventana), con el
     * instante monotono y una copia de los fixes recordados. Corre dentro del cerrojo del tomador:
     * no debe suspender ni tardar (lo que escribe en Room se lanza en otro alcance). Si lanza, el
     * tomador sigue como si nada.
     */
    private val alRecibirFix: (ahoraNanos: Long, fixes: List<FixDeUbicacion>) -> Unit = { _, _ -> }
) {
    init {
        require(ventanaMs > 0) { "ventanaMs debe ser positiva: $ventanaMs" }
    }

    private val _estado = MutableStateFlow<EstadoDeUbicacion>(EstadoDeUbicacion.Esperando)
    val estado: StateFlow<EstadoDeUbicacion> = _estado.asStateFlow()

    private val buffer = AtomicReference<List<FixDeUbicacion>>(emptyList())

    @Volatile
    private var primerFixReportado = false
    private val jobActual = AtomicReference<Job?>(null)

    /** Arranca (o reinicia) la toma en [scope]. Devuelve el trabajo, que se cancela con el alcance. */
    fun iniciar(scope: CoroutineScope): Job {
        jobActual.get()?.cancel()
        val inicio = reloj.elapsedNanos()
        primerFixReportado = false
        val job = scope.launch { correr(inicio) }
        jobActual.set(job)
        return job
    }

    /** Cancela la toma en curso. El buffer se conserva. */
    fun detener() {
        jobActual.getAndSet(null)?.cancel()
    }

    /** Copia de los fixes que el tomador recuerda, en orden de llegada al reloj monótono. */
    fun fixesRecibidos(): List<FixDeUbicacion> = buffer.get()

    /**
     * Escucha al proveedor en [scope] durante [ventanaMs] SIN tocar la toma de la pantalla ni su
     * estado: los fixes caen en el mismo buffer y avisan igual a `alRecibirFix`. Es lo que mantiene
     * viva la toma cuando el cobrador guarda y sale de la pantalla (cuyo alcance se cancela) mientras
     * un envío espera su ubicación (E-APP-146). Termina sola al vencer la ventana o al cancelarse
     * [scope]; no lanza.
     */
    fun extender(scope: CoroutineScope, ventanaMs: Long): Job {
        require(ventanaMs > 0) { "ventanaMs debe ser positiva: $ventanaMs" }
        val inicio = reloj.elapsedNanos()
        return scope.launch { escucharSinEstado(inicio, ventanaMs) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun escucharSinEstado(inicioNanos: Long, ventanaMs: Long) {
        try {
            if (proveedor.acceso() != AccesoAUbicacion.OK) return
            withTimeoutOrNull(ventanaMs) {
                coroutineScope {
                    launch { leerUltimaConocida(inicioNanos) }
                    proveedor.actualizaciones().collect { recibir(it, inicioNanos, false) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin proveedor no hay ubicación tardía; la captura sale sin ella.
        }
    }

    /**
     * La mejor ubicación para un guardado en [instanteElapsedNanos] (por defecto, ahora), tomada con los
     * fixes ya recibidos y solo hacia atrás en el tiempo. Nunca suspende ni lanza.
     * Devuelve [DecisionDeUbicacion.AceptarAntes] o [DecisionDeUbicacion.Rechazar].
     */
    fun mejorUbicacionAntesDeGuardar(
        instanteElapsedNanos: Long = reloj.elapsedNanos()
    ): DecisionDeUbicacion {
        val previos = buffer.get().filter { it.elapsedRealtimeNanos <= instanteElapsedNanos }
        return AceptacionDeUbicacion.decidir(instanteElapsedNanos, previos, config())
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun correr(inicioNanos: Long) {
        when (proveedor.acceso()) {
            AccesoAUbicacion.SIN_PERMISO -> return terminarSin(
                EstadoDeUbicacion.SinPermiso,
                MotivoSinUbicacion.SIN_PERMISO
            )
            AccesoAUbicacion.GPS_APAGADO -> return terminarSin(
                EstadoDeUbicacion.GpsApagado,
                MotivoSinUbicacion.GPS_APAGADO
            )
            AccesoAUbicacion.OK -> Unit
        }
        _estado.value = EstadoDeUbicacion.Esperando
        try {
            withTimeoutOrNull(ventanaMs) {
                // E-APP-149 (H4): las dos fuentes corren en paralelo; una colgada no bloquea a la otra.
                coroutineScope {
                    launch { leerUltimaConocida(inicioNanos) }
                    proveedor.actualizaciones().collect { recibir(it, inicioNanos, false) }
                }
            }
            if (_estado.value is EstadoDeUbicacion.Esperando) {
                terminarSin(EstadoDeUbicacion.SinFix, MotivoSinUbicacion.SIN_FIX)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            terminarSin(EstadoDeUbicacion.SinPermiso, MotivoSinUbicacion.SIN_PERMISO)
        } catch (_: Exception) {
            if (_estado.value is EstadoDeUbicacion.Esperando) {
                terminarSin(EstadoDeUbicacion.SinFix, MotivoSinUbicacion.ERROR)
            }
        }
    }

    /** La última conocida tiene su propio tope ([ULTIMA_CONOCIDA_MAX_MS]); su fallo o demora no estorba. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun leerUltimaConocida(inicioNanos: Long) {
        val ultima = try {
            withTimeoutOrNull(ULTIMA_CONOCIDA_MAX_MS) { proveedor.ultimaConocida() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (ultima != null) recibir(ultima, inicioNanos, true)
    }

    private fun terminarSin(estado: EstadoDeUbicacion, motivo: MotivoSinUbicacion) {
        _estado.value = estado
        reporte.sinUbicacion(motivo)
    }

    /** Guarda el fix y, si ahora sirve, lo publica. Serializado: las dos fuentes pueden llegar a la vez. */
    @Synchronized
    private fun recibir(fix: FixDeUbicacion, inicioNanos: Long, deUltima: Boolean) {
        buffer.updateAndGet { actual ->
            (actual + fix).sortedBy {
                it.elapsedRealtimeNanos
            }.takeLast(CAPACIDAD)
        }
        val ahora = reloj.elapsedNanos()
        avisar(ahora)
        val decision = AceptacionDeUbicacion.decidir(
            ahora,
            buffer.get().filter { it.elapsedRealtimeNanos <= ahora },
            config()
        )
        if (decision !is DecisionDeUbicacion.AceptarAntes) return
        _estado.value = EstadoDeUbicacion.Disponible(decision.fix)
        if (!primerFixReportado) {
            primerFixReportado = true
            reporte.primerFix(
                msDesdeInicio = (ahora - inicioNanos).coerceAtLeast(0L) / NANOS_POR_MS,
                precisionM = decision.fix.precisionM.toInt(),
                deUltimaConocida = deUltima
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun avisar(ahoraNanos: Long) {
        try {
            alRecibirFix(ahoraNanos, buffer.get())
        } catch (_: Exception) {
            // Quien escribe la ubicación de una captura no puede tumbar la toma.
        }
    }

    companion object {
        /** Cuántos fixes se recuerdan. */
        const val CAPACIDAD = 32

        /** Ventana de actualizaciones: 3 minutos (la pantalla suele cerrarse antes). */
        const val VENTANA_POR_DEFECTO_MS = 180_000L

        /** Tope de la última conocida: si el sistema no responde, se sigue solo con las actualizaciones. */
        const val ULTIMA_CONOCIDA_MAX_MS = 5_000L
        private const val NANOS_POR_MS = 1_000_000L
    }
}
