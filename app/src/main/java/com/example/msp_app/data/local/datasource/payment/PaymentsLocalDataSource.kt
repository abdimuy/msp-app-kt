package com.example.msp_app.data.local.datasource.payment

import android.content.Context
import androidx.room.Transaction
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.OverduePaymentsEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PaymentsLocalDataSource @Inject constructor(
    private val paymentDao: PaymentDao,
    private val saleDao: SaleDao
) {
    /**
     * Puente legacy: los callers `viewModel()` y los workers aún no-Hilt
     * siguen construyendo con `context` sin cambios. Delega en la MISMA
     * instancia que `@Inject` recibe vía [com.example.msp_app.core.database.di.DatabaseModule]
     * — ambos resuelven a [AppDatabase.getInstance], una sola conexión a
     * `msp_db`. No abre un builder nuevo: eso duplicaría la ruta de escritura
     * al dinero.
     */
    constructor(context: Context) : this(
        AppDatabase.getInstance(context).paymentDao(),
        AppDatabase.getInstance(context).saleDao()
    )

    suspend fun getPaymentById(id: String): PaymentEntity? {
        return paymentDao.getPaymentById(id)
    }

    suspend fun getPaymentsBySaleId(saleId: Int): List<PaymentEntity> {
        return paymentDao.getPaymentsBySaleId(saleId)
    }

    suspend fun getPaymentsByDate(start: String, end: String): List<PaymentEntity> {
        return paymentDao.getPaymentsByDate(start, end)
    }

    suspend fun getForgivenessByDate(start: String, end: String): List<PaymentEntity> {
        return paymentDao.getForgivenessByDate(start, end)
    }

    suspend fun getPaymentsGroupedByDaySince(startDate: String): Map<String, List<PaymentEntity>> {
        return paymentDao.getPaymentsGroupedByDaySince(startDate)
    }

    /**
     * Reactive variant: subscribers receive a fresh day-grouped map every
     * time the `Payment` table changes within the filter window. Preferred
     * over the suspend variant for UI consumers that need to stay in sync
     * with periodic background syncs.
     */
    fun observePaymentsGroupedByDaySince(
        startDate: String
    ): Flow<Map<String, List<PaymentEntity>>> {
        return paymentDao.observePaymentsGroupedByDaySince(startDate)
    }

    suspend fun getLocationsGroupedBySaleId(): List<PaymentLocationsGroup> {
        return paymentDao.getAllLocations()
            .groupBy { it.DOCTO_CC_ACR_ID }
            .map { (saleId, list) -> PaymentLocationsGroup(saleId, list) }
    }

    suspend fun getAdjustedPaymentPercentage(startDate: String): Double {
        return paymentDao.getAdjustedPaymentPercentage(startDate) ?: 0.0
    }

    /**
     * Variante REACTIVA del porcentaje ajustado (defecto D6). El `NULL` de SQL —que aparece
     * cuando todavía no hay ninguna fila que sumar— se traduce a `0.0` igual que en la versión
     * one-shot, pero aquí ese `0.0` deja de ser definitivo: en cuanto entran pagos o ventas,
     * Room re-emite y el porcentaje se recalcula solo.
     */
    fun observeAdjustedPaymentPercentage(startDate: String): Flow<Double> {
        return paymentDao.observeAdjustedPaymentPercentage(startDate).map { it ?: 0.0 }
    }

    suspend fun getSuggestedAmountsBySaleId(saleId: Int): List<Int> {
        return paymentDao.getSuggestedAmountsBySaleId(saleId)
    }

    suspend fun getOverduePayments(): List<OverduePaymentsEntity> {
        return paymentDao.getOverduePayments()
    }

    suspend fun getPagosAtrasadosBySaleId(saleId: Int): OverduePaymentsEntity? {
        return paymentDao.getOverduePaymentBySaleId(saleId)
    }

    suspend fun savePayment(payment: PaymentEntity) {
        paymentDao.savePayment(payment)
    }

    /**
     * Refresca la caché local con el set de pagos del servidor SIN perder
     * el dinero capturado localmente que aún no se sube.
     *
     * **Fix money-path (bug pre-existente, dormido):** antes hacía
     * `deleteAll()` (`DELETE FROM payment`), que borraba TODA la tabla —
     * incluidos los pagos pendientes de subir (`GUARDADO_EN_MICROSIP = 0`).
     * Ningún caller de producción usaba este wrapper (los reales,
     * `CobranzaReconciler`/`CobranzaSyncManager`, llaman `paymentDao.saveAll`
     * directo), así que el bug nunca detonó, pero el primer caller que lo
     * usara habría destruido dinero no sincronizado.
     *
     * Ahora usa [PaymentDao.deleteUploaded] (`DELETE ... WHERE
     * GUARDADO_EN_MICROSIP = 1`): borra solo lo ya confirmado por el
     * servidor y luego re-inserta el set entrante con REPLACE
     * (`OnConflictStrategy.REPLACE` en [PaymentDao.saveAll]). Semántica:
     * - Los pendientes (`= 0`) sobreviven al DELETE, ya que este solo toca
     *   filas subidas.
     * - Si el servidor reenvía un pago cuyo ID coincide con uno pendiente,
     *   el REPLACE lo pisa con la versión del servidor (que ya trae
     *   `GUARDADO_EN_MICROSIP = 1`): es correcto, ese pago ya está en el
     *   servidor y deja de estar pendiente — no se pierde dinero, se
     *   reconcilia.
     */
    suspend fun saveAll(payments: List<PaymentEntity>) {
        paymentDao.deleteUploaded()
        paymentDao.saveAll(payments)
    }

    suspend fun getPendingPayments(): List<PaymentEntity> {
        return paymentDao.getPendingPayments()
    }

    suspend fun getAllPayments(): List<PaymentEntity> {
        return paymentDao.getAllPayments()
    }

    suspend fun changePaymentStatus(id: String, status: Boolean) {
        paymentDao.updateEstado(
            id,
            if (status) 1 else 0
        )
    }

    /**
     * Guarda el DOCTO_CC_ID que devolvió el servidor al aplicar el pago.
     * Sin él el pago local y el que baja del servidor no se reconocen como el
     * mismo y los totales de pantalla salen inflados.
     */
    suspend fun updatePaymentDoctoCcId(id: String, doctoCcId: Int) {
        paymentDao.updateDoctoCcId(id, doctoCcId)
    }

    suspend fun updatePaymentLocation(id: String, lat: Double, lng: Double) {
        paymentDao.updateLocation(id, lat, lng)
    }

    @Transaction
    suspend fun insertPaymentAndUpdateSale(
        payment: PaymentEntity,
        saleId: Int,
        newAmount: Double,
        newEstadoCobranza: EstadoCobranza
    ) {
        paymentDao.savePayment(payment)
        saleDao.updateTotal(saleId, newAmount, newEstadoCobranza)
    }

    suspend fun saveAndEnqueue(
        payment: PaymentEntity,
        saleId: Int,
        newAmount: Double,
        newEstadoCobranza: EstadoCobranza
    ) {
        insertPaymentAndUpdateSale(payment, saleId, newAmount, newEstadoCobranza)
    }
}
