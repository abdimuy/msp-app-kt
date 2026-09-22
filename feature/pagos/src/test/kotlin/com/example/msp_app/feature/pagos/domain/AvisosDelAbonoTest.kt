package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Los cuatro niveles de aviso.** La rareza decide el nivel, y cada umbral
 * tiene su prueba **en la frontera exacta**: 3 cuotas, 6 cuotas, 12 cuotas, el
 * múltiplo de 50 y el de al lado.
 *
 * Las fronteras son el contrato entero de este archivo. Un umbral que se corre
 * un peso convierte el aviso raro (0.08 % de los abonos) en uno frecuente, y un
 * aviso frecuente se aprende a ignorar — que es exactamente la falla que estos
 * niveles existen para no tener.
 *
 * La cuenta de estas pruebas: **cuota de $250**, **saldo de $100,000** (lo
 * bastante alto para que nada choque con el bloqueo duro) y **esperado hoy de
 * $250**.
 */
class AvisosDelAbonoTest {

    private val cuota = dinero("250")
    private val saldo = dinero("100000")
    private val esperadoHoy = dinero("250")

    // ── Nivel 0: bloqueo ──────────────────────────────────────────────────

    @Test
    fun `un sobrepago es nivel bloqueo y no inventa mensajes`() {
        val aviso = evaluar(dinero("100050"))

        assertEquals(NivelDeAviso.BLOQUEO, aviso.nivel)
        assertEquals(listOf(SenalDelMonto.NO_SE_PUEDE_REGISTRAR), aviso.senales)
        // La banda roja del sobrepago ya dice el máximo registrable. Un segundo
        // texto para el mismo hecho es ruido en una pantalla de dinero.
        assertEquals(emptyList<String>(), aviso.mensajes)
    }

    @Test
    fun `un monto bloqueado no arrastra ninguna rareza de los niveles de arriba`() {
        // $100,001 no es múltiplo de 50 Y son 400 cuotas. Nada de eso importa:
        // un monto prohibido no necesita además ruido de "¿seguro?" encima.
        val aviso = evaluar(dinero("100001"))

        assertEquals(NivelDeAviso.BLOQUEO, aviso.nivel)
        assertEquals(listOf(SenalDelMonto.NO_SE_PUEDE_REGISTRAR), aviso.senales)
    }

    // ── Nivel 1: nota al pie ──────────────────────────────────────────────

    @Test
    fun `menos de lo esperado es nivel nota, y su texto lo pone la banda ambar`() {
        val aviso = evaluar(dinero("200"))

        assertEquals(NivelDeAviso.NOTA, aviso.nivel)
        assertEquals(listOf(SenalDelMonto.ABAJO_DE_LO_ESPERADO), aviso.senales)
        assertEquals(emptyList<String>(), aviso.mensajes)
    }

    @Test
    fun `pagar exactamente lo esperado no dice nada`() {
        assertEquals(NivelDeAviso.NINGUNO, evaluar(esperadoHoy).nivel)
    }

    // ── El múltiplo de 50, y el de al lado ────────────────────────────────

    @Test
    fun `un multiplo de 50 por arriba de lo esperado no dice nada`() {
        assertEquals(NivelDeAviso.NINGUNO, evaluar(dinero("300")).nivel)
    }

    @Test
    fun `un peso al lado del multiplo de 50 pide confirmar`() {
        val aviso = evaluar(dinero("301"))

        assertEquals(NivelDeAviso.CONFIRMAR, aviso.nivel)
        assertTrue(SenalDelMonto.NO_ES_MULTIPLO_DE_CINCUENTA in aviso.senales)
        assertEquals(listOf("Los pagos van de 50 en 50"), aviso.mensajes)
    }

    // ── La app no interroga lo que ella misma propuso ─────────────────────

    @Test
    fun `liquidar la venta no enciende nada, aunque el saldo no sea redondo`() {
        // Un saldo termina donde termina, y son 5.1 cuotas. Las dos señales se
        // callan: pagar el saldo completo es liquidar, o sea la operación que la
        // pantalla describe.
        val saldoRaro = dinero("1287.33")
        val aviso = AvisosDelAbono.evaluar(
            monto = saldoRaro,
            saldo = saldoRaro,
            parcialidad = cuota,
            esperadoHoy = esperadoHoy,
            historial = emptyList()
        )

        assertEquals(NivelDeAviso.NINGUNO, aviso.nivel)
        assertEquals(emptyList<SenalDelMonto>(), aviso.senales)
        assertTrue(aviso.loPropusoLaPantalla)
    }

