package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitRecommendationEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.port.CitaEstructurada
import com.example.msp_app.feature.visitas.domain.port.PromesaEstructurada
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El adaptador que escribe la visita: **las columnas estructuradas, el par con
 * la recomendación, y el encolado que la Task 5 hizo independiente de la
 * ubicación.**
 *
 * Room in-memory y el `VisitsLocalDataSource` real: no se prueba una copia del
 * camino de escritura, se prueba el camino.
 */
class RegistroDeVisitaAdapterTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-09-01T15:52:00Z")
    private val hoy = LocalDate.of(2026, 9, 1)
    private val clock = FakeClock(ahora)
    private val telemetry = RecordingTelemetry(clock)
    private val encolador = EncoladorQueGraba()

    private lateinit var adaptador: RegistroDeVisitaAdapter

    private val cobrador = User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7)

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
        adaptador = adaptadorCon { cobrador }
    }

    private fun adaptadorCon(usuario: suspend () -> User?) = RegistroDeVisitaAdapter(
        db = db,
        saleDao = db.saleDao(),
        visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), encolador, clock),
        recomendaciones = db.visitRecommendationDao(),
        telemetry = telemetry,
        clock = clock,
        traerUsuario = usuario
    )

    // ─── las columnas estructuradas ──────────────────────────────────────────

    /**
     * La promesa cae en **columnas**, no en el texto de `NOTA`. Ese parseo es el
     * defecto que este plan vino a arreglar.
     */
    @Test
    fun `la promesa se escribe en columnas y la nota queda limpia`() = runTest {
        adaptador.registrar(
            visita(
                tipoVisita = Constants.PIDE_REAGENDAR,
                nota = "el viernes que cobre mi esposo",
                promesa = PromesaEstructurada(
                    ventaId = VENTA_B,
                    fecha = hoy.plusDays(3),
                    monto = Money.of(BigDecimal("220"))
                )
            )
        )

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertEquals("2026-09-04", guardada.PROMESA_FECHA)
        assertEquals(VENTA_B, guardada.PROMESA_VENTA_ID)
        assertEquals(22_000L, guardada.PROMESA_MONTO_CENTAVOS)
        assertEquals("el viernes que cobre mi esposo", guardada.NOTA)
        // Control positivo del "no se serializa": la nota NO contiene la fecha.
        assertTrue(
            "la nota no puede llevar la fecha adentro",
            guardada.NOTA!!.none { it.isDigit() }
        )
    }

    /** El monto cruza a centavos **enteros**, exacto y sin pasar por un flotante. */
    @Test
    fun `los centavos son exactos con decimales`() = runTest {
        adaptador.registrar(
            visita(
                promesa = PromesaEstructurada(
                    ventaId = null,
                    fecha = hoy.plusDays(1),
                    monto = Money.of(BigDecimal("1287.35"))
                )
            )
        )

        assertEquals(128_735L, db.visitDao().getVisitById(VISITA_ID).PROMESA_MONTO_CENTAVOS)
    }

    /** Sin monto se guarda `NULL`, no cero: cero sería "prometió no pagar". */
    @Test
    fun `una promesa sin monto guarda nulo, no cero`() = runTest {
        adaptador.registrar(
            visita(promesa = PromesaEstructurada(null, hoy.plusDays(1), monto = null))
        )

        assertNull(db.visitDao().getVisitById(VISITA_ID).PROMESA_MONTO_CENTAVOS)
    }

    /** La cita guarda día **y** hora como campos; sin hora, `NULL`. */
    @Test
    fun `la cita guarda dia y hora, y sin hora guarda nulo`() = runTest {
        adaptador.registrar(
            visita(
                tipoVisita = Constants.PIDE_TIEMPO,
                cita = CitaEstructurada(hoy, LocalTime.of(16, 0))
            )
        )
        assertEquals("16:00", db.visitDao().getVisitById(VISITA_ID).CITA_HORA)

        adaptador.registrar(
            visita(
                id = "visita-2",
                tipoVisita = Constants.PIDE_TIEMPO,
                cita = CitaEstructurada(hoy.plusDays(1), hora = null)
            )
        )
        val sinHora = db.visitDao().getVisitById("visita-2")
        assertEquals("2026-09-02", sinHora.CITA_FECHA)
        assertNull(sinHora.CITA_HORA)
    }

    // ─── el encolado de la Task 5 ────────────────────────────────────────────

    /**
     * **La visita se encola en el mismo guardado**, sin ubicación de por medio.
     * Antes de la Task 5 el encolado vivía en `UpdateLocationService`: si no
     * corría, la visita nunca se enviaba.
     */
    @Test
    fun `la visita queda encolada aunque no haya ubicacion`() = runTest {
        adaptador.registrar(visita(ubicacion = null))

        assertEquals(listOf(VISITA_ID), encolador.encoladas)
        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertEquals(0.0, guardada.LAT, 0.0)
        assertEquals(0.0, guardada.LNG, 0.0)
        assertEquals(0, guardada.GUARDADO_EN_MICROSIP)
    }

    /** Con ubicación, las coordenadas se escriben; sin ella, el encolado es el mismo. */
    @Test
    fun `con ubicacion se escriben las coordenadas`() = runTest {
        adaptador.registrar(visita(ubicacion = UbicacionDeLaVisita(18.46, -97.39)))

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertEquals(18.46, guardada.LAT, 0.0001)
        assertEquals(-97.39, guardada.LNG, 0.0001)
        assertEquals(listOf(VISITA_ID), encolador.encoladas)
    }

    // ─── el alcance (Task 13) ────────────────────────────────────────────────

    /**
     * Una **cita** es del CLIENTE: se propaga a TODAS sus cuentas activas, no
     * solo a la que el cobrador tenía abierta. El literal de cable de la cita es
     * de alcance venta; lo que la vuelve del cliente es el DÍA.
     */
    @Test
    fun `una cita propaga a todas las cuentas del cliente`() = runTest {
        adaptador.registrar(
            visita(
                tipoVisita = Constants.PIDE_TIEMPO,
                cita = CitaEstructurada(hoy, LocalTime.of(9, 0))
            )
        )

        assertEquals(
            EstadoCobranza.VOLVER_VISITAR.name,
            db.saleDao().getById(VENTA_A)!!.ESTADO_COBRANZA
        )
        assertEquals(
            "la otra cuenta del cliente tambien se toca",
            EstadoCobranza.VOLVER_VISITAR.name,
            db.saleDao().getById(VENTA_B)!!.ESTADO_COBRANZA
        )
    }

    /**
     * Control positivo del alcance: el MISMO literal **sin** día de cita sigue
     * siendo de alcance venta y toca solo la cuenta abierta. Sin esta prueba, la
     * de arriba pasaría también con un mapeo que propagara siempre.
     */
    @Test
    fun `el mismo literal sin cita toca solo la cuenta abierta`() = runTest {
        adaptador.registrar(visita(tipoVisita = Constants.PIDE_TIEMPO, ventaId = VENTA_A))

        assertEquals(
            EstadoCobranza.VOLVER_VISITAR.name,
            db.saleDao().getById(VENTA_A)!!.ESTADO_COBRANZA
        )
        assertEquals(
            "sin dia de cita, la otra cuenta no se toca",
            "PENDIENTE",
            db.saleDao().getById(VENTA_B)!!.ESTADO_COBRANZA
        )
    }

    // ─── el par con la recomendación ─────────────────────────────────────────

    /**
     * **Lo que el sistema sugirió queda al lado de lo que el cobrador hizo.**
     * Sin este par, el recomendador nunca se puede evaluar (§10).
     */
    @Test
    fun `la recomendacion queda ligada a la visita`() = runTest {
        db.visitRecommendationDao().guardar(recomendacion())

        adaptador.registrar(visita(recomendacionId = REC_ID))

        val ligada = db.visitRecommendationDao().porId(REC_ID)
        assertEquals(VISITA_ID, ligada?.VISITA_ID)
        // El brazo del experimento se conserva intacto: es el espacio del control.
        assertEquals("tratamiento", ligada?.GRUPO)
        assertEquals(0, ligada?.POSICION)
    }

    /**
     * Y la fila de la recomendación **sobrevive** aunque su visita se pode: no
     * hay FK con `CASCADE`, a propósito (Task 26). Si se fuera con la visita, se
     * perdería la mitad "qué sugirió" del par.
     */
    @Test
    fun `la recomendacion sobrevive a la poda de su visita`() = runTest {
        db.visitRecommendationDao().guardar(recomendacion())
        adaptador.registrar(visita(recomendacionId = REC_ID))
        db.visitDao().markSyncedByIds(listOf(VISITA_ID))

        db.visitDao().deleteUploadedVisits(conservarDesde = "2027-01-01")

        val ligada = db.visitRecommendationDao().porId(REC_ID)
        assertEquals("la sugerencia no se va con la visita", VISITA_ID, ligada?.VISITA_ID)
    }

    /** Una recomendación que ya no está no se calla: se reporta y la visita queda. */
    @Test
    fun `una recomendacion que ya no esta se reporta`() = runTest {
        val resultado = adaptador.registrar(visita(recomendacionId = "rec-fantasma"))

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertTrue(
            telemetry.recorded.any {
                it.name == VisitasTelemetria.CODE_RECOMENDACION_NO_LIGADA
            }
        )
    }

    /** Control positivo: con la fila presente, ese evento **no** se emite. */
    @Test
    fun `con la recomendacion presente no se emite el evento`() = runTest {
        db.visitRecommendationDao().guardar(recomendacion())

        adaptador.registrar(visita(recomendacionId = REC_ID))

        assertTrue(
            telemetry.recorded.none {
                it.name == VisitasTelemetria.CODE_RECOMENDACION_NO_LIGADA
            }
        )
    }

    // ─── los finales ─────────────────────────────────────────────────────────

    @Test
    fun `sin cobrador no se escribe nada`() = runTest {
        val sinUsuario = adaptadorCon { null }

        val resultado = sinUsuario.registrar(visita())

        assertEquals(ResultadoDelRegistro.SIN_COBRADOR, resultado)
        assertNull(runCatching { db.visitDao().getVisitById(VISITA_ID) }.getOrNull())
        assertTrue("nada se encola si nada se escribe", encolador.encoladas.isEmpty())
    }

    @Test
    fun `un cobrador en cero cuenta como sin cobrador`() = runTest {
        val sinId = adaptadorCon { cobrador.copy(COBRADOR_ID = 0) }

        assertEquals(ResultadoDelRegistro.SIN_COBRADOR, sinId.registrar(visita()))
    }

    @Test
    fun `sin cuentas del cliente en el telefono no se escribe nada`() = runTest {
        val resultado = adaptador.registrar(visita(clienteId = 99999))

        assertEquals(ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO, resultado)
        assertNull(runCatching { db.visitDao().getVisitById(VISITA_ID) }.getOrNull())
    }

    /** La reagenda legada se conserva: la lista vieja sigue viendo lo mismo. */
    @Test
    fun `una promesa deja la fecha temporal de cobranza en la venta`() = runTest {
        adaptador.registrar(
            visita(
                ventaId = VENTA_A,
                promesa = PromesaEstructurada(VENTA_A, hoy.plusDays(3), null)
            )
        )

        assertEquals("2026-09-04", db.saleDao().getById(VENTA_A)!!.DIA_TEMPORAL_COBRANZA)
    }

    // ─── fixtures ────────────────────────────────────────────────────────────

    @Suppress("LongParameterList") // constructor de fixture: 1:1 con el DTO del puerto.
    private fun visita(
        id: String = VISITA_ID,
        clienteId: Int = CLIENTE_ID,
        ventaId: Int? = VENTA_A,
        tipoVisita: String = Constants.NO_SE_ENCONTRABA,
        nota: String? = null,
        promesa: PromesaEstructurada? = null,
        cita: CitaEstructurada? = null,
        ubicacion: UbicacionDeLaVisita? = null,
        recomendacionId: String? = null
    ) = VisitaARegistrar(
        visitaId = id,
        clienteId = clienteId,
        ventaId = ventaId,
        tipoVisita = tipoVisita,
        nota = nota,
        promesa = promesa,
        cita = cita,
        ubicacion = ubicacion,
        recomendacionId = recomendacionId
    )

    private fun recomendacion() = VisitRecommendationEntity(
        ID = REC_ID,
        CLIENTE_ID = CLIENTE_ID,
        VENTA_ID = VENTA_B,
        COBRADOR_ID = 7,
        GENERADA_EN = "2026-09-01T14:00:00Z",
        POSICION = 0,
        MOTIVO = "cercania",
        ALGORITMO = "cercania_v1"
    )

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

    /** Fake a mano (sin MockK): lista pública de llamadas. */
    private class EncoladorQueGraba : VisitsWorkEnqueuer {
        val encoladas: MutableList<String> = mutableListOf()

        override fun enqueue(visitId: String) {
            encoladas += visitId
        }
    }

    private companion object {
        const val VISITA_ID = "visita-victoria-1"
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188
        const val REC_ID = "rec-victoria-1"
    }
}
