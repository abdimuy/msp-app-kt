package com.example.msp_app.data.models.visit

data class Visit(
    val ID: String,
    val CLIENTE_ID: Int,
    val COBRADOR: String = "",
    val COBRADOR_ID: Int,
    val FECHA: String,
    val FORMA_COBRO_ID: Int,
    val LAT: Double,
    val LNG: Double,
    val NOTA: String? = null,
    val TIPO_VISITA: String,
    val ZONA_CLIENTE_ID: Int,
    val IMPTE_DOCTO_CC_ID: Int = 0,
    val GUARDADO_EN_MICROSIP: Int = 0
)