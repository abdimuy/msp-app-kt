package com.example.msp_app.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS garantias_new (
                ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                EXTERNAL_ID TEXT NOT NULL,
                DOCTO_CC_ID INTEGER,
                ESTADO TEXT NOT NULL,
                DESCRIPCION_FALLA TEXT NOT NULL,
                OBSERVACIONES TEXT,
                UPLOADED INTEGER NOT NULL,
                FECHA_SOLICITUD TEXT NOT NULL,
                NOMBRE_CLIENTE TEXT,
                NOMBRE_PRODUCTO TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO garantias_new (ID, EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD)
            SELECT ID, EXTERNAL_ID, DOCTO_CC_ID, ESTADO, DESCRIPCION_FALLA, OBSERVACIONES, UPLOADED, FECHA_SOLICITUD
            FROM garantias
            """.trimIndent()
        )

        db.execSQL("DROP TABLE garantias")
        db.execSQL("ALTER TABLE garantias_new RENAME TO garantias")

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_garantias_EXTERNAL_ID ON garantias (EXTERNAL_ID)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_garantias_DOCTO_CC_ID ON garantias (DOCTO_CC_ID)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_garantias_FECHA_SOLICITUD ON garantias (FECHA_SOLICITUD)"
        )
    }
}
