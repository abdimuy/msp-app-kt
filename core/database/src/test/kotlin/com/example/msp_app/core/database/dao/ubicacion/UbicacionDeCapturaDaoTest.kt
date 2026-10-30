package com.example.msp_app.core.database.dao.ubicacion

import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E-APP-145: una fila por captura; anotar dos veces no la reescribe y las
 * transiciones de estado solo salen de `pendiente`.
 */
class UbicacionDeCapturaDaoTest : RoomTestBase() {

    private val dao get() = db.ubicacionDeCapturaDao()

    private fun fila(
        id: String,
        lat: Double = 20.6597,
        origen: String = UbicacionDeCapturaEntity.ORIGEN_ANTES
    ) = UbicacionDeCapturaEntity(
        id = id,
        tipo = UbicacionDeCapturaEntity.TIPO_PAGO,
        lat = lat,
        lng = -103.3496,
        precisionM = 12.0,
        fixAtMs = 1_000L,
        origen = origen,
        estadoEnvio = UbicacionDeCapturaEntity.PENDIENTE,
        intentos = 0,
        creadaEn = 2_000L
    )

    @Test
    fun `E-APP-145 anotar dos veces el mismo id no reescribe la primera ubicacion`() = runTest {
        assertEquals(true, dao.anotar(fila("a", lat = 20.0)) > 0)
        assertEquals(
            -1L,
            dao.anotar(fila("a", lat = 21.0, origen = UbicacionDeCapturaEntity.ORIGEN_DESPUES))
        )

        val guardada = dao.obtener("a")!!
        assertEquals(20.0, guardada.lat, 0.0)
        assertEquals(UbicacionDeCapturaEntity.ORIGEN_ANTES, guardada.origen)
    }

    @Test
    fun `E-APP-145 una fila final no vuelve a pendiente ni cambia de final`() = runTest {
        dao.anotar(fila("a"))
        assertEquals(1, dao.marcarEnviada("a"))
        assertEquals(0, dao.marcarDescartada("a"))
        assertEquals(0, dao.sumarIntento("a"))
        assertEquals(UbicacionDeCapturaEntity.ENVIADA, dao.obtener("a")!!.estadoEnvio)
        assertEquals(0, dao.obtener("a")!!.intentos)
    }

    @Test
    fun `E-APP-145 sumar intento cuenta y pendientes solo trae las pendientes del tipo`() =
        runTest {
            dao.anotar(fila("a"))
            dao.anotar(fila("b"))
            dao.sumarIntento("a")
            dao.sumarIntento("a")
            dao.marcarDescartada("b")

            assertEquals(2, dao.obtener("a")!!.intentos)
            assertEquals(
                listOf("a"),
                dao.pendientes(UbicacionDeCapturaEntity.TIPO_PAGO).map { it.id }
            )
            assertEquals(
                emptyList<String>(),
                dao.pendientes(UbicacionDeCapturaEntity.TIPO_VISITA).map {
                    it.id
                }
            )
            assertNull(dao.obtener("no-existe"))
        }
}
