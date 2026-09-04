package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.TicketPrinting
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.application.CargarTicketDeVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.di.VisitasIoDispatcher
import com.example.msp_app.feature.visitas.domain.model.TicketDeVisita
import com.example.msp_app.feature.visitas.printing.TicketDeVisitaFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El ticket de visita: lo arma, lo muestra y lo imprime.
 *
 * Gemelo del `TicketDePagoViewModel` y por la misma razón que sus estados
 * comparten forma: la regla del mock —**solo el día del cobro**, **cada
 * impresión registrada**— es una sola, y las dos pantallas la aplican con las
 * MISMAS piezas de `:core:printing` (la fachada [TicketPrinting]). Nadie llama a `PrinterPort.print` directo desde aquí:
 * imprimir y registrar son una sola operación, así que no existe un camino que
 * imprima sin dejar rastro.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions"
) // una funcion por control del flujo de impresion; agruparlas escondería cuál toca qué fase.
class TicketDeVisitaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarTicket: CargarTicketDeVisita,
    private val impresion: TicketPrinting,
    private val telemetry: Telemetry,
    @VisitasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    val visitaId: String = checkNotNull(savedStateHandle.get<String>(VisitasRutas.ARG_VISITA_ID)) {
        "TicketDeVisitaViewModel sin ${VisitasRutas.ARG_VISITA_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(TicketDeVisitaUiState())
    val state: StateFlow<TicketDeVisitaUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(VisitasTelemetria.PANTALLA_TICKET)
        cargar()
    }

    /** Vuelve a leer la visita y a evaluar el permiso. Cada llamada es UNA lectura. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(cargando = true, error = null)
            mutableState.value = leer()
        }
    }

    /** Imprime en la impresora recordada, o abre el picker si no hay ninguna. */
    fun imprimir() {
        val actual = mutableState.value
        if (!actual.sePuedeImprimir) return
        telemetry.tap(VisitasTelemetria.PANTALLA_TICKET, "imprimir")
        viewModelScope.launch {
            enFase(FaseDeImpresionDeVisita.IMPRIMIENDO)
            val emparejadas = withContext(io) { impresion.emparejadas() }.getOrElse {
                reportarFallo(it, destino = null, disponibles = emptyList())
                return@launch
            }
            val recordada = withContext(io) { impresion.recordada(emparejadas) }
            if (recordada == null) {
                mutableState.value = mutableState.value.copy(
                    impresion = ImpresionDeVisitaUi(
                        fase = FaseDeImpresionDeVisita.ELIGIENDO,
                        disponibles = emparejadas
                    )
                )
            } else {
                imprimirEn(recordada, emparejadas)
            }
        }
    }

    /** Abre el picker en cualquier momento — "cambiar impresora" siempre disponible. */
    fun cambiarImpresora() {
        telemetry.tap(VisitasTelemetria.PANTALLA_TICKET, "cambiar_impresora")
        viewModelScope.launch {
            val previa = mutableState.value.impresion
            val emparejadas = withContext(io) { impresion.emparejadas() }.getOrElse {
                reportarFallo(it, destino = previa.impresora, disponibles = emptyList())
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                impresion = ImpresionDeVisitaUi(
                    fase = FaseDeImpresionDeVisita.ELIGIENDO,
                    impresora = previa.impresora,
                    disponibles = emparejadas
                )
            )
        }
    }

    /** Elige impresora, la recuerda para la próxima e imprime en ella. */
    fun elegirImpresora(dispositivo: PrinterDevice) {
        if (!mutableState.value.sePuedeImprimir) return
        telemetry.tap(VisitasTelemetria.PANTALLA_TICKET, "elegir_impresora")
        val disponibles = mutableState.value.impresion.disponibles.ifEmpty { listOf(dispositivo) }
        viewModelScope.launch {
            withContext(io) { impresion.recordar(dispositivo) }
            imprimirEn(dispositivo, disponibles)
        }
    }

    /** Cierra el picker o el aviso de fallo sin salir de la pantalla. */
    fun cerrarImpresion() {
        mutableState.value = mutableState.value.copy(
            impresion = ImpresionDeVisitaUi(impresora = mutableState.value.impresion.impresora)
        )
    }

    private suspend fun imprimirEn(dispositivo: PrinterDevice, disponibles: List<PrinterDevice>) {
        val actual = mutableState.value
        val ticket = actual.ticket ?: return
        val permiso = actual.permiso ?: return
        mutableState.value = actual.copy(
            impresion = ImpresionDeVisitaUi(
                fase = FaseDeImpresionDeVisita.IMPRIMIENDO,
                impresora = dispositivo,
                disponibles = disponibles
            )
        )
        val lineas = TicketDeVisitaFormatter.toTicketLines(ticket, permiso)
        val resultado = withContext(io) {
            impresion.imprimir(device = dispositivo, ticketId = ticket.visitaId, ticket = lineas)
        }
        resultado.fold(
            onSuccess = {
                telemetry.tap(VisitasTelemetria.PANTALLA_TICKET, "impreso")
                val nuevoPermiso = withContext(io) {
                    impresion.permiso(ticket.visitaId, ticket.registradaEn)
                }
                mutableState.value = mutableState.value.copy(
                    permiso = nuevoPermiso,
                    vistaPrevia = vistaPreviaDe(ticket, nuevoPermiso),
                    impresion = ImpresionDeVisitaUi(
                        fase = FaseDeImpresionDeVisita.IMPRESO,
                        impresora = dispositivo,
                        disponibles = disponibles
                    )
                )
            },
            onFailure = { reportarFallo(it, dispositivo, disponibles) }
        )
    }

    private fun enFase(fase: FaseDeImpresionDeVisita) {
        mutableState.value = mutableState.value.copy(
            impresion = mutableState.value.impresion.copy(fase = fase, mensaje = null)
        )
    }

    /**
     * Un fallo de impresión NO se traga (NORMA DE ERRORES): se emite con su
     * código y, anti-PII, con el NOMBRE de la clase del fallo — nunca la MAC de
     * la impresora, que identifica el equipo del cobrador.
     */
    private fun reportarFallo(
        fallo: Throwable,
        destino: PrinterDevice?,
        disponibles: List<PrinterDevice>
    ) {
        telemetry.error(
            code = VisitasTelemetria.CODE_TICKET_VISITA_NO_SE_IMPRIMIO,
            message = "el ticket de visita no se imprimio",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        mutableState.value = mutableState.value.copy(
            impresion = ImpresionDeVisitaUi(
                fase = FaseDeImpresionDeVisita.FALLO,
                impresora = destino,
                disponibles = disponibles,
                mensaje = mensajeDe(fallo)
            )
        )
    }

    @Suppress("TooGenericExceptionCaught") // cualquier fallo de Room degrada igual; se reporta.
    private suspend fun leer(): TicketDeVisitaUiState = try {
        val ticket = withContext(io) { cargarTicket(visitaId) }
        if (ticket == null) {
            telemetry.error(
                code = VisitasTelemetria.CODE_TICKET_VISITA_SIN_VISITA,
                message = "la ruta del ticket apunta a una visita que el telefono no tiene",
                props = emptyMap()
            )
            TicketDeVisitaUiState(cargando = false, error = ErrorDelTicketDeVisita.VISITA_NO_ESTA)
        } else {
            val permiso =
                withContext(io) { impresion.permiso(ticket.visitaId, ticket.registradaEn) }
            TicketDeVisitaUiState(
                cargando = false,
                ticket = ticket,
                permiso = permiso,
                vistaPrevia = vistaPreviaDe(ticket, permiso)
            )
        }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = VisitasTelemetria.CODE_TICKET_VISITA_FALLO,
            message = "no se pudo leer la visita del ticket",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        TicketDeVisitaUiState(cargando = false, error = ErrorDelTicketDeVisita.NO_SE_PUDO_LEER)
    }

    private fun vistaPreviaDe(ticket: TicketDeVisita, permiso: PrintPermission): String =
        TicketDeVisitaFormatter.toTicketText(ticket, permiso)

    /** Mensajes cortos es-MX: 2-4 palabras, minúsculas, sin punto final. */
    private fun mensajeDe(fallo: Throwable): String = when (fallo) {
        is PrintError.BluetoothDisabled -> "activa el bluetooth"
        is PrintError.NotPaired -> "impresora no emparejada"
        is PrintError.PermissionDenied -> "falta permiso bluetooth"
        is PrintError.ConnectionFailed -> "no se pudo conectar"
        is PrintError.WriteFailed -> "no se envió el ticket"
        else -> "no se pudo imprimir"
    }
}
