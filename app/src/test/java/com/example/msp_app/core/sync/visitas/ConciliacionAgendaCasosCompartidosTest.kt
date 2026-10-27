package com.example.msp_app.core.sync.visitas

import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.agenda.RoomFuenteDeAgenda
import com.example.msp_app.data.api.services.visits.ConciliacionAgendaRequest
import com.example.msp_app.data.api.services.visits.ConciliacionAgendaResponse
import com.example.msp_app.data.api.services.visits.DiferenciaAgendaDto
import com.example.msp_app.data.api.services.visits.LadoConciliacionDto
import com.example.msp_app.data.api.services.visits.VisitasAgendaApi
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El arnés de "cero diferencias" del lado del teléfono (plan visitas P2,
 * Tarea 18; E-APP-133; spec §4.6-2). Los casos son el juego
 * `conciliacion-agenda` de msp-api (copia byte a byte en
 * `src/test/resources/conciliacion-agenda/`, `scripts/casos-compromisos.sh
 * copiar`); msp-api comprueba con los mismos bytes lo que el servidor deriva y
 * las diferencias que devuelve (`TestConciliacionCasos_ServidorDerivaYConcilia_EAPP133`).
 *
 * Aquí: cada caso se siembra en Room como lo dejan el sync y la captura
 * (`sales`, `Visit`, `visita_zona`, `Payment`), `ConciliacionDeAgenda` deriva
 * con `AgendaLocal` de verdad sobre Room de verdad y manda lo que el juego dice
 * que el teléfono debe mandar; contra el servidor falso que contesta con las
 * diferencias del juego se cuenta lo mismo.
 *
 * Control positivo: tres casos traen una diferencia a propósito (-2, cuenta
 * podada, visita sin enviar). "Cero diferencias" solo vale si este mismo arnés
 * sabe encontrar una.
 *
 * NO es una medición contra el servidor ni contra Firebird: las entradas del
 * lado servidor son las que sus repositorios devuelven según sus pruebas E2E.
 */
class ConciliacionAgendaCasosCompartidosTest : RoomTestBase() {

    private val gson = Gson()
    private val cdmx: ZoneId = ZoneId.of("America/Mexico_City")

    private data class VisitaJson(
        val id: String,
        val cliente_id: Int,
        val venta_id: Int,
        val tipo_visita: String,
        val fecha: String,
        val promesa_venta_id: Int?,
        val promesa_fecha: String?,
        val promesa_monto_centavos: Long?,
        val cita_fecha: String?,
        val cita_hora: String?
    )

    private data class VentaJson(val pk: Int, val cargo: Int, val cliente_id: Int)

    private data class PagoJson(
        val id: String,
        val cargo: Int,
        val docto_cc_id: Int,
        val fecha_hora_pago: String,
        val pago_recibido_id: String?,
        val guardado: Boolean
    )

    private data class TelefonoJson(
        val ventas: List<VentaJson>,
        val visitas_propias: List<VisitaJson>,
        val visitas_zona: List<VisitaJson>,
        val pagos: List<PagoJson>
    )

    private data class ItemJson(val venta_id: Int, val visita_id: String, val estado: String)

    private data class LadoJson(val visita_id: String, val estado: String)

    private data class DiferenciaJson(
        val venta_id: Int,
        val telefono: LadoJson?,
        val servidor: LadoJson?
    )

    private data class EsperadoJson(
        val telefono: List<ItemJson>,
        val servidor: List<ItemJson>,
        val diferencias: List<DiferenciaJson>
    )

    private data class CasoJson(
        val nombre: String,
        val hoy: String,
        val zona_id: Int,
        val limite_conocido: String?,
        val telefono: TelefonoJson,
        val esperado: EsperadoJson
    ) {
        var archivo: String = ""
        override fun toString() = archivo
    }

    private fun directorio(): File {
        val url = requireNotNull(javaClass.classLoader?.getResource("conciliacion-agenda")) {
            "no está src/test/resources/conciliacion-agenda; corre scripts/casos-compromisos.sh copiar"
        }
        return File(url.toURI())
    }

    private fun archivos(): List<File> = directorio().listFiles { f -> f.name.endsWith(".json") }!!
        .sortedBy { it.name }

