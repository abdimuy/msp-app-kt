package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.busqueda.ActualizadorDeLista
import com.example.msp_app.core.common.busqueda.ConsultaDeBusqueda
import com.example.msp_app.core.common.busqueda.Rechazo
import com.example.msp_app.core.common.busqueda.TextosDeLista
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.clientes.ResultadoDeClientes
import com.example.msp_app.data.repository.ClienteRepository
import com.example.msp_app.data.repository.ResultadoDePadron
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado de la hoja "Buscar cliente", compartido por ventas y garantías (E-APP-157).
 *
 * La lógica vive fuera de aquí: la búsqueda en `MotorDeBusqueda`/`BuscadorDeClientes` y el
 * botón de actualizar (un solo vuelo + tope total de duración) en `ActualizadorDeLista`.
 */
@OptIn(FlowPreview::class)
class ClienteSearchViewModel internal constructor(
    application: Application,
    private val repository: ClienteRepository,
    private val buscar: suspend (String) -> ResultadoDeClientes = repository::buscar,
    /** Normalizar y ordenar hasta ~22k filas (y recorrer 44k en "Parecidos") no va en el hilo principal. */
    private val dispatcherDeBusqueda: CoroutineDispatcher = Dispatchers.Default
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application,
        ClienteRepository(application.applicationContext)
    )

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _resultado = MutableStateFlow(ResultadoDeClientes.VACIO)
    val resultado: StateFlow<ResultadoDeClientes> = _resultado

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    /** `null` mientras no se ha leído; `0` = el padrón aún no se descargó. */
    private val _clienteCount = MutableStateFlow<Int?>(null)
    val clienteCount: StateFlow<Int?> = _clienteCount

    private val _ultimaActualizacion = MutableStateFlow<String?>(null)
    val ultimaActualizacion: StateFlow<String?> = _ultimaActualizacion

    private var ultimoResultado: ResultadoDePadron? = null

    private val actualizador = ActualizadorDeLista(
        scope = viewModelScope,
        topeMs = TopeDeDescarga.MANUAL_MS,
        descargar = { repository.descargarPadron() },
        aplicar = {
            ultimoResultado = repository.aplicarPadronConResultado(it)
            ultimoResultado == ResultadoDePadron.REEMPLAZADO
        },
        alTerminarBien = {
            refrescarEstadoDelPadron()
            reintentos.value++
        },
        rechazo = Rechazo(
            mensaje = {
                if (ultimoResultado == ResultadoDePadron.INCOMPLETA_IGNORADA) {
                    TextosDeLista.ERROR_LISTA_INCOMPLETA
                } else {
                    TextosDeLista.ERROR_LISTA_VACIA
                }
            },
            sePuedeForzar = { ultimoResultado == ResultadoDePadron.INCOMPLETA_IGNORADA },
            forzar = {
                repository.aplicarPadronConResultado(it, forzar = true) == ResultadoDePadron.REEMPLAZADO
            }
        )
    )
    private val reintentos = MutableStateFlow(0)

    val isSyncing: StateFlow<Boolean> = actualizador.actualizando
    val syncError: StateFlow<String?> = actualizador.mensajeDeError

    /** Hay una lista incompleta rechazada que el usuario puede aplicar con [forzarActualizacion]. */
    val puedeForzar: StateFlow<Boolean> = actualizador.puedeForzar

    init {
        viewModelScope.launch { refrescarEstadoDelPadron() }

        // Un solo recolector: teclear otra consulta, o repetir la búsqueda tras actualizar la lista,
        // CANCELA la búsqueda en vuelo (collectLatest); así una consulta vieja nunca pisa a la nueva
        // ni apaga el indicador de la vigente.
        viewModelScope.launch {
            combine(
                _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
                reintentos
            ) { texto, _ -> texto }
                .collectLatest { ejecutarBusqueda(it) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    /** Botón "Actualizar lista". Un segundo toque mientras corre no lanza otra descarga. */
    fun syncFromServer() {
        actualizador.iniciar()
    }

    /** "Actualizar de todos modos": aplica la lista incompleta ya descargada, sin volver a bajarla. */
    fun forzarActualizacion() {
        actualizador.forzarActualizacion()
    }

    fun clearSyncError() {
        actualizador.limpiarError()
    }

    private suspend fun ejecutarBusqueda(texto: String) {
        if (ConsultaDeBusqueda.interpretar(texto) == ConsultaDeBusqueda.Vacia) {
            _resultado.value = ResultadoDeClientes.VACIO
            return
        }
        _isSearching.value = true
        try {
            _resultado.value = withContext(dispatcherDeBusqueda) { buscar(texto) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ClienteSearchVM", "La búsqueda falló", e)
            _resultado.value = ResultadoDeClientes.VACIO
        } finally {
            _isSearching.value = false
        }
    }

    private suspend fun refrescarEstadoDelPadron() {
        _clienteCount.value = try {
            repository.getCount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ClienteSearchVM", "No se pudo contar el padrón", e)
            0
        }
        _ultimaActualizacion.value = repository.ultimaActualizacionMs()?.let {
            AppTime.formatForDisplay(Instant.ofEpochMilli(it))
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
