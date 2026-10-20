package com.example.msp_app.core.sync.visitas

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-103, I8: prueba de forma sobre el código fuente de la bajada. El
 * manager y su proveedor no nombran `Visit`, su DAO ni su entidad, no abren
 * transacciones propias ni ejecutan SQL: la única vía de escritura que tienen
 * es `VisitaZonaDao`, que solo nombra `visita_zona` y `visitas_sync_state`
 * (`VisitaZonaNoEscribeVisitTest`).
 */
class VisitasZonaSyncManagerFormaTest {

    private val dir = File("src/main/java/com/example/msp_app/core/sync/visitas")
    private val archivos = listOf("VisitasZonaSyncManager.kt", "VisitasZonaSyncProvider.kt")

    private val prohibido = Regex(
        """\bVisit(?:Entity|Dao)\b|\bvisitDao\(|\bwithTransaction\b|\bexecSQL\b|\bopenHelper\b|""" +
            """\b(?:paymentDao|saleDao|localSaleDao|cobranzaSyncStateDao)\("""
    )

    @Test
    fun laBajadaNoNombraVisitNiTablasDeDinero_EAPP103() {
        archivos.forEach { nombre ->
            val texto = File(dir, nombre).readText()
            assertEquals(
                "$nombre no debe tener otra vía de escritura que VisitaZonaDao",
                emptyList<String>(),
                prohibido.findAll(texto).map { it.value }.toList()
            )
        }
    }

    /** Control positivo: la misma expresión caza lo que prohíbe. */
    @Test
    fun controlPositivoLaExpresionCazaUnaEscrituraEnVisit() {
        assertTrue(File(dir, archivos[0]).readText().contains("VisitaZonaDao"))
        val ajeno = "db.visitDao().insertVisit(v); db.withTransaction { }; db.paymentDao().x()"
        assertEquals(
            listOf("visitDao(", "withTransaction", "paymentDao("),
            prohibido.findAll(ajeno).map { it.value }.toList()
        )
    }
}
