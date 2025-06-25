package com.example.msp_app.features.auth.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _userData = MutableStateFlow<ResultState<User?>>(ResultState.Idle)
    val userData: StateFlow<ResultState<User?>> = _userData

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    init {
        auth.addAuthStateListener {
            _currentUser.value = it.currentUser
            val email = it.currentUser?.email
            if (email != null) {
                getUserDataByEmail(email)
            }
        }
    }

    fun logout() {
        auth.signOut()
    }


    fun getUserDataByEmail(email: String) {
        viewModelScope.launch {
            _userData.value = ResultState.Loading
            try {
                val querySnapshot = FirebaseFirestore.getInstance()
                    .collection(Constants.USERS_COLLECTION)
                    .whereEqualTo("EMAIL", email)
                    .get()
                    .await()

                val data = querySnapshot.documents.firstOrNull()?.let { doc ->
                    doc.toObject(User::class.java)
                        ?.copy(ID = doc.id)
                }
                _userData.value = ResultState.Success(data)
            } catch (e: Exception) {
                _userData.value = ResultState.Error(e.message ?: "Error desconocido")
            }
        }
    }
}
