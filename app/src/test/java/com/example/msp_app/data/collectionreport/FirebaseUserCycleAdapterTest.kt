package com.example.msp_app.data.collectionreport

import com.example.msp_app.data.models.auth.User
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cobertura del adapter real de [com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort]
 * (Plan 5, Task 10). Fakes-only (sin MockK): se inyecta el seam `fetchUser` que en producción lee
 * Firestore. Se ejercita el contrato de mapeo `FECHA_CARGA_INICIAL: Timestamp -> Instant` y `NOMBRE`,
 * y la degradación (ausencia de usuario / campos nulos / fallo de la fuente NO tumban el reporte).
 */
class FirebaseUserCycleAdapterTest {

    /** Seam grabador: cuenta llamadas y devuelve el usuario (o lanza) programado. */
    private class FakeFetch(
        var user: User? = null,
        var throwable: Throwable? = null
    ) {
        var calls: Int = 0
            private set

        val fetch: suspend () -> User? = {
            calls++
            throwable?.let { throw it }
            user
        }
    }

    private val cycleStart: Instant = Instant.parse("2026-08-01T00:00:00Z")

    private fun userWith(nombre: String = "Gabriel Roque", fecha: Instant? = cycleStart): User =
        User(
            NOMBRE = nombre,
            FECHA_CARGA_INICIAL = fecha?.let { Timestamp(Date.from(it)) }
        )

    // ─── fechaCargaInicial ────────────────────────────────────────────────────

    @Test
    fun `fechaCargaInicial mapea el Timestamp del usuario a Instant UTC exacto`() = runTest {
        val fetch = FakeFetch(user = userWith(fecha = cycleStart))
        val adapter = FirebaseUserCycleAdapter(fetchUser = fetch.fetch)

        assertEquals(cycleStart, adapter.fechaCargaInicial())
        assertEquals(1, fetch.calls)
    }

    @Test
    fun `fechaCargaInicial es null cuando no hay usuario`() = runTest {
        val adapter = FirebaseUserCycleAdapter(fetchUser = FakeFetch(user = null).fetch)

        assertNull(adapter.fechaCargaInicial())
    }

    @Test
    fun `fechaCargaInicial es null cuando FECHA_CARGA_INICIAL es null`() = runTest {
        val adapter =
            FirebaseUserCycleAdapter(fetchUser = FakeFetch(user = userWith(fecha = null)).fetch)

        assertNull(adapter.fechaCargaInicial())
    }

    @Test
    fun `fechaCargaInicial degrada a null si la fuente falla`() = runTest {
        val fetch = FakeFetch(throwable = IllegalStateException("firestore caído"))
        val adapter = FirebaseUserCycleAdapter(fetchUser = fetch.fetch)

        assertNull(adapter.fechaCargaInicial())
        assertEquals(1, fetch.calls)
    }

    // ─── cobradorNombre ───────────────────────────────────────────────────────

    @Test
    fun `cobradorNombre devuelve el NOMBRE del usuario`() = runTest {
        val adapter = FirebaseUserCycleAdapter(
            fetchUser = FakeFetch(user = userWith(nombre = "Minerva López")).fetch
        )

        assertEquals("Minerva López", adapter.cobradorNombre())
    }

    @Test
    fun `cobradorNombre es cadena vacía cuando no hay usuario`() = runTest {
        val adapter = FirebaseUserCycleAdapter(fetchUser = FakeFetch(user = null).fetch)

        assertEquals("", adapter.cobradorNombre())
    }

    @Test
    fun `cobradorNombre es cadena vacía cuando el NOMBRE está en blanco`() = runTest {
        val adapter = FirebaseUserCycleAdapter(
            fetchUser = FakeFetch(user = userWith(nombre = "   ")).fetch
        )

        assertEquals("", adapter.cobradorNombre())
    }

    @Test
    fun `cobradorNombre degrada a cadena vacía si la fuente falla`() = runTest {
        val fetch = FakeFetch(throwable = RuntimeException("sin red"))
        val adapter = FirebaseUserCycleAdapter(fetchUser = fetch.fetch)

        assertEquals("", adapter.cobradorNombre())
        assertEquals(1, fetch.calls)
    }
}
