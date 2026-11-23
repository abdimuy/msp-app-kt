package com.example.msp_app.feature.garantias.domain.model

import java.time.Instant

/**
 * Una acción que el servidor declara válida para una etapa y un origen.
 *
 * [etapaDestino] es el literal que viaja en `etapa_destino` al avanzar el
 * artículo. [etiqueta] es el texto que ve el cobrador ("Marcar recolectado",
 * "Enviar a proveedor"). El teléfono **no** deduce la acción a partir de la
 * etapa actual: la lee de la tabla que publica el servidor (D2).
 */
data class AccionDeGarantia(
    val etapaDestino: String,
    val etiqueta: String
)

/** La llave con la que el catálogo agrupa las acciones. */
data class EtapaYOrigen(
    val etapa: String,
    val origen: OrigenDeFolio
)

/**
 * La tabla de transiciones del servidor, copiada al teléfono para poder
 * decidir sin señal.
 *
 * `GET /v2/garantias/catalogos` **todavía no existe** en `msp-api`: el
 * contrato del servidor tiene la máquina de estados en Go
 * (`internal/garantias/domain/transiciones.go`) y no la publica por HTTP. Por
 * eso la pantalla de recolección no puede ofrecer acciones todavía y muestra
 * "Sin catálogo".
 *
 * La pieza es intercambiable a propósito: el día que el endpoint exista, su
 * adaptador llena este objeto y **ninguna pantalla cambia**. Mientras tanto,
 * [SIN_CATALOGO] es la respuesta honesta y no una tabla inventada en el
 * teléfono, que sería justo lo que D2 prohíbe.
 *
 * [porEtapaYOrigen] viene indexada por la pareja, porque la misma etapa admite
 * salidas distintas según el origen (`SiguientesPorAvanzar(etapa, origen)` en
 * el servidor). Un folio de piso y uno de cliente no comparten tabla.
 */
data class CatalogoDeGarantias(
    val porEtapaYOrigen: Map<EtapaYOrigen, List<AccionDeGarantia>> = emptyMap(),
    val actualizadoEn: Instant? = null
) {
    /** `true` cuando no hay ninguna acción que ofrecer. */
    val estaVacio: Boolean
        get() = porEtapaYOrigen.isEmpty()

    /**
     * Las acciones válidas para [etapa] en un folio de [origen].
     *
     * Lista vacía es un estado legítimo: significa "el servidor no declara
     * ninguna salida desde aquí", y la pantalla no pinta botones.
     */
    fun accionesPara(etapa: String, origen: OrigenDeFolio): List<AccionDeGarantia> =
        porEtapaYOrigen[EtapaYOrigen(etapa, origen)].orEmpty()

    companion object {
        /** El catálogo que se usa mientras el servidor no lo publique. */
        val SIN_CATALOGO: CatalogoDeGarantias = CatalogoDeGarantias()
    }
}
