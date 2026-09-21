package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.testing.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [tipoCandadoDe] y [Reclamo.estaVivo] — sin Room, sin reloj real (siempre
 * [FakeClock]). La frontera se prueba en su propio milisegundo EXACTO,
 * espejo de la comparación `CLAIMED_AT <= :now - :leaseMs` de `LocalSaleDao`.
 */
class ReclamoDeEdicionTest {

    // ── tipoCandadoDe ────────────────────────────────────────────────────

    @Test
    fun `tipoCandadoDe reconoce EDIT y UPLOAD`() {
        assertEquals(TipoCandado.EDIT, tipoCandadoDe("EDIT"))
        assertEquals(TipoCandado.UPLOAD, tipoCandadoDe("UPLOAD"))
    }

    @Test
    fun `tipoCandadoDe trata null, vacio y desconocido como sin candado reconocible`() {
        assertNull(tipoCandadoDe(null))
        assertNull(tipoCandadoDe(""))
        assertNull(tipoCandadoDe("edit")) // minúscula: no es el valor exacto que escribe el DAO
        assertNull(tipoCandadoDe("BORRADO"))
    }

    // ── Reclamo.estaVivo — sin candado ──────────────────────────────────

    @Test
    fun `sin kind no esta vivo aunque haya claimedAt`() {
        val reclamo = Reclamo(kind = null, claimedAt = 1_000L)
        assertFalse(reclamo.estaVivo(ahora = 1_000L))
    }

    @Test
    fun `sin claimedAt no esta vivo aunque haya kind`() {
        val reclamo = Reclamo(kind = TipoCandado.EDIT, claimedAt = null)
        assertFalse(reclamo.estaVivo(ahora = 1_000L))
    }

    // ── Reclamo.estaVivo — frontera EDIT (30 min), con FakeClock ────────

    @Test
    fun `candado EDIT vence exactamente en su propio arrendamiento`() {
        val clock = FakeClock.at("2026-04-15T18:00:00Z")
        val claimedAt = clock.now().toEpochMilli()
        clock.advance(java.time.Duration.ofMillis(LocalSaleClaimLeases.EDIT_LEASE_MS))
        val ahora = clock.now().toEpochMilli()

        val reclamo = Reclamo(kind = TipoCandado.EDIT, claimedAt = claimedAt)

        assertFalse("vence EN el milisegundo exacto del arrendamiento", reclamo.estaVivo(ahora))
    }

    @Test
    fun `candado EDIT sigue vivo un milisegundo antes de vencer`() {
        val clock = FakeClock.at("2026-04-15T18:00:00Z")
        val claimedAt = clock.now().toEpochMilli()
        clock.advance(java.time.Duration.ofMillis(LocalSaleClaimLeases.EDIT_LEASE_MS - 1))
        val ahora = clock.now().toEpochMilli()

        val reclamo = Reclamo(kind = TipoCandado.EDIT, claimedAt = claimedAt)

        assertTrue(reclamo.estaVivo(ahora))
    }

    // ── Reclamo.estaVivo — frontera UPLOAD (180 s), con FakeClock ───────

    @Test
    fun `candado UPLOAD vence exactamente en su propio arrendamiento`() {
        val clock = FakeClock.at("2026-04-15T18:00:00Z")
        val claimedAt = clock.now().toEpochMilli()
        clock.advance(java.time.Duration.ofMillis(LocalSaleClaimLeases.UPLOAD_LEASE_MS))
        val ahora = clock.now().toEpochMilli()

        val reclamo = Reclamo(kind = TipoCandado.UPLOAD, claimedAt = claimedAt)

        assertFalse("vence EN el milisegundo exacto del arrendamiento", reclamo.estaVivo(ahora))
    }

    @Test
    fun `candado UPLOAD sigue vivo un milisegundo antes de vencer`() {
        val clock = FakeClock.at("2026-04-15T18:00:00Z")
        val claimedAt = clock.now().toEpochMilli()
        clock.advance(java.time.Duration.ofMillis(LocalSaleClaimLeases.UPLOAD_LEASE_MS - 1))
        val ahora = clock.now().toEpochMilli()

        val reclamo = Reclamo(kind = TipoCandado.UPLOAD, claimedAt = claimedAt)

        assertTrue(reclamo.estaVivo(ahora))
    }

    @Test
    fun `los dos arrendamientos son distintos entre si`() {
        // Si alguien colapsara ambos a un solo valor, EDIT y UPLOAD vencerían
        // al mismo tiempo — este test lo haría evidente de inmediato.
        assertTrue(LocalSaleClaimLeases.EDIT_LEASE_MS != LocalSaleClaimLeases.UPLOAD_LEASE_MS)
    }
}
