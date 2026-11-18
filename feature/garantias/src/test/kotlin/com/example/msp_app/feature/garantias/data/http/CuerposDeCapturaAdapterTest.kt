package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.garantias.domain.model.AperturaDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ArticuloNuevo
import com.example.msp_app.feature.garantias.domain.model.DomicilioDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeCuenta
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Los cuatro cuerpos de la captura, con los nombres de campo del API.
 *
 * ## Qué protege
 *
 * Un folio de piso no puede llevar una sola llave de cliente ni de domicilio
 * (`warranty_customer_not_allowed` es 422); uno de cliente los lleva todos. El
 * adaptador escribe cada campo opcional **sólo** cuando tiene valor, y la prueba
 * lo fija llave por llave. La vigencia viaja como medianoche de la zona del
 * negocio, no como la hora del reloj.
 */
class CuerposDeCapturaAdapterTest {

    private val adapter = CuerposDeCapturaAdapter()
    private val ahora = Instant.parse("2026-11-13T10:00:00Z")

    @Test
    fun `una apertura de piso no lleva una sola llave de cliente ni de domicilio`() {
        val cuerpo = adapter.apertura(
            apertura = AperturaDeGarantia(
                origen = OrigenDeFolio.PISO,
                descripcion = "equipo en mostrador",
                primerArticulo = ArticuloNuevo(clave = "CLAVE-01", descripcion = "bocina")
            ),
            deviceCreatedAt = ahora
        )

        assertEquals(CuerpoDeGarantia.TIPO_JSON, cuerpo.tipoDeContenido)
        val json = json(cuerpo.texto)
        assertEquals("2026-11-13T10:00:00Z", json.get("device_created_at").asString)
        assertEquals("piso", json.get("origen").asString)
        assertEquals("equipo en mostrador", json.get("description").asString)

        for (llave in LLAVES_DE_CLIENTE) {
            assertFalse("piso no debe mandar $llave", json.has(llave))
        }
    }

    @Test
    fun `una apertura de cliente lleva cliente venta estado y domicilio`() {
        val cuerpo = adapter.apertura(
            apertura = AperturaDeGarantia(
                origen = OrigenDeFolio.CLIENTE,
                descripcion = "bocina rota",
                primerArticulo = ArticuloNuevo(clave = "CLAVE-01", descripcion = "bocina"),
                clienteId = 30144,
                ventaId = 91027,
                estadoCuenta = EstadoDeCuenta.SALDO_PENDIENTE,
                domicilio = DomicilioDeGarantia(
                    calle = "Av. Reforma",
                    numeroExterior = "123",
                    colonia = "Centro",
                    localidad = "Cuauhtemoc",
                    ciudad = "CDMX",
                    codigoPostal = "06000"
                ),
                vigenciaHasta = LocalDate.parse("2026-12-31")
            ),
            deviceCreatedAt = ahora
        )

        val json = json(cuerpo.texto)
        assertEquals("cliente", json.get("origen").asString)
        assertEquals(30144, json.get("cliente_id").asInt)
        assertEquals(91027, json.get("venta_id").asInt)
        assertEquals("saldo_pendiente", json.get("estado_cuenta").asString)
        assertEquals("Av. Reforma", json.get("calle").asString)
        assertEquals("123", json.get("numero_exterior").asString)
        assertEquals("Centro", json.get("colonia").asString)
        assertEquals("Cuauhtemoc", json.get("localidad").asString)
        assertEquals("CDMX", json.get("ciudad").asString)
        assertEquals("06000", json.get("codigo_postal").asString)
    }

    @Test
    fun `la vigencia viaja como medianoche de la zona del negocio`() {
        val cuerpo = adapter.apertura(
            apertura = AperturaDeGarantia(
                origen = OrigenDeFolio.CLIENTE,
                descripcion = "bocina rota",
                primerArticulo = ArticuloNuevo(clave = "CLAVE-01", descripcion = "bocina"),
                clienteId = 1,
                ventaId = 2,
                estadoCuenta = EstadoDeCuenta.LIQUIDADA,
                domicilio = DomicilioDeGarantia(calle = "x"),
                vigenciaHasta = LocalDate.parse("2026-12-31")
            ),
            deviceCreatedAt = ahora
        )

        val esperado = AppTime.toWireFormat(AppTime.startOfDay(LocalDate.parse("2026-12-31")))
        assertEquals(esperado, json(cuerpo.texto).get("vigencia_hasta").asString)
    }

