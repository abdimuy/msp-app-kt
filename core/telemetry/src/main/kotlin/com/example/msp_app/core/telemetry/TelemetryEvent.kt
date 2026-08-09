package com.example.msp_app.core.telemetry

import java.time.Instant

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
 *   `props["screen"]=screen` (el `element` es el identificador validado; el
 *   `screen` de contexto no está en la lista de invariantes exigidos por el
 *   brief — igual viaja como prop estático, nunca como texto de usuario).
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
 * @property occurredAt viene SIEMPRE de [com.example.msp_app.core.common.time.AppClock] —
 *   nunca de `Instant.now()`/`System.currentTimeMillis()` directo, para que
 *   los tests sean deterministas con `FakeClock`.
 */
data class TelemetryEvent(
    val type: TelemetryEventType,
    val name: String,
    val occurredAt: Instant,
    val props: Map<String, String> = emptyMap()
) {

    init {
        require(name.isNotBlank()) {
            "el name de un TelemetryEvent no puede estar vacío"
        }
        require(NAME_PATTERN.matches(name)) {
            "el name '$name' no es un identificador estático válido " +
                "(alfabeto permitido: a-z 0-9 _ .); prohibido texto de usuario/PII"
        }
    }

    companion object {
        /** Alfabeto estático anti-PII (spec §7): minúsculas, dígitos, `_`, `.` para namespacing. */
        private val NAME_PATTERN = Regex("^[a-z0-9_.]+$")
    }
}
