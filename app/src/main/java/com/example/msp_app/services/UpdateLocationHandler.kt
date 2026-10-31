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
 * **E-APP-146 (retencion del envio por ubicacion):** el pago YA NO se encola aqui. La subida la encola
 * el guardado ([com.example.msp_app.data.pagos.RegistroDeAbonoAdapter] y la condonacion), una sola
 * vez y con `ExistingWorkPolicy.KEEP`; un segundo encolado desde este servicio era el que podia llegar
 * mientras el worker esperaba la ubicacion. Ahora la ubicacion del pago la escribe el tomador en
 * cuanto acepta un fix, y el servicio queda sin quien lo arranque para pagos.
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
    private val updateVisitLocation: suspend (id: String, lat: Double, lng: Double) -> Unit
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
                // E-APP-146: solo escribe LAT/LNG (un adorno). Ya no encola: el guardado del pago
                // es quien lo encola, una vez, y el worker retiene el envio hasta tener ubicacion.
                location?.let {
                    ejecutar(ERROR_CODE_PAYMENT_LOCATION_NOT_WRITTEN, CONTEXT_PAYMENT_LOCATION) {
                        updatePaymentLocation(paymentId, it.latitude, it.longitude)
                    }
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
         * No se pudo escribir `LAT`/`LNG` del pago: la ubicacion es un adorno del registro, no un
         * requisito del dinero.
         */
        const val ERROR_CODE_PAYMENT_LOCATION_NOT_WRITTEN = "pago_ubicacion_no_escrita"

        /** No se pudo escribir `LAT`/`LNG` de la visita. Esta rama no encola nada. */
        const val ERROR_CODE_VISIT_LOCATION_NOT_WRITTEN = "visita_ubicacion_no_escrita"

        private const val CONTEXT_LOCATION_FETCH = "UpdateLocationHandler.fetchLocation"
        private const val CONTEXT_PAYMENT_LOCATION = "UpdateLocationHandler.updatePaymentLocation"
        private const val CONTEXT_VISIT_LOCATION = "UpdateLocationHandler.updateVisitLocation"
    }
}
