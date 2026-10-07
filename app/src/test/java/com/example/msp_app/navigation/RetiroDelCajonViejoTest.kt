package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `DrawerContainer` se fue: ni el archivo ni una sola mención en código (spec §3.7). */
class RetiroDelCajonViejoTest {

    private val escaner = EscanerDeFuentes()

    @Test
    fun `nada en el repo menciona DrawerContainer`() {
        assertEquals(emptyList<String>(), escaner.archivosQueMencionan("DrawerContainer"))
        assertFalse(
            escaner.existe("app/src/main/java/com/example/msp_app/components/DrawerContainer.kt")
        )
    }

    /** Control positivo: el mismo escáner ve las 20 llamadas que lo reemplazaron más la definición. */
    @Test
    fun `control positivo, el escaner ve el menu nuevo en todas las pantallas`() {
        val conMenu = escaner.archivosQueMencionan("MenuDeLaApp")
        assertTrue("el escáner vio ${conMenu.size} archivos con MenuDeLaApp", conMenu.size >= 20)
    }
}
