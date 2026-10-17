package com.example.msp_app.core.visitas.envio

import com.example.msp_app.navigation.EscanerDeFuentes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I3 (spec visitas §4.2-1): `Visit.CONFIRMADA_EN` y `Visit.RESGUARDADA_EN` son
 * la prueba del servidor, y solo `VisitDao` las escribe. Cuenta como escritura
 * cualquier `CONFIRMADA_EN =`/`RESGUARDADA_EN =` en código de producción de
 * cualquier módulo: el `SET` de SQL (con valor o `NULL`) y también un
 * `copy(CONFIRMADA_EN = …)` de Kotlin seguido de un `insertVisit`.
 */
class EscritoresDeEntregaTest {

    private val escritura = Regex("""\b(CONFIRMADA_EN|RESGUARDADA_EN)\s*=(?!=)""")

    private val visitDao =
        "core/database/src/main/kotlin/com/example/msp_app/core/database/dao/visit/VisitDao.kt"

    private fun escritores(fuentes: List<Pair<String, String>>): Set<String> =
        fuentes.filter { (_, texto) -> escritura.containsMatchIn(texto) }.map { it.first }.toSet()

    private fun fuentesReales(): List<Pair<String, String>> {
        val raiz = (if (File("src/main/java").isDirectory) File("..") else File(".")).canonicalFile
        return EscanerDeFuentes().archivos.map { f ->
            f.canonicalFile.relativeTo(raiz).invariantSeparatorsPath to f.readText()
        }
    }

    @Test
    fun `I3 solo VisitDao escribe CONFIRMADA_EN y RESGUARDADA_EN`() {
        assertEquals(setOf(visitDao), escritores(fuentesReales()))
    }

    /** Control positivo: la MISMA búsqueda ve el DAO real y caza escritores ajenos. */
    @Test
    fun `control positivo el escaner ve el DAO y caza una escritura fuera de el`() {
        val reales = fuentesReales()
        assertTrue(reales.size > 100)
        assertTrue(escritores(reales).contains(visitDao))

        val ajenas = listOf(
            "app/Worker.kt" to "dao.insertVisit(v.copy(CONFIRMADA_EN = ahora))",
            "app/OtroDao.kt" to "UPDATE Visit SET RESGUARDADA_EN = NULL WHERE ID = :id",
            "app/Lector.kt" to "if (v.CONFIRMADA_EN == null) return",
            "app/Consulta.kt" to "WHERE CONFIRMADA_EN >= :desde AND RESGUARDADA_EN IS NULL"
        )
        assertEquals(setOf("app/Worker.kt", "app/OtroDao.kt"), escritores(ajenas))
    }
}