    @Test
    fun `un chip exacto no enciende nada, ni siquiera siendo 20 cuotas`() {
        // $5,000 sobre una cuota de $250 son 20 cuotas: sin la regla sería el
        // nivel 3 más grave que hay. Con la regla, es el chip de "al corriente"
        // que la pantalla acaba de ofrecer, y tocarlo no puede abrir un
        // interrogatorio.
        val aviso = AvisosDelAbono.evaluar(
            monto = dinero("5000"),
            saldo = saldo,
            parcialidad = cuota,
            esperadoHoy = esperadoHoy,
            historial = emptyList(),
            sugeridos = listOf(esperadoHoy, dinero("5000"))
        )

        assertEquals(NivelDeAviso.NINGUNO, aviso.nivel)
        assertTrue(aviso.loPropusoLaPantalla)
    }

    @Test
    fun `control positivo - el MISMO monto sin estar en la fila si enciende el nivel 3`() {
        // Sin esto, una regla que exentara SIEMPRE dejaría el test de arriba en
        // verde sin medir nada.
        val aviso = evaluar(dinero("5000"))

        assertEquals(NivelDeAviso.TECLEAR, aviso.nivel)
        assertFalse(aviso.loPropusoLaPantalla)
    }

    @Test
    fun `pagar justo lo esperado no enciende nada, aunque no sea redondo`() {
        // Lo esperado hoy es SIEMPRE el primer chip de la fila cuando existe, así
        // que entra por la regla general y no necesita exención propia.
        val aviso = AvisosDelAbono.evaluar(
            monto = dinero("220"),
            saldo = saldo,
            parcialidad = cuota,
            esperadoHoy = dinero("220"),
            historial = emptyList(),
            sugeridos = listOf(dinero("220"))
        )

        assertEquals(NivelDeAviso.NINGUNO, aviso.nivel)
    }

    @Test
    fun `un chip que esta por debajo de lo esperado tampoco se reclama`() {
        // El redondo de $100 sobre una cuenta que espera $250. Es corto, pero es
        // lo que la fila está ofreciendo.
        val aviso = AvisosDelAbono.evaluar(
            monto = dinero("100"),
            saldo = saldo,
            parcialidad = cuota,
            esperadoHoy = esperadoHoy,
            historial = emptyList(),
            sugeridos = listOf(esperadoHoy, dinero("100"))
        )

        assertEquals(NivelDeAviso.NINGUNO, aviso.nivel)
        assertEquals(emptyList<SenalDelMonto>(), aviso.senales)
    }

    @Test
    fun `el sobrepago se bloquea aunque venga de la fila`() {
        // El nivel 0 no es un aviso, es una imposibilidad. (En producción no
        // puede pasar —`MontosSugeridos` topa los chips en el saldo— y por eso
        // mismo se mide: la guarda no depende de esa cortesía.)
        val aviso = AvisosDelAbono.evaluar(
            monto = dinero("999999"),
            saldo = saldo,
            parcialidad = cuota,
            esperadoHoy = esperadoHoy,
            historial = emptyList(),
            sugeridos = listOf(dinero("999999"))
        )

        assertEquals(NivelDeAviso.BLOQUEO, aviso.nivel)
    }

    // ── Las fronteras de cuotas: 3, 6 y 12 ────────────────────────────────

    @Test
    fun `dos cuotas y pico todavia no piden nada`() {
        // $700 = 2.8 cuotas. La frontera de las 3 cuotas, por abajo.
        assertEquals(NivelDeAviso.NINGUNO, evaluar(dinero("700")).nivel)
    }

    @Test
    fun `tres cuotas exactas piden confirmar con un toque`() {
        val aviso = evaluar(dinero("750"))

        assertEquals(NivelDeAviso.CONFIRMAR, aviso.nivel)
        assertTrue(SenalDelMonto.DE_TRES_A_SEIS_CUOTAS in aviso.senales)
        assertEquals(listOf("Son 3 cuotas de \$250"), aviso.mensajes)
    }

