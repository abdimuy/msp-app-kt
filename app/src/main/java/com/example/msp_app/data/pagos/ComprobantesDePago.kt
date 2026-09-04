package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.feature.pagos.domain.Comprobantes
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

/** El campo del archivo en el multipart de pagos. **Singular**, y así lo exige el
 * servidor: `CrearPagoMultipartFields.Imagen` lleva `form:"imagen"`
 * (`dto_pago_recibido.go`). El default de `MultipartRequestBuilder.addImage` es
 * `"imagenes"` —el de ventas— y usarlo aquí haría que el servidor no viera ni
 * una sola foto, contestando 200 igual. */
private const val CAMPO_IMAGEN = "imagen"

/**
 * Las partes de un multipart, ya armadas, junto con **cuáles imágenes van
 * realmente en ellas**.
 *
 * Que [enviadas] viaje aquí y no se dé por supuesta es el punto entero de este
 * tipo: quien marque `SUBIDA_EN` tiene que marcar exactamente lo que el
 * servidor recibió, y no lo que había en la base cuando se empezó a armar.
 */
data class PartesDeComprobantes(
    val partes: List<MultipartBody.Part>,
    val enviadas: List<PaymentImageEntity>,
    /** Cuántas filas se quedaron fuera (archivo ausente o tipo no permitido). */
    val omitidas: Int
)

/**
 * Arma las partes `imagen` + `id_<n>` (+ `descripcion_<n>`) de un pago.
 *
 * ## El `n` cuenta las que ENTRAN, no las que se miraron
 *
 * El servidor parea por posición: `id_0` es de la primera parte `imagen`,
 * `id_1` de la segunda (`parsePositionalImagenID`). Si una fila se omite —su
 * archivo ya no está— y el índice siguiera contando filas, **todos los ids de
 * ahí en adelante quedarían corridos**: la foto 2 subiría con el id de la 3.
 * Por eso el índice se toma de [PartesDeComprobantes.enviadas], que solo crece
 * cuando la parte de verdad se agregó.
 *
 * ## Por qué no se usa `MultipartRequestBuilder.addImage`
 *
 * El patrón viene de `LocalSaleSyncHandler` (`multipartRequest` +
 * `getImageParts()`) y el resto se reusa, pero ese helper hace dos cosas que
 * aquí son defectos: deduce el MIME de la **extensión** en vez de usar el que
 * la fila guarda, y **omite en silencio** el archivo que no existe — o sea,
 * exactamente el corrimiento de índices de arriba, sin que nadie se entere.
 * Aquí la omisión se decide antes y se cuenta.
 *
 * ## El tipo se vuelve a filtrar
 *
 * Contra la MISMA lista que usó la pantalla al adjuntar
 * ([Comprobantes.permitido]). No es redundancia: la fila pudo escribirla otra
 * versión de la app, y un tipo fuera de la whitelist hace que el servidor
 * responda 422 **al pago entero** — la foto tumbaría el dinero, que es
 * justamente lo que no puede pasar.
 */
fun partesDeComprobantes(imagenes: List<PaymentImageEntity>): PartesDeComprobantes {
    val partes = mutableListOf<MultipartBody.Part>()
    val enviadas = mutableListOf<PaymentImageEntity>()
    var omitidas = 0
    imagenes.forEach { imagen ->
        val archivo = File(imagen.URI)
        if (!archivo.exists() || !Comprobantes.permitido(imagen.MIME)) {
            omitidas += 1
            return@forEach
        }
        val posicion = enviadas.size
        partes += MultipartBody.Part.createFormData(
            CAMPO_IMAGEN,
            archivo.name,
            archivo.asRequestBody(imagen.MIME.toMediaTypeOrNull())
        )
        // `id_<n>`: el UUID de LA IMAGEN (`PaymentImageEntity.ID`), nunca el
        // del pago. Es lo que hace que un reintento no suba la foto dos veces.
        partes += MultipartBody.Part.createFormData("id_$posicion", imagen.ID)
        imagen.DESCRIPCION?.takeIf { it.isNotBlank() }?.let { descripcion ->
            partes += MultipartBody.Part.createFormData("descripcion_$posicion", descripcion)
        }
        enviadas += imagen
    }
    return PartesDeComprobantes(partes = partes, enviadas = enviadas, omitidas = omitidas)
}
