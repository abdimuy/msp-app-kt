package com.example.msp_app.feature.pagos.domain.model

import java.time.Instant
import java.time.LocalTime

/**
 * Dónde empieza la jornada del cobrador. Las tres ventanas de abajo parten
 * `[JORNADA_ABRE, JORNADA_CIERRA)` sin hueco y sin traslape, y eso es lo que
 * [SenalDeFicha.enLaVentanaDe] necesita para poder clasificar una hora.
 *
 * **Los cortes son un primer corte, no una medición.** No hay en este repo
 * ninguna hora de visita con la que calibrarlos, y no se inventó una: lo que
 * queda fijado por prueba es la INVARIANTE (partición total, sin hueco ni
 * traslape), no estos números. Recalibrarlos con horas reales es trabajo
 * pendiente y ningún test los da por buenos.
 */
private val JORNADA_ABRE: LocalTime = LocalTime.of(8, 0)

/** Corte mañana/tarde. Ver la advertencia de [JORNADA_ABRE]. */
private val MEDIODIA: LocalTime = LocalTime.of(13, 0)

/** Corte tarde/noche. Ver la advertencia de [JORNADA_ABRE]. */
private val ANOCHECE: LocalTime = LocalTime.of(18, 0)

/** Dónde termina la jornada del cobrador. Ver la advertencia de [JORNADA_ABRE]. */
private val JORNADA_CIERRA: LocalTime = LocalTime.of(21, 0)

/**
 * Un tramo del día, **medio abierto**: `[desde, hastaExclusivo)`.
 *
 * Medio abierto y no cerrado porque con dos extremos inclusivos las 13:00
 * caerían a la vez en la mañana y en la tarde, y cualquier conteo por ventana
 * sumaría la misma visita dos veces.
 */
data class RangoDelDia(val desde: LocalTime, val hastaExclusivo: LocalTime) {

    init {
        require(desde < hastaExclusivo) {
            "rango del día vacío o invertido: $desde..$hastaExclusivo"
        }
    }

    /** `true` si [hora] cae dentro — `desde` incluido, `hastaExclusivo` no. */
    fun contiene(hora: LocalTime): Boolean = hora >= desde && hora < hastaExclusivo
}

/**
 * Cuánto tiene que gritar una señal en pantalla.
 *
 * Es la SEGUNDA propiedad del enum, y existe por la misma razón que la primera
 * (precedente de la Task 21 con `escalaLaHoja`): **un valor nuevo no compila
 * hasta contestar cómo se pinta**. La diferencia con [RangoDelDia] es que ésta
 * sí tiene consumidor hoy, y en dos lugares —
 * [com.example.msp_app.feature.pagos.ui.components.AfordanteDeLaFicha] y los
 * chips de la ficha—, así que no es una promesa: es la regla que la pantalla
 * ejecuta.
 */
enum class PesoDeLaSenal {
    /** Contexto útil. Se pinta en el color de marca, junto a las demás. */
    INFORMA,

    /**
     * **Riesgo para quien va a tocar la puerta.** Se pinta en el color de
     * peligro, va primero entre los chips y **sube a la barra superior**, que
     * es lo único de la pantalla que se ve siempre sin desplazar.
     *
     * Una advertencia que hay que buscar no es una advertencia.
     */
    ADVIERTE
}

