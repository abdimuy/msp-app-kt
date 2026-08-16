package com.example.msp_app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba trivial post-hoist: [AppDatabase] compila y vive en `:core:database`,
 * y reporta la version de esquema vigente (v29: la 28->29 agrego AFTER_ID a
 * `cobranza_sync_state` —la otra mitad del cursor de paginacion— y el indice
 * sobre `Payment.PAGO_RECIBIDO_ID` que la 26->27 dejo pendiente).
 */
class AppDatabaseTest : RobolectricTestBase() {

    @Test
    fun `AppDatabase se instancia in-memory y reporta version 29`() {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        try {
            assertEquals(29, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
