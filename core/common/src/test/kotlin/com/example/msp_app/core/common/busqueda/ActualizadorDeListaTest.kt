package com.example.msp_app.core.common.busqueda

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: botón "Actualizar lista" con tope total, reloj virtual y un servidor falso lento. */
class ActualizadorDeListaTest {

    private class Lista(var contenido: List<Int>)

    /** Servidor falso: tarda [duracionMs] (virtual) y devuelve [respuesta]; anota cancelaciones. */
    private class ServidorFalso(var duracionMs: Long, var respuesta: List<Int> = listOf(1, 2, 3)) {
        var llamadas = 0
        var cancelaciones = 0
        var terminadas = 0
        suspend fun descargar(): List<Int> {
            llamadas++
            try {
                delay(duracionMs)
            } catch (e: CancellationException) {
                cancelaciones++
                throw e
            }
            terminadas++
            return respuesta
        }
    }

    private fun TestScope.actualizador(
        servidor: ServidorFalso,
        lista: Lista,
        topeMs: Long = TopeDeDescarga.MANUAL_MS,
        aplicado: Boolean = true,
        alTerminar: suspend () -> Unit = {}
    ) = ActualizadorDeLista(
        scope = this,
        topeMs = topeMs,
        descargar = { servidor.descargar() },
        aplicar = { nueva ->
            if (aplicado) lista.contenido = nueva
            aplicado
        },
        alTerminarBien = alTerminar
    )

    @Test fun `el tope manual esta en un solo lugar y vale 90 segundos`() =
        assertEquals(90_000L, TopeDeDescarga.MANUAL_MS)

    @Test fun `el tope de segundo plano es mayor que el manual`() =
        assertTrue(TopeDeDescarga.SEGUNDO_PLANO_MS > TopeDeDescarga.MANUAL_MS)

    @Test fun `exito antes del tope reemplaza la lista y avisa`() = runTest {
        val lista = Lista(listOf(9))
        val servidor = ServidorFalso(duracionMs = 10_000)
        var avisos = 0
        val a = actualizador(servidor, lista) { avisos++ }
        assertTrue(a.iniciar())
        runCurrent()
        assertTrue(a.actualizando.value)
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(listOf(1, 2, 3), lista.contenido)
        assertFalse(a.actualizando.value)
        assertNull(a.mensajeDeError.value)
        assertEquals(1, avisos)
    }

    @Test fun `vence exactamente en el tope y no antes`() = runTest {
        val lista = Lista(listOf(9))
        val servidor = ServidorFalso(duracionMs = Long.MAX_VALUE / 4)
        val a = actualizador(servidor, lista)
        a.iniciar()
        runCurrent()
        advanceTimeBy(TopeDeDescarga.MANUAL_MS - 1)
        runCurrent()
        assertTrue("a 89.999 s sigue actualizando", a.actualizando.value)
        assertNull(a.mensajeDeError.value)
        advanceTimeBy(1)
        runCurrent()
        assertFalse("a 90 s ya terminó", a.actualizando.value)
        assertEquals(TextosDeLista.ERROR_LENTA, a.mensajeDeError.value)
        assertEquals(TopeDeDescarga.MANUAL_MS, currentTime)
    }

    @Test fun `al vencer la peticion se cancela de verdad`() = runTest {
        val servidor = ServidorFalso(duracionMs = 1_000_000)
        val a = actualizador(servidor, Lista(listOf(9)))
        a.iniciar()
        advanceTimeBy(TopeDeDescarga.MANUAL_MS + 1)
        runCurrent()
        assertEquals(1, servidor.cancelaciones)
        assertEquals(0, servidor.terminadas)
    }

    @Test fun `al vencer la lista anterior queda intacta`() = runTest {
        val lista = Lista(listOf(9, 8, 7))
        val a = actualizador(ServidorFalso(1_000_000), lista)
        a.iniciar()
        advanceTimeBy(TopeDeDescarga.MANUAL_MS + 1)
        runCurrent()
        assertEquals(listOf(9, 8, 7), lista.contenido)
    }

    @Test fun `el mensaje de lentitud menciona que se puede seguir buscando`() {
        assertTrue(TextosDeLista.ERROR_LENTA.contains("la conexión está lenta"))
        assertTrue(
            TextosDeLista.ERROR_LENTA.endsWith("Puedes seguir buscando en la lista guardada")
        )
    }

    @Test fun `un segundo toque mientras corre no duplica la descarga`() = runTest {
        val servidor = ServidorFalso(duracionMs = 30_000)
        val a = actualizador(servidor, Lista(listOf(9)))
        assertTrue(a.iniciar())
        assertFalse(a.iniciar())
        assertFalse(a.iniciar())
        advanceTimeBy(30_001)
        runCurrent()
        assertEquals(1, servidor.llamadas)
    }

