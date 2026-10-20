package com.example.msp_app.features.guarantees.data

import com.example.msp_app.core.database.dao.guarantee.GuaranteeDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.GuaranteeEntity
import com.example.msp_app.core.database.entities.GuaranteeImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-091: la garantía nueva y sus imágenes, en UNA transacción con su
 * marcador. Nunca una garantía sin imágenes, nunca dos por el mismo id.
 */
class RegistroDeGarantiaTest : RoomTestBase() {

    private val clock = FakeClock(Instant.parse("2026-10-20T16:00:00Z"))

    @Test
    fun `E-APP-091 si las imagenes truenan no queda la garantia`() = runTest {
        val rota = object : GuaranteeDao by db.guaranteeDao() {
            override suspend fun insertGuaranteesImagen(images: List<GuaranteeImageEntity>): Unit =
                error("disco lleno")
        }
        val registro = RegistroDeGarantia(db, clock, rota)
        registro.capturar(ID, "rosa@example.com")

        runCatching { registro.escribir(garantia(), imagenes()) }

        assertTrue(
            "antes quedaba una garantia sin imagenes",
            db.guaranteeDao().getAllGuarantees().isEmpty()
        )
        assertEquals(CapturaEnCursoEntity.CAPTURADA, db.capturaEnCursoDao().obtener(ID)!!.ESTADO)
    }

    @Test
    fun `E-APP-091 cancelada dentro de la transaccion queda completa con sus imagenes`() = runTest {
        lateinit var job: Job
        val cancela = object : GuaranteeDao by db.guaranteeDao() {
            override suspend fun insertGuarantees(guarantee: GuaranteeEntity) {
                db.guaranteeDao().insertGuarantees(guarantee)
                job.cancel()
            }
        }
        job = launch(UnconfinedTestDispatcher(testScheduler)) {
            RegistroDeGarantia(db, clock, cancela).escribir(garantia(), imagenes())
        }
        job.join()

        assertEquals(1, db.guaranteeDao().getAllGuarantees().size)
        assertEquals(2, db.guaranteeDao().getImagesByExternalId(ID).size)
    }

    @Test
    fun `E-APP-091 el reintento con el mismo id no crea otra garantia`() = runTest {
        val registro = RegistroDeGarantia(db, clock)
        registro.capturar(ID, "rosa@example.com")
        assertTrue(registro.escribir(garantia(), imagenes()))
        assertFalse(registro.escribir(garantia(), imagenes()))

        assertEquals(
            "la PK autogenerada duplicaba la garantia",
            1,
            db.guaranteeDao().getAllGuarantees().size
        )
        registro.cerrar(ID)
        assertEquals(CapturaEnCursoEntity.CERRADA, db.capturaEnCursoDao().obtener(ID)!!.ESTADO)
    }

    private fun garantia() = GuaranteeEntity(
        ID = 0,
        EXTERNAL_ID = ID,
        DOCTO_CC_ID = null,
        ESTADO = "NOTIFICADO",
        DESCRIPCION_FALLA = "la base de la cama truena al sentarse",
        OBSERVACIONES = null,
        UPLOADED = 0,
        FECHA_SOLICITUD = "2026-10-20T16:00:00Z",
        NOMBRE_CLIENTE = "Victoria Flores Olmedo",
        NOMBRE_PRODUCTO = "Base matrimonial"
    )

    private fun imagenes() = (0..1).map { i ->
        GuaranteeImageEntity(
            ID = "img-$i",
            GARANTIA_ID = ID,
            IMG_PATH = "/tmp/guarantee_${ID}_$i.jpg",
            IMG_MIME = "image/jpeg",
            IMG_DESC = null,
            FECHA_SUBIDA = "2026-10-20T16:00:00Z"
        )
    }

    private companion object {
        const val ID = "7e0c2b1a-3d4f-4a5b-8c6d-9e0f1a2b3c4d"
    }
}
