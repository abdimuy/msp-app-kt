package com.example.msp_app.feature.collectionreport.domain.model

/**
 * Forma de cobro de un pago, del catálogo Microsip (`FORMA_COBRO_ID`).
 *
 * Los IDs son el contrato real de datos (schema Room v27, columna
 * `FORMA_COBRO_ID`): [EFECTIVO] `157`, [CHEQUE] `158`,
 * [TRANSFERENCIA] `52569`, [CONDONACION] `137026`. Cualquier id desconocido
 * cae a [OTRO] — nunca se lanza en el borde de datos.
 *
 * La clasificación vive en un SOLO punto testeable ([fromId]) — decisión del
 * brief (parked cheque): el cheque (158) es su propia categoría, NO efectivo
 * físico, y hoy NO aparece en el duo del mockup pero SÍ suma al total cobrado
 * (ver `ReportAggregator`).
 */
enum class PaymentMethod(val formaCobroId: Int) {
    EFECTIVO(157),
    CHEQUE(158),
    TRANSFERENCIA(52569),
    CONDONACION(137026),
    OTRO(0);

    companion object {
        /** Mapea el `FORMA_COBRO_ID` crudo a un método; desconocido -> [OTRO]. */
        fun fromId(id: Int): PaymentMethod = entries.firstOrNull { it.formaCobroId == id } ?: OTRO
    }
}
