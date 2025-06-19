package com.example.msp_app.data.models.payment

import com.example.msp_app.data.local.entities.PaymentEntity

fun PaymentApi.toEntity(): PaymentEntity = PaymentEntity(
    ID,
    COBRADOR,
    DOCTO_CC_ACR_ID,
    DOCTO_CC_ID,
    FECHA_HORA_PAGO,
    GUARDADO_EN_MICROSIP,
    IMPORTE,
    try { LAT.toDouble() } catch (e: NumberFormatException) { null },
    try { LNG.toDouble() } catch (e: NumberFormatException) { null },
    CLIENTE_ID,
    COBRADOR_ID,
    FORMA_COBRO_ID,
    ZONA_CLIENTE_ID,
    NOMBRE_CLIENTE
)

fun PaymentEntity.toDomain(): PaymentApi = PaymentApi(
    ID,
    COBRADOR,
    DOCTO_CC_ACR_ID,
    DOCTO_CC_ID,
    FECHA_HORA_PAGO,
    GUARDADO_EN_MICROSIP,
    IMPORTE,
    LAT.toString(),
    LNG.toString(),
    CLIENTE_ID,
    COBRADOR_ID,
    FORMA_COBRO_ID,
    ZONA_CLIENTE_ID,
    NOMBRE_CLIENTE
)

