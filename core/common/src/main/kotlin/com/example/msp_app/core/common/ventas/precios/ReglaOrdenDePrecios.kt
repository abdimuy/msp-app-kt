package com.example.msp_app.core.common.ventas.precios

import java.math.BigDecimal
import java.math.RoundingMode

enum class TipoDeLinea { PRODUCTO, COMBO }

/**
 * Una línea tal como se ENVÍA al servidor (precio unitario por escalón).
 * [dentroDeCombo]: producto hijo de un combo; se valida por línea pero no suma
 * a los totales, porque su valor ya va en el precio del combo.
 */
data class LineaDePrecios(
    val tipo: TipoDeLinea,
    val nombre: String,
    val cantidad: BigDecimal,
    val contado: BigDecimal,
    val corto: BigDecimal,
    val anual: BigDecimal,
    val dentroDeCombo: Boolean = false
)

sealed interface VeredictoOrdenDePrecios {
    data object Valido : VeredictoOrdenDePrecios

    /** [linea] es `null` cuando lo que rompe la regla son los totales. */
    data class Invalido(
        val codigo: String,
        val mensaje: String,
        val linea: LineaDePrecios?
    ) : VeredictoOrdenDePrecios
}

/**
 * Espejo de la regla del servidor (msp-api `internal/ventas/domain/
 * monto_snapshot.go:89-104`, `venta.go:1080-1119`): contado ≤ corto ≤ anual,
 * no estricta, y un escalón en 0 no se capturó y se salta.
 *
 * E-APP-062: sin esta validación previa el teléfono enviaba la venta, el
 * servidor respondía 422 y el worker la daba por perdida en
 * `MSP_FAILED_INTENTS`. Aquí se detiene antes, con el mismo código y mensaje.
 *
 * Como el servidor, NO distingue contado de crédito: se aplica a las líneas
 * tal como se envían. En CONTADO la app envía corto y anual en 0 (E-APP-061),
 * así que una venta de contado bien armada nunca tiene orden que romper.
 */
object ReglaOrdenDePrecios {

    const val CODIGO_LINEA = "precio_tier_order_invalid"
    const val MENSAJE_LINEA =
        "los precios capturados de la línea deben ir de menor a mayor: contado, corto plazo y anual"
    const val CODIGO_TOTALES = "monto_tier_order_invalid"
    const val MENSAJE_TOTALES =
        "los totales de la venta son imposibles: los montos capturados deben ir de menor a mayor: " +
            "contado, corto plazo y anual"

    private const val ESCALA_TOTALES = 2

    fun ordenValido(contado: BigDecimal, corto: BigDecimal, anual: BigDecimal): Boolean {
        val capturados = listOf(contado, corto, anual).filter { it.signum() != 0 }
        return capturados.zipWithNext().all { (antes, despues) -> antes <= despues }
    }

    fun validar(lineas: List<LineaDePrecios>): VeredictoOrdenDePrecios {
        lineas.firstOrNull { !ordenValido(it.contado, it.corto, it.anual) }?.let {
            return VeredictoOrdenDePrecios.Invalido(CODIGO_LINEA, MENSAJE_LINEA, it)
        }

        val queSuman = lineas.filterNot { it.dentroDeCombo }
        fun total(escalon: (LineaDePrecios) -> BigDecimal): BigDecimal =
            queSuman.fold(BigDecimal.ZERO) { suma, l -> suma + escalon(l) * l.cantidad }
                .setScale(ESCALA_TOTALES, RoundingMode.HALF_UP)

        return if (ordenValido(total { it.contado }, total { it.corto }, total { it.anual })) {
            VeredictoOrdenDePrecios.Valido
        } else {
            VeredictoOrdenDePrecios.Invalido(CODIGO_TOTALES, MENSAJE_TOTALES, null)
        }
    }
}
