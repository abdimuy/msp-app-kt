package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspColors
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.format.DateTimeFormatter

/**
 * Cómo se trata en pantalla cada uno de los ocho estados del catálogo de la
 * Task 14.
 *
 * **No es una novena clasificación.** Hay más tratos que estados por una sola
 * razón, explicada abajo: [EstadoCuenta.PROMETIO_PROXIMA] se pinta distinto
 * según traiga fecha o no, y esa fecha la devuelve el propio dominio en
 * [EstadoDelPeriodo.fechaPromesa]. Ningún valor de aquí decide un estado.
 */
enum class TratoDelEstado {
    /** Cobró lo del periodo. */
    PAGADO,

    /** Entró dinero pero no lo esperado. */
    PARCIAL,

    /** Sigue en la lista de esta semana: hay que volver a esa puerta. */
    REGRESAS,

    /**
     * **El único trato que dice "no vuelvas esta semana".** Solo lo alcanza una
     * promesa CON fecha.
     */
    DIFERIDO,

    /** Se negó: atención especial. */
    ESCALAR,

    /** Quedaron de verse. */
    CITA,

    /** Fuiste y no había quién atendiera. */
    NADIE,

    /** Nadie trabajó esta cuenta. */
    SIN_TRABAJAR
}

/**
 * La terna que pinta un estado: **color + ícono + texto**, nunca color solo
 * (regla dura de accesibilidad del design system).
 *
 * [relleno] distingue el único estado que el mock pinta en sólido invertido —
 * "se negó" (`.st.negado{background:var(--red)}`) — del resto, que van en
 * estilo outline con su tint de fondo.
 */
@Immutable
data class EstadoVisual(
    val trato: TratoDelEstado,
    val etiqueta: String,
    val detalle: String,
    val icono: ImageVector,
    val contenido: Color,
    val fondo: Color,
    val relleno: Boolean
) {
    /** ¿Esta cuenta sigue pidiendo trabajo esta semana? */
    val requiereAtencion: Boolean get() = EstadoCuentaUi.requiereAtencion(trato)
}

/**
 * El mapa estado → presentación. Todo lo que no necesita Compose vive aquí como
 * función pura y por eso se prueba sin Robolectric.
 *
 * ## La regla que esta tarea heredó, y por qué existe
 *
 * `PIDE_REAGENDAR` ("Pidió reagendar visita") mapea a
 * [EstadoCuenta.PROMETIO_PROXIMA] por la tabla del plan, **pero no trae fecha
 * ni monto**. Y [EstadoCuenta.PROMETIO_PROXIMA] es el único camino del sistema
 * que le dice a un cobrador que deje de trabajar una puerta este periodo. Sin
 * fecha, "No cae esta semana" es una afirmación que nada sostiene: se estaría
 * sacando una cuenta de la ruta con base en un texto que solo dice "regrese".
 *
 * Por eso [tratoDe] ramifica sobre [EstadoDelPeriodo.fechaPromesa] —un campo
 * que el DOMINIO ya devuelve, no algo que la pantalla adivine— y manda la
 * promesa sin fecha a [TratoDelEstado.REGRESAS], junto a "Visité, vuelvo". El
 * estado sigue siendo `PROMETIO_PROXIMA`; lo que cambia es cómo se pinta. Esta
 * pantalla no deriva estados: los consume.
 *
 * Cuando la Task 19 capture la promesa estructurada, una promesa con fecha
 * empezará a existir de verdad y caerá sola en [TratoDelEstado.DIFERIDO] sin
 * tocar una línea de aquí.
 *
 * ## La MISMA regla, una rama más allá: [EstadoCuenta.CITA_A_UNA_HORA]
 *
 * Una cita sin hora tiene exactamente la misma forma que una promesa sin
 * fecha: "Quedaron de verse" saca la cuenta del trabajo de la semana, y sin
 * hora no hay nada a qué sujetar al cliente. Un estado que se llama *cita a una
 * hora* y llega sin hora no es una cita — es un pendiente.
 *
 * Hoy ese estado es inalcanzable (ningún literal de `TIPO_VISITA` mapea a él,
 * ver `EstadoCuenta`), pero la Task 19 lo **arma**: captura la cita
 * estructurada y las columnas `CITA_FECHA`/`CITA_HORA` ya existen desde la
 * migración de la Task 26, con `CITA_HORA` **nullable** justamente porque el
 * mock contempla "otro día sin hora". Dejar la rama sin guarda sería plantar el
 * defecto que esta tarea acaba de quitar del lado de la promesa, con fecha de
 * activación conocida.
 */
