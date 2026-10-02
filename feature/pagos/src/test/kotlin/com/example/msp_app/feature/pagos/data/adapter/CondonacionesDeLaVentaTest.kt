package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EFECTIVO = 157
private const val CONDONACION = 137026
private const val VENTA = 77188

/**
 * **`RoomPagosAdapter.condonacionesDe` sobre Room real**: trae las
 * condonaciones de la venta —y sólo ésas— y distingue la que el servidor
 * rechazó.
 *
 * Es el complemento exacto de `pagosDe`: la misma lectura por
 * `DOCTO_CC_ACR_ID`, del otro lado del filtro de formas de cobranza. Se cobra
 * también que `pagosDe` **siga** sin ver la condonación — el ritmo, el último
 * pago, el estado del periodo y la guarda anti-duplicado del abono leen de ahí.
 */
class CondonacionesDeLaVentaTest : RoomTestBase() {

    private val pagos by lazy { RoomPagosAdapter(db.paymentDao(), RecordingTelemetry()) }

    @Test
    fun `trae las condonaciones de la venta y no los abonos`() = runTest {
        db.paymentDao().saveAll(
            listOf(
                fila("abono", EFECTIVO, 400.0, "2026-09-01T15:00:00Z"),
                fila("cond", CONDONACION, 2300.0, "2026-09-01T17:00:00Z"),
                fila("cond-otra-venta", CONDONACION, 50.0, "2026-09-01T17:00:00Z", venta = 1)
            )
        )

        val condonaciones = pagos.condonacionesDe(VENTA)

        assertEquals(listOf("cond"), condonaciones.map { it.condonacionId })
        val una = condonaciones.single()
        assertEquals(Money.of(BigDecimal("2300.00")), una.importe)
        assertEquals(Instant.parse("2026-09-01T17:00:00Z"), una.fecha)
        assertEquals("Efrain Dominguez Reyes", una.cobrador)
        assertTrue(una.aplicada)
    }

    @Test
    fun `pagosDe sigue sin ver la condonacion`() = runTest {
        db.paymentDao().saveAll(
            listOf(
                fila("abono", EFECTIVO, 400.0, "2026-09-01T15:00:00Z"),
                fila("cond", CONDONACION, 2300.0, "2026-09-01T17:00:00Z")
            )
        )

        assertEquals(listOf("abono"), pagos.pagosDe(VENTA).map { it.pagoId })
    }

    /**
     * La que el worker marcó rechazada (`DOCTO_CC_ID = -1`) sale con
     * `aplicada = false`. Control positivo en el mismo lote: una pendiente
     * (`DOCTO_CC_ID = 0`) y una aplicada (`> 0`) siguen saliendo aplicadas.
     */
    @Test
    fun `la rechazada por el servidor sale como no aplicada`() = runTest {
        db.paymentDao().saveAll(
            listOf(
                fila("rechazada", CONDONACION, 2300.0, "2026-09-01T17:00:00Z")
                    .copy(DOCTO_CC_ID = DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR),
                fila("pendiente", CONDONACION, 100.0, "2026-09-01T16:00:00Z")
                    .copy(DOCTO_CC_ID = 0, GUARDADO_EN_MICROSIP = false),
                fila("aplicada", CONDONACION, 200.0, "2026-09-01T15:00:00Z")
            )
        )

        val porId = pagos.condonacionesDe(VENTA).associateBy { it.condonacionId }

        assertFalse(porId.getValue("rechazada").aplicada)
        assertTrue(porId.getValue("pendiente").aplicada)
        assertTrue(porId.getValue("aplicada").aplicada)
    }

    /**
     * E-APP-052: un abono que el servidor NO tiene (-1) no sale en el historial
     * (decisión del dueño del 2026-10-02). Control en el mismo lote: el aplicado
     * y el pendiente de subir siguen saliendo.
     */
    @Test
    fun `el abono que el servidor no tiene no sale en el historial`() = runTest {
        db.paymentDao().saveAll(
            listOf(
                fila("no-aplicado", EFECTIVO, 350.0, "2026-08-12T17:00:00Z")
                    .copy(DOCTO_CC_ID = DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR),
                fila("aplicado", EFECTIVO, 400.0, "2026-09-01T15:00:00Z"),
                fila("pendiente", EFECTIVO, 200.0, "2026-09-02T15:00:00Z")
                    .copy(DOCTO_CC_ID = 0, GUARDADO_EN_MICROSIP = false)
            )
        )

        assertEquals(listOf("pendiente", "aplicado"), pagos.pagosDe(VENTA).map { it.pagoId })
    }

    // `raw` (no `importe`) para no disparar NoDoubleForMoney: es el Double crudo del schema.
    private fun fila(id: String, formaCobro: Int, raw: Double, fecha: String, venta: Int = VENTA) =
        PaymentEntity(
            ID = id,
            COBRADOR = "Efrain Dominguez Reyes",
            DOCTO_CC_ACR_ID = venta,
            DOCTO_CC_ID = 16_132_275,
            FECHA_HORA_PAGO = fecha,
            GUARDADO_EN_MICROSIP = true,
            IMPORTE = raw,
            LAT = null,
            LNG = null,
            CLIENTE_ID = 5021,
            COBRADOR_ID = 12,
            FORMA_COBRO_ID = formaCobro,
            ZONA_CLIENTE_ID = 25,
            NOMBRE_CLIENTE = "Victoria Flores Olmedo"
        )
}
