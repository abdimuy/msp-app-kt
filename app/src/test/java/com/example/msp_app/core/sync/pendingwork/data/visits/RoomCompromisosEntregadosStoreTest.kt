package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La lectura y la devolución a pendiente de la conciliación inversa del
 * compromiso, sobre Room de verdad (P2 Tarea 18, E-APP-132).
 */
class RoomCompromisosEntregadosStoreTest : RoomTestBase() {

    private fun visita(
        id: String,
        guardado: Int = 1,
        promesa: String? = "2026-10-20",
        cita: String? = null,
        confirmada: String? = "2026-10-16T12:00:00Z",
        resguardada: String? = null,
        compromisoConfirmado: String? = "2026-10-16T12:00:00Z",
        compromisoResguardado: String? = null,
        fecha: String = "2026-10-15T17:00:00Z"
    ) = VisitEntity(
        ID = id,
        CLIENTE_ID = 10,
        COBRADOR_ID = 7,
        FECHA = fecha,
        FORMA_COBRO_ID = 0,
        LAT = 0.0,
        LNG = 0.0,
        TIPO_VISITA = "Pidió que regrese otro día",
        ZONA_CLIENTE_ID = 21,
        IMPTE_DOCTO_CC_ID = 100,
        GUARDADO_EN_MICROSIP = guardado,
        PROMESA_VENTA_ID = promesa?.let { 100 },
        PROMESA_FECHA = promesa,
        PROMESA_MONTO_CENTAVOS = promesa?.let { 15000L },
        CITA_FECHA = cita,
        CITA_HORA = cita?.let { "10:00" },
        CONFIRMADA_EN = confirmada,
        RESGUARDADA_EN = resguardada,
        COMPROMISO_CONFIRMADO_EN = compromisoConfirmado,
        COMPROMISO_RESGUARDADO_EN = compromisoResguardado
    )

    private fun id(n: Int) = "6f1c2a3b-0000-4000-8000-%012d".format(n)

    private fun store() = RoomCompromisosEntregadosStore(db.visitDao())

    @Test
    fun `solo trae las soltadas con compromiso entregado y no resguardado`() = runBlocking {
        val dao = db.visitDao()
        dao.insertVisit(visita(id(1))) // entra: promesa
        dao.insertVisit(visita(id(2), promesa = null, cita = "2026-10-17")) // entra: cita
        dao.insertVisit(visita(id(3), guardado = 0)) // pendiente: no
        dao.insertVisit(visita(id(4), promesa = null)) // sin compromiso: no
        dao.insertVisit(visita(id(5), compromisoConfirmado = null)) // espera relleno: no
        dao.insertVisit(
            visita(id(6), compromisoResguardado = "2026-10-16T12:00:00Z")
        ) // en la bandeja: no
        dao.insertVisit(
            visita(id(7), resguardada = "2026-10-16T12:00:00Z")
        ) // visita resguardada: no
        dao.insertVisit(visita(id(8), confirmada = null)) // legada sin confirmar: sí
        dao.insertVisit(
            visita(id(9), confirmada = "2026-08-01T12:00:00Z")
        ) // fuera de la ventana: no

        val r = store().entregadasConCompromisoDesde("2026-09-27T00:00:00Z")

        assertEquals(listOf(id(1), id(2), id(8)), r.map { it.visitaId })
    }

    @Test
    fun `lleva el compromiso como esta en Room`() = runBlocking {
        db.visitDao().insertVisit(visita(id(1), cita = "2026-10-17"))

        val r = store().entregadasConCompromisoDesde("2026-09-27T00:00:00Z").single()

        assertEquals(
            CompromisoLocal(100, "2026-10-20", 15000L, "2026-10-17", "10:00"),
            r.compromiso
        )
    }

    @Test
    fun `devolver a pendiente deja la visita pendiente y sin confirmada`() = runBlocking {
        val dao = db.visitDao()
        dao.insertVisit(visita(id(1)))
        dao.insertVisit(visita(id(2)))

        val n = store().devolverAPendiente(listOf(id(1)))

        assertEquals(1, n)
        assertEquals(0, dao.findVisitById(id(1))!!.GUARDADO_EN_MICROSIP)
        assertEquals(null, dao.findVisitById(id(1))!!.CONFIRMADA_EN)
        // Control: la otra no se tocó.
        assertEquals(1, dao.findVisitById(id(2))!!.GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `trocea los lotes por debajo del tope de SQLite`() = runBlocking {
        val tamanos = mutableListOf<Int>()
        val store = RoomCompromisosEntregadosStore(db.visitDao()) { lote ->
            tamanos += lote.size
            lote.size
        }

        val total = store.devolverAPendiente((1..2300).map { id(it) })

        assertEquals(2300, total)
        assertEquals(true, tamanos.size > 1)
        assertEquals(true, tamanos.all { it <= RoomPendingVisitsStore.SQLITE_MAX_IN_PARAMS })
    }
}
