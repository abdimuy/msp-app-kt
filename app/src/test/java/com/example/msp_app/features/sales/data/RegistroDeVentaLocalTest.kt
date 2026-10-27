package com.example.msp_app.features.sales.data

import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-094 (D5): la venta local nueva se escribe en UNA transacción con inserts
 * `ABORT` y su marcador; nunca a medias, nunca dos veces, nunca pisando una
 * venta ya escrita.
 */
class RegistroDeVentaLocalTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-26T18:00:00Z"))

    @Test
    fun `E-APP-094 si las fotos truenan no queda ni la venta ni sus lineas ni sus combos`() =
        runTest {
            val rota = object : LocalSaleDao by db.localSaleDao() {
                override suspend fun insertarImagenesNuevas(
                    imagenes: List<LocalSaleImageEntity>
                ): Unit = error("disco lleno")
            }
            val registro = RegistroDeVentaLocal(db, clock, ventas = rota)
            registro.capturar(ID, USUARIO)

            runCatching { registro.escribir(venta(), lineas(), combos(), fotos()) }

            assertNull("antes quedaba la cabecera sin el resto", db.localSaleDao().getSaleById(ID))
            assertTrue(db.localSaleProduct().getProductsForSale(ID).isEmpty())
            assertTrue(db.localSaleComboDao().getCombosForSale(ID).isEmpty())
            assertEquals(
                CapturaEnCursoEntity.CAPTURADA,
                db.capturaEnCursoDao().obtener(ID)!!.ESTADO
            )
        }

    @Test
    fun `E-APP-094 cancelada dentro de la transaccion queda completa`() = runTest {
        lateinit var job: Job
        val cancela = object : LocalSaleComboDao by db.localSaleComboDao() {
            override suspend fun insertarCombosNuevos(combos: List<LocalSaleComboEntity>) {
                db.localSaleComboDao().insertarCombosNuevos(combos)
                job.cancel()
            }
        }
        val registro = RegistroDeVentaLocal(db, clock, combos = cancela)
        registro.capturar(ID, USUARIO)
        job = launch(UnconfinedTestDispatcher(testScheduler)) {
            registro.escribir(venta(), lineas(), combos(), fotos())
        }
        job.join()

        assertEquals(ID, db.localSaleDao().getSaleById(ID)?.LOCAL_SALE_ID)
        assertEquals(2, db.localSaleProduct().getProductsForSale(ID).size)
        assertEquals(1, db.localSaleComboDao().getCombosForSale(ID).size)
        assertEquals(2, db.localSaleDao().getImagesForSale(ID).size)
        assertEquals(CapturaEnCursoEntity.ESCRITA, db.capturaEnCursoDao().obtener(ID)!!.ESTADO)
    }

    @Test
    fun `E-APP-094 la misma venta escrita dos veces es una sola y no pierde fotos ni ENVIADO`() =
        runTest {
            val registro = RegistroDeVentaLocal(db, clock)
            registro.capturar(ID, USUARIO)
            assertEquals(
                ResultadoDeLaVentaLocal.ESCRITA,
                registro.escribir(venta(), lineas(), combos(), fotos())
            )
            db.localSaleDao().updateSaleStatus(ID, true)

            val segunda = registro.escribir(venta(), lineas(), combos(), fotos())

            assertEquals(ResultadoDeLaVentaLocal.YA_ESTABA, segunda)
            assertEquals(
                1,
                db.localSaleDao().getAllSalesSinFiltro().count { it.LOCAL_SALE_ID == ID }
            )
            assertEquals(
                "un REPLACE se llevaba las fotos por CASCADE",
                2,
                db.localSaleDao().getImagesForSale(ID).size
            )
            assertTrue(
                "un REPLACE regresaba ENVIADO a 0",
                db.localSaleDao().getSaleById(ID)!!.ENVIADO
            )
        }

    @Test
    fun `E-APP-094 lineas sin cabecera no se pisan ni se escribe otra venta`() = runTest {
        val huerfana = LocalSaleProductEntity(ID, 101, "Ropero", 3, 1.0, 1.0, 1.0)
        db.localSaleProduct().insertAllSaleProducts(listOf(huerfana))
        val registro = RegistroDeVentaLocal(db, clock)

        val resultado = registro.escribir(venta(), lineas(), combos(), fotos())

        assertEquals(ResultadoDeLaVentaLocal.LINEAS_HUERFANAS, resultado)
        assertNull(db.localSaleDao().getSaleById(ID))
        assertEquals(listOf(huerfana), db.localSaleProduct().getProductsForSale(ID))
    }

    @Test
    fun `E-APP-094 una linea repetida aborta toda la venta en vez de pisarse`() = runTest {
        val registro = RegistroDeVentaLocal(db, clock)
        val repetidas = lineas() + lineas().first().copy(CANTIDAD = 9)

        runCatching { registro.escribir(venta(), repetidas, combos(), fotos()) }

        assertNull(db.localSaleDao().getSaleById(ID))
        assertTrue(db.localSaleProduct().getProductsForSale(ID).isEmpty())
    }

    @Test
    fun `E-APP-094 cerrar deja el marcador CERRADA`() = runTest {
        val registro = RegistroDeVentaLocal(db, clock)
        registro.capturar(ID, USUARIO)
        registro.escribir(venta(), lineas(), combos(), fotos())

        registro.cerrar(ID)

        val marcador = db.capturaEnCursoDao().obtener(ID)!!
        assertEquals(CapturaEnCursoEntity.CERRADA, marcador.ESTADO)
        assertEquals(CapturaEnCursoEntity.TIPO_VENTA_LOCAL, marcador.TIPO)
        assertEquals(USUARIO, marcador.USUARIO)
    }

    private suspend fun LocalSaleDao.getAllSalesSinFiltro(): List<LocalSaleEntity> =
        listOfNotNull(getSaleById(ID))

    private fun venta() = LocalSaleEntity(
        LOCAL_SALE_ID = ID,
        NOMBRE_CLIENTE = "Rosa Hernández López",
        FECHA_VENTA = "2026-10-26T18:00:00Z",
        LATITUD = 19.04,
        LONGITUD = -98.2,
        DIRECCION = "Calle Hidalgo 14",
        PARCIALIDAD = 200.0,
        ENGANCHE = 0.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = "",
        NOTA = "",
        DIA_COBRANZA = "LUNES",
        PRECIO_TOTAL = 9000.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 8000.0,
        MONTO_DE_CONTADO = 7000.0,
        ENVIADO = false
    )

    private fun lineas() = listOf(
        LocalSaleProductEntity(ID, 101, "Ropero", 1, 5000.0, 4500.0, 4000.0, COMBO_ID = COMBO),
        LocalSaleProductEntity(ID, 102, "Buró", 1, 4000.0, 3500.0, 3000.0, COMBO_ID = COMBO)
    )

    private fun combos() = listOf(
        LocalSaleComboEntity(COMBO, ID, "Recámara", 9000.0, 8000.0, 7000.0)
    )

    private fun fotos() = listOf(
        LocalSaleImageEntity("img-1", ID, "/data/sale_${ID}_img-1.jpg", "2026-10-26T18:00:00Z"),
        LocalSaleImageEntity("img-2", ID, "/data/sale_${ID}_img-2.jpg", "2026-10-26T18:00:00Z")
    )

    private companion object {
        const val ID = "5f1c2a3b-7d4e-4c8f-9a1b-2c3d4e5f6a7b"
        const val COMBO = "combo-recamara"
        const val USUARIO = "vendedor@muebleriamsp.mx"
    }
}
