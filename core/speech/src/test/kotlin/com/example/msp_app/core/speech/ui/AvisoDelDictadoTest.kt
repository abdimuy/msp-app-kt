package com.example.msp_app.core.speech.ui

import com.example.msp_app.core.speech.domain.FalloDelDictado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El texto del aviso, uno por motivo, y **la regla de palabras del repo medida**
 * en vez de recordada: `CLAUDE.md` §3 pide 2-4 palabras, mayúscula inicial y sin
 * punto final. Una regla de estilo que nadie mide se rompe en el segundo commit.
 */
class AvisoDelDictadoTest {

    @Test
    fun `cada motivo que el cobrador puede arreglar tiene su aviso`() {
        assertEquals("Sin permiso del micrófono", avisoDe(FalloDelDictado.SIN_PERMISO))
        assertEquals("Este teléfono no dicta", avisoDe(FalloDelDictado.SIN_MOTOR))
        assertEquals("El micrófono está ocupado", avisoDe(FalloDelDictado.MICROFONO_OCUPADO))
        assertEquals("No se pudo dictar", avisoDe(FalloDelDictado.MOTOR_FALLO))
    }

    /**
     * **El silencio no avisa.** Nadie habló: pintar un aviso inventaría un
     * problema que no existe.
     */
    @Test
    fun `el silencio y el audio perdido no avisan`() {
        assertNull(avisoDe(FalloDelDictado.SIN_HABLA))
        assertNull(avisoDe(FalloDelDictado.AUDIO_NO_SE_GUARDO))
        assertNull(avisoDe(null))
    }

    /** Control positivo: si `avisoDe` devolviera siempre `null`, lo de arriba no probaría nada. */
    @Test
    fun `al menos un motivo si produce texto`() {
        assertNotNull(
            "si ningun motivo avisa, el test de los silencios no prueba nada",
            FalloDelDictado.entries.firstNotNullOfOrNull { avisoDe(it) }
        )
    }

    @Test
    fun `los avisos respetan el vocabulario del repo`() {
        FalloDelDictado.entries.mapNotNull { avisoDe(it) }.forEach { aviso ->
            val palabras = aviso.split(" ")
            assertTrue(
                "\"$aviso\" tiene ${palabras.size} palabras y el tope es 4",
                palabras.size <= MAXIMO
            )
            assertTrue("\"$aviso\" no empieza con mayuscula", aviso.first().isUpperCase())
            assertTrue("\"$aviso\" termina con punto", !aviso.endsWith("."))
            assertTrue("\"$aviso\" dice ciclo; en la UI se dice semana", !aviso.contains("ciclo"))
        }
    }

    private companion object {
        /** El tope del repo: 2-4 palabras en texto de usuario. */
        const val MAXIMO = 4
    }
}
