package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.di.VisitasIoDispatcher
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalTime
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
 * Registrar visita. **Aquí nacen los dos datos que hoy no existen**: la promesa
 * estructurada (venta + fecha + monto) y la cita con hora.
 *
 * Destino de navegación con su propio `SavedStateHandle`, igual que las
 * Tasks 16-18 — y no por simetría: la cámara (Task 23) manda al usuario fuera de
 * la app y el proceso puede morir mientras tanto. Un destino con
 * `SavedStateHandle` vuelve con su argumento y con [visitaId] intactos; un
 * diálogo alojado en la composición del llamador no vuelve en absoluto.
 *
 * ## Lo que el `SavedStateHandle` sostiene
 *
 * 1. **[visitaId], acuñado una vez.** Es el id de la visita y la clave de
 *    idempotencia del envío. Generarlo dentro del manejador del botón daría uno
 *    distinto por toque, que es exactamente el defecto que `NewPaymentDialog` ya
 *    había tenido que arreglar del lado del dinero.
 * 2. **[yaSeEncolo], el guard**, puesto **sincrónicamente antes** de lanzar la
 *    corrutina: ni un doble toque rápido ni un toque sobre el ViewModel recreado
 *    pueden colarse entre el chequeo y el lanzamiento.
 *
 * ## Nada se serializa en la nota
 *
 * [onNota] escribe texto libre y **nada más**. La fecha va a `PROMESA_FECHA`, el
 * monto a `PROMESA_MONTO_CENTAVOS` y la hora a `CITA_HORA`. Meterlas en la nota
 * —como hace hoy `NewVisitDialog` con "La cita ha sido reagendada para el …"— es
 * el defecto que este plan vino a arreglar.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions"
) // una funcion por control de la captura; agruparlas escondería cuál toca qué campo.
class RegistrarVisitaViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val abrirRegistro: AbrirRegistroDeVisita,
    private val registrarVisita: RegistrarVisita,
    private val telemetry: Telemetry,
    private val clock: AppClock,
    @VisitasIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    val clienteId: Int = checkNotNull(savedStateHandle.get<Int>(VisitasRutas.ARG_CLIENTE_ID)) {
        "RegistrarVisitaViewModel sin ${VisitasRutas.ARG_CLIENTE_ID} en el SavedStateHandle"
    }

    /** La cuenta que el cobrador tenía abierta. `0` = entró por el cliente. */
    val ventaId: Int? = savedStateHandle.get<Int>(VisitasRutas.ARG_VENTA_ID)
        ?.takeIf { it != VisitasRutas.SIN_VENTA }

    /** El id de ESTA visita. Sobrevive rotación y muerte de proceso. */
    val visitaId: String = savedStateHandle.get<String>(CLAVE_VISITA_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[CLAVE_VISITA_ID] = it }

    private var yaSeEncolo: Boolean
        get() = savedStateHandle[CLAVE_YA_SE_ENCOLO] ?: false
        set(valor) {
            savedStateHandle[CLAVE_YA_SE_ENCOLO] = valor
        }

    private val mutableState = MutableStateFlow(RegistrarVisitaUiState())
    val state: StateFlow<RegistrarVisitaUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(VisitasTelemetria.PANTALLA)
        cargar()
    }

    /** Vuelve a leer el cliente y su recomendación. Cada llamada es UNA lectura. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(cargando = true, error = null)
            mutableState.value = leer()
        }
    }

    /**
     * Elige el desenlace. **Limpia lo que el desenlace anterior había capturado**
     * (la venta, la fecha, el monto, la hora): arrastrar una promesa a un
     * "no estaba" escribiría un compromiso que el cliente no hizo.
     *
     * La etiqueta arranca en la primera de su grupo, así que no existe un estado
     * intermedio "resultado elegido, sin literal que escribir".
     */
    fun onResultado(resultado: ResultadoDeVisita) = editar { actual ->
        CapturaDeVisita(
            resultado = resultado,
            etiqueta = CatalogoDeResultados.etiquetaPorDefecto(resultado),
            nota = actual.nota,
            // La venta por la que se entró se conserva como destino sugerido de
            // la promesa: es la cuenta que el cobrador tenía abierta. Todo lo
            // demás (fecha, monto, hora) arranca vacío a propósito.
            ventaDeLaPromesa = ventaId
        )
    }

    /**
     * Vuelve a la lista de los cinco desenlaces, y **borra lo capturado bajo el
     * anterior** por la misma razón que [onResultado]: un compromiso arrastrado
     * es un compromiso que el cliente no hizo.
     */
    fun limpiarResultado() = editar { CapturaDeVisita(nota = it.nota) }

    /** Elige la etiqueta dentro del desenlace. Una ajena al grupo se ignora. */
    fun onEtiqueta(etiqueta: String) = editar { actual ->
        val resultado = actual.resultado ?: return@editar actual
        if (!CatalogoDeResultados.perteneceA(resultado, etiqueta)) {
            actual
        } else {
            actual.copy(etiqueta = etiqueta)
        }
    }

    /** Texto libre. Nunca lleva la fecha ni la hora dentro. */
    fun onNota(nota: String) = editar { it.copy(nota = nota) }

    /** Sobre cuál venta prometió. `null` = sobre el cliente completo. */
    fun onVentaDeLaPromesa(ventaId: Int?) = editar { it.copy(ventaDeLaPromesa = ventaId) }

    fun onFechaPromesa(fecha: LocalDate) = editar {
        it.copy(fechaPromesa = fecha)
    }

    /** El monto prometido. `null` = "dijo cuándo pero no cuánto", que es legítimo. */
    fun onMontoPrometido(monto: Money?) = editar { it.copy(montoPrometido = monto) }

    fun onFechaCita(fecha: LocalDate) = editar { it.copy(fechaCita = fecha) }

    /** La hora de la cita. `null` = "otro día sin hora", el tercer caso del mock. */
    fun onHoraCita(hora: LocalTime?) = editar { it.copy(horaCita = hora) }

    /** Abre el calendario de "otro día". No captura nada por sí solo. */
    fun abrirCalendario() {
        if (!mutableState.value.sePuedeCapturar) return
        mutableState.value = mutableState.value.copy(eligiendoDia = true)
    }

    fun cerrarCalendario() {
        mutableState.value = mutableState.value.copy(eligiendoDia = false)
    }

    /** Abre el reloj de "otra hora". */
    fun abrirReloj() {
        if (!mutableState.value.sePuedeCapturar) return
        mutableState.value = mutableState.value.copy(eligiendoHora = true)
    }

    fun cerrarReloj() {
        mutableState.value = mutableState.value.copy(eligiendoHora = false)
    }

    /**
     * Elige el día desde el calendario: cae en la promesa o en la cita según el
     * desenlace vigente, y cierra el diálogo. Un solo camino para las dos, así
     * que no pueden divergir.
     */
    fun onDiaDelCalendario(fecha: LocalDate) {
        when (mutableState.value.captura.resultado) {
            ResultadoDeVisita.PROMETIO -> onFechaPromesa(fecha)
            ResultadoDeVisita.CITA -> onFechaCita(fecha)
            else -> Unit
        }
        cerrarCalendario()
    }

    /** Elige la hora desde el reloj y cierra el diálogo. */
    fun onHoraDelReloj(hora: LocalTime) {
        onHoraCita(hora)
        cerrarReloj()
    }

    /**
     * Registra la visita. **El único camino que escribe.**
     *
     * El guard se pone sincrónicamente y antes del `launch`; los bloqueos se
     * vuelven a evaluar aquí y otra vez dentro del caso de uso, con la MISMA
     * función.
     */
    fun guardar() {
        val actual = mutableState.value
        if (!actual.sePuedeGuardar) return
        if (yaSeEncolo) return
        yaSeEncolo = true
        mutableState.value = actual.copy(guardando = true, fallo = null)
        viewModelScope.launch {
            aplicar(escribir(actual))
        }
    }

    private suspend fun escribir(actual: RegistrarVisitaUiState): ResultadoDelRegistro =
        withContext(io) {
            registrarVisita(
                visitaId = visitaId,
                clienteId = clienteId,
                ventaId = ventaId,
                captura = actual.captura,
                hoy = actual.hoy,
                recomendacionId = actual.recomendacion?.recomendacionId
            )
        }

    /**
     * Cierra el registro.
     *
     * Un fallo **libera el guard**: a diferencia del abono, aquí nada de dinero
     * se movió, la escritura es una transacción que no dejó nada, y el reintento
     * va con el MISMO [visitaId] —así que ni siquiera un guardado que sí hubiera
     * quedado podría convertirse en una segunda visita: el `INSERT` es
     * `REPLACE` sobre la misma llave.
     */
    private fun aplicar(resultado: ResultadoDelRegistro) {
        if (resultado == ResultadoDelRegistro.REGISTRADA) {
            mutableState.value = mutableState.value.copy(
                guardando = false,
                fallo = null,
                registrada = visitaId
            )
            return
        }
        yaSeEncolo = false
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO,
            message = "la visita no quedo registrada",
            props = mapOf(VisitasTelemetria.PROP_RESULTADO to resultado.name)
        )
        mutableState.value = mutableState.value.copy(
            guardando = false,
            fallo = falloDe(resultado)
        )
    }

    private fun falloDe(resultado: ResultadoDelRegistro): FalloDeLaVisita = when (resultado) {
        ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO -> FalloDeLaVisita.CLIENTE_NO_ESTA
        ResultadoDelRegistro.SIN_COBRADOR -> FalloDeLaVisita.SIN_COBRADOR
        else -> FalloDeLaVisita.NO_SE_PUDO_GUARDAR
    }

    /**
     * Aplica [cambio] y recalcula los bloqueos. **Único** lugar que toca la
     * captura, así que no existe un camino que la cambie sin volver a evaluar si
     * se puede guardar.
     */
    private fun editar(cambio: (CapturaDeVisita) -> CapturaDeVisita) {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar) return
        val captura = cambio(actual.captura)
        mutableState.value = actual.copy(
            captura = captura,
            bloqueos = ReglasDeLaVisita.bloqueosDe(captura, actual.hoy),
            fallo = null
        )
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room degrada igual; se reporta con su clase.
    private suspend fun leer(): RegistrarVisitaUiState = try {
        val hoy = AppTime.todayInBusinessZone(clock)
        val apertura = withContext(io) { abrirRegistro(clienteId) }
        val contexto = apertura.contexto
        if (contexto == null) {
            RegistrarVisitaUiState(cargando = false, error = ErrorDeLaVisita.CLIENTE_NO_ESTA)
        } else {
            val captura = CapturaDeVisita(ventaDeLaPromesa = ventaId)
            RegistrarVisitaUiState(
                cargando = false,
                contexto = contexto,
                recomendacion = apertura.recomendacion,
                captura = captura,
                hoy = hoy,
                bloqueos = ReglasDeLaVisita.bloqueosDe(captura, hoy)
            ).also {
                // El guard se SUELTA al recargar, y esto no es un descuido.
                //
                // Vale para el abono no soltarlo sin comprobar, porque allá un
                // reintento sería un segundo cobro. Aquí no hay dinero y la
                // escritura es idempotente por construcción: el `INSERT` es
                // `REPLACE` sobre `Visit.ID`, y [visitaId] sobrevive en el
                // `SavedStateHandle`, así que reintentar reescribe la MISMA
                // fila — nunca una segunda visita. Entre "el cobrador vuelve y
                // puede reintentar" y "la pantalla declara registrado algo que
                // quizá no se escribió", la primera es la que no pierde trabajo
                // de campo.
                yaSeEncolo = false
            }
        }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = VisitasTelemetria.CODE_CONTEXTO_FALLO,
            message = "no se pudo cargar el cliente de la pantalla de visita",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        RegistrarVisitaUiState(cargando = false, error = ErrorDeLaVisita.FALLO_LA_CARGA)
    }

    private companion object {
        const val CLAVE_VISITA_ID = "visitas_visita_id"
        const val CLAVE_YA_SE_ENCOLO = "visitas_ya_se_encolo"
    }
}
