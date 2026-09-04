package com.example.msp_app.data.visitas

import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * El campo del archivo en el multipart de visitas. **Singular**, y así lo exige
 * el servidor: `parseImagenesFromForm` lee `form.File["imagen"]`
 * (`multipart_helpers.go`). El default de `MultipartRequestBuilder.addImage` es
 * `"imagenes"` —el de ventas— y usarlo aquí haría que el servidor no viera ni
 * una sola foto, contestando 201 igual.
 */
private const val CAMPO_IMAGEN = "imagen"

/**
 * Las partes de un multipart de visita, ya armadas, junto con **cuáles imágenes
 * van realmente en ellas**.
 *
 * Que [enviadas] viaje aquí y no se dé por supuesta es el punto entero de este
 * tipo: quien marque `SUBIDA_EN` tiene que marcar exactamente lo que el servidor
 * recibió, y no lo que había en la base cuando se empezó a armar.
 */
data class PartesDeComprobantesDeVisita(
    val partes: List<MultipartBody.Part>,
    val enviadas: List<VisitImageEntity>,
    /** Cuántas filas se quedaron fuera (archivo ausente o tipo no permitido). */
    val omitidas: Int
)

/**
 * Arma las partes `imagen` + `id_<n>` (+ `descripcion_<n>`) de una visita.
 *
 * ## Por qué es un gemelo de `partesDeComprobantes` (pagos) y no el mismo código
 *
 * Se parecen porque el handler de visitas copió la forma del de cobranza. Pero
 * son **dos contratos independientes**, en dos paquetes Go distintos, y difieren
 * justo en la parte que este armador decide:
 *
 * - **`id_<n>` es OBLIGATORIO acá.** `parsePositionalImagenID` contesta 422
 *   `imagen_id_requerido` si falta, y su propio comentario explica por qué se
 *   cerró: con clave de storage determinista, un id opcional dejaba que un
 *   reintento sin id guardara la foto **dos veces**. En cobranza sigue siendo
 *   opcional y el servidor acuña uno.
 * - **La whitelist es OTRA declaración.** `domain.IsAllowedMime` (visitas) y el
 *   tag `contentType` de `CrearPagoMultipartFields.Imagen` (cobranza) coinciden
 *   hoy y no tienen por qué mañana: son dos paquetes Go que no se conocen.
 *
 * Lo que **no** los diferencia, aunque una versión anterior de este comentario
 * lo afirmara: un MIME no permitido tumba la escritura entera en **las dos**
 * rutas — acá `parseImagenesFromForm` corta con `ErrImagenMimeNoPermitido`, y
 * allá Huma valida el tag `contentType` antes del handler y devuelve 422 del
 * request completo. Por eso el filtro de abajo no es defensa en profundidad en
 * ninguna de las dos: es lo que impide que una foto tire trabajo de campo (o
 * dinero).
 *
 * Fundirlos en una sola función haría que un cambio en un endpoint cambiara el
 * otro en silencio, que es exactamente el acoplamiento que no se quiere entre
 * dos módulos del servidor que no se conocen.
 *
 * ## El `n` cuenta las que ENTRAN, no las que se miraron
 *
 * El servidor parea por posición: `id_0` es de la primera parte `imagen`, `id_1`
 * de la segunda. Si una fila se omite —su archivo ya no está— y el índice
 * siguiera contando filas, **todos los ids de ahí en adelante quedarían
 * corridos**: la foto 2 subiría con el id de la 3. Por eso el índice se toma de
 * [PartesDeComprobantesDeVisita.enviadas], que solo crece cuando la parte de
 * verdad se agregó.
 *
 * ## El `Content-Type` de cada parte es obligatorio
 *
 * `partContentType` lee el header de la parte y una parte que no declara nada da
 * `""`, que **no** está en la whitelist → 422 de la visita entera. `asRequestBody(mime)`
 * es lo que hace que OkHttp lo escriba; `MultipartBody.Part.createFormData` con
 * un cuerpo sin `MediaType` no lo pondría.
 */
fun partesDeComprobantesDeVisita(imagenes: List<VisitImageEntity>): PartesDeComprobantesDeVisita {
    val partes = mutableListOf<MultipartBody.Part>()
    val enviadas = mutableListOf<VisitImageEntity>()
    var omitidas = 0
    imagenes.forEach { imagen ->
        val archivo = File(imagen.URI)
        if (!archivo.exists() || !ComprobantesDeVisita.permitido(imagen.MIME)) {
            omitidas += 1
            return@forEach
        }
        val posicion = enviadas.size
        partes += MultipartBody.Part.createFormData(
            CAMPO_IMAGEN,
            archivo.name,
            archivo.asRequestBody(imagen.MIME.toMediaTypeOrNull())
        )
        // `id_<n>`: el UUID de LA IMAGEN (`VisitImageEntity.ID`), nunca el de la
        // visita. Es lo que hace que un reintento no suba la foto dos veces.
        partes += MultipartBody.Part.createFormData("id_$posicion", imagen.ID)
        imagen.DESCRIPCION?.takeIf { it.isNotBlank() }?.let { descripcion ->
            partes += MultipartBody.Part.createFormData("descripcion_$posicion", descripcion)
        }
        enviadas += imagen
    }
    return PartesDeComprobantesDeVisita(partes = partes, enviadas = enviadas, omitidas = omitidas)
}
