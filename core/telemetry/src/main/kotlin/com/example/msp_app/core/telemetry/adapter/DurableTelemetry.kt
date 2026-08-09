package com.example.msp_app.core.telemetry.adapter

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.TelemetryEvent
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.telemetry.queue.DurableTelemetryQueue
import com.example.msp_app.core.telemetry.queue.TelemetrySink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Adapter real de [Telemetry] (Plan 4, Task 4): traduce cada llamada del
 * puerto a un [TelemetryEvent] y lo encola en la [queue] durable (T3), que ya
 * resuelve durabilidad/FIFO/reintento/"nunca tirar errores" — este adapter NO
 * repite esa política, solo mapea forma y decide QUÉ tan síncrono es cada
 * llamada:
 *
 * - `screenView`/`tap`/`event` (ruido de UI, alto volumen): se encolan de
 *   forma **asíncrona** vía [enqueueScope] — bloquear el hilo de UI en cada
 *   tap sería peor que el riesgo aceptado (perder el evento si el proceso
 *   muere en la ventana entre `launch` y el insert; el mismo caso de pérdida
 *   "legítima" que documenta [DurableTelemetryQueue]).
 * - `error` (tier "nunca tirar errores", spec §7.1): se encola de forma
 *   **síncrona** ([runBlocking]) — un error es raro y crítico, vale la pena
 *   pagar el costo de bloquear el caller hasta que el insert disco-primero
 *   confirme, para no perderlo ni siquiera en esa ventana.
 *
 * La construcción del [TelemetryEvent] (validación anti-PII de
 * `name`/`screen`) ocurre SIEMPRE en el hilo del caller, antes de
 * encolar/lanzar — un `name`/`screen` inválido lanza `IllegalArgumentException`
 * de inmediato al caller (bug de integración, fallar rápido), NO se traga acá.
 * Es la capa de captura (`Modifier.trackClick`/`ScreenScope`) la que decide
 * absorber ese fallo para cumplir el mandato "nunca crashear la UI" — este
 * adapter es una capa más baja y deliberadamente no lo hace (mismo criterio
 * que usa `RecordingTelemetry` en tests).
 *
 * @param enqueueScope Dónde corren los `launch` de encolado asíncrono.
 *   Inyectable (tests pasan un `TestScope` para controlar el scheduler
 *   virtual); en producción, un scope propio de vida larga (no depende de
 *   ningún ciclo de vida de Android, mismo criterio "sin kill-switch" que
 *   [DurableTelemetryQueue] — no sostiene ningún cliente de red).
 */
class DurableTelemetry(
    private val queue: DurableTelemetryQueue,
    private val sink: TelemetrySink,
    private val clock: AppClock = AppClock.System,
    private val enqueueScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Telemetry {

    override fun screenView(screen: String) {
        enqueueAsync(TelemetryEventType.SCREEN_VIEW, name = screen)
    }

    override fun tap(screen: String, element: String) {
        enqueueAsync(TelemetryEventType.TAP, name = element, props = mapOf("screen" to screen))
    }

    override fun event(name: String, props: Map<String, String>) {
        enqueueAsync(TelemetryEventType.EVENT, name = name, props = props)
    }

    override fun error(code: String, message: String, props: Map<String, String>) {
        val event = buildEvent(
            type = TelemetryEventType.ERROR,
            name = code,
            props = props + ("message" to message)
        )
        runBlocking(Dispatchers.IO) { queue.enqueue(event) }
    }

    /**
     * Drena la cola hacia [sink]. `:app` (Plan 5/T8) cablea el disparo real
     * (worker/scope periódico) — acá solo se expone la función invocable, tal
     * como pide el brief de Task 4.
     */
    suspend fun drain(batchSize: Int = DurableTelemetryQueue.DEFAULT_BATCH_SIZE): Int =
        queue.drain(sink, batchSize)

    private fun enqueueAsync(
        type: TelemetryEventType,
        name: String,
        props: Map<String, String> = emptyMap()
    ) {
        val event = buildEvent(type, name, props)
        enqueueScope.launch { queue.enqueue(event) }
    }

    private fun buildEvent(
        type: TelemetryEventType,
        name: String,
        props: Map<String, String>
    ): TelemetryEvent = TelemetryEvent(
        type = type,
        name = name,
        occurredAt = clock.now(),
        props = props
    )
}
