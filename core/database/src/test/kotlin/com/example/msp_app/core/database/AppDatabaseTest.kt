package com.example.msp_app.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba trivial post-hoist: [AppDatabase] compila y vive en `:core:database`,
 * y reporta la version de esquema vigente (v30: la 29->30 —la unica migracion
 * del plan `pagos-y-visitas`— agrego cinco columnas nullable a `Visit`
 * (promesa y cita) y cinco tablas nuevas: comprobantes de visita y de pago,
 * recomendaciones, y las dos mitades de la ficha del cliente).
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
