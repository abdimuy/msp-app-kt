package com.example.msp_app.core.ubicacion

import androidx.room.withTransaction
import com.example.msp_app.core.common.location.captura.CapturaUbicada
import com.example.msp_app.core.common.location.captura.CapturasEnEspera
import com.example.msp_app.core.common.location.captura.DecisionDeUbicacion
import com.example.msp_app.core.common.location.captura.FixDeUbicacion
import com.example.msp_app.core.common.location.captura.OrigenDeUbicacion
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.location.captura.TomadorDeUbicacion
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.ubicacion.UbicacionDeCapturaDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * La ubicacion de un abono, condonacion o visita al guardar y justo despues (E-APP-146; decision
 * del dueno 2026-10-30: el envio se RETIENE hasta una ubicacion valida o 30 s).
 *
 * ## Antes de guardar
 * [alGuardar] pregunta al tomador por la mejor ubicacion que ya tiene (sin suspender). Si la regla
 * la acepta como ANTES, la captura nace con ella y [anotarEnLaTransaccion] deja la fila
 * `ubicacion_de_captura` (precision, hora y origen quedan SOLO en el telefono) en la MISMA
 * transaccion. Si no hay, [anotarEnLaTransaccion] escribe en esa misma transaccion el plazo del
 * envio (`plazo_de_envio`, K1): es lo que hace esperar al worker.
 *
 * ## Despues de guardar (K5)
 * Sin ANTES, [despuesDeGuardar] anota la captura en [CapturasEnEspera] y mantiene viva la toma
 * 35 s en el alcance de PROCESO (la pantalla puede irse). EN CUANTO el tomador recibe un fix
 * ([alRecibir], no al cerrar ninguna ventana) la regla se aplica a cada captura en espera y, si la
 * acepta, en UNA transaccion se escribe `LAT`/`LNG` en la captura (solo si seguian en 0,0) y la
 * fila de ubicacion. El worker, que relee Room en cada vuelta, la ve y la manda en el POST. Un fix
 * que llega despues de que el envio salio queda solo en el telefono y no genera trabajo nuevo.
 *
 * Nada de esto puede fallar hacia el dinero: lo que lanza se traga (menos la cancelacion).
 */
