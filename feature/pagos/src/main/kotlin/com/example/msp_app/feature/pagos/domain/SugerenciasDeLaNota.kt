package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import java.text.Normalizer

/** Un término del vocabulario, ya normalizado y partido en palabras. */
private typealias Termino = List<String>

/**
 * **Lo que el vocabulario de la nota sugiere marcar en el catálogo.**
 *
 * La ficha tiene dos campos con dos lectores distintos: [SenalDeFicha] es lo
 * único que lee código —la pantalla pinta la advertencia en rojo y la sube a la
 * barra superior— y la nota es prosa para el humano que llega mañana. El
 * problema real es que el cobrador escribe *"hay perro"* en la prosa, y ahí esa
 * información **no la puede pintar nadie**: queda invisible para la pantalla,
 * para el orden de la ruta y para cualquier conteo futuro.
 *
 * Esta pieza es el puente: lee el texto y devuelve qué casillas del catálogo
 * **ofrecerle** al cobrador. Es lo que hace que la nota deje de repetir lo que
 * ya podría ser dato.
 *
 * Dominio puro: cero Android, cero Compose, cero I/O. Sólo `String` adentro y
 * `Set<SenalDeFicha>` afuera.
 *
 * ## Sugiere, no decide — y de ahí sale todo lo demás
 *
 * El resultado es un conjunto de **candidatas**. Quien llama le ofrece la
 * casilla al cobrador; marcarla es suya. Eso fija la asimetría que gobierna
 * cada decisión de abajo:
 *
 * - **Un falso positivo cuesta un toque** — ignorar una casilla ofrecida.
 * - **Un falso negativo cuesta nada** — la nota sigue ahí, entera, y el
 *   cobrador siempre puede marcar la casilla a mano.
 *
 * Por eso el vocabulario es **generoso** (más términos de los estrictamente
 * necesarios) y la negación es **conservadora** (ante la duda, no sugiere).
 * La única sugerencia que sí cuesta caro es la que **contradice** lo que la
 * nota dice —ofrecer `HAY_PERRO` sobre *"no hay perro"*—, porque ésa no es
 * ruido: es un dato al revés que alguien puede terminar marcando.
 *
 * Nada de aquí escribe, recorta ni reescribe la nota. La nota es del humano.
 *
 * ## Normalización
 *
 * Minúsculas y sin diacríticos (NFD + quitar `\p{M}`), porque quien escribe
 * parado en una puerta escribe *"Perro"*, *"PERRO"*, *"manana"*. Después del
 * plegado, `ñ` es `n` y *"mañana"*, *"MAÑANA"* y *"manana"* son el mismo token.
 *
 * **No se reusa [BusquedaDeClientes.normalizar]** aunque hoy la receta es
 * idéntica: son dos dueños distintos: si mañana la búsqueda decide plegar
 * guiones o dígitos para encontrar teléfonos, lo que sugiere una puerta
 * cambiaría sin que nadie lo haya pedido. Copiar tres líneas es más barato que
 * acoplar dos reglas de negocio que sólo coinciden por accidente.
 *
 * ## Palabra completa, nunca subcadena
 *
 * El texto se parte en palabras (`[^\p{L}\p{N}]+`) y un término se busca como
 * **secuencia contigua de palabras**. En español la subcadena es una trampa
 * cara: *"perrón"* (elogio) contiene *perro*, *"anoche"* contiene *noche*,
 * *"perrera"* contiene *perr*. Con `contains` las tres dispararían.
 *
 * Buscar secuencias —y no palabras sueltas— es además lo que permite que el
 * vocabulario tenga frases (*"en la mañana"*, *"no es el titular"*), y las
 * frases son justo lo que desambigua el español (ver abajo).
 *
 * **No hay tolerancia a errores de dedo, y es deliberado.** *"hay pero"* no
 * sugiere `HAY_PERRO`. Una coincidencia difusa a una letra de distancia haría
 * que *"pero"* —de las palabras más comunes del idioma: *"pero no está"*—
 * dispare perro en media cartera. Exacto y aburrido gana.
 *
 * ## Qué NO entró al vocabulario, y por qué
 *
 * El catálogo ya decidió qué es señal; esto decide qué **palabras** apuntan a
 * cada señal. Lo que quedó fuera quedó fuera por ambiguo, no por raro:
 *
 * - **`mañana` a secas.** Es el falso amigo mayor del español: *"vuelvo
 *   mañana"* es un día, no una hora. Sólo entran las formas que ya traen la
 *   hora pegada: *"en la mañana"*, *"por las mañanas"*, *"temprano"*, *"am"*.
 * - **`tarde` a secas.** *"paga tarde"*, *"siempre llega tarde"* hablan de
 *   retraso, y en una app de cobranza eso es lo más probable que signifique.
 *   Entran *"en la tarde"*, *"por las tardes"*, *"tardes"*, *"después de
 *   comer"*.
 * - **`pm`**: cubre de las 12:00 a las 23:59, o sea **dos** de las tres
 *   ventanas. Un término que no puede elegir señal no es una sugerencia.
 *   `am` sí entró porque cae entero en la mañana.
 * - **`mediodía`**: las 12:00 son `ESTA_EN_LA_MANANA` y las 13:00 son
 *   `ESTA_EN_LA_TARDE` —el corte del catálogo pasa justo por esa palabra—, y
 *   nadie que escribe *"al mediodía"* quiere decir un minuto exacto.
 * - **`señor` / `señora`**: es la forma más común de nombrar **al titular**
 *   (*"la señora paga los viernes"*), así que no es evidencia de que atienda
 *   otra persona. Sí entraron los parentescos, que sólo se usan para terceros.
 * - **`madrugada`**: cae fuera de `JORNADA` (abre 08:00), así que no hay señal
 *   que ofrecer.
 * - **Referencias de la casa** (*"casa azul, portón negro"*) y **nombres
 *   propios**: el catálogo ya las excluyó por diseño. No tienen señal destino,
 *   así que no tienen vocabulario. Esa prosa **debe** quedarse en la nota.
 *
 * ## La negación: cómo se resuelve y qué NO cubre
 *
 * *"no hay perro"* no puede sugerir `HAY_PERRO`. La regla es una sola:
 *
 * > Una aparición del término queda anulada si hay un negador
 * > ([NEGADORES]) **antes** de ella, **en la misma cláusula**, dentro de
 * > [VENTANA_DE_NEGACION] palabras. Si alguna aparición sobrevive, la señal se
 * > sugiere.
 *
 * Las dos mitades hacen falta. La **cláusula** (lo que hay entre `.,;:/|` o un
 * salto de línea) evita que un `no` contagie una idea distinta: el cobrador
 * escribe listas, y en *"no ir solo, hay perro"* el perro sí existe. La
 * **ventana** evita lo contrario: en una cláusula larga sin puntuación, un `no`
 * del principio no debe apagar lo que se dice al final.
 *
 * `5` es un **primer corte, no una medición** —no hay en este repo un corpus de
 * notas reales con el cual calibrarlo—. Se eligió para que el negador alcance a
 * cruzar una frase verbal corta (*"**no** lo encuentro en la **noche**"*, cinco
 * palabras) sin llegar a la idea siguiente. Lo que este archivo fija por prueba
 * son los casos, no el número.
 *
 * **Lo que este enfoque NO cubre** (y no lo finge):
 *
 * 1. **El negador que llega después del término.** *"el perro ya no está"*
 *    **sí** sugiere `HAY_PERRO`: la ventana sólo mira hacia atrás. Mirar hacia
 *    adelante costaría *"hay perro, no muerde"*, que es peor.
 * 2. **La negación semántica sin negador.** *"el perro se murió"*, *"se lo
 *    llevaron"* sugieren `HAY_PERRO`. Aquí no hay análisis de significado: hay
 *    una lista cerrada de palabras.
 * 3. **La cláusula corrida sin puntuación.** *"no tiene timbre hay perro"* no
 *    sugiere nada: el `no` queda a cuatro palabras y anula. Es el lado barato
 *    del error (falso negativo) y por eso se prefirió así.
 * 4. **El negador que quedó solo en su cláusula.** *"no, hay perro"* sí
 *    sugiere: la coma lo dejó en otra cláusula.
 *
 * Se consideró y **no se hizo** una segunda pasada que ignorara los negadores
 * que forman parte de un término ya reconocido (arreglaría el caso 3 cuando el
 * `no` viene de *"no ir solo"*). Duplica el recorrido para ganar un falso
 * negativo que no le cuesta nada a nadie.
 *
 * ## `NO_IR_SOLO`: la señal que ya es una negación
 *
 * Hay una trampa que sólo aparece aquí: la señal **es** una prohibición, así
 * que la heurística de negación la invertiría. Si el vocabulario tuviera
 * *"ir solo"*, entonces *"nunca ir solo"* —que la afirma— quedaría anulada por
 * su propio `nunca`.
 *
 * Por eso su vocabulario se escribe **ya negado**: entran *"no ir solo"*,
 * *"nunca ir solo"*, *"no entrar solo"* como frases completas, y nunca *"solo"*
 * suelto. El resto de sus términos son afirmativos de riesgo
 * (*"peligroso"*, *"agresivo"*), donde la heurística vuelve a funcionar al
 * derecho: *"no es peligroso"* no sugiere nada.
 *
 * Que *"agresivo"* apunte a `NO_IR_SOLO` es a propósito y el catálogo lo
 * explica: *"fue grosero o agresivo"* es el resultado de UNA visita y vive en
 * `TipoVisitaCatalogo`; lo que es propiedad permanente del domicilio es
 * `NO_IR_SOLO`. Si el cobrador lo escribió en la ficha, está hablando del
 * domicilio.
 *
 * ## La compuerta de compilación
 *
 * [vocabularioDe] es un `when` exhaustivo **sin `else`**, del mismo molde que
 * [etiquetaDe]: agregar un valor a [SenalDeFicha] no compila hasta contestar
 * con qué palabras se reconoce. `terminos()` vacío es una respuesta válida
 * —"esta señal no se deduce de texto"— pero hay que escribirla; un `else`
 * habría convertido esa decisión en un silencio.
 */
