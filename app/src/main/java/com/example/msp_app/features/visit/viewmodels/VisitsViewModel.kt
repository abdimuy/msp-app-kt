package com.example.msp_app.features.visit.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.core.utils.VisitStatusMapper
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
import com.example.msp_app.data.models.visit.toDomain
import com.example.msp_app.data.models.visit.toEntity
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VisitsViewModel(application: Application) : AndroidViewModel(application) {
    private val visitStore = VisitsLocalDataSource(application.applicationContext)
    private val saleStore = VisitsLocalDataSource(application.applicationContext)

    private val _pendingVisits = MutableStateFlow<ResultState<List<Visit>>>(ResultState.Idle)
    val pendingVisits: StateFlow<ResultState<List<Visit>>> = _pendingVisits

    private val _saveVisitState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val savePaymentState: StateFlow<ResultState<Unit>> = _saveVisitState

    private val _visitsByDateState = MutableStateFlow<ResultState<List<Visit>>>(ResultState.Idle)
    val visitsByDate: StateFlow<ResultState<List<Visit>>> = _visitsByDateState

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

    fun syncPendingVisits() {
        viewModelScope.launch {
            _pendingVisits.value = ResultState.Loading
            try {
                val pendingVisits = withContext(Dispatchers.IO) {
                    visitStore.getPendingVisits().map { it.toDomain() }
                }

                if (pendingVisits.isEmpty()) {
                    _pendingVisits.value = ResultState.Success(emptyList())
                    return@launch
                }

                for (visit in pendingVisits) {
                    enqueuePendingVisitsWorker(
                        visitId = visit.ID,
                        context = getApplication(),
                        replace = true
                    )
                }

                val newPendingVisits = visitStore.getPendingVisits().map { it.toDomain() }
                _pendingVisits.value = ResultState.Success(newPendingVisits)
            } catch (e: Exception) {
                _pendingVisits.value =
                    ResultState.Error(e.message ?: "Error sincronizando visitas pendientes")
            }
        }
    }

    fun saveVisit(visit: Visit, saleId: Int, newDate: String?) {
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
                    if (
                        newDate != null && newDate.isNotEmpty()
                    ) {
                        saleStore.updateTemporaryCollectionDate(
                            saleId = saleId,
                            newDate = newDate
                        )
                    }
                }
                _saveVisitState.value = ResultState.Success(Unit)
            } catch (e: Exception) {
                _saveVisitState.value = ResultState.Error(e.message ?: "Error guardando pago")
            }
        }
    }

    fun getVisitsByDate(startDate: String, endDate: String) {
        viewModelScope.launch {
            _visitsByDateState.value = ResultState.Loading

            val visits = withContext(Dispatchers.IO) {
                visitStore.getVisitsByDate(startDate, endDate)
                    .map { it.toDomain() }
            }
            _visitsByDateState.value = ResultState.Success(visits)
        }
    }
}