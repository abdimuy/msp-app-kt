package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.garantias.domain.model.ArticuloDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.DomicilioDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ImagenDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.google.gson.annotations.SerializedName
import java.time.Instant

/**
 * La forma de `GET /v2/garantias/{id}` y su traducción al dominio.
 *
 * Los DTO son tolerantes a propósito: todos los campos son anulables y la
 * traducción pone el valor neutro cuando falta uno. Una respuesta incompleta no
 * debe tumbar la pantalla de detalle; la cola, en cambio, trata un detalle sin
 * el evento esperado como "todavía no", no como un id inventado (D4).
 *
 * Los nombres de las propiedades son camelCase y el nombre de cable va en
 * [SerializedName]: así el linter del repo no pelea con los `snake_case` del
 * contrato y la traducción se lee como una tabla.
 */
data class DetalleDeGarantiaDto(
    @SerializedName("garantia") val garantia: GarantiaDto? = null,
    @SerializedName("eventos") val eventos: List<EventoDto>? = null
)

data class GarantiaDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("folio") val folio: String? = null,
    @SerializedName("origen") val origen: String? = null,
    @SerializedName("cliente_id") val clienteId: Int? = null,
    @SerializedName("venta_id") val ventaId: Int? = null,
    @SerializedName("estado_cuenta") val estadoCuenta: String? = null,
    @SerializedName("estado") val estado: String? = null,
    @SerializedName("description") val descripcion: String? = null,
    @SerializedName("vigencia_hasta") val vigenciaHasta: String? = null,
    @SerializedName("calle") val calle: String? = null,
    @SerializedName("numero_exterior") val numeroExterior: String? = null,
    @SerializedName("colonia") val colonia: String? = null,
    @SerializedName("localidad") val localidad: String? = null,
    @SerializedName("ciudad") val ciudad: String? = null,
    @SerializedName("codigo_postal") val codigoPostal: String? = null,
    @SerializedName("gps_lat") val gpsLat: Double? = null,
    @SerializedName("gps_lon") val gpsLon: Double? = null,
    @SerializedName("abierto_por") val abiertoPor: String? = null,
    @SerializedName("cerrado_en") val cerradoEn: String? = null,
    @SerializedName("created_at") val creadoEn: String? = null,
    @SerializedName("updated_at") val actualizadoEn: String? = null,
    @SerializedName("articulos") val articulos: List<ArticuloDto>? = null
)

data class ArticuloDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("garantia_id") val garantiaId: String? = null,
    @SerializedName("rol") val rol: String? = null,
    @SerializedName("reemplaza_a") val reemplazaA: String? = null,
    @SerializedName("articulo_id") val articuloId: Int? = null,
    @SerializedName("clave") val clave: String? = null,
    @SerializedName("description") val descripcion: String? = null,
    @SerializedName("ruta") val ruta: String? = null,
    @SerializedName("etapa") val etapa: String? = null,
    @SerializedName("ubicacion") val ubicacion: String? = null,
    @SerializedName("dictamen") val dictamen: String? = null,
    @SerializedName("desenlace") val desenlace: String? = null,
    @SerializedName("cerrado_en") val cerradoEn: String? = null,
    @SerializedName("created_at") val creadoEn: String? = null,
    @SerializedName("updated_at") val actualizadoEn: String? = null
)

data class EventoDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("garantia_id") val garantiaId: String? = null,
    @SerializedName("articulo_ref") val articuloRef: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("description") val descripcion: String? = null,
    @SerializedName("etapa_desde") val etapaDesde: String? = null,
    @SerializedName("etapa_hasta") val etapaHasta: String? = null,
    @SerializedName("usuario") val usuario: String? = null,
    @SerializedName("rol_decisor") val rolDecisor: String? = null,
    @SerializedName("gps_lat") val gpsLat: Double? = null,
    @SerializedName("gps_lon") val gpsLon: Double? = null,
    @SerializedName("created_at") val creadoEn: String? = null,
    @SerializedName("device_created_at") val deviceCreatedAt: String? = null,
    @SerializedName("clave_idempotencia") val claveIdempotencia: String? = null,
    @SerializedName("distancia_entrega_m") val distanciaEntregaM: Double? = null,
    @SerializedName("auditoria_gps") val auditoriaGps: String? = null,
    @SerializedName("imagenes") val imagenes: List<ImagenDto>? = null
)

