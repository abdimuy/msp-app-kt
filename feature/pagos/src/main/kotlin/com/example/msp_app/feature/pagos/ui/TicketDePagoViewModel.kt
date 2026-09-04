package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.printing.application.ImpresionFueraDelDia
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.TicketPrinting
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarTicketDePago
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.model.TicketDePago
import com.example.msp_app.feature.pagos.printing.TicketDePagoFormatter
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
 * El ticket de pago: lo arma, lo muestra y lo imprime.
 *
 * ## Las dos mitades de la regla del mock
 *
 * 1. **Solo se imprime el día del cobro.** Esta pantalla usa
 *    `TicketPrinting.permiso` para PINTARSE —apagar el CTA, decir si el
 *    siguiente papel sería copia— y ese veredicto vive en
 *    [TicketDePagoUiState.permiso]. Pero **lo que hace cierta la regla no es el
 *    botón**: la comprobación de verdad ocurre dentro de `PrintTicketUseCase`,
 *    con una lectura FRESCA del reloj en el instante de imprimir. Un veredicto
 *    leído al abrir la pantalla está rancio en cuanto se lee — una pantalla
 *    abierta a las 23:58 y tocada a las 00:01 imprimía fuera del día con el
 *    diseño anterior.
 * 2. **Cada impresión queda registrada.** Nadie llama a `PrinterPort.print`
 *    desde aquí: se llama a `TicketPrinting.imprimir`, que imprime **y** registra en
 *    la misma operación, así que no existe un camino que imprima sin registrar.
 *    Tras imprimir, el permiso se vuelve a evaluar y la pantalla —y el papel
 *    siguiente— pasan a decir "reimpresión".
 *
 * Destino de navegación con su propio `SavedStateHandle`, igual que las Tasks
 * 16-19: la ruta lleva SOLO el `pagoId`, así que la pantalla vuelve entera
 * después de una rotación o de que el proceso muera con el picker de Bluetooth
 * encima.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions"
) // una funcion por control del flujo de impresion; agruparlas escondería cuál toca qué fase.
class TicketDePagoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarTicket: CargarTicketDePago,
    private val impresion: TicketPrinting,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    val pagoId: String = checkNotNull(savedStateHandle.get<String>(PagosRutas.ARG_PAGO_ID)) {
        "TicketDePagoViewModel sin ${PagosRutas.ARG_PAGO_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(TicketDePagoUiState())
    val state: StateFlow<TicketDePagoUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer el abono y a evaluar el permiso. Cada llamada es UNA lectura. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(cargando = true, error = null)
            mutableState.value = leer()
        }
    }

    /**
     * Imprime. Si hay una impresora recordada y sigue emparejada imprime directo;
     * si no, abre el picker. Un `FueraDelDia` **no llega aquí** —el CTA está
     * apagado— y aun así se vuelve a comprobar: la regla del día es la razón de
     * ser de la pantalla y no puede depender de que un botón esté bien pintado.
     */
    fun imprimir() {
        val actual = mutableState.value
        if (!actual.sePuedeImprimir) return
        telemetry.tap(PANTALLA, "imprimir")
        viewModelScope.launch {
            enFase(FaseDeImpresion.IMPRIMIENDO)
            val emparejadas = withContext(io) { impresion.emparejadas() }.getOrElse {
                reportarFallo(it, destino = null, disponibles = emptyList())
                return@launch
            }
            val recordada = withContext(io) { impresion.recordada(emparejadas) }
            if (recordada == null) {
                mutableState.value = mutableState.value.copy(
                    impresion = ImpresionUi(
                        fase = FaseDeImpresion.ELIGIENDO,
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
        telemetry.tap(PANTALLA, "cambiar_impresora")
        viewModelScope.launch {
            val previa = mutableState.value.impresion
            val emparejadas = withContext(io) { impresion.emparejadas() }.getOrElse {
                reportarFallo(it, destino = previa.impresora, disponibles = emptyList())
                return@launch
            }
            mutableState.value = mutableState.value.copy(
                impresion = ImpresionUi(
                    fase = FaseDeImpresion.ELIGIENDO,
                    impresora = previa.impresora,
                    disponibles = emparejadas
                )
            )
        }
    }

    /** Elige impresora, la recuerda para la próxima e imprime en ella. */
    fun elegirImpresora(dispositivo: PrinterDevice) {
        if (!mutableState.value.sePuedeImprimir) return
        telemetry.tap(PANTALLA, "elegir_impresora")
        val disponibles = mutableState.value.impresion.disponibles.ifEmpty { listOf(dispositivo) }
        viewModelScope.launch {
            withContext(io) { impresion.recordar(dispositivo) }
            imprimirEn(dispositivo, disponibles)
        }
    }

    /** Cierra el picker o el aviso de fallo sin salir de la pantalla. */
    fun cerrarImpresion() {
        mutableState.value = mutableState.value.copy(
            impresion = ImpresionUi(impresora = mutableState.value.impresion.impresora)
        )
    }

    /**
     * Manda el ticket y, si sale, **vuelve a evaluar el permiso**: el papel
     * siguiente ya es una copia y la pantalla lo tiene que decir. La vista previa
     * se rearma con el nuevo permiso, así que lo que se ve en pantalla y lo que
     * saldría del rodillo son el mismo contenido.
     */
    private suspend fun imprimirEn(dispositivo: PrinterDevice, disponibles: List<PrinterDevice>) {
        val actual = mutableState.value
        val ticket = actual.ticket ?: return
        val permiso = actual.permiso ?: return
        mutableState.value = actual.copy(
            impresion = ImpresionUi(
                fase = FaseDeImpresion.IMPRIMIENDO,
                impresora = dispositivo,
                disponibles = disponibles
            )
        )
        val lineas = TicketDePagoFormatter.toTicketLines(ticket, permiso)
        val resultado = withContext(io) {
            impresion.imprimir(
                device = dispositivo,
                ticketId = ticket.pagoId,
                cobradoEn = ticket.cobradoEn,
                ticket = lineas
            )
        }
        resultado.fold(
            onSuccess = {
                telemetry.tap(PANTALLA, "impreso")
                val nuevoPermiso = withContext(io) {
                    impresion.permiso(ticket.pagoId, ticket.cobradoEn)
                }
                mutableState.value = mutableState.value.copy(
                    permiso = nuevoPermiso,
                    vistaPrevia = vistaPreviaDe(ticket, nuevoPermiso),
                    impresion = ImpresionUi(
                        fase = FaseDeImpresion.IMPRESO,
                        impresora = dispositivo,
                        disponibles = disponibles
                    )
                )
            },
            onFailure = { fallo ->
                // El rechazo por día NO deja la pantalla ofreciendo imprimir: se
                // vuelve a leer el permiso, la banda pasa a "fuera del día" y el
                // CTA se apaga. Es el único camino por el que la pantalla puede
                // enterarse de que el día cambió mientras estaba abierta.
                if (fallo === ImpresionFueraDelDia) {
                    val vencido = withContext(io) {
                        impresion.permiso(ticket.pagoId, ticket.cobradoEn)
                    }
                    mutableState.value = mutableState.value.copy(
                        permiso = vencido,
                        vistaPrevia = vistaPreviaDe(ticket, vencido)
                    )
                }
                reportarFallo(fallo, dispositivo, disponibles)
            }
        )
    }

    private fun enFase(fase: FaseDeImpresion) {
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
            code = if (fallo === ImpresionFueraDelDia) {
                PagosTelemetria.CODE_TICKET_PAGO_FUERA_DEL_DIA
            } else {
                PagosTelemetria.CODE_TICKET_PAGO_NO_SE_IMPRIMIO
            },
            message = "el ticket de pago no se imprimio",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        mutableState.value = mutableState.value.copy(
            impresion = ImpresionUi(
                fase = FaseDeImpresion.FALLO,
                impresora = destino,
                disponibles = disponibles,
                mensaje = mensajeDe(fallo)
            )
        )
    }

    @Suppress("TooGenericExceptionCaught") // cualquier fallo de Room degrada igual; se reporta.
    private suspend fun leer(): TicketDePagoUiState = try {
        val ticket = withContext(io) { cargarTicket(pagoId) }
        if (ticket == null) {
            telemetry.error(
                code = PagosTelemetria.CODE_TICKET_PAGO_SIN_ABONO,
                message = "la ruta del ticket apunta a un abono que el telefono no tiene",
                props = emptyMap()
            )
            TicketDePagoUiState(cargando = false, error = ErrorDelTicket.PAGO_NO_ESTA)
        } else {
            val permiso = withContext(io) { impresion.permiso(ticket.pagoId, ticket.cobradoEn) }
            TicketDePagoUiState(
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
            code = PagosTelemetria.CODE_TICKET_PAGO_FALLO,
            message = "no se pudo leer el abono del ticket",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        TicketDePagoUiState(cargando = false, error = ErrorDelTicket.NO_SE_PUDO_LEER)
    }

    private fun vistaPreviaDe(ticket: TicketDePago, permiso: PrintPermission): String =
        TicketDePagoFormatter.toTicketText(ticket, permiso)

    /** Mensajes cortos es-MX: 2-4 palabras, minúsculas, sin punto final. */
    private fun mensajeDe(fallo: Throwable): String = when (fallo) {
        ImpresionFueraDelDia -> "ya no es el día"
        is PrintError.BluetoothDisabled -> "activa el bluetooth"
        is PrintError.NotPaired -> "impresora no emparejada"
        is PrintError.PermissionDenied -> "falta permiso bluetooth"
        is PrintError.ConnectionFailed -> "no se pudo conectar"
        is PrintError.WriteFailed -> "no se envió el ticket"
        else -> "no se pudo imprimir"
    }

    private companion object {
        const val PANTALLA = "pagos_ticket"
    }
}
