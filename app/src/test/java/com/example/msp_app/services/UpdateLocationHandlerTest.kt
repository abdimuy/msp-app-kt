package com.example.msp_app.services

import android.location.Location
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Task 3 (plan `pagos-y-visitas`): `UpdateLocationService.getCurrentLocation`
 * crasheaba sin `try/catch` cuando faltaba el permiso de ubicación. La
 * lógica vive ahora en [UpdateLocationHandler] — puro, sin `Service` ni
 * Room — así que se prueba con `runBlocking` + fakes, sin Robolectric para
 * la parte de coroutines (se hereda igual de [RobolectricTestBase] porque
 * `android.location.Location` necesita el shadow para no lanzar
 * "not mocked" en JVM puro).
 *
 * **Control de reversión (verificado de verdad, ver task-3-report.md):**
 * quitar el `catch (e: SecurityException)` de
 * [UpdateLocationHandler.fetchLocationOrNull] pone en ROJO
 * `sin permiso de ubicacion, completa sin propagar y con ubicacion en null`
 * — la excepción vuelve a propagar en vez de completar.
 *
 * Task 5: la rama de visita ya NO recibe un `enqueueVisit` — no existe forma
 * de que este handler encole una visita, ni siquiera por accidente. Ver
 * `VisitsLocalDataSourceTest` para el encolado real (en el guardado) y para
 * la prueba de que la ubicación tardía no lo duplica.
 */
class UpdateLocationHandlerTest : RobolectricTestBase() {

    private lateinit var telemetry: RecordingTelemetry
    private val paymentLocationUpdates = mutableListOf<Triple<String, Double, Double>>()
    private val visitLocationUpdates = mutableListOf<Triple<String, Double, Double>>()
    private val enqueuedPayments = mutableListOf<String>()

    @Before
    fun setUp() {
        telemetry = RecordingTelemetry()
        paymentLocationUpdates.clear()
        visitLocationUpdates.clear()
        enqueuedPayments.clear()
    }

    private fun handler() = UpdateLocationHandler(
        telemetry = telemetry,
        updatePaymentLocation = { id, lat, lng -> paymentLocationUpdates += Triple(id, lat, lng) },
        updateVisitLocation = { id, lat, lng -> visitLocationUpdates += Triple(id, lat, lng) },
        enqueuePayment = { id -> enqueuedPayments += id }
    )

    private fun fakeLocation(lat: Double, lng: Double): Location = Location("fused").apply {
        latitude = lat
        longitude = lng
    }

    // --- control de reversión: SecurityException por permiso ausente ---

    @Test
    fun `sin permiso de ubicacion, completa sin propagar y con ubicacion en null`() = runBlocking {
        handler().handle(paymentId = "pago-1", visitId = null) {
            throw SecurityException("permiso de ubicación no concedido")
        }

        assertTrue(
            "no debe persistir ubicación cuando el fetch falló",
            paymentLocationUpdates.isEmpty()
        )
        assertEquals(
            "debe encolar el pago igual (sigue sin ubicación)",
            listOf("pago-1"),
            enqueuedPayments
        )
    }

    @Test
    fun `SecurityException se reporta por Telemetry con el codigo de permiso denegado`() =
        runBlocking {
            handler().handle(paymentId = "pago-1", visitId = null) {
                throw SecurityException("permiso de ubicación no concedido")
            }

            val errors = telemetry.recorded.filter { it.type == TelemetryEventType.ERROR }
            assertEquals(1, errors.size)
            assertEquals(
                UpdateLocationHandler.ERROR_CODE_LOCATION_PERMISSION_DENIED,
                errors.single().name
            )

            // Anti-PII: el mensaje libre del SecurityException nunca debe llegar a telemetría.
            val reportedMessage = errors.single().props["message"].orEmpty()
            assertFalse(reportedMessage.contains("permiso de ubicación no concedido"))
            assertTrue(reportedMessage.contains("SecurityException"))
        }

