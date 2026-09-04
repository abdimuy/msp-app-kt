package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.domain.port.CitaEstructurada
import com.example.msp_app.feature.visitas.domain.port.PromesaEstructurada
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * **RULING V — la promesa tiene que sobrevivir una sincronización
 * completa, y aquí se prueba.**
 *
 * ## El problema, en una línea
 *
 * El servidor **no tiene** campos de promesa ni de cita: el contrato de
 * `POST /v2/visitas` no los lleva y el plan decidió no expandirlo. Y
 * `SalesViewModel.syncSales` poda las visitas ya subidas
 * (`VisitDao.deleteUploadedVisits`). O sea que, sin arreglo, una promesa vivía
 * exactamente hasta la siguiente sincronización — y con ella se iba el insumo
 * del recomendador y la única forma de medir el cumplimiento.
 *
 * ## El arreglo, y por qué este
 *
 * La poda dejó de ser `GUARDADO_EN_MICROSIP = 1` a secas: ahora conserva
 * también las visitas cuyo compromiso más lejano siga dentro de la ventana de
 * retención. **Se eligió estrechar la poda y no copiar la promesa a otra
 * tabla** porque (a) esta tarea no define migraciones —el esquema lo fija la
 * Task 26 y la promesa vive en `Visit`—, (b) una copia crea una segunda fuente
 * de verdad que puede desincronizarse de la fila que la originó, y (c) la razón
 * por la que era seguro borrar una visita subida —"ya está a salvo en el
 * servidor"— **es falsa para estas columnas**, así que la corrección natural es
 * dejar de borrarlas mientras signifiquen algo.
 *
 * Este test recorre la sincronización COMPLETA con las piezas reales, sin fakes de
 * escritura: se registra la visita por el adaptador de producción, el
 * reconciliador la marca como subida y después corre la MISMA poda que llama
 * `syncSales`.
 */
class PromesaSobreviveAlSyncTest : RoomTestBase() {

    /** Martes 1-sep-2026, 09:52 en zona de negocio. */
    private val ahora = Instant.parse("2026-09-01T15:52:00Z")
    private val hoy = LocalDate.of(2026, 9, 1)
    private val clock = FakeClock(ahora)
    private val telemetry = RecordingTelemetry(clock)

    private lateinit var visitas: VisitsLocalDataSource
    private lateinit var adaptador: RegistroDeVisitaAdapter

