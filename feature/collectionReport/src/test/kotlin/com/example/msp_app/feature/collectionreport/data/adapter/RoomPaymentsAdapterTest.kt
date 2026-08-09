package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EFECTIVO_ID = 157
private const val CHEQUE_ID = 158
private const val TRANSFERENCIA_ID = 52569
private const val CONDONACION_ID = 137026
private const val UNKNOWN_FORMA_ID = 999
private const val SALE_ID = 77021

/**
 * Money-path del borde de datos: [RoomPaymentsAdapter] sobre la query real de
 * Room/SQLite (Robolectric, DB en memoria) — no un predicado abstracto. Verifica
 * el mapeo `IMPORTE: Double` -> [Money] exacto, el ruteo por `FORMA_COBRO_ID`
 * (incl. que 137026 NUNCA cae en cobrados), el rango medio-abierto en el borde
 * de medianoche y los casos vacíos.
 */
class RoomPaymentsAdapterTest : RoomTestBase() {

    private val adapter by lazy { RoomPaymentsAdapter(db.paymentDao()) }

    // Día D del reporte y sus límites de negocio (America/Mexico_City, UTC-6).
    private val dayD = LocalDate.of(2026, 4, 15)
    private val startD = AppTime.toWireFormat(AppTime.startOfDay(dayD)) // 2026-04-15T06:00:00Z
    private val endD = AppTime.toWireFormat(AppTime.startOfNextDay(dayD)) // 2026-04-16T06:00:00Z
    private val rangeD = DateRange(startD, endD)

    // `raw` (no `importe`) para no disparar la regla NoDoubleForMoney: es el
    // Double crudo del schema v27 que el adapter convierte a Money en el borde.
    private fun payment(
        id: String,
        formaCobro: Int,
        raw: Double = 350.0,
        fechaHoraPago: String = "2026-04-15T18:30:00Z",
        guardado: Boolean = true,
        nombreCliente: String = "Rosa Elena Martinez Vazquez"
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = SALE_ID,
        DOCTO_CC_ID = 91027,
        FECHA_HORA_PAGO = fechaHoraPago,
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = raw,
        LAT = 19.043415,
        LNG = -98.198234,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = nombreCliente
    )

