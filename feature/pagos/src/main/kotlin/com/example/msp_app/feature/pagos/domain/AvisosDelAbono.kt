package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Cuánta fricción merece este monto. **La rareza decide el nivel**: un aviso
 * que sale seguido se aprende a ignorar, y el que se aprende a ignorar ya no
 * protege de nada.
 *
 * El orden de declaración **es** el orden de gravedad, y de eso depende
 * [AvisosDelAbono.evaluar] para escoger el nivel ganador cuando hay varias
 * señales. Está medido en `AvisosDelAbonoTest`: reordenar estas entradas
 * cambia el comportamiento, no sólo la lectura.
 */
enum class NivelDeAviso {
    /** El monto es normal. La pantalla no dice nada. */
    NINGUNO,

    /**
     * **Nivel 0.** No se puede registrar. Lo decide [SeguridadDelAbono], que es
     * quien manda sobre el sobrepago; aquí sólo se refleja para que el `when`
     * de la pantalla sea total.
     */
    BLOQUEO,

    /**
     * **Nivel 1.** Nota al pie, sin fricción: menos de lo esperado. Es el 24 %
     * de los abonos de la ruta — normal y legítimo. El texto lo pinta la banda
     * ámbar que ya existe, ver [AvisoDelMonto.mensajes].
     */
    NOTA,

    /** **Nivel 2.** Menos del 1 % de los abonos: confirmar con un toque extra. */
    CONFIRMAR,

    /**
     * **Nivel 3.** Menos del 0.1 % de los abonos: hay que **teclear el monto
     * otra vez**.
     *
     * ## Por qué teclear y no un botón rojo
     *
     * Un cero de más **no es una decisión, es un resbalón**. Un botón rojo se
     * confirma igual de rápido que uno gris porque el dedo ya iba en camino: el
     * gesto ya estaba lanzado cuando apareció el color, y cambiar el color no
     * cambia el gesto. Teclear el monto de nuevo sí atrapa el resbalón, porque
     * para que pase habría que teclear el cero de más **dos veces**, con la
     * cifra equivocada a la vista.
     *
     * El costo está acotado por la misma medición que justifica el nivel: esto
     * se dispara en el **0.08 %** de los abonos de la ruta. Cinco de 6,165.
     */
    TECLEAR
}

/**
 * Qué se le notó a este monto. Cada señal declara su nivel, y ése es el único
 * lugar donde la asignación vive: la pantalla nunca decide la gravedad de nada.
 *
 * Las cifras entre paréntesis son las medidas sobre los 6,165 abonos reales de
 * la ruta (tabla `Payment` del teléfono).
 */
enum class SenalDelMonto(val nivel: NivelDeAviso) {

    /**
     * El bloqueo duro de [SeguridadDelAbono], reflejado. No trae mensaje
     * propio: la banda roja del sobrepago ya lo dice con el máximo registrable
     * en la mano, y dos textos para un solo hecho es ruido.
     */
    NO_SE_PUEDE_REGISTRAR(NivelDeAviso.BLOQUEO),

    /**
     * Menos de lo esperado hoy (**24 %**). Tampoco trae mensaje propio: lo
     * pinta [RarezaDelAbono.ABAJO_DE_LO_ESPERADO] con sus dos cifras, y esa
     * banda no se toca.
     */
    ABAJO_DE_LO_ESPERADO(NivelDeAviso.NOTA),

    /**
     * No es múltiplo de 50. **El 99.8 % de los abonos sí lo son**: de 6,165,
     * sólo dos no lo fueron.
     */
    NO_ES_MULTIPLO_DE_CINCUENTA(NivelDeAviso.CONFIRMAR),

    /** De 3 a 6 cuotas de golpe (**0.70 %**). */
    DE_TRES_A_SEIS_CUOTAS(NivelDeAviso.CONFIRMAR),

    /** Más del triple de lo que este cliente suele dar. */
    MAS_DEL_TRIPLE_DE_LO_HABITUAL(NivelDeAviso.CONFIRMAR),

