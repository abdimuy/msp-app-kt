package com.example.msp_app.core.common.time

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La escalera de [TiempoRelativo], escalón por escalón y borde por borde.
 *
 * El grueso vive en [casosDeLaEscalera]: una tabla en la que cada fila trae la
 * razón por la que existe, y esa razón es también el mensaje que se imprime si
 * la fila se pone roja. Un `assertEquals` pelado diría *"expected hace 1 semana
 * but was hace 7 días"* y dejaría al que lo lee averiguando cuál de los seis
 * escalones se movió.
 *
 * El ancla es siempre el **viernes 2026-09-18**. Que sea una fecha fija y no
 * `hoy` es el punto entero de la firma: la función no lee ningún reloj, así que
 * esta suite da lo mismo el día que se corra y no se pudre sola dentro de un
 * mes.
 */
class TiempoRelativoTest {

    private lateinit var zonaOriginal: TimeZone

    @Before
    fun guardarZona() {
        zonaOriginal = TimeZone.getDefault()
    }

    @After
    fun restaurarZona() {
        TimeZone.setDefault(zonaOriginal)
    }

    /**
     * Una fila de la tabla. [porQue] no es adorno: es lo que se imprime cuando
     * la fila falla, y es lo que impide que alguien borre un borde creyendo que
     * era un caso repetido.
     */
    private data class Caso(
        val fecha: String,
        val hoy: String,
        val esperado: String,
        val porQue: String
    )

    // region — La tabla

