package com.example.msp_app.data.menu

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSeccionesPlegadasAdapterTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun adaptador() = DataStoreSeccionesPlegadasAdapter(
        PreferenceDataStoreFactory.create(
            produceFile = { File(carpeta.newFolder(), "test_menu_lateral.preferences_pb") }
        )
    )

    @Test
    fun `sin nada guardado no hay secciones plegadas`() = runTest {
        assertEquals(emptySet<String>(), adaptador().plegadas().first())
    }

    @Test
    fun `plegar y desplegar hacen ida y vuelta sin tocar las demas`() = runTest {
        val adaptador = adaptador()
        adaptador.setPlegada("VENTAS", plegada = true)
        adaptador.setPlegada("ALMACEN", plegada = true)
        adaptador.setPlegada("VENTAS", plegada = false)

        assertEquals(setOf("ALMACEN"), adaptador.plegadas().first())
    }
}
