package com.example.msp_app.core.common.cobranza.domain

import com.example.msp_app.core.testing.time.FakeClock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La derivación completa: el borde del dinero, los ocho estados, la propagación
 * por cliente y "sin tocar" al abrir la semana.
 *
 * Sin MockK y sin Mockito: todo es dominio puro y el único doble es [FakeClock],
 * que ya existe en `:core:testing`. El reloj importa — un borde de periodo es
 * justo donde alguien alcanza `System.currentTimeMillis()`.
 */
class EstadoCuentaDeriverTest {

    private val lunes = Instant.parse("2026-08-31T06:00:00Z")
    private val clock = FakeClock(Instant.parse("2026-09-03T18:00:00Z"))
    private val ventana = VentanaCobro.desdeInicioSemana(lunes, clock)

    private val ventaGuadalupe = 5001
    private val ventaGuadalupeDos = 5002
    private val clienteGuadalupe = 900

    private fun cuenta(
        ventaId: Int = ventaGuadalupe,
        clienteId: Int = clienteGuadalupe,
        parcialidad: String = "300"
    ) = CuentaDelPeriodo(ventaId, clienteId, BigDecimal(parcialidad))

    private fun pago(
        ventaId: Int = ventaGuadalupe,
        importe: String,
        formaCobroId: Int = FORMA_EFECTIVO,
        fechaHora: Instant = Instant.parse("2026-09-02T17:00:00Z")
    ) = PagoEnVentana(ventaId, BigDecimal(importe), formaCobroId, fechaHora)

    private fun visita(
        tipoVisita: String,
        ventaId: Int? = ventaGuadalupe,
        clienteId: Int = clienteGuadalupe,
        fechaHora: Instant = Instant.parse("2026-09-02T16:00:00Z")
    ) = VisitaEnVentana(clienteId, ventaId, tipoVisita, fechaHora)

    private fun estadoDe(
        cuentas: List<CuentaDelPeriodo> = listOf(cuenta()),
        pagos: List<PagoEnVentana> = emptyList(),
        visitas: List<VisitaEnVentana> = emptyList(),
        ventaId: Int = ventaGuadalupe
    ): EstadoCuenta = EstadoCuentaDeriver
        .derivar(cuentas, pagos, visitas, ventana)
        .porVenta
        .getValue(ventaId)
        .estado

    // ─────────────────────────────────────────────────────────────────────
    // EL BORDE DEL DINERO — los tres casos exactos del brief
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `borde exacto - suma igual a PARCIALIDAD es Pago`() {
        assertEquals(
            EstadoCuenta.PAGO,
            EstadoCuentaDeriver.estadoPorDinero(BigDecimal("300"), BigDecimal("300"))
        )
    }

    @Test
    fun `borde exacto - suma igual a PARCIALIDAD menos 1 es Abono parcial`() {
        assertEquals(
            EstadoCuenta.ABONO_PARCIAL,
            EstadoCuentaDeriver.estadoPorDinero(BigDecimal("299"), BigDecimal("300"))
        )
    }

    @Test
    fun `borde exacto - suma mayor que PARCIALIDAD es Pago`() {
        assertEquals(
            EstadoCuenta.PAGO,
            EstadoCuentaDeriver.estadoPorDinero(BigDecimal("301"), BigDecimal("300"))
        )
    }

    @Test
    fun `borde exacto - un centavo menos que PARCIALIDAD sigue siendo Abono parcial`() {
        assertEquals(
            EstadoCuenta.ABONO_PARCIAL,
            EstadoCuentaDeriver.estadoPorDinero(BigDecimal("299.99"), BigDecimal("300"))
        )
    }

    @Test
    fun `la escala no cambia el veredicto - 300 y 300 punto 00 son la misma cantidad`() {
        assertEquals(
            EstadoCuenta.PAGO,
            EstadoCuentaDeriver.estadoPorDinero(BigDecimal("300.00"), BigDecimal("300"))
        )
    }

    @Test
    fun `sin dinero el borde no aplica y devuelve null, no un estado`() {
        // Devolver SIN_TOCAR aqui borraria un SE_NEGO para quien llame directo.
        assertNull(EstadoCuentaDeriver.estadoPorDinero(BigDecimal.ZERO, BigDecimal("300")))
        assertNull(EstadoCuentaDeriver.estadoPorDinero(BigDecimal("-50"), BigDecimal("300")))
    }

