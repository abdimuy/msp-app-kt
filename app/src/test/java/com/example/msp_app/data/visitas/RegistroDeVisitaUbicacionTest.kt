package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.location.captura.FixDeUbicacion
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.ubicacion.MomentoDeGuardado
import com.example.msp_app.core.ubicacion.UbicacionAlGuardar
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.pagos.CondonacionFixtures
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-148 (K6): la ubicacion y el plazo de envio de las visitas, con el adaptador REAL sobre Room
 * real y una ubicacion falsa que graba lo que se le pide.
 */
class RegistroDeVisitaUbicacionTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-30T18:00:00Z"))
    private val encoladas = mutableListOf<String>()

    private class UbicacionGrabadora(
        private val antes: FixDeUbicacion? = null,
        private val falla: Boolean = false
    ) : UbicacionAlGuardar {
        val momentos = mutableListOf<MomentoDeGuardado>()
        val anotadas = mutableListOf<String>()
        val momentosAnotados = mutableListOf<MomentoDeGuardado>()
        val esperando = mutableListOf<String>()

        override fun alGuardar(): MomentoDeGuardado = MomentoDeGuardado(
            elapsedNanos = 5L,
            antes = antes,
            guardadoEpochMs = 1_793_383_200_000L
        ).also { momentos += it }

        override suspend fun anotarEnLaTransaccion(
            id: String,
            tipo: String,
            momento: MomentoDeGuardado
        ) {
            anotadas += id
            momentosAnotados += momento
            if (falla) error("plazo roto")
        }

        override fun despuesDeGuardar(id: String, tipo: String, momento: MomentoDeGuardado) {
            esperando += id
            if (falla) error("espera rota")
        }
    }

    private val fixAntes = FixDeUbicacion(
        lat = 20.5888,
        lon = -100.3899,
        precisionM = 8.0,
        hora = Instant.parse("2026-10-30T17:59:50Z"),
        elapsedRealtimeNanos = 0L
    )

    @Before
    fun sembrar() = runTest {
        db.saleDao().insertAll(
            listOf(
                CondonacionFixtures.venta(saldo = 1000.0, id = VENTA_A),
                CondonacionFixtures.venta(saldo = 2000.0, id = VENTA_B)
            )
        )
    }

    private fun adaptador(
        ubicacion: UbicacionAlGuardar,
        traerUsuario: suspend () -> User? = { COBRADOR }
    ) = RegistroDeVisitaAdapter(
        db = db,
        saleDao = db.saleDao(),
        visitas = VisitsLocalDataSource(
            db.visitDao(),
            db.saleDao(),
            object : VisitsWorkEnqueuer {
                override fun enqueue(visitId: String) {
                    encoladas += "encolada:$visitId"
                }
            },
            clock
        ),
        recomendaciones = db.visitRecommendationDao(),
        imagenes = db.visitImageDao(),
        telemetry = RecordingTelemetry(clock),
        clock = clock,
        traerUsuario = traerUsuario,
        emailEnSesion = { "rosa@example.com" },
        ubicacion = ubicacion
    )

    private fun visita(id: String, venta: Int = VENTA_A) = VisitaARegistrar(
        visitaId = id,
        clienteId = CLIENTE,
        ventaId = venta,
        tipoVisita = Constants.NO_SE_ENCONTRABA,
        nota = null
    )

    private suspend fun lat(id: String) = db.visitDao().getVisitById(id).LAT

    private suspend fun lng(id: String) = db.visitDao().getVisitById(id).LNG

    @Test
    fun `E-APP-148 las cuentas del lote nacen con la ubicacion que el tomador ya tenia`() =
        runTest {
            val ubicacion = UbicacionGrabadora(antes = fixAntes)

            val resultado = adaptador(ubicacion).registrarTodas(
                SEMILLA,
                listOf(visita("v-1"), visita("v-2", VENTA_B))
            )

            assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
            listOf("v-1", "v-2").forEach {
                assertEquals(20.5888, lat(it), 0.0)
                assertEquals(-100.3899, lng(it), 0.0)
            }
            assertEquals("se lee UNA vez para todo el lote", 1, ubicacion.momentos.size)
        }

    @Test
    fun `E-APP-148 el plazo de cada cuenta se anota con la misma hora de guardado del lote`() =
        runTest {
            val ubicacion = UbicacionGrabadora()

            adaptador(
                ubicacion
            ).registrarTodas(SEMILLA, listOf(visita("v-1"), visita("v-2", VENTA_B)))

            assertEquals(listOf("v-1", "v-2"), ubicacion.anotadas)
            assertEquals("quedan esperando las dos", listOf("v-1", "v-2"), ubicacion.esperando)
            assertEquals(
                "un solo momento: el tope de 30 s corre desde el guardado mas antiguo del lote",
                1,
                ubicacion.momentos.map { it.guardadoEpochMs }.distinct().size
            )
        }

    @Test
    fun `E-APP-148 las visitas se encolan despues de dejarlas esperando la ubicacion`() = runTest {
        val ubicacion = UbicacionGrabadora()
        val ordenados = mutableListOf<String>()
        val conOrden = object : UbicacionAlGuardar by ubicacion {
            override fun despuesDeGuardar(id: String, tipo: String, momento: MomentoDeGuardado) {
                ordenados += "espera:$id"
            }
        }
        val a = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(
                db.visitDao(),
                db.saleDao(),
                object : VisitsWorkEnqueuer {
                    override fun enqueue(visitId: String) {
                        ordenados += "encolada:$visitId"
                    }
                },
                clock
            ),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = RecordingTelemetry(clock),
            clock = clock,
            traerUsuario = { COBRADOR },
            emailEnSesion = { "rosa@example.com" },
            ubicacion = conOrden
        )

        a.registrarTodas(SEMILLA, listOf(visita("v-1")))

        assertEquals(listOf("espera:v-1", "encolada:v-1"), ordenados)
    }

    @Test
    fun `E-APP-148 si anotar o esperar la ubicacion lanza, la visita se guarda y se encola igual`() =
        runTest {
            val resultado = adaptador(UbicacionGrabadora(falla = true))
                .registrarTodas(SEMILLA, listOf(visita("v-1")))

            assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
            assertEquals(0.0, lat("v-1"), 0.0)
            assertEquals(listOf("encolada:v-1"), encoladas)
        }

    @Test
    fun `E-APP-148 una visita que no se escribio no anota plazo`() = runTest {
        val ubicacion = UbicacionGrabadora()
        val sinCobrador = adaptador(ubicacion) { null }
        assertEquals(
            ResultadoDelRegistro.SIN_COBRADOR,
            sinCobrador.registrarTodas(SEMILLA, listOf(visita("v-1")))
        )
        assertTrue(ubicacion.anotadas.isEmpty())
        assertTrue(ubicacion.esperando.isEmpty())
    }

    @Test
    fun `E-APP-148 una captura reanudada horas despues no recibe la ubicacion de ahora`() =
        runTest {
            val ubicacion = UbicacionGrabadora(antes = fixAntes)
            // La pantalla se cierra mientras se espera al cobrador: el marcador queda CAPTURADA.
            val job = launch {
                adaptador(ubicacion) { awaitCancellation() }
                    .registrarTodas(SEMILLA, listOf(visita("v-1")))
            }
            testScheduler.runCurrent()
            job.cancel()
            job.join()
            assertTrue("nada se escribio", db.visitDao().getVisitsByClienteId(CLIENTE).isEmpty())

            val reanudadas = adaptador(ubicacion).reanudarPendientes()

            assertEquals(1, reanudadas)
            assertEquals(
                "se escribio en 0,0: el telefono ya no esta donde se hizo",
                0.0,
                lat("v-1"),
                0.0
            )
            assertTrue(
                "sin ubicacion de ahora, sin plazo y sin espera",
                ubicacion.momentosAnotados.all { it == MomentoDeGuardado.SinUbicacion } &&
                    ubicacion.esperando.isEmpty()
            )
            assertEquals("pero se encolo", listOf("encolada:v-1"), encoladas)
        }

    @Test
    fun `E-APP-148 anotarUbicacion no pisa una ubicacion que la visita ya tenia`() = runTest {
        val a = adaptador(UbicacionGrabadora(antes = fixAntes))
        a.registrarTodas(SEMILLA, listOf(visita("v-1")))

        a.anotarUbicacion(listOf("v-1"), UbicacionDeLaVisita(lat = 19.0, lng = -99.0))

        assertEquals("sigue la primera", 20.5888, lat("v-1"), 0.0)
        assertEquals(-100.3899, lng("v-1"), 0.0)
    }

    @Test
    fun `E-APP-148 anotarUbicacion escribe cuando la visita sigue en 0,0`() = runTest {
        val a = adaptador(UbicacionGrabadora())
        a.registrarTodas(SEMILLA, listOf(visita("v-1")))

        a.anotarUbicacion(listOf("v-1"), UbicacionDeLaVisita(lat = 19.0, lng = -99.0))

        assertEquals(19.0, lat("v-1"), 0.0)
        assertEquals(-99.0, lng("v-1"), 0.0)
    }

    private companion object {
        const val SEMILLA = "semilla-visita-1"
        const val CLIENTE = 4821
        const val VENTA_A = 13_662_829
        const val VENTA_B = 13_662_830
        val COBRADOR = User(ID = "u-1", NOMBRE = "Efrain Dominguez", COBRADOR_ID = 7)
    }
}
