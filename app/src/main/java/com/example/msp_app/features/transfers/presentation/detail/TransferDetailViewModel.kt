package com.example.msp_app.features.transfers.presentation.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.features.transfers.data.api.TransfersApiService
import com.example.msp_app.features.transfers.data.repository.TransfersRepository
import com.example.msp_app.features.transfers.domain.models.TransferWithDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for transfer detail screen
 * Manages detailed transfer information and actions
 */
class TransferDetailViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: TransfersRepository by lazy {
        val apiService = ApiProvider.create(TransfersApiService::class.java)
        TransfersRepository(apiService)
    }

    private val _transferDetailState = MutableStateFlow<ResultState<TransferWithDetails>>(ResultState.Idle)
    val transferDetailState: StateFlow<ResultState<TransferWithDetails>> = _transferDetailState.asStateFlow()

    private val _expandedProducts = MutableStateFlow<Set<Int>>(emptySet())
    val expandedProducts: StateFlow<Set<Int>> = _expandedProducts.asStateFlow()

    /**
     * Load transfer details
     */
    fun loadTransferDetail(doctoInId: Int) {
        viewModelScope.launch {
            _transferDetailState.value = ResultState.Loading
            try {
                val result = repository.getTransferDetail(doctoInId)

                result.fold(
                    onSuccess = { transferDetail ->
                        _transferDetailState.value = ResultState.Success(transferDetail)
                    },
                    onFailure = { error ->
                        _transferDetailState.value = ResultState.Error(error.message ?: "Error al cargar detalle")
                    }
                )
            } catch (e: Exception) {
                _transferDetailState.value = ResultState.Error(e.message ?: "Error al cargar detalle")
            }
        }
    }

    /**
     * Refresh transfer details
     */
    fun refreshTransferDetail(doctoInId: Int) {
        loadTransferDetail(doctoInId)
    }

    /**
     * Toggle product expansion
     */
    fun toggleProductExpansion(productId: Int) {
        val currentExpanded = _expandedProducts.value.toMutableSet()
        if (currentExpanded.contains(productId)) {
            currentExpanded.remove(productId)
        } else {
            currentExpanded.add(productId)
        }
        _expandedProducts.value = currentExpanded
    }

    /**
     * Expand all products
     */
    fun expandAllProducts() {
        val currentState = _transferDetailState.value
        if (currentState is ResultState.Success) {
            val productIds = currentState.data.details.map { it.articuloId }.toSet()
            _expandedProducts.value = productIds
        }
    }

    /**
     * Collapse all products
     */
    fun collapseAllProducts() {
        _expandedProducts.value = emptySet()
    }

    /**
     * Check if a product is expanded
     */
    fun isProductExpanded(productId: Int): Boolean {
        return _expandedProducts.value.contains(productId)
    }
}
