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
                e(4, FOTO_AGREGADA, "a", "foto=f9 archivo=1")
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
        assertEquals("los 20 malos de Go", 20, malos.size)
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
