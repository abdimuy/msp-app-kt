package com.example.msp_app.features.warehouses

import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Activity mínima solo para este test: `hiltViewModel()` (usado por las
 * pantallas reales) resuelve `@HiltViewModel`s a través del
 * `HiltViewModelFactory` que Hilt inyecta como `defaultViewModelProviderFactory`
 * de cualquier `@AndroidEntryPoint` Activity. Usar `ViewModelProvider(activity)`
 * directamente ejercita ese mismo mecanismo sin depender de Compose/Navigation.
 */
@AndroidEntryPoint
class WarehouseHiltGraphTestActivity : ComponentActivity()

/**
 * Task 8 (Plan 1 — cimiento): prueba que el grafo Hilt REAL (no una fake
 * inyección manual) resuelve [WarehouseViewModel] de punta a punta —
 * `WarehousesApi` (provisto por `NetworkModule`, Task 7) →
 * `WarehouseRemoteDataSource` (`@Inject`) → `WarehouseRepository` (`@Inject`)
 * → `WarehouseViewModel` (`@HiltViewModel`) — el mismo camino que siguen las 4
 * pantallas que hoy llaman `hiltViewModel()`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class WarehouseHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `el grafo Hilt resuelve WarehouseViewModel de punta a punta`() {
        val activity = Robolectric.buildActivity(WarehouseHiltGraphTestActivity::class.java)
            .create()
            .get()

        val viewModel = ViewModelProvider(activity)[WarehouseViewModel::class.java]

        assertNotNull(viewModel)
        // Misma instancia mientras viva el ViewModelStore de la activity —
        // igual que el scoping que usan las pantallas reales con hiltViewModel().
        assertSame(viewModel, ViewModelProvider(activity)[WarehouseViewModel::class.java])
    }
}
