package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto

/**
 * **Qué se enseña de la bitácora.** Cuatro, y son cuatro por la misma razón por
 * la que el catálogo de la ficha nace corto: un filtro que nadie elige vacía de
 * significado a los que sí.
 *
 * Se descartó una pastilla por cada uno de los ocho [EstadoCuenta]: son ocho
 * pastillas, se van a tres renglones, y ninguna contesta una pregunta que el
 * cobrador se haga de verdad. Las que sí se hace son dos —*"¿cuándo me pagó?"*
 * y *"¿cuántas veces fui?"*— más una tercera que decide la ruta de mañana:
 * *"¿qué me prometió?"*.
 *
 * ## Sólo en las listas largas
 *
 * El detalle de cliente enseña tres contactos. Unas pastillas ahí ocuparían más
 * alto que las filas que filtran, en la pantalla que ya pelea cada dp contra el
 * saldo. Viven en "ver los N contactos" y en el detalle de venta.
 */
enum class FiltroDeContactos(val etiqueta: String) {
    /** Todo lo que pasó en esa puerta, mezclado. El estado inicial. */
    TODOS("Todos"),

    /** Sólo lo que dejó dinero. */
    COBROS("Cobros"),

    /** Sólo las puertas tocadas, hayan dejado dinero o no. */
    VISITAS("Visitas"),

    /**
     * Sólo lo que compromete una fecha futura: la promesa y la cita.
     *
     * Es un subconjunto de [VISITAS] y no un tipo aparte, porque una promesa
     * **es** una visita — la que deja tarea pendiente. Por eso es un filtro y
     * no un tercer [TipoDeContacto]: el tipo dice qué pasó, el filtro dice qué
     * quiero mirar.
     */
    PROMESAS("Promesas");

    /** ¿[contacto] se enseña con este filtro puesto? */
    fun deja(contacto: ContactoDeCobranza): Boolean = when (this) {
        TODOS -> true
        COBROS -> contacto.tipo == TipoDeContacto.COBRO
        VISITAS -> contacto.tipo == TipoDeContacto.VISITA
        PROMESAS -> contacto.estado in COMPROMETEN_UNA_FECHA
    }

    companion object {
        /**
         * Los estados que dejan una fecha futura anotada. Se listan contra el
         * `enum` y no por "el que traiga fechaPromesa" porque el contacto ya no
         * carga la promesa — sólo su estado, que es lo que el catálogo derivó.
         */
        private val COMPROMETEN_UNA_FECHA = setOf(
            EstadoCuenta.PROMETIO_PROXIMA,
            EstadoCuenta.CITA_A_UNA_HORA
        )
    }
}
