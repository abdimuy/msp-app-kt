package com.example.msp_app.core.upload

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La tabla de decisión de entrega garantizada. Cada renglón del spec
 * (`docs/module-standards/ENTREGA_GARANTIZADA.md`) tiene su aserción.
 */
class UploadDecisionTest {

    // ── Los dos casos que perdieron dinero ───────────────────────────────

    @Test
    fun `404 del tunel sin custodia se reintenta`() {
        // Antes devolvía DONE: un 404 de un proxy/túnel se leía como rechazo
        // definitivo del API y el pago se soltaba sin que nadie lo tuviera.
        assertEquals(
            UploadDecision.RETRY,
            classifyUpload(code = 404, reachedMspApi = false, captureConfirmed = false)
        )
    }

    @Test
    fun `500 que llego al API pero no se capturo se reintenta`() {
        // El caso del pool trabado: la petición falla Y la captura falla a la
        // vez. reachedMspApi es true (problem+json) pero nadie lo resguardó.
        assertEquals(
            UploadDecision.RETRY,
            classifyUpload(code = 500, reachedMspApi = true, captureConfirmed = false)
        )
    }

    // ── 2xx ──────────────────────────────────────────────────────────────

    @Test
    fun `2xx suelta la captura`() {
        listOf(200, 201, 202, 204, 299).forEach { code ->
            assertEquals(
                "HTTP $code debe soltar",
                UploadDecision.RELEASE,
                classifyUpload(code = code)
            )
        }
    }

    // ── Siempre reintentar ───────────────────────────────────────────────

    @Test
    fun `401 se reintenta en toda combinacion`() {
        listOf(false, true).forEach { reached ->
            listOf(false, true).forEach { captured ->
                assertEquals(
                    "401 (reached=$reached, captured=$captured) debe reintentar",
                    UploadDecision.RETRY,
                    classifyUpload(401, reachedMspApi = reached, captureConfirmed = captured)
                )
            }
        }
    }

    @Test
    fun `señales de backoff se reintentan aun con custodia confirmada`() {
        listOf(408, 409, 425, 429).forEach { code ->
            assertEquals(
                "HTTP $code debe reintentar aunque haya custodia",
                UploadDecision.RETRY,
                classifyUpload(code, captureConfirmed = true)
            )
        }
    }

    // ── La condición de paro ─────────────────────────────────────────────

    @Test
    fun `4xx con custodia confirmada suelta`() {
        listOf(400, 403, 404, 422).forEach { code ->
            assertEquals(
                "HTTP $code resguardado debe soltar",
                UploadDecision.RELEASE,
                classifyUpload(code, reachedMspApi = true, captureConfirmed = true)
            )
        }
    }

    @Test
    fun `5xx con custodia confirmada suelta`() {
        listOf(500, 502, 503).forEach { code ->
            assertEquals(
                "HTTP $code resguardado debe soltar",
                UploadDecision.RELEASE,
                classifyUpload(code, captureConfirmed = true)
            )
        }
    }

    @Test
    fun `4xx sin custodia se reintenta`() {
        listOf(400, 403, 422).forEach { code ->
            assertEquals(
                "HTTP $code sin custodia debe reintentar",
                UploadDecision.RETRY,
                classifyUpload(code, reachedMspApi = true, captureConfirmed = false)
            )
        }
    }

    // ── reachedMspApi ya no decide nada ──────────────────────────────────

    @Test
    fun `reachedMspApi no cambia ninguna decision`() {
        val codes = listOf(200, 401, 404, 409, 422, 500, 502, 999, 0, -1)
        codes.forEach { code ->
            listOf(false, true).forEach { captured ->
                assertEquals(
                    "HTTP $code (captured=$captured): reachedMspApi no debe influir",
                    classifyUpload(code, reachedMspApi = false, captureConfirmed = captured),
                    classifyUpload(code, reachedMspApi = true, captureConfirmed = captured)
                )
            }
        }
    }

    // ── Ante la duda, conservar ──────────────────────────────────────────

    @Test
    fun `codigos desconocidos se reintentan`() {
        listOf(0, -1, 100, 199, 300, 302, 600, 999).forEach { code ->
            assertEquals(
                "HTTP $code desconocido debe reintentar",
                UploadDecision.RETRY,
                classifyUpload(code)
            )
        }
    }

    @Test
    fun `un 3xx no se confunde con exito ni aun resguardado`() {
        // 3xx queda fuera de 400..599, así que la custodia no lo suelta.
        assertEquals(UploadDecision.RETRY, classifyUpload(301, captureConfirmed = true))
    }

    // ── El puerto de verificación ────────────────────────────────────────

    @Test
    fun `ExistenceVerifier distingue existe, no existe e indeterminado`() = runTest {
        val existe = ExistenceVerifier { true }
        val noExiste = ExistenceVerifier { false }
        val indeterminado = ExistenceVerifier { null }

        assertEquals(true, existe.exists("abc"))
        assertEquals(false, noExiste.exists("abc"))
        assertNull(indeterminado.exists("abc"))
    }

    @Test
    fun `el nombre de la cabecera es el que emite el servidor`() {
        assertEquals("X-Intent-Captured", HEADER_INTENT_CAPTURED)
    }
}
