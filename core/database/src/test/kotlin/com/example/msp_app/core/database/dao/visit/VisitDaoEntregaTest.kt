package com.example.msp_app.core.database.dao.visit

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Spec visitas §4.2-1/-7: los únicos escritores de `CONFIRMADA_EN` y
 * `RESGUARDADA_EN`, y las dos lecturas que el envío y la reconciliación
 * inversa usan para decidir.
 */
class VisitDaoEntregaTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    @Test
    fun `confirmar estampa CONFIRMADA_EN y limpia RESGUARDADA_EN`() = runTest {
        dao.insertVisit(visita("a"))
        dao.resguardar("a", T1)

        assertEquals(1, dao.confirmar(listOf("a"), T2))

        val v = dao.findVisitById("a")!!
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
        assertEquals(T2, v.CONFIRMADA_EN)
        assertNull(v.RESGUARDADA_EN)
    }

    @Test
    fun `confirmar retiene la visita con foto sin subir y suelta la de foto subida`() = runTest {
        dao.insertVisit(visita("a"))
        dao.insertVisit(visita("b"))
        db.visitImageDao().insertAll(
            listOf(
                imagen(id = "img-a", visitaId = "a", subidaEn = null),
                imagen(id = "img-b", visitaId = "b", subidaEn = T1)
            )
        )

        assertEquals(1, dao.confirmar(listOf("a", "b"), T2))

        assertNull(dao.findVisitById("a")!!.CONFIRMADA_EN)
        assertEquals(0, dao.findVisitById("a")!!.GUARDADO_EN_MICROSIP)
        assertEquals(T2, dao.findVisitById("b")!!.CONFIRMADA_EN)
    }

    @Test
    fun `confirmar toca solo los ids dados y una lista vacia no toca nada`() = runTest {
        listOf("a", "b", "c").forEach { dao.insertVisit(visita(it)) }

        assertEquals(0, dao.confirmar(emptyList(), T1))
        assertEquals(2, dao.confirmar(listOf("a", "c", "no-existe"), T1))

        assertEquals(listOf("b"), dao.pendientesOrdenadas())
        assertNull(dao.findVisitById("b")!!.CONFIRMADA_EN)
    }

    @Test
    fun `confirmarTrasSubida no aplica la retencion por foto`() = runTest {
        dao.insertVisit(visita("a"))
        db.visitImageDao().insertAll(listOf(imagen(id = "img-a", visitaId = "a", subidaEn = null)))

        assertEquals(1, dao.confirmarTrasSubida("a", T2))

        val v = dao.findVisitById("a")!!
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
        assertEquals(T2, v.CONFIRMADA_EN)
        assertNull(v.RESGUARDADA_EN)
    }

    @Test
    fun `resguardar marca la pendiente y no pisa una confirmada`() = runTest {
        dao.insertVisit(visita("a"))
        dao.insertVisit(visita("b"))
        dao.confirmar(listOf("a"), T1)

        assertEquals(0, dao.resguardar("a", T2))
        assertEquals(1, dao.resguardar("b", T2))

        val a = dao.findVisitById("a")!!
        assertEquals(T1, a.CONFIRMADA_EN)
        assertNull(a.RESGUARDADA_EN)
        val b = dao.findVisitById("b")!!
        assertEquals(1, b.GUARDADO_EN_MICROSIP)
        assertNull(b.CONFIRMADA_EN)
        assertEquals(T2, b.RESGUARDADA_EN)
    }

    @Test
    fun `devolverAPendiente limpia las dos marcas solo de los ids dados`() = runTest {
        listOf("a", "b", "c").forEach { dao.insertVisit(visita(it)) }
        dao.resguardar("a", T1)
        dao.confirmar(listOf("b", "c"), T1)

        assertEquals(2, dao.devolverAPendiente(listOf("a", "b")))

        listOf("a", "b").forEach { id ->
            val v = dao.findVisitById(id)!!
            assertEquals(0, v.GUARDADO_EN_MICROSIP)
            assertNull(v.CONFIRMADA_EN)
            assertNull(v.RESGUARDADA_EN)
        }
        assertEquals(T1, dao.findVisitById("c")!!.CONFIRMADA_EN)
    }

    @Test
    fun `pendientesOrdenadas devuelve todas por FECHA e ID sin tope`() = runTest {
        (1..60).forEach { i ->
            dao.insertVisit(
                visita(
                    id = "v%02d".format(61 - i),
                    fecha = "2026-10-%02dT10:00:00Z".format(1 + i % 28)
                )
            )
        }
        dao.insertVisit(visita(id = "entregada", guardado = 1))

        val ids = dao.pendientesOrdenadas()

        assertEquals(60, ids.size)
        val claves = ids.map { dao.findVisitById(it)!!.FECHA to it }
        assertEquals(claves.sortedWith(compareBy({ it.first }, { it.second })), claves)
    }

    /**
     * Review Focus 2: la legada (`GUARDADO_EN_MICROSIP = 1` y `CONFIRMADA_EN`
     * nulo tras la 34→35) entra a la reconciliación inversa sin importar
     * `desde`; una confirmada vieja o una resguardada no.
     */
    @Test
    fun `entregadasDesde incluye la legada sin confirmar y excluye resguardadas y confirmadas viejas`() =
        runTest {
            dao.insertVisit(visita(id = "legada", fecha = "2026-09-01T10:00:00Z", guardado = 1))
            dao.insertVisit(visita(id = "reciente", fecha = "2026-10-15T10:00:00Z"))
            dao.insertVisit(visita(id = "vieja", fecha = "2026-10-01T10:00:00Z"))
            dao.insertVisit(visita(id = "resguardada", fecha = "2026-10-14T10:00:00Z"))
            dao.insertVisit(visita(id = "pendiente", fecha = "2026-10-14T10:00:00Z"))
            dao.confirmar(listOf("reciente"), "2026-10-16T18:00:00Z")
            dao.confirmar(listOf("vieja"), "2026-10-02T18:00:00Z")
            dao.resguardar("resguardada", "2026-10-16T18:00:00Z")

            assertEquals(listOf("legada", "reciente"), dao.entregadasDesde("2026-10-10T00:00:00Z"))
        }

    @Test
    fun `las lecturas de la entidad devuelven las dos marcas`() = runTest {
        dao.insertVisit(visita("a"))
        dao.insertVisit(visita("b"))
        dao.confirmar(listOf("a"), T1)
        dao.resguardar("b", T2)

        assertEquals(T1, dao.getVisitById("a").CONFIRMADA_EN)
        assertEquals(T2, dao.getVisitById("b").RESGUARDADA_EN)
        val porCliente = dao.getVisitsByClienteId(CLIENTE_ID).associateBy { it.ID }
        assertEquals(T1, porCliente.getValue("a").CONFIRMADA_EN)
        assertEquals(T2, porCliente.getValue("b").RESGUARDADA_EN)
        val porFecha = dao.getVisitsByDate(
            "2026-10-16T00:00:00Z",
            "2026-10-17T00:00:00Z"
        ).associateBy {
            it.ID
        }
        assertEquals(T1, porFecha.getValue("a").CONFIRMADA_EN)
        assertEquals(T2, porFecha.getValue("b").RESGUARDADA_EN)
    }

    /**
     * `getPendingVisits` también lee las dos columnas. Una pendiente legítima
     * las trae en NULL (tras `devolverAPendiente`), así que para probar que el
     * SELECT las lee se inserta además una fila pendiente con las dos marcas
     * puestas a mano: solo esta prueba la escribe así.
     */
    @Test
    fun `getPendingVisits devuelve las dos marcas`() = runTest {
        dao.insertVisit(visita("devuelta"))
        dao.confirmar(listOf("devuelta"), T1)
        dao.devolverAPendiente(listOf("devuelta"))
        dao.insertVisit(visita("con-marcas").copy(CONFIRMADA_EN = T1, RESGUARDADA_EN = T2))

        val pendientes = dao.getPendingVisits().associateBy { it.ID }

        assertEquals(setOf("devuelta", "con-marcas"), pendientes.keys)
        assertNull(pendientes.getValue("devuelta").CONFIRMADA_EN)
        assertNull(pendientes.getValue("devuelta").RESGUARDADA_EN)
        assertEquals(T1, pendientes.getValue("con-marcas").CONFIRMADA_EN)
        assertEquals(T2, pendientes.getValue("con-marcas").RESGUARDADA_EN)
    }

    private fun visita(id: String, fecha: String = "2026-10-16T10:00:00Z", guardado: Int = 0) =
        VisitEntity(
            ID = id,
            CLIENTE_ID = CLIENTE_ID,
            COBRADOR = "Domínguez Reyes, Efraín",
            COBRADOR_ID = 7,
            FECHA = fecha,
            FORMA_COBRO_ID = 157,
            LAT = 19.043415,
            LNG = -98.198234,
            NOTA = "No estaba, vuelvo el jueves",
            TIPO_VISITA = "SIN_PAGO",
            ZONA_CLIENTE_ID = 21,
            IMPTE_DOCTO_CC_ID = 512345,
            GUARDADO_EN_MICROSIP = guardado
        )

    private fun imagen(id: String, visitaId: String, subidaEn: String?) = VisitImageEntity(
        ID = id,
        VISITA_ID = visitaId,
        URI = "/data/user/0/com.example.msp_app/files/visitas/$id.jpg",
        MIME = "image/jpeg",
        DESCRIPCION = "Fachada",
        ORDEN = 0,
        CREADA_EN = "2026-10-16T10:00:01Z",
        SUBIDA_EN = subidaEn
    )

    private companion object {
        const val CLIENTE_ID = 30144
        const val T1 = "2026-10-16T17:00:00Z"
        const val T2 = "2026-10-16T18:00:00Z"
    }
}
