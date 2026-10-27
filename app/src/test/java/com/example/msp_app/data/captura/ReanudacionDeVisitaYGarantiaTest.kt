package com.example.msp_app.data.captura

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.visitas.RegistroDeVisitaAdapter
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import com.example.msp_app.features.guarantees.data.RegistroDeGarantia
import com.example.msp_app.features.guarantees.data.ResumenDeLaGarantia
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-119 (D15, regla del dueño "nada se pierde", decidido por el
 * coordinador 2026-10-26): una visita o garantía CAPTURADA con 0 escrituras se
 * REANUDA al volver a la app y, mientras tanto, cuenta en "N capturas sin
 * terminar". Antes (medición M4 de la compuerta DESPUÉS): marcador VISITA
 * CAPTURADA, 0 visitas, aviso 0 y ninguna pantalla que la reanudara. Room real.
 */
class ReanudacionDeVisitaYGarantiaTest : RoomTestBase() {

    private val capturadaEn = Instant.parse("2026-10-20T15:52:00Z")
    private val clock = FakeClock(capturadaEn)
    private val hoy = LocalDate.of(2026, 10, 20)
    private val telemetry = RecordingTelemetry(clock)
    private val encoladas = mutableListOf<String>()
    private val garantiasEncoladas = mutableListOf<String>()

    private val dosCuentas = CapturaDeVisita(
        resultado = ResultadoDeVisita.SE_NEGO,
        etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
        cuentas = setOf(VENTA_A, VENTA_B)
    )

    private val noEstaba = CapturaDeVisita(
        resultado = ResultadoDeVisita.NO_ESTABA,
        etiqueta = CatalogoDeResultados.etiquetaPorDefecto(ResultadoDeVisita.NO_ESTABA),
        cuentas = emptySet()
    )