/** El día de una promesa o de una cita, tal como se lee en la ruta. */
private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/** La hora de una cita. */
private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", BUSINESS_LOCALE)

object EstadoCuentaUi {

    /** El trato de [estado]. Función TOTAL: los ocho estados tienen el suyo. */
    fun tratoDe(estado: EstadoDelPeriodo): TratoDelEstado = when (estado.estado) {
        EstadoCuenta.PAGO -> TratoDelEstado.PAGADO
        EstadoCuenta.ABONO_PARCIAL -> TratoDelEstado.PARCIAL
        EstadoCuenta.VISITE_VUELVO -> TratoDelEstado.REGRESAS
        // La rama que sostiene toda la regla de arriba.
        EstadoCuenta.PROMETIO_PROXIMA ->
            if (estado.fechaPromesa == null) TratoDelEstado.REGRESAS else TratoDelEstado.DIFERIDO
        EstadoCuenta.SE_NEGO -> TratoDelEstado.ESCALAR
        // La MISMA guarda que la promesa: sin hora no hay cita que sostenga la espera.
        EstadoCuenta.CITA_A_UNA_HORA ->
            if (estado.horaCita == null) TratoDelEstado.REGRESAS else TratoDelEstado.CITA
        EstadoCuenta.NO_ESTABA -> TratoDelEstado.NADIE
        EstadoCuenta.SIN_TOCAR -> TratoDelEstado.SIN_TRABAJAR
    }

    /**
     * ¿La cuenta sigue pidiendo trabajo esta semana? Solo [TratoDelEstado.PAGADO]
     * (ya cobró), [TratoDelEstado.DIFERIDO] (hay fecha) y [TratoDelEstado.CITA]
     * (hay hora acordada) dicen que no.
     *
     * Los tres "no" descansan en un dato que existe: dinero cobrado, una fecha,
     * una hora. Ninguno se alcanza por la sola presencia de un estado.
     */
    fun requiereAtencion(trato: TratoDelEstado): Boolean = when (trato) {
        TratoDelEstado.PAGADO, TratoDelEstado.DIFERIDO, TratoDelEstado.CITA -> false
        else -> true
    }

    /**
     * El texto corto del estado. Mayúscula inicial, sin punto final.
     *
     * ## Por qué [TratoDelEstado.SIN_TRABAJAR] no se llama "Sin trabajar"
     *
     * Las otras nueve etiquetas están en la voz del cobrador y cuentan **qué
     * pasó en la puerta**: "Pagó esta semana", "Visité, vuelvo", "Se negó", "No
     * estaba". "Sin trabajar" era la única que hablaba del pendiente del
     * cobrador, y sobre la única cuenta a la que nadie ha ido todavía: se leía
     * como reproche por algo que aún no tocaba hacer.
     *
     * "Falta pasar" dice el mismo hecho desde la ruta —esa puerta queda por
     * visitar— sin acusar a nadie. El dueño lo cazó en el teléfono.
     *
     * El enum sigue siendo `SIN_TRABAJAR` a propósito: ese es el nombre del
     * trato en el DOMINIO, no el texto que lee el cobrador. Los dos no tienen
     * por qué coincidir.
     */
    fun etiquetaDe(estado: EstadoDelPeriodo): String = when (tratoDe(estado)) {
        TratoDelEstado.PAGADO -> "Pagó esta semana"
        TratoDelEstado.PARCIAL -> "Abonó parcial"
        TratoDelEstado.REGRESAS -> when (estado.estado) {
            EstadoCuenta.PROMETIO_PROXIMA -> "Prometió sin fecha"
            EstadoCuenta.CITA_A_UNA_HORA -> "Cita sin hora"
            else -> "Visité, vuelvo"
        }
        // **Con el monto prometido**, cuando lo hay. Se capturaba y no se
        // pintaba en ninguna parte de la app: el cobrador acordaba "$800 el 25"
        // y volvía a una pantalla que sólo decía el día. `montoPrometido` es
        // nullable a propósito —prometer una fecha sin cantidad es un caso real
        // de campo— y por eso se compone, no se asume.
        TratoDelEstado.DIFERIDO -> prometio(estado)
        TratoDelEstado.ESCALAR -> "Se negó"
        // **Con el día, no sólo la hora.** Decía "Cita 16:30" y el cobrador no
        // tenía cómo saber de qué día hablaba; el dueño lo cazó en el teléfono.
        // `horaCita` aquí ya no puede ser null (sin hora, `tratoDe` mandó a
        // REGRESAS), pero `fechaCita` sí: una cita de hoy puede venir sin día.
        TratoDelEstado.CITA -> cita(estado)
        TratoDelEstado.NADIE -> "No estaba"
        TratoDelEstado.SIN_TRABAJAR -> "Falta pasar"
    }