    @Test fun `diez toques en la misma ventana hacen una sola descarga`() = runTest {
        val servidor = ServidorFalso(duracionMs = 5_000)
        val a = actualizador(servidor, Lista(listOf(9)))
        val aceptados = (1..10).count { a.iniciar() }
        advanceUntilIdle()
        assertEquals(1, aceptados)
        assertEquals(1, servidor.llamadas)
    }

    @Test fun `terminada una descarga se puede lanzar otra`() = runTest {
        val servidor = ServidorFalso(duracionMs = 1_000)
        val a = actualizador(servidor, Lista(listOf(9)))
        a.iniciar()
        advanceUntilIdle()
        assertTrue(a.iniciar())
        advanceUntilIdle()
        assertEquals(2, servidor.llamadas)
    }

    @Test fun `tras vencer se puede reintentar y el error anterior se limpia`() = runTest {
        val servidor = ServidorFalso(duracionMs = 1_000_000)
        val lista = Lista(listOf(9))
        val a = actualizador(servidor, lista)
        a.iniciar()
        advanceUntilIdle()
        assertEquals(TextosDeLista.ERROR_LENTA, a.mensajeDeError.value)
        servidor.duracionMs = 1_000
        assertTrue(a.iniciar())
        assertNull("al reintentar el error viejo se limpia", a.mensajeDeError.value)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), lista.contenido)
        assertNull(a.mensajeDeError.value)
    }

    @Test fun `la cancelacion externa se propaga y no se vuelve mensaje de error`() = runTest {
        val servidor = ServidorFalso(duracionMs = 1_000_000)
        val hijo = TestScope(testScheduler)
        val a = ActualizadorDeLista(
            scope = hijo,
            descargar = { servidor.descargar() },
            aplicar = { true }
        )
        a.iniciar()
        runCurrent()
        hijo.cancel()
        runCurrent()
        assertEquals(1, servidor.cancelaciones)
        assertNull("cancelar no es un error que mostrar", a.mensajeDeError.value)
        assertFalse(a.actualizando.value)
    }

    @Test fun `un IOException se muestra como sin conexion`() = runTest {
        val a =
            ActualizadorDeLista<List<Int>>(
                this,
                descargar = { throw IOException("boom") },
                aplicar = { true }
            )
        a.iniciar()
        advanceUntilIdle()
        assertEquals(TextosDeLista.ERROR_SIN_CONEXION, a.mensajeDeError.value)
        assertFalse(a.actualizando.value)
    }

    @Test fun `un SocketTimeoutException del cliente HTTP se muestra como conexion lenta`() = runTest {
        val a = ActualizadorDeLista<List<Int>>(
            this,
            descargar = { throw SocketTimeoutException("read timed out") },
            aplicar = { true }
        )
        a.iniciar()
        advanceUntilIdle()
        assertEquals(TextosDeLista.ERROR_LENTA, a.mensajeDeError.value)
    }

    @Test fun `un error inesperado da el mensaje generico y no truena`() = runTest {
        val a = ActualizadorDeLista<List<Int>>(
            this,
            descargar = { error("respuesta rara") },
            aplicar = { true }
        )
        a.iniciar()
        advanceUntilIdle()
        assertEquals(TextosDeLista.ERROR_GENERICO, a.mensajeDeError.value)
    }

    @Test fun `si aplicar rechaza la lista se avisa y no se llama al final`() = runTest {
        var avisos = 0
        val lista = Lista(listOf(9))
        val a =
            actualizador(
                ServidorFalso(100, respuesta = emptyList()),
                lista,
                aplicado = false
            ) { avisos++ }
        a.iniciar()
        advanceUntilIdle()
        assertEquals(TextosDeLista.ERROR_LISTA_VACIA, a.mensajeDeError.value)
        assertEquals(0, avisos)
        assertEquals(listOf(9), lista.contenido)
    }

    @Test fun `el motivo del rechazo lo decide quien aplica`() = runTest {
        val a = ActualizadorDeLista<List<Int>>(
            this,
            descargar = { listOf(1) },
            aplicar = { false },
            mensajeSiNoSeAplica = { TextosDeLista.ERROR_LISTA_INCOMPLETA }
        )
        a.iniciar()
        advanceUntilIdle()
        assertEquals(
            "La lista recibida está incompleta. Se conserva la lista guardada",
            a.mensajeDeError.value
        )
    }

    @Test fun `un rechazo forzable ofrece forzar y forzar aplica la misma lista sin volver a descargar`() =
        runTest {
            var descargas = 0
            var forzada: List<Int>? = null
            var fin = 0
            val a = ActualizadorDeLista<List<Int>>(
                this,
                descargar = {
                    descargas++
                    listOf(1, 2, 3)
                },
                aplicar = { false },
                alTerminarBien = { fin++ },
                mensajeSiNoSeAplica = { TextosDeLista.ERROR_LISTA_INCOMPLETA },
                forzar = {
                    forzada = it
                    true
                },
                sePuedeForzar = { true }
            )
            a.iniciar()
            advanceUntilIdle()
            assertTrue(a.puedeForzar.value)
            assertTrue(a.forzarActualizacion())
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), forzada)
            assertEquals(1, descargas)
            assertEquals(1, fin)
            assertNull(a.mensajeDeError.value)
            assertFalse(a.puedeForzar.value)
            assertFalse("ya no hay lista pendiente", a.forzarActualizacion())
        }

    @Test fun `un rechazo no forzable (lista vacia) no ofrece forzar`() = runTest {
        val a = ActualizadorDeLista<List<Int>>(
            this,
            descargar = { emptyList() },
            aplicar = { false },
            forzar = { true },
            sePuedeForzar = { false }
        )
        a.iniciar()
        advanceUntilIdle()
        assertFalse(a.puedeForzar.value)
        assertFalse(a.forzarActualizacion())
        assertEquals(TextosDeLista.ERROR_LISTA_VACIA, a.mensajeDeError.value)
    }

    @Test fun `una nueva descarga o limpiar el error olvidan la lista pendiente`() = runTest {
        val a = ActualizadorDeLista<List<Int>>(
            this,
            descargar = { listOf(1) },
            aplicar = { false },
            forzar = { true },
            sePuedeForzar = { true }
        )
        a.iniciar()
        advanceUntilIdle()
        a.limpiarError()
        assertFalse(a.puedeForzar.value)
        assertFalse(a.forzarActualizacion())
    }

    @Test fun `si lo forzado tambien se rechaza el mensaje vuelve y la lista sigue pendiente`() =
        runTest {
            val a = ActualizadorDeLista<List<Int>>(
                this,
                descargar = { listOf(1) },
                aplicar = { false },
                forzar = { false },
                sePuedeForzar = { true }
            )
            a.iniciar()
            advanceUntilIdle()
            a.forzarActualizacion()
            advanceUntilIdle()
            assertEquals(TextosDeLista.ERROR_LISTA_VACIA, a.mensajeDeError.value)
            assertTrue(a.puedeForzar.value)
        }

    @Test fun `limpiarError borra el mensaje`() = runTest {
        val a = ActualizadorDeLista<List<Int>>(this, descargar = { error("x") }, aplicar = { true })
        a.iniciar()
        advanceUntilIdle()
        a.limpiarError()
        assertNull(a.mensajeDeError.value)
    }

    @Test fun `la escritura no esta bajo el tope aunque la descarga ya haya gastado casi todo`() =
        runTest {
            val lista = Lista(listOf(9))
            val servidor = ServidorFalso(duracionMs = TopeDeDescarga.MANUAL_MS - 1)
            val a = ActualizadorDeLista(
                scope = this,
                descargar = { servidor.descargar() },
                aplicar = { nueva ->
                    delay(60_000) // escribir en Room tarda; no cuenta contra el tope de la descarga
                    lista.contenido = nueva
                    true
                }
            )
            a.iniciar()
            advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), lista.contenido)
            assertNull(a.mensajeDeError.value)
        }

    @Test fun `conTopeTotal devuelve el valor si llega a tiempo`() = runTest {
        assertEquals(
            42,
            conTopeTotal(1_000) {
                delay(999)
                42
            }
        )
    }

    @Test fun `conTopeTotal lanza TiempoAgotadoException y no CancellationException`() = runTest {
        val resultado = runCatching {
            conTopeTotal(1_000) {
                delay(1_001)
                42
            }
        }.exceptionOrNull()
        assertTrue(resultado is TiempoAgotadoException)
        assertFalse(resultado is CancellationException)
        assertEquals(1_000L, (resultado as TiempoAgotadoException).topeMs)
    }

    @Test fun `conTopeTotal propaga la cancelacion externa`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val trabajo = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { conTopeTotal(1_000_000) { gate.await() } }.exceptionOrNull()
        }
        trabajo.cancel()
        val error = runCatching { trabajo.await() }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }

    @Test fun `conTopeTotal propaga la excepcion del bloque tal cual`() = runTest {
        val error = runCatching {
            conTopeTotal(
                1_000
            ) { throw IOException("socket") }
        }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
