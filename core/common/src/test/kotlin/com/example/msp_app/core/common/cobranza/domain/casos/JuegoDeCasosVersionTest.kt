package com.example.msp_app.core.common.cobranza.domain.casos

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El VERSION del juego compartido es el SHA-256 de los .json ordenados por
 * nombre y concatenados (contratos de desempeño §7). El mismo VERSION en
 * msp-api y aquí demuestra que los dos repos prueban los mismos bytes; cambiar
 * un caso sin actualizar VERSION pone esto en rojo.
 */
class JuegoDeCasosVersionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `VERSION coincide con el SHA-256 de los casos`() {
        val archivos = JuegoDeCasos.archivosDeCaso()
        assertEquals(JuegoDeCasos.versionDeclarada(), JuegoDeCasos.sha256(archivos))
    }

    @Test
    fun `el juego no esta vacio`() {
        assertTrue(
            "el directorio de casos no trae ningún .json",
            JuegoDeCasos.archivosDeCaso().isNotEmpty()
        )
    }

    @Test
    fun `VERSION tiene 64 hex minusculas`() {
        assertTrue(Regex("^[0-9a-f]{64}$").matches(JuegoDeCasos.versionDeclarada()))
    }

    @Test
    fun `sha256 concatena en orden de nombre - vector conocido`() {
        // Escritos en desorden a propósito: el orden lo pone archivosDeCaso.
        File(tmp.root, "002-b.json").writeText("b")
        File(tmp.root, "001-a.json").writeText("a")
        File(tmp.root, "VERSION").writeText("x")
        val archivos = JuegoDeCasos.archivosDeCaso(tmp.root)
        assertEquals(listOf("001-a.json", "002-b.json"), archivos.map { it.name })
        // printf ab | shasum -a 256
        assertEquals(
            "fb8e20fc2e4c3f248c60c39bd652f3c1347298bb977b8b4d5903b85055620603",
            JuegoDeCasos.sha256(archivos)
        )
    }

    @Test
    fun `archivo fuera de formato se rechaza`() {
        File(tmp.root, "001-a.json").writeText("a")
        File(tmp.root, "README.md").writeText("x")
        try {
            JuegoDeCasos.archivosDeCaso(tmp.root)
            fail("debió rechazar README.md")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("README.md"))
        }
    }
}
