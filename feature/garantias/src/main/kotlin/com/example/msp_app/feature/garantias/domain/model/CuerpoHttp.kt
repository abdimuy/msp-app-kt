package com.example.msp_app.feature.garantias.domain.model

/**
 * Un cuerpo HTTP **ya serializado**, listo para viajar sin que nadie lo rearme.
 *
 * ## Por qué guarda bytes y no un objeto
 *
 * El API de garantías responde 409 `warranty_idempotency_key_mismatch` a un
 * reintento con la misma clave y **cualquier** campo distinto, incluida la hora
 * escrita con otro formato (D3). Un cuerpo que se reconstruye desde el dominio
 * en cada intento puede cambiar; uno que se serializó una sola vez, no. Por eso
 * la cola guarda el texto y el trabajador lo convierte a estos mismos bytes en
 * cada reintento.
 *
 * La igualdad es por **contenido** (`contentEquals`), no por identidad: la
 * prueba de D3 compara el cuerpo recibido, no el objeto, y un `data class` con
 * un `ByteArray` dentro compararía la referencia del arreglo y daría falsos
 * negativos.
 *
 * [tipoDeContenido] también forma parte de la identidad: el multipart de la
 * firma lleva un boundary fijo y el `image/png` de su parte no se deduce del
 * archivo (mutante A9).
 */
class CuerpoHttp private constructor(
    private val contenido: ByteArray,
    val tipoDeContenido: String
) {

    /** Copia defensiva: quien recibe el cuerpo no puede alterar el encolado. */
    val bytes: ByteArray
        get() = contenido.copyOf()

    /** Los mismos bytes vistos como texto UTF-8; útil para pruebas y logs. */
    val texto: String
        get() = contenido.toString(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean = other is CuerpoHttp &&
        tipoDeContenido == other.tipoDeContenido &&
        contenido.contentEquals(other.contenido)

    override fun hashCode(): Int = 31 * tipoDeContenido.hashCode() + contenido.contentHashCode()

    override fun toString(): String = "CuerpoHttp($tipoDeContenido, ${contenido.size} bytes)"

    companion object {

        /** El cuerpo tal como se guardó, sin copiarlo dos veces. */
        fun de(bytes: ByteArray, tipoDeContenido: String): CuerpoHttp =
            CuerpoHttp(bytes.copyOf(), tipoDeContenido)

        /** El cuerpo de un comando JSON, que es texto plano en UTF-8. */
        fun deTexto(texto: String, tipoDeContenido: String): CuerpoHttp =
            CuerpoHttp(texto.toByteArray(Charsets.UTF_8), tipoDeContenido)
    }
}
