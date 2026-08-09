package com.example.msp_app.feature.collectionreport.domain.model

/**
 * Condonación — monto perdonado a un cliente (saldo mínimo, ajuste de
 * intereses, redondeo de cierre, etc.).
 *
 * Se modela aparte de [CollectionPayment] a propósito: una condonación NO es
 * efectivo cobrado, así que NO suma al total cobrado ni a los tiles de
 * efectivo/transferencia; vive en su propio chip "Condonado".
 *
 * @property cliente nombre del cliente.
 * @property motivo razón de la condonación (para el sheet). **Vacío en producción hoy** —
 *   auditado en fix round 1 (Plan 5 Task 8): el schema v27 de `Payment` (`:core:database`,
 *   inmutable) no tiene columna de razón de condonación, y el backend Go (msp-api,
 *   `internal/cobranza/domain/saldo.go`/`venta.go`) tampoco modela una — la condonación
 *   (`FORMA_COBRO_ID` 137026) es solo un monto en todo el pipeline actual, sin campo de texto
 *   libre en ningún punto. Deferred-enrichment real, no un placeholder temporal a corto
 *   plazo: requeriría una columna nueva en Microsip/el sync antes de poder llenarse. Un valor
 *   vacío NO debe mostrarse como si fuera un motivo real (la UI lo omite, no lo rellena).
 * @property amount monto condonado, exacto ([Money]).
 */
data class Forgiveness(
    val cliente: String,
    val motivo: String,
    val amount: Money
)
