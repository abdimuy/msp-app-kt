package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.garantias.data.http.RespuestasDeGarantia
import com.example.msp_app.feature.garantias.di.GarantiasReloj
import com.example.msp_app.feature.garantias.domain.ClasificadorDeEnvio
import com.example.msp_app.feature.garantias.domain.DecisionDeEnvio
import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import com.example.msp_app.feature.garantias.domain.port.ProcesadorDeGarantias
import com.example.msp_app.feature.garantias.domain.port.ResultadoDeLaCola
import java.io.IOException
import javax.inject.Inject

/**
 * Manda lo encolado, en orden y sin rearmar nada.
 *
 * ## El orden es por folio, no global
 *
 * Un comando no sale mientras otro anterior del **mismo** folio siga pendiente:
 * `abrir` tiene que responder antes de que salga el artículo, y la recolección
 * antes de que salgan sus fotos. Si uno queda rechazado, los que dependen de él
 * se detienen en vez de pegar contra una ruta que nunca va a existir (D4,
 * mutante A5). Folios distintos no se estorban.
 *
 * ## La clave y los bytes no se tocan
 *
 * El comando se leyó de Room y se manda tal como se guardó. Este procesador no
 * genera claves ni reconstruye el cuerpo: eso es lo que hace que un reintento
 * sea el mismo comando para el servidor y no un 409 (D3, mutantes A1 y A2).
 */
