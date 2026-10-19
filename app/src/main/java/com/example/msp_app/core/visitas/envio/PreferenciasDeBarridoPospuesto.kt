package com.example.msp_app.core.visitas.envio

import android.content.Context
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.MemoriaDeBarridoPospuesto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [MemoriaDeBarridoPospuesto] en SharedPreferences `visitas_barrido_pospuesto`
 * (válvula de la adenda de E-APP-079). Solo un entero: cuántas corridas
 * seguidas lleva el barrido pospuesto por dinero.
 */
class PreferenciasDeBarridoPospuesto @Inject constructor(
    @ApplicationContext private val context: Context
) : MemoriaDeBarridoPospuesto {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun seguidas(): Int = prefs.getInt(CLAVE, 0).coerceAtLeast(0)

    override fun anotar(seguidas: Int) {
        prefs.edit().putInt(CLAVE, seguidas.coerceAtLeast(0)).apply()
    }

    private companion object {
        const val PREFS = "visitas_barrido_pospuesto"
        const val CLAVE = "seguidas"
    }
}
