package com.example.msp_app.feature.compartirproductos.android

import android.content.Context
import android.util.Log

/** Se llama una vez al arrancar la app: borra las tarjetas de más de un día que hayan quedado en `cacheDir/compartir/`. */
object LimpiezaAlArrancar {
    fun ejecutar(context: Context): Int = try {
        AlmacenDeTemporales(context.cacheDir).barrerViejos()
    } catch (e: SecurityException) {
        Log.w("LimpiezaAlArrancar", "sin permiso para barrer cacheDir/compartir", e)
        0
    }
}
