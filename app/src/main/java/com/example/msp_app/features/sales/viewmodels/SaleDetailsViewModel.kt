package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.DateUtils
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.sale.toDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SaleDetailsViewModel(application: Application) : AndroidViewModel(application) {
    private val saleStore = SalesLocalDataSource(application.applicationContext)

    private val _saleState = MutableStateFlow<ResultState<Sale?>>(ResultState.Idle)
    val saleState: StateFlow<ResultState<Sale?>> = _saleState

    fun loadSaleDetails(saleId: Int) {
        viewModelScope.launch {
            _saleState.value = ResultState.Loading
            try {
                val res = saleStore.getById(saleId)
                if (res == null) {
                    _saleState.value = ResultState.Error("No se encontró la venta con ID: $saleId")
                    return@launch
                }

                val sale = res.toDomain().copy(
                    FECHA = DateUtils.formatIsoDate(res.FECHA)
                )
                _saleState.value = ResultState.Success(sale)
            } catch (e: Exception) {
                _saleState.value =
                    ResultState.Error(e.message ?: "Error al cargar los detalles de la venta")
            }
        }
    }
}