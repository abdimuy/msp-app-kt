package com.example.msp_app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba trivial post-hoist: [AppDatabase] compila y vive en `:core:database`,
 * y reporta la version de esquema vigente (v30: la 29->30 agrego el reclamo
 * de edicion —`EDIT_CLAIM_ID`/`EDIT_CLAIMED_AT`/`REVISION` en `local_sale`—
 * para el plan "Corregir una venta antes de que suba").
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
}
