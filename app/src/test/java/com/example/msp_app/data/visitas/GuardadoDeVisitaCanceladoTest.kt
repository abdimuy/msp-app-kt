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
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertSame
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
     * E-APP-127 (decisión del coordinador 2026-10-27; medición MB de la compuerta
     * DESPUÉS): Atrás mientras se espera la ubicación ya NO pierde la visita. El
     * caso medido en el emulador (E-APP-078, 9 de 9) perdía las dos cuentas
     * porque la ubicación iba antes de escribir. Ahora las dos quedan escritas,
     * encoladas y sin coordenadas, y el evento dice 2 de 2 en la fase de
     * ubicación.
     */
    @Test
    fun `E-APP-127 cancelada esperando la ubicacion la visita ya quedo escrita y encolada`() =
        runTest {
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

            assertEquals("las dos cuentas quedaron", 2, visitas())
            assertTrue("y movieron su estado de cobranza", ventasAntes != ventas())
            assertEquals(2, encoladas.size)
            assertEquals("sin coordenadas: 0,0", listOf("0.0,0.0", "0.0,0.0"), coordenadas())
            val e = evento(VisitasTelemetria.CODE_VISITA_GUARDADO_CANCELADO)
            assertEquals("2", e.props[VisitasTelemetria.PROP_ESCRITAS])
            assertEquals("2", e.props[VisitasTelemetria.PROP_CUENTAS])
            assertEquals(VisitasTelemetria.FASE_UBICACION, e.props[VisitasTelemetria.PROP_FASE])
            assertEquals(listOf(Triple(FaseDelGuardado.UBICACION, 2, 2)), avisos)
        }

    /**
     * E-APP-127: la ubicación que llega después del guardado se parcha sobre las
     * visitas ya escritas. Sólo LAT/LNG: ni reabre (GUARDADO sigue en 0, no se
     * duplica ninguna fila) ni re-encola (el envío no la esperó).
     */
    @Test
    fun `E-APP-127 la ubicacion que llega despues se parcha sin reabrir ni duplicar ni reencolar`() =
        runTest {
            val pedida = CompletableDeferred<Unit>()
            val suelta = CompletableDeferred<Unit>()
            val ubicacionTardia = object : UbicacionPort {
                override suspend fun ubicacionActual(): UbicacionDeLaVisita {
                    pedida.complete(Unit)
                    suelta.await()
                    return UbicacionDeLaVisita(18.4617, -97.3928)
                }
            }
            val job = launch {
                casoDeUso(ubicacionTardia).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
            }
            pedida.await()
            assertEquals("escrita y encolada antes de tener ubicación", 2, encoladas.size)
            assertEquals(listOf("0.0,0.0", "0.0,0.0"), coordenadas())

            suelta.complete(Unit)
            job.join()

            assertEquals(listOf("18.4617,-97.3928", "18.4617,-97.3928"), coordenadas())
            assertEquals("no duplica", 2, visitas())
            assertEquals("no re-encola", 2, encoladas.size)
            assertEquals(
                "no reabre",
                0,
                db.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM Visit WHERE GUARDADO_EN_MICROSIP <> 0"
                ).use {
                    it.moveToFirst()
                    it.getInt(0)
                }
            )
        }

    private fun coordenadas(): List<String> =
        db.openHelper.readableDatabase.query("SELECT LAT, LNG FROM Visit ORDER BY ID").use { c ->
            buildList { while (c.moveToNext()) add("${c.getDouble(0)},${c.getDouble(1)}") }
        }

    /**
     * E-APP-087 (decisión 3 del dueño 2026-10-20, todo o nada): cancelada en la
     * consulta del cobrador —antes de la transacción— no queda NINGUNA cuenta,
     * ni encolada, y el aviso dice 0 de 2. Antes quedaba la primera (1 de 2).
     */
    @Test
    fun `E-APP-087 cancelada esperando al cobrador no deja ninguna cuenta`() = runTest {
        lateinit var job: Job
        val ventasAntes = ventas()
        alTraerUsuario = {
            currentCoroutineContext().job.cancel()
            awaitCancellation()
        }

        job = launch {
            casoDeUso(ubicacionInmediata).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
        }
        job.join()

        assertEquals(0, visitas())
        assertEquals("sales sin tocar", ventasAntes, ventas())
        assertTrue(encoladas.isEmpty())
        assertEquals(listOf(Triple(FaseDelGuardado.CUENTAS, 0, 2)), avisos)
    }

    /**
     * E-APP-087: cancelada al encolar la primera (post-commit): las DOS cuentas
     * ya están escritas en la misma transacción y las dos se encolan; el bloque
     * protegido terminó, así que no hay cancelación tardía que reportar.
     */
    @Test
    fun `E-APP-087 cancelada al encolar deja las dos cuentas escritas y encoladas`() = runTest {
        lateinit var job: Job
        alEncolar = { if (encoladas.size == 1) job.cancel() }

        job = launch {
            casoDeUso(ubicacionInmediata).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
        }
        job.join()

        assertEquals(2, visitas())
        assertEquals(2, encoladas.size)
        assertTrue(
            "ya no existe el parcial k de N",
            telemetry.recorded.none { it.name == VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA }
        )
    }

    /**
     * La carrera de E-APP-078 forzada con `UnconfinedTestDispatcher`, sobre el
     * guardado todo o nada (E-APP-087): las dos cuentas quedan y se encolan, y
     * la cancelación tardía no convierte lo escrito en "no se guardó".
     */
    @Test
    fun `E-APP-087 con la carrera de E-APP-078 forzada las dos cuentas quedan y se encolan`() =
        runTest {
            lateinit var job: Job
            alEncolar = { if (encoladas.size == 1) job.cancel() }

            job = launch(UnconfinedTestDispatcher(testScheduler)) {
                casoDeUso(ubicacionInmediata).invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
            }
            job.join()

            assertEquals(2, visitas())
            assertEquals(2, encoladas.size)
            assertTrue(avisos.isEmpty())
        }

    /**
     * Una cancelación lanzada DENTRO del bloque post-commit (al encolar) no es
     * tardía: sale la misma instancia (E-APP-078). Las dos cuentas ya están en
     * la base —la transacción commiteó— y un reintento no las reescribe porque
     * el marcador quedó ESCRITA (E-APP-087).
     */
    @Test
    fun `E-APP-078 cancelacion lanzada dentro del bloque antes de terminar se propaga`() = runTest {
        val propia = CancellationException("cancelada al encolar")
        alEncolar = { if (encoladas.size == 1) throw propia }
        var recibida: Throwable? = null

        val job = launch {
            val caso = casoDeUso(ubicacionInmediata)
            try {
                caso.invoke("visita-1", CLIENTE_ID, null, dosCuentas, hoy)
            } catch (e: CancellationException) {
                recibida = e
                throw e
            }
        }
        job.join()

        // La recuperación de stack traces de kotlinx-coroutines (modo debug de
        // las pruebas) entrega una copia cuya causa es la original.
        assertSame(
            "la cancelacion del bloque sale de invoke",
            propia,
            recibida.takeIf { it === propia } ?: recibida?.cause
        )
        assertEquals("todo o nada: la transaccion ya commiteo las dos", 2, visitas())
        assertEquals(1, encoladas.size)
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
