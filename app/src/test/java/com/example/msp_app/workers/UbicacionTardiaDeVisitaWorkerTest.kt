package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.visits.UbicacionTardiaBody
import com.example.msp_app.data.api.services.visits.V2UbicacionTardiaApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * E-APP-129: el trabajo que lleva al servidor la ubicación tardía de una visita.
 * Room real, API falsa. Fija lo que decidió el coordinador (2026-10-27): sin
 * ubicación no se manda nada, nunca se toca la visita local, y cada código del
 * servidor tiene su desenlace.
 */
class UbicacionTardiaDeVisitaWorkerTest : RoomTestBase() {

    private lateinit var context: Context
    private val llamadas = mutableListOf<Pair<String, UbicacionTardiaBody>>()

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun api(respuesta: suspend () -> VisitaDTO = { VisitaDTO(id = "visita-001") }) =
        object : V2UbicacionTardiaApi {
            override suspend fun aplicarUbicacion(
                id: String,
                body: UbicacionTardiaBody
            ): VisitaDTO {
                llamadas += id to body
                return respuesta()
            }
        }

    private suspend fun sembrar(lat: Double, lng: Double, guardado: Int = 1) {
        VisitsLocalDataSource(context).saveVisit(
            pendingVisit("visita-001", guardado = guardado).copy(LAT = lat, LNG = lng)
        )
    }

    private fun correr(
        api: V2UbicacionTardiaApi,
        intento: Int = 0,
        useV2: Boolean = true,
        visitId: String? = "visita-001"
    ): Result {
        val entrada = Data.Builder().apply {
            if (visitId != null) {
                putString(
                    "visit_id",
                    visitId
                )
            }
        }.build()
        val worker = TestListenableWorkerBuilder<UbicacionTardiaDeVisitaWorker>(context, entrada)
            .setRunAttemptCount(intento)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = UbicacionTardiaDeVisitaWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    api = api,
                    visitDao = db.visitDao(),
                    useV2 = useV2
                )
            })
            .build()
        return runBlocking { (worker as UbicacionTardiaDeVisitaWorker).doWork() }
    }

    @Test
    fun `con ubicacion en Room manda exactamente esas coordenadas, una vez, y termina`() = runTest {
        sembrar(18.4617, -97.3928)

        val resultado = correr(api())

        assertEquals(Result.success(), resultado)
        assertEquals(listOf("visita-001" to UbicacionTardiaBody(18.4617, -97.3928)), llamadas)
    }

    @Test
    fun `sin ubicacion en Room no manda nada`() = runTest {
        sembrar(0.0, 0.0)

        val resultado = correr(api())

        assertEquals(Result.success(), resultado)
        assertEquals(emptyList<Pair<String, UbicacionTardiaBody>>(), llamadas)
    }

    @Test
    fun `no toca la visita local ni la reabre`() = runTest {
        sembrar(18.4617, -97.3928, guardado = 1)
        val antes = db.visitDao().findVisitById("visita-001")

        correr(api())

        assertEquals(antes, db.visitDao().findVisitById("visita-001"))
        assertEquals(1, db.visitDao().findVisitById("visita-001")!!.GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `404 reintenta mientras quedan intentos y luego suelta`() = runTest {
        sembrar(18.4617, -97.3928)
        val noExiste = api { throw httpError(404) }

        assertEquals(Result.retry(), correr(noExiste, intento = 0))
        assertEquals(
            Result.success(),
            correr(noExiste, intento = UbicacionTardiaDeVisitaWorker.MAX_INTENTOS)
        )
    }

    @Test
    fun `red y 5xx y 429 reintentan`() = runTest {
        sembrar(18.4617, -97.3928)

        assertEquals(Result.retry(), correr(api { throw IOException("sin red") }))
        assertEquals(Result.retry(), correr(api { throw httpError(503) }))
        assertEquals(Result.retry(), correr(api { throw httpError(429) }))
    }

    @Test
    fun `422 y otros 4xx se sueltan sin reintentar`() = runTest {
        sembrar(18.4617, -97.3928)

        assertEquals(Result.success(), correr(api { throw httpError(422) }))
        assertEquals(Result.success(), correr(api { throw httpError(400) }))
    }

    @Test
    fun `con v2 apagado no manda nada`() = runTest {
        sembrar(18.4617, -97.3928)

        assertEquals(Result.success(), correr(api(), useV2 = false))
        assertEquals(0, llamadas.size)
    }

    @Test
    fun `sin visit_id falla sin llamar`() = runTest {
        assertEquals(Result.failure(), correr(api(), visitId = null))
        assertEquals(0, llamadas.size)
    }
}
