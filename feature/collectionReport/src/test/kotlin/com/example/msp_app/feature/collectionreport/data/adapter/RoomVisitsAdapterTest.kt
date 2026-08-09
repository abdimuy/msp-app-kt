package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
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
 * dominio, `NOTA` nula -> vacío, el rango medio-abierto por `Visit.FECHA` y el
 * caso vacío.
 */
class RoomVisitsAdapterTest : RoomTestBase() {

    private val adapter by lazy { RoomVisitsAdapter(db.visitDao()) }

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
        db.visitDao().insertVisit(visit(id = "v1", clienteId = 55012))

        val result = adapter.visitsIn(rangeD)

        assertEquals(1, result.size)
        val v = result.single()
        assertEquals("v1", v.id)
        assertEquals("55012", v.cliente)
        assertEquals("Promesa de pago manana", v.nota)
        assertEquals(Instant.parse("2026-04-15T18:30:00Z"), v.visitedAt)
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
