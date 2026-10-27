package com.example.msp_app.visitas.envio.simulacion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.visitas.envio.simulacion.MundoBajada.Companion.ZONA_A
import com.example.msp_app.visitas.envio.simulacion.MundoBajada.Companion.ZONA_B
import java.io.File
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Simulación determinista del TELÉFONO con la bajada de la agenda de visitas
 * (plan visitas P2, Tarea 19; E-APP-116). Ver [MundoBajada].
 *
 * Cuatro familias:
 *  1. semillas aleatorias: tras CADA acción, I6, I8 y el formato del
 *     historial (que msp-api vuelve a verificar fuera de línea con
 *     `TestVerificarHistorialesEnDisco`), I1–I3 del envío, I4 e I10 del
 *     servidor modelado e I-bajada; al final, vivacidad;
 *  2. guiones fijos, uno por regla, cada uno verde sin defecto;
 *  3. los mismos guiones con el defecto de su regla: rojo con esa invariante;
 *  4. controles positivos aleatorios: cada defecto cazado en ≤ 200 semillas.
 *
 * I5 no está aquí (ver [MundoBajada]): depende de la Tarea 17.
 *
 * `-Dsim.bajada.semillas` / `-Dsim.bajada.pasos` cambian el tamaño de la familia 1.
 */
class SimulacionBajadaVisitasTest : RoomTestBase() {

    private lateinit var context: Context

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun mundo(
        semilla: Long,
        defectos: DefectosBajada = DefectosBajada(),
        prob: Map<FallaBajada, Double> = MundoBajada.PROBABILIDADES_POR_OMISION
    ) = MundoBajada(semilla, db, context, defectos, prob)

    private fun limpiar() = db.clearAllTables()

    private val sinFallas: Map<FallaBajada, Double> = emptyMap()

    // ─── 1. semillas aleatorias ────────────────────────────────────────────

    @Test
    fun `E-APP-116 semillas aleatorias cumplen I1 I3 I4 I6 I8 I10 e I-bajada tras cada paso y drenan`() =
        runBlocking {
            val semillas = System.getProperty("sim.bajada.semillas")?.toLongOrNull() ?: SEMILLAS
            val pasos = System.getProperty("sim.bajada.pasos")?.toIntOrNull() ?: PASOS
            val salida = File("build/sim-historial-bajada").apply {
                deleteRecursively()
                mkdirs()
            }
            val cobertura = mutableMapOf<String, Int>()
            (1L..semillas).forEach { semilla ->
                val m = mundo(semilla)
                repeat(pasos) { p ->
                    m.accionAleatoria()
                    m.verificarInvariantes("paso $p")
                }
                m.repararYDrenar()
                m.verificarFinal()
                escribirJsonl(File(salida, "bajada-$semilla.jsonl"), m.historialConFinales())
                m.cobertura.forEach { (k, v) -> cobertura[k] = (cobertura[k] ?: 0) + v }
                limpiar()
            }
            println(
                "SIM E-APP-116 semillas=$semillas pasos=$pasos cobertura=${cobertura.toSortedMap()}"
            )
            val exigidas = listOf(
                "empate_cruza_pagina",
                "marca_retiene",
                "proceso_muerto_entre_pagina_y_cursor",
                MotivoCursor.REINICIO_ZONA,
                MotivoCursor.REINICIO_EPOCH,
                "relleno_confirma",
                "apk_viejo",
                "reenvio_distinto",
                "relleno_t6",
                "binario_p1",
                "cambiar_zona",
                "bajada_error"
            ) + FallaBajada.entries.map { it.nombre }
            if (semillas >= SEMILLAS) {
                val faltan = exigidas.filter { (cobertura[it] ?: 0) == 0 }
                assertTrue("la simulación nunca recorrió: $faltan", faltan.isEmpty())
            }
        }

    // ─── 2 y 3. guiones fijos, verdes sin defecto y rojos con el suyo ──────

    /** Corre [guion] sin defecto (debe quedar limpio) y con [defecto] (debe romper [quiere]). */
    private fun verdeYRojo(
        defecto: DefectosBajada,
        quiere: Set<String>,
        guion: suspend (MundoBajada) -> Unit
    ) = runBlocking {
        val sano = mundo(GUION, prob = sinFallas)
        guion(sano)
        sano.verificarInvariantes("guion sin defecto")
        limpiar()
        val roto = mundo(GUION, defecto, prob = sinFallas)
        val inv = try {
            guion(roto)
            roto.violacion()?.inv
        } catch (e: AssertionError) {
            (e.cause as? InvarianteError)?.inv ?: throw e
        }
        assertTrue("con $defecto debía romper $quiere; salió $inv", inv in quiere)
    }

