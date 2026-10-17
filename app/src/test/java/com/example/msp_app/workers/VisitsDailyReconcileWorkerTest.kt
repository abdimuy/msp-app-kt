package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomDeliveredVisitsStore
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
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
        store = RoomDeliveredVisitsStore(db.visitDao()),
        registry = servidor,
        errorReporter = sinReporte,
        clock = { ahora }
    )

    private class Registro {
        val encoladas = mutableListOf<String>()
        val corridas = mutableListOf<Instant>()
    }

    private fun construir(
        registro: Registro,
        modo: ModoEnvioVisitas = ModoEnvioVisitas.V3,
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
                conciliar = conciliar,
                pendientes = { db.visitDao().pendientesOrdenadas() },
                encolar = { registro.encoladas += it },
                anotarCorrida = { registro.corridas += it },
                clock = FakeClock(ahora)
            )
        })
        .build()

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
}
