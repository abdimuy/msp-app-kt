package com.example.msp_app.feature.garantias.data.queue

import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.garantias.data.http.CuerpoDeGarantia
import com.example.msp_app.feature.garantias.data.http.DatosDeEntrega
import com.example.msp_app.feature.garantias.domain.ResultadoDeLaLlamada
import com.example.msp_app.feature.garantias.domain.model.ArticuloDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.CuerpoHttp
import com.example.msp_app.feature.garantias.domain.model.DetalleDeGarantia
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.EstadoDeFoto
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.FolioSeguido
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.FotoPendienteDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.OrigenDeFolio
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.ColaDeComandosPort
import com.example.msp_app.feature.garantias.domain.port.EstadoDeLasFotosPort
import com.example.msp_app.feature.garantias.domain.port.FoliosSeguidosPort
import com.example.msp_app.feature.garantias.domain.port.FotosDeGarantiaPort
import com.example.msp_app.feature.garantias.domain.port.GarantiasRemotasPort
import com.example.msp_app.feature.garantias.domain.port.ResolucionDeReferenciasPort
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cola completa contra fakes: el orden por folio, la clave que no cambia y
 * el archivo que sólo se borra después del 2xx.
 *
 * Cada prueba usa el procesador real y dobles de los puertos. No hay MockK ni
 * Mockito: los fakes registran lo que recibieron y permiten comparar **bytes**
 * de cuerpo, que es lo que el servidor compara para decidir si una clave
 * repetida es un reintento o un 409 (D3).
 */
class ProcesadorDeLaColaTest {

    private val ahora = Instant.parse("2026-11-13T10:00:00Z")

