package com.example.msp_app.data.visitas

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitsWorkEnqueuer
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * E-APP-078, cuarta prueba de la compuerta: **sin cancelación, lo que se
 * escribe en `Visit` y `sales` no cambia.** El caso de uso real sobre el
 * adaptador real y Room en memoria; las filas completas (todas las columnas)
 * se comparan contra la foto tomada con el código ANTERIOR al cambio (base
 * 3b8dea08), así que cualquier diferencia en el SQL, el contenido o el orden
 * de lo escrito pone esta prueba roja.
 */
class GuardadoDeVisitaFilasIdenticasTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-09-01T15:52:00Z")
    private val hoy = LocalDate.of(2026, 9, 1)
    private val clock = FakeClock(ahora)
    private val telemetry = RecordingTelemetry(clock)

    private val ubicacion = object : UbicacionPort {
        override suspend fun ubicacionActual() = UbicacionDeLaVisita(18.4617, -97.3928)
    }

    private fun casoDeUso() = RegistrarVisita(
        RegistroDeVisitaAdapter(
            db = db,
            saleDao = db.saleDao(),
            visitas = VisitsLocalDataSource(db.visitDao(), db.saleDao(), SinEncolar, clock),
            recomendaciones = db.visitRecommendationDao(),
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock,
            traerUsuario = { User(ID = "u-1", NOMBRE = "Efraín Domínguez", COBRADOR_ID = 7) }
        ),
        ubicacion,
        telemetry
    )

    @Before
    fun setUp() = runTest {
        db.saleDao().insertAll(listOf(venta(VENTA_A), venta(VENTA_B)))
    }

    private suspend fun guardar(semilla: String, captura: CapturaDeVisita, ventaId: Int?) =
        casoDeUso().invoke(
            visitaId = semilla,
            clienteId = CLIENTE_ID,
            ventaId = ventaId,
            captura = captura,
            hoy = hoy
        )

    private fun filas(sql: String): String {
        val c = db.openHelper.readableDatabase.query(sql)
        return buildString {
            c.use {
                while (it.moveToNext()) {
                    (0 until it.columnCount).joinTo(this, "|") { i ->
                        "${it.getColumnName(i)}=${if (it.isNull(i)) "null" else it.getString(i)}"
                    }
                    append('\n')
                }
            }
        }
    }

    /**
     * La foto es de v35. Las columnas que v36 agregó a `Visit` (las marcas del
     * compromiso, E-APP-080) no están en ella: se comparan las de v35 y se
     * exige aparte que el guardado deje las nuevas en nulo.
     */
    private fun foto() =
        filas("SELECT ${columnasDeVisitEnLaFoto()} FROM Visit ORDER BY ID") + "--\n" +
            filas("SELECT * FROM sales ORDER BY DOCTO_CC_ACR_ID")

    private fun columnasDeVisitEnLaFoto(): String =
        db.openHelper.readableDatabase.query("PRAGMA table_info(Visit)").use { c ->
            buildList {
                while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name")))
            }
        }.filterNot { it in COLUMNAS_POSTERIORES_A_LA_FOTO }.joinToString()

    private fun columnasPosterioresNoNulas(): Int = db.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM Visit WHERE " +
            COLUMNAS_POSTERIORES_A_LA_FOTO.joinToString(" OR ") { "$it IS NOT NULL" }
    ).use { c ->
        c.moveToFirst()
        c.getInt(0)
    }

    @Test
    fun `E-APP-078 sin cancelacion las filas de Visit y sales son identicas a las de la base`() =
        runTest {
            // Alcance CLIENTE (propaga ESTADO_COBRANZA por CLIENTE_ID).
            guardar(
                "visita-no-estaba",
                CapturaDeVisita(
                    resultado = ResultadoDeVisita.NO_ESTABA,
                    etiqueta = TipoVisitaCatalogo.NO_SE_ENCONTRABA,
                    cuentas = setOf(VENTA_A)
                ),
                ventaId = VENTA_A
            )
            // Alcance VENTA con dos cuentas (dos filas, updateTotal por cuenta).
            guardar(
                "visita-se-nego",
                CapturaDeVisita(
                    resultado = ResultadoDeVisita.SE_NEGO,
                    etiqueta = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
                    nota = "dice que hasta que le arreglen el refri",
                    cuentas = setOf(VENTA_A, VENTA_B)
                ),
                ventaId = null
            )
            // Promesa: DIA_TEMPORAL_COBRANZA y las columnas estructuradas.
            guardar(
                "visita-prometio",
                CapturaDeVisita(
                    resultado = ResultadoDeVisita.PROMETIO,
                    etiqueta = TipoVisitaCatalogo.PIDE_REAGENDAR,
                    nota = "el viernes que cobre mi esposo",
                    cuentas = setOf(VENTA_B),
                    fechaPromesa = hoy.plusDays(3),
                    montoPrometido = Money.of(BigDecimal("220"))
                ),
                ventaId = VENTA_B
            )

            val base = requireNotNull(javaClass.getResource(FOTO_DE_LA_BASE)) { FOTO_DE_LA_BASE }
                .readText(Charsets.UTF_8)
            assertEquals(base, foto())
            assertEquals(
                "el guardado no escribe las marcas del compromiso",
                0,
                columnasPosterioresNoNulas()
            )
        }

    private object SinEncolar : VisitsWorkEnqueuer {
        override fun enqueue(visitId: String) = Unit
    }

    private fun venta(ventaId: Int) = SaleEntity(
        DOCTO_CC_ACR_ID = ventaId,
        DOCTO_CC_ID = ventaId + 1,
        FOLIO = "V-$ventaId",
        CLIENTE_ID = CLIENTE_ID,
        APLICADO = "S",
        COBRADOR_ID = 7,
        CLIENTE = "Victoria Flores Olmedo",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Centro",
        IMPORTE_PAGO_PROMEDIO = 220.0,
        TOTAL_IMPORTE = 6310.0,
        NUM_IMPORTES = 20,
        FECHA = "2026-04-14T00:00:00Z",
        PARCIALIDAD = 220,
        ENGANCHE = 900.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "J. Carlos Méndez",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6310.0,
        IMPTE_REST = 1450.0,
        SALDO_REST = 1450.0,
        FECHA_ULT_PAGO = null,
        CALLE = "C. Hidalgo 214",
        CIUDAD = "Tehuacán",
        ESTADO = "Puebla",
        TELEFONO = "2381627597",
        NOMBRE_COBRADOR = "Efraín Domínguez Reyes",
        ESTADO_COBRANZA = "PENDIENTE",
        DIA_COBRANZA = "MARTES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 5200.0,
        AVAL_O_RESPONSABLE = "Rosa María Ramírez",
        FREC_PAGO = "SEMANAL"
    )

    private companion object {
        const val CLIENTE_ID = 5021
        const val VENTA_A = 77021
        const val VENTA_B = 77188

        /**
         * Tomada corriendo ESTA prueba con el código de la base 3b8dea08 (antes
         * de E-APP-078); el archivo es la salida tal cual, sin editar.
         */
        const val FOTO_DE_LA_BASE = "/e_app_078/filas-base-3b8dea08.txt"

        /** Agregadas por la 35→36, después de tomar la foto. */
        val COLUMNAS_POSTERIORES_A_LA_FOTO =
            listOf("COMPROMISO_CONFIRMADO_EN", "COMPROMISO_RESGUARDADO_EN")
    }
}