    @Test
    fun `paymentsIn mapea todos los campos a dominio`() = runTest {
        db.paymentDao().savePayment(
            payment(
                id = "p1",
                formaCobro = EFECTIVO_ID,
                raw = 1234.56,
                guardado = false,
                nombreCliente = "Minerva Lopez Garcia"
            )
        )

        val result = adapter.paymentsIn(rangeD)

        assertEquals(1, result.size)
        val p = result.single()
        assertEquals("p1", p.id)
        assertEquals("Minerva Lopez Garcia", p.cliente)
        assertEquals(SALE_ID.toString(), p.ventaLabel)
        assertEquals(BigDecimal("1234.56"), p.amount.amount)
        assertEquals(PaymentMethod.EFECTIVO, p.method)
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), p.paidAt)
        assertTrue("guardado=false debe mapear a synced=false", !p.synced)
    }

    @Test
    fun `paymentsIn convierte IMPORTE Double a Money con escala 2 exacta`() = runTest {
        // 0.1 + 0.2 en Double no es 0.3; el puente Money.of(Double) lo normaliza.
        db.paymentDao().savePayment(payment(id = "p1", formaCobro = EFECTIVO_ID, raw = 0.1))
        db.paymentDao().savePayment(payment(id = "p2", formaCobro = EFECTIVO_ID, raw = 0.2))

        val total = Money.sum(adapter.paymentsIn(rangeD).map { it.amount })

        assertEquals(BigDecimal("0.30"), total.amount)
    }

    @Test
    fun `paymentsIn rutea cada FORMA_COBRO_ID a su metodo`() = runTest {
        db.paymentDao().savePayment(payment(id = "efe", formaCobro = EFECTIVO_ID))
        db.paymentDao().savePayment(payment(id = "che", formaCobro = CHEQUE_ID))
        db.paymentDao().savePayment(payment(id = "tra", formaCobro = TRANSFERENCIA_ID))

        val byId = adapter.paymentsIn(rangeD).associateBy { it.id }

        assertEquals(PaymentMethod.EFECTIVO, byId.getValue("efe").method)
        assertEquals(PaymentMethod.CHEQUE, byId.getValue("che").method)
        assertEquals(PaymentMethod.TRANSFERENCIA, byId.getValue("tra").method)
    }

    @Test
    fun `paymentsIn NUNCA incluye una condonacion 137026`() = runTest {
        db.paymentDao().savePayment(payment(id = "cobro", formaCobro = EFECTIVO_ID))
        db.paymentDao().savePayment(payment(id = "cond", formaCobro = CONDONACION_ID))

        val ids = adapter.paymentsIn(rangeD).map { it.id }

        assertEquals(listOf("cobro"), ids)
    }

    @Test
    fun `paymentsIn excluye una forma de cobro desconocida`() = runTest {
        db.paymentDao().savePayment(payment(id = "raro", formaCobro = UNKNOWN_FORMA_ID))

        assertEquals(emptyList<String>(), adapter.paymentsIn(rangeD).map { it.id })
    }

    @Test
    fun `paymentsIn respeta el rango medio-abierto en el borde de medianoche`() = runTest {
        // 23:59:59 CDMX del 15-abr = 2026-04-16T05:59:59Z -> dentro de D.
        db.paymentDao().savePayment(
            payment(id = "prev", formaCobro = EFECTIVO_ID, fechaHoraPago = "2026-04-16T05:59:59Z")
        )
        // Medianoche exacta (fin exclusivo de D) -> fuera de D.
        db.paymentDao().savePayment(
            payment(id = "boundary", formaCobro = EFECTIVO_ID, fechaHoraPago = endD)
        )

        assertEquals(listOf("prev"), adapter.paymentsIn(rangeD).map { it.id })
    }

    @Test
    fun `paymentsIn vacio devuelve lista vacia sin NPE`() = runTest {
        assertEquals(emptyList<String>(), adapter.paymentsIn(rangeD).map { it.id })
    }

    @Test
    fun `forgivenessIn devuelve solo condonaciones con motivo vacio`() = runTest {
        db.paymentDao().savePayment(payment(id = "cobro", formaCobro = EFECTIVO_ID))
        db.paymentDao().savePayment(
            payment(
                id = "cond",
                formaCobro = CONDONACION_ID,
                raw = 42.50,
                nombreCliente = "Josefina Ramirez Cruz"
            )
        )

        val result = adapter.forgivenessIn(rangeD)

        assertEquals(1, result.size)
        val f = result.single()
        assertEquals("Josefina Ramirez Cruz", f.cliente)
        assertEquals("", f.motivo)
        assertEquals(BigDecimal("42.50"), f.amount.amount)
    }

    @Test
    fun `forgivenessIn vacio devuelve lista vacia`() = runTest {
        db.paymentDao().savePayment(payment(id = "cobro", formaCobro = EFECTIVO_ID))

        assertEquals(emptyList<String>(), adapter.forgivenessIn(rangeD).map { it.cliente })
    }

    @Test
    fun `pendingCount cuenta solo los no guardados en Microsip`() = runTest {
        db.paymentDao().savePayment(payment(id = "sub1", formaCobro = EFECTIVO_ID, guardado = true))
        db.paymentDao().savePayment(
            payment(id = "pen1", formaCobro = EFECTIVO_ID, guardado = false)
        )
        db.paymentDao().savePayment(payment(id = "pen2", formaCobro = CHEQUE_ID, guardado = false))

        assertEquals(2, adapter.pendingCount())
    }

    @Test
    fun `pendingCount sin pendientes es cero`() = runTest {
        db.paymentDao().savePayment(payment(id = "sub1", formaCobro = EFECTIVO_ID, guardado = true))

        assertEquals(0, adapter.pendingCount())
    }

    @Test
    fun `paymentsGroupedByDaySince agrupa y mapea a dominio`() = runTest {
        db.paymentDao().savePayment(
            payment(id = "d15", formaCobro = EFECTIVO_ID, fechaHoraPago = "2026-04-15T18:00:00Z")
        )
        db.paymentDao().savePayment(
            payment(id = "d16", formaCobro = CHEQUE_ID, fechaHoraPago = "2026-04-16T18:00:00Z")
        )

        val grouped = adapter.paymentsGroupedByDaySince(startD)

        assertEquals(setOf("2026-04-15", "2026-04-16"), grouped.keys)
        assertEquals("d15", grouped.getValue("2026-04-15").single().id)
        assertEquals(PaymentMethod.CHEQUE, grouped.getValue("2026-04-16").single().method)
    }
}
