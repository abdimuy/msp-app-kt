package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.busqueda.ActualizadorDeLista
import com.example.msp_app.core.common.busqueda.ConsultaDeBusqueda
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.clientes.ResultadoDeClientes
import com.example.msp_app.data.repository.ClienteRepository
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Estado de la hoja "Buscar cliente", compartido por ventas y garantías (E-APP-157).
 *
 * La lógica vive fuera de aquí: la búsqueda en `MotorDeBusqueda`/`BuscadorDeClientes` y el
 * botón de actualizar (un solo vuelo + tope total de duración) en `ActualizadorDeLista`.
 */
@OptIn(FlowPreview::class)
class ClienteSearchViewModel internal constructor(
    application: Application,
    private val repository: ClienteRepository
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

    private val actualizador = ActualizadorDeLista(
        scope = viewModelScope,
        topeMs = TopeDeDescarga.MANUAL_MS,
        descargar = { repository.descargarPadron() },
        aplicar = { repository.aplicarPadron(it) },
        alTerminarBien = {
            refrescarEstadoDelPadron()
            reintentos.value++
        }
    )
    private val reintentos = MutableStateFlow(0)

    val isSyncing: StateFlow<Boolean> = actualizador.actualizando
    val syncError: StateFlow<String?> = actualizador.mensajeDeError

    init {
        viewModelScope.launch { refrescarEstadoDelPadron() }

        viewModelScope.launch {
            _query.debounce(DEBOUNCE_MS).distinctUntilChanged().collect { buscar(it) }
        }
        viewModelScope.launch {
            reintentos.collect { if (it > 0) buscar(_query.value) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    /** Botón "Actualizar lista". Un segundo toque mientras corre no lanza otra descarga. */
    fun syncFromServer() {
        actualizador.iniciar()
    }

    fun clearSyncError() {
        actualizador.limpiarError()
    }

    private suspend fun buscar(texto: String) {
        if (ConsultaDeBusqueda.interpretar(texto) == ConsultaDeBusqueda.Vacia) {
            _resultado.value = ResultadoDeClientes.VACIO
            return
        }
        _isSearching.value = true
        try {
            _resultado.value = repository.buscar(texto)
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
