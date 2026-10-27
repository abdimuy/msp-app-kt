package com.example.msp_app.core.common.visitas.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El VERSION del juego de compromisos es el SHA-256 de los .json ordenados por
 * nombre y concatenados, el mismo que comprueba msp-api
 * (`TestCasosCompromisos_VersionCorrespondeAlHash`). El mismo VERSION en los
 * dos repos demuestra que los dos prueban los mismos bytes (I5, E-APP-104).
 */
class JuegoDeCompromisosVersionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `VERSION coincide con el SHA-256 de los casos`() {
        val archivos = JuegoDeCompromisos.archivosDeCaso()
        assertEquals(JuegoDeCompromisos.versionDeclarada(), JuegoDeCompromisos.sha256(archivos))
    }

    @Test
    fun `el juego trae al menos 30 casos`() {
        assertTrue(JuegoDeCompromisos.archivosDeCaso().size >= MINIMO_DE_CASOS)
    }

    @Test
    fun `VERSION tiene 64 hex minusculas`() {
        assertTrue(Regex("^[0-9a-f]{64}$").matches(JuegoDeCompromisos.versionDeclarada()))
    }

    @Test
    fun `un byte de mas cambia el hash - control positivo`() {
        val archivos = JuegoDeCompromisos.archivosDeCaso()
        val copias = archivos.mapIndexed { i, f ->
            File(tmp.root, f.name).apply {
                writeBytes(
                    if (i == archivos.lastIndex) f.readBytes() + ' '.code.toByte() else f.readBytes()
                )
            }
        }
        assertNotEquals(JuegoDeCompromisos.versionDeclarada(), JuegoDeCompromisos.sha256(copias))
    }

    @Test
    fun `sha256 concatena en orden de nombre - vector conocido`() {
        File(tmp.root, "002-b.json").writeText("b")
        File(tmp.root, "001-a.json").writeText("a")
        File(tmp.root, "VERSION").writeText("x")
        val archivos = JuegoDeCompromisos.archivosDeCaso(tmp.root)
        assertEquals(listOf("001-a.json", "002-b.json"), archivos.map { it.name })
        // printf ab | shasum -a 256
        assertEquals(
            "fb8e20fc2e4c3f248c60c39bd652f3c1347298bb977b8b4d5903b85055620603",
            JuegoDeCompromisos.sha256(archivos)
        )
    }

    @Test
    fun `archivo fuera de formato se rechaza`() {
        for (intruso in listOf("1-caso.json", "README.md", "002-Con-Mayusculas.json")) {
            val dir = tmp.newFolder()
            File(dir, "001-a.json").writeText("a")
            File(dir, intruso).writeText("x")
            try {
                JuegoDeCompromisos.archivosDeCaso(dir)
                fail("debió rechazar $intruso")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message.orEmpty().contains(intruso))
            }
        }
    }

    private companion object {
        const val MINIMO_DE_CASOS = 30
    }
}
