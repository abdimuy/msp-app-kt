package com.example.msp_app.core.database.dao.payment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val CARGO_CANCELADO = 48213
private const val OTRO_CARGO = 48999

/**
 * LA prueba que impide perder un pago cuando en oficina cancelan el cargo.
 *
 * [PaymentDao.deleteByDoctoCcAcrId] la llama el merge del sync al recibir
 * `cargo_cancelado`. Antes borraba TODOS los pagos del cargo sin mirar
 * `GUARDADO_EN_MICROSIP`: un cobrador que capturó un pago en la calle sobre un
 * cargo que ese mismo día cancelaron en oficina lo perdía sin dejar rastro —
 * ni en la app, ni en el servidor, ni en un log. Nadie podía saber que había
 * existido.
 *
 * El contrato correcto es asimétrico a propósito: el cache confirmado sí se
 * tira (la cancelación en Microsip es definitiva y el servidor es la fuente),
 * pero la captura pendiente se queda. Esa captura después fallará al subirse
 * contra un cargo cancelado, y ese fallo queda registrado en la captura de
 * intentos fallidos del servidor para resolverse desde el escritorio. Fallar
 * ruidosamente es recuperable; perder el dato en silencio no.
 *
 * Corre contra el Room/SQLite real (Robolectric, base en memoria), así que
 * prueba la query que produce el compilador de Room, no una reimplementación.
 */
class PaymentDaoCargoCanceladoSurvivalTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun payment(
        id: String,
        doctoCcAcrId: Int,
        guardadoEnMicrosip: Boolean,
        importe: Double = 350.0,
        nombreCliente: String = "Araceli Jimenez Cortes"
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = doctoCcAcrId,
        DOCTO_CC_ID = if (guardadoEnMicrosip) 91027 else 0,
        FECHA_HORA_PAGO = "2026-08-07T16:45:00Z",
        GUARDADO_EN_MICROSIP = guardadoEnMicrosip,
        IMPORTE = importe,
        LAT = 19.043415,
        LNG = -98.198234,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = nombreCliente
    )

    @Test
    fun `la captura pendiente sobrevive a la cancelacion del cargo y la confirmada no`() = runTest {
        val dao = database.paymentDao()
        val pendiente = payment(
            id = "9f1c7a24-0d3b-4e58-9c11-6a2f83b47d90",
            doctoCcAcrId = CARGO_CANCELADO,
            guardadoEnMicrosip = false,
            importe = 480.50,
            nombreCliente = "Guadalupe Ramirez Torres"
        )
        val confirmado = payment(
            id = "77001",
            doctoCcAcrId = CARGO_CANCELADO,
            guardadoEnMicrosip = true
        )
        dao.saveAll(listOf(pendiente, confirmado))

        dao.deleteByDoctoCcAcrId(CARGO_CANCELADO)

        val restantes = dao.getPaymentsBySaleId(CARGO_CANCELADO)
        assertEquals("solo debe quedar la captura pendiente", 1, restantes.size)
        val superviviente = restantes.single()
        assertEquals(pendiente.ID, superviviente.ID)
        assertEquals("el importe capturado no se toca", 480.50, superviviente.IMPORTE, 0.0)
        assertEquals("Guadalupe Ramirez Torres", superviviente.NOMBRE_CLIENTE)
        assertEquals("la atribución del cobrador se conserva", 7, superviviente.COBRADOR_ID)
        assertTrue(
            "y debe seguir en la cola de pendientes por subir",
            dao.getPendingPayments().any { it.ID == pendiente.ID }
        )
    }

    @Test
    fun `varias capturas pendientes del mismo cargo cancelado sobreviven todas`() = runTest {
        val dao = database.paymentDao()
        val pendientes = listOf(
            payment(
                id = "3b8e1f60-7c4a-4b2d-8e91-1d5f6c02a447",
                doctoCcAcrId = CARGO_CANCELADO,
                guardadoEnMicrosip = false,
                importe = 200.0,
                nombreCliente = "Rosa Elena Martinez Vazquez"
            ),
            payment(
                id = "c72d9a03-5e18-4f6b-a0d2-8b34e91f5c62",
                doctoCcAcrId = CARGO_CANCELADO,
                guardadoEnMicrosip = false,
                importe = 325.75,
                nombreCliente = "Maria Lopez Hernandez"
            )
        )
        dao.saveAll(pendientes + payment("77002", CARGO_CANCELADO, guardadoEnMicrosip = true))

        dao.deleteByDoctoCcAcrId(CARGO_CANCELADO)

        val sobrevivientes = dao.getPendingPayments().map { it.ID }.toSet()
        assertEquals(
            "ni un solo peso capturado se pierde",
            pendientes.map { it.ID }.toSet(),
            sobrevivientes
        )
    }

    @Test
    fun `el borrado no toca los pagos de otro cargo`() = runTest {
        val dao = database.paymentDao()
        dao.saveAll(
            listOf(
                payment("77003", CARGO_CANCELADO, guardadoEnMicrosip = true),
                payment("77004", OTRO_CARGO, guardadoEnMicrosip = true)
            )
        )

        dao.deleteByDoctoCcAcrId(CARGO_CANCELADO)

        assertTrue(dao.getPaymentsBySaleId(CARGO_CANCELADO).isEmpty())
        assertEquals(1, dao.getPaymentsBySaleId(OTRO_CARGO).size)
    }
}
