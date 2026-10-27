package com.example.msp_app.core.database.dao.captura

import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E-APP-084 / E-APP-090: las transiciones del marcador sólo avanzan, y la única
 * consulta que borra nunca toca una CAPTURADA ni una con discrepancia.
 */
class CapturaEnCursoDaoTest : RoomTestBase() {

    private val dao get() = db.capturaEnCursoDao()

    private fun marcador(
        id: String,
        estado: String = CapturaEnCursoEntity.CAPTURADA,
        en: String = VIEJA
    ) = CapturaEnCursoEntity(
        ID = id,
        TIPO = CapturaEnCursoEntity.TIPO_ABONO,
        LLAVE = "77188",
        USUARIO = USUARIO,
        ESTADO = estado,
        RESUMEN = "{}",
        CREADA_EN = en,
        ACTUALIZADA_EN = en
    )

    @Test
    fun `E-APP-084 capturar dos veces el mismo UUID deja una fila y no la reescribe`() = runTest {
        dao.capturar(marcador("a"))
        dao.marcarEscrita("a", HOY)
        dao.capturar(marcador("a"))

        assertEquals(CapturaEnCursoEntity.ESCRITA, dao.obtener("a")!!.ESTADO)
    }

    @Test
    fun `E-APP-084 cerrar una CAPTURADA no hace nada y escribir una CERRADA tampoco`() = runTest {
        dao.capturar(marcador("a"))
        assertEquals(0, dao.marcarCerrada("a", HOY))
        assertEquals(1, dao.marcarEscrita("a", HOY))
        assertEquals(1, dao.marcarCerrada("a", HOY))
        assertEquals(0, dao.marcarEscrita("a", HOY))
        assertEquals(CapturaEnCursoEntity.CERRADA, dao.obtener("a")!!.ESTADO)
    }

    @Test
    fun `E-APP-090 el barrido borra solo CERRADA vieja sin discrepancia`() = runTest {
        dao.capturar(marcador("capturada-vieja"))
        dao.capturar(marcador("escrita-vieja", CapturaEnCursoEntity.ESCRITA))
        dao.capturar(marcador("cerrada-vieja", CapturaEnCursoEntity.CERRADA))
        dao.capturar(marcador("cerrada-reciente", CapturaEnCursoEntity.CERRADA, HOY))
        dao.capturar(marcador("cerrada-con-discrepancia", CapturaEnCursoEntity.CERRADA))
        dao.marcarDiscrepancia("cerrada-con-discrepancia", "contenido_no_coincide", VIEJA)

        assertEquals(1, dao.barrerCerradas(LIMITE))

        assertNull(dao.obtener("cerrada-vieja"))
        assertNotNull(dao.obtener("capturada-vieja"))
        assertNotNull(dao.obtener("escrita-vieja"))
        assertNotNull(dao.obtener("cerrada-reciente"))
        assertNotNull(dao.obtener("cerrada-con-discrepancia"))
    }

    @Test
    fun `E-APP-084 la abierta de una llave es la mas reciente CAPTURADA o ESCRITA del usuario`() =
        runTest {
            dao.capturar(marcador("vieja", CapturaEnCursoEntity.CERRADA, VIEJA))
            dao.capturar(marcador("nueva", en = HOY))
            dao.capturar(marcador("de-otro", en = HOY).copy(ID = "z", USUARIO = "otro@example.com"))

            assertEquals(
                "nueva",
                dao.abiertaDe(CapturaEnCursoEntity.TIPO_ABONO, "77188", USUARIO)!!.ID
            )
            assertNull(dao.abiertaDe(CapturaEnCursoEntity.TIPO_ABONO, "1", USUARIO))
        }

    @Test
    fun `E-APP-084 sin terminar cuenta CAPTURADA y discrepancias abiertas del usuario`() = runTest {
        dao.capturar(marcador("c1"))
        dao.capturar(marcador("e1", CapturaEnCursoEntity.ESCRITA))
        dao.capturar(marcador("e2", CapturaEnCursoEntity.ESCRITA))
        dao.marcarDiscrepancia("e2", "contenido_no_coincide", HOY)
        dao.capturar(marcador("o1").copy(USUARIO = "otro@example.com"))
        dao.capturar(marcador("v1").copy(TIPO = CapturaEnCursoEntity.TIPO_VISITA))

        assertEquals(
            "c1 y e2; la visita sin pantalla que la reanude no cuenta",
            2,
            dao.observarSinTerminar(USUARIO).first()
        )
    }

    private companion object {
        const val USUARIO = "rosa@example.com"
        const val VIEJA = "2026-10-01T10:00:00Z"
        const val LIMITE = "2026-10-13T10:00:00Z"
        const val HOY = "2026-10-20T10:00:00Z"
    }
}
