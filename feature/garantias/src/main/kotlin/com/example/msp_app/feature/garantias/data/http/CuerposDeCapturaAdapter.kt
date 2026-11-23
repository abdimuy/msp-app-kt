package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.garantias.domain.model.AperturaDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloNuevo
import com.example.msp_app.feature.garantias.domain.model.CuerpoDeComando
import com.example.msp_app.feature.garantias.domain.model.DomicilioDeGarantia
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.CuerposDeComandoPort
import com.google.gson.JsonObject
import java.time.Instant
import javax.inject.Inject

/**
 * Arma los cuatro cuerpos de la captura con los nombres de campo del API.
 *
 * ## Una sola vez
 *
 * El caso de uso llama a este adaptador **al encolar** y guarda el texto que
 * recibe. La cola no vuelve a pasar por acá: regenerar el cuerpo en un reintento
 * cambiaría la hora (`device_created_at`) o el orden de las llaves y el servidor
 * respondería 409 `warranty_idempotency_key_mismatch` para siempre (D3, mutante
 * A2).
 *
 * ## Lo que no se manda
 *
 * Un folio de [com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio.PISO]
 * no lleva una sola llave de cliente ni de domicilio: el servidor responde 422
 * (`warranty_customer_not_allowed`) si las recibe. El adaptador escribe cada
 * campo opcional **sólo** cuando tiene valor.
 *
 * Las coordenadas del cobrador no se mueven: la apertura no las manda nunca
 * (D5) y el avance sólo las manda si la captura las tiene.
 */
internal class CuerposDeCapturaAdapter @Inject constructor() : CuerposDeComandoPort {

    override fun apertura(apertura: AperturaDeGarantia, deviceCreatedAt: Instant): CuerpoDeComando {
        val cuerpo = JsonObject().apply {
            addProperty("device_created_at", AppTime.toWireFormat(deviceCreatedAt))
            addProperty("origen", apertura.origen.wireValue)
            addProperty("description", apertura.descripcion)
            apertura.clienteId?.let { addProperty("cliente_id", it) }
            apertura.ventaId?.let { addProperty("venta_id", it) }
            apertura.estadoCuenta?.let { addProperty("estado_cuenta", it.wireValue) }
            apertura.vigenciaHasta?.let {
                addProperty("vigencia_hasta", AppTime.toWireFormat(AppTime.startOfDay(it)))
            }
            apertura.domicilio?.let { escribirDomicilio(it) }
        }
        return CuerpoDeComando(cuerpo.toString(), CuerpoDeGarantia.TIPO_JSON)
    }

    override fun articulo(articulo: ArticuloNuevo, deviceCreatedAt: Instant): CuerpoDeComando {
        val cuerpo = JsonObject().apply {
            addProperty("device_created_at", AppTime.toWireFormat(deviceCreatedAt))
            addProperty("clave", articulo.clave)
            addProperty("description", articulo.descripcion)
            articulo.articuloId?.let { addProperty("articulo_id", it) }
        }
        return CuerpoDeComando(cuerpo.toString(), CuerpoDeGarantia.TIPO_JSON)
    }

    override fun avance(
        etapaDestino: String,
        deviceCreatedAt: Instant,
        ubicacion: Ubicacion?
    ): CuerpoDeComando {
        val cuerpo = JsonObject().apply {
            addProperty("device_created_at", AppTime.toWireFormat(deviceCreatedAt))
            addProperty("etapa_destino", etapaDestino)
            if (ubicacion != null) {
                addProperty("gps_lat", ubicacion.latitud)
                addProperty("gps_lon", ubicacion.longitud)
            }
        }
        return CuerpoDeComando(cuerpo.toString(), CuerpoDeGarantia.TIPO_JSON)
    }

    override fun entrega(
        deviceCreatedAt: Instant,
        ubicacion: Ubicacion,
        firma: FirmaDeConformidad
    ): CuerpoDeComando {
        val datos = CuerpoDeGarantia.datosDeEntrega(
            deviceCreatedAt = AppTime.toWireFormat(deviceCreatedAt),
            ubicacion = ubicacion,
            rutaDeLaFirma = firma.rutaArchivo
        )
        return CuerpoDeComando(datos.aJson(), CuerpoDeGarantia.TIPO_MULTIPART)
    }

    private fun JsonObject.escribirDomicilio(domicilio: DomicilioDeGarantia) {
        if (domicilio.calle.isNotBlank()) addProperty("calle", domicilio.calle)
        if (domicilio.numeroExterior.isNotBlank()) {
            addProperty("numero_exterior", domicilio.numeroExterior)
        }
        if (domicilio.colonia.isNotBlank()) addProperty("colonia", domicilio.colonia)
        if (domicilio.localidad.isNotBlank()) addProperty("localidad", domicilio.localidad)
        if (domicilio.ciudad.isNotBlank()) addProperty("ciudad", domicilio.ciudad)
        if (domicilio.codigoPostal.isNotBlank()) {
            addProperty("codigo_postal", domicilio.codigoPostal)
        }
    }
}
