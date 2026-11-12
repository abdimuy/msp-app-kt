package com.example.msp_app.feature.garantias.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * El domicilio del folio, copiado del cliente al abrirlo.
 *
 * Sólo existe en los folios de origen [OrigenDeFolio.CLIENTE]. En uno de piso
 * el servidor responde 422 (`warranty_address_not_allowed`) si se manda, así
 * que [AperturaDeGarantia] lo deja en `null` y el adaptador no escribe una sola
 * llave de domicilio.
 *
 * **No lleva GPS.** Las coordenadas del domicilio viven aparte
 * ([DetalleDeGarantia.ubicacionDelDomicilio]) porque el GPS del cobrador nunca
 * se rotula como si fuera la casa del cliente (D5).
 */
data class DomicilioDeGarantia(
    val calle: String = "",
    val numeroExterior: String = "",
    val colonia: String = "",
    val localidad: String = "",
    val ciudad: String = "",
    val codigoPostal: String = ""
) {
    /** Sin una sola línea escrita no hay domicilio que mandar. */
    val estaVacio: Boolean
        get() = calle.isBlank() &&
            numeroExterior.isBlank() &&
            colonia.isBlank() &&
            localidad.isBlank() &&
            ciudad.isBlank() &&
            codigoPostal.isBlank()
}

/**
 * Un artículo del folio tal como lo devuelve `GET /v2/garantias/{id}`.
 *
 * [etapa] y [ubicacion] son los literales del servidor y el teléfono **no** los
 * traduce a otra máquina de estados: los usa para buscar la acción válida en el
 * catálogo (D2) y para pintar la línea de tiempo.
 *
 * [rol] distingue `original` de `reemplazo`; [reemplazaA] es el id del artículo
 * original cuando este vino a sustituirlo.
 */
data class ArticuloDeGarantia(
    val id: String,
    val rol: String,
    val reemplazaA: String? = null,
    val articuloId: Int? = null,
    val clave: String,
    val descripcion: String,
    val ruta: String? = null,
    val etapa: String,
    val ubicacion: String,
    val dictamen: String? = null,
    val desenlace: String? = null,
    val cerradoEn: Instant? = null,
    val creadoEn: Instant,
    val actualizadoEn: Instant
)

/**
 * Una imagen de la línea de tiempo.
 *
 * [url] es la ruta relativa que el servidor devuelve (`…/imagenes/{iid}`) y se
 * pide con el token del `BearerAuthInterceptor`; no se abre directo con un
 * visor de URLs. [id] es también la clave con la que se subió.
 */
data class ImagenDeGarantia(
    val id: String,
    val tipo: String,
    val descripcion: String,
    val subidaPor: String,
    val creadaEn: Instant,
    val url: String
)

/**
 * Una entrada de la línea de tiempo del folio.
 *
 * [claveIdempotencia] es la pieza que hace que el teléfono no tenga que
 * adivinar: después de mandar un comando, pide el detalle y busca el evento con
 * **esa** clave. Su [articuloRef] dice qué artículo creó o movió, y su [id] es
 * el evento al que se cuelgan las fotos. Buscar "el último" o "el primero del
 * mismo tipo" falla en cuanto hay dos recolecciones en el mismo folio (D4,
 * mutante A6).
 *
 * [ubicacion] es el GPS **del cobrador** en ese evento, no el del domicilio.
 */
data class EventoDeGarantia(
    val id: String,
    val articuloRef: String? = null,
    val tipo: String,
    val descripcion: String,
    val etapaDesde: String? = null,
    val etapaHasta: String? = null,
    val usuario: String,
    val rolDecisor: String? = null,
    val ubicacion: Ubicacion? = null,
    val creadoEn: Instant,
    val deviceCreatedAt: Instant,
    val claveIdempotencia: String,
    val distanciaEntregaM: Double? = null,
    val auditoriaGps: String? = null,
    val imagenes: List<ImagenDeGarantia> = emptyList()
)

/**
 * El folio completo con su línea de tiempo: lo que devuelve
 * `GET /v2/garantias/{id}`.
 *
 * El estado del folio ([estado]) y la etapa de cada artículo los decide el
 * servidor. Este objeto es de **sólo lectura** para el teléfono; la captura
 * escribe comandos, no campos.
 *
 * [estaCerrado] es el único atajo que se permite: un folio cerrado ya no
 * admite recolección ni entrega y la pantalla no ofrece el botón.
 */
data class DetalleDeGarantia(
    val id: String,
    val folio: String,
    val origen: OrigenDeFolio,
    val clienteId: Int? = null,
    val ventaId: Int? = null,
    val estadoCuenta: String? = null,
    val estado: String,
    val descripcion: String,
    val vigenciaHasta: LocalDate? = null,
    val domicilio: DomicilioDeGarantia? = null,
    val ubicacionDelDomicilio: Ubicacion? = null,
    val abiertoPor: String,
    val cerradoEn: Instant? = null,
    val creadoEn: Instant,
    val actualizadoEn: Instant,
    val articulos: List<ArticuloDeGarantia> = emptyList(),
    val eventos: List<EventoDeGarantia> = emptyList()
) {
    val estaCerrado: Boolean
        get() = cerradoEn != null

    /** El evento que produjo [clave], o `null` si el servidor todavía no lo tiene. */
    fun eventoPorClave(clave: String): EventoDeGarantia? =
        eventos.firstOrNull { it.claveIdempotencia == clave }

    /**
     * El artículo que creó el comando con [clave], resuelto **por la clave del
     * evento**, nunca por orden ni por fecha (D4).
     *
     * `null` significa "el servidor todavía no lo cuenta": el comando acaba de
     * responder o el detalle viene de una copia vieja. La cola lo vuelve a
     * pedir; no inventa un id.
     */
    fun articuloDeLaClave(clave: String): ArticuloDeGarantia? {
        val referencia = eventoPorClave(clave)?.articuloRef ?: return null
        return articulos.firstOrNull { it.id == referencia }
    }

    /** Un artículo por su id del servidor. */
    fun articulo(id: String): ArticuloDeGarantia? = articulos.firstOrNull { it.id == id }
}
