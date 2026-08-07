package com.example.msp_app.data.local.entities

import androidx.room.DatabaseView
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
    val NOMBRE_CLIENTE: String,
    val PAGO_RECIBIDO_ID: String? = null
)

/**
 * Single source of truth for the overdue_payments_view SQL.
 * Used by both @DatabaseView and migrations to prevent whitespace mismatches.
 */
const val OVERDUE_PAYMENTS_VIEW_SQL = """
    SELECT
        base.DOCTO_CC_ID,
        base.FECHA_ULT_PAGO,
        base.NUM_IMPORTES,
        base.PARCIALIDADES_TRANSCURRIDAS,
        CASE
            WHEN ((base.PARCIALIDADES_TRANSCURRIDAS * base.PARCIALIDAD
                  - (base.PRECIO_TOTAL - base.SALDO_REST)) / base.PARCIALIDAD)
                > (base.SALDO_REST / base.PARCIALIDAD)
            THEN (base.SALDO_REST / base.PARCIALIDAD)
            ELSE ((base.PARCIALIDADES_TRANSCURRIDAS * base.PARCIALIDAD
                  - (base.PRECIO_TOTAL - base.SALDO_REST - base.ENGANCHE)) / base.PARCIALIDAD)
        END AS NUM_PAGOS_ATRASADOS
    FROM (
        SELECT
            s.DOCTO_CC_ID,
            COALESCE(MAX(p.FECHA_HORA_PAGO), s.FECHA) AS FECHA_ULT_PAGO,
            COALESCE(COUNT(p.FECHA_HORA_PAGO), 0) AS NUM_IMPORTES,
            s.SALDO_REST,
            s.PRECIO_TOTAL,
            s.ENGANCHE,
            s.PARCIALIDAD,
            s.FREC_PAGO,
            (
                JULIANDAY(
                    CASE
                        WHEN s.SALDO_REST = 0 THEN MAX(p.FECHA_HORA_PAGO)
                        ELSE DATE('now')
                    END
                ) - JULIANDAY(s.FECHA)
            ) / CASE
                WHEN s.FREC_PAGO = 'SEMANAL' THEN 7
                WHEN s.FREC_PAGO = 'QUINCENAL' THEN 15
                WHEN s.FREC_PAGO = 'MENSUAL' THEN 30
                ELSE 1
            END AS PARCIALIDADES_TRANSCURRIDAS
        FROM sales AS s
        LEFT JOIN payment AS p ON s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID
        GROUP BY s.DOCTO_CC_ID, s.FREC_PAGO
    ) AS base
"""

@DatabaseView(
    viewName = "overdue_payments_view",
    value = OVERDUE_PAYMENTS_VIEW_SQL
)
data class OverduePaymentsEntity(
    val DOCTO_CC_ID: Int,
    val FECHA_ULT_PAGO: String,
    val NUM_IMPORTES: Int,
    val PARCIALIDADES_TRANSCURRIDAS: Double,
    val NUM_PAGOS_ATRASADOS: Double
)
