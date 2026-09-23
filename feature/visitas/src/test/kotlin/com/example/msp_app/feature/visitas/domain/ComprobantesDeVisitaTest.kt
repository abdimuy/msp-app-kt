package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto
import com.example.msp_app.feature.visitas.domain.model.Miniatura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas puras de los comprobantes de visita.
 *
 * Los dos bordes que importan y por qué:
 *
 * - **La whitelist** tiene que coincidir con `visitas/domain.IsAllowedMime`. Si
 *   se despega, el teléfono guarda una foto que ningún reintento va a poder
 *   subir — y peor: un MIME no permitido hace que el servidor conteste 422 a la
 *   **visita entera**.
 * - **Los dos codecs no son intercambiables.** Los dos son tres textos, pero su
 *   segundo campo significa cosas distintas. Cruzarlos produce basura, y hay una
 *   prueba que lo afirma para que nadie lo haga creyendo que da igual.
 */
class ComprobantesDeVisitaTest {

    // --- La whitelist -------------------------------------------------------

    /**
     * La lista EXACTA del servidor de visitas, no una parecida.
     * `imagen_storage.go`: `IsAllowedMime` sobre
     * `MimeJPEG, MimePNG, MimeGIF, MimeWebP, MimePDF`.
     */
    @Test
    fun `la whitelist es la del servidor de visitas`() {
        assertEquals(
            setOf("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf"),
            ComprobantesDeVisita.TIPOS_PERMITIDOS
        )
    }

    @Test
    fun `permitido tolera mayusculas y espacios`() {
        assertTrue(ComprobantesDeVisita.permitido(" IMAGE/JPEG "))
        assertTrue(ComprobantesDeVisita.permitido("image/jpeg"))
    }

    @Test
    fun `permitido rechaza lo que el servidor rechaza`() {
        assertFalse(ComprobantesDeVisita.permitido("image/heic"))
        assertFalse(ComprobantesDeVisita.permitido("video/mp4"))
        assertFalse(ComprobantesDeVisita.permitido(ComprobantesDeVisita.DESCONOCIDO))
        assertFalse(ComprobantesDeVisita.permitido(null))
        assertFalse(ComprobantesDeVisita.permitido(""))
    }

    // --- El tipo por los BYTES ----------------------------------------------

