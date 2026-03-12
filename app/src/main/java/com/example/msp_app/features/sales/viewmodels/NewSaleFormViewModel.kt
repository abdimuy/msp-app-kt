package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.draft.DraftCombo
import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.core.draft.SaleDraftManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NewSaleFormViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext
    val draftManager = SaleDraftManager(context)

    private val _formState = MutableStateFlow(NewSaleFormState())
    val formState: StateFlow<NewSaleFormState> = _formState.asStateFlow()

    // --- Field update methods ---

    fun updateClientName(value: String) {
        _formState.update { it.copy(clientName = value) }
    }

    fun updateSelectedClienteId(value: Int?) {
        _formState.update { it.copy(selectedClienteId = value) }
    }

    fun updatePhone(value: String) {
        _formState.update { state ->
            val newErrors = if (value.isNotEmpty() || state.errors.phone) {
                state.errors.copy(
                    phone = !NewSaleFormValidator.validatePhone(value, state.tipoVenta)
                )
            } else {
                state.errors
            }
            state.copy(phone = value, errors = newErrors)
        }
    }

    fun updateStreet(value: String) {
        _formState.update { state ->
            val newErrors = if (value.isNotEmpty() || state.errors.location) {
                state.errors.copy(location = !NewSaleFormValidator.validateStreet(value))
            } else {
                state.errors
            }
            state.copy(street = value, errors = newErrors)
        }
    }

    fun updateNumero(value: String) {
        _formState.update { it.copy(numero = value) }
    }

    fun updateColonia(value: String) {
        _formState.update { it.copy(colonia = value) }
    }

    fun updatePoblacion(value: String) {
        _formState.update { it.copy(poblacion = value) }
    }

    fun updateCiudad(value: String) {
        _formState.update { it.copy(ciudad = value) }
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        _formState.update {
            it.copy(
                latitude = latitude,
                longitude = longitude,
                locationPermissionGranted = true,
                hasValidLocation = latitude != 0.0 && longitude != 0.0
            )
        }
    }

    fun updateTipoVenta(value: String) {
        _formState.update { state ->
            if (value == "CONTADO") {
                state.copy(
                    tipoVenta = value,
                    selectedZoneId = null,
                    selectedZoneName = "",
                    errors = state.errors.copy(zone = false)
                )
            } else {
                state.copy(tipoVenta = value)
            }
        }
    }

    fun updateZone(zoneId: Int?, zoneName: String) {
        _formState.update {
            it.copy(
                selectedZoneId = zoneId,
                selectedZoneName = zoneName,
                errors = it.errors.copy(zone = false)
            )
        }
    }

    fun updateDownpayment(value: String) {
        _formState.update { state ->
            val newErrors = if (value.isNotEmpty() || state.errors.downpayment) {
                state.errors.copy(downpayment = !NewSaleFormValidator.validateDownpayment(value))
            } else {
                state.errors
            }
            state.copy(downpayment = value, errors = newErrors)
        }
    }

    fun updateInstallment(value: String) {
        _formState.update { state ->
            val newErrors = if (value.isNotEmpty() || state.errors.installment) {
                state.errors.copy(
                    installment = !NewSaleFormValidator.validateInstallment(value, state.tipoVenta)
                )
            } else {
                state.errors
            }
            state.copy(installment = value, errors = newErrors)
        }
    }

    fun updateGuarantor(value: String) {
        _formState.update { it.copy(guarantor = value) }
    }

    fun updateCollectionDay(value: String) {
        _formState.update { state ->
            state.copy(
                collectionDay = value,
                errors = state.errors.copy(
                    collectionDay = !NewSaleFormValidator.validateCollectionDay(
                        value,
                        state.tipoVenta
                    )
                )
            )
        }
    }

    fun updatePaymentFrequency(value: String) {
        _formState.update { state ->
            state.copy(
                paymentFrequency = value,
                errors = state.errors.copy(
                    paymentFrequency = !NewSaleFormValidator.validatePaymentFrequency(
                        value,
                        state.tipoVenta
                    )
                )
            )
        }
    }

    fun updateNote(value: String) {
        _formState.update { it.copy(note = value) }
    }

    // --- Image methods ---

    fun addImageUri(uri: Uri) {
        _formState.update { it.copy(imageUris = it.imageUris + uri) }
    }

    fun removeImageUri(uri: Uri) {
        _formState.update { it.copy(imageUris = it.imageUris.filterNot { u -> u == uri }) }
    }

    fun createCameraUri(): Uri {
        val imageFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            imageFile
        )
    }

    fun validateImageSize(uri: Uri): Boolean {
        val maxSizeInBytes = 20 * 1000 * 1000
        val imageSize = try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (e: Exception) {
            0L
        }
        return imageSize <= maxSizeInBytes
    }

    fun processPickedImages(uris: List<Uri>): Boolean {
        var hasOversized = false
        uris.forEach { uri ->
            if (validateImageSize(uri)) {
                val persistentPath = draftManager.copyImageToPersistentStorage(uri)
                val file = File(persistentPath)
                val persistentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                addImageUri(persistentUri)
            } else {
                hasOversized = true
            }
        }
        return hasOversized
    }

    fun processCameraImage(uri: Uri): Boolean {
        return if (validateImageSize(uri)) {
            val persistentPath = draftManager.copyImageToPersistentStorage(uri)
            val file = File(persistentPath)
            val persistentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            addImageUri(persistentUri)
            false // no size error
        } else {
            true // has size error
        }
    }

    // --- Validation ---

    fun validateFields(hasProducts: Boolean): Boolean {
        val state = _formState.value
        val errors = NewSaleFormValidator.validateAll(state, hasProducts)
        val locationDataValid = NewSaleFormValidator.validateLocation(
            state.latitude,
            state.longitude,
            state.locationPermissionGranted
        )

        _formState.update {
            it.copy(
                hasValidLocation = locationDataValid,
                errors = errors
            )
        }

        return NewSaleFormValidator.isAllValid(state, hasProducts)
    }

    // --- Draft methods ---

    suspend fun loadDraftSuspend(): SaleDraft? {
        return draftManager.loadDraft()
    }

    fun applyDraft(draft: SaleDraft) {
        _formState.update {
            it.copy(
                clientName = draft.clientName,
                phone = draft.phone,
                street = draft.street,
                numero = draft.numero,
                colonia = draft.colonia,
                poblacion = draft.poblacion,
                ciudad = draft.ciudad,
                tipoVenta = draft.tipoVenta,
                downpayment = draft.downpayment,
                installment = draft.installment,
                guarantor = draft.guarantor,
                note = draft.note,
                collectionDay = draft.collectionDay,
                paymentFrequency = draft.paymentFrequency,
                latitude = draft.latitude,
                longitude = draft.longitude,
                selectedZoneId = draft.zonaClienteId,
                selectedZoneName = draft.zonaClienteNombre,
                imageUris = draft.imageUris.mapNotNull { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            )
        }
    }

    fun saveDraftAuto(saleItems: List<Any>, comboItems: List<ComboItem>) {
        val state = _formState.value
        if (state.saleCompleted) return

        viewModelScope.launch {
            val imagePaths = state.imageUris.mapNotNull { uri ->
                try {
                    if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") {
                        val path = uri.path?.substringAfter("draft_images/")
                        if (path != null) "${context.filesDir}/draft_images/$path" else null
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            val draftCombos = comboItems.map { combo ->
                DraftCombo(
                    comboId = combo.comboId,
                    nombreCombo = combo.nombreCombo,
                    precioLista = combo.precioLista,
                    precioCortoPlazo = combo.precioCortoPlazo,
                    precioContado = combo.precioContado
                )
            }

            val draft = SaleDraft(
                clientName = state.clientName,
                phone = state.phone,
                street = state.street,
                numero = state.numero,
                colonia = state.colonia,
                poblacion = state.poblacion,
                ciudad = state.ciudad,
                tipoVenta = state.tipoVenta,
                downpayment = state.downpayment,
                installment = state.installment,
                guarantor = state.guarantor,
                note = state.note,
                collectionDay = state.collectionDay,
                paymentFrequency = state.paymentFrequency,
                latitude = state.latitude,
                longitude = state.longitude,
                imageUris = imagePaths,
                productsJson = draftManager.saleItemsToJson(saleItems),
                combosJson = draftManager.combosToJson(draftCombos),
                zonaClienteId = state.selectedZoneId,
                zonaClienteNombre = state.selectedZoneName
            )
            draftManager.saveDraft(draft)
        }
    }

    fun clearDraft() {
        viewModelScope.launch {
            draftManager.clearDraft()
        }
    }

    fun clearOldDrafts() {
        viewModelScope.launch {
            draftManager.clearOldDrafts(maxAgeDays = 7)
        }
    }

    // --- Save data preparation ---

    data class SaleCreationData(
        val saleId: String,
        val saleDate: String,
        val clientName: String,
        val imageUris: List<Uri>,
        val latitude: Double,
        val longitude: Double,
        val address: String,
        val numero: String?,
        val colonia: String?,
        val poblacion: String?,
        val ciudad: String?,
        val tipoVenta: String,
        val installment: Double,
        val downpayment: Double,
        val phone: String,
        val paymentFrequency: String,
        val guarantor: String,
        val note: String,
        val collectionDay: String,
        val zonaClienteId: Int?,
        val zonaClienteNombre: String?,
        val clienteId: Int?
    )

    fun buildSaleData(): SaleCreationData {
        val state = _formState.value
        val tipoVenta = state.tipoVenta
        return SaleCreationData(
            saleId = UUID.randomUUID().toString(),
            saleDate = java.time.Instant.now().toString(),
            clientName = state.clientName,
            imageUris = state.imageUris,
            latitude = state.latitude,
            longitude = state.longitude,
            address = state.street,
            numero = state.numero.ifBlank { null },
            colonia = state.colonia.ifBlank { null },
            poblacion = state.poblacion.ifBlank { null },
            ciudad = state.ciudad.ifBlank { null },
            tipoVenta = tipoVenta,
            installment = if (tipoVenta == "CONTADO") 0.0 else state.installment.toDoubleOrNull() ?: 0.0,
            downpayment = if (tipoVenta == "CONTADO") 0.0 else state.downpayment.toDoubleOrNull() ?: 0.0,
            phone = state.phone.ifBlank { "" },
            paymentFrequency = if (tipoVenta == "CONTADO") "" else state.paymentFrequency,
            guarantor = if (tipoVenta == "CONTADO") "" else state.guarantor,
            note = state.note,
            collectionDay = if (tipoVenta == "CONTADO") "" else state.collectionDay,
            zonaClienteId = state.selectedZoneId,
            zonaClienteNombre = state.selectedZoneName,
            clienteId = state.selectedClienteId
        )
    }

    fun markSaleCompleted() {
        _formState.update { it.copy(saleCompleted = true) }
    }

    fun clearAllFields() {
        _formState.value = NewSaleFormState()
    }
}