    @Test
    fun `un reintento manda la misma clave y los mismos bytes`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.ENTREGAR,
            ruta = RutasDeGarantia.ENTREGAR,
            folioRef = "folio-local-1",
            contentType = CuerpoDeGarantia.TIPO_MULTIPART,
            cuerpo = datosDeEntrega().aJson()
        ).copy(folioId = "folio-servidor-1")
        val mundo = Mundo(
            comandos = listOf(comando),
            evidencia = Evidencia(
                archivos = mapOf(FIRMA to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
            ),
            respuestas = listOf(
                ResultadoDeLaLlamada.Respuesta(status = 503),
                ResultadoDeLaLlamada.Respuesta(status = 200)
            )
        )

        mundo.procesador.procesar()
        mundo.procesador.procesar()

        assertEquals(2, mundo.remotas.enviados.size)
        val primero = mundo.remotas.enviados[0]
        val segundo = mundo.remotas.enviados[1]
        assertEquals(primero.clave, segundo.clave)
        assertEquals("clave-fija-1", primero.clave)
        assertEquals(primero.ruta, segundo.ruta)
        assertEquals(primero.cuerpo, segundo.cuerpo)
        assertTrue(primero.cuerpo.bytes.contentEquals(segundo.cuerpo.bytes))
        assertEquals(CuerpoDeGarantia.TIPO_MULTIPART, primero.cuerpo.tipoDeContenido)
        assertTrue(primero.cuerpo.texto.contains("Content-Type: image/png"))
    }

    @Test
    fun `un comando no sale mientras su abrir del mismo folio siga pendiente`() = runTest {
        val escenario = escenarioDelFolio()
        val mundo = escenario.mundo

        val primera = mundo.procesador.procesar()

        assertEquals(listOf(RutasDeGarantia.ABRIR), mundo.remotas.enviados.map { it.ruta })
        assertEquals(3, primera.comandosPendientes)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(escenario.abrir.id))
        // No basta con que no salgan: mientras `abrir` siga pendiente, sus
        // dependientes (ids 2 y 3) ni siquiera se intentan. Es lo que mata al
        // mutante A5 (seguir con el siguiente comando del folio).
        assertEquals(0, mundo.cola.intentosDe(2))
        assertEquals(0, mundo.cola.intentosDe(3))

        val segunda = mundo.procesador.procesar()

        assertEquals(
            listOf(
                RutasDeGarantia.ABRIR,
                "v2/garantias/folio-servidor-1/articulos",
                "v2/garantias/folio-servidor-1/articulos/articulo-servidor-1/avanzar"
            ),
            mundo.remotas.enviados.drop(1).map { it.ruta }
        )
        assertEquals(3, segunda.comandosEnviados)
        assertEquals(EstadoLocalDeFolio.ABIERTO, mundo.folios.abiertos["folio-local-1"])
        // Dos comandos del mismo folio (agregar y avanzar) resuelven el mismo
        // artículo local: la propagación es idempotente y siempre con el mismo
        // id del servidor.
        assertEquals(
            listOf(Triple("articulo-local-1", "articulo-servidor-1", ahora)),
            mundo.resolucion.articulos.distinct()
        )
        assertEquals(
            listOf(Triple("evento-local-1", "evento-servidor-1", ahora)),
            mundo.resolucion.eventos
        )
        assertEquals(
            listOf(Triple("evento-local-1", "evento-servidor-1", "folio-servidor-1")),
            mundo.estado.eventosResueltos
        )
    }

    @Test
    fun `un comando rechazado detiene a los que dependen de el`() = runTest {
        val abrir = comando(
            id = 1,
            operacion = OperacionDeGarantia.ABRIR_FOLIO,
            ruta = RutasDeGarantia.ABRIR,
            folioRef = "folio-local-1",
            cuerpo = """{"origen":"piso"}"""
        )
        val agregar = comando(
            id = 2,
            operacion = OperacionDeGarantia.AGREGAR_ARTICULO,
            ruta = RutasDeGarantia.AGREGAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"descripcion":"bocina"}"""
        ).copy(articuloRef = "articulo-local-1")
        val mundo = Mundo(
            comandos = listOf(abrir, agregar),
            respuestas = listOf(
                ResultadoDeLaLlamada.Respuesta(
                    status = 422,
                    detalle = "el folio ya está cerrado"
                )
            )
        )

        val resultado = mundo.procesador.procesar()

        assertEquals(1, resultado.comandosRechazados)
        assertEquals(EstadoDeComando.RECHAZADO, mundo.cola.estadoDe(abrir.id))
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(agregar.id))
        assertEquals("el folio ya está cerrado", mundo.folios.rechazos["folio-local-1"])
        assertEquals(listOf(RutasDeGarantia.ABRIR), mundo.remotas.enviados.map { it.ruta })

        mundo.procesador.procesar()

        assertEquals(listOf(RutasDeGarantia.ABRIR), mundo.remotas.enviados.map { it.ruta })
    }

    @Test
    fun `un rechazo que no es el abrir no detiene al comando que sigue en la misma pasada`() =
        runTest {
            val primero = avanzarDelFolio(id = 1)
            val segundo = avanzarDelFolio(id = 2)
            val mundo = Mundo(
                comandos = listOf(primero, segundo),
                respuestas = listOf(
                    ResultadoDeLaLlamada.Respuesta(
                        status = 422,
                        detalle = "el folio no admite esa operación en su estado actual"
                    ),
                    ResultadoDeLaLlamada.Respuesta(status = 200)
                ),
                detalles = mapOf("folio-servidor-1" to detalleConEvento(segundo.clave))
            )

            val resultado = mundo.procesador.procesar()

            assertEquals(1, resultado.comandosRechazados)
            assertEquals(1, resultado.comandosEnviados)
            assertEquals(EstadoDeComando.RECHAZADO, mundo.cola.estadoDe(primero.id))
            assertEquals(EstadoDeComando.ENVIADO, mundo.cola.estadoDe(segundo.id))
            assertEquals(
                listOf(RUTA_AVANZAR_RESUELTA, RUTA_AVANZAR_RESUELTA),
                mundo.remotas.enviados.map { it.ruta }
            )
        }

    @Test
    fun `un rechazo viejo que no es el abrir no congela al comando nuevo del folio`() = runTest {
        val viejo = avanzarDelFolio(id = 1).copy(
            estado = EstadoDeComando.RECHAZADO,
            ultimoError = "el folio no admite esa operación en su estado actual"
        )
        val nuevo = avanzarDelFolio(id = 2)
        val mundo = Mundo(
            comandos = listOf(viejo, nuevo),
            respuestas = listOf(ResultadoDeLaLlamada.Respuesta(status = 200)),
            detalles = mapOf("folio-servidor-1" to detalleConEvento(nuevo.clave))
        )

        val resultado = mundo.procesador.procesar()

        assertEquals(1, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.ENVIADO, mundo.cola.estadoDe(nuevo.id))
        assertEquals(listOf(RUTA_AVANZAR_RESUELTA), mundo.remotas.enviados.map { it.ruta })
    }

    @Test
    fun `el evento se resuelve por su clave y no por el orden de la linea de tiempo`() = runTest {
        val avanzar = comando(
            id = 1,
            operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
            ruta = RutasDeGarantia.AVANZAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"etapa":"recolectado"}"""
        ).copy(
            folioId = "folio-servidor-1",
            articuloRef = "articulo-local-1",
            articuloId = "articulo-servidor-1",
            eventoRef = "evento-local-1"
        )
        val mundo = Mundo(
            comandos = listOf(avanzar),
            respuestas = listOf(ResultadoDeLaLlamada.Respuesta(status = 200)),
            detalles = mapOf(
                "folio-servidor-1" to detalle(
                    claveDeAvanzar = avanzar.clave,
                    claveDeOtraRecoleccion = "clave-de-otra-recoleccion"
                )
            )
        )

        mundo.procesador.procesar()

        assertEquals(
            listOf(Triple("evento-local-1", "evento-servidor-1", ahora)),
            mundo.resolucion.eventos
        )
        assertEquals(EstadoDeComando.ENVIADO, mundo.cola.estadoDe(avanzar.id))
    }

    @Test
    fun `una foto se reintenta con la misma clave y el archivo se borra solo despues del 2xx`() =
        runTest {
            val foto = foto(clave = "foto-fija-1")
            val mundo = Mundo(
                evidencia = Evidencia(
                    fotos = listOf(foto),
                    archivos = mapOf(foto.rutaArchivo to byteArrayOf(1, 2, 3, 4))
                ),
                respuestasDeImagen = listOf(
                    ResultadoDeLaLlamada.Respuesta(status = 503),
                    ResultadoDeLaLlamada.Respuesta(status = 201)
                )
            )

            mundo.procesador.procesar()

            assertEquals(listOf("foto-fija-1"), mundo.remotas.imagenes.map { it.clave })
            assertFalse(mundo.estado.llamadas.contains("eliminar:foto-fija-1"))
            assertTrue(mundo.archivos.borrados.isEmpty())
            assertEquals(EstadoDeFoto.PENDIENTE, mundo.fotos.estadoDe("foto-fija-1"))

            val segunda = mundo.procesador.procesar()

            assertEquals(
                listOf("foto-fija-1", "foto-fija-1"),
                mundo.remotas.imagenes.map { it.clave }
            )
            assertEquals(mundo.remotas.imagenes[0].cuerpo, mundo.remotas.imagenes[1].cuerpo)
            assertEquals(
                listOf(
                    "anotarIntento:foto-fija-1",
                    "marcarSubida:foto-fija-1",
                    "eliminar:foto-fija-1"
                ),
                mundo.estado.llamadas
            )
            assertEquals(listOf(foto.rutaArchivo), mundo.archivos.borrados)
            assertEquals(1, segunda.fotosSubidas)
            assertEquals(0, segunda.fotosPendientes)
        }

    @Test
    fun `un comando suelto sin folio se manda y se marca enviado`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.ABRIR_FOLIO,
            ruta = RutasDeGarantia.ABRIR,
            folioRef = null,
            cuerpo = """{"origen":"piso"}"""
        )
        val mundo = Mundo(
            comandos = listOf(comando),
            respuestas = listOf(
                ResultadoDeLaLlamada.Respuesta(
                    status = 201,
                    cuerpo = """{"id":"folio-servidor-1"}"""
                )
            )
        )

        val resultado = mundo.procesador.procesar()

        assertEquals(listOf(RutasDeGarantia.ABRIR), mundo.remotas.enviados.map { it.ruta })
        assertEquals(1, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.ENVIADO, mundo.cola.estadoDe(comando.id))
    }

    @Test
    fun `un comando sin cuerpo se rechaza sin llamar al api`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.AGREGAR_ARTICULO,
            ruta = RutasDeGarantia.AGREGAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = null
        ).copy(folioId = "folio-servidor-1")
        val mundo = Mundo(comandos = listOf(comando))

        val resultado = mundo.procesador.procesar()

        assertEquals(1, resultado.comandosRechazados)
        assertEquals(EstadoDeComando.RECHAZADO, mundo.cola.estadoDe(comando.id))
        assertTrue(mundo.remotas.enviados.isEmpty())
    }

    @Test
    fun `una entrega sin la firma en el telefono se rechaza`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.ENTREGAR,
            ruta = RutasDeGarantia.ENTREGAR,
            folioRef = "folio-local-1",
            contentType = CuerpoDeGarantia.TIPO_MULTIPART,
            cuerpo = datosDeEntrega().aJson()
        ).copy(folioId = "folio-servidor-1")
        val mundo = Mundo(comandos = listOf(comando))

        val resultado = mundo.procesador.procesar()

        assertEquals(1, resultado.comandosRechazados)
        assertEquals(EstadoDeComando.RECHAZADO, mundo.cola.estadoDe(comando.id))
        assertTrue(mundo.remotas.enviados.isEmpty())
    }

    @Test
    fun `un comando sin el id del folio se anota como intento`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.AGREGAR_ARTICULO,
            ruta = RutasDeGarantia.AGREGAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"descripcion":"bocina"}"""
        )
        val mundo = Mundo(comandos = listOf(comando))

        val resultado = mundo.procesador.procesar()

        assertEquals(0, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(comando.id))
        assertEquals(1, mundo.cola.intentosDe(comando.id))
        assertTrue(mundo.remotas.enviados.isEmpty())
    }

    @Test
    fun `un abrir sin id en el cuerpo se reintenta`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.ABRIR_FOLIO,
            ruta = RutasDeGarantia.ABRIR,
            folioRef = "folio-local-1",
            cuerpo = """{"origen":"piso"}"""
        )
        val mundo = Mundo(
            comandos = listOf(comando),
            respuestas = listOf(ResultadoDeLaLlamada.Respuesta(status = 201, cuerpo = """{}"""))
        )

        val resultado = mundo.procesador.procesar()

        assertEquals(0, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(comando.id))
    }

    @Test
    fun `un token vencido se anota y se reintenta`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.ABRIR_FOLIO,
            ruta = RutasDeGarantia.ABRIR,
            folioRef = "folio-local-1",
            cuerpo = """{"origen":"piso"}"""
        )
        val mundo = Mundo(
            comandos = listOf(comando),
            respuestas = listOf(ResultadoDeLaLlamada.Respuesta(status = 401))
        )

        val resultado = mundo.procesador.procesar()

        assertEquals(0, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(comando.id))
        assertEquals(1, mundo.cola.intentosDe(comando.id))
    }

    @Test
    fun `un detalle que todavia no cuenta el cambio se reintenta`() = runTest {
        val avanzar = comando(
            id = 1,
            operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
            ruta = RutasDeGarantia.AVANZAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"etapa":"recolectado"}"""
        ).copy(
            folioId = "folio-servidor-1",
            articuloRef = "articulo-local-1",
            articuloId = "articulo-servidor-1",
            eventoRef = "evento-local-1"
        )
        val mundo = Mundo(
            comandos = listOf(avanzar),
            respuestas = listOf(ResultadoDeLaLlamada.Respuesta(status = 200)),
            detalles = mapOf(
                "folio-servidor-1" to detalle(
                    claveDeAvanzar = "clave-que-no-existe",
                    claveDeOtraRecoleccion = "otra"
                )
            )
        )

        val resultado = mundo.procesador.procesar()

        assertEquals(0, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(avanzar.id))
        assertEquals(1, mundo.cola.intentosDe(avanzar.id))
    }

    @Test
    fun `un fallo de red al enviar detiene la pasada y se reintenta`() = runTest {
        val comando = comando(
            id = 1,
            operacion = OperacionDeGarantia.ABRIR_FOLIO,
            ruta = RutasDeGarantia.ABRIR,
            folioRef = "folio-local-1",
            cuerpo = """{"origen":"piso"}"""
        )
        val mundo = Mundo(comandos = listOf(comando), fallo = FalloDeRed.AL_ENVIAR)

        val resultado = mundo.procesador.procesar()

        assertTrue(resultado.sinRed)
        assertEquals(0, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(comando.id))
    }

    @Test
    fun `un fallo de red al leer el detalle se reintenta`() = runTest {
        val avanzar = comando(
            id = 1,
            operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
            ruta = RutasDeGarantia.AVANZAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"etapa":"recolectado"}"""
        ).copy(
            folioId = "folio-servidor-1",
            articuloRef = "articulo-local-1",
            articuloId = "articulo-servidor-1",
            eventoRef = "evento-local-1"
        )
        val mundo = Mundo(
            comandos = listOf(avanzar),
            respuestas = listOf(ResultadoDeLaLlamada.Respuesta(status = 200)),
            fallo = FalloDeRed.AL_DETALLE
        )

        val resultado = mundo.procesador.procesar()

        assertTrue(resultado.sinRed)
        assertEquals(0, resultado.comandosEnviados)
        assertEquals(EstadoDeComando.PENDIENTE, mundo.cola.estadoDe(avanzar.id))
    }

    /** Un folio abierto sin señal, con artículo y recolección detrás. */
    private fun escenarioDelFolio(): EscenarioDelFolio {
        val abrir = comando(
            id = 1,
            operacion = OperacionDeGarantia.ABRIR_FOLIO,
            ruta = RutasDeGarantia.ABRIR,
            folioRef = "folio-local-1",
            cuerpo = """{"origen":"piso"}"""
        )
        val agregar = comando(
            id = 2,
            operacion = OperacionDeGarantia.AGREGAR_ARTICULO,
            ruta = RutasDeGarantia.AGREGAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"descripcion":"bocina"}"""
        ).copy(articuloRef = "articulo-local-1")
        val avanzar = comando(
            id = 3,
            operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
            ruta = RutasDeGarantia.AVANZAR_ARTICULO,
            folioRef = "folio-local-1",
            cuerpo = """{"etapa":"recolectado"}"""
        ).copy(articuloRef = "articulo-local-1", eventoRef = "evento-local-1")
        val mundo = Mundo(
            comandos = listOf(abrir, agregar, avanzar),
            respuestas = listOf(
                ResultadoDeLaLlamada.Respuesta(status = 503),
                ResultadoDeLaLlamada.Respuesta(
                    status = 201,
                    cuerpo = """{"id":"folio-servidor-1"}"""
                ),
                ResultadoDeLaLlamada.Respuesta(status = 200),
                ResultadoDeLaLlamada.Respuesta(status = 200)
            ),
            detalles = mapOf(
                "folio-servidor-1" to detalle(
                    claveDeAgregar = agregar.clave,
                    claveDeAvanzar = avanzar.clave,
                    claveDeOtraRecoleccion = "clave-de-otra-recoleccion"
                )
            )
        )
        return EscenarioDelFolio(mundo = mundo, abrir = abrir)
    }

    /** Un `avanzar` de un artículo ya resuelto: listo para viajar. */
    private fun avanzarDelFolio(id: Long): ComandoDeGarantia = comando(
        id = id,
        operacion = OperacionDeGarantia.AVANZAR_ARTICULO,
        ruta = RutasDeGarantia.AVANZAR_ARTICULO,
        folioRef = "folio-local-1",
        cuerpo = """{"etapa":"recolectado"}"""
    ).copy(
        folioId = "folio-servidor-1",
        articuloRef = "articulo-local-1",
        articuloId = "articulo-servidor-1",
        eventoRef = "evento-local-$id"
    )

    /** El detalle del folio con el evento que responde a [clave]. */
    private fun detalleConEvento(clave: String): DetalleDeGarantia = detalle(
        claveDeAgregar = "clave-que-no-existe",
        claveDeAvanzar = clave,
        claveDeOtraRecoleccion = "otra"
    )

    private fun comando(
        id: Long,
        operacion: OperacionDeGarantia,
        ruta: String,
        folioRef: String? = null,
        cuerpo: String? = null,
        contentType: String? = CuerpoDeGarantia.TIPO_JSON
    ): ComandoDeGarantia = ComandoDeGarantia(
        id = id,
        clave = "clave-fija-$id",
        operacion = operacion,
        metodo = "POST",
        ruta = ruta,
        cuerpo = cuerpo,
        contentType = contentType,
        deviceCreatedAt = ahora,
        estado = EstadoDeComando.PENDIENTE,
        intentos = 0,
        creadoEn = ahora,
        actualizadoEn = ahora,
        folioRef = folioRef
    )

    private fun foto(clave: String): FotoPendienteDeGarantia = FotoPendienteDeGarantia(
        clave = clave,
        rutaArchivo = "fotos/$clave.jpg",
        mime = "image/jpeg",
        estado = EstadoDeFoto.PENDIENTE,
        intentos = 0,
        creadoEn = ahora,
        actualizadoEn = ahora,
        folioRef = "folio-local-1",
        folioId = "folio-servidor-1",
        eventoRef = "evento-local-1",
        eventoId = "evento-servidor-1"
    )

    private fun datosDeEntrega(): DatosDeEntrega = CuerpoDeGarantia.datosDeEntrega(
        deviceCreatedAt = "2026-11-13T10:00:00Z",
        ubicacion = Ubicacion(latitud = 19.4326, longitud = -99.1332),
        rutaDeLaFirma = FIRMA
    )

    private fun detalle(
        claveDeAgregar: String,
        claveDeAvanzar: String,
        claveDeOtraRecoleccion: String
    ): DetalleDeGarantia = DetalleDeGarantia(
        id = "folio-servidor-1",
        folio = "G-0001",
        origen = OrigenDeFolio.PISO,
        estado = "abierto",
        descripcion = "bocina",
        abiertoPor = "agente",
        creadoEn = ahora,
        actualizadoEn = ahora,
        articulos = listOf(articulo()),
        eventos = listOf(
            evento(
                id = "evento-ajeno",
                clave = claveDeOtraRecoleccion,
                tipo = "recoleccion",
                articuloRef = "articulo-servidor-1"
            ),
            evento(
                id = "evento-del-agregar",
                clave = claveDeAgregar,
                tipo = "articulo_agregado",
                articuloRef = "articulo-servidor-1"
            ),
            evento(
                id = "evento-servidor-1",
                clave = claveDeAvanzar,
                tipo = "recoleccion",
                articuloRef = "articulo-servidor-1"
            )
        )
    )

    /**
     * Variante para la prueba de la clave: el evento buscado va **en medio** de
     * la línea de tiempo, con otro del mismo tipo antes y después. Así fallan
     * tanto tomar "el primero" como "el último" (mutante A6): sólo la clave
     * idempotente devuelve el evento correcto.
     */
    private fun detalle(claveDeAvanzar: String, claveDeOtraRecoleccion: String): DetalleDeGarantia {
        val base = detalle(
            claveDeAgregar = "clave-que-no-existe",
            claveDeAvanzar = claveDeAvanzar,
            claveDeOtraRecoleccion = claveDeOtraRecoleccion
        )
        return base.copy(
            eventos = base.eventos + evento(
                id = "evento-ajeno-despues",
                clave = "$claveDeOtraRecoleccion-despues",
                tipo = "recoleccion",
                articuloRef = "articulo-servidor-1"
            )
        )
    }

    private fun articulo(): ArticuloDeGarantia = ArticuloDeGarantia(
        id = "articulo-servidor-1",
        rol = "original",
        clave = "clave-del-articulo",
        descripcion = "bocina",
        etapa = "recolectado",
        ubicacion = "taller",
        creadoEn = ahora,
        actualizadoEn = ahora
    )

    private fun evento(
        id: String,
        clave: String,
        tipo: String,
        articuloRef: String?
    ): EventoDeGarantia = EventoDeGarantia(
        id = id,
        articuloRef = articuloRef,
        tipo = tipo,
        descripcion = "evento",
        usuario = "agente",
        creadoEn = ahora,
        deviceCreatedAt = ahora,
        claveIdempotencia = clave
    )

    private companion object {
        const val FIRMA = "firmas/firma.png"
        const val RUTA_AVANZAR_RESUELTA =
            "v2/garantias/folio-servidor-1/articulos/articulo-servidor-1/avanzar"
    }
}

