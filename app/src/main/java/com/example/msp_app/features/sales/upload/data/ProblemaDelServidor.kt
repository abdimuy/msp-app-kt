package com.example.msp_app.features.sales.upload.data

import com.google.gson.JsonParser

/** Fragmento de un cuerpo de error RFC 7807 del servidor. */
data class ProblemaDelServidor(val code: String?, val detail: String?) {

    companion object {
        private const val PREFIJO_CODIGO = "code="
        private val ETIQUETAS = mapOf("linea.articulo" to "Artículo", "linea.combo" to "Combo")

        /**
         * Lectura de mejor esfuerzo; nunca lanza (el diagnóstico no puede tapar el
         * error HTTP original). E-APP-068: si el servidor nombra la línea que
         * rechazó (`errors[{location:"linea.articulo"|"linea.combo", value}]`), el
         * nombre se agrega al mensaje que ve el vendedor.
         */
        fun leer(body: String?): ProblemaDelServidor {
            if (body.isNullOrBlank()) return ProblemaDelServidor(null, null)
            return try {
                val obj = JsonParser.parseString(body).asJsonObject
                val mensaje = obj["detail"]?.takeUnless { it.isJsonNull }?.asString
                    ?: obj["message"]?.takeUnless { it.isJsonNull }?.asString
                val errores = obj["errors"]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                    .orEmpty()
                // E-APP-069: Huma manda el código en `errors[].message` como `code=<código>`
                // (msp-api venthttp/auth.go:mapAppError); `obj.code` queda para formas viejas.
                // Sólo se guarda y se muestra: ninguna decisión de envío lo lee.
                val codigo = errores.firstNotNullOfOrNull { e ->
                    e["message"]?.takeIf { it.isJsonPrimitive }?.asString
                        ?.takeIf { it.startsWith(PREFIJO_CODIGO) }
                        ?.removePrefix(PREFIJO_CODIGO)
                        ?.takeIf { it.isNotBlank() }
                } ?: obj["code"]?.takeUnless { it.isJsonNull }?.asString
                val linea = errores
                    .firstNotNullOfOrNull { e ->
                        val etiqueta = ETIQUETAS[e["location"]?.takeUnless { it.isJsonNull }?.asString]
                        val valor = e["value"]?.takeIf { it.isJsonPrimitive }?.asString
                        if (etiqueta != null && !valor.isNullOrBlank()) "$etiqueta: $valor" else null
                    }
                ProblemaDelServidor(
                    code = codigo,
                    detail = when {
                        linea == null -> mensaje
                        mensaje == null -> linea
                        else -> "$mensaje\n$linea"
                    }
                )
            } catch (_: Exception) {
                ProblemaDelServidor(null, null)
            }
        }
    }
}
