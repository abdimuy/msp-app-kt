package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto

/**
 * Las reglas puras de los comprobantes del abono: **qué tipo se puede subir**,
 * **cuántos caben** y **cómo sobreviven a la muerte del proceso**.
 *
 * Es dominio: cero Android, cero Room, cero Retrofit. Lo que decide aquí lo
 * ejecutan el ViewModel (al adjuntar) y el armador del multipart (al subir), y
 * que sea la MISMA función en los dos lados es lo que impide que la pantalla
 * acepte un archivo que el servidor va a rechazar.
 */
object Comprobantes {

    /**
     * La whitelist del servidor, copiada de su declaración —no parafraseada:
     * `CrearPagoMultipartFields.Imagen` lleva
     * `contentType:"image/jpeg,image/png,image/gif,image/webp,application/pdf"`
     * (`dto_pago_recibido.go`). Cobranza acepta PDF a propósito: los recibos
     * SAT llegan así.
     *
     * Si esta lista y la del servidor se despegan, el teléfono guarda una foto
     * que ningún reintento va a poder subir.
     */
    val TIPOS_PERMITIDOS: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "application/pdf"
    )

    /**
     * Cuántos comprobantes caben en un abono. El contrato del servidor dice
     * "0..N" y no impone techo; el techo es del teléfono: cada foto son cientos
     * de KB que un equipo de gama baja carga hasta que la subida confirme, y un
     * multipart que crece sin límite es el que nunca termina de subir con
     * señal intermitente.
     */
    const val MAXIMO: Int = 5

    /** ¿El servidor va a aceptar este tipo? Tolera mayúsculas y espacios. */
    fun permitido(mime: String?): Boolean = mime?.trim()?.lowercase() in TIPOS_PERMITIDOS

    /**
     * El tipo REAL de un archivo, leído de sus primeros bytes.
     *
     * No se pregunta por la extensión ni al `ContentResolver`, y esa es toda la
     * gracia: el destino de la cámara lo nombra esta app (`...jpg`), así que
     * preguntar por la extensión sería preguntarle a la app lo que ella misma
     * escribió — una respuesta que **no puede** desmentir a la cámara. Los
     * bytes sí: una cámara que devuelve `RESULT_OK` sin escribir nada (archivo
     * vacío, disco lleno) o que escribe un formato que no reconocemos cae aquí
     * en [DESCONOCIDO], que no está en [TIPOS_PERMITIDOS], y la foto se
     * descarta en el teléfono en vez de viajar para que el servidor la rechace.
     *
     * @param primerosBytes los primeros [BYTES_DE_FIRMA] bytes del archivo (o
     *   menos, si el archivo es más corto).
     */
    @Suppress("MagicNumber") // son las firmas de los formatos, no cifras de negocio.
    fun tipoDe(primerosBytes: ByteArray): String = when {
        empiezaCon(primerosBytes, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
        empiezaCon(primerosBytes, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
        empiezaCon(primerosBytes, 0x47, 0x49, 0x46, 0x38) -> "image/gif"
        esWebp(primerosBytes) -> "image/webp"
        empiezaCon(primerosBytes, 0x25, 0x50, 0x44, 0x46) -> "application/pdf"
        else -> DESCONOCIDO
    }

    /**
     * Cuántos bytes hay que leer para que [tipoDe] pueda decidir. Los manda
     * WebP, que es el único con la firma partida (`RIFF` + 4 de tamaño +
     * `WEBP`).
     */
    const val BYTES_DE_FIRMA: Int = 12

    /** Lo que devuelve [tipoDe] cuando no reconoce nada. Nunca está permitido. */
    const val DESCONOCIDO: String = "application/octet-stream"

    @Suppress("MagicNumber") // 0xFF es la máscara del byte, no una cifra de negocio.
    private fun empiezaCon(bytes: ByteArray, vararg firma: Int): Boolean {
        if (bytes.size < firma.size) return false
        return firma.withIndex().all { (indice, esperado) ->
            bytes[indice].toInt() and 0xFF == esperado
        }
    }

    @Suppress("MagicNumber") // las posiciones de la firma partida de WebP.
    private fun esWebp(bytes: ByteArray): Boolean {
        if (bytes.size < BYTES_DE_FIRMA) return false
        val riff = empiezaCon(bytes, 0x52, 0x49, 0x46, 0x46)
        val webp = bytes.copyOfRange(8, BYTES_DE_FIRMA)
            .map { it.toInt() and 0xFF } == listOf(0x57, 0x45, 0x42, 0x50)
        return riff && webp
    }

    /**
     * Codifica un comprobante para el `SavedStateHandle`.
     *
     * El `SavedStateHandle` guarda tipos simples, y el dominio **no puede** ser
     * `Parcelable` (eso arrastraría `android.os` a una capa que tiene prohibido
     * importar Android). Se codifica a texto, con `\n` de separador: ni un
     * UUID, ni un MIME, ni una ruta generada por esta app
     * (`<filesDir>/comprobantes/<uuid>.jpg`) puede contener un salto de línea,
     * así que el separador no puede aparecer dentro de un campo.
     */
    fun codificar(comprobante: ComprobanteDelAbono): String =
        codificar(comprobante.id, comprobante.mime, comprobante.archivo)

    /**
     * Devuelve el comprobante, o `null` si el texto no es uno.
     *
     * Total a propósito: lo que entra es lo que quedó guardado antes de que el
     * proceso muriera, y un formato viejo o truncado no puede tumbar la
     * pantalla del dinero. El llamador **cuenta** los que se cayeron y los
     * reporta — un descarte silencioso es justo lo que la norma de errores
     * prohíbe.
     */
    fun decodificar(texto: String): ComprobanteDelAbono? {
        val (id, mime, archivo) = camposDe(texto) ?: return null
        return ComprobanteDelAbono(id = id, archivo = archivo, mime = mime)
    }

    /**
     * Lo mismo para el destino que espera a la cámara. Codec **propio** y no el
     * de arriba: los dos son tres textos, pero el segundo campo significa cosas
     * distintas (un MIME contra un `content://`), y reusar el otro codec
     * obligaría a cruzarlos en cada llamada. Este plan ya lleva siete defectos
     * de la familia "el identificador equivocado en el lugar del otro".
     */
    fun codificarDestino(destino: DestinoDeFoto): String =
        codificar(destino.id, destino.uriParaLaCamara, destino.archivoCrudo)

    /** El destino guardado, o `null` si el texto no es uno. Total, como [decodificar]. */
    fun decodificarDestino(texto: String): DestinoDeFoto? {
        val (id, uri, archivo) = camposDe(texto) ?: return null
        return DestinoDeFoto(id = id, uriParaLaCamara = uri, archivoCrudo = archivo)
    }

    private fun codificar(primero: String, segundo: String, tercero: String): String =
        listOf(primero, segundo, tercero).joinToString(SEPARADOR)

    /** Los tres campos, o `null` si falta alguno o si sobran. */
    private fun camposDe(texto: String): Triple<String, String, String>? {
        val partes = texto.split(SEPARADOR)
        if (partes.size != CAMPOS) return null
        val (primero, segundo, tercero) = partes
        if (primero.isBlank() || segundo.isBlank() || tercero.isBlank()) return null
        return Triple(primero, segundo, tercero)
    }

    private const val SEPARADOR = "\n"

    private const val CAMPOS = 3
}