@Suppress("LongParameterList") // las siete piezas son la cola completa; ninguna es opcional.
internal class ProcesadorDeLaCola @Inject constructor(
    private val cola: ColaDeComandosPort,
    private val folios: FoliosSeguidosPort,
    private val remotas: GarantiasRemotasPort,
    private val resolutor: ResolutorDeReferencias,
    private val subidor: SubidorDeFotos,
    private val cuerpos: CuerposDeLaCola,
    @GarantiasReloj private val reloj: AppClock
) : ProcesadorDeGarantias {

    override suspend fun procesar(): ResultadoDeLaCola {
        val acumulado = Acumulado()
        if (recorrer(acumulado)) subidor.subirPendientes(acumulado)
        return acumulado.resultado(
            comandosPendientes = cola.pendientes().size,
            fotosPendientes = subidor.pendientesCount()
        )
    }

    /** Recorre los folios y devuelve `false` cuando no hay señal para seguir. */
    private suspend fun recorrer(acumulado: Acumulado): Boolean {
        val porFolio = cola.pendientes().groupBy { it.folioRef }
        for ((folioRef, comandos) in porFolio) {
            val paso = if (folioRef == null) {
                procesarSueltos(comandos, acumulado)
            } else {
                procesarFolio(folioRef, acumulado)
            }
            if (paso == Paso.DetenerTodo) return false
        }
        return true
    }

    private suspend fun procesarSueltos(
        comandos: List<ComandoDeGarantia>,
        acumulado: Acumulado
    ): Paso {
        val referencias = Referencias()
        for (comando in comandos) {
            val paso = procesarComando(comando, referencias, acumulado)
            if (paso != Paso.Seguir) return paso
        }
        return Paso.Seguir
    }

    /**
     * Los comandos de un folio, en el orden en que se guardaron.
     *
     * Un `enviado` deja pasar al siguiente; un `rechazado` detiene el folio
     * entero; un `pendiente` se intenta y, si no sale, también lo detiene.
     */
    private suspend fun procesarFolio(folioRef: String, acumulado: Acumulado): Paso {
        val comandos = cola.delFolio(folioRef)
        val referencias = Referencias.de(comandos)
        for (comando in comandos) {
            when (comando.estado) {
                EstadoDeComando.ENVIADO -> Unit
                EstadoDeComando.RECHAZADO -> return Paso.DetenerFolio
                EstadoDeComando.PENDIENTE -> {
                    val paso = procesarComando(comando, referencias, acumulado)
                    if (paso != Paso.Seguir) return paso
                }
            }
        }
        return Paso.Seguir
    }

    private suspend fun procesarComando(
        comando: ComandoDeGarantia,
        referencias: Referencias,
        acumulado: Acumulado
    ): Paso {
        val ruta = cuerpos.rutaDe(comando, referencias)
            ?: return anotarIntento(comando, MotivosDeLaCola.SIN_REFERENCIAS, acumulado)
        return when (val cuerpo = cuerpos.cuerpoDe(comando)) {
            is CuerpoDelComando.Listo ->
                enviar(comando, ruta, cuerpo.cuerpo, referencias, acumulado)
            CuerpoDelComando.SinCuerpo ->
                rechazar(comando, MotivosDeLaCola.SIN_CUERPO, acumulado)
            CuerpoDelComando.FirmaIlegible ->
                rechazar(comando, MotivosDeLaCola.FIRMA_ILEGIBLE, acumulado)
        }
    }

    private suspend fun enviar(
        comando: ComandoDeGarantia,
        ruta: String,
        cuerpo: com.example.msp_app.feature.garantias.domain.model.CuerpoHttp,
        referencias: Referencias,
        acumulado: Acumulado
    ): Paso {
        val resultado = try {
            remotas.enviar(comando.clave, comando.metodo, ruta, cuerpo)
        } catch (_: IOException) {
            return anotarIntento(comando, MotivosDeLaCola.SIN_RED, acumulado, sinRed = true)
        }
        return when (val decision = ClasificadorDeEnvio.clasificar(resultado)) {
            DecisionDeEnvio.Enviado ->
                if (comando.operacion == OperacionDeGarantia.ABRIR_FOLIO) {
                    confirmarApertura(comando, resultado, referencias, acumulado)
                } else {
                    confirmarDependiente(comando, referencias, acumulado)
                }
            DecisionDeEnvio.Reintentar ->
                anotarIntento(
                    comando,
                    motivoDe(resultado),
                    acumulado,
                    resultado is ResultadoDeLaLlamada.FalloDeRed
                )
            DecisionDeEnvio.RenovarToken ->
                anotarIntento(comando, MotivosDeLaCola.TOKEN_VENCIDO, acumulado)
            is DecisionDeEnvio.Rechazado -> rechazar(comando, decision.motivo, acumulado)
        }
    }

    /** Un comando que necesita el folio ya abierto y, si aplica, su evento. */
    private suspend fun confirmarDependiente(
        comando: ComandoDeGarantia,
        referencias: Referencias,
        acumulado: Acumulado
    ): Paso {
        val folioId = referencias.folioIdDe(comando)
            ?: return anotarIntento(comando, MotivosDeLaCola.SIN_REFERENCIAS, acumulado)
        val resuelto = try {
            resolutor.resolver(comando, folioId, referencias, reloj.now())
        } catch (_: IOException) {
            return anotarIntento(comando, MotivosDeLaCola.SIN_RED, acumulado, sinRed = true)
        }
        if (!resuelto) return anotarIntento(comando, MotivosDeLaCola.SIN_RESOLVER, acumulado)

        cola.marcarEnviado(comando.id, reloj.now())
        acumulado.comandoEnviado()
        return Paso.Seguir
    }

    /**
     * `abrir` devuelve el `id` del folio en su propio cuerpo; se guarda **antes**
     * de marcar el comando enviado, para que un corte entre las dos escrituras
     * no pierda el folio (D4).
     */
    private suspend fun confirmarApertura(
        comando: ComandoDeGarantia,
        resultado: ResultadoDeLaLlamada,
        referencias: Referencias,
        acumulado: Acumulado
    ): Paso {
        val cuerpo = (resultado as? ResultadoDeLaLlamada.Respuesta)?.cuerpo
        val folioId = RespuestasDeGarantia.idDeFolio(cuerpo)
            ?: return anotarIntento(comando, MotivosDeLaCola.SIN_ID_DE_FOLIO, acumulado)

        val ahora = reloj.now()
        comando.folioRef?.let { ref ->
            resolutor.resolverFolio(ref, folioId, referencias, ahora)
            folios.marcarAbierto(ref, folioId, ahora)
        }
        cola.marcarEnviado(comando.id, ahora)
        acumulado.comandoEnviado()
        return Paso.Seguir
    }

    private suspend fun rechazar(
        comando: ComandoDeGarantia,
        motivo: String,
        acumulado: Acumulado
    ): Paso {
        val ahora = reloj.now()
        cola.marcarRechazado(comando.id, motivo, ahora)
        if (comando.operacion == OperacionDeGarantia.ABRIR_FOLIO) {
            comando.folioRef?.let { ref ->
                folios.marcarEstado(ref, EstadoLocalDeFolio.RECHAZADO, motivo, ahora)
            }
        }
        acumulado.comandoRechazado()
        return Paso.DetenerFolio
    }

    private suspend fun anotarIntento(
        comando: ComandoDeGarantia,
        motivo: String,
        acumulado: Acumulado,
        sinRed: Boolean = false
    ): Paso {
        cola.anotarIntento(comando.id, motivo, reloj.now())
        if (sinRed) acumulado.marcarSinRed()
        return if (sinRed) Paso.DetenerTodo else Paso.DetenerFolio
    }
}

/** Qué hacer con el resto del folio después de un comando. */
internal enum class Paso {
    Seguir,
    DetenerFolio,
    DetenerTodo
}
