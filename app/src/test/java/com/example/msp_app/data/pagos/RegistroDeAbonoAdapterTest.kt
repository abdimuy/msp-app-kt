package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La escritura real del abono, contra una base Room en memoria.
 *
 * Lo que se prueba aquí y no en el ViewModel: que el importe cruza a `Double`
 * **sin perder centavos**, que el `SALDO_REST` baja exactamente una vez, y que
 * ningún camino de fallo deja algo escrito a medias.
 */
class RegistroDeAbonoAdapterTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-09-01T18:00:00Z"))
    private val telemetria = RecordingTelemetry(clock)

    private lateinit var pagos: PaymentsLocalDataSource

    private var usuario: User? = COBRADOR

    @Before
    fun setUpAdaptador() = runTest {
        pagos = PaymentsLocalDataSource(db.paymentDao(), db.saleDao())
        db.saleDao().insertAll(listOf(venta()))
    }

    private fun adaptador() = RegistroDeAbonoAdapter(
        saleDao = db.saleDao(),
        pagos = pagos,
        telemetry = telemetria,
        clock = clock,
        traerUsuario = { usuario }
    )

    @Test
    fun `registra el abono y baja el saldo exactamente una vez`() = runTest {
        val resultado = adaptador().registrar(abono(dinero("220")))

        assertEquals(ResultadoDelAbono.REGISTRADO, resultado)
        val guardado = pagos.getPaymentById(ABONO_ID)!!
        assertEquals(220.0, guardado.IMPORTE, 1e-9)
        assertEquals(VENTA_ID, guardado.DOCTO_CC_ACR_ID)
        assertEquals("el abono se atribuye a quien esta cobrando", 77, guardado.COBRADOR_ID)
        assertEquals(MetodoDeCobro.EFECTIVO.formaCobroId, guardado.FORMA_COBRO_ID)
        assertEquals("SALDO_REST = 1450 - 220", 1230.0, saldo(), 1e-9)
    }

    @Test
    fun `la transferencia viaja con su propia forma de cobro`() = runTest {
        adaptador().registrar(abono(dinero("100"), MetodoDeCobro.TRANSFERENCIA))
        assertEquals(
            MetodoDeCobro.TRANSFERENCIA.formaCobroId,
            pagos.getPaymentById(ABONO_ID)!!.FORMA_COBRO_ID
        )
    }

    @Test
    fun `los centavos cruzan a Double sin perderse`() = runTest {
        // El borde heredado: `PaymentEntity.IMPORTE` es Double. La conversión
        // pasa por el texto decimal exacto, no por aritmética de flotante.
        adaptador().registrar(abono(dinero("1234.56")))
        assertEquals(1234.56, pagos.getPaymentById(ABONO_ID)!!.IMPORTE, 0.0)
        assertEquals(215.44, saldo(), 1e-9)
    }

    @Test
    fun `una venta que el telefono no tiene no escribe nada`() = runTest {
        val resultado = adaptador().registrar(abono(dinero("220")).copy(ventaId = 999))
        assertEquals(ResultadoDelAbono.VENTA_NO_ESTA_EN_EL_TELEFONO, resultado)
        assertNull(pagos.getPaymentById(ABONO_ID))
        assertEquals("el saldo quedo intacto", 1450.0, saldo(), 1e-9)
    }

    @Test
    fun `sin cobrador no se escribe nada`() = runTest {
        usuario = null
        assertEquals(ResultadoDelAbono.SIN_COBRADOR, adaptador().registrar(abono(dinero("220"))))
        assertNull(pagos.getPaymentById(ABONO_ID))
        assertEquals(1450.0, saldo(), 1e-9)
    }

    @Test
    fun `un cobrador sin id tampoco cuenta`() = runTest {
        usuario = COBRADOR.copy(COBRADOR_ID = 0)
        assertEquals(ResultadoDelAbono.SIN_COBRADOR, adaptador().registrar(abono(dinero("220"))))
        assertNull(pagos.getPaymentById(ABONO_ID))
    }

    @Test
    fun `un fallo se reporta con su codigo y sin PII`() = runTest {
        val roto = RegistroDeAbonoAdapter(
            saleDao = db.saleDao(),
            pagos = pagos,
            telemetry = telemetria,
            clock = clock,
            traerUsuario = { error("firestore caido cobrando a Victoria Flores") }
        )

        assertEquals(ResultadoDelAbono.FALLO_EL_GUARDADO, roto.registrar(abono(dinero("220"))))
        assertNull("nada quedo escrito", pagos.getPaymentById(ABONO_ID))
        assertEquals(1450.0, saldo(), 1e-9)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_ABONO_NO_SE_GUARDO
        }
        assertEquals("IllegalStateException", error.props[PagosTelemetria.PROP_EXCEPCION])
        assertFalse(error.props.values.any { it.contains("Victoria") })
    }

    /**
     * **Caracterización — la razón de ser del guard anti-duplicado del
     * ViewModel.** `PaymentDao.savePayment` inserta con `REPLACE`, así que dos
     * registros con la misma clave dejan UNA fila... pero `SaleDao.updateTotal`
     * **resta otra vez**. La idempotencia de la fila NO es idempotencia del
     * dinero: el saldo se lleva el golpe dos veces.
     *
     * Este test no pide arreglar `updateTotal` —es Room inmutable de
     * producción— sino fijar por escrito que el "exactamente una vez" tiene que
     * vivir arriba, en `RegistrarAbonoViewModel`, donde sí se prueba.
     */
    @Test
    fun `dos registros con la MISMA clave dejan una fila pero restan dos veces`() = runTest {
        val adaptador = adaptador()
        adaptador.registrar(abono(dinero("220")))
        adaptador.registrar(abono(dinero("220")))

        assertEquals("una sola fila: el REPLACE de la llave primaria", 1, pagosDeLaVenta())
        assertEquals("y aun asi el saldo bajo dos veces", 1010.0, saldo(), 1e-9)
        assertTrue(
            "por eso el exactamente-una-vez vive en el ViewModel",
            saldo() < 1230.0
        )
    }

    // --- Plomería ------------------------------------------------------------

    private suspend fun saldo(): Double = db.saleDao().getById(VENTA_ID)!!.SALDO_REST

    private suspend fun pagosDeLaVenta(): Int = db.paymentDao().getPaymentsBySaleId(VENTA_ID).size

    private fun abono(importe: Money, metodo: MetodoDeCobro = MetodoDeCobro.EFECTIVO) =
        AbonoARegistrar(
            abonoId = ABONO_ID,
            ventaId = VENTA_ID,
            importe = importe,
            metodo = metodo
        )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = VENTA_ID,
        DOCTO_CC_ID = VENTA_ID + 1,
        FOLIO = "V-5188",
        CLIENTE_ID = 5021,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 25,
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
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6310.0,
        IMPTE_REST = 1450.0,
        SALDO_REST = 1450.0,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacan",
        ESTADO = "Puebla",
        TELEFONO = "2381627597",
        NOMBRE_COBRADOR = "Rosa Elena Martinez Vazquez",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "LUNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = "SEMANAL"
    )

    private companion object {
        const val VENTA_ID = 77188
        const val ABONO_ID = "abono-de-prueba-0001"

        val COBRADOR = User(
            ID = "u-1",
            NOMBRE = "Rosa Elena Martinez Vazquez",
            EMAIL = "rosa@example.com",
            COBRADOR_ID = 77
        )
    }
}
