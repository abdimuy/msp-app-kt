package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.graphics.Color
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.theme.mspDarkColors
import com.example.msp_app.core.designsystem.theme.mspLightColors
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regla heredada de la Task 14, probada de frente: **una promesa sin fecha
 * NO puede pintarse como diferida.**
 *
 * `PIDE_REAGENDAR` es el único literal que llega hoy a
 * [EstadoCuenta.PROMETIO_PROXIMA] y no trae fecha; si la pantalla lo pintara
 * como "no cae esta semana", una cuenta saldría de la ruta con base en nada.
 * El control de reversión de esta tarea es exactamente este archivo: quitar la
 * rama de `fechaPromesa == null` en `EstadoCuentaUi.tratoDe` pone en rojo los
 * cuatro primeros tests.
 */
class EstadoCuentaUiTest {

    private fun promesa(fecha: LocalDate?) = EstadoDelPeriodo(
        estado = EstadoCuenta.PROMETIO_PROXIMA,
        abonoDelPeriodo = Money.ZERO,
        parcialidad = Money.of(BigDecimal("220")),
        fechaPromesa = fecha
    )

    @Test
    fun `promesa sin fecha no es diferida`() {
        assertNotEquals(TratoDelEstado.DIFERIDO, EstadoCuentaUi.tratoDe(promesa(null)))
    }

    @Test
    fun `promesa sin fecha pide regresar`() {
        assertEquals(TratoDelEstado.REGRESAS, EstadoCuentaUi.tratoDe(promesa(null)))
    }

    @Test
    fun `promesa sin fecha requiere atencion`() {
        assertTrue(EstadoCuentaUi.requiereAtencion(EstadoCuentaUi.tratoDe(promesa(null))))
    }

    @Test
    fun `promesa sin fecha nunca dice que no cae esta semana`() {
        val detalle = EstadoCuentaUi.detalleDe(promesa(null))
        assertFalse(detalle.contains("no cae"))
        assertEquals("Prometió sin fecha", EstadoCuentaUi.etiquetaDe(promesa(null)))
    }

    @Test
    fun `promesa con fecha si difiere y muestra la fecha`() {
        val conFecha = promesa(LocalDate.of(2026, 9, 15))
        assertEquals(TratoDelEstado.DIFERIDO, EstadoCuentaUi.tratoDe(conFecha))
        assertFalse(EstadoCuentaUi.requiereAtencion(TratoDelEstado.DIFERIDO))
        assertTrue(EstadoCuentaUi.etiquetaDe(conFecha).contains("15"))
        assertEquals("No cae esta semana", EstadoCuentaUi.detalleDe(conFecha))
    }

