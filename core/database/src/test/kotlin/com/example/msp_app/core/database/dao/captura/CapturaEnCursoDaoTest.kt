package com.example.msp_app.core.database.dao.captura

import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
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
    fun `E-APP-119 sin terminar cuenta toda CAPTURADA del usuario, visitas y garantias incluidas, y las discrepancias`() =
        runTest {
            dao.capturar(marcador("c1"))
            dao.capturar(marcador("e1", CapturaEnCursoEntity.ESCRITA))
            dao.capturar(marcador("e2", CapturaEnCursoEntity.ESCRITA))
            dao.marcarDiscrepancia("e2", "contenido_no_coincide", HOY)
            dao.capturar(marcador("o1").copy(USUARIO = "otro@example.com"))
            dao.capturar(marcador("v1").copy(TIPO = CapturaEnCursoEntity.TIPO_VISITA))
            dao.capturar(marcador("g1").copy(TIPO = CapturaEnCursoEntity.TIPO_GARANTIA))
            dao.capturar(
                marcador("v2-escrita", CapturaEnCursoEntity.ESCRITA)
                    .copy(TIPO = CapturaEnCursoEntity.TIPO_VISITA)
            )
            dao.capturar(marcador("vl").copy(TIPO = CapturaEnCursoEntity.TIPO_VENTA_LOCAL))
            dao.capturar(marcador("cd").copy(TIPO = CapturaEnCursoEntity.TIPO_CONDONACION))

            assertEquals(
                "c1, e2, v1, g1 y vl; la ESCRITA, la de otro usuario y la condonación (D4) no cuentan",
                5,
                dao.observarSinTerminar(USUARIO).first()
            )
        }

    @Test
    fun `E-APP-117 una discrepancia sobre un marcador CERRADA cuenta en el aviso`() = runTest {
        dao.capturar(marcador("cerrada", CapturaEnCursoEntity.CERRADA))
        assertEquals(
            "control: una CERRADA sin discrepancia no cuenta",
            0,
            dao.observarSinTerminar(USUARIO).first()
        )

        dao.marcarDiscrepancia("cerrada", "contenido_no_coincide", HOY)

        assertEquals(1, dao.observarSinTerminar(USUARIO).first())
    }

    @Test
    fun `E-APP-118 los envios que no coinciden cuentan abonos y condonaciones en cualquier estado`() =
        runTest {
            dao.capturar(marcador("abono-cerrado", CapturaEnCursoEntity.CERRADA))
            dao.marcarDiscrepancia("abono-cerrado", "contenido_no_coincide", HOY)
            dao.capturar(
                marcador("condonacion", CapturaEnCursoEntity.ESCRITA)
                    .copy(TIPO = CapturaEnCursoEntity.TIPO_CONDONACION, LLAVE = "77021")
            )
            dao.marcarDiscrepancia("condonacion", "no_existe_en_servidor", HOY)
            dao.capturar(marcador("sin-discrepancia", CapturaEnCursoEntity.ESCRITA))
            dao.capturar(marcador("de-otro").copy(USUARIO = "otro@example.com"))
            dao.marcarDiscrepancia("de-otro", "contenido_no_coincide", HOY)

            assertEquals(2, dao.observarEnviosQueNoCoinciden(USUARIO).first())
        }

    @Test
    fun `E-APP-118 la venta del aviso es la del pago o condonacion que no coincide si no hay abono sin guardar`() =
        runTest {
            assertNull("sin nada, sin destino", dao.observarVentaDelAviso(USUARIO).first())
            dao.capturar(
                marcador("condonacion", CapturaEnCursoEntity.ESCRITA, VIEJA)
                    .copy(TIPO = CapturaEnCursoEntity.TIPO_CONDONACION, LLAVE = "77021")
            )
            dao.marcarDiscrepancia("condonacion", "contenido_no_coincide", HOY)

            assertEquals("77021", dao.observarVentaDelAviso(USUARIO).first())

            dao.capturar(marcador("abono-viejo", en = "2026-09-01T10:00:00Z").copy(LLAVE = "88001"))
            assertEquals(
                "el mas viejo manda: el abono sin guardar",
                "88001",
                dao.observarVentaDelAviso(USUARIO).first()
            )
        }

    @Test
    fun `E-APP-119 la venta local sin terminar es la CAPTURADA mas vieja y dice si ya esta escrita`() =
        runTest {
            assertNull(dao.observarVentaLocalSinTerminar(USUARIO).first())
            dao.capturar(
                marcador("venta-1", en = VIEJA)
                    .copy(TIPO = CapturaEnCursoEntity.TIPO_VENTA_LOCAL, LLAVE = "venta_nueva")
            )

            assertEquals(
                VentaLocalSinTerminarFila("venta-1", false),
                dao.observarVentaLocalSinTerminar(USUARIO).first()
            )

            db.localSaleDao().insertSale(ventaLocal("venta-1"))
            assertEquals(
                "ya en local_sale: el toque abre la venta",
                VentaLocalSinTerminarFila("venta-1", true),
                dao.observarVentaLocalSinTerminar(USUARIO).first()
            )
        }

    private fun ventaLocal(id: String) = LocalSaleEntity(
        LOCAL_SALE_ID = id,
        NOMBRE_CLIENTE = "Guadalupe Hernández Ruiz",
        FECHA_VENTA = HOY,
        LATITUD = 0.0,
        LONGITUD = 0.0,
        DIRECCION = "C. Reforma 18",
        PARCIALIDAD = 150.0,
        ENGANCHE = null,
        TELEFONO = "2381234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = null,
        NOTA = null,
        DIA_COBRANZA = "LUNES",
        PRECIO_TOTAL = 4200.0,
        TIEMPO_A_CORTO_PLAZOMESES = 4,
        MONTO_A_CORTO_PLAZO = 3600.0,
        MONTO_DE_CONTADO = 3200.0,
        ENVIADO = false
    )

    private companion object {
        const val USUARIO = "rosa@example.com"
        const val VIEJA = "2026-10-01T10:00:00Z"
        const val LIMITE = "2026-10-13T10:00:00Z"
        const val HOY = "2026-10-20T10:00:00Z"
    }
}
