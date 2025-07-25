package com.example.msp_app.data.models.guarantee

data class GuaranteeImage(
    val ID: String,
    val GARANTIA_ID: Int,
    val IMG_PATH: String,
    val IMG_MIME: String,
    val IMG_DESC: String?,
    val FECHA_SUBIDA: String
)