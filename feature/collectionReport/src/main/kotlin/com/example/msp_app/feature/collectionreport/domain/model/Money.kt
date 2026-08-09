package com.example.msp_app.feature.collectionreport.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Value object de dinero en pesos mexicanos. Envuelve un [BigDecimal] con
 * escala FIJA de 2 (centavos) — nunca `Double`, que no puede representar
 * centavos exactos (`0.1 + 0.2 != 0.3`).
 *
 * **Invariante de escala:** toda instancia tiene `amount.scale() == 2`. Por eso
 * el constructor es privado y toda construcción pasa por [of] / [ZERO] (que
 * normalizan a escala 2 con [RoundingMode.HALF_UP]). Esto hace que la igualdad
 * de `Money` sea consistente por valor: `BigDecimal.equals` es sensible a la
 * escala (`"0" != "0.00"`), así que sin escala fija `ZERO` no sería igual a
 * `of(0.0)`. Se usa constructor privado + factory (no `init{}` de validación)
 * porque una `value class` no admite `init{}` con lógica; la factory es el punto
 * de control de la escala.
 *
 * El render a pantalla es `formatMoneyMxn(money.amount)` (design system): la
 * aritmética NUNCA sale a `Double`.
 */
@JvmInline
value class Money private constructor(val amount: BigDecimal) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(amount.add(other.amount))

    operator fun minus(other: Money): Money = Money(amount.subtract(other.amount))

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    companion object {
        /** Escala fija en centavos. */
        private const val SCALE = 2

        /** Cero canónico (escala 2), identidad de la suma. */
        val ZERO: Money = of(BigDecimal.ZERO)

        /**
         * Construye un `Money` desde un [BigDecimal] arbitrario, normalizando a
         * escala 2 con redondeo comercial ([RoundingMode.HALF_UP]). Es la vía
         * canónica de construcción dentro del dominio.
         */
        fun of(amount: BigDecimal): Money = Money(amount.setScale(SCALE, RoundingMode.HALF_UP))

        /**
         * ÚNICO puente `Double` -> `Money`, permitido SOLO en el borde de datos.
         *
         * El `Double` proviene del campo `IMPORTE` del schema de Room v27
         * (contrato de datos inmutable de producción; ver DISPATCH-CONVENTIONS y
         * la política de migración: el schema no se reescribe). Una vez cruzado
         * este borde, el valor vive como `BigDecimal` y NUNCA vuelve a `Double`:
         * no propagar este puente a cálculos ni a nuevas APIs.
         *
         * Usa [BigDecimal.valueOf] (representación decimal "amigable" de
         * `Double.toString`, no los bits binarios) y redondea HALF_UP a 2
         * decimales, consistente con `formatMoneyMxn`.
         */
        fun of(raw: Double): Money = of(BigDecimal.valueOf(raw))

        /** Suma una colección de `Money`; lista vacía -> [ZERO]. */
        fun sum(items: Iterable<Money>): Money = items.fold(ZERO) { acc, money -> acc + money }
    }
}
