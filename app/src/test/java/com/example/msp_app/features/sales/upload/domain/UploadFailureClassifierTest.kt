package com.example.msp_app.features.sales.upload.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadFailureClassifierTest {

    @Test
    fun `422 validation error is permanent`() {
        assertEquals(
            UploadFailureClassification.PERMANENT,
            UploadFailureClassifier.classify(422)
        )
    }

    @Test
    fun `400 bad request is permanent`() {
        assertEquals(
            UploadFailureClassification.PERMANENT,
            UploadFailureClassifier.classify(400)
        )
    }

    @Test
    fun `403 forbidden is permanent`() {
        assertEquals(
            UploadFailureClassification.PERMANENT,
            UploadFailureClassifier.classify(403)
        )
    }

    @Test
    fun `404 not found is permanent`() {
        assertEquals(
            UploadFailureClassification.PERMANENT,
            UploadFailureClassifier.classify(404)
        )
    }

    @Test
    fun `410 gone is permanent`() {
        assertEquals(
            UploadFailureClassification.PERMANENT,
            UploadFailureClassifier.classify(410)
        )
    }

    @Test
    fun `408 request timeout is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(408)
        )
    }

    @Test
    fun `409 conflict is transient — IETF reserves it for in-flight concurrency`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(409)
        )
    }

    @Test
    fun `425 too early is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(425)
        )
    }

    @Test
    fun `429 too many requests is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(429)
        )
    }

    @Test
    fun `500 internal server error is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(500)
        )
    }

    @Test
    fun `502 bad gateway is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(502)
        )
    }

    @Test
    fun `503 service unavailable is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(503)
        )
    }

    @Test
    fun `504 gateway timeout is transient`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(504)
        )
    }

    @Test
    fun `unknown status defaults to transient — err on retry side`() {
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(0)
        )
        assertEquals(
            UploadFailureClassification.TRANSIENT,
            UploadFailureClassifier.classify(999)
        )
    }
}