    @Test
    fun `seis cuotas exactas siguen siendo nivel confirmar`() {
        // La frontera de las 6: el rango de nivel 2 la INCLUYE, y "más de 6" es
        // lo que escala. Un peso decide entre un toque y teclear el monto.
        val aviso = evaluar(dinero("1500"))

        assertEquals(NivelDeAviso.CONFIRMAR, aviso.nivel)
        assertTrue(SenalDelMonto.DE_TRES_A_SEIS_CUOTAS in aviso.senales)
        assertTrue(SenalDelMonto.MAS_DE_SEIS_CUOTAS !in aviso.senales)
        assertEquals(listOf("Son 6 cuotas de \$250"), aviso.mensajes)
    }

    @Test
    fun `pasadas las seis cuotas hay que teclear el monto`() {
        val aviso = evaluar(dinero("1550"))

        assertEquals(NivelDeAviso.TECLEAR, aviso.nivel)
        assertTrue(SenalDelMonto.MAS_DE_SEIS_CUOTAS in aviso.senales)
        assertEquals(listOf("Son 6 cuotas de \$250"), aviso.mensajes)
    }

    @Test
    fun `doce cuotas exactas son nivel teclear, pero todavia no son inéditas`() {
        // La frontera de las 12: hasta aquí llega "de 6 a 12", que sí ha
        // ocurrido (5 abonos de 6,165). Pasarla es lo que nunca pasó.
        val aviso = evaluar(dinero("3000"))

        assertEquals(NivelDeAviso.TECLEAR, aviso.nivel)
        assertTrue(SenalDelMonto.MAS_DE_DOCE_CUOTAS !in aviso.senales)
        assertEquals(listOf("Son 12 cuotas de \$250"), aviso.mensajes)
    }

    @Test
    fun `pasadas las doce cuotas nadie en la ruta ha pagado tanto`() {
        val aviso = evaluar(dinero("3050"))

        assertEquals(NivelDeAviso.TECLEAR, aviso.nivel)
        assertTrue(SenalDelMonto.MAS_DE_DOCE_CUOTAS in aviso.senales)
        // Los dos mensajes del nivel, y los dos dicen algo distinto: cuántas
        // cuotas son, y que eso no tiene precedente en la ruta.
        assertEquals(
            listOf("Son 12 cuotas de \$250", "Nadie en la ruta ha pagado tanto"),
            aviso.mensajes
        )
    }

    @Test
    fun `sin cuota no se cuentan cuotas`() {
        // Una venta sin parcialidad no tiene contra qué contar. Contar contra
        // cero inventaría un múltiplo infinito y pondría TODO en nivel 3.
        val aviso = AvisosDelAbono.evaluar(
            monto = dinero("5000"),
            saldo = saldo,
            parcialidad = Money.ZERO,
            esperadoHoy = Money.ZERO,
            historial = emptyList()
        )

        assertEquals(NivelDeAviso.NINGUNO, aviso.nivel)
    }

    // ── Las fronteras de la costumbre: el triple y las diez veces ─────────

    @Test
    fun `el triple exacto de lo que suele dar todavia no avisa`() {
        assertEquals(NivelDeAviso.NINGUNO, porCostumbre(dinero("600")).nivel)
    }

    @Test
    fun `mas del triple de lo que suele dar pide confirmar`() {
        val aviso = porCostumbre(dinero("650"))

        assertEquals(NivelDeAviso.CONFIRMAR, aviso.nivel)
        assertTrue(SenalDelMonto.MAS_DEL_TRIPLE_DE_LO_HABITUAL in aviso.senales)
        assertEquals(listOf("Suele dar \$200"), aviso.mensajes)
    }

    @Test
    fun `diez veces exactas lo que suele dar ya pide teclear`() {
        // "Diez veces O MÁS": la frontera es cerrada, al revés que la del triple.
        val aviso = porCostumbre(dinero("2000"))

        assertEquals(NivelDeAviso.TECLEAR, aviso.nivel)
        assertEquals(listOf("Suele dar \$200, esto es 10 veces más"), aviso.mensajes)
    }

    @Test
    fun `sin costumbre medida no se acusa a nadie de salirse de ella`() {
        // Dos abonos no hacen una costumbre (ver `AbonoHabitualTest`), así que
        // $2,000 no puede ser "diez veces lo que suele dar": no se sabe qué suele dar.
        val aviso = AvisosDelAbono.evaluar(
            monto = dinero("2000"),
            saldo = saldo,
            parcialidad = Money.ZERO,
            esperadoHoy = Money.ZERO,
            historial = listOf(abono("200", dia = 1), abono("200", dia = 2))
        )

        assertEquals(NivelDeAviso.NINGUNO, aviso.nivel)
    }

