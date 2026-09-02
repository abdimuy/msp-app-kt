package com.example.msp_app.services

import android.location.Location
import android.util.Log
import com.example.msp_app.core.telemetry.Telemetry
import kotlinx.coroutines.CancellationException

/**
 * Extracted from [UpdateLocationService.onStartCommand] (plan `pagos-y-visitas`,
 * Task 3 — bug: `client.getCurrentLocation` had no `try/catch`, so a missing
 * location permission threw `SecurityException` synchronously and killed the
 * `Service`, crashing whatever flow — a payment or a visit capture — was
 * running).
 *
 * This class holds none of the `Service`/`FusedLocationProviderClient`
 * plumbing on purpose: it is a plain, Android-`Service`-free unit so
 * - it is unit-testable without Robolectric/Room (all dependencies are
 *   plain suspend/plain lambdas the caller wires up), and
 * - Task 5's planned rewrite (decoupling visit enqueueing from this
 *   service) can swap out [enqueueVisit] / [updateVisitLocation] without
 *   touching the guard in [fetchLocationOrNull] — the fix survives that
 *   rewrite because it does not live inline in the `Service`'s callbacks.
 *
 * `client.getCurrentLocation(...)` throws [SecurityException] SYNCHRONOUSLY
 * (before it ever returns a `Task`) when the location permission is
 * missing — that is the crash this class exists to absorb. The returned
 * `Task` can also fail for other reasons (Play Services unavailable, no
 * location resolvable, resolution required, etc.); `.await()` re-throws
 * those the same way inside [fetchLocation]. Both are caught here, each
 * under its own greppable error code (norma de errores, `global-constraints.md`).
 *
 * Location is an ornament on the payment/visit record, not a requirement of
 * either flow — so on any failure fetching it, [handle] completes anyway
 * with a null location instead of propagating.
 */
class UpdateLocationHandler(
    private val telemetry: Telemetry,
    private val updatePaymentLocation: suspend (id: String, lat: Double, lng: Double) -> Unit,
    private val updateVisitLocation: suspend (id: String, lat: Double, lng: Double) -> Unit,
    private val enqueuePayment: (id: String) -> Unit,
    private val enqueueVisit: (id: String) -> Unit
) {
    /**
     * Exactly one of [paymentId] / [visitId] is expected non-null (the
     * caller already guards that, same as before this refactor).
     */
    suspend fun handle(
        paymentId: String?,
        visitId: String?,
        fetchLocation: suspend () -> Location
    ) {
        val location = fetchLocationOrNull(fetchLocation)

        try {
            when {
                paymentId != null -> {
                    location?.let { updatePaymentLocation(paymentId, it.latitude, it.longitude) }
                    enqueuePayment(paymentId)
                }

                visitId != null -> {
                    location?.let { updateVisitLocation(visitId, it.latitude, it.longitude) }
                    enqueueVisit(visitId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Alcance de Task 3 (norma de errores, global-constraints.md,
            // Ruling I): el texto de la tarea pide reportar por Telemetry
            // solo el fetch de ubicación. Este catch preserva el
            // comportamiento previo (log best-effort) para fallos de
            // guardado/encolado — código :app legacy que esta tarea solo
            // atraviesa de paso, no se retrofitea aquí.
            Log.e(TAG, "Error al persistir ubicación o encolar", e)
        }
    }

    private suspend fun fetchLocationOrNull(fetchLocation: suspend () -> Location): Location? =
        try {
            fetchLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            telemetry.error(
                code = ERROR_CODE_LOCATION_PERMISSION_DENIED,
                message = "$CONTEXT_LOCATION_FETCH: ${e::class.simpleName}"
            )
            null
        } catch (e: Exception) {
            telemetry.error(
                code = ERROR_CODE_LOCATION_UNAVAILABLE,
                message = "$CONTEXT_LOCATION_FETCH: ${e::class.simpleName}"
            )
            null
        }

    companion object {
        /** Falta el permiso de ubicación — `SecurityException` sincrónica. */
        const val ERROR_CODE_LOCATION_PERMISSION_DENIED = "ubicacion_permiso_denegado"

        /** El `Task` de Play Services falló por cualquier otra razón. */
        const val ERROR_CODE_LOCATION_UNAVAILABLE = "ubicacion_no_disponible"

        private const val CONTEXT_LOCATION_FETCH = "UpdateLocationHandler.fetchLocation"
        private const val TAG = "UpdateLocationService"
    }
}
