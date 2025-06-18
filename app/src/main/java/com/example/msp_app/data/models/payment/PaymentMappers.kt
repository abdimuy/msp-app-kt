package com.example.msp_app.data.models.payment

import com.example.msp_app.data.local.entities.PaymentEntity

fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    ID,
    COBRADOR,
    DOCTO_CC_ACR_ID,
    DOCTO_CC_ID,
    FECHA_HORA_PAGO,
    GUARDADO_EN_MICROSIP,
    IMPORTE,
    LAT,
    LNG,
    CLIENTE_ID,
    COBRADOR_ID,
    FORMA_COBRO_ID,
    ZONA_CLIENTE_ID,
    NOMBRE_CLIENTE
)

fun PaymentEntity.toDomain(): Payment = Payment(
    ID,
    COBRADOR,
    DOCTO_CC_ACR_ID,
    DOCTO_CC_ID,
    FECHA_HORA_PAGO,
    GUARDADO_EN_MICROSIP,
    IMPORTE,
    LAT,
    LNG,
    CLIENTE_ID,
    COBRADOR_ID,
    FORMA_COBRO_ID,
    ZONA_CLIENTE_ID,
    NOMBRE_CLIENTE
)