    /** Más de 6 cuotas de golpe (**0.08 %**, cinco abonos). */
    MAS_DE_SEIS_CUOTAS(NivelDeAviso.TECLEAR),

    /** Diez veces o más lo que este cliente suele dar. */
    DIEZ_VECES_LO_HABITUAL(NivelDeAviso.TECLEAR),

    /**
     * Más de 12 cuotas de golpe. **Nunca ha pasado**: el abono más grande de la
     * historia de la ruta es de $1,500, y ni siquiera liquidó —dejó saldo—.
     */
    MAS_DE_DOCE_CUOTAS(NivelDeAviso.TECLEAR)
}

/**
 * El veredicto de fricción: el [nivel] que gana, todas las [senales] que se
 * encendieron y los [mensajes] que la pantalla tiene que decir.
 *
 * [senales] las trae **todas** y [mensajes] sólo las del nivel ganador. No es
 * una inconsistencia: las señales son el hecho medido (sirven para probar y
 * para reportar), los mensajes son lo que el cobrador lee parado en la puerta,
 * y ahí un texto de menor gravedad debajo del grave le quita fuerza al grave.
 * Es la misma regla que la hoja ya aplica con las alertas rojas: **una sola
 * gana**.
 */
data class AvisoDelMonto(
    val nivel: NivelDeAviso,
    val senales: List<SenalDelMonto>,
    val mensajes: List<String>,
    /**
     * **Este monto lo propuso la pantalla.** Un chip sugerido, o el saldo
     * completo (que es liquidar).
     *
     * No es un detalle de presentación: es la razón por la que no hay ninguna
     * señal encendida, y la hoja la necesita para callar también las bandas
     * viejas que no salen de este clasificador. Ver [AvisosDelAbono.evaluar].
     */
    val loPropusoLaPantalla: Boolean = false
) {
    /** ¿Este monto pide una confirmación con más fricción que la normal? */
    val pideFriccionExtra: Boolean
        get() = nivel == NivelDeAviso.CONFIRMAR || nivel == NivelDeAviso.TECLEAR

    /** ¿El paso dos pide **teclear** el monto otra vez? Ver [NivelDeAviso.TECLEAR]. */
    val pideTeclearElMonto: Boolean get() = nivel == NivelDeAviso.TECLEAR

    companion object {
        /** Nada que decir: el monto es normal, o la venta todavía no cargó. */
        val NINGUNO: AvisoDelMonto = AvisoDelMonto(NivelDeAviso.NINGUNO, emptyList(), emptyList())

        /** Nada que decir **porque la pantalla misma ofreció este monto**. */
        val LO_PROPUSO_LA_PANTALLA: AvisoDelMonto = AvisoDelMonto(
            nivel = NivelDeAviso.NINGUNO,
            senales = emptyList(),
            mensajes = emptyList(),
            loPropusoLaPantalla = true
        )
    }
}

/**
 * Los textos de los avisos escalonados, en un solo lugar y fijados por
 * `TextosDelAvisoTest` — mismo molde que `TextosCorreccion` en
 * `:feature:ventaCorreccion`.
 *
 * ## Dicen el HECHO, no un regaño
 *
 * Nada de *"¿estás seguro?"* ni *"monto inusual"*: esas frases no informan, sólo
 * piden que alguien dude sin darle con qué. El cobrador está viendo el billete
 * en la mano y sabe decidir — lo que le falta es el dato, y el dato es "esto son
 * 24 cuotas" o "este cliente suele dar $200".
 *
 * Mayúscula inicial y sin punto final, la convención de texto de usuario del
 * repo (`CLAUDE.md` §3 tal como el dueño la fijó el 2026-09-20).
 */
object TextosDelAviso {

    /** El monto no es múltiplo de 50. El 99.8 % de los abonos de la ruta sí lo es. */
    const val DE_CINCUENTA_EN_CINCUENTA: String = "Los pagos van de 50 en 50"

    /** Más de 12 cuotas de golpe: no ha ocurrido nunca en esta ruta. */
    const val NADIE_HA_PAGADO_TANTO: String = "Nadie en la ruta ha pagado tanto"

