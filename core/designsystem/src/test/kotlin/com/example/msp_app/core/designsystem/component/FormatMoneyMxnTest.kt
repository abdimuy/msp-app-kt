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
 * Contrato caracterizado contra el JRE (`DecimalFormat("$#,##0")` con
 * `Locale("es","MX")`, `RoundingMode.HALF_UP`): símbolo `$` literal, coma de
 * miles (U+002C), signo negativo `-` (U+002D) como prefijo antes del `$`,
 * **nunca decimales** — decisión de negocio (MSP no opera con centavos): el
 * monto que entra es exacto a centavos (`BigDecimal`/`Money` de escala 2 sin
 * cambios), pero el string de DISPLAY siempre redondea al peso entero.
 */
class FormatMoneyMxnTest {

    // --- Casos base del brief -------------------------------------------------

    @Test
    fun `cero formatea sin decimales`() {
        assertEquals("$0", formatMoneyMxn(BigDecimal("0")))
    }

    @Test
    fun `entero positivo agrupa miles`() {
        assertEquals("$1,200", formatMoneyMxn(BigDecimal("1200")))
    }

    @Test
    fun `monto con un decimal se redondea al peso`() {
        assertEquals("$18,301", formatMoneyMxn(BigDecimal("18300.5")))
    }

    @Test
    fun `monto exacto del mockup del piloto`() {
        // $18,300 es el ejemplo textual del mockup (hero del reporte de cobranza) —
        // sin decimales, esta es la razón de ser del cambio.
        assertEquals("$18,300", formatMoneyMxn(BigDecimal("18300")))
    }

    @Test
    fun `decimales por debajo de medio peso se redondean hacia abajo`() {
        assertEquals("$99", formatMoneyMxn(BigDecimal("99.40")))
    }

    // --- Negativos ------------------------------------------------------------

    @Test
    fun `negativo lleva el signo antes del simbolo`() {
        assertEquals("-$850", formatMoneyMxn(BigDecimal("-850")))
    }

    @Test
    fun `negativo grande agrupa miles y millones`() {
        assertEquals("-$1,234,568", formatMoneyMxn(BigDecimal("-1234567.89")))
    }

    @Test
    fun `negativo redondea alejandose de cero (HALF_UP)`() {
        assertEquals("-$2", formatMoneyMxn(BigDecimal("-1.50")))
    }

    @Test
    fun `cero negativo se normaliza a cero sin signo`() {
        // DecimalFormat imprimiría "-$0" para un negativo que redondea a 0;
        // formatMoneyMxn lo normaliza a "$0" (un menos delante de cero es
        // ruido visual, no dinero real).
        assertEquals("$0", formatMoneyMxn(BigDecimal("-0.001")))
        assertEquals("$0", formatMoneyMxn(BigDecimal("-0.00")))
        assertEquals("$0", formatMoneyMxn(BigDecimal("-0.40")))
    }

    // --- Agrupación de miles / millones / miles de millones -------------------

    @Test
    fun `mil exacto agrupa`() {
        assertEquals("$1,000", formatMoneyMxn(BigDecimal("1000")))
    }

    @Test
    fun `millones no se truncan`() {
        assertEquals("$1,000,000", formatMoneyMxn(BigDecimal("1000000")))
        assertEquals("$1,234,568", formatMoneyMxn(BigDecimal("1234567.89")))
    }

    @Test
    fun `monto muy grande de miles de millones no se trunca`() {
        assertEquals("$1,000,000,000", formatMoneyMxn(BigDecimal("999999999.99")))
        assertEquals("$12,345,678,901,235", formatMoneyMxn(BigDecimal("12345678901234.56")))
    }

    // --- Redondeo HALF_UP al peso entero (convención de dinero, la trampa clásica) --------

    @Test
    fun `medio peso sube (HALF_UP, no HALF_EVEN)`() {
        // El default de DecimalFormat es HALF_EVEN, que dejaría "$18,300" para un
        // valor que exactamente está a medio peso de dos enteros pares/impares.
        // Se fija HALF_UP explícito -> el medio peso siempre sube.
        assertEquals("$19", formatMoneyMxn(BigDecimal("18.50")))
        assertEquals("$21", formatMoneyMxn(BigDecimal("20.50")))
    }

    @Test
    fun `la vieja trampa de Double (2_675) ya no aplica al redondear a peso entero`() {
        // Con 2 decimales, 2.675 como Double se representaba mal (2.67499...) y
        // rompía el redondeo comercial. Al peso entero el caso ya no es
        // observable en el resultado (2.675 redondea a $3 con BigDecimal exacto
        // o con Double), pero el contrato sigue exigiendo BigDecimal: el `amount`
        // que llega aquí puede venir de sumas con muchos decimales de un `Money`
        // exacto, y solo BigDecimal garantiza que esa suma previa fue correcta.
        assertEquals("$3", formatMoneyMxn(BigDecimal("2.675")))
    }

    @Test
    fun `redondeo arrastra el acarreo (999_5 a 1000)`() {
        assertEquals("$1,000", formatMoneyMxn(BigDecimal("999.5")))
    }

    @Test
    fun `decimales extra se redondean al peso`() {
        assertEquals("$3", formatMoneyMxn(BigDecimal("3.14159")))
        assertEquals("$1", formatMoneyMxn(BigDecimal("0.994")))
        assertEquals("$1", formatMoneyMxn(BigDecimal("0.995")))
        assertEquals("$0", formatMoneyMxn(BigDecimal("0.494")))
    }

    // --- Determinismo de locale -----------------------------------------------

    @Test
    fun `el formato es independiente del locale por defecto del dispositivo`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY) // usa "." para miles y "," decimal
            assertEquals("$1,234,568", formatMoneyMxn(BigDecimal("1234567.89")))
            Locale.setDefault(Locale.US)
            assertEquals("$1,234,568", formatMoneyMxn(BigDecimal("1234567.89")))
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
            assertEquals("$1,235", formatMoneyMxn(BigDecimal("1234.56")))
            assertEquals("$0", formatMoneyMxn(BigDecimal("-0.001")))

            Locale.setDefault(Locale("th", "TH")) // tailandés (dígitos tailandeses por default)
            assertEquals("$1,235", formatMoneyMxn(BigDecimal("1234.56")))
            assertEquals("-$850", formatMoneyMxn(BigDecimal("-850")))
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
