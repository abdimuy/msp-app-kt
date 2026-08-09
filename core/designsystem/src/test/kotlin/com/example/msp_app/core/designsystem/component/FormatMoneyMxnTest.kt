package com.example.msp_app.core.designsystem.component

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Suite exhaustiva de [formatMoneyMxn] — el logic-path de dinero del design
 * system, del que depende todo display de monto del piloto. JVM puro (sin
 * Robolectric): `DecimalFormat` es de la stdlib.
 *
 * Contrato caracterizado contra el JRE (`DecimalFormat("$#,##0.00")` con
 * `Locale("es","MX")`, `RoundingMode.HALF_UP`): símbolo `$` literal, coma de
 * miles (U+002C), punto decimal (U+002E), signo negativo `-` (U+002D) como
 * prefijo antes del `$`, siempre 2 decimales.
 */
class FormatMoneyMxnTest {

    // --- Casos base del brief -------------------------------------------------

    @Test
    fun `cero formatea con dos decimales`() {
        assertEquals("$0.00", formatMoneyMxn(BigDecimal("0")))
    }

    @Test
    fun `entero positivo agrupa miles`() {
        assertEquals("$1,200.00", formatMoneyMxn(BigDecimal("1200")))
    }

    @Test
    fun `un decimal se completa a dos`() {
        assertEquals("$18,300.50", formatMoneyMxn(BigDecimal("18300.5")))
    }

    @Test
    fun `dos decimales exactos se preservan`() {
        assertEquals("$99.99", formatMoneyMxn(BigDecimal("99.99")))
    }

    // --- Negativos ------------------------------------------------------------

    @Test
    fun `negativo lleva el signo antes del simbolo`() {
        assertEquals("-$850.00", formatMoneyMxn(BigDecimal("-850")))
    }

    @Test
    fun `negativo grande agrupa miles y millones`() {
        assertEquals("-$1,234,567.89", formatMoneyMxn(BigDecimal("-1234567.89")))
    }

    @Test
    fun `negativo redondea alejandose de cero (HALF_UP)`() {
        assertEquals("-$1.01", formatMoneyMxn(BigDecimal("-1.005")))
    }

    @Test
    fun `cero negativo se normaliza a cero sin signo`() {
        // DecimalFormat imprimiría "-$0.00" para un negativo que redondea a 0;
        // formatMoneyMxn lo normaliza a "$0.00" (un menos delante de cero es
        // ruido visual, no dinero real).
        assertEquals("$0.00", formatMoneyMxn(BigDecimal("-0.001")))
        assertEquals("$0.00", formatMoneyMxn(BigDecimal("-0.00")))
        assertEquals("$0.00", formatMoneyMxn(BigDecimal("-0.004")))
    }

    // --- Agrupación de miles / millones / miles de millones -------------------

    @Test
    fun `mil exacto agrupa`() {
        assertEquals("$1,000.00", formatMoneyMxn(BigDecimal("1000")))
    }

    @Test
    fun `millones no se truncan`() {
        assertEquals("$1,000,000.00", formatMoneyMxn(BigDecimal("1000000")))
        assertEquals("$1,234,567.89", formatMoneyMxn(BigDecimal("1234567.89")))
    }

    @Test
    fun `monto muy grande de miles de millones no se trunca`() {
        assertEquals("$999,999,999.99", formatMoneyMxn(BigDecimal("999999999.99")))
        assertEquals("$12,345,678,901,234.56", formatMoneyMxn(BigDecimal("12345678901234.56")))
    }

    // --- Redondeo HALF_UP (convención de dinero) ------------------------------

    @Test
    fun `medio centavo sube (HALF_UP, no HALF_EVEN)`() {
        // El default de DecimalFormat es HALF_EVEN, que daría "$1.00". Se fija
        // HALF_UP explícito → "$1.01" (redondeo comercial mexicano).
        assertEquals("$1.01", formatMoneyMxn(BigDecimal("1.005")))
        assertEquals("$0.01", formatMoneyMxn(BigDecimal("0.005")))
    }

    @Test
    fun `HALF_UP redondea 2_345 hacia arriba (HALF_EVEN daria 2_34)`() {
        assertEquals("$2.35", formatMoneyMxn(BigDecimal("2.345")))
    }

    @Test
    fun `BigDecimal exacto evita la trampa de Double (2_675)`() {
        // Como Double, 2.675 se representa como 2.67499… y redondearía a $2.67.
        // Como BigDecimal exacto, HALF_UP da el $2.68 correcto — por esto el
        // contrato exige BigDecimal, nunca Double.
        assertEquals("$2.68", formatMoneyMxn(BigDecimal("2.675")))
    }

    @Test
    fun `redondeo arrastra el acarreo (9_999 a 10_00)`() {
        assertEquals("$10.00", formatMoneyMxn(BigDecimal("9.999")))
    }

    @Test
    fun `decimales extra se redondean a dos`() {
        assertEquals("$3.14", formatMoneyMxn(BigDecimal("3.14159")))
        assertEquals("$0.99", formatMoneyMxn(BigDecimal("0.994")))
        assertEquals("$1.00", formatMoneyMxn(BigDecimal("0.995")))
    }

    // --- Determinismo de locale -----------------------------------------------

    @Test
    fun `el formato es independiente del locale por defecto del dispositivo`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY) // usa "." para miles y "," decimal
            assertEquals("$1,234,567.89", formatMoneyMxn(BigDecimal("1234567.89")))
            Locale.setDefault(Locale.US)
            assertEquals("$1,234,567.89", formatMoneyMxn(BigDecimal("1234567.89")))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `bajo locale arabe o tailandes conserva digitos latinos y formato es-MX`() {
        // Locales cuyo default nativo usa dígitos no-latinos (arábigo-índicos /
        // tailandeses): al fijar es-MX explícito en los símbolos, la salida
        // sigue en dígitos latinos y con formato mexicano — device-independent.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "EG")) // árabe (dígitos arábigo-índicos por default)
            assertEquals("$1,234.56", formatMoneyMxn(BigDecimal("1234.56")))
            assertEquals("$0.00", formatMoneyMxn(BigDecimal("-0.001")))

            Locale.setDefault(Locale("th", "TH")) // tailandés (dígitos tailandeses por default)
            assertEquals("$1,234.56", formatMoneyMxn(BigDecimal("1234.56")))
            assertEquals("-$850.00", formatMoneyMxn(BigDecimal("-850")))
        } finally {
            Locale.setDefault(original)
        }
    }

    // --- Constante de máscara -------------------------------------------------

    @Test
    fun `MASKED_MONEY es el glifo de privacidad exacto`() {
        assertEquals("$••••", MASKED_MONEY)
    }
}
