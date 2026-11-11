package com.example.msp_app.feature.compartirproductos.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenciasEnSharedPrefsTest : RobolectricTestBase() {

    private fun nuevas() = PreferenciasEnSharedPrefs(
        ApplicationProvider.getApplicationContext<Context>()
    )

    @Test fun `por omision las tres casillas vienen marcadas`() {
        assertEquals(SeleccionDePrecios(true, true, true), nuevas().seleccionDePrecios())
    }

    @Test fun `la eleccion de casillas se recuerda para la proxima vez`() {
        nuevas().guardarSeleccionDePrecios(
            SeleccionDePrecios(contado = true, cortoPlazo = false, credito = true)
        )
        // Otra instancia = otra «sesión»: lo leído viene de disco, no de memoria.
        assertEquals(SeleccionDePrecios(true, false, true), nuevas().seleccionDePrecios())
        nuevas().guardarSeleccionDePrecios(SeleccionDePrecios(false, false, false))
        assertEquals(SeleccionDePrecios(false, false, false), nuevas().seleccionDePrecios())
    }

    private val contexto get() = ApplicationProvider.getApplicationContext<Context>()

    private fun sembrarTitulosViejos() {
        contexto.getSharedPreferences("compartir_productos", Context.MODE_PRIVATE).edit()
            .putString("titulo_10", "Sofá Valencia Verde")
            .putString("titulo_11", "Comedor Roble")
            .commit()
    }

    private fun clavesViejas() = contexto.getSharedPreferences(
        "compartir_productos",
        Context.MODE_PRIVATE
    )
        .all.keys.filter { it.startsWith("titulo_") }

    @Test fun `los titulos editados que un telefono guardo antes se borran y las casillas se quedan`() {
        nuevas().guardarSeleccionDePrecios(SeleccionDePrecios(false, true, true))
        sembrarTitulosViejos()
        assertEquals(2, nuevas().borrarTitulosEditadosViejos())
        assertEquals(emptyList<String>(), clavesViejas())
        assertEquals(SeleccionDePrecios(false, true, true), nuevas().seleccionDePrecios())
        assertEquals(0, nuevas().borrarTitulosEditadosViejos())
    }

    @Test fun `la limpieza de arranque borra los titulos editados viejos (E-APP-180)`() {
        sembrarTitulosViejos()
        LimpiezaAlArrancar.ejecutar(contexto)
        assertEquals(emptyList<String>(), clavesViejas())
    }
}
