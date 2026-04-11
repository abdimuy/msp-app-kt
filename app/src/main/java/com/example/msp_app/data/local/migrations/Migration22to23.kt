package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Recreates overdue_payments_view so its SQL in sqlite_master matches
 * what Room generates from the @DatabaseView annotation.
 * The previous migration (21→22) used trimIndent() which produced
 * whitespace that differed from Room's expected format, causing
 * schema validation failures on upgrade.
 */
val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP VIEW IF EXISTS `overdue_payments_view`")
        db.execSQL(OVERDUE_PAYMENTS_VIEW_SQL)
    }
}

/**
 * Must match exactly the SQL that Room generates from the @DatabaseView
 * annotation in OverduePaymentsEntity. Copy-pasted from the annotation
 * value to ensure whitespace matches.
 */
private const val OVERDUE_PAYMENTS_VIEW_SQL = """
CREATE VIEW `overdue_payments_view` AS SELECT
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
