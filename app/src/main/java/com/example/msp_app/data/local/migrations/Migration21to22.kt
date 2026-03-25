package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIEW IF NOT EXISTS `overdue_payments_view` AS
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
            """.trimIndent()
        )
    }
}
