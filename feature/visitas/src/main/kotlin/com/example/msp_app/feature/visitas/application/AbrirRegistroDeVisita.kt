package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.RecomendacionMostrada
import com.example.msp_app.feature.visitas.domain.port.ContextoDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.RecomendacionesPort
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Lo que la pantalla necesita para abrirse: **a quién se visita** y **qué
 * sugirió el sistema**.
 *
 * [contexto] en `null` significa que el teléfono ya no tiene ninguna cuenta de
 * ese cliente — la pantalla no puede registrar contra nada y lo dice.
 * [recomendacion] en `null` es normal: hoy nadie escribe la tabla todavía.
 */
data class AperturaDeVisita(
    val contexto: ContextoDeVisita?,
    val recomendacion: RecomendacionMostrada?
)

/**
 * Abre el registro de visita: una lectura del cliente y una de la
 * recomendación, en una sola llamada y con una sola respuesta.
 *
 * ## Por qué la recomendación no puede tumbar la pantalla
 *
 * La visita es el trabajo; la recomendación es el acompañamiento. Si su lectura
 * falla, el cobrador tiene que poder registrar igual — pero **el error no se
 * traga**: se emite [VisitasTelemetria.CODE_RECOMENDACION_FALLO] y se sigue sin
 * ella. "Es esperado" no autoriza el silencio, autoriza un código propio.
 */
class AbrirRegistroDeVisita @Inject constructor(
    private val contexto: ContextoDeVisitaPort,
    private val recomendaciones: RecomendacionesPort,
    private val telemetry: Telemetry
) {

    suspend operator fun invoke(clienteId: Int): AperturaDeVisita = AperturaDeVisita(
        contexto = contexto.contexto(clienteId),
        recomendacion = recomendacionDe(clienteId)
    )

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room degrada igual: la pantalla sigue, el evento se emite.
    private suspend fun recomendacionDe(clienteId: Int): RecomendacionMostrada? = try {
        recomendaciones.vigenteDe(clienteId)
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = VisitasTelemetria.CODE_RECOMENDACION_FALLO,
            message = "no se pudo leer la recomendacion del cliente; la pantalla sigue sin ella",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        null
    }
}
