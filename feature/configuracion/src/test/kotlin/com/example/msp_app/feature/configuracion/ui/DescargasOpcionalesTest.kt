package com.example.msp_app.feature.configuracion.ui

import com.example.msp_app.core.mapas.domain.AvanceDeLaDescarga as AvanceDelMapa
import com.example.msp_app.core.mapas.domain.CabeceraDePmtiles
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La traducción de los dos vocabularios de estado al único que pinta la sección.
 *
 * JVM pura, sin Robolectric: son funciones puras y lo que se prueba es que
 * **ningún estado se pierde por el camino**. Un `when` incompleto no compila en
 * Kotlin sobre un `sealed interface`, pero un `when` que mapea dos estados
 * distintos al mismo sí — y eso es lo que estos tests cobran.
 */
class DescargasOpcionalesTest {

    // -----------------------------------------------------------------------
    // Dictado
    // -----------------------------------------------------------------------

    @Test
    fun `el peso del dictado sale del paquete, en megas decimales`() {
        val fila = filaDelDictado(MODELO, EstadoDelModelo.Ausente)

        // 43 537 433 B / 10^6 = 43.537…, que la app anuncia como "43.5".
        assertEquals("43.5", fila.megas)
        assertEquals(DescargaOpcional.DICTADO, fila.cual)
    }

    @Test
    fun `cada estado del modelo tiene su renglon y ninguno se pierde`() {
        val traducidos = ESTADOS_DEL_MODELO.map { filaDelDictado(MODELO, it).estado }

        assertEquals(
            listOf(
                EstadoDeLaDescarga.AUSENTE,
                EstadoDeLaDescarga.ESPERANDO_WIFI,
                EstadoDeLaDescarga.DESCARGANDO,
                EstadoDeLaDescarga.INTERRUMPIDA,
                EstadoDeLaDescarga.LISTA
            ),
            traducidos
        )
        // Control negativo: cinco estados distintos tienen que dar cinco
        // renglones distintos. Sin esto, un `when` que mandara todo a AUSENTE
        // pasaría el test de arriba si alguien reordenara la lista esperada.
        assertEquals(traducidos.size, traducidos.toSet().size)
    }

    /** El dictado **nunca** cae en "sin origen": su URL es una constante del módulo. */
    @Test
    fun `el dictado nunca queda sin origen`() {
        val sinOrigen = ESTADOS_DEL_MODELO
            .map { filaDelDictado(MODELO, it).estado }
            .filter { it == EstadoDeLaDescarga.SIN_ORIGEN }

        assertEquals(emptyList<EstadoDeLaDescarga>(), sinOrigen)
    }

    // -----------------------------------------------------------------------
    // Mapa
    // -----------------------------------------------------------------------

    /**
     * **El estado real de hoy.** El `.pmtiles` no está publicado, así que el
     * paquete viene nulo: sin peso que anunciar y sin descarga que ofrecer.
     */
    @Test
    fun `sin paquete el mapa queda sin origen y sin megas`() {
        val fila = filaDelMapa(paquete = null, estado = EstadoDelExtracto.SinOrigen)

        assertEquals(EstadoDeLaDescarga.SIN_ORIGEN, fila.estado)
        assertEquals(null, fila.megas)
    }

    /**
     * **Robustez suprema del borde:** paquete nulo gana sobre cualquier estado
     * que el puerto reporte. Un paquete que no existe no puede estar bajándose,
     * y un renglón que dijera "Descargando" sin origen sería un dato falso.
     */
    @Test
    fun `sin paquete ningun estado del puerto puede decir otra cosa`() {
        val traducidos = ESTADOS_DEL_EXTRACTO.map { filaDelMapa(null, it).estado }.toSet()

        assertEquals(setOf(EstadoDeLaDescarga.SIN_ORIGEN), traducidos)
    }

