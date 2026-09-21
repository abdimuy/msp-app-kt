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
 *
 * ## La extensión de la Task 1 (`2026-09-20-fila-de-contactos`): las etiquetas de enum
 *
 * `MetodoDeCobro` nacía con `EFECTIVO("efectivo")` — el barrido del 18-sep no lo
 * vio porque [ALCANCE] es una lista de PANTALLAS, y `HistorialDePagos.kt` (donde
 * vive el enum) no estaba en ella ni tenía por qué: las cuatro superficies que
 * SÍ leen `MetodoDeCobro.etiqueta` (`LineaDeContactos`, `HojaDeConfirmacion`,
 * `PiezasDelAbono`, `RitmoYRiel`) sólo hacen `metodo.etiqueta` — ningún literal
 * viaja por esas pantallas, así que agregarlas a [ALCANCE] no habría atrapado
 * nada.
 *
 * Meter `HistorialDePagos.kt` a mano a [ALCANCE] habría sido la MISMA lista a
 * mano que esta clase ya rechaza para las pantallas — y además una demasiado
 * ancha: ese archivo, y los que están al lado en `:feature:pagos`, están llenos
 * de texto de usuario de otras tareas ("editar", "confirmar", "sí", "no cobró")
 * que no es de esta Task 1 y que barrer entero pondría en rojo por una razón
 * ajena. La granularidad correcta no es el ARCHIVO, es la DECLARACIÓN: un
 * `enum class` con un parámetro de constructor llamado `etiqueta` tipado
 * `String` es, por construcción, un catálogo cerrado de texto de usuario —
 * exactamente el molde de `MetodoDeCobro`. [etiquetasDeEnum] lo descubre en
 * TODO el repo (no sólo en `:feature:pagos`) parseando la posición del
 * parámetro `etiqueta` en el constructor y leyendo el literal en esa misma
 * posición de cada entrada — sin tocar el resto del archivo.
 *
 * Medido sobre el repo completo, el molde encontró **tres** enums con el mismo
 * defecto, no uno: además de `MetodoDeCobro`, `MontosSugeridos.Sugerencia`
 * (`"esperado hoy"`, `"al corriente"`, `"liquidar"`) y `EstadoDeGarantia`
 * (`"notificada"`, `"recolectada"`, `"entregada"`, `"sin estado"`). Los tres se
 * corrigieron con esta tarea — dejar dos en rojo habría significado inventar
 * una excepción escrita a mano para que el barrido pasara, que es la trampa que
 * este archivo entero existe para cerrar.
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

    /**
     * El control positivo del descubrimiento de enums: si [etiquetasDeEnum]
     * devolviera vacío, la siguiente prueba pasaría en verde sin medir nada —
     * la misma mentira que el resto de los controles positivos de esta clase
     * existe para impedir. Se ancla en `MetodoDeCobro.EFECTIVO` porque es el
     * defecto medido que originó la Task 1.
     */
    @Test
    fun `se descubren enums con parametro etiqueta en todo el repo`() {
        assertTrue(
            "no se descubrió ningún enum con parámetro etiqueta: el barrido de " +
                "enums pondría rojos falsos o un verde vacío",
            etiquetasDeEnum.isNotEmpty()
        )
        assertTrue(
            "MetodoDeCobro.EFECTIVO tiene que aparecer: es el defecto medido de la Task 1",
            etiquetasDeEnum.any { (quien, _) -> quien == "MetodoDeCobro.EFECTIVO" }
        )
    }

    /**
     * La extensión de la Task 1. Ver el KDoc de la clase: un `enum class` con
     * parámetro `etiqueta: String` es un catálogo cerrado de texto de usuario,
     * así que cada literal en esa posición lleva mayúscula inicial igual que
     * cualquier otro texto de usuario.
     */
    @Test
    fun `ninguna etiqueta de un enum de usuario empieza en minuscula`() {
        val enMinuscula = etiquetasDeEnum
            .filter { (_, texto) -> empiezaEnMayuscula(texto) == false }
            .map { (quien, texto) -> "$quien: \"$texto\"" }
            .sorted()
        assertEquals(
            "las etiquetas de un enum de usuario llevan mayúscula inicial (principio 10 " +
                "del brief, y CLAUDE.md). El barrido del 18-sep no las vio porque su alcance " +
                "era una lista de pantallas, no de declaraciones: ver el KDoc de esta clase",
            emptyList<String>(),
            enMinuscula
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
     * `("$nombreDeLaEntrada", "$literalDeSuEtiqueta")` de cada entrada de cada
     * `enum class` con parámetro `etiqueta: String`, en TODO el repo — ver el
     * KDoc de la clase para por qué es por declaración y no por archivo.
     */
    private val etiquetasDeEnum: List<Pair<String, String>> by lazy {
        escaner.archivos.flatMap { etiquetasDeEnumsEn(it) }
    }

    /** [etiquetasDeEnum], pero de un solo archivo. */
    private fun etiquetasDeEnumsEn(archivo: File): List<Pair<String, String>> {
        val lineas = codigoDe(archivo).lines()
        val encontradas = mutableListOf<Pair<String, String>>()
        lineas.forEachIndexed { i, linea ->
            val header = ENUM_CON_ETIQUETA.find(linea) ?: return@forEachIndexed
            val enumNombre = header.groupValues[1]
            val indice = indiceDeEtiqueta(header.groupValues[2]) ?: return@forEachIndexed
            for (j in (i + 1) until lineas.size) {
                val candidata = lineas[j]
                if (candidata.isBlank()) continue
                val entrada = ENTRADA_DE_ENUM.find(candidata) ?: break
                val (entryNombre, argumentos) = entrada.destructured
                val literal = literalEnPosicion(argumentos, indice)
                if (literal != null) encontradas += "$enumNombre.$entryNombre" to literal
            }
        }
        return encontradas
    }

    /**
     * En qué posición del constructor de un `enum class` va el parámetro
     * llamado `etiqueta`, o `null` si ese enum no tiene uno.
     */
    private fun indiceDeEtiqueta(parametros: String): Int? {
        val nombres = parametros.split(',').map {
            it.substringBefore(':').trim().substringAfterLast(' ')
        }
        val indice = nombres.indexOf("etiqueta")
        return indice.takeIf { it >= 0 }
    }

    /**
     * El literal de Kotlin en la posición [indice] de una llamada al
     * constructor de una entrada de enum (`NOMBRE(arg0, arg1, ...)`), o `null`
     * si esa posición no existe o no es un literal de texto.
     */
    private fun literalEnPosicion(argumentos: String, indice: Int): String? {
        val partes = splitArgumentosDeNivelSuperior(argumentos)
        if (indice >= partes.size) return null
        return LITERAL.matchEntire(partes[indice])?.groupValues?.get(1)
    }

    /**
     * Parte una lista de argumentos por sus comas de NIVEL SUPERIOR — las que
     * no están dentro de un literal de texto. Suficiente para las entradas de
     * enum de este repo, que son literales simples (`String`, `Int`), no
     * llamadas anidadas.
     */
    private fun splitArgumentosDeNivelSuperior(argumentos: String): List<String> {
        val partes = mutableListOf<String>()
        val actual = StringBuilder()
        var enComillas = false
        argumentos.forEachIndexed { i, c ->
            if (c == '"' && (i == 0 || argumentos[i - 1] != '\\')) enComillas = !enComillas
            if (c == ',' && !enComillas) {
                partes += actual.toString().trim()
                actual.clear()
            } else {
                actual.append(c)
            }
        }
        partes += actual.toString().trim()
        return partes
    }

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

        /**
         * El encabezado de un `enum class` con constructor, en la forma en la
         * que este repo lo escribe (una sola línea): `enum class Nombre(params)`.
         * No exige que `etiqueta` esté entre los parámetros — eso lo decide
         * [indiceDeEtiqueta] sobre el grupo 2 — así que un enum con constructor
         * y SIN `etiqueta` simplemente no aporta entradas.
         */
        val ENUM_CON_ETIQUETA = Regex("""enum class (\w+)\(([^)]*)\)""")

        /**
         * Una entrada de enum: `NOMBRE(argumentos)`, con `,` o `;` opcional al
         * final (la última entrada de Kotlin no lleva coma). El nombre en
         * MAYÚSCULAS_CON_GUION_BAJO es la convención de este repo para
         * entradas de enum, y es lo que distingue una entrada de la siguiente
         * declaración de nivel superior (`fun`, `companion object`), que
         * arranca en minúscula o con `@`.
         */
        val ENTRADA_DE_ENUM = Regex("""^\s*([A-Z][A-Z0-9_]*)\((.*)\)\s*[,;]?\s*$""")

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
