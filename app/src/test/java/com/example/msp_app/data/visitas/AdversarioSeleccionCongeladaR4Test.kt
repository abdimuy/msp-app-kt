package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Adversario ronda 4 sobre E-VIS-055, con Room en memoria, el adaptador real y
 * el caso de uso real.
 *
 * Dos clases de prueba conviven aquí y el nombre lo dice:
 *  - **ESPERADA ROJA**: afirma el defecto tal como está hoy, para que la suite
 *    siga verde y el hallazgo quede fijado; se pone ROJA el día que alguien lo
 *    arregle, y entonces se reescribe con el esperado bueno. El adversario no
 *    toca el código de producción: el coordinador decide.
 *  - El resto: el código resiste (o hace lo que se dijo) y la prueba lo fija.
 */
class AdversarioSeleccionCongeladaR4Test : RoomTestBase() {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val clock = FakeClock(Instant.parse("2026-09-01T15:52:00Z"))
    private val hoy = LocalDate.of(2026, 9, 1)
    private val telemetriaDelCaso = RecordingTelemetry(clock)
    private val cobrador = User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7)

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
    }

    /**
     * **H-r4-1, ESPERADA ROJA.** Una ruta que escribe sin avisar `alEscribir`.
     *
     * `RegistroDeVisitaAdapter.guardar` confirma la transacción de la visita,
     * escribe la foto y DESPUÉS encola. Si encolar falla y la telemetría de ese
     * fallo también lanza (`DurableTelemetry.error` hace `runBlocking` sobre un
     * insert de Room), la excepción sale de los `catch` internos al `catch`
     * externo, que contesta [ResultadoDelRegistro.FALLO_EL_GUARDADO] —"la
     * transaccion no dejo nada escrito"— con la visita y la foto YA en Room.
     *
     * `RegistrarVisita` solo llama `alEscribir` con REGISTRADA, así que la
     * pantalla cuenta 0 escrituras y DESCONGELA (ver la prueba de la VM en
     * `RegistrarVisitaAdversarioR4Test`). Con la selección abierta el cobrador
     * elige otro desenlace con cuentas, el ancla pasa a ser otra visita, y el
     * `REPLACE` de `visita_imagenes` muda la foto: exactamente lo que E-VIS-055
     * debía hacer imposible.
     */
    @Test
    fun `H-r4-1 ESPERADA ROJA un guardado que escribio y responde fallo no avisa y la foto se muda`() =
        runTest {
            val encolador = EncoladorQueFallaUnaVez()
            val telemetriaRota = TelemetriaQueFallaUnaVez(RecordingTelemetry(clock))
            val caso = RegistrarVisita(
                registro = adaptador(encolador, telemetriaRota),
                ubicacion = UbicacionFija,
                telemetry = telemetriaDelCaso
            )
            val avisos = AtomicInteger(0)

            val primero = caso(
                visitaId = SEMILLA,
                clienteId = CLIENTE_ID,
                ventaId = null,
                captura = captura(ResultadoDeVisita.NO_ESTABA, emptySet()),
                hoy = hoy,
                comprobantes = listOf(foto()),
                alEscribir = { avisos.incrementAndGet() }
            )

            assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, primero)
            assertEquals("hoy nadie avisa la escritura", 0, avisos.get())
            assertNotNull("la visita SÍ quedó escrita", db.visitDao().findVisitById(SEMILLA))
            assertEquals(
                "la foto SÍ quedó escrita, atada a la semilla",
                SEMILLA,
                db.visitImageDao().getByVisitaId(SEMILLA).single().VISITA_ID
            )

            // Con 0 avisos la pantalla descongela; el cobrador cambia el desenlace.
            val segundo = caso(
                visitaId = SEMILLA,
                clienteId = CLIENTE_ID,
                ventaId = null,
                captura = captura(ResultadoDeVisita.VISITE_VUELVO, setOf(VENTA_A, VENTA_B)),
                hoy = hoy,
                comprobantes = listOf(foto())
            )

            assertEquals(ResultadoDelRegistro.REGISTRADA, segundo)
            val anclaNueva = IdsDeLaVisita.ancla(SEMILLA, setOf(VENTA_A, VENTA_B))
            assertEquals(
                "HOY la foto se mudó de visita (debería seguir en la semilla)",
                listOf(FOTO),
                db.visitImageDao().getByVisitaId(anclaNueva).map { it.ID }
            )
            val enLaSemilla = db.visitImageDao().getByVisitaId(SEMILLA).map { it.ID }
            assertEquals(emptyList<String>(), enLaSemilla)
        }

    /**
     * **Control positivo de H-r4-1.** El mismo montaje sin fallo inyectado
     * avisa una escritura: la prueba de arriba no mide un `alEscribir` que se
     * dejó de llamar por otra razón.
     */
    @Test
    fun `H-r4-1 control sin fallo inyectado el mismo guardado avisa su escritura`() = runTest {
        val caso = RegistrarVisita(
            registro = adaptadorSano(),
            ubicacion = UbicacionFija,
            telemetry = telemetriaDelCaso
        )
        val avisos = AtomicInteger(0)

        val resultado = caso(
            visitaId = SEMILLA,
            clienteId = CLIENTE_ID,
            ventaId = null,
            captura = captura(ResultadoDeVisita.NO_ESTABA, emptySet()),
            hoy = hoy,
            comprobantes = listOf(foto()),
            alEscribir = { avisos.incrementAndGet() }
        )

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertEquals(1, avisos.get())
    }

    /**
     * **H-r4-3, medido (el autor lo dijo sin medir).** Quitar una foto que un
     * parcial ya escribió: la pantalla borra su archivo, pero el reintento con
     * la misma ancla NO borra su fila —`insertAll` es `REPLACE` por id de foto
     * y nadie borra las ausentes—. La fila queda pendiente para siempre y el
     * armador del multipart la omite en cada envío por no tener archivo.
     */
    @Test
    fun `H-r4-3 quitar una foto ya escrita deja su fila pendiente sin archivo para siempre`() =
        runTest {
            val caso = RegistrarVisita(
                registro = adaptadorSano(),
                ubicacion = UbicacionFija,
                telemetry = telemetriaDelCaso
            )
            val quitada = archivo("IMG-QUITADA")
            val queda = archivo("IMG-QUEDA")
            val cuentas = setOf(VENTA_A, VENTA_B)
            caso(
                visitaId = SEMILLA,
                clienteId = CLIENTE_ID,
                ventaId = null,
                captura = captura(ResultadoDeVisita.VISITE_VUELVO, cuentas),
                hoy = hoy,
                comprobantes = listOf(foto("IMG-QUITADA", quitada), foto("IMG-QUEDA", queda))
            )

            // quitarFoto() borra el archivo (camara.descartar) y el reintento
            // va sin ella, con las mismas cuentas congeladas.
            File(quitada).delete()
            caso(
                visitaId = SEMILLA,
                clienteId = CLIENTE_ID,
                ventaId = null,
                captura = captura(ResultadoDeVisita.VISITE_VUELVO, cuentas),
                hoy = hoy,
                comprobantes = listOf(foto("IMG-QUEDA", queda))
            )

            val ancla = IdsDeLaVisita.ancla(SEMILLA, cuentas)
            val pendientes = db.visitImageDao().getPendientesDe(ancla)
            assertEquals(
                "la fila de la foto quitada sigue ahí",
                setOf("IMG-QUITADA", "IMG-QUEDA"),
                pendientes.map { it.ID }.toSet()
            )
            val partes = partesDeComprobantesDeVisita(pendientes)
            assertEquals(listOf("IMG-QUEDA"), partes.enviadas.map { it.ID })
            assertEquals("la quitada se omite en cada envío", 1, partes.omitidas)
        }

    // ─── utilidades ──────────────────────────────────────────────────────────

    private fun adaptadorSano() =
        adaptador(EncoladorQueFallaUnaVez(fallar = false), RecordingTelemetry(clock))

    private fun adaptador(encolador: VisitsWorkEnqueuer, telemetry: Telemetry) =
        RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), encolador, clock),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { cobrador }
        )

    private fun captura(resultado: ResultadoDeVisita, cuentas: Set<Int>) = CapturaDeVisita(
        resultado = resultado,
        etiqueta = CatalogoDeResultados.etiquetaPorDefecto(resultado),
        cuentas = cuentas
    )

    private fun archivo(id: String): String {
        val archivo = File(carpeta.root, "$id.jpg")
        archivo.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00))
        return archivo.absolutePath
    }

    private fun foto(id: String = FOTO, ruta: String = "/files/comprobante_visita_$id.jpg") =
        ComprobanteDeVisita(id = id, archivo = ruta, mime = "image/jpeg")

    private object UbicacionFija : UbicacionPort {
        override suspend fun ubicacionActual(): UbicacionDeLaVisita =
            UbicacionDeLaVisita(lat = 18.46, lng = -97.39)
    }

    /** El encolado de WorkManager que falla la primera vez. */
    private class EncoladorQueFallaUnaVez(private var fallar: Boolean = true) : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) {
            if (fallar) {
                fallar = false
                throw IllegalStateException("workmanager no pudo encolar")
            }
        }
    }

    /**
     * La telemetría durable cuyo primer `error()` lanza (un insert de Room que
     * falla una vez) y los demás se graban.
     */
    private class TelemetriaQueFallaUnaVez(
        private val real: RecordingTelemetry
    ) : Telemetry by real {
        private var yaFallo = false

        override fun error(code: String, message: String, props: Map<String, String>) {
            if (!yaFallo) {
                yaFallo = true
                throw IllegalStateException("la cola de telemetria no pudo insertar")
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
        const val SEMILLA = "visita-r4-semilla"
        const val FOTO = "IMG-R4-1"
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188
    }
}
