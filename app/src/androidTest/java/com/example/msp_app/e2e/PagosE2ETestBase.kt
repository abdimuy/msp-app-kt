package com.example.msp_app.e2e

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.workers.PendingPaymentsWorker
import com.google.gson.GsonBuilder
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Shared on-device scaffolding for the payment-upload e2e suite (B6/B7).
 *
 * Wires:
 *  - An in-memory Room [AppDatabase], installed as the process singleton via
 *    [AppDatabase.setInstanceForTesting] BEFORE any [PaymentsLocalDataSource]
 *    is constructed — the data source resolves the DB lazily in its own
 *    constructor, so ordering matters.
 *  - A [MockWebServer] standing in for msp-api's `v2/cobranza/pagos`
 *    endpoint.
 *  - A real [V2PaymentsApi], built with a plain Retrofit + Gson against the
 *    MockWebServer (no Firebase bearer interceptor — MockWebServer needs no
 *    auth, and pulling in the v2 client's `BearerAuthInterceptor` would
 *    require a signed-in Firebase user for no benefit here).
 *  - A real, test-mode WorkManager (real scheduler + constraint tracking)
 *    whose [WorkerFactory] hands out [PendingPaymentsWorker] instances wired
 *    to the on-device Room + the MockWebServer-backed [V2PaymentsApi]. This
 *    is the exact class the production scheduler instantiates — subclasses
 *    drive it through [enqueuePendingPaymentsWorker]/[TestDriver], never by
 *    constructing the worker directly.
 *
 * ### WorkManager init gotcha
 *
 * [com.example.msp_app.MspApplication.onCreate] calls
 * `enqueueClienteSyncWorker(this)`, which resolves `WorkManager.getInstance`
 * and forces the default (production) `WorkManagerImpl` singleton to
 * initialize via the automatic `androidx.startup` content provider. That
 * always runs before any test code — it's driven by `Application#onCreate`,
 * which the instrumentation process executes before the test class is even
 * loaded.
 *
 * This does NOT break [WorkManagerTestInitHelper.initializeTestWorkManager]
 * though. Verified by disassembling `androidx.work:work-runtime:2.10.2` and
 * `work-testing:2.10.2`:
 *  - `WorkManagerImpl.initialize(Context, Configuration)` throws
 *    `IllegalStateException("WorkManager is already initialized...")` only
 *    when BOTH the delegated instance AND the default instance are already
 *    non-null.
 *  - `WorkManagerTestInitHelper.initializeTestWorkManager(...)` never calls
 *    `initialize(...)`. It builds its own `WorkManagerImpl` directly and
 *    calls the static `WorkManagerImpl.setDelegate(...)`.
 *  - `WorkManagerImpl.getInstance()` (and therefore every
 *    `WorkManager.getInstance(context)` call in app code, including inside
 *    [PendingPaymentsWorker]'s own default constructor arguments and
 *    `enqueuePendingPaymentsWorker`) always resolves the delegated instance
 *    first, falling back to the default instance only when no delegate is
 *    set.
 *
 * So calling `initializeTestWorkManager` in `@Before` — even though
 * `MspApplication` already forced the default instance during process
 * startup — makes the test instance win for the rest of the test process.
 * No manifest surgery (disabling the default `WorkManagerInitializer`) was
 * necessary.
 */
abstract class PagosE2ETestBase {

    protected lateinit var context: Context
    protected lateinit var db: AppDatabase
    protected lateinit var mockWebServer: MockWebServer
    protected lateinit var v2Api: V2PaymentsApi
    protected lateinit var testDriver: TestDriver

    @Before
    fun setUpPagosE2EBase() {
        context = ApplicationProvider.getApplicationContext()

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        AppDatabase.setInstanceForTesting(db)

        mockWebServer = MockWebServer()
        mockWebServer.start()

        v2Api = buildV2PaymentsApi(mockWebServer)

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker? {
                if (workerClassName != PendingPaymentsWorker::class.java.name) return null
                return PendingPaymentsWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    paymentsStore = PaymentsLocalDataSource(appContext),
                    v2Api = v2Api,
                    useV2 = true
                )
            }
        }

        val wmConfiguration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, wmConfiguration)
        testDriver = WorkManagerTestInitHelper.getTestDriver(context)
            ?: error("WorkManagerTestInitHelper.getTestDriver returned null after init")
    }

    @After
    fun tearDownPagosE2EBase() {
        mockWebServer.shutdown()
        AppDatabase.clearInstance()
    }

    private fun buildV2PaymentsApi(server: MockWebServer): V2PaymentsApi {
        val gson = GsonBuilder().create()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        return retrofit.create(V2PaymentsApi::class.java)
    }

    /** The unique WorkManager work name [enqueuePendingPaymentsWorker] uses for a payment. */
    protected fun uniqueWorkName(paymentId: String) = "sync_pending_payments_$paymentId"

    /**
     * Latest [WorkInfo] for the unique work backing [paymentId], if any
     * request exists yet.
     *
     * Deliberately goes through [WorkManager.getWorkInfosForUniqueWorkFlow]
     * (Kotlin `Flow`) rather than the sibling `ListenableFuture`-returning
     * overload: this module has no direct dependency on Guava, and AndroidX's
     * Gradle module metadata resolves the transitive `listenablefuture`
     * artifact to the deliberately-empty `9999.0-empty-to-avoid-conflict-
     * with-guava` shim whenever nothing in the graph requests real Guava —
     * which makes `com.google.common.util.concurrent.ListenableFuture`
     * unresolvable at compile time here. The `Flow` overload never exposes
     * that type in its signature, so it needs no extra dependency.
     */
    protected suspend fun currentWorkInfo(paymentId: String): WorkInfo? =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(uniqueWorkName(paymentId))
            .first()
            .firstOrNull()

    protected suspend fun currentWorkId(paymentId: String): UUID = currentWorkInfo(paymentId)?.id
        ?: error("no WorkInfo enqueued yet for payment $paymentId")

    /**
     * Polls [currentWorkInfo] for [paymentId] until [until] is satisfied or
     * [timeoutMillis] elapses, returning the last observed [WorkInfo]. Real
     * on-device WorkManager scheduling (constraint tracking + coroutine
     * dispatch inside [PendingPaymentsWorker]) is not synchronous even under
     * the test WorkManager, so tests poll rather than assert immediately
     * after triggering constraints.
     */
    protected suspend fun awaitWorkInfo(
        paymentId: String,
        timeoutMillis: Long = 20_000,
        pollMillis: Long = 150,
        until: (WorkInfo) -> Boolean
    ): WorkInfo {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: WorkInfo? = null
        while (System.currentTimeMillis() < deadline) {
            val info = currentWorkInfo(paymentId)
            if (info != null) {
                last = info
                if (until(info)) return info
            }
            delay(pollMillis)
        }
        return last ?: error("no WorkInfo observed for payment $paymentId within ${timeoutMillis}ms")
    }
}
