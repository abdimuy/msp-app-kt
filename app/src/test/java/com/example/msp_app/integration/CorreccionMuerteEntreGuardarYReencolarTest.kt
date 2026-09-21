package com.example.msp_app.integration

import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.LocalSalesWorkEnqueuer
import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.LocalSalesPendingSynchronizer
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.ventacorreccion.data.AppClockRelojPort
import com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SALE_ID = "sale-muerte-reencolado-001"

/**
 * Capa 3 de la Task 3 (plan "Corregir una venta antes de que suba"): "la app muere entre
 * guardar y reencolar" — `GuardarCorreccion` corre con un `ReencolarSubidaPort` real de
 * producción que LANZA (simula el proceso muerto justo después del commit). La transacción de
 * Room ya cerró, así que la corrección persiste; el barrido real (`LocalSalesPendingSynchronizer`,
 * `:app`) la encuentra en la siguiente pasada. Vive en `:app` (no en `:feature:ventaCorreccion`)
 * porque necesita el sincronizador real, que sólo `:app` puede ver.
 */
class CorreccionMuerteEntreGuardarYReencolarTest : RoomTestBase() {

    private val clock = FakeClock.at("2026-09-20T12:00:00Z")

    private fun freeSale(saleId: String = SALE_ID) = LocalSaleEntity(
        LOCAL_SALE_ID = saleId,
        NOMBRE_CLIENTE = "Guadalupe Hernandez Solis",
        FECHA_VENTA = "2026-09-19T10:00:00Z",
        LATITUD = 19.298455,
        LONGITUD = -99.167684,
        DIRECCION = "Andador Jazmin 12",
        PARCIALIDAD = 700.0,
        ENGANCHE = 300.0,
        TELEFONO = "5587654321",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = "Marco Antonio Hernandez",
        NOTA = null,
        DIA_COBRANZA = "VIERNES",
        PRECIO_TOTAL = 4200.0,
        TIEMPO_A_CORTO_PLAZOMESES = 6,
        MONTO_A_CORTO_PLAZO = 3900.0,
        MONTO_DE_CONTADO = 3600.0,
        ENVIADO = false
    )

    /** Adapter real de producción, montado a mano (mismo tipo que `WorkManagerReencolarSubidaAdapter`
     * proveería, pero sin WorkManager real en un unit test) — SIEMPRE lanza, como pide el escenario. */
    private object ReencolarQueSiempreLanza : ReencolarSubidaPort {
        override fun cancelarTrabajoEncolado(saleId: String) = Unit
        override fun reencolar(saleId: String, userEmail: String) {
            throw IllegalStateException("proceso muerto justo despues del commit, simulado")
        }
    }

    private class RecordingEnqueuer : LocalSalesWorkEnqueuer {
        val calls = mutableListOf<String>()
        override fun enqueue(localSaleId: String, userEmail: String) {
            calls += localSaleId
        }
    }

    @Test
    fun `guardar con reencolado que lanza persiste la correccion, y el barrido real la encuentra despues`() =
        runTest {
            db.localSaleDao().insertSale(freeSale())

            val port = RoomVentaLocalCorreccionAdapter(
                db,
                db.localSaleDao(),
                db.localSaleProduct(),
                db.localSaleComboDao()
            )
            val reloj = AppClockRelojPort(clock)
            val reclamar = ReclamarCorreccion(port, reloj, ReencolarQueSiempreLanza)
            val guardar = GuardarCorreccion(port, reloj, ReencolarQueSiempreLanza)

            val reclamo = reclamar(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)

            // GuardarCorreccion no debe propagar lo que lanza el reencolado: el commit ya cerró.
            guardar(
                SALE_ID,
                reclamo.claimId,
                reclamo.venta.campos.copy(nombreCliente = "Corregida antes de que la app muera"),
                emptyList(),
                emptyList(),
                "cobrador.pruebas@muebleriamsp.mx"
            )

            val saleTrasGuardar = db.localSaleDao().getSaleById(SALE_ID)
            assertEquals("Corregida antes de que la app muera", saleTrasGuardar?.NOMBRE_CLIENTE)
            assertEquals(1, saleTrasGuardar?.REVISION)

            // "La app muere": nadie reencoló de verdad. Se reabre la sesión y corre el barrido REAL.
            val enqueuer = RecordingEnqueuer()
            val synchronizer = LocalSalesPendingSynchronizer(
                fetchPending = {
                    db.localSaleDao().getUploadableSales(
                        now = clock.now().toEpochMilli(),
                        editLeaseMs = LocalSaleClaimLeases.EDIT_LEASE_MS,
                        uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS
                    )
                },
                enqueuer = enqueuer
            )

            val resultado = synchronizer.sync(
                SyncContext(userId = "u1", userEmail = "cobrador.pruebas@muebleriamsp.mx")
            )

            assertEquals(SyncResult.Enqueued(itemCount = 1, workRequestCount = 1), resultado)
            assertTrue(
                "la venta corregida debe aparecer en el barrido",
                enqueuer.calls.contains(SALE_ID)
            )
        }
}
