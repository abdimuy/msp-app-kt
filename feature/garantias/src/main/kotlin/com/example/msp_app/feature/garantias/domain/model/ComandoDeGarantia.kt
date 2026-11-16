package com.example.msp_app.feature.garantias.domain.model

import java.time.Instant

/**
 * Las operaciones que este teléfono encola para un folio. El literal es el que
 * guarda la columna `OPERACION` y no se enseña al cobrador: la pantalla habla
 * de "abrir folio", "agregar artículo", "recolectar" y "entregar".
 *
 * No incluye las diecinueve rutas del API: la captura de la tarea 11 sólo
 * produce estas cuatro. Agregar una acción nueva es sumar un literal y su
 * cuerpo, no reescribir la cola.
 */
enum class OperacionDeGarantia(val wireValue: String) {
    ABRIR_FOLIO("abrir_folio"),
    AGREGAR_ARTICULO("agregar_articulo"),
    AVANZAR_ARTICULO("avanzar_articulo"),
    ENTREGAR("entregar")
}

/** El estado de una fila de `garantia_comandos_pendientes`. */
enum class EstadoDeComando(val wireValue: String) {
    PENDIENTE("pendiente"),
    ENVIADO("enviado"),
    RECHAZADO("rechazado")
}

/** El estado de una fila de `garantia_fotos_pendientes`. */
enum class EstadoDeFoto(val wireValue: String) {
    PENDIENTE("pendiente"),
    ENVIADA("enviada"),
    RECHAZADA("rechazada")
}

/**
 * Las rutas de la captura, con la referencia del folio sin resolver.
 *
 * Un comando encolado sin señal todavía no conoce el `id` del servidor (D4), así
 * que guarda la **plantilla** con [FOLIO], [ARTICULO] o [EVENTO] en su lugar.
 * [resolver] la completa cuando el id llega, en el momento de enviar, y falla
 * ruidosamente si quedó un marcador: una ruta a medio resolver pegaría contra
 * un `{folio}` literal y el servidor respondería 422 para siempre.
 */
object RutasDeGarantia {
    const val FOLIO: String = "{folio}"
    const val ARTICULO: String = "{articulo}"
    const val EVENTO: String = "{evento}"

    const val ABRIR: String = "v2/garantias"
    const val DETALLE: String = "v2/garantias/$FOLIO"
    const val AGREGAR_ARTICULO: String = "v2/garantias/$FOLIO/articulos"
    const val AVANZAR_ARTICULO: String = "v2/garantias/$FOLIO/articulos/$ARTICULO/avanzar"
    const val ENTREGAR: String = "v2/garantias/$FOLIO/entregar"
    const val IMAGENES: String = "v2/garantias/$FOLIO/eventos/$EVENTO/imagenes"

    /**
     * Completa [plantilla] con los ids que ya conoce el servidor.
     *
     * @throws IllegalArgumentException si después de sustituir queda un
     *   marcador, o si falta un id que la plantilla necesita.
     */
    fun resolver(
        plantilla: String,
        folioId: String,
        articuloId: String? = null,
        eventoId: String? = null
    ): String {
        var ruta = plantilla.replace(FOLIO, folioId)
        if (articuloId != null) ruta = ruta.replace(ARTICULO, articuloId)
        if (eventoId != null) ruta = ruta.replace(EVENTO, eventoId)

        require(!ruta.contains('{')) {
            "la ruta $plantilla quedó sin resolver: $ruta"
        }
        return ruta
    }
}

/**
 * Un comando **nuevo** que la captura quiere encolar. Es lo que entra al puerto
 * de la cola; lo que sale es [ComandoDeGarantia], con su id local y su estado.
 *
 * [clave] se genera UNA vez, al guardar, y no se vuelve a generar. [cuerpo] y
 * [deviceCreatedAt] también se congelan en este objeto: el worker reenvía esos
 * bytes y esa hora, no los reconstruye (D3, mutantes A1 y A2).
 *
 * [folioRef] es la referencia local del folio cuando el servidor todavía no
 * tiene un id; [articuloRef] y [eventoRef] hacen lo mismo para las capturas que
 * dependen de una respuesta anterior (D4). [ruta] puede traer los marcadores de
 * [RutasDeGarantia].
 */
data class ComandoNuevo(
    val clave: String,
    val operacion: OperacionDeGarantia,
    val metodo: String,
    val ruta: String,
    val cuerpo: String?,
    val contentType: String?,
    val deviceCreatedAt: Instant,
    val folioRef: String? = null,
    val articuloRef: String? = null,
    val eventoRef: String? = null
)

/**
 * Una fila ya guardada de la cola de comandos.
 *
 * Es inmutable a propósito: la cola no edita [clave], [cuerpo],
 * [contentType], [deviceCreatedAt] ni [ruta]. Las únicas escrituras posibles son
 * de estado y de resolución de ids, y viven en el puerto de la cola. Si el
 * cobrador quiere otra cosa, es otro comando con otra clave.
 */
data class ComandoDeGarantia(
    val id: Long,
    val clave: String,
    val operacion: OperacionDeGarantia,
    val metodo: String,
    val ruta: String,
    val cuerpo: String?,
    val contentType: String?,
    val deviceCreatedAt: Instant,
    val estado: EstadoDeComando,
    val intentos: Int,
    val creadoEn: Instant,
    val actualizadoEn: Instant,
    val ultimoError: String? = null,
    val folioRef: String? = null,
    val folioId: String? = null,
    val articuloRef: String? = null,
    val articuloId: String? = null,
    val eventoRef: String? = null,
    val eventoId: String? = null
)

/**
 * Una foto que la captura quiere subir. Su [clave] es **también el id de la
 * imagen en el servidor**: se genera una sola vez, al tomar la foto, y no se
 * regenera en un reintento. Regenerarla crearía una segunda imagen para la
 * misma evidencia (D5).
 *
 * [eventoRef] es la referencia local del evento que la foto documenta. La foto
 * no puede subir hasta que ese evento exista en el servidor; la cola propaga el
 * id cuando `avanzar` responde (D4).
 */
data class FotoNueva(
    val clave: String,
    val rutaArchivo: String,
    val mime: String,
    val folioRef: String? = null,
    val eventoRef: String? = null
)

/**
 * Una fila ya guardada de `garantia_fotos_pendientes`.
 *
 * [subidaEn] sólo se llena cuando el servidor confirmó la subida. El archivo se
 * borra **después** de esa confirmación, nunca antes (mutante A7).
 */
data class FotoPendienteDeGarantia(
    val clave: String,
    val rutaArchivo: String,
    val mime: String,
    val estado: EstadoDeFoto,
    val intentos: Int,
    val creadoEn: Instant,
    val actualizadoEn: Instant,
    val comandoId: Long? = null,
    val folioRef: String? = null,
    val folioId: String? = null,
    val eventoRef: String? = null,
    val eventoId: String? = null,
    val ultimoError: String? = null,
    val subidaEn: Instant? = null
)
