package com.example.msp_app.feature.pagos.domain.model

/**
 * Un comprobante fotográfico ya capturado y listo para viajar con el abono.
 *
 * ## [id] NO es decorativo — es la mitad de la idempotencia
 *
 * Es el UUID que **el teléfono** genera al preparar la cámara, y el que viaja
 * como `id_<n>` en el multipart de `POST /v2/cobranza/pagos`. El servidor lo
 * usa como clave de la imagen (`parsePositionalImagenID`); cuando no llega uno,
 * **inventa uno nuevo en cada intento**, así que un reintento sobre señal
 * intermitente subiría la misma foto dos veces. Por eso se acuña una vez, se
 * persiste en `pago_imagenes.ID` y se reenvía igual en cada intento.
 *
 * **[id] y el `PAGO_ID` no son intercambiables.** El que identifica la imagen
 * es este; el del pago es `AbonoARegistrar.abonoId`. Confundirlos manda al
 * servidor una foto cuya clave es la del pago —y, en el reintento, una segunda
 * imagen con la misma clave que la primera.
 *
 * ## [archivo] es una RUTA ABSOLUTA, no un `content://`
 *
 * Aterriza en la columna `pago_imagenes.URI`, que se llama así por simetría con
 * `LocalSaleImageEntity.IMAGE_URI` — y que, igual que aquella, guarda la ruta
 * del archivo local ya comprimido. Es lo que el worker necesita: quien arma el
 * multipart hace `File(URI)` y lo lee, sin `ContentResolver` ni permisos de
 * lectura que puedan haber caducado (el `content://` de un `FileProvider` de la
 * cámara vale para una entrega, no para un reintento tres horas después).
 *
 * [mime] es el tipo real del archivo escrito, no el que la extensión sugiere:
 * es lo que el servidor valida contra su whitelist
 * (`CrearPagoMultipartFields.Imagen`, `contentType:"image/jpeg,..."`).
 */
data class ComprobanteDelAbono(
    val id: String,
    val archivo: String,
    val mime: String
)

/**
 * El destino de una foto que **todavía no se toma**: la cámara escribirá ahí.
 *
 * Existe como tipo propio —y no como un par suelto— porque sus dos rutas son
 * distintas y confundirlas rompe cosas distintas:
 *
 * - [uriParaLaCamara] es el `content://` del `FileProvider`, lo ÚNICO que un
 *   intent de cámara acepta como `EXTRA_OUTPUT`.
 * - [archivoCrudo] es la ruta real de ese mismo archivo, la que se puede
 *   comprimir y borrar sin `ContentResolver`.
 *
 * [id] se acuña **aquí**, antes de disparar la cámara, y no al aceptar la foto:
 * si el proceso muere con la cámara encima, el destino vuelve entero del
 * `SavedStateHandle` y la foto conserva la clave con la que iba a viajar.
 */
data class DestinoDeFoto(
    val id: String,
    val uriParaLaCamara: String,
    val archivoCrudo: String
)
