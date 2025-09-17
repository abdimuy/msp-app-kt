package com.example.msp_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.features.auth.screens.LoginScreen
import com.example.msp_app.features.auth.viewmodel.AuthViewModel
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.ui.theme.ThemeController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class MainActivity : FragmentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private var lastActivityTime = System.currentTimeMillis()
    private val inactivityTimeoutMs = 5 * 60 * 1000L // 5 minutos

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                persistentCacheSettings {
                    setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                }
            )
            .build()

        FirebaseFirestore.getInstance().firestoreSettings = settings

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

        enableEdgeToEdge()

        setContent {
            MspappTheme(dynamicColor = false) {
                val authState by authViewModel.authState.collectAsStateWithLifecycle()

                AuthHandler(
                    authState = authState,
                    onBiometricSuccess = authViewModel::onBiometricSuccess,
                    onBiometricFailed = authViewModel::onBiometricFailed,
                    onLoginSuccess = authViewModel::onLoginSuccess
                )
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    private fun AuthHandler(
        authState: AuthViewModel.AuthState,
        onBiometricSuccess: () -> Unit,
        onBiometricFailed: () -> Unit,
        onLoginSuccess: () -> Unit
    ) {
        when (authState) {
            is AuthViewModel.AuthState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is AuthViewModel.AuthState.Unauthenticated -> {
                LoginScreen(onLoginSuccess = onLoginSuccess)
            }

            is AuthViewModel.AuthState.RequiresBiometric -> {
                LaunchedEffect(authState.user) {
                    authenticateUser(
                        onSuccess = onBiometricSuccess,
                        onFailed = onBiometricFailed
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Esperando autenticación biométrica...",
                        modifier = Modifier.padding(top = 60.dp)
                    )
                }
            }

            is AuthViewModel.AuthState.Authenticated -> {
                AppNavigation()
            }
        }
    }

    private fun authenticateUser(
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        val biometricManager = BiometricManager.from(this)

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt(onSuccess, onFailed)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                onSuccess()
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onSuccess()
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onSuccess()
            }

            else -> {
                onSuccess()
            }
        }
    }

    private fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt =
            BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onFailed()
                        finish()
                    } else {
                        onSuccess()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
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
        if ((authViewModel.authState.value is AuthViewModel.AuthState.Authenticated) &&
            (currentTime - lastActivityTime > inactivityTimeoutMs)
        ) {
            authViewModel.signOut()
        }
        lastActivityTime = currentTime
    }

    override fun onPause() {
        super.onPause()
        lastActivityTime = System.currentTimeMillis()
    }
}