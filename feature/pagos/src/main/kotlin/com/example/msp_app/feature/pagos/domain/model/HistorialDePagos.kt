package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/**
 * El historial de pagos de una venta = **ritmo + riel** (decisión del
 * `task-16-brief.md`): arriba [semanas] con [resumen], abajo [meses] con el
 * detalle.
 *
 * **La agrupación por mes es visible sin colapsar:** [meses] no lleva ningún
 * flag de expandido/colapsado a propósito — el mes interrumpe el riel con su
 * nodo, su nombre y su [MesDePagos.subtotal], y los pagos cuelgan debajo. Un
 * `expandido: Boolean` aquí sería la puerta de entrada al comportamiento que
 * el brief descartó.
 */
data class HistorialDePagos(
    val semanas: List<SemanaDeRitmo>,
    val resumen: ResumenDeRitmo,
    val meses: List<MesDePagos>,
    val totalPagos: Int
)

/**
 * Cómo se portó una semana. Los tres valores son los tres de la leyenda del
 * mock (`historial-de-pagos.html:108`): *a tiempo · tarde · sin pago*.
 */
enum class RitmoDeSemana {
    /** Cubrió la parcialidad dentro de la semana. */
    A_TIEMPO,

    /** Entró dinero pero no alcanzó la parcialidad. */
    TARDE,

    /** No entró nada. */
    SIN_PAGO
}

/** Una barra del ritmo: la semana que empieza en [inicio] y lo que entró en ella. */
data class SemanaDeRitmo(
    val inicio: LocalDate,
    val cobrado: Money,
    val ritmo: RitmoDeSemana
)

/**
 * El pie del ritmo: cumple / promedio / sin pago.
 *
 * [promedio] es el promedio de las semanas **con dinero**, no de las doce: una
 * semana en cero no es un abono chico, es la ausencia de abono, y meterla al
 * denominador convierte "tres semanas sin pagar" en "abona poquito", que es lo
 * contrario del argumento que el cobrador necesita.
 */
data class ResumenDeRitmo(
    val cumplidas: Int,
    val totalSemanas: Int,
    val promedio: Money,
    val semanasSinPago: Int
)

/**
 * Un mes del riel: su nodo cuadrado, su nombre y su subtotal, con sus pagos
 * colgando.
 */
data class MesDePagos(
    val mes: YearMonth,
    val nombre: String,
    val subtotal: Money,
    val pagos: List<PagoDelHistorial>
)

/**
 * Un abono del riel.
 *
 * [formaCobroId] viaja crudo porque es la llave con la que el dominio de
 * cobranza filtra qué cuenta como cobranza
 * (`VentanaCobro.FORMAS_COBRO_COBRANZA`); [metodo] es su lectura para pantalla.
 * Los dos son el mismo hecho a distinta altura, no dos fuentes de verdad: el
 * adaptador deriva el segundo del primero, en un solo lugar.
 */
data class PagoDelHistorial(
    val pagoId: String,
    val ventaId: Int,
    val fecha: Instant,
    val importe: Money,
    val formaCobroId: Int,
    val metodo: MetodoDeCobro,
    val nota: String?,
    /**
     * Quién cobró (`Payment.COBRADOR`). Campo ADITIVO de la Task 20: el ticket
     * de pago lo imprime en el renglón "cobró", y el dato ya vive en la fila del
     * pago — leerlo de ahí evita un puerto nuevo hacia la sesión y hace que el
     * ticket diga quién cobró ESE abono, no quién trae el teléfono hoy.
     *
     * Vacío cuando la fila no lo trae. Un solo escritor de producción
     * ([com.example.msp_app.feature.pagos.data.adapter.RoomPagosAdapter]), así
     * que el default no esconde ningún camino sin barrer.
     */
    val cobrador: String = "",
    /**
     * El id con el que **este teléfono capturó** el abono, cuando la fila que se
     * está leyendo ya no es la de la captura (`Payment.PAGO_RECIBIDO_ID`).
     *
     * Existe porque [pagoId] **no sobrevive a la sincronización**:
     * `CobranzaSyncManager.mergePagos` borra la fila del UUID en cuanto el
     * servidor acusa recibo y reinserta la canónica bajo la llave numérica de
     * Microsip. El UUID no se pierde —viaja a esta columna— pero deja de ser la
     * PK, así que preguntar *"¿está mi abono en el historial?"* comparando solo
     * contra [pagoId] devuelve **falso negativo** después de un merge.
     *
     * Eso importa porque el guard anti-duplicado de la pantalla de abono se
     * resuelve con esa pregunta al despertar de la muerte del proceso: un falso
     * negativo suelta el cerrojo, y un segundo `confirmar()` vuelve a descontar
     * `SALDO_REST`, que es un decremento y no un set absoluto.
     *
     * `null` en el histórico anterior al cutover y en las capturas que todavía
     * no subieron (ahí [pagoId] **es** el id de captura).
     */
    val capturaId: String? = null
)

/**
 * Cómo entró el dinero. Solo los tres que `VentanaCobro.FORMAS_COBRO_COBRANZA`
 * cuenta como cobranza — la condonación NO está aquí: no es dinero que entró y
 * su lógica no se toca (fuera de alcance del plan).
 */
enum class MetodoDeCobro(val etiqueta: String, val formaCobroId: Int) {
    EFECTIVO("efectivo", FORMA_EFECTIVO),
    CHEQUE("cheque", FORMA_CHEQUE),
    TRANSFERENCIA("transferencia", FORMA_TRANSFERENCIA);

    companion object {
        /**
         * Lee la forma de cobro cruda. Total por diseño: una forma que no sea
         * ninguna de las tres no debería llegar (el adaptador filtra con el
         * mismo conjunto que usa el dominio), y si llegara se muestra como
         * efectivo antes que romper la pantalla — el monto y la fecha, que es lo
         * que el cobrador está mirando, siguen siendo ciertos.
         */
        fun de(formaCobroId: Int): MetodoDeCobro =
            entries.firstOrNull { it.formaCobroId == formaCobroId } ?: EFECTIVO
    }
}

private const val FORMA_EFECTIVO = 157
private const val FORMA_CHEQUE = 158
private const val FORMA_TRANSFERENCIA = 52569
