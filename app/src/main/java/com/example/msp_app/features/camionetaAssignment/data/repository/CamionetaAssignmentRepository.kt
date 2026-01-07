package com.example.msp_app.features.camionetaAssignment.data.repository

import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.features.camionetaAssignment.domain.models.Camioneta
import com.example.msp_app.features.camionetaAssignment.domain.models.UsuarioAsignado
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CamionetaAssignmentRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val warehousesApi: WarehousesApi get() = ApiProvider.create(WarehousesApi::class.java)

    companion object {
        private const val CONFIG_COLLECTION = "config"
        private const val ALMACENES_EXCLUIDOS_DOC = "almacenes_excluidos"
        private const val EXCLUIDOS_FIELD = "excluidos"
    }

    /**
     * Fetches all warehouses from API.
     */
    suspend fun getAllAlmacenes(): Result<List<WarehouseListResponse.Warehouse>> {
        return try {
            val response = warehousesApi.getAllWarehouses()
            if (response.error.isNullOrEmpty()) {
                Result.success(response.body)
            } else {
                Result.failure(Exception(response.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches the list of excluded warehouse IDs from Firestore config.
     * Returns default [19] (ALMACEN_GENERAL) if document doesn't exist.
     */
    suspend fun getAlmacenesExcluidos(): Result<List<Int>> {
        return try {
            val doc = firestore
                .collection(CONFIG_COLLECTION)
                .document(ALMACENES_EXCLUIDOS_DOC)
                .get()
                .await()

            if (doc.exists()) {
                @Suppress("UNCHECKED_CAST")
                val excluidos = doc.get(EXCLUIDOS_FIELD) as? List<Long> ?: listOf(Constants.ALMACEN_GENERAL_ID.toLong())
                Result.success(excluidos.map { it.toInt() })
            } else {
                // Default: exclude ALMACEN_GENERAL
                Result.success(listOf(Constants.ALMACEN_GENERAL_ID))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches all users from Firestore.
     */
    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = firestore
                .collection(Constants.USERS_COLLECTION)
                .get()
                .await()

            val users = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)?.copy(ID = doc.id)
            }

            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Returns a Flow that emits updates when users collection changes.
     * Useful for real-time updates of assignments.
     */
    fun observeUsers(): Flow<Result<List<User>>> = callbackFlow {
        var registration: ListenerRegistration? = null

        registration = firestore
            .collection(Constants.USERS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val users = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(User::class.java)?.copy(ID = doc.id)
                    }
                    trySend(Result.success(users))
                }
            }

        awaitClose {
            registration?.remove()
        }
    }

    /**
     * Assigns a camioneta to a user.
     * Updates the CAMIONETA_ASIGNADA field in the user's Firestore document.
     */
    suspend fun asignarCamioneta(userId: String, almacenId: Int): Result<Unit> {
        return try {
            firestore
                .collection(Constants.USERS_COLLECTION)
                .document(userId)
                .update("CAMIONETA_ASIGNADA", almacenId)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Removes the camioneta assignment from a user.
     * Sets CAMIONETA_ASIGNADA to null.
     */
    suspend fun desasignarCamioneta(userId: String): Result<Unit> {
        return try {
            firestore
                .collection(Constants.USERS_COLLECTION)
                .document(userId)
                .update("CAMIONETA_ASIGNADA", FieldValue.delete())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches and combines all data to return a list of Camionetas with their assigned users.
     * Filters out excluded warehouses.
     */
    suspend fun getCamionetasConUsuarios(): Result<List<Camioneta>> {
        return try {
            // Fetch all data in parallel
            val almacenesResult = getAllAlmacenes()
            val excluidosResult = getAlmacenesExcluidos()
            val usersResult = getAllUsers()

            // Check for errors
            if (almacenesResult.isFailure) {
                return Result.failure(almacenesResult.exceptionOrNull() ?: Exception("Error fetching almacenes"))
            }
            if (excluidosResult.isFailure) {
                return Result.failure(excluidosResult.exceptionOrNull() ?: Exception("Error fetching excluidos"))
            }
            if (usersResult.isFailure) {
                return Result.failure(usersResult.exceptionOrNull() ?: Exception("Error fetching users"))
            }

            val almacenes = almacenesResult.getOrThrow()
            val excluidos = excluidosResult.getOrThrow()
            val users = usersResult.getOrThrow()

            // Filter out excluded warehouses (these are not camionetas)
            val camionetas = almacenes
                .filter { it.ALMACEN_ID !in excluidos }
                .map { almacen ->
                    val usuariosAsignados = users
                        .filter { user -> user.CAMIONETA_ASIGNADA == almacen.ALMACEN_ID }
                        .map { user ->
                            UsuarioAsignado(
                                id = user.ID,
                                nombre = user.NOMBRE,
                                email = user.EMAIL,
                                cobradorId = user.COBRADOR_ID
                            )
                        }

                    Camioneta(
                        almacenId = almacen.ALMACEN_ID,
                        nombre = almacen.ALMACEN,
                        existencias = almacen.EXISTENCIAS,
                        usuariosAsignados = usuariosAsignados
                    )
                }

            Result.success(camionetas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gets users that are not assigned to any camioneta.
     */
    suspend fun getUsuariosSinAsignar(): Result<List<User>> {
        return try {
            val usersResult = getAllUsers()
            if (usersResult.isFailure) {
                return Result.failure(usersResult.exceptionOrNull() ?: Exception("Error fetching users"))
            }

            val users = usersResult.getOrThrow()
            val sinAsignar = users.filter { it.CAMIONETA_ASIGNADA == null }

            Result.success(sinAsignar)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validates if a camioneta can accept more users (max 3).
     */
    suspend fun validarAsignacion(almacenId: Int): Result<Boolean> {
        return try {
            val usersResult = getAllUsers()
            if (usersResult.isFailure) {
                return Result.failure(usersResult.exceptionOrNull() ?: Exception("Error fetching users"))
            }

            val users = usersResult.getOrThrow()
            val countAsignados = users.count { it.CAMIONETA_ASIGNADA == almacenId }

            Result.success(countAsignados < Camioneta.MAX_USUARIOS_POR_CAMIONETA)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
