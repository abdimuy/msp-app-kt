package com.example.msp_app.visitas.envio.simulacion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import java.time.Duration
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
 * Escenarios del agente adversario (plan visitas P1, Tarea 19; spec §8.7)
 * sobre el worker REAL, Room en memoria y los casos de uso reales
 * ([MundoSimulado]).
 *
 * Las tres fijan el invariante bueno. E-VIS-031 y E-VIS-032 nacieron como
 * ESPERADA ROJA (afirmaban el defecto); al arreglarlos se les dio la vuelta
 * y ahora afirman que la foto viaja. Al revertir cualquiera de los dos
 * arreglos, su prueba se pone roja.
 */
class AdversarioEnvioVisitasTest : RoomTestBase() {

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
        capturaFalla: Double = 0.0
    ) = MundoSimulado(semilla, db, context, carpeta.newFolder(), modo, emptyMap(), capturaFalla)

    private suspend fun fotosDe(id: String) = db.visitImageDao().getByVisitaId(id)

    /**
     * E-VIS-031. Una legada (`GUARDADO_EN_MICROSIP = 1`, sin marcas; la deja
     * el envío de antes de v35 o el modo `v2_actual` cuando una foto entra
     * después de soltar la visita) con una foto sin `SUBIDA_EN` y CON
     * archivo. El servidor tiene la visita pero no la foto. Antes del arreglo
     * `VisitDao.confirmar` la retenía por la foto y el barrido no la veía (solo
     * miraba confirmadas): la foto no viajaba nunca. Ahora el barrido incluye
     * las legadas: la devuelve a pendiente, el reenvío idempotente sube la foto
     * y la visita se confirma.
     */
    @Test
    fun `E-VIS-031 legada con foto con archivo vuelve a pendiente y la foto viaja`() = runBlocking {
        val m = mundo(31)
        val id = m.capturarLegada(enServidor = true)
        val foto = m.agregarFotoA(id, conArchivo = true)
        // Control: una legada sin foto, igual de vieja, se confirma sin el barrido.
        val control = m.capturarLegada(enServidor = true)

        m.repararRedYDrenar()

        assertNotNull(
            "control: la legada sin foto se confirma",
            db.visitDao().findVisitById(control)?.CONFIRMADA_EN
        )
        assertTrue(
            "el barrido devolvió la legada",
            m.historial.any { it.detalle.startsWith("barrido_devuelve") }
        )
        val fila = db.visitDao().findVisitById(id)
        assertNotNull(fila)
        assertNotNull("la legada se confirma", fila!!.CONFIRMADA_EN)
        assertEquals(1, fila.GUARDADO_EN_MICROSIP)
        val img = fotosDe(id).single { it.ID == foto }
        assertNotNull("la foto se estampa", img.SUBIDA_EN)
        assertTrue("el servidor recibe la foto", foto in m.fotosEnServidor(id))
        m.verificarFinal()

        // Y el mes siguiente no cambia nada: la poda se la lleva como a cualquier confirmada.
        m.avanzarYPodar(DIAS_DE_INVERSAS)
        m.repararRedYDrenar()
        m.verificarFinal()
    }

    /**
     * E-VIS-032. Antes del arreglo el barrido abandonaba una foto por la edad
     * de su `CREADA_EN`, que sale del reloj del teléfono: con la fecha
     * atrasada (pila agotada, fecha puesta a mano) y luego corregida, la foto
     * tardía ya "tenía" más de 7 días y se abandonaba el mismo día. Ahora el
     * barrido NUNCA abandona una foto sin enviar (decisión 032-a): la edad
     * solo va a la telemetría.
     */
    @Test
    fun `E-VIS-032 reloj atrasado y corregido no abandona la foto tras confirmar y la foto viaja`() =
        runBlocking {
            val m = mundo(32)
            val real = m.clock.now()
            m.clock.setNow(real.minus(Duration.ofDays(DIAS_DE_ATRASO)))
            val id = m.capturar(fotos = 0, sinArchivo = 0, rechazable = false)
            m.armarOtroCamino(fotoConArchivo = true)
            m.intentarId(id)
            assertNotNull(db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)
            val tardia = fotosDe(id).single()
            assertNull("control: la foto tardía entró sin enviar", tardia.SUBIDA_EN)

            m.clock.setNow(real) // la red corrige el reloj
            m.reconciliacionInversa()
            m.repararRedYDrenar()

            assertTrue(
                "el barrido la devuelve aunque la foto parezca vieja",
                m.historial.any { it.detalle.startsWith("barrido_devuelve") }
            )
            assertTrue(
                "la telemetría la cuenta como vieja",
                m.historial.any { it.detalle.startsWith("barrido_foto_vieja") }
            )
            val img = fotosDe(id).single()
            assertNotNull("la foto viaja", img.SUBIDA_EN)
            assertTrue("el servidor la tiene", img.ID in m.fotosEnServidor(id))
            assertNull("sin violaciones", m.violaciones())
            m.verificarFinal()

            // Control: el mismo guion sin el atraso también lo cobra el barrido.
            db.clearAllTables()
            val c = mundo(32)
            val idc = c.capturar(fotos = 0, sinArchivo = 0, rechazable = false)
            c.armarOtroCamino(fotoConArchivo = true)
            c.intentarId(idc)
            c.reconciliacionInversa()
            c.repararRedYDrenar()
            assertNotNull("control: sin atraso la foto viaja", fotosDe(idc).single().SUBIDA_EN)
            c.verificarFinal()
        }

    /**
     * E-VIS-042. Ida y vuelta del kill-switch `config/visitas_envio`: en
     * `v2_actual` el envío de hoy suelta sin prueba (un 422 sin custodia, un
     * 422 con custodia y un 2xx quedan todos `GUARDADO_EN_MICROSIP = 1` sin
     * marca). Al volver a `v3`, la inversa —que trata esas filas como
     * legadas— devuelve a pendiente lo que el servidor no tiene, confirma lo
     * que sí, y el worker v3 resguarda lo que tiene custodia. Nada queda
     * soltado sin marca. El código resiste.
     */
    @Test
    fun `E-VIS-042 volver de v2_actual a v3 recupera lo que v2_actual solto sin prueba`() =
        runBlocking {
            // 422 sin custodia (capturaFalla = 1) y un 2xx con foto.
            val m = mundo(33, modo = ModoEnvioVisitas.V2_ACTUAL, capturaFalla = 1.0)
            val sinCustodia = m.capturar(fotos = 0, sinArchivo = 0, rechazable = true)
            val conFoto = m.capturar(fotos = 1, sinArchivo = 0, rechazable = false)
            m.intentarId(sinCustodia)
            m.intentarId(conFoto)
            // Control positivo: v2_actual las soltó, y una sin que el servidor la tenga.
            assertEquals(1, db.visitDao().findVisitById(sinCustodia)!!.GUARDADO_EN_MICROSIP)
            assertNull(db.visitDao().findVisitById(sinCustodia)!!.RESGUARDADA_EN)
            assertNull(db.visitDao().findVisitById(conFoto)!!.CONFIRMADA_EN)

            m.modo = ModoEnvioVisitas.V3
            m.repararRedYDrenar()

            val a = db.visitDao().findVisitById(sinCustodia)!!
            assertEquals("vuelve a pendiente y se conserva", 0, a.GUARDADO_EN_MICROSIP)
            assertNull(a.CONFIRMADA_EN)
            assertNull(a.RESGUARDADA_EN)
            assertNotNull(
                "lo que el servidor tiene se confirma",
                db.visitDao().findVisitById(conFoto)!!.CONFIRMADA_EN
            )
            assertTrue(
                db.visitDao().entregadasDesde("").none {
                    db.visitDao().findVisitById(it)?.CONFIRMADA_EN == null
                }
            )

            // 422 con custodia: v2_actual la soltó sin RESGUARDADA_EN; en v3 se resguarda.
            db.clearAllTables()
            val b = mundo(34, modo = ModoEnvioVisitas.V2_ACTUAL, capturaFalla = 0.0)
            val conCustodia = b.capturar(fotos = 0, sinArchivo = 0, rechazable = true)
            b.intentarId(conCustodia)
            assertEquals(1, db.visitDao().findVisitById(conCustodia)!!.GUARDADO_EN_MICROSIP)
            assertNull(db.visitDao().findVisitById(conCustodia)!!.RESGUARDADA_EN)
            b.modo = ModoEnvioVisitas.V3
            b.repararRedYDrenar()
            assertNotNull(
                "en v3 queda resguardada",
                db.visitDao().findVisitById(conCustodia)!!.RESGUARDADA_EN
            )
            assertTrue(db.visitDao().entregadasDesde("").isEmpty())
        }

    private companion object {
        const val DIAS_DE_INVERSAS = 40L
        const val DIAS_DE_ATRASO = 10L
    }
}
