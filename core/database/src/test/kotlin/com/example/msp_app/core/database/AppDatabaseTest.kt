package com.example.msp_app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba trivial post-hoist: [AppDatabase] compila y vive en `:core:database`,
 * y reporta la version de esquema vigente (v30: la 29->30 agrego el candado
 * unico de la fila —`CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT`/`REVISION` en
 * `local_sale`— para el plan "Corregir una venta antes de que suba").
 */
class AppDatabaseTest : RobolectricTestBase() {

    @Test
    fun `AppDatabase se instancia in-memory y reporta version 30`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(30, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    /**
     * Important #3 de la ronda 2 de revisión: sin `@ColumnInfo(defaultValue
     * = "0")` en `LocalSaleEntity.REVISION`, una instalación NUEVA (que usa
     * el `CREATE TABLE` que Room genera de la entidad — el camino que este
     * test ejercita, NO la migración) declararía `REVISION INTEGER NOT
     * NULL` SIN `DEFAULT 0`, mientras que una instalación MIGRADA (vía el
     * `ALTER TABLE ... DEFAULT 0` de `MIGRATION_29_30`) sí lo tendría — dos
     * esquemas distintos que ninguna prueba de migración puede ver, porque
     * esas solo comparan contra lo que la entidad declara. Esta prueba mira
     * el `PRAGMA` real de una base nueva, no el de una migrada.
     */
    @Test
    fun `una instalacion nueva declara REVISION con DEFAULT 0, igual que una migrada`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            assertRevisionHasDefaultZero(db)
        } finally {
            db.close()
        }
    }

    private fun assertRevisionHasDefaultZero(db: AppDatabase) {
        db.openHelper.readableDatabase.query("PRAGMA table_info(local_sale)").use { cursor ->
            var seen = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != "REVISION") continue
                seen = true
                assertEquals(
                    "una instalacion nueva debe declarar el mismo DEFAULT 0 que la migracion",
                    "0",
                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                )
            }
            assertTrue("la columna REVISION debe existir en una instalacion nueva", seen)
        }
    }
}
