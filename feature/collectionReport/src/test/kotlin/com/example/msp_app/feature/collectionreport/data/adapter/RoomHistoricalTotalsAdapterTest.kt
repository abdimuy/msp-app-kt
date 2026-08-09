package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.SuggestedGoal
import com.example.msp_app.feature.collectionreport.domain.model.Money
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private const val EFECTIVO_ID = 157
private const val CONDONACION_ID = 137026

/**
 * [RoomHistoricalTotalsAdapter] sobre la query real de Room/SQLite, con
 * [FakeClock] para anclar "hoy" de forma determinista. Verifica la suma por día
 * de negocio, el orden cronológico ascendente, la exclusión de condonaciones y
 * de días sin dinero, y su encaje con `SuggestedGoal`.
 */
class RoomHistoricalTotalsAdapterTest : RoomTestBase() {

    // Hoy = 12:00 CDMX del 15-abr (2026-04-15T18:00:00Z).
    private val clock = FakeClock.at("2026-04-15T18:00:00Z")
    private val adapter by lazy { RoomHistoricalTotalsAdapter(db.paymentDao(), clock) }

    // `raw` (no `importe`) para no disparar la regla NoDoubleForMoney: es el
    // Double crudo del schema v27 que el adapter convierte a Money en el borde.
    private fun payment(
        id: String,
        raw: Double,
        fechaHoraPago: String,
        formaCobro: Int = EFECTIVO_ID
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 91027,
        FECHA_HORA_PAGO = fechaHoraPago,
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = raw,
        LAT = 19.043415,
        LNG = -98.198234,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = formaCobro,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Rosa Elena Martinez Vazquez"
    )

    @Test
    fun `dailyTotals suma por dia en orden ascendente excluyendo condonaciones y dias vacios`() =
        runTest {
            val dao = db.paymentDao()
            // 2026-04-13: 100 + 50 = 150 cobrado.
            dao.savePayment(payment("a", 100.0, "2026-04-13T16:00:00Z"))
            dao.savePayment(payment("b", 50.0, "2026-04-13T18:00:00Z"))
            // 2026-04-14: sin pagos -> no debe aparecer.
            // 2026-04-15: 200 cobrado + una condonacion que NO suma.
            dao.savePayment(payment("c", 200.0, "2026-04-15T16:00:00Z"))
            dao.savePayment(payment("cond", 999.0, "2026-04-15T16:30:00Z", CONDONACION_ID))

            val totals = adapter.dailyTotals(days = 3)

            assertEquals(
                listOf(BigDecimal("150.00"), BigDecimal("200.00")),
                totals.map { it.amount }
            )
        }

    @Test
    fun `dailyTotals con days no positivo devuelve vacio`() = runTest {
        db.paymentDao().savePayment(payment("a", 100.0, "2026-04-15T16:00:00Z"))

        assertEquals(emptyList<Money>(), adapter.dailyTotals(days = 0))
        assertEquals(emptyList<Money>(), adapter.dailyTotals(days = -5))
    }

    @Test
    fun `dailyTotals alimenta la mediana de SuggestedGoal`() = runTest {
        val dao = db.paymentDao()
        dao.savePayment(payment("a", 100.0, "2026-04-13T16:00:00Z"))
        dao.savePayment(payment("c", 200.0, "2026-04-15T16:00:00Z"))

        val meta = SuggestedGoal.suggest(adapter.dailyTotals(days = 3))

        // Mediana de [100, 200] (tamano par) = promedio = 150.00.
        assertEquals(BigDecimal("150.00"), meta.amount)
    }
}
