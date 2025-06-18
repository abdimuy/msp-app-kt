package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "Payment",
    indices = [
        Index(value = ["DOCTO_CC_ACR_ID"]),
        Index(value = ["DOCTO_CC_ID"]),
        Index(value = ["FECHA_HORA_PAGO"])
    ])
data class PaymentEntity(
    @PrimaryKey val ID: String,
    val COBRADOR: String,
    val DOCTO_CC_ACR_ID: Int,
    val DOCTO_CC_ID: Int,
    val FECHA_HORA_PAGO: String,
    val GUARDADO_EN_MICROSIP: Boolean,
    val IMPORTE: Double,
    val LAT: Double?,
    val LNG: Double?,
    val CLIENTE_ID: Int,
    val COBRADOR_ID: Int,
    val FORMA_COBRO_ID: Int,
    val ZONA_CLIENTE_ID: Int,
    val NOMBRE_CLIENTE: String
)

