package com.example.msp_app.feature.garantias.domain.model

import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import java.time.LocalDate

/**
 * El estado de cuenta del cliente al abrir un folio de origen
 * [OrigenDeFolio.CLIENTE].
 *
 * Los dos literales son los que acepta `POST /v2/garantias`
 * (`estado_cuenta`). En un folio de piso no aplica: mandarlo es 422
 * (`warranty_account_state_not_allowed`).
 */
enum class EstadoDeCuenta(val valor: String) {
    LIQUIDADA("liquidada"),
    SALDO_PENDIENTE("saldo_pendiente")
}

/**
 * El artículo con el que nace el folio. Se manda en el **segundo** comando de
 * la misma cola, después de que `abrir` devuelva el `id` del servidor (D4).
 *
 * [clave] es la clave o identificador físico del artículo (máximo 30
 * caracteres en la columna del servidor); [articuloId] es el id de Microsip
 * cuando el teléfono lo conoce. La descripción es obligatoria para el servidor
 * (`warranty_article_description_required`).
 */
data class ArticuloNuevo(
    val clave: String,
    val descripcion: String,
    val articuloId: Int? = null
)

/**
 * Lo que el cobrador capturó para abrir un folio.
 *
 * Un folio de [OrigenDeFolio.PISO] no lleva cliente, venta, estado de cuenta
 * ni domicilio; uno de [OrigenDeFolio.CLIENTE] los exige. El teléfono copia el
 * domicilio del cliente que ya tiene y **nunca** manda el GPS del cobrador como
 * si fuera la casa del cliente (D5).
 *
 * El folio y el primer artículo se encolan como **dos comandos** de la misma
 * cola: el segundo espera al `id` del primero (D4).
 */
data class AperturaDeGarantia(
    val origen: OrigenDeFolio,
    val descripcion: String,
    val primerArticulo: ArticuloNuevo,
    val clienteId: Int? = null,
    val ventaId: Int? = null,
    val estadoCuenta: EstadoDeCuenta? = null,
    val domicilio: DomicilioDeGarantia? = null,
    val vigenciaHasta: LocalDate? = null
) {
    /** Un folio de cliente sin sus datos no se puede encolar. */
    val datosDeClienteCompletos: Boolean
        get() = origen == OrigenDeFolio.PISO ||
            (clienteId != null && ventaId != null && estadoCuenta != null && domicilio != null)
}

/**
 * Una foto recién tomada, todavía en `filesDir`.
 *
 * [clave] se genera **una sola vez**, al tomar la foto, y es también el `id` de
 * la imagen en el servidor. Regenerarla en un reintento crearía una segunda
 * imagen para la misma evidencia (D5).
 *
 * [mime] es el tipo real del archivo (`image/jpeg` o `image/png`); el servidor
 * lo valida contra su lista y además mira los bytes.
 */
data class FotoDeCaptura(
    val clave: String,
    val rutaArchivo: String,
    val mime: String,
    val descripcion: String = ""
)

/**
 * La recolección de un artículo: avanzarlo a `recolectado` y subir sus fotos.
 *
 * [ubicacion] es el GPS del cobrador **si lo hay**. Sin GPS la recolección se
 * guarda igual y la pantalla dice "Sin ubicación" (D5); no es un bloqueo como
 * en la entrega.
 *
 * [folioRef] y [articuloRef] son referencias locales que la cola resuelve
 * cuando el servidor devuelve los ids (D4). Las fotos esperan al evento que
 * crea este comando.
 */
data class RecoleccionDeGarantia(
    val folioRef: String,
    val articuloRef: String,
    val ubicacion: Ubicacion? = null,
    val fotos: List<FotoDeCaptura> = emptyList()
)

/**
 * La firma de conformidad ya escrita en disco, lista para viajar en el
 * multipart de la entrega.
 *
 * [tamanoBytes] es el del PNG en disco, no el del `Bitmap` en memoria. La regla
 * del servidor es 1 MB y [ReglasDeEntrega] la aplica antes de encolar.
 */
data class FirmaDeConformidad(
    val rutaArchivo: String,
    val tamanoBytes: Int,
    val mime: String = ReglasDeEntrega.MIME_DE_LA_FIRMA
)

/**
 * La entrega de un folio: GPS **obligatorio** y firma del cliente.
 *
 * [ubicacion] no es anulable a propósito. Un comando de entrega encolado sin
 * GPS daría 422 para siempre (`warranty_delivery_gps_required`) y el cobrador
 * ya no podría corregirlo desde el teléfono (D5, mutante A8). La pantalla
 * deshabilita el botón con "Esperando ubicación" hasta que haya coordenadas.
 */
data class EntregaDeGarantia(
    val folioRef: String,
    val ubicacion: Ubicacion,
    val firma: FirmaDeConformidad
)