    @Test
    fun `visita completa sin propagar cuando el permiso esta ausente`() = runBlocking {
        handler().handle(paymentId = null, visitId = "visita-1") {
            throw SecurityException("permiso de ubicación no concedido")
        }

        assertTrue(visitLocationUpdates.isEmpty())
    }

    // --- otro fallo de Play Services (no permiso) ---

    @Test
    fun `fallo generico de Play Services se reporta con su propio codigo y continua`() =
        runBlocking {
            handler().handle(paymentId = "pago-2", visitId = null) {
                throw IllegalStateException("resolution required")
            }

            val errors = telemetry.recorded.filter { it.type == TelemetryEventType.ERROR }
            assertEquals(1, errors.size)
            assertEquals(
                UpdateLocationHandler.ERROR_CODE_LOCATION_UNAVAILABLE,
                errors.single().name
            )
            assertEquals(listOf("pago-2"), enqueuedPayments)
            assertTrue(paymentLocationUpdates.isEmpty())
        }

    // --- camino feliz: se preserva ---

    @Test
    fun `con ubicacion disponible, la persiste y encola sin emitir error`() = runBlocking {
        handler().handle(paymentId = "pago-3", visitId = null) {
            fakeLocation(19.4326, -99.1332)
        }

        assertEquals(listOf(Triple("pago-3", 19.4326, -99.1332)), paymentLocationUpdates)
        assertEquals(listOf("pago-3"), enqueuedPayments)
        assertTrue(telemetry.recorded.none { it.type == TelemetryEventType.ERROR })
    }

    @Test
    fun `con ubicacion disponible para visita, solo actualiza LAT-LNG (Task 5, no encola)`() =
        runBlocking {
            handler().handle(paymentId = null, visitId = "visita-3") {
                fakeLocation(19.4326, -99.1332)
            }

            assertEquals(
                listOf(Triple("visita-3", 19.4326, -99.1332)),
                visitLocationUpdates
            )
            assertTrue(telemetry.recorded.none { it.type == TelemetryEventType.ERROR })
            // No hay ningun parametro `enqueueVisit` en este handler (Task 5):
            // es estructuralmente imposible que esta rama encole una visita.
        }

    // --- el adorno no se lleva puesto al dinero (Arreglo C, I5) ---

    /**
     * **El hallazgo I5, medido.** Escribir `LAT`/`LNG` y encolar el pago
     * compartían un `try`. Si lo primero lanzaba, el `catch` mudo se lo tragaba
     * y **el pago nunca se encolaba** — y hasta el Arreglo C este era el único
     * encolado inmediato del abono.
     *
     * Es la misma forma que la Task 11 arregló en `VisitsReconcileObserver`
     * ("si el primero lanza, el segundo nunca se registra"), que nunca había
     * llegado al camino del dinero.
     *
     * **Control de reversión:** volver a poner las dos llamadas bajo un solo
     * `try` pone este test en ROJO — `enqueuedPayments` queda vacío.
     */
    @Test
    fun `si escribir LAT-LNG truena, el pago se encola igual`() = runBlocking {
        val handler = UpdateLocationHandler(
            telemetry = telemetry,
            updatePaymentLocation = { _, _, _ -> error("room se cayo al escribir LAT/LNG") },
            updateVisitLocation = { id, lat, lng -> visitLocationUpdates += Triple(id, lat, lng) },
            enqueuePayment = { id -> enqueuedPayments += id }
        )

        handler.handle(paymentId = "pago-5", visitId = null) { fakeLocation(19.4326, -99.1332) }

        assertEquals(
            "la ubicacion es un acompanante; el dinero se encola igual",
            listOf("pago-5"),
            enqueuedPayments
        )
    }

