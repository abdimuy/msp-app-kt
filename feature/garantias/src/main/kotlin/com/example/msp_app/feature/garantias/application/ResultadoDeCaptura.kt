package com.example.msp_app.feature.garantias.application

/**
 * Por qué una captura no se encoló.
 *
 * Es un enum y no un texto para que la pantalla sea la única que decide cómo se
 * dice (`ui/TextosDeGarantias`) y la prueba compare el bloqueo exacto sin
 * depender de la redacción. Cada valor corresponde a una regla del servidor o a
 * una validación que evita encolar un comando que moriría en 422.
 */
enum class FalloDeCaptura {
    /** El folio no dice qué le pasó al artículo (`warranty_description_required`). */
    SIN_DESCRIPCION,

    /** El primer artículo no tiene clave física. */
    SIN_CLAVE_DEL_ARTICULO,

    /** El artículo no se describe (`warranty_article_description_required`). */
    SIN_DESCRIPCION_DEL_ARTICULO,

    /** Un folio de cliente sin cliente, venta, estado de cuenta o domicilio. */
    SIN_DATOS_DEL_CLIENTE,

    /** El folio local ya no está: se borró o nunca existió en este teléfono. */
    FOLIO_DESCONOCIDO,

    /** El folio se rechazó al abrirse: sus comandos dependientes quedan detenidos. */
    FOLIO_RECHAZADO,

    /** La recolección no trae una etapa de destino. */
    SIN_ACCION,

    /** La entrega sin GPS daría 422 para siempre (`warranty_delivery_gps_required`). */
    SIN_UBICACION,

    /** La entrega sin firma daría 422 (`warranty_signature_required`). */
    SIN_FIRMA,

    /** La cámara devolvió un archivo que no es una imagen de las que el servidor acepta. */
    FOTO_NO_GUARDADA,

    /** El PNG de la firma está vacío. */
    FIRMA_VACIA,

    /** El PNG pasa del megabyte que acepta el servidor. */
    FIRMA_MUY_GRANDE,

    /** El PNG no se pudo escribir en `filesDir`. */
    FIRMA_NO_GUARDADA
}

/**
 * Lo que devuelve una captura: o quedó en la cola, o no se tocó nada.
 *
 * No hay un tercer estado "a medias": cada caso de uso escribe en una sola
 * transacción y devuelve [Encolado] con la referencia local del folio para que
 * la pantalla navegue a su detalle.
 */
sealed interface ResultadoDeCaptura {

    /** El comando (y sus fotos) quedaron guardados y el trabajador fue despertado. */
    data class Encolado(val folioRef: String) : ResultadoDeCaptura

    /** No se encoló nada; la pantalla explica [fallo]. */
    data class Bloqueado(val fallo: FalloDeCaptura) : ResultadoDeCaptura
}
