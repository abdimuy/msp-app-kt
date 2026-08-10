package com.example.msp_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.example.msp_app.core.context.LocalConnectivityState
import com.example.msp_app.core.context.rememberConnectivityState
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.compose.LocalTelemetry
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.ui.theme.ThemeController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        var isAuthenticated by mutableStateOf(false)
    }

    /**
     * Adapter real (`DurableTelemetry`, drenando al `StubTelemetrySink` —
     * Plan 4, Task 4/8) provisto por `TelemetryModule` (`:core:telemetry`).
     * Se expone al árbol Compose vía [LocalTelemetry] en [onCreate] para que
     * `ScreenScope`/`Modifier.trackClick` dejen de resolver al no-op de
     * seguridad y encolen de verdad. Field injection (no constructor): Hilt
     * exige esa forma para una `Activity`/`FragmentActivity`.
     */
    @Inject
    lateinit var telemetry: Telemetry

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

        // Habilitar edge to edge con comportamiento por defecto del sistema
        enableEdgeToEdge()

        setContent {
            val connectivityState by rememberConnectivityState()

            // `enableEdgeToEdge()` fija el color de los íconos de la barra de sistema según el
            // tema del SISTEMA, no el toggle de tema DENTRO de la app — si el usuario pone la
            // app en oscuro con el SO en claro (o viceversa), los íconos (reloj/wifi/batería)
            // quedan del color del SO y se vuelven invisibles contra el fondo de la app (negro
            // sobre negro en oscuro). Se corrige leyendo `ThemeController.statusBarAppearanceDark`
            // (estado de Compose, dispara este efecto en cada cambio — sigue a `isDarkMode` por
            // defecto pero también refleja el tema LOCAL de pantallas desacopladas como el
            // reporte de cobranza, ver KDoc de `ThemeController.statusBarAppearanceDark`) y
            // fijando la apariencia de íconos vía `WindowInsetsControllerCompat`: tema claro ->
            // íconos oscuros (`isAppearanceLight* = true`), tema oscuro -> íconos claros
            // (`false`).
            val isDarkTheme = ThemeController.statusBarAppearanceDark
            LaunchedEffect(isDarkTheme) {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDarkTheme
                insetsController.isAppearanceLightNavigationBars = !isDarkTheme
            }

            MspappTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalConnectivityState provides connectivityState,
                    LocalTelemetry provides telemetry
                ) {
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
    }

    private fun authenticateUser() {
        val biometricManager = BiometricManager.from(this)

        when (
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        ) {
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
            BiometricPrompt(
                this,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
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

                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        isAuthenticated = true
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                }
            )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación requerida")
            .setSubtitle("Usa tu huella digital, rostro o patrón para acceder")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
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
