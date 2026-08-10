package com.example.msp_app.core.printing.adapters

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.printing.domain.PrintError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Virtual-time coverage of the gateway's connect timeout/cancellation policy — the
 * concurrency crux — exercised through the [DantSuPrinterGateway.withConnectTimeout]
 * seam with a fake `connect` lambda. No Bluetooth, no real dispatcher, no hardware:
 * the DantSu `BluetoothConnection.connect()` boundary is deliberately NOT crossed
 * here (its native `BluetoothSocket.connect()` interrupt behaviour — a few residual
 * seconds of block after cancellation — is a platform reality left to the real-
 * hardware field test). What IS pinned here is the pure policy that wraps it:
 *  - a connect that never returns times out to a typed [PrintError.ConnectionFailed]
 *    exactly at [DantSuPrinterGateway.CONNECT_TIMEOUT_MS] (fail-fast, not a hang);
 *  - a genuine scope cancellation propagates as a [CancellationException] and is
 *    NOT swallowed into a [PrintError] (only the timeout's own subtype is caught);
 *  - a fast connect returns its value untouched.
 *
 * Robolectric only to construct the gateway's [BluetoothPrinterDiscovery] (which
 * needs a `Context`); the discovery is never invoked by these tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DantSuPrinterGatewayTimeoutTest {
    private val gateway =
        DantSuPrinterGateway(BluetoothPrinterDiscovery(ApplicationProvider.getApplicationContext()))

    @Test
    fun `a connect that never returns times out to ConnectionFailed at the bound`() = runTest {
        val error =
            runCatching { gateway.withConnectTimeout<Unit> { awaitCancellation() } }
                .exceptionOrNull()

        assertTrue(
            "a stalled connect must surface as ConnectionFailed, got $error",
            error is PrintError.ConnectionFailed
        )
        // The timeout fires exactly at the documented bound (virtual time), so an
        // absent printer fails fast instead of hanging on the OS RFCOMM freeze.
        assertEquals(DantSuPrinterGateway.CONNECT_TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `a genuine cancellation propagates instead of being swallowed as ConnectionFailed`() =
        runTest {
            var caught: Throwable? = null
            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        gateway.withConnectTimeout<Unit> { awaitCancellation() }
                    } catch (throwable: Throwable) {
                        caught = throwable
                        throw throwable
                    }
                }

            // Cancel WELL before the timeout would fire — this is a real cancellation,
            // not a timeout, so it must not be reclassified as a ConnectionFailed.
            advanceTimeBy(DantSuPrinterGateway.CONNECT_TIMEOUT_MS / 2)
            job.cancel()
            job.join()

            assertTrue("the job must end cancelled", job.isCancelled)
            assertTrue(
                "cancellation must stay a CancellationException, got $caught",
                caught is CancellationException
            )
            assertFalse(
                "cancellation must not be swallowed into a PrintError, got $caught",
                caught is PrintError
            )
        }

    @Test
    fun `a fast connect returns its value untouched`() = runTest {
        val value = gateway.withConnectTimeout { "connected" }

        assertEquals("connected", value)
    }
}
