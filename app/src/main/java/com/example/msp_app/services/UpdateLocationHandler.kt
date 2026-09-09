package com.example.msp_app.services

import android.location.Location
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
 * - Task 5's rewrite (decoupling visit enqueueing from this service) could
 *   change [updateVisitLocation]'s caller without touching the guard in
 *   [fetchLocationOrNull] — the fix survives that rewrite because it does
 *   not live inline in the `Service`'s callbacks.
 *
 * **Task 5 (plan `pagos-y-visitas`):** the visit branch used to call an
 * `enqueueVisit` lambda here too — the ONLY place a visit's upload got
 * enqueued. If this `Service` never ran (permission denied, Play Services
 * down, foreground-service start refused), the visit was written to Room
 * and never enqueued. The upload is now enqueued by
 * [com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource.saveVisitAndEnqueue]
 * at save time, so this class no longer enqueues visits at all — the
 * location arriving late only updates LAT/LNG (see [handle]).
 *
 * **Arreglo C:** the same lesson finally reached the money path.
 * `RegistroDeAbonoAdapter` now enqueues the payment in the same coroutine as
 * the write, so [enqueuePayment] here is a second net rather than the only
 * one — and it no longer shares a `try` with the LAT/LNG write, which used to
 * take it down with it.
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
    private val enqueuePayment: (id: String) -> Unit
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

        when {
            paymentId != null -> {
                // DOS `try` separados, y esa separacion es el arreglo: escribir
                // LAT/LNG es un adorno y encolar es dinero. Con un `try`
                // compartido, un `updatePaymentLocation` que lanzara se llevaba
                // puesto el `enqueuePayment` — que hasta el Arreglo C era el
                // UNICO encolado inmediato del pago. Es la misma leccion que la
                // Task 11 aplico a `VisitsReconcileObserver` ("si el primero
                // lanza, el segundo nunca se registra"), que nunca habia llegado
                // al camino del dinero.
                //
                // Desde el Arreglo C `RegistroDeAbonoAdapter` ya encola en la
                // misma corrutina de la escritura, asi que este encolado es la
                // segunda red (y el unico del camino de la condonacion). Sigue
                // siendo idempotente: `ExistingWorkPolicy.KEEP` sobre el nombre
                // unico `sync_pending_payments_<id>`.
                location?.let {
                    ejecutar(ERROR_CODE_PAYMENT_LOCATION_NOT_WRITTEN, CONTEXT_PAYMENT_LOCATION) {
                        updatePaymentLocation(paymentId, it.latitude, it.longitude)
                    }
                }
                ejecutar(ERROR_CODE_PAYMENT_NOT_ENQUEUED, CONTEXT_PAYMENT_ENQUEUE) {
                    enqueuePayment(paymentId)
                }
            }

            visitId != null -> {
                // Task 5: NUNCA encola aqui. El guardado ya encolo la
                // subida (VisitsLocalDataSource.saveVisitAndEnqueue); esta
                // rama solo actualiza LAT/LNG si la ubicacion llego. Al no
                // encolar, no puede crear una segunda subida ni pisar el
                // estado de una visita ya subida.
                //
                // Round 1 de revision: no hay test de reversion para "esta
                // rama no puede volver a encolar" a proposito. La garantia
                // es del sistema de tipos, no de una asercion — el
                // constructor de este handler ya no tiene NINGUN parametro
                // `enqueueVisit` (fue borrado, no dejado sin llamar), asi
                // que no queda ningun colaborador que un test pudiera
                // observar. Reintroducir ese parametro solo para medir su
                // ausencia reintroduciria el bug que Task 5 arreglo.
                location?.let {
                    ejecutar(ERROR_CODE_VISIT_LOCATION_NOT_WRITTEN, CONTEXT_VISIT_LOCATION) {
                        updateVisitLocation(visitId, it.latitude, it.longitude)
                    }
                }
            }
        }
    }

    /**
     * Corre [block] y, si falla, lo REPORTA con su propio codigo greppable en
     * vez de tragarselo.
     *
     * El `catch` anterior era un `Log.e` pelado amparado en el Ruling I
     * ("`:app` legacy no se retrofitea"). El **Ruling Q**, posterior, corrigio
     * esa redaccion: *la exencion de `:app` cubre codigo legacy preexistente, no
     * codigo nuevo que este plan escriba* — y esta clase la escribio la Task 3.
     *
     * Anti-PII: viaja el nombre de la clase de la excepcion mas un contexto
     * estatico, nunca `e.message`.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // deliberado: la norma de errores exige reportar TODO throwable.
    private suspend fun ejecutar(code: String, context: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            telemetry.error(code = code, message = "$context: ${e::class.simpleName}")
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

        /**
         * No se pudo escribir `LAT`/`LNG` del pago. **El encolado corre igual**:
         * la ubicacion es un adorno del registro, no un requisito del dinero.
         */
        const val ERROR_CODE_PAYMENT_LOCATION_NOT_WRITTEN = "pago_ubicacion_no_escrita"

        /**
         * No se pudo encolar la subida del pago desde el servicio. El pago no se
         * pierde —`RegistroDeAbonoAdapter` ya encolo al escribir, y
         * `PaymentsPendingSynchronizer` barre en el siguiente login— pero el
         * fallo tiene que verse.
         */
        const val ERROR_CODE_PAYMENT_NOT_ENQUEUED = "pago_no_se_encolo"

        /** No se pudo escribir `LAT`/`LNG` de la visita. Esta rama no encola nada. */
        const val ERROR_CODE_VISIT_LOCATION_NOT_WRITTEN = "visita_ubicacion_no_escrita"

        private const val CONTEXT_LOCATION_FETCH = "UpdateLocationHandler.fetchLocation"
        private const val CONTEXT_PAYMENT_LOCATION = "UpdateLocationHandler.updatePaymentLocation"
        private const val CONTEXT_PAYMENT_ENQUEUE = "UpdateLocationHandler.enqueuePayment"
        private const val CONTEXT_VISIT_LOCATION = "UpdateLocationHandler.updateVisitLocation"
    }
}
