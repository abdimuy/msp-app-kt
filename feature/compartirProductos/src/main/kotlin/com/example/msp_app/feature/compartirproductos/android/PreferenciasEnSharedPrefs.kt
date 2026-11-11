package com.example.msp_app.feature.compartirproductos.android

import android.content.Context
import android.content.SharedPreferences
import com.example.msp_app.feature.compartirproductos.domain.PreferenciasDeCompartir
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios

/** Lo que el vendedor ya decidió, en preferencias locales (no toca Room: la base sigue en su versión). */
class PreferenciasEnSharedPrefs(private val prefs: SharedPreferences) : PreferenciasDeCompartir {

    constructor(context: Context) : this(
        context.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
    )

    override fun seleccionDePrecios(): SeleccionDePrecios = SeleccionDePrecios(
        contado = prefs.getBoolean(CONTADO, true),
        cortoPlazo = prefs.getBoolean(CORTO, true),
        credito = prefs.getBoolean(CREDITO, true)
    )

    override fun guardarSeleccionDePrecios(seleccion: SeleccionDePrecios) {
        prefs.edit()
            .putBoolean(CONTADO, seleccion.contado)
            .putBoolean(CORTO, seleccion.cortoPlazo)
            .putBoolean(CREDITO, seleccion.credito)
            .apply()
    }

    /**
     * E-APP-180: los títulos editados que un teléfono guardó antes ya no se leen nunca; esto además
     * borra las claves (una sola vez al arrancar, desde [LimpiezaAlArrancar]).
     * @return cuántas claves borró.
     */
    fun borrarTitulosEditadosViejos(): Int {
        val viejas = prefs.all.keys.filter { it.startsWith(TITULO_VIEJO) }
        if (viejas.isNotEmpty()) {
            prefs.edit().apply { viejas.forEach { remove(it) } }.apply()
        }
        return viejas.size
    }

    private companion object {
        const val ARCHIVO = "compartir_productos"
        const val CONTADO = "casilla_contado"
        const val CORTO = "casilla_corto_plazo"
        const val CREDITO = "casilla_credito"
        const val TITULO_VIEJO = "titulo_"
    }
}