object SugerenciasDeLaNota {

    /**
     * Las señales que el vocabulario de [nota] sugiere marcar, **menos** las de
     * [yaMarcadas].
     *
     * [yaMarcadas] es parámetro y no trabajo del llamador a propósito: el
     * contrato útil para la pantalla es *"qué me falta ofrecer"*, no *"qué
     * dice el texto"*. Dejarlo afuera obligaría a cada llamador a recordar la
     * resta, y el día que uno la olvide la pantalla ofrece marcar algo que ya
     * está marcado. Su valor por omisión deja la otra pregunta —la del
     * vocabulario puro— igual de contestable, que es como la prueba el test.
     *
     * `null`, vacío o en blanco devuelven `emptySet()`: no hay texto del que
     * deducir nada. El conjunto sale en el orden de
     * [SenalDeFicha.EN_ORDEN_DE_PANTALLA] —advertencias primero— por comodidad
     * de quien lo pinta; **el orden no es contrato**, la igualdad de conjuntos
     * sí.
     */
    fun para(nota: String?, yaMarcadas: Set<SenalDeFicha> = emptySet()): Set<SenalDeFicha> {
        val clausulas = clausulasDe(nota)
        if (clausulas.isEmpty()) return emptySet()
        return SenalDeFicha.EN_ORDEN_DE_PANTALLA.filterTo(LinkedHashSet<SenalDeFicha>()) { senal ->
            senal !in yaMarcadas && loDiceLaNota(clausulas, VOCABULARIO.getValue(senal))
        }
    }

