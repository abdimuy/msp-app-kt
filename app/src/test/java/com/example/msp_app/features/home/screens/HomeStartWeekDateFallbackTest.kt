package com.example.msp_app.features.home.screens

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DEFECTO D5 — el inicio de semana del tablero.
 *
 * **Este test estaba INVERTIDO y se invierte a propósito.** Su versión anterior fijaba, como
 * mejora, el fallback de `Home.kt`:
 * ```kotlin
 * val startInstant = initialDate?.toDate()?.toInstant() ?: AppClock.System.now()
 * ```
 * Aquel cambio sí arregló una cosa (el fallback viejo etiquetaba la hora local del dispositivo
 * como UTC y daba un instante equivocado), pero conservó el error de fondo: **cuando el
 * documento de usuario de Firestore no está disponible, la semana empieza AHORA**, y entonces
 * ningún pago pasado califica. En campo eso se vio como $0.00 cobrado en la semana con la tabla
 * de pagos llena. Que la causa fuera la ventana de fechas y no una resincronización pendiente
 * quedó probado en el mismo tablero: el contador de VENTAS (103) —que no filtra por fecha—
 * sobrevivía intacto mientras los pagos daban 0. Sólo un rango malo produce `0/103`.
 *
 * La regla nueva la fija [resolveStartWeekDate]: sin fecha de carga NO hay semana (devuelve
 * `null`), y el tablero lo dice ([START_WEEK_UNKNOWN_LABEL]) en vez de calcular sobre una
 * ventana inventada. Si alguien reintroduce cualquier fallback —`now()`, "hoy", o el instante
 * del dispositivo— estos tests se ponen en rojo.
 */
class HomeStartWeekDateFallbackTest {

    // Instante "real" que leería el reloj del dispositivo durante el test.
    private val fixedInstant: Instant = Instant.parse("2026-08-08T14:30:00Z")

    @Test
    fun `sin fecha de carga NO hay inicio de semana - ni ahora ni hoy`() {
        val resultado = resolveStartWeekDate(null)

        assertNull("sin FECHA_CARGA_INICIAL no se puede inventar una semana", resultado)
        // Dicho también como propiedad, por si alguien cambia el tipo de retorno: sea lo que
        // sea, no puede coincidir con el instante actual ni con la medianoche de hoy.
        val clock = FakeClock(fixedInstant)
        assertNotEquals(AppTime.toWireFormat(clock.now()), resultado)
        assertNotEquals(
            AppTime.toWireFormat(AppTime.startOfDay(AppTime.todayInBusinessZone(clock))),
            resultado
        )
    }

    @Test
    fun `con fecha de carga devuelve ese instante exacto en wire UTC`() {
        val carga = Instant.parse("2026-08-03T16:00:00Z")

        assertEquals("2026-08-03T16:00:00Z", resolveStartWeekDate(carga))
    }

    @Test
    fun `el resultado no depende de la zona horaria del dispositivo`() {
        val original = TimeZone.getDefault()
        try {
            val carga = Instant.parse("2026-08-03T16:00:00Z")

            TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City")) // UTC-6
            val bajoCdmx = resolveStartWeekDate(carga)

            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
            val bajoKiritimati = resolveStartWeekDate(carga)

            assertEquals("2026-08-03T16:00:00Z", bajoCdmx)
            assertEquals(bajoCdmx, bajoKiritimati)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `el aviso de semana desconocida cumple las reglas de texto del proyecto`() {
        // es-MX, minúsculas, sin punto final, 2-4 palabras, y NUNCA la palabra "ciclo".
        assertEquals(START_WEEK_UNKNOWN_LABEL, START_WEEK_UNKNOWN_LABEL.lowercase())
        assertEquals(false, START_WEEK_UNKNOWN_LABEL.endsWith("."))
        assertEquals(4, START_WEEK_UNKNOWN_LABEL.split(" ").size)
        assertEquals(false, START_WEEK_UNKNOWN_LABEL.contains("ciclo"))
        assertEquals(true, START_WEEK_UNKNOWN_LABEL.contains("semana"))
    }
}
