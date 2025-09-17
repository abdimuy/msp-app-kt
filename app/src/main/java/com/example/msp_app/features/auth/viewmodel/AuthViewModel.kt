package com.example.msp_app.features.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    
    sealed class AuthState {
        object Loading : AuthState()
        object Unauthenticated : AuthState()
        data class Authenticated(val user: FirebaseUser) : AuthState()
        data class RequiresBiometric(val user: FirebaseUser) : AuthState()
    }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()
    
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        viewModelScope.launch {
            _authState.value = when (val user = firebaseAuth.currentUser) {
                null -> AuthState.Unauthenticated
                else -> AuthState.RequiresBiometric(user)
            }
        }
    }
    
    init {
        auth.addAuthStateListener(authStateListener)
    }
    
    fun onBiometricSuccess() {
        (_authState.value as? AuthState.RequiresBiometric)?.let {
            _authState.value = AuthState.Authenticated(it.user)
        }
    }
    
    fun onBiometricFailed() {
        _authState.value = AuthState.Unauthenticated
    }
    
    fun signOut() {
        auth.signOut()
    }
    
    fun onLoginSuccess() {
        // El AuthStateListener detectará automáticamente el cambio
        // y actualizará el estado a RequiresBiometric
    }
    
    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
    }
}