package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.entities.PaymentEntity
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
    private val paymentDao: PaymentDao
) : PaymentsPort {

    override suspend fun paymentsIn(range: DateRange): List<CollectionPayment> = paymentDao
        .getPaymentsByDate(range.startIso, range.endExclusiveIso)
        .map { it.toCollectionPayment() }

    override suspend fun forgivenessIn(range: DateRange): List<Forgiveness> = paymentDao
        .getForgivenessByDate(range.startIso, range.endExclusiveIso)
        .map { it.toForgiveness() }

    override suspend fun paymentsGroupedByDaySince(
        startIso: String
    ): Map<String, List<CollectionPayment>> = paymentDao
        .getPaymentsGroupedByDaySince(startIso)
        .mapValues { (_, rows) -> rows.map { it.toCollectionPayment() } }

    override suspend fun pendingCount(): Int = paymentDao.getPendingPayments().size
}

/**
 * `IMPORTE: Double` -> [Money] en el borde. `ventaLabel` usa
 * `DOCTO_CC_ACR_ID` (referencia de la venta): el schema v27 de `Payment` no
 * guarda el nombre comercial de la venta; enriquecerlo requeriría un join con
 * `sales` (deferido, YAGNI — no lo exige el mockup).
 */
private fun PaymentEntity.toCollectionPayment(): CollectionPayment = CollectionPayment(
    id = ID,
    cliente = NOMBRE_CLIENTE,
    ventaLabel = DOCTO_CC_ACR_ID.toString(),
    amount = Money.of(IMPORTE),
    method = PaymentMethod.fromId(FORMA_COBRO_ID),
    paidAt = AppTime.parseWireFormat(FECHA_HORA_PAGO),
    synced = GUARDADO_EN_MICROSIP
)

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
