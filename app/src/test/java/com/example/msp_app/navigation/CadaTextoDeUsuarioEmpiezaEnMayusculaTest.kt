package com.example.msp_app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El texto de usuario empieza en mayúscula, y ahora alguien lo mide.**
 *
 * ## El defecto que esto mata
 *
 * El dueño revisó un release en su SM-A256E y encontró los botones en
 * minúscula: *"registrar abono"*, *"visita"*, *"continuar"*, *"otra hora"*.
 * Preguntó si ya se había arreglado. No: **nunca se tocó**.
 *
 * Pudo pasar porque la regla —principio 10 del brief— vivía sólo en
 * `.superpowers/`, que `.gitignore:32` no guarda. Dentro del repo la única
 * versión escrita de la regla era **la invertida**: el KDoc de
 * `EtiquetasDeFicha` afirmaba *"Minúsculas y sin punto final, como todo el
 * texto de usuario de este plan"*, y `CatalogoDeLaFichaTest` lo fijaba con un
 * test que cobraba la minúscula. Una regla que sólo vive en prosa deriva; una
 * que vive en prosa invertida y con test se vuelve la regla de verdad.
 *
 * Por eso esta compuerta: la regla queda escrita en `CLAUDE.md` y en
 * `docs/superpowers/plans/2026-09-17-principios-cobranza-2026.md`, y **medida**
 * aquí.
 *
 * ## Qué se mide, y qué NO
 *
 * Se mide **sólo la mayúscula inicial**. NO se mide "2-4 palabras", aunque el
 * principio también lo pida: pondría rojos textos que sí queremos — el mensaje
 * de `HojaDeConfirmacion` tiene trece palabras y está bien escrito. Un
 * criterio que hay que aflojar el primer día no es un criterio.
 *
 * ## El alcance es una decisión del dueño, no una derivación
 *
 * [ALCANCE] es **el detalle de cliente y la visita**, que es lo que él revisó y
 * lo que decidió arreglar. El resto del repo queda fuera **por ahora**, no por
 * estar bien: `:core:appgate` sí está al 100 %, `:core:speech` no tiene
 * defectos, pero los 38 `const val` de los tickets siguen en minúscula.
 *
 * Es una lista, y una lista miente cuando se queda corta. Por eso
 * [el alcance existe entero] falla si un archivo se renombra o se mueve: sin
 * eso, un `endsWith` que deja de coincidir encogería la compuerta a nada y
 * seguiría en verde. **El día que el barrido se extienda, esta lista crece.**
 *
 * ## Las cuatro excepciones, todas derivadas
 *
 * Ninguna es una lista escrita a mano:
 *
 * | Excepción | Cómo se reconoce |
 * |---|---|
 * | El sumidero aplica versalitas | su cuerpo contiene `.uppercase(BUSINESS_LOCALE)` |
 * | `.uppercase()` en el call site | el literal lo lleva pegado |
 * | Preguntas con `¿` / `¡` | se mira **la primera letra**, no el primer `Char` |
 * | Arranca con interpolación o cifra | el primer carácter no es letra |
 *
 * El sumidero es la excepción legítima de verdad y por eso se **deriva del
 * código**: `LabelDeSeccion("lo que hay que saber")` se pinta *LO QUE HAY QUE
 * SABER*, así que exigirle mayúscula inicial al literal sería exigir algo que
 * nadie ve. Un día que alguien escriba un sumidero nuevo, esta compuerta lo
 * aprende sola.
 */
class CadaTextoDeUsuarioEmpiezaEnMayusculaTest {

    private val escaner = EscanerDeFuentes()

    /**
     * **El control positivo del alcance.** Una lista de rutas es lo que más
     * fácil se pudre: basta un rename para que el barrido mida cero archivos y
     * siga en verde — que es exactamente la mentira que la regla del control
     * positivo existe para impedir.
     */
    @Test
    fun `el alcance existe entero`() {
        val perdidos = ALCANCE.filterNot { ruta -> archivoDe(ruta) != null }
        assertEquals(
            "estas rutas de ALCANCE ya no existen: o se renombraron y hay que " +
                "actualizar la lista, o el barrido está midiendo menos de lo que cree",
            emptyList<String>(),
            perdidos
        )
    }

