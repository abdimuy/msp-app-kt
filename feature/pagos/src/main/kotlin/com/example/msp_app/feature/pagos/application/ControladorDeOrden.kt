package com.example.msp_app.feature.pagos.application

import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.port.CercaniaPort
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardadoPort
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * El orden elegido en la hoja "Ordenar por" (decisión del dueño, 2026-10-05):
 * su valor vigente, las distancias que [OrdenDeLaLista.MAS_CERCA] necesita y
 * su persistencia "durante el día".
 *
 * Vive aparte de [com.example.msp_app.feature.pagos.ui.ListaDeClientesViewModel]
 * por dos motivos, no uno: agrupar [CercaniaPort] y [OrdenGuardadoPort] en un
 * solo constructor evita que el ViewModel cruce el umbral `LongParameterList`
 * de detekt, y mover [restaurar]/[elegir]/[quitar] aquí evita que cruce
 * `TooManyFunctions` — los dos son el mismo síntoma: la lógica de "qué orden
 * está activo y por qué" es una responsabilidad propia, no un apéndice del
 * ViewModel.
 *
 * `@Inject constructor` sin `@Provides` explícito: Hilt arma esta clase sola
 * en cuanto [CercaniaPort]/[OrdenGuardadoPort] tienen binding (los provee
 * `PagosPortsModule` en `:app`), igual que cualquier otra clase con
 * constructor `@Inject`.
 */
class ControladorDeOrden @Inject constructor(
    private val cercania: CercaniaPort,
    private val ordenGuardado: OrdenGuardadoPort,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) {
    /** El orden vigente. [OrdenDeLaLista.COBRANZA] hasta que [restaurar] diga otra cosa. */
    var orden: OrdenDeLaLista = OrdenDeLaLista.COBRANZA
        private set

    /** Metros por `DOCTO_CC_ACR_ID` — sólo tiene datos útiles mientras [orden] es `MAS_CERCA`. */
    var distancias: Map<Int, Double> = emptyMap()
        private set

    /**
     * Restaura el orden guardado si sigue vigente — "vigente" quiere decir que
     * se guardó el mismo [hoy] de negocio que esta carga acaba de resolver. Un
     * orden de ayer (o de nunca: [OrdenGuardadoPort.leer] en `null`) cae a
     * [OrdenDeLaLista.COBRANZA], el default con el que ya nace [orden].
     *
     * **A propósito NO pide distancias** (antes lo hacía, llamando
     * [actualizarDistanciasSiHaceFalta] al final). Leer [OrdenGuardadoPort] es
     * rápido —una preferencia en disco—, pero [actualizarDistanciasSiHaceFalta]
     * puede quedarse esperando al GPS; encadenarla aquí significa que quien
     * llama a [restaurar] no puede proyectar la cartera recién leída hasta que
     * el GPS conteste, aunque el orden YA se conozca. Quien llama decide
     * cuándo pedir las distancias — típicamente después de proyectar una vez.
     */
    suspend fun restaurar(hoy: LocalDate) {
        val guardado = withContext(io) { ordenGuardado.leer() } ?: return
        orden = if (guardado.dia == hoy) guardado.orden else OrdenDeLaLista.COBRANZA
    }

    /**
     * Pide distancias SÓLO si [orden] las necesita — las otras seis órdenes no
     * tocan [CercaniaPort]. Con [TIMEOUT_UBICACION_MS] de tope: un GPS que
     * nunca contesta no debe dejar [distancias] (y a quien lea este
     * `ControladorDeOrden`) esperando para siempre — pasado el plazo se
     * degrada a mapa vacío, exactamente como "sin permiso" o "sin fix", que
     * [OrdenDeLaLista.MAS_CERCA] ya sabe resolver cayendo a cobranza.
     */
    suspend fun actualizarDistanciasSiHaceFalta() {
        if (orden != OrdenDeLaLista.MAS_CERCA) return
        distancias = withContext(io) {
            withTimeoutOrNull(TIMEOUT_UBICACION_MS) { cercania.distanciasPorVenta() }.orEmpty()
        }
    }

    /** Elige [nuevo], pide distancias si hace falta y persiste con el día [hoy] (si se conoce). */
    suspend fun elegir(nuevo: OrdenDeLaLista, hoy: LocalDate?) {
        orden = nuevo
        actualizarDistanciasSiHaceFalta()
        guardar(hoy)
    }

    /** Vuelve a [OrdenDeLaLista.COBRANZA] y lo persiste igual que cualquier otra elección. */
    suspend fun quitar(hoy: LocalDate?) {
        orden = OrdenDeLaLista.COBRANZA
        guardar(hoy)
    }

    /** Sin `hoy` (cartera nula) no hay día que guardar. */
    private suspend fun guardar(hoy: LocalDate?) {
        hoy ?: return
        withContext(io) { ordenGuardado.guardar(orden, hoy) }
    }

    private companion object {
        /** Tope para que un GPS sin respuesta no cuelgue la lista para siempre. */
        const val TIMEOUT_UBICACION_MS = 8_000L
    }
}
