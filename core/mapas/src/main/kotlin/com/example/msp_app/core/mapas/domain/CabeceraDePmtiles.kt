package com.example.msp_app.core.mapas.domain

/**
 * **La cabecera de un archivo PMTiles v3**, leída de sus primeros 127 bytes.
 *
 * ## Por qué esto existe en vez de un SHA-256
 *
 * `:core:speech` verifica su modelo con un checksum, y para aquél está bien: el
 * `.bin` de whisper es un objeto inmutable publicado una vez. El extracto del
 * mapa **no lo es**: se regenera del build diario de Protomaps (ver
 * `huella-geografica-medida.md`), así que un `sha256` escrito en el código
 * quedaría mal el día que alguien vuelva a correr `pmtiles extract` — y
 * obligaría a un release de la app para cambiar un mapa.
 *
 * La cabecera dice algo mejor y más barato: **qué es el archivo y si llegó
 * entero**. [finDeLosDatos] es `tileDataOffset + tileDataLength`, o sea el
 * último byte que el archivo dice tener; comparado contra el largo real del
 * archivo es una prueba de completitud que sale **del propio archivo**, sin que
 * nadie tenga que anunciar el tamaño por fuera y sin leer 25 MB para digerirlos.
 *
 * ## Medido, no supuesto
 *
 * El formato de abajo está verificado contra el extracto real
 * (`ruta-cobranza-z14.pmtiles`, 2026-09-16): magia `PMTiles`, versión 3, zoom
 * 0–14, caja `-98.2, 17.9 → -96.4, 19.6` — exactamente los números que
 * `huella-geografica-medida.md` midió contra Firebird por un camino
 * independiente. Que los dos coincidan es el control positivo del lector: un
 * parser con los desplazamientos corridos no habría dado esa caja.
 *
 * Todos los enteros del formato son **little-endian**.
 */
data class CabeceraDePmtiles(
    val version: Int,
    val zoomMinimo: Int,
    val zoomMaximo: Int,
    val oeste: Double,
    val sur: Double,
    val este: Double,
    val norte: Double,
    /** Dónde arranca el JSON de metadatos, y cuánto mide. Va comprimido. */
    val metadatosDesde: Long,
    val metadatosLargo: Long,
    /**
     * El byte siguiente al último de datos de tesela. En un archivo completo
     * vale exactamente el largo del archivo.
     */
    val finDeLosDatos: Long
)

/** Por qué un `.pmtiles` no se pudo aceptar. Cada caso tiene su telemetría. */
enum class FalloDeLaCabecera {

    /** El archivo es más corto que la cabecera: no hay nada que leer. */
    DEMASIADO_CORTO,

    /** No empieza con `PMTiles`. No es un archivo de este formato. */
    SIN_LA_MAGIA,

    /** Es PMTiles pero de otra versión. MapLibre solo lee la 3. */
    VERSION_AJENA
}

/** El largo exacto de la cabecera de PMTiles v3. */
const val LARGO_DE_LA_CABECERA: Int = 127

private const val MAGIA = "PMTiles"
private const val VERSION_SOPORTADA = 3

// Desplazamientos del formato v3. Nombrados porque un `102` suelto en medio de
// una aritmética de bytes es exactamente lo que nadie puede revisar.
private const val EN_VERSION = 7
private const val EN_METADATOS_DESDE = 24
private const val EN_METADATOS_LARGO = 32
private const val EN_DATOS_DESDE = 56
private const val EN_DATOS_LARGO = 64
private const val EN_ZOOM_MINIMO = 100
private const val EN_ZOOM_MAXIMO = 101
private const val EN_OESTE = 102
private const val EN_SUR = 106
private const val EN_ESTE = 110
private const val EN_NORTE = 114

private const val OCHO_BYTES = 8
private const val CUATRO_BYTES = 4
private const val UN_BYTE = 8

/** Los grados llegan multiplicados por diez millones, en `int32`. */
private const val GRADOS_POR_ENTERO = 1e7

/**
 * Lee la cabecera de [bytes].
 *
 * Devuelve `Result` porque es una operación falible de las que el contrato del
 * repo pide envolver (brief §18), y porque quien la llama —el almacén— tiene
 * que poder distinguir "no es un pmtiles" de "no llegó entero" para decidir si
 * reanuda o vuelve a empezar.
 */
fun leerCabeceraDePmtiles(bytes: ByteArray): Result<CabeceraDePmtiles> {
    if (bytes.size < LARGO_DE_LA_CABECERA) {
        return Result.failure(CabeceraInvalida(FalloDeLaCabecera.DEMASIADO_CORTO))
    }
    val magia = String(bytes, 0, MAGIA.length, Charsets.US_ASCII)
    if (magia != MAGIA) {
        return Result.failure(CabeceraInvalida(FalloDeLaCabecera.SIN_LA_MAGIA))
    }
    val version = bytes[EN_VERSION].toInt() and BYTE
    if (version != VERSION_SOPORTADA) {
        return Result.failure(CabeceraInvalida(FalloDeLaCabecera.VERSION_AJENA))
    }
    return Result.success(
        CabeceraDePmtiles(
            version = version,
            zoomMinimo = bytes[EN_ZOOM_MINIMO].toInt() and BYTE,
            zoomMaximo = bytes[EN_ZOOM_MAXIMO].toInt() and BYTE,
            oeste = bytes.grados(EN_OESTE),
            sur = bytes.grados(EN_SUR),
            este = bytes.grados(EN_ESTE),
            norte = bytes.grados(EN_NORTE),
            metadatosDesde = bytes.entero64(EN_METADATOS_DESDE),
            metadatosLargo = bytes.entero64(EN_METADATOS_LARGO),
            finDeLosDatos = bytes.entero64(EN_DATOS_DESDE) + bytes.entero64(EN_DATOS_LARGO)
        )
    )
}

/** El fallo de [leerCabeceraDePmtiles], con su motivo tipado. */
class CabeceraInvalida(val motivo: FalloDeLaCabecera) :
    IllegalArgumentException("cabecera de pmtiles invalida: $motivo")

private const val BYTE = 0xFF

private fun ByteArray.entero64(desde: Int): Long {
    var valor = 0L
    for (i in OCHO_BYTES - 1 downTo 0) {
        valor = (valor shl UN_BYTE) or (this[desde + i].toLong() and BYTE.toLong())
    }
    return valor
}

private fun ByteArray.entero32(desde: Int): Int {
    var valor = 0
    for (i in CUATRO_BYTES - 1 downTo 0) {
        valor = (valor shl UN_BYTE) or (this[desde + i].toInt() and BYTE)
    }
    return valor
}

private fun ByteArray.grados(desde: Int): Double = entero32(desde) / GRADOS_POR_ENTERO
