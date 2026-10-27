package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Adversario ronda 5 (delta `3773800d..1e566de1`) con Room en memoria, el
 * adaptador real y el caso de uso real. H-r5-1 quedó arreglada por E-VIS-074:
 * su prueba ya no afirma el defecto, afirma el esperado bueno.
 */
class AdversarioSeleccionCongeladaR5Test : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-09-01T15:52:00Z"))
    private val hoy = LocalDate.of(2026, 9, 1)
    private val cobrador = User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7)

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
    }

    /**
     * **E-VIS-074 (H-r5-1), raíz en el adaptador.** El puerto promete "Total:
     * no lanza". Con dos cuentas el ancla COMITEA con su foto; en la segunda
     * falla algo antes de la transacción (aquí `traerUsuario`, que es
     * Firestore) y el `catch` externo de `registrar` reporta. Si esa
     * telemetría lanza, el puerto contesta FALLO_EL_GUARDADO igual: antes
     * lanzaba, el caso de uso no terminaba y la VM moría sin `aplicar`.
     */
    @Test
    fun `E-VIS-074 H-r5-1 la telemetria que lanza sin cobrador no hace lanzar al puerto y E-APP-087 no deja nada`() =
        runTest {
            val lanza = TelemetriaQueLanzaEn(VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO)
            val llamadas = AtomicInteger(0)
            val caso = RegistrarVisita(
                registro = adaptador(
                    telemetry = lanza,
                    traerUsuario = {
                        if (llamadas.incrementAndGet() == 1) {
                            error(
                                "firestore sin red"
                            )
                        } else {
                            cobrador
                        }
                    }
                ),
                ubicacion = UbicacionFija,
                telemetry = RecordingTelemetry(clock)
            )
            val avisos = AtomicInteger(0)
            val cuentas = setOf(VENTA_A, VENTA_B)

            val resultado = caso(
                visitaId = SEMILLA,
                clienteId = CLIENTE_ID,
                ventaId = null,
                captura = captura(ResultadoDeVisita.VISITE_VUELVO, cuentas),
                hoy = hoy,
                comprobantes = listOf(foto()),
                alEscribir = { avisos.incrementAndGet() }
            )

            assertEquals("la telemetría sí se pidió (y lanzó)", 1, lanza.lanzadas)
            assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
            // E-APP-087 (todo o nada): el cobrador se consulta UNA vez, antes de
            // la transacción; sin él no queda ni el ancla ni su foto.
            val ancla = IdsDeLaVisita.ancla(SEMILLA, cuentas)
            assertNull("todo o nada: ni el ancla", db.visitDao().findVisitById(ancla))
            assertTrue(db.visitImageDao().getByVisitaId(ancla).isEmpty())
            assertEquals("nada escrito, nada avisado", 0, avisos.get())
        }

    /**
     * **E-VIS-074, recomendación no ligada.** El aviso de que la recomendación
     * mostrada ya no estaba corre DENTRO de la transacción del ancla. Si su
     * telemetría lanzaba, la transacción se revertía: el trabajo de campo se
     * perdía por un reporte. La visita queda escrita igual.
     */
    @Test
    fun `E-VIS-074 la telemetria de la recomendacion no ligada que lanza no tira la visita`() =
        runTest {
            val lanza = TelemetriaQueLanzaEn(VisitasTelemetria.CODE_RECOMENDACION_NO_LIGADA)
            val caso = RegistrarVisita(
                registro = adaptador(telemetry = lanza, traerUsuario = { cobrador }),
                ubicacion = UbicacionFija,
                telemetry = RecordingTelemetry(clock)
            )

            val resultado = caso(
                visitaId = SEMILLA,
                clienteId = CLIENTE_ID,
                ventaId = null,
                captura = captura(ResultadoDeVisita.VISITE_VUELVO, setOf(VENTA_A)),
                hoy = hoy,
                recomendacionId = "recomendacion-que-ya-no-esta"
            )

            assertEquals("la telemetría sí se pidió (y lanzó)", 1, lanza.lanzadas)
            assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
            assertNotNull(
                "la visita quedó escrita",
                db.visitDao().findVisitById(IdsDeLaVisita.ancla(SEMILLA, setOf(VENTA_A)))
            )
        }

    /** **H-r5-1, control positivo.** Telemetría sana: el mismo montaje contesta, no lanza. */
    @Test
    fun `H-r5-1 control con telemetria sana el mismo fallo contesta sin lanzar`() = runTest {
        val llamadas = AtomicInteger(0)
        val caso = RegistrarVisita(
            registro = adaptador(
                telemetry = RecordingTelemetry(clock),
                traerUsuario = {
                    if (llamadas.incrementAndGet() == 1) {
                        error(
                            "firestore sin red"
                        )
                    } else {
                        cobrador
                    }
                }
            ),
            ubicacion = UbicacionFija,
            telemetry = RecordingTelemetry(clock)
        )

        val resultado = caso(
            visitaId = SEMILLA,
            clienteId = CLIENTE_ID,
            ventaId = null,
            captura = captura(ResultadoDeVisita.VISITE_VUELVO, setOf(VENTA_A, VENTA_B)),
            hoy = hoy,
            comprobantes = listOf(foto())
        )

        assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
    }

    /**
     * Toda la puerta con Firestore caído al primer intento: el adaptador
     * contesta FALLO_EL_GUARDADO sin escribir nada. Con 2af527f5 eso ya no
     * suelta la selección (congelar de más, aceptado): esto fija que de verdad
     * no quedó nada, para que el costo sea solo de UX.
     */
    @Test
    fun `R5 sin red para el usuario el fallo no deja nada escrito`() = runTest {
        val caso = RegistrarVisita(
            registro = adaptador(
                telemetry = RecordingTelemetry(clock),
                traerUsuario = { error("firestore sin red") }
            ),
            ubicacion = UbicacionFija,
            telemetry = RecordingTelemetry(clock)
        )

        val resultado = caso(
            visitaId = SEMILLA,
            clienteId = CLIENTE_ID,
            ventaId = null,
            captura = captura(ResultadoDeVisita.NO_ESTABA, emptySet()),
            hoy = hoy,
            comprobantes = listOf(foto())
        )

        assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
        assertNull(db.visitDao().findVisitById(SEMILLA))
        assertTrue(db.visitImageDao().getByVisitaId(SEMILLA).isEmpty())
    }

    /**
     * H-r4-5: la lista blanca descarta todo lo que no sea el conteo, aunque
     * alguien lo meta al mapa del evento: ids, textos y conteos negativos.
     */
    @Test
    fun `R5 el evento de cuenta ausente solo deja pasar el conteo no negativo`() {
        val capturado = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, m -> capturado += e to m }
        try {
            TelemetriaDeVisitas.emitir(
                TelemetriaDeVisitas.EVENTO_CUENTA_AUSENTE,
                TelemetriaDeVisitas.cuentaAusente(3) + mapOf(
                    "venta_id" to VENTA_A,
                    "cliente" to "Victoria Flores Olmedo",
                    "visitas.visita_id" to SEMILLA
                )
            )
            TelemetriaDeVisitas.emitir(
                TelemetriaDeVisitas.EVENTO_CUENTA_AUSENTE,
                TelemetriaDeVisitas.cuentaAusente(-1)
            )
            TelemetriaDeVisitas.emitir(
                TelemetriaDeVisitas.EVENTO_CUENTA_AUSENTE,
                mapOf(TelemetriaDeVisitas.CA_AUSENTES to "77021")
            )
        } finally {
            TelemetriaDeVisitas.sinkParaPrueba = null
        }

        assertEquals(
            listOf(
                TelemetriaDeVisitas.EVENTO_CUENTA_AUSENTE to
                    mapOf<String, Any>(TelemetriaDeVisitas.CA_AUSENTES to 3)
            ),
            capturado
        )
    }

    // ─── utilidades ──────────────────────────────────────────────────────────

    private fun adaptador(telemetry: Telemetry, traerUsuario: suspend () -> User?) =
        RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), EncoladorSano, clock),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = traerUsuario
        )

    private fun captura(resultado: ResultadoDeVisita, cuentas: Set<Int>) = CapturaDeVisita(
        resultado = resultado,
        etiqueta = CatalogoDeResultados.etiquetaPorDefecto(resultado),
        cuentas = cuentas
    )

    private fun foto() = ComprobanteDeVisita(
        id = FOTO,
        archivo = "/files/comprobante_visita_$FOTO.jpg",
        mime = "image/jpeg"
    )

    private object UbicacionFija : UbicacionPort {
        override suspend fun ubicacionActual(): UbicacionDeLaVisita =
            UbicacionDeLaVisita(lat = 18.46, lng = -97.39)
    }

    private object EncoladorSano : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) = Unit
    }

    /** `DurableTelemetry.error` cuyo insert de Room falla con un código dado. */
    private inner class TelemetriaQueLanzaEn(
        private val codigo: String,
        private val real: RecordingTelemetry = RecordingTelemetry(clock)
    ) : Telemetry by real {
        var lanzadas = 0

        override fun error(code: String, message: String, props: Map<String, String>) {
            if (code == codigo) {
                lanzadas++
                throw IllegalStateException(
                    "la cola de telemetria no pudo insertar"
                )
            }
            real.error(code, message, props)
        }
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
        const val SEMILLA = "visita-r5-semilla"
        const val FOTO = "IMG-R5-1"
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188
    }
}
