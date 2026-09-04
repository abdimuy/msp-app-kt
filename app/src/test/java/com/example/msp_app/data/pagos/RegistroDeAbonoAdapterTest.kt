package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
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
import org.junit.Assert.assertNotEquals
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

    /** Los `Payment.ID` con los que se pidio la ubicacion, en orden. */
    private val ubicacionesPedidas = mutableListOf<String>()

    /** Lo que hace la lambda de ubicacion. Por defecto, grabar y volver. */
    private var alPedirUbicacion: (String) -> Unit = { ubicacionesPedidas += it }

    @Before
    fun setUpAdaptador() = runTest {
        pagos = PaymentsLocalDataSource(db.paymentDao(), db.saleDao())
        db.saleDao().insertAll(listOf(venta()))
    }

    private fun adaptador() = RegistroDeAbonoAdapter(
        db = db,
        saleDao = db.saleDao(),
        pagos = pagos,
        telemetry = telemetria,
        clock = clock,
        traerUsuario = { usuario },
        pedirUbicacion = { alPedirUbicacion(it) }
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
            db = db,
            saleDao = db.saleDao(),
            pagos = pagos,
            telemetry = telemetria,
            clock = clock,
            traerUsuario = { error("firestore caido cobrando a Victoria Flores") },
            pedirUbicacion = { ubicacionesPedidas += it }
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

    // --- La transacción: o las dos escrituras, o ninguna ---------------------

    /**
     * **El hallazgo Critical, medido.**
     *
     * `PaymentsLocalDataSource` es una `class` común: el `@Transaction` de
     * `insertPaymentAndUpdateSale` **no hace nada fuera de un `@Dao`**, así que
     * sin `db.withTransaction` el insert del pago y el descuento del saldo son
     * dos escrituras sueltas.
     *
     * Aquí `updateTotal` truena DESPUÉS de que el pago ya se insertó. Sin la
     * transacción queda un **pago huérfano**: cobrado en el historial, con el
     * saldo intacto. Y no es solo un renglón feo — el guard anti-duplicado de la
     * pantalla se resuelve MIRANDO el historial, así que vería ese huérfano y
     * daría el abono por registrado con el saldo nunca descontado.
     *
     * Con la transacción no queda nada, y el reintento con la misma clave
     * descuenta **una sola vez**.
     */
    @Test
    fun `si el saldo truena despues del insert, no queda pago huerfano y el reintento descuenta una vez`() =
        runTest {
            val fragil = RegistroDeAbonoAdapter(
                db = db,
                saleDao = db.saleDao(),
                pagos = PaymentsLocalDataSource(
                    db.paymentDao(),
                    SaleDaoQueTruenaAlDescontar(db.saleDao())
                ),
                telemetry = telemetria,
                clock = clock,
                traerUsuario = { usuario },
                pedirUbicacion = { ubicacionesPedidas += it }
            )

            assertEquals(
                ResultadoDelAbono.FALLO_EL_GUARDADO,
                fragil.registrar(abono(dinero("220")))
            )
            assertNull(
                "la transaccion revirtio el insert: ningun pago huerfano",
                pagos.getPaymentById(ABONO_ID)
            )
            assertEquals("y el saldo no se movio", 1450.0, saldo(), 1e-9)

            // Reintento con la MISMA clave, ya sin el fallo.
            assertEquals(ResultadoDelAbono.REGISTRADO, adaptador().registrar(abono(dinero("220"))))
            assertEquals("descontado UNA sola vez", 1230.0, saldo(), 1e-9)
            assertEquals(1, pagosDeLaVenta())
        }

    // --- El tercer cinturón: contra el saldo recién leído ---------------------

    @Test
    fun `el escritor bloquea un sobrepago aunque nadie lo haya filtrado antes`() = runTest {
        // Este puerto es inyectable en todo el grafo: el bloqueo tiene que vivir
        // tambien aqui, no solo en la pantalla y en el caso de uso.
        val resultado = adaptador().registrar(abono(dinero("1451")))

        assertEquals(ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD, resultado)
        assertNull(pagos.getPaymentById(ABONO_ID))
        assertEquals(1450.0, saldo(), 1e-9)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_BLOQUEADO_EN_ESCRITURA
        }
        assertEquals("EXCEDE_EL_SALDO", error.props[PagosTelemetria.PROP_BLOQUEOS])
    }

    @Test
    fun `el saldo exacto sigue pasando por el escritor`() = runTest {
        assertEquals(ResultadoDelAbono.REGISTRADO, adaptador().registrar(abono(dinero("1450"))))
        assertEquals(0.0, saldo(), 1e-9)
    }

    @Test
    fun `un saldo que se puso rancio bajo los pies se bloquea en la escritura`() = runTest {
        // La pantalla cargo con saldo 1450 y aprobo 1000; para cuando llega la
        // escritura la venta ya solo debe 500. Los dos cinturones de arriba
        // miraban el saldo VIEJO; este mira el de la base.
        db.saleDao().updateTotal(VENTA_ID, 950.0, EstadoCobranza.PAGADO)
        assertEquals(500.0, saldo(), 1e-9)

        assertEquals(
            ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD,
            adaptador().registrar(abono(dinero("1000")))
        )
        assertNull(pagos.getPaymentById(ABONO_ID))
        assertEquals("el saldo no se movio", 500.0, saldo(), 1e-9)
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

    // --- La ubicación: se pide, y jamás decide -------------------------------

    /**
     * **El hallazgo #2 de la ronda 1, medido.**
     *
     * `PaymentFactory` deja `LAT`/`LNG` en `0.0` y el mapa del día descarta
     * exactamente ese punto (`RouteMapScreen`: `lat != 0.0 || lng != 0.0`), así
     * que sin esta llamada **todo abono del camino nuevo era invisible en el
     * mapa**. El legado sí la hacía (`NewPaymentDialog` arrancaba
     * `UpdateLocationService`); el camino que la Task 21 volvió principal, no.
     *
     * **Control de reversión:** borrar `pedirUbicacionDelAbono(abono.abonoId)`
     * de `RegistroDeAbonoAdapter.registrar` pone este test en ROJO.
     */
    @Test
    fun `un abono registrado pide su ubicacion`() = runTest {
        assertEquals(ResultadoDelAbono.REGISTRADO, adaptador().registrar(abono(dinero("220"))))
        assertEquals(listOf(ABONO_ID), ubicacionesPedidas)
    }

    /**
     * **El id que viaja es el del PAGO, no el de la venta ni el del cliente.**
     *
     * `UpdateLocationService` mete ese id en `PaymentDao.updateLocation`, que
     * filtra `Payment.id`. Este plan ya cazó seis defectos de la familia "un id
     * donde iba el otro", y sin esta afirmación mandar `ventaId` habría dejado
     * el mapa igual de vacío y todos los demás tests en verde.
     */
    @Test
    fun `el id que viaja es el del pago, no el de la venta`() = runTest {
        adaptador().registrar(abono(dinero("220")))

        val pedido = ubicacionesPedidas.single()
        assertEquals(pagos.getPaymentById(ABONO_ID)!!.ID, pedido)
        assertNotEquals(VENTA_ID.toString(), pedido)
        assertNotEquals(CLIENTE_ID.toString(), pedido)
    }

    /**
     * **La ubicación nunca bloquea el dinero.** Arrancar un servicio de primer
     * plano puede lanzar (Android 12+ lo rechaza con la app en segundo plano).
     * El abono ya está escrito cuando eso pasa: devolver `FALLO_EL_GUARDADO`
     * haría que la pantalla ofreciera reintentar un cobro que ya ocurrió.
     *
     * **Control de reversión:** quitar el `try/catch` de
     * `pedirUbicacionDelAbono` propaga la excepción al `catch` general y el
     * resultado se vuelve `FALLO_EL_GUARDADO` — este test se pone ROJO.
     */
    @Test
    fun `si arrancar la ubicacion falla, el abono sigue registrado y se reporta`() = runTest {
        alPedirUbicacion = { error("startForegroundService rechazado para Victoria Flores") }

        assertEquals(ResultadoDelAbono.REGISTRADO, adaptador().registrar(abono(dinero("220"))))
        assertEquals("el pago quedo escrito", 220.0, pagos.getPaymentById(ABONO_ID)!!.IMPORTE, 0.0)
        assertEquals("y el saldo bajo", 1230.0, saldo(), 1e-9)

        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_SIN_UBICACION
        }
        assertEquals("IllegalStateException", error.props[PagosTelemetria.PROP_EXCEPCION])
        // Anti-PII: el texto libre de la excepción no viaja (`props` incluye
        // ya el `message`, así que esta sola afirmación cubre los dos).
        assertFalse(error.props.values.any { it.contains("Victoria") })
    }

    @Test
    fun `un abono que NO se registro no pide ubicacion`() = runTest {
        usuario = null
        adaptador().registrar(abono(dinero("220")))
        assertTrue("sin cobrador no hay pago que ubicar", ubicacionesPedidas.isEmpty())

        usuario = COBRADOR
        adaptador().registrar(abono(dinero("1451")))
        assertTrue("un sobrepago bloqueado tampoco", ubicacionesPedidas.isEmpty())

        adaptador().registrar(abono(dinero("220")).copy(ventaId = 999))
        assertTrue("ni una venta que el telefono no tiene", ubicacionesPedidas.isEmpty())
    }

    /**
     * **Control positivo del test de arriba.** El mismo montaje, con el abono
     * pasando de verdad, SÍ graba una petición: el vacío de arriba es una
     * ausencia medida, no una lambda que nunca se cableó.
     */
    @Test
    fun `control positivo, el mismo montaje si graba cuando el abono pasa`() = runTest {
        adaptador().registrar(abono(dinero("220")))
        assertEquals(1, ubicacionesPedidas.size)
    }

    /**
     * Una transacción revertida no puede pedir ubicación de un pago que no
     * existe: se pide DESPUÉS del commit, no antes.
     */
    @Test
    fun `si la transaccion se revierte no se pide ubicacion`() = runTest {
        val fragil = RegistroDeAbonoAdapter(
            db = db,
            saleDao = db.saleDao(),
            pagos = PaymentsLocalDataSource(
                db.paymentDao(),
                SaleDaoQueTruenaAlDescontar(db.saleDao())
            ),
            telemetry = telemetria,
            clock = clock,
            traerUsuario = { usuario },
            pedirUbicacion = { ubicacionesPedidas += it }
        )

        assertEquals(ResultadoDelAbono.FALLO_EL_GUARDADO, fragil.registrar(abono(dinero("220"))))
        assertTrue(ubicacionesPedidas.isEmpty())
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

    /**
     * `SaleDao` real salvo por [updateTotal], que truena. Delegación de Kotlin:
     * fakes escritos a mano, sin MockK ni Mockito.
     */
    private class SaleDaoQueTruenaAlDescontar(real: SaleDao) : SaleDao by real {
        override suspend fun updateTotal(
            saleId: Int,
            amount: Double,
            estadoCobranza: EstadoCobranza
        ): Unit = error("updateTotal truena justo despues del insert")
    }

    private companion object {
        const val VENTA_ID = 77188
        const val CLIENTE_ID = 5021
        const val ABONO_ID = "abono-de-prueba-0001"

        val COBRADOR = User(
            ID = "u-1",
            NOMBRE = "Rosa Elena Martinez Vazquez",
            EMAIL = "rosa@example.com",
            COBRADOR_ID = 77
        )
    }
}
