package com.example.msp_app.core.common.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.cobranza.domain.VisitScope
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Derivación de compromisos en el teléfono (plan visitas P2, Tarea 17; spec
 * §5.3; I5, I9; E-APP-104).
 *
 * Es la MISMA regla que `DerivarCompromisos` de msp-api
 * (`internal/visitas/domain/derivar_compromisos.go`), punto por punto, y las
 * dos se amarran con el juego compartido `testdata/compromisos/` (copia byte a
 * byte en `src/test/resources/compromisos/`, mismo VERSION) y con los 1000
 * historiales que genera `TestCompromisos_RapidGeneraCasosParaKotlin`. Si una
 * de las dos cambia sin la otra, `CompromisosCasosCompartidosTest` o
 * `generadosCoincidenConGo` se ponen rojas.
 *
 * Es una función pura: "hoy" y la zona del negocio se inyectan; aquí no se lee
 * el reloj. El estado no se guarda en ninguna tabla (I9).
 *
 * Los siete puntos:
 *  1. Visita APLICABLE a la venta V: alcance CLIENTE del cliente de V, o
 *     alcance VENTA con ventaId = V ([alcanceDe], la única clasificación del
 *     repo: [TipoVisitaCatalogo.alcanceDe]).
 *  2. M = la aplicable más reciente: mayor instante; empate → menor id en orden
 *     de BYTES UTF-8 (como Go; ver [compararIdsPorBytes], E-APP-105).
 *  3. Compromiso de una visita para V: cita → tipo cita (con el monto de la
 *     promesa si la promesa es de V o del cliente); si no, promesa de V o del
 *     cliente → tipo promesa.
 *  4. Si M tiene compromiso para V, es el vigente: cumplida / cita_cumplida si
 *     hay pago que cierra; si no, por día.
 *  5. Si M no tiene compromiso: P = la aplicable más reciente CON cita anterior
 *     a M. Una posterior con instante ≥ inicio de la cita la cumple; si no, una
 *     posterior con compromiso para V la reemplaza (sin entrada); si no, la cita
 *     de P sigue con su estado del punto 4 medido desde P (E-VIS-038).
 *  6. Pago que cierra el compromiso de la visita X: pago a V no rechazado con
 *     instante estrictamente posterior; sin hora, si su fecha civil ≥ la fecha
 *     civil de X en la zona del negocio (E-APP-106). Incluye condonación.
 *  7. [columnaSugeridaDe].
 */

/** Estado derivado del compromiso vigente (tabla 5.3.2). [codigo] es el del servidor. */
enum class EstadoCompromiso(val codigo: String) {
    CUMPLIDA("cumplida"),
    CITA_CUMPLIDA("cita_cumplida"),
    VIGENTE_FUTURA("vigente_futura"),
    VIGENTE_HOY("vigente_hoy"),
    INCUMPLIDA("incumplida"),
    CERRADA_SIN_CUMPLIR("cerrada_sin_cumplir")
}

/** Promesa o cita. */
enum class TipoCompromiso(val codigo: String) {
    PROMESA("promesa"),
    CITA("cita")
}

/** Lo que el compromiso opina de la columna de la cuenta. [SIN_OPINION] = decide el documento 1. */
enum class ColumnaSugerida(val codigo: String) {
    POR_VISITAR("por_visitar"),
    NO_PAGA("no_paga"),
    SIN_OPINION("")
}

/** Hasta hoy − día ≤ 14 el compromiso es incumplida; después, cerrada_sin_cumplir. */
const val DIAS_DE_GRACIA_INCUMPLIDA: Long = 14

/** Una venta del universo a derivar. */
data class CuentaCompromiso(val ventaId: Int, val clienteId: Int)

/** "Pagaré tal día (tal monto, de tal venta)". [ventaId] nulo = promesa del cliente. */
data class PromesaCompromiso(
    val ventaId: Int?,
    val fecha: LocalDate,
    val montoCentavos: Long?
) {
    /** La promesa aplica a [venta] si es de esa venta o del cliente (sin venta). */
    fun esDe(venta: Int): Boolean = ventaId == null || ventaId == venta
}

/** "Regresa tal día (a tal hora)". Sin [hora], la cita empieza al inicio del día. */
data class CitaCompromiso(val fecha: LocalDate, val hora: LocalTime?) {
    /** Inicio civil de la cita en [zona]: fecha + hora, o las 00:00 si no trae hora (E-APP-106). */
    fun inicio(zona: ZoneId): Instant =
        (hora?.let { fecha.atTime(it) } ?: fecha.atStartOfDay()).atZone(zona).toInstant()
}

/** Lo que la derivación necesita de una visita. [instante] es UTC. */
data class VisitaCompromiso(
    val id: String,
    val clienteId: Int,
    val ventaId: Int?,
    val tipoVisita: String,
    val instante: Instant,
    val promesa: PromesaCompromiso?,
    val cita: CitaCompromiso?
)

