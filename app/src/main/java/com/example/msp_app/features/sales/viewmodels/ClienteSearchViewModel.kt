package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.data.local.entities.ClienteEntity
import com.example.msp_app.data.repository.ClienteRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class ClienteSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClienteRepository(application.applicationContext)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<List<ClienteEntity>>(emptyList())
    val results: StateFlow<List<ClienteEntity>> = _results

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _clienteCount = MutableStateFlow(0)
    val clienteCount: StateFlow<Int> = _clienteCount

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError

    init {
        loadCount()

        viewModelScope.launch {
            _query
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length >= 2) {
                        _isSearching.value = true
                        try {
                            _results.value = repository.searchClientes(query)
                        } catch (_: Exception) {
                            _results.value = emptyList()
                        } finally {
                            _isSearching.value = false
                        }
                    } else {
                        _results.value = emptyList()
                    }
                }
        }
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun syncFromServer() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            try {
                repository.syncFromServer()
                loadCount()
                // Re-run current search if active
                val currentQuery = _query.value
                if (currentQuery.length >= 2) {
                    _results.value = repository.searchClientes(currentQuery)
                }
            } catch (e: Exception) {
                _syncError.value = e.message ?: "Error al sincronizar"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    private fun loadCount() {
        viewModelScope.launch {
            try {
                _clienteCount.value = repository.getCount()
            } catch (_: Exception) {
                _clienteCount.value = 0
            }
        }
    }
}
