package com.example.msp_app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

object ThemeController {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    private var prefsInitialized = false
    private lateinit var prefs: SharedPreferences

    var isDarkMode by mutableStateOf(false)
        private set

    /**
     * Apariencia de los íconos de la barra de sistema (status/nav bar, ver
     * `MainActivity.onCreate` — `WindowInsetsControllerCompat.isAppearanceLight*`). Sigue a
     * [isDarkMode] por defecto (mismo flip que el resto de la app vía [toggle]), pero una
     * pantalla con su PROPIO tema local desacoplado de [isDarkMode] (p. ej. el reporte de
     * cobranza, `:feature:collectionReport` — su `CollectionReportUiState.darkTheme` es un
     * espejo local, no persiste ni escribe aquí por diseño, ver KDoc de
     * `CollectionReportViewModel.toggleTheme`) puede reportar su tema activo con
     * [reportStatusBarAppearanceDark] sin tocar [isDarkMode]/`MspappTheme` del resto de la app —
     * así los íconos del sistema quedan correctos en CUALQUIER pantalla, oscura o clara, sin
     * que el tema local de una pantalla se filtre al resto de la app (fix defecto visual:
     * íconos de la barra de estado invisibles al entrar en oscuro).
     */
    var statusBarAppearanceDark by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefsInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        statusBarAppearanceDark = isDarkMode
        prefsInitialized = true
    }

    fun toggle() {
        isDarkMode = !isDarkMode
        statusBarAppearanceDark = isDarkMode

        prefs.edit {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            apply()
        }
    }

    /** Ver KDoc de [statusBarAppearanceDark]. */
    fun reportStatusBarAppearanceDark(dark: Boolean) {
        statusBarAppearanceDark = dark
    }
}
