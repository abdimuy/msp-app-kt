package com.example.msp_app.core.avisos

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/**
 * Qué puede hacer la app hoy con los avisos (plan visitas P3, Tarea 15;
 * E-APP-134). Es solo lectura: pedir cada cosa es de [GuiaDePermisos] y nada
 * de esto bloquea el cobro. Los avisos son comodidad, no fuente de verdad.
 */
data class EstadoDePermisos(
    val notificaciones: Boolean,
    val alarmasExactas: Boolean,
    val bateriaSinOptimizar: Boolean
) {
    companion object {
        fun leer(context: Context): EstadoDePermisos = EstadoDePermisos(
            notificaciones = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            alarmasExactas = alarmasExactas(context),
            bateriaSinOptimizar = bateriaSinOptimizar(context)
        )

        /** `canScheduleExactAlarms()` es de API 31; antes de eso no hay permiso que pedir (minSdk 24). */
        private fun alarmasExactas(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        private fun bateriaSinOptimizar(context: Context): Boolean =
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }
}
