package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje.AporteInput
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje.Frecuencia
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje.VentaCobranzaInput
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de paridad contra el Go original (`msp-api`,
 * `internal/rutas/domain/{aporte,calendario,desglose}.go` +
 * `internal/rutas/app/{cobranza_semanal,listar_rutas}.go`). Cada caso reproduce a mano el
 * cálculo Go (ver comentarios `// Go:` en cada test) — no solo verifica el comportamiento
 * Kotlin en aislamiento.
 */
class CobranzaPorcentajeTest {

    private fun bd(v: String) = BigDecimal(v)

    // region — calcAporte ------------------------------------------------------

    @Test
    fun `calcAporte una venta con exactamente una cuota vencida y pagada da aporte 1`() {
        // saldoAlInicio = 4500+500=5000; pagadoAntes = 5000-5000=0; debia=min(500*1,5000)=500;
        // vencidas=max(0,(500-0)/500)=1; abonoEnCuotas=500/500=1; aporte=min(1, 1+1)=1.
        val aporte = CobranzaPorcentaje.calcAporte(
            AporteInput(
                parcialidad = bd("500"),
                plazos = bd("1"),
                totalImporte = bd("5000"),
                abonoSemana = bd("500"),
                saldoHoy = bd("4500")
            )
        )
        assertEquals(0, bd("1").compareTo(aporte))
    }

    @Test
    fun `calcAporte atraso acumulado se cubre completo cuando el abono alcanza`() {
        // 3 cuotas vencidas (plazos=3), abono paga las 3 atrasadas + la actual = 4 cuotas.
        // saldoAlInicio=7000+3000=10000=totalImporte -> pagadoAntes=0.
        // debia=min(500*3,10000)=1500; vencidas=max(0,(1500-0)/500)=3.
        // abonoEnCuotas=3000/500=6; aporte=min(6, 3+1)=4 (tope por vencidas+1, no por el abono).
        val aporte = CobranzaPorcentaje.calcAporte(
            AporteInput(
                parcialidad = bd("500"),
                plazos = bd("3"),
                totalImporte = bd("10000"),
                abonoSemana = bd("3000"),
                saldoHoy = bd("7000")
            )
        )
        assertEquals(0, bd("4").compareTo(aporte))
    }

    @Test
    fun `calcAporte atraso acumulado con abono parcial refleja solo lo pagado`() {
        // 3 cuotas vencidas, pero el abono de la semana solo alcanza para 1 cuota.
        // saldoAlInicio=4500+500=5000=totalImporte -> pagadoAntes=0.
        // debia=min(1500,5000)=1500; vencidas=3; abonoEnCuotas=500/500=1; aporte=min(1,4)=1.
        val aporte = CobranzaPorcentaje.calcAporte(
            AporteInput(
                parcialidad = bd("500"),
                plazos = bd("3"),
                totalImporte = bd("5000"),
                abonoSemana = bd("500"),
                saldoHoy = bd("4500")
            )
        )
        assertEquals(0, bd("1").compareTo(aporte))
    }

    @Test
    fun `calcAporte abono cero da aporte cero aunque haya atraso`() {
        // saldoAlInicio=5000+0=5000=totalImporte -> pagadoAntes=0; debia=min(1000,5000)=1000;
        // vencidas=2; abonoEnCuotas=0/500=0; aporte=min(0,3)=0.
        val aporte = CobranzaPorcentaje.calcAporte(
            AporteInput(
                parcialidad = bd("500"),
                plazos = bd("2"),
                totalImporte = bd("5000"),
                abonoSemana = bd("0"),
                saldoHoy = bd("5000")
            )
        )
        assertEquals(0, bd("0").compareTo(aporte))
    }

    @Test
    fun `calcAporte con parcialidad cero o negativa siempre da cero (guardia)`() {
        val input = AporteInput(
            parcialidad = bd("0"),
            plazos = bd("2"),
            totalImporte = bd("5000"),
            abonoSemana = bd("500"),
            saldoHoy = bd("4500")
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(CobranzaPorcentaje.calcAporte(input)))
        assertEquals(
            0,
            BigDecimal.ZERO.compareTo(
                CobranzaPorcentaje.calcAporte(input.copy(parcialidad = bd("-500")))
            )
        )
    }