    /**
     * Cuántas palabras hacia atrás alcanza un negador. Ver "La negación" en el
     * KDoc de la clase: es un primer corte sin calibrar, no una medición.
     */
    private const val VENTANA_DE_NEGACION: Int = 5

    /**
     * Las palabras que anulan un término que viene después. Lista cerrada y
     * corta: cada una se escribió porque aparece pegada a un término del
     * catálogo (*"sin perro"*, *"nunca está en la tarde"*), no por completitud
     * gramatical. Un negador de más apaga sugerencias buenas.
     */
    private val NEGADORES: Set<String> = setOf(
        "no",
        "ni",
        "sin",
        "nunca",
        "jamas",
        "tampoco",
        "ningun",
        "ninguna",
        "ninguno",
        "nadie",
        "nada"
    )

    /**
     * Las tres expresiones van **antes** de [VOCABULARIO] y no al final del
     * archivo: las propiedades de un `object` se inicializan en orden de
     * declaración y [VOCABULARIO] las usa al arrancar (vía [terminos]). Si
     * alguien las "ordena" hacia abajo valen `null` en ese momento y la
     * primera nota que se escriba revienta. No es estilo: es la única
     * dependencia de orden del archivo.
     */
    private val MARCAS_DE_ACENTO = "\\p{M}".toRegex()

