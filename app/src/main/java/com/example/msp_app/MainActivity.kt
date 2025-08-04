package com.example.msp_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import com.example.msp_app.core.utils.downloadApk
import com.example.msp_app.core.utils.installApk
import com.example.msp_app.navigation.AppNavigation
import com.example.msp_app.ui.theme.MspappTheme
import com.example.msp_app.ui.theme.ThemeController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //val currentVersionCode = getCurrentVersionCode(this)
        //val currentVersionName = packageManager.getPackageInfo(packageName, 0).versionName

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

        lifecycleScope.launch {
            try {
                val apkUrl =
                    "https://msp2025.loclx.io/download-app/app-release.apk"
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


        setContent {
            MspappTheme(dynamicColor = false) {
                AppNavigation()
            }
        }
    }

    /**private suspend fun fetchLatestVersionInfo(url: String): Pair<Int, String> =
    withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()
    val jsonString = response.body?.string() ?: ""

    val json = JSONObject(jsonString)
    val versionCode = json.getInt("versionCode")
    val apkUrl = json.getString("apkUrl")

    Pair(versionCode, apkUrl)
    }**/

    /**private fun showUpdateDialog(apkUrl: String) {
    AlertDialog.Builder(this)
    .setTitle("Nueva actualización disponible")
    .setMessage("¿Deseas descargar e instalar la actualización?")
    .setPositiveButton("Sí") { dialog, _ ->
    lifecycleScope.launch {
    try {
    Toast.makeText(
    this@MainActivity,
    "Descargando actualización...",
    Toast.LENGTH_SHORT
    ).show()

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
    "Error al descargar la actualización",
    Toast.LENGTH_LONG
    ).show()
    }
    }
    dialog.dismiss()
    }
    .setNegativeButton("No") { dialog, _ ->
    dialog.dismiss()
    }
    .setCancelable(false)
    .show()
    }**/
}