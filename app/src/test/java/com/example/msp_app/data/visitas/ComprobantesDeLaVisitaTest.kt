package com.example.msp_app.data.visitas

import com.example.msp_app.core.database.entities.VisitImageEntity
import java.io.File
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El armador del multipart de visitas: las partes que el servidor de verdad
 * espera, no las que uno supone.
 *
 * Lo que se verifica contra `internal/visitas/infra/visitashttp`:
 *
 * - el campo del archivo es **`imagen`**, singular (`form.File["imagen"]`);
 * - cada parte lleva su **`id_<n>` posicional**, y el `n` cuenta **partes
 *   agregadas**, no filas miradas (`parsePositionalImagenID` parea por
 *   posición);
 * - cada parte declara su **`Content-Type`** — `partContentType` devuelve `""`
 *   si falta, y `""` no está en la whitelist, así que la visita entera se caería
 *   con 422.
 */
class ComprobantesDeLaVisitaTest {

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    @Test
    fun `una imagen arma su parte y su id posicional`() {
        val partes = partesDeComprobantesDeVisita(listOf(fila("IMG-1", orden = 0)))

        assertEquals(listOf("imagen", "id_0"), partes.partes.map { it.nombre() })
        assertEquals("IMG-1", partes.partes[1].texto())
        assertEquals(listOf("IMG-1"), partes.enviadas.map { it.ID })
        assertEquals(0, partes.omitidas)
    }

    /**
     * **El `Content-Type` de la parte del archivo no es opcional.** Sin él el
     * servidor lee `""`, que no está en la whitelist, y contesta 422 a la visita
     * entera — la foto tumbando trabajo de campo.
     */
    @Test
    fun `la parte del archivo declara su Content-Type`() {
        val partes = partesDeComprobantesDeVisita(listOf(fila("IMG-1", orden = 0)))

        assertEquals("image/jpeg", partes.partes.first().body.contentType()?.toString())
    }

    @Test
    fun `la descripcion viaja como descripcion_n cuando la hay`() {
        val partes = partesDeComprobantesDeVisita(
            listOf(fila("IMG-1", orden = 0, descripcion = "la puerta del cliente"))
        )

        assertEquals(listOf("imagen", "id_0", "descripcion_0"), partes.partes.map { it.nombre() })
        assertEquals("la puerta del cliente", partes.partes[2].texto())
    }

    /** Una descripción en blanco no manda una parte vacía. */
    @Test
    fun `una descripcion en blanco no arma parte`() {
        val partes = partesDeComprobantesDeVisita(
            listOf(fila("IMG-1", orden = 0, descripcion = "   "))
        )

        assertEquals(listOf("imagen", "id_0"), partes.partes.map { it.nombre() })
    }

    /**
     * **El `n` cuenta partes agregadas, no filas miradas.** Con el índice de la
     * fila, la tercera imagen viajaría como `id_2` siendo la SEGUNDA parte
     * `imagen`, y el servidor parea por posición: la foto 2 subiría con el id
     * de la 3.
     */
    @Test
    fun `una fila omitida no corre los ids de las demas`() {
        val partes = partesDeComprobantesDeVisita(
            listOf(
                fila("IMG-1", orden = 0),
                filaSinArchivo("IMG-FANTASMA", orden = 1),
                fila("IMG-3", orden = 2)
            )
        )

        assertEquals(
            listOf("imagen", "id_0", "imagen", "id_1"),
            partes.partes.map { it.nombre() }
        )
        assertEquals(listOf("IMG-1", "IMG-3"), partes.enviadas.map { it.ID })
        assertEquals(1, partes.omitidas)
    }

    /**
     * Un tipo fuera de la whitelist se omite en vez de tumbar la visita. La fila
     * la pudo escribir otra versión de la app, y mandarla sería un 422 para la
     * visita entera.
     */
    @Test
    fun `un tipo no permitido se omite en vez de tumbar la visita`() {
        val partes = partesDeComprobantesDeVisita(
            listOf(fila("IMG-MALA", orden = 0, mime = "image/heic"), fila("IMG-2", orden = 1))
        )

        assertEquals(listOf("IMG-2"), partes.enviadas.map { it.ID })
        assertEquals(listOf("imagen", "id_0"), partes.partes.map { it.nombre() })
        assertEquals(1, partes.omitidas)
    }

    /** Sin filas no hay partes: es el caso normal, y no arma un multipart vacío. */
    @Test
    fun `sin filas no hay partes`() {
        val partes = partesDeComprobantesDeVisita(emptyList())

        assertEquals(emptyList<Any>(), partes.partes)
        assertEquals(emptyList<Any>(), partes.enviadas)
        assertEquals(0, partes.omitidas)
    }

    private fun fila(
        id: String,
        orden: Int = 0,
        mime: String = "image/jpeg",
        descripcion: String? = null
    ): VisitImageEntity {
        val archivo = File(carpeta.root, "$id.jpg")
        archivo.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        return VisitImageEntity(
            ID = id,
            VISITA_ID = "visita-001",
            URI = archivo.absolutePath,
            MIME = mime,
            DESCRIPCION = descripcion,
            ORDEN = orden,
            CREADA_EN = "2026-09-04T17:00:00Z"
        )
    }

    private fun filaSinArchivo(id: String, orden: Int) = VisitImageEntity(
        ID = id,
        VISITA_ID = "visita-001",
        URI = File(carpeta.root, "$id-borrado.jpg").absolutePath,
        MIME = "image/jpeg",
        ORDEN = orden,
        CREADA_EN = "2026-09-04T17:00:00Z"
    )

    private fun MultipartBody.Part.nombre(): String? = headers?.get("Content-Disposition")
        ?.substringAfter("name=\"", "")
        ?.substringBefore('"')

    private fun MultipartBody.Part.texto(): String = Buffer().also { body.writeTo(it) }.readUtf8()
}