    /**
     * **El control positivo del criterio**, con dos literales **sintéticos** y
     * no con un ancla del corpus: los literales del corpus son justamente lo
     * que este cambio está reescribiendo, así que anclarse en uno sería anclarse
     * en algo que se mueve.
     *
     * Molde de `CadaPantallaConTemaAnimaElCambioTest`: el mismo predicado tiene
     * que decir rojo de uno y verde del otro. Sin esto, un
     * [empiezaEnMayuscula] que devolviera `true` siempre daría verde para
     * siempre y nadie se enteraría.
     */
    @Test
    fun `el criterio rechaza la minuscula y acepta la mayuscula`() {
        assertEquals("un literal en minúscula tiene que dar rojo", false, empiezaEnMayuscula(MAL))
        assertEquals(
            "el mismo literal corregido tiene que dar verde",
            true,
            empiezaEnMayuscula(BIEN)
        )
    }

    /**
     * Los tres casos que el criterio **perdona**, y que no son excepciones
     * escritas a mano sino propiedades del texto: la pregunta mide su primera
     * letra y no el `¿`, y lo que arranca con interpolación o con cifra no
     * tiene primera letra que medir.
     */
    @Test
    fun `el criterio perdona la pregunta, la interpolacion y la cifra`() {
        assertEquals(
            "¿A cuál cuenta? cumple: la LETRA es mayúscula",
            true,
            empiezaEnMayuscula(PREGUNTA)
        )
        assertEquals(
            "un texto que abre con interpolación no se mide",
            null,
            empiezaEnMayuscula(INTERPOLA)
        )
        assertEquals("un texto que abre con cifra no se mide", null, empiezaEnMayuscula(CIFRA))
    }

    /**
     * El control positivo del sumidero: si la derivación devolviera un conjunto
     * vacío, el barrido dejaría de perdonar las versalitas y se llenaría de
     * rojos falsos; si devolviera medio repo, perdonaría todo y daría verde
     * vacío. Se ancla en las dos piezas compartidas que `CLAUDE.md` nombra.
     */
    @Test
    fun `los sumideros de versalitas se derivan del codigo`() {
        assertTrue(
            "no se derivó ningún sumidero de versalitas: el barrido pondría rojos falsos " +
                "sobre los literales que se pintan en mayúsculas",
            sumideros.isNotEmpty()
        )
        assertTrue(
            "faltan las dos piezas compartidas que CLAUDE.md nombra: $sumideros",
            sumideros.containsAll(listOf("LabelDeSeccion", "TituloDeHoja"))
        )
    }

    @Test
    fun `ningun texto de usuario del detalle de cliente ni de la visita empieza en minuscula`() {
        val medidos = ALCANCE.flatMap { ruta ->
            val archivo = archivoDe(ruta) ?: return@flatMap emptyList()
            literalesMedibles(archivo).map { ruta.substringAfterLast('/') to it }
        }
        assertTrue(
            "el barrido no midió ningún literal en ${ALCANCE.size} archivos: el extractor " +
                "dejó de encontrar texto y este test no probaría nada",
            medidos.size >= PISO_DE_LITERALES
        )

        val enMinuscula = medidos
            .filter { (_, texto) -> empiezaEnMayuscula(texto) == false }
            .map { (archivo, texto) -> "$archivo: \"$texto\"" }
            .sorted()
        assertEquals(
            "el texto de usuario lleva mayúscula inicial (principio 10 del brief, y " +
                "CLAUDE.md). Estos empiezan en minúscula. Si alguno se pinta en versalitas, " +
                "el arreglo NO es aflojar este test: es pasarlo por un sumidero que aplique " +
                ".uppercase(BUSINESS_LOCALE), y entonces la compuerta lo perdona sola",
            emptyList<String>(),
            enMinuscula
        )
    }

    // -----------------------------------------------------------------------

    /** El archivo del alcance, o `null` si ya no está donde dice [ALCANCE]. */
    private fun archivoDe(ruta: String): File? =
        escaner.archivos.firstOrNull { it.path.replace('\\', '/').endsWith(ruta) }

    /**
     * Las funciones de producción que **transforman el texto antes de
     * pintarlo**. Derivadas de todo el repo y no sólo del alcance: un sumidero
     * de `:core:designsystem` perdona igual a quien lo llama desde `:feature:`.
     */
    private val sumideros: Set<String> by lazy {
        escaner.archivos
            .flatMap { declaracionesDe(codigoDe(it)) }
            .filter { APLICA_VERSALITAS.containsMatchIn(it.texto) }
            .map { it.nombre }
            .toSet()
    }

