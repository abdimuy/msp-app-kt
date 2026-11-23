package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cuerpo que viaja se arma siempre igual.
 *
 * ## Qué protege
 *
 * Un multipart con boundary aleatorio o un `Content-Type` deducido del archivo
 * harían que el **mismo** comando pareciera otro en el reintento, y el servidor
 * respondería 409 `warranty_idempotency_key_mismatch`. El boundary es fijo y el
 * tipo de la firma también (mutante A9). La prueba compara **bytes**, no
 * objetos: es el mismo criterio con el que el servidor decide.
 */
class CuerpoDeGarantiaTest {

    private val ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332)

    @Test
    fun `el multipart de la entrega es byte por byte el mismo en dos armados`() {
        val datos = CuerpoDeGarantia.datosDeEntrega(
            deviceCreatedAt = "2026-11-13T10:00:00Z",
            ubicacion = ubicacion,
            rutaDeLaFirma = "/data/firmas/firma.png"
        )
        val firma = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A)

        val primero = CuerpoDeGarantia.entrega(datos, firma)
        val segundo = CuerpoDeGarantia.entrega(datos, firma)

        assertTrue(primero.bytes.contentEquals(segundo.bytes))
        assertEquals(primero, segundo)
        assertEquals(CuerpoDeGarantia.TIPO_MULTIPART, primero.tipoDeContenido)
    }

    @Test
    fun `el boundary del multipart es fijo y no se sortea`() {
        val datos = CuerpoDeGarantia.datosDeEntrega(
            deviceCreatedAt = "2026-11-13T10:00:00Z",
            ubicacion = ubicacion,
            rutaDeLaFirma = "/data/firmas/firma.png"
        )
        val cuerpo = CuerpoDeGarantia.entrega(datos, byteArrayOf(1, 2, 3))

        assertTrue(cuerpo.texto.contains("--${CuerpoDeGarantia.BOUNDARY}"))
        assertEquals(
            "multipart/form-data; boundary=${CuerpoDeGarantia.BOUNDARY}",
            cuerpo.tipoDeContenido
        )
    }

    @Test
    fun `la parte de la firma declara image png sin mirar el archivo`() {
        val datos = CuerpoDeGarantia.datosDeEntrega(
            deviceCreatedAt = "2026-11-13T10:00:00Z",
            ubicacion = ubicacion,
            rutaDeLaFirma = "/data/firmas/firma.jpeg"
        )
        val cuerpo = CuerpoDeGarantia.entrega(datos, byteArrayOf(1, 2, 3))

        assertTrue(cuerpo.texto.contains("Content-Type: ${ReglasDeEntrega.MIME_DE_LA_FIRMA}"))
        assertEquals("image/png", ReglasDeEntrega.MIME_DE_LA_FIRMA)
        assertTrue(cuerpo.texto.contains("filename=\"firma.png\""))
    }

    @Test
    fun `los datos de la entrega sobreviven al json guardado`() {
        val datos = CuerpoDeGarantia.datosDeEntrega(
            deviceCreatedAt = "2026-11-13T10:00:00Z",
            ubicacion = ubicacion,
            rutaDeLaFirma = "/data/firmas/firma.png"
        )

        val recuperados = DatosDeEntrega.deJson(datos.aJson())

        assertEquals(datos, recuperados)
    }

    @Test
    fun `un json de entrega sin firma no se puede leer`() {
        assertNull(DatosDeEntrega.deJson("""{"device_created_at":"2026-11-13T10:00:00Z"}"""))
    }

    @Test
    fun `un json de entrega que no es objeto no se puede leer`() {
        assertNull(DatosDeEntrega.deJson("[]"))
        assertNull(DatosDeEntrega.deJson("no es json"))
    }

    @Test
    fun `la foto usa el mime que se le pasa`() {
        val cuerpo = CuerpoDeGarantia.imagen(byteArrayOf(1, 2, 3), "image/jpeg")

        assertTrue(cuerpo.texto.contains("Content-Type: image/jpeg"))
        assertEquals(CuerpoDeGarantia.TIPO_MULTIPART, cuerpo.tipoDeContenido)
    }
}
