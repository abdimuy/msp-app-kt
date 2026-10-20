package com.example.msp_app.core.avisos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Canales de los avisos de la agenda (spec visitas §6.5 y §6.6; plan P3,
 * Tarea 12). Crear un canal que ya existe no cambia nada, así que [crear] es
 * idempotente y se llama en cada arranque.
 */
object CanalesDeNotificacion {
    const val VISITAS_ASIGNADAS = "visitas_asignadas"
    const val AVISOS_COMPROMISOS = "avisos_compromisos"
    const val SINCRONIZACION = "sincronizacion"

    fun crear(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                VISITAS_ASIGNADAS,
                "Visitas asignadas",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                AVISOS_COMPROMISOS,
                "Avisos de compromisos",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                SINCRONIZACION,
                "Sincronización",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
