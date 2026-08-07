package com.example.msp_app.features.payments.upload.domain

/**
 * What the payment upload worker should do with a given HTTP response code.
 *
 * The semantics differ deliberately from the sales `UploadFailureClassifier`: a
 * pago that fails validation (4xx) is **not** surrendered — because the cobranza
 * route now mounts the failed-intent capture middleware, any 4xx/5xx that
 * msp-api itself produces is persisted server-side with its full body. So a
 * rejection that reached msp-api means "the server has it; correct it from the
 * desk", which for the phone is DONE.
 *
 * A 5xx is ambiguous on its own: it can come from msp-api itself (captured), or
 * from a gateway/proxy sitting in front of msp-api that never forwarded the
 * request far enough to be captured. Treating every 5xx as captured could
 * silently drop a pago that a gateway swallowed; treating every 5xx as
 * uncaptured (retry forever) spams the failed-intent inbox with duplicate
 * captures every time a msp-api 5xx *is* retried indefinitely — the capture
 * middleware assigns a fresh ID per attempt, it does not deduplicate.
 *
 * The resolving signal is `Content-Type`: msp-api always answers its own
 * errors with `application/problem+json` (see msp-api's `response.go`). A
 * gateway/proxy 5xx returns HTML or plain text, never `problem+json`. So
 * "reached msp-api" == the response's `Content-Type` contains `problem+json`.
 * See [PaymentUploadClassifier].
 */
enum class PaymentUploadDecision {
    /**
     * The server has the pago: it was applied/accepted (2xx), rejected but
     * captured as a failed-intent (a non-blip 4xx, always problem+json), or a
     * 5xx that msp-api itself produced (problem+json — captured once). Mark
     * GUARDADO_EN_MICROSIP=true — the phone is finished; resolution lives
     * desk-side.
     */
    DONE,

    /**
     * Never mark done — keep retrying. Covers the token blip (401), backoff
     * signals (408/409/425/429), and any 5xx that did NOT come from msp-api
     * (no `problem+json` — a gateway/proxy in front of it never captured the
     * intent). Only a confirmed-reached response is trusted enough to stop.
     */
    RETRY
}

/**
 * Classifies a payment upload HTTP status plus whether the response actually
 * reached msp-api (see [PaymentUploadDecision] for the `problem+json` signal
 * and the rationale). The golden rule: only ever reach DONE when there is
 * confidence the server holds the pago. Network failures never reach this
 * function — they are always retried by the worker so the pago is never lost
 * from a device that alone still holds it.
 */
object PaymentUploadClassifier {
    fun classify(code: Int, reachedMspApi: Boolean): PaymentUploadDecision = when {
        // 401 is inside 400..499 but is a token blip, not a data rejection.
        code == 401 -> PaymentUploadDecision.RETRY
        code == 408 || code == 409 || code == 425 || code == 429 -> PaymentUploadDecision.RETRY
        // 5xx: only trust it as captured when it actually reached msp-api
        // (problem+json). A gateway/proxy 5xx never got captured — retry
        // forever rather than risk losing the pago. A msp-api 5xx WAS
        // captured once (fresh ID, no dedup) — retrying it forever would
        // just spam the failed-intent inbox with duplicate captures, so it
        // is DONE: the desk resolves it from there.
        code in 500..599 -> if (reachedMspApi) {
            PaymentUploadDecision.DONE
        } else {
            PaymentUploadDecision.RETRY
        }
        // Any other 4xx (400 malformed, 403 missing permission, 422 validation)
        // is captured server-side (always problem+json) → the desk corrects it.
        code in 400..499 -> PaymentUploadDecision.DONE
        // 2xx never routes here (handled before classify); treat as done defensively.
        code in 200..299 -> PaymentUploadDecision.DONE
        // Unknown/other → be safe and retry rather than risk losing the pago.
        else -> PaymentUploadDecision.RETRY
    }
}
