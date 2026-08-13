package com.example.msp_app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba trivial post-hoist: [AppDatabase] compila y vive en `:core:database`,
 * y reporta la version de esquema vigente (v28: la 27->28 agrego EPOCH a
 * `cobranza_sync_state` para el resync por generacion del sync de cobranza).
 */
class AppDatabaseTest : RobolectricTestBase() {

    @Test
    fun `AppDatabase se instancia in-memory y reporta version 28`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(28, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
