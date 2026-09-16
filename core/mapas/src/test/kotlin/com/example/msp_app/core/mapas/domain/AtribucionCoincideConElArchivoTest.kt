package com.example.msp_app.core.mapas.domain

import com.example.msp_app.core.mapas.fake.ExtractoReal
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **La atribución no es un texto elegido: es el que traen los datos.**
 *
 * `ATRIBUCION_DE_OSM` está escrita como constante porque la pantalla necesita
 * una cadena corta y sin HTML. Este test es lo que impide que esa constante se
 * despegue de la realidad: lee el JSON de metadatos del extracto **real** —que
 * va comprimido con gzip dentro del `.pmtiles`, en el desplazamiento que la
 * cabecera dice— y comprueba que el archivo efectivamente atribuye a
 * OpenStreetMap.
 *
 * Si alguien regenerara el extracto sobre datos de otro origen, esto se pondría
 * rojo, que es exactamente cuando hay que volver a mirar la licencia.
 */
class AtribucionCoincideConElArchivoTest {

    @Test
    fun `el extracto real atribuye a OpenStreetMap`() {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        val cabeceraBytes = ByteArray(LARGO_DE_LA_CABECERA)
        ExtractoReal.archivo.inputStream().use { it.read(cabeceraBytes) }
        val cabecera = leerCabeceraDePmtiles(cabeceraBytes).getOrThrow()

        val metadatos = ExtractoReal.archivo.inputStream().use { entrada ->
            entrada.skip(cabecera.metadatosDesde)
            val comprimido = ByteArray(cabecera.metadatosLargo.toInt())
            var leidos = 0
            while (leidos < comprimido.size) {
                val n = entrada.read(comprimido, leidos, comprimido.size - leidos)
                if (n < 0) break
                leidos += n
            }
            GZIPInputStream(comprimido.inputStream()).bufferedReader().readText()
        }

        // Control positivo del método: si el JSON no se pudo descomprimir, la
        // ausencia de "OpenStreetMap" no probaría nada.
        assertTrue("no se leyo el JSON de metadatos", metadatos.contains("\"vector_layers\""))
        assertTrue(
            "el archivo no atribuye a OpenStreetMap: ${metadatos.take(200)}",
            metadatos.contains("OpenStreetMap")
        )
        assertTrue(
            "la constante de la app no dice lo que dice el archivo",
            ATRIBUCION_DE_OSM.contains("OpenStreetMap")
        )
    }
}
