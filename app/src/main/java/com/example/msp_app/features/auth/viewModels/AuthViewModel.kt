package com.example.msp_app.features.auth.viewModels

import android.app.Application
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
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

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
                val firestore = FirebaseFirestore.getInstance()
                val TIMEOUT_MS = 3000L

                val serverSnapshot = withTimeoutOrNull(TIMEOUT_MS) {
                    firestore.collection(Constants.USERS_COLLECTION)
                        .whereEqualTo("EMAIL", email)
                        .get()
                        .await()
                }

                val querySnapshot = serverSnapshot
                    ?: firestore.collection(Constants.USERS_COLLECTION)
                        .whereEqualTo("EMAIL", email)
                        .get(Source.CACHE)
                        .await()

                val data = querySnapshot.documents.firstOrNull()?.let { doc ->
                    doc.toObject(User::class.java)?.copy(ID = doc.id)
                }
                _userData.value = ResultState.Success(data)

                data?.let {
                    updateAppVersion()
                }
            } catch (e: Exception) {
                _userData.value = ResultState.Error(e.message ?: "Error desconocido")
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

    fun updateAppVersion() {
        viewModelScope.launch {
            val currentResult = _userData.value
            if (currentResult is ResultState.Success && currentResult.data != null) {
                val userId = currentResult.data.ID
                val appVersion = Constants.APP_VERSION
                val versionDate = Timestamp.now()

                try {
                    FirebaseFirestore.getInstance()
                        .collection(Constants.USERS_COLLECTION)
                        .document(userId)
                        .update(
                            "VERSION_APP", appVersion,
                            "FECHA_VERSION_APP", versionDate
                        ).await()

                    val updatedUser = currentResult.data.copy(
                        VERSION_APP = appVersion,
                        FECHA_VERSION_APP = versionDate
                    )
                    _userData.value = ResultState.Success(updatedUser)

                } catch (e: Exception) {
                }
            }
        }
    }

    fun clearUpdateStartOfWeekDateState() {
        _updateStartOfWeekDateState.value = ResultState.Idle
    }
}