/**
 * **El catálogo cerrado de la ficha.** Seis valores, y la razón de que sean
 * seis es una sola pregunta: *¿esto lo lee código, o solo un humano?*
 *
 * Hay **dos clases de lector**, y las dos son código:
 *
 * 1. **La pantalla**, que ya existe: una señal que la UI pinta distinto —un
 *    chip de peligro, la pastilla de la barra— ya tiene consumidor hoy.
 *    Es [peso] quien lo decide.
 * 2. **El conteo por ventana horaria (BTTC)**, que **todavía no existe**: no
 *    hay una sola línea que lo implemente, ni en esta app ni en `msp-api`
 *    (verificado por grep en ambos repos). [ventana] existe para que el día que
 *    se construya encuentre el dato ya estructurado — y para que **hoy** un
 *    valor nuevo de horario no compile sin declarar de qué hora habla.
 *
 * Lo que NO tiene ninguno de los dos lectores es nota libre.
 *
 * ## Qué NO entró, y por qué
 *
 * - **Referencias de la casa** (*"casa azul, portón negro"*): son señas que solo
 *   lee un humano y la pantalla no puede pintar de otra forma. Un catálogo de
 *   fachadas además sería infinito por construcción.
 * - **Quién atiende, por su nombre** (*"doña Remedios"*): el dato accionable es
 *   que **no es el titular**, y eso ya es [ATIENDE_OTRA_PERSONA]. El nombre y el
 *   parentesco los lee un humano.
 * - **Fin de semana / días concretos:** es otro eje (el día, no la hora) metido
 *   en el mismo conjunto plano, y no hay evidencia de que la ruta corra en fin
 *   de semana. Un valor que el cobrador no puede accionar es el que nadie elige,
 *   y un valor que nadie elige vacía de significado a los que sí.
 * - **Calidad de dato** (`teléfono equivocado`, `cambió domicilio`) — el
 *   `DATA_QUALITY` de kollect: allá existen porque su backend los sincroniza y
 *   alguien los procesa del otro lado. Aquí no hay endpoint de ficha ni
 *   pantalla que los use: sería copiar la forma sin copiar el lector.
 * - **`buen pagador` / `no va a pagar`** — el `OPPORTUNITY`/`INTENT_ABILITY` de
 *   kollect: es *propensity*, y el catálogo de ocho estados ya lo deriva de los
 *   pagos REALES. Escribirlo a mano invita a ordenar la ruta por una opinión de
 *   hace ocho meses en vez de por la medición.
 * - **"fue grosero", "se negó"**: son **resultados de UNA visita**, no
 *   propiedades del domicilio, y ya tienen dueño en `TipoVisitaCatalogo`. Lo que
 *   sí es propiedad del domicilio —y por eso sí entró— es [NO_IR_SOLO].
 *
 * **El catálogo nace corto y crece con evidencia.** Kollect tiene 16 valores
 * porque su backend los consume; un catálogo largo aquí se llenaría de valores
 * que nadie elige, y entonces el dato deja de significar algo — que es peor que
 * no tenerlo, porque después alguien lo usa para ordenar la ruta.
 *
 * ## El guardrail: agregar un valor no compila
 *
 * Molde `SignalType`/`SignalLabels` de kollect, con **tres** compuertas:
 * [ventana] (¿qué dice de la hora?), [peso] (¿cómo se pinta?) y
 * [com.example.msp_app.feature.pagos.domain.etiquetaDe], un `when` exhaustivo
 * sin `else` (¿cómo se llama en español?). Ninguna admite un valor nuevo sin
 * respuesta.
 *
 * `SignalFamily`, la tercera pieza del molde, **no se copió**: allá agrupa 16
 * valores en 4 cubetas; aquí sería derivable y no la consumiría nadie, y un
 * `enum` que nadie lee es la misma clase de fallo silencioso que un catálogo
 * largo.
 *
 * El literal que se persiste en `cliente_ficha_senales.SENAL` es [Enum.name].
 * Renombrar una constante **desconecta** la ficha ya guardada de su valor.
 */
enum class SenalDeFicha(val ventana: RangoDelDia?, val peso: PesoDeLaSenal) {

    /**
     * **Riesgo para quien toca la puerta: no se va solo a este domicilio.**
     *
     * Es propiedad del DOMICILIO y persistente —de ahí que sea ficha y no
     * resultado de visita—, y cambia **quién** va y **a qué hora**, que es
     * justamente lo que la ruta decide. Su lector de hoy es la pantalla: sube a
     * la barra superior en color de peligro, donde se ve sin desplazar.
     *
     * No se confunde con *"fue grosero o agresivo"* de `TipoVisitaCatalogo`:
     * eso es lo que pasó UNA vez; esto es la instrucción permanente.
     */
    NO_IR_SOLO(null, PesoDeLaSenal.ADVIERTE),

