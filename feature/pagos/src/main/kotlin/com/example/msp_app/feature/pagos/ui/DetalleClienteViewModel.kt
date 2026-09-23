package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.TiempoRelativo
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
import com.example.msp_app.feature.pagos.application.GuardarFichaDelCliente
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.CuentaDelAbono
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.port.AccionesExternasPort
import com.example.msp_app.feature.pagos.domain.port.DestinoEnElMapa
import com.example.msp_app.feature.pagos.domain.port.PrivacidadPort
import com.example.msp_app.feature.pagos.domain.port.ResultadoDeLaFicha
import com.example.msp_app.feature.pagos.domain.port.TemaDeLaAppPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * El detalle de cliente como **destino de navegación con su propio ViewModel y
 * `SavedStateHandle`** — no un diálogo.
 *
 * No es cosmético y es el patrón que fijan las Tasks 17-21: un
 * `FullScreenDialog` vive dentro de la composición de quien lo abrió y muere
 * con ella; la cámara (Tasks 22-23) saca al usuario de la app y el proceso
 * puede morir mientras tanto. Un destino con `SavedStateHandle` sobrevive a
 * eso, un diálogo no.
 *
 * `@HiltViewModel`, sin `@Singleton` (kill-switch de baseURL): nada de lo que
 * inyecta sostiene una sesión o un API service.
 */
