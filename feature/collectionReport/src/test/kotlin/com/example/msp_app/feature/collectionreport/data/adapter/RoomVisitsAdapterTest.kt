package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [RoomVisitsAdapter] sobre la query real de Room/SQLite. Verifica el mapeo a
 * dominio, la resolución del NOMBRE real vía el join a `cliente` (y su
 * fallback `"Cliente #<id>"`), `NOTA` nula -> vacío, el rango medio-abierto
 * por `Visit.FECHA` y el caso vacío.
 */
class RoomVisitsAdapterTest : RoomTestBase() {

    private val adapter by lazy { RoomVisitsAdapter(db.visitDao(), db.clienteDao()) }

    private fun cliente(id: Int, nombre: String) = ClienteEntity(
        CLIENTE_ID = id,
        NOMBRE = nombre,
        ESTATUS = "A",
        CAUSA_SUSP = null
    )

    private val dayD = LocalDate.of(2026, 4, 15)
    private val startD = AppTime.toWireFormat(AppTime.startOfDay(dayD))
    private val endD = AppTime.toWireFormat(AppTime.startOfNextDay(dayD))
    private val rangeD = DateRange(startD, endD)

    private fun visit(
        id: String,
        fecha: String = "2026-04-15T18:30:00Z",
        nota: String? = "Promesa de pago manana",
        clienteId: Int = 30144
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = clienteId,
        COBRADOR = "Efrain Dominguez Reyes",
        COBRADOR_ID = 7,
        FECHA = fecha,
        FORMA_COBRO_ID = 0,
        LAT = 19.043415,
        LNG = -98.198234,
        NOTA = nota,
        TIPO_VISITA = "VISITA",
        ZONA_CLIENTE_ID = 21,
        IMPTE_DOCTO_CC_ID = 0,
        GUARDADO_EN_MICROSIP = 0
    )

    @Test
    fun `visitsIn mapea los campos a dominio`() = runTest {
        db.clienteDao().insertAll(listOf(cliente(id = 55012, nombre = "Fernanda Reyes Ortiz")))
        db.visitDao().insertVisit(visit(id = "v1", clienteId = 55012))

        val result = adapter.visitsIn(rangeD)

        assertEquals(1, result.size)
        val v = result.single()
        assertEquals("v1", v.id)
        assertEquals("Fernanda Reyes Ortiz", v.cliente)
        assertEquals("Promesa de pago manana", v.nota)
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), v.visitedAt)
        assertEquals("VISITA", v.tipo)
    }

    @Test
    fun `visitsIn mapea el TIPO_VISITA real, sin transformarlo`() = runTest {
        db.visitDao().insertVisit(
            visit(id = "v1", clienteId = 30144).copy(TIPO_VISITA = "No se encontraba")
        )

        val v = adapter.visitsIn(rangeD).single()

        assertEquals("No se encontraba", v.tipo)
    }

    @Test
    fun `visitsIn resuelve el nombre real del cliente con un solo join batch`() = runTest {
        db.clienteDao().insertAll(
            listOf(
                cliente(id = 30144, nombre = "Rosa Elena Martinez Vazquez"),
                cliente(id = 55012, nombre = "Fernanda Reyes Ortiz")
            )
        )
        db.visitDao().insertVisit(visit(id = "v1", clienteId = 30144))
        db.visitDao().insertVisit(visit(id = "v2", clienteId = 55012))

        val byId = adapter.visitsIn(rangeD).associateBy { it.id }

        assertEquals("Rosa Elena Martinez Vazquez", byId.getValue("v1").cliente)
        assertEquals("Fernanda Reyes Ortiz", byId.getValue("v2").cliente)
    }

    @Test
    fun `visitsIn cae a Cliente numero id cuando el cliente no esta en local`() = runTest {
        // Sin insertar ningún cliente: el CLIENTE_ID de la visita no cruza con `cliente`.
        db.visitDao().insertVisit(visit(id = "v1", clienteId = 90210))

        val v = adapter.visitsIn(rangeD).single()

        assertEquals("Cliente #90210", v.cliente)
    }

    @Test
    fun `visitsIn mapea NOTA nula a cadena vacia`() = runTest {
        db.visitDao().insertVisit(visit(id = "v1", nota = null))

        assertEquals("", adapter.visitsIn(rangeD).single().nota)
    }

    @Test
    fun `visitsIn respeta el rango medio-abierto en el borde de medianoche`() = runTest {
        db.visitDao().insertVisit(visit(id = "prev", fecha = "2026-04-16T05:59:59Z"))
        db.visitDao().insertVisit(visit(id = "boundary", fecha = endD))

        assertEquals(listOf("prev"), adapter.visitsIn(rangeD).map { it.id })
    }

    @Test
    fun `visitsIn vacio devuelve lista vacia sin NPE`() = runTest {
        assertEquals(emptyList<String>(), adapter.visitsIn(rangeD).map { it.id })
    }
}