    private val cobrador = User(
        ID = "u-1",
        NOMBRE = "Efraín Domínguez Reyes",
        EMAIL = "efrain@example.com",
        COBRADOR_ID = 7
    )

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        visitas = VisitsLocalDataSource(
            visitDao = db.visitDao(),
            saleDao = db.saleDao(),
            enqueuer = EncoladorQueGraba(),
            clock = clock
        )
        adaptador = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = visitas,
            recomendaciones = db.visitRecommendationDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { cobrador }
        )
    }

    /**
     * **El criterio de aceptación.** Se registra una promesa, el servidor la
     * confirma, corre la poda del sync — y la promesa sigue ahí, con su fecha y
     * su monto intactos.
     */
    @Test
    fun `la promesa sobrevive a una sincronizacion completa`() = runTest {
        val resultado = adaptador.registrar(visitaConPromesa())
        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)

        // La sincronización, en su orden real:
        // 1. el reconciliador confirma la visita contra el servidor…
        db.visitDao().markSyncedByIds(listOf(VISITA_ID))
        // 2. …y `syncSales` poda lo ya confirmado.
        visitas.deleteUploadedVisits()

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertNotNull("la visita con promesa no puede desaparecer en la poda", guardada)
        assertEquals("2026-09-04", guardada.PROMESA_FECHA)
        assertEquals(22_000L, guardada.PROMESA_MONTO_CENTAVOS)
        assertEquals(VENTA_ID, guardada.PROMESA_VENTA_ID)
        assertEquals(1, guardada.GUARDADO_EN_MICROSIP)
    }

    /** Lo mismo para la cita: su día y su hora tampoco existen en el servidor. */
    @Test
    fun `la cita sobrevive a una sincronizacion completa`() = runTest {
        adaptador.registrar(visitaConCita())

        db.visitDao().markSyncedByIds(listOf(VISITA_ID))
        visitas.deleteUploadedVisits()

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertEquals("2026-09-03", guardada.CITA_FECHA)
        assertEquals("16:00", guardada.CITA_HORA)
    }

    /**
     * **Control positivo de la poda.** Una visita subida **sin** compromiso sí
     * desaparece — o sea que la prueba de arriba mide la condición nueva y no
     * una poda que dejó de podar.
     */
    @Test
    fun `una visita subida sin promesa ni cita si se poda`() = runTest {
        adaptador.registrar(visitaSinCompromiso())

        db.visitDao().markSyncedByIds(listOf(VISITA_ID))
        visitas.deleteUploadedVisits()

        assertNull(
            "sin compromiso, la poda se comporta igual que antes",
            runCatching { db.visitDao().getVisitById(VISITA_ID) }.getOrNull()
        )
    }

    /**
     * Y la ventana de retención cierra: con el reloj noventa días después de la
     * promesa, la fila se poda. "Sobrevive el sync" no es "no se borra nunca".
     */
    @Test
    fun `pasada la ventana de retencion la promesa si se poda`() = runTest {
        adaptador.registrar(visitaConPromesa())
        db.visitDao().markSyncedByIds(listOf(VISITA_ID))

        // 2026-09-04 + 90 días = 2026-12-03; un día más y el corte la rebasa.
        clock.setNow(Instant.parse("2026-12-04T15:00:00Z"))
        visitas.deleteUploadedVisits()

        assertNull(
            "la retencion tiene techo",
            runCatching { db.visitDao().getVisitById(VISITA_ID) }.getOrNull()
        )
    }

    /**
     * La promesa **pendiente de subir** también sobrevive — esa parte no
     * cambió, y sin ella la poda nueva podría estar cubriendo un defecto viejo.
     */
    @Test
    fun `una promesa aun sin subir sobrevive igual que antes`() = runTest {
        adaptador.registrar(visitaConPromesa())

        visitas.deleteUploadedVisits()

        assertNotNull(db.visitDao().getVisitById(VISITA_ID))
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    private fun visitaConPromesa() = VisitaARegistrar(
        visitaId = VISITA_ID,
        clienteId = CLIENTE_ID,
        ventaId = VENTA_ID,
        tipoVisita = Constants.PIDE_REAGENDAR,
        nota = "el viernes que cobre mi esposo",
        promesa = PromesaEstructurada(
            ventaId = VENTA_ID,
            fecha = hoy.plusDays(3),
            monto = Money.of(BigDecimal("220"))
        )
    )

    private fun visitaConCita() = VisitaARegistrar(
        visitaId = VISITA_ID,
        clienteId = CLIENTE_ID,
        ventaId = VENTA_ID,
        tipoVisita = Constants.PIDE_TIEMPO,
        nota = null,
        cita = CitaEstructurada(
            fecha = hoy.plusDays(2),
            hora = LocalTime.of(16, 0)
        )
    )

    private fun visitaSinCompromiso() = VisitaARegistrar(
        visitaId = VISITA_ID,
        clienteId = CLIENTE_ID,
        ventaId = VENTA_ID,
        tipoVisita = Constants.NO_SE_ENCONTRABA,
        nota = null
    )

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = VENTA_ID,
        DOCTO_CC_ID = VENTA_ID + 1,
        FOLIO = "V-5188",
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = 220.0,
        TOTAL_IMPORTE = 6310.0,
        NUM_IMPORTES = 20,
        FECHA = "2026-04-14T00:00:00Z",
        PARCIALIDAD = 220,
        ENGANCHE = 900.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "J. Carlos Méndez",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6310.0,
        IMPTE_REST = 1450.0,
        SALDO_REST = 1450.0,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacán",
        ESTADO = "Puebla",
        TELEFONO = "2381627597",
        NOMBRE_COBRADOR = "Efraín Domínguez Reyes",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "MARTES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "Rosa María Ramírez",
        FREC_PAGO = "SEMANAL"
    )

    /** Fake a mano (sin MockK): lista pública de llamadas. */
    private class EncoladorQueGraba : VisitsWorkEnqueuer {
        val encoladas: MutableList<String> = mutableListOf()

        override fun enqueue(visitId: String) {
            encoladas += visitId
        }
    }

    private companion object {
        const val VISITA_ID = "visita-victoria-1"
        const val CLIENTE_ID = 5021
        const val VENTA_ID = 77188
    }
}
