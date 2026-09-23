package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustez suprema de la captura: los bordes exactos de la promesa y de la cita.
 *
 * "Hoy" es el martes 1-sep-2026, el mismo de los fixtures de `:feature:pagos`,
 * y entra por parámetro: estas reglas no preguntan la hora del sistema.
 */
class ReglasDeLaVisitaTest {

    private val hoy = LocalDate.of(2026, 9, 1)

    /** Una cuenta cualquiera del cliente: el `DOCTO_CC_ACR_ID` del refrigerador. */
    private val cuenta = 77188

    private fun dinero(pesos: String) = Money.of(BigDecimal(pesos))

    // ─── promesa ─────────────────────────────────────────────────────────────

    /** **Promesa con fecha pasada.** Nace vencida: no difiere nada y no se guarda. */
    @Test
    fun `una promesa con fecha pasada esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.minusDays(1),
            montoPrometido = dinero("220")
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.COMPROMISO_EN_EL_PASADO),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
        assertFalse(ReglasDeLaVisita.sePuedeGuardar(captura, hoy))
    }

    /** El borde exacto: **hoy** sí se puede prometer — es el segmento *hoy*. */
    @Test
    fun `una promesa para hoy si se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy,
            montoPrometido = dinero("220")
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    /** **Promesa sin monto.** Legítima: "dijo cuándo pero no cuánto" pasa. */
    @Test
    fun `una promesa sin monto se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.plusDays(3),
            montoPrometido = null
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    /** Pero un monto de CERO no: eso significaría "prometió no pagar". */
    @Test
    fun `una promesa de cero pesos esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.plusDays(3),
            montoPrometido = Money.ZERO
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.PROMESA_SIN_MONTO),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    /** Un peso —el mínimo positivo entero— sí pasa: el borde es `> 0`. */
    @Test
    fun `una promesa de un peso si se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.plusDays(3),
            montoPrometido = dinero("1")
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    /** Sin fecha no hay promesa: es el único camino que difiere trabajo. */
    @Test
    fun `una promesa sin fecha esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            montoPrometido = dinero("220")
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.PROMESA_SIN_FECHA),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    /** Los dos defectos a la vez se reportan los dos, no solo el primero. */
    @Test
    fun `una promesa pasada y de cero reporta los dos bloqueos`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.minusDays(5),
            montoPrometido = Money.ZERO
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.COMPROMISO_EN_EL_PASADO, BloqueoDeLaVisita.PROMESA_SIN_MONTO),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    /** El borde de arriba: **hoy + 365** pasa. */
    @Test
    fun `una promesa a un ano exacto si se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.plusDays(ReglasDeLaVisita.HORIZONTE_DIAS)
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    /** Y **hoy + 366** no: un año mal tecleado no es un compromiso. */
    @Test
    fun `una promesa un dia mas alla del horizonte esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.PROMETIO,
            cuentas = setOf(cuenta),
            fechaPromesa = hoy.plusDays(ReglasDeLaVisita.HORIZONTE_DIAS + 1)
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.COMPROMISO_MUY_LEJANO),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    // ─── cita ────────────────────────────────────────────────────────────────

    /** **Cita sin hora.** Es el tercer caso del mock y se puede guardar. */
    @Test
    fun `una cita sin hora se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            fechaCita = hoy.plusDays(2),
            horaCita = null
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    /** Pero una cita sin DÍA no: no cae en ningún segmento de la lista. */
    @Test
    fun `una cita sin dia esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            horaCita = LocalTime.of(16, 0)
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.CITA_SIN_DIA),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    /**
     * **Una cita hacia atrás está bloqueada.** No es simetría por gusto: una
     * cita fechada más allá de la ventana de retención se poda en la primera
     * sincronización, o sea que se pierde justo el registro que esa ventana
     * existe para conservar.
     */
    @Test
    fun `una cita con dia pasado esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            fechaCita = hoy.minusDays(1),
            horaCita = LocalTime.of(16, 0)
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.COMPROMISO_EN_EL_PASADO),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    /** El borde exacto de la cita: **hoy** sí — es el caso del mock. */
    @Test
    fun `una cita para hoy si se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            fechaCita = hoy,
            horaCita = LocalTime.of(16, 0)
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    /** Y la cita también se topa por arriba, con el MISMO horizonte. */
    @Test
    fun `una cita mas alla del horizonte esta bloqueada`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            fechaCita = hoy.plusDays(ReglasDeLaVisita.HORIZONTE_DIAS + 1)
        )
        assertEquals(
            listOf(BloqueoDeLaVisita.COMPROMISO_MUY_LEJANO),
            ReglasDeLaVisita.bloqueosDe(captura, hoy)
        )
    }

    /** Una cita a un año exacto pasa: los dos desenlaces usan la misma regla. */
    @Test
    fun `una cita a un ano exacto si se puede guardar`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            fechaCita = ReglasDeLaVisita.ultimoDiaValido(hoy)
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }

    // ─── el resto ────────────────────────────────────────────────────────────

    @Test
    fun `sin resultado no se puede guardar nada`() {
        assertEquals(
            listOf(BloqueoDeLaVisita.SIN_RESULTADO),
            ReglasDeLaVisita.bloqueosDe(CapturaDeVisita(), hoy)
        )
    }

    /**
     * Los tres desenlaces que se sostienen con la sola etiqueta no piden fecha ni
     * monto. Los dos de cuenta sí piden **al menos una cuenta**, que es lo único
     * sin lo cual no habría fila que tocar.
     */
    @Test
    fun `no estaba, vuelvo y se nego no piden mas datos`() {
        listOf(
            ResultadoDeVisita.NO_ESTABA,
            ResultadoDeVisita.VISITE_VUELVO,
            ResultadoDeVisita.SE_NEGO
        ).forEach { resultado ->
            val cuentas = if (resultado.alcance == VisitScope.VENTA) setOf(cuenta) else emptySet()
            val captura = CapturaDeVisita(resultado = resultado, cuentas = cuentas)
            assertTrue(
                "$resultado no deberia pedir nada mas",
                ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty()
            )
        }
    }

    // ─── la cuenta ───────────────────────────────────────────────────────────

    /**
     * **Un desenlace de cuenta sin una sola cuenta marcada está bloqueado.** No
     * es una guarda defensiva: la pantalla ofrece desmarcarlas todas, así que el
     * estado se alcanza con dos toques. Y una visita de alcance VENTA sin venta
     * no toca ninguna fila de `sales`: el trabajo de campo se perdería entero.
     */
    @Test
    fun `un desenlace de cuenta sin cuentas marcadas esta bloqueado`() {
        listOf(ResultadoDeVisita.VISITE_VUELVO, ResultadoDeVisita.SE_NEGO).forEach { resultado ->
            assertEquals(
                "$resultado sin cuentas deberia estar bloqueado",
                listOf(BloqueoDeLaVisita.SIN_CUENTAS),
                ReglasDeLaVisita.bloqueosDe(
                    CapturaDeVisita(resultado = resultado, cuentas = emptySet()),
                    hoy
                )
            )
        }
    }

    /** Y la promesa, además del suyo, reporta también el de la cuenta que falta. */
    @Test
    fun `una promesa sin cuenta y sin fecha reporta los dos bloqueos`() {
        assertEquals(
            listOf(BloqueoDeLaVisita.SIN_CUENTAS, BloqueoDeLaVisita.PROMESA_SIN_FECHA),
            ReglasDeLaVisita.bloqueosDe(
                CapturaDeVisita(resultado = ResultadoDeVisita.PROMETIO),
                hoy
            )
        )
    }

    /**
     * **Un desenlace de toda la puerta NO pide cuentas**, y no puede pedirlas: el
     * estado se propaga a todas las cuentas activas del cliente por `CLIENTE_ID`,
     * sin que nadie elija. Control positivo del bloqueo de arriba.
     */
    @Test
    fun `un desenlace de toda la puerta no pide cuentas`() {
        listOf(ResultadoDeVisita.NO_ESTABA, ResultadoDeVisita.CITA).forEach { resultado ->
            val captura = CapturaDeVisita(
                resultado = resultado,
                cuentas = emptySet(),
                fechaCita = hoy
            )
            assertTrue(
                "$resultado no deberia pedir cuentas",
                BloqueoDeLaVisita.SIN_CUENTAS !in ReglasDeLaVisita.bloqueosDe(captura, hoy)
            )
        }
    }

    /**
     * Una fecha de promesa arrastrada bajo un desenlace que no es promesa **no**
     * bloquea: la regla mira el desenlace, no los campos sueltos. (Y el caso de
     * uso tampoco la escribe — ver `RegistrarVisitaTest`.)
     */
    @Test
    fun `una fecha vieja bajo otro desenlace no bloquea`() {
        val captura = CapturaDeVisita(
            resultado = ResultadoDeVisita.NO_ESTABA,
            fechaPromesa = hoy.minusYears(1)
        )
        assertTrue(ReglasDeLaVisita.bloqueosDe(captura, hoy).isEmpty())
    }
}
