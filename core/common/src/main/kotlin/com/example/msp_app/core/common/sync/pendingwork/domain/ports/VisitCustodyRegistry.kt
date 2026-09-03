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
 *  - **It caps the id list at [MAX_IDS_PER_REQUEST] per request** and answers
 *    `422 ids_too_many` beyond that. The cap is a property of the endpoint, so
 *    it lives here rather than on any one caller; chunking to it is the
 *    caller's job.
 *
 * The response is a bare JSON array of the ids that exist. Ids the server does
 * not know are simply absent — never an error, since that absence is the very
 * thing the endpoint exists to report.
 */
interface VisitCustodyRegistry {

    /**
     * @param visitIds at most [MAX_IDS_PER_REQUEST] ids, non-empty.
     * @return the subset of [visitIds] the server already holds, in any order.
     * @throws Throwable on any transport or protocol failure — the caller
     *   treats a throw as "unknown", never as "the server does not have them".
     */
    suspend fun findExisting(visitIds: List<String>): List<String>

    companion object {
        /**
         * **Exactly the server's cap, and it must stay exactly that.**
         *
         * `GET /v2/visitas/by-ids` declares `maxIDsPorRequest = 100` (msp-api,
         * Task 8) and answers `422 ids_too_many` at 101. The number is a
         * URL-length budget, not a SQLite one: a UUID plus its comma is 37
         * bytes on the wire, so 100 ids is ~3.7 KB of a ~8 KB practical
         * request-line ceiling — and comfortably inside the 2,048-byte default
         * query-string limit's neighbourhood on the IIS production target.
         *
         * Copying cobranza's 500 would have meant ~18.5 KB and a wall of 422s;
         * quietly sending 101 would leave those visitas pending forever with no
         * signal. Hence: chunk at 100, and let a violation be loud.
         *
         * It lives on the port, not on a use case, because it describes the
         * endpoint — every implementation and every caller is bound by the same
         * number, and an adapter should never have to reach into a use case to
         * learn its own limit.
         */
        const val MAX_IDS_PER_REQUEST: Int = 100
    }
}
