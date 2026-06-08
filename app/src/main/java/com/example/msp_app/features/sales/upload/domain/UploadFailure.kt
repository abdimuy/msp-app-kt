package com.example.msp_app.features.sales.upload.domain

/**
 * Snapshot of a server-side rejection of a venta upload.
 *
 * Domain entity — pure Kotlin, no Android imports — so the classifier and
 * any logic that reasons about failures can be unit-tested without Room or
 * coroutines machinery.
 */
data class UploadFailure(
    val httpCode: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val classification: UploadFailureClassification,
    val atEpochMillis: Long
) {
    val isPermanent: Boolean
        get() = classification == UploadFailureClassification.PERMANENT
}

/**
 * Permanent failures will not succeed without the client correcting the
 * request body — the worker should not keep retrying. Transient failures
 * can succeed on a subsequent attempt (network blip, 5xx, 429, etc.).
 *
 * This taxonomy is the load-bearing input to `Result.failure()` vs
 * `Result.retry()` in [PendingLocalSalesWorker].
 */
enum class UploadFailureClassification { PERMANENT, TRANSIENT }
