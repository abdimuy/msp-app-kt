package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué señales sugiere el vocabulario de una nota — y, sobre todo, **cuáles
 * no**.
 *
 * La tabla es el test: cada fila es una nota que un cobrador escribiría de
 * verdad y el conjunto EXACTO que debe salir. Exacto y no "contiene", porque la
 * mitad de los defectos de esta pieza son sugerencias de más —*"vuelvo mañana"*
 * ofreciendo `ESTA_EN_LA_MANANA`— y un aserto de contención los deja pasar.
 *
 * ## El control positivo
 *
 * Media tabla espera `emptySet()`. Una implementación que devolviera siempre
 * `emptySet()` pasaría todas esas filas sin hacer nada — la ausencia no es un
 * hallazgo hasta probar que el método habría encontrado la cosa. Por eso
 * `el control positivo` afirma que la MISMA tabla produce las **seis** señales
 * del catálogo. Con esa fila en verde, cada `emptySet()` significa algo.
 *
 * ## Las filas incómodas
 *
 * Las de `no lo resuelve` afirman el comportamiento **real**, no el deseado:
 * *"el perro ya no está"* sí sugiere `HAY_PERRO`. Están escritas al derecho a
 * propósito: el día que alguien mejore la negación, estas filas se ponen rojas
 * y es una conversación, no un test que ya no dice nada.
 */
class SugerenciasDeLaNotaTest {

    private data class Caso(val nota: String, val espera: Set<SenalDeFicha>)

    private fun caso(nota: String, vararg espera: SenalDeFicha) = Caso(nota, espera.toSet())

    private val tabla = listOf(
        // --- Los tres que el dueño de la ficha nombró ---------------------
        caso("hay perro", SenalDeFicha.HAY_PERRO),
        caso("no ir solo", SenalDeFicha.NO_IR_SOLO),
        caso("atiende la suegra", SenalDeFicha.ATIENDE_OTRA_PERSONA),

        // --- Una por cada una de las seis: faltaban las tres de horario ---
        caso("lo encuentro en la mañana", SenalDeFicha.ESTA_EN_LA_MANANA),
        caso("por las tardes está", SenalDeFicha.ESTA_EN_LA_TARDE),
        caso("trabaja de noche", SenalDeFicha.ESTA_EN_LA_NOCHE),

        // --- Acentos y mayúsculas: se escribe parado en una puerta --------
        caso("viene en la Mañana", SenalDeFicha.ESTA_EN_LA_MANANA),
        caso("EN LA MANANA", SenalDeFicha.ESTA_EN_LA_MANANA),
        caso("en la manana", SenalDeFicha.ESTA_EN_LA_MANANA),
        caso("CUIDADO, EL PERRO MUERDE", SenalDeFicha.HAY_PERRO),

        // --- Palabra completa, nunca subcadena ----------------------------
        // "perrón" es un elogio y contiene "perro"; "anoche" contiene "noche".
        // Con `contains` las dos dispararían.
        caso("el trabajo quedó perrón"),
        caso("anoche no estaba"),
        // Sin coincidencia difusa a propósito: "pero" es de las palabras más
        // comunes del idioma y está a una letra de "perro".
        caso("no contesta pero deja recado"),
        caso("hay pero"),

        // --- Los falsos amigos que dejaron su palabra fuera del vocabulario
        // "mañana" es un día, no una hora.
        caso("vuelvo mañana"),
        caso("mañana paso otra vez"),
        // "tarde" en cobranza es retraso, no hora del día.
        caso("siempre paga tarde"),

        // --- Las negaciones que SÍ resuelve -------------------------------
        caso("no hay perro"),
        caso("sin perro"),
        caso("no es peligroso"),
        caso("no lo encuentro en la noche"),
        // El negador que forma parte del término no niega nada: la señal ya es
        // una prohibición y su vocabulario se escribe negado.
        caso("nunca ir solo", SenalDeFicha.NO_IR_SOLO),
        caso("no es el titular", SenalDeFicha.ATIENDE_OTRA_PERSONA),
        // La cláusula acota el contagio: el perro de la segunda idea sobrevive
        // al "no" de la primera.
        caso("no ir solo, hay perro", SenalDeFicha.NO_IR_SOLO, SenalDeFicha.HAY_PERRO),
        // Basta una aparición limpia: el que se corrige a sí mismo no pierde.
        caso("no hay perro / bueno sí hay perro", SenalDeFicha.HAY_PERRO),

        // --- Las que NO resuelve, afirmadas como se comportan hoy ---------
        // 1. El negador que llega DESPUÉS: la ventana sólo mira hacia atrás.
        caso("el perro ya no está", SenalDeFicha.HAY_PERRO),
        // 2. Sin negador no hay negación: aquí no se analiza el significado.
        caso("se lo llevaron el perro", SenalDeFicha.HAY_PERRO),
        // 3. La coma dejó al "no" solo en su cláusula y ya no alcanza.
        caso("no, hay perro", SenalDeFicha.HAY_PERRO),
        // 4. El otro lado, el barato: cláusula corrida sin puntuación, el "no"
        //    queda a cuatro palabras y apaga una sugerencia buena.
        caso("no tiene timbre hay perro"),
        caso("no ir solo hay perro", SenalDeFicha.NO_IR_SOLO),

        // --- Vacío, blanco y prosa sin vocabulario del catálogo ------------
        caso(""),
        caso("   "),
        caso("\n\n\t "),
        // Referencias de la casa: el catálogo las excluyó por diseño, así que
        // no tienen señal destino. Esta prosa DEBE quedarse en la nota.
        caso("casa azul, portón negro, junto a la tienda"),

        // --- Varias señales de una sola nota -------------------------------
        caso(
            "trabaja de noche\natiende la suegra",
            SenalDeFicha.ESTA_EN_LA_NOCHE,
            SenalDeFicha.ATIENDE_OTRA_PERSONA
        ),
        caso(
            "atiende su hija en la tarde, hay perro",
            SenalDeFicha.ATIENDE_OTRA_PERSONA,
            SenalDeFicha.ESTA_EN_LA_TARDE,
            SenalDeFicha.HAY_PERRO
        ),
        // El guion no parte la idea: "pit-bull" sigue siendo un término.
        caso("hay pit-bull y no ir solo", SenalDeFicha.HAY_PERRO, SenalDeFicha.NO_IR_SOLO)
    )

