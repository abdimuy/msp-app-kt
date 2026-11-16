package com.example.msp_app.feature.garantias.application

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.BloqueoDeEntrega
import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.CuerposDeComandoPort
import com.example.msp_app.feature.garantias.domain.port.EncoladoDeGarantiasPort
import com.example.msp_app.feature.garantias.domain.port.FirmaDeConformidadPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import javax.inject.Inject

/**
 * Encola la entrega de un folio con la firma del cliente.
 *
 * ## El PNG se guarda antes de encolar
 *
 * El comando guarda la **ruta** de la firma y la cola lee ese archivo en cada
 * intento. Por eso el archivo se escribe primero: una entrega encolada con una
 * ruta que apunta a la nada moriría en el primer reintento, ya con el cobrador
 * fuera de la casa del cliente.
 *
 * ## Sin GPS no se encola
 *
 * El servidor exige las coordenadas (`warranty_delivery_gps_required`) y un
 * comando así se quedaría en 422 para siempre, sin forma de corregirlo desde el
 * teléfono (D5, mutante A8). La pantalla ya apaga el botón con "Esperando
 * ubicación"; esto lo vuelve a evaluar antes de escribir.
 *
 * Si el encolado falla, la firma se borra: no se deja un PNG huérfano que
 * ningún barrido del módulo conoce.
 */
@Suppress("LongParameterList") // las siete piezas son la entrega completa; ninguna es opcional.
class EntregarGarantia @Inject constructor(
    private val folios: FoliosSeguidosPort,
    private val cola: ColaDeComandosPort,
    private val cuerpos: CuerposDeComandoPort,
    private val firmas: FirmaDeConformidadPort,
    private val claves: GeneradorDeClavesPort,
    private val encolado: EncoladoDeGarantiasPort,
    @GarantiasReloj private val reloj: AppClock
) {

    /**
     * @param ubicacion el GPS del cobrador; obligatorio.
     * @param png los bytes de la firma dibujada en pantalla; obligatorios.
     */
    // Una falla de Room no es una entrega: el PNG quedaría huérfano. Se atrapa
    // amplio a propósito, igual que el interceptor de `:core:network` cuando no
    // puede renovar el token: cualquier camino que escape deja el archivo sin
    // dueño. Un `Error` (OOM) no se disfraza.
    @Suppress("TooGenericExceptionCaught")
    suspend fun ejecutar(
        folioRef: String,
        ubicacion: Ubicacion?,
        png: ByteArray?
    ): ResultadoDeCaptura {
        val fallo = validar(folioRef, ubicacion, png)
        if (fallo != null) return ResultadoDeCaptura.Bloqueado(fallo)

        val ubicacionSegura = checkNotNull(ubicacion) { "la entrega sin GPS se frena en validar" }
        val bytes = checkNotNull(png) { "la entrega sin firma se frena en validar" }
        val firma = firmas.guardarPng(bytes)
            ?: return ResultadoDeCaptura.Bloqueado(FalloDeCaptura.FIRMA_NO_GUARDADA)

        val ahora = reloj.now()
        val cuerpo = cuerpos.entrega(ahora, ubicacionSegura, firma)
        try {
            cola.encolar(
                ComandoNuevo(
                    clave = claves.nueva(),
                    operacion = OperacionDeGarantia.ENTREGAR,
                    metodo = METODO_POST,
                    ruta = RutasDeGarantia.ENTREGAR,
                    cuerpo = cuerpo.texto,
                    contentType = cuerpo.tipoDeContenido,
                    deviceCreatedAt = ahora,
                    folioRef = folioRef
                )
            )
        } catch (e: Exception) {
            firmas.borrar(firma.rutaArchivo)
            throw e
        }

        encolado.encolar()
        return ResultadoDeCaptura.Encolado(folioRef)
    }

    private suspend fun validar(
        folioRef: String,
        ubicacion: Ubicacion?,
        png: ByteArray?
    ): FalloDeCaptura? {
        val folio = folios.porId(folioRef)
        if (folio == null) return FalloDeCaptura.FOLIO_DESCONOCIDO
        if (folio.estadoLocal == EstadoLocalDeFolio.RECHAZADO) return FalloDeCaptura.FOLIO_RECHAZADO

        val firma = png?.let { FirmaDeConformidad(rutaArchivo = "", tamanoBytes = it.size) }
        return ReglasDeEntrega.bloqueosDe(ubicacion, firma).firstOrNull()?.aFallo()
    }

    private fun BloqueoDeEntrega.aFallo(): FalloDeCaptura = when (this) {
        BloqueoDeEntrega.SIN_UBICACION -> FalloDeCaptura.SIN_UBICACION
        BloqueoDeEntrega.SIN_FIRMA -> FalloDeCaptura.SIN_FIRMA
        BloqueoDeEntrega.FIRMA_VACIA -> FalloDeCaptura.FIRMA_VACIA
        BloqueoDeEntrega.FIRMA_MUY_GRANDE -> FalloDeCaptura.FIRMA_MUY_GRANDE
    }

    private companion object {
        const val METODO_POST = "POST"
    }
}
