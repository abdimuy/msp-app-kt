package com.example.msp_app.core.database.dao.tareas

import com.example.msp_app.core.database.entities.TareaZonaEntity
import com.example.msp_app.core.database.entities.TareasSyncStateEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ZONA = 21552
private const val OTRA_ZONA = 21550
private const val C1 = "2026-10-26T15:00:00.123456Z"
private const val C2 = "2026-10-26T16:00:00.654321Z"

/**
 * E-APP-121 · `tarea_zona` y `tareas_sync_state` (spec visitas §6.2, §5.4.3;
 * plan visitas P3, Tarea 10): la página y su posición van juntas, y los
 * reinicios dejan cursor y `AFTER_ID` nulos en la misma operación.
 */
class TareaZonaDaoTest : RoomTestBase() {

    private val dao get() = db.tareaZonaDao()

    @Test
    fun aplicarPaginaGuardaLasTareasYElCursorPar_EAPP121() = runTest {
        dao.aplicarPagina(ZONA, listOf(tarea("t-1"), tarea("t-2")), C1, "t-2")

        assertEquals(listOf(tarea("t-1"), tarea("t-2")), dao.deZona(ZONA))
        assertEquals(TareasSyncStateEntity(ZONA, C1, "t-2", null), dao.estado(ZONA))
    }

    @Test
    fun laCancelacionLlegaComoCambioDeLaMismaFila_EAPP121() = runTest {
        dao.aplicarPagina(ZONA, listOf(tarea("t-1")), C1, "t-1")
        val cancelada = tarea("t-1").copy(UPDATED_AT = C2, CANCELADA_AT = C2)

        dao.aplicarPagina(ZONA, listOf(cancelada), C2, "t-1")

        assertEquals(listOf(cancelada), dao.deZona(ZONA))
    }

    @Test
    fun mediaPosicionSeRechazaSinEscribirNada_EAPP121() = runTest {
        dao.aplicarPagina(ZONA, listOf(tarea("t-1")), C1, "t-1")

        val error = runCatching { dao.aplicarPagina(ZONA, listOf(tarea("t-2")), null, "t-2") }
            .exceptionOrNull()

        assertTrue("$error", error is IllegalArgumentException)
        assertEquals(listOf("t-1"), dao.deZona(ZONA).map { it.ID })
        assertEquals(TareasSyncStateEntity(ZONA, C1, "t-1", null), dao.estado(ZONA))
    }

    @Test
    fun cambioDeZonaBorraLoAjenoYDejaTodasSinPosicion_EAPP121() = runTest {
        dao.aplicarPagina(OTRA_ZONA, listOf(tarea("w-1", OTRA_ZONA)), C1, "w-1")
        dao.guardarEpoch(OTRA_ZONA, 3)
        assertEquals(2, dao.restosDeOtraZona(ZONA))

        dao.reiniciarZona(ZONA)

        assertEquals(emptyList<TareaZonaEntity>(), dao.deZona(OTRA_ZONA))
        assertEquals(TareasSyncStateEntity(OTRA_ZONA, null, null, 3), dao.estado(OTRA_ZONA))
        assertEquals(TareasSyncStateEntity(ZONA, null, null, null), dao.estado(ZONA))
        assertEquals(0, dao.restosDeOtraZona(ZONA))
    }

    @Test
    fun replayPorEpochBorraTodoYConservaLaGeneracion_EAPP121() = runTest {
        dao.aplicarPagina(ZONA, listOf(tarea("t-1")), C1, "t-1")
        dao.guardarEpoch(ZONA, 7)

        dao.reiniciarPorEpoch(ZONA)

        assertEquals(emptyList<TareaZonaEntity>(), dao.deZona(ZONA))
        assertEquals(TareasSyncStateEntity(ZONA, null, null, 7), dao.estado(ZONA))
    }

    @Test
    fun guardarEpochNoMueveLaPosicion_EAPP121() = runTest {
        dao.aplicarPagina(ZONA, emptyList(), C1, "t-9")

        dao.guardarEpoch(ZONA, 4)

        assertEquals(TareasSyncStateEntity(ZONA, C1, "t-9", 4), dao.estado(ZONA))
    }

    private fun tarea(id: String, zona: Int = ZONA) = TareaZonaEntity(
        ID = id,
        VENTA_ID = 512345,
        CLIENTE_ID = 11486,
        ZONA_CLIENTE_ID = zona,
        FECHA = "2026-10-28",
        HORA = "10:30",
        NOTA = "Pasar con la señora Núñez antes de las 11",
        REEMPLAZA_A = null,
        CREATED_AT = "2026-10-26T14:59:59.000001Z",
        UPDATED_AT = C1,
        CANCELADA_AT = null
    )
}
