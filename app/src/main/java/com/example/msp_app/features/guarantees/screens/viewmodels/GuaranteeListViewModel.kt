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

    fun loadGuarantees() {
        viewModelScope.launch {
            _guarantees.value = guaranteeStore.getAllGuarantees()
        }
    }
}
