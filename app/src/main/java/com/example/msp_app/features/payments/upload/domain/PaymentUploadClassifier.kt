package com.example.msp_app.features.payments.upload.domain

/**
 * What the payment upload worker should do with a given HTTP response code.
 *
 * The semantics differ deliberately from the sales `UploadFailureClassifier`: a
 * pago that fails validation (4xx) is **not** surrendered — because the cobranza
 * route now mounts the failed-intent capture middleware, any 4xx/5xx the server
 * produces is persisted server-side with its full body. So a 4xx means "the
 * server has it; correct it from the desk", which for the phone is DONE.
 */
enum class PaymentUploadDecision {
    /**
     * The server has the pago. Either it was applied/accepted (2xx) or it was
     * rejected but captured as a failed-intent (data 4xx). Mark
     * GUARDADO_EN_MICROSIP=true — the phone is finished; resolution lives desk-side.
     */
    DONE,

    /**
     * Transient — retry without marking. The server either did not see the pago
     * (401 token blip refreshed by the interceptor) or asked us to back off
     * (408/409/425/429).
     */
    RETRY,

    /**
     * Server-side 5xx. Retry (it may be a transient blip that never persisted);
     * once the attempt cap is reached, treat as DONE — an app-level 5xx was
     * captured as a failed-intent, so the pago is recoverable from the desk and
     * the phone should stop spinning.
     */
    RETRY_THEN_DONE
}

/**
 * Classifies a payment upload HTTP status. The golden rule: only ever reach
 * DONE when there is confidence the server holds the pago (a 2xx, or a 4xx that
 * the capture middleware guarantees is persisted). Network failures never reach
 * this function — they are always retried by the worker so the pago is never
 * lost from a device that alone still holds it.
 */
object PaymentUploadClassifier {
    fun classifyHttpCode(code: Int): PaymentUploadDecision = when (code) {
        // 401 is inside 400..499 but is a token blip, not a data rejection.
        401 -> PaymentUploadDecision.RETRY
        408, 409, 425, 429 -> PaymentUploadDecision.RETRY
        in 500..599 -> PaymentUploadDecision.RETRY_THEN_DONE
        // Any other 4xx (400 malformed, 403 missing permission, 422 validation)
        // is captured server-side → the desk corrects it.
        in 400..499 -> PaymentUploadDecision.DONE
        // 2xx never routes here (handled before classify); treat as done defensively.
        in 200..299 -> PaymentUploadDecision.DONE
        // Unknown/other → be safe and retry rather than risk losing the pago.
        else -> PaymentUploadDecision.RETRY
    }
}
