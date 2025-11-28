package com.example.msp_app.data.repository

import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.models.auth.User
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UsersRepository {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun getAllUsers(): Result<List<User>> {
        return try {
            val snapshot = firestore
                .collection(Constants.USERS_COLLECTION)
                .get()
                .await()

            val users = snapshot.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)
            }

            Result.success(users)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
