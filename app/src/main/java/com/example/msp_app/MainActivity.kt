package com.example.msp_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enqueuePendingPaymentsWorker(this)

        setContent {
            MspappTheme(dynamicColor = false) {
                val auth = FirebaseAuth.getInstance()
                var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }

                AppNavigation()
            }
        }
    }
}