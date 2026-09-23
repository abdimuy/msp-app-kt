package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.LocalDate

/**
 * Los cuatro chips de la lista de clientes, en el vocabulario del **catálogo de
 * ocho** de la Task 14.
 *
 * ## Qué contesta cada chip
 *
 * La pregunta que la lista responde es siempre la misma —"¿a qué puerta voy
 * ahora?"— y los cuatro chips son las cuatro respuestas posibles:
 *
 * | chip | quiere decir |
 * |---|---|
 * | **sin visitar** | nadie la ha tocado esta semana |
 * | **volver** | ya se trabajó y sigue pidiendo otra pasada |
 * | **después** | no vuelvas a esa puerta ahora, por acuerdo o por conflicto |
 * | **pagados** | cobrada, no pide nada |
 *
 * ## La partición, que es el invariante de esta pantalla
 *
 * Los cuatro son **disjuntos** y **cubren los ocho estados**: ninguna cuenta se
 * queda fuera de todos los chips. Eso es lo que separa esta lista de las tres
 * pestañas que `SalesScreen` tenía antes de la Task 21, que miraban
 * `Sale.ESTADO_COBRANZA` y escondían trabajo — aquella columna la escribe
 * `PaymentsViewModel.savePayment` con `PAGADO` en **cada** pago guardado, sin
 * comparar el monto contra `PARCIALIDAD`, así que su pestaña "PAGADOS" se
 * tragaba a quien abonó cincuenta pesos de una parcialidad de trescientos.
 * Estos chips no leen esa columna: preguntan por el estado del periodo que
 * `EstadoCuentaDeriver` deriva del dinero y de las visitas.
 *
 * `SegmentoDeCobranzaTest` recorre los ocho estados y exige las dos mitades del
 * invariante. Si mañana alguien agrega un noveno estado, ahí se entera.
 *
 * ## La fecha decide, no el tipo de compromiso
 *
 * Una promesa y una cita **no viven en un chip fijo**: viven donde las pone su
 * fecha. A futuro son [YA_NO_ESTA_SEMANA] —hay un acuerdo, no hay nada que
 * hacer hoy—; vencidas caen en [VOLVER_A_VISITAR], porque una promesa que no se
 * cumplió volvió a ser trabajo pendiente y es justo la que más urge tocar.
 * Archivarlas bajo "ya no se visitan" sería esconderlas donde nadie las busca.
 *
 * Las de **hoy** caen en [VOLVER_A_VISITAR]: son trabajo de hoy y tienen que
 * aparecer en la lista de pendientes del día. El matiz conocido es que una cita
 * de las 4 de la tarde se ve ahí desde la mañana; separarlas pediría un quinto
 * chip, que no cabe en una fila en pantalla chica.
 *
 * Sin fecha, una promesa ni siquiera llega aquí: `tratoDe` la manda a `REGRESAS`
 * (regla heredada de la Task 16), que es [VOLVER_A_VISITAR].
 */
enum class SegmentoDeCobranza(val etiqueta: String) {

    /** Nadie la ha trabajado esta semana. */
    SIN_VISITAR("Sin visitar"),

    /**
     * Ya se trabajó y sigue pidiendo otra pasada: abonó menos de la parcialidad,
     * pasaste y no se resolvió, no había quién atendiera, o el compromiso venció
     * sin cumplirse.
     */
    VOLVER_A_VISITAR("Volver"),

    /**
     * No vuelvas a esa puerta ahora. Dos motivos distintos con la misma
     * consecuencia para el día de hoy: hay un compromiso a futuro —promesa con
     * fecha o cita acordada— o la cuenta se negó y pasó a atención especial.
     */
    YA_NO_ESTA_SEMANA("Después"),

    /** Cobrada: no pide trabajo. */
    PAGADOS("Pagados");

    /**
     * ¿Cae [estado] en este segmento?
     *
     * El `when` sobre [TratoDelEstado] es **exhaustivo y sin `else`**: agregar un
     * trato rompe la compilación aquí en vez de caer en silencio fuera de todos
     * los chips, que es la forma en que una pantalla empieza a esconder trabajo.
     */
    fun contiene(estado: EstadoDelPeriodo, hoy: LocalDate): Boolean =
        when (EstadoCuentaUi.tratoDe(estado)) {
            TratoDelEstado.SIN_TRABAJAR -> this == SIN_VISITAR

            TratoDelEstado.PARCIAL,
            TratoDelEstado.REGRESAS,
            TratoDelEstado.NADIE -> this == VOLVER_A_VISITAR

            // Se negó: hay conflicto, y volver a tocar esa puerta esta semana no
            // ayuda. Va con los compromisos a futuro aunque el motivo sea el
            // contrario, porque para el día de hoy la consecuencia es la misma.
            TratoDelEstado.ESCALAR -> this == YA_NO_ESTA_SEMANA

            // Aquí `fechaPromesa` no puede ser null: sin fecha, `tratoDe` manda
            // la promesa a REGRESAS.
            TratoDelEstado.DIFERIDO -> caePorFecha(estado.fechaPromesa, hoy)

            // La MISMA forma que la promesa, y por la misma razón. Antes de que
            // `CITA_FECHA` existiera (migración de la Task 26) se daba por hecho
            // que toda cita del periodo era de hoy, así que la del lunes a las
            // 4pm salía bajo "hoy" el jueves. Sin día, la cita no cae en ningún
            // chip de trabajo: nada sostiene que sea de hoy ni que ya pasó.
            TratoDelEstado.CITA -> caePorFecha(estado.fechaCita, hoy)

            TratoDelEstado.PAGADO -> this == PAGADOS
        }

    /**
     * Dónde cae un compromiso fechado. **Vencido o de hoy** es trabajo pendiente
     * ([VOLVER_A_VISITAR]); **a futuro** es un acuerdo que hay que respetar
     * ([YA_NO_ESTA_SEMANA]). Sin fecha no cae en ninguno — ver el KDoc de la
     * clase.
     */
    private fun caePorFecha(fecha: LocalDate?, hoy: LocalDate): Boolean {
        val comprometida = fecha ?: return false
        return if (comprometida.isAfter(hoy)) {
            this == YA_NO_ESTA_SEMANA
        } else {
            this == VOLVER_A_VISITAR
        }
    }
}
