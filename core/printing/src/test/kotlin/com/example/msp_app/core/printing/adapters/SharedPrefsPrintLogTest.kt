package com.example.msp_app.core.printing.adapters

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El registro de impresiones sobre `SharedPreferences`: que una segunda
 * impresión se detecte como reimpresión, que la fecha de la PRIMERA copia no se
 * pise, y que el registro **sobreviva a que la app se reinicie** — que es lo que
 * hace que la detección sirva de algo.
 *
 * Mismo bring-up que [PreferredPrinterRepositoryTest] (plain [Application], sin
 * grafo de DI).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SharedPrefsPrintLogTest {

    private val telemetry = RecordingTelemetry()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val log = SharedPrefsPrintLog(context, telemetry)

    private val ticket = "9f1c6a54-pago-guadalupe"
    private val primera = Instant.parse("2026-09-04T16:30:00Z")

    @Test
    fun `un ticket nunca impreso no tiene registro`() {
        assertNull(log.find(ticket))
    }

    @Test
    fun `la primera impresion queda con conteo uno`() {
        val registro = log.record(ticket, primera)

        assertEquals(1, registro.prints)
        assertEquals(primera, registro.firstPrintedAt)
        assertEquals(registro, log.find(ticket))
    }

    @Test
    fun `la segunda impresion se detecta como reimpresion`() {
        log.record(ticket, primera)

        val segunda = log.record(ticket, primera.plusSeconds(SEIS_MINUTOS))

        assertEquals(2, segunda.prints)
        // La PRIMERA vez no se pisa: es la que dice qué copia salió antes.
        assertEquals(primera, segunda.firstPrintedAt)
    }

    @Test
    fun `el registro sobrevive a que la app se reinicie`() {
        log.record(ticket, primera)

        // Otra instancia = otro proceso: lee el mismo archivo de prefs.
        val trasReinicio = SharedPrefsPrintLog(context, RecordingTelemetry()).find(ticket)

        assertNotNull(trasReinicio)
        assertEquals(1, trasReinicio?.prints)
        assertEquals(primera, trasReinicio?.firstPrintedAt)
    }

    @Test
    fun `dos tickets distintos no se pisan`() {
        val otro = "3ab7-visita-hermosillo"
        log.record(ticket, primera)
        log.record(ticket, primera.plusSeconds(SEIS_MINUTOS))

        val registroDelOtro = log.record(otro, primera)

        assertEquals(1, registroDelOtro.prints)
        assertEquals(2, log.find(ticket)?.prints)
    }

    @Test
    fun `una fecha ilegible degrada a sin registro y NO se traga`() {
        // Se ensucia el archivo a mano: es el disco corrupto/el formato viejo.
        context.getSharedPreferences("print_log_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt("$ticket.conteo", 1)
            .putString("$ticket.primera", "no-es-una-fecha")
            .commit()

        assertNull(log.find(ticket))

        val errores = telemetry.recorded.filter { it.type == TelemetryEventType.ERROR }
        assertEquals(
            listOf(PrintLogTelemetry.CODE_PRINT_LOG_FECHA_ILEGIBLE),
            errores.map { it.name }
        )
        // Anti-PII: viaja la CLASE de la excepción, nunca el texto crudo.
        assertEquals(
            "DateTimeParseException",
            errores.single().props[PrintLogTelemetry.PROP_EXCEPCION]
        )
    }

    @Test
    fun `el camino feliz no emite ningun error`() {
        // Control positivo del test de arriba: el grabador SÍ ve errores cuando
        // los hay (lo prueba `una fecha ilegible...`), así que esta ausencia
        // significa algo.
        log.record(ticket, primera)
        log.find(ticket)

        assertTrue(telemetry.recorded.none { it.type == TelemetryEventType.ERROR })
    }

    private companion object {
        const val SEIS_MINUTOS = 360L
    }
}
