package com.example.msp_app.feature.garantias.domain

import com.example.msp_app.feature.garantias.domain.model.AccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CatalogoDeGarantias
import com.example.msp_app.feature.garantias.domain.model.EtapaYOrigen
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
 * existe— la pantalla cae a [RESPALDO], una copia **declarada y acotada** del
 * tronco de recolección del servidor
 * (`internal/garantias/domain/transiciones.go`, `SiguientesPorAvanzar`). No es
 * una tabla paralela: son las únicas aristas que un artículo recién agregado
 * puede caminar, con el origen como filtro (R1):
 *
 * - `registrado → en_revision` es **sólo piso**: el artículo de piso no se
 *   recolecta, entra directo a revisión.
 * - `registrado → pendiente_recoleccion` es **sólo cliente**.
 * - `pendiente_recoleccion → recolectado` es **sólo cliente**.
 *
 * Un `recolectado` ofrecido desde `registrado`, o para un folio de piso, es una
 * transición que el servidor nunca admite: responde 422
 * (`warranty_folio_operation_not_allowed`). Por eso la llave del respaldo lleva
 * el origen: la misma etapa admite salidas distintas según de dónde venga el
 * folio, y el teléfono no las mezcla.
 *
 * En cuanto el endpoint exista, [acciones] devuelve el catálogo y el respaldo
 * desaparece sin tocar la pantalla.
 */
object AccionesDeRecoleccion {

    /** El literal de `etapa_destino` de la recolección; es el del servidor. */
    const val RECOLECTADO: String = "recolectado"

    private const val REGISTRADO = "registrado"
    private const val PENDIENTE_RECOLECCION = "pendiente_recoleccion"
    private const val EN_REVISION = "en_revision"

    /**
     * El tronco de recolección indexado por etapa y origen, como
     * `SiguientesPorAvanzar(etapa, origen)` en el servidor.
     *
     * Las etapas fuera de la tabla no ofrecen salida: un folio ya recolectado no
     * vuelve a recolectarse y las etapas de decisión (`en_revision` y las que
     * siguen) no las mueve la captura.
     */
    private val RESPALDO: Map<EtapaYOrigen, List<AccionDeGarantia>> = mapOf(
        EtapaYOrigen(REGISTRADO, OrigenDeFolio.PISO) to listOf(
            AccionDeGarantia(etapaDestino = EN_REVISION, etiqueta = "Pasar a revisión")
        ),
        EtapaYOrigen(REGISTRADO, OrigenDeFolio.CLIENTE) to listOf(
            AccionDeGarantia(
                etapaDestino = PENDIENTE_RECOLECCION,
                etiqueta = "Programar recolección"
            )
        ),
        EtapaYOrigen(PENDIENTE_RECOLECCION, OrigenDeFolio.CLIENTE) to listOf(
            AccionDeGarantia(etapaDestino = RECOLECTADO, etiqueta = "Marcar recolectado")
        )
    )

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
        return RESPALDO[EtapaYOrigen(etapa, origen)].orEmpty()
    }
}
