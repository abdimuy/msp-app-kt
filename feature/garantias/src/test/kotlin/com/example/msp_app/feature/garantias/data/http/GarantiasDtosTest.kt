package com.example.msp_app.feature.garantias.data.http

import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La traducción de `GET /v2/garantias/{id}` al dominio.
 *
 * ## Qué protege
 *
 * Los DTO son tolerantes a propósito: una respuesta incompleta **no** debe
 * tumbar la pantalla de detalle. La prueba fija las tres defensas que lo hacen
 * posible —origen desconocido cae en `PISO`, domicilio en blanco no es
 * domicilio, fecha ilegible cae en `EPOCH`— y la única excepción legítima: un
 * detalle sin `garantia` es un contrato roto, no una respuesta vacía.
 */
class GarantiasDtosTest {

    @Test
    fun `el detalle completo se traduce al dominio`() {
        val detalle = detalleCompleto().toDomain()

        assertEquals("folio-servidor-1", detalle.id)
        assertEquals("G-0001", detalle.folio)
        assertEquals(OrigenDeFolio.CLIENTE, detalle.origen)
        assertEquals(30144, detalle.clienteId)
        assertEquals(91027, detalle.ventaId)
        assertEquals("saldo_pendiente", detalle.estadoCuenta)
        assertEquals("abierto", detalle.estado)
        assertEquals("bocina rota", detalle.descripcion)
        assertEquals(LocalDate.parse("2026-12-31"), detalle.vigenciaHasta)
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), detalle.creadoEn)
        assertEquals(Instant.parse("2026-11-13T10:30:00Z"), detalle.actualizadoEn)
        assertEquals(Instant.parse("2026-11-13T11:00:00Z"), detalle.cerradoEn)
        assertEquals("agente-1", detalle.abiertoPor)
    }

    @Test
    fun `el domicilio y el gps del domicilio viajan al dominio`() {
        val detalle = detalleCompleto().toDomain()

        assertNotNull(detalle.domicilio)
        val domicilio = detalle.domicilio!!
        assertEquals("Av. Reforma", domicilio.calle)
        assertEquals("123", domicilio.numeroExterior)
        assertEquals("Centro", domicilio.colonia)
        assertEquals("Cuauhtemoc", domicilio.localidad)
        assertEquals("CDMX", domicilio.ciudad)
        assertEquals("06000", domicilio.codigoPostal)

        assertEquals(19.4326, detalle.ubicacionDelDomicilio!!.latitud, 1e-9)
        assertEquals(-99.1332, detalle.ubicacionDelDomicilio!!.longitud, 1e-9)
    }

    @Test
    fun `el articulo se traduce con todos sus campos`() {
        val articulo = detalleCompleto().toDomain().articulos.single()

        assertEquals("articulo-servidor-1", articulo.id)
        assertEquals("original", articulo.rol)
        assertEquals("articulo-original-1", articulo.reemplazaA)
        assertEquals(555, articulo.articuloId)
        assertEquals("CLAVE-01", articulo.clave)
        assertEquals("bocina", articulo.descripcion)
        assertEquals("ruta-1", articulo.ruta)
        assertEquals("recolectado", articulo.etapa)
        assertEquals("taller", articulo.ubicacion)
        assertEquals("procede", articulo.dictamen)
        assertEquals("reemplazo", articulo.desenlace)
        assertEquals(Instant.parse("2026-11-13T11:00:00Z"), articulo.cerradoEn)
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), articulo.creadoEn)
        assertEquals(Instant.parse("2026-11-13T10:30:00Z"), articulo.actualizadoEn)
    }

    @Test
    fun `el evento se traduce con su ubicacion y sus imagenes`() {
        val evento = detalleCompleto().toDomain().eventos.single()

        assertEquals("evento-servidor-1", evento.id)
        assertEquals("articulo-servidor-1", evento.articuloRef)
        assertEquals("recoleccion", evento.tipo)
        assertEquals("recolectado", evento.descripcion)
        assertEquals("registrado", evento.etapaDesde)
        assertEquals("recolectado", evento.etapaHasta)
        assertEquals("agente-1", evento.usuario)
        assertEquals("cobrador", evento.rolDecisor)
        assertEquals(19.4326, evento.ubicacion!!.latitud, 1e-9)
        assertEquals(Instant.parse("2026-11-13T10:00:00Z"), evento.creadoEn)
        assertEquals(Instant.parse("2026-11-13T09:59:00Z"), evento.deviceCreatedAt)
        assertEquals("clave-idempotencia-1", evento.claveIdempotencia)
        assertEquals(120.5, evento.distanciaEntregaM!!, 1e-9)
        assertEquals("gps-ok", evento.auditoriaGps)

        val imagen = evento.imagenes.single()
        assertEquals("imagen-1", imagen.id)
        assertEquals("recoleccion", imagen.tipo)
        assertEquals("foto de la bocina", imagen.descripcion)
        assertEquals("agente-1", imagen.subidaPor)
        assertEquals(Instant.parse("2026-11-13T10:05:00Z"), imagen.creadaEn)
        assertEquals("/v2/garantias/1/imagenes/1", imagen.url)
    }

    @Test
    fun `un origen desconocido cae en piso`() {
        val detalle = DetalleDeGarantiaDto(
            garantia = GarantiaDto(origen = "bodega")
        ).toDomain()

        assertEquals(OrigenDeFolio.PISO, detalle.origen)
    }

    @Test
    fun `un origen piso se conserva`() {
        val detalle = DetalleDeGarantiaDto(
            garantia = GarantiaDto(origen = "piso")
        ).toDomain()

        assertEquals(OrigenDeFolio.PISO, detalle.origen)
    }

    @Test
    fun `un domicilio en blanco no es domicilio`() {
        val detalle = DetalleDeGarantiaDto(
            garantia = GarantiaDto(calle = "", ciudad = "   ")
        ).toDomain()

        assertNull(detalle.domicilio)
    }

    @Test
    fun `un detalle sin folio es un contrato roto`() {
        val dto = DetalleDeGarantiaDto(garantia = null)

        assertThrows(IllegalArgumentException::class.java) { dto.toDomain() }
    }

    @Test
    fun `las fechas ausentes o ilegibles caen en epoch`() {
        val detalle = DetalleDeGarantiaDto(
            garantia = GarantiaDto(
                creadoEn = "no-es-una-fecha",
                actualizadoEn = null,
                cerradoEn = "tampoco",
                articulos = listOf(
                    ArticuloDto(creadoEn = "mal", actualizadoEn = null, cerradoEn = "mal")
                )
            ),
            eventos = listOf(
                EventoDto(creadoEn = null, deviceCreatedAt = "mal", imagenes = listOf(ImagenDto()))
            )
        ).toDomain()

        assertEquals(Instant.EPOCH, detalle.creadoEn)
        assertEquals(Instant.EPOCH, detalle.actualizadoEn)
        assertNull(detalle.cerradoEn)
        assertEquals(Instant.EPOCH, detalle.articulos.single().creadoEn)
        assertNull(detalle.articulos.single().cerradoEn)
        assertEquals(Instant.EPOCH, detalle.eventos.single().deviceCreatedAt)
        assertEquals(Instant.EPOCH, detalle.eventos.single().imagenes.single().creadaEn)
    }

    @Test
    fun `los articulos y eventos ausentes son listas vacias`() {
        val detalle = DetalleDeGarantiaDto(
            garantia = GarantiaDto(articulos = null),
            eventos = null
        ).toDomain()

        assertTrue(detalle.articulos.isEmpty())
        assertTrue(detalle.eventos.isEmpty())
    }

    private fun detalleCompleto(): DetalleDeGarantiaDto = DetalleDeGarantiaDto(
        garantia = GarantiaDto(
            id = "folio-servidor-1",
            folio = "G-0001",
            origen = "cliente",
            clienteId = 30144,
            ventaId = 91027,
            estadoCuenta = "saldo_pendiente",
            estado = "abierto",
            descripcion = "bocina rota",
            vigenciaHasta = "2026-12-31",
            calle = "Av. Reforma",
            numeroExterior = "123",
            colonia = "Centro",
            localidad = "Cuauhtemoc",
            ciudad = "CDMX",
            codigoPostal = "06000",
            gpsLat = 19.4326,
            gpsLon = -99.1332,
            abiertoPor = "agente-1",
            cerradoEn = "2026-11-13T11:00:00Z",
            creadoEn = "2026-11-13T10:00:00Z",
            actualizadoEn = "2026-11-13T10:30:00Z",
            articulos = listOf(articuloCompleto())
        ),
        eventos = listOf(eventoCompleto())
    )

    private fun articuloCompleto(): ArticuloDto = ArticuloDto(
        id = "articulo-servidor-1",
        garantiaId = "folio-servidor-1",
        rol = "original",
        reemplazaA = "articulo-original-1",
        articuloId = 555,
        clave = "CLAVE-01",
        descripcion = "bocina",
        ruta = "ruta-1",
        etapa = "recolectado",
        ubicacion = "taller",
        dictamen = "procede",
        desenlace = "reemplazo",
        cerradoEn = "2026-11-13T11:00:00Z",
        creadoEn = "2026-11-13T10:00:00Z",
        actualizadoEn = "2026-11-13T10:30:00Z"
    )

    private fun eventoCompleto(): EventoDto = EventoDto(
        id = "evento-servidor-1",
        garantiaId = "folio-servidor-1",
        articuloRef = "articulo-servidor-1",
        tipo = "recoleccion",
        descripcion = "recolectado",
        etapaDesde = "registrado",
        etapaHasta = "recolectado",
        usuario = "agente-1",
        rolDecisor = "cobrador",
        gpsLat = 19.4326,
        gpsLon = -99.1332,
        creadoEn = "2026-11-13T10:00:00Z",
        deviceCreatedAt = "2026-11-13T09:59:00Z",
        claveIdempotencia = "clave-idempotencia-1",
        distanciaEntregaM = 120.5,
        auditoriaGps = "gps-ok",
        imagenes = listOf(imagenCompleta())
    )

    private fun imagenCompleta(): ImagenDto = ImagenDto(
        id = "imagen-1",
        tipo = "recoleccion",
        descripcion = "foto de la bocina",
        subidaPor = "agente-1",
        creadaEn = "2026-11-13T10:05:00Z",
        url = "/v2/garantias/1/imagenes/1"
    )
}
