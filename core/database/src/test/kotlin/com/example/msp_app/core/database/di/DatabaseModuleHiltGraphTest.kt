package com.example.msp_app.core.database.di

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.dao.sale.SaleDao
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
 * Task 3 (Plan 2 — cimiento de datos): prueba que el grafo Hilt REAL resuelve
 * [AppDatabase] y sus DAOs a través de [DatabaseModule], y — la parte de
 * money-safety que este test existe para plantar — que
 * `DatabaseModule.provideAppDatabase` DELEGA en [AppDatabase.getInstance] en
 * vez de abrir una segunda conexión propia al archivo `msp_db`.
 *
 * `hiltRule.inject()` se llama a mano en cada test (no en un `@Before`
 * compartido) porque el tercer test necesita `setInstanceForTesting` ANTES
 * de que Hilt resuelva el grafo — mismo orden que exige el caso real
 * (override de test alcanzando el binding).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class DatabaseModuleHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var appDatabase: AppDatabase

    @Inject
    lateinit var paymentDao: PaymentDao

    @Inject
    lateinit var saleDao: SaleDao

    @Before
    fun clearSingletonBefore() {
        // `AppDatabase.instance` es un companion `Volatile var` (estado
        // JVM-wide); Robolectric puede reusar el classloader entre métodos de
        // esta clase, así que se limpia antes de cada test para que ninguno
        // herede el estado del anterior.
        AppDatabase.clearInstance()
    }

    @After
    fun clearSingletonAfter() {
        AppDatabase.clearInstance()
    }

    @Test
    fun `el grafo Hilt resuelve AppDatabase y sus DAOs`() {
        hiltRule.inject()

        assertNotNull(appDatabase)
        assertNotNull(paymentDao)
        assertNotNull(saleDao)
    }

    @Test
    fun `la AppDatabase inyectada es la MISMA instancia que getInstance (una sola conexion a msp_db)`() {
        hiltRule.inject()

        val direct = AppDatabase.getInstance(ApplicationProvider.getApplicationContext())

        assertSame(
            "DatabaseModule debe delegar en getInstance, nunca abrir un Room.databaseBuilder propio",
            direct,
            appDatabase
        )
    }

    @Test
    fun `setInstanceForTesting antes de inyectar sigue alcanzando el grafo Hilt`() {
        val inMemory = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            AppDatabase.setInstanceForTesting(inMemory)

            hiltRule.inject()

            assertSame(
                "el override de test (setInstanceForTesting) debe seguir alcanzando el grafo de Hilt",
                inMemory,
                appDatabase
            )
        } finally {
            inMemory.close()
        }
    }
}
