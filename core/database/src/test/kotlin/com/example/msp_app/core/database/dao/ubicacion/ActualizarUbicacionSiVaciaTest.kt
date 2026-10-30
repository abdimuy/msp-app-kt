package com.example.msp_app.core.database.dao.ubicacion

import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-146 / E-APP-148: la ubicacion que llega con la captura ya guardada se escribe en UN solo
 * UPDATE de lat y lng, solo si seguian en 0,0, y nunca pisa una ubicacion existente.
 */
class ActualizarUbicacionSiVaciaTest : RoomTestBase() {

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
}
