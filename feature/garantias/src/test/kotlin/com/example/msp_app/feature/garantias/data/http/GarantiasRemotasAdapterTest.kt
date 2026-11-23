package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * El transporte del API v2 sobre Retrofit.
 *
 * ## Qué protege
 *
 * El adaptador **no** rearma el cuerpo ni la clave: manda los bytes congelados
 * tal cual (D3). La prueba lee el [RequestBody] con un `okio.Buffer` y compara
 * bytes y `Content-Type` con lo que recibió, que es lo que el servidor compara
 * para decidir idempotencia. Un fallo de red se traduce a `FalloDeRed`, nunca a
 * una excepción que tumbe al trabajador, y un método que no es `POST` se rechaza
 * sin llamar al API.
 */
class GarantiasRemotasAdapterTest {

    private val cuerpo = CuerpoHttp.deTexto("""{"origen":"piso"}""", "application/json")

    @Test
    fun `un metodo que no es post se rechaza sin llamar al api`() = runTest {
        val api = ApiFalsa()
        val adaptador = GarantiasRemotasAdapter(api)

        val resultado = adaptador.enviar("clave-1", "PUT", RutasDeGarantia.ABRIR, cuerpo)

        assertEquals(0, api.llamadas)
        val respuesta = resultado as ResultadoDeLaLlamada.Respuesta
        assertEquals(405, respuesta.status)
    }

    @Test
    fun `una respuesta exitosa conserva el status y el cuerpo`() = runTest {
        val api = ApiFalsa(
            respuesta = Response.success(
                """{"id":"folio-1"}""".toResponseBody("application/json".toMediaType())
            )
        )
        val adaptador = GarantiasRemotasAdapter(api)

        val resultado = adaptador.enviar("clave-1", "POST", RutasDeGarantia.ABRIR, cuerpo)

        val respuesta = resultado as ResultadoDeLaLlamada.Respuesta
        assertEquals(200, respuesta.status)
        assertEquals("""{"id":"folio-1"}""", respuesta.cuerpo)
        assertEquals("clave-1", api.ultimaClave)
        assertEquals(RutasDeGarantia.ABRIR, api.ultimaRuta)
    }

    @Test
    fun `un fallo de red se traduce a fallo de red`() = runTest {
        val api = ApiFalsa(fallar = true)
        val adaptador = GarantiasRemotasAdapter(api)

        val resultado = adaptador.enviar("clave-1", "POST", RutasDeGarantia.ABRIR, cuerpo)

        assertTrue(resultado is ResultadoDeLaLlamada.FalloDeRed)
    }

    @Test
    fun `los bytes y el content type viajan identicos a lo encolado`() = runTest {
        val api = ApiFalsa()
        val adaptador = GarantiasRemotasAdapter(api)

        adaptador.enviar("clave-1", "POST", RutasDeGarantia.ABRIR, cuerpo)

        val enviado = api.ultimoCuerpo!!
        assertEquals("application/json", enviado.contentType().toString())
        assertEquals(cuerpo.texto, enviado.aBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `el multipart conserva su boundary fijo`() = runTest {
        val api = ApiFalsa()
        val adaptador = GarantiasRemotasAdapter(api)
        val multipart = CuerpoHttp.de(
            bytes = byteArrayOf(1, 2, 3),
            tipoDeContenido = CuerpoDeGarantia.TIPO_MULTIPART
        )

        adaptador.enviar("clave-1", "POST", RutasDeGarantia.ENTREGAR, multipart)

        assertEquals(
            "multipart/form-data; boundary=${CuerpoDeGarantia.BOUNDARY}",
            api.ultimoCuerpo!!.contentType().toString()
        )
        assertTrue(api.ultimoCuerpo!!.aBytes().contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `subir una imagen usa la misma via que un comando`() = runTest {
        val api = ApiFalsa()
        val adaptador = GarantiasRemotasAdapter(api)

        val resultado = adaptador.subirImagen("foto-1", RutasDeGarantia.IMAGENES, cuerpo)

        assertTrue(resultado is ResultadoDeLaLlamada.Respuesta)
        assertEquals("foto-1", api.ultimaClave)
        assertEquals(RutasDeGarantia.IMAGENES, api.ultimaRuta)
    }

    @Test
    fun `el detalle resuelve la ruta del folio y lo traduce al dominio`() = runTest {
        val api = ApiFalsa(
            detalleDto = DetalleDeGarantiaDto(
                garantia = GarantiaDto(id = "folio-1", folio = "G-1", origen = "cliente")
            )
        )
        val adaptador = GarantiasRemotasAdapter(api)

        val detalle = adaptador.detalle("folio-1")

        assertEquals("v2/garantias/folio-1", api.ultimaRuta)
        assertEquals("G-1", detalle.folio)
        assertEquals(OrigenDeFolio.CLIENTE, detalle.origen)
    }

    @Test
    fun `un error del servidor conserva codigo y detalle`() = runTest {
        val api = ApiFalsa(
            respuesta = Response.error(
                409,
                """
                {"status":409,"detail":"la clave ya se usó",
                 "errors":[{"message":"code=warranty_idempotency_key_mismatch"}]}
                """.trimIndent().toResponseBody("application/json".toMediaType())
            )
        )
        val adaptador = GarantiasRemotasAdapter(api)

        val resultado = adaptador.enviar("clave-1", "POST", RutasDeGarantia.ABRIR, cuerpo)

        val respuesta = resultado as ResultadoDeLaLlamada.Respuesta
        assertEquals(409, respuesta.status)
        assertEquals("warranty_idempotency_key_mismatch", respuesta.codigo)
        assertEquals("la clave ya se usó", respuesta.detalle)
    }

    private class ApiFalsa(
        private val respuesta: Response<ResponseBody> = Response.success(
            "".toResponseBody("application/json".toMediaType())
        ),
        private val fallar: Boolean = false,
        private val detalleDto: DetalleDeGarantiaDto = DetalleDeGarantiaDto()
    ) : V2GarantiasApi {

        var llamadas: Int = 0
        var ultimaClave: String? = null
        var ultimaRuta: String? = null
        var ultimoCuerpo: RequestBody? = null

        override suspend fun enviar(
            clave: String,
            ruta: String,
            cuerpo: RequestBody
        ): Response<ResponseBody> {
            llamadas++
            ultimaClave = clave
            ultimaRuta = ruta
            ultimoCuerpo = cuerpo
            if (fallar) throw IOException("sin señal")
            return respuesta
        }

        override suspend fun detalle(ruta: String): DetalleDeGarantiaDto {
            ultimaRuta = ruta
            if (fallar) throw IOException("sin señal")
            return detalleDto
        }
    }

    private fun RequestBody.aBytes(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}
