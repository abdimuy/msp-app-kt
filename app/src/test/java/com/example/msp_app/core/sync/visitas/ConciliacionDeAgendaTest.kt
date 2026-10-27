package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.common.visitas.domain.CompromisoVigente
import com.example.msp_app.core.common.visitas.domain.CuentaCompromiso
import com.example.msp_app.core.common.visitas.domain.EstadoCompromiso
import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.agenda.FuenteDeAgenda
import com.example.msp_app.data.api.services.visits.ConciliacionAgendaRequest
import com.example.msp_app.data.api.services.visits.ConciliacionAgendaResponse
import com.example.msp_app.data.api.services.visits.DiferenciaAgendaDto
import com.example.msp_app.data.api.services.visits.VisitasAgendaApi
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/**
 * La conciliación directa de la agenda (plan visitas P2, Tarea 18; E-APP-131):
 * qué manda, qué cuenta y qué hace cuando el servidor no contesta. Sin Room ni
 * red: la fuente y el servidor son falsos. El camino con Room y los datos
 * reales del sync lo cubre `ConciliacionAgendaCasosCompartidosTest`.
 *
 * Robolectric solo porque la clase registra con `android.util.Log`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class ConciliacionDeAgendaTest {

    private val zona = 21
    private val ahora = Instant.parse("2026-10-16T18:00:00Z")

    private class FuenteFalsa(
        val cuentas: List<CuentaCompromiso>,
        val propias: List<VisitEntity> = emptyList(),
        val errorAlLeer: Throwable? = null
    ) : FuenteDeAgenda {
        override suspend fun cuentasDeZona(zona: Int): List<CuentaCompromiso> {
            errorAlLeer?.let { throw it }
            return cuentas
        }

        override suspend fun visitasPropias(desdeAproximado: String): List<VisitEntity> = propias
        override suspend fun visitasDeZona(zona: Int): List<VisitaZonaEntity> = emptyList()
        override suspend fun pagos(): List<PaymentEntity> = emptyList()
    }

    private class ApiFalsa(
        val responde: suspend (ConciliacionAgendaRequest) -> ConciliacionAgendaResponse
    ) : VisitasAgendaApi {
        val peticiones = mutableListOf<ConciliacionAgendaRequest>()
        override suspend fun conciliar(
            body: ConciliacionAgendaRequest
        ): ConciliacionAgendaResponse {
            peticiones += body
            return responde(body)
        }
    }

    private fun visita(id: String, venta: Int, promesa: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = venta / 10,
        COBRADOR_ID = 7,
        FECHA = "2026-10-15T17:00:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        TIPO_VISITA = "Pidió que regrese otro día",
        ZONA_CLIENTE_ID = 21,
        IMPTE_DOCTO_CC_ID = venta,
        PROMESA_VENTA_ID = venta,
        PROMESA_FECHA = promesa,
        PROMESA_MONTO_CENTAVOS = 15000L
    )

    private fun agenda(fuente: FuenteDeAgenda) = AgendaLocal(fuente)

    private fun sinDiferencias() = ApiFalsa { ConciliacionAgendaResponse(emptyList()) }

    @Test
    fun `manda zona hoy del negocio y los items ordenados por venta`() = runBlocking {
        val api = sinDiferencias()
        val fuente = FuenteFalsa(
            cuentas = listOf(CuentaCompromiso(300, 30), CuentaCompromiso(100, 10)),
            propias = listOf(
                visita("00000000-0000-3000-8000-000000000003", 300, "2026-10-20"),
                visita("00000000-0000-3000-8000-000000000001", 100, "2026-10-16")
            )
        )

        val r = ConciliacionDeAgenda(api, agenda(fuente)).conciliar(zona, ahora)

        val p = api.peticiones.single()
        assertEquals(zona, p.zona_id)
        // 18:00Z del 16 son las 12:00 del 16 en el negocio.
        assertEquals("2026-10-16", p.hoy)
        assertEquals(listOf(100, 300), p.items.map { it.venta_id })
        assertEquals(listOf("vigente_hoy", "vigente_futura"), p.items.map { it.estado })
        assertEquals(ResultadoConciliacionDeAgenda.Ok(2, 0, emptyList()), r)
    }

    /** El "hoy" es el del negocio, no el UTC: 03:00Z del 17 todavía es el 16 en la Ciudad de México. */
    @Test
    fun `el hoy es el del negocio y no el de UTC`() = runBlocking {
        val api = sinDiferencias()

        ConciliacionDeAgenda(api, agenda(FuenteFalsa(emptyList())))
            .conciliar(zona, Instant.parse("2026-10-17T03:00:00Z"))

        assertEquals("2026-10-16", api.peticiones.single().hoy)
    }

    @Test
    fun `cuenta las diferencias y devuelve solo ids numericos de venta`() = runBlocking {
        val api = ApiFalsa {
            ConciliacionAgendaResponse(
                listOf(
                    DiferenciaAgendaDto(300, null, null),
                    DiferenciaAgendaDto(100, null, null)
                )
            )
        }

        val r = ConciliacionDeAgenda(api, agenda(FuenteFalsa(emptyList()))).conciliar(zona, ahora)

        assertEquals(ResultadoConciliacionDeAgenda.Ok(0, 2, listOf(100, 300)), r)
    }

    @Test
    fun `una respuesta sin diferencias es una falla y no un cero`() = runBlocking {
        val api = ApiFalsa { ConciliacionAgendaResponse(null) }

        val r = ConciliacionDeAgenda(api, agenda(FuenteFalsa(emptyList()))).conciliar(zona, ahora)

        assertTrue(r is ResultadoConciliacionDeAgenda.Fallida)
    }

    @Test
    fun `mas ventas de las que acepta el servidor no se manda`() = runBlocking {
        val api = sinDiferencias()
        val n = VisitasAgendaApi.MAX_ITEMS + 1
        val itemsFalsos = (1..n).associateWith { v ->
            CompromisoVigente(
                v,
                "00000000-0000-3000-8000-%012d".format(v),
                TipoCompromiso.PROMESA,
                EstadoCompromiso.VIGENTE_FUTURA,
                LocalDate.parse("2026-10-20"),
                null as LocalTime?,
                null
            )
        }

        val items = ConciliacionDeAgenda.itemsDe(itemsFalsos)
        assertEquals(n, items.size)
        // La clase decide con el tope del API: se prueba con el mismo mapa por la fuente real.
        val fuente = FuenteFalsa(
            cuentas = (1..n).map { CuentaCompromiso(it, it) },
            propias = (1..n).map {
                visita(
                    "00000000-0000-3000-8000-%012d".format(it),
                    it,
                    "2026-10-20"
                )
            }
        )
        val r = ConciliacionDeAgenda(api, agenda(fuente)).conciliar(zona, ahora)

        assertEquals(ResultadoConciliacionDeAgenda.Omitida(MotivoOmitida.DEMASIADAS_VENTAS, n), r)
        assertTrue("no se mandó nada", api.peticiones.isEmpty())
    }

    @Test
    fun `5000 ventas exactas si se mandan`() = runBlocking {
        val api = sinDiferencias()
        val n = VisitasAgendaApi.MAX_ITEMS
        val fuente = FuenteFalsa(
            cuentas = (1..n).map { CuentaCompromiso(it, it) },
            propias = (1..n).map {
                visita(
                    "00000000-0000-3000-8000-%012d".format(it),
                    it,
                    "2026-10-20"
                )
            }
        )

        val r = ConciliacionDeAgenda(api, agenda(fuente)).conciliar(zona, ahora)

        assertEquals(ResultadoConciliacionDeAgenda.Ok(n, 0, emptyList()), r)
        assertEquals(n, api.peticiones.single().items.size)
    }

    private fun http(code: Int) = HttpException(
        Response.error<Any>(code, "".toResponseBody("application/json".toMediaType()))
    )

    @Test
    fun `un servidor sin la ruta es omitida y no falla`() = runBlocking {
        for (code in listOf(404, 405)) {
            val api = ApiFalsa { throw http(code) }
            val r = ConciliacionDeAgenda(
                api,
                agenda(FuenteFalsa(emptyList()))
            ).conciliar(zona, ahora)
            assertEquals(
                "HTTP $code",
                ResultadoConciliacionDeAgenda.Omitida(MotivoOmitida.SERVIDOR_SIN_LA_RUTA),
                r
            )
        }
    }

    @Test
    fun `un error del servidor o de la red es falla con su causa`() = runBlocking {
        val r500 = ConciliacionDeAgenda(
            ApiFalsa { throw http(500) },
            agenda(FuenteFalsa(emptyList()))
        )
            .conciliar(zona, ahora)
        assertEquals(ResultadoConciliacionDeAgenda.Fallida("http", 500), r500)

        val r422 = ConciliacionDeAgenda(
            ApiFalsa { throw http(422) },
            agenda(FuenteFalsa(emptyList()))
        )
            .conciliar(zona, ahora)
        assertEquals(ResultadoConciliacionDeAgenda.Fallida("http", 422), r422)

        val red = ConciliacionDeAgenda(
            ApiFalsa {
                throw IOException("sin red")
            },
            agenda(FuenteFalsa(emptyList()))
        )
            .conciliar(zona, ahora)
        assertEquals(ResultadoConciliacionDeAgenda.Fallida("IOException"), red)
    }

    @Test
    fun `una lectura local que falla es falla y no manda nada`() = runBlocking {
        val api = sinDiferencias()
        val fuente = FuenteFalsa(emptyList(), errorAlLeer = IllegalStateException("room"))

        val r = ConciliacionDeAgenda(api, agenda(fuente)).conciliar(zona, ahora)

        assertEquals(ResultadoConciliacionDeAgenda.Fallida("IllegalStateException"), r)
        assertTrue(api.peticiones.isEmpty())
    }

    @Test
    fun `la cancelacion se propaga`() {
        var propagada = false
        runBlocking {
            try {
                ConciliacionDeAgenda(
                    ApiFalsa { throw CancellationException("cancelado") },
                    agenda(FuenteFalsa(emptyList()))
                ).conciliar(zona, ahora)
            } catch (e: CancellationException) {
                propagada = true
            }
        }
        assertTrue(propagada)
    }

    @Test
    fun `los conteos de telemetria distinguen falla de omitida y de cero`() {
        assertEquals(
            ConciliacionAgendaConteosEsperado(4, 1, fallida = false, omitida = false),
            ResultadoConciliacionDeAgenda.Ok(4, 1, listOf(9)).conteos().aEsperado()
        )
        assertEquals(
            ConciliacionAgendaConteosEsperado(0, 0, fallida = true, omitida = false),
            ResultadoConciliacionDeAgenda.Fallida("http", 500).conteos().aEsperado()
        )
        assertEquals(
            ConciliacionAgendaConteosEsperado(7, 0, fallida = false, omitida = true),
            ResultadoConciliacionDeAgenda.Omitida(
                MotivoOmitida.DEMASIADAS_VENTAS,
                7
            ).conteos().aEsperado()
        )
    }

    /**
     * E-VIS-116: la captura de intentos fallidos (bandeja de dinero) es por
     * prefijo `v2/visitas`. La conciliación no puede vivir bajo él. El control
     * positivo es la ruta que sí se captura: la lectura del prefijo ve a
     * `V2VisitsApi`.
     */
    @Test
    fun `la ruta de la conciliacion queda fuera del prefijo que captura la bandeja de dinero`() {
        fun rutasPost(c: Class<*>) = c.methods
            .mapNotNull { it.getAnnotation(retrofit2.http.POST::class.java)?.value }
        val prefijoCapturado = "v2/visitas"

        val capturadas =
            rutasPost(com.example.msp_app.data.api.services.visits.V2VisitsApi::class.java)
        assertTrue(
            "control positivo: V2VisitsApi sí cae en el prefijo",
            capturadas.any {
                it.startsWith(prefijoCapturado)
            }
        )

        val rutas = rutasPost(VisitasAgendaApi::class.java)
        assertEquals(listOf("v2/agenda-compromisos/conciliacion"), rutas)
        assertTrue(rutas.none { it.startsWith(prefijoCapturado) })
    }

    private data class ConciliacionAgendaConteosEsperado(
        val items: Int,
        val diferencias: Int,
        val fallida: Boolean,
        val omitida: Boolean
    )

    private fun com.example.msp_app.core.visitas.envio.ConciliacionAgendaConteos.aEsperado() =
        ConciliacionAgendaConteosEsperado(items, diferencias, fallida, omitida)
}
