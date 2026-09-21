package com.example.msp_app.core.database.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Cuatro columnas nuevas en `local_sale` para el plan "Corregir una venta
 * antes de que suba" (`docs/superpowers/plans/2026-09-20-editar-venta-antes-de-subir.md`,
 * sección "El mecanismo de la carrera" + la ronda 2 de revisión que cierra una
 * carrera adicional entre editar y subir): el dueño puede corregir una venta
 * capturada sin señal MIENTRAS el subidor intenta mandarla, y las dos cosas
 * no pueden pisarse.
 *
 * - `CLAIM_ID` (nullable): UUID del candado vivo de la fila. `NULL` = nadie
 *   la tiene. Es UN SOLO candado que puede tomar la edición o la subida,
 *   nunca las dos — mutua exclusión por construcción (una sola columna), no
 *   por dos predicados que alguien pueda desincronizar. Lo escribe
 *   `LocalSaleDao.claimForEdit`/`claimForUpload` con un solo `UPDATE`
 *   guardado por predicado — la atomicidad vive en SQLite, no en Kotlin.
 * - `CLAIM_KIND` (nullable): `'EDIT'` o `'UPLOAD'`. Dice qué arrendamiento
 *   aplica para decidir si `CLAIM_ID` venció (cada tipo tiene el suyo).
 * - `CLAIMED_AT` (nullable): epoch ms en que se acuñó el candado. Con esto
 *   el candado tiene arrendamiento: vencido, el otro lado lo recupera solo.
 *   Sin arrendamiento, la app muriendo con el editor abierto (o un POST que
 *   nunca vuelve) dejaría la venta retenida para siempre — dinero perdido en
 *   vez de una carrera incómoda.
 * - `REVISION` (`NOT NULL DEFAULT 0`): correcciones commiteadas, sólo sube.
 *   0 para toda fila preexistente: nadie ha corregido nada todavía.
 *
 * Solo agrega columnas: ninguna tabla se recrea, así que las ventas
 * pendientes del dueño (y sus productos/combos/imágenes) quedan intactas.
 */
val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CLAIM_ID TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CLAIM_KIND TEXT")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN CLAIMED_AT INTEGER")
        db.execSQL("ALTER TABLE local_sale ADD COLUMN REVISION INTEGER NOT NULL DEFAULT 0")
    }
}
