package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.core.sync.cobranza.CobranzaSyncManager
import com.example.msp_app.core.sync.cobranza.CobranzaWriteMutex
import com.example.msp_app.core.sync.cobranza.RefrescoDelSaldoDeLaVenta
import com.example.msp_app.core.sync.cobranza.SyncOutcome
import com.example.msp_app.core.sync.cobranza.UserContext
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.cobranza.DigestResponse
import com.example.msp_app.data.api.services.cobranza.IdsResponse
import com.example.msp_app.data.api.services.cobranza.PagoDto
import com.example.msp_app.data.api.services.cobranza.SyncPagosResponse
import com.example.msp_app.data.api.services.cobranza.SyncVentasResponse
import com.example.msp_app.data.api.services.cobranza.V2CobranzaApi
import com.example.msp_app.data.api.services.cobranza.VentaDto
import com.example.msp_app.data.api.services.payment.PagoRecibidoDTO
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.data.pagos.CondonacionFixtures.EFECTIVO
import com.example.msp_app.data.pagos.CondonacionFixtures.condonacion
import com.example.msp_app.data.pagos.CondonacionFixtures.saldoDe
import com.example.msp_app.data.pagos.CondonacionFixtures.venta
import com.example.msp_app.data.pagos.RegistroDeCondonacion
import com.example.msp_app.data.pagos.ResultadoDeLaCondonacion
import java.io.File
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * **Una condonación que el servidor rechazó por saldo no deja el saldo local
 * descontado.**
 *
 * P3 de la compuerta del 2026-09-26: `422 pago_saldo_insuficiente` con
 * `X-Intent-Captured` → `RELEASE` → `markDone`, y nada más. La fila quedaba
 * `GUARDADO = 1`, `DOCTO_CC_ID = 0` —indistinguible de un pago aplicado cuya
 * respuesta se perdió (E-APP-019/E-APP-032)— y el saldo local descontado por
 * algo que Microsip nunca aplicó. Peor: con `DOCTO_CC_ID = 0` la fila seguía
 * entrando a `sumImporteNoReconocidoPorElServidor`, así que cada `mergeVentas`
 * la volvía a restar del saldo del servidor.
 *
 * El arreglo, en `PaymentDao.soltarCondonacionRechazada`, en UNA transacción:
 * la fila pasa a [DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR] (no se borra) y el
 * saldo se FIJA al que el servidor tiene hoy (`saldos/by-ids`) menos lo que sigue
 * en vuelo. **Nunca se suma el importe de vuelta**: la compuerta posterior del
 * 2026-09-27 demostró que eso deja el saldo por encima del real. Sin señal, el
 * saldo no se toca. Lo que NO cambia —y se cobra aquí— es el abono y cualquier
 * otro rechazo.
 */
class PendingPaymentsWorkerCondonacionRechazadaTest : RoomTestBase() {

    private lateinit var context: Context

    /** El mutex de escritura de cobranza que comparten el sync y el refresco. */
    private val mutexDelSync = CobranzaWriteMutex()

    /** Trabajos que la prueba intercala en otro hilo; se esperan al final. */
    private val intercaladas = mutableListOf<Deferred<Unit>>()
    private val clock = FakeClock(Instant.parse("2026-09-26T21:28:00Z"))

    @Before
    fun setUpWorker() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `condonacion rechazada por saldo - saldo del servidor y fila marcada rechazada`() =
        runTest {
            sembrarCondonacionEscrita(ID_COND, 2300.0)
            assertEquals("precondición: la escritura descontó", 0.0, saldoDe(db), 1e-9)
            assertEquals(
                "precondición: el merge la restaría como no reconocida",
                2300.0,
                db.paymentDao().sumImporteNoReconocidoPorElServidor(CondonacionFixtures.VENTA),
                1e-9
            )

            val result = correrWorker(ID_COND, rechazoPorSaldo(), CobranzaDelServidor("300.00"))

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals("el saldo es el que el servidor sí tiene", 300.0, saldoDe(db), 1e-9)
            val fila = db.paymentDao().getPaymentById(ID_COND)
            assertNotNull("la fila rechazada NO se borra", fila)
            assertTrue("la fila se suelta: la oficina la tiene", fila!!.GUARDADO_EN_MICROSIP)
            assertEquals(
                "la fila dice 'rechazada', no el 0 de 'sin documento'",
                DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR,
                fila.DOCTO_CC_ID
            )
            assertEquals(
                "el siguiente mergeVentas ya no la vuelve a restar",
                0.0,
                db.paymentDao().sumImporteNoReconocidoPorElServidor(CondonacionFixtures.VENTA),
                1e-9
            )
        }

