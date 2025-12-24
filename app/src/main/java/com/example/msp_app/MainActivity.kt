package com.example.msp_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.ui.theme.ThemeController
import com.example.msp_app.workers.PaymentsExportWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.gson.Gson
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {

    companion object {
        var isAuthenticated by mutableStateOf(false)
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var lastActivityTime = System.currentTimeMillis()
    private val inactivityTimeoutMs = 5 * 60 * 1000L // 5 minutos

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    persistentCacheSettings {
                        setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    }
                )
                .build()

            FirebaseFirestore.getInstance().firestoreSettings = settings
        } catch (e: IllegalStateException) {
            // FirebaseFirestore ya fue inicializado, ignorar
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                "loc_service",
                "Ubicación en segundo plano",
                NotificationManager.IMPORTANCE_LOW
            )
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)
        }

        ThemeController.init(this)

        initializeAutoExport()

        // Habilitar edge to edge con comportamiento por defecto del sistema
        enableEdgeToEdge()

        setContent {
            MspappTheme(dynamicColor = false) {
                if (isAuthenticated) {
                    AppNavigation()
                } else {
                    LaunchedEffect(Unit) {
                        authenticateUser()
                    }
                }
            }
        }
    }

    private fun initializeAutoExport() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            fetchUserDataAndScheduleExport(currentUser.uid)
        } else {
            Log.d("AutoExport", "⚠️ No hay usuario autenticado, export no programado")
        }
    }

    private fun fetchUserDataAndScheduleExport(userId: String) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)

                    if (user != null) {
                        scheduleAutoExport(user)
                    } else {
                        Log.w("AutoExport", "⚠️ Error parseando usuario")
                    }
                } else {
                    Log.w("AutoExport", "⚠️ Documento de usuario no existe en Firestore")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("AutoExport", "❌ Error obteniendo usuario de Firestore", exception)
            }
    }

    private fun scheduleAutoExport(user: User) {
        val gson = Gson()
        val userJson = gson.toJson(user)
        val input = workDataOf("user_json" to userJson)

        val workRequest = PeriodicWorkRequestBuilder<PaymentsExportWorker>(
            5, TimeUnit.HOURS
        )
            .setInputData(input)
            .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "AutoExportPayments",
                ExistingPeriodicWorkPolicy.REPLACE, // Reemplaza worker anterior
                workRequest
            )
    }

    fun reprogramAutoExport() {
        Log.d("AutoExport", "🔄 Reprogramando auto-export...")
        initializeAutoExport()
    }

    private fun authenticateUser() {
        val biometricManager = BiometricManager.from(this)

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt()
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                isAuthenticated = true
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                isAuthenticated = true
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                isAuthenticated = true
            }

            else -> {
                isAuthenticated = true
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        finish()
                    } else {
                        isAuthenticated = true
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticated = true
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación requerida")
            .setSubtitle("Usa tu huella digital, rostro o patrón para acceder")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lastActivityTime = System.currentTimeMillis()
    }

    override fun onResume() {
        super.onResume()
        val currentTime = System.currentTimeMillis()
        if (isAuthenticated && (currentTime - lastActivityTime > inactivityTimeoutMs)) {
            isAuthenticated = false
        }
        lastActivityTime = currentTime
    }

    override fun onPause() {
        super.onPause()
        lastActivityTime = System.currentTimeMillis()
    }
}