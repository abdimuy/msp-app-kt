package com.example.msp_app.feature.compartirproductos.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.compartirproductos.domain.SeleccionDePrecios
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `el titulo editado se recuerda por producto`() {
        val p = nuevas()
        p.guardarTituloEditado(10, "Sofá Valencia Verde")
        p.guardarTituloEditado(11, "Comedor Roble")
        assertEquals("Sofá Valencia Verde", nuevas().tituloEditado(10))
        assertEquals("Comedor Roble", nuevas().tituloEditado(11))
        assertNull(nuevas().tituloEditado(12))
    }

    @Test fun `un titulo vacio o en blanco borra el recuerdo`() {
        val p = nuevas()
        p.guardarTituloEditado(10, "Algo")
        p.guardarTituloEditado(10, "   ")
        assertNull(nuevas().tituloEditado(10))
        p.guardarTituloEditado(10, "Algo")
        p.guardarTituloEditado(10, null)
        assertNull(nuevas().tituloEditado(10))
    }

    @Test fun `el titulo se guarda recortado de espacios`() {
        val p = nuevas()
        p.guardarTituloEditado(3, "  Mesa Redonda  ")
        assertEquals("Mesa Redonda", nuevas().tituloEditado(3))
    }

    @Test fun `editar un producto no afecta al otro ni a las casillas`() {
        val p = nuevas()
        p.guardarSeleccionDePrecios(SeleccionDePrecios(false, true, true))
        p.guardarTituloEditado(1, "Uno")
        p.guardarTituloEditado(1, null)
        p.guardarTituloEditado(2, "Dos")
        assertEquals("Dos", nuevas().tituloEditado(2))
        assertEquals(SeleccionDePrecios(false, true, true), nuevas().seleccionDePrecios())
    }
}
