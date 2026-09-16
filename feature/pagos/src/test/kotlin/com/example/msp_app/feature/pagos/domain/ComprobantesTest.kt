package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto
import com.example.msp_app.feature.pagos.domain.model.Miniatura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas puras del comprobante: **qué tipo se acepta**, **qué tipo es un
 * archivo de verdad**, y **qué sobrevive a la muerte del proceso**.
 *
 * Las tres son de dominio y se prueban sin Android, sin Room y sin cámara.
 */
class ComprobantesTest {

    // --- La whitelist: la MISMA que declara el servidor ----------------------

    /**
     * Los cinco tipos son los del `contentType` de `CrearPagoMultipartFields.
     * Imagen` (`dto_pago_recibido.go`). Si esta lista se despega de aquélla, el
     * teléfono guarda comprobantes que ningún reintento va a poder subir.
     */
    @Test
    fun `los cinco tipos del contrato del servidor pasan`() {
        listOf("image/jpeg", "image/png", "image/gif", "image/webp", "application/pdf")
            .forEach { assertTrue("$it esta en el contrato", Comprobantes.permitido(it)) }
    }

    @Test
    fun `lo que el servidor no acepta no pasa`() {
        listOf("image/heic", "image/heif", "video/mp4", "application/zip", "text/plain", "", " ")
            .forEach { assertFalse("$it no esta en el contrato", Comprobantes.permitido(it)) }
        assertFalse("un tipo ausente tampoco", Comprobantes.permitido(null))
        assertFalse(Comprobantes.permitido(Comprobantes.DESCONOCIDO))
    }

    @Test
    fun `el tipo se compara sin importar mayusculas ni espacios`() {
        assertTrue(Comprobantes.permitido("IMAGE/JPEG"))
        assertTrue(Comprobantes.permitido("  image/png  "))
    }

    // --- El tipo real, leído de los bytes ------------------------------------