    /**
     * Los literales de [archivo] que son texto de usuario medible.
     *
     * `codigoDe` ya descarta comentarios y KDoc —este repo documenta en el
     * código los defectos que mató, y un KDoc que cita *"registrar abono"* para
     * explicar el defecto no es el defecto—. Lo que queda por descartar aquí son
     * los literales que no se pintan (`testTag`, patrones de fecha) y los que
     * viajan dentro de una llamada a un sumidero, que puede abarcar varias
     * líneas y por eso se sigue por profundidad de paréntesis.
     */
    private fun literalesMedibles(archivo: File): List<String> {
        check(sumideros.isNotEmpty()) { "sin sumideros derivados el barrido mentiría" }
        val invocaSumidero = Regex(
            "(?<![A-Za-z0-9_])(" +
                sumideros.joinToString("|") { Regex.escape(it) } +
                ")\\s*\\("
        )
        val encontrados = mutableListOf<String>()
        var profundidad = 0
        codigoDe(archivo).lineSequence().forEach { linea ->
            val sinTexto = LITERAL.replace(linea) { "\"\"" }
            if (profundidad > 0) {
                profundidad += sinTexto.count { it == '(' } - sinTexto.count { it == ')' }
                return@forEach
            }
            val llamada = invocaSumidero.find(sinTexto)
            if (llamada != null) {
                val desdeElParentesis = sinTexto.substring(llamada.range.last)
                profundidad = desdeElParentesis.count { it == '(' } -
                    desdeElParentesis.count { it == ')' }
                return@forEach
            }
            if (NO_SE_PINTA.containsMatchIn(linea)) return@forEach
            LITERAL.findAll(linea).forEach { texto ->
                val despues = linea.substring(texto.range.last + 1)
                if (!despues.startsWith(".uppercase")) encontrados += texto.groupValues[1]
            }
        }
        return encontrados
    }

    private companion object {

        /**
         * **El detalle de cliente y la visita** — el alcance que el dueño eligió.
         * Ver el KDoc de la clase: crece cuando el barrido crezca, y
         * [el alcance existe entero] falla si una ruta se pudre.
         */
        val ALCANCE: List<String> = listOf(
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "domain/EtiquetasDeFicha.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/DetalleClienteScreen.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/DetalleUiState.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/HojaDeAbono.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/HojaDeLaFicha.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/Marco.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/PiezasDeLaFicha.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/PiezasDelCliente.kt",
            "feature/visitas/src/main/kotlin/com/example/msp_app/feature/visitas/" +
                "ui/RegistrarVisitaScreen.kt",
            "feature/visitas/src/main/kotlin/com/example/msp_app/feature/visitas/" +
                "ui/RegistrarVisitaUiState.kt",
            "feature/visitas/src/main/kotlin/com/example/msp_app/feature/visitas/" +
                "ui/components/PiezasDeLaVisita.kt",
            "feature/visitas/src/main/kotlin/com/example/msp_app/feature/visitas/" +
                "ui/components/CalendarioYReloj.kt"
        )

        /**
         * Cuántos literales tiene que ver el barrido para creerse. No es una
         * medición fina: es el piso que separa "midió el corpus" de "el
         * extractor dejó de encontrar nada y el test pasa vacío".
         */
        const val PISO_DE_LITERALES = 60

        /** Un literal de Kotlin en una línea, con sus escapes. */
        val LITERAL = Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"")

        /** El sumidero de versalitas, tal como lo escribe este repo. */
        val APLICA_VERSALITAS = Regex("""\.uppercase\(BUSINESS_LOCALE\)""")

        /** Literales que nadie ve: tags de test y patrones de fecha. */
        val NO_SE_PINTA = Regex("""testTag\(|_TAG|ofPattern\(""")

        /** Los dos literales sintéticos del control positivo del criterio. */
        const val MAL = "continuar"
        const val BIEN = "Continuar"
        const val PREGUNTA = "¿A cuál cuenta?"
        const val INTERPOLA = "\$monto de abono"
        const val CIFRA = "3 atrasos"
    }
}

/**
 * ¿[literal] empieza en mayúscula?
 *
 * `null` significa **fuera de alcance**, no "sí": un texto que abre con una
 * interpolación (`"$monto de abono"`) o con una cifra (`"3 atrasos"`) no tiene
 * primera letra que medir, y uno sin ninguna letra —`" · "`, `"$"`— tampoco.
 * Aplanar los tres estados a un `Boolean` volvería indistinguible "cumple" de
 * "no aplica", y el día que el extractor se rompiera, todo sería "cumple".
 *
 * Se mira **la primera letra y no el primer `Char`** porque el español abre
 * preguntas y exclamaciones con `¿` y `¡`: *"¿A cuál cuenta?"* cumple.
 */
internal fun empiezaEnMayuscula(literal: String): Boolean? {
    val primero = literal.firstOrNull() ?: return null
    if (primero == '$' || primero.isDigit()) return null
    val primeraLetra = literal.firstOrNull { it.isLetter() } ?: return null
    return primeraLetra.isUpperCase()
}
