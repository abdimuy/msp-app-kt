package com.example.msp_app.core.appgate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La tabla completa de [decideVersionGate]. JVM plano: la decisión no toca
 * Android, ni red, ni reloj — y por eso es la pieza con más pruebas del
 * módulo. Si algo de esto se rompe, la app se bloquea sola en campo.
 *
 * Cada caso está escrito como un renglón de la tabla del KDoc, incluidos los
 * bordes que en producción son los que muerden: instalado **igual** al mínimo
 * (permitido, no bloqueado), mínimo ausente/`0` (compuerta apagada) y mínimo
 * negativo (dato corrupto, tampoco bloquea).
 */
class VersionGateTest {

    private fun decide(installed: Int, minimum: Int, exempt: Boolean = false) = decideVersionGate(
        installedVersionCode = installed,
        minVersionCode = minimum,
        exempt = exempt
    )

    // --- Compuerta apagada -----------------------------------------------------

    @Test
    fun `sin minimo configurado (0) no bloquea aunque la version sea vieja`() {
        assertEquals(
            VersionVerdict.ALLOWED,
            decide(installed = 1, minimum = NO_MINIMUM_VERSION_CODE)
        )
    }

    @Test
    fun `un minimo negativo (dato corrupto) no bloquea`() {
        assertEquals(VersionVerdict.ALLOWED, decide(installed = 1, minimum = -5))
    }

    @Test
    fun `sin minimo y sin version instalada valida tampoco bloquea`() {
        assertEquals(VersionVerdict.ALLOWED, decide(installed = 0, minimum = 0))
    }

    // --- Comparación por versionCode -------------------------------------------

    @Test
    fun `instalado MENOR que el minimo bloquea`() {
        assertEquals(VersionVerdict.BLOCKED, decide(installed = 55, minimum = 56))
    }

    @Test
    fun `instalado IGUAL al minimo permite`() {
        // El borde que más veces se programa al revés: el mínimo es inclusivo.
        assertEquals(VersionVerdict.ALLOWED, decide(installed = 56, minimum = 56))
    }

    @Test
    fun `instalado MAYOR que el minimo permite`() {
        assertEquals(VersionVerdict.ALLOWED, decide(installed = 57, minimum = 56))
    }

    @Test
    fun `instalado muy por debajo del minimo bloquea`() {
        assertEquals(VersionVerdict.BLOCKED, decide(installed = 1, minimum = 56))
    }

    @Test
    fun `instalado en 0 con minimo real bloquea`() {
        assertEquals(VersionVerdict.BLOCKED, decide(installed = 0, minimum = 56))
    }

    // --- Exención --------------------------------------------------------------

    @Test
    fun `exento no bloquea aunque la version sea menor`() {
        assertEquals(VersionVerdict.ALLOWED, decide(installed = 1, minimum = 56, exempt = true))
    }

    @Test
    fun `exento tampoco altera el caso que ya estaba permitido`() {
        assertEquals(VersionVerdict.ALLOWED, decide(installed = 99, minimum = 56, exempt = true))
    }

    @Test
    fun `NO exento con version menor SI bloquea - el gemelo del caso exento`() {
        // Mismo estímulo que el test de arriba salvo por `exempt`: si la
        // exención estuviera cableada al revés, uno de los dos fallaría.
        assertEquals(VersionVerdict.BLOCKED, decide(installed = 1, minimum = 56, exempt = false))
    }

    // --- Barrido: la tabla entera en un solo lugar ------------------------------

    @Test
    fun `barrido de la tabla completa`() {
        val casos = listOf(
            Triple(56, 56, false) to VersionVerdict.ALLOWED,
            Triple(57, 56, false) to VersionVerdict.ALLOWED,
            Triple(55, 56, false) to VersionVerdict.BLOCKED,
            Triple(55, 0, false) to VersionVerdict.ALLOWED,
            Triple(55, -1, false) to VersionVerdict.ALLOWED,
            Triple(55, 56, true) to VersionVerdict.ALLOWED,
            Triple(0, 1, false) to VersionVerdict.BLOCKED,
            Triple(0, 0, true) to VersionVerdict.ALLOWED
        )
        casos.forEach { (entrada, esperado) ->
            val (installed, minimum, exempt) = entrada
            assertEquals(
                "instalado=$installed minimo=$minimum exento=$exempt",
                esperado,
                decide(installed, minimum, exempt)
            )
        }
    }
}
