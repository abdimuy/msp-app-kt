package com.example.msp_app.core.database.dao.ubicacion

import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-146 / E-APP-148: la ubicacion que llega con la captura ya guardada se escribe en UN solo
 * UPDATE de lat y lng, solo si seguian en 0,0, y nunca pisa una ubicacion existente.
 */
class ActualizarUbicacionSiVaciaTest : RoomTestBase() {

    private companion object {
        const val CICLOS = 1500
    }

    private fun pago(id: String, lat: Double?, lng: Double?) = PaymentEntity(
        ID = id,
        COBRADOR = "Mendoza Torres, Ana",
        DOCTO_CC_ACR_ID = 5000,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-30T18:00:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 220.0,
        LAT = lat,
        LNG = lng,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 77,
        FORMA_COBRO_ID = 87327,
        ZONA_CLIENTE_ID = 21552,
        NOMBRE_CLIENTE = "Lopez Garcia, Minerva"
    )

    private fun visita(id: String, lat: Double, lng: Double) = VisitEntity(
        ID = id,
        CLIENTE_ID = 11486,
        COBRADOR_ID = 77,
        FECHA = "2026-10-30T18:00:00Z",
        FORMA_COBRO_ID = 0,
        LAT = lat,
        LNG = lng,
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21552
    )

    @Test
    fun `E-APP-146 el pago en 0,0 recibe lat y lng juntas`() = runTest {
        db.paymentDao().savePayment(pago("p", 0.0, 0.0))
        assertEquals(1, db.paymentDao().updateLocationIfEmpty("p", 20.5, -100.3))
        val p = db.paymentDao().getPaymentById("p")!!
        assertEquals(20.5, p.LAT!!, 0.0)
        assertEquals(-100.3, p.LNG!!, 0.0)
    }

    @Test
    fun `E-APP-146 el pago con lat y lng nulas tambien cuenta como vacio`() = runTest {
        db.paymentDao().savePayment(pago("p", null, null))
        assertEquals(1, db.paymentDao().updateLocationIfEmpty("p", 20.5, -100.3))
    }

    @Test
    fun `E-APP-146 nunca sobrescribe una ubicacion que ya tenia, ni a medias`() = runTest {
        db.paymentDao().savePayment(pago("p", 19.0, -99.0))
        db.paymentDao().savePayment(pago("solo-lng", 0.0, -99.0))
        assertEquals(0, db.paymentDao().updateLocationIfEmpty("p", 20.5, -100.3))
        assertEquals(0, db.paymentDao().updateLocationIfEmpty("solo-lng", 20.5, -100.3))
        assertEquals(19.0, db.paymentDao().getPaymentById("p")!!.LAT!!, 0.0)
        assertEquals(0.0, db.paymentDao().getPaymentById("solo-lng")!!.LAT!!, 0.0)
    }

    @Test
    fun `E-APP-146 un pago que no existe toca cero filas`() = runTest {
        assertEquals(0, db.paymentDao().updateLocationIfEmpty("no-existe", 20.5, -100.3))
    }

    @Test
    fun `E-APP-148 la visita en 0,0 recibe lat y lng, y no se sobrescribe despues`() = runTest {
        db.visitDao().insertVisit(visita("v", 0.0, 0.0))
        assertEquals(1, db.visitDao().updateLocationIfEmpty("v", 20.5, -100.3))
        assertEquals(0, db.visitDao().updateLocationIfEmpty("v", 21.0, -101.0))
        val v = db.visitDao().getVisitById("v")
        assertEquals(20.5, v.LAT, 0.0)
        assertEquals(-100.3, v.LNG, 0.0)
    }

    // ─── E-APP-147 (K3): el worker nunca lee lat sin lng ──────────────────────

    /**
     * Un lector concurrente contra ciclos de `(0,0)` -> escritura. Cuenta lecturas a medias (una
     * coordenada escrita y la otra no). La escritura real es UN solo UPDATE, asi que no hay ninguna.
     */
    private fun lecturasAMedias(escribir: suspend (String) -> Unit): Pair<Int, Int> = runBlocking {
        db.paymentDao().savePayment(pago("c", 0.0, 0.0))
        var aMedias = 0
        var lecturas = 0
        val terminado = java.util.concurrent.atomic.AtomicBoolean(false)
        val lector = launch(Dispatchers.Default) {
            while (!terminado.get()) {
                val p = db.paymentDao().getPaymentById("c")!!
                lecturas++
                if (((p.LAT ?: 0.0) != 0.0) != ((p.LNG ?: 0.0) != 0.0)) aMedias++
            }
        }
        repeat(CICLOS) {
            db.openHelper.writableDatabase.execSQL(
                "UPDATE Payment SET LAT = 0.0, LNG = 0.0 WHERE id = 'c'"
            )
            escribir("c")
        }
        terminado.set(true)
        lector.join()
        aMedias to lecturas
    }

    @Test
    fun `E-APP-147 la escritura real nunca se ve a medias, y el control positivo si`() {
        val (real, lecturasReal) = lecturasAMedias {
            db.paymentDao().updateLocationIfEmpty(it, 20.5, -100.3)
        }
        assertEquals("el UPDATE unico nunca se ve a medias", 0, real)
        assertTrue("el lector leyo ($lecturasReal)", lecturasReal > 100)

        // Control positivo: la MISMA escritura partida en dos UPDATE sueltos SI se ve a medias.
        val (partida, _) = lecturasAMedias {
            db.openHelper.writableDatabase.execSQL("UPDATE Payment SET LAT = 20.5 WHERE id = '$it'")
            db.openHelper.writableDatabase.execSQL(
                "UPDATE Payment SET LNG = -100.3 WHERE id = '$it'"
            )
        }
        assertTrue("el lector habria visto lecturas a medias ($partida)", partida > 0)
    }
}
