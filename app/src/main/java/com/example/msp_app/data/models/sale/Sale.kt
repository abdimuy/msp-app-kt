package com.example.msp_app.data.models.sale

data class Sale(
    val DOCTO_CC_ACR_ID: Int,
    val DOCTO_CC_ID: Int,
    val FOLIO: String,
    val CLIENTE_ID: Int,
    val APLICADO: String,
    val COBRADOR_ID: Int,
    val CLIENTE: String,
    val ZONA_CLIENTE_ID: Int,
    val LIMITE_CREDITO: Double,
    val NOTAS: String,
    val ZONA_NOMBRE: String,
    val IMPORTE_PAGO_PROMEDIO: Double?, // nullable
    val TOTAL_IMPORTE: Double,
    val NUM_IMPORTES: Int,
    val FECHA: String, // ISO 8601
    val PARCIALIDAD: Int,
    val ENGANCHE: Double,
    val TIEMPO_A_CORTO_PLAZOMESES: Int,
    val MONTO_A_CORTO_PLAZO: Double,
    val VENDEDOR_1: String,
    val VENDEDOR_2: String,
    val VENDEDOR_3: String,
    val PRECIO_TOTAL: Double,
    val IMPTE_REST: Double,
    val SALDO_REST: Double,
    val FECHA_ULT_PAGO: String?, // nullable ISO 8601
    val CALLE: String,
    val CIUDAD: String,
    val ESTADO: String,
    val TELEFONO: String,
    val NOMBRE_COBRADOR: String,
    val ESTADO_COBRANZA: EstadoCobranza,
    val DIA_COBRANZA: String,
    val DIA_TEMPORAL_COBRANZA: String,
    val PRECIO_DE_CONTADO: Double,
    val AVAL_O_RESPONSABLE: String,
    val FREC_PAGO: FrecuenciaPago
)

enum class EstadoCobranza {
    PAGADO,
    NO_PAGADO,
    PENDIENTE,
    VISITADO,
    VOLVER_VISITAR
}

enum class FrecuenciaPago {
    SEMANAL,
    QUINCENAL,
    MENSUAL
}

data class SaleWithProducts(
    val DOCTO_CC_ACR_ID: Int,
    val DOCTO_CC_ID: Int,
    val FOLIO: String,
    val CLIENTE_ID: Int,
    val APLICADO: String,
    val COBRADOR_ID: Int,
    val CLIENTE: String,
    val ZONA_CLIENTE_ID: Int,
    val LIMITE_CREDITO: Double,
    val NOTAS: String,
    val ZONA_NOMBRE: String,
    val IMPORTE_PAGO_PROMEDIO: Double?,
    val TOTAL_IMPORTE: Double,
    val NUM_IMPORTES: Int,
    val FECHA: String,
    val PARCIALIDAD: Int,
    val ENGANCHE: Double,
    val TIEMPO_A_CORTO_PLAZOMESES: Int,
    val MONTO_A_CORTO_PLAZO: Double,
    val VENDEDOR_1: String,
    val VENDEDOR_2: String,
    val VENDEDOR_3: String,
    val PRECIO_TOTAL: Double,
    val IMPTE_REST: Double,
    val SALDO_REST: Double,
    val FECHA_ULT_PAGO: String?,
    val CALLE: String,
    val CIUDAD: String,
    val ESTADO: String,
    val TELEFONO: String,
    val NOMBRE_COBRADOR: String,
    val ESTADO_COBRANZA: EstadoCobranza,
    val DIA_COBRANZA: String,
    val DIA_TEMPORAL_COBRANZA: String,
    val PRECIO_DE_CONTADO: Double,
    val AVAL_O_RESPONSABLE: String,
    val FREC_PAGO: FrecuenciaPago,
    val PRODUCTOS: String
)