    /** Un grupo empatado de `UPDATED_AT` que cruza dos páginas: la segunda sigue por id. */
    @Test
    fun `E-APP-116 empate que cruza la pagina sigue por id y no repite ni salta`() =
        verdeYRojo(DefectosBajada(afterIdCero = true), setOf("I6")) { m ->
            m.rafaga(n = 5, zona = ZONA_A)
            m.avanzar(2_000)
            m.bajar()
            m.verificarInvariantes("tras bajar el empate")
            m.repararYDrenar()
            m.verificarFinal()
        }

    /** Con el cursor solo por tiempo, el canal sync pierde la cola del grupo empatado (I4). */
    @Test
    fun `E-APP-116 el canal sync y el inventario dan el mismo conjunto con empates`() =
        verdeYRojo(DefectosBajada(cursorSoloTiempo = true), setOf("I4")) { m ->
            m.rafaga(n = 5, zona = ZONA_A)
            m.avanzar(2_000)
            m.verificarInvariantes("tras el empate")
        }

    /**
     * Commit tardío: la primera escritura tarda en aterrizar y una segunda,
     * posterior, ya se ve. La marca retiene la segunda hasta que la primera
     * aterriza; sin marca el cursor la salta para siempre.
     */
    @Test
    fun `E-APP-116 la marca retiene el commit en vuelo y el cursor no lo salta`() =
        verdeYRojo(DefectosBajada(sinMarca = true), setOf("I-bajada")) { m ->
            m.ajena(ZONA_A, Duration.ofMillis(3_000), fueraDeVentana = false)
            m.avanzar(500)
            m.ajena(ZONA_A, Duration.ZERO, fueraDeVentana = false)
            m.avanzar(1_600)
            m.bajar()
            m.verificarInvariantes("con la primera en vuelo")
            m.avanzar(2_000)
            m.verificarInvariantes("ya aterrizó")
            m.bajar()
            m.repararYDrenar()
            m.verificarFinal()
        }

    /** La muerte dentro de la transacción de la página no deja ni filas ni cursor (I6). */
    @Test
    fun `E-APP-116 morir entre aplicar la pagina y guardar el cursor no persiste nada y la siguiente retoma`() =
        runBlocking {
            val m = mundo(GUION, prob = sinFallas)
            m.rafaga(n = 5, zona = ZONA_A)
            m.avanzar(2_000)
            assertNull("muere en la segunda página", m.bajar(matarEn = 1))
            assertTrue(m.historial.any { it.detalle == "proceso_muerto_entre_pagina_y_cursor" })
            val tras = db.visitaZonaDao().deZona(ZONA_A).size
            assertEquals("solo quedó la primera página", 2, tras)
            m.verificarInvariantes("tras morir")
            m.bajar()
            assertEquals(5, db.visitaZonaDao().deZona(ZONA_A).size)
            m.verificarInvariantes("final")
        }

    /** Cambio de zona con visitas propias pendientes: `Visit` intacta (I8), cursor nulo. */
    @Test
    fun `E-APP-116 cambio de zona con pendientes no toca Visit`() =
        verdeYRojo(DefectosBajada(bajadaEscribeSalida = true), setOf("I8")) { m ->
            m.cambiarCapacidad(contrato3 = true)
            val enviada = m.capturar(promesa = true, cita = false)
            m.correrWorker(enviada)
            m.capturar(promesa = false, cita = true) // pendiente
            m.ajena(ZONA_A, Duration.ZERO, fueraDeVentana = false)
            m.ajena(ZONA_B, Duration.ZERO, fueraDeVentana = false)
            m.avanzar(2_000)
            m.bajar()
            m.cambiarZona()
            m.bajar()
            val e = db.visitaZonaDao().estado(ZONA_A)
            assertTrue(
                "la zona vieja quedó sin posición",
                e == null || e.CURSOR == null && e.AFTER_ID == null
            )
            assertTrue("solo lo de la zona nueva", db.visitaZonaDao().deZona(ZONA_A).isEmpty())
        }

    /**
     * Relleno de `UPDATED_AT` (T6) con estampas viejas y la generación nueva:
     * el teléfono replica desde el inicio sin tocar sus pendientes.
     */
    @Test
    fun `E-APP-116 epoch nuevo con pendientes replica y no toca Visit`() = runBlocking {
        val m = mundo(GUION, prob = sinFallas)
        val pendiente = m.capturar(promesa = true, cita = true)
        repeat(4) { m.ajena(ZONA_A, Duration.ZERO, fueraDeVentana = false) }
        m.avanzar(2_000)
        m.bajar()
        m.binarioP1(ZONA_A)
        m.binarioP1(ZONA_A)
        m.avanzar(2_000)
        m.bajar()
        assertEquals(
            "las filas del binario P1 no existen para la bajada",
            4,
            db.visitaZonaDao().deZona(ZONA_A).size
        )
        val antes = db.visitDao().findVisitById(pendiente)
        m.rellenoT6()
        m.avanzar(2_000)
        m.bajar()
        assertEquals(antes, db.visitDao().findVisitById(pendiente))
        assertEquals("el replay trae las rellenadas", 6, db.visitaZonaDao().deZona(ZONA_A).size)
        assertEquals(
            "la generación nueva se guardó al terminar",
            2,
            db.visitaZonaDao().estado(ZONA_A)?.EPOCH
        )
        m.verificarInvariantes("final")
    }

