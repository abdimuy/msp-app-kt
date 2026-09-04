package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
import com.example.msp_app.feature.pagos.application.GuardarFichaDelCliente
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.di.PagosIoDispatcher
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.domain.port.ResultadoDeLaFicha
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
class DetalleClienteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cargarDetalleCliente: CargarDetalleCliente,
    private val guardarFichaDelCliente: GuardarFichaDelCliente,
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
    val state: StateFlow<DetalleClienteUiState> = mutableState.asStateFlow()

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
     * Abre la hoja de la ficha, sembrada con lo que ya está guardado.
     *
     * **No abre sobre una lectura fallida.** `detalle.ficha == null` significa
     * "no se pudo leer", y editar desde ahí guardaría una ficha en blanco encima
     * del conocimiento que sí estaba en la base — ver
     * [com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort.fichaDe].
     */
    fun editarFicha() {
        val ficha = mutableState.value.detalle?.ficha ?: return
        mutableState.value = mutableState.value.copy(
            edicionDeLaFicha = EdicionDeLaFicha(
                senales = ficha.senales,
                nota = ficha.nota.orEmpty()
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
    }
}
