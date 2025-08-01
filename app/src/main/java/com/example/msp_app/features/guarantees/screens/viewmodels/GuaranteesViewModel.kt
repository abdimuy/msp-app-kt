package com.example.msp_app.features.guarantees.screens.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.Constants.ENTREGADO
import com.example.msp_app.core.utils.Constants.RECOLECTADO
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.guarantee.GuaranteesApi
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.entities.GuaranteeEntity
import com.example.msp_app.data.local.entities.GuaranteeEventEntity
import com.example.msp_app.data.local.entities.GuaranteeImageEntity
import com.example.msp_app.workmanager.enqueuePendingGuaranteeEventsWorker
import com.example.msp_app.workmanager.enqueuePendingGuaranteesWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class GuaranteesViewModel(application: Application) : AndroidViewModel(application) {
    private val guaranteeStore = GuaranteesLocalDataSource(application.applicationContext)
    private val api: GuaranteesApi get() = ApiProvider.create(GuaranteesApi::class.java)

    private val _guarantees = MutableStateFlow<List<GuaranteeEntity>>(emptyList())
    val guarantees: StateFlow<List<GuaranteeEntity>> = _guarantees

    private val _selectedGuarantee = MutableStateFlow<GuaranteeEntity?>(null)
    val selectedGuarantee: StateFlow<GuaranteeEntity?> = _selectedGuarantee

    private val _guaranteeBySale = MutableStateFlow<GuaranteeEntity?>(null)
    val guaranteeBySale: StateFlow<GuaranteeEntity?> = _guaranteeBySale

    private val _guaranteeEvents = MutableStateFlow<List<GuaranteeEventEntity>>(emptyList())
    val guaranteeEvents: StateFlow<List<GuaranteeEventEntity>> = _guaranteeEvents

    private val _allGuaranteeEvents = MutableStateFlow<List<GuaranteeEventEntity>>(emptyList())
    val allGuaranteeEvents: StateFlow<List<GuaranteeEventEntity>> = _allGuaranteeEvents

    fun loadAllGuarantees() {
        viewModelScope.launch {
            val result = guaranteeStore.getAllGuarantees()
            _guarantees.value = result
        }
    }

    fun getGuaranteeById(id: Int) {
        viewModelScope.launch {
            val guarantee = guaranteeStore.getGuaranteeById(id)
            _selectedGuarantee.value = guarantee
        }
    }

    fun insertGuarantee(guarantee: GuaranteeEntity) {
        viewModelScope.launch {
            guaranteeStore.insertGuarantee(guarantee)
            loadAllGuarantees()
            enqueueGuaranteeForUpload(guarantee)
        }
    }

    fun loadGuaranteeBySaleId(doctoCcId: Int) {
        viewModelScope.launch {
            val result = guaranteeStore.getGuaranteeByDoctoCcId(doctoCcId)
            _guaranteeBySale.value = result
        }
    }

    fun loadEventsByGuaranteeId(guaranteeId: String) {
        viewModelScope.launch {
            val eventos = guaranteeStore.getEventsByGuaranteeId(guaranteeId)
            _guaranteeEvents.value = eventos
        }
    }

    fun loadAllGuaranteeEvents() {
        viewModelScope.launch {
            val eventos = guaranteeStore.getAllGuaranteeEvents()
            _allGuaranteeEvents.value = eventos
        }
    }

    fun saveImagesLocally(
        context: Context,
        uris: List<Uri>,
        guaranteeId: String
    ): List<Pair<String, String>> {
        val savedPaths = mutableListOf<Pair<String, String>>() // Pair(path, mimeType)

        uris.forEach { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val extension = if (mime == "image/png") ".png" else ".jpg"

            val fileName = "guarantee_${guaranteeId}_${System.currentTimeMillis()}$extension"
            val file = File(context.filesDir, fileName)

            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            savedPaths.add(file.absolutePath to mime)
        }

        return savedPaths
    }


    fun saveGuaranteeImages(
        context: Context,
        uris: List<Uri>,
        guaranteeExternalId: String,
        description: String?,
        fechaSubida: String
    ) {
        viewModelScope.launch {
            val savedPaths = saveImagesLocally(context, uris, guaranteeExternalId)
            val images = savedPaths.map { (path, mime) ->
                GuaranteeImageEntity(
                    ID = UUID.randomUUID().toString(),
                    GARANTIA_ID = guaranteeExternalId,
                    IMG_PATH = path,
                    IMG_MIME = mime,
                    IMG_DESC = description,
                    FECHA_SUBIDA = fechaSubida
                )
            }
            guaranteeStore.insertGuaranteeImage(images)
        }
    }

    fun onRecolectarProductoClick(guarantee: GuaranteeEntity) {
        viewModelScope.launch {
            guaranteeStore.updateGuaranteeStatusAndInsertEvent(
                guaranteeId = guarantee.ID,
                externalId = guarantee.EXTERNAL_ID,
                newEstado = RECOLECTADO,
                tipoEvento = RECOLECTADO,
                comentario = "El producto fue recolectado del cliente"
            )
            enqueuePendingGuaranteeEventsWorker(getApplication())
        }
    }

    fun onEntregarProducto(guarantee: GuaranteeEntity) {
        viewModelScope.launch {
            guaranteeStore.updateGuaranteeStatusAndInsertEvent(
                guaranteeId = guarantee.ID,
                externalId = guarantee.EXTERNAL_ID,
                newEstado = ENTREGADO,
                tipoEvento = ENTREGADO,
                comentario = "El producto fue entregado al cliente"
            )
            enqueuePendingGuaranteeEventsWorker(getApplication())
        }
    }

    fun postGuaranteeRemote(guarantee: GuaranteeEntity) {
        viewModelScope.launch {
            try {
                val images = guaranteeStore.getImagesByExternalId(guarantee.EXTERNAL_ID)

                val externalIdBody =
                    guarantee.EXTERNAL_ID.toRequestBody("text/plain".toMediaTypeOrNull())
                val descripcionFallaBody =
                    guarantee.DESCRIPCION_FALLA.toRequestBody("text/plain".toMediaTypeOrNull())
                val observacionesBody =
                    guarantee.OBSERVACIONES?.toRequestBody("text/plain".toMediaTypeOrNull())

                val imageParts = images.mapNotNull { image ->
                    val file = java.io.File(image.IMG_PATH)
                    if (file.exists()) {
                        val requestFile = file.asRequestBody(image.IMG_MIME.toMediaTypeOrNull())
                        val filename =
                            "${image.ID}.${image.IMG_MIME.split("/").getOrNull(1) ?: "jpg"}"
                        okhttp3.MultipartBody.Part.createFormData("imagenes", filename, requestFile)
                    } else {
                        null
                    }
                }

                api.saveGuaranteeWithImages(
                    doctoCcId = guarantee.DOCTO_CC_ID,
                    externalId = externalIdBody,
                    descripcionFalla = descripcionFallaBody,
                    observaciones = observacionesBody,
                    imagenes = imageParts
                )

                guaranteeStore.markGuaranteeAsUploaded(guarantee.EXTERNAL_ID)
            } catch (e: Exception) {
                Log.e("GuaranteesViewModel", "Error posting guarantee: ${e.message}")
            }
        }
    }

    fun enqueueGuaranteeForUpload(guarantee: GuaranteeEntity) {
        enqueuePendingGuaranteesWorker(
            getApplication(),
            guarantee.EXTERNAL_ID
        )
    }

    fun syncPendingGuarantees() {
        viewModelScope.launch {
            try {
                val pendingGuarantees =
                    guaranteeStore.getAllGuarantees().filter { it.UPLOADED == 0 }
                pendingGuarantees.forEach { guarantee ->
                    enqueuePendingGuaranteesWorker(
                        getApplication(),
                        guarantee.EXTERNAL_ID,
                        replace = true
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "GuaranteesViewModel",
                    "Error sincronizando garantías pendientes: ${e.message}"
                )
            }
        }
    }

    fun syncPendingGuaranteeEvents() {
        viewModelScope.launch {
            try {
                val pendingEvents =
                    guaranteeStore.getAllGuaranteeEvents().filter { it.ENVIADO == 0 }
                if (pendingEvents.isNotEmpty()) {
                    enqueuePendingGuaranteeEventsWorker(
                        getApplication(),
                        replace = true
                    )
                }
            } catch (e: Exception) {
                Log.e("GuaranteesViewModel", "Error sincronizando eventos pendientes: ${e.message}")
            }
        }
    }

}