    /**
     * Los seis escalones y los bordes exactos entre ellos.
     *
     * Cada borde va en PAR —el último día de un escalón y el primer día del
     * siguiente— porque un corte mal puesto por uno sólo se ve si se mira de
     * los dos lados. El borde semanas→meses no cae en un número fijo de días
     * (depende del calendario: son 31 días desde el 18 de agosto y serían 28
     * desde el 18 de febrero), así que el par está escrito con fechas reales y
     * no con una resta.
     */
    private val casosDeLaEscalera: List<Caso> = listOf(
        Caso(
            fecha = "2026-09-18",
            hoy = "2026-09-18",
            esperado = "hoy",
            porQue = "el mismo día de negocio se dice por su nombre, nunca 'hace 0 días'"
        ),
        Caso(
            fecha = "2026-09-17",
            hoy = "2026-09-18",
            esperado = "ayer",
            porQue = "un día atrás se dice por su nombre, nunca 'hace 1 día'"
        ),
        Caso(
            fecha = "2026-09-16",
            hoy = "2026-09-18",
            esperado = "hace 2 días",
            porQue = "primer día del escalón de días; también es el caso que 'anteayer' NO ocupa"
        ),
        Caso(
            fecha = "2026-09-12",
            hoy = "2026-09-18",
            esperado = "hace 6 días",
            porQue = "BORDE: último día que se cuenta en días"
        ),
        Caso(
            fecha = "2026-09-11",
            hoy = "2026-09-18",
            esperado = "hace 1 semana",
            porQue = "BORDE: el día 7 ya es semana, y en SINGULAR"
        ),
        Caso(
            fecha = "2026-09-05",
            hoy = "2026-09-18",
            esperado = "hace 1 semana",
            porQue = "BORDE: 13 días siguen siendo una semana; el escalón no se parte a la mitad"
        ),
        Caso(
            fecha = "2026-09-04",
            hoy = "2026-09-18",
            esperado = "hace 2 semanas",
            porQue = "BORDE singular/plural: 14 días estrenan el plural de semana"
        ),
        Caso(
            fecha = "2026-08-28",
            hoy = "2026-09-18",
            esperado = "hace 3 semanas",
            porQue = "21 días; el escalón de semanas sí cuenta de a una"
        ),
        Caso(
            fecha = "2026-08-19",
            hoy = "2026-09-18",
            esperado = "hace 4 semanas",
            porQue = "BORDE: 30 días es la semana más vieja alcanzable — 'hace 5 semanas' no existe"
        ),
        Caso(
            fecha = "2026-08-18",
            hoy = "2026-09-18",
            esperado = "hace 1 mes",
            porQue = "BORDE semanas→meses: cerró un mes de CALENDARIO (mismo día del mes), en SINGULAR"
        ),
        Caso(
            fecha = "2026-07-19",
            hoy = "2026-09-18",
            esperado = "hace 1 mes",
            porQue = "BORDE: 61 días y el segundo mes no cerró todavía; sigue siendo uno"
        ),
        Caso(
            fecha = "2026-07-18",
            hoy = "2026-09-18",
            esperado = "hace 2 meses",
            porQue = "BORDE singular/plural: 'mes'→'meses' cambia la raíz, no agrega una letra"
        ),
        Caso(
            fecha = "2025-10-18",
            hoy = "2026-09-18",
            esperado = "hace 11 meses",
            porQue = "once meses siguen siendo meses: el escalón de años no se adelanta"
        ),
        Caso(
            fecha = "2025-09-19",
            hoy = "2026-09-18",
            esperado = "hace 11 meses",
            porQue = "BORDE: 364 días, un día antes del aniversario, todavía meses"
        ),
        Caso(
            fecha = "2025-09-18",
            hoy = "2026-09-18",
            esperado = "hace 1 año",
            porQue = "BORDE meses→años: el aniversario exacto, en SINGULAR"
        ),
        Caso(
            fecha = "2024-09-19",
            hoy = "2026-09-18",
            esperado = "hace 1 año",
            porQue = "BORDE: un día antes del segundo aniversario sigue siendo un año"
        ),
        Caso(
            fecha = "2024-09-18",
            hoy = "2026-09-18",
            esperado = "hace 2 años",
            porQue = "BORDE singular/plural: el segundo aniversario estrena el plural de año"
        ),
        Caso(
            fecha = "2020-01-01",
            hoy = "2026-09-18",
            esperado = "hace 6 años",
            porQue = "una nota vieja de verdad no degenera en un número enorme de días"
        ),
        Caso(
            fecha = "2026-02-28",
            hoy = "2026-03-28",
            esperado = "hace 1 mes",
            porQue = "el mes de calendario es el mismo día del mes siguiente, no un bloque de 30 días"
        ),
        Caso(
            fecha = "2024-02-29",
            hoy = "2025-03-01",
            esperado = "hace 1 año",
            porQue = "un 29 de febrero no corre el aniversario ni inventa un escalón"
        ),
        Caso(
            fecha = "2026-09-19",
            hoy = "2026-09-18",
            esperado = "hoy",
            porQue = "una fecha futura colapsa a 'hoy'; jamás sale un 'hace -1 días' a la pantalla"
        ),
        Caso(
            fecha = "2027-01-01",
            hoy = "2026-09-18",
            esperado = "hoy",
            porQue = "un reloj de teléfono adelantado meses tampoco produce 'en 3 meses'"
        )
    )

    @Test
    fun `la escalera completa, escalon por escalon y borde por borde`() {
        casosDeLaEscalera.forEach { caso ->
            assertEquals(
                "${caso.porQue} — fecha=${caso.fecha} hoy=${caso.hoy}",
                caso.esperado,
                TiempoRelativo.de(LocalDate.parse(caso.fecha), LocalDate.parse(caso.hoy))
            )
        }
    }

    // endregion

    // region — El control positivo

