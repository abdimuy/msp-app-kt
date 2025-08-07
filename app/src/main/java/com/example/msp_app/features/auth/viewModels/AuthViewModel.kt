package com.example.msp_app.features.auth.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.sale.SalesLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import com.example.msp_app.data.models.sale.toSale
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val auth = FirebaseAuth.getInstance()
    private val _userData = MutableStateFlow<ResultState<User?>>(ResultState.Idle)
    val userData: StateFlow<ResultState<User?>> = _userData

    private val paymentStore = PaymentsLocalDataSource(application.applicationContext)
    private val visitsStore = VisitsLocalDataSource(application.applicationContext)
    private val salesStore = SalesLocalDataSource(application.applicationContext)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _updateStartOfWeekDateState =
        MutableStateFlow<ResultState<User?>>(ResultState.Idle)
    val updateStartOfWeekDateState: StateFlow<ResultState<User?>> = _updateStartOfWeekDateState

    private var userDataListener: ListenerRegistration? = null
    private var hasCheckedVersion = false

    init {
        auth.addAuthStateListener {
            _currentUser.value = it.currentUser
            val email = it.currentUser?.email
            if (email != null) {
                getUserDataByEmail(email)
            } else {
                userDataListener?.remove()
                userDataListener = null
                _userData.value = ResultState.Idle
            }
        }
    }

    fun logout() {
        auth.signOut()
    }


    fun getUserDataByEmail(email: String) {
        userDataListener?.remove()
        hasCheckedVersion = false

        _userData.value = ResultState.Loading

        val firestore = FirebaseFirestore.getInstance()

        userDataListener = firestore.collection(Constants.USERS_COLLECTION)
            .whereEqualTo("EMAIL", email)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _userData.value = ResultState.Error(error.message ?: "Error desconocido")
                    Log.e("AuthViewModel", "Error al escuchar datos del usuario: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.firstOrNull()
                    val data = doc?.let {
                        it.toObject(User::class.java)?.copy(ID = it.id)
                    }
                    _userData.value = ResultState.Success(data)

                    if (!hasCheckedVersion && data != null) {
                        hasCheckedVersion = true
                        updateAppVersion(data.ID, data)
                    }
                } else {
                    _userData.value = ResultState.Success(null)
                }
            }
    }

    fun updateStartOfWeekDate() {
        viewModelScope.launch {
            val current = _userData.value
            if (current is ResultState.Success && current.data != null) {
                val pendingPayments = paymentStore.getPendingPayments()
                if (pendingPayments.isNotEmpty()) {
                    _updateStartOfWeekDateState.value =
                        ResultState.Error("Hay ${pendingPayments.size} pagos pendientes")
                    return@launch
                }
                val pendingVisits = visitsStore.getPendingVisits()
                if (pendingVisits.isNotEmpty()) {
                    _updateStartOfWeekDateState.value =
                        ResultState.Error("Hay ${pendingVisits.size} visitas pendientes")
                    return@launch
                }

                val startOfWeekDate = Timestamp.now()

                try {
                    FirebaseFirestore.getInstance()
                        .collection(Constants.USERS_COLLECTION)
                        .document(current.data.ID)
                        .update(Constants.START_OF_WEEK_DATE_FIELD, startOfWeekDate)
                        .await()

                    val sales = salesStore.getAll().map {
                        it.toDomain()
                    }
                    val salesWithoutStatus = sales.map {
                        it.copy(
                            ESTADO_COBRANZA = EstadoCobranza.PENDIENTE
                        ).toSale().toEntity()
                    }
                    salesStore.saveAll(salesWithoutStatus)

                    _userData.value = ResultState.Success(
                        current.data.copy(FECHA_CARGA_INICIAL = startOfWeekDate)
                    )
                    _updateStartOfWeekDateState.value = ResultState.Success(
                        current.data.copy(FECHA_CARGA_INICIAL = startOfWeekDate)
                    )
                } catch (e: Exception) {
                    _updateStartOfWeekDateState.value = ResultState.Error(
                        e.message ?: "Error al actualizar la fecha de inicio de semana"
                    )
                }
            } else {
                _updateStartOfWeekDateState.value = ResultState.Error(
                    "No se pudo actualizar la fecha de inicio de semana"
                )
            }
        }
    }

    fun updateAppVersion(userId: String, userData: User) {
        viewModelScope.launch {
            val appVersion = Constants.APP_VERSION
            val versionDate = Timestamp.now()

            try {
                FirebaseFirestore.getInstance()
                    .collection(Constants.USERS_COLLECTION)
                    .document(userId)
                    .update(
                        Constants.VERSION_APP, appVersion,
                        Constants.FECHA_VERSION_APP, versionDate
                    ).await()
            } catch (e: Exception) {
                Log.e("APPVERSION", "Error al actualizar la versión: ${e.message}")
            }
        }
    }

    fun clearUpdateStartOfWeekDateState() {
        _updateStartOfWeekDateState.value = ResultState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        userDataListener?.remove()
        userDataListener = null
    }
}

