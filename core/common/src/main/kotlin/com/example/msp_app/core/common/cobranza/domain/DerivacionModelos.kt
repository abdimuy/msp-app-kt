package com.example.msp_app.core.common.cobranza.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Una cuenta (venta a crédito) que participa del periodo.
 *
 * [parcialidad] es `Sale.PARCIALIDAD` — lo que la venta debe cubrir por
 * periodo. Viaja como [BigDecimal] y NUNCA como `Double`: el gate
 * `NoDoubleForMoney` de este módulo lo prohíbe y `0.1 + 0.2 != 0.3` es la razón
 * de fondo. En Room el campo es `Int` (pesos enteros); el puente a
 * [BigDecimal] lo hace el adaptador, no el dominio.
 */
data class CuentaDelPeriodo(
    val ventaId: Int,
    val clienteId: Int,
    val parcialidad: BigDecimal
)

/**
 * Un pago capturado. [importe] es [BigDecimal] por la misma razón que
 * [CuentaDelPeriodo.parcialidad].
 *
 * [formaCobroId] se filtra contra [VentanaCobro.FORMAS_COBRO_COBRANZA]: la
 * condonación no es cobranza.
 */
data class PagoEnVentana(
    val ventaId: Int,
    val importe: BigDecimal,
    val formaCobroId: Int,
    val fechaHora: Instant
)

/**
 * Una visita capturada.
 *
 * [ventaId] es `VisitEntity.IMPTE_DOCTO_CC_ID` y puede venir en `null` (o en 0,
 * que el adaptador debe traducir a `null`) cuando la visita se registró sin
 * venta ligada. Para una visita de alcance [VisitScope.CLIENTE] eso da igual
 * —se propaga por [clienteId]—, pero una de alcance [VisitScope.VENTA] sin
 * venta no puede aplicarse a nada y se reporta como incidencia.
 *
 * ## Los tres campos de la Task 19
 *
 * [fechaPromesa], [montoPrometido] y [horaCita] existen para que el catálogo
 * esté **listo para recibirlos**, no para inventarlos. Hoy llegan siempre en
 * `null`: la captura estructurada la construye la Task 19 y las columnas las
 * agrega la migración aditiva de la Task 26. Reconstruirlos desde `NOTA` —donde
 * `NewVisitDialog` escribe hoy "La cita ha sido reagendada para el …"— es
 * precisamente el defecto que este plan vino a arreglar.
 */
data class VisitaEnVentana(
    val clienteId: Int,
    val ventaId: Int?,
    val tipoVisita: String,
    val fechaHora: Instant,
    val fechaPromesa: LocalDate? = null,
    val montoPrometido: BigDecimal? = null,
    val horaCita: LocalTime? = null
)

/**
 * El estado derivado de UNA cuenta en el periodo, con los números que lo
 * justifican para que la pantalla no tenga que recalcularlos.
 *
 * [abonoVentana] es la suma de los pagos válidos del periodo para esa venta —
 * el `AbonoSemana` del servidor. [fechaPromesa] / [montoPrometido] / [horaCita]
 * son los campos de la Task 19: hoy siempre `null`.
 */
data class ResultadoEstadoCuenta(
    val estado: EstadoCuenta,
    val abonoVentana: BigDecimal,
    val parcialidad: BigDecimal,
    val fechaPromesa: LocalDate? = null,
    val montoPrometido: BigDecimal? = null,
    val horaCita: LocalTime? = null
)

/**
 * Una anomalía detectada al derivar. **No** es una excepción: el dominio es
 * puro y no puede emitir telemetría (`:core:telemetry` depende de
 * `:core:common`, así que la dependencia inversa sería un ciclo). Se devuelve
 * como dato y el llamador —el ViewModel de `:feature:pagos`, Task 15— la
 * reenvía a `Telemetry.error(code, …)` **una vez por sincronización**, no una
 * vez por recomposición.
 *
 * [code] es constante nombrada y grepeable ([IncidenciaCobranza.Companion]) y
 * [ocurrencias] es un conteo. Ninguno de los dos lleva PII: ni el literal
 * ofensor, ni el cliente, ni un monto. Esa es la disciplina anti-PII del KDoc
 * de `Telemetry`, aplicada en la frontera correcta.
 */
data class IncidenciaCobranza(val code: String, val ocurrencias: Int) {
    companion object {
        /**
         * Llegó un `TIPO_VISITA` fuera del catálogo cerrado de 14. La visita NO
         * se pierde: aterriza en [TipoVisitaCatalogo.ESTADO_DESCONOCIDO]. Este
         * código es la única señal de que hay un build en la calle mandando algo
         * que el servidor ya no debería aceptar.
         */
        const val CODE_TIPO_VISITA_FUERA_DE_CATALOGO: String =
            "cobranza_tipo_visita_fuera_de_catalogo"

        /**
         * Una cuenta con `PARCIALIDAD <= 0`. No se puede distinguir "Pagó" de
         * "Abonó parcial" sin un objetivo, así que un abono positivo se queda en
         * [EstadoCuenta.ABONO_PARCIAL] — el estado conservador, el que sí manda
         * al cobrador a confirmar. El servidor toma la misma precaución:
         * `CalcAporte` devuelve `decimal.Zero` cuando `Parcialidad <= 0`
         * (`internal/rutas/domain/aporte.go:74-77`).
         */
        const val CODE_PARCIALIDAD_NO_POSITIVA: String = "cobranza_parcialidad_no_positiva"

        /**
         * Una visita de alcance [VisitScope.VENTA] sin venta ligada: no hay a
         * qué cuenta aplicarla. Se descarta explícitamente en vez de
         * colapsarla a una venta arbitraria del cliente —que es el bug que la
         * Task 13 arregló en la dirección contraria— y se reporta para que no
         * sea un silencio.
         */
        const val CODE_VISITA_SIN_VENTA: String = "cobranza_visita_sin_venta"
    }
}

/**
 * El resultado completo de derivar un periodo: el estado de cada venta más las
 * incidencias agregadas.
 *
 * [porVenta] tiene una entrada por cada [CuentaDelPeriodo] de la entrada —
 * ninguna cuenta se queda fuera, ni siquiera la que nadie tocó (esa es
 * [EstadoCuenta.SIN_TOCAR]).
 */
data class DerivacionPeriodo(
    val porVenta: Map<Int, ResultadoEstadoCuenta>,
    val incidencias: List<IncidenciaCobranza>
)
