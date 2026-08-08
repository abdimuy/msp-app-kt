package com.example.msp_app.features.sales.domain.models

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettlementCalculatorTest {

    // Datos de la hoja de especificación:
    // contado=$5,000 | corto plazo=$5,800 | total=$7,800
    private val saleDate = "25/03/2026"

    private fun settlement(
        cashPrice: Double = 5000.0,
        shortTermAmount: Double = 5800.0,
        totalPrice: Double = 7800.0,
        // nada pagado
        remainingBalance: Double = 7800.0,
        date: String = saleDate
    ) = Settlement(cashPrice, shortTermAmount, totalPrice, remainingBalance, date)

    private fun nowAtMonth(monthsAfterSale: Int, graceDays: Long = 14L): LocalDateTime {
        // Para que elapsedMonths = monthsAfterSale, necesitamos:
        // now - graceDays >= saleDate + (monthsAfterSale - 1) meses
        // Usamos saleDate + (monthsAfterSale - 1) meses + graceDays + 1 día
        return LocalDateTime.of(2026, 3, 25, 12, 0)
            .plusMonths(monthsAfterSale.toLong() - 1)
            .plusDays(graceDays + 1)
    }

    // ============================================================
    // Curva completa con datos de la hoja (grace=14)
    // ============================================================

    @Test
    fun `mes 1 - precio de contado`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(1))
        assertEquals(5000.0, result.amount, 0.01)
        assertEquals("Precio de contado", result.category)
    }

    @Test
    fun `mes 2 - primer escalon corto plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(2))
        // 5000 + (2-1) * (800/3) = 5000 + 266.67
        assertEquals(5266.67, result.amount, 0.01)
        assertEquals("Precio a 2 meses", result.category)
    }

    @Test
    fun `mes 3 - segundo escalon corto plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(3))
        // 5000 + (3-1) * (800/3) = 5000 + 533.33
        assertEquals(5533.33, result.amount, 0.01)
        assertEquals("Precio a 3 meses", result.category)
    }

    @Test
    fun `mes 4 - meseta corto plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(4))
        assertEquals(5800.0, result.amount, 0.01)
        assertEquals("Precio a 4 meses", result.category)
    }

    @Test
    fun `mes 5 - meseta corto plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(5))
        assertEquals(5800.0, result.amount, 0.01)
        assertEquals("Precio a 5 meses", result.category)
    }

    @Test
    fun `mes 6 - primer escalon largo plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(6))
        // 5800 + (1/8) * 2000 = 5800 + 250
        assertEquals(6050.0, result.amount, 0.01)
        assertEquals("Precio a 6 meses", result.category)
    }

    @Test
    fun `mes 7 - segundo escalon largo plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(7))
        // 5800 + (2/8) * 2000 = 5800 + 500
        assertEquals(6300.0, result.amount, 0.01)
        assertEquals("Precio a 7 meses", result.category)
    }

    @Test
    fun `mes 8 - tercer escalon largo plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(8))
        assertEquals(6550.0, result.amount, 0.01)
    }

    @Test
    fun `mes 9 - cuarto escalon largo plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(9))
        assertEquals(6800.0, result.amount, 0.01)
    }

    @Test
    fun `mes 10 - quinto escalon largo plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(10))
        assertEquals(7050.0, result.amount, 0.01)
    }

    @Test
    fun `mes 11 - sexto escalon largo plazo`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(11))
        assertEquals(7300.0, result.amount, 0.01)
    }

    @Test
    fun `mes 12 - precio promocional`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(12))
        // 5800 + (7/8) * 2000 = 5800 + 1750 = 7550
        assertEquals(7550.0, result.amount, 0.01)
        assertEquals("Precio Promocional", result.category)
    }

    @Test
    fun `mes 13 - precio total`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(13))
        assertEquals(7800.0, result.amount, 0.01)
        assertEquals("Precio total", result.category)
    }

    @Test
    fun `mes 14 y mas - sigue siendo precio total`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(14))
        assertEquals(7800.0, result.amount, 0.01)
        assertEquals("Precio total", result.category)
    }

    // ============================================================
    // Curvas completas: múltiples escenarios de precios
    // ============================================================

    private fun assertFullCurve(
        s: Settlement,
        expectedAmounts: Map<Int, Double>,
        label: String,
        graceDays: Long = 14L
    ) {
        val expectedCategories = expectedAmounts.keys.associateWith { month ->
            when {
                month <= 1 -> "Precio de contado"
                month < 12 -> "Precio a $month meses"
                month == 12 -> "Precio Promocional"
                else -> "Precio total"
            }
        }

        for (month in expectedAmounts.keys.sorted()) {
            val result = calculatePaymentResult(s, nowAtMonth(month, graceDays), graceDays)
            assertEquals(
                "$label — monto incorrecto en mes $month",
                expectedAmounts[month]!!,
                result.amount,
                0.01
            )
            assertEquals(
                "$label — categoria incorrecta en mes $month",
                expectedCategories[month],
                result.category
            )
        }
    }

    @Test
    fun `curva completa - hoja de especificacion 5000-5800-7800`() {
        assertFullCurve(
            s = settlement(),
            label = "Hoja spec",
            expectedAmounts = mapOf(
                1 to 5000.0,
                2 to 5266.67,
                3 to 5533.33,
                4 to 5800.0,
                5 to 5800.0,
                6 to 6050.0,
                7 to 6300.0,
                8 to 6550.0,
                9 to 6800.0,
                10 to 7050.0,
                11 to 7300.0,
                12 to 7550.0,
                13 to 7800.0,
                14 to 7800.0
            )
        )
    }

    @Test
    fun `curva completa - precios bajos 1000-1400-2100`() {
        val s = settlement(
            cashPrice = 1000.0,
            shortTermAmount = 1400.0,
            totalPrice = 2100.0,
            remainingBalance = 2100.0
        )
        assertFullCurve(
            s = s,
            label = "Precios bajos",
            expectedAmounts = mapOf(
                1 to 1000.0,
                2 to 1133.33,
                3 to 1266.67,
                4 to 1400.0,
                5 to 1400.0,
                6 to 1487.5,
                7 to 1575.0,
                8 to 1662.5,
                9 to 1750.0,
                10 to 1837.5,
                11 to 1925.0,
                12 to 2012.5,
                13 to 2100.0
            )
        )
    }

    @Test
    fun `curva completa - precios altos 15000-18000-25000`() {
        val s = settlement(
            cashPrice = 15000.0,
            shortTermAmount = 18000.0,
            totalPrice = 25000.0,
            remainingBalance = 25000.0
        )
        assertFullCurve(
            s = s,
            label = "Precios altos",
            expectedAmounts = mapOf(
                1 to 15000.0,
                2 to 16000.0,
                3 to 17000.0,
                4 to 18000.0,
                5 to 18000.0,
                6 to 18875.0,
                7 to 19750.0,
                8 to 20625.0,
                9 to 21500.0,
                10 to 22375.0,
                11 to 23250.0,
                12 to 24125.0,
                13 to 25000.0
            )
        )
    }

    @Test
    fun `curva completa - contado igual a corto plazo 5000-5000-7800`() {
        val s = settlement(
            cashPrice = 5000.0,
            shortTermAmount = 5000.0,
            totalPrice = 7800.0,
            remainingBalance = 7800.0
        )
        assertFullCurve(
            s = s,
            label = "Contado = corto plazo",
            expectedAmounts = mapOf(
                1 to 5000.0,
                2 to 5000.0,
                3 to 5000.0,
                4 to 5000.0,
                5 to 5000.0,
                6 to 5350.0,
                7 to 5700.0,
                8 to 6050.0,
                9 to 6400.0,
                10 to 6750.0,
                11 to 7100.0,
                12 to 7450.0,
                13 to 7800.0
            )
        )
    }

    @Test
    fun `curva completa - diferencia minima entre precios 10000-10100-10200`() {
        val s = settlement(
            cashPrice = 10000.0,
            shortTermAmount = 10100.0,
            totalPrice = 10200.0,
            remainingBalance = 10200.0
        )
        assertFullCurve(
            s = s,
            label = "Diferencia minima",
            expectedAmounts = mapOf(
                1 to 10000.0,
                2 to 10033.33,
                3 to 10066.67,
                4 to 10100.0,
                5 to 10100.0,
                6 to 10112.5,
                7 to 10125.0,
                8 to 10137.5,
                9 to 10150.0,
                10 to 10162.5,
                11 to 10175.0,
                12 to 10187.5,
                13 to 10200.0
            )
        )
    }

    @Test
    fun `curva completa - con $2000 ya pagados`() {
        val s = settlement(remainingBalance = 5800.0) // 7800 - 2000 pagados
        assertFullCurve(
            s = s,
            label = "Con pagos parciales",
            expectedAmounts = mapOf(
                1 to 3000.0,
                2 to 3266.67,
                3 to 3533.33,
                4 to 3800.0,
                5 to 3800.0,
                6 to 4050.0,
                7 to 4300.0,
                8 to 4550.0,
                9 to 4800.0,
                10 to 5050.0,
                11 to 5300.0,
                12 to 5550.0,
                13 to 5800.0
            )
        )
    }

    @Test
    fun `curva completa - con grace period de 0 dias`() {
        assertFullCurve(
            s = settlement(),
            label = "Grace 0",
            graceDays = 0,
            expectedAmounts = mapOf(
                1 to 5000.0,
                2 to 5266.67,
                3 to 5533.33,
                4 to 5800.0,
                5 to 5800.0,
                6 to 6050.0,
                7 to 6300.0,
                8 to 6550.0,
                9 to 6800.0,
                10 to 7050.0,
                11 to 7300.0,
                12 to 7550.0,
                13 to 7800.0
            )
        )
    }

    // ============================================================
    // Pagos parciales: descuenta lo ya pagado
    // ============================================================

    @Test
    fun `con pagos parciales descuenta totalPaid`() {
        // Cliente ha pagado $2000 de $7800
        val s = settlement(remainingBalance = 5800.0) // 7800 - 2000 = 5800
        val result = calculatePaymentResult(s, nowAtMonth(6))
        // Monto base mes 6: 6050 - totalPaid(2000) = 4050
        assertEquals(4050.0, result.amount, 0.01)
    }

    @Test
    fun `con saldo completamente pagado`() {
        val s = settlement(remainingBalance = 0.0)
        val result = calculatePaymentResult(s, nowAtMonth(6))
        // 6050 - 7800 = negativo
        assertEquals(6050.0 - 7800.0, result.amount, 0.01)
    }

    // ============================================================
    // Caso especial: cashPrice = 0
    // ============================================================

    @Test
    fun `cashPrice cero devuelve no disponible`() {
        val s = settlement(cashPrice = 0.0)
        val result = calculatePaymentResult(s, nowAtMonth(1))
        assertEquals(0.0, result.amount, 0.01)
        assertEquals("No disponible", result.category)
        assertEquals("-", result.validUntil)
    }

    // ============================================================
    // Grace period variations
    // ============================================================

    @Test
    fun `grace 0 dias - mes cambia inmediatamente`() {
        // Con grace=0, 1 día después del mes ya cuenta como mes siguiente
        val now = LocalDateTime.of(2026, 4, 26, 12, 0) // ~1 mes después
        val result = calculatePaymentResult(settlement(), now, gracePeriodDays = 0)
        assertEquals(5266.67, result.amount, 0.01) // mes 2
    }

    @Test
    fun `grace 0 dias - justo antes del mes no cambia`() {
        val now = LocalDateTime.of(2026, 4, 25, 12, 0) // exactamente 1 mes
        val result = calculatePaymentResult(settlement(), now, gracePeriodDays = 0)
        assertEquals(5000.0, result.amount, 0.01) // mes 1
    }

    @Test
    fun `grace 30 dias - retrasa el cambio de mes`() {
        // Con grace=30, necesitas 1 mes + 30 días para pasar a mes 2
        val now = LocalDateTime.of(2026, 5, 24, 12, 0) // ~2 meses después
        val result = calculatePaymentResult(settlement(), now, gracePeriodDays = 30)
        // now - 30 días = 24 abril, que es < 1 mes desde 25 marzo → mes 1
        assertEquals(5000.0, result.amount, 0.01)
    }

    @Test
    fun `grace 30 dias - pasa a mes 2 despues del umbral`() {
        val now = LocalDateTime.of(2026, 5, 26, 12, 0)
        val result = calculatePaymentResult(settlement(), now, gracePeriodDays = 30)
        // now - 30 días = 26 abril, que es > 1 mes desde 25 marzo → mes 2
        assertEquals(5266.67, result.amount, 0.01)
    }

    @Test
    fun `grace 14 mismo comportamiento que default`() {
        val now = nowAtMonth(6, graceDays = 14)
        val explicit = calculatePaymentResult(settlement(), now, gracePeriodDays = 14)
        val defaultResult = calculatePaymentResult(settlement(), now)
        assertEquals(explicit.amount, defaultResult.amount, 0.01)
        assertEquals(explicit.category, defaultResult.category)
    }

    // ============================================================
    // Continuidad de la curva
    // ============================================================

    @Test
    fun `escalones corto plazo son uniformes`() {
        val m1 = calculatePaymentResult(settlement(), nowAtMonth(1)).amount
        val m2 = calculatePaymentResult(settlement(), nowAtMonth(2)).amount
        val m3 = calculatePaymentResult(settlement(), nowAtMonth(3)).amount

        val step1to2 = m2 - m1
        val step2to3 = m3 - m2
        assertEquals(step1to2, step2to3, 0.01) // pasos iguales
    }

    @Test
    fun `escalones largo plazo son uniformes`() {
        val amounts = (6..12).map { month ->
            calculatePaymentResult(settlement(), nowAtMonth(month)).amount
        }
        val steps = amounts.zipWithNext { a, b -> b - a }
        // Todos los pasos deben ser iguales (250)
        steps.forEach { step ->
            assertEquals(250.0, step, 0.01)
        }
    }

    @Test
    fun `transicion mes 4 es continua con formula extendida`() {
        val m3 = calculatePaymentResult(settlement(), nowAtMonth(3)).amount
        val m4 = calculatePaymentResult(settlement(), nowAtMonth(4)).amount
        val m2 = calculatePaymentResult(settlement(), nowAtMonth(2)).amount
        // El paso 3→4 debe ser igual al paso 2→3
        assertEquals(m3 - m2, m4 - m3, 0.01)
    }

    // ============================================================
    // Otros precios: verificar con distintos montos
    // ============================================================

    @Test
    fun `precios bajos - contado 1000`() {
        val s = settlement(
            cashPrice = 1000.0,
            shortTermAmount = 1400.0,
            totalPrice = 2100.0,
            remainingBalance = 2100.0
        )
        val result = calculatePaymentResult(s, nowAtMonth(1))
        assertEquals(1000.0, result.amount, 0.01)
    }

    @Test
    fun `precios bajos - mes 6`() {
        val s = settlement(
            cashPrice = 1000.0,
            shortTermAmount = 1400.0,
            totalPrice = 2100.0,
            remainingBalance = 2100.0
        )
        val result = calculatePaymentResult(s, nowAtMonth(6))
        // 1400 + (1/8) * 700 = 1400 + 87.5
        assertEquals(1487.5, result.amount, 0.01)
    }

    @Test
    fun `precios iguales contado y corto plazo`() {
        val s = settlement(
            cashPrice = 5000.0,
            shortTermAmount = 5000.0,
            totalPrice = 7800.0,
            remainingBalance = 7800.0
        )
        // shortTermInterest = 0, meses 1-5 todos dan 5000
        val m2 = calculatePaymentResult(s, nowAtMonth(2))
        assertEquals(5000.0, m2.amount, 0.01)

        val m4 = calculatePaymentResult(s, nowAtMonth(4))
        assertEquals(5000.0, m4.amount, 0.01)
    }

    // ============================================================
    // Fecha de validez
    // ============================================================

    @Test
    fun `validUntil incluye 14 dias extra`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(1))
        // saleDate(25/03) + 1 mes = 25/04 + 14 días = 09/05/2026
        assertEquals("09/05/2026", result.validUntil)
    }

    @Test
    fun `validUntil mes 6`() {
        val result = calculatePaymentResult(settlement(), nowAtMonth(6))
        // saleDate(25/03) + 6 meses = 25/09 + 14 días = 09/10/2026
        assertEquals("09/10/2026", result.validUntil)
    }

    // ============================================================
    // Guardas: datos inválidos o edge cases
    // ============================================================

    @Test
    fun `shortTermAmount menor que cashPrice no genera interes negativo`() {
        val s = settlement(
            cashPrice = 5000.0,
            shortTermAmount = 3000.0,
            totalPrice = 7800.0,
            remainingBalance = 7800.0
        )
        val m2 = calculatePaymentResult(s, nowAtMonth(2))
        // safeShortTermAmount = max(3000, 5000) = 5000
        // interest = (5000 - 5000) / 3 = 0
        assertEquals(5000.0, m2.amount, 0.01)

        val m4 = calculatePaymentResult(s, nowAtMonth(4))
        assertEquals(5000.0, m4.amount, 0.01)
    }

    @Test
    fun `shortTermAmount cero con cashPrice positivo usa cashPrice como piso`() {
        val s = settlement(
            cashPrice = 5000.0,
            shortTermAmount = 0.0,
            totalPrice = 7800.0,
            remainingBalance = 7800.0
        )
        val m2 = calculatePaymentResult(s, nowAtMonth(2))
        assertEquals(5000.0, m2.amount, 0.01)

        val m6 = calculatePaymentResult(s, nowAtMonth(6))
        // safeShortTermAmount = 5000, progress = 1/8
        // 5000 + (1/8) * (7800 - 5000) = 5000 + 350
        assertEquals(5350.0, m6.amount, 0.01)
    }

    @Test
    fun `venta futura devuelve precio de contado`() {
        // Venta hecha "hoy" pero now es antes de la venta + grace
        val now = LocalDateTime.of(2026, 3, 25, 12, 0)
        val result = calculatePaymentResult(settlement(), now)
        assertEquals(5000.0, result.amount, 0.01)
        assertEquals("Precio de contado", result.category)
    }

    @Test
    fun `venta con fecha futura lejana devuelve contado`() {
        val s = settlement(date = "25/12/2026")
        val now = LocalDateTime.of(2026, 3, 25, 12, 0)
        val result = calculatePaymentResult(s, now)
        assertEquals(5000.0, result.amount, 0.01)
        assertEquals("Precio de contado", result.category)
    }

    @Test
    fun `cashPrice y shortTermAmount ambos cero con totalPrice devuelve precio total`() {
        val s = settlement(
            cashPrice = 0.0,
            shortTermAmount = 0.0,
            totalPrice = 7800.0,
            remainingBalance = 7800.0
        )
        val result = calculatePaymentResult(s, nowAtMonth(1))
        assertEquals(7800.0, result.amount, 0.01)
        assertEquals("Precio total", result.category)
    }

    @Test
    fun `cashPrice y shortTermAmount ambos cero con pagos parciales`() {
        val s = settlement(
            cashPrice = 0.0,
            shortTermAmount = 0.0,
            totalPrice = 7800.0,
            remainingBalance = 5000.0
        )
        val result = calculatePaymentResult(s, nowAtMonth(1))
        // 7800 - (7800 - 5000) = 5000
        assertEquals(5000.0, result.amount, 0.01)
    }

    @Test
    fun `todos los precios en cero devuelve no disponible`() {
        val s = settlement(
            cashPrice = 0.0,
            shortTermAmount = 0.0,
            totalPrice = 0.0,
            remainingBalance = 0.0
        )
        val result = calculatePaymentResult(s, nowAtMonth(1))
        assertEquals(0.0, result.amount, 0.01)
        assertEquals("No disponible", result.category)
    }

    @Test
    fun `venta futura con pagos parciales descuenta correctamente`() {
        val s = settlement(remainingBalance = 5800.0, date = "25/12/2026")
        val now = LocalDateTime.of(2026, 3, 25, 12, 0)
        val result = calculatePaymentResult(s, now)
        // cashPrice(5000) - totalPaid(7800-5800=2000) = 3000
        assertEquals(3000.0, result.amount, 0.01)
    }

    // ============================================================
    // Fix bug #6: "ahora" en zona de negocio (America/Mexico_City), no en la
    // zona del dispositivo. Ver AppTime.nowInBusinessZone.
    // ============================================================

    /**
     * Reproduce el bug corregido: con la fecha de venta 31/07/2026 y el instante fijo
     * 2026-09-01T02:00:00Z (grace=0 para aislar el efecto de zona):
     *  - Interpretado con zona de dispositivo UTC  -> 01/09/2026 02:00 (ya "septiembre")
     *  - Interpretado con zona de dispositivo CDMX (-06:00) -> 31/08/2026 20:00 (aun "agosto")
     *  - Interpretado con zona de dispositivo Tijuana (-07:00 en esa fecha) -> 31/08/2026 19:00
     *
     * El viejo `LocalDateTime.now()` (zona del dispositivo) hacia que un cobrador con el
     * telefono en UTC ofreciera "Precio a 2 meses" mientras uno en CDMX/Tijuana ofrecia
     * "Precio de contado" para la MISMA venta en el MISMO instante real — categoria y monto
     * distintos por un accidente de reloj del telefono, no por tiempo transcurrido real.
     */
    @Test
    fun `bug reproducido - LocalDateTime now() en zona dispositivo cambia categoria cerca de limite de mes`() {
        val s = settlement(date = "31/07/2026")
        val instant = Instant.parse("2026-09-01T02:00:00Z")

        val oldStyleNowUtc = instant.atZone(ZoneId.of("UTC")).toLocalDateTime()
        val oldStyleNowTijuana = instant.atZone(ZoneId.of("America/Tijuana")).toLocalDateTime()

        val resultUtc = calculatePaymentResult(s, oldStyleNowUtc, gracePeriodDays = 0)
        val resultTijuana = calculatePaymentResult(s, oldStyleNowTijuana, gracePeriodDays = 0)

        // Mismo instante real, mismo cliente, misma venta -> categorias DISTINTAS: el bug.
        assertEquals("Precio a 2 meses", resultUtc.category)
        assertEquals("Precio de contado", resultTijuana.category)
        assertNotEquals(
            "se esperaba reproducir el bug: categorias deberian diferir segun zona del dispositivo",
            resultUtc.category,
            resultTijuana.category
        )
    }

    @Test
    fun `fix - now de negocio (CDMX) no depende de la zona del dispositivo en limite de mes`() {
        val originalTz = TimeZone.getDefault()
        try {
            val s = settlement(date = "31/07/2026")
            val instant = Instant.parse("2026-09-01T02:00:00Z")
            val fakeClock = FakeClock(instant)

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val nowUnderUtcDefault = AppTime.nowInBusinessZone(fakeClock)
            val resultUnderUtcDefault =
                calculatePaymentResult(s, nowUnderUtcDefault, gracePeriodDays = 0)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val nowUnderTijuanaDefault = AppTime.nowInBusinessZone(fakeClock)
            val resultUnderTijuanaDefault =
                calculatePaymentResult(s, nowUnderTijuanaDefault, gracePeriodDays = 0)

            // AppTime.nowInBusinessZone ignora TimeZone.getDefault(): misma hora de negocio
            // (CDMX) sin importar en que zona este configurado el telefono.
            assertEquals(nowUnderUtcDefault, nowUnderTijuanaDefault)
            assertEquals("Precio de contado", resultUnderUtcDefault.category)
            assertEquals("Precio de contado", resultUnderTijuanaDefault.category)
            assertEquals(resultUnderUtcDefault.amount, resultUnderTijuanaDefault.amount, 0.01)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun `default de calculatePaymentResult usa AppTime nowInBusinessZone sin importar zona del dispositivo`() {
        val originalTz = TimeZone.getDefault()
        try {
            // Venta muy antigua: cae en "Precio total" sin importar el dia exacto de "hoy",
            // asi que sirve como smoke test de que el default (sin pasar `now`) no revienta
            // y no depende de la zona del dispositivo.
            val s = settlement(date = "01/01/2020")

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val resultUtc = calculatePaymentResult(s)

            TimeZone.setDefault(TimeZone.getTimeZone("America/Tijuana"))
            val resultTijuana = calculatePaymentResult(s)

            assertNotNull(resultUtc)
            assertEquals("Precio total", resultUtc.category)
            assertEquals(resultUtc.category, resultTijuana.category)
            assertEquals(resultUtc.amount, resultTijuana.amount, 0.01)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    // ============================================================
    // Fin de mes: dia 31 vs meses de 30/28 dias (ChronoUnit.MONTHS.between).
    // Comportamiento ACTUAL de la libreria, documentado (verificado empiricamente con
    // java.time directo) — no se altera con este fix, solo se caracteriza.
    // ============================================================

    @Test
    fun `fin de mes - venta dia 31 de enero sigue en mes 1 el 1 de marzo (febrero de 28 dias)`() {
        // 2026 no es bisiesto -> febrero tiene 28 dias. ChronoUnit.MONTHS.between no cuenta
        // el mes calendario completo entre 31/01 y 01/03 sino hasta el 02/03 (ver siguiente test).
        val s = settlement(date = "31/01/2026")
        val now = LocalDateTime.of(2026, 3, 1, 0, 0)

        val result = calculatePaymentResult(s, now, gracePeriodDays = 0)

        assertEquals("Precio de contado", result.category)
        assertEquals(5000.0, result.amount, 0.01)
    }

    @Test
    fun `fin de mes - venta dia 31 de enero pasa a mes 2 el 2 de marzo`() {
        val s = settlement(date = "31/01/2026")
        val now = LocalDateTime.of(2026, 3, 2, 0, 0)

        val result = calculatePaymentResult(s, now, gracePeriodDays = 0)

        assertEquals("Precio a 2 meses", result.category)
        assertEquals(5266.67, result.amount, 0.01)
    }

    @Test
    fun `fin de mes - venta dia 28 de enero ya esta en mes 2 un dia antes que la venta dia 31`() {
        // Mismo "now" (01/03), pero la venta del dia 28 (sin recorte de mes corto) ya cuenta
        // un mes completo, mientras que la del dia 31 (test anterior) todavia no. Documenta que
        // una venta fechada el ultimo dia de un mes largo obtiene, por aritmetica de calendario,
        // un dia extra de "Precio de contado" frente a una fechada unos dias antes.
        val s = settlement(date = "28/01/2026")
        val now = LocalDateTime.of(2026, 3, 1, 0, 0)

        val result = calculatePaymentResult(s, now, gracePeriodDays = 0)

        assertEquals("Precio a 2 meses", result.category)
        assertEquals(5266.67, result.amount, 0.01)
    }

    @Test
    fun `fin de mes - venta dia 31 de enero, meses de 30 dias (abril) tambien corren la frontera`() {
        val s = settlement(date = "31/01/2026")
        val now = LocalDateTime.of(2026, 5, 1, 0, 0)

        val result = calculatePaymentResult(s, now, gracePeriodDays = 0)

        assertEquals("Precio a 3 meses", result.category)
        assertEquals(5533.33, result.amount, 0.01)
    }

    @Test
    fun `fin de mes - venta dia 31 de enero pasa a mes 4 el 2 de mayo`() {
        val s = settlement(date = "31/01/2026")
        val now = LocalDateTime.of(2026, 5, 2, 0, 0)

        val result = calculatePaymentResult(s, now, gracePeriodDays = 0)

        assertEquals("Precio a 4 meses", result.category)
        assertEquals(5800.0, result.amount, 0.01)
    }
}
