package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import javax.inject.Inject

/**
 * Adapter of [VisitCustodyRegistry] over `GET /v2/visitas/by-ids`.
 *
 * Lives in `:app` — not in `:core:common` — because it is the only layer
 * allowed to know Retrofit exists, and because [V2VisitsApi] itself lives here.
 * The port stays in the module; the adapter follows the same precedent as
 * `UserCyclePort` → `FirebaseUserCycleAdapter`.
 *
 * Deliberately NOT `@Singleton`: it holds an [V2VisitsApi] proxy built by an
 * `ApiProvider`-family factory, and a scoped holder would freeze that proxy for
 * the life of the process — the shape the baseURL kill-switch guard exists to
 * forbid. Unscoped, every injection point resolves a fresh service.
 *
 * It translates and it validates the batch bound; it decides nothing. A
 * transport failure propagates untouched, because only the use case may decide
 * what a failure means — and there it means "unknown", never "the server does
 * not have them".
 */
class V2VisitCustodyRegistry @Inject constructor(
    private val api: V2VisitsApi
) : VisitCustodyRegistry {

    /**
     * The two `require`s are not ceremony. They turn a caller's mistake into a
     * loud local failure the reconciler reports by name, instead of a `422`
     * (`ids_too_many` / `ids_required`) that would look like a server problem
     * and leave those visitas pending forever with a misleading signal.
     */
    override suspend fun findExisting(visitIds: List<String>): List<String> {
        require(visitIds.isNotEmpty()) {
            "by-ids nunca se llama con lista vacia: el servidor responde 422 ids_required"
        }
        require(visitIds.size <= ReconcileVisitsUseCase.MAX_IDS_PER_REQUEST) {
            "by-ids acepta como maximo ${ReconcileVisitsUseCase.MAX_IDS_PER_REQUEST} ids por " +
                "peticion (recibidos ${visitIds.size}); el servidor responde 422 ids_too_many"
        }
        return api.visitasExistentesPorIds(visitIds.joinToString(separator = ","))
    }
}
