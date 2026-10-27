package com.example.msp_app.core.avisos

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.msp_app.MainActivity

/** Muestra la notificación de [AvisoInmediato] (E-APP-123). Abre la app al tocarla. */
internal class NotificadorDeVisitaAsignada(private val context: Context) {

    /** `POST_NOTIFICATIONS` concedido (en Android < 13 no se pide) y avisos no bloqueados. */
    fun permitido(): Boolean {
        val concedido = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        return concedido && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // permitido() lo comprueba antes de llamar.
    fun mostrar(titulo: String, texto: String, id: Int) {
        CanalesDeNotificacion.crear(context)
        val abrir = PendingIntent.getActivity(
            context,
            id,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: android.content.Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CanalesDeNotificacion.VISITAS_ASIGNADAS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}
