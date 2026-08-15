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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.appgate.AppEntryStep
import com.example.msp_app.core.appgate.GATE_WAIT_TIMEOUT_MS
import com.example.msp_app.core.appgate.resolveAppEntryStep
import com.example.msp_app.core.appgate.ui.VersionBlockedScreen
import com.example.msp_app.core.appgate.ui.VersionGateViewModel
import com.example.msp_app.core.context.LocalConnectivityState
import com.example.msp_app.core.context.rememberConnectivityState
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.settings.SettingsRepository
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
import kotlinx.coroutines.delay

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

    /**
     * Fundación de Configuración (spec
     * `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
     * §"`app/` (composición root)"): la raíz de composición lee
     * `fontSizeLevel`/`reduceMotion` de aquí para el override Opción C de
     * [androidx.compose.ui.platform.LocalDensity] + [LocalReduceMotion] (ver
     * `setContent` abajo) — el mismo field-injection que [telemetry] ya usa.
     */
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var lastActivityTime = System.currentTimeMillis()
    private val inactivityTimeoutMs = 5 * 60 * 1000L // 5 minutos

    /** El prompt vivo, para poder cerrarlo si la compuerta de versión gana. */
    private var biometricPrompt: BiometricPrompt? = null

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

            // `ThemeMode.SYSTEM` (Configuración, "Automático") necesita el `isSystemInDarkTheme()`
            // vigente — `ThemeController` es un objeto plano no-Composable, así que no puede leerlo
            // solo; esta raíz de composición se lo reporta en cada cambio (ver KDoc de
            // `ThemeController.updateSystemDarkMode`).
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(systemDark) {
                ThemeController.updateSystemDarkMode(systemDark)
            }

            MspappTheme(dynamicColor = false) {
                // Fundación de Configuración — Opción C (spec §"Mecánica del tamaño de letra"):
                // el tamaño EFECTIVO es máx(nivel elegido en la app, `fontScale` del SO); la app
                // nunca achica por debajo de lo que el teléfono ya pide. `LocalFontSizeLevel`/
                // `LocalReduceMotion` quedan disponibles para cualquier pantalla migrada
                // (`:feature:collectionReport` Tier 1/2, trabajo de otro agente) sin que esta
                // raíz aplique la rampa comprimida — eso es progresivo, pantalla por pantalla.
                val fontSizeLevel by settingsRepository.fontSizeLevel.collectAsState(
                    FontSizeLevel.NORMAL
                )
                val reduceMotion by settingsRepository.reduceMotion.collectAsState(false)
                val baseDensity = LocalDensity.current
                val effectiveFontScale = maxOf(fontSizeLevel.nominalScale, baseDensity.fontScale)
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, effectiveFontScale),
                    LocalFontSizeLevel provides fontSizeLevel,
                    LocalReduceMotion provides reduceMotion,
                    LocalConnectivityState provides connectivityState,
                    LocalTelemetry provides telemetry
                ) {
                    AppEntry()
                }
            }
        }
    }

    /**
     * Quién manda en el arranque: **la compuerta de versión, antes que la
     * huella** (ver `resolveAppEntryStep`). Autenticarse en una app que no se
     * puede usar no le sirve a nadie, y el orden dejó de depender de si el
     * proceso venía vivo o no.
     *
     * El `VersionGateViewModel` se resuelve acá, en el `ViewModelStore` de la
     * `Activity` — la misma instancia que después toma `AppNavigation`, así
     * que no hay dos compuertas encolando descargas por su cuenta.
     */
    @Composable
    private fun AppEntry() {
        val gateViewModel: VersionGateViewModel = hiltViewModel()
        val verdict by gateViewModel.verdict.collectAsStateWithLifecycle()
        var gateWaitElapsed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(GATE_WAIT_TIMEOUT_MS)
            gateWaitElapsed = true
        }

        when (resolveAppEntryStep(verdict, isAuthenticated, gateWaitElapsed)) {
            AppEntryStep.VERSION_BLOCKED -> {
                // Si el prompt alcanzó a abrirse antes de que llegara el
                // veredicto, se cierra: dejarlo encima de la pantalla de
                // bloqueo pediría una huella para nada.
                LaunchedEffect(Unit) { dismissBiometricPrompt() }
                VersionBlockedScreen(viewModel = gateViewModel)
            }

            AppEntryStep.WAITING_FOR_GATE -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            AppEntryStep.AUTHENTICATE -> LaunchedEffect(Unit) { authenticateUser() }

            AppEntryStep.RUN -> AppNavigation()
        }
    }

    private fun dismissBiometricPrompt() {
        biometricPrompt?.cancelAuthentication()
        biometricPrompt = null
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
        val prompt =
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

        // Se guarda para poder cancelarlo si la compuerta de versión bloquea
        // mientras el prompt está arriba.
        biometricPrompt = prompt
        prompt.authenticate(promptInfo)
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
