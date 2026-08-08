package com.example.msp_app

import android.app.Application
import com.example.msp_app.core.debug.RemoteDbDebugger
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.workmanager.enqueueClienteSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@HiltAndroidApp
class MspApplication : Application() {

    /**
     * Process-scoped coroutine scope used by background, fire-and-forget work
     * that must survive Activity/Composition recreation (e.g. the session
     * pending-work sync dispatched from AppNavigation).
     *
     * SupervisorJob so a failure in one child does not cancel siblings; IO
     * dispatcher so the default home for DB + network work is the right one.
     */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Inicializar el sistema de logging remoto
        initializeRemoteLogging()

        // Inicializar el sistema de debug remoto de base de datos
        initializeRemoteDbDebugger()

        // Inicializar sincronización periódica de clientes
        enqueueClienteSyncWorker(this)
    }

    private fun initializeRemoteDbDebugger() {
        try {
            RemoteDbDebugger.init(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