    // endregion

    // region — vencimientosVencidos ---------------------------------------------

    @Test
    fun `vencimientosVencidos semanal es floor de dias entre 7, minimo 0`() {
        // Go: floor(daysBetween(cargo, inicio)/7).
        assertEquals(
            2,
            CobranzaPorcentaje.vencimientosVencidos(
                Frecuencia.SEMANAL,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 15),
                0
            )
        )
        // fechaInicio antes de fechaCargo -> 0 (d<0).
        assertEquals(
            0,
            CobranzaPorcentaje.vencimientosVencidos(
                Frecuencia.SEMANAL,
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 1),
                0
            )
        )
    }

    @Test
    fun `vencimientosVencidos mensual respeta la gracia`() {
        val cargo = LocalDate.of(2026, 1, 10)
        // Feb1 (+2 gracia = Feb3) y Mar1 (+2 = Mar3) ambos < Mar5 -> 2.
        assertEquals(
            2,
            CobranzaPorcentaje.vencimientosVencidos(
                Frecuencia.MENSUAL,
                cargo,
                LocalDate.of(2026, 3, 5),
                2
            )
        )
        // inicio = Mar3 exacto: Mar1+2=Mar3 NO es estrictamente < Mar3 -> solo cuenta Feb1.
        assertEquals(
            1,
            CobranzaPorcentaje.vencimientosVencidos(
                Frecuencia.MENSUAL,
                cargo,
                LocalDate.of(2026, 3, 3),
                2
            )
        )
    }

    @Test
    fun `vencimientosVencidos quincenal solo cuenta candidatos que ya ocurrieron`() {
        // cargo=5 ene; inicio=20 ene, gracia=2: día-15 (+2=17) < 20 -> cuenta;
        // último-día-de-mes (31 ene) es POSTERIOR a inicio -> no cuenta.
        assertEquals(
            1,
            CobranzaPorcentaje.vencimientosVencidos(
                Frecuencia.QUINCENAL,
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 20),
                2
            )
        )
    }

    @Test
    fun `vencimientosVencidos contado siempre es cero`() {
        assertEquals(
            0,
            CobranzaPorcentaje.vencimientosVencidos(
                Frecuencia.CONTADO,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                2
            )
        )
    }

    // endregion

    // region — aplicaEnVentana ---------------------------------------------------

    @Test
    fun `aplicaEnVentana semanal exige que haya transcurrido al menos una semana completa`() {
        val cargo = LocalDate.of(2026, 1, 1)
        // Exactamente 7 días después -> aplica.
        assertTrue(
            CobranzaPorcentaje.aplicaEnVentana(
                Frecuencia.SEMANAL,
                cargo,
                LocalDate.of(2026, 1, 8),
                LocalDate.of(2026, 1, 8)
            )
        )
        // Solo 6 días transcurridos -> no aplica (ninguna semana completa en la ventana).
        assertTrue(
            !CobranzaPorcentaje.aplicaEnVentana(
                Frecuencia.SEMANAL,
                cargo,
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 7)
            )
        )
    }

    @Test
    fun `aplicaEnVentana mensual detecta un dia-1 dentro de la ventana`() {
        val cargo = LocalDate.of(2026, 1, 10)
        assertTrue(
            CobranzaPorcentaje.aplicaEnVentana(
                Frecuencia.MENSUAL,
                cargo,
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15)
            )
        )
        // La ventana termina antes de que exista un día-1 posterior a cargo.
        assertTrue(
            !CobranzaPorcentaje.aplicaEnVentana(
                Frecuencia.MENSUAL,
                cargo,
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 31)
            )
        )
    }

    @Test
    fun `aplicaEnVentana contado nunca aplica`() {
        assertTrue(
            !CobranzaPorcentaje.aplicaEnVentana(
                Frecuencia.CONTADO,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
            )
        )
    }

    // endregion

    // region — resumenPonderado / resumenCobertura / calcular --------------------

    @Test
    fun `resumenPonderado excluye contado y ventas que no aplican en la ventana`() {
        val fechaInicio = LocalDate.of(2026, 1, 8)
        val hoy = LocalDate.of(2026, 1, 8)
        // fechaCargo exactamente una semana antes de fechaInicio -> aplica.
        val ventaAplica = VentaCobranzaInput(
            parcialidad = bd("500"),
            frecuencia = Frecuencia.SEMANAL,
            fechaCargo = LocalDate.of(2026, 1, 1),
            totalImporte = bd("5000"),
            abonoSemana = bd("500"),
            saldoHoy = bd("4500")
        )
        // fechaCargo a menos de 7 días de fechaInicio -> no aplica.
        val ventaNoAplica = ventaAplica.copy(fechaCargo = LocalDate.of(2026, 1, 5))
        val ventaContado = ventaAplica.copy(frecuencia = Frecuencia.CONTADO)

        val resumen = CobranzaPorcentaje.resumenPonderado(
            listOf(ventaAplica, ventaNoAplica, ventaContado),
            fechaInicio,
            hoy
        )

        assertEquals(1, resumen.denominador)
        assertEquals(0, bd("1").compareTo(resumen.numerador))
        assertEquals("100.00", resumen.pct?.setScale(2, RoundingMode.HALF_UP).toString())
    }

    @Test
    fun `resumenPonderado sin ventas aplicables da pct nulo`() {
        val resumen = CobranzaPorcentaje.resumenPonderado(
            emptyList(),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 1)
        )
        assertEquals(0, resumen.denominador)
        assertNull(resumen.pct)
    }

    @Test
    fun `resumenCobertura cuenta ventas activas no-contado con abono positivo`() {
        val base = VentaCobranzaInput(
            parcialidad = bd("500"),
            frecuencia = Frecuencia.SEMANAL,
            fechaCargo = LocalDate.of(2026, 1, 1),
            totalImporte = bd("5000"),
            abonoSemana = bd("500"),
            saldoHoy = bd("4500")
        )
        val sinAbono = base.copy(abonoSemana = bd("0"), saldoHoy = bd("5000"))
        val contado = base.copy(frecuencia = Frecuencia.CONTADO)

        val cobertura = CobranzaPorcentaje.resumenCobertura(listOf(base, sinAbono, contado))

        assertEquals(1, cobertura.numerador)
        assertEquals(2, cobertura.denominador)
        assertEquals("50.00", cobertura.pct?.setScale(2, RoundingMode.HALF_UP).toString())
    }

    @Test
    fun `resumenCobertura sin ventas da pct nulo`() {
        val cobertura = CobranzaPorcentaje.resumenCobertura(emptyList())
        assertEquals(0, cobertura.denominador)
        assertNull(cobertura.pct)
    }

    @Test
    fun `calcular combina ponderado y cobertura de una sola pasada`() {
        val fechaInicio = LocalDate.of(2026, 1, 8)
        val hoy = LocalDate.of(2026, 1, 8)
        val pagoCompleto = VentaCobranzaInput(
            parcialidad = bd("500"),
            frecuencia = Frecuencia.SEMANAL,
            fechaCargo = LocalDate.of(2026, 1, 1),
            totalImporte = bd("5000"),
            abonoSemana = bd("500"),
            saldoHoy = bd("4500")
        )
        val sinPago = pagoCompleto.copy(abonoSemana = bd("0"), saldoHoy = bd("5000"))

        val resultado = CobranzaPorcentaje.calcular(listOf(pagoCompleto, sinPago), fechaInicio, hoy)

        assertEquals(2, resultado.clientesTotal)
        assertEquals(1, resultado.clientesPagaron)
        assertEquals(
            "50.00",
            resultado.porcentajeCuentas?.setScale(2, RoundingMode.HALF_UP).toString()
        )
        // Ambas ventas aplican (misma fechaCargo semanal); pagoCompleto aporta 1, sinPago aporta 0.
        assertEquals(
            "50.00",
            resultado.porcentajeCobro?.setScale(2, RoundingMode.HALF_UP).toString()
        )
    }

    // endregion
}
