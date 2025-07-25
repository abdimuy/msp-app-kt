package com.example.msp_app.data.models.sale

import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.local.entities.SaleWithProductsEntity

fun Sale.toEntity(): SaleEntity = SaleEntity(
    DOCTO_CC_ACR_ID,
    DOCTO_CC_ID,
    FOLIO,
    CLIENTE_ID,
    APLICADO,
    COBRADOR_ID,
    CLIENTE,
    ZONA_CLIENTE_ID,
    LIMITE_CREDITO,
    NOTAS,
    ZONA_NOMBRE,
    IMPORTE_PAGO_PROMEDIO,
    TOTAL_IMPORTE,
    NUM_IMPORTES,
    FECHA,
    PARCIALIDAD,
    ENGANCHE,
    TIEMPO_A_CORTO_PLAZOMESES,
    MONTO_A_CORTO_PLAZO,
    VENDEDOR_1,
    VENDEDOR_2,
    VENDEDOR_3,
    PRECIO_TOTAL,
    IMPTE_REST,
    SALDO_REST,
    FECHA_ULT_PAGO,
    CALLE,
    CIUDAD,
    ESTADO,
    TELEFONO,
    NOMBRE_COBRADOR,
    ESTADO_COBRANZA.name,
    DIA_COBRANZA,
    DIA_TEMPORAL_COBRANZA,
    PRECIO_DE_CONTADO,
    AVAL_O_RESPONSABLE,
    FREC_PAGO?.name ?: FrecuenciaPago.SEMANAL.name
)

fun SaleEntity.toDomain(): Sale = Sale(
    DOCTO_CC_ACR_ID,
    DOCTO_CC_ID,
    FOLIO,
    CLIENTE_ID,
    APLICADO,
    COBRADOR_ID,
    CLIENTE,
    ZONA_CLIENTE_ID,
    LIMITE_CREDITO,
    NOTAS,
    ZONA_NOMBRE,
    IMPORTE_PAGO_PROMEDIO,
    TOTAL_IMPORTE,
    NUM_IMPORTES,
    FECHA,
    PARCIALIDAD,
    ENGANCHE,
    TIEMPO_A_CORTO_PLAZOMESES,
    MONTO_A_CORTO_PLAZO,
    VENDEDOR_1,
    VENDEDOR_2,
    VENDEDOR_3,
    PRECIO_TOTAL,
    IMPTE_REST,
    SALDO_REST,
    FECHA_ULT_PAGO,
    CALLE,
    CIUDAD,
    ESTADO,
    TELEFONO,
    NOMBRE_COBRADOR,
    EstadoCobranza.valueOf(ESTADO_COBRANZA),
    DIA_COBRANZA,
    DIA_TEMPORAL_COBRANZA,
    PRECIO_DE_CONTADO,
    AVAL_O_RESPONSABLE,
    FrecuenciaPago.valueOf(FREC_PAGO.toString())
)

fun SaleWithProductsEntity.toDomain(): SaleWithProducts =
    SaleWithProducts(
        DOCTO_CC_ACR_ID,
        DOCTO_CC_ID,
        FOLIO,
        CLIENTE_ID,
        APLICADO,
        COBRADOR_ID,
        CLIENTE,
        ZONA_CLIENTE_ID,
        LIMITE_CREDITO,
        NOTAS,
        ZONA_NOMBRE,
        IMPORTE_PAGO_PROMEDIO,
        TOTAL_IMPORTE,
        NUM_IMPORTES,
        FECHA,
        PARCIALIDAD,
        ENGANCHE,
        TIEMPO_A_CORTO_PLAZOMESES,
        MONTO_A_CORTO_PLAZO,
        VENDEDOR_1,
        VENDEDOR_2,
        VENDEDOR_3,
        PRECIO_TOTAL,
        IMPTE_REST,
        SALDO_REST,
        FECHA_ULT_PAGO,
        CALLE,
        CIUDAD,
        ESTADO,
        TELEFONO,
        NOMBRE_COBRADOR,
        ESTADO_COBRANZA = EstadoCobranza.valueOf(ESTADO_COBRANZA),
        DIA_COBRANZA,
        DIA_TEMPORAL_COBRANZA,
        PRECIO_DE_CONTADO,
        AVAL_O_RESPONSABLE,
        FREC_PAGO = FrecuenciaPago.valueOf(FREC_PAGO.toString()),
        PRODUCTOS = PRODUCTOS.toString(),
        NUM_PAGOS_ATRASADOS = NUM_PAGOS_ATRASADOS ?: 0
    )

fun SaleWithProducts.toSale(): Sale =
    Sale(
        DOCTO_CC_ACR_ID = DOCTO_CC_ACR_ID,
        DOCTO_CC_ID = DOCTO_CC_ID,
        FOLIO = FOLIO,
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = APLICADO,
        COBRADOR_ID = COBRADOR_ID,
        CLIENTE = CLIENTE,
        ZONA_CLIENTE_ID = ZONA_CLIENTE_ID,
        LIMITE_CREDITO = LIMITE_CREDITO,
        NOTAS = NOTAS,
        ZONA_NOMBRE = ZONA_NOMBRE,
        IMPORTE_PAGO_PROMEDIO = IMPORTE_PAGO_PROMEDIO,
        TOTAL_IMPORTE = TOTAL_IMPORTE,
        NUM_IMPORTES = NUM_IMPORTES,
        FECHA = FECHA,
        PARCIALIDAD = PARCIALIDAD,
        ENGANCHE = ENGANCHE,
        TIEMPO_A_CORTO_PLAZOMESES = TIEMPO_A_CORTO_PLAZOMESES,
        MONTO_A_CORTO_PLAZO = MONTO_A_CORTO_PLAZO,
        VENDEDOR_1 = VENDEDOR_1,
        VENDEDOR_2 = VENDEDOR_2,
        VENDEDOR_3 = VENDEDOR_3,
        PRECIO_TOTAL = PRECIO_TOTAL,
        IMPTE_REST = IMPTE_REST,
        SALDO_REST = SALDO_REST,
        FECHA_ULT_PAGO = FECHA_ULT_PAGO,
        CALLE = CALLE,
        CIUDAD = CIUDAD,
        ESTADO = ESTADO,
        TELEFONO = TELEFONO,
        NOMBRE_COBRADOR = NOMBRE_COBRADOR,
        ESTADO_COBRANZA = ESTADO_COBRANZA,
        DIA_COBRANZA = DIA_COBRANZA,
        DIA_TEMPORAL_COBRANZA = DIA_TEMPORAL_COBRANZA,
        PRECIO_DE_CONTADO = PRECIO_DE_CONTADO,
        AVAL_O_RESPONSABLE = AVAL_O_RESPONSABLE,
        FREC_PAGO = FREC_PAGO
    )