/**
 * Lo que la derivación necesita de un pago, condonación o captura local
 * pendiente. [instante] nulo = pago de oficina sin hora (DA-7); [fecha] es su
 * día civil (siempre).
 */
data class PagoCompromiso(
    val ventaId: Int,
    val instante: Instant?,
    val fecha: LocalDate,
    val rechazado: Boolean
)

/** La entrada derivada de una venta. */
data class CompromisoVigente(
    val ventaId: Int,
    val visitaId: String,
    val tipo: TipoCompromiso,
    val estado: EstadoCompromiso,
    val dia: LocalDate,
    val hora: LocalTime?,
    val montoCentavos: Long?
)

/** Alcance de una visita: la de `TipoVisitaCatalogo`, sin segunda tabla (cita fuerza CLIENTE). */
fun alcanceDe(tipo: String, tieneCita: Boolean): VisitScope =
    TipoVisitaCatalogo.alcanceDe(tipo, tieneCita)

/**
 * Por venta de [cuentas], su compromiso derivado. Las ventas sin compromiso (o
 * con uno reemplazado) no aparecen; visitas y pagos fuera de [cuentas] se
 * ignoran; el orden de las listas no importa.
 */
fun derivarCompromisos(
    cuentas: List<CuentaCompromiso>,
    visitas: List<VisitaCompromiso>,
    pagos: List<PagoCompromiso>,
    hoy: LocalDate,
    zona: ZoneId
): Map<Int, CompromisoVigente> {
    val porVenta = HashMap<Int, MutableList<VisitaCompromiso>>()
    val porCliente = HashMap<Int, MutableList<VisitaCompromiso>>()
    for (v in visitas) {
        val ventaId = v.ventaId
        when {
            alcanceDe(v.tipoVisita, v.cita != null) == VisitScope.CLIENTE ->
                porCliente.getOrPut(v.clienteId) { mutableListOf() }.add(v)
            ventaId != null -> porVenta.getOrPut(ventaId) { mutableListOf() }.add(v)
        }
    }
    val pagosPorVenta = pagos.groupBy { it.ventaId }
    val salida = LinkedHashMap<Int, CompromisoVigente>()
    for (c in cuentas) {
        val aplicables = porVenta[c.ventaId].orEmpty() + porCliente[c.clienteId].orEmpty()
        val cv = derivarCuenta(c.ventaId, aplicables, pagosPorVenta[c.ventaId].orEmpty(), hoy, zona)
        if (cv != null) salida[c.ventaId] = cv
    }
    return salida
}

/** Punto 7: hoy o incumplida (cualquier tipo) y cita futura → por visitar; promesa futura → no paga. */
fun columnaSugeridaDe(c: CompromisoVigente): ColumnaSugerida = when (c.estado) {
    EstadoCompromiso.VIGENTE_HOY, EstadoCompromiso.INCUMPLIDA -> ColumnaSugerida.POR_VISITAR
    EstadoCompromiso.VIGENTE_FUTURA ->
        if (c.tipo == TipoCompromiso.CITA) ColumnaSugerida.POR_VISITAR else ColumnaSugerida.NO_PAGA
    EstadoCompromiso.CUMPLIDA,
    EstadoCompromiso.CITA_CUMPLIDA,
    EstadoCompromiso.CERRADA_SIN_CUMPLIR -> ColumnaSugerida.SIN_OPINION
}

/**
 * Orden de BYTES UTF-8, el de `a.ID < b.ID` en Go (E-APP-105). `String.compareTo`
 * de Kotlin compara unidades UTF-16 y difiere de Go cuando un id trae un
 * carácter fuera del plano básico frente a uno de U+E000..U+FFFF.
 */
fun compararIdsPorBytes(a: String, b: String): Int {
    val x = a.toByteArray(Charsets.UTF_8)
    val y = b.toByteArray(Charsets.UTF_8)
    for (i in 0 until minOf(x.size, y.size)) {
        val d = (x[i].toInt() and BYTE_MASK) - (y[i].toInt() and BYTE_MASK)
        if (d != 0) return d
    }
    return x.size - y.size
}

private const val BYTE_MASK = 0xFF

/** Más reciente primero; empate de instante → menor id en orden de bytes (punto 2). */
private val porRecencia: Comparator<VisitaCompromiso> =
    compareByDescending<VisitaCompromiso> { it.instante }
        .thenComparator { a, b -> compararIdsPorBytes(a.id, b.id) }

