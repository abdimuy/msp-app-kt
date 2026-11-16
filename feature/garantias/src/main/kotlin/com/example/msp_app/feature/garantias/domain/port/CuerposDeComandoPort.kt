package com.example.msp_app.feature.garantias.domain.port

import com.example.msp_app.feature.garantias.domain.model.AperturaDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloNuevo
import com.example.msp_app.feature.garantias.domain.model.CuerpoDeComando
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import java.time.Instant

/**
 * Serializa los cuerpos de los cuatro comandos de la captura.
 *
 * ## Por qué es un puerto
 *
 * La cola guarda texto, pero `application` no puede armar JSON a mano ni
 * importar `data.*`: si lo hiciera, el formato del contrato se repartiría por
 * los casos de uso y un cambio de campo obligaría a tocar cuatro pantallas.
 * Acá el formato vive en un solo adaptador (`data/http`), que conoce los
 * nombres del API y el boundary fijo del multipart.
 *
 * **Una llamada, una serialización.** El caso de uso llama una vez, guarda el
 * texto que recibe y la cola no lo vuelve a pedir: regenerarlo en un reintento
 * cambiaría la hora o el orden y rompería la idempotencia (D3, mutante A2).
 *
 * [apertura] no recibe ubicación a propósito: el GPS del cobrador nunca se
 * manda como domicilio del cliente (D5).
 */
interface CuerposDeComandoPort {

    /** El cuerpo de `POST /v2/garantias`. */
    fun apertura(apertura: AperturaDeGarantia, deviceCreatedAt: Instant): CuerpoDeComando

    /** El cuerpo de `POST /v2/garantias/{id}/articulos`. */
    fun articulo(articulo: ArticuloNuevo, deviceCreatedAt: Instant): CuerpoDeComando

    /** El cuerpo de `POST /v2/garantias/{id}/articulos/{aid}/avanzar`. */
    fun avance(
        etapaDestino: String,
        deviceCreatedAt: Instant,
        ubicacion: Ubicacion? = null
    ): CuerpoDeComando

    /**
     * Los campos de texto de la entrega, con la ruta local de la firma.
     *
     * El `Content-Type` es el multipart de boundary fijo; los bytes del PNG se
     * leen al enviar y no se guardan en la cola.
     */
    fun entrega(
        deviceCreatedAt: Instant,
        ubicacion: Ubicacion,
        firma: FirmaDeConformidad
    ): CuerpoDeComando
}
