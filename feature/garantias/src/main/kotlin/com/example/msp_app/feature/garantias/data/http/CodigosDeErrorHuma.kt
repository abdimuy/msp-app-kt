package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Lee la forma de error de Huma que devuelve `msp-api` en las rutas de
 * garantías.
 *
 * **La trampa:** Huma no publica el código de negocio en un campo `code` del
 * JSON. Lo entierra en el texto de los detalles, con la forma `code=<código>`:
 *
 * ```json
 * {"title":"...","status":409,"detail":"la clave ya se usó con otro contenido",
 *  "errors":[{"message":"code=warranty_idempotency_key_mismatch"}]}
 * ```
 *
 * Leer `obj["code"]` devuelve `null` siempre. Por eso el clasificador mira el
 * status **y** el código, y este parser es la única pieza que conoce el
 * formato.
 *
 * El molde es `extraerCodigoDeError` de `VentaEdicionRemotaDtos.kt` en `:app`,
 * con dos diferencias: acá también se extrae el `detail` (el texto en español
 * que se le muestra al cobrador) y todo fallo de lectura o de parseo se
 * traduce a `null`, nunca a una excepción que tumbe el trabajador.
 */
object CodigosDeErrorHuma {

    private val PATRON_CODIGO = Regex("""code=([A-Za-z0-9_.-]+)""")

    /**
     * El código de negocio del cuerpo, o `null` si no trae ninguno.
     *
     * Busca primero en cada `errors[].message` y, si ahí no aparece, en
     * `detail`: algunos errores de Huma sólo llenan ese campo.
     */
    fun codigo(cuerpo: String?): String? {
        val objeto = objetoDe(cuerpo) ?: return null
        return codigoEnErrores(objeto) ?: codigoEnDetalle(objeto)
    }

    /** El `detail` del cuerpo, que es el mensaje en español del servidor. */
    fun detalle(cuerpo: String?): String? {
        val objeto = objetoDe(cuerpo) ?: return null
        return objeto.get("detail")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Arma la respuesta que consume el clasificador. El cuerpo de un 2xx no
     * trae código ni detalle, y eso no es un error: el clasificador sólo mira
     * el status.
     */
    fun respuesta(status: Int, cuerpo: String?): ResultadoDeLaLlamada.Respuesta =
        ResultadoDeLaLlamada.Respuesta(
            status = status,
            codigo = codigo(cuerpo),
            detalle = detalle(cuerpo),
            cuerpo = cuerpo
        )

    private fun objetoDe(cuerpo: String?): JsonObject? {
        if (cuerpo.isNullOrBlank()) return null
        return runCatching { JsonParser.parseString(cuerpo) }
            .getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
    }

    private fun codigoEnErrores(objeto: JsonObject): String? = objeto.get("errors")
        ?.takeIf { it.isJsonArray }
        ?.asJsonArray
        ?.asSequence()
        ?.mapNotNull { it.comoObjeto() }
        ?.mapNotNull { it.get("message")?.comoTexto() }
        ?.mapNotNull { PATRON_CODIGO.find(it)?.groupValues?.get(1) }
        ?.firstOrNull()

    private fun codigoEnDetalle(objeto: JsonObject): String? = objeto.get("detail")
        ?.comoTexto()
        ?.let { PATRON_CODIGO.find(it)?.groupValues?.get(1) }

    private fun JsonElement.comoObjeto(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonElement.comoTexto(): String? = takeIf { it.isJsonPrimitive }?.asString
}
