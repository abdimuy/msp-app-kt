package com.example.msp_app.feature.compartirproductos.android

import android.content.Context
import android.util.Log

/**
 * Se llama una vez al arrancar la app: borra las tarjetas de más de un día que hayan quedado en
 * `cacheDir/compartir/` y los títulos editados que un teléfono guardó antes de E-APP-180.
 */
object LimpiezaAlArrancar {
    fun ejecutar(context: Context): Int = try {
        PreferenciasEnSharedPrefs(context).borrarTitulosEditadosViejos()
        AlmacenDeTemporales(context.cacheDir).barrerViejos()
    } catch (e: SecurityException) {
        Log.w("LimpiezaAlArrancar", "sin permiso para barrer cacheDir/compartir", e)
        0
    }
}