/** El escenario del folio sin señal: el mundo armado y el comando que lo abre. */
private data class EscenarioDelFolio(
    val mundo: Mundo,
    val abrir: ComandoDeGarantia
)

/** Dónde falla la red de la prueba: en el envío, al leer el detalle, o en ningún lado. */
private enum class FalloDeRed { NINGUNO, AL_ENVIAR, AL_DETALLE }

/** La evidencia que la prueba deja en disco y en la cola antes de procesar. */
private data class Evidencia(
    val fotos: List<FotoPendienteDeGarantia> = emptyList(),
    val archivos: Map<String, ByteArray> = emptyMap()
)

/** El mundo de la prueba: procesador real, puertos falsos. */
private class Mundo(
    comandos: List<ComandoDeGarantia> = emptyList(),
    evidencia: Evidencia = Evidencia(),
    respuestas: List<ResultadoDeLaLlamada> = emptyList(),
    respuestasDeImagen: List<ResultadoDeLaLlamada> = emptyList(),
    detalles: Map<String, DetalleDeGarantia> = emptyMap(),
    fallo: FalloDeRed = FalloDeRed.NINGUNO
) {
    val reloj = FakeClock(Instant.parse("2026-11-13T10:00:00Z"))
    val cola = ColaFalsa(comandos.toMutableList())
    val folios = FoliosFalsos()
    val remotas = RemotasFalsas(
        respuestas.toMutableList(),
        respuestasDeImagen.toMutableList(),
        detalles,
        fallo
    )
    val fotos = FotosFalsas(evidencia.fotos.toMutableList())
    val archivos = ArchivosFalsos(evidencia.archivos.toMutableMap())
    val estado = EstadoFalso(fotos, archivos)
    val resolucion = ResolucionFalsa()

    val procesador = ProcesadorDeLaCola(
        cola = cola,
        folios = folios,
        remotas = remotas,
        resolutor = ResolutorDeReferencias(remotas, resolucion, estado),
        subidor = SubidorDeFotos(remotas, fotos, estado, archivos, reloj),
        cuerpos = CuerposDeLaCola(archivos),
        reloj = reloj
    )
}

