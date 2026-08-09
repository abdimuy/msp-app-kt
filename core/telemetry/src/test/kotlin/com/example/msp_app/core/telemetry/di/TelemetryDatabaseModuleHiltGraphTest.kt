package com.example.msp_app.core.telemetry.di

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.telemetry.queue.TelemetryDatabase
import com.example.msp_app.core.telemetry.queue.TelemetryEventDao
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 3: prueba que el grafo Hilt REAL resuelve [TelemetryDatabase] y su
 * DAO a través de [TelemetryDatabaseModule], y — la parte que este test
 * existe para plantar — que `provideTelemetryDatabase` DELEGA en
 * [TelemetryDatabase.getInstance] en vez de abrir una segunda conexión
 * propia a `telemetry_db`. Mismo patrón que
 * `com.example.msp_app.core.database.di.DatabaseModuleHiltGraphTest`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class TelemetryDatabaseModuleHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var telemetryDatabase: TelemetryDatabase

    @Inject
    lateinit var telemetryEventDao: TelemetryEventDao

    @Before
    fun clearSingletonBefore() {
        // `TelemetryDatabase.instance` es un companion `Volatile var`
        // (estado JVM-wide); Robolectric puede reusar el classloader entre
        // métodos de esta clase, así que se limpia antes de cada test para
        // que ninguno herede el estado del anterior.
        TelemetryDatabase.clearInstance()
    }

    @After
    fun clearSingletonAfter() {
        TelemetryDatabase.clearInstance()
    }

    @Test
    fun `el grafo Hilt resuelve TelemetryDatabase y su DAO`() {
        hiltRule.inject()

        assertNotNull(telemetryDatabase)
        assertNotNull(telemetryEventDao)
    }

    @Test
    fun `la TelemetryDatabase inyectada es la MISMA instancia que getInstance (una sola conexion a telemetry_db)`() {
        hiltRule.inject()

        val direct = TelemetryDatabase.getInstance(ApplicationProvider.getApplicationContext())

        assertSame(
            "TelemetryDatabaseModule debe delegar en getInstance, nunca abrir un Room.databaseBuilder propio",
            direct,
            telemetryDatabase
        )
    }

    @Test
    fun `setInstanceForTesting antes de inyectar sigue alcanzando el grafo Hilt`() {
        val inMemory = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TelemetryDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            TelemetryDatabase.setInstanceForTesting(inMemory)

            hiltRule.inject()

            assertSame(
                "el override de test (setInstanceForTesting) debe seguir alcanzando el grafo de Hilt",
                inMemory,
                telemetryDatabase
            )
        } finally {
            inMemory.close()
        }
    }
}
