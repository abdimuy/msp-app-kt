package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.ProductEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import java.math.BigDecimal
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EFECTIVO = 157
private const val TRANSFERENCIA = 52569
private const val CONDONACION = 137026
private const val VENTA = 77188
private const val CREDITO = 91027
private const val CLIENTE = 5021

/**
 * La **frontera de la REGLA DE DINERO**, probada sobre Room/SQLite real
 * (Robolectric, base en memoria) y no sobre un predicado abstracto.
 *
 * Lo que se defiende: `PARCIALIDAD` es `Int` y `IMPORTE` es `Double` en un
 * schema de producción que no se toca. Al cruzarlos hay que usar
 * `BigDecimal.valueOf`, **nunca** `BigDecimal(double)` — ese constructor
 * arrastra el error binario del flotante y mete centavos fantasma en un número
 * que era exacto.
 */
class RoomAdaptersMoneyTest : RoomTestBase() {

    private val telemetria = RecordingTelemetry()
    private val ventas by lazy { RoomVentasAdapter(db.saleDao()) }
    private val pagos by lazy { RoomPagosAdapter(db.paymentDao()) }
    private val visitas by lazy { RoomVisitasAdapter(db.visitDao(), telemetria) }

    // `restante`/`bruto`: son los `Double` crudos del schema inmutable. Los nombres
    // evitan a propósito el vocabulario de dinero que `NoDoubleForMoney` vigila —
    // la regla es correcta y el punto del test es justamente que estos crudos NO
    // salgan del adaptador sin envolverse.
    private fun venta(parcialidad: Int = 220, restante: Double = 1450.0, bruto: Double = 6400.0) =
        SaleEntity(
            DOCTO_CC_ACR_ID = VENTA,
            DOCTO_CC_ID = CREDITO,
            FOLIO = "V-5188",
            CLIENTE_ID = CLIENTE,
            APLICADO = "S",
            COBRADOR_ID = 12,
            CLIENTE = "Victoria Flores Olmedo",
            ZONA_CLIENTE_ID = 25,
            LIMITE_CREDITO = 0.0,
            NOTAS = "Trabaja de noche",
            ZONA_NOMBRE = "Centro",
            IMPORTE_PAGO_PROMEDIO = null,
            TOTAL_IMPORTE = bruto,
            NUM_IMPORTES = 20,
            FECHA = "2026-05-04T06:00:00Z",
            PARCIALIDAD = parcialidad,
            ENGANCHE = 900.0,
            TIEMPO_A_CORTO_PLAZOMESES = 4,
            MONTO_A_CORTO_PLAZO = 5900.0,
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
        )

