package com.example.msp_app.feature.pagos.domain.model

import java.time.Instant
import java.time.LocalTime

/**
 * Dónde empieza la jornada del cobrador. Las tres ventanas de abajo parten
 * `[JORNADA_ABRE, JORNADA_CIERRA)` sin hueco y sin traslape, y eso es lo que
 * [SenalDeFicha.enLaVentanaDe] necesita para poder clasificar una hora.
 */
private val JORNADA_ABRE: LocalTime = LocalTime.of(8, 0)

/** Corte mañana/tarde. */
private val MEDIODIA: LocalTime = LocalTime.of(13, 0)

/** Corte tarde/noche. */
private val ANOCHECE: LocalTime = LocalTime.of(18, 0)

/** Dónde termina la jornada del cobrador. */
private val JORNADA_CIERRA: LocalTime = LocalTime.of(21, 0)

/**
 * Un tramo del día, **medio abierto**: `[desde, hastaExclusivo)`.
 *
 * Medio abierto y no cerrado por la misma razón por la que lo es el rango de
 * consulta de pagos: con dos extremos inclusivos, las 13:00 caerían a la vez en
 * la mañana y en la tarde, y el conteo del BTTC sumaría la misma visita dos
 * veces.
 */
data class RangoDelDia(val desde: LocalTime, val hastaExclusivo: LocalTime) {

    init {
        require(
            desde < hastaExclusivo
        ) { "rango del día vacío o invertido: $desde..$hastaExclusivo" }
    }

    /** `true` si [hora] cae dentro — `desde` incluido, `hastaExclusivo` no. */
    fun contiene(hora: LocalTime): Boolean = hora >= desde && hora < hastaExclusivo
}

/**
 * **El catálogo cerrado de la ficha.** Cuatro valores, y la razón de que sean
 * cuatro es una sola pregunta: *¿la máquina lo va a consultar?*
 *
 * El plan lo dice sin rodeos — el catálogo es **lo único que puede alimentar el
 * BTTC**, porque el texto libre no se consulta. Entonces un valor de catálogo
 * es la **promesa** de que alguien lo va a leer con código; si no se ve quién,
 * es nota libre. El BTTC (§10 del expediente) sale de contar el *Right Party
 * Contact* por ventana horaria — *"son cuentas, no ML"* —, así que las dos
 * únicas preguntas que la máquina le hace hoy a la ficha son **cuándo se abre
 * la puerta** y **quién la abre**. Todo lo demás lo lee un humano, y para eso
 * está la nota.
 *
 * ## Qué NO entró, y por qué
 *
 * - **Referencias de la casa** (*"casa azul, portón negro"*): nadie las
 *   consulta con código; son señas para el humano que llega mañana.
 * - **Advertencias** (*"hay perro"*, *"fue grosero"*): no hay consumidor en
 *   código. La agresión y la negativa además **ya tienen dueño** —
 *   `TipoVisitaCatalogo` y el catálogo de ocho estados—, y un segundo lugar
 *   donde vivan sería una segunda fuente de verdad sobre el mismo hecho.
 * - **Fin de semana / días concretos:** es otro eje (el día, no la hora) y no
 *   hay evidencia de que la ruta corra en fin de semana. Un valor que el
 *   cobrador no puede accionar es exactamente el que nadie elige, y un valor
 *   que nadie elige vacía de significado a los que sí.
 *
 * **El catálogo nace corto y crece con evidencia.** Un catálogo largo se llena
 * de valores muertos, el dato deja de significar algo, y después alguien lo usa
 * para ordenar la ruta creyendo que significa algo.
 *
 * ## El guardrail: agregar un valor no compila
 *
 * Molde `SignalType`/`SignalLabels` de kollect. Dos compuertas, no una:
 *
 * 1. [ventana] es **propiedad del enum**, no un `if` (precedente de la Task 21
 *    con `escalaLaHoja`): un valor nuevo **no compila** hasta contestar *"¿qué
 *    dice esto sobre la hora?"*, que es justo el dato que el BTTC va a leer.
 *    `null` es una respuesta legítima y explícita: *"no dice nada de la hora"*.
 * 2. [com.example.msp_app.feature.pagos.domain.etiquetaDe] es un `when`
 *    exhaustivo sin `else`: un valor nuevo tampoco compila hasta tener etiqueta
 *    en español, así que no puede llegar a la pantalla sin nombre.
 *
 * El literal que se persiste en `cliente_ficha_senales.SENAL` es
 * [Enum.name]. Renombrar una constante **desconecta** la ficha ya guardada de
 * su valor: el esquema conserva el texto (igual que `TIPO_VISITA`) y
 * [deLiteral] lo devuelve como desconocido, pero la señal deja de leerse.
 */
