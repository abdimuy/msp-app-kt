package com.example.msp_app.data.local.dao.visit

import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the data-loss bug closed by [VisitDao.deleteUploadedVisits].
 *
 * `SalesViewModel.syncSales()` used to call [VisitDao.deleteAllVisits] as part
 * of the periodic sync sweep, which wiped every locally-stored visita —
 * including ones still pending upload (`GUARDADO_EN_MICROSIP = 0`) that had
 * not yet been confirmed by the server. A cobrador who registered a visita
 * offline and had not yet gotten connectivity to upload it would silently
 * lose that visita on the next sync sweep.
 *
 * This test runs against a real, in-memory Room [com.example.msp_app.core.database.AppDatabase]
 * and the REAL [VisitDao] (no fakes) — it proves the fix directly at the SQL
 * level: [VisitDao.deleteUploadedVisits] deletes only confirmed rows and
 * leaves pending rows untouched.
 */
class VisitDaoDeleteUploadedRegressionTest : RoomTestBase() {

    private val dao get() = db.visitDao()

    /** El corte de retención de los tests: todo compromiso anterior a este día se poda. */
    private val corte = "2026-06-15"

    /**
     * El corte de comprobantes para los tests que NO hablan de fotos: tan
     * antiguo que ninguna fila pendiente lo alcanzaría, así que la tercera
     * condición de la poda no participa y estas pruebas siguen midiendo lo que
     * medían.
     */
    private val sinComprobantes = "1970-01-01T00:00:00Z"

    /** El corte de comprobantes de los tests que SÍ hablan de fotos. */
    private val corteDeComprobantes = "2026-06-08T00:00:00Z"

    /** Un comprobante creado DESPUÉS del corte: todavía retiene. */
    private val reciente = "2026-06-12T09:00:00Z"

    /** Un comprobante creado ANTES del corte: ya se dio por abandonado. */
    private val antiguo = "2026-05-30T09:00:00Z"

    private fun visit(id: String, guardado: Int, promesa: String? = null, cita: String? = null) =
        VisitEntity(
            ID = id,
            CLIENTE_ID = 11486,
            COBRADOR = "Ramirez Ortiz, Fernando",
            COBRADOR_ID = 200,
            FECHA = "2026-06-01T09:30:00Z",
            FORMA_COBRO_ID = 0,
            LAT = 0.0,
            LNG = 0.0,
            NOTA = "Visita de prueba",
            TIPO_VISITA = "SIN_PAGO",
            ZONA_CLIENTE_ID = 21552,
            IMPTE_DOCTO_CC_ID = 5000,
            GUARDADO_EN_MICROSIP = guardado,
            PROMESA_FECHA = promesa,
            CITA_FECHA = cita
        )

    @Test
    fun `deleteUploadedVisits removes only confirmed rows and preserves pending ones`() = runTest {
        // Mixed cohort: two visitas already confirmed by the server, two that
        // are still awaiting upload (the exact scenario the old
        // deleteAllVisits() call in SalesViewModel.syncSales() used to destroy).
        dao.insertVisit(visit(id = "visita-uploaded-1", guardado = 1))
        dao.insertVisit(visit(id = "visita-uploaded-2", guardado = 1))
        dao.insertVisit(visit(id = "visita-pending-1", guardado = 0))
        dao.insertVisit(visit(id = "visita-pending-2", guardado = 0))

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = sinComprobantes)

        // The two confirmed visitas are gone.
        assertNoLongerExists(dao, "visita-uploaded-1")
        assertNoLongerExists(dao, "visita-uploaded-2")

        // The two pending visitas MUST survive — this is the data-loss fix.
        val pending1 = dao.getVisitById("visita-pending-1")
        val pending2 = dao.getVisitById("visita-pending-2")
        assertNotNull("pending visita must survive deleteUploadedVisits", pending1)
        assertNotNull("pending visita must survive deleteUploadedVisits", pending2)
        assertEquals(0, pending1.GUARDADO_EN_MICROSIP)
        assertEquals(0, pending2.GUARDADO_EN_MICROSIP)

