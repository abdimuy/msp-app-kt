package com.example.msp_app.core.context

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.msp_app.features.auth.viewModels.AuthViewModel

val LocalAuthViewModel = staticCompositionLocalOf<AuthViewModel> {
    error("AuthViewModel not provided")
}
