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
import com.example.msp_app.feature.pagos.domain.Comprobantes
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.SeguridadDelAbono
import com.example.msp_app.feature.pagos.domain.VeredictoDelAbono
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.domain.model.DestinoDeFoto
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.ComprobantesPort
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
 *
 * ## La foto cuelga del abono y no lo puede detener (Task 22)
 *
 * Los comprobantes viven en el `SavedStateHandle` por la misma razón que las
 * otras dos cosas: **la cámara manda al cobrador fuera de la app** y el proceso
 * puede morir mientras tanto. Cada llamada al puerto de la cámara va dentro de
 * su `catch (Throwable)` con telemetría, ninguna corre dentro del camino que
 * escribe dinero, y ningún fallo suyo escribe en `fallo`, en `guardando` ni en
 * el guard: la regla que manda sobre esta tarea es que **la foto nunca bloquea
 * el guardado**.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions",
    // Siete dependencias: las seis de la Task 18 mas el puerto de la camara.
    // Agruparlas en un holder solo escondería el wiring —mismo criterio que
    // `CollectionReportViewModel`—, y el puerto no puede vivir en otro tipo
    // inyectado sin partir en dos el estado único de la pantalla.
    "LongParameterList"
) // una tecla por gesto del teclado + los dos pasos de la confirmacion; partirla los separaria.
class RegistrarAbonoViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val cargarDetalleVenta: CargarDetalleVenta,
    private val registrarAbono: RegistrarAbono,
    private val camara: ComprobantesPort,
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

    /**
     * El destino que espera a la cámara, persistido: el proceso puede morir con
     * la cámara encima y la foto tiene que volver con **el id que ya se le
     * acuñó**, o el reintento de subida deja de ser idempotente.
     */
    private var destinoGuardado: DestinoDeFoto?
        get() = savedStateHandle.get<String>(CLAVE_DESTINO)
            ?.let { Comprobantes.decodificarDestino(it) }
        set(valor) {
            savedStateHandle[CLAVE_DESTINO] = valor?.let { Comprobantes.codificarDestino(it) }
        }

    /**
     * Los comprobantes ya adjuntos. La memoria manda y el `SavedStateHandle` es
     * su espejo: se decodifica UNA vez, al construirse el ViewModel, para que
     * un formato viejo no se reporte en cada recomposición.
     */
    private var comprobantes: List<ComprobanteDelAbono> = emptyList()
        set(valor) {
            field = valor
            savedStateHandle[CLAVE_COMPROBANTES] =
                ArrayList(valor.map { Comprobantes.codificar(it) })
        }

    /**
     * Hay un destino de cámara en vuelo. **No se persiste**: si el proceso
     * muere antes de que el destino quede guardado, no se acuñó nada que
     * proteger. Es el mismo patrón sincrónico del guard del abono, aplicado a
     * un recurso mucho más barato.
     */
    private var pidiendoFoto: Boolean = false

    private val mutableState = MutableStateFlow(RegistrarAbonoUiState())
    val state: StateFlow<RegistrarAbonoUiState> = mutableState.asStateFlow()

    init {
        telemetry.screenView(PANTALLA)
        restaurarComprobantes()
        cargar()
    }

    /**
     * Vuelve a poner en pie lo capturado antes de que el proceso muriera. Lo
     * que no se pueda leer se descarta —no puede tumbar la pantalla del
     * dinero— pero se **cuenta y se reporta**: una foto que desaparece sola es
     * justo lo que la norma de errores prohíbe.
     */
    private fun restaurarComprobantes() {
        val crudos = savedStateHandle.get<ArrayList<String>>(CLAVE_COMPROBANTES).orEmpty()
        val leidos = crudos.mapNotNull { Comprobantes.decodificar(it) }
        // Reescribe el handle ya normalizado: lo ilegible no vuelve a leerse ni
        // a contarse en la siguiente muerte de proceso.
        comprobantes = leidos
        val ilegibles = crudos.size - leidos.size
        if (ilegibles > 0) {
            telemetry.error(
                code = PagosTelemetria.CODE_ABONO_FOTO_ILEGIBLE,
                message = "una entrada de comprobante guardada no se pudo leer; se descarto",
                props = mapOf(PagosTelemetria.PROP_OCURRENCIAS to ilegibles.toString())
            )
        }
    }

    /** Vuelve a leer la venta. Cada llamada es UNA sincronización. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = conLoCapturado(RegistrarAbonoUiState(cargando = true))
            mutableState.value = conLoCapturado(leer())
        }
    }

    /**
     * Cuelga lo capturado —comprobantes y destino en vuelo— al estado que se va
     * a publicar, leyéndolo del `SavedStateHandle` **en el instante de la
     * asignación**.
     *
     * El instante importa. Si el destino se leyera al construir el estado (antes
     * del `withContext` de la carga) y la cámara volviera mientras tanto, esta
     * asignación repondría un destino ya atendido y la pantalla **volvería a
     * abrir la cámara**. Aquí la lectura y la publicación ocurren sin punto de
     * suspensión entre medias, en el mismo dispatcher, así que
     * [tomarDestinoPendiente] no puede colarse entre las dos.
     */
    private fun conLoCapturado(estado: RegistrarAbonoUiState): RegistrarAbonoUiState =
        estado.copy(comprobantes = comprobantes, destinoDeFoto = destinoGuardado)

    // --- La foto: cuelga del abono y NUNCA lo detiene -------------------------

    /**
     * **Paso uno de la foto:** prepara el destino y pide abrir la cámara.
     *
     * No abre nada por sí mismo — deja [RegistrarAbonoUiState.destinoDeFoto]
     * puesto y la pantalla dispara el intent. Así el id viaja por el
     * `SavedStateHandle` y sobrevive a que el proceso muera con la cámara
     * encima, que es el escenario normal en un teléfono de gama baja.
     *
     * [pidiendoFoto] se pone **sincrónicamente antes** del `launch`, igual que
     * el guard del abono: dos toques rápidos no pueden acuñar dos destinos, que
     * dejaría un archivo crudo huérfano por cada uno.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // la camara falla de mil formas; ninguna puede llegar al dinero.
    fun pedirFoto() {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar || actual.destinoDeFoto != null || pidiendoFoto) return
        if (actual.comprobantes.size >= Comprobantes.MAXIMO) {
            mutableState.value = actual.copy(falloDeLaFoto = FalloDeLaFoto.YA_NO_CABEN)
            return
        }
        pidiendoFoto = true
        viewModelScope.launch {
            try {
                val destino = withContext(io) { camara.nuevoDestino() }
                destinoGuardado = destino
                mutableState.value = mutableState.value.copy(
                    destinoDeFoto = destino,
                    falloDeLaFoto = null
                )
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
     * archivo: guardarlo solo aplazaría el rechazo hasta un 422 que nadie va a
     * ver, en un teléfono que ya no tiene la foto a mano.
     */
    @Suppress(
        "TooGenericExceptionCaught"
    ) // comprimir puede reventar hasta con un OOM; el abono no se entera.
    fun fotoTomada() {
        val destino = tomarDestinoPendiente() ?: return reportarFotoSinDestino()
        viewModelScope.launch {
            try {
                val comprobante = withContext(io) { camara.aceptar(destino) }
                if (Comprobantes.permitido(comprobante.mime)) {
                    adjuntar(comprobante)
                } else {
                    rechazarPorTipo(comprobante)
                }
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
     * Toma el destino en vuelo **y lo suelta**, sincrónicamente y en un solo
     * paso.
     *
     * ## Por qué el `SavedStateHandle` manda sobre el estado
     *
     * Cuando el proceso muere con la cámara encima, el ViewModel se reconstruye
     * y el resultado de la cámara llega **antes** de que [cargar] resuelva: en
     * esa ventana `state.destinoDeFoto` todavía es `null` aunque el destino
     * exista. Leerlo del estado tiraba la foto ahí, en silencio, y después la
     * carga reponía el destino y la pantalla **reabría la cámara** — desde
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
     * forma que tiene una foto de perderse en este camino, y perder evidencia
     * en silencio es justo lo que la norma de errores prohíbe.
     */
    private fun reportarFotoSinDestino() {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_FOTO_SIN_DESTINO,
            message = "la camara devolvio una foto y ya no habia destino que la reclamara",
            props = emptyMap()
        )
    }

    /** Quita un comprobante ya adjunto y borra su archivo. */
    fun quitarFoto(id: String) {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar) return
        val quitado = actual.comprobantes.firstOrNull { it.id == id } ?: return
        comprobantes = actual.comprobantes.filterNot { it.id == id }
        mutableState.value = actual.copy(comprobantes = comprobantes, falloDeLaFoto = null)
        borrarArchivo(quitado.archivo)
    }

    private fun adjuntar(comprobante: ComprobanteDelAbono) {
        comprobantes = comprobantes + comprobante
        mutableState.value = mutableState.value.copy(
            comprobantes = comprobantes,
            falloDeLaFoto = null
        )
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // borrar un archivo puede fallar por permisos o por FS; se reporta y se sigue.
    private suspend fun rechazarPorTipo(comprobante: ComprobanteDelAbono) {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_FOTO_TIPO_NO_PERMITIDO,
            message = "la camara dejo un tipo que el servidor no acepta; no se adjunta",
            // Anti-PII: el MIME es un valor tecnico cerrado, no dato del cliente.
            props = mapOf(PagosTelemetria.PROP_TIPO to comprobante.mime)
        )
        mutableState.value = mutableState.value.copy(
            falloDeLaFoto = FalloDeLaFoto.TIPO_NO_PERMITIDO
        )
        // El archivo se va con el rechazo: nada lo va a subir nunca.
        try {
            camara.descartar(comprobante.archivo)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Throwable) {
            reportarFalloDeFoto(fallo, "no se pudo borrar la foto rechazada", conAviso = false)
        }
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
                // Sin aviso en pantalla: el cobrador pidió quitar la foto y la
                // foto se quitó. Lo que quedó fue un archivo en disco, y eso es
                // asunto del que lo tiene que limpiar, no suyo.
                reportarFalloDeFoto(fallo, "no se pudo borrar el archivo local", conAviso = false)
            }
        }
    }

    /**
     * Un fallo de la capa de fotos. Reporta con el NOMBRE de la clase de la
     * excepción (nunca su texto, que puede arrastrar la ruta o datos del
     * cliente) y, si toca, enciende el aviso.
     *
     * **Nunca toca el abono**: no cambia `fallo`, ni `guardando`, ni el guard.
     */
    private fun reportarFalloDeFoto(fallo: Throwable, porque: String, conAviso: Boolean = true) {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_FOTO_FALLO,
            message = "$porque; el abono no se ve afectado",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        if (conAviso) {
            mutableState.value = mutableState.value.copy(
                falloDeLaFoto = FalloDeLaFoto.NO_SE_PUDO_TOMAR
            )
        }
    }

    fun onDigito(digito: Int) = editar { it.conDigito(digito) }

    fun onPunto() = editar { it.conPunto() }

    fun onBorrar() = editar { it.sinUltimo() }

    /** Rellena el teclado desde un chip. El chip nunca ofrece más que el saldo. */
    fun onSugerido(importe: Money) = editar { MontoCapturado.deSugerido(importe) }

    fun onMetodo(metodo: MetodoDeCobro) {
        val actual = mutableState.value
        // Misma guarda que el teclado: con el cerrojo puesto nada de esto puede
        // terminar en un registro.
        if (!actual.sePuedeCapturar) return
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
    private suspend fun verificar(resultado: ResultadoDelAbono): Verificacion = try {
        val venta = withContext(io) { cargarDetalleVenta(ventaId) }
        when {
            venta == null -> noSeSupo(resultado, porque = "la venta ya no esta en el telefono")
            estaEnElHistorial(venta) -> Verificacion.QUEDO
            else -> Verificacion.NO_QUEDO
        }
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        // El error NO se traga: viaja el nombre de la clase de la excepción,
        // nunca su texto (que puede arrastrar datos del cliente).
        noSeSupo(
            resultado = resultado,
            porque = "la relectura de la venta fallo",
            excepcion = fallo.javaClass.simpleName
        )
    }

    /**
     * Registra que la comprobación no se pudo hacer y devuelve
     * [Verificacion.NO_SE_PUDO_SABER].
     *
     * **Este es el ÚNICO evento de esta rama.** El llamador no emite además
     * `pagos_abono_no_quedo_registrado`: sería afirmar que el abono no quedó
     * justo después de concluir que no se puede saber — una falsedad en la
     * bitácora, y un segundo evento para una sola falla. Un registro que miente
     * es peor que el silencio.
     */
    private fun noSeSupo(
        resultado: ResultadoDelAbono,
        porque: String,
        excepcion: String? = null
    ): Verificacion {
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_SIN_VERIFICAR,
            message = "no se pudo comprobar si el abono quedo; el guard se conserva: $porque",
            props = buildMap {
                put(PagosTelemetria.PROP_RESULTADO, resultado.name)
                excepcion?.let { put(PagosTelemetria.PROP_EXCEPCION, it) }
            }
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
            metodo = confirmacion.metodo,
            // Se manda lo que el ESTADO tiene en este instante, no lo que la
            // hoja congeló: con la hoja arriba `sePuedeCapturar` es falso, así
            // que la lista no puede haber cambiado desde el paso uno.
            comprobantes = comprobantes
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
        when (verificar(resultado)) {
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
                // El guard SE QUEDA PUESTO. Soltarlo sin saber es la suposición
                // que este diseño evita; la duda la resuelve `resolverGuard` en
                // la siguiente carga, mirando el historial — y por eso la banda
                // ofrece [cargar] como reintento REAL, en vez de dejar vivo un
                // CTA que no haría nada.
                //
                // NO se emite nada aquí: `verificar` ya reportó el hecho con su
                // causa. Decir además "no quedó registrado" sería afirmar lo
                // que se acaba de declarar incognoscible.
                terminarConFallo(
                    fallo = FalloDelAbono.NO_SE_PUDO_VERIFICAR,
                    verificacionPendiente = true
                )
            }
        }
    }

    private fun terminarComoRegistrado() {
        mutableState.value = mutableState.value.copy(
            guardando = false,
            confirmacion = null,
            fallo = null,
            registrado = abonoId,
            // La duda se acabó: el abono está.
            verificacionPendiente = false
        )
    }

    /**
     * [verificacionPendiente] echa el cerrojo: apaga el CTA, apaga la captura y
     * enciende "volver a revisar". Es lo ÚNICO que lo pone.
     */
    private fun terminarConFallo(fallo: FalloDelAbono, verificacionPendiente: Boolean = false) {
        mutableState.value = mutableState.value.copy(
            guardando = false,
            confirmacion = null,
            fallo = fallo,
            verificacionPendiente = verificacionPendiente
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

    /**
     * ¿Está [abonoId] entre los abonos de la venta?
     *
     * **Se pregunta por DOS columnas, y no es defensa por si acaso.** El
     * oráculo del guard era solo `pagoId`, que es `Payment.ID` — y
     * `CobranzaSyncManager.mergePagos` **borra esa fila a propósito** en cuanto
     * el servidor acusa recibo (`filterExistingIDs` →
     * `paymentDao.deleteByIDs`), reinsertando la canónica bajo la llave numérica
     * de Microsip. O sea que la defensa contra el cobro duplicado se apoyaba en
     * un dato que el sync elimina cada 30 segundos.
     *
     * **Verificado antes de tocar nada, y el borrado NO está mal:** el colapso
     * del gemelo es lo que impide que el mismo abono quede dos veces en Room y
     * duplique todos los totales del cobrador. Lo que faltaba era el segundo
     * oráculo: `toEntity()` **sí persiste** el UUID de la captura en
     * `Payment.PAGO_RECIBIDO_ID` de la fila canónica (por eso
     * `findCollapsibleUuidTwins` puede leerlo), así que el rastro nunca se
     * pierde — solo deja de ser la PK.
     *
     * La secuencia que esto cierra: se registra el abono → guard puesto → muere
     * el proceso con la pantalla en el back stack → un tick de sync colapsa el
     * gemelo UUID → se restaura la pantalla → [resolverGuard] no encontraba
     * `abonoId`, **soltaba el guard** y rearmaba el formulario con el mismo id;
     * un segundo `confirmar()` insertaba otra fila y `updateTotal` —que es un
     * decremento, no un set absoluto— bajaba `SALDO_REST` dos veces. El daño de
     * cable estaba acotado (`crearPago` viaja con `idempotencyKey`), pero el
     * local no: saldo descontado dos veces, y `SALDO_REST` es el techo del
     * bloqueo duro.
     */
    private fun estaEnElHistorial(venta: DetalleVenta): Boolean = venta.historial.meses.any { mes ->
        mes.pagos.any { it.pagoId == abonoId || it.capturaId == abonoId }
    }

    private fun falloDe(resultado: ResultadoDelAbono): FalloDelAbono = when (resultado) {
        ResultadoDelAbono.VENTA_NO_ESTA_EN_EL_TELEFONO -> FalloDelAbono.VENTA_NO_ESTA
        ResultadoDelAbono.SIN_COBRADOR -> FalloDelAbono.SIN_COBRADOR
        ResultadoDelAbono.BLOQUEADO_POR_SEGURIDAD -> FalloDelAbono.BLOQUEADO
        else -> FalloDelAbono.NO_SE_PUDO_GUARDAR
    }

    /**
     * Edita el monto. La guarda es [RegistrarAbonoUiState.sePuedeCapturar], que
     * incluye el cerrojo de la verificación pendiente: sin ella, teclear un
     * dígito limpiaba `fallo` —borrando la banda Y su botón— y devolvía el CTA a
     * la vida con el guard puesto, o sea el botón mudo otra vez, por la puerta
     * de al lado. Aquí no se teclea lo que no puede terminar en un registro.
     */
    private fun editar(cambio: (MontoCapturado) -> MontoCapturado) {
        val actual = mutableState.value
        if (!actual.sePuedeCapturar) return
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
                    // Lo capturado NO se repone aquí: lo cuelga `conLoCapturado`
                    // en el instante de publicar, que es lo que impide reponer
                    // un destino que la cámara ya atendió.
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

        /** Los comprobantes adjuntos, codificados. Ver [Comprobantes.codificar]. */
        const val CLAVE_COMPROBANTES = "pagos_abono_comprobantes"

        /** El destino que espera a la cámara. Ver [Comprobantes.codificarDestino]. */
        const val CLAVE_DESTINO = "pagos_abono_destino_foto"
    }
}
