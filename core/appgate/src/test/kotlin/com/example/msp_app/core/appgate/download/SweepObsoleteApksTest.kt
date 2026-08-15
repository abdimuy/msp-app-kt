package com.example.msp_app.core.appgate.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val INSTALLED_CODE = 57

/**
 * El barrido de después de instalar.
 *
 * Los 11 MB del APK viven en `filesDir`, que Android **nunca** recupera (esa
 * es la decisión correcta: un `cacheDir` podría vaciarse justo antes de que el
 * cobrador instale y lo obligaría a rebajar el archivo, quizá con sus datos).
 * El precio de esa decisión es que hay que borrarlo a mano, y esto es ese
 * borrado.
 */
class SweepObsoleteApksTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /** Lector de mentira: la versión sale del nombre del archivo. */
    private val readerPorNombre = ApkVersionReader { apk ->
        apk.name.removeSuffix(".apk").toIntOrNull()?.let { code ->
            ApkVersion(versionCode = code, versionName = "2.$code.0")
        }
    }

    private fun apk(name: String): File = File(tempFolder.root, name).apply { writeText("apk") }

    @Test
    fun `el APK que ya se instalo se borra`() {
        val instalado = apk("$INSTALLED_CODE.apk")

        val borrados = sweepObsoleteApks(tempFolder.root, INSTALLED_CODE, readerPorNombre)

        assertEquals(1, borrados)
        assertFalse("el archivo ya instalado no tiene por qué seguir ahí", instalado.exists())
    }

    @Test
    fun `un APK mas nuevo que el instalado se conserva - es la descarga adelantada`() {
        val futuro = apk("58.apk")

        sweepObsoleteApks(tempFolder.root, INSTALLED_CODE, readerPorNombre)

        assertTrue("borrarlo obligaría a rebajar 11 MB cuando llegue el bloqueo", futuro.exists())
    }

    @Test
    fun `un archivo cuya version no se puede leer se conserva - suele ser un parcial`() {
        val parcial = apk("a-medias.apk")

        sweepObsoleteApks(tempFolder.root, INSTALLED_CODE, readerPorNombre)

        assertTrue("un parcial se reanuda, no se rebaja entero", parcial.exists())
    }

    @Test
    fun `un directorio que no existe no es un problema`() {
        val inexistente = File(tempFolder.root, "updates")

        assertEquals(0, sweepObsoleteApks(inexistente, INSTALLED_CODE, readerPorNombre))
    }

    @Test
    fun `un APK a medias no se puede leer y por eso no se toca`() {
        val parcial = apk("sin-version.apk")

        assertEquals(
            0,
            sweepObsoleteApks(tempFolder.root, INSTALLED_CODE, ApkVersionReader { null })
        )
        assertTrue(parcial.exists())
    }

    @Test
    fun `barre varios de golpe y respeta al nuevo`() {
        val viejo = apk("55.apk")
        val instalado = apk("$INSTALLED_CODE.apk")
        val futuro = apk("58.apk")

        val borrados = sweepObsoleteApks(tempFolder.root, INSTALLED_CODE, readerPorNombre)

        assertEquals(2, borrados)
        assertFalse(viejo.exists())
        assertFalse(instalado.exists())
        assertTrue(futuro.exists())
    }
}
