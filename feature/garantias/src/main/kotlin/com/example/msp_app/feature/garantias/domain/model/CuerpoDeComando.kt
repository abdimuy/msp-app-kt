package com.example.msp_app.feature.garantias.domain.model

/**
 * El cuerpo de un comando **ya serializado** y su `Content-Type`.
 *
 * Es texto y no un objeto por la misma razón que [CuerpoHttp]: lo que se guarda
 * en la cola es exactamente lo que viaja. Un cuerpo reconstruido en cada
 * intento puede cambiar de orden, de formato de hora o de boundary, y el
 * servidor responde 409 `warranty_idempotency_key_mismatch` a la misma clave
 * con cualquier campo distinto (D3).
 *
 * [tipoDeContenido] viaja con el texto porque la entrega se guarda como JSON de
 * campos y se envía como multipart: el llamador no tiene que recordar cuál de
 * los dos es.
 */
data class CuerpoDeComando(
    val texto: String,
    val tipoDeContenido: String
)