    @Test
    fun `sin dinero pero con visita, derivar conserva la visita y no la pisa con sin tocar`() {
        assertEquals(
            EstadoCuenta.SE_NEGO,
            estadoDe(visitas = listOf(visita(TipoVisitaCatalogo.FUE_GROSERO)))
        )
    }

    @Test
    fun `parcialidad no positiva no permite decidir y cae en el estado conservador`() {
        assertEquals(
            EstadoCuenta.ABONO_PARCIAL,
            EstadoCuentaDeriver.estadoPorDinero(BigDecimal("500"), BigDecimal.ZERO)
        )
    }

    @Test
    fun `el borde exacto tambien vale a traves de derivar`() {
        assertEquals(EstadoCuenta.PAGO, estadoDe(pagos = listOf(pago(importe = "300"))))
        assertEquals(EstadoCuenta.ABONO_PARCIAL, estadoDe(pagos = listOf(pago(importe = "299"))))
        assertEquals(EstadoCuenta.PAGO, estadoDe(pagos = listOf(pago(importe = "301"))))
    }

    @Test
    fun `dos abonos parciales que juntos alcanzan la parcialidad son Pago`() {
        val estado = estadoDe(
            pagos = listOf(
                pago(importe = "150", fechaHora = Instant.parse("2026-09-01T15:00:00Z")),
                pago(importe = "150", fechaHora = Instant.parse("2026-09-02T15:00:00Z"))
            )
        )
        assertEquals(EstadoCuenta.PAGO, estado)
    }

    @Test
    fun `la condonacion no cuenta como abono - no pinta Pago donde nadie pago`() {
        val estado =
            estadoDe(pagos = listOf(pago(importe = "300", formaCobroId = FORMA_CONDONACION)))
        assertEquals(EstadoCuenta.SIN_TOCAR, estado)
    }

    @Test
    fun `cheque y transferencia si cuentan como cobranza`() {
        assertEquals(
            EstadoCuenta.PAGO,
            estadoDe(pagos = listOf(pago(importe = "300", formaCobroId = FORMA_CHEQUE)))
        )
        assertEquals(
            EstadoCuenta.PAGO,
            estadoDe(pagos = listOf(pago(importe = "300", formaCobroId = FORMA_TRANSFERENCIA)))
        )
    }

