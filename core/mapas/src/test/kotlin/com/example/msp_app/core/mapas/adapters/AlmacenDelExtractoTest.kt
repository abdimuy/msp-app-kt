package com.example.msp_app.core.mapas.adapters

import com.example.msp_app.core.mapas.application.MapasTelemetria
import com.example.msp_app.core.mapas.fake.ExtractoReal
import com.example.msp_app.core.mapas.fake.archivoPmtilesTruncado
import com.example.msp_app.core.mapas.fake.archivoPmtilesValido
import com.example.msp_app.core.mapas.fake.cabeceraDePruebaDe
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El almacén: qué acepta, qué rechaza y **qué reporta al rechazar**.
 *
 * La norma de errores no se cumple declarándola: cada camino de rechazo tiene
 * su test con `RecordingTelemetry` afirmando el `code`, y además afirmando que
 * `props` **no arrastró ninguna coordenada ni ninguna ruta de archivo**.
 */
class AlmacenDelExtractoTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val telemetry = RecordingTelemetry()

    private fun almacen(): AlmacenDelExtracto = AlmacenDelExtracto(carpeta.root, telemetry)

    private fun parcial(): File = File(carpeta.root, "ruta.parcial")

    private fun definitivo(): File = File(carpeta.root, "ruta.pmtiles")

    @Test
    fun `un pmtiles completo se promueve y queda listo`() {
        archivoPmtilesValido(parcial())
        val almacen = almacen()

        assertTrue(almacen.verificarYPromover())

        assertFalse("el parcial tiene que desaparecer", parcial().exists())
        assertTrue(definitivo().isFile)
        assertNotNull(almacen.mapaListo())
        assertEquals(emptyList<String>(), telemetry.recorded.map { it.name })
    }

    /**
     * **El defecto que este almacén existe para impedir.**
     *
     * Un `.pmtiles` cortado a la mitad trae la cabecera entera —son los primeros
     * 127 bytes— así que MapLibre lo abriría y pintaría el pedazo que alcanzó a
     * bajar: calles que se terminan a media colonia, con pinta de mapa de
     * verdad. Eso es un dato FALSO.
     */
    @Test
    fun `un pmtiles truncado se rechaza, se borra y se reporta`() {
        archivoPmtilesTruncado(parcial())

        assertFalse(almacen().verificarYPromover())

        assertFalse("un truncado no se puede reanudar: se borra", parcial().exists())
        assertFalse(definitivo().exists())
        val evento = telemetry.recorded.single()
        assertEquals(MapasTelemetria.CODE_EXTRACTO_INVALIDO, evento.name)
        assertEquals("INCOMPLETO", evento.props[MapasTelemetria.PROP_MOTIVO])
    }

    @Test
    fun `un archivo que no es pmtiles se rechaza con su motivo`() {
        parcial().writeBytes(cabeceraDePruebaDe(finDeLosDatos = 127L, magia = "NOTPMTI"))

        assertFalse(almacen().verificarYPromover())

        assertEquals(
            "SIN_LA_MAGIA",
            telemetry.recorded.single().props[MapasTelemetria.PROP_MOTIVO]
        )
    }

    @Test
    fun `un pmtiles de otra version se rechaza con su motivo`() {
        parcial().writeBytes(cabeceraDePruebaDe(finDeLosDatos = 127L, version = 2))

        assertFalse(almacen().verificarYPromover())

        assertEquals(
            "VERSION_AJENA",
            telemetry.recorded.single().props[MapasTelemetria.PROP_MOTIVO]
        )
    }

    /**
     * Un definitivo que está pero no verifica devuelve `null` **y emite**. Sin
     * la emisión, desde afuera se vería exactamente igual que "todavía no lo
     * bajaron", que es el error silencioso que `CLAUDE.md` ya pagó caro.
     */
    @Test
    fun `un definitivo corrupto no se usa y no se calla`() {
        archivoPmtilesTruncado(definitivo())

        assertNull(almacen().mapaListo())

        assertEquals(MapasTelemetria.CODE_EXTRACTO_INVALIDO, telemetry.recorded.single().name)
    }

    @Test
    fun `sin archivo no hay mapa y no hay ruido`() {
        assertNull(almacen().mapaListo())

        assertEquals(emptyList<String>(), telemetry.recorded.map { it.name })
    }

    @Test
    fun `borrar se lleva las dos mitades`() {
        archivoPmtilesValido(parcial())
        archivoPmtilesValido(definitivo())

        almacen().borrarTodo()

        assertFalse(parcial().exists())
        assertFalse(definitivo().exists())
    }

    /**
     * **Anti-PII, medido y no declarado.** Ninguna emisión de este módulo puede
     * llevar una coordenada ni una ruta de archivo: una lat/lng dice dónde vive
     * el cliente y la ruta lleva el `filesDir` del dispositivo.
     */
    @Test
    fun `ninguna emision lleva claves fuera de la lista blanca`() {
        archivoPmtilesTruncado(parcial())
        almacen().verificarYPromover()
        archivoPmtilesTruncado(definitivo())
        almacen().mapaListo()

        assertTrue("no se emitio nada: el test no probaria nada", telemetry.recorded.isNotEmpty())
        telemetry.recorded.forEach { evento ->
            val claves = evento.props.keys - "message" // lo agrega RecordingTelemetry
            assertTrue(
                "clave no permitida en $evento",
                claves.all { it in MapasTelemetria.CLAVES_PERMITIDAS }
            )
            val texto = evento.props.values.joinToString(" ") + evento.name
            assertFalse("una ruta de archivo viajo a telemetria", texto.contains(carpeta.root.path))
        }
    }

    // ── El extracto de verdad ───────────────────────────────────────────────

    /**
     * El almacén, contra los 25 MB reales: se promueve, se relee desde disco y
     * la URL que sale es la que MapLibre espera. Es lo más cerca de "el mapa
     * funciona" que se puede llegar en la JVM — falta el render, que necesita GL.
     */
    @Test
    fun `el extracto real se verifica, se promueve y produce su url de teselas`() {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        ExtractoReal.archivo.copyTo(parcial(), overwrite = true)
        val almacen = almacen()

        assertTrue(almacen.verificarYPromover())

        val mapa = almacen.mapaListo()
        assertNotNull(mapa)
        assertEquals("pmtiles://file://${definitivo().absolutePath}", mapa!!.urlDeTeselas)
        assertEquals(ExtractoReal.ZOOM_MAXIMO_MEDIDO, mapa.cabecera.zoomMaximo)
        assertEquals(emptyList<String>(), telemetry.recorded.map { it.name })
    }

    /** El mismo archivo real, al que le faltan los últimos bytes: rechazado. */
    @Test
    fun `el extracto real al que le falta la cola se rechaza`() {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        val bytes = ExtractoReal.archivo.readBytes()
        parcial().writeBytes(bytes.copyOf(bytes.size - 1))

        assertFalse(almacen().verificarYPromover())

        assertEquals(
            "INCOMPLETO",
            telemetry.recorded.single().props[MapasTelemetria.PROP_MOTIVO]
        )
    }
}
