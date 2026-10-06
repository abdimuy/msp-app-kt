package com.example.msp_app.data.menu

import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EstadoDeEnvioAdapterTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-10-06T16:10:00Z")

    private fun estado(
        recurso: String,
        sincronizado: String = "2026-10-06T16:07:00Z",
        error: String? = null
    ) = CobranzaSyncStateEntity(
        RESOURCE = recurso,
        ZONA_CLIENTE_ID = 21,
        CURSOR = null,
        LAST_SYNCED_AT = sincronizado,
        LAST_ERROR = error
    )

    private fun pagoPendiente(id: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Efraín Domínguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 0,
        FECHA_HORA_PAGO = "2026-10-06T16:00:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = 200.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernández Soto"
    )

    @Test
    fun `los dos recursos sanos dan los minutos desde el mas viejo`() {
        val estados =
            listOf(
                estado("ventas", "2026-10-06T16:07:00Z"),
                estado("pagos", "2026-10-06T16:05:00Z")
            )
        assertEquals(5L, minutosDesdeElUltimoContacto(estados, ahora))
    }

    @Test
    fun `si el ultimo intento fallo no hay tiempo aunque LAST_SYNCED_AT sea reciente`() {
        val estados = listOf(estado("ventas"), estado("pagos", error = "timeout"))
        assertNull(minutosDesdeElUltimoContacto(estados, ahora))
    }

    @Test
    fun `una marca ilegible no da tiempo`() {
        assertNull(
            minutosDesdeElUltimoContacto(listOf(estado("ventas"), estado("pagos", "ayer")), ahora)
        )
    }

    @Test
    fun `una marca en el futuro no da minutos negativos`() {
        val estados =
            listOf(
                estado("ventas", "2026-10-06T16:30:00Z"),
                estado("pagos", "2026-10-06T16:30:00Z")
            )
        assertNull(minutosDesdeElUltimoContacto(estados, ahora))
    }

    @Test
    fun `sin uno de los dos recursos no hay tiempo`() {
        assertNull(minutosDesdeElUltimoContacto(listOf(estado("ventas")), ahora))
    }

    @Test
    fun `el adaptador junta pendientes y tiempo desde Room`() = runTest {
        db.paymentDao().saveAll(listOf(pagoPendiente("p1"), pagoPendiente("p2")))
        db.cobranzaSyncStateDao().upsert(estado("ventas"))
        db.cobranzaSyncStateDao().upsert(estado("pagos"))
        val adaptador = EstadoDeEnvioAdapter(
            paymentDao = db.paymentDao(),
            localSaleDao = db.localSaleDao(),
            syncStateDao = db.cobranzaSyncStateDao(),
            clock = FakeClock(ahora),
            pulso = flowOf(Unit)
        )

        assertEquals(
            EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = 3),
            adaptador.estado().first()
        )
    }
}
