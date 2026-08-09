package com.example.msp_app.core.telemetry

/**
 * Puerto de observabilidad de producto que cada módulo consume para emitir
 * telemetría de uso (Plan 4, spec §7: `screenView` / `tap` / `event` /
 * `error`). Implementaciones reales (cola Room + red) llegan en tareas
 * posteriores de este plan; el consumidor de features solo ve esta interfaz.
 *
 * ## Disciplina anti-PII (LFPDPPP) — OBLIGATORIA para todo llamador
 *
 * Los parámetros de este puerto son **estáticos del desarrollador**, nunca
 * datos capturados de la sesión del usuario. Está PROHIBIDO pasar:
 * - Texto libre tecleado por el usuario (notas, búsquedas, comentarios).
 * - `contentDescription` u otro string de UI derivado de datos de negocio.
 * - Nombres, teléfonos, direcciones o cualquier identificador de cliente.
 * - Identidad de cobrador/vendedor más allá de un id opaco ya anonimizado
 *   aguas arriba (este puerto no anonimiza nada — el llamador es responsable).
 * - Montos exactos u otros datos financieros sensibles.
 *
 * `screen`, `element`, `name`, `code` deben ser constantes de código (ids de
 * pantalla/evento definidos en el módulo emisor), no valores derivados de
 * datos remotos o de entrada del usuario. `props` acepta solo pares
 * clave/valor igualmente estáticos (p.ej. `"result" to "success"`), nunca el
 * contenido de un campo de negocio.
 */
interface Telemetry {
    /** Se navegó a la pantalla [screen] (id estático, p.ej. `"cobranza_detalle"`). */
    fun screenView(screen: String)

    /** El usuario tocó [element] (id estático, p.ej. `"boton_confirmar"`) dentro de [screen]. */
    fun tap(screen: String, element: String)

    /** Evento de negocio nombrado [name] (id estático), con [props] estáticos opcionales. */
    fun event(name: String, props: Map<String, String> = emptyMap())

    /** Error identificado por [code] (id estático) con [message] técnico (NO texto de usuario). */
    fun error(code: String, message: String, props: Map<String, String> = emptyMap())
}
