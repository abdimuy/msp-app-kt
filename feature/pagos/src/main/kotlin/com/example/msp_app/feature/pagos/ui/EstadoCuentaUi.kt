package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
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
 * fecha, "no cae esta semana" es una afirmación que nada sostiene: se estaría
 * sacando una cuenta de la ruta con base en un texto que solo dice "regrese".
 *
 * Por eso [tratoDe] ramifica sobre [EstadoDelPeriodo.fechaPromesa] —un campo
 * que el DOMINIO ya devuelve, no algo que la pantalla adivine— y manda la
 * promesa sin fecha a [TratoDelEstado.REGRESAS], junto a "visité, vuelvo". El
 * estado sigue siendo `PROMETIO_PROXIMA`; lo que cambia es cómo se pinta. Esta
 * pantalla no deriva estados: los consume.
 *
 * Cuando la Task 19 capture la promesa estructurada, una promesa con fecha
 * empezará a existir de verdad y caerá sola en [TratoDelEstado.DIFERIDO] sin
 * tocar una línea de aquí.
 */
object EstadoCuentaUi {

    private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)
    private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", BUSINESS_LOCALE)

    /** El trato de [estado]. Función TOTAL: los ocho estados tienen el suyo. */
    fun tratoDe(estado: EstadoDelPeriodo): TratoDelEstado = when (estado.estado) {
        EstadoCuenta.PAGO -> TratoDelEstado.PAGADO
        EstadoCuenta.ABONO_PARCIAL -> TratoDelEstado.PARCIAL
        EstadoCuenta.VISITE_VUELVO -> TratoDelEstado.REGRESAS
        // La rama que sostiene toda la regla de arriba.
        EstadoCuenta.PROMETIO_PROXIMA ->
            if (estado.fechaPromesa == null) TratoDelEstado.REGRESAS else TratoDelEstado.DIFERIDO
        EstadoCuenta.SE_NEGO -> TratoDelEstado.ESCALAR
        EstadoCuenta.CITA_A_UNA_HORA -> TratoDelEstado.CITA
        EstadoCuenta.NO_ESTABA -> TratoDelEstado.NADIE
        EstadoCuenta.SIN_TOCAR -> TratoDelEstado.SIN_TRABAJAR
    }

    /**
     * ¿La cuenta sigue pidiendo trabajo esta semana? Solo [TratoDelEstado.PAGADO]
     * (ya cobró), [TratoDelEstado.DIFERIDO] (hay fecha) y [TratoDelEstado.CITA]
     * (hay hora acordada) dicen que no.
     */
    fun requiereAtencion(trato: TratoDelEstado): Boolean = when (trato) {
        TratoDelEstado.PAGADO, TratoDelEstado.DIFERIDO, TratoDelEstado.CITA -> false
        else -> true
    }

    /** El texto corto del estado. Minúsculas, sin punto final. */
    fun etiquetaDe(estado: EstadoDelPeriodo): String = when (tratoDe(estado)) {
        TratoDelEstado.PAGADO -> "pagó esta semana"
        TratoDelEstado.PARCIAL -> "abonó parcial"
        TratoDelEstado.REGRESAS ->
            if (estado.estado == EstadoCuenta.PROMETIO_PROXIMA) "prometió sin fecha" else "visité, vuelvo"
        TratoDelEstado.DIFERIDO -> "prometió el " + DIA_Y_MES.format(estado.fechaPromesa)
        TratoDelEstado.ESCALAR -> "se negó"
        TratoDelEstado.CITA -> estado.horaCita?.let { "cita " + HORA.format(it) } ?: "cita sin hora"
        TratoDelEstado.NADIE -> "no estaba"
        TratoDelEstado.SIN_TRABAJAR -> "sin trabajar"
    }

    /** La línea que explica qué hacer. Minúsculas, sin punto final. */
    fun detalleDe(estado: EstadoDelPeriodo): String = when (tratoDe(estado)) {
        TratoDelEstado.PAGADO -> "no hace falta volver"
        TratoDelEstado.PARCIAL -> "falta por confirmar"
        TratoDelEstado.REGRESAS ->
            if (estado.estado == EstadoCuenta.PROMETIO_PROXIMA) "sin fecha, regresas" else "regresas esta semana"
        TratoDelEstado.DIFERIDO -> "no cae esta semana"
        TratoDelEstado.ESCALAR -> "escalar esta cuenta"
        TratoDelEstado.CITA -> "quedaron de verse"
        TratoDelEstado.NADIE -> "regresas esta semana"
        TratoDelEstado.SIN_TRABAJAR -> "nadie la ha trabajado"
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
        TratoDelEstado.REGRESAS ->
            if (estado.estado == EstadoCuenta.PROMETIO_PROXIMA) PagosIconos.PromesaSinFecha else PagosIconos.Vuelvo
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
     * promesa, aquí dice "falta 1 de 2" en vez de dar por cerrado al cliente.
     */
    fun avisoDeCuentas(estados: List<EstadoDelPeriodo>): String? {
        val pendientes = estados.count { requiereAtencion(tratoDe(it)) }
        if (pendientes == 0) return null
        val verbo = if (pendientes == 1) "falta" else "faltan"
        return "$verbo $pendientes de ${estados.size}"
    }

    /** Solo "se negó" va en sólido invertido (`.st.negado` del mock). */
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
