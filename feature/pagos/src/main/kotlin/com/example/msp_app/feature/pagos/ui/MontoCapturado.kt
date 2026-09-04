package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Immutable
import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal

/**
 * Lo que el cobrador lleva tecleado en el teclado del abono.
 *
 * Es un editor de **texto**, no de dinero: guarda los dígitos tal como se
 * pulsaron y solo los convierte a [Money] cuando alguien pregunta por
 * [importe]. Un editor que redondeara a cada tecla haría que "1", "12", "125"
 * pasaran por tres montos distintos en el camino a "$125".
 *
 * [sugerido] marca que el contenido vino de un chip: la siguiente tecla lo
 * **reemplaza** en vez de anexarse, para que tocar "liquidar" y luego teclear
 * no produzca "1290" seguido de dígitos.
 */
@Immutable
data class MontoCapturado(val crudo: String = "", val sugerido: Boolean = false) {

    /** Agrega un dígito. Ignora el que ya no cabe en vez de truncar en silencio. */
    fun conDigito(digito: Int): MontoCapturado = when {
        sugerido -> MontoCapturado(crudo = digito.toString())
        yaNoCabe() -> this
        crudo == "0" -> copy(crudo = digito.toString())
        else -> copy(crudo = crudo + digito)
    }

    /**
     * El teclado deja de aceptar dígitos: ya hay dos decimales, o ya se
     * tecleó un entero absurdamente largo. Se ignora la tecla en vez de
     * recortar el número por detrás, que sería cambiar el monto en silencio.
     */
    private fun yaNoCabe(): Boolean {
        val decimales = crudo.substringAfter('.', missingDelimiterValue = "")
        if (crudo.contains('.')) return decimales.length >= MAX_DECIMALES
        return crudo.length >= MAX_ENTEROS
    }

    /** Abre los centavos. Un segundo punto no hace nada. */
    fun conPunto(): MontoCapturado {
        if (sugerido) return MontoCapturado(crudo = "0.")
        if (crudo.contains('.')) return this
        return copy(crudo = if (crudo.isEmpty()) "0." else "$crudo.")
    }

    /** Borra el último caracter; sobre un sugerido, lo borra entero. */
    fun sinUltimo(): MontoCapturado {
        if (sugerido) return MontoCapturado()
        return copy(crudo = crudo.dropLast(1))
    }

    /**
     * El monto como dinero. Un texto que no es un número —vacío, solo un
     * punto— vale [Money.ZERO], que [com.example.msp_app.feature.pagos.domain.
     * SeguridadDelAbono] bloquea por no positivo. Nunca lanza: el teclado no
     * puede tumbar una pantalla de dinero.
     */
    val importe: Money
        get() = try {
            Money.of(BigDecimal(normalizado()))
        } catch (_: NumberFormatException) {
            Money.ZERO
        }

    /** ¿Hay algo que registrar? */
    val esPositivo: Boolean get() = importe > Money.ZERO

    /** El texto canónico, sin el punto colgando: `"125."` -> `"125"`. */
    fun normalizado(): String = crudo.trimEnd('.').ifEmpty { "0" }

    /**
     * Lo que se pinta mientras se teclea: `"$1,250.50"`, y `"$0"` cuando no hay
     * nada. Se formatea el TEXTO tecleado y no el [Money] porque el cobrador
     * tiene que ver sus propias teclas —incluido el punto recién abierto, que
     * un formateador de dinero borraría.
     */
    fun enPantalla(): String {
        if (crudo.isEmpty()) return "$0"
        val enteros = crudo.substringBefore('.')
        val agrupados = enteros
            .reversed()
            .chunked(GRUPO)
            .joinToString(",")
            .reversed()
            .ifEmpty { "0" }
        return if (crudo.contains(
                '.'
            )
        ) {
            "$$agrupados.${crudo.substringAfter('.')}"
        } else {
            "$$agrupados"
        }
    }

    companion object {
        /** Centavos: dos decimales, ni uno más. */
        const val MAX_DECIMALES: Int = 2

        /** Techo de dígitos enteros. Nadie abona mil millones; un teclado sin tope sí desborda. */
        const val MAX_ENTEROS: Int = 9

        /** Dígitos por grupo de millares, solo para el display. */
        private const val GRUPO = 3

        /** El monto que deja un chip sugerido: reemplazable con la siguiente tecla. */
        fun deSugerido(importe: Money): MontoCapturado = MontoCapturado(
            crudo = importe.amount.stripTrailingZeros().toPlainString(),
            sugerido = true
        )
    }
}