    /** "Son 4 cuotas de $250" — cuántas cuotas cubre este monto, y de a cuánto. */
    fun sonCuotas(cuotas: Int, parcialidad: Money): String =
        "Son $cuotas cuotas de ${formatMoneyMxn(parcialidad.amount)}"

    /** "Suele dar $200" — lo que este cliente entrega de costumbre. */
    fun sueleDar(habitual: Money): String = "Suele dar ${formatMoneyMxn(habitual.amount)}"

    /** "Suele dar $200, esto es 30 veces más". */
    fun sueleDarYEstoEs(habitual: Money, veces: Int): String =
        "${sueleDar(habitual)}, esto es $veces veces más"
}

/**
 * **El clasificador de los cuatro niveles de aviso.** Dominio PURO: recibe el
 * monto, la cuota, el saldo y el historial del cliente, y devuelve el nivel con
 * su mensaje. Sin Android, sin Room, sin reloj — se prueba sin pantalla.
 *
 * ## Qué es de aquí y qué no
 *
 * - **Nivel 0 (bloquear)** sigue siendo de [SeguridadDelAbono]. Aquí sólo se
 *   consulta —la MISMA función, `bloqueosDe`— para no evaluar rarezas sobre un
 *   monto que de todos modos no se puede registrar.
 * - **Nivel 1 (nota al pie)** sigue siendo de [RarezaDelAbono.ABAJO_DE_LO_ESPERADO]
 *   y su banda ámbar. Aquí se refleja como señal para que el `when` de la
 *   pantalla sea total, pero no se le inventa un texto nuevo.
 * - **Niveles 2 y 3** son de esta función entera.
 *
 * ## Avisar no es bloquear
 *
 * Ninguno de los cuatro niveles impide registrar. Alguien sí puede pagar $237,
 * y alguien sí puede liquidar de golpe: los dos son legítimos y los dos son
 * raros. Todo lo que cambia entre niveles es **qué tan caro es decir que sí**.
 *
 * ## La app no interroga lo que ella misma propuso
 *
 * **Si el monto coincide exactamente con alguno de los sugeridos que la
 * pantalla está ofreciendo, no se enciende ninguna señal.** Es la regla general,
 * no una exención para liquidar: un cobrador que toca un chip y recibe un
 * interrogatorio aprende dos cosas malas a la vez —que los chips no son de fiar
 * y que los avisos son ruido—, y la segunda se lleva por delante al aviso que sí
 * importaba.
 *
 * El nivel 0 **sigue encendiéndose**: no es un aviso, es una imposibilidad. De
 * todos modos no puede chocar, porque `MontosSugeridos` topa todos los chips en
 * el saldo — un sugerido nunca es un sobrepago.
 *
 * Esto **reemplaza** las dos exenciones a mano que tenía el "de 50 en 50"
 * (liquidar y pagar lo esperado): las dos eran casos particulares de esta regla,
 * porque los dos montos son chips de esta misma fila.
 *
 * ## Los umbrales, y por qué están donde están
 *
 * Todos salen de la medición sobre 6,165 abonos reales (tabla `Payment`), no de
 * una intuición:
 *
 * | Señal | Frecuencia medida | Nivel |
 * |---|---|---|
 * | una cuota | 68 % | — |
 * | menos de una cuota | 24 % | 1 |
 * | no múltiplo de 50 | 0.03 % (2 de 6,165) | 2 |
 * | de 3 a 6 cuotas | 0.70 % | 2 |
 * | de 6 a 12 cuotas | 0.08 % (5 abonos) | 3 |
 * | más de 12 cuotas | **nunca** | 3 |
 *
 * Las fronteras son cerradas por abajo y abiertas por arriba en la dirección
 * que la tabla nombra: `[3, 6]` cuotas es nivel 2, `(6, 12]` es nivel 3 y
 * `(12, ∞)` es nivel 3 con el otro mensaje. Las cinco fronteras exactas —3, 6,
 * 12, múltiplo de 50 y el de al lado— tienen prueba propia.
 */
object AvisosDelAbono {

