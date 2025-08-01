package com.example.msp_app.data.models.guarantee

import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity

fun Guarantee.toEntity(): GuaranteeEntity = GuaranteeEntity(
    ID,
    EXTERNAL_ID,
    DOCTO_CC_ID,
    ESTADO,
    DESCRIPCION_FALLA,
    OBSERVACIONES,
    UPLOADED,
    FECHA_SOLICITUD
)

fun GuaranteeEntity.toDomain(): Guarantee = Guarantee(
    ID,
    EXTERNAL_ID,
    DOCTO_CC_ID,
    ESTADO,
    DESCRIPCION_FALLA,
    OBSERVACIONES,
    UPLOADED,
    FECHA_SOLICITUD
)

fun GuaranteeImage.toEntity(): GuaranteeImageEntity = GuaranteeImageEntity(
    ID,
    GARANTIA_ID,
    IMG_PATH,
    IMG_MIME,
    IMG_DESC,
    FECHA_SUBIDA
)

fun GuaranteeImageEntity.toDomain(): GuaranteeImage = GuaranteeImage(
    ID,
    GARANTIA_ID,
    IMG_PATH,
    IMG_MIME,
    IMG_DESC,
    FECHA_SUBIDA
)

fun GuaranteeEvent.toEntity(): GuaranteeEventEntity = GuaranteeEventEntity(
    ID,
    GARANTIA_ID,
    TIPO_EVENTO,
    FECHA_EVENTO,
    COMENTARIO,
    ENVIADO
)

fun GuaranteeEventEntity.toDomain(): GuaranteeEvent = GuaranteeEvent(
    ID,
    GARANTIA_ID,
    TIPO_EVENTO,
    FECHA_EVENTO,
    COMENTARIO,
    ENVIADO
)