    /**
     * El nombre del archivo lo pone la app (`...jpg`), así que la extensión no
     * puede desmentir a la cámara. Los bytes sí.
     */
    @Test
    fun `cada firma se reconoce por sus bytes`() {
        assertEquals("image/jpeg", Comprobantes.tipoDe(bytes(0xFF, 0xD8, 0xFF, 0xE0)))
        assertEquals("image/png", Comprobantes.tipoDe(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D)))
        assertEquals("image/gif", Comprobantes.tipoDe(bytes(0x47, 0x49, 0x46, 0x38, 0x39)))
        assertEquals("application/pdf", Comprobantes.tipoDe(bytes(0x25, 0x50, 0x44, 0x46, 0x2D)))
        assertEquals(
            "image/webp",
            Comprobantes.tipoDe(
                // "RIFF" + 4 bytes de tamaño + "WEBP".
                bytes(0x52, 0x49, 0x46, 0x46, 0x1A, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50)
            )
        )
    }

    /**
     * **El caso que motiva todo esto:** la cámara contesta `RESULT_OK` y no
     * escribe nada (disco lleno, la app de cámara se muere a medias). El
     * archivo vacío tiene que caer en un tipo NO permitido, o la app guardaría
     * un comprobante que el servidor va a rechazar cuando ya no hay forma de
     * volver a tomar la foto.
     */
    @Test
    fun `un archivo vacio no es de ningun tipo permitido`() {
        val tipo = Comprobantes.tipoDe(ByteArray(0))
        assertEquals(Comprobantes.DESCONOCIDO, tipo)
        assertFalse(Comprobantes.permitido(tipo))
    }

    @Test
    fun `unos bytes que no son de ningun formato conocido son desconocidos`() {
        assertEquals(Comprobantes.DESCONOCIDO, Comprobantes.tipoDe(bytes(0x00, 0x01, 0x02, 0x03)))
    }

    /**
     * `RIFF` sin `WEBP` es otro contenedor (un WAV, por ejemplo). Reconocerlo
     * como WebP mandaría al servidor un audio con `Content-Type: image/webp`.
     */
    @Test
    fun `un RIFF que no es WEBP no se confunde con uno`() {
        val wav = bytes(0x52, 0x49, 0x46, 0x46, 0x24, 0x00, 0x00, 0x00, 0x57, 0x41, 0x56, 0x45)
        assertEquals(Comprobantes.DESCONOCIDO, Comprobantes.tipoDe(wav))
    }

    /** Una firma truncada no alcanza para afirmar nada. */
    @Test
    fun `una firma incompleta no se da por buena`() {
        assertEquals(Comprobantes.DESCONOCIDO, Comprobantes.tipoDe(bytes(0xFF, 0xD8)))
        assertEquals(
            "un RIFF sin los 12 bytes no se puede decidir",
            Comprobantes.DESCONOCIDO,
            Comprobantes.tipoDe(bytes(0x52, 0x49, 0x46, 0x46, 0x1A))
        )
    }

    // --- El codec del `SavedStateHandle` -------------------------------------

    @Test
    fun `un comprobante ida y vuelta es el mismo comprobante`() {
        val original = ComprobanteDelAbono(
            id = "b3f1c2d4-0000-4000-8000-000000000001",
            archivo = "/data/user/0/com.example.msp_app/files/comprobante_pago_1.jpg",
            mime = "image/jpeg"
        )
        assertEquals(original, Comprobantes.decodificar(Comprobantes.codificar(original)))
    }

    @Test
    fun `un destino ida y vuelta es el mismo destino`() {
        val original = DestinoDeFoto(
            id = "b3f1c2d4-0000-4000-8000-000000000002",
            uriParaLaCamara = "content://com.example.msp_app.fileprovider/cache/crudo.jpg",
            archivoCrudo = "/data/user/0/com.example.msp_app/cache/crudo.jpg"
        )
        assertEquals(
            original,
            Comprobantes.decodificarDestino(Comprobantes.codificarDestino(original))
        )
    }

    /**
     * **Los dos codecs no son intercambiables.** Sus campos segundos significan
     * cosas distintas —un MIME contra un `content://`—, y cruzarlos es el
     * defecto de la familia "el identificador equivocado en el lugar del otro"
     * que este plan ya cazó siete veces. Aquí se afirma que el cruce PRODUCE
     * basura, para que nadie lo haga creyendo que da igual.
     */
    @Test
    fun `decodificar un destino como comprobante pone el uri donde va el mime`() {
        val destino = DestinoDeFoto(
            id = "IMG-1",
            uriParaLaCamara = "content://fake/1",
            archivoCrudo = "/tmp/1.jpg"
        )
        val cruzado = Comprobantes.decodificar(Comprobantes.codificarDestino(destino))!!
        assertEquals("content://fake/1", cruzado.mime)
        assertFalse("y ese mime no pasaria la whitelist", Comprobantes.permitido(cruzado.mime))
    }

    @Test
    fun `un texto que no es un comprobante se descarta en vez de tumbar la pantalla`() {
        assertNull(Comprobantes.decodificar(""))
        assertNull("faltan campos", Comprobantes.decodificar("solo-un-id\nimage/jpeg"))
        assertNull("sobran campos", Comprobantes.decodificar("a\nb\nc\nd"))
        assertNull("un campo vacio no sirve", Comprobantes.decodificar("a\n\nc"))
        assertNull(Comprobantes.decodificarDestino("a\nb"))
    }

    /** El techo del teléfono. Es una regla del dominio, no de la pantalla. */
    @Test
    fun `el maximo de comprobantes es el que dice el dominio`() {
        assertEquals(5, Comprobantes.MAXIMO)
    }

    private fun bytes(vararg valores: Int): ByteArray =
        ByteArray(valores.size) { valores[it].toByte() }

    // ─── la miniatura de la rejilla ──────────────────────────────────────────

    /**
     * **El muestreo cuida el lado CORTO.** El cuadro de la rejilla es cuadrado y
     * recorta, así que lo que decide si la miniatura se ve borrosa es la
     * dimensión menor. Los bordes exactos: en el pedido justo NO se reduce, y un
     * pixel por encima del doble sí.
     */
    @Test
    fun `el muestreo mira el lado corto y respeta el pedido`() {
        val lado = Comprobantes.LADO_DE_MINIATURA
        assertEquals("el lado justo no se reduce", 1, Comprobantes.muestreoPara(lado, lado))
        assertEquals("un pixel menos tampoco", 1, Comprobantes.muestreoPara(lado - 1, lado - 1))
        assertEquals("el doble exacto sí", 2, Comprobantes.muestreoPara(lado * 2, lado * 2))
        assertEquals("y el cuádruple, cuatro", 4, Comprobantes.muestreoPara(lado * 4, lado * 4))
        assertEquals(
            "una panorámica se muestrea por su lado corto, no por el largo",
            1,
            Comprobantes.muestreoPara(lado * 8, lado)
        )
    }

    /** Medidas imposibles no reducen nada; el `while` no puede girar sin techo. */
    @Test
    fun `el muestreo no revienta con medidas imposibles`() {
        assertEquals(1, Comprobantes.muestreoPara(0, 0))
        assertEquals(1, Comprobantes.muestreoPara(-10, -10))
        assertEquals(1, Comprobantes.muestreoPara(1000, 1000, lado = 0))
        assertTrue(
            "hay techo: sin él, un lado absurdo desborda el Int",
            Comprobantes.muestreoPara(Int.MAX_VALUE, Int.MAX_VALUE) <= 64
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
}
