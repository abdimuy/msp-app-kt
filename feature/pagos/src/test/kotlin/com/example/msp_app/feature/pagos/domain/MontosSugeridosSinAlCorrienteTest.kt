package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **"Al corriente" ya no se sugiere** (decisión del dueño del 2026-10-16). Los
 * demás sugeridos siguen en el mismo orden.
 *
 * Las pruebas no nombran el símbolo `AL_CORRIENTE` a propósito: comparan por
 * nombre y por rótulo, para que sigan compilando —y se pongan en ROJO— si
 * alguien devuelve la entrada al enum y a la lista de candidatos.
 */
class MontosSugeridosSinAlCorrienteTest {

    private val hoy = PagosFixtures.HOY

    /**
     * Propiedad sobre [CASOS] cuentas generadas con semilla fija: ningún
     * sugerido es "al corriente", y la invariante de dinero se sostiene.
     *
     * **Control positivo**: el generador tiene que producir muchas cuentas con
     * atraso propio —un `alCorriente` mayor que cero y distinto de las otras
     * cifras de la deuda—, que son justo las que antes pintaban el chip. Si el
     * generador dejara de producirlas, la propiedad pasaría sin medir nada.
     */
    @Test
    fun `ninguna cuenta generada sugiere al corriente`() {
        val azar = Random(SEMILLA)
        var conAtrasoPropio = 0
        repeat(CASOS) { i ->
            val venta = cuentaAlAzar(azar)
            val historial = historialAlAzar(azar)
            val sugeridos = MontosSugeridos.de(venta, historial)
            val caso = "caso $i: saldo=${venta.saldo.amount} sugeridos=$sugeridos"

            sugeridos.forEach {
                assertTrue(caso, it.cual.name in PERMITIDAS)
                assertTrue(caso, it.cual.etiqueta != "Al corriente")
                assertTrue(caso, it.importe > Money.ZERO)
                assertTrue(caso, it.importe <= venta.saldo)
            }
            assertTrue(caso, sugeridos.size <= MontosSugeridos.MAXIMO_DE_CHIPS)
            assertEquals(caso, sugeridos.size, sugeridos.map { it.importe }.toSet().size)

            val atraso = MontosSugeridos.alCorriente(venta, hoy)
            if (atraso > Money.ZERO &&
                atraso != MontosSugeridos.esperadoHoy(venta) &&
                atraso != MontosSugeridos.liquidar(venta)
            ) {
                conAtrasoPropio++
            }
        }
        assertTrue(
            "control positivo: sólo $conAtrasoPropio de $CASOS cuentas traían atraso propio",
            conAtrasoPropio >= CASOS / 5
        )
    }

    @Test
    fun `la entrada al corriente ya no existe`() {
        assertTrue(MontosSugeridos.Sugerencia.entries.none { it.name == "AL_CORRIENTE" })
    }

    /**
     * El caso del de-duplicado: sin oferta de liquidación, "liquidar" es el
     * saldo, y con un atraso igual al saldo "al corriente" valía lo mismo. Antes
     * el chip que sobrevivía se rotulaba AL CORRIENTE; ahora se rotula
     * **Liquidar**, que es lo que de verdad hace ese importe.
     */
    @Test
    fun `cuando el atraso vale el saldo el chip dice liquidar`() {
        val venta = AbonoFixtures.detalle().copy(
            liquidacion = null,
            enganche = Money.ZERO,
            abonado = Money.ZERO,
            fechaVenta = hoy.minusDays(700)
        )
        assertEquals(venta.saldo, MontosSugeridos.alCorriente(venta, hoy))
        val sugeridos = MontosSugeridos.de(venta)
        assertEquals(
            listOf(
                MontosSugeridos.Sugerencia.ESPERADO_HOY,
                MontosSugeridos.Sugerencia.LIQUIDAR
            ),
            sugeridos.take(2).map { it.cual }
        )
        assertEquals(venta.saldo, sugeridos[1].importe)
        assertEquals("Liquidar", sugeridos[1].cual.etiqueta)
    }

    private fun cuentaAlAzar(
        azar: Random
    ): com.example.msp_app.feature.pagos.domain.model.DetalleVenta {
        val parcialidad = pesos(azar.nextInt(0, 801))
        val saldo = pesos(azar.nextInt(0, 12_001))
        val enganche = pesos(azar.nextInt(0, 1_501))
        val abonado = pesos(enganche.amount.toInt() + azar.nextInt(0, 8_001))
        val origen = OrigenDeLaCuota.entries.random(azar)
        val cuota = CuotaDeLaVenta(
            if (origen == OrigenDeLaCuota.DUDOSA) Money.ZERO else pesos(azar.nextInt(0, 801)),
            origen
        )
        val liquidacion = if (azar.nextBoolean()) {
            null
        } else {
            Liquidacion(
                monto = pesos(azar.nextInt(0, 14_001)),
                vigenteHasta = null,
                categoria = "azar"
            )
        }
        val base = AbonoFixtures.detalle()
        return base.copy(
            saldo = saldo,
            parcialidad = parcialidad,
            cuota = cuota,
            enganche = enganche,
            abonado = abonado,
            fechaVenta = if (azar.nextInt(10) == 0) {
                null
            } else {
                hoy.minusDays(
                    azar.nextLong(-30, 900)
                )
            },
            frecuencia = FRECUENCIAS.random(azar),
            liquidacion = liquidacion,
            estado = base.estado.copy(abonoDelPeriodo = pesos(azar.nextInt(0, 400)))
        )
    }

    private fun historialAlAzar(azar: Random): List<AbonoPrevio> = List(azar.nextInt(0, 6)) { dia ->
        AbonoPrevio(
            fecha = Instant.parse("2026-08-%02dT12:00:00Z".format(dia + 1)),
            importe = pesos(listOf(100, 150, 200, 250, 300).random(azar))
        )
    }

    private fun pesos(n: Int): Money = Money.of(BigDecimal(n))

    private companion object {
        const val SEMILLA = 20261016
        const val CASOS = 5_000
        val FRECUENCIAS = listOf("semanal", "quincenal", "mensual", "otra", " Semanal ")
        val PERMITIDAS = setOf(
            "ESPERADO_HOY",
            "ESPERADO_POR_COSTUMBRE",
            "LIQUIDAR",
            "LO_DE_SIEMPRE",
            "REDONDO"
        )
    }
}
