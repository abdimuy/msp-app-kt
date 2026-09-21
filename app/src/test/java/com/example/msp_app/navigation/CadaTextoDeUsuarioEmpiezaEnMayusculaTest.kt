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
 *
 * ## El Arreglo B (21-sep): la lista a mano era el `etiqueta` del molde, no sólo de [ALCANCE]
 *
 * El dueño encontró en su teléfono "no estaba", "visité, vuelvo" y "prometió
 * pagar" en minúscula — el catálogo de desenlaces de una visita,
 * `ResultadoDeVisita`. El molde de la Task 1 debió atraparlo y no lo hizo:
 * exigía que el parámetro se llamara **literalmente** `etiqueta`, y
 * `ResultadoDeVisita` lo llama `titulo` (con `detalle` al lado). Era la MISMA
 * lista a mano que el resto de esta clase ya rechaza, sólo que escrita como el
 * nombre de un parámetro en vez de como una ruta de archivo — y por eso
 * tardó exactamente igual en encontrarse: nadie se acordó de agregarlo.
 *
 * Un segundo defecto, independiente, escondía a `ResultadoDeVisita` incluso si
 * el nombre hubiera coincidido: su constructor pone un KDoc arriba de CADA
 * parámetro (`/** Título del renglón... */`), así que nunca cabe en una sola
 * línea, y [ENUM_HEADER] exigía eso. [parametrosDelConstructor] ya no lo exige
 * — sigue la profundidad de paréntesis, como ya hacía [literalesMedibles] para
 * los sumideros de varias líneas.
 *
 * El arreglo real no fue agregar `ResultadoDeVisita` a una lista: fue quitarle
 * el nombre exacto a [indicesDeTextoDeUsuario], que ahora acepta CUALQUIER
 * parámetro `String` salvo los de [NOMBRES_SIN_PROSA] (`id`, `code`, `crudo`...).
 * Medido sobre el repo completo con el molde ya corregido, aparecieron
 * **cuatro** enums más con el mismo defecto — `BloqueoDeLaVisita.razon`,
 * `ErrorDelTicketDeVisita.mensaje` y `ErrorDelTicket.mensaje` (dos, uno por
 * pantalla de ticket) — y los cuatro se corrigieron aquí, igual que los tres
 * de la Task 1.
 *
 * ## Lo que [etiquetasDeEnum] SÍ descubre solo, y lo que [ALCANCE] sigue sin descubrir
 *
 * [etiquetasDeEnum] es, desde este arreglo, genuinamente "por declaración": un
 * enum nuevo con un parámetro `String` que no esté en [NOMBRES_SIN_PROSA] entra
 * al barrido el día que se escribe, en cualquier módulo, sin tocar este
 * archivo. [ALCANCE], en cambio, **sigue siendo una lista a mano** — el dueño
 * pidió "barrer el detalle de venta y la pantalla de agregar pago", así que se
 * agregaron `DetalleVentaScreen.kt`, `RegistrarAbonoScreen.kt` y sus piezas
 * directas (`PiezasDelAbono.kt`, `TarjetasDeDinero.kt`), pero seguir
 * derivando ESTA lista sola —"todo archivo bajo `ui/`", por ejemplo— se probó
 * y se descartó: `LineaDeContactos.kt` tiene `MESES_ABREVIADOS` (`"ene"`,
 * `"feb"`...), abreviaturas de mes que van en minúscula a propósito dentro de
 * una fecha compuesta (`"18 feb"`, ver su KDoc), y un barrido por carpeta las
 * habría marcado en rojo por una razón que no es la de esta tarea. La frontera
 * que sí se cerró para siempre es la de la interpolación fuera de posición
 * cero (ver el KDoc de [empiezaEnMayuscula]), que es la que de verdad
 * escondía falsos positivos; la de "qué archivo entra a [ALCANCE]" se queda
 * como decisión del dueño, declarada aquí y no implícita.
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
     * El control positivo del Arreglo B (Task del 21-sep): una interpolación
     * que NO va al principio también deja el literal fuera de alcance. Antes
     * de este arreglo, `" · $producto · saldo "` medía la **p** de `producto`
     * —el nombre de la variable— y `"“$it”"` medía la **i** de `it`; los dos
     * son el defecto real que escondió `TiraDeContexto` y la nota entre
     * comillas de `ContactoEnLinea` del barrido original.
     */
    @Test
    fun `la interpolacion se perdona aunque no vaya al principio`() {
        assertEquals(
            "el separador y la variable no tienen letra propia que medir",
            null,
            empiezaEnMayuscula(" · \$producto · saldo ")
        )
        assertEquals(
            "la comilla no es la variable: sigue sin haber letra que medir",
            null,
            empiezaEnMayuscula("“\$it”")
        )
        assertEquals(
            "con texto real ANTES de la interpolación sí hay letra que medir",
            false,
            empiezaEnMayuscula("vigente hasta el \$dia")
        )
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
            etiquetasDeEnum.any { (quien, _) -> quien == "MetodoDeCobro.EFECTIVO.etiqueta" }
        )
    }

    /**
     * El control positivo del Arreglo B: [etiquetasDeEnum] tiene que
     * descubrir `ResultadoDeVisita.NO_ESTABA` por su parámetro `titulo` —no
     * `etiqueta`— y pese a que su constructor nunca cabe en una sola línea
     * (cada parámetro lleva su propio KDoc arriba). Si esta prueba se pusiera
     * roja, la siguiente pasaría en verde por no medir nada — la misma
     * mentira que los demás controles positivos de esta clase existen para
     * impedir.
     */
    @Test
    fun `se descubre un enum con parametro no llamado etiqueta y constructor en varias lineas`() {
        assertTrue(
            "ResultadoDeVisita.NO_ESTABA.titulo tiene que aparecer: es el defecto real " +
                "que el dueño vio en su teléfono (Arreglo B)",
            etiquetasDeEnum.any { (quien, _) -> quien == "ResultadoDeVisita.NO_ESTABA.titulo" }
        )
        assertTrue(
            "ResultadoDeVisita.NO_ESTABA.detalle también, en la MISMA entrada: un enum " +
                "puede tener más de un parámetro de prosa",
            etiquetasDeEnum.any { (quien, _) -> quien == "ResultadoDeVisita.NO_ESTABA.detalle" }
        )
    }

    /**
     * La extensión de la Task 1 y del Arreglo B. Ver el KDoc de la clase: un
     * `enum class` con un parámetro `String` que no esté en [NOMBRES_SIN_PROSA]
     * es un catálogo cerrado de texto de usuario, así que cada literal en esa
     * posición lleva mayúscula inicial igual que cualquier otro texto de
     * usuario.
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
                "era una lista de pantallas, no de declaraciones, y el del 21-sep exigía que " +
                "el parámetro se llamara literalmente `etiqueta`: ver el KDoc de esta clase",
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
     * `("$enum.$entrada.$parametro", "$literal")` de cada entrada de cada
     * `enum class` con al menos un parámetro `String` que no sea de la lista
     * [NOMBRES_SIN_PROSA], en TODO el repo — ver el KDoc de la clase para por
     * qué es por declaración y no por archivo, y por qué YA NO exige que el
     * parámetro se llame literalmente `etiqueta`.
     */
    private val etiquetasDeEnum: List<Pair<String, String>> by lazy {
        escaner.archivos.flatMap { etiquetasDeEnumsEn(it) }
    }

    /**
     * [etiquetasDeEnum], pero de un solo archivo.
     *
     * Recorre línea por línea buscando `enum class Nombre(`; el constructor
     * puede cerrar en la MISMA línea (`MetodoDeCobro`) o varias líneas después
     * —un parámetro por renglón, con su propio KDoc arriba, como
     * `ResultadoDeVisita`—, así que [parametrosDelConstructor] sigue la
     * profundidad de paréntesis en vez de exigir que todo quepa en un renglón.
     */
    private fun etiquetasDeEnumsEn(archivo: File): List<Pair<String, String>> {
        val lineas = codigoDe(archivo).lines()
        val encontradas = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < lineas.size) {
            val header = ENUM_HEADER.find(lineas[i])
            if (header == null) {
                i++
                continue
            }
            val enumNombre = header.groupValues[1]
            val (parametros, ultimaLinea) =
                parametrosDelConstructor(lineas, i, header.range.last + 1)
            val indices = indicesDeTextoDeUsuario(parametros)
            if (indices.isNotEmpty()) {
                for (j in (ultimaLinea + 1) until lineas.size) {
                    val candidata = lineas[j]
                    if (candidata.isBlank()) continue
                    val entrada = ENTRADA_DE_ENUM.find(candidata) ?: break
                    val (entryNombre, argumentos) = entrada.destructured
                    indices.forEach { (indice, nombreParam) ->
                        val literal = literalEnPosicion(argumentos, indice)
                        if (literal != null) {
                            encontradas += "$enumNombre.$entryNombre.$nombreParam" to literal
                        }
                    }
                }
            }
            i = ultimaLinea + 1
        }
        return encontradas
    }

    /**
     * El texto de los parámetros del constructor de un `enum class`, desde
     * [desde] en la línea [inicio] hasta que su paréntesis cierra — sin
     * importar cuántas líneas tome. Devuelve ese texto y el índice de la
     * ÚLTIMA línea consumida, para que [etiquetasDeEnumsEn] sepa dónde seguir
     * buscando las entradas.
     *
     * Cuenta paréntesis carácter por carácter en vez de con una regex de una
     * sola línea: es lo que permite que `ResultadoDeVisita` —cuyo constructor
     * pone un KDoc arriba de cada parámetro y por eso nunca cabe en un
     * renglón— se descubra igual que `MetodoDeCobro`, que sí cabe.
     */
    private fun parametrosDelConstructor(
        lineas: List<String>,
        inicio: Int,
        desde: Int
    ): Pair<String, Int> {
        val texto = StringBuilder()
        var profundidad = 1
        var j = inicio
        var pos = desde
        while (j < lineas.size) {
            val linea = lineas[j]
            while (pos < linea.length) {
                val caracter = linea[pos]
                if (caracter == '(') profundidad++
                if (caracter == ')') {
                    profundidad--
                    if (profundidad == 0) return texto.toString() to j
                }
                texto.append(caracter)
                pos++
            }
            texto.append('\n')
            j++
            pos = 0
        }
        return texto.toString() to (lineas.size - 1)
    }

    /**
     * De [parametros] —el texto crudo entre los paréntesis del constructor—,
     * el `(índice, nombre)` de cada parámetro `String` que **no** está en
     * [NOMBRES_SIN_PROSA].
     *
     * La lista es una lista de EXCLUSIÓN y no de inclusión, y esa dirección
     * importa: un parámetro `String` es prosa hasta que su nombre demuestre lo
     * contrario (`id`, `code`, `crudo`...), así que un enum nuevo con un
     * parámetro `titulo` o `mensaje` —cualquier nombre que no esté en la
     * lista— entra al barrido el día que se escribe, sin tocar este archivo.
     * Al revés —una lista de nombres permitidos— es la misma lista a mano que
     * escondió `ResultadoDeVisita.titulo`: sólo protege al parámetro que
     * alguien se acordó de escribir.
     */
    private fun indicesDeTextoDeUsuario(parametros: String): List<Pair<Int, String>> {
        val partes = splitArgumentosDeNivelSuperior(parametros)
        val resultado = mutableListOf<Pair<Int, String>>()
        partes.forEachIndexed { indice, parte ->
            val sinModificador = parte.trim().removePrefix("val ").removePrefix("var ").trim()
            val nombre = sinModificador.substringBefore(':').trim()
            val tipo = sinModificador.substringAfter(':', "").trim()
            if (tipo == "String" && nombre.isNotEmpty() && nombre !in NOMBRES_SIN_PROSA) {
                resultado += indice to nombre
            }
        }
        return resultado
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
     *
     * `ofPattern` entra a la misma lista que los sumideros, aunque no aplique
     * versalitas: un patrón de fecha (`"d MMM yyyy"`) tampoco es prosa, y
     * cuando el `DateTimeFormatter.ofPattern(...)` se escribe en varias
     * líneas —como en `DetalleVentaScreen.FECHA_DE_VENTA`— [NO_SE_PINTA] no lo
     * ve, porque esa regex mide una línea a la vez y `"d MMM yyyy"` vive en la
     * línea de ABAJO de `ofPattern(`. El mismo seguimiento por profundidad de
     * paréntesis que ya protege a los sumideros resuelve esto sin duplicar
     * mecanismo.
     */
    private fun literalesMedibles(archivo: File): List<String> {
        check(sumideros.isNotEmpty()) { "sin sumideros derivados el barrido mentiría" }
        val invocaSumidero = Regex(
            "(?<![A-Za-z0-9_])(" +
                (sumideros + "ofPattern").joinToString("|") { Regex.escape(it) } +
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
         * **El detalle de cliente, el de venta, el abono y la visita** — el
         * alcance que el dueño eligió, ampliado el 21-sep para cubrir el
         * defecto que vio en su teléfono. Ver el KDoc de la clase: crece
         * cuando el barrido crezca, y [el alcance existe entero] falla si una
         * ruta se pudre.
         *
         * Sigue siendo una lista a mano — eso NO cambió con la Task del
         * 21-sep, y el KDoc de la clase explica por qué (falsos positivos como
         * `MESES_ABREVIADOS`). Lo que sí dejó de ser una lista a mano es
         * [etiquetasDeEnum], que ahora barre TODO el repo por estructura y no
         * necesita que nadie agregue un archivo aquí.
         */
        val ALCANCE: List<String> = listOf(
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "domain/EtiquetasDeFicha.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/DetalleClienteScreen.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/DetalleVentaScreen.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/DetalleUiState.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/RegistrarAbonoScreen.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/HojaDeAbono.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/HojaDeLaFicha.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/Marco.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/PiezasDeLaFicha.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/PiezasDelAbono.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/PiezasDelCliente.kt",
            "feature/pagos/src/main/kotlin/com/example/msp_app/feature/pagos/" +
                "ui/components/TarjetasDeDinero.kt",
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

        /** Literales que nadie ve: tags de test y patrones de fecha de una sola línea. */
        val NO_SE_PINTA = Regex("""testTag\(|_TAG|ofPattern\(""")

        /**
         * El encabezado de un `enum class` con constructor, hasta el paréntesis
         * que lo abre. [parametrosDelConstructor] sigue desde ahí — ya no hace
         * falta que el resto quepa en la misma línea.
         */
        val ENUM_HEADER = Regex("""enum class (\w+)\(""")

        /**
         * Nombres de parámetro que, aunque sean `String`, no son prosa por
         * construcción: un identificador, una clave, una ruta o un patrón no
         * son texto que el dueño haya escrito para que alguien lo lea. Es una
         * lista de EXCLUSIÓN — ver el KDoc de [indicesDeTextoDeUsuario] para
         * por qué esa dirección es la que importa.
         */
        val NOMBRES_SIN_PROSA = setOf(
            "id", "code", "key", "crudo", "wireValue", "route", "path",
            "pattern", "format", "mimeType", "url", "uri", "tag", "icon"
        )

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
 *
 * ## La interpolación se perdona donde aparezca, no sólo al principio
 *
 * La versión anterior sólo miraba el primer `Char` del literal: `"$monto de
 * abono"` se perdonaba, pero `" · $producto · saldo "` NO — el `$` va después
 * de un separador, así que la vieja regla caía directo a "busca la primera
 * letra" y encontraba la **p** de `producto`, el nombre de la variable, y la
 * reportaba como si fuera prosa en minúscula. Es el mismo defecto que
 * `"“$it”"` (la nota entrecomillada de `ContactoEnLinea`): la **i** de `it` se
 * leía como si fuera la primera letra del texto.
 *
 * Por eso ahora se recorre [literal] carácter por carácter, saltando
 * separadores (espacios, puntuación, comillas), y el primer carácter que
 * **importa** decide: una letra mide su mayúscula, una cifra o un `$` dejan
 * todo el literal fuera de alcance —lo que venga después de un `$` es una
 * variable, no una palabra que el dueño haya escrito—. Si no aparece ninguno
 * de los tres, tampoco hay nada que medir.
 */
internal fun empiezaEnMayuscula(literal: String): Boolean? {
    for (caracter in literal) {
        if (caracter.isLetter()) return caracter.isUpperCase()
        if (caracter.isDigit() || caracter == '$') return null
    }
    return null
}
