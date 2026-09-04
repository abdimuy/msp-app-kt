package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.LocalDate

/**
 * Los cuatro chips de la lista de clientes, en el vocabulario del **catálogo de
 * ocho** de la Task 14.
 *
 * ## Por qué chips y no las tres pestañas de antes
 *
 * `SalesScreen` parte la lista en `POR VISITAR` / `VISITADOS` / `PAGADOS`
 * mirando `Sale.ESTADO_COBRANZA` (el enum legado de 5 valores). Esa columna la
 * escribe `PaymentsViewModel.savePayment` con `PAGADO` en **cada** pago
 * guardado, sin comparar el monto contra `PARCIALIDAD`, así que la pestaña
 * "PAGADOS" incluye a quien abonó cincuenta pesos de una parcialidad de
 * trescientos. Los chips no leen esa columna: preguntan por el estado del
 * periodo que `EstadoCuentaDeriver` deriva del dinero y de las visitas.
 *
 * ## La equivalencia con las pestañas viejas
 *
 * El puente `EstadoCobranza.aEstadoCuenta()` (`:core:database`) existe justo
 * para esto, y `SegmentosVsPestanasLegadasTest` lo recorre valor por valor:
 *
 * | `EstadoCobranza` | `aEstadoCuenta()` | trato | chip |
 * |---|---|---|---|
 * | `PENDIENTE` | `SIN_TOCAR` | `SIN_TRABAJAR` | **sin visitar** |
 * | `VISITADO` | `VISITE_VUELVO` | `REGRESAS` | **vencidos** |
 * | `VOLVER_VISITAR` | `VISITE_VUELVO` | `REGRESAS` | **vencidos** |
 * | `NO_PAGADO` | `SE_NEGO` | `ESCALAR` | **vencidos** |
 * | `PAGADO` | `PAGO` | `PAGADO` | solo *todos* |
 *
 * O sea: la pestaña `POR VISITAR` (`PENDIENTE ∪ VISITADO ∪ VOLVER_VISITAR`) es
 * exactamente `vencidos ∪ sin visitar`, y el orden que aquella pestaña tenía
 * sigue aplicando sobre el mismo conjunto. `PAGADOS` desaparece como chip a
 * propósito: la lista es de trabajo pendiente y una cuenta cobrada no pide
 * trabajo; sigue estando en *todos*.
 *
 * ## La partición del trabajo
 *
 * Los tres chips de trabajo son **disjuntos y cubren todo lo que pide trabajo**:
 * una cuenta o ya se le pasó el compromiso (*vencidos*), o cae hoy (*hoy*), o
 * nadie la ha tocado esta semana (*sin visitar*). Ninguna cuenta pendiente se
 * queda sin chip, que es lo que hacía que las pestañas escondieran trabajo.
 */
enum class SegmentoDeCobranza(val etiqueta: String) {

    /** Toda la ruta. */
    TODOS("todos"),

    /**
     * Se le pasó el compromiso: abonó menos de la parcialidad, pasaste y no se
     * resolvió, no había quién atendiera, se negó, o prometió para una fecha
     * que ya quedó atrás.
     */
    VENCIDOS("vencidos"),

    /**
     * Cae hoy: la promesa es para hoy, o hay una cita **de hoy** a una hora
     * acordada.
     *
     * **Hoy no se pinta** — ver `HOY_VISIBLE` en `PiezasDeLaLista.kt`. El
     * segmento existe entero (filtra, cuenta y está probado); lo único apagado
     * es su chip, porque la captura que escribe `PROMESA_FECHA`/`CITA_FECHA`
     * (Task 19) todavía no es alcanzable desde ninguna pantalla y el chip
     * marcaría 0 para siempre. Lo enciende la Task 21.
     */
    HOY("hoy"),

    /** Nadie la ha trabajado esta semana. */
    SIN_VISITAR("sin visitar");

    /**
     * ¿Cae [estado] en este segmento?
     *
     * El `when` sobre [TratoDelEstado] es **exhaustivo y sin `else`**: agregar
     * un trato rompe la compilación aquí en vez de caer en silencio fuera de
     * todos los chips, que es la forma en que una pantalla empieza a esconder
     * trabajo.
     */
    fun contiene(estado: EstadoDelPeriodo, hoy: LocalDate): Boolean {
        if (this == TODOS) return true
        return when (EstadoCuentaUi.tratoDe(estado)) {
            TratoDelEstado.PARCIAL,
            TratoDelEstado.REGRESAS,
            TratoDelEstado.ESCALAR,
            TratoDelEstado.NADIE -> this == VENCIDOS

            // Aquí `fechaPromesa` no puede ser null: sin fecha, `tratoDe` manda
            // la promesa a REGRESAS (regla heredada de la Task 16).
            TratoDelEstado.DIFERIDO -> when (this) {
                VENCIDOS -> estado.fechaPromesa?.isBefore(hoy) == true
                HOY -> estado.fechaPromesa == hoy
                else -> false
            }

            // La MISMA forma que la promesa, y por la misma razón. Antes esta
            // rama decía `this == HOY` a secas: daba por hecho que toda cita
            // dentro del periodo era de hoy, así que la cita del lunes a las
            // 4pm habría salido bajo "hoy" el jueves. El día existe
            // (`CITA_FECHA`, migración de la Task 26) y ahora se lee; sin día
            // la cita no cae en ningún chip de trabajo, porque nada sostiene
            // que sea de hoy ni que ya haya pasado.
            TratoDelEstado.CITA -> when (this) {
                VENCIDOS -> estado.fechaCita?.isBefore(hoy) == true
                HOY -> estado.fechaCita == hoy
                else -> false
            }

            TratoDelEstado.SIN_TRABAJAR -> this == SIN_VISITAR

            // Cobrada: no pide trabajo, así que no tiene chip propio.
            TratoDelEstado.PAGADO -> false
        }
    }
}
