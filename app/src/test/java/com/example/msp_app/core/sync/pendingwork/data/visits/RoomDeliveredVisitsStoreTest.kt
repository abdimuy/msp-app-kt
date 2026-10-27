package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.workers.pendingVisit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El lado local de la reconciliación inversa (plan visitas P1, Tarea 13)
 * contra Room real: qué se lee como soltado y qué cambia cada decisión.
 */
class RoomDeliveredVisitsStoreTest : RoomTestBase() {

    private fun store() = RoomDeliveredVisitsStore(db.visitDao(), db.visitImageDao())

    @Test
    fun `lee la legada sin confirmar y la confirmada reciente, no la vieja ni la resguardada ni la pendiente`() =
        runTest {
            val dao = db.visitDao()
            dao.insertVisit(pendingVisit(id = "legada", guardado = 1))
            dao.insertVisit(pendingVisit(id = "reciente"))
            dao.confirmar(listOf("reciente"), "2026-10-10T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "vieja"))
            dao.confirmar(listOf("vieja"), "2026-08-01T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "resguardada"))
            dao.resguardar("resguardada", "2026-10-10T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "pendiente"))

            assertEquals(
                setOf("legada", "reciente"),
                store().entregadasDesde("2026-09-17T15:00:00Z").toSet()
            )
        }

    @Test
    fun `confirmar estampa CONFIRMADA_EN`() = runTest {
        db.visitDao().insertVisit(pendingVisit(id = "legada", guardado = 1))

        assertEquals(1, store().confirmar(listOf("legada"), "2026-10-17T15:00:00Z"))

        val v = db.visitDao().findVisitById("legada")!!
        assertEquals("2026-10-17T15:00:00Z", v.CONFIRMADA_EN)
        assertEquals(1, v.GUARDADO_EN_MICROSIP)
    }

    @Test
    fun `devolver a pendiente deja la visita en la cola y sin marcas`() = runTest {
        db.visitDao().insertVisit(pendingVisit(id = "c"))
        db.visitDao().confirmar(listOf("c"), "2026-10-10T00:00:00Z")

        assertEquals(1, store().devolverAPendiente(listOf("c")))

        val v = db.visitDao().findVisitById("c")!!
        assertEquals(0, v.GUARDADO_EN_MICROSIP)
        assertNull(v.CONFIRMADA_EN)
        assertEquals(listOf("c"), db.visitDao().pendientesOrdenadas())
    }

    @Test
    fun `un lote mayor al tope de parametros de SQLite se trocea`() = runTest {
        val muchas = List(1500) { "v-$it" }
        muchas.forEach { db.visitDao().insertVisit(pendingVisit(id = it, guardado = 1)) }

        assertEquals(1500, store().confirmar(muchas, "2026-10-17T15:00:00Z"))
        assertEquals(1500, store().devolverAPendiente(muchas))
        assertNotNull(db.visitDao().findVisitById("v-1499"))
        assertEquals(1500, db.visitDao().pendientesOrdenadas().size)
    }

    /** R1 de la revisión: el tope se prueba contando lotes, no con SQLite de escritorio. */
    @Test
    fun `cada lote de escritura lleva a lo mas 900 ids`() = runTest {
        val lotesConfirmar = mutableListOf<Int>()
        val lotesDevolver = mutableListOf<Int>()
        val contado = RoomDeliveredVisitsStore(
            visitDao = db.visitDao(),
            imagenes = db.visitImageDao(),
            confirmarLote = { ids, _ ->
                lotesConfirmar += ids.size
                ids.size
            },
            devolverLote = { ids ->
                lotesDevolver += ids.size
                ids.size
            }
        )
        val muchas = List(1901) { "v-$it" }

        assertEquals(1901, contado.confirmar(muchas, "2026-10-17T15:00:00Z"))
        assertEquals(1901, contado.devolverAPendiente(muchas))

        assertEquals(listOf(900, 900, 101), lotesConfirmar)
        assertEquals(listOf(900, 900, 101), lotesDevolver)
    }

    /**
     * E-APP-079 y E-VIS-031: el barrido solo ve fotos sin `SUBIDA_EN` de
     * visitas ENTREGADAS —confirmadas y legadas sin marcas— que de verdad
     * podrían viajar (archivo presente y tipo permitido, el mismo criterio que
     * `partesDeComprobantesDeVisita`). Ni pendientes ni resguardadas.
     */
    @Test
    fun `E-VIS-031 E-APP-079 fotos sin enviar de entregadas con archivo y tipo permitido`() =
        runTest {
            val dao = db.visitDao()
            dao.insertVisit(pendingVisit(id = "confirmada"))
            dao.confirmar(listOf("confirmada"), "2026-10-10T00:00:00Z")
            dao.insertVisit(pendingVisit(id = "pendiente"))
            dao.insertVisit(pendingVisit(id = "legada", guardado = 1))
            dao.insertVisit(pendingVisit(id = "resguardada"))
            dao.resguardar("resguardada", "2026-10-10T00:00:00Z")
            fun foto(
                id: String,
                visita: String,
                mime: String = "image/jpeg",
                subida: String? = null
            ) = com.example.msp_app.core.database.entities.VisitImageEntity(
                ID = id,
                VISITA_ID = visita,
                URI = "/fotos/$id.jpg",
                MIME = mime,
                ORDEN = 0,
                CREADA_EN = "2026-10-11T00:00:00Z",
                SUBIDA_EN = subida
            )
            db.visitImageDao().insertAll(
                listOf(
                    foto("viaja", "confirmada"),
                    foto("sin-archivo", "confirmada"),
                    foto("tipo-raro", "confirmada", mime = "image/bmp"),
                    foto("ya-subida", "confirmada", subida = "2026-10-10T00:00:00Z"),
                    foto("de-pendiente", "pendiente"),
                    foto("de-legada", "legada"),
                    foto("de-resguardada", "resguardada")
                )
            )
            val existen =
                setOf(
                    "/fotos/viaja.jpg",
                    "/fotos/tipo-raro.jpg",
                    "/fotos/ya-subida.jpg",
                    "/fotos/de-pendiente.jpg",
                    "/fotos/de-legada.jpg",
                    "/fotos/de-resguardada.jpg"
                )
            val store = RoomDeliveredVisitsStore(
                visitDao = dao,
                imagenes = db.visitImageDao(),
                confirmarLote = { ids, ahora -> dao.confirmar(ids, ahora) },
                devolverLote = { ids -> dao.devolverAPendiente(ids) },
                archivoExiste = { it in existen }
            )

            assertEquals(
                listOf(
                    com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar(
                        "confirmada",
                        "2026-10-11T00:00:00Z"
                    ),
                    com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar(
                        "legada",
                        "2026-10-11T00:00:00Z"
                    )
                ),
                store.fotosSinEnviarDeEntregadas()
            )
        }
}
