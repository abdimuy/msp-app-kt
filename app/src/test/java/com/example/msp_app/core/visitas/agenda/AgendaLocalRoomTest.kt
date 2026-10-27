package com.example.msp_app.core.visitas.agenda

import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [RoomFuenteDeAgenda] contra Room de verdad (E-APP-104, hueco de la revisión de T17):
 * ejecuta `SaleIdSpaces.forSaleRow(SaleWithProductsEntity)` por `cuentasDeZona`. La
 * venta lleva PK y cargo DISTINTOS a propósito (como `SaleIdSpacesRoomContractTest`):
 * con números iguales la prueba pasaría devolviera `forSaleRow` el que devolviera.
 * `Visit.IMPTE_DOCTO_CC_ID` es la PK, como la escribe `VisitFactory`.
 */
class AgendaLocalRoomTest : RoomTestBase() {

    private val cdmx: ZoneId = ZoneId.of("America/Mexico_City")

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = PK_DE_LA_VENTA,
        DOCTO_CC_ID = CARGO,
        FOLIO = "CV-48213",
        CLIENTE_ID = CLIENTE,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Guadalupe Hernández Soto",
        ZONA_CLIENTE_ID = ZONA,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Zona 21",
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

    private fun visitaConPromesa() = VisitEntity(
        ID = "00000000-0000-3000-8000-000000000001",
        CLIENTE_ID = CLIENTE,
        COBRADOR_ID = 7,
        FECHA = "2026-10-15T17:00:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        TIPO_VISITA = "Pidió que regrese otro día",
        ZONA_CLIENTE_ID = ZONA,
        IMPTE_DOCTO_CC_ID = PK_DE_LA_VENTA,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = PK_DE_LA_VENTA,
        PROMESA_FECHA = "2026-10-20",
        PROMESA_MONTO_CENTAVOS = 15000L
    )

    private fun fuente() = RoomFuenteDeAgenda(
        db.saleDao(),
        db.visitDao(),
        db.visitaZonaDao(),
        db.paymentDao()
    )

    @Test
    fun `la cuenta de la agenda es la PK de sales - E-APP-104`() = runTest {
        assertNotEquals("precondición: PK y cargo distintos", PK_DE_LA_VENTA, CARGO)
        db.saleDao().insertAll(listOf(venta()))

        assertEquals(listOf(PK_DE_LA_VENTA), fuente().cuentasDeZona(ZONA).map { it.ventaId })
        assertEquals(listOf(CLIENTE), fuente().cuentasDeZona(ZONA).map { it.clienteId })
        // Control positivo: otra zona no trae la cuenta.
        assertEquals(emptyList<Int>(), fuente().cuentasDeZona(ZONA + 1).map { it.ventaId })
    }

    @Test
    fun `la visita propia casa con la cuenta por la PK - E-APP-104`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.visitDao().insertVisit(visitaConPromesa())

        val r = AgendaLocal(fuente(), cdmx).derivar(ZONA, LocalDate.parse("2026-10-16"))

        assertEquals(setOf(PK_DE_LA_VENTA), r.keys)
        assertEquals(TipoCompromiso.PROMESA, r.getValue(PK_DE_LA_VENTA).tipo)
    }

    private companion object {
        /** `sales.DOCTO_CC_ACR_ID`: la PK de Room. */
        const val PK_DE_LA_VENTA = 91027

        /** `sales.DOCTO_CC_ID`: el cargo. Distinto de la PK a propósito. */
        const val CARGO = 48213

        const val CLIENTE = 30144
        const val ZONA = 21
    }
}
