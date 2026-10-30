package com.example.msp_app.data.visitas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.avisos.PlanDeAvisos
import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.agenda.AgendaLocal
import com.example.msp_app.core.visitas.agenda.RoomFuenteDeAgenda
import com.example.msp_app.core.visitas.capacidades.Capacidades
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.workers.fakeV2Api
import com.example.msp_app.workers.pendingVisit
import com.example.msp_app.workers.runPendingVisitsWorker
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Medicion de la decision del 2026-10-30 (opcion A): con la capacidad de
 * compromisos en falso, una cita guardada en el telefono sobrevive al envio,
 * a la bajada de la agenda y a la poda, y su alarma sigue en el plan.
 */
class CitaSobreviveConCapacidadEnFalsoTest : RoomTestBase() {

    private val cdmx: ZoneId = ZoneId.of("America/Mexico_City")
    private val ahora = Instant.parse("2026-10-28T23:40:00Z") // 17:40 CDMX
    private val clock = FakeClock(ahora)
    private val id = "visita-001"

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = 5000, DOCTO_CC_ID = 5001, FOLIO = "V-1", CLIENTE_ID = 11486,
        APLICADO = "S", COBRADOR_ID = 200, CLIENTE = "Josefa Temoxtle", ZONA_CLIENTE_ID = 21552,
        LIMITE_CREDITO = 0.0, NOTAS = "", ZONA_NOMBRE = "Z", IMPORTE_PAGO_PROMEDIO = 100.0,
        TOTAL_IMPORTE = 700.0, NUM_IMPORTES = 2, FECHA = "2026-06-01T00:00:00Z", PARCIALIDAD = 100,
        ENGANCHE = 0.0, TIEMPO_A_CORTO_PLAZOMESES = 0, MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "", VENDEDOR_2 = "", VENDEDOR_3 = "", PRECIO_TOTAL = 1000.0,
        IMPTE_REST = 500.0, SALDO_REST = 500.0, FECHA_ULT_PAGO = null, CALLE = "x",
        CIUDAD = "x", ESTADO = "x", TELEFONO = "1", NOMBRE_COBRADOR = "x",
        ESTADO_COBRANZA = "PENDIENTE", DIA_COBRANZA = "LUNES", DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 900.0, AVAL_O_RESPONSABLE = "", FREC_PAGO = "SEMANAL"
    )

    @Test
    fun `una cita local sobrevive al envio, a la bajada y a la poda con la capacidad en falso`() =
        runTest {
            val context: Context = ApplicationProvider.getApplicationContext()
            db.saleDao().insertAll(listOf(venta()))
            db.visitDao().insertVisit(
                pendingVisit(id).copy(
                    FECHA = "2026-10-28T23:30:00Z",
                    TIPO_VISITA = "Pidió que regrese otro día",
                    CITA_FECHA = "2026-10-28",
                    CITA_HORA = "18:25"
                )
            )
            // El envio con el contrato 2: la cita no viaja.
            runPendingVisitsWorker(
                context,
                db.visitImageDao(),
                clock,
                ModoEnvioVisitas.V3,
                visitId = id,
                api = fakeV2Api { _, _ -> VisitaDTO(id = id) },
                capacidades = Capacidades.CONTRATO_2
            )
            // La bajada de la agenda trae la copia del servidor: misma visita, sin cita.
            db.visitaZonaDao().rescatar(
                listOf(
                    VisitaZonaEntity(
                        ID = id, CLIENTE_ID = 11486, COBRADOR_ID = 200, COBRADOR = "x",
                        FECHA = "2026-10-28T23:30:00Z", TIPO_VISITA = "Pidió que regrese otro día",
                        ZONA_CLIENTE_ID = 21552, IMPTE_DOCTO_CC_ID = 5000, NOTA = null,
                        PROMESA_VENTA_ID = null, PROMESA_FECHA = null,
                        PROMESA_MONTO_CENTAVOS = null, CITA_FECHA = null, CITA_HORA = null,
                        UPDATED_AT = "2026-10-28T23:31:00Z"
                    )
                )
            )
            // La poda del sync.
            VisitsLocalDataSource(
                visitDao = db.visitDao(),
                saleDao = db.saleDao(),
                enqueuer = object : com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer {
                    override fun enqueue(visitId: String) = Unit
                },
                clock = clock
            ).deleteUploadedVisits()

            val fila = db.visitDao().findVisitById(id)
            assertNotNull("la visita con cita no se poda", fila)
            assertEquals("2026-10-28", fila!!.CITA_FECHA)

            val agenda = AgendaLocal(
                RoomFuenteDeAgenda(db.saleDao(), db.visitDao(), db.visitaZonaDao()),
                cdmx
            )
            val compromisos = agenda.derivarAhora(21552, ahora).values
            assertEquals(listOf(TipoCompromiso.CITA), compromisos.map { it.tipo })
            val plan = PlanDeAvisos.planear(compromisos, emptyList(), ahora, cdmx)
            assertTrue("la alarma de la cita sigue en el plan", plan.any { it.tipo == "cita" })
        }
}
