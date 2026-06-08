package com.example.msp_app.features.sales.upload.domain

/**
 * Pure function mapping a server response to a permanent/transient verdict.
 *
 * The rules follow the patterns documented by Stripe and Google Standard
 * Payments (see ADR / commit `fix(idempotency): cache only 2xx`):
 *
 *  - 4xx in the client-error range → PERMANENT. The body the client sent is
 *    wrong; reintry won't help until the user fixes it. Includes 400, 403,
 *    404, 422.
 *  - 408 / 409 / 425 / 429 → TRANSIENT. The semantics are "try again
 *    later" (408/425 timeouts, 409 concurrency, 429 rate limit).
 *  - 5xx → TRANSIENT. Server-side problems are not the client's to fix.
 *  - anything else (1xx, 3xx, network exceptions handled elsewhere)
 *    defaults to TRANSIENT so we err on the side of retrying.
 *
 * 410 Gone is treated as PERMANENT — the resource is intentionally absent
 * (e.g. soft-deleted contract).
 */
object UploadFailureClassifier {
    fun classify(httpCode: Int): UploadFailureClassification = when (httpCode) {
        408, 409, 425, 429 -> UploadFailureClassification.TRANSIENT
        in 400..499 -> UploadFailureClassification.PERMANENT
        in 500..599 -> UploadFailureClassification.TRANSIENT
        else -> UploadFailureClassification.TRANSIENT
    }
}
