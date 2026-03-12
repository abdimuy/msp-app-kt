package com.example.msp_app.features.guarantees.screens.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.data.local.datasource.guarantee.GuaranteesLocalDataSource
import com.example.msp_app.data.local.entities.GuaranteeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GuaranteeListViewModel(application: Application) : AndroidViewModel(application) {
    private val guaranteeStore = GuaranteesLocalDataSource(application.applicationContext)

    private val _guarantees = MutableStateFlow<List<GuaranteeEntity>>(emptyList())
    val guarantees: StateFlow<List<GuaranteeEntity>> = _guarantees

    private val _selectedGuarantee = MutableStateFlow<GuaranteeEntity?>(null)
    val selectedGuarantee: StateFlow<GuaranteeEntity?> = _selectedGuarantee

    fun loadGuarantees() {
        viewModelScope.launch {
            _guarantees.value = guaranteeStore.getStandaloneGuarantees()
        }
    }

    fun loadGuaranteeById(id: Int) {
        viewModelScope.launch {
            _selectedGuarantee.value = guaranteeStore.getGuaranteeById(id)
        }
    }
}
