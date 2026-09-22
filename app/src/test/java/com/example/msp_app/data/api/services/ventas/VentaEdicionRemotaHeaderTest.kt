package com.example.msp_app.data.api.services.ventas

import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de los dos cuerpos de corrección remota del header y del cliente
 * contra el Gson real, no contra los constructores de Kotlin.
 *
 * Se prueba el JSON emitido —no los objetos— porque los tres riesgos concretos
 * sólo son visibles ahí: el separador decimal que pone el locale, los campos
 * que el servidor descarta en silencio (`montos`, `zona_cliente`,
 * `referencia`) y la regla de "exactamente uno" de `dia_cobranza`.
 */
class VentaEdicionRemotaHeaderTest {

    private companion object {
        val COMA_ENTRE_DIGITOS = Regex("""\d,\d""")
    }

    private val gson = Gson()

    private fun jsonDe(cuerpo: Any): JsonObject =
        JsonParser.parseString(gson.toJson(cuerpo)).asJsonObject

    // ─── Montos como String, sin coma decimal ───────────────────────────────

    @Test
    fun `el plan de credito emite montos con punto decimal, no coma`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            tipoVenta = "CREDITO",
            parcialidad = 450.0,
            enganche = 1200.5
        )

        val plan = construirActualizarHeaderRequest(venta).planCredito!!

        assertEquals("450.00", plan.parcialidad)
        assertEquals("1200.50", plan.enganche)
    }

    @Test
    fun `el locale del telefono no puede meter una coma decimal`() {
        // El teléfono de campo está en es-MX: String.format pondría "450,00" y
        // el API responde 422. toImporteRemoto usa BigDecimal.toPlainString,
        // que es independiente del locale — esta prueba lo fija.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("es", "MX"))
            val venta = TestDataFactory.createLocalSaleEntity(
                tipoVenta = "CREDITO",
                parcialidad = 450.0,
                enganche = 0.0
            )

            val json = jsonDe(construirActualizarHeaderRequest(venta))
            val plan = json.getAsJsonObject("plan_credito")

            assertEquals("450.00", plan.get("parcialidad").asString)
            assertEquals("0.00", plan.get("enganche").asString)
            // Ningún valor del cuerpo puede traer una coma entre dígitos:
            // sería un decimal o un separador de miles emitido por el locale.
            assertFalse(COMA_ENTRE_DIGITOS.containsMatchIn(json.toString()))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `enganche nulo en Room viaja como cero, no se omite`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            tipoVenta = "CREDITO",
            enganche = null
        )

        assertEquals("0.00", construirActualizarHeaderRequest(venta).planCredito!!.enganche)
    }

    // ─── dia_cobranza: exactamente uno ──────────────────────────────────────

    @Test
    fun `dia_cobranza manda semana y omite mes`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            tipoVenta = "CREDITO",
            diaCobranza = "MIERCOLES"
        )

        val dia = jsonDe(construirActualizarHeaderRequest(venta)).getAsJsonObject("dia_cobranza")

        assertTrue(dia.has("semana"))
        assertFalse("mandar semana y mes a la vez es 422", dia.has("mes"))
        assertEquals("MIERCOLES", dia.get("semana").asString)
        assertEquals(1, dia.entrySet().size)
    }

    @Test
    fun `el dia de cobranza viaja en mayusculas y sin acentos`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            tipoVenta = "CREDITO",
            diaCobranza = "Miércoles",
            frecPago = "quincenal"
        )

        val header = construirActualizarHeaderRequest(venta)

        assertEquals("MIERCOLES", header.diaCobranza!!.semana)
        assertEquals("QUINCENAL", header.planCredito!!.frecPago)
    }

    @Test
    fun `una venta de contado no manda plan ni dia de cobranza`() {
        val venta = TestDataFactory.createLocalSaleEntity(tipoVenta = "CONTADO")

        val json = jsonDe(construirActualizarHeaderRequest(venta))

        assertNull(construirActualizarHeaderRequest(venta).planCredito)
        assertFalse(json.has("plan_credito"))
        assertFalse(json.has("dia_cobranza"))
    }

    // ─── Campos que el servidor descarta ────────────────────────────────────

    @Test
    fun `el header no manda montos ni zona_cliente`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            tipoVenta = "CREDITO",
            zonaCliente = "Zona Norte",
            zonaClienteId = 7
        )

        val json = jsonDe(construirActualizarHeaderRequest(venta))

        // `montos` lo ignora el servidor (se derivan de las líneas) y
        // `zona_cliente` es de sólo lectura: ninguno debe aparecer en el cable.
        assertFalse("el servidor ignora montos: no se manda", json.has("montos"))
        assertFalse(json.getAsJsonObject("direccion").has("zona_cliente"))
        assertFalse(json.toString().contains("Zona Norte"))
        assertEquals(7, json.getAsJsonObject("direccion").get("zona_cliente_id").asInt)
    }

    @Test
    fun `el cliente no manda referencia`() {
        val venta = TestDataFactory.createLocalSaleEntity()

        val json = jsonDe(construirActualizarClienteRequest(venta))

        // El handler la acepta y la descarta en silencio.
        assertFalse(json.getAsJsonObject("cliente").has("referencia"))
    }

    // ─── Mapeo columna por columna ──────────────────────────────────────────

    @Test
    fun `la direccion, el GPS y la fecha salen de sus columnas de Room`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            direccion = "Av. Hidalgo",
            numero = "44-B",
            colonia = "San Juan",
            poblacion = "Tepic",
            ciudad = "Tepic",
            latitude = 21.5041,
            longitude = -104.8942,
            fechaVenta = "2026-09-20T15:04:05Z"
        )

        val header = construirActualizarHeaderRequest(venta)

        assertEquals("Av. Hidalgo", header.direccion.calle)
        assertEquals("44-B", header.direccion.numeroExterior)
        assertEquals("San Juan", header.direccion.colonia)
        assertEquals("Tepic", header.direccion.poblacion)
        assertEquals("Tepic", header.direccion.ciudad)
        assertEquals(21.5041, header.gps.latitud, 0.0)
        assertEquals(-104.8942, header.gps.longitud, 0.0)
        assertEquals("2026-09-20T15:04:05Z", header.fechaVenta)
    }

    @Test
    fun `las columnas de direccion nulas viajan como cadena vacia, igual que el alta`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            colonia = null,
            poblacion = null,
            ciudad = null,
            numero = null
        )

        val header = construirActualizarHeaderRequest(venta)
        val json = jsonDe(header)

        assertEquals("", header.direccion.colonia)
        assertEquals("", header.direccion.poblacion)
        assertEquals("", header.direccion.ciudad)
        // numero_exterior sí es opcional en el servidor: se omite.
        assertFalse(json.getAsJsonObject("direccion").has("numero_exterior"))
    }

    @Test
    fun `la nota en blanco se omite`() {
        val conNota = TestDataFactory.createLocalSaleEntity(nota = "Entregar por la tarde")
        val sinNota = TestDataFactory.createLocalSaleEntity(nota = "   ")

        assertEquals("Entregar por la tarde", construirActualizarHeaderRequest(conNota).nota)
        assertFalse(jsonDe(construirActualizarHeaderRequest(sinNota)).has("nota"))
    }

    @Test
    fun `el cliente sale de sus columnas y el telefono se normaliza a E164`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            clientName = "Marisol Delgado Ríos",
            telefono = "3111234567",
            avalOResponsable = "Hugo Bañuelos",
            clienteId = 48213
        )

        val cliente = construirActualizarClienteRequest(venta).cliente

        assertEquals("Marisol Delgado Ríos", cliente.nombre)
        assertEquals("+523111234567", cliente.telefono)
        assertEquals("Hugo Bañuelos", cliente.aval)
        assertEquals(48213, cliente.clienteId)
    }

    @Test
    fun `un telefono invalido viaja como ausente, no como basura`() {
        // Asimetría deliberada del alta: el servidor acepta "sin teléfono" y
        // rechaza "teléfono inválido" — un número malo en Room no puede tumbar
        // la corrección entera.
        val venta = TestDataFactory.createLocalSaleEntity(telefono = "000000")

        val json = jsonDe(construirActualizarClienteRequest(venta))

        assertNull(construirActualizarClienteRequest(venta).cliente.telefono)
        assertFalse(json.getAsJsonObject("cliente").has("telefono"))
    }

    @Test
    fun `el aval en blanco y el cliente_id nulo se omiten`() {
        val venta = TestDataFactory.createLocalSaleEntity(
            avalOResponsable = "  ",
            clienteId = null
        )

        val cliente = jsonDe(construirActualizarClienteRequest(venta)).getAsJsonObject("cliente")

        assertFalse(cliente.has("aval"))
        assertFalse(cliente.has("cliente_id"))
        assertTrue(cliente.has("nombre"))
    }
}
