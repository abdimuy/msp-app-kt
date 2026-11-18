package com.example.msp_app.feature.garantias.data.files

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La lectura y el borrado de las fotos de la captura.
 *
 * ## Qué protege
 *
 * [ArchivosDeGarantiaAdapter.leer] devuelve `null` cuando el archivo ya no está:
 * la cola lo trata como un rechazo visible y **nunca** como una subida exitosa.
 * Borrar un archivo ausente tampoco es un error.
 */
class ArchivosDeGarantiaAdapterTest : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directorio = DirectorioDeGarantias(context)
    private val adapter = ArchivosDeGarantiaAdapter(directorio)

    @Test
    fun `leer devuelve los bytes del archivo`() = runTest {
        val archivo = directorio.nuevoArchivo(DirectorioDeGarantias.FOTOS, "f.jpg")
        archivo.writeBytes(byteArrayOf(1, 2, 3))

        val bytes = adapter.leer(directorio.relativa(archivo))

        assertTrue(bytes!!.contentEquals(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `leer un archivo ausente devuelve null`() = runTest {
        assertNull(adapter.leer("fotos/no-existe.jpg"))
    }

    @Test
    fun `borrar elimina el archivo`() = runTest {
        val archivo = directorio.nuevoArchivo(DirectorioDeGarantias.FOTOS, "f.jpg")
        archivo.writeBytes(byteArrayOf(1))

        adapter.borrar(directorio.relativa(archivo))

        assertFalse(archivo.exists())
    }

    @Test
    fun `borrar un archivo ausente no lanza`() = runTest {
        adapter.borrar("fotos/no-existe.jpg")
    }
}