    /**
     * El aviso de [monto], o [AvisoDelMonto.NINGUNO] si no hay nada que decir.
     *
     * @param monto lo que el cobrador tecleó.
     * @param saldo lo que la venta debe — el techo del bloqueo duro.
     * @param parcialidad la cuota de la venta. En cero apaga los tres umbrales
     *   de cuotas: sin cuota no hay contra qué contar, y contar contra un cero
     *   inventaría un múltiplo infinito.
     * @param esperadoHoy lo que falta de la cuota del periodo abierto.
     * @param historial los abonos que ESTE cliente ya dio. Vacío o corto apaga
     *   los dos umbrales de "lo que suele dar" — ver [AbonoHabitual].
     * @param sugeridos los importes que la pantalla está ofreciendo en sus
     *   chips. Un monto que esté en esta lista no enciende ninguna señal — ver
     *   el KDoc de esta clase.
     */
    fun evaluar(
        monto: Money,
        saldo: Money,
        parcialidad: Money,
        esperadoHoy: Money,
        historial: List<AbonoPrevio>,
        sugeridos: List<Money> = emptyList()
    ): AvisoDelMonto {
        // El bloqueo duro lo decide `SeguridadDelAbono` y se consulta, no se
        // reimplementa: que los dos caminos llamen a la MISMA función es lo que
        // hace imposible que discrepen sobre qué es sobrepago.
        if (SeguridadDelAbono.bloqueosDe(monto, saldo).isNotEmpty()) {
            return AvisoDelMonto(
                nivel = NivelDeAviso.BLOQUEO,
                senales = listOf(SenalDelMonto.NO_SE_PUEDE_REGISTRAR),
                mensajes = emptyList()
            )
        }
        if (loPropusoLaPantalla(monto, saldo, sugeridos)) {
            return AvisoDelMonto.LO_PROPUSO_LA_PANTALLA
        }
        val habitual = AbonoHabitual.de(historial)
        val senales = senalesDe(monto, parcialidad, esperadoHoy, habitual)
        // El nivel ganador es el más grave, y la gravedad es el orden de
        // declaración de `NivelDeAviso` — medido, no supuesto.
        val nivel = senales.maxByOrNull { it.nivel.ordinal }?.nivel ?: NivelDeAviso.NINGUNO
        return AvisoDelMonto(
            nivel = nivel,
            senales = senales,
            mensajes = senales
                .filter { it.nivel == nivel }
                .mapNotNull { mensajeDe(it, monto, parcialidad, habitual) }
        )
    }

    /**
     * ¿Este monto salió de la propia pantalla?
     *
     * Dos maneras, y las dos cuentan:
     *  - **es uno de los chips** que la fila está ofreciendo, o
     *  - **es el saldo completo**, que es liquidar. Va aparte porque cuando la
     *    venta trae oferta de liquidación el chip ofrece MENOS que el saldo
     *    (`$1,290` sobre un saldo de `$1,450`), así que pagar el saldo exacto no
     *    aparece en la fila y sigue siendo la operación que la pantalla
     *    describe.
     *
     * "Pagar justo lo esperado hoy" no necesita su propio caso: cuando existe es
     * SIEMPRE el primer chip de la fila, así que ya entra por la primera vía.
     */
    private fun loPropusoLaPantalla(monto: Money, saldo: Money, sugeridos: List<Money>): Boolean =
        monto == saldo || sugeridos.any { it == monto }

    private fun senalesDe(
        monto: Money,
        parcialidad: Money,
        esperadoHoy: Money,
        habitual: Money?
    ): List<SenalDelMonto> = buildList {
        if (esperadoHoy > Money.ZERO && monto < esperadoHoy) add(SenalDelMonto.ABAJO_DE_LO_ESPERADO)
        if (monto.amount.remainder(CINCUENTA).signum() != 0) {
            add(SenalDelMonto.NO_ES_MULTIPLO_DE_CINCUENTA)
        }
        addAll(senalesDeCuotas(monto, parcialidad))
        addAll(senalesDeCostumbre(monto, habitual))
    }