    /**
     * La línea que explica qué hacer. Mayúscula inicial, sin punto final.
     *
     * "Nadie la ha trabajado" cargaba el mismo reproche que la etiqueta vieja y
     * además nombraba al culpable, cuando esta línea existe para decirle al
     * cobrador qué le toca. "Todavía no vas" hace eso. El porqué completo está
     * en [etiquetaDe]; cambian juntas porque la lista y el detalle leen de aquí
     * y una misma cuenta no puede llamarse distinto en dos pantallas.
     */
    fun detalleDe(estado: EstadoDelPeriodo): String = when (tratoDe(estado)) {
        TratoDelEstado.PAGADO -> "No hace falta volver"
        TratoDelEstado.PARCIAL -> "Falta por confirmar"
        TratoDelEstado.REGRESAS -> when (estado.estado) {
            EstadoCuenta.PROMETIO_PROXIMA -> "Sin fecha, regresas"
            EstadoCuenta.CITA_A_UNA_HORA -> "Sin hora, regresas"
            else -> "Regresas esta semana"
        }
        TratoDelEstado.DIFERIDO -> "No cae esta semana"
        TratoDelEstado.ESCALAR -> "Escalar esta cuenta"
        TratoDelEstado.CITA -> "Quedaron de verse"
        TratoDelEstado.NADIE -> "Regresas esta semana"
        TratoDelEstado.SIN_TRABAJAR -> "Todavía no vas"
    }

    /**
     * El ícono — el portador de significado que sobrevive al daltonismo.
     * Los dos tratos que comparten color ([TratoDelEstado.NADIE] y
     * [TratoDelEstado.SIN_TRABAJAR], ambos `statusPending` por la tabla del
     * Task 2) NO comparten ícono ni texto.
     */
    fun iconoDe(estado: EstadoDelPeriodo): ImageVector = when (tratoDe(estado)) {
        TratoDelEstado.PAGADO -> PagosIconos.Pago
        TratoDelEstado.PARCIAL -> PagosIconos.Parcial
        // Los dos compartían el triángulo de advertencia y en pantalla se veían
        // idénticos. Ahora cada uno dice QUÉ dato le falta: a la promesa el día,
        // a la cita la hora.
        TratoDelEstado.REGRESAS -> when (estado.estado) {
            EstadoCuenta.PROMETIO_PROXIMA -> PagosIconos.PrometioSinFecha
            EstadoCuenta.CITA_A_UNA_HORA -> PagosIconos.CitaSinHora
            else -> PagosIconos.Vuelvo
        }
        TratoDelEstado.DIFERIDO -> PagosIconos.Prometio
        TratoDelEstado.ESCALAR -> PagosIconos.Negado
        TratoDelEstado.CITA -> PagosIconos.Cita
        TratoDelEstado.NADIE -> PagosIconos.NoEstaba
        TratoDelEstado.SIN_TRABAJAR -> PagosIconos.SinTocar
    }

