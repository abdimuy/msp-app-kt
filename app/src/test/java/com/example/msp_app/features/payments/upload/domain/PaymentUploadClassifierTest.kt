package com.example.msp_app.features.payments.upload.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decisión de producto (refinada): el server (`msp-api`) captura como
 * failed-intent cualquier respuesta >=400 que él mismo emite — 4xx Y 5xx —
 * sin deduplicar por reintento (cada captura es un ID nuevo). Reintentar un
 * 5xx que SÍ llegó a msp-api de forma indefinida solo spammea esa bandeja.
 *
 * Por eso "marcar listo" ya no depende solo del código: depende de si la
 * respuesta VINO de msp-api. La señal confirmada es el `Content-Type`:
 * msp-api siempre responde sus errores como `application/problem+json`
 * (ver msp-api `response.go`); un 5xx de gateway/proxy en frente de msp-api
 * devuelve HTML/texto plano, nunca problem+json — esa respuesta jamás
 * llegó a la captura de fallidos, así que el pago NO puede darse por
 * capturado y debe reintentarse por siempre.
 *
 * Reglas:
 * - 2xx → DONE.
 * - 401 → RETRY (token blip, no rechazo de datos).
 * - 408/409/425/429 → RETRY (señales de backoff).
 * - otro 4xx → DONE (siempre viene de msp-api con problem+json → capturado).
 * - 5xx && reachedMspApi → DONE (llegó y se capturó una vez; el desk lo resuelve).
 * - 5xx && !reachedMspApi → RETRY (gateway/proxy: nunca llegó, no se pierde).
 * - código desconocido → RETRY.
 */
class PaymentUploadClassifierTest {

    @Test
    fun `2xx is DONE`() {
        listOf(200, 201, 204).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.DONE,
                PaymentUploadClassifier.classify(code, reachedMspApi = true)
            )
        }
    }

    @Test
    fun `401 is RETRY regardless of reachedMspApi (token blip, not a data rejection)`() {
        assertEquals(
            PaymentUploadDecision.RETRY,
            PaymentUploadClassifier.classify(401, reachedMspApi = true)
        )
        assertEquals(
            PaymentUploadDecision.RETRY,
            PaymentUploadClassifier.classify(401, reachedMspApi = false)
        )
    }

    @Test
    fun `408 409 425 429 are RETRY (backoff signals)`() {
        listOf(408, 409, 425, 429).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.RETRY,
                PaymentUploadClassifier.classify(code, reachedMspApi = true)
            )
        }
    }

    @Test
    fun `other 4xx are DONE because the failed-intent middleware captured them`() {
        listOf(400, 403, 404, 422).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.DONE,
                PaymentUploadClassifier.classify(code, reachedMspApi = true)
            )
        }
    }

    @Test
    fun `5xx with reachedMspApi=true is DONE — captured once, desk resolves it`() {
        listOf(500, 502, 503, 504).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.DONE,
                PaymentUploadClassifier.classify(code, reachedMspApi = true)
            )
        }
    }

    @Test
    fun `5xx with reachedMspApi=false is RETRY — gateway never captured it`() {
        listOf(500, 502, 503, 504).forEach { code ->
            assertEquals(
                "code=$code",
                PaymentUploadDecision.RETRY,
                PaymentUploadClassifier.classify(code, reachedMspApi = false)
            )
        }
    }

    @Test
    fun `unknown code defaults to RETRY`() {
        assertEquals(
            PaymentUploadDecision.RETRY,
            PaymentUploadClassifier.classify(999, reachedMspApi = true)
        )
        assertEquals(
            PaymentUploadDecision.RETRY,
            PaymentUploadClassifier.classify(999, reachedMspApi = false)
        )
    }
}
