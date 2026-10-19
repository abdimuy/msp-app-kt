package com.example.msp_app.feature.visitas.data.adapter

import app.cash.turbine.test
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.feature.visitas.domain.model.PendientesDelTelefono
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-096: los cuatro conteos del aviso de pendientes, sobre Room real.
 *
 * Cada prueba siembra a la vez una fila que SÍ cuenta y otra que NO (enviada,
 * subida, huérfana o resguardada por el servidor): con una sola fila, un filtro
 * equivocado y uno correcto darían lo mismo.
 */
class RoomPendientesDelTelefonoAdapterTest : RoomTestBase() {

    private val adapter by lazy {
        RoomPendientesDelTelefonoAdapter(
            db.visitDao(),
            db.visitImageDao(),
            db.paymentDao(),
            db.localSaleDao()
        )
    }

    @Test
    fun `E-APP-096 sin nada local no hay pendientes`() = runTest {
        assertEquals(PendientesDelTelefono(), adapter.observar().first())
    }

    @Test
    fun `E-APP-096 cuenta solo las visitas sin enviar`() = runTest {
        db.visitDao().insertVisit(visita("vis-pendiente", guardada = 0))
        db.visitDao().insertVisit(visita("vis-enviada", guardada = 1))

        assertEquals(1, adapter.observar().first().visitas)
    }

    @Test
    fun `E-APP-096 cuenta solo las fotos sin subir de visitas que existen`() = runTest {
        db.visitDao().insertVisit(visita("vis-1", guardada = 1))
        db.visitImageDao().insertAll(
            listOf(
                imagen("img-sin-subir", visitaId = "vis-1", subidaEn = null),
                imagen("img-subida", visitaId = "vis-1", subidaEn = "2026-10-18T15:00:00Z"),
                // Huérfana: su visita ya no existe y nadie la va a mandar.
                imagen("img-huerfana", visitaId = "vis-borrada", subidaEn = null)
            )
        )

        assertEquals(1, adapter.observar().first().fotos)
    }

    @Test
    fun `E-APP-096 cuenta los pagos sin guardar con la lectura del menu lateral`() = runTest {
        db.paymentDao().saveAll(
            listOf(pago("p-pendiente", guardado = false), pago("p-guardado", guardado = true))
        )

        assertEquals(1, adapter.observar().first().pagos)
    }

    @Test
    fun `E-APP-096 cuenta las ventas sin subir con la lectura del menu lateral`() = runTest {
        db.localSaleDao().insertSale(venta("v-pendiente", enviado = false))
        db.localSaleDao().insertSale(venta("v-enviada", enviado = true))
        db.localSaleDao().insertSale(venta("v-resguardada", enviado = false, permanente = true))

        assertEquals(1, adapter.observar().first().ventas)
    }

    @Test
    fun `E-APP-096 el conteo se mueve solo cuando cambia Room`() = runTest {
        adapter.observar().test {
            assertEquals(0, awaitItem().total)
            db.visitDao().insertVisit(visita("vis-nueva", guardada = 0))
            assertEquals(1, awaitItem().visitas)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun visita(id: String, guardada: Int) = VisitEntity(
        ID = id,
        CLIENTE_ID = 30144,
        COBRADOR = "Martín Salgado",
        COBRADOR_ID = 12,
        FECHA = "2026-10-18T14:00:00Z",
        FORMA_COBRO_ID = 157,
        LAT = 18.46,
        LNG = -97.39,
        TIPO_VISITA = "No se encontraba",
        ZONA_CLIENTE_ID = 25,
        GUARDADO_EN_MICROSIP = guardada
    )

    private fun imagen(id: String, visitaId: String, subidaEn: String?) = VisitImageEntity(
        ID = id,
        VISITA_ID = visitaId,
        URI = "file:///data/visitas/$id.jpg",
        MIME = "image/jpeg",
        CREADA_EN = "2026-10-18T14:00:00Z",
        SUBIDA_EN = subidaEn
    )

    private fun pago(id: String, guardado: Boolean) = PaymentEntity(
        ID = id,
        COBRADOR = "Efraín Domínguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = if (guardado) 91027 else 0,
        FECHA_HORA_PAGO = "2026-10-18T16:45:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 350.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Araceli Jiménez Cortés"
    )

    private fun venta(id: String, enviado: Boolean, permanente: Boolean? = null) = LocalSaleEntity(
        LOCAL_SALE_ID = id,
        NOMBRE_CLIENTE = "Rosa Elena Martínez Vázquez",
        FECHA_VENTA = "2026-10-18T15:30:00Z",
        LATITUD = 19.043415,
        LONGITUD = -98.198234,
        DIRECCION = "Privada de las Rosas 45",
        PARCIALIDAD = 850.0,
        ENGANCHE = 500.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = null,
        NOTA = null,
        DIA_COBRANZA = "MARTES",
        PRECIO_TOTAL = 6800.0,
        TIEMPO_A_CORTO_PLAZOMESES = 8,
        MONTO_A_CORTO_PLAZO = 6300.0,
        MONTO_DE_CONTADO = 5800.0,
        ENVIADO = enviado,
        LAST_UPLOAD_PERMANENT = permanente
    )
}