data class ImagenDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("descripcion") val descripcion: String? = null,
    @SerializedName("subida_por") val subidaPor: String? = null,
    @SerializedName("created_at") val creadaEn: String? = null,
    @SerializedName("url") val url: String? = null
)

/** Traduce la respuesta del detalle al modelo de dominio de sólo lectura. */
fun DetalleDeGarantiaDto.toDomain(): DetalleDeGarantia {
    val folio = requireNotNull(garantia) { "la respuesta del detalle no trae el folio" }
    return DetalleDeGarantia(
        id = folio.id.orEmpty(),
        folio = folio.folio.orEmpty(),
        origen = origenDe(folio.origen),
        clienteId = folio.clienteId,
        ventaId = folio.ventaId,
        estadoCuenta = folio.estadoCuenta,
        estado = folio.estado.orEmpty(),
        descripcion = folio.descripcion.orEmpty(),
        vigenciaHasta = AppTime.parseWireDateOrNull(folio.vigenciaHasta),
        domicilio = folio.aDomicilio(),
        ubicacionDelDomicilio = Ubicacion.de(folio.gpsLat, folio.gpsLon),
        abiertoPor = folio.abiertoPor.orEmpty(),
        cerradoEn = AppTime.parseWireFormatOrNull(folio.cerradoEn),
        creadoEn = folio.creadoEn.aInstant(),
        actualizadoEn = folio.actualizadoEn.aInstant(),
        articulos = folio.articulos.orEmpty().map { it.toDomain() },
        eventos = eventos.orEmpty().map { it.toDomain() }
    )
}

private fun GarantiaDto.aDomicilio(): DomicilioDeGarantia? = DomicilioDeGarantia(
    calle = calle.orEmpty(),
    numeroExterior = numeroExterior.orEmpty(),
    colonia = colonia.orEmpty(),
    localidad = localidad.orEmpty(),
    ciudad = ciudad.orEmpty(),
    codigoPostal = codigoPostal.orEmpty()
).takeUnless { it.estaVacio }

private fun ArticuloDto.toDomain(): ArticuloDeGarantia = ArticuloDeGarantia(
    id = id.orEmpty(),
    rol = rol.orEmpty(),
    reemplazaA = reemplazaA,
    articuloId = articuloId,
    clave = clave.orEmpty(),
    descripcion = descripcion.orEmpty(),
    ruta = ruta,
    etapa = etapa.orEmpty(),
    ubicacion = ubicacion.orEmpty(),
    dictamen = dictamen,
    desenlace = desenlace,
    cerradoEn = AppTime.parseWireFormatOrNull(cerradoEn),
    creadoEn = creadoEn.aInstant(),
    actualizadoEn = actualizadoEn.aInstant()
)

private fun EventoDto.toDomain(): EventoDeGarantia = EventoDeGarantia(
    id = id.orEmpty(),
    articuloRef = articuloRef,
    tipo = tipo.orEmpty(),
    descripcion = descripcion.orEmpty(),
    etapaDesde = etapaDesde,
    etapaHasta = etapaHasta,
    usuario = usuario.orEmpty(),
    rolDecisor = rolDecisor,
    ubicacion = Ubicacion.de(gpsLat, gpsLon),
    creadoEn = creadoEn.aInstant(),
    deviceCreatedAt = deviceCreatedAt.aInstant(),
    claveIdempotencia = claveIdempotencia.orEmpty(),
    distanciaEntregaM = distanciaEntregaM,
    auditoriaGps = auditoriaGps,
    imagenes = imagenes.orEmpty().map { it.toDomain() }
)

private fun ImagenDto.toDomain(): ImagenDeGarantia = ImagenDeGarantia(
    id = id.orEmpty(),
    tipo = tipo.orEmpty(),
    descripcion = descripcion.orEmpty(),
    subidaPor = subidaPor.orEmpty(),
    creadaEn = creadaEn.aInstant(),
    url = url.orEmpty()
)

/**
 * El servidor sólo publica `piso` y `cliente`. Cualquier otro literal cae en
 * [OrigenDeFolio.PISO] para que la pantalla no reviente; la cola nunca usa este
 * campo para decidir una transición, así que el valor neutro no escribe nada.
 */
private fun origenDe(valor: String?): OrigenDeFolio =
    OrigenDeFolio.entries.firstOrNull { it.valor == valor } ?: OrigenDeFolio.PISO

/** Una fecha del servidor ausente o ilegible no debe tumbar el detalle. */
private fun String?.aInstant(): Instant = AppTime.parseWireFormatOrNull(this) ?: Instant.EPOCH
