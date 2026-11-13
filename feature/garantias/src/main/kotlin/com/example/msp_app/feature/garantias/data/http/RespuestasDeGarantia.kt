package com.example.msp_app.feature.garantias.data.http

import com.google.gson.JsonParser

/**
 * Lo que la cola necesita leer de una respuesta **exitosa** del API.
 *
 * El clasificador de errores ([CodigosDeErrorHuma]) se queda con el `detail` y
 * el código; acá sólo se extrae el `id` del folio que devuelve
 * `POST /v2/garantias`. Es la única forma de resolver la referencia local de un
 * folio abierto sin señal (D4).
 *
 * Todo fallo de lectura o de forma se traduce a `null`, nunca a una excepción:
 * una respuesta 2xx sin `id` es un contrato roto, y la cola la trata como
 * reintento en vez de tumbar al trabajador.
 */
object RespuestasDeGarantia {

    /** El `id` del folio del cuerpo, o `null` si no viene o no es un UUID. */
    fun idDeFolio(cuerpo: String?): String? = runCatching {
        JsonParser.parseString(cuerpo)
            .takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("id")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