    /** Contrato 2 → 3: la visita confirmada sin su compromiso se rellena, y la bajada lo trae. */
    @Test
    fun `E-APP-116 el relleno confirma el compromiso y la bajada lo trae`() = runBlocking {
        val m = mundo(GUION, prob = sinFallas)
        val id = m.capturar(promesa = true, cita = false)
        m.correrWorker(id)
        val base = db.visitDao().findVisitById(id)!!
        assertNotNull(base.CONFIRMADA_EN)
        assertNull("con contrato 2 el compromiso queda por rellenar", base.COMPROMISO_CONFIRMADO_EN)
        m.cambiarCapacidad(contrato3 = true)
        m.rellenar(0)
        assertNotNull(db.visitDao().findVisitById(id)!!.COMPROMISO_CONFIRMADO_EN)
        m.avanzar(2_000)
        m.bajar()
        val bajada = db.visitaZonaDao().deZona(ZONA_A).single { it.ID == id }
        assertEquals(base.PROMESA_FECHA, bajada.PROMESA_FECHA)
        m.verificarInvariantes("final")
    }

    /** El reenvío de un APK viejo sin bloques no borra la promesa (I10). */
    @Test
    fun `E-APP-116 reenvio del APK viejo no borra el compromiso`() =
        verdeYRojo(DefectosBajada(reenvioBorra = true), setOf("I10")) { m ->
            m.cambiarCapacidad(contrato3 = true)
            m.correrWorker(m.capturar(promesa = true, cita = true))
            m.reenvioApkViejo(0)
            m.verificarInvariantes("tras el reenvío viejo")
        }

    /** Un reenvío con promesa distinta responde 422 y no cambia la fila (tabla 5.2.3). */
    @Test
    fun `E-APP-116 reenvio con promesa distinta responde 422`() =
        verdeYRojo(DefectosBajada(reenvioBorra = true), setOf("5.2.3")) { m ->
            m.cambiarCapacidad(contrato3 = true)
            m.correrWorker(m.capturar(promesa = true, cita = false))
            m.reenvioDistinto(0)
            m.verificarInvariantes("tras el reenvío distinto")
        }

    // ─── 4. controles positivos aleatorios ─────────────────────────────────

    private fun cazadoEnSemillas(defecto: DefectosBajada, quiere: Set<String>) = runBlocking {
        (1L..SEMILLAS_CONTROL).forEach { s ->
            val m = mundo(s, defecto)
            try {
                repeat(PASOS_CONTROL) {
                    m.accionAleatoria()
                    m.violacion()?.let { v ->
                        assertTrue(
                            "$defecto semilla=$s rompió ${v.inv}, se esperaba $quiere: ${v.message}",
                            v.inv in quiere
                        )
                        println("SIM E-APP-116 control $defecto cazado en semilla=$s: ${v.message}")
                        return@runBlocking
                    }
                }
            } finally {
                limpiar()
            }
        }
        throw AssertionError(
            "control positivo: $SEMILLAS_CONTROL semillas no cazaron $defecto ($quiere)"
        )
    }

    @Test
    fun `E-APP-116 control positivo after_id cero lo caza I6`() =
        cazadoEnSemillas(DefectosBajada(afterIdCero = true), setOf("I6"))

    @Test
    fun `E-APP-116 control positivo la bajada que escribe Visit la caza I8`() =
        cazadoEnSemillas(DefectosBajada(bajadaEscribeSalida = true), setOf("I8"))

    @Test
    fun `E-APP-116 control positivo el reenvio que borra lo caza I10 o 5_2_3`() =
        cazadoEnSemillas(DefectosBajada(reenvioBorra = true), setOf("I10", "5.2.3"))

    @Test
    fun `E-APP-116 control positivo sin marca lo caza I-bajada`() =
        cazadoEnSemillas(DefectosBajada(sinMarca = true), setOf("I-bajada"))

    @Test
    fun `E-APP-116 control positivo el cursor solo por tiempo lo caza I4`() =
        cazadoEnSemillas(DefectosBajada(cursorSoloTiempo = true), setOf("I4", "I-bajada"))

    private companion object {
        const val SEMILLAS = 150L
        const val PASOS = 150
        const val SEMILLAS_CONTROL = 200L
        const val PASOS_CONTROL = 150
        const val GUION = 42L
    }
}
