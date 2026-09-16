package com.example.msp_app.core.mapas.adapters

import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.fake.ExtractoReal
import com.example.msp_app.core.mapas.fake.PlanificadorFalso
import com.example.msp_app.core.mapas.fake.archivoPmtilesValido
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** El puerto del extracto: qué contesta según lo que hay en disco y en la config. */
class ExtractoDeMapaAdapterTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val telemetry = RecordingTelemetry()
    private val planificador = PlanificadorFalso()

    private fun adaptador(extracto: ExtractoDeMapa? = PAQUETE) = ExtractoDeMapaAdapter(
        almacen = AlmacenDelExtracto(carpeta.root, telemetry),
        planificador = planificador,
        estado = EstadoDelExtractoEnMemoria(),
        extracto = extracto
    )

    /**
     * **Sin origen publicado no se ofrece un botón que no puede funcionar.**
     *
     * El extracto se genera a mano y hoy no vive en ningún servidor. Decir
     * "Ausente" —o sea "tócale a Descargar"— sería un afordante que miente.
     */
    @Test
    fun `sin url configurada el estado es SinOrigen`() = runTest(StandardTestDispatcher()) {
        assertEquals(EstadoDelExtracto.SinOrigen, adaptador(extracto = null).estado().first())
    }

    @Test
    fun `sin url configurada pedir la descarga no encola nada`() =
        runTest(StandardTestDispatcher()) {
            val puerto = adaptador(extracto = null)

            puerto.pedirLaDescarga()

            assertTrue(planificador.encolados.isEmpty())
            assertEquals(EstadoDelExtracto.SinOrigen, puerto.estado().first())
        }

    @Test
    fun `con url y sin archivo el estado es Ausente`() = runTest(StandardTestDispatcher()) {
        assertEquals(EstadoDelExtracto.Ausente, adaptador().estado().first())
    }

    /**
     * Sin esto, el cobrador que bajó el mapa ayer abriría la pantalla hoy, vería
     * "Descargar" y bajaría 25 MB de nuevo. El estado en memoria se pierde; el
     * archivo no.
     */
    @Test
    fun `al construirse ve el extracto que ya estaba en disco`() =
        runTest(StandardTestDispatcher()) {
            archivoPmtilesValido(File(carpeta.root, "ruta.pmtiles"))

            val estado = adaptador().estado().first()

            assertTrue(estado is EstadoDelExtracto.Listo)
            assertEquals(
                ExtractoReal.ZOOM_MAXIMO_MEDIDO,
                (estado as EstadoDelExtracto.Listo).mapa.cabecera.zoomMaximo
            )
        }

    /**
     * **Un mapa en disco se usa aunque no haya de dónde bajarlo.** No tener
     * origen no es no tener mapa: alguien pudo haberlo puesto por `adb push`, y
     * negarse a dibujarlo sería tirar un archivo bueno por una configuración que
     * falta.
     */
    @Test
    fun `sin url pero con archivo en disco, el mapa se usa igual`() =
        runTest(StandardTestDispatcher()) {
            archivoPmtilesValido(File(carpeta.root, "ruta.pmtiles"))

            assertTrue(adaptador(extracto = null).estado().first() is EstadoDelExtracto.Listo)
        }

    @Test
    fun `con un parcial a medias el estado es Interrumpido`() = runTest(StandardTestDispatcher()) {
        File(carpeta.root, "ruta.parcial").writeBytes(ByteArray(4_000))

        val estado = adaptador().estado().first()

        assertTrue(estado is EstadoDelExtracto.Interrumpido)
        assertEquals(4_000L, (estado as EstadoDelExtracto.Interrumpido).avance.bytesBajados)
    }

    /**
     * "Esperando wifi" y no "Descargando": es la verdad hasta que el sistema
     * arranque el trabajo. Una barra clavada en cero bajo la palabra
     * "Descargando" es lo que termina en una llamada por teléfono.
     */
    @Test
    fun `pedir la descarga deja esperando wifi y encola`() = runTest(StandardTestDispatcher()) {
        val puerto = adaptador()

        puerto.pedirLaDescarga()

        assertEquals(EstadoDelExtracto.EsperandoWifi, puerto.estado().first())
        assertEquals(1, planificador.encolados.size)
    }

    @Test
    fun `cancelar y borrar deja el extracto ausente y el disco limpio`() =
        runTest(StandardTestDispatcher()) {
            archivoPmtilesValido(File(carpeta.root, "ruta.pmtiles"))
            val puerto = adaptador()

            puerto.cancelarYBorrar()

            assertEquals(EstadoDelExtracto.Ausente, puerto.estado().first())
            assertEquals(1, planificador.cancelaciones.size)
            assertFalse(File(carpeta.root, "ruta.pmtiles").exists())
        }

    private companion object {
        val PAQUETE = ExtractoDeMapa(
            url = "https://ejemplo.invalido/ruta.pmtiles",
            tamanoBytes = ExtractoReal.TAMANO_MEDIDO
        )
    }
}
