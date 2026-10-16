package com.example.msp_app.core.common.ventas.precios

import java.math.BigDecimal
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/** E-APP-062: espejo de la regla de orden de escalones del servidor. */
class ReglaOrdenDePreciosTest {

    private fun bd(s: String) = BigDecimal(s)

    private fun linea(
        contado: String,
        corto: String,
        anual: String,
        tipo: TipoDeLinea = TipoDeLinea.PRODUCTO,
        cantidad: String = "1",
        dentroDeCombo: Boolean = false
    ) = LineaDePrecios(tipo, "art", bd(cantidad), bd(contado), bd(corto), bd(anual), dentroDeCombo)

    @Test
    fun `E-APP-062 mensajes y codigos identicos a msp-api errors_go 613 y 622`() {
        assertEquals("precio_tier_order_invalid", ReglaOrdenDePrecios.CODIGO_LINEA)
        assertEquals(
            "los precios capturados de la línea deben ir de menor a mayor: contado, corto plazo y anual",
            ReglaOrdenDePrecios.MENSAJE_LINEA
        )
        assertEquals("monto_tier_order_invalid", ReglaOrdenDePrecios.CODIGO_TOTALES)
        assertEquals(
            "los totales de la venta son imposibles: los montos capturados deben ir de menor a mayor: " +
                "contado, corto plazo y anual",
            ReglaOrdenDePrecios.MENSAJE_TOTALES
        )
    }

    @Test
    fun `E-APP-062 la linea que rompe la regla viaja en el veredicto`() {
        val mala = linea("1300", "1200", "1600")
        val v = ReglaOrdenDePrecios.validar(listOf(linea("1", "2", "3"), mala))
        assertEquals(
            VeredictoOrdenDePrecios.Invalido(
                ReglaOrdenDePrecios.CODIGO_LINEA,
                ReglaOrdenDePrecios.MENSAJE_LINEA,
                mala
            ),
            v
        )
    }

    @Test
    fun `E-APP-062 producto hijo de combo se valida por linea pero no suma a totales`() {
        // Sin la exclusión, contado total = 1000 + 5000 > corto 1200.
        val combo = linea("1000", "1200", "1600", TipoDeLinea.COMBO)
        val hijo = linea("5000", "0", "0", dentroDeCombo = true)
        assertEquals(
            VeredictoOrdenDePrecios.Valido,
            ReglaOrdenDePrecios.validar(listOf(combo, hijo))
        )
        val hijoMalo = linea("5000", "4000", "5000", dentroDeCombo = true)
        val v = ReglaOrdenDePrecios.validar(listOf(combo, hijoMalo))
        assertEquals(
            ReglaOrdenDePrecios.CODIGO_LINEA,
            (v as VeredictoOrdenDePrecios.Invalido).codigo
        )
    }

    @Test
    fun `E-APP-062 propiedad - ordenValido equivale a la definicion por pares no cero`() {
        val rnd = Random(SEMILLA)
        repeat(ITERACIONES) {
            val t = List(3) { if (rnd.nextInt(4) == 0) 0 else rnd.nextInt(1, 50) }
            val nz = t.filter { it != 0 }
            val esperado = nz == nz.sorted()
            assertEquals(
                "$t",
                esperado,
                ReglaOrdenDePrecios.ordenValido(bd("${t[0]}"), bd("${t[1]}"), bd("${t[2]}"))
            )
        }
    }

    @Test
    fun `E-APP-062 propiedad - lineas ordenadas con cantidades positivas dan totales ordenados`() {
        val rnd = Random(SEMILLA)
        repeat(ITERACIONES) {
            val lineas = List(rnd.nextInt(1, 6)) {
                val c = rnd.nextInt(1, 2000)
                val k = c + rnd.nextInt(0, 500)
                val a = k + rnd.nextInt(0, 500)
                linea("$c", "$k", "$a", cantidad = "${rnd.nextInt(1, 5)}")
            }
            assertEquals(
                VeredictoOrdenDePrecios.Valido,
                ReglaOrdenDePrecios.validar(lineas)
            )
        }
    }

    private companion object {
        const val SEMILLA = 20261016
        const val ITERACIONES = 2000
    }
}