    /**
     * **Hay perro.** La otra advertencia que un cobrador dice todos los días, y
     * es distinta de [NO_IR_SOLO] porque **la acción es otra**: no cambia quién
     * va ni cuándo, cambia qué se hace **en la puerta** — no abrir el portón,
     * llamar desde la banqueta.
     *
     * Dos valores y no un `ADVERTENCIA` genérico: una advertencia genérica se
     * pintaría roja pero no diría qué hacer distinto, y obligaría a leer la
     * nota — que es exactamente lo que no se puede exigir de una advertencia.
     */
    HAY_PERRO(null, PesoDeLaSenal.ADVIERTE),

    /** Se le encuentra temprano — el turno de mañana de la ruta. */
    ESTA_EN_LA_MANANA(RangoDelDia(JORNADA_ABRE, MEDIODIA), PesoDeLaSenal.INFORMA),

    /** Se le encuentra después de comer. */
    ESTA_EN_LA_TARDE(RangoDelDia(MEDIODIA, ANOCHECE), PesoDeLaSenal.INFORMA),

    /**
     * Se le encuentra ya de noche — el caso *"trabaja de noche"* del plan visto
     * desde el lado que le sirve al cobrador: no *cuándo trabaja*, sino
     * **cuándo se le encuentra**, que es lo único que mueve la ruta.
     */
    ESTA_EN_LA_NOCHE(RangoDelDia(ANOCHECE, JORNADA_CIERRA), PesoDeLaSenal.INFORMA),

    /**
     * *"Atiende la suegra"*: quien abre la puerta **no es el titular**.
     *
     * Es la mitad que vuelve honesto a un conteo de contactos: una visita en la
     * que solo se habló con un tercero no es un *Right Party Contact*, así que
     * no puede contar como que "esa ventana funciona". Ese conteo todavía no
     * existe (ver el KDoc de la clase); su lector de hoy es la pantalla.
     */
    ATIENDE_OTRA_PERSONA(null, PesoDeLaSenal.INFORMA);

    companion object {

        /**
         * La señal cuyo literal es [literal], o `null` si este build no la
         * conoce — el valor retirado de un catálogo que *nace corto y crece*.
         *
         * Devolver `null` en vez de lanzar es deliberado: una ficha vieja con
         * un literal que ya no existe **no puede** tumbar el detalle del
         * cliente. Quien llama lo reporta por telemetría y sigue.
         */
        fun deLiteral(literal: String): SenalDeFicha? = entries.firstOrNull { it.name == literal }

        /**
         * La señal de horario en cuya ventana cae [hora], o `null` si [hora]
         * queda fuera de la jornada.
         *
         * **Hoy su único llamador es el test de partición** —
         * `CatalogoDeLaFichaTest`—, y eso es una afirmación sobre el estado del
         * repo, no una promesa: el conteo por ventana horaria que la va a
         * consumir no está escrito en ningún lado todavía. Queda porque es la
         * forma ejecutable de la invariante que [ventana] declara: si alguien
         * abriera un hueco entre dos tramos, una hora de trabajo empezaría a
         * devolver `null` aquí y el test lo dice en el minuto exacto. Sin ella,
         * esa lógica viviría solo en el test, que es peor.
         */
        fun enLaVentanaDe(hora: LocalTime): SenalDeFicha? =
            entries.firstOrNull { it.ventana?.contiene(hora) == true }

        /** La primera hora de la jornada que las ventanas cubren. */
        val JORNADA: RangoDelDia = RangoDelDia(JORNADA_ABRE, JORNADA_CIERRA)

        /**
         * El catálogo en el orden en que se pinta: **las advertencias primero**.
         *
         * Es una función del [peso] y no el orden de declaración del `enum`,
         * porque el orden de declaración es también el de los literales
         * persistidos y no debe cargar con decisiones de pantalla.
         */
        val EN_ORDEN_DE_PANTALLA: List<SenalDeFicha> =
            entries.sortedBy { if (it.peso == PesoDeLaSenal.ADVIERTE) 0 else 1 }
    }
}

