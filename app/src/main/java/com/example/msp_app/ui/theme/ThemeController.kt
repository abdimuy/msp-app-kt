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

    fun init(context: Context) {
        if (prefsInitialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        prefsInitialized = true
    }

    fun toggle() {
        isDarkMode = !isDarkMode

        prefs.edit {
            putBoolean(KEY_DARK_MODE, isDarkMode)
            apply()
        }
    }
}