    /**
     * Lo que cierra una idea. El guion **no** está: *"pit-bull"* es una palabra
     * partida, no dos ideas, y cortar ahí rompería el término de dos palabras.
     */
    private val FIN_DE_CLAUSULA = "[\\n\\r.,;:/|!¡?¿()]+".toRegex()

    private val NO_ES_PALABRA = "[^\\p{L}\\p{N}]+".toRegex()

    /**
     * El vocabulario de cada señal, ya normalizado y partido, calculado una vez.
     *
     * Se deriva de [vocabularioDe] en vez de ser un `mapOf` literal para que la
     * compuerta siga siendo el `when` exhaustivo: un mapa literal admite un
     * enum nuevo sin entrada y falla en tiempo de ejecución, que es justo el
     * fallo silencioso que el catálogo evita en todas sus otras compuertas.
     */
    private val VOCABULARIO: Map<SenalDeFicha, List<Termino>> =
        SenalDeFicha.entries.associateWith { vocabularioDe(it) }

    /**
     * Las palabras con las que un cobrador mexicano nombra cada señal.
     *
     * `when` exhaustivo sin `else` — ver "La compuerta de compilación". Las
     * frases se escriben **con acentos y en español de verdad** porque
     * [terminos] las normaliza al arrancar: la tabla se lee, no se descifra.
     */
    @Suppress(
        "LongMethod"
    ) // es una TABLA, no logica: partirla en seis metodos esconderia la mitad del vocabulario.
    private fun vocabularioDe(senal: SenalDeFicha): List<Termino> = when (senal) {
        // Ya negado a propósito: la señal es una prohibición y la heurística de
        // negación invertiría un "ir solo" suelto. Ver el KDoc de la clase.
        SenalDeFicha.NO_IR_SOLO -> terminos(
            "no ir solo",
            "no ir sola",
            "nunca ir solo",
            "nunca ir sola",
            "no entrar solo",
            "no entrar sola",
            "ir acompañado",
            "ir acompañada",
            "acompañado",
            "acompañada",
            "peligroso",
            "peligrosa",
            "peligro",
            "riesgoso",
            "riesgosa",
            "inseguro",
            "insegura",
            "agresivo",
            "agresiva",
            "amenazó",
            "amenaza",
            "amenazas",
            "armado",
            "zona roja"
        )
        // Razas y "muerde" además del diminutivo: el cobrador escribe lo que ve
        // desde la banqueta, y "hay un pitbull" no lleva la palabra perro.
        SenalDeFicha.HAY_PERRO -> terminos(
            "perro",
            "perros",
            "perra",
            "perras",
            "perrito",
            "perritos",
            "perrita",
            "perritas",
            "pitbull",
            "pit bull",
            "rottweiler",
            "doberman",
            "muerde",
            "muerden"
        )
        // Sin "mañana" suelto (es un día, no una hora). "am" sí: cae entera en
        // esta ventana, a diferencia de "pm".
        SenalDeFicha.ESTA_EN_LA_MANANA -> terminos(
            "en la mañana",
            "por la mañana",
            "de mañana",
            "en las mañanas",
            "por las mañanas",
            "mañanas",
            "temprano",
            "tempranito",
            "am"
        )
        // Sin "tarde" suelto: en cobranza eso es retraso, no hora del día.
        SenalDeFicha.ESTA_EN_LA_TARDE -> terminos(
            "en la tarde",
            "por la tarde",
            "de tarde",
            "en las tardes",
            "por las tardes",
            "tardes",
            "después de comer",
            "saliendo de comer",
            "después de la comida"
        )
        // "noche" suelto sí entra: no tiene falso amigo. "anoche" es otra
        // palabra y la coincidencia por palabra completa ya la excluye.
        SenalDeFicha.ESTA_EN_LA_NOCHE -> terminos(
            "en la noche",
            "por la noche",
            "de noche",
            "en las noches",
            "por las noches",
            "noche",
            "noches",
            "nocturno",
            "nocturna"
        )
        // Parentescos y oficios, nunca "señor/señora": eso nombra al titular.
        SenalDeFicha.ATIENDE_OTRA_PERSONA -> terminos(
            "suegra",
            "suegro",
            "nuera",
            "yerno",
            "cuñada",
            "cuñado",
            "hermana",
            "hermano",
            "hija",
            "hijo",
            "nieta",
            "nieto",
            "tía",
            "tío",
            "sobrina",
            "sobrino",
            "esposa",
            "esposo",
            "mamá",
            "papá",
            "vecina",
            "vecino",
            "muchacha",
            "atiende otra persona",
            "otra persona",
            "no es el titular"
        )
    }

