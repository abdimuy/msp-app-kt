package com.example.msp_app.data.models.guarantee

data class GuaranteeEvent(
    val ID: String,
    val GARANTIA_ID: String,
    val TIPO_EVENTO: String,
    val FECHA_EVENTO: String,
    val COMENTARIO: String?,
    val ENVIADO: Int
)