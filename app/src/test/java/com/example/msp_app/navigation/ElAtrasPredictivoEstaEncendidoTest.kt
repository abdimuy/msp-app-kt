package com.example.msp_app.navigation

import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sin `android:enableOnBackInvokedCallback="true"` Android 14 no da el atrás
 * predictivo aunque el menú lo soporte (spec §2.1.7; decisión D10 del plan).
 */
class ElAtrasPredictivoEstaEncendidoTest {

    private val manifiesto: File =
        listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first {
            it.isFile
        }

    @Test
    fun `la aplicacion declara enableOnBackInvokedCallback`() {
        val aplicacion = Regex("""<application\b[^>]*>""").find(manifiesto.readText())?.value
        assertNotNull(
            "no se encontró <application> en ${manifiesto.path}: el test no miraría nada",
            aplicacion
        )
        assertTrue(aplicacion!!.contains("android:enableOnBackInvokedCallback=\"true\""))
    }
}