private fun derivarCuenta(
    ventaId: Int,
    aplicablesSinOrden: List<VisitaCompromiso>,
    pagos: List<PagoCompromiso>,
    hoy: LocalDate,
    zona: ZoneId
): CompromisoVigente? {
    if (aplicablesSinOrden.isEmpty()) return null
    val aplicables = aplicablesSinOrden.sortedWith(porRecencia)
    val m = aplicables.first()
    compromisoDe(m, ventaId)?.let { return conEstado(it, m, pagos, hoy, zona) }
    // Punto 5: M no trae compromiso para V. Solo cuenta la cita más reciente.
    val i = (1 until aplicables.size).firstOrNull { aplicables[it].cita != null } ?: return null
    val p = aplicables[i]
    val base =
        requireNotNull(compromisoDe(p, ventaId)) { "una visita con cita siempre trae compromiso" }
    return when (desenlaceCitaAnterior(p, aplicables.subList(0, i), ventaId, zona)) {
        DesenlaceCita.CUMPLIDA -> base.conEstadoFijo(EstadoCompromiso.CITA_CUMPLIDA)
        DesenlaceCita.SIGUE -> conEstado(base, p, pagos, hoy, zona)
        DesenlaceCita.REEMPLAZADA -> null
    }
}

/** El compromiso de una visita para una venta, todavía sin estado (punto 3). */
private data class CompromisoSinEstado(
    val ventaId: Int,
    val visitaId: String,
    val tipo: TipoCompromiso,
    val dia: LocalDate,
    val hora: LocalTime?,
    val montoCentavos: Long?
) {
    fun conEstadoFijo(estado: EstadoCompromiso) =
        CompromisoVigente(ventaId, visitaId, tipo, estado, dia, hora, montoCentavos)
}

private fun compromisoDe(v: VisitaCompromiso, ventaId: Int): CompromisoSinEstado? {
    val cita = v.cita
    val promesa = v.promesa?.takeIf { it.esDe(ventaId) }
    return when {
        // El monto viaja con la cita solo a la venta de la promesa (o a todas
        // si la promesa es del cliente): la cita es de alcance cliente.
        cita != null -> CompromisoSinEstado(
            ventaId,
            v.id,
            TipoCompromiso.CITA,
            cita.fecha,
            cita.hora,
            promesa?.montoCentavos
        )
        promesa != null -> CompromisoSinEstado(
            ventaId,
            v.id,
            TipoCompromiso.PROMESA,
            promesa.fecha,
            null,
            promesa.montoCentavos
        )
        else -> null
    }
}

/** Punto 4: el estado del compromiso de la visita [x]. */
private fun conEstado(
    cv: CompromisoSinEstado,
    x: VisitaCompromiso,
    pagos: List<PagoCompromiso>,
    hoy: LocalDate,
    zona: ZoneId
): CompromisoVigente {
    val estado = when {
        !hayPagoQueCierra(x, pagos, zona) -> estadoPorDia(cv.dia, hoy)
        cv.tipo == TipoCompromiso.PROMESA -> EstadoCompromiso.CUMPLIDA
        else -> EstadoCompromiso.CITA_CUMPLIDA
    }
    return cv.conEstadoFijo(estado)
}

private enum class DesenlaceCita { REEMPLAZADA, CUMPLIDA, SIGUE }

/**
 * ¿Una visita posterior cumple o reemplaza la cita de [p]? [posteriores] son
 * las aplicables más recientes que [p], ninguna con cita (E-VIS-038).
 */
private fun desenlaceCitaAnterior(
    p: VisitaCompromiso,
    posteriores: List<VisitaCompromiso>,
    ventaId: Int,
    zona: ZoneId
): DesenlaceCita {
    val inicio = requireNotNull(p.cita).inicio(zona)
    return when {
        posteriores.any { !it.instante.isBefore(inicio) } -> DesenlaceCita.CUMPLIDA
        posteriores.any { compromisoDe(it, ventaId) != null } -> DesenlaceCita.REEMPLAZADA
        else -> DesenlaceCita.SIGUE
    }
}

/** Punto 6. El día civil de la visita y "hoy" se toman en la zona del negocio (E-APP-106). */
private fun hayPagoQueCierra(
    x: VisitaCompromiso,
    pagos: List<PagoCompromiso>,
    zona: ZoneId
): Boolean {
    val diaDeX = x.instante.atZone(zona).toLocalDate()
    return pagos.any { p ->
        val instante = p.instante
        when {
            p.rechazado -> false
            instante != null -> instante.isAfter(x.instante)
            else -> !p.fecha.isBefore(diaDeX)
        }
    }
}

private fun estadoPorDia(dia: LocalDate, hoy: LocalDate): EstadoCompromiso {
    val atraso = ChronoUnit.DAYS.between(dia, hoy)
    return when {
        atraso < 0 -> EstadoCompromiso.VIGENTE_FUTURA
        atraso == 0L -> EstadoCompromiso.VIGENTE_HOY
        atraso <= DIAS_DE_GRACIA_INCUMPLIDA -> EstadoCompromiso.INCUMPLIDA
        else -> EstadoCompromiso.CERRADA_SIN_CUMPLIR
    }
}
