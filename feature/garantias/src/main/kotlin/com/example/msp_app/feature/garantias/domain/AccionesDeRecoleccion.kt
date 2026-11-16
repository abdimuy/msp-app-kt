package com.example.msp_app.feature.garantias.domain

import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio

/**
 * Las acciones que la pantalla de recolección puede ofrecer.
 *
 * ## La regla
 *
 * Si el catálogo del servidor tiene acciones para la etapa y el origen, **sólo
 * ésas** se ofrecen: las transiciones válidas las decide el servidor (D2) y el
 * teléfono no las deduce.
 *
 * Si el catálogo está vacío —hoy `GET /v2/garantias/catalogos` todavía no
 * existe— la pantalla ofrece el respaldo [RECOLECTADO] en las etapas que la
 * máquina de estados del servidor admite para recolectar (`registrado` y
 * `pendiente_recoleccion`, `internal/garantias/domain/transiciones.go`). Es un
 * respaldo **visible y declarado**, no una tabla paralela: en cuanto el
 * endpoint exista, [acciones] devuelve el catálogo y el respaldo desaparece sin
 * tocar la pantalla.
 *
 * Fuera de esas dos etapas el respaldo no se ofrece: un folio ya recolectado no
 * vuelve a recolectarse.
 */
object AccionesDeRecoleccion {

    /** El literal de `etapa_destino` del respaldo; es el del servidor. */
    const val RECOLECTADO: String = "recolectado"

    private const val ETIQUETA_DEL_RESPALDO = "Marcar recolectado"

    private val ETAPAS_QUE_ADMITEN_RESPALDO = setOf("registrado", "pendiente_recoleccion")

    /**
     * Lo que se puede tocar para [etapa] en un folio de [origen].
     *
     * Lista vacía = no hay salida ofrecida; la pantalla no pinta botones.
     */
    fun acciones(
        catalogo: CatalogoDeGarantias,
        etapa: String,
        origen: OrigenDeFolio
    ): List<AccionDeGarantia> {
        if (!catalogo.estaVacio) return catalogo.accionesPara(etapa, origen)
        if (etapa !in ETAPAS_QUE_ADMITEN_RESPALDO) return emptyList()
        return listOf(
            AccionDeGarantia(etapaDestino = RECOLECTADO, etiqueta = ETIQUETA_DEL_RESPALDO)
        )
    }
}
