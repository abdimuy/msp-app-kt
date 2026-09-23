package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.dao.visit.VisitRecommendationDao
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.database.entities.VisitRecommendationEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.port.CitaEstructurada
import com.example.msp_app.feature.visitas.domain.port.PromesaEstructurada
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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

    /**
     * [usuario] va al final para que el `adaptadorCon { … }` de los tests de
     * cobrador siga siendo una lambda de cierre.
     */
    private fun adaptadorCon(
        saleDao: SaleDao = db.saleDao(),
        recomendaciones: VisitRecommendationDao = db.visitRecommendationDao(),
        usuario: suspend () -> User?
    ) = RegistroDeVisitaAdapter(
        db = db,
        saleDao = saleDao,
        visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), encolador, clock),
        recomendaciones = recomendaciones,
        imagenes = db.visitImageDao(),
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

    // ─── la cuenta a la que se ata la visita ─────────────────────────────────

    /**
     * **La venta que el cobrador eligió para la promesa es la cuenta de la
     * visita.** Antes se escribía `visita.ventaId ?: 0`, y desde el detalle de
     * CLIENTE eso era 0: la lectura lo traducía a `null` y el deriver mandaba la
     * visita a `huerfanas` sin indexarla, así que la promesa se escribía y
     * después desaparecía de la derivación. El chip "de cuál venta" ofrecía un
     * efecto que no tenía.
     */
    @Test
    fun `la venta elegida para la promesa es la cuenta de la visita`() = runTest {
        adaptador.registrar(
            visita(
                // Se entró por el CLIENTE: sin cuenta abierta.
                ventaId = null,
                tipoVisita = Constants.PIDE_REAGENDAR,
                promesa = PromesaEstructurada(VENTA_B, hoy.plusDays(3), null)
            )
        )

        assertEquals(VENTA_B, db.visitDao().getVisitById(VISITA_ID).IMPTE_DOCTO_CC_ID)
    }

    /**
     * Sin venta elegida y sin cuenta abierta, cae en la **primera cuenta del
     * cliente** — la misma fila que ya dio la atribución. Lo que NO puede es
     * quedar en 0: ese era el valor que la sacaba de la derivación.
     */
    @Test
    fun `sin venta elegida ni cuenta abierta cae en la primera del cliente`() = runTest {
        adaptador.registrar(visita(ventaId = null, tipoVisita = Constants.NO_SE_ENCONTRABA))

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertNotEquals(
            "nunca 0: ese valor la saca de la derivacion",
            0,
            guardada.IMPTE_DOCTO_CC_ID
        )
        assertEquals(VENTA_A, guardada.IMPTE_DOCTO_CC_ID)
    }

    /** La cuenta abierta gana cuando la promesa no eligió una venta en particular. */
    @Test
    fun `sin venta elegida gana la cuenta por la que se entro`() = runTest {
        adaptador.registrar(
            visita(
                ventaId = VENTA_B,
                tipoVisita = Constants.PIDE_REAGENDAR,
                promesa = PromesaEstructurada(ventaId = null, hoy.plusDays(3), monto = null)
            )
        )

        assertEquals(VENTA_B, db.visitDao().getVisitById(VISITA_ID).IMPTE_DOCTO_CC_ID)
    }

    /**
     * `PROMESA_VENTA_ID` se sigue escribiendo aparte: distingue "la promesa fue
     * sobre esta cuenta" de "la visita se abrió sobre esta otra".
     */
    @Test
    fun `la promesa conserva su propia venta aunque la visita se abriera en otra`() = runTest {
        adaptador.registrar(
            visita(
                ventaId = VENTA_A,
                tipoVisita = Constants.PIDE_REAGENDAR,
                promesa = PromesaEstructurada(VENTA_B, hoy.plusDays(3), null)
            )
        )

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertEquals(VENTA_B, guardada.PROMESA_VENTA_ID)
        assertEquals(VENTA_B, guardada.IMPTE_DOCTO_CC_ID)
    }

    /**
     * **La cuenta por defecto no depende del orden en que la consulta devuelva
     * las ventas.**
     *
     * `SaleDao.getByClientId` agrupa por `DOCTO_CC_ID` y no lleva `ORDER BY`, y
     * "SQLite suele emitir en orden ascendente" no es una garantía. Aquí el
     * orden de la fuente se invierte de verdad —un DAO que delega en el real y
     * devuelve la lista al revés— y la venta elegida tiene que ser la misma. Es
     * el mismo desempate explícito que la Task 17 tuvo que agregar.
     */
    @Test
    fun `la cuenta por defecto no depende del orden de la consulta`() = runTest {
        adaptador.registrar(visita(ventaId = null, tipoVisita = Constants.NO_SE_ENCONTRABA))
        val conElOrdenDeLaBase = db.visitDao().getVisitById(VISITA_ID).IMPTE_DOCTO_CC_ID

        db.visitDao().deleteAllVisits()
        val alReves = adaptadorCon(saleDao = SaleDaoAlReves(db.saleDao())) { cobrador }
        alReves.registrar(visita(ventaId = null, tipoVisita = Constants.NO_SE_ENCONTRABA))
        val conElOrdenInvertido = db.visitDao().getVisitById(VISITA_ID).IMPTE_DOCTO_CC_ID

        assertEquals(
            "la venta elegida no puede depender del orden de la fuente",
            conElOrdenDeLaBase,
            conElOrdenInvertido
        )
        // Y el desempate es nombrado, no incidental: la cuenta más vieja.
        assertEquals(VENTA_A, conElOrdenDeLaBase)
    }

    /**
     * La atribución (`COBRADOR`, `ZONA_CLIENTE_ID`) sale de la MISMA fila, así
     * que tampoco puede bailar entre corridas.
     */
    @Test
    fun `la atribucion tampoco depende del orden de la consulta`() = runTest {
        val alReves = adaptadorCon(saleDao = SaleDaoAlReves(db.saleDao())) { cobrador }

        alReves.registrar(visita(ventaId = null))

        val guardada = db.visitDao().getVisitById(VISITA_ID)
        assertEquals("Efraín Domínguez Reyes", guardada.COBRADOR)
        assertEquals(21, guardada.ZONA_CLIENTE_ID)
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

        db.visitDao().deleteUploadedVisits(
            conservarDesde = "2027-01-01",
            comprobantesDesde = "2027-01-01T00:00:00Z"
        )

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

    /**
     * **El `catch` del adaptador, contra una transacción de Room que de verdad
     * revienta** (regla 4 de la norma de errores: la emisión se prueba, no se
     * declara).
     *
     * El DAO de recomendaciones lanza dentro de `withTransaction`, así que la
     * transacción se revierte entera: no queda visita, **no queda trabajo
     * encolado** —esto es lo que arregló sacar el encolado de la transacción— y
     * el error viaja con su código y con el NOMBRE de la clase de la excepción,
     * nunca su texto.
     */
    @Test
    fun `si la transaccion revienta no queda visita, ni encolado, y se reporta`() = runTest {
        db.visitRecommendationDao().guardar(recomendacion())
        val conDaoRoto = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), encolador, clock),
            recomendaciones = DaoQueRevienta(db.visitRecommendationDao()),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { cobrador }
        )

        val resultado = conDaoRoto.registrar(visita(recomendacionId = REC_ID))

        assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
        assertNull(
            "la transaccion no puede dejar media visita",
            runCatching { db.visitDao().getVisitById(VISITA_ID) }.getOrNull()
        )
        assertTrue(
            "un trabajo encolado sin fila despertaria a buscar nada",
            encolador.encoladas.isEmpty()
        )
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO
        }
        assertEquals("IllegalStateException", evento.props[VisitasTelemetria.PROP_EXCEPCION])
    }

    /**
     * **Una cancelación no puede separar la escritura del encolado.**
     *
     * `registrar` corre bajo el `viewModelScope` de la pantalla, que se cancela
     * en cuanto el cobrador navega hacia atrás. Sacar el encolado de la
     * transacción abrió esa ventana: `withTransaction` termina en un
     * `withContext`, que **lanza al reanudar si el job se canceló mientras el
     * bloque corría, aunque el bloque haya terminado bien** — con la transacción
     * ya comiteada. El resultado sería una visita escrita y jamás encolada, que
     * es exactamente el defecto que la Task 5 existió para arreglar.
     *
     * El fake cancela el job como ÚLTIMA escritura de la transacción, que es
     * justo el instante en que la ventana se abre. Se afirman las DOS mitades:
     * la visita quedó escrita **y** quedó encolada.
     */
    @Test
    fun `una cancelacion no deja la visita escrita y sin encolar`() = runTest {
        db.visitRecommendationDao().guardar(recomendacion())
        val propio = Job()
        val conCancelacion = adaptadorCon(
            recomendaciones = DaoQueCancela(db.visitRecommendationDao(), propio)
        ) { cobrador }

        // `join()` y no `advanceUntilIdle()`: la transacción de Room corre en su
        // propio `transactionExecutor`, fuera del scheduler del test, así que
        // adelantar el reloj virtual devolvería el control con la escritura
        // todavía en vuelo y la prueba mediría antes de tiempo.
        launch(propio) { conCancelacion.registrar(visita(recomendacionId = REC_ID)) }.join()

        assertNotNull(
            "la transaccion alcanzo a comitear: la visita esta escrita",
            runCatching { db.visitDao().getVisitById(VISITA_ID) }.getOrNull()
        )
        assertEquals(
            "una visita escrita y sin encolar es el defecto de la Task 5 por otra puerta",
            listOf(VISITA_ID),
            encolador.encoladas
        )
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
        recomendacionId: String? = null,
        comprobantes: List<ComprobanteDeVisita> = emptyList()
    ) = VisitaARegistrar(
        visitaId = id,
        clienteId = clienteId,
        ventaId = ventaId,
        tipoVisita = tipoVisita,
        nota = nota,
        promesa = promesa,
        cita = cita,
        ubicacion = ubicacion,
        recomendacionId = recomendacionId,
        comprobantes = comprobantes
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

    /**
     * Fake a mano (sin MockK) que **cancela el job al ligar la recomendación**,
     * que es la última escritura de la transacción — el instante exacto en el
     * que se abre la ventana entre el commit y el encolado. No lanza: la
     * transacción termina bien y la cancelación queda pedida.
     */
    private class DaoQueCancela(
        private val real: VisitRecommendationDao,
        private val job: Job
    ) : VisitRecommendationDao by real {
        override suspend fun ligarConVisita(id: String, visitaId: String): Int {
            val ligadas = real.ligarConVisita(id, visitaId)
            job.cancel()
            return ligadas
        }
    }

    /**
     * Fake a mano que devuelve las ventas del cliente **al revés**. La consulta
     * real no lleva `ORDER BY`, así que este es el orden que la base podría
     * emitir cualquier día sin avisar.
     */
    private class SaleDaoAlReves(private val real: SaleDao) : SaleDao by real {
        override suspend fun getByClientId(clientId: Int) = real.getByClientId(clientId).reversed()
    }

    /**
     * Fake a mano (sin MockK) que **revienta al ligar la recomendación**, que es
     * la última escritura de la transacción. Delega lo demás en el DAO real.
     */
    private class DaoQueRevienta(
        private val real: VisitRecommendationDao
    ) : VisitRecommendationDao by real {
        override suspend fun ligarConVisita(id: String, visitaId: String): Int =
            throw IllegalStateException("room se cayo a media transaccion")
    }

    // ─── los comprobantes (Task 23) ──────────────────────────────────────────

    /** Sin fotos, no se escribe ninguna fila. */
    @Test
    fun `una visita sin comprobantes no escribe ninguna fila`() = runTest {
        adaptador.registrar(visita())

        assertEquals(emptyList<Any>(), db.visitImageDao().getByVisitaId(VISITA_ID))
    }

    /**
     * Las fotos se escriben con **el id de la imagen** y el de la visita en su
     * propia columna. Cruzarlos mandaría al servidor una foto cuya clave es la
     * de la visita — y, en el reintento, una segunda imagen con la misma clave.
     */
    @Test
    fun `los comprobantes se escriben con el id de la imagen y el de la visita separados`() =
        runTest {
            adaptador.registrar(visita(comprobantes = listOf(foto("IMG-1"))))

            val fila = db.visitImageDao().getByVisitaId(VISITA_ID).single()
            assertEquals("IMG-1", fila.ID)
            assertEquals(VISITA_ID, fila.VISITA_ID)
            assertEquals("/files/comprobante_visita_IMG-1.jpg", fila.URI)
            assertEquals("image/jpeg", fila.MIME)
            assertNull("una foto recien escrita esta pendiente de subir", fila.SUBIDA_EN)
        }

    /** `ORDEN` es la posición de captura: la que decide el `n` de `id_<n>`. */
    @Test
    fun `varios comprobantes conservan su orden de captura`() = runTest {
        adaptador.registrar(
            visita(comprobantes = listOf(foto("IMG-1"), foto("IMG-2"), foto("IMG-3")))
        )

        val filas = db.visitImageDao().getByVisitaId(VISITA_ID)
        assertEquals(listOf("IMG-1", "IMG-2", "IMG-3"), filas.map { it.ID })
        assertEquals(listOf(0, 1, 2), filas.map { it.ORDEN })
    }

    /**
     * **El orden entre las dos escrituras es funcional, no estético.** Encolar
     * es lo que despierta al worker, y el worker sube lo que encuentre en
     * `visita_imagenes` **una sola vez**: al recibir un 2xx marca
     * `GUARDADO_EN_MICROSIP` y ya nadie reintenta. Escribir las fotos después de
     * encolar habría subido la visita sin ellas cada vez que el teléfono tuviera
     * señal en ese instante.
     *
     * Se mide DESDE DENTRO del encolador, que es el único punto donde la
     * pregunta "¿ya estaban escritas?" tiene una respuesta que no depende del
     * orden en que el test mire después.
     */
    @Test
    fun `los comprobantes ya estan escritos cuando se encola el envio`() = runTest {
        var filasAlEncolar = -1
        val espia = EncoladorQueMira {
            filasAlEncolar = db.visitImageDao().getByVisitaId(VISITA_ID).size
        }
        val conEspia = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), espia, clock),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { cobrador }
        )

        conEspia.registrar(visita(comprobantes = listOf(foto("IMG-1"))))

        assertEquals("la foto se escribe ANTES de encolar", 1, filasAlEncolar)
    }

    /**
     * **Si guardar la foto truena, la visita queda escrita y encolada igual.**
     * La foto nunca bloquea el guardado — ni siquiera cuando lo que falla es
     * guardar la foto.
     */
    @Test
    fun `si guardar la foto truena, la visita queda escrita, encolada y se reporta`() = runTest {
        val conDaoRoto = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), encolador, clock),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = DaoDeImagenesQueRevienta(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { cobrador }
        )

        val resultado = conDaoRoto.registrar(visita(comprobantes = listOf(foto("IMG-1"))))

        assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
        assertNotNull("la visita se queda", db.visitDao().getVisitById(VISITA_ID))
        assertEquals(listOf(VISITA_ID), encolador.encoladas)
        val evento = telemetry.recorded.single {
            it.name == VisitasTelemetria.CODE_VISITA_COMPROBANTES_NO_SE_GUARDARON
        }
        assertEquals("IllegalStateException", evento.props[VisitasTelemetria.PROP_EXCEPCION])
    }

    /**
     * **Control positivo del anterior.** El mismo montaje con el DAO real SÍ
     * escribe la fila, así que el "la visita se queda" de arriba no puede ser un
     * adaptador que dejó de escribir fotos por otra razón.
     */
    @Test
    fun `control positivo, el mismo montaje si escribe la fila`() = runTest {
        adaptador.registrar(visita(comprobantes = listOf(foto("IMG-1"))))

        assertEquals(1, db.visitImageDao().getByVisitaId(VISITA_ID).size)
    }

    private fun foto(id: String) = ComprobanteDeVisita(
        id = id,
        archivo = "/files/comprobante_visita_$id.jpg",
        mime = "image/jpeg"
    )

    /** Encolador que ejecuta [alEncolar] en el instante exacto del encolado. */
    private class EncoladorQueMira(private val alEncolar: suspend () -> Unit) : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) {
            kotlinx.coroutines.runBlocking { alEncolar() }
        }
    }

    /** DAO de imágenes que revienta al escribir. Lo demás no hace falta acá. */
    private class DaoDeImagenesQueRevienta : VisitImageDao {
        override suspend fun insertAll(imagenes: List<VisitImageEntity>) =
            throw IllegalStateException("room se cayo al escribir la foto")

        override suspend fun getByVisitaId(visitaId: String): List<VisitImageEntity> = emptyList()

        override suspend fun getPendientesDe(visitaId: String): List<VisitImageEntity> = emptyList()

        override suspend fun marcarSubida(imagenId: String, subidaEn: String) = Unit

        override suspend fun rutasVivas(): List<String> = emptyList()

        override suspend fun huerfanasAnterioresA(limite: String): List<VisitImageEntity> =
            emptyList()

        override suspend fun eliminar(imagenId: String) = Unit
    }

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
