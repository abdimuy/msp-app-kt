package com.example.msp_app.data.models.visit

import com.example.msp_app.data.local.entities.VisitEntity

fun Visit.toEntity(): VisitEntity = VisitEntity(
    ID,
    CLIENTE_ID,
    COBRADOR,
    COBRADOR_ID,
    FECHA,
    FORMA_COBRO_ID,
    LAT,
    LNG,
    NOTA,
    TIPO_VISITA,
    ZONA_CLIENTE_ID,
    IMPTE_DOCTO_CC_ID,
    GUARDADO_EN_MICROSIP
)

fun VisitEntity.toDomain(): Visit = Visit(
    ID,
    CLIENTE_ID,
    COBRADOR,
    COBRADOR_ID,
    FECHA,
    FORMA_COBRO_ID,
    LAT,
    LNG,
    NOTA,
    TIPO_VISITA,
    ZONA_CLIENTE_ID,
    IMPTE_DOCTO_CC_ID,
    GUARDADO_EN_MICROSIP
)