    /**
     * **El control positivo de toda esta suite.**
     *
     * Una tabla grande da una sensación de cobertura que una implementación
     * tonta puede regalar: `fun de(...) = "hoy"` dejaría verdes los dos casos de
     * `hoy` y los dos de futuro, o sea cuatro filas, sin calcular nada. Peor
     * todavía, si alguien rompiera la función entera y devolviera siempre la
     * misma cadena, hace falta que algo se ponga ROJO.
     *
     * Esto es ese algo: dos entradas que DEBEN producir salidas distintas, y la
     * afirmación explícita de que lo son. Si `de` devolviera una constante —la
     * que fuera— este test falla antes que cualquier otro.
     *
     * La segunda mitad sube la apuesta: la tabla no puede colapsar a unos pocos
     * textos. Sus 22 filas tienen que producir al menos los diez textos
     * distintos que la escalera promete, así que una implementación que
     * acertara un escalón y aplanara el resto tampoco pasa.
     */
    @Test
    fun `control positivo - dos entradas distintas NO pueden dar el mismo texto`() {
        val hoy = LocalDate.of(2026, 9, 18)

        val deHoy = TiempoRelativo.de(hoy, hoy)
        val deAyer = TiempoRelativo.de(hoy.minusDays(1), hoy)

        // El `assertNotEquals` va PRIMERO a propósito: si fuera después de los
        // dos `assertEquals`, un mutante que devolviera siempre "hoy" moriría
        // en el segundo `assertEquals` y el mensaje diría "expected ayer but
        // was hoy" — cierto, pero no diría que el problema es que la función
        // dejó de distinguir. Puesto primero, CUALQUIER constante —"hoy",
        // "ayer" o "" — muere acá y con la razón escrita.
        assertNotEquals(
            "si `de` devolviera siempre la misma cadena, este assert es el que tiene que " +
                "ponerse rojo: el mismo día y el día anterior no pueden leerse igual",
            deHoy,
            deAyer
        )
        assertEquals("hoy", deHoy)
        assertEquals("ayer", deAyer)

        val distintos = casosDeLaEscalera
            .map { TiempoRelativo.de(LocalDate.parse(it.fecha), LocalDate.parse(it.hoy)) }
            .toSet()
        assertTrue(
            "la tabla colapsó a ${distintos.size} textos distintos ($distintos): una " +
                "implementación que aplana la escalera estaría pasando la tabla entera",
            distintos.size >= ESCALONES_DISTINTOS_MINIMOS
        )
    }

    // endregion

    // region — El día de negocio, no el reloj

    /**
     * El caso que motivó comparar por día y no por duración: ocho horas de
     * distancia que se leen *"ayer"*, porque cruzaron la medianoche.
     *
     * Un formateador por duración diría aquí *"hace 8 horas"*, y eso es lo que
     * este test prohíbe.
     */
    @Test
    fun `las 23 horas de ayer vistas a las 7 de hoy son ayer, no ocho horas`() {
        // 2026-09-18T05:00Z = 23:00 del 17 en CDMX (UTC-6).
        val nota = Instant.parse("2026-09-18T05:00:00Z")
        // 2026-09-18T13:00Z = 07:00 del 18 en CDMX. Ocho horas después.
        val clock = FakeClock.at("2026-09-18T13:00:00Z")

        assertEquals("ayer", TiempoRelativo.de(nota, clock))
    }

    /**
     * El espejo del test de arriba, y sin él la regla estaría probada a medias:
     * veintidós horas de distancia que se leen *"hoy"* porque NO cruzaron la
     * medianoche. Un corte por duración (24 h) daría lo mismo acá — por eso hace
     * falta el par: sólo juntos distinguen "por día" de "por horas".
     */
    @Test
    fun `veintidos horas dentro del mismo dia de negocio siguen siendo hoy`() {
        // 2026-09-18T06:30Z = 00:30 del 18 en CDMX.
        val nota = Instant.parse("2026-09-18T06:30:00Z")
        // 2026-09-19T04:59Z = 22:59 del MISMO 18 en CDMX.
        val clock = FakeClock.at("2026-09-19T04:59:00Z")

        assertEquals("hoy", TiempoRelativo.de(nota, clock))
    }

    /**
     * La medianoche que manda es la del negocio, no la del teléfono. Un equipo
     * en otro huso —o con la zona mal puesta— no puede mover el corte, que es
     * exactamente la clase de defecto que `checkNoLegacyDateApi` persigue en el
     * resto del repo.
     */
    @Test
    fun `el corte del dia no depende de la zona del dispositivo`() {
        val nota = Instant.parse("2026-09-18T05:00:00Z")
        val clock = FakeClock.at("2026-09-18T13:00:00Z")

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        // En UTC ambos instantes caen el 18: un formateador ingenuo diría "hoy".
        assertEquals("ayer", TiempoRelativo.de(nota, clock))

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"))
        assertEquals("ayer", TiempoRelativo.de(nota, clock))
    }

