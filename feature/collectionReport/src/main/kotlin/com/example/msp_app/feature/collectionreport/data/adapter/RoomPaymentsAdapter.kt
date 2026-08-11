package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleRefRow
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort

/**
 * Adapter Room de [PaymentsPort] sobre [PaymentDao] (schema v27, inmutable).
 *
 * Es el ÚNICO borde donde `IMPORTE: Double` cruza a [Money] vía
 * [Money.of] `(Double)`; de aquí en adelante la aritmética es exacta. El ruteo
 * de forma de cobro lo hace el DAO en SQL: [PaymentDao.getPaymentsByDate]
 * filtra `FORMA_COBRO_ID IN (157, 158, 52569)` (excluye la condonación 137026),
 * y [PaymentDao.getForgivenessByDate] devuelve SOLO 137026 — así una
 * condonación jamás llega a [paymentsIn].
 *
 * `paidAt` se parsea con [AppTime.parseWireFormat] (estricto): las filas ya
 * pasaron el filtro de rango medio-abierto del DAO (`>= :start AND < :end`
 * sobre strings RFC3339), por lo que su `FECHA_HORA_PAGO` es un wire válido;
 * una fila con fecha corrupta ordena fuera de cualquier rango de negocio y no
 * es devuelta. Fallar ruidosamente ante una fila genuinamente corrupta es
 * preferible a subcontar dinero en silencio.
 */
class RoomPaymentsAdapter(
    private val paymentDao: PaymentDao,
    private val saleDao: SaleDao
) : PaymentsPort {

    override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> = paymentDao
        .getPaymentsByDate(range.startIso, range.endExclusiveIso)
        .enrichWithSale()

    override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> = paymentDao
        .getForgivenessByDate(range.startIso, range.endExclusiveIso)
        .map { it.toForgiveness() }

    override suspend fun paymentsGroupedByDaySince(
        startIso: String
    ): Map<String, List<CollectionPayment>> {
        val grouped = paymentDao.getPaymentsGroupedByDaySince(startIso)
        val refs = saleRefs(grouped.values.flatten())
        return grouped.mapValues { (_, rows) -> rows.map { it.toCollectionPayment(refs) } }
    }

    override suspend fun pendingCount(): Int = paymentDao.getPendingPayments().size

    /**
     * Resuelve la venta (folio + saldo) de cada pago con UN solo query batch a `sales` por
     * `DOCTO_CC_ACR_ID` (evita el N+1 de un `getById` por pago), y mapea cada [PaymentEntity]
     * a dominio con su venta ya resuelta. Un pago cuya venta ya no está en local queda con
     * `folio = ""` / `saldo = null` (nunca inventado).
     */
    private suspend fun List<PaymentEntity>.enrichWithSale(): List<CollectionPayment> {
        val refs = saleRefs(this)
        return map { it.toCollectionPayment(refs) }
    }

    private suspend fun saleRefs(payments: List<PaymentEntity>): Map<Int, SaleRefRow> {
        val acrIds = payments.map { it.DOCTO_CC_ACR_ID }.distinct()
        if (acrIds.isEmpty()) return emptyMap()
        return saleDao.getSaleRefsByAcrIds(acrIds).associateBy { it.saleId }
    }
}

/**
 * `IMPORTE: Double` -> [Money] en el borde. `ventaLabel` usa `DOCTO_CC_ACR_ID` (referencia
 * numérica de la venta). El folio comercial y el saldo restante se resuelven con un join a
 * `sales` sobre `DOCTO_CC_ACR_ID` ([refs], el mismo cruce que la app en `PaymentTicketScreen`);
 * si la venta ya no está en local (p. ej. saldada y prunada), `folio`/`saldo` quedan vacíos —
 * la UI omite esas líneas, nunca las inventa.
 */
private fun PaymentEntity.toCollectionPayment(refs: Map<Int, SaleRefRow>): CollectionPayment {
    val ref = refs[DOCTO_CC_ACR_ID]
    return CollectionPayment(
        id = ID,
        cliente = NOMBRE_CLIENTE,
        ventaLabel = DOCTO_CC_ACR_ID.toString(),
        amount = Money.of(IMPORTE),
        method = PaymentMethod.fromId(FORMA_COBRO_ID),
        paidAt = AppTime.parseWireFormat(FECHA_HORA_PAGO),
        synced = GUARDADO_EN_MICROSIP,
        folio = ref?.folio.orEmpty(),
        saldo = ref?.let { Money.of(it.saldo) },
        saleId = DOCTO_CC_ACR_ID
    )
}

/**
 * Condonación desde una fila de `Payment` (forma 137026). `motivo` queda vacío:
 * el schema v27 no tiene columna de razón de condonación (contrato auditado);
 * es un hueco documentado para enriquecer en una tarea posterior si el sheet lo
 * necesita. **Re-auditado en fix round 1 (Plan 5 Task 8):** tampoco existe en el backend Go
 * (msp-api) — `internal/cobranza/domain/saldo.go`/`venta.go` modelan la condonación como
 * monto puro, sin texto libre. La UI (`ReportSheetContent.condonadoSheet`) SIEMPRE debe
 * tratar un `motivo` vacío como "sin línea", nunca fabricar uno — ver KDoc de
 * [Forgiveness.motivo].
 */
private fun PaymentEntity.toForgiveness(): Forgiveness = Forgiveness(
    cliente = NOMBRE_CLIENTE,
    motivo = "",
    amount = Money.of(IMPORTE)
)
