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
     * ## Dónde este oráculo NO alcanza, y hay que decirlo
     *
     * `null` en las capturas que todavía no subieron —ahí [pagoId] **es** el id
     * de captura, así que la pregunta se contesta igual— **y también `null` en
     * todo el canal legado**: el API Node nunca escribió
     * `MSP_PAGOS_RECIBIDOS.IMPTE_DOCTO_CC_ID`, así que `PAGO_RECIBIDO_ID` es
     * `NULL` para el histórico anterior al cutover (lo dice el KDoc de
     * `PaymentDao.deleteLegacyTwinsByDoctoCcIds`, que por eso parea por
     * `DOCTO_CC_ID` y no por este campo).
     *
     * O sea: un abono colapsado por la vía **legada** no deja rastro de su UUID
     * de captura, y en esa vía el guard puede seguir dando el falso negativo que
     * este campo cierra para la vía v2.
     *
     * Hoy eso es inalcanzable: `PAGOS_USE_V2 = true` en `prod` y el único flavor
     * que lo tiene en `false` está retirado. **Voltear ese flag reabriría este
     * camino sin ninguna prueba que lo cubra** — regresión no probada en el
     * camino del dinero. Se dice acá en vez de dejar creer que el oráculo es
     * total.
     */
    val capturaId: String? = null,
    /**
     * Dónde se cobró este abono, cuando el teléfono lo pudo tomar.
     *
     * `Payment.LAT`/`LNG` ya viajaban en las TRES proyecciones del historial
     * (`getPaymentById`, `getPaymentsBySaleId`, `getPaymentsByDate`) sin que
     * nadie las mapeara. Es lo que pone el pin del mapa del detalle en la puerta
     * donde de verdad se cobró, en vez de geocodificar una dirección de texto.
     *
     * `null` en todo abono capturado sin permiso de ubicación o sin señal, y en
     * el histórico anterior a que se guardara.
     */
    val ubicacion: UbicacionDelCobro? = null
)

/**
 * El punto donde se cobró un abono, o donde se hizo una visita.
 *
 * **Existe como un solo valor, y no como dos `Double?` sueltos, a propósito.**
 * Media coordenada no ubica nada: una latitud sin longitud pintaría un pin en el
 * meridiano cero, o sea un dato FALSO en vez de un dato ausente.
 *
 * ## Las dos formas de no tener punto, y por qué la regla vive aquí
 *
 * Hay DOS maneras de que una fila no traiga punto, y las dos tienen que dar
 * `null`:
 *
 *  - **Falta la mitad** — columna nula. La cubre [de], que es la puerta de las
 *    tablas cuyas columnas admiten nulos (`Payment.LAT`/`LNG`).
 *  - **El par en cero** — `VisitEntity.LAT`/`LNG` son `Double` **no nulos**, así
 *    que una visita registrada con el GPS apagado no guarda "nada": guarda
 *    `0.0, 0.0`. Ese par es un punto de verdad —está en el Golfo de Guinea, a
 *    unos 9 000 km de la ruta— y pintarlo sería exactamente el mismo dato falso
 *    que media coordenada. La cubre [medida].
 *
 * Las dos puertas comparten la misma regla porque [de] delega en [medida]: si
 * cada una llevara su criterio, un abono en `(0, 0)` pasaría y una visita en
 * `(0, 0)` no, y el mismo hecho contaría dos historias según la tabla de donde
 * salió. La pantalla legada `SaleMapScreen.kt:52-65` ya descarta ese par al
 * dibujar pines de PAGOS, lo cual dice que el cero también llega por ese lado.
 */
data class UbicacionDelCobro(val lat: Double, val lng: Double) {

    companion object {
        /** El par, o `null` si falta cualquiera de los dos o si no se midió. */
        fun de(lat: Double?, lng: Double?): UbicacionDelCobro? =
            if (lat != null && lng != null) medida(lat, lng) else null

        /**
         * El par de una columna que **no admite nulos**, o `null` cuando es el
         * par en cero — o sea, cuando nadie lo midió. Ver el KDoc de la clase.
         */
        fun medida(lat: Double, lng: Double): UbicacionDelCobro? =
            if (lat == SIN_MEDIR && lng == SIN_MEDIR) null else UbicacionDelCobro(lat, lng)
    }
}

/**
 * Lo que guarda una columna de coordenada que no admite nulos cuando no hubo
 * señal: el cero del tipo, no un lugar.
 */
private const val SIN_MEDIR = 0.0

/**
 * Cómo entró el dinero. Solo los tres que `VentanaCobro.FORMAS_COBRO_COBRANZA`
 * cuenta como cobranza — la condonación NO está aquí: no es dinero que entró y
 * su lógica no se toca (fuera de alcance del plan).
 */
enum class MetodoDeCobro(val etiqueta: String, val formaCobroId: Int) {
    EFECTIVO("Efectivo", FORMA_EFECTIVO),
    CHEQUE("Cheque", FORMA_CHEQUE),
    TRANSFERENCIA("Transferencia", FORMA_TRANSFERENCIA);

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
