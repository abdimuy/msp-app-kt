package com.example.msp_app.core.database.dao.avisos

import com.example.msp_app.core.database.entities.AvisoProgramadoEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val T1 = "tarea-7f3c2a10-0001"
private const val EN_1 = "2026-10-26T15:00:00Z"
private const val EN_2 = "2026-10-26T16:00:00Z"

/**
 * E-APP-121 · `aviso_emitido` y `aviso_programado` (spec visitas §6.5 y §6.6;
 * plan visitas P3, Tarea 10, paso 3).
 */
class AvisosDaoTest : RoomTestBase() {

    private val dao get() = db.avisosDao()

    @Test
    fun marcarEmitidoEsIdempotente_EAPP121() = runTest {
        assertFalse(dao.emitido(T1))

        assertTrue("la primera vez inserta", dao.marcarEmitido(T1, EN_1))
        assertFalse("la segunda no", dao.marcarEmitido(T1, EN_2))
        assertFalse("ni la tercera", dao.marcarEmitido(T1, EN_2))

        assertTrue(dao.emitido(T1))
        dao.podarEmitidosAnterioresA(EN_2)
        assertFalse(
            "conserva la hora de la PRIMERA emisión: con la segunda no se habría podado",
            dao.emitido(T1)
        )
    }

    @Test
    fun podarSoloQuitaLoAnteriorAlLimite_EAPP121() = runTest {
        dao.marcarEmitido("vieja", "2026-10-01T08:00:00Z")
        dao.marcarEmitido("limite", "2026-10-10T08:00:00Z")
        dao.marcarEmitido("nueva", "2026-10-20T08:00:00Z")

        dao.podarEmitidosAnterioresA("2026-10-10T08:00:00Z")

        assertEquals(
            listOf(false, true, true),
            listOf("vieja", "limite", "nueva").map { dao.emitido(it) }
        )
    }

    @Test
    fun reemplazarProgramadosDevuelveLosAnteriores_EAPP121() = runTest {
        val a = aviso("a", "2026-10-27T14:00:00Z")
        val b = aviso("b", "2026-10-27T08:00:00Z")
        val c = aviso("c", "2026-10-28T08:00:00Z")

        assertEquals(emptyList<AvisoProgramadoEntity>(), dao.reemplazarProgramados(listOf(a, b)))
        assertEquals("por instante", listOf(b, a), dao.programados())

        assertEquals("devuelve lo que había", listOf(b, a), dao.reemplazarProgramados(listOf(c, a)))
        assertEquals("y deja solo lo nuevo", listOf(a, c), dao.programados())

        assertEquals(listOf(a, c), dao.reemplazarProgramados(emptyList()))
        assertEquals(emptyList<AvisoProgramadoEntity>(), dao.programados())
    }

    private fun aviso(id: String, instante: String) =
        AvisoProgramadoEntity(ID = id, INSTANTE = instante, TIPO = "tarea", ORIGEN_ID = "o-$id")
}