enum class SenalDeFicha(val ventana: RangoDelDia?) {

    /** Se le encuentra temprano — el turno de mañana de la ruta. */
    ESTA_EN_LA_MANANA(RangoDelDia(JORNADA_ABRE, MEDIODIA)),

    /** Se le encuentra después de comer. */
    ESTA_EN_LA_TARDE(RangoDelDia(MEDIODIA, ANOCHECE)),

    /**
     * Se le encuentra ya de noche — el caso *"trabaja de noche"* del plan visto
     * desde el lado que le sirve al cobrador: no *cuándo trabaja*, sino
     * **cuándo se le encuentra**, que es lo único que mueve la ruta.
     */
    ESTA_EN_LA_NOCHE(RangoDelDia(ANOCHECE, JORNADA_CIERRA)),

    /**
     * *"Atiende la suegra"*: quien abre la puerta **no es el titular**.
     *
     * Su lector en código es el BTTC, y es la mitad del par que lo hace
     * honesto: una visita en la que solo se habló con un tercero **no es un
     * Right Party Contact** y no puede contar como que "esa ventana funciona".
     * Sin esta señal el conteo aprendería *"las tardes sirven"* de visitas en
     * las que nunca se habló con el deudor.
     *
     * **Quién** atiende va en la nota: el nombre y el parentesco los lee un
     * humano, no la máquina.
     */
    ATIENDE_OTRA_PERSONA(null);

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
         * Es la función que el BTTC va a usar para meter cada contacto en su
         * cubeta, y es también el control positivo de que las tres ventanas
         * **parten** la jornada: si alguien abriera un hueco entre dos, una hora
         * de trabajo empezaría a devolver `null` aquí.
         */
        fun enLaVentanaDe(hora: LocalTime): SenalDeFicha? =
            entries.firstOrNull { it.ventana?.contiene(hora) == true }

        /** La primera hora de la jornada que las ventanas cubren. */
        val JORNADA: RangoDelDia = RangoDelDia(JORNADA_ABRE, JORNADA_CIERRA)
    }
}

/**
 * La ficha del cliente: **conocimiento persistente**, distinto del resultado de
 * una visita. Es lo que hace mejor la **próxima** visita.
 *
 * Dos campos con trabajos distintos y dos cardinalidades distintas — por eso
 * son dos tablas, y por eso son dos campos aquí:
 *
 * - [senales] es el catálogo cerrado, 0..N. **Es lo único que la máquina
 *   consulta.**
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
         * Normaliza lo que el cobrador tecleó: recorta los extremos, **conserva
         * los saltos de línea de adentro** —el cobrador escribe listas: *"trabaja
         * de noche / atiende la suegra"*— y corta a [NOTA_MAX].
         *
         * Devuelve `null` cuando no queda nada. **Vacío y nulo no son dos
         * estados:** un `""` guardado se leería como "hay nota" y pintaría una
         * tarjeta en blanco, así que el vacío se normaliza a la ausencia, que es
         * lo que la columna `NOTA` nullable ya sabe representar.
         *
         * El corte respeta los pares suplentes: partir un emoji a la mitad deja
         * medio carácter que ninguna fuente puede pintar, así que si el corte
         * cae entre las dos mitades se descarta la que quedó suelta.
         */
        fun limpia(texto: String?): String? {
            val recortado = texto?.trim() ?: return null
            val cortado = if (recortado.length <= NOTA_MAX) {
                recortado
            } else {
                recortado.take(NOTA_MAX).sinSuplenteSuelto()
            }
            return cortado.trimEnd().ifBlank { null }
        }

        private fun String.sinSuplenteSuelto(): String =
            if (isNotEmpty() && last().isHighSurrogate()) dropLast(1) else this
    }
}
