package com.example.msp_app.feature.garantias.data.files

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La firma del cliente en `filesDir/garantias/firmas/`.
 *
 * ## Qué protege
 *
 * La regla del megabyte es la misma del servidor: si el teléfono aceptara una
 * firma más grande, la entrega se encolaría para morir en 422. Una firma vacía
 * tampoco es una firma. Y el PNG se escribe **antes** de encolar la entrega, así
 * que la ruta devuelta tiene que apuntar a un archivo que existe.
 */
class FirmaDeConformidadAdapterTest : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directorio = DirectorioDeGarantias(context)
    private val adapter = FirmaDeConformidadAdapter(directorio)

    @Test
    fun `un png valido se guarda con su ruta relativa y su tamano`() = runTest {
        val bytes = ByteArray(64) { it.toByte() }

        val firma = adapter.guardarPng(bytes)!!

        assertEquals(64, firma.tamanoBytes)
        assertEquals(ReglasDeEntrega.MIME_DE_LA_FIRMA, firma.mime)
        assertTrue(firma.rutaArchivo.startsWith("firmas/"))
        assertTrue(directorio.archivo(firma.rutaArchivo).readBytes().contentEquals(bytes))
    }

    @Test
    fun `una firma vacia no se guarda`() = runTest {
        assertNull(adapter.guardarPng(ByteArray(0)))
    }

    @Test
    fun `una firma mayor al limite no se guarda`() = runTest {
        assertNull(adapter.guardarPng(ByteArray(ReglasDeEntrega.MAX_BYTES_DE_FIRMA + 1)))
    }

    @Test
    fun `una firma justo en el limite si se guarda`() = runTest {
        val firma = adapter.guardarPng(ByteArray(ReglasDeEntrega.MAX_BYTES_DE_FIRMA))

        assertEquals(ReglasDeEntrega.MAX_BYTES_DE_FIRMA, firma!!.tamanoBytes)
    }

    @Test
    fun `borrar elimina el png`() = runTest {
        val firma = adapter.guardarPng(ByteArray(4))!!
        val archivo = directorio.archivo(firma.rutaArchivo)
        assertTrue(archivo.exists())

        adapter.borrar(firma.rutaArchivo)

        assertFalse(archivo.exists())
    }
}
