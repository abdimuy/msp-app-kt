package com.example.msp_app.features.sales.data

import android.net.Uri
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * E-APP-094 (D5): las fotos de la venta se comprimen antes de la transacción con
 * nombre fijo, y una foto que falla detiene la venta en vez de tragarse.
 */
class FotosDeLaVentaLocalTest : RobolectricTestBase() {

    private lateinit var dir: File

    @Before
    fun crearDirectorio() {
        dir = Files.createTempDirectory("fotos-venta").toFile()
    }

    private val escribe = FotosDeLaVentaLocal.Compresor { _, destino ->
        destino.writeBytes(byteArrayOf(1, 2, 3))
        destino
    }

    @Test
    fun `E-APP-094 reintentar el mismo borrador da los mismos ids y archivos`() = runTest {
        val fotos = FotosDeLaVentaLocal(dir, escribe)
        val uris =
            listOf(
                Uri.parse("content://x/draft_images/a.jpg"),
                Uri.parse("content://x/draft_images/b.jpg")
            )

        val primera = fotos.preparar(ID, uris, FECHA)
        val segunda = fotos.preparar(ID, uris, FECHA)

        assertEquals(primera.map { it.LOCAL_SALE_IMAGE_ID }, segunda.map { it.LOCAL_SALE_IMAGE_ID })
        assertEquals(primera.map { it.IMAGE_URI }, segunda.map { it.IMAGE_URI })
        assertEquals("sin archivos de más ni temporales", 2, dir.listFiles()!!.size)
        primera.forEach {
            assertEquals("sale_${ID}_${it.LOCAL_SALE_IMAGE_ID}.jpg", File(it.IMAGE_URI).name)
        }
    }

    @Test
    fun `E-APP-094 una foto que falla no se traga - lanza con su numero y no deja temporal`() =
        runTest {
            val fallaLaSegunda = FotosDeLaVentaLocal.Compresor { uri, destino ->
                if (uri.toString().endsWith("b.jpg")) {
                    destino.writeBytes(byteArrayOf(9))
                    throw IOException("no se pudo decodificar")
                }
                destino.writeBytes(byteArrayOf(1))
                destino
            }
            val uris = listOf(Uri.parse("content://x/a.jpg"), Uri.parse("content://x/b.jpg"))

            try {
                FotosDeLaVentaLocal(dir, fallaLaSegunda).preparar(ID, uris, FECHA)
                fail("antes la foto fallida se saltaba y la venta seguía")
            } catch (fallida: FotosDeLaVentaLocal.FotoNoProcesada) {
                assertEquals(2, fallida.numero)
                assertEquals("No se pudo procesar la foto 2", fallida.message)
            }
            assertTrue(dir.listFiles()!!.none { it.name.endsWith(".tmp") })
        }

    @Test
    fun `E-APP-094 una foto comprimida vacia cuenta como fallida`() = runTest {
        val vacia = FotosDeLaVentaLocal.Compresor { _, destino ->
            destino.writeBytes(ByteArray(0))
            destino
        }
        try {
            FotosDeLaVentaLocal(
                dir,
                vacia
            ).preparar(ID, listOf(Uri.parse("content://x/a.jpg")), FECHA)
            fail("una foto vacía no es una foto")
        } catch (fallida: FotosDeLaVentaLocal.FotoNoProcesada) {
            assertEquals(1, fallida.numero)
        }
    }

    private companion object {
        const val ID = "5f1c2a3b-7d4e-4c8f-9a1b-2c3d4e5f6a7b"
        const val FECHA = "2026-10-26T18:00:00Z"
    }
}
