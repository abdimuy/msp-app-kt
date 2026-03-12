package com.example.msp_app.features.guarantees.screens.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.Constants.NOTIFICADO
import com.example.msp_app.core.utils.ImageCompressor
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity
import com.example.msp_app.workmanager.enqueuePendingGuaranteesWorker
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateGuaranteeViewModel(application: Application) : AndroidViewModel(application) {
    private val guaranteeStore = GuaranteesLocalDataSource(application.applicationContext)

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

    fun saveGuarantee(context: Context, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val externalId = UUID.randomUUID().toString()
                val fechaSolicitud = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

                val entity = GuaranteeEntity(
                    ID = 0,
                    EXTERNAL_ID = externalId,
                    DOCTO_CC_ID = null,
                    ESTADO = NOTIFICADO,
                    DESCRIPCION_FALLA = _descripcionFalla.value,
                    OBSERVACIONES = _observaciones.value.takeIf { it.isNotBlank() },
                    UPLOADED = 0,
                    FECHA_SOLICITUD = fechaSolicitud,
                    NOMBRE_CLIENTE = _clienteNombre.value,
                    NOMBRE_PRODUCTO = _productoNombre.value
                )

                guaranteeStore.insertGuarantee(entity)

                val savedPaths = saveImagesLocally(context, _imageUris.value, externalId)
                val images = savedPaths.map { (path, mime) ->
                    GuaranteeImageEntity(
                        ID = UUID.randomUUID().toString(),
                        GARANTIA_ID = externalId,
                        IMG_PATH = path,
                        IMG_MIME = mime,
                        IMG_DESC = _descripcionFalla.value,
                        FECHA_SUBIDA = fechaSolicitud
                    )
                }
                guaranteeStore.insertGuaranteeImage(images)

                enqueuePendingGuaranteesWorker(context, externalId)

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
                val fileName = "guarantee_${guaranteeId}_${System.currentTimeMillis()}_$index.jpg"
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
}
