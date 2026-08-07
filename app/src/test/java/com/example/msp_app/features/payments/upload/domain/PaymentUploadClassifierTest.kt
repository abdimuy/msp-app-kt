package com.example.msp_app.features.payments.upload.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decisión de producto: un 5xx SIEMPRE reintenta, nunca marca listo. Un 5xx
 * puede originarse en un gateway/proxy ANTES de que msp-api capture el
 * intent — asumir "ya capturado" podía perder el pago. Es preferible un
 * pago atorado-y-visible (reintentando por siempre) a uno perdido.
 *
 * Solo dos caminos llegan a DONE: 2xx (aplicado) y 4xx-no-401 (capturado por
 * el middleware de fallidos). Red y 5xx nunca marcan listo.
 */
class PaymentUploadClassifierTest {

    @Test
    fun `2xx is DONE`() {
        listOf(200, 201, 204).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.DONE,
                PaymentUploadClassifier.classifyHttpCode(code)
            )
        }
    }

    @Test
    fun `401 is RETRY (token blip, not a data rejection)`() {
        assertEquals(PaymentUploadDecision.RETRY, PaymentUploadClassifier.classifyHttpCode(401))
    }

    @Test
    fun `408 409 425 429 are RETRY (backoff signals)`() {
        listOf(408, 409, 425, 429).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.RETRY,
                PaymentUploadClassifier.classifyHttpCode(code)
            )
        }
    }

    @Test
    fun `other 4xx are DONE because the failed-intent middleware captured them`() {
        listOf(400, 403, 404, 422).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.DONE,
                PaymentUploadClassifier.classifyHttpCode(code)
            )
        }
    }

    @Test
    fun `5xx is always RETRY, never DONE`() {
        listOf(500, 502, 503, 504).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.RETRY,
                PaymentUploadClassifier.classifyHttpCode(code)
            )
        }
    }

    @Test
    fun `unknown code defaults to RETRY`() {
        assertEquals(PaymentUploadDecision.RETRY, PaymentUploadClassifier.classifyHttpCode(999))
    }
}
