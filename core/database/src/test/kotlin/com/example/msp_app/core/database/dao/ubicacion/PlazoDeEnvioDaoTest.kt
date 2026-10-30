package com.example.msp_app.core.database.dao.ubicacion

import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-145 (K1): el plazo de retencion vive en SU tabla. Anotarlo dos veces no lo reescribe ni
 * lanza, y es independiente de `ubicacion_de_captura`: la ubicacion tardia de la misma captura no
 * se bloquea por el plazo (el insert IGNORE de aquella no ve esta fila).
 */
class PlazoDeEnvioDaoTest : RoomTestBase() {

    private val plazos get() = db.plazoDeEnvioDao()
    private val ubicaciones get() = db.ubicacionDeCapturaDao()

    private fun plazo(id: String, guardadoEn: Long = 1_000L) = PlazoDeEnvioEntity(
        id = id,
        tipo = UbicacionDeCapturaEntity.TIPO_PAGO,
        guardadoEnEpochMs = guardadoEn,
        plazoMs = PlazoDeEnvioEntity.PLAZO_MS
    )

    @Test
    fun `E-APP-145 anotar dos veces el mismo id no lanza ni reescribe el plazo`() = runTest {
        assertTrue(plazos.anotar(plazo("a", guardadoEn = 1_000L)) > 0)
        assertEquals(-1L, plazos.anotar(plazo("a", guardadoEn = 9_999L)))

        assertEquals(1_000L, plazos.obtener("a")!!.guardadoEnEpochMs)
        assertEquals(30_000L, plazos.obtener("a")!!.plazoMs)
    }

    @Test
    fun `E-APP-145 una captura sin fila no tiene plazo`() = runTest {
        assertNull(plazos.obtener("no-existe"))
    }

    @Test
    fun `E-APP-145 el plazo no bloquea la ubicacion tardia de la misma captura`() = runTest {
        plazos.anotar(plazo("a"))

        val fila = UbicacionDeCapturaEntity(
            id = "a",
            tipo = UbicacionDeCapturaEntity.TIPO_PAGO,
            lat = 20.0,
            lng = -103.0,
            precisionM = 10.0,
            fixAtMs = 1L,
            origen = UbicacionDeCapturaEntity.ORIGEN_DESPUES,
            estadoEnvio = UbicacionDeCapturaEntity.PENDIENTE,
            intentos = 0,
            creadaEn = 2L
        )
        assertTrue("el insert IGNORE de la ubicacion entra", ubicaciones.anotar(fila) > 0)
    }
}