    /** Normaliza y parte cada frase del vocabulario. Una vez, al arrancar. */
    private fun terminos(vararg frases: String): List<Termino> =
        frases.map { palabrasDe(normaliza(it)) }

    /**
     * ¿Alguna cláusula contiene algún término sin negar?
     *
     * Basta **una** aparición limpia: *"no hay perro / bueno sí hay perro"* se
     * sugiere. Exigir que todas las apariciones estén limpias castigaría al
     * cobrador que se corrige a sí mismo.
     */
    private fun loDiceLaNota(clausulas: List<List<String>>, vocabulario: List<Termino>): Boolean =
        clausulas.any { palabras ->
            vocabulario.any { termino -> apareceSinNegar(palabras, termino) }
        }

    /** ¿[termino] aparece en [palabras] sin un negador dentro de la ventana? */
    private fun apareceSinNegar(palabras: List<String>, termino: Termino): Boolean {
        if (termino.isEmpty() || termino.size > palabras.size) return false
        for (inicio in 0..palabras.size - termino.size) {
            if (!empiezaEn(palabras, inicio, termino)) continue
            val desde = maxOf(0, inicio - VENTANA_DE_NEGACION)
            val negado = (desde until inicio).any { palabras[it] in NEGADORES }
            if (!negado) return true
        }
        return false
    }

    /** ¿La secuencia [termino] empieza exactamente en [inicio]? */
    private fun empiezaEn(palabras: List<String>, inicio: Int, termino: Termino): Boolean =
        termino.indices.all { palabras[inicio + it] == termino[it] }

    /**
     * La nota partida en cláusulas, cada una ya en palabras normalizadas.
     *
     * La cláusula es la unidad de negación (ver el KDoc de la clase). Los
     * saltos de línea cuentan como corte porque
     * [com.example.msp_app.feature.pagos.domain.model.FichaDelCliente] ya
     * documenta que el cobrador escribe listas: *"trabaja de noche / atiende la
     * suegra"* son dos ideas, no una.
     */
    private fun clausulasDe(nota: String?): List<List<String>> {
        val texto = nota ?: return emptyList()
        return normaliza(texto).split(FIN_DE_CLAUSULA)
            .map { palabrasDe(it) }
            .filter { it.isNotEmpty() }
    }

    /** Minúsculas y sin diacríticos. Ver "Normalización" en el KDoc. */
    private fun normaliza(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
            .replace(MARCAS_DE_ACENTO, "")

    /** Todo lo que no es letra ni dígito separa palabras. */
    private fun palabrasDe(texto: String): List<String> =
        texto.split(NO_ES_PALABRA).filter { it.isNotEmpty() }
}
