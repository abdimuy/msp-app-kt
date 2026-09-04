package com.example.msp_app.feature.pagos.ui

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
        assertEquals("prometió sin fecha", EstadoCuentaUi.etiquetaDe(promesa(null)))
    }

    @Test
    fun `promesa con fecha si difiere y muestra la fecha`() {
        val conFecha = promesa(LocalDate.of(2026, 9, 15))
        assertEquals(TratoDelEstado.DIFERIDO, EstadoCuentaUi.tratoDe(conFecha))
        assertFalse(EstadoCuentaUi.requiereAtencion(TratoDelEstado.DIFERIDO))
        assertTrue(EstadoCuentaUi.etiquetaDe(conFecha).contains("15"))
        assertEquals("no cae esta semana", EstadoCuentaUi.detalleDe(conFecha))
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
        assertEquals("falta 1 de 2", EstadoCuentaUi.avisoDeCuentas(listOf(pagada, promesa(null))))
        assertEquals(
            null,
            EstadoCuentaUi.avisoDeCuentas(listOf(pagada, promesa(LocalDate.of(2026, 9, 15))))
        )
        assertEquals(
            "faltan 2 de 2",
            EstadoCuentaUi.avisoDeCuentas(listOf(promesa(null), promesa(null)))
        )
    }
}
