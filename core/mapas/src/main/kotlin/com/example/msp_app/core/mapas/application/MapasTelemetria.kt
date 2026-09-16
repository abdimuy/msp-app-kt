package com.example.msp_app.core.mapas.application

/**
 * Los `code` de este módulo, en **un solo lugar** y como constantes: la norma de
 * errores pide que sean grepeables, y un literal escrito en el `catch` no lo es
 * cuando alguien lo escribe distinto la segunda vez.
 *
 * ## LAS COORDENADAS NO VIAJAN. NUNCA.
 *
 * Una latitud y una longitud **son** dato del cliente: dicen dónde vive. El
 * KDoc de `Telemetry` prohíbe direcciones con todas sus letras y un par de
 * grados con siete decimales es una dirección más precisa que la escrita.
 *
 * Tampoco viaja **la ruta del archivo** del extracto: lleva el `filesDir` del
 * dispositivo. Lo que puede salir de acá es el nombre de la clase de excepción,
 * un código HTTP y el motivo tipado de una cabecera inválida.
 */
object MapasTelemetria {

    /** La descarga del extracto se cortó. Lo bajado se conserva. */
    const val CODE_DESCARGA_CORTADA: String = "mapa_extracto_descarga_cortada"

    /**
     * El `.pmtiles` no es lo que dice ser, o no llegó entero. Se descarta y se
     * vuelve a bajar de cero.
     */
    const val CODE_EXTRACTO_INVALIDO: String = "mapa_extracto_invalido"

    /** No se pudo borrar el extracto que el cobrador pidió borrar. */
    const val CODE_EXTRACTO_NO_SE_BORRO: String = "mapa_extracto_no_se_borro"

    /** No se pudo crear la carpeta donde vive el extracto. */
    const val CODE_CARPETA_NO_SE_CREO: String = "mapa_carpeta_no_se_creo"

    /**
     * MapLibre no arrancó: la librería nativa no cargó, o el motor gráfico no
     * está. El suelo degrada a liso y el pin sigue.
     */
    const val CODE_MOTOR_NO_ARRANCO: String = "mapa_motor_no_arranco"

    /** El nombre de la clase de excepción. Nunca su `message`. */
    const val PROP_EXCEPCION: String = "excepcion"

    /** El código HTTP de la descarga. */
    const val PROP_HTTP: String = "http"

    /** El motivo tipado de una cabecera rechazada — el nombre del enum. */
    const val PROP_MOTIVO: String = "motivo"

    /**
     * **La lista blanca de claves.** Es lo que `props` puede llevar en este
     * módulo y nada más; los tests la usan para afirmar que ninguna emisión
     * arrastró una coordenada ni una ruta de archivo.
     */
    val CLAVES_PERMITIDAS: Set<String> = setOf(PROP_EXCEPCION, PROP_HTTP, PROP_MOTIVO)
}
