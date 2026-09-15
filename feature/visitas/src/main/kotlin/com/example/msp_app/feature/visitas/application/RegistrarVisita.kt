package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.CitaEstructurada
import com.example.msp_app.feature.visitas.domain.port.PromesaEstructurada
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Registra una visita: arma el dato estructurado, pide la ubicación **sin
 * depender de ella**, y escribe.
 *
 * ## La ubicación nunca bloquea (propiedad de la Task 5)
 *
 * Antes de la Task 5 el encolado del envío vivía dentro de
 * `UpdateLocationService`: si ese servicio no corría —permiso negado, Play
 * Services caído, un `startForegroundService` rechazado— la visita quedaba en
 * Room y **nunca se enviaba**; el cobrador la creía registrada y no lo estaba.
 * Aquí la ubicación es un adorno del registro: si falla, se emite
 * [VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE] y la visita se escribe igual,
 * con `LAT`/`LNG` en cero, que es exactamente lo que ya hace `VisitFactory`
 * mientras el servicio parcha las columnas después.
 *
 * ## El segundo cinturón
 *
 * La pantalla ya apaga el CTA con [ReglasDeLaVisita], y aun así este caso de uso
 * vuelve a evaluar antes de escribir. No es desconfianza: una promesa sin fecha
 * es el defecto que este plan vino a arreglar, y una invariante que vive en un
 * solo lugar es una invariante que un refactor puede borrar sin poner nada rojo.
 * Los dos cinturones llaman a la MISMA función, así que no pueden discrepar.
 *
 * ## Una captura, N visitas
 *
 * Un desenlace de alcance `VENTA` marcado sobre N cuentas escribe **N filas**.
 * Es la traducción literal de lo que pasa en la puerta: "no te voy a pagar
 * nada" se dice UNA vez sobre todo lo que se debe, y hasta hoy había que
 * registrar dos visitas —dos capturas completas, dos notas, dos fotos— para
 * dejar constancia de una sola frase.
 *
 * Cada fila va atada a SU cuenta (`ventaId`), que es lo que hace que el estado
 * aterrice en la venta correcta: `VisitScope.VENTA` toca solo `saleId`
 * (`VisitsLocalDataSource.insertVisitAndUpdateState`). El alcance `CLIENTE`
 * sigue escribiendo **una sola** visita, porque ahí la propagación ya la hace la
 * base por `CLIENTE_ID` y N filas serían N veces el mismo hecho.
 *
 * Los N ids salen de [IdsDeLaVisita] — derivados, nunca acuñados al azar. El
 * porqué completo y la propiedad de idempotencia están en su KDoc.
 */
