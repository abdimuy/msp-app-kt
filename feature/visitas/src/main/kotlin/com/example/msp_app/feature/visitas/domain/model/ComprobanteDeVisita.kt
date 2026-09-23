package com.example.msp_app.feature.visitas.domain.model

/**
 * Un comprobante fotográfico ya capturado y listo para viajar con la visita.
 *
 * ## [id] NO es decorativo — es un campo OBLIGATORIO del contrato
 *
 * Es el UUID que **el teléfono** genera al preparar la cámara, y el que viaja
 * como `id_<n>` en el multipart de `POST /v2/visitas`.
 *
 * Y aquí visitas difiere de pagos, que es donde copiar sin mirar rompe: en
 * cobranza el `id_<n>` es opcional y el servidor acuña uno cuando falta; en
 * visitas es **requerido** (`parsePositionalImagenID`: sin él, 422
 * `imagen_id_requerido` **para la visita entera**, no solo para la foto). El
 * KDoc del propio servidor dice por qué se cerró así: la clave de storage es
 * `visitas/<visita_id>/<imagen_id><ext>`, determinista, y un id inventado por
 * intento habría guardado la misma foto dos veces en el reintento.
 *
 * **[id] y el `VISITA_ID` no son intercambiables.** El que identifica la imagen
 * es este; el de la visita es `VisitaARegistrar.visitaId`. Confundirlos manda al
 * servidor una foto cuya clave es la de la visita —y, en el reintento, una
 * segunda imagen con la misma clave que la primera.
 *
 * ## [archivo] es una RUTA ABSOLUTA, no un `content://`
 *
 * Aterriza en la columna `visita_imagenes.URI`, que se llama así por simetría
 * con `LocalSaleImageEntity.IMAGE_URI` — y que, igual que aquella, guarda la
 * ruta del archivo local ya comprimido. Es lo que el worker necesita: quien arma
 * el multipart hace `File(URI)` y lo lee, sin `ContentResolver` ni permisos de
 * lectura que puedan haber caducado (el `content://` de un `FileProvider` de la
 * cámara vale para una entrega, no para un reintento tres horas después).
 *
 * [mime] es el tipo real del archivo escrito, no el que la extensión sugiere:
 * es lo que el servidor valida contra su whitelist
 * (`visitas/domain.IsAllowedMime`), y también lo que se manda como
 * `Content-Type` de la parte — un `Content-Type` ausente en la parte es "" del
 * lado del servidor y cae fuera de la whitelist (`partContentType`).
 */
data class ComprobanteDeVisita(
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

/**
 * Los píxeles ya reducidos de un comprobante, listos para pintar en la rejilla.
 *
 * ## Por qué esto existe en vez de un cargador de imágenes
 *
 * La rejilla enseña la foto, y enseñarla con `coil` volvería el golden de
 * Roborazzi dependiente de una carga asíncrona con caché: el verde dejaría de
 * significar "la pantalla no cambió" y pasaría a significar "esta vez la imagen
 * llegó". Un archivo local decodificado con `BitmapFactory` + `inSampleSize`
 * siempre da el mismo pixel.
 *
 * ## Por qué viaja como `IntArray` y no como un `Bitmap`
 *
 * `Bitmap` es `android.graphics`, y el dominio tiene prohibido importar
 * Android. Lo que cruza es el buffer ARGB pelado; quien lo convierte en
 * `ImageBitmap` es la capa `ui/`, que sí puede. Eso además deja la
 * decodificación **fuera del hilo principal** —vive en el adaptador, que el
 * ViewModel llama en su dispatcher de IO— y deja la rejilla como un composable
 * puro sobre datos, que es lo que hace el golden determinista.
 *
 * El `require` no es decorativo: un buffer más corto que `ancho * alto` revienta
 * dentro de `Bitmap.createBitmap`, o sea **dentro de una composición**, donde el
 * fallo ya no se puede reportar sin tumbar la pantalla del registro. Acá revienta
 * en el adaptador, donde el `catch` con telemetría del ViewModel lo recoge.
 */
class Miniatura(val ancho: Int, val alto: Int, val pixeles: IntArray) {
    init {
        require(ancho > 0 && alto > 0) { "una miniatura sin lado no se puede pintar" }
        require(pixeles.size >= ancho * alto) { "el buffer no alcanza para $ancho x $alto" }
    }
}
