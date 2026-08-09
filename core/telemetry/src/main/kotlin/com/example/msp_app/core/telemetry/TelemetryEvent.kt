package com.example.msp_app.core.telemetry

import java.time.Instant
import java.util.Collections

/**
 * Discriminante del evento encolado. Los 4 tipos base del spec §7 — no se
 * agregan `PERFORMANCE`/`LIFECYCLE`/`FUNNEL` (roadmap §7.3) hasta que un
 * consumidor real los pida (YAGNI).
 */
enum class TelemetryEventType {
    SCREEN_VIEW,
    TAP,
    EVENT,
    ERROR
}

/**
 * VO inmutable de un evento de telemetría ya encolado — lo que la cola
 * durable (Room, tarea posterior) persiste y lo que graba
 * `RecordingTelemetry` en tests.
 *
 * ## Mapeo desde el puerto [Telemetry]
 *
 * El adapter que implementa [Telemetry] construye un [TelemetryEvent] por
 * cada llamada. Este VO **no** modela `screen`/`element`/`code` como campos
 * propios (YAGNI — decisión de esta tarea, documentada aquí en vez de en el
 * brief): el identificador estático de cada llamada se normaliza en [name]
 * (el único campo validado por el invariante anti-PII), y el contexto no
 * validado va en [props]:
 * - `screenView(screen)` → `type=SCREEN_VIEW`, `name=screen`.
 * - `tap(screen, element)` → `type=TAP`, `name=element`,
 *   `props["screen"]=screen`. El `screen` de un `tap` recibe el MISMO
 *   invariante que el `name` de un `screenView` — una pantalla es una
 *   pantalla sin importar qué llamada la registró, así que un evento `TAP`
 *   con `screen` ausente o con formato inválido se rechaza en construcción,
 *   igual que un `screenView` con `name` inválido.
 * - `event(name, props)` → `type=EVENT`, `name`, `props` tal cual.
 * - `error(code, message, props)` → `type=ERROR`, `name=code`,
 *   `props = props + ("message" to message)` (el mensaje técnico no se
 *   valida contra el alfabeto estático porque es prosa de diagnóstico, no un
 *   identificador — sigue prohibido que sea texto de usuario, por contrato
 *   del puerto).
 *
 * `appVersion`/`sessionId` (contexto que rellena el adapter, no el
 * llamador) también viven en [props] por la misma razón de YAGNI: no hay
 * consumidor real todavía que necesite campos tipados dedicados.
 *
 * ## Inmutabilidad de [props]
 *
 * La construcción (vía el `operator fun invoke` del companion, la única vía
 * pública — el constructor primario es privado) toma una copia defensiva de
 * [props] y la envuelve con [Collections.unmodifiableMap]: una mutación
 * posterior del `Map` que pasó el llamador NO se refleja en el evento ya
 * construido, y un intento de mutar el `Map` expuesto (incluso vía cast a
 * `MutableMap`) lanza `UnsupportedOperationException`.
 *
 * `@ConsistentCopyVisibility` hace que el `copy()` autogenerado herede la
 * visibilidad `private` del constructor primario (comportamiento por
 * defecto desde Kotlin 2.1, adoptado aquí ya en 2.0.21): sin esto, `copy()`
 * sería público pero invocaría el constructor primario directamente, sin
 * pasar por `invoke()` — un `event.copy(props = mutableMapOf(...))` externo
 * se saltaría la copia defensiva. Con la anotación, `copy()` no es
 * alcanzable fuera de este archivo.
 *
 * @property occurredAt viene SIEMPRE de [com.example.msp_app.core.common.time.AppClock] —
 *   nunca de `Instant.now()`/`System.currentTimeMillis()` directo, para que
 *   los tests sean deterministas con `FakeClock`.
 */
@ConsistentCopyVisibility
data class TelemetryEvent private constructor(
    val type: TelemetryEventType,
    val name: String,
    val occurredAt: Instant,
    val props: Map<String, String>
) {

    companion object {
        /** Alfabeto estático anti-PII (spec §7): minúsculas, dígitos, `_`, `.` para namespacing. */
        private val NAME_PATTERN = Regex("^[a-z0-9_.]+$")

        private fun isStaticIdentifier(value: String): Boolean =
            value.isNotBlank() && NAME_PATTERN.matches(value)

        private fun requireStaticIdentifier(value: String, fieldLabel: String) {
            require(isStaticIdentifier(value)) {
                "el $fieldLabel '$value' no es un identificador estático válido " +
                    "(alfabeto permitido: a-z 0-9 _ .); prohibido texto de usuario/PII"
            }
        }

        operator fun invoke(
            type: TelemetryEventType,
            name: String,
            occurredAt: Instant,
            props: Map<String, String> = emptyMap()
        ): TelemetryEvent {
            requireStaticIdentifier(name, "name")
            if (type == TelemetryEventType.TAP) {
                val screen = props["screen"]
                require(screen != null) { "un evento TAP debe traer 'screen' en props" }
                requireStaticIdentifier(screen, "screen")
            }
            return TelemetryEvent(
                type,
                name,
                occurredAt,
                Collections.unmodifiableMap(props.toMap())
            )
        }
    }
}
