package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La cuota que la pantalla puede afirmar**, por la precedencia de tres
 * escalones.
 *
 * El caso que trajo todo esto: una venta con `PARCIALIDAD = 3000` y **ni un
 * solo pago**, donde la pantalla decía *"abono corto · esperado $3,000 · este
 * abono $600"*. El dato estaba mal capturado y nadie podía desmentirlo.
 *
 * Lo que estas pruebas fijan, en orden de importancia:
 *
 * 1. **Con tres pagos o más, la parcialidad no se mira.** Es el escalón que
 *    cubre al 91 % de la ruta y el que vuelve irrelevante una captura mala.
 * 2. **El escalón 3 es conservador.** El ejemplo que lo motivó resultó ser un
 *    dato de prueba, así que esto defiende un caso posible y no uno observado:
 *    una cuota alta pero plausible **no** puede encender el aviso.
 * 3. **Sin muestra no hay opinión.** Una ruta que no ha cobrado mil veces no
 *    declara nada absurdo.
 */
class CuotaDeLaVentaTest {

    // ── Escalón 1: manda el comportamiento ────────────────────────────────

    @Test
    fun `con tres pagos iguales la cuota sale del comportamiento`() {
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("3000"),
            pagosDeLaVenta = listOf(abono("200", 1), abono("200", 2), abono("200", 3)),
            lineaBase = null
        )

        // La parcialidad capturada dice $3,000 y no se mira: esta cuenta paga
        // $200, y eso es un hecho.
        assertEquals(dinero("200"), cuota.monto)
        assertEquals(OrigenDeLaCuota.COMPORTAMIENTO, cuota.origen)
        assertEquals(dinero("200"), cuota.esperado)
    }

    @Test
    fun `tres pagos sin ninguno repetido no son costumbre y caen al escalon de abajo`() {
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("220"),
            pagosDeLaVenta = listOf(abono("100", 1), abono("250", 2), abono("300", 3)),
            lineaBase = null
        )

        assertEquals(dinero("220"), cuota.monto)
        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    // ── Escalón 2: uno o dos pagos, contrastados ─────────────────────────

    @Test
    fun `con un pago que cuadra, la parcialidad se afirma`() {
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("220"),
            pagosDeLaVenta = listOf(abono("200", 1)),
            lineaBase = null
        )

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    @Test
    fun `un abono corto normal NO pone en duda la parcialidad`() {
        // $50 sobre una cuota de $220 es un abono corto de los de todos los
        // días: el 24 % de los abonos de la ruta queda por debajo de lo
        // esperado. Con uno o dos pagos la evidencia es delgada y el bar tiene
        // que estar alto.
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("220"),
            pagosDeLaVenta = listOf(abono("50", 1)),
            lineaBase = null
        )

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    @Test
    fun `diez veces exactas todavia no bastan para dudar`() {
        // La frontera, por abajo: el bar es "MÁS de un orden de magnitud".
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("2000"),
            pagosDeLaVenta = listOf(abono("200", 1)),
            lineaBase = null
        )

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    @Test
    fun `pasado un orden de magnitud, la parcialidad queda en duda`() {
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("2050"),
            pagosDeLaVenta = listOf(abono("200", 1)),
            lineaBase = null
        )

        assertEquals(OrigenDeLaCuota.DUDOSA, cuota.origen)
        // Y lo que la pantalla afirma como esperado es CERO: "no se sabe qué
        // toca", el mismo contrato que ya tenían `SeguridadDelAbono` y
        // `AvisosDelAbono`.
        assertEquals(Money.ZERO, cuota.esperado)
        assertEquals(dinero("2050"), cuota.monto)
    }

    // ── Escalón 3: ningún pago, contra la ruta ───────────────────────────

    @Test
    fun `sin pagos y sin linea base no se duda de nada`() {
        // El lado conservador cuando no hay con qué opinar.
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("3000"),
            pagosDeLaVenta = emptyList(),
            lineaBase = null
        )

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    @Test
    fun `sin pagos, una cuota alta pero plausible NO enciende el aviso`() {
        // La ruta tiene 34 ventas con cuota de $300 a $1,000, todas legítimas.
        // Con un techo de $600, $1,000 sigue por debajo del doble: no se avisa.
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("1000"),
            pagosDeLaVenta = emptyList(),
            lineaBase = lineaBase(techo = "600")
        )

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    @Test
    fun `el doble exacto del techo todavia no es absurdo`() {
        // La frontera, por abajo.
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("1200"),
            pagosDeLaVenta = emptyList(),
            lineaBase = lineaBase(techo = "600")
        )

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, cuota.origen)
    }

    @Test
    fun `sin pagos, una cuota absurda para la ruta si enciende el aviso`() {
        // El caso del reporte: $3,000 contra un techo de $600.
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("3000"),
            pagosDeLaVenta = emptyList(),
            lineaBase = lineaBase(techo = "600")
        )

        assertEquals(OrigenDeLaCuota.DUDOSA, cuota.origen)
        assertEquals(Money.ZERO, cuota.esperado)
    }

    @Test
    fun `la linea base NO se aplica cuando la venta si tiene pagos`() {
        // Una venta con cuota alta que SÍ se paga: $2,000 pagando $2,000. El
        // escalón 1 la resuelve y la ruta no tiene nada que opinar. Es el caso
        // que un umbral global mal puesto habría marcado en falso.
        val cuota = CuotaDeLaVenta.de(
            parcialidad = dinero("2000"),
            pagosDeLaVenta = listOf(abono("2000", 1), abono("2000", 2), abono("2000", 3)),
            lineaBase = lineaBase(techo = "600")
        )

        assertEquals(OrigenDeLaCuota.COMPORTAMIENTO, cuota.origen)
        assertEquals(dinero("2000"), cuota.monto)
    }

    private fun lineaBase(techo: String) = LineaBaseDeLaRuta(
        techo = dinero(techo),
        pagosMedidos = LineaBaseDeLaRuta.MINIMO_DE_PAGOS
    )

    private fun abono(pesos: String, dia: Int) = AbonoPrevio(
        fecha = Instant.parse("2026-08-%02dT12:00:00Z".format(dia)),
        importe = dinero(pesos)
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}

