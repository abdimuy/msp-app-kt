package com.example.msp_app.feature.compartirproductos.android

import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

class IntentDeCompartirTest : RobolectricTestBase() {

    private fun uris(n: Int) = List(n) {
        Uri.parse(
            "content://com.example.msp_app.fileprovider/cache/compartir/tarjeta_${it}_$it.jpg"
        )
    }

    private fun instalar(paquete: String) {
        val info = PackageInfo().apply { packageName = paquete }
        shadowOf(RuntimeEnvironment.getApplication().packageManager).installPackage(info)
    }

    // region — qué app se abre

    @Test fun `WhatsApp normal gana sobre Business`() {
        assertEquals("com.whatsapp", IntentDeCompartir.paqueteDeWhatsApp { true })
    }

    @Test fun `si solo hay Business se usa Business`() {
        assertEquals(
            "com.whatsapp.w4b",
            IntentDeCompartir.paqueteDeWhatsApp { it == "com.whatsapp.w4b" }
        )
    }

    @Test fun `si no hay ninguno no hay paquete`() {
        assertNull(IntentDeCompartir.paqueteDeWhatsApp { false })
    }

    @Test fun `estaInstalado consulta el sistema de paquetes de verdad`() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        assertFalse(IntentDeCompartir.estaInstalado(pm, "com.whatsapp"))
        instalar("com.whatsapp")
        assertTrue(IntentDeCompartir.estaInstalado(pm, "com.whatsapp"))
        assertFalse(IntentDeCompartir.estaInstalado(pm, "com.whatsapp.w4b"))
    }

    @Test fun `paraEsteTelefono abre WhatsApp normal Business o el selector segun lo instalado`() {
        val ctx = RuntimeEnvironment.getApplication()
        val ninguno = IntentDeCompartir.paraEsteTelefono(ctx, uris(1), "t")
        assertEquals(Intent.ACTION_CHOOSER, ninguno.action)

        instalar("com.whatsapp.w4b")
        assertEquals(
            "com.whatsapp.w4b",
            IntentDeCompartir.paraEsteTelefono(ctx, uris(1), "t").`package`
        )

        instalar("com.whatsapp")
        assertEquals(
            "com.whatsapp",
            IntentDeCompartir.paraEsteTelefono(ctx, uris(1), "t").`package`
        )
    }
    // endregion

    // region — un producto

    @Test fun `un producto usa ACTION_SEND con imagen jpeg uri texto y permiso`() {
        val u = uris(1)
        val i = IntentDeCompartir.armar(u, "Sala Valencia\nContado: $8,990", "com.whatsapp")
        assertEquals(Intent.ACTION_SEND, i.action)
        assertEquals("image/jpeg", i.type)
        assertEquals("com.whatsapp", i.`package`)
        @Suppress("DEPRECATION")
        assertEquals(u[0], i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("Sala Valencia\nContado: $8,990", i.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertFalse(
            "no debe poder escribir",
            i.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
        )
    }

    @Test fun `el permiso de lectura tambien viaja en el ClipData`() {
        val u = uris(1)
        val i = IntentDeCompartir.armar(u, "t", "com.whatsapp")
        assertEquals(1, i.clipData!!.itemCount)
        assertEquals(u[0], i.clipData!!.getItemAt(0).uri)
    }
    // endregion

    // region — varios

    @Test fun `diez productos usan ACTION_SEND_MULTIPLE con las diez uris en orden`() {
        val u = uris(10)
        val i = IntentDeCompartir.armar(u, "t", "com.whatsapp")
        assertEquals(Intent.ACTION_SEND_MULTIPLE, i.action)
        assertEquals("image/jpeg", i.type)
        @Suppress("DEPRECATION")
        val enviadas = i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(u, enviadas)
        assertEquals(10, i.clipData!!.itemCount)
        assertEquals(u.last(), i.clipData!!.getItemAt(9).uri)
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun `dos productos ya son SEND_MULTIPLE y uno solo no`() {
        assertEquals(
            Intent.ACTION_SEND_MULTIPLE,
            IntentDeCompartir.armar(uris(2), "t", null).let {
                (it.extras!!.get(Intent.EXTRA_INTENT) as Intent).action
            }
        )
        assertEquals(
            Intent.ACTION_SEND,
            IntentDeCompartir.armar(uris(1), "t", "com.whatsapp").action
        )
    }

    @Test fun `sin tarjetas no se arma nada`() {
        assertThrows(IllegalArgumentException::class.java) {
            IntentDeCompartir.armar(emptyList(), "t", "com.whatsapp")
        }
    }
    // endregion

    // region — sin WhatsApp

    @Test fun `sin WhatsApp sale el selector con el mismo envio dentro y permiso de lectura`() {
        val u = uris(3)
        val selector = IntentDeCompartir.armar(u, "hola", null)
        assertEquals(Intent.ACTION_CHOOSER, selector.action)
        assertTrue(selector.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val dentro = selector.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, dentro.action)
        assertNull("el selector no fuerza paquete", dentro.`package`)
        assertEquals("hola", dentro.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(dentro.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
    // endregion
}
