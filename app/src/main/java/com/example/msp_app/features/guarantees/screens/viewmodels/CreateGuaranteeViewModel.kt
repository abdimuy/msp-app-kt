package com.example.msp_app.features.guarantees.screens.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.core.utils.Constants.NOTIFICADO
import com.example.msp_app.core.utils.ImageCompressor
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.features.guarantees.currentGuaranteeTimestamp
import com.example.msp_app.features.guarantees.data.RegistroDeGarantia
import com.example.msp_app.workmanager.enqueuePendingGuaranteesWorker
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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
     */
    fun saveGuarantee(context: Context, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        val id = externalId
        val fecha = fechaSolicitud
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                registro.capturar(id, emailDeLaSesion().orEmpty())
                val entity = GuaranteeEntity(
                    ID = 0,
                    EXTERNAL_ID = id,
                    DOCTO_CC_ID = null,
                    ESTADO = NOTIFICADO,
                    DESCRIPCION_FALLA = _descripcionFalla.value,
                    OBSERVACIONES = _observaciones.value.takeIf { it.isNotBlank() },
                    UPLOADED = 0,
                    FECHA_SOLICITUD = fecha,
                    NOMBRE_CLIENTE = _clienteNombre.value,
                    NOMBRE_PRODUCTO = _productoNombre.value
                )
                val savedPaths = saveImagesLocally(context, _imageUris.value, id)
                val images = savedPaths.mapIndexed { indice, (path, mime) ->
                    GuaranteeImageEntity(
                        ID = idDeImagen(id, indice),
                        GARANTIA_ID = id,
                        IMG_PATH = path,
                        IMG_MIME = mime,
                        IMG_DESC = _descripcionFalla.value,
                        FECHA_SUBIDA = fecha
                    )
                }
                registro.escribir(entity, images)
                withContext(NonCancellable) {
                    enqueuePendingGuaranteesWorker(context, id)
                    registro.cerrar(id)
                }
                onSuccess()
            } finally {
                _isSaving.value = false
            }
        }
    }

    private suspend fun saveImagesLocally(
        context: Context,
        uris: List<Uri>,
        guaranteeId: String
    ): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val savedPaths = mutableListOf<Pair<String, String>>()
        uris.forEachIndexed { index, uri ->
            try {
                if (!ImageCompressor.isValidImage(context, uri)) return@forEachIndexed
                // Nombre fijo por (garantía, posición): un reintento sobrescribe
                // el mismo archivo en vez de dejar copias huérfanas (E-APP-091).
                val fileName = "guarantee_${guaranteeId}_$index.jpg"
                val result = ImageCompressor.compressImage(
                    context = context,
                    uri = uri,
                    outputFileName = fileName
                )
                savedPaths.add(result.outputFile.absolutePath to "image/jpeg")
            } catch (_: Exception) {
            }
        }
        return@withContext savedPaths
    }

    private companion object {
        const val CLAVE_EXTERNAL_ID = "garantia_external_id"
        const val CLAVE_FECHA = "garantia_fecha_solicitud"

        /** Id determinista de la imagen: el reintento no la duplica. */
        fun idDeImagen(externalId: String, indice: Int): String =
            UUID.nameUUIDFromBytes("$externalId/$indice".toByteArray(Charsets.UTF_8)).toString()
    }
}