/**
 * **La línea base de la ruta**: el percentil 99 de lo que la ruta paga de
 * verdad.
 *
 * Las dos decisiones que estas pruebas fijan son las que el estadístico tenía
 * que contestar: **cuál percentil** y **cuánta muestra hace falta** antes de
 * dejarlo opinar.
 */
class LineaBaseDeLaRutaTest {

    /**
     * **Los dos números elegidos, fijados a mano.**
     *
     * Las pruebas de frontera se escriben contra las constantes, así que se
     * mueven con ellas: son correctas donde esté el umbral, pero no opinan
     * sobre DÓNDE debe estar. Esto sí — cambiar el percentil o el piso de
     * muestra deja de ser un ajuste silencioso y pasa por aquí.
     */
    @Test
    fun `el estadistico elegido es el percentil 99 sobre mil pagos`() {
        assertEquals(99, LineaBaseDeLaRuta.PERCENTIL)
        assertEquals(1_000, LineaBaseDeLaRuta.MINIMO_DE_PAGOS)
    }

    @Test
    fun `sin muestra suficiente no hay linea base`() {
        // Con 999 pagos el percentil 99 es la décima más grande contando desde
        // arriba: todavía se comporta como un máximo. Callar es el lado
        // conservador — sin línea base, el escalón 3 no avisa.
        val casi = List(LineaBaseDeLaRuta.MINIMO_DE_PAGOS - 1) { dinero("100") }

        assertNull(LineaBaseDeLaRuta.de(casi))
    }

    @Test
    fun `con la muestra justa ya hay linea base`() {
        // La frontera exacta, por arriba.
        val justos = List(LineaBaseDeLaRuta.MINIMO_DE_PAGOS) { dinero("100") }

        val linea = LineaBaseDeLaRuta.de(justos)

        assertEquals(dinero("100"), linea!!.techo)
        assertEquals(LineaBaseDeLaRuta.MINIMO_DE_PAGOS, linea.pagosMedidos)
    }

    @Test
    fun `el percentil 99 deja fuera el uno por ciento mas alto`() {
        // 990 pagos de $100 y 10 de $5,000. El percentil 99 cae en el último
        // $100: la cola no lo mueve.
        val muestra = List(990) { dinero("100") } + List(10) { dinero("5000") }

        assertEquals(dinero("100"), LineaBaseDeLaRuta.de(muestra)!!.techo)
    }

    @Test
    fun `un solo pago disparatado NO mueve la linea base, y el maximo si`() {
        // El control que justifica el percentil frente al máximo: la misma
        // muestra con una fila mal tecleada de $99,999 da el MISMO techo.
        val limpia = List(1000) { dinero("100") }
        val contaminada = List(999) { dinero("100") } + dinero("99999")

        assertEquals(
            LineaBaseDeLaRuta.de(limpia)!!.techo,
            LineaBaseDeLaRuta.de(contaminada)!!.techo
        )
        assertTrue(
            "y el máximo SÍ se habría movido: por eso no se usa el máximo",
            contaminada.max() > limpia.max()
        )
    }

    @Test
    fun `los importes en cero no entran a la muestra`() {
        // Mil filas, pero cien son ceros: no son dinero que entró y contarlos
        // correría el percentil hacia abajo. Quedan 900 y ya no alcanza.
        val conCeros = List(100) { Money.ZERO } + List(900) { dinero("100") }

        assertNull(LineaBaseDeLaRuta.de(conCeros))
    }

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
