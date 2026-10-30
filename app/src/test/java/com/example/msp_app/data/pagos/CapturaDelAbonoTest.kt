package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PaymentsWorkEnqueuer
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoARegistrar
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * "Captura nunca se pierde" del abono, contra Room real (E-APP-084/085/086).
 *
 * Cada prueba fija un hecho de dinero: el marcador nace ANTES de la espera
 * cancelable, el mismo UUID nunca descuenta dos veces, la hora del pago es la
 * de captura, y una foto que no se pudo escribir nunca deshace ni impide el
 * cobro y se repone después.
 */
class CapturaDelAbonoTest : RoomTestBase() {

    private val clock = FakeClock(CAPTURA)
    private val telemetria = RecordingTelemetry(clock)
    private val encolados = mutableListOf<String>()
    private var alEncolar: () -> Unit = {}
    private val encolador = object : PaymentsWorkEnqueuer {
        override fun enqueue(paymentId: String) {
            encolados += paymentId
            alEncolar()
        }
    }
    private var traerUsuario: suspend () -> User? = { COBRADOR }

    @Before
    fun sembrar() = runTest {
        db.saleDao().insertAll(listOf(venta()))
    }

    private fun adaptador(imagenes: PaymentImageDao = db.paymentImageDao()) =
        RegistroDeAbonoAdapter(
            db = db,
            saleDao = db.saleDao(),
            pagos = PaymentsLocalDataSource(db.paymentDao(), db.saleDao()),
            imagenes = imagenes,
            telemetry = telemetria,
            encolador = encolador,
            clock = clock,
            traerUsuario = { traerUsuario() },
            emailEnSesion = { EMAIL }
        )

    // --- E-APP-084: el marcador primero, la espera después ------------------

    @Test
    fun `E-APP-084 cancelado esperando al cobrador deja el marcador CAPTURADA y nada de dinero`() =
        runTest {
            val enEspera = CompletableDeferred<Unit>()
            traerUsuario = {
                enEspera.complete(Unit)
                CompletableDeferred<User>().await()
            }
            val job = launch { adaptador().registrar(abono()) }
            enEspera.await()
            job.cancel()
            job.join()

            assertEquals(CapturaEnCursoEntity.CAPTURADA, marcador()!!.ESTADO)
            assertNull(
                "sin cobrador no se escribe dinero",
                db.paymentDao().getPaymentById(ABONO_ID)
            )
            assertEquals("el saldo no se movio", 1450.0, saldo(), 1e-9)
            assertTrue(encolados.isEmpty())
        }

