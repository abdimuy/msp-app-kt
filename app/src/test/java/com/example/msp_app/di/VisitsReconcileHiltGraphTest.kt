package com.example.msp_app.di

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.PendingVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase
import com.example.msp_app.core.sync.pendingwork.data.visits.RoomPendingVisitsStore
import com.example.msp_app.core.sync.pendingwork.data.visits.TelemetrySyncErrorReporter
import com.example.msp_app.core.sync.pendingwork.data.visits.V2VisitCustodyRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 10 — proves the real `:app` graph builds the visitas reconciler out of
 * its three ports.
 *
 * Without this, `VisitsReconcileModule` would be a set of bindings nobody asks
 * for yet (the triggers arrive in Task 11), and Dagger validates only what is
 * reachable — a broken binding would compile fine and surface the day the first
 * trigger is written.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class VisitsReconcileHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var reconcileVisits: ReconcileVisitsUseCase

    @Inject
    lateinit var store: PendingVisitsStore

    @Inject
    lateinit var registry: VisitCustodyRegistry

    @Inject
    lateinit var errorReporter: SyncErrorReporter

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `el grafo Hilt de app resuelve el reconciliador y sus tres puertos`() {
        assertNotNull(reconcileVisits)
        assertTrue(store is RoomPendingVisitsStore)
        assertTrue(registry is V2VisitCustodyRegistry)
        assertTrue(errorReporter is TelemetrySyncErrorReporter)
    }
}
