package com.example.msp_app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba trivial post-hoist: [AppDatabase] compila y vive en `:core:database`,
 * y reporta la version de esquema vigente (v31, dos migraciones en cadena:
 * la 29->30 —la unica del plan `pagos-y-visitas`— agrego cinco columnas
 * nullable a `Visit` (promesa y cita) y cinco tablas nuevas (comprobantes de
 * visita y de pago, recomendaciones, y las dos mitades de la ficha del
 * cliente); la 30->31 agrego el candado unico de la fila
 * —`CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT`/`REVISION`/`CORRECCION_NO_ENVIADA`/
 * `REVISION_POSTEADA` en `local_sale`— para el plan "Corregir una venta antes
 * de que suba". La 30->31 nacio como 29->30 en su rama y se renumero al
 * integrarse: ver el KDoc de `MIGRATION_30_31`).
 */
class AppDatabaseTest : RobolectricTestBase() {

    @Test
    fun `AppDatabase se instancia in-memory y reporta version 31`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(31, db.openHelper.readableDatabase.version)
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
     * `ALTER TABLE ... DEFAULT 0` de `MIGRATION_30_31`) sí lo tendría — dos
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
            assertColumnHasDefaultZero(db, "REVISION")
        } finally {
            db.close()
        }
    }

    /**
     * Ronda 3 de revisión: mismo argumento que `REVISION`, aplicado a la
     * columna nueva `CORRECCION_NO_ENVIADA` — una instalación nueva y una
     * migrada deben declarar el mismo `DEFAULT 0`.
     */
    @Test
    fun `una instalacion nueva declara CORRECCION_NO_ENVIADA con DEFAULT 0, igual que una migrada`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            assertColumnHasDefaultZero(db, "CORRECCION_NO_ENVIADA")
        } finally {
            db.close()
        }
    }

    private fun assertColumnHasDefaultZero(db: AppDatabase, columnName: String) {
        db.openHelper.readableDatabase.query("PRAGMA table_info(local_sale)").use { cursor ->
            var seen = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) != columnName) continue
                seen = true
                assertEquals(
                    "una instalacion nueva debe declarar el mismo DEFAULT 0 que la migracion",
                    "0",
                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                )
            }
            assertTrue("la columna $columnName debe existir en una instalacion nueva", seen)
        }
    }
}
