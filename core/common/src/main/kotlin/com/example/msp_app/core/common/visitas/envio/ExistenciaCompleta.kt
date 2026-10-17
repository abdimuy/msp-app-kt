package com.example.msp_app.core.common.visitas.envio

/**
 * Spec visitas §4.2-3: "existencia COMPLETA", no existencia a secas.
 *
 * Solo [COMPLETA] puede soltar una visita por existencia. Las otras tres
 * significan lo mismo para el worker (no se suelta por esta vía) y se
 * distinguen solo para el diagnóstico.
 */
enum class Existencia { COMPLETA, INCOMPLETA, NO_EXISTE, INDETERMINADA }

/**
 * Lo que devolvió `GET /v2/visitas/{id}`.
 *
 * `imagenIds` nulo = el servidor no lo dijo (servidor viejo que omite el
 * campo; Gson no aplica defaults de Kotlin). El servidor nuevo lo manda
 * SIEMPRE, `[]` si no hay fotos, y lista solo las fotos con fila Y archivo en
 * disco al momento del GET (E-VIS-022).
 */
data class VisitaEnServidor(val id: String?, val imagenIds: List<String>?)

/**
 * ¿El servidor tiene la visita [enviada] completa? Función pura y total.
 *
 * COMPLETA exige las tres cosas: GET 200, cuerpo con el MISMO id (no vacío),
 * y cada foto local pendiente presente en `imagen_ids`. Un 200 con otro id o
 * sin cuerpo (proxy, portal cautivo) no prueba nada: INDETERMINADA. Una foto
 * pendiente que el servidor no lista, o un servidor que no dice cuáles tiene,
 * es INCOMPLETA: la visita no se suelta (Review Focus 4 del plan P1).
 *
 * La comparación de ids es exacta, a propósito: un desajuste solo puede
 * dejar la visita reintentando, nunca soltarla sin prueba.
 */
fun existenciaCompleta(
    enviada: String,
    respuesta: VisitaEnServidor?,
    fotosPendientes: Set<String>,
    codigoGet: Int?
): Existencia = when {
    codigoGet == HTTP_NOT_FOUND -> Existencia.NO_EXISTE
    codigoGet != HTTP_OK || respuesta == null -> Existencia.INDETERMINADA
    enviada.isBlank() || respuesta.id != enviada -> Existencia.INDETERMINADA
    fotosPendientes.isEmpty() -> Existencia.COMPLETA
    respuesta.imagenIds == null -> Existencia.INCOMPLETA
    respuesta.imagenIds.containsAll(fotosPendientes) -> Existencia.COMPLETA
    else -> Existencia.INCOMPLETA
}

private const val HTTP_OK = 200
private const val HTTP_NOT_FOUND = 404