    private fun senalesDeCuotas(monto: Money, parcialidad: Money): List<SenalDelMonto> {
        if (parcialidad <= Money.ZERO) return emptyList()
        val cuota = parcialidad.amount
        return buildList {
            if (monto.amount >= cuota.multiply(TRES) && monto.amount <= cuota.multiply(SEIS)) {
                add(SenalDelMonto.DE_TRES_A_SEIS_CUOTAS)
            }
            if (monto.amount > cuota.multiply(SEIS)) add(SenalDelMonto.MAS_DE_SEIS_CUOTAS)
            if (monto.amount > cuota.multiply(DOCE)) add(SenalDelMonto.MAS_DE_DOCE_CUOTAS)
        }
    }

    private fun senalesDeCostumbre(monto: Money, habitual: Money?): List<SenalDelMonto> {
        if (habitual == null || habitual <= Money.ZERO) return emptyList()
        val suele = habitual.amount
        return buildList {
            // Estrictamente MÁS del triple, y DIEZ veces o más: los dos bordes
            // los dicta el dueño, y son distintos entre sí a propósito.
            if (monto.amount > suele.multiply(TRES)) {
                add(SenalDelMonto.MAS_DEL_TRIPLE_DE_LO_HABITUAL)
            }
            if (monto.amount >= suele.multiply(DIEZ)) add(SenalDelMonto.DIEZ_VECES_LO_HABITUAL)
        }
    }

    /**
     * El texto de una señal, o `null` cuando el hecho ya lo dice otra banda que
     * esta tarea no toca.
     *
     * `when` exhaustivo y **sin `else`**: una señal nueva no compila hasta que
     * alguien decida qué dice. Es la misma pregunta que [RarezaDelAbono] obliga
     * a contestar con `escalaLaHoja`.
     */
    private fun mensajeDe(
        senal: SenalDelMonto,
        monto: Money,
        parcialidad: Money,
        habitual: Money?
    ): String? = when (senal) {
        SenalDelMonto.NO_SE_PUEDE_REGISTRAR -> null
        SenalDelMonto.ABAJO_DE_LO_ESPERADO -> null
        SenalDelMonto.NO_ES_MULTIPLO_DE_CINCUENTA -> TextosDelAviso.DE_CINCUENTA_EN_CINCUENTA
        SenalDelMonto.DE_TRES_A_SEIS_CUOTAS, SenalDelMonto.MAS_DE_SEIS_CUOTAS ->
            TextosDelAviso.sonCuotas(cuotasDe(monto, parcialidad), parcialidad)

        SenalDelMonto.MAS_DEL_TRIPLE_DE_LO_HABITUAL ->
            habitual?.let { TextosDelAviso.sueleDar(it) }

        SenalDelMonto.DIEZ_VECES_LO_HABITUAL ->
            habitual?.let { TextosDelAviso.sueleDarYEstoEs(it, vecesDe(monto, it)) }

        SenalDelMonto.MAS_DE_DOCE_CUOTAS -> TextosDelAviso.NADIE_HA_PAGADO_TANTO
    }

    /**
     * Cuántas cuotas COMPLETAS cubre el monto. Se trunca hacia abajo, que es el
     * lado que nunca exagera: con $900 sobre una cuota de $250 el mensaje dice
     * "3 cuotas" y no "4", porque la cuarta no está completa. El monto exacto
     * sigue a la vista arriba, en la cifra grande.
     */
    private fun cuotasDe(monto: Money, parcialidad: Money): Int =
        monto.amount.divide(parcialidad.amount, 0, RoundingMode.DOWN).toInt()

    /** Cuántas veces lo habitual es este monto. Se trunca por lo mismo que [cuotasDe]. */
    private fun vecesDe(monto: Money, habitual: Money): Int =
        monto.amount.divide(habitual.amount, 0, RoundingMode.DOWN).toInt()

    private val TRES: BigDecimal = BigDecimal(3)
    private val SEIS: BigDecimal = BigDecimal(6)
    private val DIEZ: BigDecimal = BigDecimal(10)
    private val DOCE: BigDecimal = BigDecimal(12)
    private val CINCUENTA: BigDecimal = BigDecimal(50)
}