    @Test
    fun `con paquete cada estado del extracto tiene su renglon`() {
        val traducidos = ESTADOS_DEL_EXTRACTO.map { filaDelMapa(PAQUETE, it).estado }

        assertEquals(
            listOf(
                EstadoDeLaDescarga.SIN_ORIGEN,
                EstadoDeLaDescarga.AUSENTE,
                EstadoDeLaDescarga.ESPERANDO_WIFI,
                EstadoDeLaDescarga.DESCARGANDO,
                EstadoDeLaDescarga.INTERRUMPIDA,
                EstadoDeLaDescarga.LISTA
            ),
            traducidos
        )
        assertEquals(traducidos.size, traducidos.toSet().size)
    }

    @Test
    fun `el peso del mapa sale del paquete, en megas decimales`() {
        // 25 507 515 B / 10^6 = 25.507…, que la app anuncia como "25.5" — no
        // como los "24 MB" de `huella-geografica-medida.md`, que son mebibytes.
        assertEquals("25.5", filaDelMapa(PAQUETE, EstadoDelExtracto.Ausente).megas)
    }

    /**
     * **Las dos descargas usan la MISMA unidad.** Es la propiedad que la sección
     * necesita para que "43.5 MB" y "25.5 MB" se puedan comparar de un vistazo:
     * los dos `megas()` son megabytes decimales (10⁶), y sobre los mismos bytes
     * tienen que dar exactamente el mismo texto.
     */
    @Test
    fun `los dos modulos miden los megas igual`() {
        val bytes = 25_507_515L
        val porElDictado = filaDelDictado(MODELO.copy(tamanoBytes = bytes), EstadoDelModelo.Listo)
        val porElMapa = filaDelMapa(PAQUETE.copy(tamanoBytes = bytes), EstadoDelExtracto.Ausente)

        assertEquals(porElDictado.megas, porElMapa.megas)
        // Control positivo: si los dos devolvieran `null`, el assert de arriba
        // pasaría sin haber medido nada.
        assertTrue("no se midió ningún peso: no probaría nada", porElMapa.megas != null)
    }

    private companion object {

        val MODELO = ModeloDeDictado(
            url = "https://ejemplo.invalido/ggml-tiny-q8_0.bin",
            tamanoBytes = 43_537_433L,
            sha256 = "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"
        )

        val PAQUETE = ExtractoDeMapa(
            url = "https://ejemplo.invalido/ruta-cobranza-z14.pmtiles",
            tamanoBytes = 25_507_515L
        )

        /** Los cinco estados del modelo, uno por variante del `sealed`. */
        val ESTADOS_DEL_MODELO = listOf(
            EstadoDelModelo.Ausente,
            EstadoDelModelo.EsperandoWifi,
            EstadoDelModelo.Descargando(AvanceDeLaDescarga(20_000_000L, 43_537_433L)),
            EstadoDelModelo.Interrumpido(AvanceDeLaDescarga(20_000_000L, 43_537_433L)),
            EstadoDelModelo.Listo
        )

        /** Los seis del extracto. El sexto es `SinOrigen`, que el dictado no tiene. */
        val ESTADOS_DEL_EXTRACTO = listOf(
            EstadoDelExtracto.SinOrigen,
            EstadoDelExtracto.Ausente,
            EstadoDelExtracto.EsperandoWifi,
            EstadoDelExtracto.Descargando(AvanceDelMapa(12_000_000L, 25_507_515L)),
            EstadoDelExtracto.Interrumpido(AvanceDelMapa(12_000_000L, 25_507_515L)),
            EstadoDelExtracto.Listo(
                MapaDeLaRuta(
                    ruta = "/no/existe/ruta-cobranza-z14.pmtiles",
                    cabecera = CabeceraDePmtiles(
                        version = 3,
                        zoomMinimo = 0,
                        zoomMaximo = 14,
                        oeste = -98.2,
                        sur = 17.9,
                        este = -96.4,
                        norte = 19.6,
                        metadatosDesde = 127L,
                        metadatosLargo = 512L,
                        finDeLosDatos = 25_507_515L
                    )
                )
            )
        )
    }
}
