package com.example.msp_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.ui.theme.ThemeController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class MainActivity : ComponentActivity() {
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
                AppNavigation()
            }
        }
    }
}