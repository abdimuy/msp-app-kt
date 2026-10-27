package com.example.msp_app.core.database.dao.visit

import android.database.sqlite.SQLiteException
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitaZonaEntity
import com.example.msp_app.core.database.entities.VisitasSyncStateEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-080 (spec visitas §5.4.8, I6, I8): la agenda bajada de la zona vive en
 * `visita_zona` y su posición en `visitas_sync_state`. La página y el cursor se
 * escriben en UNA transacción; el cambio de zona y el replay por generación
 * borran lo bajado y dejan `CURSOR` y `AFTER_ID` nulos en la misma operación;
 * ninguno de los tres toca `Visit`.
 *
 * "Matar el proceso a la mitad" se simula con un trigger de prueba que aborta
 * la escritura del cursor: es la última sentencia de cada operación, así que
 * todo lo anterior ya se escribió cuando revienta y solo la transacción lo
 * deshace.
 */
class VisitaZonaDaoTest : RoomTestBase() {

    private val dao get() = db.visitaZonaDao()
    private val visitDao get() = db.visitDao()

    // ─── aplicarPagina ──────────────────────────────────────────────────────

    @Test
    fun `aplicarPagina guarda los items y el cursor par de la zona`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1"), item("v-2")), C1, "v-2")

        assertEquals(listOf("v-1", "v-2"), dao.deZona(ZONA).map { it.ID })
        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-2", null), dao.estado(ZONA))
    }

    @Test
    fun `aplicarPagina reemplaza la fila bajada con el mismo id`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1", nota = "vieja")), C1, "v-1")
        dao.aplicarPagina(ZONA, listOf(item("v-1", nota = "nueva", updatedAt = C2)), C2, "v-1")

        val filas = dao.deZona(ZONA)
        assertEquals(1, filas.size)
        assertEquals("nueva", filas.single().NOTA)
        assertEquals(C2, filas.single().UPDATED_AT)
    }

    @Test
    fun `aplicarPagina conserva el EPOCH ya persistido`() = runTest {
        dao.guardarEpoch(ZONA, 4)

        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")

        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", 4), dao.estado(ZONA))
    }

    /** Página vacía: el gestor manda el cursor recibido y el AFTER_ID que ya tenía. */
    @Test
    fun `aplicarPagina sin items escribe solo la posicion que le dan`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")

        dao.aplicarPagina(ZONA, emptyList(), C1, "v-1")

        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", null), dao.estado(ZONA))
        assertEquals(listOf("v-1"), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun `E-APP-080 aplicarPagina con cursor nulo y afterId lanza y no escribe nada`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")

        val error = runCatching {
            dao.aplicarPagina(ZONA, listOf(item("v-2")), null, "v-2")
        }.exceptionOrNull()

        assertTrue("debe rechazar AFTER_ID sin CURSOR: $error", error is IllegalArgumentException)

        assertEquals(listOf("v-1"), dao.deZona(ZONA).map { it.ID })
        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", null), dao.estado(ZONA))
    }

    @Test
    fun `aplicarPagina con cursor nulo y afterId nulo es valida`() = runTest {
        dao.aplicarPagina(ZONA, emptyList(), null, null)

        assertEquals(VisitasSyncStateEntity(ZONA, null, null, null), dao.estado(ZONA))
    }

    /** I6: si muere al escribir el cursor, no queda ni la página ni el cursor. */
    @Test
    fun `E-APP-080 aplicarPagina interrumpida al escribir el cursor no deja ni items ni cursor`() =
        runTest {
            dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")
            abortarEscriturasDelCursor()

            val error = runCatching {
                dao.aplicarPagina(ZONA, listOf(item("v-2"), item("v-3")), C2, "v-3")
            }.exceptionOrNull()

            assertTrue("la muerte simulada debe propagarse: $error", error is SQLiteException)
            assertEquals(listOf("v-1"), dao.deZona(ZONA).map { it.ID })
            assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", null), dao.estado(ZONA))
        }

    /** I8: una fila bajada con el id de una visita propia pendiente no la pisa. */
    @Test
    fun `E-APP-080 aplicarPagina con el id de una Visit pendiente no la toca`() = runTest {
        val propia = visitaPendiente("v-1")
        visitDao.insertVisit(propia)

        dao.aplicarPagina(ZONA, listOf(item("v-1", nota = "lo del servidor")), C1, "v-1")

        assertEquals(propia, visitDao.findVisitById("v-1"))
        assertEquals("lo del servidor", dao.deZona(ZONA).single().NOTA)
    }

    // ─── reiniciarZona (cambio de zona) ─────────────────────────────────────

    @Test
    fun `E-APP-080 reiniciarZona borra la zona vieja y deja cursor y AFTER_ID nulos`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1"), item("v-2")), C1, "v-2")
        dao.guardarEpoch(ZONA, 4)
        dao.aplicarPagina(OTRA_ZONA, listOf(item("w-1", zona = OTRA_ZONA)), C2, "w-1")
        dao.guardarEpoch(OTRA_ZONA, 4)

        dao.reiniciarZona(OTRA_ZONA)

        assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
        assertEquals(listOf("w-1"), dao.deZona(OTRA_ZONA).map { it.ID })
        assertEquals(VisitasSyncStateEntity(OTRA_ZONA, null, null, 4), dao.estado(OTRA_ZONA))
    }

    /**
     * Volver a la zona vieja: sus filas se borraron, así que su cursor no puede
     * seguir diciendo "ya tengo hasta aquí" o esas visitas no vuelven a bajar.
     */
    @Test
    fun `reiniciarZona deja sin posicion tambien a la zona vieja para que al volver baje todo`() =
        runTest {
            dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")
            dao.guardarEpoch(ZONA, 4)

            dao.reiniciarZona(OTRA_ZONA)

            assertEquals(VisitasSyncStateEntity(ZONA, null, null, 4), dao.estado(ZONA))
            assertEquals(VisitasSyncStateEntity(OTRA_ZONA, null, null, null), dao.estado(OTRA_ZONA))
        }

    @Test
    fun `E-APP-080 reiniciarZona interrumpida no borra la zona vieja ni su cursor`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")
        abortarEscriturasDelCursor()

        val error = runCatching { dao.reiniciarZona(OTRA_ZONA) }.exceptionOrNull()

        assertTrue("la muerte simulada debe propagarse: $error", error is SQLiteException)
        assertEquals(listOf("v-1"), dao.deZona(ZONA).map { it.ID })
        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", null), dao.estado(ZONA))
    }

    /** I8, Review Focus 5: el cambio de zona con visitas propias pendientes. */
    @Test
    fun `E-APP-080 reiniciarZona no toca Visit pendiente ni entregada`() = runTest {
        val pendiente = visitaPendiente("v-1")
        val entregada = visitaPendiente("v-9").copy(
            GUARDADO_EN_MICROSIP = 1,
            CONFIRMADA_EN = "2026-10-16T18:00:01Z"
        )
        visitDao.insertVisit(pendiente)
        visitDao.insertVisit(entregada)
        dao.aplicarPagina(ZONA, listOf(item("v-1"), item("v-9")), C1, "v-9")

        dao.reiniciarZona(OTRA_ZONA)

        assertEquals(pendiente, visitDao.findVisitById("v-1"))
        assertEquals(entregada, visitDao.findVisitById("v-9"))
        assertEquals(listOf("v-1"), visitDao.pendientesOrdenadas())
    }

    // ─── reiniciarPorEpoch ──────────────────────────────────────────────────

    @Test
    fun `reiniciarPorEpoch borra todo lo bajado y el cursor sin persistir la generacion`() =
        runTest {
            dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")
            dao.guardarEpoch(ZONA, 4)

            dao.reiniciarPorEpoch(ZONA)

            assertEquals(emptyList<String>(), dao.deZona(ZONA).map { it.ID })
            assertEquals(VisitasSyncStateEntity(ZONA, null, null, 4), dao.estado(ZONA))
        }

    @Test
    fun `reiniciarPorEpoch interrumpida no borra nada`() = runTest {
        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")
        abortarEscriturasDelCursor()

        val error = runCatching { dao.reiniciarPorEpoch(ZONA) }.exceptionOrNull()

        assertTrue("la muerte simulada debe propagarse: $error", error is SQLiteException)
        assertEquals(listOf("v-1"), dao.deZona(ZONA).map { it.ID })
        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", null), dao.estado(ZONA))
    }

    @Test
    fun `E-APP-080 reiniciarPorEpoch no toca Visit`() = runTest {
        val pendiente = visitaPendiente("v-1")
        visitDao.insertVisit(pendiente)
        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")

        dao.reiniciarPorEpoch(ZONA)

        assertEquals(pendiente, visitDao.findVisitById("v-1"))
    }

    // ─── epoch, inventario, borrado, poda ───────────────────────────────────

    @Test
    fun `guardarEpoch crea el estado sin posicion y despues solo cambia la generacion`() = runTest {
        assertNull(dao.estado(ZONA))

        dao.guardarEpoch(ZONA, 3)
        assertEquals(VisitasSyncStateEntity(ZONA, null, null, 3), dao.estado(ZONA))

        dao.aplicarPagina(ZONA, listOf(item("v-1")), C1, "v-1")
        dao.guardarEpoch(ZONA, 5)
        assertEquals(VisitasSyncStateEntity(ZONA, C1, "v-1", 5), dao.estado(ZONA))
    }

    @Test
    fun `idsYUpdatedAt devuelve la zona desde la fecha dada, ordenado por id`() = runTest {
        dao.aplicarPagina(
            ZONA,
            listOf(
                item("v-3", fecha = "2026-10-16T10:00:00Z", updatedAt = C2),
                item("v-1", fecha = "2026-07-16T23:59:59Z"),
                item("v-2", fecha = "2026-07-17T00:00:00Z")
            ),
            C2,
            "v-3"
        )
        dao.aplicarPagina(OTRA_ZONA, listOf(item("w-1", zona = OTRA_ZONA)), C1, "w-1")

        assertEquals(
            listOf(IdYUpdatedAt("v-2", C1), IdYUpdatedAt("v-3", C2)),
            dao.idsYUpdatedAt(ZONA, "2026-07-17")
        )
    }

    /** CLAUDE.md §1: SQLite de Android ≤ 11 admite 999 parámetros; se trocea. */
    @Test
    fun `borrar acepta mas ids que el tope de parametros de SQLite`() = runTest {
        val ids = (1..2000).map { "v-%04d".format(it) }
        dao.aplicarPagina(ZONA, ids.map { item(it) } + item("queda"), C1, "queda")

        dao.borrar(ids)

        assertEquals(listOf("queda"), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun `podarFueraDeVentana borra solo lo anterior a la ventana`() = runTest {
        dao.aplicarPagina(
            ZONA,
            listOf(
                item("vieja", fecha = "2026-07-16T23:59:59Z"),
                item("borde", fecha = "2026-07-17T00:00:00Z"),
                item("nueva", fecha = "2026-10-16T10:00:00Z")
            ),
            C1,
            "vieja"
        )

        dao.podarFueraDeVentana("2026-07-17")

        assertEquals(listOf("borde", "nueva"), dao.deZona(ZONA).map { it.ID })
    }

    @Test
    fun `una fila bajada con todo lo opcional en nulo se guarda y se lee`() = runTest {
        val minima = VisitaZonaEntity(
            ID = "v-min",
            CLIENTE_ID = 11486,
            COBRADOR_ID = null,
            COBRADOR = null,
            FECHA = "2026-10-16T10:00:00Z",
            TIPO_VISITA = "SIN_PAGO",
            ZONA_CLIENTE_ID = ZONA,
            IMPTE_DOCTO_CC_ID = null,
            NOTA = null,
            PROMESA_VENTA_ID = null,
            PROMESA_FECHA = null,
            PROMESA_MONTO_CENTAVOS = null,
            CITA_FECHA = null,
            CITA_HORA = null,
            UPDATED_AT = C1
        )

        dao.aplicarPagina(ZONA, listOf(minima), C1, "v-min")

        assertEquals(listOf(minima), dao.deZona(ZONA))
    }

    // ─── apoyo ──────────────────────────────────────────────────────────────

    /** La última sentencia de cada operación escribe `visitas_sync_state`. */
    private fun abortarEscriturasDelCursor() {
        val w = db.openHelper.writableDatabase
        w.execSQL(
            "CREATE TRIGGER muerte_simulada_upd BEFORE UPDATE ON visitas_sync_state " +
                "BEGIN SELECT RAISE(ABORT, 'muerte simulada'); END"
        )
        w.execSQL(
            "CREATE TRIGGER muerte_simulada_ins BEFORE INSERT ON visitas_sync_state " +
                "BEGIN SELECT RAISE(ABORT, 'muerte simulada'); END"
        )
    }

    private fun item(
        id: String,
        zona: Int = ZONA,
        fecha: String = "2026-10-15T18:00:00Z",
        nota: String? = "No estaba, vuelvo el jueves",
        updatedAt: String = C1
    ) = VisitaZonaEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 2991009,
        COBRADOR = "López García, Minerva",
        FECHA = fecha,
        TIPO_VISITA = "PROMESA",
        ZONA_CLIENTE_ID = zona,
        IMPTE_DOCTO_CC_ID = 512345,
        NOTA = nota,
        PROMESA_VENTA_ID = 512345,
        PROMESA_FECHA = "2026-10-17",
        PROMESA_MONTO_CENTAVOS = 15000L,
        CITA_FECHA = "2026-10-17",
        CITA_HORA = "10:30",
        UPDATED_AT = updatedAt
    )

    private fun visitaPendiente(id: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR = "Mendoza Torres, Ana",
        COBRADOR_ID = 2991009,
        FECHA = "2026-10-15T18:00:00Z",
        FORMA_COBRO_ID = 157,
        LAT = 19.4326077,
        LNG = -99.133208,
        NOTA = "Lo capturado en el teléfono",
        TIPO_VISITA = "PROMESA",
        ZONA_CLIENTE_ID = ZONA,
        IMPTE_DOCTO_CC_ID = 512345,
        GUARDADO_EN_MICROSIP = 0,
        PROMESA_VENTA_ID = 512345,
        PROMESA_FECHA = "2026-10-20",
        PROMESA_MONTO_CENTAVOS = 20000L,
        CITA_FECHA = "2026-10-20",
        CITA_HORA = "17:00"
    )

    private companion object {
        const val ZONA = 21552
        const val OTRA_ZONA = 21553
        const val C1 = "2026-10-16T18:00:00.123456Z"
        const val C2 = "2026-10-16T19:00:00Z"
    }
}
