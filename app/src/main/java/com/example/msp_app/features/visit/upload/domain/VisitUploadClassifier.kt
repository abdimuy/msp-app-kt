package com.example.msp_app.features.visit.upload.domain

/**
 * What the visit upload worker should do with a given HTTP response code.
 *
 * The semantics differ deliberately from the sales `UploadFailureClassifier`: a
 * visita that fails validation (4xx) is **not** surrendered — because the cobranza
 * route now mounts the failed-intent capture middleware, any 4xx/5xx the server
 * produces is persisted server-side with its full body. So a 4xx means "the
 * server has it; correct it from the desk", which for the phone is DONE.
 */
enum class VisitUploadDecision {
    /**
     * The server has the visita. Either it was applied/accepted (2xx) or it was
     * rejected but captured as a failed-intent (data 4xx). Mark
     * GUARDADO_EN_MICROSIP=true — the phone is finished; resolution lives desk-side.
     */
    DONE,

    /**
     * Transient — retry without marking, no cap. Reserved for failures the
     * worker itself never routes here today (kept for defensive/unknown
     * codes, see the `else` branch below). Every *named* pure-retry HTTP
     * code has its own capped policy — see [RETRY_THEN_FAIL] — because an
     * uncapped RETRY is exactly the bug this classifier exists to prevent: a
     * phone with an expired token retrying forever and draining the battery.
     */
    RETRY,

    /**
     * Server-side 5xx. Retry (it may be a transient blip that never persisted);
     * once the attempt cap is reached, treat as DONE — an app-level 5xx was
     * captured as a failed-intent, so the visita is recoverable from the desk and
     * the phone should stop spinning.
     */
    RETRY_THEN_DONE,

    /**
     * Pure-retry codes with NO server-side custody guarantee: 401 (token
     * blip — may need re-auth, not just a resend), 408/425/429 (backoff
     * signals from the gateway/rate-limiter, which sits in front of the
     * cobranza failed-intent capture middleware and so never persists these).
     * Retry with backoff below the attempt cap; at the cap, STOP asking
     * WorkManager to retry — but never mark GUARDADO_EN_MICROSIP, because
     * unlike RETRY_THEN_DONE there is no proof the server holds the visita.
     * The worker returns `Result.failure()`, which ends that WorkManager job
     * without losing the record: the visita stays pending in Room and is
     * picked up again by `VisitsPendingSynchronizer`
     * (`SyncAllPendingWorkUseCase`, `ExistingWorkPolicy.REPLACE`) on the next
     * session — the same mechanism already built to resume workers stuck in
     * a terminal state. Capped, not lost.
     */
    RETRY_THEN_FAIL
}

/**
 * Classifies a visit upload HTTP status. The golden rule: only ever reach
 * DONE when there is confidence the server holds the visita (a 2xx, a 4xx that
 * the capture middleware guarantees is persisted, or a 409 that the server's
 * idempotent-by-id lookup proves is the SAME visita already stored). Network
 * failures never reach this function — they are always retried by the worker
 * so the visita is never lost from a device that alone still holds it.
 */
object VisitUploadClassifier {
    fun classifyHttpCode(code: Int): VisitUploadDecision = when (code) {
        // 409 = ErrVisitaYaExiste. The visita's ID is a client-generated UUID
        // used as an end-to-end idempotency key, and app/registrar_visita.go
        // handles the collision by looking the visita up by that ID and
        // returning the EXISTING one — the server already has it. So a 409
        // is a success signal, not a retry: mark DONE immediately, at
        // attempt 1, not only once the attempt cap is reached.
        409 -> VisitUploadDecision.DONE
        // 401 (token blip), 408/425/429 (gateway/rate-limiter backoff
        // signals): none of these reach the cobranza failed-intent capture
        // middleware, so there is no custody guarantee to fall back on.
        // Retry with a cap — see RETRY_THEN_FAIL.
        401, 408, 425, 429 -> VisitUploadDecision.RETRY_THEN_FAIL
        in 500..599 -> VisitUploadDecision.RETRY_THEN_DONE
        // Any other 4xx (400 malformed, 403 missing permission, 422 validation)
        // is captured server-side → the desk corrects it.
        in 400..499 -> VisitUploadDecision.DONE
        // 2xx never routes here (handled before classify); treat as done defensively.
        in 200..299 -> VisitUploadDecision.DONE
        // Unknown/other → be safe and retry rather than risk losing the visita.
        else -> VisitUploadDecision.RETRY
    }
}
