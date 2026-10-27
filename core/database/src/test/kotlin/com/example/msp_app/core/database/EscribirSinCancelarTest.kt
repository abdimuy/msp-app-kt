package com.example.msp_app.core.database

import androidx.room.withTransaction
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * E-APP-084: el helper de la escritura de dinero. Cancelar el job a media
 * transacción no la corta, y un bloque que terminó devuelve su resultado aunque
 * la cancelación llegue tarde (la carrera de E-APP-078, forzada con
 * `UnconfinedTestDispatcher`).
 */
class EscribirSinCancelarTest : RoomTestBase() {

    private fun marcador(id: String) = CapturaEnCursoEntity(
        ID = id,
        TIPO = CapturaEnCursoEntity.TIPO_ABONO,
        LLAVE = "1",
        USUARIO = "rosa@example.com",
        ESTADO = CapturaEnCursoEntity.CAPTURADA,
        RESUMEN = "{}",
        CREADA_EN = "2026-10-20T10:00:00Z",
        ACTUALIZADA_EN = "2026-10-20T10:00:00Z"
    )

    @Test
    fun `E-APP-084 cancelada dentro de la transaccion la escritura termina y devuelve su resultado`() =
        runTest {
            lateinit var job: Job
            var resultado: String? = null
            job = launch(UnconfinedTestDispatcher(testScheduler)) {
                resultado = db.escribirSinCancelar {
                    db.capturaEnCursoDao().capturar(marcador("a"))
                    job.cancel()
                    db.capturaEnCursoDao().marcarEscrita("a", "2026-10-20T10:00:01Z")
                    "escrita"
                }
            }
            job.join()

            assertEquals("escrita", resultado)
            assertEquals(CapturaEnCursoEntity.ESCRITA, db.capturaEnCursoDao().obtener("a")!!.ESTADO)
        }

    @Test
    fun `E-APP-084 una cancelacion lanzada por el bloque se propaga como la misma instancia`() =
        runTest {
            val propia = CancellationException("del bloque")
            var vista: Throwable? = null
            try {
                db.escribirSinCancelar<Unit> {
                    db.capturaEnCursoDao().capturar(marcador("b"))
                    throw propia
                }
            } catch (e: CancellationException) {
                vista = e
            }
            // La recuperación de stack traces de kotlinx-coroutines (modo debug de las
            // pruebas) entrega una copia cuya causa es la original.
            assertSame(propia, vista.takeIf { it === propia } ?: vista?.cause)
            assertEquals("la transaccion se revirtio", null, db.capturaEnCursoDao().obtener("b"))
        }

    @Test
    fun `E-APP-084 sinCancelar devuelve el resultado del bloque terminado aunque el job se cancele`() =
        runTest {
            lateinit var job: Job
            var resultado: Int? = null
            job = launch(UnconfinedTestDispatcher(testScheduler)) {
                resultado = sinCancelar {
                    db.capturaEnCursoDao().capturar(marcador("c"))
                    job.cancel()
                    7
                }
            }
            job.join()
            assertEquals(7, resultado)
            assertNotNull(db.capturaEnCursoDao().obtener("c"))
        }

    /**
     * X1b (compuerta DESPUÉS 2026-10-26): la guarda `terminado` de [sinCancelar]
     * sólo se ejerce cuando el bloque deja un job HIJO que termina después que
     * el bloque — es lo que hace `withTransaction` de Room 2.6.1 (su
     * `runBlocking` hereda el job). Entonces `withContext` reanuda por
     * `afterCompletion`, de forma cancelable, y lanza la cancelación tardía con
     * todo ya escrito. Sin la guarda, el llamador leería "no se hizo".
     */
    @Test
    fun `E-APP-084 sinCancelar con una transaccion adentro devuelve su resultado aunque la cancelacion llegue tarde`() =
        runTest {
            lateinit var job: Job
            var resultado: Int? = null
            job = launch(UnconfinedTestDispatcher(testScheduler)) {
                resultado = sinCancelar {
                    db.withTransaction {
                        db.capturaEnCursoDao().capturar(marcador("d"))
                        job.cancel()
                        db.capturaEnCursoDao().marcarEscrita("d", "2026-10-20T10:00:01Z")
                    }
                    7
                }
            }
            job.join()

            assertEquals(7, resultado)
            assertEquals(CapturaEnCursoEntity.ESCRITA, db.capturaEnCursoDao().obtener("d")!!.ESTADO)
        }
}