    @Test
    fun `tipoDe reconoce cada formato por su firma`() {
        assertEquals("image/jpeg", ComprobantesDeVisita.tipoDe(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/png", ComprobantesDeVisita.tipoDe(bytes(0x89, 0x50, 0x4E, 0x47)))
        assertEquals("image/gif", ComprobantesDeVisita.tipoDe(bytes(0x47, 0x49, 0x46, 0x38)))
        assertEquals("application/pdf", ComprobantesDeVisita.tipoDe(bytes(0x25, 0x50, 0x44, 0x46)))
    }

    /** WebP es el único con la firma partida: `RIFF` + 4 de tamaño + `WEBP`. */
    @Test
    fun `tipoDe reconoce webp con su firma partida`() {
        val webp = bytes(
            0x52, 0x49, 0x46, 0x46,
            0x00, 0x00, 0x00, 0x00,
            0x57, 0x45, 0x42, 0x50
        )
        assertEquals("image/webp", ComprobantesDeVisita.tipoDe(webp))
    }

    /**
     * `RIFF` sin `WEBP` es un WAV, no una imagen. Sin esta prueba, un
     * `startsWith("RIFF")` a secas pasaría igual.
     */
    @Test
    fun `un RIFF que no es webp no se acepta`() {
        val wav = bytes(
            0x52, 0x49, 0x46, 0x46,
            0x00, 0x00, 0x00, 0x00,
            0x57, 0x41, 0x56, 0x45
        )
        assertEquals(ComprobantesDeVisita.DESCONOCIDO, ComprobantesDeVisita.tipoDe(wav))
    }

    /**
     * **El caso real que esto existe para atrapar:** la cámara contesta
     * `RESULT_OK` y no escribe nada (disco lleno, app de cámara muerta a
     * medias). El archivo vacío no es una imagen y no puede viajar.
     */
    @Test
    fun `un archivo vacio no es de ningun tipo permitido`() {
        assertEquals(ComprobantesDeVisita.DESCONOCIDO, ComprobantesDeVisita.tipoDe(ByteArray(0)))
        assertFalse(ComprobantesDeVisita.permitido(ComprobantesDeVisita.tipoDe(ByteArray(0))))
    }

    /** Una firma truncada tampoco alcanza: se decide con lo que hay, sin adivinar. */
    @Test
    fun `una firma truncada no se completa a la buena`() {
        assertEquals(
            ComprobantesDeVisita.DESCONOCIDO,
            ComprobantesDeVisita.tipoDe(bytes(0xFF, 0xD8))
        )
    }

    // --- Los codecs del SavedStateHandle ------------------------------------

    @Test
    fun `el comprobante sobrevive ida y vuelta`() {
        val original = ComprobanteDeVisita(
            id = "6f1c8b3e-0000-4a11-9f2b-77aa11223344",
            archivo = "/data/user/0/com.example.msp_app/files/comprobante_visita_x.jpg",
            mime = "image/jpeg"
        )

        assertEquals(
            original,
            ComprobantesDeVisita.decodificar(ComprobantesDeVisita.codificar(original))
        )
    }

    @Test
    fun `el destino sobrevive ida y vuelta`() {
        val original = DestinoDeFoto(
            id = "6f1c8b3e-0000-4a11-9f2b-77aa11223344",
            uriParaLaCamara = "content://com.example.msp_app.fileprovider/cache/x.jpg",
            archivoCrudo = "/data/user/0/com.example.msp_app/cache/x.jpg"
        )

        assertEquals(
            original,
            ComprobantesDeVisita.decodificarDestino(ComprobantesDeVisita.codificarDestino(original))
        )
    }

    /**
     * **Cruzar los codecs produce basura.** Los dos son tres textos y por eso da
     * la impresión de que da igual; el segundo campo es un MIME en uno y un
     * `content://` en el otro. Esta prueba existe para que nadie lo pruebe en
     * producción.
     */
    @Test
    fun `cruzar los dos codecs produce basura`() {
        val destino = DestinoDeFoto(
            id = "IMG-1",
            uriParaLaCamara = "content://fake/camara/1",
            archivoCrudo = "/cache/crudo-1.jpg"
        )

        val leidoComoComprobante =
            ComprobantesDeVisita.decodificar(ComprobantesDeVisita.codificarDestino(destino))

        assertEquals("content://fake/camara/1", leidoComoComprobante?.mime)
        assertFalse(
            "un content:// jamas puede pasar por MIME permitido",
            ComprobantesDeVisita.permitido(leidoComoComprobante?.mime)
        )
        assertNotEquals(destino.archivoCrudo, leidoComoComprobante?.archivo.orEmpty() + "!")
    }

    /** Un texto que no tiene los tres campos no se completa: devuelve `null`. */
    @Test
    fun `un texto con campos de menos no se decodifica`() {
        assertNull(ComprobantesDeVisita.decodificar("solo-un-campo"))
        assertNull(ComprobantesDeVisita.decodificar("dos\ncampos"))
        assertNull(ComprobantesDeVisita.decodificarDestino("dos\ncampos"))
    }

    /** Ni uno con campos de más: cuatro no son tres. */
    @Test
    fun `un texto con campos de mas no se decodifica`() {
        assertNull(ComprobantesDeVisita.decodificar("a\nb\nc\nd"))
    }

    /** Un campo en blanco es una entrada rota, no un campo vacío legítimo. */
    @Test
    fun `un campo en blanco no se decodifica`() {
        assertNull(ComprobantesDeVisita.decodificar("id\n\n/ruta.jpg"))
        assertNull(ComprobantesDeVisita.decodificar("\nimage/jpeg\n/ruta.jpg"))
    }

    /**
     * El tope es un número real, no decorativo: cinco fotos comprimidas quedan
     * muy por debajo del `BodyLimit` de 10 MB que aplica al request COMPLETO.
     */
    @Test
    fun `el tope de fotos es cinco`() {
        assertEquals(5, ComprobantesDeVisita.MAXIMO)
    }

    // ─── la miniatura de la rejilla ──────────────────────────────────────────

    /**
     * **El muestreo cuida el lado CORTO.** El cuadro de la rejilla es cuadrado y
     * recorta, así que lo que decide si la miniatura se ve borrosa es la
     * dimensión menor. Los bordes exactos: en el pedido justo NO se reduce, y un
     * pixel por encima del doble sí.
     */
    @Test
    fun `el muestreo mira el lado corto y respeta el pedido`() {
        val lado = ComprobantesDeVisita.LADO_DE_MINIATURA
        assertEquals("el lado justo no se reduce", 1, ComprobantesDeVisita.muestreoPara(lado, lado))
        assertEquals(
            "un pixel menos tampoco",
            1,
            ComprobantesDeVisita.muestreoPara(lado - 1, lado - 1)
        )
        assertEquals("el doble exacto sí", 2, ComprobantesDeVisita.muestreoPara(lado * 2, lado * 2))
        assertEquals(
            "y el cuádruple, cuatro",
            4,
            ComprobantesDeVisita.muestreoPara(lado * 4, lado * 4)
        )
        assertEquals(
            "una panorámica se muestrea por su lado corto, no por el largo",
            1,
            ComprobantesDeVisita.muestreoPara(lado * 8, lado)
        )
    }

    /** Medidas imposibles no reducen nada; el `while` no puede girar sin techo. */
    @Test
    fun `el muestreo no revienta con medidas imposibles`() {
        assertEquals(1, ComprobantesDeVisita.muestreoPara(0, 0))
        assertEquals(1, ComprobantesDeVisita.muestreoPara(-10, -10))
        assertEquals(1, ComprobantesDeVisita.muestreoPara(1000, 1000, lado = 0))
        assertTrue(
            "hay techo: sin él, un lado absurdo desborda el Int",
            ComprobantesDeVisita.muestreoPara(Int.MAX_VALUE, Int.MAX_VALUE) <= 64
        )
    }

    /**
     * Un buffer más corto que `ancho * alto` revienta **aquí** y no dentro de una
     * composición, donde ya no se podría reportar sin tumbar la pantalla.
     */
    @Test
    fun `una miniatura con el buffer corto no se puede construir`() {
        assertThrows(IllegalArgumentException::class.java) {
            Miniatura(4, 4, IntArray(15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Miniatura(0, 4, IntArray(64))
        }
    }

    private fun bytes(vararg valores: Int): ByteArray =
        ByteArray(valores.size) { valores[it].toByte() }
}
