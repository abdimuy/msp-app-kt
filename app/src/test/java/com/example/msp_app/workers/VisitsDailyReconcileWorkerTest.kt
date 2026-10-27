package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionDeCompromisos
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomDeliveredVisitsStore
import com.example.msp_app.core.sync.visitas.MotivoOmitida
import com.example.msp_app.core.sync.visitas.ResultadoConciliacionDeAgenda
import com.example.msp_app.core.sync.visitas.ResultadoInventario
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reconciliación inversa diaria de punta a punta (plan visitas P1, Tarea 13;
 * Review Focus 2 y 3; E-VIS-009): Room real + use case real + `by-ids`
 * falso. Lo que el servidor no tiene vuelve a pendiente y se reencola; lo que
 * nombra se confirma; una tanda que falla no toca nada.
 */
class VisitsDailyReconcileWorkerTest : RoomTestBase() {

    private companion object {
        // UUID canónicos: el use case no le pregunta al servidor por otra forma.
        const val LEGADA_SI = "6f1c2a3b-0000-4000-8000-000000000001"
        const val LEGADA_NO = "6f1c2a3b-0000-4000-8000-000000000002"
        const val LEGADA = "6f1c2a3b-0000-4000-8000-000000000003"
        const val MIA = "6f1c2a3b-0000-4000-8000-000000000004"
        const val INTRUSA = "6f1c2a3b-0000-4000-8000-000000000005"
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val ahora = Instant.parse("2026-10-17T15:00:00Z")

    private class Servidor(
        val tiene: Set<String> = emptySet(),
        val falla: Boolean = false,
        val ajenos: List<String> = emptyList()
    ) : VisitCustodyRegistry {
        val preguntas = mutableListOf<List<String>>()
        override suspend fun findExisting(visitIds: List<String>): List<String> {
            preguntas += visitIds
            if (falla) throw java.io.IOException("sin red")
            return visitIds.filter { it in tiene } + ajenos
        }
    }

    private val sinReporte = object : SyncErrorReporter {
        override fun report(code: String, message: String, props: Map<String, String>) = Unit
    }

    private fun casoDeUso(servidor: Servidor) = ReconcileDeliveredVisitsUseCase(
        store = RoomDeliveredVisitsStore(db.visitDao(), db.visitImageDao()),
        registry = servidor,
        errorReporter = sinReporte,
        clock = { ahora }
    )

    private class Registro {
        val encoladas = mutableListOf<String>()
        val corridas = mutableListOf<Instant>()
        val eventos = mutableListOf<Pair<String, Map<String, Any>>>()
    }

    private fun construir(
        registro: Registro,
        modo: ModoEnvioVisitas = ModoEnvioVisitas.V3,
        useV2: Boolean = true,
        inventariar: suspend () -> ResultadoInventario? = { null },
        conciliarAgenda: suspend () -> ResultadoConciliacionDeAgenda? = { null },
        conciliarCompromisos: suspend () -> ConciliacionDeCompromisos? = { null },
        conciliar: suspend () -> ConciliacionInversa
    ): VisitsDailyReconcileWorker = TestListenableWorkerBuilder<VisitsDailyReconcileWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = VisitsDailyReconcileWorker(
                appContext,
                workerParameters,
                modo = { modo },
                useV2 = useV2,
                conciliar = conciliar,
                pendientes = { db.visitDao().pendientesOrdenadas() },
                encolar = { registro.encoladas += it },
                anotarCorrida = { registro.corridas += it },
                clock = FakeClock(ahora),
                telemetria = { e, d -> registro.eventos += e to d },
                inventariar = inventariar,
                conciliarAgenda = conciliarAgenda,
                conciliarCompromisos = conciliarCompromisos
            )
        })
        .build()

    /**
     * E-VIS-031 con `VISITAS_USE_V2 = false` (flavor devserver): el envío legacy
     * nunca estampa `SUBIDA_EN`, así que si el barrido devolviera una legada
     * con foto, el worker legacy la reenviaría sin la foto y la siguiente
     * corrida la devolvería otra vez, para siempre. Ni el barrido ni la
     * inversa corren; la marca de corrida tampoco se anota.
     */
    @Test
    fun `E-VIS-031 sin VISITAS_USE_V2 ni el barrido ni la inversa corren`() = runBlocking {
        val dao = db.visitDao()
        dao.insertVisit(pendingVisit(id = LEGADA, guardado = 1))
        var corridas = 0
        val conciliar: suspend () -> ConciliacionInversa = {
            corridas++
            casoDeUso(Servidor()).execute()
        }

        val apagado = Registro()
        assertEquals(
            ListenableWorker.Result.success(),
            construir(apagado, useV2 = false, conciliar = conciliar).doWork()
        )
        assertEquals("no corre", 0, corridas)
        assertEquals(1, dao.findVisitById(LEGADA)!!.GUARDADO_EN_MICROSIP)
        assertEquals(emptyList<String>(), apagado.encoladas)
        assertEquals(emptyList<Instant>(), apagado.corridas)

        // Control positivo: con v2 la misma legada sí se revisa (y vuelve a pendiente).
        val prendido = Registro()
        construir(prendido, useV2 = true, conciliar = conciliar).doWork()
        assertEquals(1, corridas)
        assertEquals(0, dao.findVisitById(LEGADA)!!.GUARDADO_EN_MICROSIP)
    }

    @Test
    fun inversaDevuelveAPendienteLaLegadaQueElServidorNoTiene() = runBlocking {
        val dao = db.visitDao()
        dao.insertVisit(pendingVisit(id = LEGADA_SI, guardado = 1))
        dao.insertVisit(pendingVisit(id = LEGADA_NO, guardado = 1))
        val registro = Registro()

        val r = construir(
            registro
        ) { casoDeUso(Servidor(tiene = setOf(LEGADA_SI))).execute() }.doWork()

        assertEquals(ListenableWorker.Result.success(), r)
        val si = dao.findVisitById(LEGADA_SI)!!
        assertEquals(1, si.GUARDADO_EN_MICROSIP)
        assertEquals("2026-10-17T15:00:00Z", si.CONFIRMADA_EN)
        val no = dao.findVisitById(LEGADA_NO)!!
        assertEquals(0, no.GUARDADO_EN_MICROSIP)
        assertNull(no.CONFIRMADA_EN)
        assertEquals(listOf(LEGADA_NO), registro.encoladas)
        assertEquals(listOf(ahora), registro.corridas)
    }

    @Test
    fun tandaFallidaNoDevuelveNiConfirma() = runBlocking {
        val dao = db.visitDao()
        dao.insertVisit(pendingVisit(id = LEGADA, guardado = 1))
        val registro = Registro()
        val servidor = Servidor(falla = true)

        construir(registro) { casoDeUso(servidor).execute() }.doWork()

        assertEquals(1, servidor.preguntas.size) // control: sí se preguntó
        val v = dao.findVisitById(LEGADA)!!
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
        assertNull(v.CONFIRMADA_EN)
        assertEquals(emptyList<String>(), registro.encoladas)
        // Una corrida a medias no se anota: la siguiente apertura la reintenta.
        assertEquals(emptyList<Instant>(), registro.corridas)
    }

    @Test
    fun idAjenoNoSeConfirma() = runBlocking {
        val dao = db.visitDao()
        dao.insertVisit(pendingVisit(id = MIA, guardado = 1))
        // Otra visita del teléfono que NO estaba en la tanda (pendiente): el
        // servidor la nombra por su cuenta y no debe confirmarse.
        dao.insertVisit(pendingVisit(id = INTRUSA))
        val registro = Registro()

        construir(registro) {
            casoDeUso(Servidor(tiene = setOf(MIA), ajenos = listOf(INTRUSA))).execute()
        }.doWork()

        assertNotNull(dao.findVisitById(MIA)!!.CONFIRMADA_EN)
        val intrusa = dao.findVisitById(INTRUSA)!!
        assertEquals(0, intrusa.GUARDADO_EN_MICROSIP)
        assertNull(intrusa.CONFIRMADA_EN)
    }

    @Test
    fun `en modo v2_actual no corre`() = runBlocking {
        val registro = Registro()
        var llamadas = 0

        val r = construir(registro, modo = ModoEnvioVisitas.V2_ACTUAL) {
            llamadas++
            ConciliacionInversa(1, 1, 0, 0)
        }.doWork()

        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(0, llamadas)
        assertEquals(emptyList<String>(), registro.encoladas)
        assertEquals(emptyList<Instant>(), registro.corridas)
    }

    @Test
    fun `sin faltantes no reencola y anota la corrida`() = runBlocking {
        db.visitDao().insertVisit(pendingVisit(id = "pendiente-ajena-a-la-corrida"))
        val registro = Registro()

        construir(registro) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

        assertEquals(emptyList<String>(), registro.encoladas)
        assertEquals(listOf(ahora), registro.corridas)
    }

    /** E-APP-079: la devuelta por una foto sin enviar sale hoy, no en la próxima pasada de 15 min. */
    @Test
    fun `E-APP-079 devueltas por foto reencolan las pendientes`() = runBlocking {
        db.visitDao().insertVisit(pendingVisit(id = "devuelta-por-foto"))
        val registro = Registro()

        construir(registro) { ConciliacionInversa(3, 0, 2, 0, devueltasPorFoto = 1) }.doWork()

        assertEquals(listOf("devuelta-por-foto"), registro.encoladas)
        assertEquals(listOf(ahora), registro.corridas)
    }

    @Test
    fun `lectura ilegible no anota la corrida`() = runBlocking {
        val registro = Registro()

        construir(registro) { ConciliacionInversa(0, 0, 0, 0, entregadasIlegibles = true) }.doWork()

        assertEquals(emptyList<Instant>(), registro.corridas)
    }

    @Test
    fun `un escape inesperado devuelve success sin anotar`() = runBlocking {
        val registro = Registro()

        val r = construir(registro) { throw IllegalStateException("grafo") }.doWork()

        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(emptyList<Instant>(), registro.corridas)
    }

    // ─── P2 Tarea 18: inventario de la agenda (E-APP-109) ──────────────────

    @Test
    fun `E-APP-109 la corrida diaria hace el inventario y reporta solo conteos`() = runBlocking {
        val registro = Registro()
        var inventarios = 0

        construir(registro, inventariar = {
            inventarios++
            ResultadoInventario.Reconciliado(revisadas = 40, rescatadas = 2, borradas = 1)
        }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

        assertEquals(1, inventarios)
        assertEquals(
            mapOf<String, Any>(
                "visitas.inventario.revisadas" to 40,
                "visitas.inventario.rescatadas" to 2,
                "visitas.inventario.borradas" to 1,
                "visitas.inventario.fallido" to 0
            ),
            registro.eventos.single { it.first == TelemetriaDeVisitas.EVENTO_INVENTARIO }.second
        )
    }

    /** El inventario tiene el interruptor de la bajada, no el del envío. */
    @Test
    fun `E-APP-109 en modo v2_actual el inventario si corre`() = runBlocking {
        val registro = Registro()
        var inventarios = 0
        var inversas = 0

        construir(registro, modo = ModoEnvioVisitas.V2_ACTUAL, inventariar = {
            inventarios++
            ResultadoInventario.Igual(revisadas = 5)
        }) {
            inversas++
            ConciliacionInversa(0, 0, 0, 0)
        }.doWork()

        assertEquals(0, inversas)
        assertEquals(1, inventarios)
    }

    @Test
    fun `E-APP-109 un inventario que revienta no tumba la corrida ni la inversa`() = runBlocking {
        val registro = Registro()

        val r = construir(registro, inventariar = { throw IllegalStateException("grafo") }) {
            ConciliacionInversa(3, 0, 3, 0)
        }.doWork()

        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(listOf(ahora), registro.corridas)
        assertEquals(
            emptyList<String>(),
            registro.eventos.map { it.first }.filter { it == TelemetriaDeVisitas.EVENTO_INVENTARIO }
        )
    }

    @Test
    fun `E-APP-109 inventario fallido se reporta como fallido`() = runBlocking {
        val registro = Registro()

        construir(registro, inventariar = { ResultadoInventario.Fallido(7, "ids incompletos") }) {
            ConciliacionInversa(0, 0, 0, 0)
        }.doWork()

        assertEquals(
            1,
            registro.eventos.single { it.first == TelemetriaDeVisitas.EVENTO_INVENTARIO }
                .second["visitas.inventario.fallido"]
        )
    }

    // ─── P2 Tarea 18: conciliación directa de la agenda (E-APP-131) ────────

    @Test
    fun `E-APP-131 la corrida diaria concilia la agenda y reporta solo conteos`() = runBlocking {
        val registro = Registro()
        var llamadas = 0

        construir(registro, conciliarAgenda = {
            llamadas++
            ResultadoConciliacionDeAgenda.Ok(
                items = 12,
                diferencias = 2,
                ventasConDiferencia = listOf(100, 200)
            )
        }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

        assertEquals(1, llamadas)
        assertEquals(
            mapOf<String, Any>(
                "visitas.conciliacion.agenda_fallida" to 0,
                "visitas.conciliacion.agenda_omitida" to 0,
                "visitas.conciliacion.agenda_items" to 12,
                "visitas.conciliacion.diferencias_agenda" to 2
            ),
            registro.eventos.single { it.first == TelemetriaDeVisitas.EVENTO_CONCILIACION_AGENDA }.second
        )
        assertEquals(listOf(ahora), registro.corridas)
    }

    /** Una falla no es un cero: sin la clave de diferencias, y la corrida no se anota (reintento propio). */
    @Test
    fun `E-APP-131 una conciliacion fallida no reporta diferencias ni anota la corrida`() =
        runBlocking {
            val registro = Registro()

            val r = construir(registro, conciliarAgenda = {
                ResultadoConciliacionDeAgenda.Fallida("http", 500)
            }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

            assertEquals(ListenableWorker.Result.success(), r)
            val datos = registro.eventos.single {
                it.first == TelemetriaDeVisitas.EVENTO_CONCILIACION_AGENDA
            }.second
            assertEquals(1, datos["visitas.conciliacion.agenda_fallida"])
            assertEquals(null, datos["visitas.conciliacion.diferencias_agenda"])
            assertEquals(emptyList<Instant>(), registro.corridas)
        }

    @Test
    fun `E-APP-131 una conciliacion omitida a proposito no impide anotar la corrida`() =
        runBlocking {
            val registro = Registro()

            construir(registro, conciliarAgenda = {
                ResultadoConciliacionDeAgenda.Omitida(MotivoOmitida.SERVIDOR_SIN_LA_RUTA)
            }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

            val datos = registro.eventos.single {
                it.first == TelemetriaDeVisitas.EVENTO_CONCILIACION_AGENDA
            }.second
            assertEquals(1, datos["visitas.conciliacion.agenda_omitida"])
            assertEquals(null, datos["visitas.conciliacion.diferencias_agenda"])
            assertEquals(listOf(ahora), registro.corridas)
        }

    /** Como el inventario, la conciliación de la agenda se rige por el interruptor de la bajada, no por el del envío. */
    @Test
    fun `E-APP-131 en modo v2_actual la conciliacion de la agenda si corre`() = runBlocking {
        val registro = Registro()
        var agenda = 0
        var inversas = 0

        construir(registro, modo = ModoEnvioVisitas.V2_ACTUAL, conciliarAgenda = {
            agenda++
            ResultadoConciliacionDeAgenda.Ok(0, 0, emptyList())
        }) {
            inversas++
            ConciliacionInversa(0, 0, 0, 0)
        }.doWork()

        assertEquals(0, inversas)
        assertEquals(1, agenda)
    }

    @Test
    fun `E-APP-131 una conciliacion de la agenda que revienta no tumba la corrida ni la anota`() =
        runBlocking {
            val registro = Registro()

            val r = construir(
                registro,
                conciliarAgenda = { throw IllegalStateException("grafo") }
            ) {
                ConciliacionInversa(3, 0, 3, 0)
            }.doWork()

            assertEquals(ListenableWorker.Result.success(), r)
            assertEquals(emptyList<Instant>(), registro.corridas)
        }

    // ─── P2 Tarea 18: conciliación inversa del compromiso (E-APP-132) ──────

    @Test
    fun `E-APP-132 las incompletas devueltas reencolan las pendientes y reportan conteos`() =
        runBlocking {
            db.visitDao().insertVisit(pendingVisit(id = "pendiente-a-reenviar"))
            val registro = Registro()

            construir(registro, conciliarCompromisos = {
                ConciliacionDeCompromisos(
                    revisadas = 5,
                    incompletas = 3,
                    porRellenar = 2,
                    distintas = 1,
                    devueltas = 2,
                    tandasFallidas = 0
                )
            }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

            assertEquals(listOf("pendiente-a-reenviar"), registro.encoladas)
            assertEquals(
                mapOf<String, Any>(
                    "visitas.reconcilia.incompleta" to 3,
                    "visitas.reconcilia.compromiso_distinto" to 1,
                    "visitas.reconcilia.incompleta_devuelta" to 2,
                    "visitas.conciliacion.compromisos_revisados" to 5,
                    "visitas.conciliacion.compromisos_tandas_fallidas" to 0,
                    "visitas.conciliacion.compromisos_ilegibles" to 0
                ),
                registro.eventos.single { it.first == TelemetriaDeVisitas.EVENTO_CONCILIACION_COMPROMISOS }.second
            )
            assertEquals(listOf(ahora), registro.corridas)
        }

    /** Un compromiso DISTINTO solo se reporta: no hay nada que reencolar. */
    @Test
    fun `E-APP-132 una distinta no reencola nada`() = runBlocking {
        db.visitDao().insertVisit(pendingVisit(id = "pendiente-ajena"))
        val registro = Registro()

        construir(registro, conciliarCompromisos = {
            ConciliacionDeCompromisos(1, 1, 0, 1, 0, 0)
        }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

        assertEquals(emptyList<String>(), registro.encoladas)
        assertEquals(
            1,
            registro.eventos.single { it.first == TelemetriaDeVisitas.EVENTO_CONCILIACION_COMPROMISOS }
                .second["visitas.reconcilia.compromiso_distinto"]
        )
    }

    @Test
    fun `E-APP-132 una tanda sin decidir no anota la corrida`() = runBlocking {
        val registro = Registro()

        construir(registro, conciliarCompromisos = {
            ConciliacionDeCompromisos(0, 0, 0, 0, 0, tandasFallidas = 1)
        }) { ConciliacionInversa(3, 0, 3, 0) }.doWork()

        assertEquals(emptyList<Instant>(), registro.corridas)
    }

    @Test
    fun `E-APP-132 en modo v2_actual la conciliacion del compromiso no corre`() = runBlocking {
        val registro = Registro()
        var llamadas = 0

        construir(registro, modo = ModoEnvioVisitas.V2_ACTUAL, conciliarCompromisos = {
            llamadas++
            null
        }) { ConciliacionInversa(0, 0, 0, 0) }.doWork()

        assertEquals(0, llamadas)
    }

    @Test
    fun `E-APP-132 una conciliacion del compromiso que revienta no tumba la corrida`() =
        runBlocking {
            val registro = Registro()

            val r = construir(
                registro,
                conciliarCompromisos = { throw IllegalStateException("grafo") }
            ) {
                ConciliacionInversa(3, 0, 3, 0)
            }.doWork()

            assertEquals(ListenableWorker.Result.success(), r)
            assertEquals(emptyList<Instant>(), registro.corridas)
        }
}
