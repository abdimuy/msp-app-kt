package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.testing.time.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Test

private const val AHORA = 10_000_000L

/**
 * Casos con nombre propio de [evaluarCorregibilidad]: los controles positivos pedidos por el
 * dueño y las fronteras del arrendamiento vistas a través de la función completa (no sólo de
 * [Reclamo] — ver `ReclamoDeEdicionTest` para lo mismo aislado). La tabla de precedencia
 * exhaustiva vive en `EstadoCorreccionExhaustivoTest` (barrido paramétrico).
 */
class EstadoCorreccionTest {

    // ── Control positivo de la rama `enviado` (regla del dueño) ─────────
    // El nivel 2 le cambió el destino a esta rama: antes producía `YaSeEnvio`,
    // ahora `CorregibleEnviada`. El par de casos de abajo es el mismo control
    // positivo de siempre, re-apuntado: dos llamadas que difieren SÓLO en
    // `enviado`, con todo lo demás limpio. Si alguien borra la rama
    // `if (enviado)`, la función cae al chequeo de `permanente`/candado y el
    // primer caso pasa a dar `Corregible` — que es exactamente lo que da el
    // segundo. Ninguna otra combinación produce `CorregibleEnviada` por
    // casualidad. Y de paso es la prueba de que `YaSeEnvio` ya NO sale de
    // aquí: un regreso silencioso al comportamiento del nivel 1 dejaría al
    // dueño otra vez sin salida sobre su propia venta.

    @Test
    fun `evaluar devuelve CorregibleEnviada solo si enviado, nunca YaSeEnvio`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = false,
            correccionNoEnviada = false,
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.CorregibleEnviada, estado)
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
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.Corregible, estadoSinCandadoNiPermanente)
    }

    // ── Control positivo de la marca terminal (paso 1, gana sobre TODO) ──

    @Test
    fun `la marca terminal gana sobre la cola, el candado vivo y el fallo permanente`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = true,
            correccionNoEnviada = true,
            correccionRemotaPendiente = true,
            correccionRemotaEstado = CorreccionRemotaTerminal.RECHAZADA_ESTADO,
            claimKind = "UPLOAD",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.LaOficinaYaLaAplico, estado)
    }

    @Test
    fun `el mismo caso sin la marca terminal cae en CorreccionEnCamino (necesidad del paso 1)`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = true,
            correccionNoEnviada = true,
            correccionRemotaPendiente = true,
            correccionRemotaEstado = null,
            claimKind = "UPLOAD",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.CorreccionEnCamino, estado)
    }

    // ── Control positivo de la cola (paso 2, gana sobre la divergencia) ──

    @Test
    fun `la correccion en cola gana sobre la divergencia marcada`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = false,
            correccionNoEnviada = true,
            correccionRemotaPendiente = true,
            correccionRemotaEstado = null,
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.CorreccionEnCamino, estado)
    }

    @Test
    fun `el mismo caso sin la cola cae en LaRevisaLaOficina (necesidad del paso 2)`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = false,
            correccionNoEnviada = true,
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.LaRevisaLaOficina, estado)
    }

    /**
     * [esCorreccionRemotaTerminal] reconoce CUALQUIER valor no vacío, no sólo los dos de
     * [CorreccionRemotaTerminal.CONOCIDOS] — la dirección segura: un valor que todavía no
     * conocemos también cerró la puerta. Visto a través de la función completa, no sólo del
     * predicado aislado.
     */
    @Test
    fun `una marca terminal desconocida tambien cierra la puerta`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = false,
            correccionNoEnviada = false,
            correccionRemotaPendiente = false,
            correccionRemotaEstado = "UN_ESTADO_QUE_TODAVIA_NO_EXISTE",
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.LaOficinaYaLaAplico, estado)
    }

    @Test
    fun `una marca en blanco no es terminal, la venta enviada sigue siendo corregible`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = false,
            correccionNoEnviada = false,
            correccionRemotaPendiente = false,
            correccionRemotaEstado = "   ",
            claimKind = null,
            claimedAt = null,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.CorregibleEnviada, estado)
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
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
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
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
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
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
            claimKind = "EDIT",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.Corregible, estado)
    }

    // ── Precedencia: enviado manda sobre el candado y sobre permanente ───

    @Test
    fun `enviado manda incluso con un candado UPLOAD vivo y permanente en true`() {
        val estado = evaluarCorregibilidad(
            enviado = true,
            permanente = true,
            correccionNoEnviada = false,
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
            claimKind = "UPLOAD",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.CorregibleEnviada, estado)
    }

    @Test
    fun `sin enviar, el fallo permanente manda sobre un candado UPLOAD vivo`() {
        val estado = evaluarCorregibilidad(
            enviado = false,
            permanente = true,
            correccionNoEnviada = false,
            correccionRemotaPendiente = false,
            correccionRemotaEstado = null,
            claimKind = "UPLOAD",
            claimedAt = AHORA,
            ahora = AHORA
        )

        assertEquals(EstadoCorreccion.LaRevisaLaOficina, estado)
    }
}