    /**
     * La sobrecarga con reloj por omisión existe y resuelve contra
     * [AppClock.System]. Es el único camino del archivo que toca el reloj real,
     * así que se prueba con la única afirmación que no depende del segundo en
     * que corra: el ahora, leído contra el ahora, es `hoy`.
     */
    @Test
    fun `sin reloj explicito usa AppClock System`() {
        assertEquals("hoy", TiempoRelativo.de(AppClock.System.now()))
    }

    // endregion

    // region — Invariantes de la escalera

    /**
     * Barrido de dos años contra un ancla fija: ninguna distancia puede producir
     * un texto mal formado. Cubre los huecos que una tabla escrita a mano no ve
     * —los días que nadie eligió— y fija de paso las tres cosas que el KDoc
     * promete: nunca un `hace 0 X`, nunca un plural equivocado, y nunca más de
     * cuatro semanas.
     */
    @Test
    fun `ningun dia produce un cero, un plural roto ni una quinta semana`() {
        val hoy = LocalDate.of(2026, 9, 18)
        val singulares = setOf("día", "semana", "mes", "año")

        (0L..DIAS_DEL_BARRIDO).forEach { atras ->
            val texto = TiempoRelativo.de(hoy.minusDays(atras), hoy)
            if (texto == TiempoRelativo.HOY || texto == TiempoRelativo.AYER) return@forEach

            val partes = texto.split(" ")
            assertEquals("texto mal formado a $atras días: \"$texto\"", 3, partes.size)
            assertEquals("texto mal formado a $atras días: \"$texto\"", "hace", partes[0])

            val cantidad = partes[1].toLong()
            val unidad = partes[2]
            assertTrue("\"$texto\" ($atras días atrás) cuenta cero", cantidad > 0)
            assertEquals(
                "\"$texto\" ($atras días atrás) usa el número y el plural cruzados",
                cantidad == 1L,
                unidad in singulares
            )
            assertTrue(
                "\"$texto\" pasa de cuatro semanas: a esa distancia ya debería hablar de meses",
                unidad !in setOf("semana", "semanas") || cantidad <= SEMANAS_MAXIMAS
            )
        }
    }

    /**
     * El fragmento sale en minúscula porque el llamador lo incrusta en una
     * frase (*"Nota de hace 3 días"*). Fijarlo acá es lo que evita que alguien
     * lo "corrija" a mayúscula inicial creyendo que aplica la norma del repo, y
     * rompa la frase de todos los call sites de una.
     *
     * **La norma de texto de usuario no la define ni este test ni el KDoc** —la
     * define el brief (principio 10) y `CLAUDE.md`—; lo que se fija acá es que
     * esta función devuelve un fragmento y no un texto terminado.
     */
    @Test
    fun `el fragmento sale en minuscula porque se incrusta en otra frase`() {
        casosDeLaEscalera.forEach { caso ->
            val texto = TiempoRelativo.de(LocalDate.parse(caso.fecha), LocalDate.parse(caso.hoy))
            assertEquals(
                "\"$texto\" no es un texto de usuario terminado: es un fragmento",
                texto.lowercase(BUSINESS_LOCALE),
                texto
            )
        }
    }

    // endregion

    private companion object {
        /**
         * `hoy`, `ayer`, y los ocho textos distintos que la tabla produce de
         * `hace 2 días` en adelante. No es el total de la tabla (varias filas
         * comparten texto a propósito, que es como se prueban los bordes): es
         * el piso por debajo del cual la escalera se aplanó.
         */
        const val ESCALONES_DISTINTOS_MINIMOS = 10

        /** Dos años y pico: suficiente para entrar al escalón de años. */
        const val DIAS_DEL_BARRIDO = 800L

        /** El techo del escalón de semanas, ver `TiempoRelativo.de`. */
        const val SEMANAS_MAXIMAS = 4L
    }
}
