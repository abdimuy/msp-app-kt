package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Tres columnas nuevas en `local_sale` para el plan "Corregir una venta antes
 * de que suba" (`docs/superpowers/plans/2026-09-20-editar-venta-antes-de-subir.md`,
 * sección "El mecanismo de la carrera"): el dueño puede corregir una venta
 * capturada sin señal MIENTRAS el subidor intenta mandarla, y las dos cosas
 * no pueden pisarse.
 *
 * - `EDIT_CLAIM_ID` (nullable): UUID del reclamo vivo. `NULL` = nadie está
 *   corrigiendo. Lo escribe `LocalSaleDao.claimForEdit` con un solo `UPDATE`
 *   guardado por predicado — la atomicidad vive en SQLite, no en Kotlin.
 * - `EDIT_CLAIMED_AT` (nullable): epoch ms en que se acuñó el reclamo. Con
 *   esto el reclamo tiene arrendamiento: vencido, el subidor recupera la
 *   venta solo. Sin arrendamiento, la app muriendo con el editor abierto
 *   dejaría la venta retenida para siempre — dinero perdido en vez de una
 *   carrera incómoda.
 * - `REVISION` (`NOT NULL DEFAULT 0`): correcciones commiteadas, sólo sube.
 *   0 para toda fila preexistente: nadie ha corregido nada todavía.
 *
 * Solo agrega columnas: ninguna tabla se recrea, así que las ventas
 * pendientes del dueño (y sus productos/combos/imágenes) quedan intactas.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_sale ADD COLUMN EDIT_CLAIM_ID TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN EDIT_CLAIMED_AT INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN REVISION INTEGER NOT NULL DEFAULT 0")
    }
}
