package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia

/**
 * Los ids del servidor que la cola ya conoce **durante una pasada**.
 *
 * La lista de comandos se lee una vez por folio, así que después de resolver el
 * `id` de un folio o de un artículo las filas en memoria siguen con `null`. En
 * vez de releer la tabla tras cada comando, esta clase lleva el mapa de
 * referencias locales a ids y lo actualiza cuando el servidor responde.
 *
 * Sólo guarda lo que el servidor devolvió. Si un id falta, [folioIdDe] y sus
 * hermanas devuelven `null` y la ruta no se puede resolver: la cola se detiene
 * en vez de inventar un `{folio}` literal que el API rechazaría para siempre
 * (D4, mutante A5).
 */
internal class Referencias {

    private var folioId: String? = null
    private val articulos = mutableMapOf<String, String>()
    private val eventos = mutableMapOf<String, String>()

    /** Toma lo que las filas ya traen resuelto de pasadas anteriores. */
    fun registrar(comando: ComandoDeGarantia) {
        comando.folioId?.let { folioId = it }
        comando.articuloRef?.let { ref ->
            comando.articuloId?.let { articulos[ref] = it }
        }
        comando.eventoRef?.let { ref ->
            comando.eventoId?.let { eventos[ref] = it }
        }
    }

    fun folioIdDe(comando: ComandoDeGarantia): String? = comando.folioId ?: folioId

    fun articuloIdDe(comando: ComandoDeGarantia): String? =
        comando.articuloId ?: comando.articuloRef?.let { articulos[it] }

    fun eventoIdDe(comando: ComandoDeGarantia): String? =
        comando.eventoId ?: comando.eventoRef?.let { eventos[it] }

    fun registrarFolio(id: String) {
        folioId = id
    }

    fun registrarArticulo(ref: String, id: String) {
        articulos[ref] = id
    }

    fun registrarEvento(ref: String, id: String) {
        eventos[ref] = id
    }

    companion object {

        /** Arranca con lo que ya está resuelto en las filas del folio. */
        fun de(comandos: List<ComandoDeGarantia>): Referencias =
            Referencias().apply { comandos.forEach(::registrar) }
    }
}
