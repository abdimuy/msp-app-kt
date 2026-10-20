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
import com.example.msp_app.feature.visitas.domain.port.FaseDelGuardado
import com.example.msp_app.feature.visitas.domain.port.GuardadoCanceladoPort
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
 *
 * ## La cancelación deja rastro (E-APP-078)
 *
 * El caso de uso corre bajo el `viewModelScope` de la pantalla. Si el cobrador
 * sale mientras se espera la ubicación (sin red, ~30 s sin fix), la cancelación
 * llega antes de cualquier escritura y la visita no se guarda — medido, 9 de 9,
 * y hasta hoy sin ningún evento. Aquí **la conducta no cambia**: la cancelación
 * se relanza, la MISMA instancia, y lo único nuevo es que antes se anota
 * [VisitasTelemetria.CODE_VISITA_GUARDADO_CANCELADO] (local) y se avisa por
 * [avisoDeCancelacion] (lo que llega a la oficina). El arreglo de fondo es otra
 * tarea.
 */
@Suppress(
    // Cada paso del guardado (cancelacion, parcial, telemetria total) es su
    // propia funcion; juntarlos para detekt mezclaria caminos de guardado.
    "TooManyFunctions"
)
class RegistrarVisita @Inject constructor(
    private val registro: RegistroDeVisitaPort,
    private val ubicacion: UbicacionPort,
    private val telemetry: Telemetry,
    private val avisoDeCancelacion: GuardadoCanceladoPort = GuardadoCanceladoPort { _, _, _ -> }
) {

    /** Hasta dónde llegó un guardado, para contarlo si se cancela. */
    private class Avance(var cuentas: Int) {
        var fase: FaseDelGuardado = FaseDelGuardado.UBICACION
        var escritas: Int = 0
    }

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
     * @param alEscribir se llama una vez por cada visita que QUEDÓ escrita, en
     *   el momento en que quedó. Es lo único que deja a la pantalla distinguir un
     *   fallo que no escribió nada de un parcial (E-VIS-055): el desenlace es el
     *   mismo [ResultadoDelRegistro] en los dos casos.
     */
    @Suppress("LongParameterList") // los comprobantes son el septimo dato del hecho, no una opcion.
    suspend operator fun invoke(
        visitaId: String,
        clienteId: Int,
        ventaId: Int?,
        captura: CapturaDeVisita,
        hoy: LocalDate,
        recomendacionId: String? = null,
        comprobantes: List<ComprobanteDeVisita> = emptyList(),
        alEscribir: () -> Unit = {}
    ): ResultadoDelRegistro {
        val bloqueos = ReglasDeLaVisita.bloqueosDe(captura, hoy)
        if (bloqueos.isNotEmpty()) {
            // Anti-PII: viajan los NOMBRES de los bloqueos, nunca la fecha, el
            // monto ni la nota.
            reportarSinLanzar(
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
        val avance = Avance(
            cuentas = if (resultado.alcance == VisitScope.CLIENTE) 1 else captura.cuentas.size
        )
        return try {
            registrarConAvance(
                visitaId = visitaId,
                clienteId = clienteId,
                ventaId = ventaId,
                captura = captura,
                resultado = resultado,
                recomendacionId = recomendacionId,
                comprobantes = comprobantes,
                avance = avance,
                alEscribir = alEscribir
            )
        } catch (cancelada: CancellationException) {
            reportarCancelado(avance)
            // E-APP-078: la MISMA instancia, sin envolver.
            throw cancelada
        }
    }

    @Suppress("LongParameterList") // los datos del hecho mas el avance que se cuenta.
    private suspend fun registrarConAvance(
        visitaId: String,
        clienteId: Int,
        ventaId: Int?,
        captura: CapturaDeVisita,
        resultado: ResultadoDeVisita,
        recomendacionId: String?,
        comprobantes: List<ComprobanteDeVisita>,
        avance: Avance,
        alEscribir: () -> Unit
    ): ResultadoDelRegistro {
        // UNA sola lectura de la ubicación para las N filas: son el mismo hecho,
        // en el mismo instante y en la misma puerta. Pedirla N veces daría N
        // coordenadas distintas de un cobrador que no se movió, y N eventos de
        // telemetría por un solo permiso negado.
        val donde = ubicacionActual()
        avance.fase = FaseDelGuardado.CUENTAS
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
            return registro.registrar(plantilla).also {
                if (it == ResultadoDelRegistro.REGISTRADA) alEscribir()
            }
        }
        return registrarUnaPorCuenta(plantilla, resultado, captura, avance, alEscribir)
    }

    /**
     * Anota la cancelación. Cada una de sus dos salidas (la telemetría local y el
     * aviso a la oficina) va en su propio `try`: si una lanza, la otra se intenta
     * igual y la excepción que sale sigue siendo la cancelación. Lo mismo vale
     * para el parcial del `catch` de [registrarUnaPorCuenta] (C1 de la compuerta
     * DESPUÉS). Anti-PII: solo conteos y la fase.
     */
    @Suppress("TooGenericExceptionCaught") // la telemetria nunca reemplaza a la cancelacion.
    private fun reportarCancelado(avance: Avance) {
        try {
            telemetry.error(
                code = VisitasTelemetria.CODE_VISITA_GUARDADO_CANCELADO,
                message = "el guardado de la visita se cancelo al salir de la pantalla",
                props = mapOf(
                    VisitasTelemetria.PROP_ESCRITAS to avance.escritas.toString(),
                    VisitasTelemetria.PROP_CUENTAS to avance.cuentas.toString(),
                    VisitasTelemetria.PROP_FASE to when (avance.fase) {
                        FaseDelGuardado.UBICACION -> VisitasTelemetria.FASE_UBICACION
                        FaseDelGuardado.CUENTAS -> VisitasTelemetria.FASE_CUENTAS
                    }
                )
            )
        } catch (_: Throwable) {
            // Sin telemetría local; el aviso a la oficina se intenta igual.
        }
        try {
            avisoDeCancelacion.cancelado(avance.fase, avance.escritas, avance.cuentas)
        } catch (_: Throwable) {
            // Idem: la cancelación sale intacta.
        }
    }

    /**
     * Escribe las N cuentas en UNA escritura, **todo o nada** (E-APP-087,
     * decisión 3 del dueño 2026-10-20). Ya no hay parcial k de N: o quedaron
     * las N o ninguna, y el reintento —con la MISMA semilla y las MISMAS
     * cuentas— deriva los mismos ids; el marcador ESCRITA impide reescribir
     * (no re-fija `ESTADO_COBRANZA`). Una cancelación sale intacta (E-APP-078).
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
     *   Que el ancla no cambie entre un parcial y su reintento lo sostiene la
     *   pantalla, que congela el desenlace y las cuentas (E-VIS-055): con otras
     *   cuentas el ancla sería otra visita y el `REPLACE` sobre el id de la foto
     *   la mudaría.
     * - **La promesa** solo puede existir sobre una cuenta, y por eso
     *   [ResultadoDeVisita.admiteVariasCuentas] la deja fuera de la selección
     *   múltiple: aquí su lista trae exactamente una.
     */
    private suspend fun registrarUnaPorCuenta(
        plantilla: VisitaARegistrar,
        resultado: ResultadoDeVisita,
        captura: CapturaDeVisita,
        avance: Avance,
        alEscribir: () -> Unit
    ): ResultadoDelRegistro {
        val ids = IdsDeLaVisita.paraCuentas(plantilla.visitaId, captura.cuentas)
        avance.cuentas = ids.size
        val ancla = ids.keys.first()
        val visitas = ids.map { (cuenta, id) ->
            plantilla.copy(
                visitaId = id,
                ventaId = cuenta,
                promesa = promesaDe(resultado, captura, cuenta),
                recomendacionId = plantilla.recomendacionId.takeIf { cuenta == ancla },
                comprobantes = if (cuenta == ancla) plantilla.comprobantes else emptyList()
            )
        }
        val desenlace = registro.registrarTodas(plantilla.visitaId, visitas)
        if (desenlace == ResultadoDelRegistro.REGISTRADA) {
            avance.escritas = ids.size
            repeat(ids.size) { alEscribir() }
        }
        return desenlace
    }

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
        reportarSinLanzar(
            code = VisitasTelemetria.CODE_UBICACION_NO_DISPONIBLE,
            message = "no se pudo obtener la ubicacion; la visita se registra igual",
            props = buildMap {
                put(VisitasTelemetria.PROP_CAUSA, causa)
                excepcion?.let { put(VisitasTelemetria.PROP_EXCEPCION, it) }
            }
        )
    }

    /**
     * E-VIS-074: la salida a `telemetry.error` de los reportes del registro.
     * **Total: no lanza.** `DurableTelemetry.error` inserta en Room de forma
     * síncrona y puede fallar. Si su excepción saliera del caso de uso (el
     * parcial corre con el ancla YA escrita), la pantalla nunca llegaría a
     * `aplicar`: se quedaría en "guardando" y la corrutina del `viewModelScope`
     * moriría sin manejador. El desenlace del registro es lo que se contesta.
     */
    @Suppress("TooGenericExceptionCaught") // la telemetria caida no cambia el desenlace.
    private fun reportarSinLanzar(code: String, message: String, props: Map<String, String>) {
        try {
            telemetry.error(code = code, message = message, props = props)
        } catch (_: Throwable) {
            // Sin reporte: el desenlace sigue su camino.
        }
    }
}
