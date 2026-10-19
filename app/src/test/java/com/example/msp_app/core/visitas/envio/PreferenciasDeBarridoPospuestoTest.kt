package com.example.msp_app.core.visitas.envio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/** Válvula de la adenda de E-APP-079: el contador sobrevive entre instancias. */
class PreferenciasDeBarridoPospuestoTest : RobolectricTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `E-APP-079 valvula la memoria empieza en cero y conserva lo anotado`() {
        assertEquals(0, PreferenciasDeBarridoPospuesto(context).seguidas())
        PreferenciasDeBarridoPospuesto(context).anotar(3)
        assertEquals(3, PreferenciasDeBarridoPospuesto(context).seguidas())
        PreferenciasDeBarridoPospuesto(context).anotar(0)
        assertEquals(0, PreferenciasDeBarridoPospuesto(context).seguidas())
    }
}
