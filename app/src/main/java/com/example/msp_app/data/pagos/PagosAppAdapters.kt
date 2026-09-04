package com.example.msp_app.data.pagos

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.port.LiquidacionPort
import com.example.msp_app.feature.pagos.domain.port.PeriodoDeCobroPort
import com.example.msp_app.features.sales.domain.models.Settlement
import com.example.msp_app.features.sales.domain.models.calculatePaymentResult
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Implementación real de [LiquidacionPort], provista en el composition root de
 * `:app` (precedente `UserCyclePort` → `FirebaseUserCycleAdapter`).
 *
 * **No reimplementa el cálculo.** `calculatePaymentResult`
 * (`features/sales/domain/models/SettlementCalculator.kt`) es lógica de dinero
 * que ya corre en producción detrás de "Hoy liquida con"; la Task 16 la
 * CONSUME. Reescribirla sería tocar dinero que esta tarea no vino a tocar, y
 * tenerla dos veces garantizaría que las dos versiones se despeguen.
 *
 * `PaymentResults.amount` es un `Double` — el borde donde cruza a [Money] es
 * esta clase y solo esta, vía `Money.of(Double)`, que usa `BigDecimal.valueOf`
 * y nunca el constructor `BigDecimal(double)`.
 *
 * Una liquidación en cero o negativa NO es una oferta: significa que la venta
 * ya no admite liquidación anticipada (o que ya está saldada). Se devuelve
 * `null` y la sección no se pinta, en vez de ofrecerle al cobrador cerrar una
 * deuda por cero pesos.
 */
class SettlementLiquidacionAdapter(
    private val saleDao: SaleDao
) : LiquidacionPort {

    override suspend fun liquidacionDe(ventaId: Int): Liquidacion? {
        val venta = saleDao.getById(ventaId) ?: return null
        val resultado = calculatePaymentResult(
            Settlement(
                cashPrice = venta.PRECIO_DE_CONTADO,
                shortTermAmount = venta.MONTO_A_CORTO_PLAZO,
                totalPrice = venta.PRECIO_TOTAL,
                remainingBalance = venta.SALDO_REST,
                date = fechaParaElCalculo(venta.FECHA)
            )
        )
        val monto = Money.of(resultado.amount)
        if (monto <= Money.ZERO || venta.SALDO_REST <= 0.0) return null
        return Liquidacion(
            monto = monto,
            vigenteHasta = vigenciaDe(resultado.validUntil),
            categoria = resultado.category
        )
    }

    /**
     * `SettlementCalculator` parsea `dd/MM/yyyy` (formato de pantalla), pero
     * `sales.FECHA` viaja en el formato de cable que `AppTime` entiende. Se
     * traduce aquí en vez de tocar el calculador: la fecha es la MISMA, solo
     * cambia cómo se escribe, y `AppTime` es la única fuente de fechas del repo.
     */
    private fun fechaParaElCalculo(fechaEnCable: String): String {
        val fecha = AppTime.parseWireFormatOrNull(fechaEnCable)?.let(AppTime::toBusinessDate)
            ?: return fechaEnCable
        return DIA_MES_ANIO.format(fecha)
    }

    private fun vigenciaDe(texto: String): LocalDate? = runCatching {
        LocalDate.parse(texto, DIA_MES_ANIO)
    }.getOrNull()

    private companion object {
        val DIA_MES_ANIO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "MX"))
    }
}

/**
 * Implementación real de [PeriodoDeCobroPort]. Delega en el
 * [UserCyclePort] que `:app` ya provee (`FirebaseUserCycleAdapter`) en vez de
 * hablarle a Firestore por segunda vez: el `FECHA_CARGA_INICIAL` que ve el
 * reporte de cobranza y el que ven las pantallas de detalle tienen que ser el
 * mismo instante, o dos pantallas de la misma app derivarían periodos
 * distintos.
 *
 * Aplana [com.example.msp_app.feature.collectionreport.domain.port.CycleStart]
 * a `Instant?` a propósito: aquí "no hay dato" y "no se pudo leer" llevan a la
 * MISMA pantalla —sin ventana no hay derivación— y el que sí distingue los dos
 * casos, para reintentar, es el reporte de cobranza. La ausencia no queda en
 * silencio: `DerivarEstadoDelPeriodo` emite
 * `pagos_periodo_desconocido` cuando llega `null`.
 */
class UserCyclePeriodoDeCobroAdapter(
    private val userCyclePort: UserCyclePort
) : PeriodoDeCobroPort {

    override suspend fun inicioDelPeriodo(): Instant? = userCyclePort.cycleStart().instantOrNull
}
