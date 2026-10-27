package com.example.msp_app.data.api.services.cobranza

import com.example.msp_app.data.models.sale.toDomain
import com.example.msp_app.data.models.sale.toEntity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariante de `sales`: la PK (`DOCTO_CC_ACR_ID`) es igual al cargo (`DOCTO_CC_ID`)
 * en toda fila que se escribe (E-APP-104, compuerta DESPUÉS de T17).
 *
 * `AgendaLocal` casa las cuentas por la PK (`SaleIdSpaces.forSaleRow`) y los pagos por
 * el cargo (`forSalePayments`). La compuerta midió que, si un escritor los separa, el
 * pago desaparece para la agenda y una promesa pagada sale INCUMPLIDA, y que ninguna
 * prueba lo detectaba. Esta prueba lo fija en los dos escritores que hay hoy y se pone
 * roja si aparece un tercer constructor de `SaleEntity` o un UPDATE que mueva esas
 * columnas: ahí hay que decidir de nuevo, no heredar el supuesto.
 */
class SalesPkIgualACargoTest {

    @Test
    fun `VentaDto toEntity deja la PK igual al cargo`() {
        for (cargo in listOf(1, 14_998_985, 15_416_710)) {
            val fila = venta(cargo).toEntity()
            assertEquals(cargo, fila.DOCTO_CC_ID)
            assertEquals(cargo, fila.DOCTO_CC_ACR_ID)
        }
    }

    @Test
    fun `la ida y vuelta por el dominio conserva PK y cargo`() {
        val fila = venta(14_998_985).toEntity()
        val vuelta = fila.toDomain().toEntity()
        assertEquals(fila.DOCTO_CC_ACR_ID, vuelta.DOCTO_CC_ACR_ID)
        assertEquals(fila.DOCTO_CC_ID, vuelta.DOCTO_CC_ID)
        assertEquals(vuelta.DOCTO_CC_ID, vuelta.DOCTO_CC_ACR_ID)
    }

    @Test
    fun `los unicos constructores de SaleEntity en produccion son los dos conocidos`() {
        val constructores = fuentesDeProduccion()
            .filter { (_, texto) -> CONSTRUCTOR.containsMatchIn(texto) }
            .keys
            .map { it.substringAfterLast('/') }
            .toSet()
        // Control positivo: el barrido sí encuentra el constructor de VentaDto.
        assertTrue("el barrido no vio VentaDto.kt", "VentaDto.kt" in constructores)
        assertEquals(
            "Un escritor nuevo de `sales` debe dejar DOCTO_CC_ACR_ID = DOCTO_CC_ID " +
                "(o AgendaLocal debe casar cuentas y pagos con el mismo id). Agrégalo aquí " +
                "con su prueba de comportamiento.",
            setOf("VentaDto.kt", "SaleMappers.kt"),
            constructores
        )
    }

    @Test
    fun `ningun UPDATE de sales mueve la PK ni el cargo`() {
        val sets = fuentesDeProduccion().values.flatMap { texto ->
            UPDATE_SALES.findAll(texto).map { it.groupValues[1] }.toList()
        }
        // Control positivo: el barrido sí ve los SET de sales (SALDO_REST, ESTADO_COBRANZA…).
        assertTrue("el barrido no vio ningún UPDATE sales", sets.any { "SALDO_REST" in it })
        val mueven = sets.filter { COLUMNA_DE_ID.containsMatchIn(it) }
        assertEquals(emptyList<String>(), mueven)
    }

    private fun venta(cargo: Int) = VentaDto(
        docto_cc_id = cargo,
        docto_pv_id = null,
        cliente_id = 4821,
        zona_cliente_id = 21,
        folio = "Q00002413",
        fecha_cargo = "2025-09-26T00:00:00Z",
        fecha_venta = null,
        precio_total = "12000.00",
        total_importe = "1000.00",
        impte_rest = "0.00",
        saldo = "11000.00",
        num_pagos = 10,
        fecha_ult_pago = null,
        cargo_cancelado = false,
        updated_at = "2026-09-30T08:36:18.000000Z",
        cliente_nombre = "Rocio Aguilar Medina",
        limite_credito = null,
        cliente_notas = "",
        cobrador_id = null,
        nombre_cobrador = "Rosa Elena Martinez Vazquez",
        zona_nombre = "Centro",
        calle = "Av. Reforma 100",
        ciudad = "Tehuacan",
        estado = "Puebla",
        telefono = "",
        parcialidad = 250,
        enganche = "500.00",
        tiempo_corto_plazo_meses = null,
        monto_corto_plazo = null,
        precio_de_contado = null,
        aval_o_responsable = "",
        vendedor_1 = "",
        vendedor_2 = "",
        vendedor_3 = "",
        frec_pago = "SEMANAL"
    )

    private fun fuentesDeProduccion(): Map<String, String> {
        val raiz = raizDelRepo()
        val modulos = INCLUDE.findAll(File(raiz, "settings.gradle.kts").readText())
            .map { it.groupValues[1].trim(':').replace(':', '/') }
            .toList()
        check(modulos.isNotEmpty()) { "no se leyó ningún include(...) de settings.gradle.kts" }
        return modulos.flatMap { modulo ->
            val srcMain = File(raiz, "$modulo/src/main")
            if (!srcMain.isDirectory) {
                emptyList()
            } else {
                srcMain.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .map { it.relativeTo(raiz).invariantSeparatorsPath to it.readText() }
                    .toList()
            }
        }.toMap()
    }

    private fun raizDelRepo(): File {
        var actual: File? = File(".").absoluteFile
        while (actual != null) {
            if (File(actual, "settings.gradle.kts").isFile) return actual
            actual = actual.parentFile
        }
        error("no se encontró settings.gradle.kts")
    }

    private companion object {
        val INCLUDE = Regex("""include\("(:[^"]+)"\)""")

        /** `SaleEntity(` como llamada, no la declaración ni `LocalSaleEntity(` / `SaleWithProductsEntity(`. */
        val CONSTRUCTOR = Regex("""(?<![A-Za-z_])(?<!class )SaleEntity\(""")

        /** El SET de un `UPDATE sales … WHERE`, en el texto de la consulta. */
        val UPDATE_SALES = Regex("""UPDATE\s+sales\s+SET([\s\S]*?)WHERE""", RegexOption.IGNORE_CASE)

        val COLUMNA_DE_ID = Regex("""\bDOCTO_CC_(ACR_)?ID\s*=""")
    }
}
