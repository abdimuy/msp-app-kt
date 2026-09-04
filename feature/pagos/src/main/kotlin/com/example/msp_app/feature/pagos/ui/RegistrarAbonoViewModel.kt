package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.RegistrarAbono
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.VeredictoDelAbono
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La pantalla de registrar abono. **La pantalla del dinero.**
 *
 * Destino de navegación con su propio `SavedStateHandle`, igual que los
 * detalles de las Tasks 16-17 — y aquí el `SavedStateHandle` no es solo por
 * simetría: sostiene las dos cosas que **no pueden** perderse al rotar el
 * teléfono ni al morir el proceso mientras la cámara está encima (Task 22).
 *
 * ## Las tres capas anti-duplicado
 *
 * 1. **[abonoId], acuñado una vez y persistido.** Es el id del pago y la clave
 *    de idempotencia. `NewPaymentDialog` ya la acuña una vez por apertura
 *    (`rememberPaymentIdempotencyKey`) precisamente porque generarla dentro del
 *    manejador del botón daba una clave distinta por toque y la idempotencia
 *    del servidor nunca podía actuar. Aquí vive en el `SavedStateHandle`: un
 *    `remember` muere en la rotación, y con él moría esa protección.
 * 2. **[yaSeEncolo], el guard persistido**, puesto **sincrónicamente antes** de
 *    lanzar la corrutina. Un doble toque rápido no puede colarse entre el
 *    chequeo y el lanzamiento, y un toque después de recrearse el ViewModel
 *    tampoco.
 * 3. **La confirmación en dos pasos.** [confirmar] no escribe nada si no hay
 *    una [ConfirmacionPendiente] viva: no existe camino que registre sin haber
 *    pasado por el paso dos.
 *
 * ## El guard puesto pero sin abono: se resuelve mirando, no adivinando
 *
 * Si el proceso muere entre el toque y la transacción, el guard queda puesto y
 * el abono no existe. Declararlo "ya registrado" perdería dinero de verdad. Por
 * eso [cargar] comprueba el hecho en vez de suponerlo: el historial de la venta
 * —que la carga ya trae— dice si [abonoId] está o no está. Si está, la pantalla
 * queda en su final; si no está, el guard se libera y se emite
 * [PagosTelemetria.CODE_ABONO_GUARD_SIN_ABONO]. Es la regla de control positivo
 * aplicada al dinero: una ausencia no es un hallazgo hasta probar que la
 * consulta habría encontrado la cosa.
 *
 * ## El pago sigue soberano
 *
 * Nada de aquí depende de visitas. La rareza "ya abonó este periodo" se lee del
 * dinero del periodo (`EstadoDelPeriodo.abonoDelPeriodo`), no de una visita.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions"
) // una tecla por gesto del teclado + los dos pasos de la confirmacion; partirla los separaria.
class RegistrarAbonoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cargarDetalleVenta: CargarDetalleVenta,
    private val registrarAbono: RegistrarAbono,
    private val telemetry: Telemetry,
    private val clock: AppClock,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    val ventaId: Int = checkNotNull(savedStateHandle.get<Int>(PagosRutas.ARG_VENTA_ID)) {
        "RegistrarAbonoViewModel sin ${PagosRutas.ARG_VENTA_ID} en el SavedStateHandle"
    }

    /**
     * La clave de idempotencia de ESTE abono. Se acuña una sola vez por
     * instancia de pantalla y sobrevive a la rotación y a la muerte del proceso.
     */
    val abonoId: String = savedStateHandle.get<String>(CLAVE_ABONO_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[CLAVE_ABONO_ID] = it }

    /** El guard anti-duplicado, persistido. Ver el KDoc de la clase. */
    private var yaSeEncolo: Boolean
        get() = savedStateHandle[CLAVE_YA_SE_ENCOLO] ?: false
        set(valor) {
            savedStateHandle[CLAVE_YA_SE_ENCOLO] = valor
        }

    private val mutableState = MutableStateFlow(RegistrarAbonoUiState())
    val state: StateFlow<RegistrarAbonoUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer la venta. Cada llamada es UNA sincronización. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = RegistrarAbonoUiState(cargando = true)
            mutableState.value = leer()
        }
    }

    fun onDigito(digito: Int) = editar { it.conDigito(digito) }

    fun onPunto() = editar { it.conPunto() }

    fun onBorrar() = editar { it.sinUltimo() }

    /** Rellena el teclado desde un chip. El chip nunca ofrece más que el saldo. */
    fun onSugerido(importe: Money) = editar { MontoCapturado.deSugerido(importe) }

    fun onMetodo(metodo: MetodoDeCobro) {
        val actual = mutableState.value
        if (actual.registrado != null || actual.guardando) return
        mutableState.value = actual.copy(metodo = metodo)
    }

    /**
     * **Paso uno.** Congela lo que se va a registrar y levanta la hoja de
     * confirmación. NO escribe: el dinero se mueve solo en [confirmar].
     *
     * Con el monto bloqueado es un no-op — el CTA ya está apagado, y esta
     * guarda es la que hace que también lo sea para cualquier otro llamador.
     */
    fun pedirConfirmacion() {
        val actual = mutableState.value
        if (!actual.sePuedeRegistrar) return
        mutableState.value = actual.copy(
            confirmacion = ConfirmacionPendiente(
                importe = actual.monto.importe,
                metodo = actual.metodo,
                veredicto = actual.veredicto
            ),
            fallo = null
        )
    }

    /** Salir del paso dos sin registrar. No escribe nada, por construcción. */
    fun descartarConfirmacion() {
        mutableState.value = mutableState.value.copy(confirmacion = null)
    }

    /**
     * **Paso dos.** El único camino que escribe dinero.
     *
     * Vuelve a evaluar los bloqueos contra el saldo vigente antes de mover
     * nada: la hoja congeló su veredicto para pintarlo, no para decidir.
     */
    fun confirmar() {
        val actual = mutableState.value
        val venta = actual.venta ?: return
        // Sin paso uno no hay paso dos: nadie registra sin haber confirmado.
        val confirmacion = actual.confirmacion ?: return
        if (SeguridadDelAbono.bloqueosDe(confirmacion.importe, venta.saldo).isNotEmpty()) return
        if (yaSeEncolo || actual.guardando || actual.registrado != null) return
        // Sincrónico y ANTES del launch: ni un doble toque rápido ni un toque
        // sobre el ViewModel recreado pueden colarse entre el chequeo y aquí.
        yaSeEncolo = true
        mutableState.value = actual.copy(guardando = true)
        viewModelScope.launch {
            aplicar(escribir(venta, confirmacion))
        }
    }

    /**
     * ¿El abono está en la base? La MISMA pregunta que hace [resolverGuard], y
     * por la misma razón: el guard no se suelta sobre una suposición.
     *
     * Es TOTAL a propósito — un fallo de lectura no es un "no quedó", es un "no
     * se sabe", y los dos llevan a decisiones opuestas sobre el guard.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de lectura es un "no se sabe", y se reporta con su clase.
    private suspend fun verificar(): Verificacion = try {
        val venta = withContext(io) { cargarDetalleVenta(ventaId) }
        when {
            venta == null -> noSeSupo(porque = "la venta ya no esta en el telefono")
            estaEnElHistorial(venta) -> Verificacion.QUEDO
            else -> Verificacion.NO_QUEDO
        }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        // El error NO se traga: viaja el nombre de la clase de la excepción,
        // nunca su texto (que puede arrastrar datos del cliente).
        noSeSupo(
            porque = "la relectura de la venta fallo",
            excepcion = fallo.javaClass.simpleName
        )
    }

    /**
     * Registra que la comprobación no se pudo hacer y devuelve
     * [Verificacion.NO_SE_PUDO_SABER]. El evento se emite AQUÍ, donde se conoce
     * la causa, y no en el llamador — así hay un solo evento por hecho.
     */
    private fun noSeSupo(porque: String, excepcion: String? = null): Verificacion {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_SIN_VERIFICAR,
            message = "no se pudo comprobar si el abono quedo; el guard se conserva: $porque",
            props = excepcion?.let { mapOf(PagosTelemetria.PROP_EXCEPCION to it) }.orEmpty()
        )
        return Verificacion.NO_SE_PUDO_SABER
    }

    private suspend fun escribir(
        venta: DetalleVenta,
        confirmacion: ConfirmacionPendiente
    ): ResultadoDelAbono = withContext(io) {
        registrarAbono(
            abonoId = abonoId,
            venta = venta,
            importe = confirmacion.importe,
            metodo = confirmacion.metodo
        )
    }

    /**
     * Cierra el registro.
     *
     * **El guard NO se libera por decreto.** Un resultado distinto de
     * [ResultadoDelAbono.REGISTRADO] dice que el puerto no pudo confirmar, no
     * que la base quedó limpia: el proceso puede haber muerto después del commit
     * y antes de que nadie viera el resultado. Antes de soltar el guard se
     * comprueba el hecho — es la misma regla de control positivo que aplica
     * [resolverGuard] al entrar.
     */
    private suspend fun aplicar(resultado: ResultadoDelAbono) {
        if (resultado == ResultadoDelAbono.REGISTRADO) {
            terminarComoRegistrado()
            return
        }
        when (verificar()) {
            Verificacion.QUEDO -> {
                // El puerto reportó fallo pero el dinero SÍ quedó. Soltar el
                // guard aquí sería ofrecer un segundo cobro por el mismo abono.
                telemetry.error(
                    code = PagosTelemetria.CODE_ABONO_FALLO_PERO_SI_QUEDO,
                    message = "el puerto reporto fallo pero el abono esta en el historial",
                    props = mapOf(PagosTelemetria.PROP_RESULTADO to resultado.name)
                )
                terminarComoRegistrado()
            }

            Verificacion.NO_QUEDO -> {
                // Comprobado: no hay abono. Se libera el guard y el reintento va
                // con la MISMA clave, así que no puede volverse un segundo cobro.
                yaSeEncolo = false
                reportarQueNoQuedo(resultado)
                terminarConFallo(falloDe(resultado))
            }

            Verificacion.NO_SE_PUDO_SABER -> {
                // El guard SE QUEDA PUESTO. Al volver a entrar, `resolverGuard`
                // resuelve la duda mirando el historial; soltarlo aquí sin saber
                // es exactamente la suposición que este diseño evita. El evento
                // de "no se pudo saber" ya lo emitió `verificar`, con su causa.
                reportarQueNoQuedo(resultado)
                terminarConFallo(FalloDelAbono.NO_SE_PUDO_VERIFICAR)
            }
        }
    }

    private fun terminarComoRegistrado() {
        mutableState.value = mutableState.value.copy(
            guardando = false,
            confirmacion = null,
            fallo = null,
            registrado = abonoId
        )
    }

    private fun terminarConFallo(fallo: FalloDelAbono) {
        mutableState.value = mutableState.value.copy(
            guardando = false,
            confirmacion = null,
            fallo = fallo
        )
    }

    /**
     * El evento de la PANTALLA. Lleva código propio y no el del adaptador
     * ([PagosTelemetria.CODE_ABONO_NO_SE_GUARDO]): emitir los dos con el mismo
     * código contaría una sola falla dos veces, y el conteo es justo la señal
     * que la norma de errores existe para producir.
     */
    private fun reportarQueNoQuedo(resultado: ResultadoDelAbono) {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_NO_QUEDO_REGISTRADO,
            message = "el abono no quedo registrado",
            props = mapOf(PagosTelemetria.PROP_RESULTADO to resultado.name)
        )
    }

    /** ¿Está [abonoId] entre los abonos de la venta? */
    private fun estaEnElHistorial(venta: DetalleVenta): Boolean =
        venta.historial.meses.any { mes -> mes.pagos.any { it.pagoId == abonoId } }

    private fun falloDe(resultado: ResultadoDelAbono): FalloDelAbono = when (resultado) {
        ResultadoDelAbono.VENTA_NO_ESTA_EN_EL_TELEFONO -> FalloDelAbono.VENTA_NO_ESTA
        ResultadoDelAbono.SIN_COBRADOR -> FalloDelAbono.SIN_COBRADOR
        ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD -> FalloDelAbono.BLOQUEADO
        else -> FalloDelAbono.NO_SE_PUDO_GUARDAR
    }

    private fun editar(cambio: (MontoCapturado) -> MontoCapturado) {
        val actual = mutableState.value
        // Un abono ya registrado —o uno en vuelo— no se sigue editando.
        if (actual.registrado != null || actual.guardando || actual.confirmacion != null) return
        mutableState.value = conVeredicto(actual.copy(monto = cambio(actual.monto), fallo = null))
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room/Firestore degrada igual; se reporta.
    private suspend fun leer(): RegistrarAbonoUiState = try {
        val venta = withContext(io) { cargarDetalleVenta(ventaId) }
        if (venta == null) {
            RegistrarAbonoUiState(cargando = false, error = ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO)
        } else {
            conVeredicto(
                RegistrarAbonoUiState(
                    cargando = false,
                    venta = venta,
                    sugeridos = MontosSugeridos.de(venta, AppTime.todayInBusinessZone(clock)),
                    monto = montoInicialDe(venta),
                    registrado = resolverGuard(venta)
                )
            )
        }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_VENTA_FALLO,
            message = "no se pudo cargar la venta de la pantalla de abono",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        RegistrarAbonoUiState(cargando = false, error = ErrorDeDetalle.FALLO_LA_CARGA)
    }

    /**
     * El teclado arranca con lo esperado hoy, marcado como sugerido: el caso
     * normal es un toque, y la primera tecla lo reemplaza entero.
     */
    private fun montoInicialDe(venta: DetalleVenta): MontoCapturado {
        val esperado = MontosSugeridos.esperadoHoy(venta)
        return if (esperado > Money.ZERO) MontoCapturado.deSugerido(esperado) else MontoCapturado()
    }

    /**
     * Resuelve el guard contra el hecho. Devuelve el [abonoId] cuando el abono
     * SÍ está en el historial (pantalla en su final), y libera el guard cuando
     * no está — ver el KDoc de la clase.
     */
    private fun resolverGuard(venta: DetalleVenta): String? {
        if (!yaSeEncolo) return null
        if (estaEnElHistorial(venta)) return abonoId
        yaSeEncolo = false
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_GUARD_SIN_ABONO,
            message = "el guard anti-duplicado estaba puesto pero el abono no esta en el historial",
            props = emptyMap()
        )
        return null
    }

    /** Recalcula el veredicto sobre el estado dado. Único lugar que lo hace. */
    private fun conVeredicto(estado: RegistrarAbonoUiState): RegistrarAbonoUiState {
        val venta = estado.venta ?: return estado.copy(veredicto = VeredictoDelAbono.SIN_VENTA)
        return estado.copy(
            veredicto = SeguridadDelAbono.evaluar(
                monto = estado.monto.importe,
                saldo = venta.saldo,
                esperadoHoy = MontosSugeridos.esperadoHoy(venta),
                // Se consume el dinero del periodo YA derivado. No se mira
                // ninguna visita: el pago sigue soberano.
                yaAbonoEstePeriodo = venta.estado.abonoDelPeriodo > Money.ZERO
            )
        )
    }

    /** El resultado de preguntarle a la base si el abono quedó. */
    private enum class Verificacion { QUEDO, NO_QUEDO, NO_SE_PUDO_SABER }

    private companion object {
        const val PANTALLA = "pagos_registrar_abono"

        /** Llaves del `SavedStateHandle`. Sobreviven rotación y muerte de proceso. */
        const val CLAVE_ABONO_ID = "pagos_abono_id"
        const val CLAVE_YA_SE_ENCOLO = "pagos_abono_ya_se_encolo"
    }
}
