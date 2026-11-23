package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.garantias.data.http.CuerpoDeGarantia
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.ClasificadorDeEnvio
import com.example.msp_app.feature.garantias.domain.DecisionDeEnvio
import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.FotoPendienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.EstadoDeLasFotosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import java.io.IOException
import javax.inject.Inject

/**
 * Sube las fotos que ya tienen su evento en el servidor.
 *
 * ## La clave de la foto no se regenera
 *
 * [FotoPendienteDeGarantia.clave] se generó al tomar la foto y es también el
 * `id` de la imagen en el servidor. Un reintento manda la misma clave y los
 * mismos bytes, así que el servidor no crea una segunda imagen para la misma
 * evidencia (D3, mutante A1).
 *
 * ## El archivo se borra sólo después del 2xx
 *
 * El orden es [EstadoDeLasFotosPort.marcarSubida] y **después**
 * [EstadoDeLasFotosPort.eliminar], que borra la fila y su archivo. Si la
 * subida no respondió 2xx, la foto sigue en disco y en la cola (mutante A7).
 */
internal class SubidorDeFotos @Inject constructor(
    private val remotas: GarantiasRemotasPort,
    private val fotos: FotosDeGarantiaPort,
    private val estado: EstadoDeLasFotosPort,
    private val archivos: ArchivosDeGarantiaPort,
    @GarantiasReloj private val reloj: AppClock
) {

    /** Sube todo lo que ya tiene `EVENTO_ID`; lo demás espera a su comando. */
    suspend fun subirPendientes(acumulado: Acumulado) {
        for (foto in fotos.listasParaSubir()) {
            subir(foto, acumulado)
        }
    }

    /** Cuántas fotos siguen en la cola, para decidir si el trabajador reintenta. */
    suspend fun pendientesCount(): Int = fotos.pendientesCount()

    private suspend fun subir(foto: FotoPendienteDeGarantia, acumulado: Acumulado) {
        val folioId = foto.folioId ?: return
        val eventoId = foto.eventoId ?: return

        val bytes = archivos.leer(foto.rutaArchivo) ?: run {
            estado.marcarRechazada(foto.clave, MotivosDeLaCola.FOTO_ILEGIBLE, reloj.now())
            acumulado.fotoRechazada()
            return
        }

        val resultado = enviar(foto, folioId, eventoId, bytes)
        aplicar(foto, resultado, acumulado)
    }

    private suspend fun enviar(
        foto: FotoPendienteDeGarantia,
        folioId: String,
        eventoId: String,
        bytes: ByteArray
    ): ResultadoDeLaLlamada {
        val cuerpo = CuerpoDeGarantia.imagen(bytes, foto.mime)
        val ruta = RutasDeGarantia.resolver(
            plantilla = RutasDeGarantia.IMAGENES,
            folioId = folioId,
            eventoId = eventoId
        )
        return try {
            remotas.subirImagen(foto.clave, ruta, cuerpo)
        } catch (e: IOException) {
            ResultadoDeLaLlamada.FalloDeRed(e.message ?: MotivosDeLaCola.SIN_RED)
        }
    }

    private suspend fun aplicar(
        foto: FotoPendienteDeGarantia,
        resultado: ResultadoDeLaLlamada,
        acumulado: Acumulado
    ) {
        val ahora = reloj.now()
        when (val decision = ClasificadorDeEnvio.clasificar(resultado)) {
            DecisionDeEnvio.Enviado -> {
                estado.marcarSubida(foto.clave, ahora)
                estado.eliminar(foto.clave)
                acumulado.fotoSubida()
            }
            DecisionDeEnvio.RenovarToken ->
                estado.anotarIntento(foto.clave, MotivosDeLaCola.TOKEN_VENCIDO, ahora)
            DecisionDeEnvio.Reintentar ->
                estado.anotarIntento(foto.clave, motivoDe(resultado), ahora)
            is DecisionDeEnvio.Rechazado -> {
                estado.marcarRechazada(foto.clave, decision.motivo, ahora)
                acumulado.fotoRechazada()
            }
        }
    }
}
