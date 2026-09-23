package com.example.msp_app.data.visitas

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El barrido de archivos sin fila: las **dos** condiciones, cada una con su
 * control.
 *
 * Es una función suelta y sin Android a propósito, así que se prueba con
 * directorios de verdad, sin Robolectric y sin cámara.
 */
class ComprobantesDeVisitaHuerfanosTest {

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    private val corte = 1_000_000L

    /** Viejo **y** sin fila: el único que se barre. */
    @Test
    fun `barre el comprobante viejo que ninguna fila referencia`() {
        val huerfano = archivo("comprobante_visita_IMG-1.jpg", modificado = corte - 1)

        val barridos = comprobantesDeVisitaHuerfanos(carpeta.root, emptySet(), corte)

        assertEquals(listOf(huerfano.absolutePath), barridos.map { it.absolutePath })
    }

    /**
     * **Un archivo viejo QUE UNA FILA REFERENCIA se conserva**: es la foto de
     * una visita que todavía no sube. Sin este control, el barrido borraría la
     * evidencia que espera señal.
     */
    @Test
    fun `un archivo viejo que una fila referencia se conserva`() {
        val vivo = archivo("comprobante_visita_IMG-1.jpg", modificado = corte - 1)

        val barridos = comprobantesDeVisitaHuerfanos(
            carpeta.root,
            setOf(vivo.absolutePath),
            corte
        )

        assertEquals(emptyList<Any>(), barridos)
    }

    /**
     * **Un archivo reciente sin fila se conserva**: puede ser el de la captura
     * que está ocurriendo ahora mismo — la foto se toma ANTES de que la visita
     * se registre.
     */
    @Test
    fun `un archivo reciente sin fila se conserva`() {
        archivo("comprobante_visita_IMG-1.jpg", modificado = corte + 1)

        assertEquals(
            emptyList<Any>(),
            comprobantesDeVisitaHuerfanos(carpeta.root, emptySet(), corte)
        )
    }

    /**
     * **Nada que no sea un comprobante de VISITA se toca.** En `filesDir` viven
     * también los borradores de venta y los comprobantes de PAGO, que tienen su
     * propia tabla y su propio barrido: sin el filtro de prefijo, cada barrido
     * borraría las fotos del otro.
     */
    @Test
    fun `no toca archivos de otros modulos`() {
        archivo("comprobante_pago_IMG-1.jpg", modificado = corte - 1)
        archivo("draft_venta_1.jpg", modificado = corte - 1)

        assertEquals(
            emptyList<Any>(),
            comprobantesDeVisitaHuerfanos(carpeta.root, emptySet(), corte)
        )
    }

    /** Control positivo del anterior: en el MISMO directorio, el suyo sí se barre. */
    @Test
    fun `control positivo, en el mismo directorio el suyo si se barre`() {
        archivo("comprobante_pago_IMG-1.jpg", modificado = corte - 1)
        val mio = archivo("comprobante_visita_IMG-2.jpg", modificado = corte - 1)

        assertEquals(
            listOf(mio.absolutePath),
            comprobantesDeVisitaHuerfanos(carpeta.root, emptySet(), corte).map { it.absolutePath }
        )
    }

    /** Un directorio que no existe no revienta: devuelve nada. */
    @Test
    fun `un directorio inexistente no revienta`() {
        assertEquals(
            emptyList<Any>(),
            comprobantesDeVisitaHuerfanos(File(carpeta.root, "no-existe"), emptySet(), corte)
        )
    }

    private fun archivo(nombre: String, modificado: Long): File {
        val f = File(carpeta.root, nombre)
        f.writeBytes(byteArrayOf(1, 2, 3))
        f.setLastModified(modificado)
        return f
    }
}
