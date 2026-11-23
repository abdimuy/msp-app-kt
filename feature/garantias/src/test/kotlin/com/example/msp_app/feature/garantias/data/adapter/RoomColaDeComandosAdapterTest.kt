package com.example.msp_app.feature.garantias.data.adapter

import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.garantias.data.http.CuerpoDeGarantia
import com.example.msp_app.feature.garantias.domain.model.ComandoNuevo
import com.example.msp_app.feature.garantias.domain.model.EstadoDeComando
import com.example.msp_app.feature.garantias.domain.model.EstadoDeFoto
import com.example.msp_app.feature.garantias.domain.model.FotoNueva
import com.example.msp_app.feature.garantias.domain.model.OperacionDeGarantia
import com.example.msp_app.feature.garantias.domain.model.RutasDeGarantia
import com.example.msp_app.feature.garantias.domain.port.ArchivosDeGarantiaPort
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La cola sobre Room de verdad (en memoria): lo que se guarda al encolar no
 * cambia después.
 *
 * ## Qué protege
 *
 * D3 no es una convención de código: es que la fila conserve la clave, el
 * cuerpo y la hora del dispositivo aunque el comando falle y se reintente. Si
 * un `UPDATE` de estado tocara esas columnas, el servidor vería otro contenido
 * para la misma clave y respondería 409 para siempre. La prueba escribe, anota
 * un intento, marca el envío y vuelve a leer.
 */
class RoomColaDeComandosAdapterTest : RoomTestBase() {

    private val reloj = FakeClock(Instant.parse("2026-11-13T10:00:00Z"))

    private lateinit var cola: RoomColaDeComandosAdapter
    private lateinit var fotos: RoomFotosDeGarantiaAdapter
    private lateinit var archivos: ArchivosFalsos
    private lateinit var estado: RoomEstadoDeLasFotosAdapter

    @Before
    fun crearAdaptadores() {
        cola = RoomColaDeComandosAdapter(
            db = db,
            comandoDao = db.garantiaComandoDao(),
            fotoDao = db.garantiaFotoDao(),
            reloj = reloj
        )
        fotos = RoomFotosDeGarantiaAdapter(db.garantiaFotoDao(), reloj)
        archivos = ArchivosFalsos()
        estado = RoomEstadoDeLasFotosAdapter(db.garantiaFotoDao(), archivos)
    }

    @Test
    fun `anotar un intento y marcar el envio no tocan clave cuerpo ni hora`() = runTest {
        val comando = comandoNuevo()
        val id = cola.encolar(comando)

        cola.anotarIntento(id, "sin conexión", reloj.now())
        cola.marcarEnviado(id, reloj.now())

        val guardado = cola.porClave(comando.clave)
        assertEquals(EstadoDeComando.ENVIADO, guardado?.estado)
        assertEquals(comando.clave, guardado?.clave)
        assertEquals(comando.cuerpo, guardado?.cuerpo)
        assertEquals(comando.contentType, guardado?.contentType)
        assertEquals(comando.ruta, guardado?.ruta)
        assertEquals(comando.deviceCreatedAt, guardado?.deviceCreatedAt)
        assertEquals(1, guardado?.intentos)
    }

    @Test
    fun `encolar guarda el comando y sus fotos en la misma transaccion`() = runTest {
        val comando = comandoNuevo()
        val foto = FotoNueva(
            clave = "foto-1",
            rutaArchivo = "fotos/foto-1.jpg",
            mime = "image/jpeg",
            folioRef = "folio-local-1",
            eventoRef = "evento-local-1"
        )

        val id = cola.encolar(comando, listOf(foto))

        val guardada = fotos.porClave("foto-1")
        assertEquals(id, guardada?.comandoId)
        assertEquals("folio-local-1", guardada?.folioRef)
        assertEquals(EstadoDeFoto.PENDIENTE, guardada?.estado)
        assertEquals(1, fotos.pendientesCount())
    }

    @Test
    fun `delFolio devuelve los comandos en el orden en que se guardaron`() = runTest {
        val primero = cola.encolar(comandoNuevo(clave = "clave-1"))
        val segundo = cola.encolar(comandoNuevo(clave = "clave-2"))
        val tercero = cola.encolar(comandoNuevo(clave = "clave-3"))

        val delFolio = cola.delFolio("folio-local-1")

        assertEquals(listOf(primero, segundo, tercero), delFolio.map { it.id })
        assertEquals(listOf("clave-1", "clave-2", "clave-3"), delFolio.map { it.clave })
    }

    @Test
    fun `una foto conserva su clave y se borra con su archivo solo al eliminarla`() = runTest {
        val foto = FotoNueva(
            clave = "foto-fija-1",
            rutaArchivo = "fotos/foto-fija-1.jpg",
            mime = "image/jpeg"
        )
        fotos.guardar(foto, comandoId = null)

        estado.marcarSubida("foto-fija-1", reloj.now())

        assertEquals(EstadoDeFoto.ENVIADA, fotos.porClave("foto-fija-1")?.estado)
        assertTrue(archivos.borrados.isEmpty())

        estado.eliminar("foto-fija-1")

        assertNull(fotos.porClave("foto-fija-1"))
        assertEquals(listOf("fotos/foto-fija-1.jpg"), archivos.borrados)
    }

    private fun comandoNuevo(clave: String = "clave-fija-1"): ComandoNuevo = ComandoNuevo(
        clave = clave,
        operacion = OperacionDeGarantia.ABRIR_FOLIO,
        metodo = "POST",
        ruta = RutasDeGarantia.ABRIR,
        cuerpo = """{"origen":"piso"}""",
        contentType = CuerpoDeGarantia.TIPO_JSON,
        deviceCreatedAt = Instant.parse("2026-11-13T09:59:00Z"),
        folioRef = "folio-local-1"
    )
}

private class ArchivosFalsos : ArchivosDeGarantiaPort {

    val borrados = mutableListOf<String>()

    override suspend fun leer(ruta: String): ByteArray? = byteArrayOf(1)

    override suspend fun borrar(ruta: String) {
        borrados += ruta
    }
}