    @Before
    fun sembrar() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
    }

    @Test
    fun `E-APP-119 una visita cancelada esperando al cobrador cuenta en el aviso y se reanuda al volver`() =
        runTest {
            visitaCanceladaEsperandoAlCobrador()

            assertEquals(CapturaEnCursoEntity.CAPTURADA, marcador(SEMILLA).ESTADO)
            assertEquals("0 escrituras", 0, visitas())
            assertEquals("visible mientras tanto", 1, aviso())

            // El cobrador vuelve a la app tres horas después.
            clock.advanceHours(3)
            reanudador().reanudar()

            assertEquals("las dos cuentas, todo o nada", 2, visitas())
            assertEquals(2, encoladas.size)
            assertEquals(CapturaEnCursoEntity.CERRADA, marcador(SEMILLA).ESTADO)
            assertEquals("el aviso se apaga", 0, aviso())
            assertEquals(
                "la FECHA es la de captura, no la de la reanudación",
                listOf("2026-10-20T15:52:00Z", "2026-10-20T15:52:00Z"),
                fechasDeVisitas()
            )
        }

    /**
     * E-APP-126 (medición MA de la compuerta DESPUÉS 2026-10-27): la visita de
     * alcance CLIENTE ("no estaba") iba por `registrar`, sin marcador; cancelada
     * esperando al cobrador se perdía sin aviso. Ahora escribe su marcador como
     * las demás, cuenta en el aviso y se reanuda al volver.
     */
    @Test
    fun `E-APP-126 una visita de alcance cliente cancelada esperando al cobrador cuenta en el aviso y se reanuda`() =
        runTest {
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                casoDeUso(adaptador(traerUsuario = { awaitCancellation() }))
                    .invoke(SEMILLA, CLIENTE_ID, null, noEstaba, hoy)
            }
            job.cancel()
            job.join()

            assertEquals(CapturaEnCursoEntity.CAPTURADA, marcador(SEMILLA).ESTADO)
            assertEquals(0, visitas())
            assertEquals("visible mientras tanto", 1, aviso())

            reanudador().reanudar()

            assertEquals(1, visitas())
            assertEquals(CapturaEnCursoEntity.CERRADA, marcador(SEMILLA).ESTADO)
            assertEquals(0, aviso())
        }

    @Test
    fun `E-APP-119 reanudar no toca la visita que la pantalla de este proceso esta escribiendo`() =
        runTest {
            visitaCanceladaEsperandoAlCobrador()
            CapturasEnProceso.entrar(SEMILLA)
            try {
                reanudador().reanudar()
                assertEquals("la pantalla la está escribiendo", 0, visitas())
            } finally {
                CapturasEnProceso.salir(SEMILLA)
            }

            reanudador().reanudar()
            assertEquals("control positivo: libre, se reanuda", 2, visitas())
        }

    @Test
    fun `E-APP-119 una garantia capturada sin escribir cuenta en el aviso y se reanuda con sus imagenes`() =
        runTest {
            RegistroDeGarantia(db, clock).capturar(
                GARANTIA,
                USUARIO,
                resumenDeGarantia(
                    imagenes = listOf("/data/files/guarantee_${GARANTIA}_0.jpg")
                ).json()
            )
            assertEquals(1, aviso())

            reanudador(comprimir = { _, _ -> error("ya estaban comprimidas") }).reanudar()

            assertEquals(1, db.guaranteeDao().getAllGuarantees().size)
            assertEquals(1, db.guaranteeDao().getImagesByExternalId(GARANTIA).size)
            assertEquals(listOf(GARANTIA), garantiasEncoladas)
            assertEquals(CapturaEnCursoEntity.CERRADA, marcador(GARANTIA).ESTADO)
            assertEquals(0, aviso())
        }

    @Test
    fun `E-APP-119 una garantia cortada al comprimir se reanuda comprimiendo sus fuentes`() =
        runTest {
            RegistroDeGarantia(
                db,
                clock
            ).capturar(GARANTIA, USUARIO, resumenDeGarantia(imagenes = null).json())
            val pedidas = mutableListOf<List<String>>()

            reanudador(comprimir = { fuentes, id ->
                pedidas += fuentes
                listOf("/data/files/guarantee_${id}_0.jpg" to "image/jpeg")
            }).reanudar()

            assertEquals(listOf(listOf(FUENTE)), pedidas)
            assertEquals(1, db.guaranteeDao().getImagesByExternalId(GARANTIA).size)
            assertEquals(0, aviso())
        }

    @Test
    fun `E-APP-119 sin ninguna imagen la garantia no se escribe y sigue visible`() = runTest {
        RegistroDeGarantia(
            db,
            clock
        ).capturar(GARANTIA, USUARIO, resumenDeGarantia(imagenes = null).json())

        reanudador(comprimir = { _, _ -> emptyList() }).reanudar()

        assertTrue(
            "nunca una garantía sin imágenes",
            db.guaranteeDao().getAllGuarantees().isEmpty()
        )
        assertEquals(CapturaEnCursoEntity.CAPTURADA, marcador(GARANTIA).ESTADO)
        assertEquals(1, aviso())
    }

    // --- plomería ---------------------------------------------------------------

    private suspend fun kotlinx.coroutines.test.TestScope.visitaCanceladaEsperandoAlCobrador() {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            casoDeUso(adaptador(traerUsuario = { awaitCancellation() }))
                .invoke(SEMILLA, CLIENTE_ID, null, dosCuentas, hoy)
        }
        // El cobrador sale de la pantalla mientras se espera su usuario.
        job.cancel()
        job.join()
    }

    private fun adaptador(
        traerUsuario: suspend () -> User? = {
            User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7)
        }
    ) = RegistroDeVisitaAdapter(
        db = db,
        saleDao = db.saleDao(),
        visitas = VisitsLocalDataSource(
            db.visitDao(),
            db.saleDao(),
            object : VisitsWorkEnqueuer {
                override fun enqueue(visitId: String) {
                    encoladas += visitId
                }
            },
            clock
        ),
        recomendaciones = db.visitRecommendationDao(),
        imagenes = db.visitImageDao(),
        telemetry = telemetry,
        clock = clock,
        traerUsuario = traerUsuario,
        emailEnSesion = { USUARIO }
    )

    private fun casoDeUso(adaptador: RegistroDeVisitaAdapter) = RegistrarVisita(
        adaptador,
        object : UbicacionPort {
            override suspend fun ubicacionActual() = UbicacionDeLaVisita(18.4617, -97.3928)
        },
        telemetry
    )

    private fun reanudador(
        comprimir: suspend (
            List<String>,
            String
        ) -> List<Pair<String, String>> = { _, _ -> emptyList() }
    ) = ReanudadorDeCapturas(
        visitas = adaptador(),
        garantias = RegistroDeGarantia(db, clock),
        comprimirGarantia = comprimir,
        encolarGarantia = { garantiasEncoladas += it },
        emailEnSesion = { USUARIO }
    )

    private fun resumenDeGarantia(imagenes: List<String>?) = ResumenDeLaGarantia(
        descripcionFalla = "la base de la cama truena al sentarse",
        observaciones = "",
        clienteNombre = "Victoria Flores Olmedo",
        productoNombre = "Base matrimonial",
        fechaSolicitud = "2026-10-20T16:00:00Z",
        fuentes = listOf(FUENTE),
        imagenes = imagenes?.map { ResumenDeLaGarantia.ImagenResumida(it, "image/jpeg") }
    )

    private suspend fun aviso(): Int = db.capturaEnCursoDao().observarSinTerminar(USUARIO).first()

    private suspend fun marcador(id: String) = db.capturaEnCursoDao().obtener(id)!!

    private fun visitas(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM Visit").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun fechasDeVisitas(): List<String> =
        db.openHelper.readableDatabase.query("SELECT FECHA FROM Visit ORDER BY ID").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
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
        const val USUARIO = "efrain@example.com"
        const val SEMILLA = "visita-d15"
        const val GARANTIA = "4b8e1d2c-6a3f-4e9b-a1c7-5d0f2e8b9a64"
        const val FUENTE = "content://media/picker/0/com.android.providers.media.photopicker/media/1000017"
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188
    }
}
