package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.entities.PaymentImageEntity
import java.io.File
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El armado del multipart de comprobantes — **el punto donde un índice
 * corrido manda cada foto con el id de otra**.
 *
 * Sin Android y sin red: son archivos de verdad en una carpeta temporal y las
 * partes que okhttp produce, leídas de vuelta.
 */
class ComprobantesDePagoTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    // --- El campo y el pareo, que son contrato del servidor ------------------

    /**
     * El campo del archivo es **`imagen`**, singular: es lo que declara
     * `CrearPagoMultipartFields` (`form:"imagen"`). El default del helper de
     * ventas es `"imagenes"`, y con ése el servidor contestaría 200 sin haber
     * visto una sola foto — un fallo invisible.
     */
    @Test
    fun `el campo del archivo es imagen, en singular`() {
        val resultado = partesDeComprobantes(listOf(fila("IMG-1", "uno.jpg")))

        assertEquals(listOf("imagen", "id_0"), resultado.partes.map { it.nombre() })
    }

    /** El `id_<n>` lleva el UUID de LA IMAGEN, que es lo que hace idempotente al reintento. */
    @Test
    fun `el id que viaja es el de la imagen, no el del pago`() {
        val resultado = partesDeComprobantes(listOf(fila("IMG-1", "uno.jpg", pagoId = "PAGO-9")))

        val id = resultado.partes.single { it.nombre() == "id_0" }.texto()
        assertEquals("IMG-1", id)
        assertTrue("el id del pago no viaja como id de imagen", id != "PAGO-9")
    }

    @Test
    fun `varias imagenes se numeran en orden`() {
        val resultado = partesDeComprobantes(
            listOf(fila("IMG-1", "uno.jpg"), fila("IMG-2", "dos.jpg"), fila("IMG-3", "tres.jpg"))
        )

        assertEquals(
            listOf("imagen", "id_0", "imagen", "id_1", "imagen", "id_2"),
            resultado.partes.map { it.nombre() }
        )
        assertEquals(
            listOf("IMG-1", "IMG-2", "IMG-3"),
            resultado.partes.filter { it.nombre()?.startsWith("id_") == true }.map { it.texto() }
        )
        assertEquals(3, resultado.enviadas.size)
    }

    /**
     * **El defecto que este test existe para atrapar.** La segunda imagen ya no
     * tiene archivo —el sistema vació la caché, el usuario borró—, así que se
     * omite. Si el índice contara FILAS en vez de partes agregadas, la tercera
     * viajaría como `id_2` mientras es la segunda parte `imagen`: el servidor la
     * guardaría con el id equivocado, y el reintento subiría un duplicado.
     *
     * El id de la que sí viajó en segundo lugar tiene que ser `id_1`.
     */
    @Test
    fun `una imagen sin archivo no corre los ids de las demas`() {
        val resultado = partesDeComprobantes(
            listOf(
                fila("IMG-1", "uno.jpg"),
                filaSinArchivo("IMG-2"),
                fila("IMG-3", "tres.jpg")
            )
        )

        assertEquals(
            listOf("imagen", "id_0", "imagen", "id_1"),
            resultado.partes.map { it.nombre() }
        )
        assertEquals(
            listOf("IMG-1", "IMG-3"),
            resultado.partes.filter { it.nombre()?.startsWith("id_") == true }.map { it.texto() }
        )
        assertEquals(
            "SUBIDA_EN solo puede marcar lo que viajo",
            listOf("IMG-1", "IMG-3"),
            resultado.enviadas.map { it.ID }
        )
        assertEquals(1, resultado.omitidas)
    }

    /**
     * Un tipo fuera de la whitelist tumbaría el pago ENTERO con un 422. Se
     * queda fuera del multipart, y se cuenta.
     */
    @Test
    fun `un tipo no permitido se omite en vez de tumbar el pago`() {
        val resultado = partesDeComprobantes(
            listOf(fila("IMG-1", "uno.jpg", mime = "video/mp4"), fila("IMG-2", "dos.jpg"))
        )

        assertEquals(listOf("IMG-2"), resultado.enviadas.map { it.ID })
        assertEquals(
            listOf("IMG-2"),
            resultado.partes.filter { it.nombre() == "id_0" }.map { it.texto() }
        )
        assertEquals(1, resultado.omitidas)
    }

    /**
     * **Control positivo del test de arriba.** El mismo montaje, con el tipo
     * permitido, SÍ manda las dos: la omisión de arriba la produce el filtro, no
     * un fixture que nunca podía pasar.
     */
    @Test
    fun `control positivo, el mismo montaje con jpeg manda las dos`() {
        val resultado = partesDeComprobantes(
            listOf(fila("IMG-1", "uno.jpg"), fila("IMG-2", "dos.jpg"))
        )

        assertEquals(listOf("IMG-1", "IMG-2"), resultado.enviadas.map { it.ID })
        assertEquals(0, resultado.omitidas)
    }

    /** El `Content-Type` de la parte sale de la FILA, no de la extensión. */
    @Test
    fun `el tipo de la parte es el que guarda la fila`() {
        val resultado = partesDeComprobantes(
            listOf(fila("IMG-1", "recibo.jpg", mime = "application/pdf"))
        )

        val archivo = resultado.partes.single { it.nombre() == "imagen" }
        assertEquals("application/pdf", archivo.body.contentType().toString())
    }

    @Test
    fun `la descripcion viaja pareada cuando la hay`() {
        val resultado = partesDeComprobantes(
            listOf(fila("IMG-1", "uno.jpg", descripcion = "recibo firmado"))
        )

        assertEquals(
            listOf("imagen", "id_0", "descripcion_0"),
            resultado.partes.map { it.nombre() }
        )
        assertEquals(
            "recibo firmado",
            resultado.partes.single { it.nombre() == "descripcion_0" }.texto()
        )
    }

    @Test
    fun `sin comprobantes no hay partes`() {
        val resultado = partesDeComprobantes(emptyList())

        assertEquals(emptyList<MultipartBody.Part>(), resultado.partes)
        assertEquals(emptyList<PaymentImageEntity>(), resultado.enviadas)
        assertEquals(0, resultado.omitidas)
    }

    // --- Plomería ------------------------------------------------------------

    private fun fila(
        id: String,
        nombre: String,
        pagoId: String = "PAGO-1",
        mime: String = "image/jpeg",
        descripcion: String? = null
    ): PaymentImageEntity {
        val archivo = File(carpeta.root, nombre)
        archivo.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        return PaymentImageEntity(
            ID = id,
            PAGO_ID = pagoId,
            URI = archivo.absolutePath,
            MIME = mime,
            DESCRIPCION = descripcion,
            ORDEN = 0,
            CREADA_EN = "2026-09-04T18:00:00Z"
        )
    }

    /** Una fila cuya ruta apunta a un archivo que ya no está. */
    private fun filaSinArchivo(id: String) = PaymentImageEntity(
        ID = id,
        PAGO_ID = "PAGO-1",
        URI = File(carpeta.root, "$id-borrado.jpg").absolutePath,
        MIME = "image/jpeg",
        ORDEN = 0,
        CREADA_EN = "2026-09-04T18:00:00Z"
    )

    /** El nombre de campo que la parte declara en su `Content-Disposition`. */
    private fun MultipartBody.Part.nombre(): String? = headers?.get("Content-Disposition")
        ?.substringAfter("name=\"", "")
        ?.substringBefore('"')

    /** El cuerpo de una parte de texto, leído de vuelta. */
    private fun MultipartBody.Part.texto(): String = Buffer().also { body.writeTo(it) }.readUtf8()
}
