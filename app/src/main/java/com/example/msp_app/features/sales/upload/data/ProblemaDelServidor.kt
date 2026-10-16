package com.example.msp_app.features.sales.upload.data

import com.google.gson.JsonParser

/** Fragmento de un cuerpo de error RFC 7807 del servidor. */
data class ProblemaDelServidor(val code: String?, val detail: String?) {

    companion object {
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
                val linea = obj["errors"]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                    ?.firstNotNullOfOrNull { e ->
                        val etiqueta = ETIQUETAS[e["location"]?.takeUnless { it.isJsonNull }?.asString]
                        val valor = e["value"]?.takeIf { it.isJsonPrimitive }?.asString
                        if (etiqueta != null && !valor.isNullOrBlank()) "$etiqueta: $valor" else null
                    }
                ProblemaDelServidor(
                    code = obj["code"]?.takeUnless { it.isJsonNull }?.asString,
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
