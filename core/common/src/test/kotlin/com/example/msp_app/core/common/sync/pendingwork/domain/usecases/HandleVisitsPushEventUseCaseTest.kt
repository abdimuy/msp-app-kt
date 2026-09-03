package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.ERROR_CODE_EVENTO_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.ERROR_CODE_STREAM_CAIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.ERROR_CODE_STREAM_DESHABILITADO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.EVENT_VISITAS_CONFIRMADAS
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.PROP_HTTP_CODE
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The push event is the visits reconciler's fourth trigger. These tests pin
 * the two properties that make it safe to add:
 *
 *  1. a recognised event triggers a reconciliation and **marks nothing**;
 *  2. an unrecognised event triggers **nothing at all**.
 *
 * The second is the one that matters most, and it is stronger than the bar the
 * task brief originally set. "An unknown event does not break the stream"
 * would have passed against the cobranza client already in the field — and
 * that client, measured with a probe, treats every event on a socket as that
 * socket's event regardless of its name. Surviving an event you then act on
 * wrongly is the defect; not acting is the property.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HandleVisitsPushEventUseCaseTest {

    /** Counts triggers. A trigger that never fires is what most of these assert. */
    private class RecordingTrigger(private val throws: Throwable? = null) {
        var calls: Int = 0
            private set

        suspend fun invoke() {
            calls++
            throws?.let { throw it }
        }
    }

    private fun build(trigger: RecordingTrigger, reporter: RecordingSyncErrorReporter) =
        HandleVisitsPushEventUseCase(
            triggerReconciliation = { trigger.invoke() },
            errorReporter = reporter
        )

    // ─── El evento reconocido ────────────────────────────────────────────────

    @Test
    fun `el evento conocido dispara exactamente una reconciliacion`() = runTest {
        val trigger = RecordingTrigger()
        val reporter = RecordingSyncErrorReporter()

        val outcome = build(trigger, reporter).onEvent(EVENT_VISITAS_CONFIRMADAS)

        assertEquals(HandleVisitsPushEventUseCase.Outcome.RECONCILIACION_DISPARADA, outcome)
        assertEquals(1, trigger.calls)
        assertTrue("un evento sano no reporta errores", reporter.reported.isEmpty())
    }

    // ─── Compatibilidad hacia adelante: LAS DOS MITADES ──────────────────────

    @Test
    fun `un tipo desconocido NO dispara ninguna accion`() = runTest {
        val trigger = RecordingTrigger()
        val reporter = RecordingSyncErrorReporter()

        val outcome = build(trigger, reporter).onEvent("un_evento_del_futuro")

        assertEquals(HandleVisitsPushEventUseCase.Outcome.IGNORADO_TIPO_DESCONOCIDO, outcome)
        assertEquals(
            "un tipo desconocido no debe disparar NADA — sobrevivir a un evento " +
                "sobre el que igual actúas es el defecto medido en el cliente de cobranza",
            0,
            trigger.calls
        )
    }

    @Test
    fun `los tipos de cobranza no disparan el reconciliador de visitas`() = runTest {
        val trigger = RecordingTrigger()
        val reporter = RecordingSyncErrorReporter()
        val handler = build(trigger, reporter)

        // Exactamente los nombres que el servidor ya emite en OTROS streams.
        handler.onEvent("pagos_changed")
        handler.onEvent("saldos_changed")

        assertEquals(
            "un evento de cobranza jamás debe reconciliar visitas",
            0,
            trigger.calls
        )
    }

    @Test
    fun `un evento sin nombre se trata como desconocido`() = runTest {
        val trigger = RecordingTrigger()
        val reporter = RecordingSyncErrorReporter()

        val outcome = build(trigger, reporter).onEvent(null)

        assertEquals(HandleVisitsPushEventUseCase.Outcome.IGNORADO_TIPO_DESCONOCIDO, outcome)
        assertEquals(0, trigger.calls)
    }

    @Test
    fun `el nombre se compara exacto, sin prefijos ni mayusculas`() = runTest {
        val trigger = RecordingTrigger()
        val reporter = RecordingSyncErrorReporter()
        val handler = build(trigger, reporter)

        listOf(
            "VISITAS_CONFIRMADAS",
            "visitas_confirmadas_v2",
            "pre_visitas_confirmadas",
            " visitas_confirmadas",
            ""
        ).forEach { handler.onEvent(it) }

        assertEquals("solo el nombre exacto dispara", 0, trigger.calls)
    }

    @Test
    fun `ignorar un tipo desconocido no reporta telemetria`() = runTest {
        val reporter = RecordingSyncErrorReporter()

        build(RecordingTrigger(), reporter).onEvent("un_evento_del_futuro")

        assertTrue(
            "la compatibilidad hacia adelante funcionando no es una falla: reportar " +
                "cada evento desconocido convertiría un deploy skew sano en una " +
                "inundación de telemetría",
            reporter.reported.isEmpty()
        )
    }

    // ─── Norma de errores ────────────────────────────────────────────────────

    @Test
    fun `si el disparo lanza, se reporta con el codigo nombrado`() = runTest {
        val trigger = RecordingTrigger(throws = IllegalStateException("grafo no listo"))
        val reporter = RecordingSyncErrorReporter()

        val outcome = build(trigger, reporter).onEvent(EVENT_VISITAS_CONFIRMADAS)

        assertEquals(HandleVisitsPushEventUseCase.Outcome.FALLO_AL_DISPARAR, outcome)
        assertEquals(listOf(ERROR_CODE_EVENTO_FALLIDO), reporter.codes)
    }

    @Test
    fun `el reporte no filtra el mensaje crudo de la excepcion`() = runTest {
        val secreto = "María Hernández debe 4200"
        val trigger = RecordingTrigger(throws = IllegalStateException(secreto))
        val reporter = RecordingSyncErrorReporter()

        build(trigger, reporter).onEvent(EVENT_VISITAS_CONFIRMADAS)

        val reportado = reporter.reported.single()
        assertTrue(
            "el mensaje debe llevar la CLASE de la excepción, no su texto (LFPDPPP)",
            reportado.message.contains("IllegalStateException")
        )
        assertTrue(
            "ningún dato de negocio puede viajar en la telemetría",
            !reportado.message.contains(secreto)
        )
    }

    @Test
    fun `una cancelacion se propaga y no se reporta como error`() = runTest {
        val trigger = RecordingTrigger(throws = CancellationException("scope cerrado"))
        val reporter = RecordingSyncErrorReporter()
        val handler = build(trigger, reporter)

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { handler.onEvent(EVENT_VISITAS_CONFIRMADAS) }
        }
        assertTrue(
            "cancelar no es fallar: reportarlo llenaría la cola de ruido en cada stop()",
            reporter.reported.isEmpty()
        )
    }

    @Test
    fun `un stream caido se reporta con su codigo y su status`() {
        val reporter = RecordingSyncErrorReporter()

        build(RecordingTrigger(), reporter).onStreamFailure(java.io.IOException("socket"), 502)

        val reportado = reporter.reported.single()
        assertEquals(ERROR_CODE_STREAM_CAIDO, reportado.code)
        assertEquals("502", reportado.props[PROP_HTTP_CODE])
        assertTrue(reportado.message.contains("IOException"))
    }

    @Test
    fun `un stream caido sin throwable ni status se reporta igual`() {
        val reporter = RecordingSyncErrorReporter()

        build(RecordingTrigger(), reporter).onStreamFailure(null, null)

        val reportado = reporter.reported.single()
        assertEquals(
            "una caída sin causa identificable debe reportarse igual: un stream " +
                "que muere en silencio es invisible por construcción",
            ERROR_CODE_STREAM_CAIDO,
            reportado.code
        )
        assertTrue(reportado.props.isEmpty())
    }

    @Test
    fun `el 503 del servidor tiene codigo propio, distinto de una caida`() {
        val reporter = RecordingSyncErrorReporter()

        build(RecordingTrigger(), reporter).onStreamDisabledByServer()

        assertEquals(listOf(ERROR_CODE_STREAM_DESHABILITADO), reporter.codes)
        assertTrue(
            "apagar el flag a propósito y perder el stream por una falla son cosas " +
                "distintas justo cuando alguien intenta distinguirlas",
            ERROR_CODE_STREAM_DESHABILITADO != ERROR_CODE_STREAM_CAIDO
        )
    }

    @Test
    fun `todos los codigos son constantes unicas y grepeables`() {
        val codigos = listOf(
            ERROR_CODE_EVENTO_FALLIDO,
            ERROR_CODE_STREAM_CAIDO,
            ERROR_CODE_STREAM_DESHABILITADO
        )

        assertEquals("los códigos deben ser únicos", codigos.size, codigos.toSet().size)
        codigos.forEach {
            assertTrue(
                "'$it' debe empezar con visitas_ para poder greparlo",
                it.startsWith("visitas_")
            )
        }
    }

    @Test
    fun `el nombre del evento coincide con el contrato del servidor`() {
        assertEquals(
            "debe coincidir con sseEventName en handlers_sse.go — si divergen, el " +
                "canal de push queda silenciosamente muerto",
            "visitas_confirmadas",
            EVENT_VISITAS_CONFIRMADAS
        )
    }
}
