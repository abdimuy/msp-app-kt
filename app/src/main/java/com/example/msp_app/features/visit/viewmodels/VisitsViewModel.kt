package com.example.msp_app.features.visit.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.VisitStatusMapper
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import com.example.msp_app.data.models.visit.toDomain
import com.example.msp_app.data.models.visit.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VisitsViewModel(application: Application) : AndroidViewModel(application) {
    private val visitStore = VisitsLocalDataSource(application.applicationContext)

    private val api = ApiProvider.create(VisitsApi::class.java)

    private val _pendingVisits = MutableStateFlow<ResultState<List<Visit>>>(ResultState.Idle)
    val pendingVisits: StateFlow<ResultState<List<Visit>>> = _pendingVisits

    private val _saveVisitState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val savePaymentState: StateFlow<ResultState<Unit>> = _saveVisitState

    fun getPendingVisits() {
        viewModelScope.launch {
            _pendingVisits.value = ResultState.Loading
            try {
                val visits = withContext(Dispatchers.IO) {
                    visitStore.getPendingVisits().map { it.toDomain() }
                }
                _pendingVisits.value = ResultState.Success(visits)
            } catch (e: Exception) {
                _pendingVisits.value =
                    ResultState.Error(e.message ?: "Error obteniendo visitas pendientes")
            }
        }
    }

    fun saveVisit(visit: Visit, saleId: Int) {
        viewModelScope.launch {
            _saveVisitState.value = ResultState.Loading

            val status = VisitStatusMapper.map(visit.TIPO_VISITA)

            try {
                withContext(Dispatchers.IO) {
                    visitStore.insertVisitAndUpdateState(
                        saleId = saleId,
                        visit = visit.toEntity(),
                        newState = status
                    )
                }
                _saveVisitState.value = ResultState.Success(Unit)
            } catch (e: Exception) {
                _saveVisitState.value = ResultState.Error(e.message ?: "Error guardando pago")
            }
        }
    }
}