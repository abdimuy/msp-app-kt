package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.cobranza.domain.CuentaDelPeriodo
import com.example.msp_app.core.common.cobranza.domain.DerivacionPeriodo
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.EstadoCuentaDeriver
import com.example.msp_app.core.common.cobranza.domain.IncidenciaCobranza
import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.cobranza.domain.VisitaEnVentana
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.pagos.data.adapter.RoomVisitasAdapter
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import com.example.msp_app.feature.pagos.ui.TratoDelEstado
import com.example.msp_app.feature.visitas.domain.port.CitaEstructurada
import com.example.msp_app.feature.visitas.domain.port.PromesaEstructurada
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **Las guardas de las Tasks 16 y 17, ahora CON dato — de punta a punta.**
 *
 * Aquellas tareas dejaron dos ramas armadas y latentes:
 *
 * - Task 16: `PROMETIO_PROXIMA` sin fecha se pinta *regresas*, y
 *   `CITA_A_UNA_HORA` sin hora también — las dos "sin dato que lo sostenga".
 * - Task 17: el chip de un compromiso lo decide su **fecha** —`fechaPromesa` /
 *   `fechaCita`—, no el tipo de compromiso: vencido o de hoy es *volver a
 *   visitar*, a futuro es *ya no esta semana*.
 *
 * Hasta hoy nadie escribía esos datos, así que **solo se podía probar la rama
 * de la ausencia**. Este test cierra el círculo con las piezas reales: escribe
 * la visita por el adaptador de producción, la vuelve a leer por el adaptador
 * Room de `:feature:pagos`, la deriva con `EstadoCuentaDeriver` y afirma sobre
 * `EstadoCuentaUi` y `SegmentoDeCobranza`. Ninguna pieza está simulada.
 */
class LaPromesaLlegaAlSemaforoTest : RoomTestBase() {

    /** Martes 1-sep-2026, 09:52 en zona de negocio. */
    private val ahora = Instant.parse("2026-09-01T15:52:00Z")
    private val hoy = LocalDate.of(2026, 9, 1)

    /** El lunes que abrió la semana. */
    private val inicioDeSemana = Instant.parse("2026-08-31T06:00:00Z")

    private val clock = FakeClock(ahora)
    private val telemetry = RecordingTelemetry(clock)

