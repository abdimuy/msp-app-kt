package com.example.msp_app.features.guarantees.screens.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.data.captura.CapturasEnProceso
import com.example.msp_app.features.guarantees.currentGuaranteeTimestamp
import com.example.msp_app.features.guarantees.data.RegistroDeGarantia
import com.example.msp_app.features.guarantees.data.ResumenDeLaGarantia
import com.example.msp_app.features.guarantees.data.comprimirImagenesDeGarantia
import com.example.msp_app.workmanager.enqueuePendingGuaranteesWorker
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * E-APP-091: el constructor `(Application, SavedStateHandle)` es uno de los dos
 * que `SavedStateViewModelFactory` (la fábrica por defecto de `viewModel()`)
 * sabe construir por reflexión; ahí vive el id de ESTA garantía para que un
 * reintento tras salir de la pantalla o morir el proceso no cree otra.
 */
class CreateGuaranteeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    // Not a constructor param: the default `viewModel()` factory resolves this class via
    // reflection on an (Application, SavedStateHandle) constructor. Adding `clock` as a
    // constructor parameter (even with a default) breaks that reflection — same pattern
    // as NewTransferViewModel.
    private val clock: AppClock = AppClock.System
    private val registro =
        RegistroDeGarantia(AppDatabase.getInstance(application.applicationContext), clock)

    /** El id de la garantía, acuñado una vez y persistido (E-APP-091). */
    private val externalId: String
        get() = savedStateHandle.get<String>(CLAVE_EXTERNAL_ID)
            ?: UUID.randomUUID().toString().also { savedStateHandle[CLAVE_EXTERNAL_ID] = it }

    /** La hora de la solicitud: la del primer toque, no la del reintento. */
    private val fechaSolicitud: String
        get() = savedStateHandle.get<String>(CLAVE_FECHA)
            ?: currentGuaranteeTimestamp(clock).also { savedStateHandle[CLAVE_FECHA] = it }

    private val _clienteNombre = MutableStateFlow("")
    val clienteNombre: StateFlow<String> = _clienteNombre

    private val _productoNombre = MutableStateFlow("")
    val productoNombre: StateFlow<String> = _productoNombre

    private val _descripcionFalla = MutableStateFlow("")
    val descripcionFalla: StateFlow<String> = _descripcionFalla

    private val _observaciones = MutableStateFlow("")
    val observaciones: StateFlow<String> = _observaciones

    private val _imageUris = MutableStateFlow<List<Uri>>(emptyList())
    val imageUris: StateFlow<List<Uri>> = _imageUris

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    fun onClienteSelected(nombre: String) {
        _clienteNombre.value = nombre
    }

    fun onProductoChange(value: String) {
        _productoNombre.value = value
    }

    fun onDescripcionFallaChange(value: String) {
        _descripcionFalla.value = value
    }

    fun onObservacionesChange(value: String) {
        _observaciones.value = value
    }

    fun addImage(uri: Uri) {
        _imageUris.value = _imageUris.value + uri
    }

    fun removeImage(uri: Uri) {
        _imageUris.value = _imageUris.value.filterNot { it == uri }
    }

    fun isFormValid(): Boolean {
        return _clienteNombre.value.isNotBlank() &&
            _productoNombre.value.isNotBlank() &&
            _descripcionFalla.value.isNotBlank() &&
            _imageUris.value.isNotEmpty()
    }

    /**
     * E-APP-091: marcador primero (sin suspensión previa, `UNDISPATCHED`), luego
     * la compresión (cancelable), y garantía + imágenes en UNA transacción que
     * la salida de la pantalla no corta. Nunca queda una garantía sin imágenes.
     *
     * E-APP-119 (D15): el marcador lleva lo capturado (y, tras comprimir, las
     * fotos ya guardadas), así que si la pantalla se cierra antes de escribir,
     * la garantía se reanuda al volver a la app en vez de perderse.
     */
    fun saveGuarantee(context: Context, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        val id = externalId
        val fecha = fechaSolicitud
        val uris = _imageUris.value
        // Síncrono, antes del launch: la reanudación no toca esta captura.
        CapturasEnProceso.entrar(id)
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val capturado = ResumenDeLaGarantia(
                    descripcionFalla = _descripcionFalla.value,
                    observaciones = _observaciones.value,
                    clienteNombre = _clienteNombre.value,
                    productoNombre = _productoNombre.value,
                    fechaSolicitud = fecha,
                    fuentes = uris.map { it.toString() },
                    imagenes = null
                )
                registro.capturar(id, emailDeLaSesion().orEmpty(), capturado.json())
                val resumen = capturado.conImagenes(comprimirImagenesDeGarantia(context, uris, id))
                registro.anotarResumen(id, resumen.json())
                registro.escribir(
                    requireNotNull(resumen.garantia(id)) { "el formulario valida estos campos" },
                    resumen.imagenesDe(id)
                )
                withContext(NonCancellable) {
                    enqueuePendingGuaranteesWorker(context, id)
                    registro.cerrar(id)
                }
                onSuccess()
            } finally {
                CapturasEnProceso.salir(id)
                _isSaving.value = false
            }
        }
    }

    private companion object {
        const val CLAVE_EXTERNAL_ID = "garantia_external_id"
        const val CLAVE_FECHA = "garantia_fecha_solicitud"
    }
}
