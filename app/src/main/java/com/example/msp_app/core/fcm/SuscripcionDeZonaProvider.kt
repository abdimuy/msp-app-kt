package com.example.msp_app.core.fcm

import android.content.Context
import com.example.msp_app.core.avisos.KillSwitchAvisos

/** Instancia por proceso (sin baseURL: FCM no pasa por el API). */
object SuscripcionDeZonaProvider {
    fun de(context: Context): SuscripcionDeZona = SuscripcionDeZona(
        FirebaseTemas(),
        AlmacenEnPreferencias(context)
    ) { KillSwitchAvisos.fcm }
}
