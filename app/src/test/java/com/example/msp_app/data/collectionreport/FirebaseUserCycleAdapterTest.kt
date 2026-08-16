package com.example.msp_app.data.collectionreport

import com.example.msp_app.data.models.auth.User
import com.example.msp_app.feature.collectionreport.domain.port.CycleStart
import com.google.firebase.Timestamp
import java.time.Instant
import java.util.Date
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    // ─── cycleStart ───────────────────────────────────────────────────────────

    @Test
    fun `cycleStart mapea el Timestamp del usuario a Instant UTC exacto`() = runTest {
        val fetch = FakeFetch(user = userWith(fecha = cycleStart))
        val adapter = FirebaseUserCycleAdapter(fetchUser = fetch.fetch)

        assertEquals(CycleStart.Known(cycleStart), adapter.cycleStart())
        assertEquals(1, fetch.calls)
    }

    @Test
    fun `cycleStart es Missing cuando no hay usuario`() = runTest {
        val adapter = FirebaseUserCycleAdapter(fetchUser = FakeFetch(user = null).fetch)

        assertEquals(CycleStart.Missing, adapter.cycleStart())
    }

    @Test
    fun `cycleStart es Missing cuando FECHA_CARGA_INICIAL es null`() = runTest {
        val adapter =
            FirebaseUserCycleAdapter(fetchUser = FakeFetch(user = userWith(fecha = null)).fetch)

        assertEquals(CycleStart.Missing, adapter.cycleStart())
    }

    /**
     * DEFECTO D5, la mitad que vivía aquí: el adapter degradaba **cualquier** excepción de
     * Firestore a `null`, y ese `null` era indistinguible de "el cobrador no ha iniciado su
     * semana". Aguas abajo, el rango de la semana caía al día de hoy y el tablero mostraba $0.00
     * con la tabla de pagos llena — sin aviso y sin repararse solo.
     *
     * La degradación se conserva (el reporte se alimenta de Room, no de este puerto); lo que
     * cambia es que ahora es DISTINGUIBLE y REINTENTABLE. Si alguien vuelve a colapsar el fallo
     * con la ausencia, este test se pone en rojo.
     */
    @Test
    fun `cycleStart degrada a Unavailable (reintentable) si la fuente falla, no a Missing`() =
        runTest {
            val fetch = FakeFetch(throwable = IllegalStateException("firestore caído"))
            val adapter = FirebaseUserCycleAdapter(fetchUser = fetch.fetch)

            val resultado = adapter.cycleStart()

            assertEquals(CycleStart.Unavailable, resultado)
            assertNotEquals(CycleStart.Missing, resultado)
            assertNull(resultado.instantOrNull)
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
