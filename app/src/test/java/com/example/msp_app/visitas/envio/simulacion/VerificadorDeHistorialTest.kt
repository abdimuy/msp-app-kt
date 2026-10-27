package com.example.msp_app.visitas.envio.simulacion

import com.example.msp_app.visitas.envio.simulacion.TipoEvento.BANDEJA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.CAPTURA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.CONFIRMADA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.DEVUELTA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.FALLA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.FINAL
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.FOTO_AGREGADA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.RESGUARDADA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.RESPUESTA
import com.example.msp_app.visitas.envio.simulacion.TipoEvento.SERVIDOR
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Control positivo del verificador Kotlin (E-APP-076). Los historiales malos
 * y buenos son los MISMOS que `historialesMalos`/`historialesBuenos` de la
 * simulación Go (msp-api `internal/visitas/sim/historial_test.go`, rama
 * feat/visitas-p1-envio 45dc10e), copiados uno por uno: sin ellos, un
 * verificador que devuelve `null` siempre pasaría todas las simulaciones.
 * Se corren con `exigirEstampadas = false` porque los de Go no traen
 * `estampadas=`; los de abajo prueban la exigencia.
 */
class VerificadorDeHistorialTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private fun e(paso: Int, tipo: String, v: String, d: String = "") = Evento(paso, tipo, v, d)

    private val malos: List<Triple<String, String, List<Evento>>> = listOf(
        Triple(
            "confirmada sin servidor",
            "I3",
            listOf(e(1, CAPTURA, "a", "fotos="), e(2, CONFIRMADA, "a"))
        ),
        Triple(
            "confirmada antes de que el servidor la tenga",
            "I3",
            listOf(
                e(1, CAPTURA, "a", "fotos="),
                e(2, CONFIRMADA, "a"),
                e(3, SERVIDOR, "a", "filas=1 fotos=")
            )
        ),
        Triple(
            "resguardada sin bandeja (defecto H1, E-VIS-001)",
            "I3",
            listOf(
                e(1, CAPTURA, "a", "fotos="),
                e(2, RESPUESTA, "a", "http=422 eco=- cabecera=0"),
                e(3, RESGUARDADA, "a")
            )
        ),
        Triple(
            "resguardada con la fila cortada (E-VIS-024)",
            "I3",
            listOf(
                e(1, CAPTURA, "a", "fotos="),
                e(2, BANDEJA, "a", "http=400 entera=0 cabecera=1"),
                e(3, RESGUARDADA, "a")
            )
        ),
        Triple(
            "visita perdida",
            "I3",
            listOf(
                e(1, CAPTURA, "a", "fotos="),
                e(2, CONFIRMADA, "a"),
                e(3, FINAL, "a", "confirmada")
            )
        ),
        Triple(
            "capturada sin estado final",
            "I1",
            listOf(
                e(1, CAPTURA, "a", "fotos="),
                e(2, CAPTURA, "b", "fotos="),
                e(3, FINAL, "a", "pendiente")
            )
        ),
        Triple("historial vacío", "formato", emptyList()),
        Triple("captura sin ningún final", "I1", listOf(e(1, CAPTURA, "a", "fotos="))),
        Triple(
            "pasos desordenados",
            "formato",
            listOf(e(2, CAPTURA, "a", "fotos="), e(1, FINAL, "a", "pendiente"))
        ),
        Triple(
            "pasos repetidos",
            "formato",
            listOf(e(1, CAPTURA, "a", "fotos="), e(1, FINAL, "a", "pendiente"))
        ),
        Triple(
            "capturada dos veces",
            "I2",
            listOf(e(1, CAPTURA, "a", "fotos="), e(2, CAPTURA, "a", "fotos="))
        ),
        Triple(
            "dos filas en el servidor",
            "I2",
            listOf(e(1, CAPTURA, "a", "fotos="), e(2, SERVIDOR, "a", "filas=2 fotos="))
        ),
        Triple(
            "confirmada con una foto sin archivo (E-VIS-022)",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos=f1,f2"),
                e(2, SERVIDOR, "a", "filas=1 fotos=f1"),
                e(3, CONFIRMADA, "a")
            )
        ),
        Triple(
            "el archivo desaparece después de confirmar (H11)",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos=f1"),
                e(2, SERVIDOR, "a", "filas=1 fotos=f1"),
                e(3, CONFIRMADA, "a"),
                e(4, SERVIDOR, "a", "filas=1 fotos=")
            )
        ),
        Triple(
            "foto tardía antes de la marca",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos=f1 sin_archivo="),
                e(2, SERVIDOR, "a", "filas=1 fotos=f1"),
                e(3, FOTO_AGREGADA, "a", "foto=f2 archivo=1"),
                e(4, CONFIRMADA, "a")
            )
        ),
        Triple(
            "foto con archivo agregada a una visita confirmada",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos= sin_archivo="),
                e(2, SERVIDOR, "a", "filas=1 fotos="),
                e(3, CONFIRMADA, "a"),
                e(4, FOTO_AGREGADA, "a", "foto=f9 archivo=1"),
                // E-APP-079: la deuda se cobra al final, no en el acto.
                e(5, FINAL, "a", "confirmada")
            )
        ),
        Triple(
            "devuelta y resguardada con una bandeja vieja (R4 del revisor, E-APP-079)",
            "I3",
            listOf(
                e(1, CAPTURA, "a", "fotos= sin_archivo="),
                e(2, BANDEJA, "a", "http=422 entera=1 cabecera=1"),
                e(3, SERVIDOR, "a", "filas=1 fotos="),
                e(4, CONFIRMADA, "a", "estampadas="),
                e(5, FOTO_AGREGADA, "a", "foto=f9 archivo=1"),
                e(6, DEVUELTA, "a"),
                e(7, RESGUARDADA, "a"),
                e(8, FINAL, "a", "resguardada")
            )
        ),
        Triple(
            "devuelta y confirmada otra vez sin la foto agregada tras confirmar (E-APP-079)",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos= sin_archivo="),
                e(2, SERVIDOR, "a", "filas=1 fotos="),
                e(3, CONFIRMADA, "a", "estampadas="),
                e(4, FOTO_AGREGADA, "a", "foto=f9 archivo=1"),
                e(5, DEVUELTA, "a"),
                e(6, CONFIRMADA, "a", "estampadas=")
            )
        ),
        Triple(
            "pierde archivo: la que viajó en el envío que confirma y no quedó",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos=f1,f2 sin_archivo="),
                e(2, FALLA, "a", "$FALLA_FOTO_SIN_ARCHIVO foto=f2"),
                e(3, SERVIDOR, "a", "filas=1 fotos="),
                e(4, CONFIRMADA, "a", "estampadas=f1")
            )
        ),
        Triple(
            "pierde archivo: estampada y borrada sin estar en el servidor",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos=f1 sin_archivo="),
                e(2, FALLA, "a", "$FALLA_FOTO_SIN_ARCHIVO foto=f1"),
                e(3, SERVIDOR, "a", "filas=1 fotos="),
                e(4, CONFIRMADA, "a", "estampadas=f1")
            )
        ),
        Triple(
            "pierde archivo después de confirmar: no exime",
            "I3-foto",
            listOf(
                e(1, CAPTURA, "a", "fotos=f1 sin_archivo="),
                e(2, SERVIDOR, "a", "filas=1 fotos=f1"),
                e(3, CONFIRMADA, "a", "estampadas="),
                e(4, FALLA, "a", "$FALLA_FOTO_SIN_ARCHIVO foto=f1"),
                e(5, SERVIDOR, "a", "filas=1 fotos=")
            )
        ),
        Triple(
            "final que no sale de las marcas",
            "I3",
            listOf(
                e(1, CAPTURA, "a", "fotos="),
                e(2, SERVIDOR, "a", "filas=1 fotos="),
                e(3, FINAL, "a", "confirmada")
            )
        )
    )

    private val buenos: List<Pair<String, List<Evento>>> = listOf(
        "confirmada con el servidor completo" to listOf(
            e(1, CAPTURA, "a", "fotos=f1"),
            e(2, SERVIDOR, "a", "filas=1 fotos=f1"),
            e(3, CONFIRMADA, "a"),
            e(4, FINAL, "a", "confirmada")
        ),
        "resguardada con fila entera" to listOf(
            e(1, CAPTURA, "a", "fotos="),
            e(2, BANDEJA, "a", "http=422 entera=1 cabecera=1"),
            e(3, RESGUARDADA, "a"),
            e(4, FINAL, "a", "resguardada")
        ),
        "pendiente para siempre es legítimo" to listOf(e(1, CAPTURA, "a", "fotos=f1"), e(2, FINAL, "a", "pendiente")),
        "devuelta a pendiente" to listOf(
            e(1, CAPTURA, "a", "fotos="),
            e(2, SERVIDOR, "a", "filas=1 fotos="),
            e(3, CONFIRMADA, "a"),
            e(4, DEVUELTA, "a"),
            e(5, SERVIDOR, "a", "filas=0 fotos="),
            e(6, FINAL, "a", "pendiente")
        ),
        "una foto sin archivo local no bloquea" to listOf(
            e(1, CAPTURA, "a", "fotos=f1,f2 sin_archivo=f2"),
            e(2, SERVIDOR, "a", "filas=1 fotos=f1"),
            e(3, CONFIRMADA, "a"),
            e(4, FOTO_AGREGADA, "a", "foto=f3 archivo=0"),
            e(5, FINAL, "a", "confirmada")
        ),
        "pierde archivo antes de la marca y sin estampar" to listOf(
            e(1, CAPTURA, "a", "fotos=f1,f2 sin_archivo="),
            e(2, FALLA, "a", "$FALLA_FOTO_SIN_ARCHIVO foto=f2"),
            e(3, SERVIDOR, "a", "filas=1 fotos=f1"),
            e(4, CONFIRMADA, "a", "estampadas=f1"),
            e(5, FINAL, "a", "confirmada")
        ),
        "una foto de más en el servidor no estorba" to listOf(
            e(1, CAPTURA, "a", "fotos=f1"),
            e(2, SERVIDOR, "a", "filas=1 fotos=f0,f1"),
            e(3, CONFIRMADA, "a"),
            e(4, FINAL, "a", "confirmada")
        )
    )

    @Test
    fun `E-APP-076 los historiales malos de Go fallan con su invariante y los buenos pasan`() {
        malos.forEach { (nombre, inv, h) ->
            val err = verificar(h, exigirEstampadas = false)
            assertNotNull("$nombre: debía fallar con $inv", err)
            assertEquals(nombre, inv, err!!.inv)
        }
        buenos.forEach { (nombre, h) ->
            assertNull(
                "$nombre: ${verificar(h, false)?.message}",
                verificar(h, false)
            )
        }
        assertEquals("los 22 malos de Go", 22, malos.size)
    }

    /**
     * Pedido de los revisores: sin `estampadas=` obligatorio, una falla
     * `foto_sin_archivo_local` exime a la foto, y una marca que no dice qué
     * estampó escondería una foto estampada y borrada que el servidor no tiene.
     */
    @Test
    fun `E-APP-076 la marca sin estampadas es error de formato en los historiales Kotlin`() {
        val escondida = listOf(
            e(1, CAPTURA, "a", "fotos=f1 sin_archivo="),
            e(2, FALLA, "a", "$FALLA_FOTO_SIN_ARCHIVO foto=f1"),
            e(3, SERVIDOR, "a", "filas=1 fotos="),
            e(4, CONFIRMADA, "a", ""),
            e(5, FINAL, "a", "confirmada")
        )
        assertNull(
            "sin la exigencia el defecto pasa: por eso se exige",
            verificar(escondida, exigirEstampadas = false)
        )
        assertEquals("formato", verificar(escondida)!!.inv)
        val dicha = escondida.toMutableList().also { it[3] = e(4, CONFIRMADA, "a", "estampadas=f1") }
        assertEquals("con estampadas= la foto se exige", "I3-foto", verificar(dicha)!!.inv)
        val vacia = escondida.toMutableList().also { it[3] = e(4, CONFIRMADA, "a", "estampadas=") }
        assertNull("estampadas= vacío es válido: no estampó nada", verificar(vacia))
    }

    /**
     * E-APP-079: una foto con archivo agregada a una visita YA confirmada es
     * deuda. A media corrida se permite (la cobra la siguiente inversa); al
     * final, si la visita sigue confirmada sin esa foto, es I3-foto. Si el
     * barrido la devuelve y el reenvío la entrega, sale limpio.
     */
    @Test
    fun `E-APP-079 la foto agregada tras confirmar es deuda que se cobra al final`() {
        val deuda = listOf(
            e(1, CAPTURA, "a", "fotos= sin_archivo="),
            e(2, SERVIDOR, "a", "filas=1 fotos="),
            e(3, CONFIRMADA, "a", "estampadas="),
            e(4, FOTO_AGREGADA, "a", "foto=f9 archivo=1"),
            e(5, FINAL, "a", "confirmada")
        )
        assertNull("a media corrida se permite", verificar(deuda, permitirDeuda = true))
        assertEquals("al final no", "I3-foto", verificar(deuda)!!.inv)

        val pagada = listOf(
            e(1, CAPTURA, "a", "fotos= sin_archivo="),
            e(2, SERVIDOR, "a", "filas=1 fotos="),
            e(3, CONFIRMADA, "a", "estampadas="),
            e(4, FOTO_AGREGADA, "a", "foto=f9 archivo=1"),
            e(5, DEVUELTA, "a"),
            e(6, SERVIDOR, "a", "filas=1 fotos=f9"),
            e(7, CONFIRMADA, "a", "estampadas=f9"),
            e(8, FINAL, "a", "confirmada")
        )
        assertNull("devuelta y reenviada: limpia", verificar(pagada))

        val devueltaSinEntregar = pagada.take(5) + e(6, CONFIRMADA, "a", "estampadas=") + e(7, FINAL, "a", "confirmada")
        assertEquals(
            "devuelta y confirmada sin la foto: I3-foto",
            "I3-foto",
            verificar(devueltaSinEntregar)!!.inv
        )

        val perdioArchivo = deuda.take(4) + e(5, FALLA, "a", "$FALLA_FOTO_SIN_ARCHIVO foto=f9") + e(6, FINAL, "a", "confirmada")
        assertNull("una deuda sin archivo ya no la puede mandar nadie", verificar(perdioArchivo))
    }

    // ─── bajada (plan visitas P2, Tarea 19; E-APP-116) ─────────────────────
    //
    // Los MISMOS historiales que `historialesBajadaMalos`/`historialesBajadaBuenos`
    // de msp-api (`internal/visitas/sim/historial_test.go`): las reglas de
    // `pull`, `cursor`, `epoch` y `salida` se escriben dos veces y estos casos
    // las amarran.

    private fun pull(
        paso: Int,
        filas: String,
        hasMore: Int = 0,
        epoch: String = "1",
        zona: Int = 7
    ) = e(paso, TipoEvento.PULL, "", "zona=$zona filas=$filas has_more=$hasMore epoch=$epoch")

    private fun cur(
        paso: Int,
        cursor: String,
        after: String,
        motivo: String = "pagina",
        zona: Int = 7
    ) = e(paso, TipoEvento.CURSOR, "", "zona=$zona cursor=$cursor after_id=$after motivo=$motivo")

    private val t1 = "2026-10-17T18:00:01.000Z"
    private val t2 = "2026-10-17T18:00:02.000Z"

    private val malosBajada: List<Triple<String, String, List<Evento>>> = listOf(
        Triple("cursor sin after_id", "I6", listOf(pull(1, "a|$t1"), cur(2, t1, "-"))),
        Triple("after_id sin cursor", "I6", listOf(cur(1, "-", "a"))),
        Triple(
            "cursor que retrocede sin reinicio",
            "I6",
            listOf(pull(1, "a|$t2"), cur(2, t2, "a"), pull(3, "b|$t1"), cur(4, t1, "b"))
        ),
        Triple("cursor por delante de lo aplicado", "I6", listOf(cur(1, t1, "x"))),
        Triple("reinicio que conserva el cursor", "I6", listOf(cur(1, t1, "a", "reinicio_epoch"))),
        Triple(
            "cursor de una fila que borró el cambio de zona",
            "I6",
            listOf(
                pull(1, "a|$t1"),
                cur(2, t1, "a"),
                cur(3, "-", "-", "reinicio_zona", zona = 8),
                cur(4, t1, "a")
            )
        ),
        Triple(
            "cursor de una fila que borró el replay por generación",
            "I6",
            listOf(
                pull(1, "a|$t1"),
                cur(2, t1, "a"),
                cur(3, "-", "-", "reinicio_epoch"),
                cur(4, t1, "a")
            )
        ),
        Triple(
            "generación persistida a media paginación",
            "epoch",
            listOf(
                pull(1, "a|$t1", hasMore = 1, epoch = "2"),
                e(2, TipoEvento.EPOCH, "", "zona=7 epoch=2")
            )
        ),
        Triple(
            "generación persistida distinta de la de la página",
            "epoch",
            listOf(pull(1, "a|$t1", epoch = "2"), e(2, TipoEvento.EPOCH, "", "zona=7 epoch=3"))
        ),
        Triple(
            "la bajada cambia la tabla de salida",
            "I8",
            listOf(
                e(1, TipoEvento.SALIDA, "", "fase=antes huella=h1"),
                e(2, TipoEvento.SALIDA, "", "fase=despues huella=h2")
            )
        )
    )

    private val buenosBajada: List<Pair<String, List<Evento>>> = listOf(
        "retroceso tras reinicio por generación" to listOf(
            pull(1, "a|$t2"),
            cur(2, t2, "a"),
            cur(3, "-", "-", "reinicio_epoch"),
            pull(4, "b|$t1", epoch = "2"),
            cur(5, t1, "b"),
            e(6, TipoEvento.EPOCH, "", "zona=7 epoch=2")
        ),
        "página vacía conserva el cursor" to listOf(
            pull(1, "a|$t1"),
            cur(2, t1, "a"),
            pull(3, ""),
            cur(4, t1, "a"),
            e(5, TipoEvento.EPOCH, "", "zona=7 epoch=1")
        ),
        "empate: mismo cursor, id mayor" to listOf(
            pull(1, "a|$t1,b|$t1", hasMore = 1),
            cur(2, t1, "b"),
            pull(3, "c|$t1"),
            cur(4, t1, "c")
        ),
        "la bajada no toca la salida" to listOf(
            e(1, TipoEvento.SALIDA, "", "fase=antes huella=h1"),
            e(2, TipoEvento.ACCION, "", "cambiar_zona zona=8"),
            e(3, TipoEvento.SALIDA, "", "fase=despues huella=h1")
        )
    )

    @Test
    fun `E-APP-116 los historiales de la bajada malos fallan con su invariante y los buenos pasan`() {
        malosBajada.forEach { (nombre, inv, h) ->
            val err = verificar(h)
            assertNotNull("$nombre: debía fallar con $inv", err)
            assertEquals(nombre, inv, err!!.inv)
        }
        buenosBajada.forEach { (nombre, h) ->
            assertNull(
                "$nombre: ${verificar(h)?.message}",
                verificar(h)
            )
        }
        assertEquals(10, malosBajada.size)
        assertEquals(4, buenosBajada.size)
    }

    @Test
    fun `el JSONL tiene el formato de Go y se lee igual`() {
        val f = File(carpeta.root, "x.jsonl")
        val h = listOf(e(1, CAPTURA, "a", "fotos=f1 sin_archivo="), e(2, FINAL, "a", "pendiente"))
        escribirJsonl(f, h)
        assertEquals(
            "{\"paso\":1,\"tipo\":\"captura\",\"visita_id\":\"a\",\"detalle\":\"fotos=f1 sin_archivo=\"}",
            f.readLines().first()
        )
        assertEquals(h, leerJsonl(f))
    }
}