    @Test
    fun `un pago de otra venta no se suma a esta`() {
        val estado = estadoDe(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaGuadalupeDos)),
            pagos = listOf(pago(ventaId = ventaGuadalupeDos, importe = "300"))
        )
        assertEquals(EstadoCuenta.SIN_TOCAR, estado)
    }

    @Test
    fun `el dinero manda sobre la visita - pago despues de que no estaba`() {
        val estado = estadoDe(
            pagos = listOf(
                pago(importe = "300", fechaHora = Instant.parse("2026-09-02T23:00:00Z"))
            ),
            visitas = listOf(visita(TipoVisitaCatalogo.NO_SE_ENCONTRABA))
        )
        assertEquals(EstadoCuenta.PAGO, estado)
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOS OCHO ESTADOS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `estado 1 de 8 - Pago`() {
        assertEquals(EstadoCuenta.PAGO, estadoDe(pagos = listOf(pago(importe = "300"))))
    }

    @Test
    fun `estado 2 de 8 - Abono parcial`() {
        assertEquals(EstadoCuenta.ABONO_PARCIAL, estadoDe(pagos = listOf(pago(importe = "100"))))
    }

    @Test
    fun `estado 3 de 8 - Visite vuelvo`() {
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            estadoDe(visitas = listOf(visita(TipoVisitaCatalogo.NO_RESPONDE)))
        )
    }

    @Test
    fun `estado 4 de 8 - Prometio proxima`() {
        assertEquals(
            EstadoCuenta.PROMETIO_PROXIMA,
            estadoDe(visitas = listOf(visita(TipoVisitaCatalogo.PIDE_REAGENDAR)))
        )
    }

    @Test
    fun `estado 5 de 8 - Se nego`() {
        assertEquals(
            EstadoCuenta.SE_NEGO,
            estadoDe(visitas = listOf(visita(TipoVisitaCatalogo.FUE_GROSERO)))
        )
    }

    /**
     * **El estado que estaba muerto, ahora vivo.** Ningún literal produce
     * `CITA_A_UNA_HORA` —por diseño: no se reconstruye desde texto libre— y por
     * eso lo produce el DATO. La Task 19 escribe `CITA_FECHA`, y con día la
     * visita es una cita venga con el literal que venga.
     */
    @Test
    fun `estado 6 de 8 - Cita a una hora, derivada del dia de la cita`() {
        assertEquals(VisitScope.CLIENTE, EstadoCuenta.CITA_A_UNA_HORA.alcance)
        val conCita = visita(TipoVisitaCatalogo.PIDE_TIEMPO).copy(
            fechaCita = LocalDate.of(2026, 9, 3),
            horaCita = LocalTime.of(16, 0)
        )

        assertEquals(EstadoCuenta.CITA_A_UNA_HORA, estadoDe(visitas = listOf(conCita)))
    }

    /**
     * **Control positivo del literal:** el MISMO `PIDE_TIEMPO` sin día de cita
     * sigue derivando "visité, vuelvo", exactamente como antes de la Task 19.
     * Sin esta prueba, la de arriba no distinguiría "el día lo convierte en
     * cita" de "ese literal ahora siempre es cita" — y ninguna fila histórica
     * puede cambiar de significado.
     */
    @Test
    fun `el mismo literal sin dia de cita sigue siendo visite vuelvo`() {
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            estadoDe(visitas = listOf(visita(TipoVisitaCatalogo.PIDE_TIEMPO)))
        )
    }

    /**
     * Y la cita se **propaga a todas las cuentas del cliente**: es un hecho del
     * domicilio. El alcance sale del estado, así que no puede contradecirlo.
     */
    @Test
    fun `una cita se propaga a todas las ventas del cliente`() {
        val conCita = visita(TipoVisitaCatalogo.PIDE_TIEMPO, ventaId = ventaGuadalupe).copy(
            fechaCita = LocalDate.of(2026, 9, 3)
        )
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaGuadalupeDos)),
            pagos = emptyList(),
            visitas = listOf(conCita),
            ventana = ventana
        )

        assertEquals(
            EstadoCuenta.CITA_A_UNA_HORA,
            derivacion.porVenta.getValue(ventaGuadalupeDos).estado
        )
    }

    /**
     * La fecha y la hora de la cita **viajan** con el resultado: la pantalla
     * necesita las dos para decidir si es una cita o un pendiente (Task 16) y si
     * cae hoy (Task 17).
     */
    @Test
    fun `el dia y la hora de la cita llegan al resultado`() {
        val conCita = visita(TipoVisitaCatalogo.PIDE_TIEMPO).copy(
            fechaCita = LocalDate.of(2026, 9, 3),
            horaCita = LocalTime.of(16, 30)
        )
        val resultado = EstadoCuentaDeriver
            .derivar(listOf(cuenta()), emptyList(), listOf(conCita), ventana)
            .porVenta
            .getValue(ventaGuadalupe)

        assertEquals(LocalDate.of(2026, 9, 3), resultado.fechaCita)
        assertEquals(LocalTime.of(16, 30), resultado.horaCita)
    }

    /**
     * La promesa estructurada también llega entera: fecha Y monto. Es el par que
     * vuelve medible el cumplimiento.
     */
    @Test
    fun `la fecha y el monto de la promesa llegan al resultado`() {
        val conPromesa = visita(TipoVisitaCatalogo.PIDE_REAGENDAR).copy(
            fechaPromesa = LocalDate.of(2026, 9, 4),
            montoPrometido = BigDecimal("220.00")
        )
        val resultado = EstadoCuentaDeriver
            .derivar(listOf(cuenta()), emptyList(), listOf(conPromesa), ventana)
            .porVenta
            .getValue(ventaGuadalupe)

        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, resultado.estado)
        assertEquals(LocalDate.of(2026, 9, 4), resultado.fechaPromesa)
        assertEquals(0, BigDecimal("220.00").compareTo(resultado.montoPrometido))
    }

    @Test
    fun `estado 7 de 8 - No estaba`() {
        assertEquals(
            EstadoCuenta.NO_ESTABA,
            estadoDe(visitas = listOf(visita(TipoVisitaCatalogo.CASA_CERRADA, ventaId = null)))
        )
    }

    @Test
    fun `estado 8 de 8 - Sin tocar`() {
        assertEquals(EstadoCuenta.SIN_TOCAR, estadoDe())
    }

    @Test
    fun `los ocho estados quedan cubiertos por esta clase de tests`() {
        val derivables = setOf(
            EstadoCuenta.PAGO,
            EstadoCuenta.ABONO_PARCIAL,
            EstadoCuenta.VISITE_VUELVO,
            EstadoCuenta.PROMETIO_PROXIMA,
            EstadoCuenta.SE_NEGO,
            EstadoCuenta.NO_ESTABA,
            EstadoCuenta.SIN_TOCAR
        )
        assertEquals(
            setOf(EstadoCuenta.CITA_A_UNA_HORA),
            EstadoCuenta.entries.toSet() - derivables
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // "SIN TOCAR" AL ABRIR SEMANA NUEVA
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `al abrir semana nueva la cuenta cobrada la semana pasada vuelve a sin tocar`() {
        val semanaPasada = Instant.parse("2026-08-26T17:00:00Z")
        val estado = estadoDe(
            pagos = listOf(pago(importe = "300", fechaHora = semanaPasada)),
            visitas = listOf(visita(TipoVisitaCatalogo.FUE_GROSERO, fechaHora = semanaPasada))
        )
        assertEquals(EstadoCuenta.SIN_TOCAR, estado)
    }

    @Test
    fun `un pago exactamente en el instante de apertura si cuenta`() {
        assertEquals(
            EstadoCuenta.PAGO,
            estadoDe(pagos = listOf(pago(importe = "300", fechaHora = lunes)))
        )
    }

    @Test
    fun `un pago un segundo antes de la apertura no cuenta`() {
        val estado =
            estadoDe(pagos = listOf(pago(importe = "300", fechaHora = lunes.minusSeconds(1))))
        assertEquals(EstadoCuenta.SIN_TOCAR, estado)
    }

    @Test
    fun `un pago en el instante de cierre si cuenta, uno posterior no`() {
        assertEquals(
            EstadoCuenta.PAGO,
            estadoDe(pagos = listOf(pago(importe = "300", fechaHora = clock.now())))
        )
        assertEquals(
            EstadoCuenta.SIN_TOCAR,
            estadoDe(pagos = listOf(pago(importe = "300", fechaHora = clock.now().plusSeconds(1))))
        )
    }

    @Test
    fun `al avanzar el reloj la ventana se estira sola`() {
        val futuro = Instant.parse("2026-09-04T12:00:00Z")
        val relojQueAvanza = FakeClock(clock.now())
        val antes = VentanaCobro.desdeInicioSemana(lunes, relojQueAvanza)
        assertTrue(!antes.contiene(futuro))

        relojQueAvanza.advanceDays(2)
        val despues = VentanaCobro.desdeInicioSemana(lunes, relojQueAvanza)
        assertTrue(despues.contiene(futuro))
    }

    @Test
    fun `una cuenta sin nada siempre aparece en el resultado`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaGuadalupeDos)),
            pagos = emptyList(),
            visitas = emptyList(),
            ventana = ventana
        )
        assertEquals(2, derivacion.porVenta.size)
        assertTrue(derivacion.porVenta.values.all { it.estado == EstadoCuenta.SIN_TOCAR })
        assertTrue(derivacion.incidencias.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────
    // PROPAGACIÓN POR CLIENTE (descansa en la Task 13)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `no estaba se propaga a todas las ventas del cliente`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaGuadalupeDos)),
            pagos = emptyList(),
            visitas = listOf(visita(TipoVisitaCatalogo.NO_SE_ENCONTRABA, ventaId = ventaGuadalupe)),
            ventana = ventana
        )
        assertEquals(EstadoCuenta.NO_ESTABA, derivacion.porVenta.getValue(ventaGuadalupe).estado)
        assertEquals(EstadoCuenta.NO_ESTABA, derivacion.porVenta.getValue(ventaGuadalupeDos).estado)
    }

    @Test
    fun `no estaba no toca las ventas de otro cliente`() {
        val ventaRamirez = 7001
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaRamirez, clienteId = 901)),
            pagos = emptyList(),
            visitas = listOf(visita(TipoVisitaCatalogo.SOLO_MENORES)),
            ventana = ventana
        )
        assertEquals(EstadoCuenta.NO_ESTABA, derivacion.porVenta.getValue(ventaGuadalupe).estado)
        assertEquals(EstadoCuenta.SIN_TOCAR, derivacion.porVenta.getValue(ventaRamirez).estado)
    }

    @Test
    fun `se nego toca solo su venta - alcance venta no propaga`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaGuadalupeDos)),
            pagos = emptyList(),
            visitas = listOf(
                visita(TipoVisitaCatalogo.TIENE_PERO_NO_PAGA, ventaId = ventaGuadalupe)
            ),
            ventana = ventana
        )
        assertEquals(EstadoCuenta.SE_NEGO, derivacion.porVenta.getValue(ventaGuadalupe).estado)
        assertEquals(EstadoCuenta.SIN_TOCAR, derivacion.porVenta.getValue(ventaGuadalupeDos).estado)
    }

    @Test
    fun `la visita de venta gana sobre la de cliente en su propia venta, la otra hereda la de cliente`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(), cuenta(ventaId = ventaGuadalupeDos)),
            pagos = emptyList(),
            visitas = listOf(
                visita(
                    TipoVisitaCatalogo.NO_SE_ENCONTRABA,
                    ventaId = null,
                    fechaHora = Instant.parse("2026-09-02T15:00:00Z")
                ),
                visita(
                    TipoVisitaCatalogo.FUE_GROSERO,
                    ventaId = ventaGuadalupe,
                    fechaHora = Instant.parse("2026-09-02T16:00:00Z")
                )
            ),
            ventana = ventana
        )
        assertEquals(EstadoCuenta.SE_NEGO, derivacion.porVenta.getValue(ventaGuadalupe).estado)
        assertEquals(EstadoCuenta.NO_ESTABA, derivacion.porVenta.getValue(ventaGuadalupeDos).estado)
    }

    @Test
    fun `gana la visita mas reciente del periodo`() {
        val estado = estadoDe(
            visitas = listOf(
                visita(
                    TipoVisitaCatalogo.FUE_GROSERO,
                    fechaHora = Instant.parse("2026-09-01T15:00:00Z")
                ),
                visita(
                    TipoVisitaCatalogo.PIDE_REAGENDAR,
                    fechaHora = Instant.parse("2026-09-02T15:00:00Z")
                )
            )
        )
        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, estado)
    }

    @Test
    fun `una visita fuera de la ventana no decide nada`() {
        val estado = estadoDe(
            visitas = listOf(
                visita(
                    TipoVisitaCatalogo.FUE_GROSERO,
                    fechaHora = Instant.parse("2026-08-20T15:00:00Z")
                )
            )
        )
        assertEquals(EstadoCuenta.SIN_TOCAR, estado)
    }

    // ─────────────────────────────────────────────────────────────────────
    // TODO LITERAL QUE PUEDE LLEGAR ATERRIZA EN UN ESTADO
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `los 14 literales del catalogo producen un estado al derivar`() {
        TipoVisitaCatalogo.LITERALES.forEach { literal ->
            val estado = estadoDe(visitas = listOf(visita(literal, ventaId = ventaGuadalupe)))
            assertTrue("'$literal' no aterrizo en ningun estado", estado in EstadoCuenta.entries)
            assertEquals("'$literal' quedo como sin tocar", false, estado == EstadoCuenta.SIN_TOCAR)
        }
    }

    @Test
    fun `un literal retirado de un telefono viejo aterriza en se nego, no en la nada`() {
        val estado = estadoDe(
            visitas = listOf(visita(TipoVisitaCatalogo.LEGACY_DIJO_QUE_NO_VA_A_PAGAR))
        )
        assertEquals(EstadoCuenta.SE_NEGO, estado)
    }

    @Test
    fun `una fila vieja con TIPO_VISITA vacio aterriza en visite vuelvo`() {
        assertEquals(EstadoCuenta.VISITE_VUELVO, estadoDe(visitas = listOf(visita(""))))
    }

    // ─────────────────────────────────────────────────────────────────────
    // INCIDENCIAS — la norma de errores, con códigos nombrados y grepeables
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `un tipo fuera de catalogo se reporta con su codigo`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta()),
            pagos = emptyList(),
            visitas = listOf(visita("Se lo llevo la grua"), visita("")),
            ventana = ventana
        )
        assertEquals(
            listOf(IncidenciaCobranza(IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO, 2)),
            derivacion.incidencias
        )
        assertEquals(
            "cobranza_tipo_visita_fuera_de_catalogo",
            IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO
        )
    }

    @Test
    fun `una parcialidad no positiva con abono se reporta con su codigo`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(parcialidad = "0")),
            pagos = listOf(pago(importe = "500")),
            visitas = emptyList(),
            ventana = ventana
        )
        assertEquals(
            listOf(IncidenciaCobranza(IncidenciaCobranza.CODE_PARCIALIDAD_NO_POSITIVA, 1)),
            derivacion.incidencias
        )
        assertEquals(
            EstadoCuenta.ABONO_PARCIAL,
            derivacion.porVenta.getValue(ventaGuadalupe).estado
        )
        assertEquals(
            "cobranza_parcialidad_no_positiva",
            IncidenciaCobranza.CODE_PARCIALIDAD_NO_POSITIVA
        )
    }

    @Test
    fun `una parcialidad no positiva sin abono no genera ruido`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta(parcialidad = "0")),
            pagos = emptyList(),
            visitas = emptyList(),
            ventana = ventana
        )
        assertTrue(derivacion.incidencias.isEmpty())
    }

    @Test
    fun `una visita de alcance venta sin venta ligada se reporta con su codigo`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta()),
            pagos = emptyList(),
            visitas = listOf(visita(TipoVisitaCatalogo.FUE_GROSERO, ventaId = null)),
            ventana = ventana
        )
        assertEquals(
            listOf(IncidenciaCobranza(IncidenciaCobranza.CODE_VISITA_SIN_VENTA, 1)),
            derivacion.incidencias
        )
        assertEquals(EstadoCuenta.SIN_TOCAR, derivacion.porVenta.getValue(ventaGuadalupe).estado)
        assertEquals("cobranza_visita_sin_venta", IncidenciaCobranza.CODE_VISITA_SIN_VENTA)
    }

    @Test
    fun `los tres codigos de incidencia son distintos entre si`() {
        val codigos = setOf(
            IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO,
            IncidenciaCobranza.CODE_PARCIALIDAD_NO_POSITIVA,
            IncidenciaCobranza.CODE_VISITA_SIN_VENTA
        )
        assertEquals(3, codigos.size)
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOS CAMPOS QUE ESPERAN A LA TASK 19
    // ─────────────────────────────────────────────────────────────────────

    @Test
    fun `hoy la promesa llega sin fecha ni monto - el catalogo no los inventa`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta()),
            pagos = emptyList(),
            visitas = listOf(visita(TipoVisitaCatalogo.PIDE_REAGENDAR)),
            ventana = ventana
        )
        val resultado = derivacion.porVenta.getValue(ventaGuadalupe)
        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, resultado.estado)
        assertNull(resultado.fechaPromesa)
        assertNull(resultado.montoPrometido)
        assertNull(resultado.horaCita)
    }

    @Test
    fun `cuando la Task 19 pase fecha, monto y hora, el resultado los propaga tal cual`() {
        val fecha = LocalDate.of(2026, 9, 10)
        val hora = LocalTime.of(16, 30)
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta()),
            pagos = emptyList(),
            visitas = listOf(
                VisitaEnVentana(
                    clienteId = clienteGuadalupe,
                    ventaId = ventaGuadalupe,
                    tipoVisita = TipoVisitaCatalogo.PIDE_REAGENDAR,
                    fechaHora = Instant.parse("2026-09-02T16:00:00Z"),
                    fechaPromesa = fecha,
                    montoPrometido = BigDecimal("300"),
                    horaCita = hora
                )
            ),
            ventana = ventana
        )
        val resultado = derivacion.porVenta.getValue(ventaGuadalupe)
        assertEquals(fecha, resultado.fechaPromesa)
        assertEquals(BigDecimal("300"), resultado.montoPrometido)
        assertEquals(hora, resultado.horaCita)
    }

    @Test
    fun `el resultado lleva los numeros que justifican el estado`() {
        val derivacion = EstadoCuentaDeriver.derivar(
            cuentas = listOf(cuenta()),
            pagos = listOf(pago(importe = "120"), pago(importe = "80")),
            visitas = emptyList(),
            ventana = ventana
        )
        val resultado = derivacion.porVenta.getValue(ventaGuadalupe)
        assertEquals(0, BigDecimal("200").compareTo(resultado.abonoVentana))
        assertEquals(0, BigDecimal("300").compareTo(resultado.parcialidad))
    }

    private companion object {
        const val FORMA_EFECTIVO = 157
        const val FORMA_CHEQUE = 158
        const val FORMA_TRANSFERENCIA = 52569
        const val FORMA_CONDONACION = 137026
    }
}
