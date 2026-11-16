package com.example.msp_app.feature.garantias.application

import com.example.msp_app.feature.garantias.domain.model.ArticuloDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido

/**
 * Un folio en la bandeja de "mis folios".
 *
 * El API no filtra por usuario, así que la bandeja son los folios que este
 * teléfono abrió (`garantia_folios_seguidos`) y no una consulta al servidor
 * (D5). Los contadores le dicen al cobrador cuánto falta por salir; el folio
 * completo se lee al abrirlo.
 */
data class FolioEnBandeja(
    val folio: FolioSeguido,
    val comandosPendientes: Int,
    val fotosPendientes: Int,
    val ultimoError: String?
) {
    val tienePendientes: Boolean
        get() = comandosPendientes > 0 || fotosPendientes > 0

    val rechazado: Boolean
        get() = folio.estadoLocal == EstadoLocalDeFolio.RECHAZADO

    val pendienteDeEnvio: Boolean
        get() = folio.estadoLocal == EstadoLocalDeFolio.PENDIENTE_ENVIO
}

/** Un artículo del folio con su referencia **local**, si este teléfono la conoce. */
data class ArticuloDelFolio(
    val remoto: ArticuloDeGarantia,
    val articuloRef: String?
) {
    /** Sin referencia local no hay comando que avanzar: no se ofrece "Recolectar". */
    val sePuedeRecolectar: Boolean
        get() = articuloRef != null
}

/** De dónde salió lo que la pantalla puede pintar. */
enum class EstadoDeLaLectura {
    /** El servidor respondió: hay folio completo con su línea de tiempo. */
    DISPONIBLE,

    /** No hay señal y el folio ya se envió: se muestran los pendientes locales. */
    SIN_SENAL,

    /** `abrir_folio` todavía no respondió: todavía no existe del lado del servidor. */
    PENDIENTE_ENVIO,

    /** `abrir_folio` recibió un rechazo definitivo. */
    RECHAZADO,

    /** La referencia local no existe en este teléfono. */
    NO_ENCONTRADO
}

/**
 * El folio tal como lo puede pintar la pantalla de detalle.
 *
 * [detalle] viene del servidor y puede ser `null` cuando todavía no hay qué
 * leer (pendiente de envío, sin señal, rechazado). Los contadores de pendientes
 * siempre están: son la memoria del teléfono y no dependen de la red.
 */
data class DetalleDelFolio(
    val folio: FolioSeguido?,
    val estado: EstadoDeLaLectura,
    val comandosPendientes: Int = 0,
    val fotosPendientes: Int = 0,
    val motivo: String? = null,
    val detalle: DetalleDeGarantia? = null,
    val articulos: List<ArticuloDelFolio> = emptyList(),
    val eventos: List<EventoDeGarantia> = emptyList()
) {
    val tienePendientes: Boolean
        get() = comandosPendientes > 0 || fotosPendientes > 0

    companion object {
        fun noEncontrado(): DetalleDelFolio =
            DetalleDelFolio(folio = null, estado = EstadoDeLaLectura.NO_ENCONTRADO)
    }
}
