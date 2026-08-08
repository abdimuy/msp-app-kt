package com.example.msp_app.core.database.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val DB_FILE_NAME = "payment-survival-test.db"

/**
 * LA prueba crux de money-safety de este plan (spec Plan 2 Task 4, master
 * Checklist "Room safety"): un pago capturado en campo que TODAVÍA no llegó
 * al servidor (`GUARDADO_EN_MICROSIP = false`) NUNCA debe perderse ni
 * mutarse al reabrir la base.
 *
 * Qué prueba exactamente: PERSISTENCIA a través de cerrar/reabrir la base
 * REAL de producción (misma config que [AppDatabase.getInstance] — vía
 * [AppDatabase.buildDatabase], única fuente de verdad compartida por ambos,
 * ver ese companion object) sobre un archivo en disco (no in-memory: se
 * borra al cerrar, lo que ocultaría justo el bug que este test debe
 * atrapar). Simula el ciclo real de un dispositivo: capturar pagos sin
 * subir → cerrar la app → reabrir la app. Como el archivo se crea desde
 * cero, Room lo abre directo en v27 — este test NO ejercita la cadena de
 * migraciones 20→27 (eso lo cubre [MigrationSmokeTest], que sí corre los 7
 * `Migration` reales en secuencia).
 *
 * Qué rompería si este test fallara: cualquier cambio a `AppDatabase`
 * (incluido el hoist de este plan a `:core:database`), a la configuración
 * del builder, o a una migración futura, que borre/trunque/muté una fila de
 * `Payment` con `GUARDADO_EN_MICROSIP = 0` entre el cierre y la reapertura —
 * el peor bug posible en este dominio: dinero cobrado en la calle que
 * desaparece de la base local antes de llegar a Microsip.
 */
class PaymentSurvivalMigrationTest : RobolectricTestBase() {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File(context.cacheDir, DB_FILE_NAME)
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    /**
     * Abre la base por la MISMA función de configuración que usa producción
     * ([AppDatabase.buildDatabase], invocada también desde
     * [AppDatabase.getInstance]) — cero duplicación de la lista de
     * migraciones/flags a mano en el test.
     */
    private fun openProductionDatabase(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppDatabase.buildDatabase(context, dbFile.path)
            .allowMainThreadQueries()
            .build()
    }

    private fun unuploadedPayment(
        id: String,
        nombreCliente: String,
        importe: Double,
        cobradorId: Int,
        pagoRecibidoId: String?
    ) = PaymentEntity(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = 91027,
        FECHA_HORA_PAGO = "2026-08-07T16:45:00Z",
        GUARDADO_EN_MICROSIP = false,
        IMPORTE = importe,
        LAT = 19.043415,
        LNG = -98.198234,
        CLIENTE_ID = 30144,
        COBRADOR_ID = cobradorId,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = nombreCliente,
        PAGO_RECIBIDO_ID = pagoRecibidoId
    )

    @Test
    fun `pagos no subidos sobreviven cerrar y reabrir la base por la ruta de produccion`() =
        runTest {
            val pending = listOf(
                unuploadedPayment(
                    "uuid-pago-001",
                    "Maria Lopez Hernandez",
                    350.0,
                    7,
                    pagoRecibidoId = null
                ),
                unuploadedPayment(
                    "uuid-pago-002",
                    "Guadalupe Ramirez Torres",
                    480.50,
                    7,
                    pagoRecibidoId = null
                ),
                unuploadedPayment(
                    "uuid-pago-003",
                    "Rosa Elena Martinez Vazquez",
                    620.75,
                    12,
                    pagoRecibidoId = "uuid-captura-original-003"
                )
            )

            val firstOpen = openProductionDatabase()
            try {
                firstOpen.paymentDao().saveAll(pending)
            } finally {
                firstOpen.close()
            }

            assertTrue("el archivo de la base debe persistir tras el close", dbFile.exists())

            val reopened = openProductionDatabase()
            try {
                val survivors = reopened.paymentDao().getPendingPayments()

                assertEquals(
                    "debe sobrevivir EXACTAMENTE el mismo numero de pagos no subidos, cero perdida",
                    pending.size,
                    survivors.size
                )

                pending.forEach { original ->
                    val survivor = survivors.find { it.ID == original.ID }
                    assertNotNull(
                        "el pago ${original.ID} debe seguir presente tras reabrir",
                        survivor
                    )
                    assertEquals(original.IMPORTE, survivor!!.IMPORTE, 0.0)
                    assertEquals(false, survivor.GUARDADO_EN_MICROSIP)
                    assertEquals(original.NOMBRE_CLIENTE, survivor.NOMBRE_CLIENTE)
                    assertEquals(original.COBRADOR_ID, survivor.COBRADOR_ID)
                    assertEquals(original.DOCTO_CC_ACR_ID, survivor.DOCTO_CC_ACR_ID)

                    // getPendingPayments() no proyecta PAGO_RECIBIDO_ID (ver PaymentDao):
                    // se verifica a nivel de fila cruda para probar que ninguna columna,
                    // incluida esa, se pierde o muta al reabrir.
                    assertPagoRecibidoIdSurvived(reopened, original)
                }
            } finally {
                reopened.close()
            }
        }

    private fun assertPagoRecibidoIdSurvived(database: AppDatabase, original: PaymentEntity) {
        database.openHelper.readableDatabase.query(
            "SELECT PAGO_RECIBIDO_ID FROM Payment WHERE ID = ?",
            arrayOf(original.ID)
        ).use { cursor ->
            assertTrue("debe existir la fila cruda para ${original.ID}", cursor.moveToFirst())
            val columnIndex = cursor.getColumnIndexOrThrow("PAGO_RECIBIDO_ID")
            val storedValue = if (cursor.isNull(
                    columnIndex
                )
            ) {
                null
            } else {
                cursor.getString(columnIndex)
            }
            assertEquals(original.PAGO_RECIBIDO_ID, storedValue)
        }
    }
}
