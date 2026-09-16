package com.example.msp_app.core.mapas.fake

import com.example.msp_app.core.mapas.adapters.PlanificadorDeLaDescarga
import com.example.msp_app.core.mapas.domain.CabeceraDePmtiles
import com.example.msp_app.core.mapas.domain.LARGO_DE_LA_CABECERA
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import java.io.File

/**
 * Fakes escritos a mano: estado público + lista pública que graba las llamadas.
 * **Nunca MockK ni Mockito** (global-constraints §Definición de tests).
 */
class PlanificadorFalso : PlanificadorDeLaDescarga {

    val encolados: MutableList<Unit> = mutableListOf()
    val cancelaciones: MutableList<Unit> = mutableListOf()

    override fun encolarSoloConWifi() {
        encolados += Unit
    }

    override fun cancelar() {
        cancelaciones += Unit
    }
}

/**
 * Construye un `.pmtiles` **sintético y válido**: cabecera correcta más el
 * relleno justo para que `finDeLosDatos` coincida con el largo del archivo.
 *
 * No pretende ser dibujable —no tiene teselas de verdad— y no hace falta que lo
 * sea: lo que estos tests miden es la verificación, no el render. El archivo de
 * verdad se usa en los tests de [ExtractoReal].
 */
fun archivoPmtilesValido(destino: File, bytesDeDatos: Int = 64): File {
    val datos = LARGO_DE_LA_CABECERA + bytesDeDatos
    destino.writeBytes(cabeceraDePruebaDe(finDeLosDatos = datos.toLong()) + ByteArray(bytesDeDatos))
    return destino
}

/** La misma cabecera, pero el archivo se corta antes de lo que ella anuncia. */
fun archivoPmtilesTruncado(destino: File, bytesDeDatos: Int = 64, faltan: Int = 16): File {
    val datos = LARGO_DE_LA_CABECERA + bytesDeDatos
    destino.writeBytes(
        cabeceraDePruebaDe(finDeLosDatos = datos.toLong()) + ByteArray(bytesDeDatos - faltan)
    )
    return destino
}

/** Una cabecera v3 armada byte a byte, con la caja de la ruta medida. */
@Suppress("MagicNumber") // son desplazamientos del formato, no cifras de negocio.
fun cabeceraDePruebaDe(
    finDeLosDatos: Long,
    version: Int = 3,
    magia: String = "PMTiles",
    zoomMaximo: Int = ExtractoReal.ZOOM_MAXIMO_MEDIDO
): ByteArray {
    val bytes = ByteArray(LARGO_DE_LA_CABECERA)
    magia.forEachIndexed { i, c -> bytes[i] = c.code.toByte() }
    bytes[7] = version.toByte()
    // `tileDataOffset` en 56 y `tileDataLength` en 64; se reparte para que la
    // suma sea el fin anunciado, que es lo que el almacén compara.
    bytes.escribir64(56, LARGO_DE_LA_CABECERA.toLong())
    bytes.escribir64(64, finDeLosDatos - LARGO_DE_LA_CABECERA)
    bytes[100] = 0
    bytes[101] = zoomMaximo.toByte()
    bytes.escribir32(102, (ExtractoReal.OESTE_MEDIDO * 1e7).toInt())
    bytes.escribir32(106, (ExtractoReal.SUR_MEDIDO * 1e7).toInt())
    bytes.escribir32(110, (ExtractoReal.ESTE_MEDIDO * 1e7).toInt())
    bytes.escribir32(114, (ExtractoReal.NORTE_MEDIDO * 1e7).toInt())
    return bytes
}

/** Un mapa de prueba, con la caja medida de la ruta. */
fun mapaDePrueba(ruta: String = "/datos/mapas/ruta.pmtiles"): MapaDeLaRuta = MapaDeLaRuta(
    ruta = ruta,
    cabecera = CabeceraDePmtiles(
        version = 3,
        zoomMinimo = 0,
        zoomMaximo = ExtractoReal.ZOOM_MAXIMO_MEDIDO,
        oeste = ExtractoReal.OESTE_MEDIDO,
        sur = ExtractoReal.SUR_MEDIDO,
        este = ExtractoReal.ESTE_MEDIDO,
        norte = ExtractoReal.NORTE_MEDIDO,
        metadatosDesde = 172L,
        metadatosLargo = 1_179L,
        finDeLosDatos = ExtractoReal.TAMANO_MEDIDO
    )
)

private fun ByteArray.escribir64(desde: Int, valor: Long) {
    for (i in 0 until 8) this[desde + i] = ((valor shr (i * 8)) and 0xFF).toByte()
}

private fun ByteArray.escribir32(desde: Int, valor: Int) {
    for (i in 0 until 4) this[desde + i] = ((valor shr (i * 8)) and 0xFF).toByte()
}