@Suppress("LongParameterList")
class UbicacionDeCaptura(
    private val db: AppDatabase,
    private val ubicaciones: UbicacionDeCapturaDao,
    private val tomador: TomadorDeUbicacion,
    private val enEspera: CapturasEnEspera,
    private val monotono: RelojMonotono,
    private val reloj: AppClock,
    private val alcance: CoroutineScope,
    /** Las anotaciones de la transaccion del dinero; por omision, SQL directo sobre [db]. */
    private val anotaciones: AnotacionesSinAnidar = AnotacionesSinAnidar(db),
    /**
     * Escribe la ubicacion en la captura si sigue vacia: devuelve cuantas filas toco (0 = ya tenia
     * ubicacion o la fila ya no existe; no se anota nada).
     */
    private val aplicarEnLaCaptura: Map<
        String,
        suspend (
            id: String,
            lat: Double,
            lng: Double
        ) -> Int
        >,
    private val reporte: (origen: OrigenDeUbicacion?, precisionM: Int?) -> Unit =
        TelemetriaDeUbicacion::guardado
) : UbicacionAlGuardar {

    private val lock = Any()
    private var extensionHastaNanos: Long = Long.MIN_VALUE

    @Suppress("TooGenericExceptionCaught")
    override fun alGuardar(): MomentoDeGuardado = try {
        val ahora = monotono.elapsedNanos()
        val decision = tomador.mejorUbicacionAntesDeGuardar(ahora)
        MomentoDeGuardado(ahora, (decision as? DecisionDeUbicacion.AceptarAntes)?.fix)
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        MomentoDeGuardado.SinUbicacion
    }

    /**
     * Dentro de la transaccion del dinero. El SQL directo vive en [AnotacionesSinAnidar]: ver por que
     * alli. Nunca lanza.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun anotarEnLaTransaccion(
        id: String,
        tipo: String,
        momento: MomentoDeGuardado
    ) {
        try {
            val fix = momento.antes
            if (fix != null) {
                // La captura ya lleva las coordenadas en su propia fila; esta fila guarda precision,
                // hora y origen, que se quedan en el telefono.
                anotaciones.ubicacion(fila(id, tipo, fix, UbicacionDeCapturaEntity.ORIGEN_ANTES))
            } else if (momento.elapsedNanos != null) {
                // K1: el plazo, en la misma transaccion del dinero. Sin fila de plazo (falla, pagos
                // viejos, el barrido) el worker NO retiene el envio.
                anotaciones.plazo(
                    PlazoDeEnvioEntity(
                        id = id,
                        tipo = tipo,
                        guardadoEnEpochMs = reloj.now().toEpochMilli(),
                        plazoMs = PlazoDeEnvioEntity.PLAZO_MS
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // El dinero manda.
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun despuesDeGuardar(id: String, tipo: String, momento: MomentoDeGuardado) {
        try {
            val fix = momento.antes
            if (fix != null) {
                reporte(OrigenDeUbicacion.ANTES, fix.precisionM.toInt())
                return
            }
            val guardado = momento.elapsedNanos ?: return reporte(null, null)
            enEspera.registrar(id, tipo, guardado)
            mantenerViva(guardado)
            // Un fix pudo llegar entre `alGuardar` y aqui: con la captura ya anotada, se re-evalua
            // con lo recibido para no esperar al siguiente.
            alRecibir(monotono.elapsedNanos(), tomador.fixesRecibidos())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // La captura ya esta escrita; sin ubicacion sale al vencer su plazo.
        }
    }

    /**
     * Lo que hace el tomador al recibir un fix (K5): resuelve las capturas en espera y escribe cada
     * ubicacion aceptada en otro alcance, sin bloquear al tomador.
     */
    fun alRecibir(ahoraNanos: Long, fixes: List<FixDeUbicacion>) {
        enEspera.resolver(ahoraNanos, fixes).forEach { ubicada ->
            alcance.launch { escribir(ubicada) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun escribir(ubicada: CapturaUbicada) {
        val captura = ubicada.captura
        val fix = ubicada.decision.fix
        val origen = if (ubicada.decision is DecisionDeUbicacion.AceptarAntes) {
            UbicacionDeCapturaEntity.ORIGEN_ANTES
        } else {
            UbicacionDeCapturaEntity.ORIGEN_DESPUES
        }
        try {
            val aplicar = aplicarEnLaCaptura[captura.tipo]
            val escrita = db.withTransaction {
                val tocadas = aplicar?.invoke(captura.id, fix.lat, fix.lon) ?: 0
                if (tocadas > 0) ubicaciones.anotar(fila(captura.id, captura.tipo, fix, origen))
                tocadas > 0
            }
            if (escrita) {
                reporte(
                    ubicada.decision.origen,
                    fix.precisionM.toInt()
                )
            } else {
                reporte(null, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            reporte(null, null)
        }
    }

    /** Mantiene la toma 35 s mas en el alcance de proceso (una sola extension cubre varios guardados). */
    private fun mantenerViva(guardadoNanos: Long) {
        val hasta = guardadoNanos + EXTENSION_MS * NANOS_POR_MS
        synchronized(lock) {
            if (hasta <= extensionHastaNanos) return
            extensionHastaNanos = hasta
        }
        tomador.extender(alcance, EXTENSION_MS)
    }

    private fun fila(id: String, tipo: String, fix: FixDeUbicacion, origen: String) =
        UbicacionDeCapturaEntity(
            id = id,
            tipo = tipo,
            lat = fix.lat,
            lng = fix.lon,
            precisionM = fix.precisionM,
            fixAtMs = fix.hora.toEpochMilli(),
            origen = origen,
            estadoEnvio = UbicacionDeCapturaEntity.PENDIENTE,
            intentos = 0,
            creadaEn = reloj.now().toEpochMilli()
        )

    companion object {
        /** Cuanto se mantiene viva la toma tras guardar sin ubicacion: el plazo de 30 s mas margen. */
        const val EXTENSION_MS = 35_000L
        private const val NANOS_POR_MS = 1_000_000L

        /** `aplicarEnLaCaptura` para cada tipo de captura. */
        fun enLasCapturas(
            pagos: PaymentDao,
            visitas: VisitDao
        ): Map<String, suspend (String, Double, Double) -> Int> = mapOf(
            UbicacionDeCapturaEntity.TIPO_PAGO to { id, lat, lng ->
                pagos.updateLocationIfEmpty(id, lat, lng)
            },
            UbicacionDeCapturaEntity.TIPO_VISITA to { id, lat, lng ->
                visitas.updateLocationIfEmpty(id, lat, lng)
            }
        )
    }
}
