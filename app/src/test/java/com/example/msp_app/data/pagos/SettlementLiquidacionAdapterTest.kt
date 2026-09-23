package com.example.msp_app.data.pagos

import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VENTA = 77188
private const val CREDITO = 91027
private const val CLIENTE = 5021

/**
 * `SettlementLiquidacionAdapter` es **código nuevo**, así que la NORMA DE
 * ERRORES lo vincula aunque el archivo aterrice en `:app` (la exención de
 * legacy cubre los ~245 `catch` que ya estaban, no lo que se escribe hoy).
 *
 * El camino que se defiende: `calculatePaymentResult` devuelve `validUntil =
 * "-"` en sus ramas sin precio de contado — un string que `dd/MM/yyyy` no
 * puede leer. La liquidación se conserva **sin** vigencia (la cifra es lo que
 * el cobrador va a cobrar y no se tira), pero el fallo se emite.
 */
class SettlementLiquidacionAdapterTest : RoomTestBase() {

    private val telemetria = RecordingTelemetry()
    private val adapter by lazy { SettlementLiquidacionAdapter(db.saleDao(), telemetria) }

    // `contado`/`cortoPlazo`/`bruto`/`restante`: los `Double` crudos del schema
    // inmutable, con nombres fuera del vocabulario que vigila `NoDoubleForMoney`.
    private fun venta(
        contado: Double = 5200.0,
        cortoPlazo: Double = 5900.0,
        bruto: Double = 6400.0,
        restante: Double = 1450.0
    ) = SaleEntity(
        DOCTO_CC_ACR_ID = VENTA,
        DOCTO_CC_ID = CREDITO,
        FOLIO = "V-5188",
        CLIENTE_ID = CLIENTE,
        APLICADO = "S",
        COBRADOR_ID = 12,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 25,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = null,
        TOTAL_IMPORTE = bruto,
        NUM_IMPORTES = 20,
        FECHA = "2026-05-04T06:00:00Z",
        PARCIALIDAD = 220,
        ENGANCHE = 900.0,
        TIEMPO_A_CORTO_PLAZOMESES = 4,
        MONTO_A_CORTO_PLAZO = cortoPlazo,
        VENDEDOR_1 = "J. Carlos Mendez",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = bruto,
        IMPTE_REST = restante,
        SALDO_REST = restante,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacan",
        ESTADO = "Puebla",
        TELEFONO = "238 162 7597",
        NOMBRE_COBRADOR = "Efrain Dominguez Reyes",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "Rosa Maria Ramirez",
        FREC_PAGO = "SEMANAL"
    ).copy(PRECIO_DE_CONTADO = contado)

    private fun erroresDeVigencia() = telemetria.recorded.filter {
        it.type == TelemetryEventType.ERROR &&
            it.name == PagosTelemetria.CODE_LIQUIDACION_VIGENCIA_ILEGIBLE
    }

    @Test
    fun `una vigencia ilegible NO se traga en silencio`() = runTest {
        // Sin precio de contado ni monto a corto plazo, `calculatePaymentResult`
        // devuelve validUntil = "-", que `dd/MM/yyyy` no puede leer.
        db.saleDao().insertAll(listOf(venta(contado = 0.0, cortoPlazo = 0.0)))

        val liquidacion = adapter.liquidacionDe(VENTA)

        assertNotNull("la cifra se conserva aunque la vigencia no se pueda leer", liquidacion)
        assertNull(liquidacion!!.vigenteHasta)
        val error = erroresDeVigencia().single()
        assertEquals("DateTimeParseException", error.props[PagosTelemetria.PROP_EXCEPCION])
        // Anti-PII: ni el texto crudo, ni el cliente, ni el monto.
        val texto = error.name + error.props.entries.joinToString { it.key + it.value }
        assertTrue(texto, !texto.contains("Victoria") && !texto.contains("4950"))
    }

    @Test
    fun `una vigencia legible no emite nada`() = runTest {
        db.saleDao().insertAll(listOf(venta()))

        val liquidacion = adapter.liquidacionDe(VENTA)

        assertNotNull(liquidacion)
        assertNotNull("con precio de contado sí hay vigencia", liquidacion!!.vigenteHasta)
        assertTrue(erroresDeVigencia().isEmpty())
    }

    @Test
    fun `una venta saldada no ofrece liquidacion y tampoco emite`() = runTest {
        db.saleDao().insertAll(listOf(venta(restante = 0.0)))

        assertNull(adapter.liquidacionDe(VENTA))
        assertTrue(erroresDeVigencia().isEmpty())
    }

    @Test
    fun `una venta que el telefono no tiene devuelve null`() = runTest {
        assertNull(adapter.liquidacionDe(VENTA))
    }
}
