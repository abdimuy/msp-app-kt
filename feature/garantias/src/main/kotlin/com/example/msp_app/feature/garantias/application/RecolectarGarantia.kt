package com.example.msp_app.feature.garantias.application

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RecoleccionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.CuerposDeComandoPort
import com.example.msp_app.feature.garantias.domain.port.EncoladoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import javax.inject.Inject

/**
 * Encola la recolección de un artículo con sus fotos.
 *
 * ## Un comando y sus fotos, en una sola transacción
 *
 * El avance y las fotos se guardan juntos. Las fotos nacen **esperando** al
 * evento que crea este comando: la cola les propaga su `id` cuando el servidor
 * confirma, y recién entonces pueden subir (D4). Si la recolección no lleva
 * fotos, el comando sale igual.
 *
 * ## El GPS no bloquea
 *
 * Sin coordenadas la recolección se guarda igual y la pantalla dice "Sin
 * ubicación" (D5). Es la diferencia con la entrega, donde el GPS es obligatorio
 * (`ReglasDeEntrega`).
 *
 * ## La etapa la decide el servidor
 *
 * [RecoleccionDeGarantia.etapaDestino] llega del catálogo o del respaldo de la
 * captura, nunca se deduce acá de la etapa actual (D2).
 */
class RecolectarGarantia @Inject constructor(
    private val folios: FoliosSeguidosPort,
    private val cola: ColaDeComandosPort,
    private val cuerpos: CuerposDeComandoPort,
    private val claves: GeneradorDeClavesPort,
    private val encolado: EncoladoDeGarantiasPort,
    @GarantiasReloj private val reloj: AppClock
) {

    suspend fun ejecutar(recoleccion: RecoleccionDeGarantia): ResultadoDeCaptura {
        val folio = folios.porId(recoleccion.folioRef)
            ?: return ResultadoDeCaptura.Bloqueado(FalloDeCaptura.FOLIO_DESCONOCIDO)
        if (folio.estadoLocal == EstadoLocalDeFolio.RECHAZADO) {
            return ResultadoDeCaptura.Bloqueado(FalloDeCaptura.FOLIO_RECHAZADO)
        }
        if (recoleccion.etapaDestino.isBlank()) {
            return ResultadoDeCaptura.Bloqueado(FalloDeCaptura.SIN_ACCION)
        }

        val ahora = reloj.now()
        val eventoRef = claves.nueva()
        val cuerpo = cuerpos.avance(recoleccion.etapaDestino, ahora, recoleccion.ubicacion)
        val fotos = recoleccion.fotos.map { foto ->
            FotoNueva(
                clave = foto.clave,
                rutaArchivo = foto.rutaArchivo,
                mime = foto.mime,
                folioRef = recoleccion.folioRef,
                eventoRef = eventoRef
            )
        }

        cola.encolar(
            ComandoNuevo(
                clave = eventoRef,
                operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
                metodo = METODO_POST,
                ruta = RutasDeGarantia.AVANZAR_ARTICULO,
                cuerpo = cuerpo.texto,
                contentType = cuerpo.tipoDeContenido,
                deviceCreatedAt = ahora,
                folioRef = recoleccion.folioRef,
                articuloRef = recoleccion.articuloRef,
                eventoRef = eventoRef
            ),
            fotos = fotos
        )

        encolado.encolar()
        return ResultadoDeCaptura.Encolado(recoleccion.folioRef)
    }

    private companion object {
        const val METODO_POST = "POST"
    }
}
