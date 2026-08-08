package com.example.msp_app.features.sales.upload.data

import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.features.sales.upload.domain.UploadFailure
import com.example.msp_app.features.sales.upload.domain.UploadFailureRepository
import java.util.UUID

/**
 * Adapter: implements [UploadFailureRepository] against the Room DAO.
 *
 * Kept in the `data` package of the upload feature so the domain stays
 * Android-free. The worker constructs this directly — no DI framework in
 * this project — and tests substitute an in-memory or fake implementation.
 *
 * @param dao         Room DAO carrying the upload-failure columns.
 * @param keyFactory  Idempotency-Key minter, defaults to a random UUID.
 *                    Overridable in tests so assertions can pin the key.
 */
class RoomUploadFailureRepository(
    private val dao: LocalSaleDao,
    private val keyFactory: () -> String = { UUID.randomUUID().toString() }
) : UploadFailureRepository {

    override suspend fun recordFailure(saleId: String, failure: UploadFailure) {
        dao.updateUploadFailure(
            saleId = saleId,
            httpCode = failure.httpCode,
            errorCode = failure.errorCode,
            errorMessage = failure.errorMessage,
            at = failure.atEpochMillis,
            permanent = failure.isPermanent
        )
    }

    override suspend fun clearFailure(saleId: String) {
        dao.clearUploadFailure(saleId)
    }

    override suspend fun currentIdempotencyKey(saleId: String, defaultKey: String): String {
        val existing = dao.getSaleById(saleId)?.IDEMPOTENCY_KEY
        return existing ?: defaultKey
    }

    override suspend fun rotateIdempotencyKey(saleId: String, newKey: String) {
        dao.updateIdempotencyKey(saleId, newKey)
    }

    override suspend fun resetForEditAndRetry(saleId: String): String {
        val freshKey = keyFactory()
        dao.clearUploadFailure(saleId)
        dao.updateIdempotencyKey(saleId, freshKey)
        return freshKey
    }
}
