package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidor
import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.data.api.services.visits.CapacidadesDTO
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitaDetalleDTO
import com.example.msp_app.data.api.services.visits.VisitaExistenteDTO
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** El adapter de `by-ids?detalle=1` de la conciliación inversa del compromiso (P2 Tarea 18, E-APP-132). */
class V2CompromisoEnServidorRegistryTest {

    private val id1 = "6f1c2a3b-0000-4000-8000-000000000001"
    private val id2 = "6f1c2a3b-0000-4000-8000-000000000002"

    private fun dto(id: String?, promesaFecha: String? = null) = VisitaExistenteDTO(
        id = id,
        promesa_venta_id = promesaFecha?.let { 100 },
        promesa_fecha = promesaFecha,
        promesa_monto_centavos = promesaFecha?.let { 15000L },
        cita_fecha = null,
        cita_hora = null,
        imagen_ids = null
    )

    private class ApiFalsa(val responde: List<VisitaExistenteDTO>) : V2VisitsApi {
        val llamadas = mutableListOf<Pair<String, Int>>()
        override suspend fun visitasExistentesPorIdsDetalle(
            ids: String,
            detalle: Int
        ): List<VisitaExistenteDTO> {
            llamadas += ids to detalle
            return responde
        }

        override suspend fun crearVisita(idempotencyKey: String, body: CrearVisitaBody): VisitaDTO =
            throw AssertionError("no se espera")

        override suspend fun crearVisitaConImagenes(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): VisitaDTO = throw AssertionError("no se espera")

        override suspend fun visitasExistentesPorIds(ids: String): List<String> =
            throw AssertionError("no se espera")

        override suspend fun obtenerVisita(id: String): VisitaDetalleDTO =
            throw AssertionError("no se espera")

        override suspend fun capacidades(): CapacidadesDTO = throw AssertionError("no se espera")
    }

    @Test
    fun `traduce los bloques y descarta un elemento sin id`() = runBlocking {
        val api = ApiFalsa(
            listOf(
                dto(id1, promesaFecha = "2026-10-20"),
                dto(id2),
                dto(null, promesaFecha = "2026-10-21")
            )
        )

        val r = V2CompromisoEnServidorRegistry(api).findCompromisos(listOf(id1, id2))

        assertEquals(
            listOf(
                CompromisoEnServidor(id1, CompromisoLocal(100, "2026-10-20", 15000L, null, null)),
                CompromisoEnServidor(id2, CompromisoLocal.NINGUNO)
            ),
            r
        )
        assertEquals(listOf("$id1,$id2" to V2VisitsApi.DETALLE_SI), api.llamadas)
    }

    @Test
    fun `no se llama con lista vacia ni con mas de 100 ids`() {
        val registro = V2CompromisoEnServidorRegistry(ApiFalsa(emptyList()))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { registro.findCompromisos(emptyList()) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { registro.findCompromisos((1..101).map { "id-$it" }) }
        }
    }
}
