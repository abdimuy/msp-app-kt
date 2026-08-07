package com.example.msp_app.features.payments.upload.domain

/**
 * What the payment upload worker should do with a given HTTP response code.
 *
 * The semantics differ deliberately from the sales `UploadFailureClassifier`: a
 * pago that fails validation (4xx) is **not** surrendered — because the cobranza
 * route now mounts the failed-intent capture middleware, any 4xx the server
 * produces is persisted server-side with its full body. So a 4xx means "the
 * server has it; correct it from the desk", which for the phone is DONE.
 *
 * A 5xx is different: it can originate at a gateway/proxy in front of msp-api,
 * before the app ever captures the intent. Assuming "the server has it" on a
 * 5xx could silently drop the pago. Product decision: a 5xx ALWAYS retries and
 * NEVER reaches DONE — there is no attempt cap. Better a pago stuck-and-visible
 * (retrying forever) than one lost. See [PaymentUploadClassifier].
 */
enum class PaymentUploadDecision {
    /**
     * The server has the pago. Either it was applied/accepted (2xx) or it was
     * rejected but captured as a failed-intent (data 4xx, excluding 401/408/
     * 409/425/429). Mark GUARDADO_EN_MICROSIP=true — the phone is finished;
     * resolution lives desk-side.
     */
    DONE,

    /**
     * Never mark done — keep retrying. Covers the token blip (401), backoff
     * signals (408/409/425/429), and — by product decision — EVERY 5xx,
     * indefinitely: a 5xx might come from a gateway/proxy that never reached
     * msp-api's failed-intent capture, so the pago can never be assumed
     * captured. Only 2xx and a captured 4xx are trusted enough to stop.
     */
    RETRY
}

/**
 * Classifies a payment upload HTTP status. The golden rule: only ever reach
 * DONE when there is confidence the server holds the pago (a 2xx, or a 4xx that
 * the capture middleware guarantees is persisted). Network failures never reach
 * this function — they are always retried by the worker so the pago is never
 * lost from a device that alone still holds it. 5xx is classified the same way
 * as a network failure: retry forever, never DONE.
 */
object PaymentUploadClassifier {
    fun classifyHttpCode(code: Int): PaymentUploadDecision = when (code) {
        // 401 is inside 400..499 but is a token blip, not a data rejection.
        401 -> PaymentUploadDecision.RETRY
        408, 409, 425, 429 -> PaymentUploadDecision.RETRY
        // Product decision: 5xx retries forever, never DONE. It may originate
        // at a gateway/proxy before msp-api's failed-intent capture ever ran,
        // so the pago can never be assumed persisted server-side.
        in 500..599 -> PaymentUploadDecision.RETRY
        // Any other 4xx (400 malformed, 403 missing permission, 422 validation)
        // is captured server-side → the desk corrects it.
        in 400..499 -> PaymentUploadDecision.DONE
        // 2xx never routes here (handled before classify); treat as done defensively.
        in 200..299 -> PaymentUploadDecision.DONE
        // Unknown/other → be safe and retry rather than risk losing the pago.
        else -> PaymentUploadDecision.RETRY
    }
}
