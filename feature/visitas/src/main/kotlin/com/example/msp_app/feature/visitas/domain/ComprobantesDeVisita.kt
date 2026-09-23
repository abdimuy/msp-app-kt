package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto

/**
 * Las reglas puras de los comprobantes de la visita: **qué tipo se puede
 * subir**, **cuántos caben** y **cómo sobreviven a la muerte del proceso**.
 *
 * Es dominio: cero Android, cero Room, cero Retrofit. Lo que decide aquí lo
 * ejecutan el ViewModel (al adjuntar) y el armador del multipart (al subir), y
 * que sea la MISMA función en los dos lados es lo que impide que la pantalla
 * acepte un archivo que el servidor va a rechazar.
 *
 * ## Por qué esto NO es el objeto de `:feature:pagos`
 *
 * `:feature:pagos` tiene un gemelo (`Comprobantes`) que hoy dice lo mismo, y la
 * tentación de fundirlos en `:core:common` es fuerte. No se hizo, y la razón es
 * que **son dos contratos, no uno**: la whitelist de pagos copia
 * `CrearPagoMultipartFields.Imagen` (cobranza) y la de abajo copia
 * `visitas/domain.IsAllowedMime` (visitas). Son dos declaraciones
 * independientes, en dos paquetes Go distintos, que hoy coinciden y mañana no
 * tienen por qué. Un solo objeto compartido convertiría un cambio de un
 * endpoint en un cambio silencioso del otro.
 *
 * Lo que sí es común de verdad —reconocer un JPEG por sus primeros bytes— es
 * doce líneas sin contrato adentro, y duplicarlas cuesta menos que acoplar dos
 * endpoints.
 *
 * Lo que **no** los separa, para que nadie lo repita: la consecuencia de un MIME
 * no permitido es la misma en los dos (se cae la escritura entera, no solo la
 * imagen). Cambia dónde se valida —el tag `contentType` de Huma en cobranza, el
 * loop del handler en visitas—, no qué pasa.
 */
@Suppress(
    "TooManyFunctions"
) // es el reglamento entero del comprobante; partirlo partiría el contrato en dos.
object ComprobantesDeVisita {

    /**
     * La whitelist del servidor de VISITAS, copiada de su declaración —no
     * parafraseada: `internal/visitas/domain/imagen_storage.go` define
     * `IsAllowedMime` sobre `MimeJPEG, MimePNG, MimeGIF, MimeWebP, MimePDF`.
     *
     * El filtro es load-bearing en las dos puntas: un tipo fuera de la lista
     * hace que el handler conteste `imagen_mime_no_permitido` **para la visita
     * entera** (`parseImagenesFromForm` corta y devuelve error), o sea que una
     * foto mala tumbaría el registro completo. Si esta lista y la del servidor
     * se despegan, el teléfono guarda una foto que ningún reintento va a poder
     * subir.
     */
    val TIPOS_PERMITIDOS: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "application/pdf"
    )

    /**
     * Cuántos comprobantes caben en una visita.
     *
     * El contrato dice "0..N" y **no** impone tope por imagen; el tope real es
     * el `BodyLimit` global del servidor, que aplica al request COMPLETO
     * (`cfg.HTTP.MaxBodySizeMB`, 10 MB por defecto — está escrito en el KDoc de
     * `AdjuntarImagenParams`, que además explica por qué no hay tope por foto:
     * reventaría la visita entera por una sola).
     *
     * Cinco caben con holgura: `ImageCompressor` reescala a 1920px de lado
     * mayor con calidad 75-85, lo que deja fotos de unos cientos de KB, así que
     * cinco quedan cerca de 2 MB contra un techo de 10. El otro tope es el
     * teléfono: cada foto ocupa disco hasta que la subida confirme, y un
     * multipart que crece sin límite es el que nunca termina de subir con señal
     * intermitente.
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
     * escribió — una respuesta que **no puede** desmentir a la cámara. Los bytes
     * sí: una cámara que devuelve `RESULT_OK` sin escribir nada (archivo vacío,
     * disco lleno) o que escribe un formato que no reconocemos cae aquí en
     * [DESCONOCIDO], que no está en [TIPOS_PERMITIDOS], y la foto se descarta en
     * el teléfono en vez de viajar para que el servidor rechace la visita.
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
     * El lado, en píxeles, con el que se pide la miniatura de la rejilla.
     *
     * **Medido, no elegido:** el cuadro de la rejilla mide ~104dp (el ancho de
     * la columna, 328dp a 360dp de pantalla, menos dos huecos de 8dp, partido
     * en tres). A `xxhdpi` —la densidad más alta que el teléfono del cobrador
     * alcanza— eso son 312px. 320 los cubre sin pedir un pixel de más: pedir la
     * foto entera para pintarla en 104dp es lo que hace que una rejilla de
     * cinco fotos se coma la memoria de un equipo de gama baja.
     */
    const val LADO_DE_MINIATURA: Int = 320

    /**
     * El `inSampleSize` con el que decodificar una imagen de [ancho]x[alto]
     * para que su lado CORTO no baje de [lado].
     *
     * El lado corto y no el largo: el cuadro de la rejilla es cuadrado y
     * recorta, así que lo que decide si la miniatura se ve borrosa es la
     * dimensión menor. `BitmapFactory` solo honra potencias de dos, por eso
     * duplica en vez de dividir.
     *
     * Función pura y en el dominio a propósito: es la única aritmética real de
     * la miniatura, y acá se prueba sin Android y sin un archivo.
     */
    @Suppress("MagicNumber") // el 2 es la base de `inSampleSize`, no una cifra de negocio.
    fun muestreoPara(ancho: Int, alto: Int, lado: Int = LADO_DE_MINIATURA): Int {
        if (ancho <= 0 || alto <= 0 || lado <= 0) return 1
        var muestreo = 1
        val corto = minOf(ancho, alto)
        while (muestreo < MUESTREO_MAXIMO && corto / (muestreo * 2) >= lado) {
            muestreo *= 2
        }
        return muestreo
    }

    /**
     * Techo del muestreo. Sin él, una imagen con un lado absurdo (o un `outWidth`
     * corrupto) deja el `while` girando hasta desbordar el `Int`; 64 ya reduce
     * un lado de 20 480px por debajo del pedido.
     */
    private const val MUESTREO_MAXIMO = 64

    /**
     * Codifica un comprobante para el `SavedStateHandle`.
     *
     * El `SavedStateHandle` guarda tipos simples, y el dominio **no puede** ser
     * `Parcelable` (eso arrastraría `android.os` a una capa que tiene prohibido
     * importar Android). Se codifica a texto, con `\n` de separador: ni un UUID,
     * ni un MIME, ni una ruta generada por esta app
     * (`<filesDir>/comprobante_visita_<uuid>.jpg`) puede contener un salto de
     * línea, así que el separador no puede aparecer dentro de un campo.
     */
    fun codificar(comprobante: ComprobanteDeVisita): String =
        codificar(comprobante.id, comprobante.mime, comprobante.archivo)

    /**
     * Devuelve el comprobante, o `null` si el texto no es uno.
     *
     * Total a propósito: lo que entra es lo que quedó guardado antes de que el
     * proceso muriera, y un formato viejo o truncado no puede tumbar la
     * pantalla. El llamador **cuenta** los que se cayeron y los reporta — un
     * descarte silencioso es justo lo que la norma de errores prohíbe.
     */
    fun decodificar(texto: String): ComprobanteDeVisita? {
        val (id, mime, archivo) = camposDe(texto) ?: return null
        return ComprobanteDeVisita(id = id, archivo = archivo, mime = mime)
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