        assertEquals(2, dao.getPendingVisits().size)
    }

    @Test
    fun `contrast - the old deleteAllVisits would have destroyed the pending rows too`() = runTest {
        // Documents the bug this fix closes: deleteAllVisits() (still exposed
        // for other legitimate uses) has no WHERE clause and wipes pending
        // visitas along with confirmed ones. This is why syncSales() was
        // switched to call deleteUploadedVisits() instead.
        dao.insertVisit(visit(id = "visita-uploaded-1", guardado = 1))
        dao.insertVisit(visit(id = "visita-pending-1", guardado = 0))

        dao.deleteAllVisits()

        assertNoLongerExists(dao, "visita-uploaded-1")
        assertNoLongerExists(dao, "visita-pending-1")
        assertTrue(
            "deleteAllVisits leaves nothing behind, pending or not",
            dao.getPendingVisits().isEmpty()
        )
    }

    /**
     * **Ruling V.** Una visita YA SUBIDA que carga una promesa vigente NO se
     * poda: el servidor no tiene columnas para la promesa, así que borrar la
     * fila borraría la promesa para siempre.
     */
    @Test
    fun `una promesa vigente sobrevive a la poda aunque la visita ya este subida`() = runTest {
        dao.insertVisit(visit(id = "visita-con-promesa", guardado = 1, promesa = "2026-09-04"))
        dao.insertVisit(visit(id = "visita-con-cita", guardado = 1, cita = "2026-07-01"))
        dao.insertVisit(visit(id = "visita-sin-compromiso", guardado = 1))

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = sinComprobantes)

        assertNotNull(
            "la promesa no puede morir en la poda",
            dao.getVisitById("visita-con-promesa")
        )
        assertNotNull("la cita no puede morir en la poda", dao.getVisitById("visita-con-cita"))
        // La visita sin compromiso se poda exactamente como antes: el
        // comportamiento viejo no cambió para las filas viejas.
        assertNoLongerExists(dao, "visita-sin-compromiso")
    }

    /**
     * La ventana de retención sí cierra: un compromiso anterior al corte se
     * poda. Sin esta prueba, "nunca borrar" pasaría por "conservar lo vigente".
     */
    @Test
    fun `un compromiso anterior al corte si se poda`() = runTest {
        dao.insertVisit(visit(id = "promesa-vieja", guardado = 1, promesa = "2026-06-14"))
        dao.insertVisit(visit(id = "promesa-del-corte", guardado = 1, promesa = corte))

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = sinComprobantes)

        assertNoLongerExists(dao, "promesa-vieja")
        // El borde exacto: el día del corte SOBREVIVE (`<`, no `<=`).
        assertNotNull("el dia del corte sobrevive", dao.getVisitById("promesa-del-corte"))
    }

    /**
     * La promesa manda sobre la cita cuando la visita trae las dos: se conserva
     * mientras el compromiso MÁS LEJANO siga vigente, no el primero que aparezca.
     */
    @Test
    fun `con promesa vieja y cita futura gana la cita`() = runTest {
        dao.insertVisit(
            visit(
                id = "promesa-vieja-cita-futura",
                guardado = 1,
                promesa = "2026-01-02",
                cita = "2026-09-10"
            )
        )

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = sinComprobantes)

        assertNotNull(
            "el compromiso mas lejano decide, no el primero",
            dao.getVisitById("promesa-vieja-cita-futura")
        )
    }

    // ─── la tercera condición: comprobantes pendientes (Task 23) ─────────────

    /**
     * **Una visita subida con una foto que todavía no sube NO se poda.**
     *
     * El KDoc de `VisitImageEntity` le encarga esto a la Task 23: bajo la
     * convivencia JSON (Ruling E) `by-ids` puede confirmar la visita mientras
     * una foto sigue pendiente, y podar entonces borraría la única pista de que
     * esa evidencia quedó sin entregar.
     */
    @Test
    fun `una visita con comprobante pendiente no se poda`() = runTest {
        dao.insertVisit(visit(id = "visita-con-foto", guardado = 1))
        dao.insertVisit(visit(id = "visita-sin-foto", guardado = 1))
        sembrarImagen(id = "IMG-1", visitaId = "visita-con-foto", creadaEn = reciente)

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = corteDeComprobantes)

        assertNotNull(
            "la foto pendiente retiene su visita",
            dao.getVisitById("visita-con-foto")
        )
        // Control positivo: la MISMA poda, en la MISMA corrida, sí borra la
        // visita sin fotos. Sin esto, un `deleteUploadedVisits` que no borrara
        // nada pasaría igual.
        assertNoLongerExists(dao, "visita-sin-foto")
    }

    /**
     * Un comprobante **ya subido** no retiene nada: `SUBIDA_EN` es lo que
     * distingue "falta entregarla" de "ya está en el servidor".
     */
    @Test
    fun `un comprobante ya subido no retiene la visita`() = runTest {
        dao.insertVisit(visit(id = "visita-con-foto-subida", guardado = 1))
        sembrarImagen(
            id = "IMG-1",
            visitaId = "visita-con-foto-subida",
            creadaEn = reciente,
            subidaEn = "2026-06-14T12:00:00Z"
        )

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = corteDeComprobantes)

        assertNoLongerExists(dao, "visita-con-foto-subida")
    }

    /**
     * **La retención se vence sola.** Un comprobante pendiente más viejo que el
     * corte ya se dio por abandonado y deja de bloquear.
     *
     * Sin este tope, una foto que nunca va a poder subirse —su visita ya quedó
     * marcada, así que nadie la reintenta— clavaría su visita en la tabla para
     * siempre: un defecto peor que el que la condición cierra.
     */
    @Test
    fun `un comprobante pendiente viejo deja de retener`() = runTest {
        dao.insertVisit(visit(id = "visita-con-foto-vieja", guardado = 1))
        sembrarImagen(id = "IMG-1", visitaId = "visita-con-foto-vieja", creadaEn = antiguo)

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = corteDeComprobantes)

        assertNoLongerExists(dao, "visita-con-foto-vieja")
    }

    /** El borde exacto: el instante del corte RETIENE (`>=`, no `>`). */
    @Test
    fun `el comprobante del instante del corte todavia retiene`() = runTest {
        dao.insertVisit(visit(id = "visita-borde", guardado = 1))
        sembrarImagen(id = "IMG-1", visitaId = "visita-borde", creadaEn = corteDeComprobantes)

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = corteDeComprobantes)

        assertNotNull("el instante del corte retiene", dao.getVisitById("visita-borde"))
    }

    /**
     * Un comprobante pendiente **de OTRA visita** no retiene a la de al lado.
     * Con una sola visita en la tabla, un `NOT IN` mal escrito —sin correlación
     * por `VISITA_ID`— pasaría igual.
     */
    @Test
    fun `el comprobante de otra visita no retiene a la de al lado`() = runTest {
        dao.insertVisit(visit(id = "visita-con-foto", guardado = 1))
        dao.insertVisit(visit(id = "visita-vecina", guardado = 1))
        sembrarImagen(id = "IMG-1", visitaId = "visita-con-foto", creadaEn = reciente)

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = corteDeComprobantes)

        assertNotNull(dao.getVisitById("visita-con-foto"))
        assertNoLongerExists(dao, "visita-vecina")
    }

    /** Un comprobante pendiente NO salva a una visita que sigue sin subir… */
    @Test
    fun `la primera condicion sigue mandando sobre las pendientes`() = runTest {
        dao.insertVisit(visit(id = "visita-pendiente", guardado = 0))
        sembrarImagen(id = "IMG-1", visitaId = "visita-pendiente", creadaEn = reciente)

        dao.deleteUploadedVisits(conservarDesde = corte, comprobantesDesde = corteDeComprobantes)

        assertNotNull(
            "una visita sin subir nunca se poda, con foto o sin ella",
            dao.getVisitById("visita-pendiente")
        )
    }

    private suspend fun sembrarImagen(
        id: String,
        visitaId: String,
        creadaEn: String,
        subidaEn: String? = null
    ) = db.visitImageDao().insertAll(
        listOf(
            VisitImageEntity(
                ID = id,
                VISITA_ID = visitaId,
                URI = "/data/comprobante_visita_$id.jpg",
                MIME = "image/jpeg",
                ORDEN = 0,
                CREADA_EN = creadaEn,
                SUBIDA_EN = subidaEn
            )
        )
    )

    private suspend fun assertNoLongerExists(dao: VisitDao, id: String) {
        val result = runCatching { dao.getVisitById(id) }.getOrNull()
        assertEquals(
            "row for $id must be gone after deletion",
            null,
            result
        )
    }
}
