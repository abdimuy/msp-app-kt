package com.example.msp_app

import android.app.Application
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.RemoteLogger

class MspApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Inicializar el sistema de logging remoto
        initializeRemoteLogging()
    }

    private fun initializeRemoteLogging() {
        try {
            Logger.init(this)

            // Log de inicio de aplicación
            RemoteLogger.getInstance(this).info(
                module = "APP",
                action = "STARTUP",
                message = "Aplicación iniciada",
                data = mapOf(
                    "packageName" to packageName,
                    "versionName" to packageManager.getPackageInfo(packageName, 0).versionName,
                    "versionCode" to packageManager.getPackageInfo(packageName, 0).versionCode
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}