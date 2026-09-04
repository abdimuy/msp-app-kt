package com.example.msp_app.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`NewVisitDialog` está retirado, y esto lo mide.**
 *
 * ## Por qué el diálogo tenía que morir en esta tarea
 *
 * Mientras los dos caminos estaban vivos, **el mismo hecho de campo caía en dos
 * cubetas distintas según qué UI abrió el cobrador**: "pidió reagendar" por el
 * diálogo escribía la fecha dentro del texto libre de `NOTA`, así que la
 * derivación veía `PROMETIO_PROXIMA` con `fechaPromesa` nula → `REGRESAS` →
 * *vencidos*; el mismo hecho por la pantalla nueva escribe `PROMESA_FECHA` y
 * deriva `DIFERIDO` → *hoy*. Una misma realidad con dos respuestas, decididas
 * por un menú. Retirarlo es parte de que la pantalla nueva sea correcta, no
 * limpieza posterior.
 *
 * ## Cómo se prueba una ausencia
 *
 * Una ausencia no es un hallazgo hasta probar que la búsqueda habría encontrado
 * la cosa. Por eso cada `assertEquals(0, …)` de aquí abajo viene con su
 * **control positivo**: la MISMA función, sobre el MISMO árbol, buscando un
 * símbolo hermano que sí existe (`NewForgivenessDialog`, la condonación, que
 * este plan declaró intacta). Si el escáner mirara el directorio equivocado o
 * no leyera nada, el control positivo daría 0 y el test se pondría rojo antes de
 * poder mentir sobre el diálogo.
 */
class RetiroDelDialogoDeVisitaTest {

    /**
     * `src/main/java` de `:app`. El directorio de trabajo de un test de Gradle
     * es el del módulo; el `?:` cubre correr la clase desde la raíz del repo.
     */
    private val fuentes: File =
        File("src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")

    private val archivos: List<File> by lazy {
        fuentes.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Los archivos cuyo **código** menciona [simbolo]. Las líneas de comentario
     * y de KDoc se descartan: este plan documenta en el código los defectos que
     * mató, y un KDoc que explica por qué el diálogo se fue no es una llamada al
     * diálogo. Contarlo como referencia haría imposible probar la ausencia sin
     * borrar la explicación, que es justo lo contrario de lo que se quiere.
     *
     * La coincidencia es de **palabra completa**: buscar `SalesScreen` a secas
     * encontraría `UnifiedSalesScreen`, que es otra pantalla y que el brief
     * ordena no tocar — el test habría fallado por la razón equivocada, o peor,
     * habría pasado por ella.
     */
    private fun archivosQueMencionan(simbolo: String): List<String> {
        val patron = Regex("(?<![A-Za-z0-9_])" + Regex.escape(simbolo) + "(?![A-Za-z0-9_])")
        return archivos.filter { archivo ->
            archivo.readText().lineSequence().any { linea ->
                val limpia = linea.trim()
                val esComentario = limpia.startsWith("//") ||
                    limpia.startsWith("*") ||
                    limpia.startsWith("/*")
                !esComentario && patron.containsMatchIn(limpia)
            }
        }.map { it.path }
    }

    @Test
    fun `el escaner de verdad lee el codigo de la app`() {
        assertTrue("no se encontró $fuentes", fuentes.isDirectory)
        assertTrue("no se leyó ni un .kt", archivos.size > 100)
    }

    /**
     * **Nada enruta al diálogo de visita, y nada lo compone.** Ni el archivo
     * queda: no es "sin llamadas", es que no existe.
     */
    @Test
    fun `nada en la app menciona NewVisitDialog`() {
        assertEquals(emptyList<String>(), archivosQueMencionan("NewVisitDialog"))
        assertEquals(
            false,
            File(
                fuentes,
                "com/example/msp_app/features/visit/components/NewVisitDialog.kt"
            ).exists()
        )
    }

    /**
     * **Control positivo del test de arriba.** El mismo escáner encuentra la
     * condonación —el otro diálogo del mismo bloque de acciones, que este plan
     * NO toca— en al menos dos archivos: su definición y su llamador. O sea que
     * el cero de arriba es una ausencia medida, no un método ciego.
     */
    @Test
    fun `control positivo, el escaner SI encuentra el dialogo de condonacion`() {
        val condonacion = archivosQueMencionan("NewForgivenessDialog")
        assertTrue("el escáner no encontró la condonación: $condonacion", condonacion.size >= 2)
    }

    /**
     * El **ticket de visita legado** se va con el diálogo: era su única puerta
     * (`NewVisitDialog` → `Screen.VisitTicket`). Lo reemplaza el ticket de la
     * Task 20, al que se llega al registrar la visita.
     */
    @Test
    fun `nada en la app menciona el ticket de visita legado`() {
        assertEquals(emptyList<String>(), archivosQueMencionan("VisitTicketScreen"))
        assertEquals(emptyList<String>(), archivosQueMencionan("visit_ticket/"))
    }

    /**
     * **Control positivo del anterior.** El ticket de PAGO legado sí sigue vivo
     * —lo usan la condonación y el diálogo de pago, que este plan no toca—, así
     * que el escáner lo encuentra. Dos rutas de la misma forma, una retirada y
     * una viva, medidas con la misma consulta.
     */
    @Test
    fun `control positivo, el ticket de pago legado SI sigue en el codigo`() {
        val ticket = archivosQueMencionan("payment_ticket/")
        assertTrue("el escáner no encontró el ticket de pago: $ticket", ticket.isNotEmpty())
    }

    /**
     * **La lista de tres pestañas se fue.** `SalesScreen` era una fila por
     * VENTA: un cliente con dos muebles aparecía dos veces, como si fueran dos
     * personas. La reemplaza la lista por cliente de la Task 17.
     */
    @Test
    fun `nada en la app menciona SalesScreen`() {
        assertEquals(emptyList<String>(), archivosQueMencionan("SalesScreen"))
    }

    /**
     * **Control positivo del anterior**, y a la vez la guarda que el brief pide
     * explícitamente: `UnifiedSalesScreen` —la lista de ventas LOCALES, otro
     * trabajo— **sigue ahí**. El mismo escáner que no encuentra `SalesScreen` sí
     * encuentra ésta, así que el vacío de arriba no es que la consulta falle.
     */
    @Test
    fun `control positivo, UnifiedSalesScreen sigue intacta`() {
        val unificada = archivosQueMencionan("UnifiedSalesScreen")
        assertTrue("UnifiedSalesScreen desapareció: $unificada", unificada.size >= 2)
    }

    /**
     * **El botón de "enviar pendientes" no se tocó.** El brief lo protege: una
     * visita pendiente todavía bloquea "INICIALIZAR SEMANA"
     * (`AuthViewModel.kt`), así que quitarlo antes de verificar el reconciliador
     * en campo dejaría al cobrador atorado.
     */
    @Test
    fun `el envio de pendientes sigue cableado`() {
        assertTrue(archivosQueMencionan("onSyncPendingVisits").isNotEmpty())
        assertTrue(archivosQueMencionan("onSyncPendingPayments").isNotEmpty())
    }
}
