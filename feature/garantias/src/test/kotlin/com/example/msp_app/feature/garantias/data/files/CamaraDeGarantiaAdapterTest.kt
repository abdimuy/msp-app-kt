package com.example.msp_app.feature.garantias.data.files

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.garantias.domain.port.CamaraDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric

/**
 * La cámara del sistema y el archivado de sus fotos.
 *
 * ## Qué protege
 *
 * El MIME que declara el proveedor no se cree: se leen los primeros bytes y se
 * compara con las firmas de JPEG y PNG. Un archivo vacío, uno que no es imagen
 * o uno más grande que [CamaraDeGarantiaPort.LIMITE_DE_BYTES] se descartan y el
 * crudo se borra **siempre**, incluso cuando se rechaza (un crudo huérfano se
 * quedaría para siempre en la caché). La clave se acuña una sola vez, al
 * archivar.
 *
 * `nuevoDestino()` no se prueba acá: depende del `FileProvider` de `:app`, que
 * no está declarado en el manifiesto de la librería.
 */
class CamaraDeGarantiaAdapterTest : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directorio = DirectorioDeGarantias(context)
    private val adapter = CamaraDeGarantiaAdapter(context, directorio, ClavesFijas())

    private val autoridad = "prueba.garantias"

    @Test
    fun `una foto jpeg se copia a filesDir y el crudo se borra`() = runTest {
        val bytes = FIRMA_JPEG + "contenido".toByteArray()
        val crudo = publicarCrudo("foto.jpg", bytes)

        val foto = adapter.archivar(uri(crudo))!!

        assertEquals("image/jpeg", foto.mime)
        assertEquals("clave-fija", foto.clave)
        assertEquals("fotos/clave-fija.jpg", foto.rutaArchivo)
        assertTrue(directorio.archivo(foto.rutaArchivo).readBytes().contentEquals(bytes))
        assertFalse(crudo.exists())
    }

    @Test
    fun `una foto png se guarda con extension png`() = runTest {
        val bytes = FIRMA_PNG + "contenido".toByteArray()
        val crudo = publicarCrudo("foto.png", bytes)

        val foto = adapter.archivar(uri(crudo))!!

        assertEquals("image/png", foto.mime)
        assertEquals("fotos/clave-fija.png", foto.rutaArchivo)
    }

    @Test
    fun `un archivo que no es imagen se descarta y el crudo se borra`() = runTest {
        val crudo = publicarCrudo("foto.bin", "no soy una imagen".toByteArray())

        val foto = adapter.archivar(uri(crudo))

        assertNull(foto)
        assertFalse(crudo.exists())
    }

    @Test
    fun `un archivo vacio se descarta`() = runTest {
        val crudo = publicarCrudo("vacio.jpg", ByteArray(0))

        assertNull(adapter.archivar(uri(crudo)))
        assertFalse(crudo.exists())
    }

    @Test
    fun `un archivo mayor al limite se descarta`() = runTest {
        val crudo = publicarCrudo("grande.jpg", ByteArray(0))
        RandomAccessFile(crudo, "rw").use {
            it.setLength(CamaraDeGarantiaPort.LIMITE_DE_BYTES + 1L)
        }

        assertNull(adapter.archivar(uri(crudo)))
        assertFalse(crudo.exists())
    }

    @Test
    fun `un content uri sin segmento se archiva pero no borra nada`() = runTest {
        val crudo = publicarCrudo("foto.jpg", FIRMA_JPEG + "x".toByteArray())

        val foto = adapter.archivar("content://$autoridad")

        assertNotNull(foto)
        assertTrue(crudo.exists())
    }

    private fun publicarCrudo(nombre: String, bytes: ByteArray): File {
        val carpeta = File(context.cacheDir, "garantias_camara")
        carpeta.mkdirs()
        val crudo = File(carpeta, nombre)
        crudo.writeBytes(bytes)
        ProveedorDeFotos.archivo = crudo
        Robolectric.buildContentProvider(ProveedorDeFotos::class.java).create(autoridad)
        return crudo
    }

    private fun uri(crudo: File): String = "content://$autoridad/${crudo.name}"

    private class ClavesFijas : GeneradorDeClavesPort {
        override fun nueva(): String = "clave-fija"
    }

    /** Un `ContentProvider` mínimo que sirve un archivo real por `openFile`. */
    class ProveedorDeFotos : ContentProvider() {

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null

        override fun getType(uri: Uri): String = "image/jpeg"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(archivo, ParcelFileDescriptor.MODE_READ_ONLY)

        companion object {
            lateinit var archivo: File
        }
    }

    private companion object {
        val FIRMA_JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val FIRMA_PNG = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
    }
}
