package com.example.msp_app.core.visitas.envio

import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plan visitas P1, Tarea 13 (spec §4.6): "al abrir la app si pasaron > 24 h". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UltimaConciliacionDeVisitasTest {

    private val ahora = Instant.parse("2026-10-17T15:00:00Z")

    @Test
    fun `nunca corrio esta vencida`() = assertTrue(UltimaConciliacionDeVisitas.vencida(null, ahora))

    @Test
    fun `hace 23 h no esta vencida`() =
        assertFalse(UltimaConciliacionDeVisitas.vencida(ahora.minusSeconds(23 * 3600L), ahora))

    @Test
    fun `hace 24 h exactas esta vencida`() =
        assertTrue(UltimaConciliacionDeVisitas.vencida(ahora.minusSeconds(24 * 3600L), ahora))

    @Test
    fun `una marca en el futuro no pospone la reconciliacion`() =
        assertTrue(UltimaConciliacionDeVisitas.vencida(ahora.plusSeconds(3600L), ahora))

    @Test
    fun `anotar y leer van y vuelven`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull(UltimaConciliacionDeVisitas.leer(ctx))
        UltimaConciliacionDeVisitas.anotar(ctx, ahora)
        assertEquals(ahora, UltimaConciliacionDeVisitas.leer(ctx))
    }
}
