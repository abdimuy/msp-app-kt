package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Char-test del money-path: reproduce los agregados del `DailyReportScreen`
 * viejo (`payments.sumOf { it.IMPORTE }.toInt()`, `WeeklyReportContent` idéntico)
 * sobre un set FIJO de importes con centavos, y contrasta el resultado viejo
 * (Double truncado a `Int`) contra el nuevo ([Money] exacto).
 *
 * **Conclusión (corrección consciente de bug, NO regresión):** el código viejo
 * TRUNCABA los centavos en cada total mostrado — perdía hasta $0.99 por
 * categoría. El nuevo conserva el importe exacto. Todo dato es un [Double] que
 * simula el `IMPORTE` del schema Room v27, cruzado UNA vez por `Money.of(Double)`.
 */
class ReportAggregatorCharacterizationTest {

    // Importes crudos (Double, como IMPORTE en Room v27) + su forma de cobro.
    private val raw: List<Pair<Double, PaymentMethod>> = listOf(
        1200.50 to PaymentMethod.EFECTIVO,
        850.25 to PaymentMethod.TRANSFERENCIA,
        1500.75 to PaymentMethod.EFECTIVO,
        2000.99 to PaymentMethod.EFECTIVO,
        600.50 to PaymentMethod.TRANSFERENCIA
    )

    private fun payments(): List<CollectionPayment> = raw.mapIndexed { i, (imp, method) ->
        CollectionPayment(
            id = "p$i",
            cliente = "Cliente $i",
            ventaLabel = "Venta $i",
            // ÚNICO puente Double -> Money (borde de datos)
            amount = Money.of(imp),
            method = method,
            paidAt = Instant.parse("2026-08-07T15:00:00Z"),
            synced = true
        )
    }

    @Test
    fun `el total viejo truncaba centavos, el nuevo es exacto`() {
        val oldTotal: Int = raw.sumOf { it.first }.toInt() // comportamiento viejo
        val newTotal: Money = ReportAggregator.total(payments())

        assertEquals(6152, oldTotal) // 6152.99 truncado -> 6152 (pierde $0.99)
        assertEquals(Money.of(BigDecimal("6152.99")), newTotal)
        assertEquals("$6,152.99", formatMoneyMxn(newTotal.amount))
        // El viejo y el nuevo NO coinciden: es la corrección del truncamiento.
        assertNotEquals(oldTotal.toString(), newTotal.amount.toPlainString())
    }

    @Test
    fun `el split de efectivo viejo truncaba, el nuevo conserva los centavos`() {
        val oldCash: Int = raw.filter { it.second == PaymentMethod.EFECTIVO }
            .sumOf { it.first }.toInt()
        val newCash: Money = ReportAggregator.efectivo(payments()).total

        assertEquals(4702, oldCash) // 4702.24 -> 4702 (pierde $0.24)
        assertEquals(Money.of(BigDecimal("4702.24")), newCash)
    }

    @Test
    fun `el split de transferencia viejo truncaba, el nuevo conserva los centavos`() {
        val oldTr: Int = raw.filter { it.second == PaymentMethod.TRANSFERENCIA }
            .sumOf { it.first }.toInt()
        val newTr: Money = ReportAggregator.transferencia(payments()).total

        assertEquals(1450, oldTr) // 1450.75 -> 1450 (pierde $0.75)
        assertEquals(Money.of(BigDecimal("1450.75")), newTr)
    }

    @Test
    fun `las condonaciones viejas truncaban, el nuevo conserva los centavos`() {
        val rawForg = listOf(600.50, 500.25, 300.99)
        val oldForg: Int = rawForg.sumOf { it }.toInt()
        val newForg: Money = ReportAggregator.condonado(
            rawForg.mapIndexed { i, v -> Forgiveness("Cliente $i", "ajuste", Money.of(v)) }
        ).total

        assertEquals(1401, oldForg) // 1401.74 -> 1401 (pierde $0.74)
        assertEquals(Money.of(BigDecimal("1401.74")), newForg)
    }
}
