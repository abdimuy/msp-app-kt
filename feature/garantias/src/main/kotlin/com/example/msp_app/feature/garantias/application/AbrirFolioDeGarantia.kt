package com.example.msp_app.feature.garantias.application

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.model.AperturaDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.CuerposDeComandoPort
import com.example.msp_app.feature.garantias.domain.port.EncoladoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import javax.inject.Inject

/**
 * Abre un folio de garantía: `POST /v2/garantias` y, en la misma cola, el primer
 * artículo (`POST /v2/garantias/{id}/articulos`).
 *
 * ## Dos comandos, un folio
 *
 * El `id` del folio lo genera el servidor, así que el artículo se encola con la
 * **referencia local** del folio y la cola lo resuelve cuando `abrir` responde
 * (D4). Por eso el folio se guarda en `garantia_folios_seguidos` **antes** de
 * encolar el artículo: la bandeja ya lo muestra como "Pendiente de envío" y el
 * trabajador tiene a qué folio local apuntar.
 *
 * ## Lo que no se manda
 *
 * En un folio de piso no viaja una sola llave de cliente ni de domicilio; en uno
 * de cliente, el domicilio se copia del cliente que el teléfono ya tiene y
 * **nunca** se usa el GPS del cobrador como si fuera la casa (D5). Las
 * validaciones de acá son las que el servidor aplicaría igual
 * (`warranty_description_required`, `warranty_article_description_required`),
 * pero aplicadas antes de encolar para que el cobrador las corrija en pantalla.
 */
class AbrirFolioDeGarantia @Inject constructor(
    private val folios: FoliosSeguidosPort,
    private val cola: ColaDeComandosPort,
    private val cuerpos: CuerposDeComandoPort,
    private val claves: GeneradorDeClavesPort,
    private val encolado: EncoladoDeGarantiasPort,
    @GarantiasReloj private val reloj: AppClock
) {

    suspend fun ejecutar(apertura: AperturaDeGarantia): ResultadoDeCaptura {
        val fallo = primerFallo(apertura)
        if (fallo != null) return ResultadoDeCaptura.Bloqueado(fallo)

        val ahora = reloj.now()
        val folioRef = claves.nueva()
        val articuloRef = claves.nueva()
        val cuerpoDelFolio = cuerpos.apertura(apertura, ahora)
        val cuerpoDelArticulo = cuerpos.articulo(apertura.primerArticulo, ahora)

        cola.encolar(
            ComandoNuevo(
                clave = claves.nueva(),
                operacion = OperacionDeGarantia.ABRIR_FOLIO,
                metodo = METODO_POST,
                ruta = RutasDeGarantia.ABRIR,
                cuerpo = cuerpoDelFolio.texto,
                contentType = cuerpoDelFolio.tipoDeContenido,
                deviceCreatedAt = ahora,
                folioRef = folioRef
            )
        )

        folios.guardar(
            FolioSeguido(
                id = folioRef,
                origen = apertura.origen,
                clienteId = apertura.clienteId,
                ventaId = apertura.ventaId,
                estadoLocal = EstadoLocalDeFolio.PENDIENTE_ENVIO,
                abiertoEn = ahora,
                actualizadoEn = ahora
            )
        )

        cola.encolar(
            ComandoNuevo(
                clave = claves.nueva(),
                operacion = OperacionDeGarantia.AGREGAR_ARTICULO,
                metodo = METODO_POST,
                ruta = RutasDeGarantia.AGREGAR_ARTICULO,
                cuerpo = cuerpoDelArticulo.texto,
                contentType = cuerpoDelArticulo.tipoDeContenido,
                deviceCreatedAt = ahora,
                folioRef = folioRef,
                articuloRef = articuloRef
            )
        )

        encolado.encolar()
        return ResultadoDeCaptura.Encolado(folioRef)
    }

    private fun primerFallo(apertura: AperturaDeGarantia): FalloDeCaptura? = when {
        apertura.origen == OrigenDeFolio.CLIENTE && !apertura.datosDeClienteCompletos ->
            FalloDeCaptura.SIN_DATOS_DEL_CLIENTE
        apertura.descripcion.isBlank() -> FalloDeCaptura.SIN_DESCRIPCION
        apertura.primerArticulo.clave.isBlank() -> FalloDeCaptura.SIN_CLAVE_DEL_ARTICULO
        apertura.primerArticulo.descripcion.isBlank() -> FalloDeCaptura.SIN_DESCRIPCION_DEL_ARTICULO
        else -> null
    }

    private companion object {
        const val METODO_POST = "POST"
    }
}
