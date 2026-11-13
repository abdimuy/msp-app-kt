package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream

/**
 * Arma los cuerpos HTTP de la captura **una sola vez**, de forma determinista.
 *
 * ## Por qué el boundary es fijo
 *
 * Un multipart con boundary aleatorio cambia en cada reintento. El servidor
 * compara la huella del contenido para decidir si una clave repetida es un
 * reintento (200) o un contenido distinto (409); un boundary distinto hace que
 * el **mismo** comando parezca otro y la cola se trabaría para siempre. Con un
 * boundary fijo, dos intentos del mismo comando producen los mismos bytes, y
 * la prueba de D3 puede compararlos.
 *
 * El `Content-Type` de la parte de la firma también es fijo
 * ([ReglasDeEntrega.MIME_DE_LA_FIRMA]): no se deduce del archivo ni de su
 * nombre (mutante A9).
 *
 * ## Qué se guarda y qué se arma al enviar
 *
 * Para la entrega, la cola guarda un JSON con los campos de texto y la **ruta**
 * de la firma ([DatosDeEntrega.aJson]). Los bytes del PNG no caben en una
 * columna de texto y no hacen falta ahí: el archivo ya está en disco y se lee
 * en el momento de enviar. Como el archivo no cambia entre reintentos, los
 * bytes del multipart tampoco.
 */
object CuerpoDeGarantia {

    /** El boundary fijo de todos los multipart del módulo. */
    const val BOUNDARY: String = "msp-garantias-v2-7d3f1a"

    /** El `Content-Type` de los comandos con cuerpo JSON. */
    const val TIPO_JSON: String = "application/json"

    /** El `Content-Type` del multipart de la firma y de las fotos. */
    const val TIPO_MULTIPART: String = "multipart/form-data; boundary=$BOUNDARY"

    private const val NOMBRE_DE_LA_FIRMA = "firma"
    private const val ARCHIVO_DE_LA_FIRMA = "firma.png"
    private const val NOMBRE_DE_LA_FOTO = "archivo"
    private const val ARCHIVO_DE_LA_FOTO = "evidencia"

    private val FIN_DE_LINEA = "\r\n".toByteArray(Charsets.UTF_8)

    /** El cuerpo JSON ya serializado por el caso de uso, tal cual se guardó. */
    fun json(texto: String): CuerpoHttp = CuerpoHttp.deTexto(texto, TIPO_JSON)

    /**
     * Los campos de texto de la entrega, con la ruta local de la firma.
     *
     * Es lo que se escribe en `CUERPO` al encolar: no incluye los bytes del PNG,
     * que se leen al enviar.
     */
    fun datosDeEntrega(
        deviceCreatedAt: String,
        ubicacion: Ubicacion,
        rutaDeLaFirma: String
    ): DatosDeEntrega = DatosDeEntrega(
        deviceCreatedAt = deviceCreatedAt,
        latitud = ubicacion.latitud,
        longitud = ubicacion.longitud,
        rutaDeLaFirma = rutaDeLaFirma
    )

    /** El multipart de la entrega, con la firma en la parte `firma`. */
    fun entrega(datos: DatosDeEntrega, firma: ByteArray): CuerpoHttp =
        CuerpoHttp.de(multipartDeEntrega(datos, firma), TIPO_MULTIPART)

    /** El multipart de una foto, con sus bytes en la parte `archivo`. */
    fun imagen(bytes: ByteArray, mime: String): CuerpoHttp =
        CuerpoHttp.de(multipartDeImagen(bytes, mime), TIPO_MULTIPART)

    private fun multipartDeEntrega(datos: DatosDeEntrega, firma: ByteArray): ByteArray {
        val salida = ByteArrayOutputStream()
        parteDeTexto(salida, "device_created_at", datos.deviceCreatedAt)
        parteDeTexto(salida, "gps_lat", datos.latitud.toString())
        parteDeTexto(salida, "gps_lon", datos.longitud.toString())
        parteDeArchivo(
            salida = salida,
            nombre = NOMBRE_DE_LA_FIRMA,
            archivo = ARCHIVO_DE_LA_FIRMA,
            tipo = ReglasDeEntrega.MIME_DE_LA_FIRMA,
            bytes = firma
        )
        return cerrar(salida)
    }

    private fun multipartDeImagen(bytes: ByteArray, mime: String): ByteArray {
        val salida = ByteArrayOutputStream()
        parteDeArchivo(
            salida = salida,
            nombre = NOMBRE_DE_LA_FOTO,
            archivo = ARCHIVO_DE_LA_FOTO,
            tipo = mime,
            bytes = bytes
        )
        return cerrar(salida)
    }

    private fun parteDeTexto(salida: ByteArrayOutputStream, nombre: String, valor: String) {
        salida.escribir("--$BOUNDARY")
        salida.escribir("Content-Disposition: form-data; name=\"$nombre\"")
        salida.escribir("")
        salida.escribir(valor)
    }

    private fun parteDeArchivo(
        salida: ByteArrayOutputStream,
        nombre: String,
        archivo: String,
        tipo: String,
        bytes: ByteArray
    ) {
        salida.escribir(
            "Content-Disposition: form-data; name=\"$nombre\"; filename=\"$archivo\""
        )
        salida.escribir("Content-Type: $tipo")
        salida.escribir("")
        salida.write(bytes)
        salida.write(FIN_DE_LINEA)
    }

    private fun cerrar(salida: ByteArrayOutputStream): ByteArray {
        salida.escribir("--$BOUNDARY--")
        return salida.toByteArray()
    }

    private fun ByteArrayOutputStream.escribir(texto: String) {
        write(texto.toByteArray(Charsets.UTF_8))
        write(FIN_DE_LINEA)
    }
}

/**
 * Los campos de texto del multipart de la entrega, congelados al encolar.
 *
 * [rutaDeLaFirma] es local: el servidor no la ve. La cola la usa para leer el
 * PNG y armar la parte `firma` en cada intento.
 */
data class DatosDeEntrega(
    val deviceCreatedAt: String,
    val latitud: Double,
    val longitud: Double,
    val rutaDeLaFirma: String
) {

    /** Lo que se guarda en `CUERPO`: texto estable, sin bytes binarios. */
    fun aJson(): String = JsonObject().apply {
        addProperty("device_created_at", deviceCreatedAt)
        addProperty("gps_lat", latitud)
        addProperty("gps_lon", longitud)
        addProperty("firma", rutaDeLaFirma)
    }.toString()

    companion object {

        /** El camino inverso; `null` si el cuerpo guardado no tiene la forma. */
        fun deJson(texto: String): DatosDeEntrega? = runCatching {
            val objeto = JsonParser.parseString(texto).asJsonObject
            DatosDeEntrega(
                deviceCreatedAt = objeto.get("device_created_at").asString,
                latitud = objeto.get("gps_lat").asDouble,
                longitud = objeto.get("gps_lon").asDouble,
                rutaDeLaFirma = objeto.get("firma").asString
            )
        }.getOrNull()
    }
}