    @Test
    fun `el literal PIDE_REAGENDAR llega sin fecha y por eso cae en regresas`() {
        // El camino completo: literal del catálogo -> estado -> trato.
        val estado = TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.PIDE_REAGENDAR)
        assertEquals(EstadoCuenta.PROMETIO_PROXIMA, estado)
        val delPeriodo = EstadoDelPeriodo(
            estado = estado,
            abonoDelPeriodo = Money.ZERO,
            parcialidad = Money.of(BigDecimal("220"))
        )
        assertEquals(TratoDelEstado.REGRESAS, EstadoCuentaUi.tratoDe(delPeriodo))
    }

    // ── La MISMA regla, una rama más allá: la cita sin hora ────────────────────────────────

    private fun cita(hora: LocalTime?) = EstadoDelPeriodo(
        estado = EstadoCuenta.CITA_A_UNA_HORA,
        abonoDelPeriodo = Money.ZERO,
        parcialidad = Money.of(BigDecimal("220")),
        horaCita = hora
    )

    @Test
    fun `cita sin hora no saca la cuenta del trabajo de la semana`() {
        assertEquals(TratoDelEstado.REGRESAS, EstadoCuentaUi.tratoDe(cita(null)))
        assertTrue(EstadoCuentaUi.requiereAtencion(EstadoCuentaUi.tratoDe(cita(null))))
    }

    @Test
    fun `cita sin hora se nombra como pendiente, no como cita cerrada`() {
        assertEquals("Cita sin hora", EstadoCuentaUi.etiquetaDe(cita(null)))
        assertEquals("Sin hora, regresas", EstadoCuentaUi.detalleDe(cita(null)))
        assertFalse(EstadoCuentaUi.detalleDe(cita(null)).contains("quedaron"))
    }

    @Test
    fun `cita CON hora si es una cita y muestra la hora`() {
        val conHora = cita(LocalTime.of(16, 30))
        assertEquals(TratoDelEstado.CITA, EstadoCuentaUi.tratoDe(conHora))
        assertFalse(EstadoCuentaUi.requiereAtencion(TratoDelEstado.CITA))
        assertEquals("Cita 16:30", EstadoCuentaUi.etiquetaDe(conHora))
        assertEquals("Quedaron de verse", EstadoCuentaUi.detalleDe(conHora))
    }

    /**
     * **La cita dice QUÉ DÍA, no sólo a qué hora.**
     *
     * Decía "Cita 16:30" y el cobrador no tenía cómo saber de qué día se
     * hablaba: con cuarenta cuentas, una hora suelta obliga a recordar una
     * conversación. El dueño lo cazó en el teléfono el 2026-09-22.
     *
     * El día va delante porque es lo que decide si esa puerta es de hoy. El
     * caso SIN día lo cubre la prueba de arriba, que sigue esperando
     * "Cita 16:30": `fechaCita` es nullable y una cita puede llegar sin él.
     */
    @Test
    fun `la cita muestra el dia junto con la hora`() {
        val conDia = cita(LocalTime.of(16, 30)).copy(fechaCita = LocalDate.of(2026, 9, 24))

        assertEquals("Cita 24 sept 16:30", EstadoCuentaUi.etiquetaDe(conDia))
    }

    /**
     * **La promesa dice CUÁNTO, no sólo cuándo.**
     *
     * El monto prometido se capturaba y **no se pintaba en ninguna parte de la
     * app**: el cobrador acordaba "$800 el 25" y volvía a una pantalla que
     * sólo decía el día. Capturar un dato que nadie puede leer de vuelta es
     * peor que no capturarlo.
     *
     * `montoPrometido` es nullable a propósito —prometer una fecha sin
     * cantidad es un caso real de campo, y un cero significaría "prometió no
     * pagar"—, así que el texto se compone. Las dos ramas se afirman aquí: sin
     * el control de abajo, un monto que nunca se pintara pasaría igual.
     */
    @Test
    fun `la promesa muestra el monto prometido junto con el dia`() {
        val dia = LocalDate.of(2026, 9, 25)
        val conMonto = promesa(dia).copy(montoPrometido = Money.of(BigDecimal("800")))

        assertEquals("Prometió \$800 el 25 sept", EstadoCuentaUi.etiquetaDe(conMonto))
        assertEquals("Prometió el 25 sept", EstadoCuentaUi.etiquetaDe(promesa(dia)))
    }

    /**
     * **Los dos ya NO comparten glifo, y ese es el arreglo.**
     *
     * Compartían el triángulo de advertencia, así que en pantalla una promesa sin
     * fecha y una cita sin hora se veían idénticas y solo el texto las separaba.
     * Ahora cada una dice qué dato le falta: calendario sin día contra reloj sin
     * manecillas. Lo que se conserva es el TRATO —los dos siguen siendo un
     * pendiente— porque eso no cambió.
     */
    @Test
    fun `los dos compromisos sin su dato comparten trato pero ya no comparten glifo`() {
        // Misma forma: sin el dato que lo sostiene, el compromiso es un pendiente.
        assertEquals(EstadoCuentaUi.tratoDe(promesa(null)), EstadoCuentaUi.tratoDe(cita(null)))
        // Pero cada uno con su glifo: el que faltaba era este.
        assertNotEquals(EstadoCuentaUi.iconoDe(promesa(null)), EstadoCuentaUi.iconoDe(cita(null)))
        // Y ninguno comparte ícono con su versión completa.
        assertNotEquals(
            EstadoCuentaUi.iconoDe(cita(null)),
            EstadoCuentaUi.iconoDe(cita(LocalTime.of(16, 30)))
        )
        assertNotEquals(
            EstadoCuentaUi.iconoDe(promesa(null)),
            EstadoCuentaUi.iconoDe(promesa(LocalDate.of(2026, 9, 15)))
        )
        // Pero SÍ se distinguen entre sí por texto — el color/ícono no es el único portador.
        assertNotEquals(
            EstadoCuentaUi.etiquetaDe(promesa(null)),
            EstadoCuentaUi.etiquetaDe(cita(null))
        )
    }

    @Test
    fun `los ocho estados del catalogo tienen trato, etiqueta, detalle e icono`() {
        EstadoCuenta.entries.forEach { estado ->
            val delPeriodo = EstadoDelPeriodo(
                estado = estado,
                abonoDelPeriodo = Money.ZERO,
                parcialidad = Money.of(BigDecimal("220")),
                fechaPromesa = if (estado == EstadoCuenta.PROMETIO_PROXIMA) {
                    LocalDate.of(
                        2026,
                        9,
                        15
                    )
                } else {
                    null
                },
                horaCita = if (estado == EstadoCuenta.CITA_A_UNA_HORA) {
                    LocalTime.of(
                        16,
                        30
                    )
                } else {
                    null
                }
            )
            assertTrue(estado.name, EstadoCuentaUi.etiquetaDe(delPeriodo).isNotBlank())
            assertTrue(estado.name, EstadoCuentaUi.detalleDe(delPeriodo).isNotBlank())
            assertTrue(estado.name, EstadoCuentaUi.iconoDe(delPeriodo).name.isNotBlank())
        }
    }

    @Test
    fun `nunca solo color — los dos estados que comparten matiz no comparten icono ni texto`() {
        val noEstaba = EstadoDelPeriodo(EstadoCuenta.NO_ESTABA, Money.ZERO, Money.ZERO)
        val sinTocar = EstadoDelPeriodo(EstadoCuenta.SIN_TOCAR, Money.ZERO, Money.ZERO)
        val colores = mspLightColors()
        assertEquals(
            EstadoCuentaUi.contenidoDe(TratoDelEstado.NADIE, colores),
            EstadoCuentaUi.contenidoDe(TratoDelEstado.SIN_TRABAJAR, colores)
        )
        assertNotEquals(EstadoCuentaUi.iconoDe(noEstaba), EstadoCuentaUi.iconoDe(sinTocar))
        assertNotEquals(EstadoCuentaUi.etiquetaDe(noEstaba), EstadoCuentaUi.etiquetaDe(sinTocar))
    }

    /**
     * **La cuenta a la que nadie ha ido se nombra desde la ruta, no como reproche.**
     *
     * Las otras nueve etiquetas cuentan qué pasó en la puerta; "Sin trabajar" era
     * la única que hablaba del pendiente del cobrador, y su línea de apoyo
     * ("Nadie la ha trabajado") hasta nombraba al culpable. El dueño lo cazó en
     * el teléfono.
     *
     * Etiqueta y detalle se cobran JUNTOS porque la lista y el detalle leen de
     * las mismas dos funciones: la misma cuenta no puede llamarse distinto en dos
     * pantallas.
     *
     * Las dos últimas aserciones son el **control positivo**: estos mismos dos
     * métodos, sobre un estado que NO cambió, siguen devolviendo su texto. Sin
     * ellas, un fallo arriba podría ser igual de bien un `etiquetaDe` que dejó de
     * ver el estado que le pasan.
     */
    @Test
    fun `la cuenta que nadie visito dice que falta pasar, no que esta sin trabajar`() {
        val sinTocar = EstadoDelPeriodo(EstadoCuenta.SIN_TOCAR, Money.ZERO, Money.ZERO)
        assertEquals(TratoDelEstado.SIN_TRABAJAR, EstadoCuentaUi.tratoDe(sinTocar))
        assertEquals("Falta pasar", EstadoCuentaUi.etiquetaDe(sinTocar))
        assertEquals("Todavía no vas", EstadoCuentaUi.detalleDe(sinTocar))

        // Control positivo: los mismos métodos sí encuentran una etiqueta intacta.
        val noEstaba = EstadoDelPeriodo(EstadoCuenta.NO_ESTABA, Money.ZERO, Money.ZERO)
        assertEquals("No estaba", EstadoCuentaUi.etiquetaDe(noEstaba))
        assertEquals("Regresas esta semana", EstadoCuentaUi.detalleDe(noEstaba))
    }

    @Test
    fun `se nego es el unico relleno solido y usa el par del Task 2`() {
        assertTrue(EstadoCuentaUi.esRelleno(TratoDelEstado.ESCALAR))
        TratoDelEstado.entries.filter { it != TratoDelEstado.ESCALAR }.forEach {
            assertFalse(it.name, EstadoCuentaUi.esRelleno(it))
        }
        listOf(mspLightColors(), mspDarkColors()).forEach { colores ->
            assertEquals(
                colores.statusOverdue,
                EstadoCuentaUi.fondoDe(TratoDelEstado.ESCALAR, colores)
            )
            assertEquals(
                colores.onDanger,
                EstadoCuentaUi.contenidoDe(TratoDelEstado.ESCALAR, colores)
            )
        }
    }

    @Test
    fun `abono parcial usa statusTeal, no statusPartial — la trampa de nombre del Task 2`() {
        listOf(mspLightColors(), mspDarkColors()).forEach { colores ->
            assertEquals(
                colores.statusTeal,
                EstadoCuentaUi.contenidoDe(TratoDelEstado.PARCIAL, colores)
            )
            assertEquals(
                colores.statusTealTint,
                EstadoCuentaUi.fondoDe(TratoDelEstado.PARCIAL, colores)
            )
        }
    }

    @Test
    fun `el aviso cuenta las cuentas que siguen pidiendo trabajo`() {
        val pagada = EstadoDelPeriodo(EstadoCuenta.PAGO, Money.ZERO, Money.ZERO)
        assertEquals("Falta 1 de 2", EstadoCuentaUi.avisoDeCuentas(listOf(pagada, promesa(null))))
        assertEquals(
            null,
            EstadoCuentaUi.avisoDeCuentas(listOf(pagada, promesa(LocalDate.of(2026, 9, 15))))
        )
        assertEquals(
            "Faltan 2 de 2",
            EstadoCuentaUi.avisoDeCuentas(listOf(promesa(null), promesa(null)))
        )
    }

    /**
     * Decisión del dueño del 2026-10-16: sobre un badge sólido el texto y el
     * ícono van **en blanco en los dos temas**. Antes era `surface`, casi negro
     * en oscuro. "Falta pasar" no es sólido y conserva `onSurfaceMuted`.
     */
    @Test
    fun `sobre el badge solido el texto va en blanco en los dos temas`() {
        for (colores in listOf(mspLightColors(), mspDarkColors())) {
            for (trato in TratoDelEstado.entries) {
                val esperado =
                    if (trato == TratoDelEstado.SIN_TRABAJAR) colores.onSurfaceMuted else Color.White
                assertEquals("$trato", esperado, BadgeSolido.sobreSolidoDe(trato, colores))
            }
        }
    }

    /**
     * Decisión del dueño del 2026-10-16 (opción B): el texto blanco sobre cada
     * badge sólido alcanza **3:1** (WCAG, texto grande o negrita) en los dos
     * temas. El contraste se calcula aquí con la fórmula de WCAG, no con la de
     * Compose, para no medir el código con el código. Con los tokens pastel del
     * tema oscuro (2.0–2.7:1) este test está en ROJO.
     */
    @Test
    fun `el blanco sobre cada badge solido alcanza 3 a 1 en los dos temas`() {
        for (colores in listOf(mspLightColors(), mspDarkColors())) {
            for (trato in TratoDelEstado.entries - TratoDelEstado.SIN_TRABAJAR) {
                val fondo = BadgeSolido.solidoDe(trato, colores)
                val sobre = BadgeSolido.sobreSolidoDe(trato, colores)
                val razon = contraste(sobre, fondo)
                assertTrue("$trato: %.2f:1".format(razon), razon >= 3.0)
            }
        }
    }

    /** En claro el badge sigue usando los tokens del tema, sin tocar. */
    @Test
    fun `en claro el badge solido usa los tokens del tema`() {
        val c = mspLightColors()
        assertEquals(c.statusPaid, BadgeSolido.solidoDe(TratoDelEstado.PAGADO, c))
        assertEquals(c.statusTeal, BadgeSolido.solidoDe(TratoDelEstado.PARCIAL, c))
        assertEquals(c.statusPartial, BadgeSolido.solidoDe(TratoDelEstado.REGRESAS, c))
        assertEquals(c.statusOverdue, BadgeSolido.solidoDe(TratoDelEstado.DIFERIDO, c))
        assertEquals(c.danger, BadgeSolido.solidoDe(TratoDelEstado.ESCALAR, c))
        assertEquals(c.promise, BadgeSolido.solidoDe(TratoDelEstado.CITA, c))
        assertEquals(c.statusPending, BadgeSolido.solidoDe(TratoDelEstado.NADIE, c))
        assertEquals(c.surface, BadgeSolido.solidoDe(TratoDelEstado.SIN_TRABAJAR, c))
    }

    private fun contraste(a: Color, b: Color): Double {
        val la = luminanciaWcag(a)
        val lb = luminanciaWcag(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminanciaWcag(c: Color): Double {
        fun canal(v: Float): Double =
            if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        return 0.2126 * canal(c.red) + 0.7152 * canal(c.green) + 0.0722 * canal(c.blue)
    }
}
