package com.example.msp_app.data.models.guarantee

data class Guarantee(
    val ID: Int,
    val EXTERNAL_ID: String,
    val DOCTO_CC_ID: Int,
    val ESTADO: String,
    val DESCRIPCION_FALLA: String,
    val OBSERVACIONES: String?,
    val UPLOADED: Int,
    val FECHA_SOLICITUD: String
)