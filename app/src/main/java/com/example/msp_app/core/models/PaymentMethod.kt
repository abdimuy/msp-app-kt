package com.example.msp_app.core.models

enum class PaymentMethod(val id: Int, val label: String) {
    PAGO_EN_EFECTIVO(157, "Efectivo"),
    PAGO_CON_CHEQUE(158, "Cheque"),
    PAGO_CON_TRANSFERENCIA(52569, "Transferencia bancaria"),
    CONDONACION(137026, "Condonación"),
    SIN_METODO(0, "Sin método");

    companion object {
        private val byId = PaymentMethod.entries.associateBy { it.id }
        private val byLabel = PaymentMethod.entries.associateBy { it.label.lowercase() }

        fun fromId(id: Int): PaymentMethod = byId[id] ?: SIN_METODO
        fun fromLabel(label: String): PaymentMethod? = byLabel[label.lowercase()]
    }
}