private data class Envio(
    val clave: String,
    val metodo: String,
    val ruta: String,
    val cuerpo: CuerpoHttp
)

private class ColaFalsa(
    private val comandos: MutableList<ComandoDeGarantia>
) : ColaDeComandosPort {

    override suspend fun encolar(comando: ComandoNuevo, fotos: List<FotoNueva>): Long =
        error("la prueba no encola")

    override suspend fun pendientes(): List<ComandoDeGarantia> =
        comandos.filter { it.estado == EstadoDeComando.PENDIENTE }

    override suspend fun delFolio(folioRef: String): List<ComandoDeGarantia> =
        comandos.filter { it.folioRef == folioRef }.sortedBy { it.id }

    override suspend fun porClave(clave: String): ComandoDeGarantia? =
        comandos.firstOrNull { it.clave == clave }

    override suspend fun marcarEnviado(id: Long, ahora: Instant) {
        cambiar(id) { it.copy(estado = EstadoDeComando.ENVIADO) }
    }

    override suspend fun marcarRechazado(id: Long, error: String, ahora: Instant) {
        cambiar(id) { it.copy(estado = EstadoDeComando.RECHAZADO, ultimoError = error) }
    }

    override suspend fun anotarIntento(id: Long, error: String, ahora: Instant) {
        cambiar(id) { it.copy(intentos = it.intentos + 1, ultimoError = error) }
    }

    fun estadoDe(id: Long): EstadoDeComando? = comandos.firstOrNull { it.id == id }?.estado

    fun intentosDe(id: Long): Int = comandos.firstOrNull { it.id == id }?.intentos ?: 0

    private fun cambiar(id: Long, cambio: (ComandoDeGarantia) -> ComandoDeGarantia) {
        val indice = comandos.indexOfFirst { it.id == id }
        comandos[indice] = cambio(comandos[indice])
    }
}

