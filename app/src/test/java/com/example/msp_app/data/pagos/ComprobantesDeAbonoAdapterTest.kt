package com.example.msp_app.data.pagos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El barrido de huérfanos del adaptador de cámara **del dinero**, contra Room de
 * verdad. Gemelo exacto de `ComprobantesDeVisitaAdapterTest`, y esa simetría es
 * el punto: la revisión final encontró que el barrido de la VISITA reportaba lo
 * que nunca subió y el del PAGO borraba mudo — el camino menos crítico quedó
 * siendo el más robusto.
 *
 * En cobranza la pérdida además no es un borde raro: `RECONCILED_VIA_GET` y
 * `UploadDecision.RELEASE` dejan comprobantes pendientes a propósito y
 * `mergePagos` los deja sin padre.
 */
class ComprobantesDeAbonoAdapterTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-09-11T18:00:00Z")
    private val clock = FakeClock(ahora)
    private val telemetry = RecordingTelemetry(clock)

    private lateinit var context: Context
    private lateinit var adaptador: ComprobantesDeAbonoAdapter

    @Before
    fun setUpAdaptador() {
        context = ApplicationProvider.getApplicationContext()
        adaptador = ComprobantesDeAbonoAdapter(
            context = context,
            imagenes = db.paymentImageDao(),
            telemetry = telemetry,
            clock = clock
        )
    }

    /**
     * **Pendiente con padre vivo = intocable.** Es el comprobante que espera
     * señal; barrerlo sería borrar la evidencia de un cobro antes de entregarla.
     */
    @Test
    fun `una imagen pendiente cuyo pago existe no se barre nunca`() = runTest {
        sembrarPago("pago-vivo")
        val archivo = sembrarImagen(
            id = "IMG-PENDIENTE",
            pagoId = "pago-vivo",
            creadaEn = "2026-08-01T10:00:00Z"
        )

        adaptador.barrerHuerfanos()

        assertEquals(
            "la pendiente con padre vivo se queda",
            listOf("IMG-PENDIENTE"),
            db.paymentImageDao().getByPagoId("pago-vivo").map { it.ID }
        )
        assertTrue("y su archivo tambien", archivo.exists())
    }

    /**
     * **La pendiente SIN padre sí se barre —y se reporta.** Su pago ya no
     * existe, así que nadie la va a subir jamás; pero borrarla en silencio
     * convertiría el barrido en el desagüe mudo de toda la evidencia que los dos
     * caminos no-felices de la subida dejan pendiente a propósito.
     */
    @Test
    fun `una pendiente huerfana y vieja se barre y se reporta`() = runTest {
        val archivo = sembrarImagen(
            id = "IMG-PERDIDA",
            pagoId = "pago-rellaveado",
            creadaEn = "2026-08-01T10:00:00Z"
        )

        // Por `nuevoDestino()` a propósito: es el camino de producción, y con
        // esto queda probado que el barrido está CABLEADO ahí y no solo que
        // funciona suelto. `runCatching` porque bajo Robolectric el
        // `FileProvider` se declara para `com.example.msp_app` y el paquete de
        // prueba es `com.example.msp_app.test`, así que la línea siguiente a la
        // del barrido revienta al pedir el `content://`. El barrido ya corrió.
        runCatching { adaptador.nuevoDestino() }

        assertEquals(emptyList<Any>(), db.paymentImageDao().getByPagoId("pago-rellaveado"))
        assertFalse(archivo.exists())
        val evento = telemetry.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_FOTO_BARRIDA_SIN_SUBIR
        }
        assertEquals("1", evento.props[PagosTelemetria.PROP_OCURRENCIAS])
    }

    /**
     * **Control positivo del reporte:** una huérfana vieja que YA subió se barre
     * exactamente igual y **no** reporta nada — no se perdió evidencia, se
     * liberó espacio. Sin esta prueba, el evento de arriba no distinguiría "se
     * perdió un comprobante" de "el barrido corrió".
     */
    @Test
    fun `una huerfana que ya subio se barre sin reportar perdida`() = runTest {
        val archivo = sembrarImagen(
            id = "IMG-ENTREGADA",
            pagoId = "pago-rellaveado",
            creadaEn = "2026-08-01T10:00:00Z",
            subidaEn = "2026-08-02T10:00:00Z"
        )

        adaptador.barrerHuerfanos()

        assertEquals(emptyList<Any>(), db.paymentImageDao().getByPagoId("pago-rellaveado"))
        assertFalse(archivo.exists())
        assertTrue(
            "lo que ya llego al servidor no es una perdida",
            telemetry.recorded.none {
                it.name == PagosTelemetria.CODE_ABONO_FOTO_BARRIDA_SIN_SUBIR
            }
        )
    }

    /** Una huérfana RECIENTE no se barre: el pago puede estar a media re-llaveada. */
    @Test
    fun `una huerfana reciente no se barre ni se reporta`() = runTest {
        sembrarImagen(
            id = "IMG-RECIEN",
            pagoId = "pago-rellaveado",
            creadaEn = "2026-09-10T10:00:00Z"
        )

        adaptador.barrerHuerfanos()

        assertEquals(1, db.paymentImageDao().getByPagoId("pago-rellaveado").size)
        assertTrue(
            telemetry.recorded.none {
                it.name == PagosTelemetria.CODE_ABONO_FOTO_BARRIDA_SIN_SUBIR
            }
        )
    }

    private suspend fun sembrarPago(id: String) = db.paymentDao().savePayment(
        PaymentEntity(
            ID = id,
            COBRADOR = "Ramirez Ortiz, Fernando",
            DOCTO_CC_ACR_ID = 77021,
            DOCTO_CC_ID = 88021,
            FECHA_HORA_PAGO = "2026-09-01T09:30:00Z",
            GUARDADO_EN_MICROSIP = false,
            IMPORTE = 220.0,
            LAT = 0.0,
            LNG = 0.0,
            CLIENTE_ID = 11486,
            COBRADOR_ID = 200,
            FORMA_COBRO_ID = 157,
            ZONA_CLIENTE_ID = 21552,
            NOMBRE_CLIENTE = "Guadalupe Hernandez"
        )
    )

    private suspend fun sembrarImagen(
        id: String,
        pagoId: String,
        creadaEn: String,
        subidaEn: String? = null
    ): File {
        val archivo = File(context.filesDir, "$PREFIJO_COMPROBANTE$id.jpg")
        archivo.writeBytes(byteArrayOf(1, 2, 3))
        db.paymentImageDao().insertAll(
            listOf(
                PaymentImageEntity(
                    ID = id,
                    PAGO_ID = pagoId,
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
