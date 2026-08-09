package com.example.msp_app.core.telemetry.queue

import android.util.Log
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "TelemetryPropsCodec"

/**
 * Serializa/deserializa `TelemetryEvent.props` (siempre `Map<String, String>`
 * de identificadores estáticos anti-PII, ver KDoc de `Telemetry.kt`) a JSON
 * plano para la columna `propsJson` de [TelemetryEventEntity].
 *
 * `org.json.JSONObject` (built-in de Android, ya usado en el repo por
 * `CobranzaSseSubscriber`) evita sumar una dependencia de serialización
 * nueva (kotlinx.serialization/Gson/Moshi) solo para pares clave/valor de
 * `String` — YAGNI, mismo criterio que el resto de este módulo.
 *
 * [decode] NUNCA lanza: una fila corrupta (JSON inválido, escrito por una
 * versión futura/pasada del esquema) no debe tirar el drenado entero — se
 * trata como "sin props" y se loguea, coherente con el mandato "nunca tirar
 * errores a callers" de toda la cola (spec §7.1).
 */
internal object TelemetryPropsCodec {

    fun encode(props: Map<String, String>): String {
        val json = JSONObject()
        props.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    fun decode(propsJson: String): Map<String, String> {
        if (propsJson.isBlank()) return emptyMap()
        return try {
            val json = JSONObject(propsJson)
            json.keys().asSequence().associateWith { key -> json.getString(key) }
        } catch (e: JSONException) {
            Log.w(TAG, "propsJson corrupto, se trata como sin props: $propsJson", e)
            emptyMap()
        }
    }
}