private class RemotasFalsas(
    private val respuestas: MutableList<ResultadoDeLaLlamada>,
    private val respuestasDeImagen: MutableList<ResultadoDeLaLlamada>,
    private val detalles: Map<String, DetalleDeGarantia>,
    private val fallo: FalloDeRed = FalloDeRed.NINGUNO
) : GarantiasRemotasPort {

    val enviados = mutableListOf<Envio>()
    val imagenes = mutableListOf<Envio>()

    override suspend fun enviar(
        clave: String,
        metodo: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada {
        enviados += Envio(clave, metodo, ruta, cuerpo)
        if (fallo == FalloDeRed.AL_ENVIAR) throw IOException("sin señal")
        return respuestas.removeFirst()
    }

    override suspend fun detalle(folioId: String): DetalleDeGarantia {
        if (fallo == FalloDeRed.AL_DETALLE) throw IOException("sin señal")
        return detalles[folioId] ?: error("la prueba no preparó el detalle de $folioId")
    }

    override suspend fun subirImagen(
        clave: String,
        ruta: String,
        cuerpo: CuerpoHttp
    ): ResultadoDeLaLlamada {
        imagenes += Envio(clave, "POST", ruta, cuerpo)
        return respuestasDeImagen.removeFirst()
    }
}

private class FoliosFalsos : FoliosSeguidosPort {

    val abiertos = mutableMapOf<String, EstadoLocalDeFolio>()
    val rechazos = mutableMapOf<String, String>()

    override suspend fun guardar(folio: FolioSeguido) = Unit

    override suspend fun todos(): List<FolioSeguido> = emptyList()

    override suspend fun porId(id: String): FolioSeguido? = null

    override suspend fun porFolioId(folioId: String): FolioSeguido? = null

    override suspend fun marcarAbierto(id: String, folioId: String, ahora: Instant) {
        abiertos[id] = EstadoLocalDeFolio.ABIERTO
    }

    override suspend fun marcarEstado(
        id: String,
        estado: EstadoLocalDeFolio,
        error: String?,
        ahora: Instant
    ) {
        abiertos[id] = estado
        if (error != null) rechazos[id] = error
    }

    override suspend fun pendientesDeEnvio(): Int = 0
}

private class ResolucionFalsa : ResolucionDeReferenciasPort {

    val articulos = mutableListOf<Triple<String, String, Instant>>()
    val eventos = mutableListOf<Triple<String, String, Instant>>()

    override suspend fun resolverFolio(folioRef: String, folioId: String, ahora: Instant): Int = 1

    override suspend fun resolverArticulo(
        articuloRef: String,
        articuloId: String,
        ahora: Instant
    ): Int {
        articulos += Triple(articuloRef, articuloId, ahora)
        return 1
    }

    override suspend fun resolverEvento(eventoRef: String, eventoId: String, ahora: Instant): Int {
        eventos += Triple(eventoRef, eventoId, ahora)
        return 1
    }
}

private class FotosFalsas(
    private val fotos: MutableList<FotoPendienteDeGarantia>
) : FotosDeGarantiaPort {

    override suspend fun guardar(foto: FotoNueva, comandoId: Long?) = Unit

    override suspend fun guardarTodas(fotos: List<FotoNueva>, comandoId: Long?) = Unit

    override suspend fun pendientes(): List<FotoPendienteDeGarantia> =
        fotos.filter { it.estado == EstadoDeFoto.PENDIENTE }

    override suspend fun listasParaSubir(): List<FotoPendienteDeGarantia> =
        fotos.filter { it.estado == EstadoDeFoto.PENDIENTE && it.eventoId != null }

    override suspend fun porClave(clave: String): FotoPendienteDeGarantia? =
        fotos.firstOrNull { it.clave == clave }

    override suspend fun pendientesCount(): Int = pendientes().size

    override suspend fun rutasVivas(): List<String> = fotos.map { it.rutaArchivo }

    fun estadoDe(clave: String): EstadoDeFoto? = fotos.firstOrNull { it.clave == clave }?.estado

    fun marcarSubida(clave: String) {
        cambiar(clave) { it.copy(estado = EstadoDeFoto.ENVIADA) }
    }

    fun eliminar(clave: String) {
        fotos.removeAll { it.clave == clave }
    }

    private fun cambiar(
        clave: String,
        cambio: (FotoPendienteDeGarantia) -> FotoPendienteDeGarantia
    ) {
        val indice = fotos.indexOfFirst { it.clave == clave }
        fotos[indice] = cambio(fotos[indice])
    }
}

private class EstadoFalso(
    private val fotos: FotosFalsas,
    private val archivos: ArchivosFalsos
) : EstadoDeLasFotosPort {

    val llamadas = mutableListOf<String>()
    val eventosResueltos = mutableListOf<Triple<String, String, String>>()

    override suspend fun marcarSubida(clave: String, ahora: Instant) {
        llamadas += "marcarSubida:$clave"
        fotos.marcarSubida(clave)
    }

    override suspend fun marcarRechazada(clave: String, error: String, ahora: Instant) {
        llamadas += "marcarRechazada:$clave"
    }

    override suspend fun anotarIntento(clave: String, error: String, ahora: Instant) {
        llamadas += "anotarIntento:$clave"
    }

    override suspend fun resolverEvento(
        eventoRef: String,
        eventoId: String,
        folioId: String,
        ahora: Instant
    ): Int {
        eventosResueltos += Triple(eventoRef, eventoId, folioId)
        return 1
    }

    override suspend fun eliminar(clave: String) {
        llamadas += "eliminar:$clave"
        val ruta = fotos.porClave(clave)?.rutaArchivo ?: return
        fotos.eliminar(clave)
        archivos.borrar(ruta)
    }
}

private class ArchivosFalsos(
    private val contenido: MutableMap<String, ByteArray>
) : ArchivosDeGarantiaPort {

    val borrados = mutableListOf<String>()

    override suspend fun leer(ruta: String): ByteArray? = contenido[ruta]

    override suspend fun borrar(ruta: String) {
        borrados += ruta
        contenido.remove(ruta)
    }
}