    /**
     * **Y no se traga en silencio.** El `catch` era un `Log.e` pelado amparado
     * en el Ruling I; el Ruling Q (posterior) dejó claro que la exención de
     * `:app` no cubre código nuevo que este plan escribió, y esta clase la
     * escribió la Task 3.
     */
    @Test
    fun `el fallo de escribir LAT-LNG se reporta con su propio codigo y sin PII`() = runBlocking {
        val handler = UpdateLocationHandler(
            telemetry = telemetry,
            updatePaymentLocation = { _, _, _ ->
                error("room se cayo cobrando a Victoria Flores")
            },
            updateVisitLocation = { id, lat, lng -> visitLocationUpdates += Triple(id, lat, lng) },
            enqueuePayment = { id -> enqueuedPayments += id }
        )

        handler.handle(paymentId = "pago-6", visitId = null) { fakeLocation(19.4326, -99.1332) }

        val error = telemetry.recorded.single { it.type == TelemetryEventType.ERROR }
        assertEquals(UpdateLocationHandler.ERROR_CODE_PAYMENT_LOCATION_NOT_WRITTEN, error.name)
        val mensaje = error.props["message"].orEmpty()
        assertTrue(mensaje.contains("IllegalStateException"))
        assertFalse("anti-PII: el texto del fallo no viaja", mensaje.contains("Victoria"))
    }

    /**
     * **Control positivo del anterior:** el mismo montaje con la escritura sana
     * NO emite ningún error, así que el evento de arriba lo produce el fallo
     * inyectado y no un handler que reporta siempre.
     */
    @Test
    fun `control positivo, el mismo montaje sin fallo no reporta nada`() = runBlocking {
        handler().handle(paymentId = "pago-6", visitId = null) { fakeLocation(19.4326, -99.1332) }

        assertTrue(telemetry.recorded.none { it.type == TelemetryEventType.ERROR })
        assertEquals(listOf("pago-6"), enqueuedPayments)
    }

    /** Un encolado que truena tampoco puede quedar mudo. */
    @Test
    fun `si encolar truena, se reporta con su propio codigo`() = runBlocking {
        val handler = UpdateLocationHandler(
            telemetry = telemetry,
            updatePaymentLocation = { id, lat, lng -> paymentLocationUpdates += Triple(id, lat, lng) },
            updateVisitLocation = { id, lat, lng -> visitLocationUpdates += Triple(id, lat, lng) },
            enqueuePayment = { error("workmanager no acepto el trabajo") }
        )

        handler.handle(paymentId = "pago-7", visitId = null) { fakeLocation(19.4326, -99.1332) }

        assertEquals(
            "la ubicacion si se escribio: el fallo del encolado no la revierte",
            listOf(Triple("pago-7", 19.4326, -99.1332)),
            paymentLocationUpdates
        )
        assertEquals(
            UpdateLocationHandler.ERROR_CODE_PAYMENT_NOT_ENQUEUED,
            telemetry.recorded.single { it.type == TelemetryEventType.ERROR }.name
        )
    }

    /** Y el de la visita, que no encola nada, también reporta el suyo. */
    @Test
    fun `si escribir LAT-LNG de la visita truena, se reporta con su propio codigo`() = runBlocking {
        val handler = UpdateLocationHandler(
            telemetry = telemetry,
            updatePaymentLocation = { id, lat, lng -> paymentLocationUpdates += Triple(id, lat, lng) },
            updateVisitLocation = { _, _, _ -> error("room se cayo") },
            enqueuePayment = { id -> enqueuedPayments += id }
        )

        handler.handle(paymentId = null, visitId = "visita-5") { fakeLocation(19.4326, -99.1332) }

        assertEquals(
            UpdateLocationHandler.ERROR_CODE_VISIT_LOCATION_NOT_WRITTEN,
            telemetry.recorded.single { it.type == TelemetryEventType.ERROR }.name
        )
    }

    // --- cancelación estructurada: nunca se traga ---

    @Test(expected = CancellationException::class)
    fun `la cancelacion cooperativa se repropaga, no se traga`(): Unit = runBlocking {
        handler().handle(paymentId = "pago-4", visitId = null) {
            throw CancellationException("scope cancelado")
        }
    }
}
