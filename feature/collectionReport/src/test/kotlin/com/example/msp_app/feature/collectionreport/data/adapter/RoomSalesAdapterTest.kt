package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import com.example.msp_app.feature.collectionreport.domain.model.Money
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RoomSalesAdapter] sobre la query real de Room/SQLite (Robolectric, DB en memoria): verifica
 * el mapeo de columnas a [com.example.msp_app.feature.collectionreport.domain.model.SaleForCobranza],
 * el filtro de "activa" (`SALDO_REST > 0`) y la resolución de [CobranzaPorcentaje.Frecuencia]
 * (incl. el fallback a SEMANAL de `FREC_PAGO` nulo/desconocido).
 */
class RoomSalesAdapterTest : RoomTestBase() {

    private val adapter by lazy { RoomSalesAdapter(db.saleDao()) }

    // `restanteRaw`/`creditoRaw` (no `saldoRest`/`precioTotal`) para no disparar la regla
    // NoDoubleForMoney: son el Double crudo del schema v27 que el adapter convierte a
    // Money en el borde (mismo criterio que RoomPaymentsAdapterTest.payment#raw).
    private fun sale(
        acrId: Int,
        parcialidad: Int = 500,
        creditoRaw: Double = 14000.0,
        restanteRaw: Double = 3200.0,
        frecPago: String? = "SEMANAL",
        fecha: String = "2026-01-10T06:00:00Z"
    ) = SaleEntity(
        DOCTO_CC_ACR_ID = acrId,
        DOCTO_CC_ID = 91027 + acrId,
        FOLIO = "A-$acrId",
        CLIENTE_ID = 30144,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Rosa Elena Martinez Vazquez",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = 500.0,
        TOTAL_IMPORTE = 12000.0,
        NUM_IMPORTES = 24,
        FECHA = fecha,
        PARCIALIDAD = parcialidad,
        ENGANCHE = 2000.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Efrain Dominguez Reyes",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = creditoRaw,
        IMPTE_REST = restanteRaw,
        SALDO_REST = restanteRaw,
        FECHA_ULT_PAGO = "2026-04-15T18:30:00Z",
        CALLE = "Av. Reforma 100",
        CIUDAD = "Puebla",
        ESTADO = "Puebla",
        TELEFONO = "2221234567",
        NOMBRE_COBRADOR = "Efrain Dominguez Reyes",
        ESTADO_COBRANZA = "AL_CORRIENTE",
        DIA_COBRANZA = "MIERCOLES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 11000.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = frecPago
    )

    @Test
    fun `nonContadoActiveSales mapea los campos correctamente`() = runTest {
        db.saleDao().insertAll(
            listOf(
                sale(
                    acrId = 77021,
                    parcialidad = 500,
                    creditoRaw = 14000.0,
                    restanteRaw = 3200.0,
                    frecPago = "QUINCENAL",
                    fecha = "2026-01-10T06:00:00Z"
                )
            )
        )

        val sales = adapter.nonContadoActiveSales()

        assertEquals(1, sales.size)
        val sale = sales.single()
        assertEquals(77021, sale.doctoCcAcrId)
        assertEquals(Money.of(BigDecimal("500")), sale.parcialidad)
        assertEquals(Money.of(BigDecimal("14000.00")), sale.totalImporte)
        assertEquals(Money.of(BigDecimal("3200.00")), sale.saldoHoy)
        assertEquals(CobranzaPorcentaje.Frecuencia.QUINCENAL, sale.frecuencia)
    }

    @Test
    fun `nonContadoActiveSales excluye ventas saldadas`() = runTest {
        db.saleDao().insertAll(
            listOf(
                sale(acrId = 1, restanteRaw = 0.0),
                sale(acrId = 2, restanteRaw = -50.0),
                sale(acrId = 3, restanteRaw = 100.0)
            )
        )

        val sales = adapter.nonContadoActiveSales()

        assertEquals(listOf(3), sales.map { it.doctoCcAcrId })
    }

    @Test
    fun `nonContadoActiveSales cae a SEMANAL cuando FREC_PAGO es nulo o desconocido`() = runTest {
        db.saleDao().insertAll(
            listOf(
                sale(acrId = 1, frecPago = null),
                sale(acrId = 2, frecPago = "ALGO_RARO")
            )
        )

        val sales = adapter.nonContadoActiveSales().associateBy { it.doctoCcAcrId }

        assertEquals(CobranzaPorcentaje.Frecuencia.SEMANAL, sales.getValue(1).frecuencia)
        assertEquals(CobranzaPorcentaje.Frecuencia.SEMANAL, sales.getValue(2).frecuencia)
    }

    @Test
    fun `nonContadoActiveSales vacio sin ventas`() = runTest {
        assertTrue(adapter.nonContadoActiveSales().isEmpty())
    }
}
