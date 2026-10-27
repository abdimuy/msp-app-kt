package com.example.msp_app.core.avisos

import com.example.msp_app.core.common.visitas.domain.EstadoTarea
import com.example.msp_app.core.database.entities.TareaZonaEntity
import com.example.msp_app.core.testing.RoomTestBase
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-123 · Aviso inmediato "Visita asignada" (spec visitas §6.5; plan P3
 * Tarea 12) contra Room de verdad: la idempotencia vive en `aviso_emitido`.
 */
class AvisoInmediatoTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-10-27T15:00:00Z")
    private val hoy = LocalDate.parse("2026-10-27")

    private class Salida {
        val avisos = mutableListOf<Triple<String, String, Int>>()
        val permisos = mutableListOf<Boolean>()
    }

    private fun aviso(
        salida: Salida,
        permitido: Boolean = true,
        notificar: (String, String, Int) -> Unit = { t, x, i -> salida.avisos += Triple(t, x, i) }
    ) = AvisoInmediato(
        dao = db.avisosDao(),
        notificar = notificar,
        permitido = { permitido },
        ahora = { ahora },
        hoy = { hoy },
        etiqueta = { "CV-48213" },
        reportarPermiso = { salida.permisos += it }
    )

    private fun tarea(id: String = ID_1, fecha: String = "2026-10-28", hora: String? = null) =
        TareaZonaEntity(
            ID = id,
            VENTA_ID = 91027,
            CLIENTE_ID = 30144,
            ZONA_CLIENTE_ID = 21,
            FECHA = fecha,
            HORA = hora,
            NOTA = "Pasar por la tarde",
            REEMPLAZA_A = null,
            CREATED_AT = "2026-10-27T14:00:00Z",
            UPDATED_AT = "2026-10-27T14:00:00Z",
            CANCELADA_AT = null
        )

    private fun pendiente(vararg t: TareaZonaEntity) =
        t.associate { it.ID to EstadoTarea.PENDIENTE }

    @Test
    fun `mismaTareaPorTresCaminosNotificaUnaVez - E-APP-123`() = runTest {
        val s = Salida()
        val a = aviso(s)
        val t = tarea()
        repeat(3) { a.alAplicar(listOf(t), pendiente(t)) }

        assertEquals(1, s.avisos.size)
        assertEquals("Visita asignada", s.avisos.single().first)
        assertEquals("CV-48213 · Mañana", s.avisos.single().second)
        assertTrue(db.avisosDao().emitido(ID_1))
    }

    @Test
    fun `la idempotencia sobrevive a otra instancia - E-APP-123`() = runTest {
        val s = Salida()
        val t = tarea()
        aviso(s).alAplicar(listOf(t), pendiente(t))
        aviso(s).alAplicar(listOf(t), pendiente(t))
        assertEquals(1, s.avisos.size)
    }

    @Test
    fun `canceladaNoNotifica - E-APP-123`() = runTest {
        val s = Salida()
        val t = tarea()
        aviso(s).alAplicar(listOf(t), mapOf(ID_1 to EstadoTarea.CANCELADA))
        assertTrue(s.avisos.isEmpty())
        assertFalse(db.avisosDao().emitido(ID_1))
    }

    @Test
    fun `atendidaNoNotifica porque el cobrador ya visito - E-APP-123`() = runTest {
        val s = Salida()
        val t = tarea()
        aviso(s).alAplicar(listOf(t), mapOf(ID_1 to EstadoTarea.ATENDIDA))
        assertTrue(s.avisos.isEmpty())
        assertFalse(db.avisosDao().emitido(ID_1))
    }

    @Test
    fun `no atendida y sin estado tampoco notifican - E-APP-123`() = runTest {
        val s = Salida()
        val a = aviso(s)
        a.alAplicar(listOf(tarea()), mapOf(ID_1 to EstadoTarea.NO_ATENDIDA))
        a.alAplicar(listOf(tarea(ID_2)), emptyMap())
        assertTrue(s.avisos.isEmpty())
    }

    @Test
    fun `notificacionesNegadasNoRompenElSync - E-APP-123`() = runTest {
        val s = Salida()
        val t = tarea()
        aviso(s, permitido = false).alAplicar(listOf(t), pendiente(t))

        assertTrue(s.avisos.isEmpty())
        assertTrue("marca emitido igual: no se reintenta", db.avisosDao().emitido(ID_1))
        assertEquals(listOf(false), s.permisos)
        // Si luego conceden el permiso, la tarea vieja no avisa.
        aviso(s).alAplicar(listOf(t), pendiente(t))
        assertTrue(s.avisos.isEmpty())
    }

    @Test
    fun `si notificar revienta marca emitido y las demas siguen - E-APP-123`() = runTest {
        val s = Salida()
        var primera = true
        val a = aviso(s) { t, x, i ->
            if (primera) {
                primera = false
                error("sin canal")
            }
            s.avisos += Triple(t, x, i)
        }
        val t1 = tarea(ID_1)
        val t2 = tarea(ID_2)
        a.alAplicar(listOf(t1, t2), pendiente(t1, t2))

        assertEquals(1, s.avisos.size)
        assertTrue(db.avisosDao().emitido(ID_1))
        a.alAplicar(listOf(t1), pendiente(t1))
        assertEquals("no se reintenta", 1, s.avisos.size)
    }

    @Test
    fun `idDeNotificacionEstable - E-APP-123`() {
        assertEquals(idDeNotificacion(ID_1), idDeNotificacion(ID_1))
        assertTrue(idDeNotificacion(ID_1) != idDeNotificacion(ID_2))
    }

    @Test
    fun `el texto dice el dia y la hora sin punto final - E-APP-123`() {
        assertEquals("CV-48213 · Hoy", textoDeAviso("CV-48213", tarea(fecha = "2026-10-27"), hoy))
        assertEquals(
            "CV-48213 · Mañana 10:30",
            textoDeAviso("CV-48213", tarea(hora = "10:30"), hoy)
        )
        assertEquals(
            "CV-48213 · 02/11/2026",
            textoDeAviso("CV-48213", tarea(fecha = "2026-11-02"), hoy)
        )
        // La nota de la oficina no sale en la pantalla bloqueada.
        assertFalse(textoDeAviso("CV-48213", tarea(), hoy).contains("tarde"))
    }

    private companion object {
        const val ID_1 = "7b0c3a52-1d11-4f2a-9c1e-5e1d2f3a4b61"
        const val ID_2 = "7b0c3a52-1d11-4f2a-9c1e-5e1d2f3a4b62"
    }
}