@HiltViewModel
@Suppress(
    "TooManyFunctions",
    // Ocho dependencias: las siete de la Task 21 mas `AccionesExternasPort`.
    // Ese puerto es justamente lo que saca el `Intent` del Composable —mismo
    // criterio que `RegistrarAbonoViewModel` con la camara—, y agruparlas en un
    // holder solo escondería el wiring.
    "LongParameterList"
) // una funcion por gesto de la pantalla; agruparlas escondería cuál toca qué.
class DetalleClienteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarDetalleCliente: CargarDetalleCliente,
    private val guardarFichaDelCliente: GuardarFichaDelCliente,
    private val accionesExternas: AccionesExternasPort,
    private val tema: TemaDeLaAppPort,
    private val privacidad: PrivacidadPort,
    private val telemetry: Telemetry,
    @PagosIoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    /**
     * El cliente que se abrió, leído del `SavedStateHandle` — no de un campo
     * del ViewModel ni de un parámetro del Composable, que es lo que se pierde
     * cuando el proceso muere detrás de la cámara.
     */
    val clienteId: Int = checkNotNull(savedStateHandle.get<Int>(PagosRutas.ARG_CLIENTE_ID)) {
        "DetalleClienteViewModel sin ${PagosRutas.ARG_CLIENTE_ID} en el SavedStateHandle"
    }

    private val mutableState = MutableStateFlow(DetalleClienteUiState())

    /**
     * El detalle, con el tema y la privacidad **derivados** de sus puertos en cada
     * emisión — no guardados con un `copy`.
     *
     * Es el mismo reparto que `ListaDeClientesViewModel` (Ruling BQ) y por la
     * misma razón: cualquier escritor de [mutableState] que use `copy` sobre un
     * valor viejo pisaría el tema, y el pisón se queda pegado hasta el próximo
     * cambio de tema. Acá hay dos escritores que lo harían —`cargar()`, que
     * construye un estado nuevo desde cero, y `guardarFicha()`— así que el riesgo
     * no es teórico.
     *
     * El `initialValue` se siembra con las lecturas **síncronas** de los dos
     * puertos: sin eso la pantalla pinta un frame en claro antes de la primera
     * emisión (flash blanco con la app en oscuro) y enseña los montos un frame
     * antes de esconderlos, que es justo lo que el ojo existe para evitar.
     */
    val state: StateFlow<DetalleClienteUiState> =
        combine(mutableState, tema.oscuro, privacidad.ocultos) { detalle, oscuro, ocultos ->
            detalle.copy(temaOscuro = oscuro, montosOcultos = ocultos)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DetalleClienteUiState(
                temaOscuro = tema.oscuroAhora(),
                montosOcultos = privacidad.ocultosAhora()
            )
        )

    init {
        telemetry.screenView(PANTALLA)
        cargar()
    }

    /** Vuelve a leer. Cada llamada es UNA sincronización — ver [CargarDetalleCliente]. */
    fun cargar() {
        viewModelScope.launch {
            mutableState.value = DetalleClienteUiState(cargando = true)
            mutableState.value = leer()
        }
    }

    /**
     * Vuelve a leer **sin parpadeo**: a diferencia de [cargar], no reemplaza el
     * estado por uno en blanco con `cargando = true`. La usa la pantalla al
     * reanudarse — ver `RecargaAlVolver` —, y un spinner de cuerpo entero al
     * volver de registrar un abono o una visita se lee como si la pantalla se
     * hubiera perdido.
     *
     * Conserva [DetalleClienteUiState.edicionDeLaFicha] y
     * [DetalleClienteUiState.eleccionDeCuenta] explícitamente: [leer] arma un
     * `DetalleClienteUiState` desde cero y, sin este `copy`, una recarga
     * disparada al reanudar la app —con la hoja de la ficha o la de "¿a cuál
     * cuenta?" todavía abierta, por ejemplo tras un cambio de app— la cerraría
     * de golpe y tiraría lo que el cobrador estaba escribiendo o eligiendo.
     */
    fun recargar() {
        viewModelScope.launch {
            val edicionDeLaFicha = mutableState.value.edicionDeLaFicha
            val eleccionDeCuenta = mutableState.value.eleccionDeCuenta
            mutableState.value = leer().copy(
                edicionDeLaFicha = edicionDeLaFicha,
                eleccionDeCuenta = eleccionDeCuenta
            )
        }
    }

    @Suppress(
        "TooGenericExceptionCaught"
    ) // cualquier fallo de Room/Firestore degrada igual; se reporta.
    private suspend fun leer(): DetalleClienteUiState = try {
        val detalle = withContext(io) { cargarDetalleCliente(clienteId) }
        DetalleClienteUiState(
            cargando = false,
            detalle = detalle,
            error = if (detalle == null) ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO else null
        )
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (fallo: Throwable) {
        telemetry.error(
            code = PagosTelemetria.CODE_DETALLE_CLIENTE_FALLO,
            message = "no se pudo armar el detalle de cliente",
            props = mapOf(PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName)
        )
        DetalleClienteUiState(cargando = false, error = ErrorDeDetalle.FALLO_LA_CARGA)
    }

    /**
     * "Registrar abono": va directo si hay una sola cuenta cobrable, y si no
     * abre la hoja que pregunta a cuál.
     *
     * Devuelve el `ventaId` cuando hay que navegar, y `null` cuando lo que hizo
     * fue abrir la hoja. **No adivina nunca**: el `firstOrNull()` que había aquí
     * mandaba el dinero a la primera venta de la lista sin decirlo.
     */
    fun registrarAbono(): Int? {
        val ventas = state.value.detalle?.ventas.orEmpty()
        CuentaDelAbono.unica(ventas)?.let { return it }
        if (CuentaDelAbono.cobrables(ventas).isEmpty()) return null
        mutableState.value = mutableState.value.copy(
            eleccionDeCuenta = EleccionDeCuenta(CuentaDelAbono.preseleccionada(ventas))
        )
        return null
    }

    /** Cambia la cuenta marcada dentro de la hoja. Nada se registra todavía. */
    fun elegirCuenta(ventaId: Int) {
        val eleccion = mutableState.value.eleccionDeCuenta ?: return
        mutableState.value = mutableState.value.copy(
            eleccionDeCuenta = eleccion.copy(elegida = ventaId)
        )
    }

    /** Cierra la hoja sin elegir. Nada se registra. */
    fun cerrarEleccionDeCuenta() {
        mutableState.value = mutableState.value.copy(eleccionDeCuenta = null)
    }

    /**
     * Confirma la cuenta y cierra la hoja; devuelve a dónde navegar.
     *
     * La hoja se cierra **antes** de navegar para que volver del abono no
     * encuentre la hoja todavía arriba.
     */
    fun confirmarCuenta(): Int? {
        val elegida = mutableState.value.eleccionDeCuenta?.elegida ?: return null
        mutableState.value = mutableState.value.copy(eleccionDeCuenta = null)
        return elegida
    }

    /** Abre el marcador con el teléfono del cliente. */
    fun marcar() {
        val telefono = state.value.detalle?.telefono?.takeIf { it.isNotBlank() } ?: return
        telemetry.tap(PANTALLA, PagosTelemetria.ACCION_MARCAR)
        correr(PagosTelemetria.ACCION_MARCAR) { accionesExternas.marcar(telefono) }
    }

    /** Abre la conversación de WhatsApp con el cliente. */
    fun escribirPorWhatsApp() {
        val telefono = state.value.detalle?.telefono?.takeIf { it.isNotBlank() } ?: return
        telemetry.tap(PANTALLA, PagosTelemetria.ACCION_WHATSAPP)
        correr(PagosTelemetria.ACCION_WHATSAPP) { accionesExternas.escribirPorWhatsApp(telefono) }
    }

    /**
     * Abre la casa en el mapa: con el punto del último cobro cuando existe, y si
     * no con la dirección escrita.
     */
    fun comoLlegar() {
        val detalle = state.value.detalle ?: return
        telemetry.tap(PANTALLA, PagosTelemetria.ACCION_COMO_LLEGAR)
        val destino = DestinoEnElMapa(
            etiqueta = detalle.nombre,
            direccion = detalle.direccion,
            lat = detalle.ultimoCobroAqui?.lat,
            lng = detalle.ultimoCobroAqui?.lng
        )
        correr(PagosTelemetria.ACCION_COMO_LLEGAR) { accionesExternas.comoLlegar(destino) }
    }

    /**
     * Corre una acción externa y **reporta si no se pudo abrir**.
     *
     * No hay `catch`: el puerto contesta `Result` por contrato y nunca lanza. Lo
     * que no puede pasar es que el fallo se pierda — un teléfono sin WhatsApp o
     * sin app de mapas es un caso real de la flota, y sin este reporte el síntoma
     * sería "toco y no pasa nada", que no deja rastro en ningún lado.
     *
     * Anti-PII: viajan la acción (catálogo cerrado) y el nombre de la clase de
     * excepción. **El teléfono y el nombre del cliente no.**
     */
    private fun correr(accion: String, bloque: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            bloque().onFailure { fallo ->
                telemetry.error(
                    code = PagosTelemetria.CODE_ACCION_EXTERNA_FALLO,
                    message = "no se pudo abrir la app de fuera desde el detalle de cliente",
                    props = mapOf(
                        PagosTelemetria.PROP_ACCION to accion,
                        PagosTelemetria.PROP_EXCEPCION to fallo.javaClass.simpleName
                    )
                )
            }
        }
    }

    /**
     * Alterna el tema **GLOBAL** de la app, el mismo que mueven la lista, el cajón
     * legado, Configuración y el reporte de cobranza. No escribe el estado aquí: lo
     * escribe la colecta de [TemaDeLaAppPort.oscuro] que sostiene [state], que es
     * lo que hace que el glifo también reaccione a un cambio hecho en otra
     * pantalla.
     */
    fun alternarTema() {
        telemetry.tap(PANTALLA, ACCION_TEMA)
        tema.alternar()
    }

    /**
     * Esconde o enseña los montos. A diferencia del tema, [PrivacidadPort.alternar]
     * SÍ suspende —escribe en DataStore—, así que va en una corrutina.
     */
    fun alternarPrivacidad() {
        telemetry.tap(PANTALLA, ACCION_PRIVACIDAD)
        viewModelScope.launch { privacidad.alternar() }
    }

    /**
     * Abre la hoja de la ficha, sembrada con lo que ya está guardado.
     *
     * **No abre sobre una lectura fallida.** `detalle.ficha == null` significa
     * "no se pudo leer", y editar desde ahí guardaría una ficha en blanco encima
     * del conocimiento que sí estaba en la base — ver
     * [com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort.fichaDe].
     */
    fun editarFicha() {
        val detalle = mutableState.value.detalle ?: return
        val ficha = detalle.ficha ?: return
        mutableState.value = mutableState.value.copy(
            edicionDeLaFicha = EdicionDeLaFicha(
                senales = ficha.senales,
                nota = ficha.nota.orEmpty(),
                // El "hoy" sale del detalle, que lo trajo del reloj inyectado.
                // Calcularlo en la hoja obligaría a la pantalla a preguntar la
                // hora, y entonces el mismo estado se pintaría distinto mañana.
                anotada = ficha.actualizada?.let {
                    TiempoRelativo.de(AppTime.toBusinessDate(it), detalle.hoy)
                }
            )
        )
    }

    /** Cierra la hoja **descartando** el borrador. Nada se escribió. */
    fun cerrarFicha() {
        mutableState.value = mutableState.value.copy(edicionDeLaFicha = null)
    }

    /** Marca o desmarca una señal del catálogo cerrado. Solo el borrador. */
    fun alternarSenal(senal: SenalDeFicha) {
        val edicion = mutableState.value.edicionDeLaFicha ?: return
        val senales = if (senal in edicion.senales) {
            edicion.senales - senal
        } else {
            edicion.senales + senal
        }
        mutableState.value = mutableState.value.copy(
            edicionDeLaFicha = edicion.copy(senales = senales, fallo = false)
        )
    }

    /** Teclea la nota libre. Solo el borrador. */
    fun escribirNota(nota: String) {
        val edicion = mutableState.value.edicionDeLaFicha ?: return
        mutableState.value = mutableState.value.copy(
            edicionDeLaFicha = edicion.copy(nota = nota, fallo = false)
        )
    }

    /**
     * Guarda la ficha.
     *
     * ## Por qué el estado se actualiza en memoria y NO se recarga la pantalla
     *
     * Un `cargar()` aquí volvería a leer ventas, abonos, visitas, liquidaciones
     * y Firestore para reflejar una nota. Además **el detalle podría fallar** en
     * esa segunda lectura y la pantalla se caería después de un guardado
     * exitoso, que es lo peor de los dos mundos. Lo que se guardó ya se conoce:
     * es el borrador que se acaba de escribir, normalizado por
     * [FichaDelCliente.limpia] con la misma regla que usó el caso de uso — la
     * MISMA función, no una copia, así que lo pintado no puede despegarse de lo
     * guardado.
     *
     * La hoja **no se cierra si falló**: cerrarla tiraría lo que el cobrador
     * acaba de escribir.
     */
    fun guardarFicha() {
        val edicion = mutableState.value.edicionDeLaFicha ?: return
        if (edicion.guardando) return
        mutableState.value = mutableState.value.copy(
            edicionDeLaFicha = edicion.copy(guardando = true, fallo = false)
        )
        viewModelScope.launch {
            val resultado = withContext(io) {
                guardarFichaDelCliente(
                    clienteId = clienteId,
                    senales = edicion.senales,
                    nota = edicion.nota
                )
            }
            mutableState.value = when (resultado) {
                is ResultadoDeLaFicha.Guardada -> mutableState.value.copy(
                    // Lo que se pinta es LO QUE QUEDÓ ESCRITO, traído por el
                    // puerto: nada aquí vuelve a normalizar la nota ni a
                    // reconstruir el conjunto, así que no hay dos versiones de
                    // la ficha que puedan despegarse.
                    detalle = mutableState.value.detalle?.copy(ficha = resultado.ficha),
                    edicionDeLaFicha = null
                )

                ResultadoDeLaFicha.FalloElGuardado -> {
                    telemetry.error(
                        code = PagosTelemetria.CODE_FICHA_NO_QUEDO_GUARDADA,
                        message = "la ficha no quedo guardada, visto desde la pantalla"
                    )
                    mutableState.value.copy(
                        edicionDeLaFicha = mutableState.value.edicionDeLaFicha
                            ?.copy(guardando = false, fallo = true)
                    )
                }
            }
        }
    }

    private companion object {
        /** Id estático de pantalla para telemetría — sin PII. */
        const val PANTALLA = "pagos_detalle_cliente"

        /** Acción estática del toggle de tema. */
        const val ACCION_TEMA = "tema"

        /** Acción estática del ojo de privacidad. */
        const val ACCION_PRIVACIDAD = "privacidad"
    }
}
