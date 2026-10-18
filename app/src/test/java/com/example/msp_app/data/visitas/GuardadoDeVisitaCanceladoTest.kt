package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.di.VisitasPortsModule
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.FaseDelGuardado
import com.example.msp_app.feature.visitas.domain.port.GuardadoCanceladoPort
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-078 sobre Room real: el caso de uso real, el adaptador real y la
 * cancelación real del job (lo que hace el `viewModelScope` al salir de la
 * pantalla). Fija la conducta de HOY —no cambia— y que ahora deja rastro.
 */
class GuardadoDeVisitaCanceladoTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-09-01T15:52:00Z"))
    private val hoy = LocalDate.of(2026, 9, 1)
    private val telemetry = RecordingTelemetry(clock)
    private val avisos = mutableListOf<Triple<FaseDelGuardado, Int, Int>>()
    private val aviso = GuardadoCanceladoPort { f, e, n -> avisos += Triple(f, e, n) }
    private val encoladas = mutableListOf<String>()
    private var alEncolar: () -> Unit = {}
    private var alTraerUsuario: suspend (Int) -> Unit = {}

    private val dosCuentas = CapturaDeVisita(
        resultado = ResultadoDeVisita.SE_NEGO,
        etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
        cuentas = setOf(VENTA_A, VENTA_B)
    )

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
    }

    @After
    fun limpiar() {
        TelemetriaDeVisitas.sinkParaPrueba = null
    }

    private fun casoDeUso(ubicacion: UbicacionPort): RegistrarVisita {
        var usuarios = 0
        return RegistrarVisita(
            RegistroDeVisitaAdapter(
                db = db,
                saleDao = db.saleDao(),
                visitas = VisitsLocalDataSource(
                    db.visitDao(),
                    db.saleDao(),
                    object : VisitsWorkEnqueuer {
                        override fun enqueue(visitId: String) {
                            encoladas += visitId
                            alEncolar()
                        }
                    },
                    clock
                ),
                recomendaciones = db.visitRecommendationDao(),
                imagenes = db.visitImageDao(),
                telemetry = telemetry,
                clock = clock,
                traerUsuario = {
                    alTraerUsuario(++usuarios)
                    User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7)
                }
            ),
            ubicacion,
            telemetry,
            aviso
        )
    }

    private val ubicacionInmediata = object : UbicacionPort {
        override suspend fun ubicacionActual() = UbicacionDeLaVisita(18.4617, -97.3928)
    }

    private fun visitas(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM Visit").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun ventas(): String = db.openHelper.readableDatabase.query(
        "SELECT * FROM sales ORDER BY DOCTO_CC_ACR_ID"
    ).use { c ->
        buildString {
            while (c.moveToNext()) {
                (0 until c.columnCount).joinTo(this, "|") { "${c.getString(it)}" }
                append('\n')
            }
        }
    }

    private fun evento(code: String) = telemetry.recorded.single { it.name == code }

    /**
     * El caso medido en el emulador (9 de 9): Atrás mientras se espera la
     * ubicación. No se escribe NADA —ni `Visit` ni `sales`— igual que hoy, y
     * ahora queda el evento con 0 de 2 en la fase de ubicación.
     */
    @Test
    fun `E-APP-078 cancelada esperando la ubicacion no escribe nada y deja el evento`() = runTest {
        val esperando = CompletableDeferred<Unit>()
        val ubicacionQueEspera = object : UbicacionPort {
            override suspend fun ubicacionActual(): UbicacionDeLaVisita? {
                esperando.complete(Unit)
                awaitCancellation()
            }
        }
        val ventasAntes = ventas()

        val job = launch {
            casoDeUso(ubicacionQueEspera).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
        }
        esperando.await()
        job.cancelAndJoin()

        assertEquals("la conducta de hoy: ninguna visita", 0, visitas())
        assertEquals("sales sin tocar", ventasAntes, ventas())
        assertTrue(encoladas.isEmpty())
        val e = evento(VisitasTelemetria.CODE_VISITA_GUARDADO_CANCELADO)
        assertEquals("0", e.props[VisitasTelemetria.PROP_ESCRITAS])
        assertEquals("2", e.props[VisitasTelemetria.PROP_CUENTAS])
        assertEquals(VisitasTelemetria.FASE_UBICACION, e.props[VisitasTelemetria.PROP_FASE])
        assertEquals(listOf(Triple(FaseDelGuardado.UBICACION, 0, 2)), avisos)
    }

    /**
     * Cancelada al empezar la segunda cuenta (durante la consulta del usuario,
     * antes de su escritura): la primera queda completa y encolada, la segunda
     * no existe, y el parcial dice 1 de 2.
     */
    @Test
    fun `E-APP-078 cancelada entre cuentas deja la primera y reporta el parcial`() = runTest {
        lateinit var job: Job
        alTraerUsuario = { llamada ->
            if (llamada == 2) {
                currentCoroutineContext().job.cancel()
                awaitCancellation()
            }
        }

        job = launch {
            casoDeUso(ubicacionInmediata).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
        }
        job.join()

        assertEquals(1, visitas())
        assertEquals(1, encoladas.size)
        val parcial = evento(VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA)
        assertEquals("1", parcial.props[VisitasTelemetria.PROP_OCURRENCIAS])
        assertEquals("2", parcial.props[VisitasTelemetria.PROP_CUENTAS])
        assertEquals(
            VisitasTelemetria.RESULTADO_CANCELADO,
            parcial.props[VisitasTelemetria.PROP_RESULTADO]
        )
        assertEquals(listOf(Triple(FaseDelGuardado.CUENTAS, 1, 2)), avisos)
    }

    /**
     * Cancelada DENTRO del bloque `NonCancellable` de la primera cuenta (al
     * encolar, la última escritura del bloque): la primera se completa entera,
     * y la cancelación se observa al empezar la segunda. Fija que el conteo k
     * incluye a la cuenta cuyo bloque protegido terminó.
     */
    @Test
    fun `E-APP-078 cancelada dentro del bloque protegido cuenta esa cuenta como escrita`() =
        runTest {
            lateinit var job: Job
            alEncolar = { if (encoladas.size == 1) job.cancel() }

            job = launch {
                casoDeUso(ubicacionInmediata).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
            }
            job.join()

            assertEquals(1, visitas())
            assertEquals(1, encoladas.size)
            assertEquals(listOf(Triple(FaseDelGuardado.CUENTAS, 1, 2)), avisos)
            assertEquals(
                "1",
                evento(
                    VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA
                ).props[VisitasTelemetria.PROP_OCURRENCIAS]
            )
        }

    /**
     * La carrera que hacía intermitente a la prueba anterior (1 de 2855), forzada.
     * `withTransaction` de Room 2.6.1 corre en un `runBlocking` HIJO del bloque
     * `NonCancellable` y reanuda al llamador antes de que ese hijo termine. Si el
     * resto del bloque acaba primero, el bloque espera al hijo y su resultado
     * vuelve por `resumeCancellableWith`: con el job ya cancelado, `withContext`
     * lanzaba la cancelación y la cuenta escrita se reportaba como 0. Con un
     * despachador sin despacho el resto del bloque corre dentro del
     * `runBlocking`, así que el orden malo ocurre siempre.
     */
    @Test
    fun `E-APP-078 cancelada dentro del bloque protegido con la transaccion aun abierta cuenta esa cuenta como escrita`() =
        runTest {
            lateinit var job: Job
            alEncolar = { if (encoladas.size == 1) job.cancel() }

            job = launch(UnconfinedTestDispatcher(testScheduler)) {
                casoDeUso(ubicacionInmediata).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
            }
            job.join()

            assertEquals(1, visitas())
            assertEquals(1, encoladas.size)
            assertEquals(listOf(Triple(FaseDelGuardado.CUENTAS, 1, 2)), avisos)
            assertEquals(
                "1",
                evento(
                    VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA
                ).props[VisitasTelemetria.PROP_OCURRENCIAS]
            )
        }

    /**
     * El cableado real de `:app`: el aviso sale por `TelemetriaDeVisitas` (lo que
     * llega a la oficina), con el evento y las claves de la lista blanca.
     */
    @Test
    fun `E-APP-078 el aviso cableado sale por TelemetriaDeVisitas con solo conteos`() {
        val capturado = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, m -> capturado += e to m }

        VisitasPortsModule.provideGuardadoCanceladoPort().cancelado(FaseDelGuardado.UBICACION, 0, 2)
        VisitasPortsModule.provideGuardadoCanceladoPort().cancelado(FaseDelGuardado.CUENTAS, 1, 3)

        assertEquals(
            listOf(
                "visitas_guardado_cancelado" to mapOf<String, Any>(
                    "visitas.guardado_cancelado.escritas" to 0,
                    "visitas.guardado_cancelado.cuentas" to 2,
                    "visitas.guardado_cancelado.en_ubicacion" to 1
                ),
                "visitas_guardado_cancelado" to mapOf<String, Any>(
                    "visitas.guardado_cancelado.escritas" to 1,
                    "visitas.guardado_cancelado.cuentas" to 3,
                    "visitas.guardado_cancelado.en_ubicacion" to 0
                )
            ),
            capturado
        )
    }

    private fun venta(ventaId: Int) = SaleEntity(
        DOCTO_CC_ACR_ID = ventaId,
        DOCTO_CC_ID = ventaId + 1,
        FOLIO = "V-$ventaId",
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 21,
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
        VENDEDOR_1 = "J. Carlos Méndez",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6310.0,
        IMPTE_REST = 1450.0,
        SALDO_REST = 1450.0,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacán",
        ESTADO = "Puebla",
        TELEFONO = "2381627597",
        NOMBRE_COBRADOR = "Efraín Domínguez Reyes",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "MARTES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "Rosa María Ramírez",
        FREC_PAGO = "SEMANAL"
    )

    private companion object {
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188
    }
}
