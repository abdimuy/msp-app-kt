package com.example.msp_app.di

import com.example.msp_app.core.network.ConnectivityMonitor
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 7 (Plan 1 — cimiento): prueba que el grafo Hilt real (no una fake
 * inyección manual) resuelve [WarehousesApi] y [ConnectivityMonitor] a través
 * de [NetworkModule] y [ConnectivityModule]. Esto es justo lo que hace que
 * estos tipos sean "inyectables" — el objetivo entero de esta tarea.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class NetworkAndConnectivityHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var warehousesApi: WarehousesApi

    @Inject
    lateinit var connectivityMonitor: ConnectivityMonitor

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `el grafo Hilt resuelve WarehousesApi y ConnectivityMonitor`() {
        assertNotNull(warehousesApi)
        assertNotNull(connectivityMonitor)
    }
}
