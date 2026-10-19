package com.example.msp_app.core.database.dao.visit

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-080, I8 (spec visitas §5.4.8): lo bajado va a `visita_zona` y NUNCA a
 * `Visit`. Prueba de forma sobre el código fuente:
 *
 * 1. `VisitaZonaDao.kt` solo nombra las tablas `visita_zona` y
 *    `visitas_sync_state` en su SQL, y no menciona `Visit`, `VisitEntity` ni
 *    `VisitDao` en ningún lado (ni lee ni escribe la tabla de salida, ni toca
 *    una tabla de dinero).
 * 2. En todo `:core:database` (fuera de migraciones), el único archivo con SQL
 *    que escribe en `Visit` es `VisitDao.kt`, y el único `@Insert`/`@Upsert`/
 *    `@Update`/`@Delete` sobre `VisitEntity` vive ahí.
 */
class VisitaZonaNoEscribeVisitTest {

    private val raiz = File("src/main/kotlin/com/example/msp_app/core/database")
    private val visitaZonaDao = "dao/visit/VisitaZonaDao.kt"
    private val visitDao = "dao/visit/VisitDao.kt"

    /** Tablas que aparecen tras FROM/INTO/UPDATE/JOIN en el texto. */
    private val tabla = Regex(
        """\b(?:FROM|INTO|UPDATE|JOIN)\s+`?([A-Za-z_][A-Za-z0-9_]*)`?""",
        RegexOption.IGNORE_CASE
    )
    private val mencionaVisit = Regex("""\bVisit(?:Entity|Dao)?\b""")
    private val escribeVisit = Regex(
        """\b(?:UPDATE(?:\s+OR\s+\w+)?|INSERT(?:\s+OR\s+\w+)?\s+INTO|REPLACE\s+INTO|DELETE\s+FROM)\s+`?Visit`?(?![A-Za-z0-9_])""",
        RegexOption.IGNORE_CASE
    )
    private val metodoDeEscrituraDeVisitEntity = Regex(
        """@(?:Insert|Upsert|Update|Delete)\b[^@]*?\(\s*\w+\s*:\s*(?:List<)?VisitEntity\b""",
        RegexOption.DOT_MATCHES_ALL
    )

    private fun fuentes(): Map<String, String> = raiz.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(raiz).invariantSeparatorsPath to it.readText() }
        .filterNot { (ruta, _) -> ruta.startsWith("migrations/") }
        .toMap()

    private fun tablasDe(texto: String): Set<String> =
        tabla.findAll(texto).map { it.groupValues[1] }.toSet()

    private fun escritoresDeVisit(fuentes: Map<String, String>): Set<String> = fuentes
        .filter { (_, t) -> escribeVisit.containsMatchIn(t) || metodoDeEscrituraDeVisitEntity.containsMatchIn(t) }
        .keys

    @Test
    fun `E-APP-080 VisitaZonaDao solo nombra visita_zona y visitas_sync_state`() {
        val texto = fuentes().getValue(visitaZonaDao)

        assertEquals(setOf("visita_zona", "visitas_sync_state"), tablasDe(texto))
        assertEquals(
            "VisitaZonaDao no debe mencionar Visit, VisitEntity ni VisitDao",
            emptyList<String>(),
            mencionaVisit.findAll(texto).map { it.value }.toList()
        )
    }

    @Test
    fun `E-APP-080 solo VisitDao escribe en Visit`() {
        assertEquals(setOf(visitDao), escritoresDeVisit(fuentes()))
    }

    /** Control positivo: las MISMAS búsquedas ven lo real y cazan lo ajeno. */
    @Test
    fun `control positivo las busquedas cazan una escritura en Visit y una tabla ajena`() {
        val reales = fuentes()
        assertTrue("el escáner ve los DAO reales: ${reales.keys}", reales.size > 20)
        assertTrue(reales.containsKey(visitaZonaDao))
        assertTrue(escritoresDeVisit(reales).contains(visitDao))
        assertTrue(tablasDe(reales.getValue(visitDao)).contains("Visit"))

        val ajenas = mapOf(
            "a.kt" to "@Query(\"UPDATE Visit SET NOTA = :n\")",
            "b.kt" to "@Query(\"INSERT OR REPLACE INTO `Visit` (ID) VALUES (:id)\")",
            "c.kt" to "@Query(\"DELETE FROM Visit WHERE ID = :id\")",
            "d.kt" to "@Insert(onConflict = OnConflictStrategy.REPLACE)\n    suspend fun x(v: VisitEntity)",
            "e.kt" to "@Upsert\n    abstract suspend fun y(v: List<VisitEntity>)",
            "f.kt" to "@Query(\"DELETE FROM visita_zona WHERE ID = :id\")",
            "g.kt" to "@Query(\"SELECT * FROM Visit\")"
        )
        assertEquals(setOf("a.kt", "b.kt", "c.kt", "d.kt", "e.kt"), escritoresDeVisit(ajenas))
        assertEquals(
            setOf("visita_zona", "Payment"),
            tablasDe("DELETE FROM visita_zona; UPDATE `Payment` SET X = 1")
        )
        assertEquals(
            listOf("Visit", "VisitEntity"),
            mencionaVisit.findAll("FROM Visit; v: VisitEntity; visita_zona; VisitaZonaEntity")
                .map { it.value }.toList()
        )
    }
}
