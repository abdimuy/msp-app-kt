package com.example.msp_app.feature.collectionreport.domain.model

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Consistencia entre [Money] (`:core:common`, mudado desde este módulo en
 * Task 15) y `formatMoneyMxn` (design system): el redondeo a peso entero
 * ocurre SOLO en el string de salida, nunca en el VO, que sigue siendo exacto
 * a escala 2. Vive aquí y no en `:core:common:MoneyTest` porque
 * `:core:common` no depende de `:core:designsystem` — este test cruza los dos
 * módulos y `:feature:collectionReport` ya depende de ambos.
 */
class MoneyFormatMoneyMxnConsistencyTest {

    @Test
    fun `render via formatMoneyMxn es consistente`() {
        assertEquals("$1,234,568", formatMoneyMxn(Money.of(1234567.89).amount))
        assertEquals("$0", formatMoneyMxn(Money.ZERO.amount))
        assertEquals("-$850", formatMoneyMxn(Money.of(-850.0).amount))
        assertEquals(
            "$351",
            formatMoneyMxn(Money.sum(listOf(Money.of(350.50), Money.of(0.49))).amount)
        )
    }
}
