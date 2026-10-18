package com.example.msp_app.visitas.envio.simulacion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Simulación determinista del worker REAL de visitas con fallas sembradas
 * (plan visitas P1, Tarea 17; E-APP-076). Ver [MundoSimulado].
 *
 * Tres familias:
 *  1. semillas aleatorias: I1, I2, I3 e I3-foto tras CADA acción, vivacidad
 *     con la red reparada, historial JSONL en `app/build/sim-historial/` que
 *     msp-api vuelve a verificar fuera de línea (`TestVerificarHistorialesEnDisco`);
 *  2. guiones fijos, uno por defensa del worker v3: cada uno se pone rojo al
 *     revertir esa defensa (relectura, `NOT EXISTS`, estampar después del
 *     UPDATE);
 *  3. control positivo: el modo `v2_actual` (el envío de hoy) viola I3.
 *
 * `-Dsim.semillas` / `-Dsim.pasos` cambian el tamaño de la familia 1.
 */
class SimulacionEnvioVisitasTest : RoomTestBase() {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var context: Context

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun mundo(
        semilla: Long,
        modo: ModoEnvioVisitas = ModoEnvioVisitas.V3,
        prob: Map<Falla, Double> = MundoSimulado.PROBABILIDADES_POR_OMISION,
        capturaFalla: Double = 0.2
    ) = MundoSimulado(semilla, db, context, carpeta.newFolder(), modo, prob, capturaFalla)

    private fun limpiar() = db.clearAllTables()

    private fun MundoSimulado.diag(codigo: String) = historial.any {
        it.tipo == TipoEvento.FALLA && it.detalle == "diag=$codigo"
    }

    private fun MundoSimulado.falla(nombre: String) = historial.any {
        it.tipo == TipoEvento.FALLA && it.detalle == nombre
    }

    private suspend fun fotosDe(id: String) = db.visitImageDao().getByVisitaId(id)

    // ─── 1. semillas aleatorias ────────────────────────────────────────────

    @Test
    fun `E-APP-076 semillas aleatorias cumplen I1 I2 I3 tras cada paso y drenan con la red reparada`() =
        runBlocking {
            val semillas = System.getProperty("sim.semillas")?.toLongOrNull() ?: SEMILLAS
            val pasos = System.getProperty("sim.pasos")?.toIntOrNull() ?: PASOS
            val salida = File("build/sim-historial").apply {
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
                m.repararRedYDrenar()
                m.verificarFinal()
                m.escribirHistorial(File(salida, "$semilla.jsonl"))
                m.historial.filter {
                    it.tipo == TipoEvento.FALLA || it.tipo.startsWith("marca") || it.tipo == TipoEvento.DEVUELTA
                }
                    .forEach { ev ->
                        val k = if (ev.tipo == TipoEvento.FALLA) {
                            ev.detalle.substringBefore(
                                ' '
                            )
                        } else {
                            ev.tipo
                        }
                        cobertura[k] = (cobertura[k] ?: 0) + 1
                    }
                limpiar()
            }
            println(
                "SIM E-APP-076 semillas=$semillas pasos=$pasos cobertura=${cobertura.toSortedMap()}"
            )
            // Control de la cobertura: la máquina aleatoria debe recorrer cada
            // carrera y cada rama; si una no sale, esta corrida no prueba nada de ella.
            val exigidas = listOf(
                "marca_confirmada",
                "marca_resguardada",
                "devuelta",
                "diag=visita_foto_nueva",
                "marca_condicional_sin_filas",
                "foto_tardia",
                "foto_tardia_tras_relectura",
                "otro_camino_confirma",
                "otro_camino_confirma_con_archivo",
                "barrido_devuelve",
                "proceso_muerto_antes_de_marcar",
                FALLA_FOTO_SIN_ARCHIVO,
                "archivo_borrado",
                "reloj_avanza"
            ) + Falla.entries.map { it.nombre }
            if (semillas >= SEMILLAS) {
                val faltan = exigidas.filter { (cobertura[it] ?: 0) == 0 }
                assertTrue("la simulación nunca recorrió: $faltan", faltan.isEmpty())
            }
        }

    // ─── 2. guiones fijos: una defensa cada uno ────────────────────────────

    private val sinFallas: Map<Falla, Double> = emptyMap()

    /** Rojo al quitar la relectura (`apareceFotoNueva`). */
    @Test
    fun `E-APP-076 foto tardia antes de la relectura la caza la relectura y la visita se confirma completa`() =
        runBlocking {
            val m = mundo(1, prob = sinFallas)
            val id = m.capturar(fotos = 1, sinArchivo = 0, rechazable = false)
            m.armarFotoTardia()
            m.intentarId(id)
            assertTrue("la relectura debe cazar la foto tardía", m.diag("visita_foto_nueva"))
            assertTrue("no llega al UPDATE", !m.falla("marca_condicional_sin_filas"))
            assertNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            assertTrue("nada se estampa", fotosDe(id).all { it.SUBIDA_EN == null })
            m.verificarInvariantes("tras el primer intento")
            m.intentarId(id)
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            assertTrue("las dos fotos se estampan", fotosDe(id).all { it.SUBIDA_EN != null })
            m.verificarInvariantes("final")
        }

