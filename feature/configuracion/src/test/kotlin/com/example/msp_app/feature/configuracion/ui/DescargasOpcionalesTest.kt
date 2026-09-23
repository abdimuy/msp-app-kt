package com.example.msp_app.feature.configuracion.ui

import com.example.msp_app.core.speech.domain.AvanceDeLaDescarga
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La traducción del vocabulario de estado de `:core:speech` al único que pinta
 * la sección.
 *
 * JVM pura, sin Robolectric: son funciones puras y lo que se prueba es que
 * **ningún estado se pierde por el camino**. Un `when` incompleto no compila en
 * Kotlin sobre un `sealed interface`, pero un `when` que mapea dos estados
 * distintos al mismo sí — y eso es lo que estos tests cobran.
 *
 * Hubo una segunda mitad, la del extracto de mapa. Se fue con `:core:mapas`.
 */
class DescargasOpcionalesTest {

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

    /**
     * **Los cinco estados del vocabulario se usan.** Antes había un sexto,
     * `SIN_ORIGEN`, que sólo el mapa podía producir —su `.pmtiles` nunca estuvo
     * publicado— y que se fue con `:core:mapas`. Esto es lo que cobra que no
     * quede otro estado sin productor: el enum tiene que medir exactamente lo
     * que el dictado sabe decir.
     */
    @Test
    fun `ningun estado del vocabulario queda sin quien lo produzca`() {
        val producidos = ESTADOS_DEL_MODELO.map { filaDelDictado(MODELO, it).estado }.toSet()

        assertEquals(EstadoDeLaDescarga.entries.toSet(), producidos)
    }

    private companion object {

        val MODELO = ModeloDeDictado(
            url = "https://ejemplo.invalido/ggml-tiny-q8_0.bin",
            tamanoBytes = 43_537_433L,
            sha256 = "c2085835d3f50733e2ff6e4b41ae8a2b8d8110461e18821b09a15c40c42d1cca"
        )

        /** Los cinco estados del modelo, uno por variante del `sealed`. */
        val ESTADOS_DEL_MODELO = listOf(
            EstadoDelModelo.Ausente,
            EstadoDelModelo.EsperandoWifi,
            EstadoDelModelo.Descargando(AvanceDeLaDescarga(20_000_000L, 43_537_433L)),
            EstadoDelModelo.Interrumpido(AvanceDeLaDescarga(20_000_000L, 43_537_433L)),
            EstadoDelModelo.Listo
        )
    }
}
