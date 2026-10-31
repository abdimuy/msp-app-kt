package com.example.msp_app.core.ubicacion

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.common.location.captura.AccesoAUbicacion
import com.example.msp_app.core.common.location.captura.CapturasEnEspera
import com.example.msp_app.core.common.location.captura.FixDeUbicacion
import com.example.msp_app.core.common.location.captura.OrigenDeUbicacion
import com.example.msp_app.core.common.location.captura.ProveedorDeUbicacion
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.location.captura.TomadorDeUbicacion
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-145 (K1) y E-APP-146 (K5): la ubicacion de una captura al guardar y EN CUANTO el tomador
 * acepta un fix, con simulacion determinista (reloj monotono, reloj de pared y proveedor falsos;
 * tiempo virtual de `runTest`) sobre Room real. t = 0 es el arranque de la prueba.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionDeCapturaTest : RoomTestBase() {

    @Before
    fun preparar() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Room con ejecutores directos: sin hilos reales de por medio, el tiempo virtual de la
        // prueba solo avanza cuando la prueba lo manda.
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        AppDatabase.setInstanceForTesting(db)
    }

    private class Proveedor(var acceso: AccesoAUbicacion = AccesoAUbicacion.OK) : ProveedorDeUbicacion {
        val flujo = MutableSharedFlow<FixDeUbicacion>(extraBufferCapacity = 64)
        val suscripciones = AtomicInteger(0)

        override fun acceso() = acceso

        override suspend fun ultimaConocida(): FixDeUbicacion? = null

        override fun actualizaciones(): Flow<FixDeUbicacion> = callbackFlow {
            suscripciones.incrementAndGet()
            val job = launch { flujo.collect { send(it) } }
            awaitClose {
                job.cancel()
                suscripciones.decrementAndGet()
            }
        }
    }

    private inner class Montaje(
        val prueba: TestScope,
        val proveedor: Proveedor = Proveedor()
    ) {
        val alcance = CoroutineScope(SupervisorJob() + StandardTestDispatcher(prueba.testScheduler))
        val reloj = RelojMonotono { BASE + prueba.currentTime * NANOS_POR_MS }
        val pared = FakeClock(Instant.parse("2026-10-30T18:00:00Z"))
        val reportes = mutableListOf<Pair<OrigenDeUbicacion?, Int?>>()
        val enEspera = CapturasEnEspera()
        lateinit var captura: UbicacionDeCaptura
        val tomador = TomadorDeUbicacion(
            proveedor,
            reloj,
            alRecibirFix = { ahora, fixes -> captura.alRecibir(ahora, fixes) }
        )

        init {
            captura = UbicacionDeCaptura(
                db = db,
                ubicaciones = db.ubicacionDeCapturaDao(),
                tomador = tomador,
                enEspera = enEspera,
                monotono = reloj,
                reloj = pared,
                alcance = alcance,
                aplicarEnLaCaptura = UbicacionDeCaptura.enLasCapturas(db.paymentDao(), db.visitDao()),
                reporte = { origen, precision -> reportes += origen to precision }
            )
        }

        fun fix(
            msDesdeT0: Long,
            lat: Double = LAT,
            lon: Double = LON,
            precision: Double = 10.0,
            mock: Boolean = false
        ) = FixDeUbicacion(
            lat = lat,
            lon = lon,
            precisionM = precision,
            hora = Instant.parse("2026-10-30T18:00:00Z").plusMillis(msDesdeT0),
            elapsedRealtimeNanos = BASE + msDesdeT0 * NANOS_POR_MS,
            esMock = mock
        )

        suspend fun emitir(f: FixDeUbicacion) {
            proveedor.flujo.emit(f)
            prueba.runCurrent()
        }

        suspend fun avanzar(ms: Long) {
            prueba.advanceTimeBy(ms)
            prueba.runCurrent()
        }

        /** Guarda un pago como lo hace el adaptador: el pago y la anotacion, en UNA transaccion. */
        suspend fun guardarPago(id: String = PAGO, lat: Double = 0.0, lng: Double = 0.0): Boolean {
            val momento = captura.alGuardar()
            db.withTransaction {
                db.paymentDao().savePayment(pago(id, lat, lng))
                captura.anotarEnLaTransaccion(id, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
            }
            captura.despuesDeGuardar(id, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
            prueba.runCurrent()
            return momento.antes != null
        }

        suspend fun pago(id: String = PAGO) = checkNotNull(db.paymentDao().getPaymentById(id))
    }

    // ─── K1 · la anotacion dentro de la transaccion ───────────────────────────

    @Test
    fun `E-APP-145 con ubicacion antes se escribe la fila antes y no hay plazo`() = runTest {
        val m = Montaje(this)
        val job = m.tomador.iniciar(m.alcance)
        runCurrent()
        advanceTimeBy(5_000)
        m.emitir(m.fix(5_000))
        advanceTimeBy(1_000)

        assertTrue(m.guardarPago(lat = LAT, lng = LON))

        val fila = checkNotNull(db.ubicacionDeCapturaDao().obtener(PAGO))
        assertEquals(UbicacionDeCapturaEntity.ORIGEN_ANTES, fila.origen)
        assertEquals(10.0, fila.precisionM, 0.0)
        assertEquals(m.fix(5_000).hora.toEpochMilli(), fila.fixAtMs)
        assertNull("con ubicacion el envio no se retiene", db.plazoDeEnvioDao().obtener(PAGO))
        assertEquals(
            listOf<Pair<OrigenDeUbicacion?, Int?>>(OrigenDeUbicacion.ANTES to 10),
            m.reportes
        )
        job.cancel()
    }

    @Test
    fun `E-APP-145 sin ubicacion antes se escribe el plazo en la misma transaccion del pago`() =
        runTest {
            val m = Montaje(this)

            assertFalse(m.guardarPago())

            val plazo = checkNotNull(db.plazoDeEnvioDao().obtener(PAGO))
            assertEquals(m.pared.now().toEpochMilli(), plazo.guardadoEnEpochMs)
            assertEquals(30_000L, plazo.plazoMs)
            assertEquals(UbicacionDeCapturaEntity.TIPO_PAGO, plazo.tipo)
            assertNull(
                "el plazo no usa ubicacion_de_captura",
                db.ubicacionDeCapturaDao().obtener(PAGO)
            )
            assertEquals(1, m.enEspera.pendientes().size)
        }

    @Test
    fun `E-APP-145 si la transaccion del dinero se revierte tampoco queda plazo`() = runTest {
        val m = Montaje(this)
        val momento = m.captura.alGuardar()
        runCatching {
            db.withTransaction {
                db.paymentDao().savePayment(pago(PAGO, 0.0, 0.0))
                m.captura.anotarEnLaTransaccion(PAGO, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
                error("el saldo no alcanzo")
            }
        }
        assertNull(db.paymentDao().getPaymentById(PAGO))
        assertNull(db.plazoDeEnvioDao().obtener(PAGO))
    }

    @Test
    fun `E-APP-145 un fallo al escribir el plazo no revierte ni impide el pago`() = runTest {
        // Control: sin el fallo, el plazo SI se escribe (ver la prueba de arriba). Aqui la tabla
        // no existe, asi que el insert del plazo lanza de verdad dentro de la transaccion.
        db.openHelper.writableDatabase.execSQL("DROP TABLE plazo_de_envio")
        val m = Montaje(this)

        m.guardarPago()

        assertNotNull("el pago se guardo", db.paymentDao().getPaymentById(PAGO))
        assertEquals("el pago quedo pendiente de envio", false, m.pago().GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `E-APP-145 un fallo al escribir la fila de ubicacion antes tampoco revierte el pago`() =
        runTest {
            db.openHelper.writableDatabase.execSQL("DROP TABLE ubicacion_de_captura")
            val m = Montaje(this)
            val job = m.tomador.iniciar(m.alcance)
            runCurrent()
            advanceTimeBy(5_000)
            m.emitir(m.fix(5_000))
            advanceTimeBy(1_000)

            assertTrue("hay ubicacion antes", m.guardarPago(lat = LAT, lng = LON))

            assertEquals("el pago se guardo con sus coordenadas", LAT, m.pago().LAT!!, 0.0)
            job.cancel()
        }

    @Test
    fun `E-APP-145 anotar el plazo dos veces no lanza ni lo reescribe`() = runTest {
        val m = Montaje(this)
        m.guardarPago()
        m.pared.setNow(Instant.parse("2026-10-30T18:00:10Z"))
        val momento = m.captura.alGuardar()
        db.withTransaction {
            m.captura.anotarEnLaTransaccion(PAGO, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
        }
        assertEquals(
            Instant.parse("2026-10-30T18:00:00Z").toEpochMilli(),
            db.plazoDeEnvioDao().obtener(PAGO)!!.guardadoEnEpochMs
        )
    }

    // ─── K5 · el tomador escribe EN CUANTO acepta un fix ──────────────────────

    @Test
    fun `E-APP-146 el fix valido se escribe en el pago en cuanto llega, no al cerrar la ventana`() =
        runTest {
            val m = Montaje(this)
            m.guardarPago()

            advanceTimeBy(5_000)
            val f = m.fix(5_000)
            val llegada = currentTime
            m.emitir(f)

            // A los 5 s, con la ventana de 30 s abierta: ya esta en Room, con lat y lng juntos.
            assertEquals(5_000L, llegada)
            assertEquals(LAT, m.pago().LAT!!, 0.0)
            assertEquals(LON, m.pago().LNG!!, 0.0)
            val fila = checkNotNull(db.ubicacionDeCapturaDao().obtener(PAGO))
            assertEquals(UbicacionDeCapturaEntity.ORIGEN_DESPUES, fila.origen)
            assertEquals(10.0, fila.precisionM, 0.0)
            assertEquals(f.hora.toEpochMilli(), fila.fixAtMs)
            assertEquals(
                listOf<Pair<OrigenDeUbicacion?, Int?>>(OrigenDeUbicacion.DESPUES to 10),
                m.reportes
            )
            assertEquals("sale de la lista", 0, m.enEspera.pendientes().size)
        }

    /**
     * E-APP-152 (H3 de la compuerta DESPUES; mata D17): un fix que llega ENTRE `alGuardar` y
     * `despuesDeGuardar` encuentra la lista de espera vacia (la captura aun no se registra). Solo la
     * reevaluacion inmediata de `despuesDeGuardar` lo aplica; sin ella el pago esperaria al
     * siguiente fix, que quiza nunca llegue.
     */
    @Test
    fun `E-APP-152 un fix que llega entre alGuardar y despuesDeGuardar se aplica sin esperar otro`() =
        runTest {
            val m = Montaje(this)
            val job = m.tomador.iniciar(m.alcance)
            runCurrent()
            val momento = m.captura.alGuardar()
            assertNull("sin ubicacion previa", momento.antes)

            advanceTimeBy(5_000)
            m.emitir(m.fix(5_000)) // la captura aun no esta en la lista de espera
            assertEquals(0, m.enEspera.pendientes().size)

            db.withTransaction {
                db.paymentDao().savePayment(pago(PAGO, 0.0, 0.0))
                m.captura.anotarEnLaTransaccion(PAGO, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
            }
            m.captura.despuesDeGuardar(PAGO, UbicacionDeCapturaEntity.TIPO_PAGO, momento)
            runCurrent() // sin ningun fix nuevo

            assertEquals(LAT, m.pago().LAT!!, 0.0)
            assertEquals(LON, m.pago().LNG!!, 0.0)
            assertEquals(0, m.enEspera.pendientes().size)
            job.cancel()
        }

    @Test
    fun `E-APP-146 la toma sigue viva aunque la pantalla se haya ido`() = runTest {
        val m = Montaje(this)
        val pantalla = m.tomador.iniciar(m.alcance)
        runCurrent()
        m.guardarPago()
        pantalla.cancel() // el cobrador guardo y salio de la pantalla
        runCurrent()
        assertEquals("la extension de proceso escucha", 1, m.proveedor.suscripciones.get())

        advanceTimeBy(12_000)
        m.emitir(m.fix(12_000))

        assertEquals(LAT, m.pago().LAT!!, 0.0)
    }

    @Test
    fun `E-APP-146 un fix posterior a la ventana de 30 s no se escribe`() = runTest {
        val m = Montaje(this)
        m.guardarPago()
        advanceTimeBy(31_000)
        m.emitir(m.fix(31_000))
        assertEquals(0.0, m.pago().LAT!!, 0.0)
        assertNull(db.ubicacionDeCapturaDao().obtener(PAGO))
    }

    @Test
    fun `E-APP-146 nunca llega ningun fix y el pago queda en 0,0 sin trabajo colgado`() = runTest {
        val m = Montaje(this)
        m.guardarPago()
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(0.0, m.pago().LAT!!, 0.0)
        assertEquals(0.0, m.pago().LNG!!, 0.0)
        assertEquals("la extension termino sola", 0, m.proveedor.suscripciones.get())
    }

    @Test
    fun `E-APP-146 no se sobrescribe una ubicacion que el pago ya tenia`() = runTest {
        val m = Montaje(this)
        m.guardarPago(lat = 19.0, lng = -99.0) // llego del servidor / de antes por otro camino
        advanceTimeBy(5_000)
        m.emitir(m.fix(5_000))
        assertEquals(19.0, m.pago().LAT!!, 0.0)
        assertEquals(-99.0, m.pago().LNG!!, 0.0)
        assertNull("sin escritura no hay fila", db.ubicacionDeCapturaDao().obtener(PAGO))
    }

    @Test
    fun `E-APP-146 un fix simulado o impreciso no se escribe`() = runTest {
        val m = Montaje(this)
        m.guardarPago()
        advanceTimeBy(2_000)
        m.emitir(m.fix(2_000, mock = true))
        m.emitir(m.fix(2_000, precision = 80.0))
        assertEquals(0.0, m.pago().LAT!!, 0.0)
        assertEquals("sigue esperando", 1, m.enEspera.pendientes().size)

        advanceTimeBy(3_000)
        m.emitir(m.fix(5_000))
        assertEquals("el primero bueno si", LAT, m.pago().LAT!!, 0.0)
    }

    @Test
    fun `E-APP-146 con movimiento desde el ultimo fix previo al guardado no se escribe ni se reintenta`() =
        runTest {
            val m = Montaje(this)
            val pantalla = m.tomador.iniciar(m.alcance)
            runCurrent()
            advanceTimeBy(2_000)
            // Impreciso (no sirve como ANTES) pero es la referencia: el cobrador estaba AQUI.
            m.emitir(m.fix(2_000, precision = 40.0))
            advanceTimeBy(1_000)
            m.guardarPago()

            // Un fix bueno a ~220 m de la referencia: el telefono se movio, no se escribe.
            advanceTimeBy(3_000)
            m.emitir(m.fix(6_000, lat = LAT + 0.002))
            assertEquals(0.0, m.pago().LAT!!, 0.0)
            assertEquals("la captura se descarta", 0, m.enEspera.pendientes().size)

            // Y un fix posterior ya quieto tampoco la revive.
            advanceTimeBy(3_000)
            m.emitir(m.fix(9_000, lat = LAT + 0.002))
            assertEquals(0.0, m.pago().LAT!!, 0.0)
            pantalla.cancel()
        }

    @Test
    fun `E-APP-146 una captura que se guardo sin cambio de estado de envio puede recibir fix tras el POST`() =
        runTest {
            val m = Montaje(this)
            m.guardarPago()
            // El envio ya salio: el pago esta soltado (GUARDADO_EN_MICROSIP = 1).
            db.paymentDao().updateEstado(PAGO, 1)
            advanceTimeBy(8_000)
            m.emitir(m.fix(8_000))

            // La ubicacion queda local; el estado de envio no se toca y nada se vuelve a encolar.
            assertEquals(LAT, m.pago().LAT!!, 0.0)
            assertTrue(m.pago().GUARDADO_EN_MICROSIP)
        }

    @Test
    fun `E-APP-146 si escribir la ubicacion lanza, la toma sigue y el pago no cambia`() = runTest {
        val m = Montaje(this)
        m.guardarPago()
        db.openHelper.writableDatabase.execSQL("DROP TABLE ubicacion_de_captura")
        advanceTimeBy(5_000)
        m.emitir(m.fix(5_000))
        assertEquals("la transaccion se revirtio entera", 0.0, m.pago().LAT!!, 0.0)
        assertEquals(listOf<Pair<OrigenDeUbicacion?, Int?>>(null to null), m.reportes)
    }

    // ─── E-APP-148 (K6): un lote de visitas, un solo fix ──────────────────────

    private fun visita(id: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 77,
        FECHA = "2026-10-30T18:00:00Z",
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552
    )

    @Test
    fun `E-APP-148 un fix tardio ubica a todas las cuentas del lote, una sola vez cada una`() =
        runTest {
            val m = Montaje(this)
            val momento = m.captura.alGuardar()
            listOf("v-1", "v-2").forEach { id ->
                db.withTransaction {
                    db.visitDao().insertVisit(visita(id))
                    m.captura.anotarEnLaTransaccion(
                        id,
                        UbicacionDeCapturaEntity.TIPO_VISITA,
                        momento
                    )
                }
            }
            listOf("v-1", "v-2").forEach {
                m.captura.despuesDeGuardar(it, UbicacionDeCapturaEntity.TIPO_VISITA, momento)
            }
            runCurrent()

            advanceTimeBy(9_000)
            m.emitir(m.fix(9_000))

            listOf("v-1", "v-2").forEach { id ->
                val v = db.visitDao().getVisitById(id)
                assertEquals(LAT, v.LAT, 0.0)
                assertEquals(LON, v.LNG, 0.0)
                assertEquals(
                    UbicacionDeCapturaEntity.ORIGEN_DESPUES,
                    db.ubicacionDeCapturaDao().obtener(id)!!.origen
                )
                assertEquals(
                    UbicacionDeCapturaEntity.TIPO_VISITA,
                    db.plazoDeEnvioDao().obtener(id)!!.tipo
                )
            }

            // Un segundo fix, otro lugar: no pisa nada.
            advanceTimeBy(2_000)
            m.emitir(m.fix(11_000, lat = LAT + 0.0001))
            assertEquals(LAT, db.visitDao().getVisitById("v-1").LAT, 0.0)
        }

    private fun pago(id: String, lat: Double, lng: Double) = PaymentEntity(
        ID = id,
        COBRADOR = "Mendoza Torres, Ana",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-30T18:00:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 220.0,
        LAT = lat,
        LNG = lng,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21552,
        NOMBRE_CLIENTE = "Lopez Garcia, Minerva"
    )

    private companion object {
        const val PAGO = "pago-uuid-001"
        const val LAT = 20.5888
        const val LON = -100.3899
        const val BASE = 5_000_000_000_000L
        const val NANOS_POR_MS = 1_000_000L
    }
}
