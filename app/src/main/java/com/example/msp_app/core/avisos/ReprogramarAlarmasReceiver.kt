package com.example.msp_app.core.avisos

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reinicio, actualización de la app, cambio de hora o de zona, o cambio del
 * permiso de alarmas exactas: reconstruye las alarmas desde `aviso_programado`
 * (plan visitas P3, Tarea 13; E-APP-124). No hace trabajo largo: solo encola el
 * trabajo único [ReprogramarAvisosWorker.NOMBRE_UNICO]. La acción se valida
 * porque `BOOT_COMPLETED` y `MY_PACKAGE_REPLACED` exigen `exported="true"`.
 */
class ReprogramarAlarmasReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACCIONES) return
        encolarReprogramacionDeAvisos(context.applicationContext)
    }

    companion object {
        val ACCIONES: Set<String> = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
