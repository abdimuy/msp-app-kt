package com.example.msp_app.data.local

import android.content.Context
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.FechaDelCatalogo

const val PREFS_CATALOGO = "catalogo_productos"
const val CLAVE_CATALOGO_ACTUALIZADO_EN = "actualizado_en_ms"

/** La marca de actualización del catálogo, guardada en preferencias (sin tocar Room). */
fun fechaDelCatalogo(context: Context, clock: AppClock = AppClock.System) = FechaDelCatalogo(
    leer = {
        context.getSharedPreferences(PREFS_CATALOGO, Context.MODE_PRIVATE)
            .getLong(CLAVE_CATALOGO_ACTUALIZADO_EN, 0L)
    },
    guardar = { momento ->
        context.getSharedPreferences(PREFS_CATALOGO, Context.MODE_PRIVATE)
            .edit().putLong(CLAVE_CATALOGO_ACTUALIZADO_EN, momento).apply()
    },
    clock = clock
)