    @Test
    fun `la tabla - cada nota sugiere exactamente estas senales`() {
        tabla.forEach { fila ->
            assertEquals(
                "nota: ${fila.nota}",
                fila.espera,
                SugerenciasDeLaNota.para(fila.nota)
            )
        }
    }

    @Test
    fun `el control positivo - la misma tabla produce las SEIS senales`() {
        val producidas = tabla.flatMap { SugerenciasDeLaNota.para(it.nota) }.toSet()
        assertTrue(
            "la tabla no produjo NINGUNA senal: los emptySet() de arriba no prueban nada",
            producidas.isNotEmpty()
        )
        assertEquals(
            "hay senales del catalogo que ninguna fila alcanza",
            SenalDeFicha.entries.toSet(),
            producidas
        )
    }

    @Test
    fun `una nota nula no sugiere nada`() {
        assertEquals(emptySet<SenalDeFicha>(), SugerenciasDeLaNota.para(null))
    }

    @Test
    fun `lo que ya esta marcado no se vuelve a ofrecer`() {
        assertEquals(
            emptySet<SenalDeFicha>(),
            SugerenciasDeLaNota.para("hay perro", yaMarcadas = setOf(SenalDeFicha.HAY_PERRO))
        )
    }

    @Test
    fun `filtrar una senal no se lleva a las demas`() {
        assertEquals(
            setOf(SenalDeFicha.HAY_PERRO),
            SugerenciasDeLaNota.para(
                "hay perro, no ir solo",
                yaMarcadas = setOf(SenalDeFicha.NO_IR_SOLO)
            )
        )
    }

    @Test
    fun `marcar algo que la nota no menciona no cambia el resto`() {
        // yaMarcadas resta, no agrega: una senal puesta a mano que el texto no
        // nombra deja las sugerencias del texto intactas.
        assertEquals(
            setOf(SenalDeFicha.HAY_PERRO),
            SugerenciasDeLaNota.para(
                "hay perro",
                yaMarcadas = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE)
            )
        )
    }
}