    @Test
    fun `un domicilio parcial solo escribe las lineas capturadas`() {
        val cuerpo = adapter.apertura(
            apertura = AperturaDeGarantia(
                origen = OrigenDeFolio.CLIENTE,
                descripcion = "bocina rota",
                primerArticulo = ArticuloNuevo(clave = "CLAVE-01", descripcion = "bocina"),
                clienteId = 1,
                ventaId = 2,
                estadoCuenta = EstadoDeCuenta.LIQUIDADA,
                domicilio = DomicilioDeGarantia(calle = "Av. Reforma", ciudad = "CDMX")
            ),
            deviceCreatedAt = ahora
        )

        val json = json(cuerpo.texto)
        assertEquals("Av. Reforma", json.get("calle").asString)
        assertEquals("CDMX", json.get("ciudad").asString)
        for (llave in listOf("numero_exterior", "colonia", "localidad", "codigo_postal")) {
            assertFalse("sin valor no se escribe $llave", json.has(llave))
        }
    }

    @Test
    fun `un articulo sin id de microsip no manda articulo_id`() {
        val cuerpo = adapter.articulo(
            articulo = ArticuloNuevo(clave = "CLAVE-01", descripcion = "bocina"),
            deviceCreatedAt = ahora
        )

        val json = json(cuerpo.texto)
        assertEquals("CLAVE-01", json.get("clave").asString)
        assertEquals("bocina", json.get("description").asString)
        assertFalse(json.has("articulo_id"))
    }

    @Test
    fun `un articulo con id de microsip lo manda`() {
        val cuerpo = adapter.articulo(
            articulo = ArticuloNuevo(clave = "CLAVE-01", descripcion = "bocina", articuloId = 555),
            deviceCreatedAt = ahora
        )

        assertEquals(555, json(cuerpo.texto).get("articulo_id").asInt)
    }

    @Test
    fun `un avance sin gps no manda coordenadas`() {
        val cuerpo = adapter.avance(etapaDestino = "recolectado", deviceCreatedAt = ahora)

        val json = json(cuerpo.texto)
        assertEquals("recolectado", json.get("etapa_destino").asString)
        assertFalse(json.has("gps_lat"))
        assertFalse(json.has("gps_lon"))
    }

    @Test
    fun `un avance con gps manda las dos coordenadas`() {
        val cuerpo = adapter.avance(
            etapaDestino = "recolectado",
            deviceCreatedAt = ahora,
            ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332)
        )

        val json = json(cuerpo.texto)
        assertEquals(19.4326, json.get("gps_lat").asDouble, 1e-9)
        assertEquals(-99.1332, json.get("gps_lon").asDouble, 1e-9)
    }

    @Test
    fun `la entrega guarda los campos de texto y la ruta de la firma`() {
        val cuerpo = adapter.entrega(
            deviceCreatedAt = ahora,
            ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332),
            firma = FirmaDeConformidad(rutaArchivo = "firmas/f1.png", tamanoBytes = 2048)
        )

        assertEquals(CuerpoDeGarantia.TIPO_MULTIPART, cuerpo.tipoDeContenido)
        val datos = DatosDeEntrega.deJson(cuerpo.texto)!!
        assertEquals("2026-11-13T10:00:00Z", datos.deviceCreatedAt)
        assertEquals(19.4326, datos.latitud, 1e-9)
        assertEquals(-99.1332, datos.longitud, 1e-9)
        assertEquals("firmas/f1.png", datos.rutaDeLaFirma)
    }

    private fun json(texto: String): JsonObject = JsonParser.parseString(texto).asJsonObject

    private companion object {
        val LLAVES_DE_CLIENTE = listOf(
            "cliente_id",
            "venta_id",
            "estado_cuenta",
            "calle",
            "numero_exterior",
            "colonia",
            "localidad",
            "ciudad",
            "codigo_postal"
        )
    }
}