    /** Rojo al quitar el `NOT EXISTS` de `confirmarTrasSubidaSiNoHayFotosNuevas`. */
    @Test
    fun `E-APP-076 foto tardia entre la relectura y el UPDATE da 0 filas y el reintento la confirma completa`() =
        runBlocking {
            val m = mundo(2, prob = sinFallas)
            val id = m.capturar(fotos = 1, sinArchivo = 0, rechazable = false)
            m.armarFotoTardiaTrasRelectura()
            m.intentarId(id)
            m.verificarInvariantes("tras la marca condicional")
            assertTrue(
                "el UPDATE condicional debe dar 0 filas",
                m.falla("marca_condicional_sin_filas")
            )
            assertNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            assertTrue(
                "con 0 filas no se estampa ni se borra",
                fotosDe(id).all {
                    it.SUBIDA_EN == null && File(it.URI).exists()
                }
            )
            m.intentarId(id)
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            m.verificarInvariantes("final")
        }

    /**
     * Rojo al estampar antes del UPDATE (el contraejemplo de rapid en Go,
     * `foto-tardia-tras-relectura-servidor-pierde-archivo`, E-VIS-027): la
     * marca da 0 filas, el servidor pierde el archivo de la foto que viajó, y
     * el siguiente envío ya no la lleva si el teléfono la había borrado.
     */
    @Test
    fun `E-APP-076 estampar solo tras la marca permite reenviar la foto que el servidor perdio`() =
        runBlocking {
            val m = mundo(3, prob = sinFallas)
            val id = m.capturar(fotos = 1, sinArchivo = 0, rechazable = false)
            m.armarFotoTardiaTrasRelectura()
            m.intentarId(id)
            m.borrarArchivoServidor(0)
            m.verificarInvariantes("tras perder el archivo")
            m.intentarId(id)
            m.verificarInvariantes("final")
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
        }

    /**
     * La rama "ya confirmada por otro camino" (`yaConfirmada` tras 0 filas):
     * la reconciliación de pendientes real la confirma entre la relectura y el
     * UPDATE y entra una foto sin archivo. Éxito sin estampar nada.
     */
    @Test
    fun `E-APP-076 ya confirmada por otro camino no estampa y la foto nueva queda pendiente`() =
        runBlocking {
            val m = mundo(4, prob = sinFallas)
            val id = m.capturar(fotos = 0, sinArchivo = 0, rechazable = false)
            m.armarOtroCamino(fotoConArchivo = false)
            m.intentarId(id)
            assertTrue(m.falla("otro_camino_confirma"))
            assertTrue(m.falla("marca_condicional_sin_filas"))
            assertTrue(m.diag("visita_confirmada_por_otro_camino_con_foto_nueva"))
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            assertEquals(1, fotosDe(id).count { it.SUBIDA_EN == null })
            m.verificarInvariantes("final")
        }

    /**
     * El HALLAZGO de E-APP-076, ya cerrado (E-APP-079). La foto con archivo
     * que entra justo después de que otro camino confirmó la visita queda sin
     * estampar y con su archivo; a media corrida es deuda (el verificador
     * estricto la marca I3-foto). La reconciliación inversa la barre: la
     * visita vuelve a pendiente, el reenvío idempotente entrega la foto, se
     * estampa y el verificador sale limpio.
     */
    @Test
    fun `E-APP-076 E-APP-079 hallazgo foto con archivo tras confirmar por otro camino se envia tras el barrido`() =
        runBlocking {
            val m = mundo(5, prob = sinFallas)
            val id = m.capturar(fotos = 0, sinArchivo = 0, rechazable = false)
            m.armarOtroCamino(fotoConArchivo = true)
            m.intentarId(id)
            val f = fotosDe(id).single()
            assertNull("no se estampa", f.SUBIDA_EN)
            assertTrue("conserva su archivo", File(f.URI).exists())
            m.verificarInvariantes("deuda a media corrida")
            assertEquals("antes del barrido es deuda", "I3-foto", m.violaciones()?.inv)

            m.repararRedYDrenar()

            assertTrue(
                "el barrido la devolvió",
                m.historial.any { it.detalle.startsWith("barrido_devuelve") }
            )
            assertNotNull("la foto se estampó", fotosDe(id).single().SUBIDA_EN)
            assertTrue("y su archivo se borró", !File(f.URI).exists())
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            m.verificarFinal()
            assertNull(m.violaciones())
        }

