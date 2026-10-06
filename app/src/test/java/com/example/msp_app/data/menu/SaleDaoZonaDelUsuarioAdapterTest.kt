package com.example.msp_app.data.menu

import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.sync.cobranza.UserContext
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ruling R3 (plan `2026-10-06-menu-lateral`): con zona conocida da el nombre, sin usuario/zona da `null`. */
class SaleDaoZonaDelUsuarioAdapterTest : RoomTestBase() {

    private fun ventaDeZona(zonaId: Int, zonaNombre: String) = SaleEntity(
        DOCTO_CC_ACR_ID = 1,
        DOCTO_CC_ID = 1,
        FOLIO = "F-1",
        CLIENTE_ID = 30144,
        APLICADO = "N",
        COBRADOR_ID = 7,
        CLIENTE = "Araceli Jiménez Cortés",
        ZONA_CLIENTE_ID = zonaId,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = zonaNombre,
        IMPORTE_PAGO_PROMEDIO = null,
        TOTAL_IMPORTE = 0.0,
        NUM_IMPORTES = 0,
        FECHA = "2026-10-06T15:30:00Z",
        PARCIALIDAD = 1,
        ENGANCHE = 0.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 0.0,
        IMPTE_REST = 0.0,
        SALDO_REST = 0.0,
        FECHA_ULT_PAGO = null,
        CALLE = "",
        CIUDAD = "",
        ESTADO = "",
        TELEFONO = "",
        NOMBRE_COBRADOR = "",
        ESTADO_COBRANZA = "",
        DIA_COBRANZA = "",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 0.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = null
    )

    @Test
    fun `con la zona del usuario da el nombre de esa zona`() = runTest {
        db.saleDao().insertAll(listOf(ventaDeZona(zonaId = 25, zonaNombre = "R/25")))
        val adaptador = SaleDaoZonaDelUsuarioAdapter(
            saleDao = db.saleDao(),
            userContextFlow = flowOf(UserContext(zona = 25, fechaCargaInicial = null))
        )

        assertEquals("R/25", adaptador.nombreDeZona().first())
    }

    @Test
    fun `sin usuario (contexto nulo) no hay nombre de zona`() = runTest {
        db.saleDao().insertAll(listOf(ventaDeZona(zonaId = 25, zonaNombre = "R/25")))
        val adaptador = SaleDaoZonaDelUsuarioAdapter(
            saleDao = db.saleDao(),
            userContextFlow = flowOf(null)
        )

        assertNull(adaptador.nombreDeZona().first())
    }

    /** Control de defensa en profundidad: una zona 0 o negativa nunca busca nombre, aunque alguien la empuje. */
    @Test
    fun `una zona invalida en el contexto tampoco da nombre`() = runTest {
        db.saleDao().insertAll(listOf(ventaDeZona(zonaId = 0, zonaNombre = "sin zona")))
        val adaptador = SaleDaoZonaDelUsuarioAdapter(
            saleDao = db.saleDao(),
            userContextFlow = flowOf(UserContext(zona = 0, fechaCargaInicial = null))
        )

        assertNull(adaptador.nombreDeZona().first())
    }
}
