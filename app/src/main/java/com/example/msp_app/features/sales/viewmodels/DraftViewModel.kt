package com.example.msp_app.features.sales.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.features.sales.data.DraftData
import com.example.msp_app.features.sales.data.DraftManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class DraftViewModel(context: Context) : ViewModel() {

    private val draftManager = DraftManager(context)

    private val _draftState = MutableStateFlow<DraftState>(DraftState.Loading)
    val draftState: StateFlow<DraftState> = _draftState

    private val _draftDataFlow = MutableStateFlow<DraftData?>(null)

    init {
        // Observar cambios en el borrador
        viewModelScope.launch {
            draftManager.getDraftFlow().collect { draft ->
                if (draft != null) {
                    _draftState.value = DraftState.HasDraft(draft)
                } else {
                    _draftState.value = DraftState.NoDraft
                }
            }
        }

        // Guardado automático con debounce de 2 segundos
        viewModelScope.launch {
            _draftDataFlow
                .debounce(2000) // Espera 2 segundos después del último cambio
                .distinctUntilChanged()
                .collect { draftData ->
                    draftData?.let { saveDraftInternal(it) }
                }
        }
    }

    /**
     * Actualiza el borrador de forma reactiva (se guardará automáticamente con debounce)
     */
    fun updateDraft(draftData: DraftData) {
        _draftDataFlow.value = draftData
    }

    /**
     * Guarda el borrador inmediatamente sin debounce
     */
    fun saveDraftNow(draftData: DraftData) {
        viewModelScope.launch {
            saveDraftInternal(draftData)
        }
    }

    private suspend fun saveDraftInternal(draftData: DraftData) {
        try {
            // Solo guardar si hay datos relevantes
            if (draftData.hasRelevantData()) {
                draftManager.saveDraft(draftData)
            }
        } catch (e: Exception) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }

    /**
     * Carga el borrador y valida las URIs de imágenes
     */
    fun loadDraft(context: Context, onLoaded: (DraftData) -> Unit) {
        viewModelScope.launch {
            try {
                val draft = when (val state = _draftState.value) {
                    is DraftState.HasDraft -> state.draft
                    else -> null
                }

                draft?.let {
                    // Validar URIs de imágenes
                    val validImageUris = it.imageUris.filter { uriString ->
                        validateUri(context, uriString)
                    }

                    val validatedDraft = it.copy(imageUris = validImageUris)
                    onLoaded(validatedDraft)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Valida si una URI es accesible
     */
    private fun validateUri(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { true } == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Elimina el borrador
     */
    fun clearDraft() {
        viewModelScope.launch {
            draftManager.clearDraft()
            _draftDataFlow.value = null
        }
    }

    /**
     * Marca que el usuario rechazó el borrador
     */
    fun dismissDraft() {
        _draftState.value = DraftState.Dismissed
    }
}

sealed class DraftState {
    object Loading : DraftState()
    object NoDraft : DraftState()
    data class HasDraft(val draft: DraftData) : DraftState()
    object Dismissed : DraftState()
}

/**
 * Extensión para verificar si el borrador tiene datos relevantes
 */
private fun DraftData.hasRelevantData(): Boolean {
    return clientName.isNotBlank() ||
            phone.isNotBlank() ||
            location.isNotBlank() ||
            numero.isNotBlank() ||
            colonia.isNotBlank() ||
            poblacion.isNotBlank() ||
            ciudad.isNotBlank() ||
            downpayment.isNotBlank() ||
            installment.isNotBlank() ||
            guarantor.isNotBlank() ||
            note.isNotBlank() ||
            collectionday.isNotBlank() ||
            paymentfrequency.isNotBlank() ||
            imageUris.isNotEmpty() ||
            products.isNotEmpty() ||
            (latitude != 0.0 && longitude != 0.0)
}