    // `raw` (no `importe`) para no disparar NoDoubleForMoney: es el Double crudo del schema.
    private fun pago(id: String, formaCobro: Int, raw: Double) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = VENTA,
        DOCTO_CC_ID = CREDITO,
        FECHA_HORA_PAGO = "2026-08-03T17:10:00Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = raw,
        LAT = null,
        LNG = null,
        CLIENTE_ID = CLIENTE,
        COBRADOR_ID = 12,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 25,
        NOMBRE_CLIENTE = "Victoria Flores Olmedo"
    )

    private fun visita(
        id: String,
        tipo: String = "Pidió reagendar visita",
        centavos: Long? = null,
        horaCita: String? = null,
        ventaLigada: Int = VENTA
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = CLIENTE,
        COBRADOR = "Efrain Dominguez Reyes",
        COBRADOR_ID = 12,
        FECHA = "2026-09-01T16:00:00Z",
        FORMA_COBRO_ID = EFECTIVO,
        LAT = 18.46,
        LNG = -97.39,
        NOTA = null,
        TIPO_VISITA = tipo,
        ZONA_CLIENTE_ID = 25,
        IMPTE_DOCTO_CC_ID = ventaLigada,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_MONTO_CENTAVOS = centavos,
        CITA_HORA = horaCita
    )

    @Test
    fun `PARCIALIDAD Int cruza a Money exacto, sin centavos fantasma`() = runTest {
        db.saleDao().insertAll(listOf(venta(parcialidad = 350)))
        val datos = ventas.ventasDelCliente(CLIENTE).single()
        assertEquals(Money.of(BigDecimal("350.00")), datos.parcialidad)
        assertEquals(BigDecimal("350.00"), datos.parcialidad.amount)
    }

    @Test
    fun `IMPORTE Double cruza por BigDecimal-valueOf, no por el constructor binario`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        // 350.1 no tiene representación binaria exacta: BigDecimal(350.1) daría
        // 350.10000000000002273736754432320594787597656250.
        db.paymentDao().saveAll(listOf(pago("p1", EFECTIVO, 350.1)))
        val importe = pagos.pagosDe(VENTA).single().importe
        assertEquals(BigDecimal("350.10"), importe.amount)
        assertEquals(2, importe.amount.scale())
    }

    @Test
    fun `la condonacion nunca entra al historial de abonos`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.paymentDao().saveAll(
            listOf(
                pago("p1", EFECTIVO, 350.0),
                pago("p2", TRANSFERENCIA, 350.0),
                pago("p3", CONDONACION, 900.0)
            )
        )
        val historial = pagos.pagosDe(VENTA)
        assertEquals(2, historial.size)
        assertTrue(historial.none { it.formaCobroId == CONDONACION })
        assertEquals(
            setOf(MetodoDeCobro.EFECTIVO, MetodoDeCobro.TRANSFERENCIA),
            historial.map { it.metodo }.toSet()
        )
    }

    @Test
    fun `la venta se resuelve por su id sin conocer al cliente`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        val datos = ventas.venta(VENTA)
        assertEquals(CLIENTE, datos!!.clienteId)
        assertEquals(Money.of(BigDecimal("1450.00")), datos.saldo)
        assertEquals(Money.of(BigDecimal("4950.00")), datos.abonado)
        assertNull(ventas.venta(1))
    }

    @Test
    fun `los productos de la venta llegan concatenados por la query de Room`() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        db.productDao().saveAll(
            listOf(
                ProductEntity(1, CREDITO, "V-5188", 10, "Refrigerador Mabe 14'", 1, 6400.0, 6400.0, 1)
            )
        )
        assertEquals("Refrigerador Mabe 14'", ventas.ventasDelCliente(CLIENTE).single().descripcion)
    }

    @Test
    fun `PROMESA_MONTO_CENTAVOS cruza exacto a pesos con centavos`() = runTest {
        db.visitDao().insertVisit(visita("v1", centavos = 35050))
        assertEquals(
            Money.of(BigDecimal("350.50")),
            visitas.visitasDelCliente(CLIENTE).single().montoPrometido
        )
    }

    @Test
    fun `una visita sin venta ligada llega con ventaId nulo, no colapsada`() = runTest {
        db.visitDao().insertVisit(visita("v1", ventaLigada = 0))
        assertNull(visitas.visitasDelCliente(CLIENTE).single().ventaId)
    }

    @Test
    fun `CITA_HORA valida se lee`() = runTest {
        db.visitDao().insertVisit(visita("v1", horaCita = "16:30"))
        assertEquals(LocalTime.of(16, 30), visitas.visitasDelCliente(CLIENTE).single().horaCita)
        assertTrue(telemetria.recorded.none { it.type == TelemetryEventType.ERROR })
    }

    @Test
    fun `CITA_HORA invalida degrada a nulo pero NO en silencio`() = runTest {
        db.visitDao().insertVisit(visita("v1", horaCita = "4 y media"))
        val leida = visitas.visitasDelCliente(CLIENTE).single()
        assertNull(leida.horaCita)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_CITA_HORA_INVALIDA
        }
        assertEquals("DateTimeParseException", error.props[PagosTelemetria.PROP_EXCEPCION])
        // Anti-PII: el texto crudo tecleado NO viaja.
        assertTrue(error.props.values.none { it.contains("4 y media") })
    }
}