    private fun casos(): List<CasoJson> = archivos().map { f ->
        gson.fromJson(f.readText(Charsets.UTF_8), CasoJson::class.java).also { it.archivo = f.name }
    }

    private fun venta(v: VentaJson, zona: Int) = SaleEntity(
        DOCTO_CC_ACR_ID = v.pk,
        DOCTO_CC_ID = v.cargo,
        FOLIO = "CV-${v.pk}",
        CLIENTE_ID = v.cliente_id,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Cliente ${v.cliente_id}",
        ZONA_CLIENTE_ID = zona,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Zona $zona",
        IMPORTE_PAGO_PROMEDIO = 350.0,
        TOTAL_IMPORTE = 700.0,
        NUM_IMPORTES = 2,
        FECHA = "2026-06-01T00:00:00Z",
        PARCIALIDAD = 350,
        ENGANCHE = 1000.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Gabriel Roque Alvarado",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 12000.0,
        IMPTE_REST = 8000.0,
        SALDO_REST = 8000.0,
        FECHA_ULT_PAGO = null,
        CALLE = "Av. Juárez 120",
        CIUDAD = "Puebla",
        ESTADO = "Puebla",
        TELEFONO = "2221234567",
        NOMBRE_COBRADOR = "Rosa Elena Martínez Vázquez",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "LUNES",
        PRECIO_DE_CONTADO = 10000.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    private fun visitaPropia(v: VisitaJson, zona: Int) = VisitEntity(
        ID = v.id,
        CLIENTE_ID = v.cliente_id,
        COBRADOR_ID = 7,
        FECHA = v.fecha,
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        TIPO_VISITA = v.tipo_visita,
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = v.venta_id,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = v.promesa_venta_id,
        PROMESA_FECHA = v.promesa_fecha,
        PROMESA_MONTO_CENTAVOS = v.promesa_monto_centavos,
        CITA_FECHA = v.cita_fecha,
        CITA_HORA = v.cita_hora
    )

    private fun visitaZona(v: VisitaJson, zona: Int) = VisitaZonaEntity(
        ID = v.id,
        CLIENTE_ID = v.cliente_id,
        COBRADOR_ID = 2,
        COBRADOR = "otro",
        FECHA = v.fecha,
        TIPO_VISITA = v.tipo_visita,
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = v.venta_id,
        NOTA = null,
        PROMESA_VENTA_ID = v.promesa_venta_id,
        PROMESA_FECHA = v.promesa_fecha,
        PROMESA_MONTO_CENTAVOS = v.promesa_monto_centavos,
        CITA_FECHA = v.cita_fecha,
        CITA_HORA = v.cita_hora,
        UPDATED_AT = v.fecha
    )

    private fun pago(p: PagoJson, zona: Int, clienteDe: (Int) -> Int) = PaymentEntity(
        ID = p.id,
        COBRADOR = "Rosa Elena Martínez Vázquez",
        DOCTO_CC_ACR_ID = p.cargo,
        DOCTO_CC_ID = p.docto_cc_id,
        FECHA_HORA_PAGO = p.fecha_hora_pago,
        GUARDADO_EN_MICROSIP = p.guardado,
        IMPORTE = 150.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = clienteDe(p.cargo),
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = zona,
        NOMBRE_CLIENTE = "Cliente",
        PAGO_RECIBIDO_ID = p.pago_recibido_id
    )

    private suspend fun sembrar(c: CasoJson) {
        db.clearAllTables()
        val t = c.telefono
        db.saleDao().insertAll(t.ventas.map { venta(it, c.zona_id) })
        t.visitas_propias.forEach { db.visitDao().insertVisit(visitaPropia(it, c.zona_id)) }
        if (t.visitas_zona.isNotEmpty()) {
            db.visitaZonaDao().rescatar(t.visitas_zona.map { visitaZona(it, c.zona_id) })
        }
        val clientes = t.ventas.associate { it.cargo to it.cliente_id }
        t.pagos.forEach {
            db.paymentDao().savePayment(pago(it, c.zona_id) { cargo -> clientes[cargo] ?: 0 })
        }
    }

    /** Lo que el servidor haría con la petición y su propia agenda (copia de `diferenciasAgenda` de Go). */
    private fun diferencias(servidor: List<ItemJson>, items: List<ItemJson>): List<DiferenciaJson> {
        val s = servidor.associateBy { it.venta_id }
        val t = items.associateBy { it.venta_id }
        return (s.keys + t.keys).sorted().mapNotNull { v ->
            val a = t[v]
            val b = s[v]
            if (a != null && b != null && a.visita_id == b.visita_id && a.estado == b.estado) {
                null
            } else {
                DiferenciaJson(
                    v,
                    a?.let { LadoJson(it.visita_id, it.estado) },
                    b?.let { LadoJson(it.visita_id, it.estado) }
                )
            }
        }
    }

    private class ServidorFalso(val servidor: List<ItemJson>, val derivar: (List<ItemJson>) -> List<DiferenciaJson>) :
        VisitasAgendaApi {
        var ultimo: ConciliacionAgendaRequest? = null
        override suspend fun conciliar(
            body: ConciliacionAgendaRequest
        ): ConciliacionAgendaResponse {
            ultimo = body
            val items = body.items.map { ItemJson(it.venta_id, it.visita_id, it.estado) }
            return ConciliacionAgendaResponse(
                derivar(items).map {
                    DiferenciaAgendaDto(
                        it.venta_id,
                        it.telefono?.let { l -> LadoConciliacionDto(l.visita_id, l.estado) },
                        it.servidor?.let { l -> LadoConciliacionDto(l.visita_id, l.estado) }
                    )
                }
            )
        }
    }

    @Test
    fun `VERSION coincide con el SHA-256 de los casos - E-APP-133`() {
        val md = MessageDigest.getInstance("SHA-256")
        archivos().forEach { md.update(it.readBytes()) }
        val calculado = md.digest().joinToString("") { "%02x".format(it) }
        val declarado = File(directorio(), "VERSION").readText().trim()
        assertEquals(declarado, calculado)
    }

    @Test
    fun `el telefono manda lo que el juego espera y cuenta las diferencias del juego - E-APP-133`() =
        runBlocking {
            val todos = casos()
            assertTrue("el juego se leyó", todos.size >= 9)
            var con = 0
            var sin = 0
            for (c in todos) {
                sembrar(c)
                val servidor = ServidorFalso(c.esperado.servidor) { items ->
                    diferencias(c.esperado.servidor, items)
                }
                val agenda = AgendaLocal(
                    RoomFuenteDeAgenda(db.saleDao(), db.visitDao(), db.visitaZonaDao()),
                    cdmx
                )

                val r = ConciliacionDeAgenda(servidor, agenda)
                    .conciliar(c.zona_id, Instant.parse("${c.hoy}T18:00:00Z"))

                val enviado = servidor.ultimo
                assertNotNull("${c.archivo}: se mandó la petición", enviado)
                assertEquals(c.archivo, c.hoy, enviado!!.hoy)
                assertEquals(c.archivo, c.zona_id, enviado.zona_id)
                assertEquals(
                    "${c.archivo}: lo que el teléfono manda",
                    c.esperado.telefono,
                    enviado.items.map { ItemJson(it.venta_id, it.visita_id, it.estado) }
                )
                val ok = r as ResultadoConciliacionDeAgenda.Ok
                assertEquals(c.archivo, c.esperado.diferencias.size, ok.diferencias)
                assertEquals(
                    c.archivo,
                    c.esperado.diferencias.map { it.venta_id },
                    ok.ventasConDiferencia
                )
                if (c.esperado.diferencias.isEmpty()) {
                    assertNull(
                        "${c.archivo}: un caso sin diferencias no declara límite",
                        c.limite_conocido
                    )
                    sin++
                } else {
                    assertNotNull(
                        "${c.archivo}: toda diferencia declara su límite",
                        c.limite_conocido
                    )
                    con++
                }
            }
            // El juego no puede ser verde por vacío: sabe encontrar una diferencia y sabe dar cero.
            assertTrue("control positivo: casos con diferencia ($con)", con >= 3)
            assertTrue("casos con cero diferencias ($sin)", sin >= 5)
        }
}
