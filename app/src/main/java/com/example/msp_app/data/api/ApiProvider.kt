package com.example.msp_app.data.api

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import retrofit2.Retrofit

object ApiProvider : BaseApi() {

    private const val DEFAULT_BASE_URL = "https://msp2025.loclx.io/"
    private val _baseURL = MutableStateFlow(DEFAULT_BASE_URL)
    val baseURL: StateFlow<String> = _baseURL

    private var retrofitInstance: Retrofit? = null
    private var firestoreListener: ListenerRegistration? = null

    init {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firestoreUrl = getBaseUrlFromFirestore()
                if (firestoreUrl.isNotEmpty() && firestoreUrl != _baseURL.value) {
                    _baseURL.value = firestoreUrl
                    retrofitInstance = createClient(_baseURL.value)
                }
            } catch (e: Exception) {
                println("No se pudo cargar base URL desde Firestore: ${e.message}")
            }
        }

        firestoreListener = FirebaseFirestore.getInstance()
            .collection("config")
            .document("api_settings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("Error al escuchar base URL: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val newUrl = snapshot.getString("baseURL") ?: DEFAULT_BASE_URL
                    if (newUrl.isNotEmpty() && newUrl != _baseURL.value) {
                        _baseURL.value = newUrl
                        CoroutineScope(Dispatchers.IO).launch {
                            retrofitInstance = createClient(newUrl)
                        }
                    }
                }
            }
    }

    private fun getRetrofit(): Retrofit {
        return retrofitInstance ?: synchronized(this) {
            retrofitInstance ?: createClient(_baseURL.value).also { retrofitInstance = it }
        }
    }

    fun <T> create(service: Class<T>): T {
        return getRetrofit().create(service)
    }

    private suspend fun getBaseUrlFromFirestore(): String {
        val snapshot = FirebaseFirestore.getInstance()
            .collection("config")
            .document("api_settings")
            .get()
            .await()

        return snapshot.getString("baseURL") ?: ""
    }
}