    // ── El nivel ganador ──────────────────────────────────────────────────

    @Test
    fun `gana el nivel mas grave y solo se dicen sus mensajes`() {
        // $3,051: no es múltiplo de 50 (nivel 2) Y son más de 12 cuotas (nivel
        // 3). Las dos señales quedan registradas, pero sólo habla la grave.
        val aviso = evaluar(dinero("3051"))

        assertEquals(NivelDeAviso.TECLEAR, aviso.nivel)
        assertTrue(SenalDelMonto.NO_ES_MULTIPLO_DE_CINCUENTA in aviso.senales)
        assertTrue(SenalDelMonto.MAS_DE_DOCE_CUOTAS in aviso.senales)
        assertTrue("Los pagos van de 50 en 50" !in aviso.mensajes)
    }

    @Test
    fun `el orden de NivelDeAviso es el orden de gravedad`() {
        // `evaluar` escoge el nivel ganador por el ORDINAL. Reordenar este enum
        // cambia el comportamiento, así que el orden se mide, no se supone.
        assertEquals(
            listOf(
                NivelDeAviso.NINGUNO,
                NivelDeAviso.BLOQUEO,
                NivelDeAviso.NOTA,
                NivelDeAviso.CONFIRMAR,
                NivelDeAviso.TECLEAR
            ),
            NivelDeAviso.entries.toList()
        )
    }

    @Test
    fun `cada senal declara el nivel que le toca`() {
        // El mapa completo, a mano: es la tabla que el dueño aprobó, y una
        // señal que cambie de nivel tiene que pasar por aquí.
        assertEquals(
            mapOf(
                SenalDelMonto.NO_SE_PUEDE_REGISTRAR to NivelDeAviso.BLOQUEO,
                SenalDelMonto.ABAJO_DE_LO_ESPERADO to NivelDeAviso.NOTA,
                SenalDelMonto.NO_ES_MULTIPLO_DE_CINCUENTA to NivelDeAviso.CONFIRMAR,
                SenalDelMonto.DE_TRES_A_SEIS_CUOTAS to NivelDeAviso.CONFIRMAR,
                SenalDelMonto.MAS_DEL_TRIPLE_DE_LO_HABITUAL to NivelDeAviso.CONFIRMAR,
                SenalDelMonto.MAS_DE_SEIS_CUOTAS to NivelDeAviso.TECLEAR,
                SenalDelMonto.DIEZ_VECES_LO_HABITUAL to NivelDeAviso.TECLEAR,
                SenalDelMonto.MAS_DE_DOCE_CUOTAS to NivelDeAviso.TECLEAR
            ),
            SenalDelMonto.entries.associateWith { it.nivel }
        )
    }

    @Test
    fun `ningun nivel impide registrar`() {
        // Avisar no es bloquear. El único que impide es el bloqueo duro, y ése
        // no es de este archivo.
        listOf("301", "750", "1550", "3050").forEach { pesos ->
            val monto = dinero(pesos)
            assertTrue(
                "\$$pesos tendría que seguir siendo registrable",
                SeguridadDelAbono.bloqueosDe(monto, saldo).isEmpty()
            )
        }
    }

    private fun evaluar(monto: Money): AvisoDelMonto = AvisosDelAbono.evaluar(
        monto = monto,
        saldo = saldo,
        parcialidad = cuota,
        esperadoHoy = esperadoHoy,
        historial = emptyList()
    )

    /** La misma cuenta pero **sin cuota**, para aislar los umbrales de costumbre. */
    private fun porCostumbre(monto: Money): AvisoDelMonto = AvisosDelAbono.evaluar(
        monto = monto,
        saldo = saldo,
        parcialidad = Money.ZERO,
        esperadoHoy = Money.ZERO,
        historial = listOf(abono("200", dia = 1), abono("200", dia = 2), abono("200", dia = 3))
    )

    private fun abono(pesos: String, dia: Int): AbonoPrevio = AbonoPrevio(
        fecha = Instant.parse("2026-08-%02dT12:00:00Z".format(dia)),
        importe = dinero(pesos)
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
