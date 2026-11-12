package com.example.msp_app.feature.garantias.domain.model

import java.time.Instant

/**
 * De dónde salió el folio. Los literales son los que acepta
 * `POST /v2/garantias` (`origen` del cuerpo) y los que guarda la columna
 * `ORIGEN` de `garantia_folios_seguidos`.
 *
 * [PISO] no lleva cliente ni venta; [CLIENTE] los exige y copia el domicilio
 * del cliente que el teléfono ya tiene. El servidor vuelve a validarlo: mandar
 * datos de cliente en un folio de piso es 422
 * (`warranty_customer_not_allowed`).
 */
enum class OrigenDeFolio(val valor: String) {
    PISO("piso"),
    CLIENTE("cliente")
}

/**
 * El estado **local** del folio mientras el teléfono lo sigue. No es el estado
 * del servidor: ese vive en [DetalleDeGarantia] y lo decide la máquina de
 * estados de `msp-api`.
 *
 * Los literales coinciden con la columna `ESTADO_LOCAL`:
 *
 * - [PENDIENTE_ENVIO]: el folio se abrió sin señal y `abrir_folio` todavía no
 *   respondió. La bandeja lo pinta como "Pendiente de envío".
 * - [ABIERTO]: el servidor ya devolvió el `id`; el detalle se lee de la red.
 * - [RECHAZADO]: `abrir_folio` recibió un rechazo definitivo (403/422) y sus
 *   comandos dependientes quedan detenidos (D4).
 */
enum class EstadoLocalDeFolio(val valor: String) {
    PENDIENTE_ENVIO("pendiente_envio"),
    ABIERTO("abierto"),
    RECHAZADO("rechazado")
}

/**
 * Un folio que **este teléfono** abrió o tocó: la materia prima de "mis
 * folios" (D5, captura 3).
 *
 * El API no tiene filtro por usuario todavía, así que la bandeja no se puede
 * pedir al servidor. Se guarda esta lista local y cada folio ya enviado se lee
 * con `GET /v2/garantias/{id}`; los que aún no salen se muestran como
 * "Pendiente de envío". Esa limitación se declara en el reporte de la tarea.
 *
 * [id] es un UUID local y **nunca se manda como `id`**. El `id` del folio lo
 * genera el servidor y llega en [folioId]. [id] sólo sirve para que los
 * comandos encolados antes de tener señal sepan a qué folio pertenecen (D4).
 */
data class FolioSeguido(
    val id: String,
    val folioId: String? = null,
    val origen: OrigenDeFolio,
    val clienteId: Int? = null,
    val ventaId: Int? = null,
    val estadoLocal: EstadoLocalDeFolio,
    val abiertoEn: Instant,
    val actualizadoEn: Instant,
    val ultimoError: String? = null
)