/**
 * La ficha del cliente: **conocimiento persistente**, distinto del resultado de
 * una visita. Es lo que hace mejor la **próxima** visita.
 *
 * Dos campos con trabajos distintos y dos cardinalidades distintas — por eso
 * son dos tablas, y por eso son dos campos aquí:
 *
 * - [senales] es el catálogo cerrado, 0..N. **Es lo único que lee código.**
 * - [nota] es texto libre, 0..1, para el humano que llega mañana.
 *
 * `null` en [nota] y `emptySet()` en [senales] son estados normales y
 * combinables: hay clientes con señales y sin nota, y al revés.
 */
data class FichaDelCliente(
    val senales: Set<SenalDeFicha> = emptySet(),
    val nota: String? = null,
    /** Última edición, o `null` si la ficha nunca se escribió. */
    val actualizada: Instant? = null
) {

    /** Nadie ha escrito nada de este cliente todavía. */
    val vacia: Boolean get() = senales.isEmpty() && nota == null

    /**
     * Las advertencias marcadas, en el orden en que se pintan. Vacío es el caso
     * normal.
     */
    val advertencias: List<SenalDeFicha>
        get() = SenalDeFicha.EN_ORDEN_DE_PANTALLA
            .filter { it in senales && it.peso == PesoDeLaSenal.ADVIERTE }

    /** Las señales marcadas, advertencias primero. */
    val enOrden: List<SenalDeFicha>
        get() = SenalDeFicha.EN_ORDEN_DE_PANTALLA.filter { it in senales }

    companion object {

        /**
         * Tope de la nota libre, en caracteres.
         *
         * 500 es la ficha, no la bitácora: lo que pasó en una visita ya tiene
         * su lugar (`Visit.NOTA`, y la bitácora de "últimos contactos" la
         * pinta). Un tope bajo obliga a que esto sea *lo que hay que saber* y
         * no un diario; y en un teléfono de gama baja un texto sin tope se
         * vuelve una tarjeta que empuja media pantalla hacia abajo.
         */
        const val NOTA_MAX: Int = 500

        /**
         * Corta a [NOTA_MAX] **sin partir un par suplente**.
         *
         * Es la ÚNICA forma de recortar la nota en todo el código, y por eso es
         * pública: el campo de texto de la pantalla la llama en cada tecla —si
         * cortara por su cuenta con `take(NOTA_MAX)`, el emoji del borde
         * llegaría partido y esta guarda no correría nunca. Fue exactamente ese
         * defecto: la rama existía y **ningún camino real la alcanzaba**.
         *
         * Partir un emoji deja medio carácter que ninguna fuente puede pintar,
         * así que si el corte cae entre las dos mitades se descarta la que
         * quedó suelta.
         */
        fun recorta(texto: String): String {
            if (texto.length <= NOTA_MAX) return texto
            val cortado = texto.take(NOTA_MAX)
            return if (cortado.last().isHighSurrogate()) cortado.dropLast(1) else cortado
        }

        /**
         * Normaliza lo que el cobrador tecleó: recorta los extremos, **conserva
         * los saltos de línea de adentro** —el cobrador escribe listas: *"trabaja
         * de noche / atiende la suegra"*— y corta con [recorta].
         *
         * Devuelve `null` cuando no queda nada. **Vacío y nulo no son dos
         * estados:** un `""` guardado se leería como "hay nota" y pintaría una
         * tarjeta en blanco, así que el vacío se normaliza a la ausencia, que es
         * lo que la columna `NOTA` nullable ya sabe representar.
         */
        fun limpia(texto: String?): String? {
            val recortado = texto?.trim() ?: return null
            return recorta(recortado).trimEnd().ifBlank { null }
        }
    }
}
