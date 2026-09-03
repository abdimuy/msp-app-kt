package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * The server's answer to one question and no other: *of these ids you uploaded,
 * which do you already hold?*
 *
 * Backed by `GET /v2/visitas/by-ids?ids=…` (msp-api, Task 8). Two properties of
 * that endpoint shape this port:
 *
 *  - **It takes no `zona_id`** (orchestrator Ruling B). The ids are UUIDs the
 *    phone itself minted and uploaded, so a zone would add nothing and would
 *    let a wrong zone silently hide a visita the server does hold.
 *  - **It caps the id list at 100 per request** and answers `422 ids_too_many`
 *    beyond that. Chunking is the caller's job — see
 *    [com.example.msp_app.core.common.sync.pendingwork.domain.usecases
 *    .ReconcileVisitsUseCase.MAX_IDS_PER_REQUEST].
 *
 * The response is a bare JSON array of the ids that exist. Ids the server does
 * not know are simply absent — never an error, since that absence is the very
 * thing the endpoint exists to report.
 */
interface VisitCustodyRegistry {

    /**
     * @param visitIds at most `MAX_IDS_PER_REQUEST` ids, non-empty.
     * @return the subset of [visitIds] the server already holds, in any order.
     * @throws Throwable on any transport or protocol failure — the caller
     *   treats a throw as "unknown", never as "the server does not have them".
     */
    suspend fun findExisting(visitIds: List<String>): List<String>
}