    /**
     * Color de contenido, resuelto contra [MspColors] — la tabla del Task 2
     * (`docs/design/paleta-mocks-a-mspcolors.md` §4), no la esmeralda del mock.
     */
    fun contenidoDe(trato: TratoDelEstado, colors: MspColors): Color = when (trato) {
        TratoDelEstado.PAGADO -> colors.statusPaid
        TratoDelEstado.PARCIAL -> colors.statusTeal
        TratoDelEstado.REGRESAS -> colors.statusPartial
        TratoDelEstado.DIFERIDO -> colors.statusOverdue
        // Relleno sólido invertido: el contenido va sobre el rojo, no sobre el tint.
        TratoDelEstado.ESCALAR -> colors.onDanger
        TratoDelEstado.CITA -> colors.promise
        TratoDelEstado.NADIE, TratoDelEstado.SIN_TRABAJAR -> colors.statusPending
    }

    /** Fondo (tint, o relleno sólido en [TratoDelEstado.ESCALAR]). */
    fun fondoDe(trato: TratoDelEstado, colors: MspColors): Color = when (trato) {
        TratoDelEstado.PAGADO -> colors.statusPaidTint
        TratoDelEstado.PARCIAL -> colors.statusTealTint
        TratoDelEstado.REGRESAS -> colors.statusPartialTint
        TratoDelEstado.DIFERIDO -> colors.statusOverdueTint
        TratoDelEstado.ESCALAR -> colors.statusOverdue
        TratoDelEstado.CITA -> colors.promiseTint
        TratoDelEstado.NADIE -> colors.statusPendingTint
        TratoDelEstado.SIN_TRABAJAR -> colors.surface2
    }

    /**
     * El aviso del encabezado del cliente (`.tip` del mock): cuántas de sus
     * cuentas siguen pidiendo trabajo esta semana. `null` cuando ninguna —
     * entonces no hay nada que avisar y la banda no se pinta.
     *
     * Es el número que el brief protege: con dos ventas, una pagada y otra en
     * promesa, aquí dice "Falta 1 de 2" en vez de dar por cerrado al cliente.
     */
    fun avisoDeCuentas(estados: List<EstadoDelPeriodo>): String? {
        val pendientes = estados.count { requiereAtencion(tratoDe(it)) }
        if (pendientes == 0) return null
        val verbo = if (pendientes == 1) "Falta" else "Faltan"
        return "$verbo $pendientes de ${estados.size}"
    }

    /** Solo "Se negó" va en sólido invertido (`.st.negado` del mock). */
    fun esRelleno(trato: TratoDelEstado): Boolean = trato == TratoDelEstado.ESCALAR
}

/** La presentación completa de [estado], ya resuelta contra el tema vigente. */
@Composable
fun estadoVisualDe(estado: EstadoDelPeriodo): EstadoVisual {
    val trato = EstadoCuentaUi.tratoDe(estado)
    val colors = MspTheme.colors
    return EstadoVisual(
        trato = trato,
        etiqueta = EstadoCuentaUi.etiquetaDe(estado),
        detalle = EstadoCuentaUi.detalleDe(estado),
        icono = EstadoCuentaUi.iconoDe(estado),
        contenido = EstadoCuentaUi.contenidoDe(trato, colors),
        fondo = EstadoCuentaUi.fondoDe(trato, colors),
        relleno = EstadoCuentaUi.esRelleno(trato)
    )
}

/**
 * *"Cita 24 sep 16:30"*, o *"Cita 16:30"* si la cita no trajo día.
 *
 * El día va **antes** de la hora porque es lo que decide si esa puerta es
 * de hoy: una hora suelta obliga a recordar de qué día se habló, que es
 * justo lo que el cobrador no puede hacer con 40 cuentas.
 */
private fun cita(estado: EstadoDelPeriodo): String {
    val dia = estado.fechaCita?.let { DIA_Y_MES.format(it) + " " }.orEmpty()
    return "Cita " + dia + HORA.format(estado.horaCita)
}

/**
 * *"Prometió $800 el 25 sep"*, o *"Prometió el 25 sep"* cuando no hay
 * cantidad.
 *
 * El monto va pegado al verbo y antes del día: lo primero que se pregunta
 * de una promesa es cuánto, y el día ya venía saliendo bien.
 */
private fun prometio(estado: EstadoDelPeriodo): String {
    val monto = estado.montoPrometido?.let { formatMoneyMxn(it.amount) + " " }.orEmpty()
    return "Prometió " + monto + "el " + DIA_Y_MES.format(estado.fechaPromesa)
}
