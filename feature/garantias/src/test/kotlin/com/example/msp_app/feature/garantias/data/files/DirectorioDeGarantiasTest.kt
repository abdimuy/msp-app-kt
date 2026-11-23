package com.example.msp_app.feature.garantias.data.files

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La raíz de los archivos de la captura.
 *
 * ## Qué protege
 *
 * Las rutas que se guardan en Room son **relativas** a `filesDir/garantias/` y
 * con separadores de barra, no la ruta absoluta del teléfono: `filesDir` cambia
 * entre instalaciones y una ruta absoluta guardada ayer apuntaría hoy a la nada.
 * La prueba fija la raíz y el formato de la ruta.
 */
class DirectorioDeGarantiasTest : RobolectricTestBase() {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directorio = DirectorioDeGarantias(context)

    @Test
    fun `la raiz es filesDir garantias`() {
        val esperado = File(File(context.filesDir, "garantias"), "fotos/x.jpg")

        assertEquals(esperado, directorio.archivo("fotos/x.jpg"))
    }

    @Test
    fun `un archivo nuevo crea su carpeta`() {
        val archivo = directorio.nuevoArchivo(DirectorioDeGarantias.FOTOS, "f.jpg")

        assertTrue(archivo.parentFile!!.isDirectory)
        assertEquals("f.jpg", archivo.name)
    }

    @Test
    fun `la ruta relativa usa separadores de barra`() {
        val archivo = directorio.nuevoArchivo(DirectorioDeGarantias.FIRMAS, "f.png")

        assertEquals("firmas/f.png", directorio.relativa(archivo))
    }
}
