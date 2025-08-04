package com.example.msp_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.example.msp_app.core.utils.downloadApk
import com.example.msp_app.core.utils.installApk
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.features.auth.viewModels.AuthViewModel
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.ui.theme.ThemeController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

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
            val currentUser by authViewModel.currentUser.collectAsState()
            var showDialog by remember { mutableStateOf(false) }
            var downloadStarted by remember { mutableStateOf(false) }
            val baseUrl by ApiProvider.baseURL.collectAsState()

            MspappTheme(dynamicColor = false) {
                AppNavigation()

                if (currentUser != null && !downloadStarted && showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Actualizar App") },
                        text = { Text("¿Deseas descargar la actualizacion de la aplicación?") },
                        confirmButton = {
                            Button(onClick = {
                                showDialog = false
                                downloadStarted = true
                                lifecycleScope.launch {
                                    try {
                                        val apkUrl =
                                            "${baseUrl}download-app/app-release.apk"
                                        val apkFile = downloadApk(this@MainActivity, apkUrl)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Descarga completada. Abriendo instalador...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        installApk(this@MainActivity, apkFile)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Error al descargar o instalar el APK",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }) {
                                Text("Sí")
                            }
                        },
                        dismissButton = {
                            Button(onClick = {
                                showDialog = false
                            }) {
                                Text("No")
                            }
                        }
                    )
                }
                LaunchedEffect(currentUser) {
                    if (currentUser != null && !downloadStarted) {
                        showDialog = true
                    }
                }
            }
        }
    }
}