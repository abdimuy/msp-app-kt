package com.example.msp_app.features.sales.upload.data

import com.example.msp_app.features.sales.upload.domain.UploadFailure
import com.example.msp_app.features.sales.upload.domain.UploadFailureClassification
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Room-backed adapter writes and reads the upload-failure
 * columns introduced in migration 25→26, and that idempotency-key
 * rotation behaves as the worker expects.
 */
class RoomUploadFailureRepositoryTest : RoomTestBase() {

    private val dao by lazy { db.localSaleDao() }
    private val repo by lazy { RoomUploadFailureRepository(dao) }

    @Test
    fun recordFailure_persists_all_fields() = runTest {
        val saleId = seedSale("sale-rec")

        val failure = UploadFailure(
            httpCode = 422,
            errorCode = "plazo_invalido",
            errorMessage = "el plazo en meses debe ser mayor a cero",
            classification = UploadFailureClassification.PERMANENT,
            atEpochMillis = 1_700_000_000_000L
        )
        repo.recordFailure(saleId, failure)

        val stored = dao.getSaleById(saleId)
        assertNotNull(stored)
        assertEquals(422, stored!!.LAST_UPLOAD_HTTP_CODE)
        assertEquals("plazo_invalido", stored.LAST_UPLOAD_ERROR_CODE)
        assertEquals("el plazo en meses debe ser mayor a cero", stored.LAST_UPLOAD_ERROR_MESSAGE)
        assertEquals(1_700_000_000_000L, stored.LAST_UPLOAD_AT)
        assertEquals(true, stored.LAST_UPLOAD_PERMANENT)
    }

    @Test
    fun recordFailure_overwrites_prior_failure() = runTest {
        val saleId = seedSale("sale-overwrite")

        repo.recordFailure(
            saleId,
            UploadFailure(
                httpCode = 500,
                errorCode = null,
                errorMessage = "boom",
                classification = UploadFailureClassification.TRANSIENT,
                atEpochMillis = 1L
            )
        )
        repo.recordFailure(
            saleId,
            UploadFailure(
                httpCode = 422,
                errorCode = "plazo_invalido",
                errorMessage = "el plazo en meses debe ser mayor a cero",
                classification = UploadFailureClassification.PERMANENT,
                atEpochMillis = 2L
            )
        )

        val stored = dao.getSaleById(saleId)!!
        assertEquals(422, stored.LAST_UPLOAD_HTTP_CODE)
        assertEquals("plazo_invalido", stored.LAST_UPLOAD_ERROR_CODE)
        assertEquals(2L, stored.LAST_UPLOAD_AT)
        assertEquals(true, stored.LAST_UPLOAD_PERMANENT)
    }

    @Test
    fun clearFailure_nulls_all_columns_but_keeps_idempotency_key() = runTest {
        val saleId = seedSale("sale-clear")

        repo.recordFailure(
            saleId,
            UploadFailure(
                httpCode = 422,
                errorCode = "x",
                errorMessage = "y",
                classification = UploadFailureClassification.PERMANENT,
                atEpochMillis = 1L
            )
        )
        repo.rotateIdempotencyKey(saleId, "fresh-key-42")
        repo.clearFailure(saleId)

        val stored = dao.getSaleById(saleId)!!
        assertNull(stored.LAST_UPLOAD_HTTP_CODE)
        assertNull(stored.LAST_UPLOAD_ERROR_CODE)
        assertNull(stored.LAST_UPLOAD_ERROR_MESSAGE)
        assertNull(stored.LAST_UPLOAD_AT)
        assertNull(stored.LAST_UPLOAD_PERMANENT)
        assertEquals(
            "clearFailure must not touch the idempotency key — rotation is a separate concern",
            "fresh-key-42",
            stored.IDEMPOTENCY_KEY
        )
    }

    @Test
    fun currentIdempotencyKey_returns_default_when_unset() = runTest {
        val saleId = seedSale("sale-default-key")

        val resolved = repo.currentIdempotencyKey(saleId, defaultKey = saleId)

        assertEquals(saleId, resolved)
    }

    @Test
    fun currentIdempotencyKey_returns_rotated_key_when_set() = runTest {
        val saleId = seedSale("sale-rotated-key")

        repo.rotateIdempotencyKey(saleId, "rotated-uuid-v1")
        val resolved = repo.currentIdempotencyKey(saleId, defaultKey = saleId)

        assertEquals("rotated-uuid-v1", resolved)
    }

    @Test
    fun rotateIdempotencyKey_overwrites_prior_rotation() = runTest {
        val saleId = seedSale("sale-rerotate")

        repo.rotateIdempotencyKey(saleId, "key-1")
        repo.rotateIdempotencyKey(saleId, "key-2")

        val resolved = repo.currentIdempotencyKey(saleId, defaultKey = saleId)
        assertEquals("key-2", resolved)
    }

    @Test
    fun recordFailure_with_null_errorCode_and_message_persists_nulls() = runTest {
        val saleId = seedSale("sale-null-fields")

        repo.recordFailure(
            saleId,
            UploadFailure(
                httpCode = 500,
                errorCode = null,
                errorMessage = null,
                classification = UploadFailureClassification.TRANSIENT,
                atEpochMillis = 7L
            )
        )

        val stored = dao.getSaleById(saleId)!!
        assertEquals(500, stored.LAST_UPLOAD_HTTP_CODE)
        assertNull(stored.LAST_UPLOAD_ERROR_CODE)
        assertNull(stored.LAST_UPLOAD_ERROR_MESSAGE)
        assertTrue(
            "transient must persist as LAST_UPLOAD_PERMANENT=false",
            stored.LAST_UPLOAD_PERMANENT == false
        )
    }

    private suspend fun seedSale(saleId: String): String {
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        return saleId
    }
}
