package com.example.msp_app.core.visitas.agenda

import com.example.msp_app.core.common.visitas.domain.EstadoCompromiso
import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.database.entities.PaymentEntity
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
        db.visitaZonaDao()
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

    private fun pago(
        id: String,
        fechaHora: String,
        doctoCcId: Int,
        pagoRecibidoId: String? = null
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martínez Vázquez",
        DOCTO_CC_ACR_ID = CARGO,
        DOCTO_CC_ID = doctoCcId,
        FECHA_HORA_PAGO = fechaHora,
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 150.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = CLIENTE,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = ZONA,
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto",
        PAGO_RECIBIDO_ID = pagoRecibidoId
    )

    /**
     * Visita del 16 a las 11:00 locales con promesa para el 20; "hoy" es el 16.
     * Aquí PK = cargo (el invariante real, `SalesPkIgualACargoTest`): la agenda
     * casa las cuentas por PK y los pagos por cargo.
     */
    private suspend fun estadoConPago(pago: PaymentEntity): EstadoCompromiso {
        db.saleDao().insertAll(listOf(venta().copy(DOCTO_CC_ACR_ID = CARGO)))
        db.visitDao().insertVisit(
            visitaConPromesa().copy(
                FECHA = "2026-10-16T17:00:00Z",
                IMPTE_DOCTO_CC_ID = CARGO,
                PROMESA_VENTA_ID = CARGO
            )
        )
        db.paymentDao().savePayment(pago)
        val r = AgendaLocal(fuente(), cdmx).derivar(ZONA, LocalDate.parse("2026-10-16"))
        return r.getValue(CARGO).estado
    }

    /**
     * E-APP-128: el pago de la APP capturado a las 00:00:00 locales (id numérico
     * del sync y `PAGO_RECIBIDO_ID`) tiene hora y es anterior a la visita: no
     * cierra la promesa. La regla de E-APP-112 solo lo distingue del pago de
     * oficina si la lectura de Room trae `PAGO_RECIBIDO_ID`; `getAllPayments` no
     * la proyectaba y el pago se leía como pago de oficina sin hora.
     */
    @Test
    fun `pago de la app a medianoche no cierra la promesa - E-APP-128`() = runTest {
        val deLaApp = pago(
            id = "15082370",
            fechaHora = "2026-10-16T06:00:00Z",
            doctoCcId = 15082369,
            pagoRecibidoId = "6b0c1f2e-3d4a-4b5c-8d6e-7f8091a2b3c4"
        )
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, estadoConPago(deLaApp))
    }

    @Test
    fun `control - el mismo pago sin pago_recibido_id es de oficina y cierra - E-APP-128`() =
        runTest {
            val deOficina = pago(
                id = "14998988",
                fechaHora = "2026-10-16T06:00:00Z",
                doctoCcId = 14998987
            )
            assertEquals(EstadoCompromiso.CUMPLIDA, estadoConPago(deOficina))
        }

    /**
     * E-APP-125 (límite conocido): el abono no aplicado (`DOCTO_CC_ID = -2`,
     * E-APP-092) SÍ cumple la promesa en el teléfono; el servidor no lo ve (queda
     * en `MSP_FAILED_INTENTS`). Decisión del dueño 2026-10-27. Control: `-1`
     * (rechazado) no cierra.
     */
    @Test
    fun `abono no aplicado -2 cumple la promesa en el telefono - E-APP-125`() = runTest {
        val noAplicado = pago(
            id = "7d1f0c52-8a3e-4d62-9b0a-5e4c3b2a1f00",
            fechaHora = "2026-10-16T18:00:00Z",
            doctoCcId = DOCTO_CC_ID_NO_APLICADO
        )
        assertEquals(EstadoCompromiso.CUMPLIDA, estadoConPago(noAplicado))
    }

    @Test
    fun `control - el abono rechazado -1 no cumple la promesa - E-APP-125`() = runTest {
        val rechazado = pago(
            id = "7d1f0c52-8a3e-4d62-9b0a-5e4c3b2a1f01",
            fechaHora = "2026-10-16T18:00:00Z",
            doctoCcId = -1
        )
        assertEquals(EstadoCompromiso.VIGENTE_FUTURA, estadoConPago(rechazado))
    }

    private companion object {
        /** `sales.DOCTO_CC_ACR_ID`: la PK de Room. */
        const val PK_DE_LA_VENTA = 91027

        /** `sales.DOCTO_CC_ID`: el cargo. Distinto de la PK a propósito. */
        const val CARGO = 48213

        /** `Payment.DOCTO_CC_ID` del abono que Microsip no aplicó (opción B, E-APP-092). */
        const val DOCTO_CC_ID_NO_APLICADO = -2

        const val CLIENTE = 30144
        const val ZONA = 21
    }
}
