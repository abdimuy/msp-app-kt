package com.example.msp_app.data.visitas

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El barrido de huérfanos del adaptador de cámara, contra Room de verdad.
 *
 * Lo que estas pruebas fijan, y es lo que el revisor encontró que faltaba:
 * **una imagen pendiente cuya visita SIGUE existiendo no es huérfana, es
 * pendiente** — y la pendiente que sí se barre (su visita ya se podó, nadie la
 * va a subir nunca) **no desaparece muda**.
 *
 * El barrido corre al preparar la cámara, así que se ejercita por ahí: es el
 * camino de producción, no un atajo al método privado.
 */
class ComprobantesDeVisitaAdapterTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-09-11T18:00:00Z")
    private val clock = FakeClock(ahora)
    private val telemetry = RecordingTelemetry(clock)

    private lateinit var context: Context
    private lateinit var adaptador: ComprobantesDeVisitaAdapter

    @Before
    fun setUpAdaptador() {
        context = ApplicationProvider.getApplicationContext()
        adaptador = ComprobantesDeVisitaAdapter(
            context = context,
            imagenes = db.visitImageDao(),
            telemetry = telemetry,
            clock = clock
        )
    }

    /**
     * **Pendiente con padre vivo = intocable.** Es la evidencia que espera
     * señal; barrerla sería borrar el trabajo del cobrador antes de entregarlo.
     */
    @Test
    fun `una imagen pendiente cuya visita existe no se barre nunca`() = runTest {
        sembrarVisita("visita-viva")
        val archivo = sembrarImagen(
            id = "IMG-PENDIENTE",
            visitaId = "visita-viva",
            creadaEn = "2026-08-01T10:00:00Z"
        )

        adaptador.barrerHuerfanos()

        assertEquals(
            "la pendiente con padre vivo se queda",
            listOf("IMG-PENDIENTE"),
            db.visitImageDao().getByVisitaId("visita-viva").map { it.ID }
        )
        assertTrue("y su archivo tambien", archivo.exists())
    }

    /**
     * **La pendiente SIN padre sí se barre —y se reporta.** Su visita ya se
     * podó, así que nadie la va a subir jamás y el disco no puede crecer sin
     * techo; pero borrarla en silencio convertiría el barrido en el desagüe mudo
     * de toda evidencia no entregada.
     */
    @Test
    fun `una pendiente huerfana y vieja se barre y se reporta`() = runTest {
        val archivo = sembrarImagen(
            id = "IMG-PERDIDA",
            visitaId = "visita-podada",
            creadaEn = "2026-08-01T10:00:00Z"
        )

        // Por `nuevoDestino()` a propósito: es el camino de producción, y con
        // esto queda probado que el barrido está CABLEADO ahí y no solo que
        // funciona suelto. `runCatching` porque bajo Robolectric el
        // `FileProvider` se declara para `com.example.msp_app` y el paquete de
        // prueba es `com.example.msp_app.test`, así que la línea siguiente a la
        // del barrido revienta al pedir el `content://`. El barrido ya corrió.
        runCatching { adaptador.nuevoDestino() }

        assertEquals(emptyList<Any>(), db.visitImageDao().getByVisitaId("visita-podada"))
        assertFalse(archivo.exists())
        val evento = telemetry.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == VisitasTelemetria.CODE_VISITA_FOTO_BARRIDA_SIN_SUBIR
        }
        assertEquals("1", evento.props[VisitasTelemetria.PROP_OCURRENCIAS])
    }

    /**
     * **Control positivo del reporte:** una huérfana vieja que YA subió se barre
     * exactamente igual y **no** reporta nada — no se perdió evidencia, se
     * liberó espacio. Sin esta prueba, el evento de arriba no distinguiría "se
     * perdió una foto" de "el barrido corrió".
     */
    @Test
    fun `una huerfana que ya subio se barre sin reportar perdida`() = runTest {
        val archivo = sembrarImagen(
            id = "IMG-ENTREGADA",
            visitaId = "visita-podada",
            creadaEn = "2026-08-01T10:00:00Z",
            subidaEn = "2026-08-02T10:00:00Z"
        )

        adaptador.barrerHuerfanos()

        assertEquals(emptyList<Any>(), db.visitImageDao().getByVisitaId("visita-podada"))
        assertFalse(archivo.exists())
        assertTrue(
            "lo que ya llego al servidor no es una perdida",
            telemetry.recorded.none {
                it.name == VisitasTelemetria.CODE_VISITA_FOTO_BARRIDA_SIN_SUBIR
            }
        )
    }

    /** Una huérfana RECIENTE no se barre: puede estar a medio reinsertar. */
    @Test
    fun `una huerfana reciente no se barre ni se reporta`() = runTest {
        sembrarImagen(
            id = "IMG-RECIEN",
            visitaId = "visita-podada",
            creadaEn = "2026-09-10T10:00:00Z"
        )

        adaptador.barrerHuerfanos()

        assertEquals(1, db.visitImageDao().getByVisitaId("visita-podada").size)
        assertTrue(
            telemetry.recorded.none {
                it.name == VisitasTelemetria.CODE_VISITA_FOTO_BARRIDA_SIN_SUBIR
            }
        )
    }

    private suspend fun sembrarVisita(id: String) = db.visitDao().insertVisit(
        VisitEntity(
            ID = id,
            CLIENTE_ID = 11486,
            COBRADOR = "Ramirez Ortiz, Fernando",
            COBRADOR_ID = 200,
            FECHA = "2026-09-01T09:30:00Z",
            FORMA_COBRO_ID = 0,
            LAT = 0.0,
            LNG = 0.0,
            NOTA = "Visita de prueba",
            TIPO_VISITA = "SIN_PAGO",
            ZONA_CLIENTE_ID = 21552,
            IMPTE_DOCTO_CC_ID = 5000,
            GUARDADO_EN_MICROSIP = 0
        )
    )

    private suspend fun sembrarImagen(
        id: String,
        visitaId: String,
        creadaEn: String,
        subidaEn: String? = null
    ): File {
        val archivo = File(context.filesDir, "$PREFIJO_COMPROBANTE_VISITA$id.jpg")
        archivo.writeBytes(byteArrayOf(1, 2, 3))
        db.visitImageDao().insertAll(
            listOf(
                VisitImageEntity(
                    ID = id,
                    VISITA_ID = visitaId,
                    URI = archivo.absolutePath,
                    MIME = "image/jpeg",
                    ORDEN = 0,
                    CREADA_EN = creadaEn,
                    SUBIDA_EN = subidaEn
                )
            )
        )
        return archivo
    }
}
