package com.example.msp_app.data.pagos

import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * El barrido de archivos de comprobante: **qué se borra y, sobre todo, qué
 * NO**.
 *
 * Un barrido de más se lleva la foto de un pago que todavía no sube, que es
 * dinero sin comprobar. Por eso las dos condiciones —sin fila Y viejo— se
 * prueban por separado, cada una con su control positivo.
 */
class ComprobantesHuerfanosTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val ahora = Instant.parse("2026-09-04T18:00:00Z")
    private val limite = ahora.minus(Duration.ofDays(7)).toEpochMilli()

    @Test
    fun `un archivo viejo y sin fila se borra`() {
        val huerfano = archivo("comprobante_pago_viejo.jpg", diasAtras = 30)

        assertEquals(
            listOf(huerfano),
            comprobantesHuerfanos(carpeta.root, rutasVivas = emptySet(), limiteMillis = limite)
        )
    }

    /**
     * **La foto de un pago que todavía no sube.** Aunque lleve un mes esperando
     * señal, su fila la referencia: borrarla sería tirar el comprobante de
     * dinero ya cobrado.
     */
    @Test
    fun `un archivo viejo QUE UNA FILA REFERENCIA se conserva`() {
        val vivo = archivo("comprobante_pago_pendiente.jpg", diasAtras = 30)

        assertEquals(
            emptyList<File>(),
            comprobantesHuerfanos(
                carpeta.root,
                rutasVivas = setOf(vivo.absolutePath),
                limiteMillis = limite
            )
        )
    }

    /**
     * **La captura que está ocurriendo ahora mismo.** El archivo existe antes de
     * que el abono se registre, así que durante unos segundos no tiene fila.
     * Barrer por "sin fila" a secas se llevaría la foto que el cobrador acaba de
     * tomar.
     */
    @Test
    fun `un archivo reciente sin fila se conserva`() {
        archivo("comprobante_pago_recien.jpg", diasAtras = 0)

        assertEquals(
            emptyList<File>(),
            comprobantesHuerfanos(carpeta.root, rutasVivas = emptySet(), limiteMillis = limite)
        )
    }

    /** Lo que no es un comprobante no se toca: en `filesDir` viven más cosas. */
    @Test
    fun `los archivos ajenos no se tocan`() {
        archivo("draft_sale_5.json", diasAtras = 90)
        archivo("otra_cosa.txt", diasAtras = 90)

        assertEquals(
            emptyList<File>(),
            comprobantesHuerfanos(carpeta.root, rutasVivas = emptySet(), limiteMillis = limite)
        )
    }

    /** Un directorio vacío o inexistente no es un error. */
    @Test
    fun `un directorio sin nada no barre nada`() {
        assertEquals(
            emptyList<File>(),
            comprobantesHuerfanos(carpeta.root, emptySet(), limite)
        )
        assertEquals(
            emptyList<File>(),
            comprobantesHuerfanos(File(carpeta.root, "no-existe"), emptySet(), limite)
        )
    }

    private fun archivo(nombre: String, diasAtras: Long): File {
        val archivo = File(carpeta.root, nombre)
        archivo.writeText("contenido")
        archivo.setLastModified(ahora.minus(Duration.ofDays(diasAtras)).toEpochMilli())
        return archivo
    }
}