    @Test
    fun `E-APP-076 matar entre el 2xx y la marca sin fotos confirma dentro de NonCancellable`() =
        runBlocking {
            val m = mundo(6, prob = sinFallas)
            val id = m.capturar(fotos = 0, sinArchivo = 0, rechazable = false)
            m.armarMatar()
            m.intentarId(id)
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            m.verificarInvariantes("final")
        }

    @Test
    fun `E-APP-076 matar entre el 2xx y el GET con fotos deja pendiente y el reintento confirma`() =
        runBlocking {
            val m = mundo(7, prob = sinFallas)
            val id = m.capturar(fotos = 2, sinArchivo = 0, rechazable = false)
            m.armarMatar()
            m.intentarId(id)
            assertTrue(m.falla("proceso_muerto_antes_de_marcar"))
            assertNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            m.verificarInvariantes("muerto")
            m.intentarId(id)
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            m.verificarInvariantes("final")
        }

    @Test
    fun `E-APP-076 la foto que pierde su archivo la anota el inyector y no bloquea`() =
        runBlocking {
            val m = mundo(8, prob = sinFallas)
            val id = m.capturar(fotos = 2, sinArchivo = 0, rechazable = false)
            m.perderFotoLocal(0)
            assertEquals(1, m.historial.count { it.detalle.startsWith(FALLA_FOTO_SIN_ARCHIVO) })
            m.intentarId(id)
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            assertEquals(
                "la que perdió su archivo queda pendiente",
                1,
                fotosDe(id).count {
                    it.SUBIDA_EN == null
                }
            )
            m.verificarInvariantes("final")
        }

    @Test
    fun `E-APP-076 poda solo lo probado y la inversa resuelve las legadas`() = runBlocking {
        val m = mundo(9, prob = sinFallas, capturaFalla = 0.0)
        val confirmada = m.capturar(0, 0, false).also { m.intentarId(it) }
        val resguardada = m.capturar(0, 0, true).also { m.intentarId(it) }
        val pendiente = m.capturar(1, 0, false)
        val legadaFuera = m.capturarLegada(enServidor = false)
        val legadaDentro = m.capturarLegada(enServidor = true)
        m.avanzarYPodar(DIAS_PODA)
        m.avanzarYPodar(DIAS_PODA)
        m.avanzarYPodar(DIAS_PODA)
        val dao = db.visitDao()
        assertNull("confirmada vieja se poda", dao.findVisitById(confirmada))
        assertNotNull("pendiente nunca", dao.findVisitById(pendiente))
        // Desde E-APP-079 el salto de reloj corre la inversa diaria en el
        // camino: la legada que el servidor no tiene vuelve a pendiente (y
        // nunca se poda); la que sí tiene se confirma primero y SOLO después
        // envejece y se poda.
        assertEquals(
            "la que el servidor no tiene vuelve a pendiente",
            0,
            dao.findVisitById(legadaFuera)!!.GUARDADO_EN_MICROSIP
        )
        assertTrue(
            "la que el servidor tiene se confirmó antes de podarse",
            m.historial.any { it.tipo == TipoEvento.CONFIRMADA && it.visitaId == legadaDentro }
        )
        assertNull("confirmada hace más de 30 días: se poda", dao.findVisitById(legadaDentro))
        m.verificarInvariantes("tras podar")
        m.repararRedYDrenar()
        m.verificarFinal()
        m.avanzarYPodar(DIAS_PODA * 4)
        assertNull("resguardada, reportada y vieja", dao.findVisitById(resguardada))
        m.verificarInvariantes("final")
    }

    // ─── 3. control positivo ───────────────────────────────────────────────

    @Test
    fun `E-APP-076 control positivo el modo v2_actual pierde visitas en la simulacion`() =
        runBlocking {
            var conViolacion = 0
            val inv = mutableSetOf<String>()
            (1L..SEMILLAS_CONTROL).forEach { s ->
                val m = mundo(s, modo = ModoEnvioVisitas.V2_ACTUAL, capturaFalla = 0.3)
                repeat(PASOS_CONTROL) { m.accionAleatoria() }
                m.violaciones()?.let {
                    conViolacion++
                    inv += it.inv
                }
                limpiar()
            }
            println(
                "SIM E-APP-076 control v2_actual semillas_con_violacion=$conViolacion de $SEMILLAS_CONTROL inv=$inv"
            )
            assertTrue(
                "la simulación debe cazar el defecto de hoy (H1, E-VIS-001)",
                conViolacion > 0
            )
            assertTrue("el defecto de hoy es I3 (resguardada sin bandeja)", "I3" in inv)
        }

    private companion object {
        const val SEMILLAS = 500L
        const val PASOS = 200
        const val SEMILLAS_CONTROL = 200L
        const val PASOS_CONTROL = 80
        const val DIAS_PODA = 31L
    }
}
