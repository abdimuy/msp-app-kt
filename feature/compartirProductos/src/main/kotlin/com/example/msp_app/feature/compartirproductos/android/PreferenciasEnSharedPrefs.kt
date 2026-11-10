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

    override fun tituloEditado(articuloId: Int): String? = prefs.getString(
        "$TITULO$articuloId",
        null
    )?.takeIf {
        it.isNotBlank()
    }

    override fun guardarTituloEditado(articuloId: Int, titulo: String?) {
        val limpio = titulo?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply {
            if (limpio == null) {
                remove(
                    "$TITULO$articuloId"
                )
            } else {
                putString("$TITULO$articuloId", limpio)
            }
        }.apply()
    }

    private companion object {
        const val ARCHIVO = "compartir_productos"
        const val CONTADO = "casilla_contado"
        const val CORTO = "casilla_corto_plazo"
        const val CREDITO = "casilla_credito"
        const val TITULO = "titulo_"
    }
}
