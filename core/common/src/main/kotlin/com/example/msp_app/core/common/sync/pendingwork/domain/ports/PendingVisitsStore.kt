package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * Local custody of visitas: which ones the phone still holds as not-yet-known
 * by the server, and the single operation that retires one from that set.
 *
 * The flag behind both methods is `Visit.GUARDADO_EN_MICROSIP` (0 = pending,
 * 1 = the server is known to hold it). It is the ONLY local marker of "synced"
 * and it is only ever flipped on **positive** evidence:
 *
 *  - `PendingVisitsWorker` flips it after an upload the server accepted, or
 *    after a 4xx the cobranza failed-intent middleware persists server-side.
 *  - [markSynced] flips it after `GET /v2/visitas/by-ids` names the id.
 *
 * **An HTTP 409 is NOT evidence and must never reach this port.** The plan and
 * an earlier task report claimed `POST /v2/visitas` answers 409 on an id
 * collision and that this proves custody. Checked against the Go source, that
 * is wrong: on a collision the server does `FindByID` and returns the existing
 * visita with **201**; a failed lookup maps to a generic **500** and a
 * not-found to **404**. A real 409 would therefore mean the confirming step
 * was bypassed or broken — precisely the moment trusting it would destroy a
 * visita.
 */
interface PendingVisitsStore {

    /**
     * Ids of every visita still pending upload (`GUARDADO_EN_MICROSIP = 0`).
     *
     * May contain duplicates as far as this contract is concerned; the caller
     * de-duplicates before spending slots of the server's per-request cap.
     */
    suspend fun pendingVisitIds(): List<String>

    /**
     * Flips `GUARDADO_EN_MICROSIP` to 1 for [visitIds] and nothing else.
     *
     * Callers pass at most [VisitCustodyRegistry.MAX_IDS_PER_REQUEST] ids, so an
     * implementation backed by a single `IN (...)` stays far below SQLite's
     * 999-parameter ceiling.
     *
     * @return how many ids were **actually** flipped. The number is not
     *   decorative: since Task 23 the backing query also refuses to mark a
     *   visita that still holds an undelivered photo (`Ruling AR`), so a return
     *   value lower than `visitIds.size` is the ONLY local signal that
     *   distinguishes *"confirmed"* from *"held back by a comprobante"*. The
     *   port used to return `Unit`, and the caller reported `visitIds.size`
     *   unconditionally — which over-counted every time that constraint fired,
     *   in exactly the diagnostic GATE 1 of `DEPLOY.md §0.2` tells the field to
     *   look at.
     */
    suspend fun markSynced(visitIds: List<String>): Int
}
