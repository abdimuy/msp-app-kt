package com.example.msp_app.core.sync.pendingwork.data.visits

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 11's mutex-based dedup (`TriggerVisitsReconciliationUseCase`) only
 * suppresses overlap when every trigger shares ONE instance. This proves the
 * wiring that makes that true in production: [VisitsReconcileWorker] (app
 * open + periodic) and [VisitsReconcileConnectivityTrigger]'s call site
 * (`VisitsReconcileObserver`) both resolve
 * [VisitsReconcileTriggerProvider.get] — this test is what stands behind that
 * claim instead of trusting it by inspection.
 *
 * `get()` itself must not touch the Hilt graph — [VisitsReconcileEntryPoint]
 * resolution is deferred until [TriggerVisitsReconciliationUseCase.execute]
 * actually runs (see `VisitsReconcileTriggerProvider.build`'s KDoc) — so this
 * runs against a PLAIN Robolectric `Application`, not a Hilt test app: if
 * `build()` ever stopped being lazy, `get()` would throw here instead of
 * quietly needing a heavier test harness.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class VisitsReconcileTriggerProviderTest {

    /**
     * Without this, the first [VisitsReconcileTriggerProvider.get] call in
     * the JVM permanently caches an instance closed over THAT call's
     * `applicationContext` — under Robolectric that context is per-test-
     * method, so a later test in the same run would silently inherit the
     * stale instance instead of getting a fresh one. An order-dependent
     * flake, and nothing here would go red to reveal it — hence the explicit
     * reset rather than trusting Robolectric's class reloading alone.
     */
    @After
    fun tearDown() {
        VisitsReconcileTriggerProvider.reset()
    }

    @Test
    fun `get devuelve SIEMPRE la misma instancia de proceso`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val first = VisitsReconcileTriggerProvider.get(context)
        val second = VisitsReconcileTriggerProvider.get(context)

        assertSame(
            "el mutex del guard solo protege si TODOS los disparadores comparten instancia",
            first,
            second
        )
    }

    @Test
    fun `resolver la instancia no toca el grafo de Hilt (resolucion perezosa)`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // Si esto lanzara, seria porque build() dejo de ser perezoso y esta
        // llamando a EntryPointAccessors contra una Application sin Hilt.
        VisitsReconcileTriggerProvider.get(context)
    }

    @Test
    fun `reset fuerza que el SIGUIENTE get reconstruya una instancia distinta`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val beforeReset = VisitsReconcileTriggerProvider.get(context)
        VisitsReconcileTriggerProvider.reset()
        val afterReset = VisitsReconcileTriggerProvider.get(context)

        assertNotSame(
            "reset() que no reconstruye de verdad es tan inutil como no tenerlo",
            beforeReset,
            afterReset
        )
    }
}
