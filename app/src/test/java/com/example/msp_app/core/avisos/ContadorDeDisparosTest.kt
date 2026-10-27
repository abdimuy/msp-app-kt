package com.example.msp_app.core.avisos

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** E-APP-134 · Alarmas disparadas en las últimas 24 h (ventana móvil). */
@RunWith(RobolectricTestRunner::class)
class ContadorDeDisparosTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val t0 = Instant.parse("2026-10-27T15:00:00Z")

    private fun contador() = ContadorDeDisparos(
        context.getSharedPreferences("prueba_disparos", Context.MODE_PRIVATE)
    )

    @Test
    fun `alarmaDisparadaSuma - E-APP-134`() {
        val c = contador()
        c.registrar(t0)
        c.registrar(t0.plusSeconds(60))
        assertEquals(2, c.ultimas24h(t0.plusSeconds(120)))
    }

    @Test
    fun `la ventana es movil y lo viejo sale - E-APP-134`() {
        val c = contador()
        c.registrar(t0)
        c.registrar(t0.plus(Duration.ofHours(23)))
        assertEquals(2, c.ultimas24h(t0.plus(Duration.ofHours(23))))
        assertEquals(1, c.ultimas24h(t0.plus(Duration.ofHours(25))))
        assertEquals(0, c.ultimas24h(t0.plus(Duration.ofHours(48))))
    }
}