    private lateinit var adaptador: RegistroDeVisitaAdapter
    private lateinit var lector: RoomVisitasAdapter

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta()))
        adaptador = RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(
                db.visitDao(),
                db.saleDao(),
                EncoladorMudo(),
                clock
            ),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { User(ID = "u-1", COBRADOR_ID = 7) }
        )
        lector = RoomVisitasAdapter(db.visitDao(), telemetry)
    }

    // ─── Task 16, con dato presente ──────────────────────────────────────────

    /**
     * **Una promesa CON fecha difiere de verdad.** Antes esta rama era
     * inalcanzable: `PIDE_REAGENDAR` llegaba siempre sin fecha y caía en
     * *regresas*.
     */
    @Test
    fun `una promesa con fecha se pinta diferida y deja de pedir trabajo`() = runTest {
        registrarPromesa(hoy.plusDays(3), Money.of(BigDecimal("220")))

        val estado = estadoDerivado()

        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, estado.estado)
        assertEquals(TratoDelEstado.DIFERIDO, EstadoCuentaUi.tratoDe(estado))
        assertEquals("Prometió el 4 sept", EstadoCuentaUi.etiquetaDe(estado))
        assertEquals("No cae esta semana", EstadoCuentaUi.detalleDe(estado))
        assertFalse(EstadoCuentaUi.requiereAtencion(EstadoCuentaUi.tratoDe(estado)))
    }

    /**
     * **Control positivo de la guarda:** sin fecha, la MISMA etiqueta sigue
     * cayendo en *regresas*. Sin esto, la prueba de arriba no distinguiría "la
     * fecha lo difiere" de "esa etiqueta ahora siempre difiere".
     */
    @Test
    fun `la misma etiqueta sin fecha sigue pidiendo regresar`() = runTest {
        registrarSinCompromiso(Constants.PIDE_REAGENDAR)

        val estado = estadoDerivado()

        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, estado.estado)
        assertEquals(TratoDelEstado.REGRESAS, EstadoCuentaUi.tratoDe(estado))
        assertEquals("Prometió sin fecha", EstadoCuentaUi.etiquetaDe(estado))
        assertTrue(EstadoCuentaUi.requiereAtencion(EstadoCuentaUi.tratoDe(estado)))
    }

    /** **Una cita CON hora es una cita**, y muestra la hora. */
    @Test
    fun `una cita con hora se pinta como cita y muestra la hora`() = runTest {
        registrarCita(hoy, LocalTime.of(16, 0))

        val estado = estadoDerivado()

        assertEquals(EstadoCuenta.CITA_A_UNA_HORA, estado.estado)
        assertEquals(TratoDelEstado.CITA, EstadoCuentaUi.tratoDe(estado))
        assertEquals("Cita 16:00", EstadoCuentaUi.etiquetaDe(estado))
        assertEquals("Quedaron de verse", EstadoCuentaUi.detalleDe(estado))
    }

    /** **Control positivo:** una cita SIN hora sigue siendo un pendiente. */
    @Test
    fun `una cita sin hora sigue siendo pendiente`() = runTest {
        registrarCita(hoy.plusDays(2), hora = null)

        val estado = estadoDerivado()

        assertEquals(EstadoCuenta.CITA_A_UNA_HORA, estado.estado)
        assertEquals(TratoDelEstado.REGRESAS, EstadoCuentaUi.tratoDe(estado))
        assertEquals("Cita sin hora", EstadoCuentaUi.etiquetaDe(estado))
        assertTrue(EstadoCuentaUi.requiereAtencion(EstadoCuentaUi.tratoDe(estado)))
    }

    // ─── Task 17, con dato presente ──────────────────────────────────────────

    /**
     * **Una promesa para hoy es trabajo de hoy:** cae en *volver a visitar*, el
     * chip de lo que sigue pidiendo otra pasada. Y en **ninguno** de los otros
     * tres — la partición se cobra aquí también, con el dato que la escribió el
     * adaptador de producción y no uno armado a mano.
     */
    @Test
    fun `una promesa para hoy cae en volver a visitar`() = runTest {
        registrarPromesa(hoy, Money.of(BigDecimal("220")))

        val estado = estadoDerivado()

        assertEquals(listOf(SegmentoDeCobranza.VOLVER_A_VISITAR), chipsDe(estado, hoy))
    }

    /** Una cita de hoy con hora también: es el otro camino al mismo chip. */
    @Test
    fun `una cita de hoy cae en volver a visitar`() = runTest {
        registrarCita(hoy, LocalTime.of(9, 0))

        val estado = estadoDerivado()

        assertEquals(listOf(SegmentoDeCobranza.VOLVER_A_VISITAR), chipsDe(estado, hoy))
    }

    /**
     * **Una promesa futura es un acuerdo, no un pendiente:** cae en *ya no esta
     * semana*. Antes —con `TODOS`/`VENCIDOS`/`HOY`/`SIN_VISITAR`— no caía en
     * ningún chip de trabajo y sólo se veía bajo *todos*; ahora tiene chip
     * propio, que es lo que le permite a `TODOS` desaparecer sin llevarse nada.
     */
    @Test
    fun `una promesa futura cae en ya no esta semana`() = runTest {
        registrarPromesa(hoy.plusDays(3), null)

        val estado = estadoDerivado()

        assertEquals(listOf(SegmentoDeCobranza.YA_NO_ESTA_SEMANA), chipsDe(estado, hoy))
    }

    /**
     * **La MISMA promesa, dos días después, cambia de chip.** Es el control
     * positivo de los tres de arriba: lo único que se mueve es el reloj, así que
     * lo que decide el chip es la fecha y no el tipo de compromiso. La captura de
     * hoy no puede crear una promesa vencida (`ReglasDeLaVisita` la bloquea),
     * pero una hecha antes llega a su fecha y vence — y ahí tiene que reaparecer
     * como trabajo pendiente, no quedarse archivada.
     */
    @Test
    fun `la misma promesa pasa de ya no esta semana a volver a visitar al vencer`() = runTest {
        registrarPromesa(hoy.plusDays(2), Money.of(BigDecimal("220")))
        val estado = estadoDerivado()

        assertEquals(listOf(SegmentoDeCobranza.YA_NO_ESTA_SEMANA), chipsDe(estado, hoy))
        assertEquals(
            listOf(SegmentoDeCobranza.VOLVER_A_VISITAR),
            chipsDe(estado, hoy.plusDays(4))
        )
    }

    /** Los chips en los que cae [estado] un día dado. Debe ser siempre exactamente uno. */
    private fun chipsDe(estado: EstadoDelPeriodo, dia: LocalDate): List<SegmentoDeCobranza> =
        SegmentoDeCobranza.entries.filter { it.contiene(estado, dia) }

    // ─── la promesa capturada desde el CLIENTE ───────────────────────────────

    /**
     * **Una promesa capturada desde el detalle de CLIENTE llega a la
     * derivación.**
     *
     * Es el punto de entrada que la ruta sostiene a propósito ("el cobrador toca
     * una puerta, no una venta") y era el que se rompía: la visita se escribía
     * con `IMPTE_DOCTO_CC_ID = 0`, la lectura lo traducía a `null` y el deriver
     * mandaba las visitas de alcance VENTA sin venta a `sueltas` —las contaba
     * como `huerfanas` y **no las indexaba**—, así que `PROMETIO_PROXIMA`, que
     * es de alcance VENTA, se escribía en Room y después desaparecía del
     * semáforo.
     */
    @Test
    fun `una promesa capturada desde el cliente llega al semaforo`() = runTest {
        adaptador.registrar(
            VisitaARegistrar(
                visitaId = VISITA_ID,
                clienteId = CLIENTE_ID,
                // Sin cuenta abierta: se entró por el cliente.
                ventaId = null,
                tipoVisita = Constants.PIDE_REAGENDAR,
                nota = null,
                promesa = PromesaEstructurada(
                    VENTA_ID,
                    hoy.plusDays(3),
                    Money.of(BigDecimal("220"))
                )
            )
        )

        val derivacion = derivar()
        val estado = EstadoDelPeriodo.de(derivacion.porVenta.getValue(VENTA_ID))

        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, estado.estado)
        assertEquals(TratoDelEstado.DIFERIDO, EstadoCuentaUi.tratoDe(estado))
        assertEquals(hoy.plusDays(3), estado.fechaPromesa)
    }

    /**
     * Y **no se reporta como huérfana**: la incidencia
     * `cobranza_visita_sin_venta` existe para las visitas que no se pueden
     * aplicar a nada, y esta sí se puede.
     */
    @Test
    fun `esa promesa no cuenta como visita sin venta`() = runTest {
        adaptador.registrar(
            VisitaARegistrar(
                visitaId = VISITA_ID,
                clienteId = CLIENTE_ID,
                ventaId = null,
                tipoVisita = Constants.PIDE_REAGENDAR,
                nota = null,
                promesa = PromesaEstructurada(VENTA_ID, hoy.plusDays(3), null)
            )
        )

        assertTrue(
            "una promesa aplicable no es una huerfana",
            derivar().incidencias.none {
                it.code == IncidenciaCobranza.CODE_VISITA_SIN_VENTA
            }
        )
    }

    // ─── el camino completo ──────────────────────────────────────────────────

    private suspend fun registrarPromesa(fecha: LocalDate, monto: Money?) {
        adaptador.registrar(
            VisitaARegistrar(
                visitaId = VISITA_ID,
                clienteId = CLIENTE_ID,
                ventaId = VENTA_ID,
                tipoVisita = Constants.PIDE_REAGENDAR,
                nota = null,
                promesa = PromesaEstructurada(VENTA_ID, fecha, monto)
            )
        )
    }

    private suspend fun registrarCita(fecha: LocalDate, hora: LocalTime?) {
        adaptador.registrar(
            VisitaARegistrar(
                visitaId = VISITA_ID,
                clienteId = CLIENTE_ID,
                ventaId = VENTA_ID,
                tipoVisita = Constants.PIDE_TIEMPO,
                nota = null,
                cita = CitaEstructurada(fecha, hora)
            )
        )
    }

    private suspend fun registrarSinCompromiso(tipoVisita: String) {
        adaptador.registrar(
            VisitaARegistrar(
                visitaId = VISITA_ID,
                clienteId = CLIENTE_ID,
                ventaId = VENTA_ID,
                tipoVisita = tipoVisita,
                nota = null
            )
        )
    }

    /**
     * Lee lo que quedó escrito y lo deriva — el mismo camino que recorre la
     * lista del cobrador, sin un solo dato armado a mano.
     */
    private suspend fun estadoDerivado(): EstadoDelPeriodo =
        EstadoDelPeriodo.de(derivar().porVenta.getValue(VENTA_ID))

    /** La derivación completa, incluidas sus incidencias. */
    private suspend fun derivar(): DerivacionPeriodo = EstadoCuentaDeriver.derivar(
        cuentas = listOf(CuentaDelPeriodo(VENTA_ID, CLIENTE_ID, BigDecimal("220"))),
        pagos = emptyList(),
        visitas = lector.visitasDelCliente(CLIENTE_ID).map { it.aVisitaEnVentana() },
        ventana = VentanaCobro.desdeInicioSemana(inicioDeSemana, clock)
    )

    private fun VisitaDelCliente.aVisitaEnVentana() = VisitaEnVentana(
        clienteId = clienteId,
        ventaId = ventaId,
        tipoVisita = tipoVisita,
        fechaHora = fecha,
        fechaPromesa = fechaPromesa,
        montoPrometido = montoPrometido?.amount,
        fechaCita = fechaCita,
        horaCita = horaCita
    )

    private fun venta() = SaleEntity(
        DOCTO_CC_ACR_ID = VENTA_ID,
        DOCTO_CC_ID = VENTA_ID + 1,
        FOLIO = "V-5188",
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

    private class EncoladorMudo : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) = Unit
    }

    private companion object {
        const val VISITA_ID = "visita-victoria-1"
        const val CLIENTE_ID = 5021
        const val VENTA_ID = 77188
    }
}