class RegistrarVisita @Inject constructor(
    private val registro: RegistroDeVisitaPort,
    private val ubicacion: UbicacionPort,
    private val telemetry: Telemetry
) {

    /**
     * @param visitaId el id de la visita, acuñado una vez por la pantalla y
     *   persistido en su `SavedStateHandle`: también es la clave de idempotencia
     *   del envío.
     * @param ventaId la cuenta que el cobrador tenía abierta, o `null` si entró
     *   por el cliente.
     * @param comprobantes las fotos adjuntas, en orden de captura (Task 23).
     *   Viajan hasta el puerto **sin pasar por [ReglasDeLaVisita]**: ninguna
     *   regla las mira y ninguna las puede convertir en un bloqueo. Es la
     *   traducción literal de "la foto nunca bloquea el guardado".
     */
    @Suppress("LongParameterList") // los comprobantes son el septimo dato del hecho, no una opcion.
    suspend operator fun invoke(
        visitaId: String,
        clienteId: Int,
        ventaId: Int?,
        captura: CapturaDeVisita,
        hoy: LocalDate,
        recomendacionId: String? = null,
        comprobantes: List<ComprobanteDeVisita> = emptyList()
    ): ResultadoDelRegistro {
        val bloqueos = ReglasDeLaVisita.bloqueosDe(captura, hoy)
        if (bloqueos.isNotEmpty()) {
            // Anti-PII: viajan los NOMBRES de los bloqueos, nunca la fecha, el
            // monto ni la nota.
            telemetry.error(
                code = VisitasTelemetria.CODE_CAPTURA_BLOQUEADA_EN_APLICACION,
                message = "una captura bloqueada llego al caso de uso; la pantalla dejo pasar algo",
                props = mapOf(
                    VisitasTelemetria.PROP_BLOQUEOS to bloqueos.joinToString(",") { it.name }
                )
            )
            return ResultadoDelRegistro.FALLO_EL_GUARDADO
        }
        val resultado = requireNotNull(captura.resultado) {
            "sin resultado no hay bloqueos vacios: ReglasDeLaVisita lo garantiza"
        }
        // UNA sola lectura de la ubicación para las N filas: son el mismo hecho,
        // en el mismo instante y en la misma puerta. Pedirla N veces daría N
        // coordenadas distintas de un cobrador que no se movió, y N eventos de
        // telemetría por un solo permiso negado.
        val donde = ubicacionActual()
        val plantilla = VisitaARegistrar(
            visitaId = visitaId,
            clienteId = clienteId,
            ventaId = ventaId,
            tipoVisita = tipoVisitaDe(resultado, captura),
            nota = captura.nota.trim().takeIf { it.isNotBlank() },
            cita = citaDe(resultado, captura),
            ubicacion = donde,
            recomendacionId = recomendacionId,
            comprobantes = comprobantes
        )
        if (resultado.alcance == VisitScope.CLIENTE) {
            // El hecho es de la puerta entera y la base lo propaga sola a todas
            // las cuentas activas del cliente. Una fila, con la semilla de id
            // tal cual.
            return registro.registrar(plantilla)
        }
        return registrarUnaPorCuenta(plantilla, resultado, captura)
    }

    /**
     * Escribe una visita por cuenta marcada y contesta **el peor desenlace**.
     *
     * ## Por qué no se corta en el primer fallo
     *
     * Porque cada `registrar` es su propia transacción y las cuentas son
     * independientes: abandonar al primer tropiezo tiraría el trabajo de campo
     * de las cuentas que sí podían escribirse. Se intentan todas, se reporta el
     * primer fallo, y el reintento —con la MISMA semilla y las MISMAS cuentas—
     * vuelve a derivar los mismos ids y reescribe sobre sí mismo.
     *
     * ## Qué se lleva el ancla, y qué no se duplica
     *
     * - **Las fotos** cuelgan solo del ancla. `VisitImageEntity.ID` es llave
     *   primaria, así que la misma foto en N visitas se colapsaría a la última y
     *   las otras N-1 quedarían sin evidencia — peor que no intentarlo.
     * - **La recomendación** también. El par "qué sugirió el sistema / qué hizo
     *   el cobrador" es UN hecho; `ligarConVisita` es un `UPDATE` sobre la fila
     *   de la recomendación, así que ligarla N veces solo dejaría la última y
     *   haría ilegible la evaluación.
     * - **La promesa** solo puede existir sobre una cuenta, y por eso
     *   [ResultadoDeVisita.admiteVariasCuentas] la deja fuera de la selección
     *   múltiple: aquí su lista trae exactamente una.
     */
    private suspend fun registrarUnaPorCuenta(
        plantilla: VisitaARegistrar,
        resultado: ResultadoDeVisita,
        captura: CapturaDeVisita
    ): ResultadoDelRegistro {
        val ids = IdsDeLaVisita.paraCuentas(plantilla.visitaId, captura.cuentas)
        val ancla = ids.keys.first()
        var escritas = 0
        var primerFallo: ResultadoDelRegistro? = null
        ids.forEach { (cuenta, id) ->
            val desenlace = registro.registrar(
                plantilla.copy(
                    visitaId = id,
                    ventaId = cuenta,
                    promesa = promesaDe(resultado, captura, cuenta),
                    recomendacionId = plantilla.recomendacionId.takeIf { cuenta == ancla },
                    comprobantes = if (cuenta == ancla) plantilla.comprobantes else emptyList()
                )
            )
            if (desenlace == ResultadoDelRegistro.REGISTRADA) {
                escritas++
            } else if (primerFallo == null) {
                primerFallo = desenlace
            }
        }
        val fallo = primerFallo ?: return ResultadoDelRegistro.REGISTRADA
        if (escritas > 0) reportarParcial(escritas, ids.size, fallo)
        return fallo
    }

    /**
     * Unas cuentas quedaron escritas y otras no. **No se traga**: el cobrador ve
     * "no se pudo guardar" y la base tiene parte del hecho, que es un estado que
     * nadie podría reconstruir después sin este evento.
     *
     * Anti-PII: viajan dos CONTEOS y el nombre del desenlace. Ni ids de cuenta,
     * ni nombres, ni montos.
     */
    private fun reportarParcial(escritas: Int, total: Int, fallo: ResultadoDelRegistro) {
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA,
            message = "unas cuentas de la captura quedaron escritas y otras no",
            props = mapOf(
                VisitasTelemetria.PROP_OCURRENCIAS to escritas.toString(),
                VisitasTelemetria.PROP_CUENTAS to total.toString(),
                VisitasTelemetria.PROP_RESULTADO to fallo.name
            )
        )
    }

    /**
     * El literal de `TIPO_VISITA`. Sale del catálogo cerrado, nunca de texto
     * libre: una etiqueta que no pertenezca al resultado se corrige a la de ese
     * resultado en vez de escribirse tal cual — el servidor valida la escritura
     * (Ruling D) y una etiqueta cruzada rompería además la derivación del estado.
     */
    private fun tipoVisitaDe(resultado: ResultadoDeVisita, captura: CapturaDeVisita): String {
        val etiqueta = captura.etiqueta
        return if (etiqueta != null && CatalogoDeResultados.perteneceA(resultado, etiqueta)) {
            etiqueta
        } else {
            CatalogoDeResultados.etiquetaPorDefecto(resultado)
        }
    }

    /**
     * La promesa estructurada. Solo existe bajo [ResultadoDeVisita.PROMETIO]:
     * arrastrar una fecha tecleada y luego descartada al cambiar de resultado
     * escribiría una promesa que el cobrador no hizo.
     */
    private fun promesaDe(
        resultado: ResultadoDeVisita,
        captura: CapturaDeVisita,
        cuenta: Int
    ): PromesaEstructurada? {
        if (resultado != ResultadoDeVisita.PROMETIO) return null
        val fecha = captura.fechaPromesa ?: return null
        return PromesaEstructurada(
            ventaId = cuenta,
            fecha = fecha,
            monto = captura.montoPrometido
        )
    }

    /** La cita estructurada. Misma regla que la promesa: solo bajo su resultado. */
    private fun citaDe(resultado: ResultadoDeVisita, captura: CapturaDeVisita): CitaEstructurada? {
        if (resultado != ResultadoDeVisita.CITA) return null
        val fecha = captura.fechaCita ?: return null
        return CitaEstructurada(fecha = fecha, hora = captura.horaCita)
    }

    /**
     * La ubicación, si se puede saber. **Total:** ni una excepción ni un `null`
     * detienen el registro, y **ninguno de los dos se calla**.
     *
     * Los dos caminos emiten [VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE]
     * porque los dos significan lo mismo para quien depura ("esta visita quedó
     * sin coordenadas"), y se distinguen por [VisitasTelemetria.PROP_CAUSA]. Un
     * `null` silencioso sería el caso MÁS común —permiso denegado— convertido en
     * el único que no deja rastro.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // permiso, Play Services o hardware pueden fallar con cualquier excepcion.
    private suspend fun ubicacionActual(): UbicacionDeLaVisita? {
        val donde = try {
            ubicacion.ubicacionActual()
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            // Anti-PII: el NOMBRE de la clase de la excepción, nunca su texto.
            reportarSinUbicacion(
                causa = VisitasTelemetria.CAUSA_EXCEPCION,
                excepcion = fallo.javaClass.simpleName
            )
            return null
        }
        if (donde == null) reportarSinUbicacion(causa = VisitasTelemetria.CAUSA_SIN_DATO)
        return donde
    }

    private fun reportarSinUbicacion(causa: String, excepcion: String? = null) {
        telemetry.error(
            code = VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE,
            message = "no se pudo obtener la ubicacion; la visita se registra igual",
            props = buildMap {
                put(VisitasTelemetria.PROP_CAUSA, causa)
                excepcion?.let { put(VisitasTelemetria.PROP_EXCEPCION, it) }
            }
        )
    }
}
