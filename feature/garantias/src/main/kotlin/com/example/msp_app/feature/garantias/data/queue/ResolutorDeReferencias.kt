package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.domain.port.EstadoDeLasFotosPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import com.example.msp_app.feature.garantias.domain.port.ResolucionDeReferenciasPort
import java.time.Instant
import javax.inject.Inject

/**
 * Traduce la respuesta de un comando a los ids que el servidor acaba de crear.
 *
 * ## Por qué pide el detalle y no usa la respuesta del comando
 *
 * `POST /v2/garantias/{id}/articulos` y `.../avanzar` devuelven el folio con
 * sus artículos, pero **no** sus eventos. El evento es justo lo que la cola
 * necesita: de él sale el `articulo_ref` que creó un `agregar` y el `id` al
 * que se cuelgan las fotos de una recolección. Por eso, después de un 2xx, se
 * lee `GET /v2/garantias/{id}` y se busca el evento por
 * `clave_idempotencia` (D4, mutante A6).
 *
 * La fila se marca `enviado` **después** de resolver: si el detalle falla, el
 * comando sigue pendiente y el reintento manda la misma clave. El servidor ya
 * lo tiene y responde el mismo resultado, así que no se duplica nada.
 */
internal class ResolutorDeReferencias @Inject constructor(
    private val remotas: GarantiasRemotasPort,
    private val resolucion: ResolucionDeReferenciasPort,
    private val estadoDeLasFotos: EstadoDeLasFotosPort
) {

    /**
     * Propaga el `id` de un folio recién abierto a sus comandos.
     *
     * No pide el detalle: `abrir` devuelve el `id` en su propio cuerpo y no hay
     * eventos que resolver todavía.
     */
    suspend fun resolverFolio(
        folioRef: String,
        folioId: String,
        referencias: Referencias,
        ahora: Instant
    ) {
        referencias.registrarFolio(folioId)
        resolucion.resolverFolio(folioRef, folioId, ahora)
    }

    /**
     * Busca en el detalle el evento que produjo [comando] y propaga el artículo
     * y/o el evento que la captura estaba esperando.
     *
     * Devuelve `false` cuando el servidor todavía no cuenta el cambio: la cola
     * lo trata como reintento y **no** marca el comando como enviado.
     *
     * @throws java.io.IOException si el detalle no se pudo leer; el llamador
     *   decide si espera a tener señal.
     */
    suspend fun resolver(
        comando: ComandoDeGarantia,
        folioId: String,
        referencias: Referencias,
        ahora: Instant
    ): Boolean {
        if (comando.articuloRef == null && comando.eventoRef == null) return true

        val detalle = remotas.detalle(folioId)
        val evento = detalle.eventoPorClave(comando.clave) ?: return false

        return resolverArticulo(comando, detalle, evento, referencias, ahora) &&
            resolverEvento(comando, folioId, evento, referencias, ahora)
    }

    private suspend fun resolverArticulo(
        comando: ComandoDeGarantia,
        detalle: DetalleDeGarantia,
        evento: EventoDeGarantia,
        referencias: Referencias,
        ahora: Instant
    ): Boolean {
        val articuloRef = comando.articuloRef ?: return true
        val referencia = evento.articuloRef ?: return false
        val articulo = detalle.articulo(referencia) ?: return false
        referencias.registrarArticulo(articuloRef, articulo.id)
        resolucion.resolverArticulo(articuloRef, articulo.id, ahora)
        return true
    }

    private suspend fun resolverEvento(
        comando: ComandoDeGarantia,
        folioId: String,
        evento: EventoDeGarantia,
        referencias: Referencias,
        ahora: Instant
    ): Boolean {
        val eventoRef = comando.eventoRef ?: return true
        referencias.registrarEvento(eventoRef, evento.id)
        resolucion.resolverEvento(eventoRef, evento.id, ahora)
        estadoDeLasFotos.resolverEvento(eventoRef, evento.id, folioId, ahora)
        return true
    }
}
