package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.speech.domain.EstadoDelDictado
import com.example.msp_app.core.speech.domain.FalloDelDictado
import com.example.msp_app.core.speech.domain.port.DictadoFallido
import com.example.msp_app.core.speech.domain.port.DictadoPort
import com.example.msp_app.core.speech.ui.avisoDe
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.di.VisitasIoDispatcher
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.ReglasDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.domain.model.DestinoDeFoto
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ComprobantesDeVisitaPort
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
import kotlinx.coroutines.flow.update
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
 * 1. **[visitaId], acuñado una vez.** Es la semilla de la captura y, por la
 *    derivación de [IdsDeLaVisita], la clave de idempotencia de cada visita que
 *    escriba. Generarlo dentro del manejador del botón daría uno
 *    distinto por toque, que es exactamente el defecto que el retirado
 *    `NewPaymentDialog` ya había tenido que arreglar del lado del dinero.
 * 2. **[yaSeEncolo], el guard**, puesto **sincrónicamente antes** de lanzar la
 *    corrutina: ni un doble toque rápido ni un toque sobre el ViewModel recreado
 *    pueden colarse entre el chequeo y el lanzamiento.
 * 3. **El destino de la cámara y los comprobantes ya adjuntos** (Task 23). El
 *    destino en particular: si el proceso muere con la cámara encima, el
 *    resultado llega ANTES de que [cargar] resuelva, y en esa ventana el estado
 *    todavía está vacío. Leer el destino del estado ahí tira la foto en silencio
 *    y después repone el destino, con lo que la cámara **se vuelve a abrir**.
 *    Ver [tomarDestinoPendiente] y [conLoCapturado].
 *
 * ## La foto cuelga de la visita y no la puede detener
 *
 * Las tres llamadas al puerto de cámara corren dentro de su propio
 * `catch (Throwable)` con telemetría, ninguna dentro del camino que escribe, y
 * ninguna toca `fallo`, `guardando` ni el guard. Es el mismo trato que la Task 5
 * le dio a la ubicación, y la regla que manda sobre esta tarea entera.
 *
 * ## Nada se serializa en la nota
 *
 * [onNota] escribe texto libre y **nada más**. La fecha va a `PROMESA_FECHA`, el
 * monto a `PROMESA_MONTO_CENTAVOS` y la hora a `CITA_HORA`. Meterlas en la nota
 * —como hacía el retirado `NewVisitDialog` con "La cita ha sido reagendada para
 * el …"— es el defecto que este plan vino a arreglar.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions",
    // Septima dependencia: el puerto de camara (Task 23). Precedente:
    // RegistrarAbonoViewModel y CollectionReportViewModel.
    "LongParameterList"
) // una funcion por control de la captura; agruparlas escondería cuál toca qué campo.
class RegistrarVisitaViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val abrirRegistro: AbrirRegistroDeVisita,
    private val registrarVisita: RegistrarVisita,
    private val camara: ComprobantesDeVisitaPort,
    private val dictado: DictadoPort,
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

    /**
     * **La semilla de esta captura.** Sobrevive rotación y muerte de proceso.
     *
     * Con un desenlace de toda la puerta es, tal cual, el id de la única visita
     * que se escribe. Con un desenlace de cuenta es la raíz de la que
     * [IdsDeLaVisita] deriva un id por cuenta marcada — y por eso se acuña UNA
     * vez y se persiste: derivar desde una semilla nueva en cada toque haría que
     * el reintento escribiera filas nuevas al lado de las que ya están, que es
     * el defecto que el retirado `NewPaymentDialog` ya había tenido que arreglar
     * del lado del dinero.
     */
    val visitaId: String = savedStateHandle.get<String>(CLAVE_VISITA_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[CLAVE_VISITA_ID] = it }

    private var yaSeEncolo: Boolean
        get() = savedStateHandle[CLAVE_YA_SE_ENCOLO] ?: false
        set(valor) {
            savedStateHandle[CLAVE_YA_SE_ENCOLO] = valor
        }

    /**
     * El destino que espera a la cámara, persistido: el proceso puede morir con
     * la cámara encima y la foto tiene que volver con **el id que ya se le
     * acuñó**, o el reintento de subida deja de ser idempotente — y en visitas
     * ni siquiera es eso: el `id_<n>` es un campo REQUERIDO del contrato.
     */
    private var destinoGuardado: DestinoDeFoto?
        get() = savedStateHandle.get<String>(CLAVE_DESTINO)
            ?.let { ComprobantesDeVisita.decodificarDestino(it) }
        set(valor) {
            savedStateHandle[CLAVE_DESTINO] =
                valor?.let { ComprobantesDeVisita.codificarDestino(it) }
        }

    /**
     * Los comprobantes ya adjuntos. La memoria manda y el `SavedStateHandle` es
     * su espejo: se decodifica UNA vez, al construirse el ViewModel, para que un
     * formato viejo no se reporte en cada recomposición.
     */
    private var comprobantes: List<ComprobanteDeVisita> = emptyList()
        set(valor) {
            field = valor
            savedStateHandle[CLAVE_COMPROBANTES] =
                ArrayList(valor.map { ComprobantesDeVisita.codificar(it) })
        }

    /**
     * Hay un destino de cámara en vuelo. **No se persiste**: si el proceso muere
     * antes de que el destino quede guardado, no se acuñó nada que proteger. Es
     * el mismo patrón sincrónico del guard de la visita, sobre un recurso mucho
     * más barato.
     */
    private var pidiendoFoto: Boolean = false

    /**
     * **La escritura ya tomó los comprobantes.** De acá en adelante ninguna foto
     * puede entrar a ESTA visita, por buena que sea.
     *
     * No se persiste: si el proceso muere durante la escritura, el guard
     * [yaSeEncolo] y la recarga deciden, y esta bandera vuelve a `false` con el
     * ViewModel nuevo — que es lo correcto, porque la escritura de ese proceso
     * ya no va a publicar nada.
     */
    private var laEscrituraYaTomoLasFotos: Boolean = false

    private val mutableState = MutableStateFlow(RegistrarVisitaUiState())
    val state: StateFlow<RegistrarVisitaUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(VisitasTelemetria.PANTALLA)
        restaurarComprobantes()
        escucharElDictado()
        revisarElDictado()
        cargar()
    }

    // ── Dictado ─────────────────────────────────────────────────────────────

    /**
     * Enchufa el estado del micrófono al de la pantalla.
     *
     * Un `collect` y no un `combine`: el dictado es un hecho del teléfono y la
     * captura es un hecho del cobrador, y mezclarlos en un solo flujo haría que
     * cada `nivel` del micrófono recompusiera la pantalla entera.
     */
    private fun escucharElDictado() {
        viewModelScope.launch {
            dictado.estado().collect { estado ->
                mutableState.update { it.copy(dictado = estado) }
            }
        }
    }

    /**
     * Pregunta si hoy se puede dictar. Se llama al abrir la pantalla **y cada
     * vez que el cobrador contesta el diálogo del permiso**: el permiso se
     * concede y se revoca fuera de la app, así que una respuesta cacheada al
     * arrancar pintaría un micrófono que ya no puede grabar.
     */
    fun revisarElDictado() {
        viewModelScope.launch {
            val disponible = dictado.disponibilidad()
            mutableState.update {
                it.copy(
                    sePuedeDictar = disponible.sePuedeDictar && disponible.permisoConcedido,
                    // Sin motor se avisa desde el arranque; sin permiso NO, porque
                    // todavía no se le ha pedido a nadie. El aviso del permiso
                    // aparece cuando el cobrador lo niega — ver `onPermisoDeMicrofono`.
                    avisoDelDictado = if (disponible.motor == null) {
                        avisoDe(FalloDelDictado.SIN_MOTOR)
                    } else {
                        it.avisoDelDictado
                    }
                )
            }
        }
    }

    /**
     * El cobrador contestó el diálogo del sistema.
     *
     * Negarlo **no pierde la nota**: se apaga el micrófono, se pinta el aviso
     * ámbar (nunca rojo — no se perdió trabajo) y el campo sigue recibiendo
     * texto escrito a mano. Es la misma regla que "la foto nunca bloquea el
     * guardado", aplicada a la voz.
     */
    fun onPermisoDeMicrofono(concedido: Boolean) {
        if (concedido) {
            mutableState.update { it.copy(avisoDelDictado = null) }
            revisarElDictado()
        } else {
            mutableState.update {
                it.copy(
                    sePuedeDictar = false,
                    avisoDelDictado = avisoDe(FalloDelDictado.SIN_PERMISO)
                )
            }
        }
    }

    /**
     * El botón del micrófono: abre si está cerrado, cierra si está abierto.
     *
     * **El texto se AGREGA al final**, nunca reemplaza: dictar dos veces es
     * dictar dos frases. Y la grabación se guarda en [audioDeLaNota] aunque el
     * texto venga vacío, que es la regla dura de esta tarea — si el motor se
     * equivocó con un apodo, el hecho sigue ahí.
     */
    fun onMicrofono() {
        viewModelScope.launch {
            if (mutableState.value.dictado is EstadoDelDictado.Reposo) {
                abrirElMicrofono()
            } else {
                cerrarElMicrofono()
            }
        }
    }

    private suspend fun abrirElMicrofono() {
        dictado.comenzar().onFailure { error ->
            mutableState.update {
                it.copy(avisoDelDictado = avisoDe((error as? DictadoFallido)?.fallo))
            }
        }
    }

    private suspend fun cerrarElMicrofono() {
        dictado.terminar().fold(
            onSuccess = { terminado ->
                editar { captura -> captura.copy(nota = pegado(captura.nota, terminado.texto)) }
                mutableState.update {
                    it.copy(
                        // `?: it.audioDeLaNota`: una sesión que no produjo
                        // archivo no borra el audio de la sesión anterior.
                        audioDeLaNota = terminado.grabacion ?: it.audioDeLaNota,
                        avisoDelDictado = null
                    )
                }
            },
            onFailure = { error ->
                mutableState.update {
                    it.copy(avisoDelDictado = avisoDe((error as? DictadoFallido)?.fallo))
                }
            }
        )
    }

    /** Quita el audio de ESTA captura. No borra nada que ya se haya guardado. */
    fun quitarAudioDeLaNota() {
        mutableState.update { it.copy(audioDeLaNota = null) }
    }

    /**
     * Vuelve a poner en pie lo capturado antes de que el proceso muriera. Lo que
     * no se pueda leer se descarta —no puede tumbar la pantalla— pero se
     * **cuenta y se reporta**: una foto que desaparece sola es justo lo que la
     * norma de errores prohíbe.
     */
    private fun restaurarComprobantes() {
        val crudos = savedStateHandle.get<ArrayList<String>>(CLAVE_COMPROBANTES).orEmpty()
        val leidos = crudos.mapNotNull { ComprobantesDeVisita.decodificar(it) }
        // Reescribe el handle ya normalizado: lo ilegible no vuelve a leerse ni
        // a contarse en la siguiente muerte de proceso.
        comprobantes = leidos
        // Las miniaturas NO se persisten —son píxeles, no evidencia— así que se
        // vuelven a pedir para lo que sí sobrevivió. Sin esto, una muerte de
        // proceso devolvía la rejilla llena de cuadros sin foto.
        leidos.forEach(::pedirMiniatura)
        val ilegibles = crudos.size - leidos.size
        if (ilegibles > 0) {
            telemetry.error(
                code = VisitasTelemetria.CODE_VISITA_FOTO_ILEGIBLE,
                message = "una entrada de comprobante guardada no se pudo leer; se descarto",
                props = mapOf(VisitasTelemetria.PROP_OCURRENCIAS to ilegibles.toString())
            )
        }
    }

    /** Vuelve a leer el cliente y su recomendación. Cada llamada es UNA lectura. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = conLoCapturado(
                mutableState.value.copy(cargando = true, error = null)
            )
            mutableState.value = conLoCapturado(leer())
        }
    }

    /**
     * Cuelga lo capturado —comprobantes y destino en vuelo— al estado que se va a
     * publicar, leyéndolo del `SavedStateHandle` **en el instante de la
     * asignación**.
     *
     * El instante importa. [leer] construye un estado NUEVO, así que sin esto la
     * recarga borraría las fotos. Y si el destino se leyera antes del
     * `withContext` de la carga y la cámara volviera mientras tanto, esta
     * asignación repondría un destino ya atendido y la pantalla **volvería a
     * abrir la cámara**. Leído aquí, no hay punto de suspensión entre la lectura
     * y la publicación: [tomarDestinoPendiente] no puede colarse entre las dos.
     */
    private fun conLoCapturado(estado: RegistrarVisitaUiState): RegistrarVisitaUiState =
        estado.copy(
            comprobantes = comprobantes,
            miniaturas = mutableState.value.miniaturas,
            intentos = mutableState.value.intentos,
            destinoDeFoto = destinoGuardado
        )

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
            // Las cuentas arrancan marcadas; todo lo demás (fecha, monto, hora)
            // arranca vacío a propósito.
            cuentas = cuentasPorDefecto(resultado)
        )
    }

    /**
     * Con qué cuentas nace un desenlace recién elegido.
     *
     * - **Toda la puerta** ("no estaba", "cita") → ninguna. No hay nada que
     *   elegir: la base propaga el estado a todas las cuentas activas del
     *   cliente y marcar casillas prometería un control que no existe.
     * - **Prometió** → **una**: la cuenta por la que el cobrador entró, y si
     *   entró por el cliente, el ancla (la más vieja). Una promesa lleva una
     *   fecha y un monto; repartirla sería inventar dinero.
     * - **"Vuelvo" y "se negó"** → **todas marcadas**. "No te voy a pagar nada"
     *   es el caso común y desmarcar es para el caso fino; al revés, el cobrador
     *   tendría que marcar tres casillas para capturar lo que el cliente dijo
     *   una sola vez.
     */
    private fun cuentasPorDefecto(resultado: ResultadoDeVisita): Set<Int> {
        val ventas = mutableState.value.contexto?.ventas.orEmpty()
        return when {
            resultado.alcance == VisitScope.CLIENTE -> emptySet()
            resultado.admiteVariasCuentas -> ventas.map { it.ventaId }.toSet()
            else -> setOfNotNull(
                ventaId?.takeIf { entrada -> ventas.any { it.ventaId == entrada } }
                    ?: ventas.minOfOrNull { it.ventaId }
            )
        }
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

    /**
     * Pega lo dictado al final de lo que ya había, con un espacio en medio.
     *
     * Función pura y con su test: el borde que importa es el campo vacío —donde
     * un separador dejaría la nota empezando con un espacio— y el dictado vacío,
     * donde no hay nada que pegar y la nota no se puede tocar.
     */
    private fun pegado(nota: String, dictado: String): String {
        val limpio = dictado.trim()
        if (limpio.isEmpty()) return nota
        if (nota.isBlank()) return limpio
        return "${nota.trimEnd()} $limpio"
    }

    /**
     * Toca la cuenta [ventaId]. **Alterna cuando el desenlace admite varias, y
     * sustituye cuando no**: la forma y el comportamiento no pueden discrepar
     * —una casilla que se desmarca sola sería una mentira, y un botón de opción
     * que acumula, otra—, así que la misma función decide las dos cosas a partir
     * de [ResultadoDeVisita.admiteVariasCuentas].
     */
    fun onCuenta(ventaId: Int) = editar { actual ->
        val resultado = actual.resultado ?: return@editar actual
        if (!resultado.admiteVariasCuentas) return@editar actual.copy(cuentas = setOf(ventaId))
        val cuentas = if (ventaId in actual.cuentas) {
            actual.cuentas - ventaId
        } else {
            actual.cuentas + ventaId
        }
        actual.copy(cuentas = cuentas)
    }

    /**
     * El atajo del encabezado: **todas o ninguna**.
     *
     * "Ninguna" deja el CTA apagado con su razón, y eso es correcto: es el
     * cobrador diciendo "espera, esto no aplica a ninguna" antes de marcar la
     * que sí. Quitarle la opción lo obligaría a desmarcar una por una.
     */
    fun onTodasLasCuentas() = editar { actual ->
        val resultado = actual.resultado ?: return@editar actual
        if (!resultado.admiteVariasCuentas) return@editar actual
        val todas = mutableState.value.contexto?.ventas.orEmpty().map { it.ventaId }.toSet()
        actual.copy(cuentas = if (actual.cuentas.containsAll(todas)) emptySet() else todas)
    }

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

    // --- La foto: cuelga de la visita y NUNCA la detiene -----------------------

    /**
     * **El «+» de la rejilla.** Abre la hoja que pregunta de dónde sale el
     * comprobante.
     *
     * No adjunta nada por sí solo, y eso es el cambio: antes había TRES
     * afordantes para lo mismo —un chip arriba, una pastilla abajo y la lista— y
     * cuando algo fallaba el aviso no decía cuál de los tres había fallado. Ahora
     * hay un solo cuadro que agrega, y la pregunta "¿de dónde?" se hace una vez,
     * explícitamente, en vez de repartirse entre tres botones que no la hacían.
     */
    fun abrirOrigenes() {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar) return
        // Con la rejilla llena el «+» no se pinta, así que esto solo se alcanza
        // desde un test o desde una recomposición a destiempo. Se anota igual en
        // vez de callarse: un afordante que no hace nada y tampoco dice por qué
        // es la mentira que esta pantalla no comete.
        if (actual.espaciosLibres <= 0) {
            anotarIntento(FalloDeLaFoto.YA_NO_CABEN)
            return
        }
        mutableState.value = actual.copy(eligiendoOrigen = true)
    }

    /** Cierra la hoja sin elegir. Tocar el velo hace esto. */
    fun cerrarOrigenes() {
        mutableState.value = mutableState.value.copy(eligiendoOrigen = false)
    }

    /**
     * El cobrador eligió de dónde. La cámara sigue su propio camino —acuña el
     * destino ANTES de salir de la app, que es lo que deja el id a salvo de la
     * muerte del proceso—; las otras dos solo piden abrir el selector del
     * sistema, que devuelve `content://` y no necesita nada acuñado de antemano.
     */
    fun onOrigen(origen: OrigenDeLaFoto) {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar) return
        mutableState.value = actual.copy(eligiendoOrigen = false)
        if (origen == OrigenDeLaFoto.CAMARA) {
            pedirFoto()
        } else {
            mutableState.value = mutableState.value.copy(selectorPedido = origen)
        }
    }

    /**
     * La pantalla ya lanzó el selector. Se suelta la petición para que una
     * recomposición no lo vuelva a abrir.
     *
     * **No se persiste** (a diferencia del destino de la cámara): si el proceso
     * muere con el selector arriba no se acuñó nada que proteger, y reponer la
     * petición al volver reabriría el selector solo. Es el mismo criterio que
     * [pidiendoFoto].
     */
    fun selectorAtendido() {
        mutableState.value = mutableState.value.copy(selectorPedido = null)
    }

    /**
     * **Paso uno de la foto:** prepara el destino y pide abrir la cámara.
     *
     * No abre nada por sí mismo — deja [RegistrarVisitaUiState.destinoDeFoto] y
     * la pantalla lanza el intent. Así el ViewModel no importa `android.*` y el
     * camino se puede probar sin Robolectric.
     *
     * [pidiendoFoto] se pone **sincrónicamente antes** del `launch`, igual que
     * el guard de la visita: dos toques rápidos no pueden acuñar dos destinos,
     * que es la forma de dejar un archivo huérfano por cada toque de más.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // preparar el destino toca disco y FileProvider; la visita no se entera.
    private fun pedirFoto() {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar || actual.destinoDeFoto != null || pidiendoFoto) return
        if (actual.espaciosLibres <= 0) {
            anotarIntento(FalloDeLaFoto.YA_NO_CABEN)
            return
        }
        pidiendoFoto = true
        viewModelScope.launch {
            try {
                val destino = withContext(io) { camara.nuevoDestino() }
                destinoGuardado = destino
                mutableState.value = mutableState.value.copy(destinoDeFoto = destino)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (fallo: Throwable) {
                reportarFalloDeFoto(fallo, "no se pudo preparar el destino de la foto")
            } finally {
                pidiendoFoto = false
            }
        }
    }

    /**
     * **Paso dos de la foto:** la cámara escribió. Comprime, valida el tipo y
     * adjunta.
     *
     * Un tipo fuera de la whitelist del servidor se descarta **aquí**, con su
     * archivo: guardarlo solo aplazaría un **422 de la visita entera**
     * (`parseImagenesFromForm` corta el request al primer MIME no permitido)
     * hasta un momento en que el teléfono ya no la tendría a mano. Es la misma
     * consecuencia que tiene en pagos —Huma rechaza allá el request completo por
     * el tag `contentType`—, y no la diferencia entre las dos que una versión
     * anterior de este comentario afirmaba.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // comprimir puede reventar hasta con un OOM; la visita no se entera.
    fun fotoTomada() {
        val destino = tomarDestinoPendiente() ?: return reportarFotoSinDestino()
        viewModelScope.launch {
            try {
                val comprobante = withContext(io) { camara.aceptar(destino) }
                recibir(comprobante)
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (fallo: Throwable) {
                reportarFalloDeFoto(fallo, "no se pudo procesar la foto tomada")
            }
        }
    }

    /** La cámara volvió sin foto (cancelada, o fallida). Se limpia el crudo vacío. */
    fun fotoCancelada() {
        val destino = tomarDestinoPendiente() ?: return
        borrarArchivo(destino.archivoCrudo)
    }

    /**
     * **Lo que el selector del sistema devolvió**: de la galería pueden venir
     * varias de un tirón, del explorador viene una.
     *
     * Se importan **una por una y en orden**, y cada una decide sola: la que no
     * cabe se anota como [FalloDeLaFoto.YA_NO_CABEN], la que el servidor no
     * aceptaría como [FalloDeLaFoto.TIPO_NO_PERMITIDO], y las dos se pintan en
     * SU cuadro. Elegir cuatro con el hueco para dos deja dos fotos y dos cuadros
     * ámbar, que es exactamente lo que pasó; un aviso suelto habría dicho una
     * sola cosa sobre cuatro archivos distintos.
     *
     * Una lista vacía —el cobrador salió del selector sin elegir— no hace nada y
     * **no es un error**: cancelar no es fallar.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // copiar y comprimir tocan disco y ContentResolver; la visita no se entera.
    fun archivosElegidos(uris: List<String>) {
        if (uris.isEmpty()) return
        if (!mutableState.value.sePuedeCapturar) return
        viewModelScope.launch {
            uris.forEach { uri ->
                if (mutableState.value.espaciosLibres <= 0) {
                    anotarIntento(FalloDeLaFoto.YA_NO_CABEN)
                    return@forEach
                }
                try {
                    recibir(withContext(io) { camara.importar(uri) })
                } catch (cancelada: CancellationException) {
                    throw cancelada
                } catch (fallo: Throwable) {
                    reportarFalloDeFoto(fallo, "no se pudo importar el archivo elegido")
                }
            }
        }
    }

    /**
     * Un comprobante recién llegado —de la cámara o del selector— pasa por el
     * MISMO filtro. Un archivo bueno no puede depender de por dónde entró.
     */
    private suspend fun recibir(comprobante: ComprobanteDeVisita) {
        if (ComprobantesDeVisita.permitido(comprobante.mime)) {
            adjuntar(comprobante)
        } else {
            rechazarPorTipo(comprobante)
        }
    }

    /**
     * Toma el destino en vuelo **y lo suelta**, sincrónicamente y en un solo
     * paso.
     *
     * ## Por qué el `SavedStateHandle` manda sobre el estado
     *
     * Cuando el proceso muere con la cámara encima, el ViewModel se reconstruye
     * y el resultado de la cámara llega **antes** de que [cargar] resuelva: en
     * esa ventana `state.destinoDeFoto` todavía es `null` aunque el destino
     * exista. Leerlo del estado tiraría la foto ahí, en silencio, y después la
     * carga repondría el destino y la pantalla **reabriría la cámara** — desde
     * afuera, un bucle. El handle no tiene esa ventana: sobrevive intacto.
     *
     * Soltarlo aquí y no en un `finally` es lo que impide la reapertura: el
     * destino desaparece antes de que ninguna corrutina pueda publicarlo de
     * nuevo.
     */
    private fun tomarDestinoPendiente(): DestinoDeFoto? {
        val destino = destinoGuardado ?: mutableState.value.destinoDeFoto ?: return null
        soltarDestino()
        return destino
    }

    /**
     * Llegó una foto sin destino que la reclame. **No se traga**: es la única
     * forma que tiene una foto de perderse en este camino, y perder evidencia en
     * silencio es justo lo que la norma de errores prohíbe.
     */
    private fun reportarFotoSinDestino() {
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_FOTO_SIN_DESTINO,
            message = "la camara devolvio una foto y ya no habia destino que la reclamara",
            props = emptyMap()
        )
    }

    /**
     * Quita un cuadro de la rejilla por su id: una foto puesta —y entonces borra
     * su archivo— o un cuadro ámbar, que no tiene archivo que borrar porque ya
     * se descartó al rechazarlo.
     */
    fun quitarFoto(id: String) {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar) return
        if (actual.intentos.any { it.id == id }) {
            mutableState.value = actual.copy(intentos = actual.intentos.filterNot { it.id == id })
            return
        }
        val quitado = actual.comprobantes.firstOrNull { it.id == id } ?: return
        comprobantes = actual.comprobantes.filterNot { it.id == id }
        mutableState.value = actual.copy(
            comprobantes = comprobantes,
            miniaturas = actual.miniaturas - id
        )
        borrarArchivo(quitado.archivo)
    }

    /**
     * Cuelga la foto ya procesada — **salvo que llegue tarde**.
     *
     * ## La ventana que esto cierra
     *
     * `aceptar()` es asíncrono (comprime), así que entre "la cámara volvió" y
     * "la foto está en el estado" hay un hueco. Si el cobrador toca **guardar**
     * dentro de ese hueco, la escritura ya se llevó su lista y esta foto no
     * entra a `visita_imagenes`, no sube nunca, y —sin esta compuerta— **no deja
     * rastro**: la única forma que tiene una foto BUENA de desaparecer.
     *
     * [guardar] estrecha el hueco leyendo los comprobantes lo más tarde posible
     * (ver su KDoc), así que el caso normal se salva. Lo que queda después de
     * esa lectura es irrecuperable por construcción, y entonces **se reporta y
     * se borra el archivo**: nada lo va a subir, y dejarlo sería una foto
     * ocupando disco que nadie va a entregar.
     */
    private suspend fun adjuntar(comprobante: ComprobanteDeVisita) {
        if (laEscrituraYaTomoLasFotos || mutableState.value.registrada != null) {
            rechazarTardia(comprobante)
            return
        }
        comprobantes = comprobantes + comprobante
        mutableState.value = mutableState.value.copy(comprobantes = comprobantes)
        pedirMiniatura(comprobante)
    }

    /**
     * Pide los píxeles reducidos del comprobante y los cuelga del estado.
     *
     * **Fuera del hilo principal** (`withContext(io)`): decodificar un JPEG es
     * trabajo de disco y de CPU, y hacerlo dentro de la composición congelaría la
     * pantalla una vez por cuadro. Y **fuera del camino que adjunta**: si la
     * decodificación falla, el comprobante YA está adjunto y va a viajar igual —
     * la miniatura es cómo se ve, no si existe. Por eso el fallo no pinta ámbar:
     * no se perdió nada.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // decodificar puede reventar con un OOM; el comprobante ya está adjunto.
    private fun pedirMiniatura(comprobante: ComprobanteDeVisita) {
        viewModelScope.launch {
            try {
                val miniatura = withContext(io) { camara.miniatura(comprobante.archivo) }
                    ?: return@launch
                mutableState.value = mutableState.value.copy(
                    miniaturas = mutableState.value.miniaturas + (comprobante.id to miniatura)
                )
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (fallo: Throwable) {
                telemetry.error(
                    code = VisitasTelemetria.CODE_VISITA_FOTO_SIN_MINIATURA,
                    message = "no se pudo decodificar la miniatura; el comprobante sigue adjunto",
                    props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
                )
            }
        }
    }

    /**
     * La foto llegó después de que la escritura tomó los comprobantes. **No se
     * traga**: código propio, porque no falló nada y aun así la evidencia no
     * existe en ningún lado.
     */
    private suspend fun rechazarTardia(comprobante: ComprobanteDeVisita) {
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_FOTO_TARDE,
            message = "la foto termino de procesarse cuando la escritura ya se llevo la lista",
            props = emptyMap()
        )
        anotarIntento(FalloDeLaFoto.LLEGO_TARDE, comprobante.id)
        descartarArchivoDe(comprobante, "no se pudo borrar la foto que llego tarde")
    }

    private suspend fun rechazarPorTipo(comprobante: ComprobanteDeVisita) {
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_FOTO_TIPO_NO_PERMITIDO,
            message = "el archivo elegido tiene un tipo que el servidor no acepta; no se adjunta",
            // Anti-PII: el MIME es un valor tecnico cerrado, no dato del cliente.
            props = mapOf(VisitasTelemetria.PROP_TIPO to comprobante.mime)
        )
        anotarIntento(FalloDeLaFoto.TIPO_NO_PERMITIDO, comprobante.id)
        // El archivo se va con el rechazo: nada lo va a subir nunca.
        descartarArchivoDe(comprobante, "no se pudo borrar la foto rechazada")
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // borrar un archivo puede fallar por permisos o por FS; se reporta y se sigue.
    private suspend fun descartarArchivoDe(comprobante: ComprobanteDeVisita, porque: String) {
        try {
            camara.descartar(comprobante.archivo)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            reportarFalloDeFoto(fallo, porque, conCuadro = false)
        }
    }

    /**
     * Anota un cuadro ámbar en la rejilla.
     *
     * ## Ámbar, nunca rojo, y el techo de la lista
     *
     * Ninguno de los cuatro motivos impide registrar la visita, y el rojo en esta
     * pantalla ya significa otra cosa: es el color de `BandaDeFallo`, "la visita
     * no quedó registrada". Pintar una foto fallida en rojo diría que se perdió
     * trabajo de campo, y no se perdió.
     *
     * La lista se topa en [ComprobantesDeVisita.MAXIMO] y **conserva los
     * últimos**: sin techo, un cobrador que insiste con la cámara rota llena la
     * rejilla de avisos y pierde de vista las fotos que sí entraron. Los que caen
     * ya quedaron reportados por telemetría, que es donde se cuentan.
     */
    private fun anotarIntento(motivo: FalloDeLaFoto, id: String = UUID.randomUUID().toString()) {
        val intentos = mutableState.value.intentos + IntentoFallido(id = id, motivo = motivo)
        mutableState.value = mutableState.value.copy(
            intentos = intentos.takeLast(ComprobantesDeVisita.MAXIMO)
        )
    }

    /** Suelta el destino en el estado y en el `SavedStateHandle`, a la vez. */
    private fun soltarDestino() {
        destinoGuardado = null
        mutableState.value = mutableState.value.copy(destinoDeFoto = null)
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // idem: el borrado es best-effort y su fallo no vuelve a la pantalla.
    private fun borrarArchivo(archivo: String) {
        viewModelScope.launch {
            try {
                withContext(io) { camara.descartar(archivo) }
            } catch (cancelada: CancellationException) {
                throw cancelada
            } catch (fallo: Throwable) {
                // Sin cuadro en pantalla: el cobrador pidió quitar la foto y la
                // foto se quitó. Lo que quedó fue un archivo en disco, y eso es
                // asunto del que lo tiene que limpiar, no suyo.
                reportarFalloDeFoto(fallo, "no se pudo borrar el archivo local", conCuadro = false)
            }
        }
    }

    /**
     * Un fallo de la capa de fotos. Reporta con el NOMBRE de la clase de la
     * excepción (nunca su texto, que puede arrastrar la ruta o la nota del
     * cobrador) y, si toca, deja su cuadro ámbar.
     *
     * **Nunca toca la visita**: no cambia `fallo`, ni `guardando`, ni el guard.
     */
    private fun reportarFalloDeFoto(fallo: Throwable, porque: String, conCuadro: Boolean = true) {
        telemetry.error(
            code = VisitasTelemetria.CODE_VISITA_FOTO_FALLO,
            message = "$porque; la visita no se ve afectada",
            props = mapOf(VisitasTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        if (conCuadro) anotarIntento(FalloDeLaFoto.NO_SE_PUDO_TOMAR)
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
            // Los comprobantes se leen ACÁ y no en el snapshot del toque: es lo
            // más tarde que se puede leerlos sin que la escritura ya haya
            // empezado, y rescata a la foto cuyo `adjuntar` quedó encolado
            // justo antes del toque. NO rescata a la que sigue comprimiéndose
            // —esperarla sería que la foto bloquee el guardado— y por eso
            // `adjuntar` la reporta en vez de tragársela.
            val conLasFotosDeAhora = actual.copy(comprobantes = mutableState.value.comprobantes)
            // Y desde este instante la lista está tomada: lo que llegue después
            // no entra, y `adjuntar` lo reporta en vez de tragárselo.
            laEscrituraYaTomoLasFotos = true
            aplicar(escribir(conLasFotosDeAhora), conLasFotosDeAhora.captura)
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
                recomendacionId = actual.recomendacion?.recomendacionId,
                // Del ESTADO, no del campo: es el mismo estado que el guard ya
                // congeló al empezar a guardar, así que lo que se escribe es
                // exactamente lo que la pantalla enseñaba.
                comprobantes = actual.comprobantes
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
    private fun aplicar(resultado: ResultadoDelRegistro, captura: CapturaDeVisita) {
        if (resultado == ResultadoDelRegistro.REGISTRADA) {
            mutableState.value = mutableState.value.copy(
                guardando = false,
                fallo = null,
                // El ANCLA, no la semilla. Con varias cuentas la semilla no
                // nombra ninguna fila —los N ids se derivan de ella—, así que
                // navegar con ella abriría un ticket de una visita que no
                // existe. Lo calcula la MISMA función que acuñó los ids.
                registrada = IdsDeLaVisita.ancla(visitaId, captura.cuentas)
            )
            return
        }
        yaSeEncolo = false
        // Nada quedó escrito, así que la lista vuelve a estar abierta: el
        // reintento tiene que poder llevarse las fotos que se sigan adjuntando.
        laEscrituraYaTomoLasFotos = false
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
            val captura = CapturaDeVisita()
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
                laEscrituraYaTomoLasFotos = false
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
        const val CLAVE_DESTINO = "visitas_destino_de_foto"
        const val CLAVE_COMPROBANTES = "visitas_comprobantes"
    }
}