    @Test
    fun `E-APP-084 el marcador se escribe aunque el job ya este cancelado al entrar`() = runTest {
        traerUsuario = { CompletableDeferred<User>().await() }
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            currentCoroutineContext()[Job]!!.cancel()
            adaptador().registrar(abono())
        }
        job.join()
        assertNotNull("NonCancellable: el marcador entra aunque la pantalla ya se fue", marcador())
    }

    @Test
    fun `E-APP-084 un gemelo colapsado con el mismo UUID no descuenta otra vez`() = runTest {
        // El sync ya reemplazó la fila UUID por la canónica numérica que cita el
        // UUID en PAGO_RECIBIDO_ID; el saldo ya lleva el descuento.
        db.paymentDao().savePayment(canonica())
        db.saleDao().updateTotal(VENTA_ID, 220.0, EstadoCobranza.PAGADO)

        assertEquals(ResultadoDelAbono.REGISTRADO, adaptador().registrar(abono()))

        assertEquals("ni un segundo descuento", 1230.0, saldo(), 1e-9)
        assertNull("ni una segunda fila", db.paymentDao().getPaymentById(ABONO_ID))
        assertEquals(CapturaEnCursoEntity.ESCRITA, marcador()!!.ESTADO)
    }

    @Test
    fun `E-APP-084 cancelada en el encolado post-commit el resultado sigue siendo REGISTRADO`() =
        runTest {
            lateinit var job: Job
            var resultado: ResultadoDelAbono? = null
            alEncolar = { job.cancel() }
            job = launch(UnconfinedTestDispatcher(testScheduler)) {
                resultado = adaptador().registrar(abono())
            }
            job.join()

            assertEquals(ResultadoDelAbono.REGISTRADO, resultado)
            assertEquals(1230.0, saldo(), 1e-9)
            assertEquals(listOf(ABONO_ID), encolados)
        }

    // --- E-APP-085: la hora del pago es la de captura ------------------------

    @Test
    fun `E-APP-085 un abono capturado a T y guardado tres horas despues lleva la hora T`() =
        runTest {
            // Primer intento: muere esperando al cobrador.
            val enEspera = CompletableDeferred<Unit>()
            traerUsuario = {
                enEspera.complete(Unit)
                CompletableDeferred<User>().await()
            }
            val job = launch { adaptador().registrar(abono(capturadoEn = CAPTURA)) }
            enEspera.await()
            job.cancel()
            job.join()

            // Tres horas después, "Guardar": lo que manda el marcador.
            clock.advance(Duration.ofHours(3))
            traerUsuario = { COBRADOR }
            val enCurso = adaptador().enCursoDe(VENTA_ID)!!
            assertEquals(CAPTURA, enCurso.capturadoEn)
            assertEquals(
                ResultadoDelAbono.REGISTRADO,
                adaptador().registrar(abono(capturadoEn = enCurso.capturadoEn))
            )

            assertEquals(
                "2026-10-20T15:04:05Z",
                db.paymentDao().getPaymentById(ABONO_ID)!!.FECHA_HORA_PAGO
            )
        }

    @Test
    fun `E-APP-085 un reintento sin hora conserva la hora del marcador`() = runTest {
        traerUsuario = { null }
        assertEquals(
            ResultadoDelAbono.SIN_COBRADOR,
            adaptador().registrar(abono(capturadoEn = CAPTURA))
        )
        clock.advance(Duration.ofHours(3))
        traerUsuario = { COBRADOR }

        adaptador().registrar(abono(capturadoEn = null))

        assertEquals(
            "2026-10-20T15:04:05Z",
            db.paymentDao().getPaymentById(ABONO_ID)!!.FECHA_HORA_PAGO
        )
    }

    // --- E-APP-086: la foto va fuera de la transacción y se repone -----------

    @Test
    fun `E-APP-086 si la foto truena el abono queda escrito y la foto se repone al volver`() =
        runTest {
            val rota = object : PaymentImageDao by db.paymentImageDao() {
                override suspend fun insertAll(imagenes: List<PaymentImageEntity>): Unit =
                    error("disco lleno")
            }
            val conFoto = abono(comprobantes = listOf(FOTO))

            assertEquals(
                ResultadoDelAbono.REGISTRADO,
                adaptador(imagenes = rota).registrar(conFoto)
            )
            assertEquals("el cobro no espera a la foto", 1230.0, saldo(), 1e-9)
            assertEquals(CapturaEnCursoEntity.ESCRITA, marcador()!!.ESTADO)
            assertTrue(db.paymentImageDao().getByPagoId(ABONO_ID).isEmpty())

            // Con la foto rota, cerrar NO cierra: el marcador recuerda la foto.
            adaptador(imagenes = rota).cerrar(ABONO_ID)
            assertEquals(CapturaEnCursoEntity.ESCRITA, marcador()!!.ESTADO)

            // Al volver a la pantalla la foto se repone desde el marcador, sin
            // tocar el saldo; y entonces sí cierra.
            val enCurso = adaptador().enCursoDe(VENTA_ID)!!
            assertTrue(enCurso.escrito)
            assertEquals(listOf("IMG-1"), db.paymentImageDao().getByPagoId(ABONO_ID).map { it.ID })
            adaptador().cerrar(ABONO_ID)
            assertEquals(CapturaEnCursoEntity.CERRADA, marcador()!!.ESTADO)
            assertEquals("ni un segundo descuento", 1230.0, saldo(), 1e-9)
        }

    @Test
    fun `E-APP-086 reponer no reescribe una foto ya subida`() = runTest {
        adaptador().registrar(abono(comprobantes = listOf(FOTO)))
        db.paymentImageDao().marcarSubida("IMG-1", "2026-10-20T16:00:00Z")

        adaptador().enCursoDe(VENTA_ID)
        adaptador().registrar(abono(comprobantes = listOf(FOTO)))

        assertEquals(
            "REPLACE sobre la subida la volveria a mandar",
            "2026-10-20T16:00:00Z",
            db.paymentImageDao().getByPagoId(ABONO_ID).single().SUBIDA_EN
        )
    }

    @Test
    fun `E-APP-086 los archivos de un marcador abierto cuentan como vivos para el barrido`() =
        runTest {
            traerUsuario = { null }
            adaptador().registrar(abono(comprobantes = listOf(FOTO)))

            assertEquals(setOf(FOTO.archivo), archivosCitadosPorMarcadores(db.capturaEnCursoDao()))
        }

    // --- E-APP-090: sin descarte ---------------------------------------------

    @Test
    fun `E-APP-090 una CAPTURADA vieja no se barre y se sigue ofreciendo`() = runTest {
        traerUsuario = { null }
        adaptador().registrar(abono())
        clock.advance(Duration.ofDays(30))

        val enCurso = adaptador().enCursoDe(VENTA_ID)

        assertNotNull(marcador())
        assertEquals(false, enCurso!!.escrito)
    }

    @Test
    fun `E-APP-084 una ESCRITA de un dia anterior ya no se ofrece`() = runTest {
        adaptador().registrar(abono())
        clock.advance(Duration.ofDays(1))
        assertNull(adaptador().enCursoDe(VENTA_ID))
    }

    // --- Plomería --------------------------------------------------------------

    private suspend fun marcador() = db.capturaEnCursoDao().obtener(ABONO_ID)

    private suspend fun saldo(): Double = db.saleDao().getById(VENTA_ID)!!.SALDO_REST

    private fun abono(
        capturadoEn: Instant? = CAPTURA,
        comprobantes: List<ComprobanteDelAbono> = emptyList()
    ) = AbonoARegistrar(
        abonoId = ABONO_ID,
        ventaId = VENTA_ID,
        importe = Money.of(BigDecimal("220")),
        metodo = MetodoDeCobro.EFECTIVO,
        comprobantes = comprobantes,
        capturadoEn = capturadoEn
    )

    private fun canonica() = PaymentEntity(
        ID = "15080902",
        COBRADOR = "Rosa Elena Martinez Vazquez",
        DOCTO_CC_ACR_ID = VENTA_ID,
        DOCTO_CC_ID = 15080902,
        FECHA_HORA_PAGO = "2026-10-20T15:04:05Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 220.0,
        LAT = 0.0,
        LNG = 0.0,
        CLIENTE_ID = 5021,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = MetodoDeCobro.EFECTIVO.formaCobroId,
        ZONA_CLIENTE_ID = 25,
        NOMBRE_CLIENTE = "Victoria Flores Olmedo",
        PAGO_RECIBIDO_ID = ABONO_ID
    )

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
        const val ABONO_ID = "3b1f5a0e-7d2c-4c8e-9a41-5e6f7a8b9c0d"
        const val EMAIL = "rosa@example.com"

        /** 09:04:05 en Tehuacán: la hora en que el cobrador tocó "confirmar". */
        val CAPTURA: Instant = Instant.parse("2026-10-20T15:04:05Z")

        val FOTO =
            ComprobanteDelAbono(id = "IMG-1", archivo = "/tmp/IMG-1.jpg", mime = "image/jpeg")

        val COBRADOR = User(
            ID = "u-1",
            NOMBRE = "Rosa Elena Martinez Vazquez",
            EMAIL = EMAIL,
            COBRADOR_ID = 77
        )
    }
}
