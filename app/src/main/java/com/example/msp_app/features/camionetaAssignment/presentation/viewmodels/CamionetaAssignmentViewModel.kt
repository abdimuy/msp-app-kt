package com.example.msp_app.features.camionetaAssignment.presentation.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.utils.ResultState
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.features.camionetaAssignment.data.repository.CamionetaAssignmentRepository
import com.example.msp_app.features.camionetaAssignment.domain.models.Camioneta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CamionetaAssignmentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CamionetaAssignmentRepository()

    // State for the list of camionetas with their assigned users
    private val _camionetasState = MutableStateFlow<ResultState<List<Camioneta>>>(ResultState.Idle)
    val camionetasState: StateFlow<ResultState<List<Camioneta>>> = _camionetasState.asStateFlow()

    // State for users without assignment (available for assignment)
    private val _usuariosSinAsignarState = MutableStateFlow<ResultState<List<User>>>(ResultState.Idle)
    val usuariosSinAsignarState: StateFlow<ResultState<List<User>>> = _usuariosSinAsignarState.asStateFlow()

    // State for assignment operations (assign/unassign)
    private val _assignmentState = MutableStateFlow<ResultState<Unit>>(ResultState.Idle)
    val assignmentState: StateFlow<ResultState<Unit>> = _assignmentState.asStateFlow()

    // Currently selected camioneta for assignment dialog
    private val _selectedCamioneta = MutableStateFlow<Camioneta?>(null)
    val selectedCamioneta: StateFlow<Camioneta?> = _selectedCamioneta.asStateFlow()

    // State for showing assignment dialog
    private val _showAssignDialog = MutableStateFlow(false)
    val showAssignDialog: StateFlow<Boolean> = _showAssignDialog.asStateFlow()

    // State for showing unassign confirmation dialog
    private val _showUnassignDialog = MutableStateFlow(false)
    val showUnassignDialog: StateFlow<Boolean> = _showUnassignDialog.asStateFlow()

    // User selected for unassignment
    private val _userToUnassign = MutableStateFlow<Pair<String, String>?>(null) // userId, userName
    val userToUnassign: StateFlow<Pair<String, String>?> = _userToUnassign.asStateFlow()

    init {
        loadCamionetas()
    }

    /**
     * Loads all camionetas with their assigned users.
     */
    fun loadCamionetas() {
        viewModelScope.launch {
            _camionetasState.value = ResultState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getCamionetasConUsuarios()
                }

                result.fold(
                    onSuccess = { camionetas ->
                        _camionetasState.value = ResultState.Success(camionetas)
                    },
                    onFailure = { error ->
                        _camionetasState.value = ResultState.Error(
                            message = error.message ?: "Error al cargar camionetas",
                            exception = error
                        )
                    }
                )
            } catch (e: Exception) {
                _camionetasState.value = ResultState.Error(
                    message = e.message ?: "Error inesperado",
                    exception = e
                )
            }
        }
    }

    /**
     * Loads users that are not assigned to any camioneta.
     */
    fun loadUsuariosSinAsignar() {
        viewModelScope.launch {
            _usuariosSinAsignarState.value = ResultState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getUsuariosSinAsignar()
                }

                result.fold(
                    onSuccess = { users ->
                        _usuariosSinAsignarState.value = ResultState.Success(users)
                    },
                    onFailure = { error ->
                        _usuariosSinAsignarState.value = ResultState.Error(
                            message = error.message ?: "Error al cargar usuarios",
                            exception = error
                        )
                    }
                )
            } catch (e: Exception) {
                _usuariosSinAsignarState.value = ResultState.Error(
                    message = e.message ?: "Error inesperado",
                    exception = e
                )
            }
        }
    }

    /**
     * Opens the assignment dialog for a specific camioneta.
     */
    fun openAssignDialog(camioneta: Camioneta) {
        _selectedCamioneta.value = camioneta
        loadUsuariosSinAsignar()
        _showAssignDialog.value = true
    }

    /**
     * Closes the assignment dialog.
     */
    fun closeAssignDialog() {
        _showAssignDialog.value = false
        _selectedCamioneta.value = null
        _usuariosSinAsignarState.value = ResultState.Idle
    }

    /**
     * Opens the unassign confirmation dialog.
     */
    fun openUnassignDialog(userId: String, userName: String) {
        _userToUnassign.value = Pair(userId, userName)
        _showUnassignDialog.value = true
    }

    /**
     * Closes the unassign confirmation dialog.
     */
    fun closeUnassignDialog() {
        _showUnassignDialog.value = false
        _userToUnassign.value = null
    }

    /**
     * Assigns a user to a camioneta.
     * Validates that the camioneta hasn't reached its maximum capacity (3 users).
     */
    fun asignarUsuario(userId: String, almacenId: Int) {
        viewModelScope.launch {
            _assignmentState.value = ResultState.Loading
            try {
                // First validate the assignment
                val validationResult = withContext(Dispatchers.IO) {
                    repository.validarAsignacion(almacenId)
                }

                if (validationResult.isFailure) {
                    _assignmentState.value = ResultState.Error(
                        message = validationResult.exceptionOrNull()?.message ?: "Error de validacion"
                    )
                    return@launch
                }

                val canAssign = validationResult.getOrThrow()
                if (!canAssign) {
                    _assignmentState.value = ResultState.Error(
                        message = "Esta camioneta ya tiene el maximo de ${Camioneta.MAX_USUARIOS_POR_CAMIONETA} usuarios asignados"
                    )
                    return@launch
                }

                // Proceed with assignment
                val result = withContext(Dispatchers.IO) {
                    repository.asignarCamioneta(userId, almacenId)
                }

                result.fold(
                    onSuccess = {
                        _assignmentState.value = ResultState.Success(Unit)
                        closeAssignDialog()
                        loadCamionetas() // Refresh the list
                    },
                    onFailure = { error ->
                        _assignmentState.value = ResultState.Error(
                            message = error.message ?: "Error al asignar usuario",
                            exception = error
                        )
                    }
                )
            } catch (e: Exception) {
                _assignmentState.value = ResultState.Error(
                    message = e.message ?: "Error inesperado",
                    exception = e
                )
            }
        }
    }

    /**
     * Removes the camioneta assignment from a user.
     */
    fun desasignarUsuario(userId: String) {
        viewModelScope.launch {
            _assignmentState.value = ResultState.Loading
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.desasignarCamioneta(userId)
                }

                result.fold(
                    onSuccess = {
                        _assignmentState.value = ResultState.Success(Unit)
                        closeUnassignDialog()
                        loadCamionetas() // Refresh the list
                    },
                    onFailure = { error ->
                        _assignmentState.value = ResultState.Error(
                            message = error.message ?: "Error al desasignar usuario",
                            exception = error
                        )
                    }
                )
            } catch (e: Exception) {
                _assignmentState.value = ResultState.Error(
                    message = e.message ?: "Error inesperado",
                    exception = e
                )
            }
        }
    }

    /**
     * Clears the assignment state (useful after showing error/success messages).
     */
    fun clearAssignmentState() {
        _assignmentState.value = ResultState.Idle
    }
}
