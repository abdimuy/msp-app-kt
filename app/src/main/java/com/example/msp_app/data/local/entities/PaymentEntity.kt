package com.example.msp_app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Payment",
    indices = [
        Index(value = ["DOCTO_CC_ACR_ID"]),
        Index(value = ["DOCTO_CC_ID"]),
        Index(value = ["FECHA_HORA_PAGO"])
    ]
)
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

data class OverduePaymentsEntity(
    val DOCTO_CC_ID: Int,
    val FECHA_ULT_PAGO: String,
    val NUM_IMPORTES: Int,
    val PARCIALIDADES_TRANSCURRIDAS: Double,
    val NUM_PAGOS_ATRASADOS: Double
)

