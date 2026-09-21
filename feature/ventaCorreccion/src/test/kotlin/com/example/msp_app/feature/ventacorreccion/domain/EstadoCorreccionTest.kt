package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.testing.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Test

private const val AHORA = 10_000_000L

/**
 * Casos con nombre propio de [evaluarCorregibilidad]: el control positivo
 * pedido por el dueño y las fronteras del arrendamiento vistas a través de
 * la función completa (no sólo de [Reclamo] — ver `ReclamoDeEdicionTest`
 * para lo mismo aislado). La tabla de precedencia exhaustiva vive en
 * `EstadoCorreccionExhaustivoTest` (barrido paramétrico).
 */
class EstadoCorreccionTest {

    // ── Control positivo (regla del dueño) ──────────────────────────────
    // Construye el ÚNICO caso que pasa con la rama `if (enviado)` puesta:
    // enviado=true, sin la marca de divergencia, sin permanente y SIN
    // candado. Si alguien borra esa rama (la función cae directo al chequeo
    // de `permanente`/candado), este caso concreto deja de dar `YaSeEnvio`
    // — ya no hay ninguna otra combinación que produzca ese resultado por
    // casualidad, porque `permanente=false` y `claimKind=null` por sí solos
    // producen `Corregible`, nunca `YaSeEnvio`.

    @Test
    fun `evaluar devuelve YaSeEnvio solo si enviado`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = false,
            correccionNoEnviada = false,
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.YaSeEnvio, estado)
    }

    @Test
    fun `sin la rama enviado, el mismo caso cae en Corregible (prueba la necesidad de la rama)`() {
        // Este test documenta EXACTAMENTE lo que pasaría si se borrara la
        // rama `if (enviado)`: los mismos parámetros de "permanente=false,
        // sin candado" sin pasar por esa rama.
        val estadoSinCandadoNiPermanente = evaluarCorregibilidad(
            enviado = false,
            permanente = false,
            correccionNoEnviada = false,
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.Corregible, estadoSinCandadoNiPermanente)
    }

    // ── Frontera del arrendamiento de SUBIDA vista a través de la función ─

    @Test
    fun `SeEstaEnviando se pierde exactamente en el arrendamiento de subida`() {
        val clock = FakeClock.at("2026-04-15T18:00:00Z")
        val claimedAt = clock.now().toEpochMilli()
        clock.advance(java.time.Duration.ofMillis(LocalSaleClaimLeases.UPLOAD_LEASE_MS))

        val estado = evaluarCorregibilidad(
            enviado = false,
            permanente = false,
            correccionNoEnviada = false,
            claimKind = "UPLOAD",
            claimedAt = claimedAt,
            ahora = clock.now().toEpochMilli()
        )

        assertEquals(EstadoCorreccion.Corregible, estado)
    }

    @Test
    fun `SeEstaEnviando se mantiene un milisegundo antes de vencer`() {
        val clock = FakeClock.at("2026-04-15T18:00:00Z")
        val claimedAt = clock.now().toEpochMilli()
        clock.advance(java.time.Duration.ofMillis(LocalSaleClaimLeases.UPLOAD_LEASE_MS - 1))

        val estado = evaluarCorregibilidad(
            enviado = false,
            permanente = false,
            correccionNoEnviada = false,
            claimKind = "UPLOAD",
            claimedAt = claimedAt,
            ahora = clock.now().toEpochMilli()
        )

        assertEquals(EstadoCorreccion.SeEstaEnviando, estado)
    }

    // ── Un candado EDIT vivo es Corregible (decisión explícita del dueño) ─

    @Test
    fun `un candado EDIT vivo es Corregible, no bloquea al dueño de su propia venta`() {
        // claimedAt = AHORA: recién tomado, claramente vivo.
        val estado = evaluarCorregibilidad(
            enviado = false,
            permanente = false,
            correccionNoEnviada = false,
            claimKind = "EDIT",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.Corregible, estado)
    }

    // ── Precedencia: enviado manda sobre TODO, incluido un candado vivo ──

    @Test
    fun `enviado manda incluso con un candado UPLOAD vivo y permanente en true`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = true,
            correccionNoEnviada = false,
            claimKind = "UPLOAD",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.YaSeEnvio, estado)
    }

    @Test
    fun `sin enviar, el fallo permanente manda sobre un candado UPLOAD vivo`() {
        val estado = evaluarCorregibilidad(
            enviado = false,
            permanente = true,
            correccionNoEnviada = false,
            claimKind = "UPLOAD",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.LaRevisaLaOficina, estado)
    }
}