    /** Un segundo 422 sobre la misma fila (reencolado) deja el mismo saldo: idempotente. */
    @Test
    fun `un segundo rechazo sobre la misma condonacion es idempotente`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 2300.0)
        val servidor = CobranzaDelServidor("300.00")

        correrWorker(ID_COND, rechazoPorSaldo(), servidor)
        correrWorker(ID_COND, rechazoPorSaldo(), servidor)

        assertEquals(300.0, saldoDe(db), 1e-9)
        assertEquals(
            DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR,
            db.paymentDao().getPaymentById(ID_COND)!!.DOCTO_CC_ID
        )
    }

    /**
     * **El 422 marca y encola; no toca el saldo.** La lectura del servidor es de
     * otro trabajo, único por venta, que no vuelve a mandar este pago.
     */
    @Test
    fun `el 422 marca la condonacion y encola el refresco de su venta sin tocar el saldo`() =
        runTest {
            sembrarCondonacionEscrita(ID_COND, 2300.0)

            val result = correrWorker(ID_COND, rechazoPorSaldo())

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(listOf(ZONA to CondonacionFixtures.VENTA), encolados)
            assertEquals("la marca no toca el saldo", 0.0, saldoDe(db), 1e-9)
            assertEquals(
                DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR,
                db.paymentDao().getPaymentById(ID_COND)!!.DOCTO_CC_ID
            )
        }

    /**
     * **Sin señal no se infla nada, y se corrige después.** El refresco sin red
     * pide reintento (WorkManager lo repite con backoff y red obligatoria); el
     * saldo se queda donde lo dejó el merge, nunca arriba del real. Cuando hay
     * red, el mismo refresco lo deja en el del servidor — sin depender de que el
     * cursor del sync vuelva a entregar la venta (el fake respeta el cursor: una
     * segunda corrida no la trae).
     */
    @Test
    fun `sin red el refresco reintenta sin inflar y con red corrige`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 1000.0)
        mergeVentasCon(CobranzaDelServidor(saldoDelServidor = "500.00"))
        correrWorker(ID_COND, rechazoPorSaldo())

        val sinRed = correrRefresco(CobranzaDelServidor("500.00", sinRed = true))

        assertEquals(ListenableWorker.Result.retry(), sinRed)
        assertEquals("sin saldo del servidor, no se toca", 0.0, saldoDe(db), 1e-9)
        // El cursor no vuelve a entregar la venta: el sync solo NO la corrige.
        mergeVentasCon(CobranzaDelServidor(saldoDelServidor = "500.00"))
        assertEquals(0.0, saldoDe(db), 1e-9)

        assertEquals(
            ListenableWorker.Result.success(),
            correrRefresco(CobranzaDelServidor("500.00"))
        )
        assertEquals(500.0, saldoDe(db), 1e-9)
    }

    /**
     * **El cerrojo `DOCTO_CC_ID = 0`.** Una condonación que el servidor YA
     * aplicó —trae su documento— pero cuyo `markDone` se perdió, recibe después
     * un 422 por saldo (el saldo ya lo consumió ella misma). No es un rechazo: no
     * se marca -1 y el saldo no se toca.
     */
    @Test
    fun `una condonacion con documento no se marca rechazada aunque llegue un 422`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 0.0)))
        db.paymentDao().savePayment(
            condonacion(ID_COND, 2300.0).copy(DOCTO_CC_ID = 15_080_902)
        )

        correrWorker(ID_COND, rechazoPorSaldo())

        val fila = db.paymentDao().getPaymentById(ID_COND)!!
        assertEquals(15_080_902, fila.DOCTO_CC_ID)
        assertTrue(fila.GUARDADO_EN_MICROSIP)
        assertEquals("el saldo no se toca", 0.0, saldoDe(db), 1e-9)
    }

    /**
     * **El abono con el mismo 422 NO cambia.** Fuera de alcance a propósito: su
     * rechazo no está medido. Se suelta como siempre y el saldo se queda como
     * estaba.
     */
    @Test
    fun `un abono con el mismo 422 conserva el comportamiento de siempre`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2000.0)))
        db.paymentDao().savePayment(condonacion(ID_ABONO, 300.0, formaCobro = EFECTIVO))

        val result = correrWorker(ID_ABONO, rechazoPorSaldo())

        assertEquals(ListenableWorker.Result.success(), result)
        val fila = db.paymentDao().getPaymentById(ID_ABONO)!!
        assertTrue(fila.GUARDADO_EN_MICROSIP)
        assertEquals(0, fila.DOCTO_CC_ID)
        assertEquals("el saldo del abono no se toca", 2000.0, saldoDe(db), 1e-9)
    }

    /** Otro código de 422 sobre una condonación: no está medido, no cambia. */
    @Test
    fun `una condonacion rechazada por otro motivo conserva el comportamiento de siempre`() =
        runTest {
            sembrarCondonacionEscrita(ID_COND, 2300.0)

            correrWorker(ID_COND, rechazo(code = "pago_cargo_cancelado"))

            val fila = db.paymentDao().getPaymentById(ID_COND)!!
            assertTrue(fila.GUARDADO_EN_MICROSIP)
            assertEquals(0, fila.DOCTO_CC_ID)
            assertEquals(0.0, saldoDe(db), 1e-9)
        }

    /** Sin custodia confirmada nadie la tiene: se reintenta y nada cambia. */
    @Test
    fun `sin X-Intent-Captured se reintenta y no toca nada`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 2300.0)

        val result = correrWorker(ID_COND, rechazo(capturada = false))

        assertEquals(ListenableWorker.Result.retry(), result)
        val fila = db.paymentDao().getPaymentById(ID_COND)!!
        assertFalse(fila.GUARDADO_EN_MICROSIP)
        assertEquals(0, fila.DOCTO_CC_ID)
        assertEquals(0.0, saldoDe(db), 1e-9)
    }

    /** Control positivo: la condonación que el servidor APLICA no se marca rechazada. */
    @Test
    fun `una condonacion aplicada no se marca rechazada`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 2300.0)
        val aplica = api { _, _ -> PagoRecibidoDTO(id = "16132276", docto_cc_id = 15_080_902) }

        assertEquals(ListenableWorker.Result.success(), correrWorker(ID_COND, aplica))

        val fila = db.paymentDao().getPaymentById(ID_COND)!!
        assertTrue(fila.GUARDADO_EN_MICROSIP)
        assertEquals(15_080_902, fila.DOCTO_CC_ID)
        assertEquals(0.0, saldoDe(db), 1e-9)
        assertEquals(
            "una aplicada no se marca rechazada; sólo pide el refresco de todo pago aplicado",
            listOf(ZONA to CondonacionFixtures.VENTA),
            encolados
        )
    }

    /**
     * **La carrera que encontró la compuerta DESPUÉS (2026-09-27): el saldo no
     * puede quedar POR ENCIMA del real.**
     *
     * El teléfono ve 1000 pero el servidor ya tiene 500 (otro abono aplicado).
     * El cobrador condona 1000: pasa la guarda local y deja 0. Un tick del sync
     * corre ANTES que el worker: `mergeVentas` pone
     * `max(500 − 1000 en vuelo, 0) = 0`. Luego llega el 422. Sumar el importe de
     * vuelta a ciegas deja 1000 contra 500 reales — el cobrador cobraría de más.
     * El único valor correcto es el del servidor: 500.
     */
    @Test
    fun `condonacion rechazada tras un merge - el saldo queda en el del servidor`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 1000.0)
        val cobranza = CobranzaDelServidor(saldoDelServidor = "500.00")
        mergeVentasCon(cobranza)
        assertEquals("precondición: el merge dejó el clamp en cero", 0.0, saldoDe(db), 1e-9)

        correrWorker(ID_COND, rechazoPorSaldo(), cobranza)

        assertEquals("el saldo tiene que ser el del servidor", 500.0, saldoDe(db), 1e-9)
    }

    /**
     * **Compuerta posterior #2, escenario A (MEDIDO por la compuerta: 500 contra
     * 200).** `by-ids` devuelve 500; mientras tanto otro cobrador abona 300 y un
     * tick del sync —que SÍ toma el mutex de escritura— deja 200. Si el refresco
     * escribe su foto de 500 después, pisa el 200. La lectura del servidor y su
     * escritura tienen que ir bajo el MISMO mutex que `mergeVentas`.
     */
    @Test
    fun `A - un merge no puede intercalarse entre la lectura by-ids y la escritura`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 1000.0)
        val servidor =
            conMergeIntercalado(CobranzaDelServidor("500.00"), saldoTrasElMerge = "200.00")

        refrescarTrasRechazo(servidor)

        assertEquals("gana lo último que dijo el servidor", 200.0, saldoDe(db), 1e-9)
    }

    /**
     * **Escenario B (MEDIDO por la compuerta: 500 contra 300).** `by-ids` lee 500
     * ANTES de que el servidor aplique un abono en vuelo de $200 de la misma
     * venta; el worker de ese abono anota su `DOCTO_CC_ID` antes de la
     * escritura. Si `persistDoctoCcId` no respeta el mutex, la suma de lo no
     * reconocido ya no lo incluye y queda 500. Lo real es 300.
     */
    @Test
    fun `B - un abono aplicado entre la lectura y la escritura no infla el saldo`() = runTest {
        sembrarCondonacionEscrita(ID_COND, 1000.0)
        db.paymentDao().savePayment(abonoEnVuelo())
        val servidor = conAbonoAplicadoIntercalado(CobranzaDelServidor("500.00"))

        refrescarTrasRechazo(servidor)
        // El abono aplicado encola su propio refresco al anotar su documento; el
        // servidor, ya con el abono, dice 300.
        correrRefresco(CobranzaDelServidor("300.00"))

        assertEquals(300.0, saldoDe(db), 1e-9)
    }

    /**
     * **La misma carrera B, en `mergeVentas`** (preexistente de clase, medida por
     * la compuerta en HEAD: 500 contra 300). La página del sync trae 500; el
     * abono en vuelo se aplica y anota su documento antes del merge.
     */
    @Test
    fun `B en mergeVentas - un abono aplicado a media pagina no infla el saldo`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 500.0)))
        db.paymentDao().savePayment(abonoEnVuelo())
        val base = CobranzaDelServidor("500.00")
        val servidor = object : V2CobranzaApi by base {
            override suspend fun syncVentas(
                zonaId: Int,
                cursor: String?,
                afterId: Int,
                limit: Int,
                desde: String?
            ): SyncVentasResponse {
                val pagina = base.syncVentas(zonaId, cursor, afterId, limit, desde)
                intercalar { aplicarElAbonoEnVuelo() }
                return pagina
            }
        }

        mergeVentasCon(servidor)
        esperarIntercaladas()
        correrRefresco(CobranzaDelServidor("300.00"))

        assertEquals(300.0, saldoDe(db), 1e-9)
    }

    /**
     * **Tras un 200 nunca se re-postea (ZZ2), y esta prueba cuida
     * `NonCancellable`.** El servidor aplicó el abono. Mientras el worker cierra
     * sus comprobantes —entre el 200 y `markDone`— WorkManager lo cancela (se
     * fue la red con la restricción CONNECTED, o pasó su límite) y lo vuelve a
     * correr. Un segundo POST con la misma clave no duplica dinero, pero el
     * replay borra el comprobante original (medido en dev el 2026-09-25). La
     * sección posterior al 200 tiene que terminar aunque la cancelen, y la
     * captura quedar soltada.
     *
     * La salida temprana por `GUARDADO_EN_MICROSIP` la cuida además
     * `PendingPaymentsWorkerV2Test.v2_duplicate_run_after_200_does_not_resend`:
     * revertir cualquiera de las dos pone una prueba en rojo.
     */
    @Test
    fun `ZZ2 - tras un 200, cancelar antes de marcarla enviada no provoca un segundo POST`() =
        runTest {
            db.saleDao().insertAll(listOf(venta(saldo = 2000.0)))
            db.paymentDao().savePayment(abonoEnVuelo())
            val archivo = File.createTempFile("comprobante", ".jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
                deleteOnExit()
            }
            db.paymentImageDao().insertAll(
                listOf(
                    PaymentImageEntity(
                        ID = "IMG-1",
                        PAGO_ID = ID_ABONO,
                        URI = archivo.absolutePath,
                        MIME = "image/jpeg",
                        ORDEN = 0,
                        CREADA_EN = "2026-09-26T21:20:00Z"
                    )
                )
            )
            var posts = 0
            val aplica = api { _, _ ->
                posts += 1
                PagoRecibidoDTO(id = "16132300", docto_cc_id = 16_132_299)
            }
            val cerrando = CompletableDeferred<Unit>()
            val suelta = CompletableDeferred<Unit>()
            val real = db.paymentImageDao()
            val comprobantes = object : PaymentImageDao by real {
                override suspend fun marcarSubida(imagenId: String, subidaEn: String) {
                    cerrando.complete(Unit)
                    suelta.await()
                    real.marcarSubida(imagenId, subidaEn)
                }
            }

            runBlocking {
                val primero = construirWorker(ID_ABONO, aplica, comprobantes)
                val corrida = CoroutineScope(Dispatchers.IO).async { primero.doWork() }
                withTimeout(ESPERA_DEL_INTERCALADO_MS * 5) { cerrando.await() }
                corrida.cancel()
                suelta.complete(Unit)
                runCatching { corrida.await() }
            }
            // WorkManager vuelve a correr el trabajo detenido.
            correrWorker(ID_ABONO, aplica)

            assertEquals("hubo un segundo POST tras un 200", 1, posts)
            assertTrue(
                "la captura aplicada tiene que quedar soltada",
                db.paymentDao().getPaymentById(ID_ABONO)!!.GUARDADO_EN_MICROSIP
            )
        }

    /**
     * **El orden tras el 200: comprobantes → soltar → documento.** Soltar
     * (`markDone`) antes de anotar el documento es lo que hace que un reintento
     * nunca vuelva a mandar el pago, y cierra la ventana "pendiente con
     * `DOCTO_CC_ID` ya anotado" que el colapso del gemelo legacy tiene que
     * esquivar. Se exige registrando el orden de las escrituras reales.
     */
    @Test
    fun `tras el 200 el orden es comprobantes, soltar y despues el documento`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 2000.0)))
        db.paymentDao().savePayment(abonoEnVuelo())
        val archivo = File.createTempFile("comprobante", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
            deleteOnExit()
        }
        db.paymentImageDao().insertAll(
            listOf(
                PaymentImageEntity(
                    ID = "IMG-1",
                    PAGO_ID = ID_ABONO,
                    URI = archivo.absolutePath,
                    MIME = "image/jpeg",
                    ORDEN = 0,
                    CREADA_EN = "2026-09-26T21:20:00Z"
                )
            )
        )
        val orden = mutableListOf<String>()
        val pagos = db.paymentDao()
        val imagenesReales = db.paymentImageDao()
        val registraPagos = object : PaymentDao by pagos {
            override suspend fun updateEstado(id: String, newEstado: Int) {
                orden += "soltar"
                pagos.updateEstado(id, newEstado)
            }
            override suspend fun updateDoctoCcId(id: String, doctoCcId: Int) {
                orden += "documento"
                pagos.updateDoctoCcId(id, doctoCcId)
            }
        }
        val registraImagenes = object : PaymentImageDao by imagenesReales {
            override suspend fun marcarSubida(imagenId: String, subidaEn: String) {
                orden += "comprobantes"
                imagenesReales.marcarSubida(imagenId, subidaEn)
            }
        }
        val aplica = api { _, _ -> PagoRecibidoDTO(id = "16132300", docto_cc_id = 16_132_299) }

        val resultado = runBlocking {
            construirWorker(
                ID_ABONO,
                aplica,
                imagenes = registraImagenes,
                almacen = { PaymentsLocalDataSource(registraPagos, db.saleDao()) }
            ).doWork()
        }

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals(listOf("comprobantes", "soltar", "documento"), orden)
    }

    /**
     * **Cuarta compuerta posterior: la deflación fósil (MEDIDO por la compuerta:
     * 100 contra 300).** El servidor ya aplicó un abono de $200 (saldo 300) y el
     * sync lo publica mientras el teléfono todavía lo tiene sin documento:
     * `mergeVentas` calcula 300 − 200 = 100. Luego el worker anota el documento y
     * nadie recalcula. El refresco que se encola al anotar lo deja en 300.
     */
    @Test
    fun `un merge que resto un abono ya aplicado se corrige al anotar su documento`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 500.0)))
        db.paymentDao().savePayment(abonoEnVuelo())
        mergeVentasCon(CobranzaDelServidor("300.00"))
        assertEquals("precondición: el merge restó el abono ya aplicado", 100.0, saldoDe(db), 1e-9)

        correrWorker(
            ID_ABONO,
            api { _, _ -> PagoRecibidoDTO(id = "16132300", docto_cc_id = 16_132_299) },
            CobranzaDelServidor("300.00")
        )

        assertEquals(300.0, saldoDe(db), 1e-9)
    }

    /** Un pago subido encola exactamente un refresco, el de su cargo. */
    @Test
    fun `un pago aplicado encola exactamente un refresco de su venta`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 500.0)))
        db.paymentDao().savePayment(abonoEnVuelo())

        correrWorker(
            ID_ABONO,
            api { _, _ -> PagoRecibidoDTO(id = "16132300", docto_cc_id = 16_132_299) }
        )

        assertEquals(listOf(ZONA to CondonacionFixtures.VENTA), encolados)
    }

    /** Control: sin documento en la respuesta no hay nada que re-leer. */
    @Test
    fun `un 200 sin documento no encola refresco`() = runTest {
        db.saleDao().insertAll(listOf(venta(saldo = 500.0)))
        db.paymentDao().savePayment(abonoEnVuelo())

        correrWorker(ID_ABONO, api { _, _ -> PagoRecibidoDTO(id = "16132300") })

        assertTrue(encolados.isEmpty())
    }

    // ─── apoyo ─────────────────────────────────────────────────────────────────

    /** La condonación escrita por el camino REAL de la pantalla. */
    private suspend fun sembrarCondonacionEscrita(id: String, raw: Double) {
        db.saleDao().insertAll(listOf(venta(saldo = raw)))
        assertEquals(
            ResultadoDeLaCondonacion.GUARDADA,
            RegistroDeCondonacion(db).condonar(condonacion(id, raw))
        )
    }

    private fun rechazoPorSaldo(): V2PaymentsApi = rechazo(code = "pago_saldo_insuficiente")

    private fun rechazo(code: String = "pago_saldo_insuficiente", capturada: Boolean = true) =
        api { _, _ ->
            throw httpError(
                422,
                """{"type":"about:blank","title":"Unprocessable Entity","status":422,""" +
                    """"code":"$code","detail":"el importe excede el saldo del cargo"}""",
                intentCaptured = if (capturada) "5b0c3a55-1f6e-4f7e-9a0e-5c1f0a9f7a11" else null
            )
        }

    private fun api(
        crear: suspend (idempotencyKey: String, datos: RequestBody) -> PagoRecibidoDTO
    ): V2PaymentsApi = object : V2PaymentsApi {
        override suspend fun crearPago(
            idempotencyKey: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): PagoRecibidoDTO = crear(idempotencyKey, datos)

        // 404: el servidor no la tiene, así que la decisión cae a la tabla.
        override suspend fun obtenerPago(id: String): PagoRecibidoDTO = throw httpError(404)
    }

    private fun httpError(
        code: Int,
        body: String = "{}",
        intentCaptured: String? = null
    ): HttpException {
        val raw = okhttp3.Response.Builder()
            .code(code)
            .message("test")
            .protocol(Protocol.HTTP_1_1)
            .request(Request.Builder().url("http://localhost/").build())
            .header("Content-Type", "application/problem+json")
        if (intentCaptured != null) raw.header("X-Intent-Captured", intentCaptured)
        return HttpException(
            Response.error<PagoRecibidoDTO>(
                body.toResponseBody("application/problem+json".toMediaTypeOrNull()),
                raw.build()
            )
        )
    }

    /** Un abono de $200 de la misma venta, capturado y todavía sin documento. */
    private fun abonoEnVuelo() = condonacion(ID_ABONO, 200.0, formaCobro = EFECTIVO)

    /** Corre el worker REAL del abono contra un servidor que lo aplica con documento. */
    private fun aplicarElAbonoEnVuelo() {
        val aplica = api { _, _ -> PagoRecibidoDTO(id = "16132300", docto_cc_id = 16_132_299) }
        assertEquals(ListenableWorker.Result.success(), correrWorker(ID_ABONO, aplica))
    }

    /**
     * Lanza [bloque] en OTRO hilo y le da hasta un segundo para terminar. Si toma
     * el mutex que otro tiene, se queda esperando y termina después — que es lo
     * que se quiere medir.
     */
    private suspend fun intercalar(bloque: suspend () -> Unit) {
        val trabajo = CoroutineScope(Dispatchers.IO).async { bloque() }
        intercaladas += trabajo
        // En Dispatchers.IO para que la espera sea de reloj real: dentro de
        // `runTest` un timeout corre en tiempo virtual y no espera nada.
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(ESPERA_DEL_INTERCALADO_MS) { trabajo.join() }
        }
    }

    private fun esperarIntercaladas() = runBlocking {
        withTimeout(ESPERA_DEL_INTERCALADO_MS * 10) { intercaladas.forEach { it.await() } }
    }

    private fun conMergeIntercalado(base: CobranzaDelServidor, saldoTrasElMerge: String) =
        object : V2CobranzaApi by base {
            override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
                val foto = base.saldosByIds(zonaId, ids)
                intercalar { mergeVentasCon(CobranzaDelServidor(saldoTrasElMerge)) }
                return foto
            }
        }

    private fun conAbonoAplicadoIntercalado(base: CobranzaDelServidor) =
        object : V2CobranzaApi by base {
            override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
                val foto = base.saldosByIds(zonaId, ids)
                intercalar { aplicarElAbonoEnVuelo() }
                return foto
            }
        }

    /** El 422 de la condonación y el refresco del saldo que dispara. */
    private fun refrescarTrasRechazo(servidor: V2CobranzaApi) {
        correrWorker(ID_COND, rechazoPorSaldo(), servidor)
        esperarIntercaladas()
    }

    /** Corre un `syncNow` REAL del manager, que es quien llama a `mergeVentas`. */
    private suspend fun mergeVentasCon(api: V2CobranzaApi) {
        val outcome = CobranzaSyncManager(
            api = api,
            db = db,
            saleDao = db.saleDao(),
            paymentDao = db.paymentDao(),
            productDao = db.productDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            connectivity = object : ConnectivityMonitor(context) {
                override fun isNetworkAvailable(): Boolean = true
                override val isConnected: Flow<Boolean> = flowOf(true)
            },
            userContextFlow = MutableStateFlow(
                UserContext(zona = ZONA, fechaCargaInicial = null)
            ).asStateFlow(),
            cobranzaWriteMutex = mutexDelSync
        ).syncNow()
        assertTrue("el sync de apoyo no corrió: $outcome", outcome is SyncOutcome.Ok)
    }

    /**
     * El servidor de cobranza: una venta con [saldoDelServidor] por el canal de
     * sync y por `saldos/by-ids`. [sinRed] hace que `by-ids` falle como sin señal.
     */
    private class CobranzaDelServidor(
        private val saldoDelServidor: String,
        private val sinRed: Boolean = false
    ) : V2CobranzaApi {
        val pedidosPorIds = mutableListOf<Pair<Int, String>>()

        fun venta() = VentaDto(
            docto_cc_id = CondonacionFixtures.VENTA,
            docto_pv_id = null,
            cliente_id = 4821,
            zona_cliente_id = ZONA,
            folio = "Y00013662",
            fecha_cargo = "2026-01-01T00:00:00Z",
            fecha_venta = null,
            precio_total = "3500.00",
            total_importe = "3000.00",
            impte_rest = saldoDelServidor,
            saldo = saldoDelServidor,
            num_pagos = 10,
            fecha_ult_pago = null,
            cargo_cancelado = false,
            updated_at = "2026-09-26T21:27:30.000000Z",
            cliente_nombre = "Guadalupe Hernandez Soto",
            limite_credito = null,
            cliente_notas = "",
            cobrador_id = null,
            nombre_cobrador = "Rosa Elena Martinez Vazquez",
            zona_nombre = "Centro",
            calle = "Av. Reforma 100",
            ciudad = "Tehuacan",
            estado = "Puebla",
            telefono = "",
            parcialidad = 350,
            enganche = "500.00",
            tiempo_corto_plazo_meses = null,
            monto_corto_plazo = null,
            precio_de_contado = null,
            aval_o_responsable = "",
            vendedor_1 = "",
            vendedor_2 = "",
            vendedor_3 = "",
            frec_pago = "SEMANAL"
        )

        override suspend fun syncVentas(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncVentasResponse(
            // Respeta el cursor como el servidor: la venta sólo viaja la primera
            // vez; con cursor ya avanzado no hay nada nuevo que entregar.
            items = if (cursor == null) listOf(venta()) else emptyList(),
            max_updated_at = "2026-09-26T21:27:30.000000Z",
            server_now = "2026-09-26T21:27:31Z",
            has_more = false
        )

        override suspend fun syncPagos(
            zonaId: Int,
            cursor: String?,
            afterId: Int,
            limit: Int,
            desde: String?
        ) = SyncPagosResponse(
            items = emptyList(),
            max_updated_at = "2026-09-26T21:27:30.000000Z",
            server_now = "2026-09-26T21:27:31Z",
            has_more = false
        )

        override suspend fun pagosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun saldosDigest(zonaId: Int, desde: String?) =
            DigestResponse(count_activos = 0, ids_xor = "0", ids_sum = "0", max_updated_at = null)

        override suspend fun listPagoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            IdsResponse(ids = emptyList(), has_more = false)

        override suspend fun listSaldoIds(zonaId: Int, after: Int, limit: Int, desde: String?) =
            IdsResponse(ids = emptyList(), has_more = false)

        override suspend fun pagosByIds(zonaId: Int, ids: String): List<PagoDto> = emptyList()

        override suspend fun saldosByIds(zonaId: Int, ids: String): List<VentaDto> {
            pedidosPorIds += zonaId to ids
            if (sinRed) throw IOException("sin señal")
            return listOf(venta())
        }
    }

    /** Los refrescos de saldo que el worker encoló, como (zona, cargo). */
    private val encolados = mutableListOf<Pair<Int, Int>>()

    /**
     * Corre el worker REAL de la subida. Si se le pasa [cobranza] y el worker
     * encoló un refresco de saldo, lo corre después contra ese servidor —lo que
     * haría WorkManager en cuanto haya red—.
     */
    private fun correrWorker(
        id: String,
        api: V2PaymentsApi,
        cobranza: V2CobranzaApi? = null
    ): ListenableWorker.Result {
        val antes = encolados.size
        val worker = construirWorker(id, api)
        val result = runBlocking { worker.doWork() }
        if (cobranza != null && encolados.size > antes) {
            assertEquals(ListenableWorker.Result.success(), correrRefresco(cobranza))
        }
        return result
    }

    /** El worker REAL de la subida, con los seams de la prueba. */
    private fun construirWorker(
        id: String,
        api: V2PaymentsApi,
        imagenes: PaymentImageDao = db.paymentImageDao(),
        almacen: (Context) -> PaymentsLocalDataSource = { PaymentsLocalDataSource(it) }
    ): PendingPaymentsWorker {
        return TestListenableWorkerBuilder<PendingPaymentsWorker>(
            context,
            Data.Builder().putString("payment_id", id).build()
        )
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingPaymentsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    paymentsStore = almacen(appContext),
                    v2Api = api,
                    legacyApi = object : PaymentsApi {
                        override suspend fun savePayment(request: PaymentRequest) {
                            throw AssertionError("el camino legado no se usa en v2")
                        }
                    },
                    useV2 = true,
                    imagenes = imagenes,
                    clock = clock,
                    encolarRefrescoDeSaldo = { zona, cargo -> encolados += zona to cargo }
                )
            })
            .build() as PendingPaymentsWorker
    }

    /** Corre el worker REAL del refresco para el último encolado. */
    private fun correrRefresco(cobranza: V2CobranzaApi): ListenableWorker.Result {
        val (zona, cargo) = encolados.last()
        val worker = TestListenableWorkerBuilder<RefrescarSaldoDeVentaWorker>(
            context,
            Data.Builder()
                .putInt(RefrescarSaldoDeVentaWorker.KEY_ZONA, zona)
                .putInt(RefrescarSaldoDeVentaWorker.KEY_CARGO, cargo)
                .build()
        )
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = RefrescarSaldoDeVentaWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    refresco = RefrescoDelSaldoDeLaVenta(
                        api = cobranza,
                        db = db,
                        cobranzaWriteMutex = mutexDelSync
                    )
                )
            })
            .build()
        return runBlocking { (worker as RefrescarSaldoDeVentaWorker).doWork() }
    }

    private companion object {
        const val ID_COND = "8d7e4a10-2c55-4b1a-9f3e-0a6b5c4d3e21"
        const val ID_ABONO = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
        const val ZONA = 21
        const val ESPERA_DEL_INTERCALADO_MS = 1_000L
    }
}
