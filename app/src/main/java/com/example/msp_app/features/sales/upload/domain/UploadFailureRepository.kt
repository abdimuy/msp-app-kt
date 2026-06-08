package com.example.msp_app.features.sales.upload.domain

/**
 * Port: how the upload-failure subsystem persists and queries the
 * upload-failure state of a local sale. Implementations live in
 * [com.example.msp_app.features.sales.upload.data]; this interface lets
 * the worker and view-model depend on the domain rather than on Room.
 */
interface UploadFailureRepository {

    /**
     * Persists a server rejection on the local sale, overwriting any prior
     * failure. The server middleware no longer caches 4xx (see
     * `fix(idempotency): cache only 2xx`) so each retry returns a fresh
     * error that reflects the current body — the latest failure is always
     * the most accurate, no precedence logic needed.
     */
    suspend fun recordFailure(saleId: String, failure: UploadFailure)

    /**
     * Clears any persisted upload-failure for [saleId] — called when the
     * worker succeeds and when the user edits a failed sale.
     */
    suspend fun clearFailure(saleId: String)

    /**
     * Returns the current Idempotency-Key for [saleId], minting one based
     * on [defaultKey] if none has been set yet. The default is the saleId
     * itself, matching the legacy behavior used before this feature shipped.
     */
    suspend fun currentIdempotencyKey(saleId: String, defaultKey: String): String

    /** Rotates the Idempotency-Key to [newKey]. Used by edit-and-retry. */
    suspend fun rotateIdempotencyKey(saleId: String, newKey: String)

    /**
     * Atomic edit-and-retry hand-off: clears the persisted failure (so the
     * UI doesn't show a stale error after the corrected resubmit) and rotates
     * the Idempotency-Key (so the corrected body avoids cache-mismatch
     * against a prior cached 2xx).
     *
     * @return the freshly-minted key — callers may persist it, log it, or
     *         pass it into a worker enqueue payload.
     */
    suspend fun resetForEditAndRetry(saleId: String): String
}
