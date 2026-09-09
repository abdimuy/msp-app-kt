package com.example.msp_app.core.testing.outbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * # El contrato que los DOS outbox deben pasar
 *
 * La revisión final de esta rama encontró un patrón, no una lista de descuidos:
 * **cada endurecimiento tardío se detuvo en la frontera del módulo de visitas.**
 * El `NonCancellable`, el encolado incondicional, el barrido no-mudo y el fake
 * que sí puede fallar existían del lado de la visita y faltaban del lado del
 * dinero. El camino menos crítico había quedado siendo el más robusto.
 *
 * Portar los arreglos cierra los huecos de hoy y **no impide el sexto**. Esta
 * clase es el mecanismo que sí lo impide: una propiedad que se aprenda de un
 * lado se agrega **acá**, y desde ese momento **los dos lados tienen que
 * cumplirla o quedarse rojos**.
 *
 * ## Lo que se comparte es el contrato, no el código
 *
 * El pago sigue soberano: no hay una sola línea de producción compartida entre
 * los dos módulos por culpa de esta clase. Lo único común es la pregunta.
 *
 * ## Por qué esto no puede ser decorativo
 *
 * El modo de fallo obvio de un contrato así es que cada lado lo satisfaga con su
 * propio fake "conforme". Este plan ya pagó por esa lección: la undécima
 * aserción inerte fue un fake del worker de pagos que declaraba
 * `marcarSubida(...) = Unit` mientras su gemelo de visitas delegaba al DAO real,
 * así que la aserción leía su propia semilla. **Si un fake puede no hacer nada,
 * el contrato es decorativo.**
 *
 * Tres cosas lo impiden acá, y las tres son estructurales:
 *
 * 1. **La base es del contrato, no del sujeto.** [db] la crea `RoomTestBase` y
 *    el sujeto la recibe; todo lo que el contrato afirma sobre el estado lo lee
 *    de esa base con los DAO reales. Un sujeto no puede traer la suya.
 * 2. **Toda afirmación positiva tiene su control negativo, en el mismo test.**
 *    El primero de todos —`el instrumento discrimina, o el contrato entero no
 *    mide nada`— exige que el instrumento diga CERO antes y UNO después, y cero
 *    para un id ajeno. Un stub que siempre contesta lo mismo —lo que sea— muere
 *    ahí.
 * 3. **Los códigos de telemetría los pone cada módulo desde su constante de
 *    PRODUCCIÓN** ([codigoComprobanteNoGuardado], [codigoBarridoSinSubir]), y el
 *    contrato exige que el camino feliz **no** los emita. Un fake que emitiera
 *    siempre, o nunca, falla una de las dos mitades.
 *
 * ## Por qué esta clase vive en `:core:testing` y no en `:app` (Ruling BF)
 *
 * Porque **el contrato existe para hacer cumplir una disciplina y estaba él
 * mismo fuera del análisis**: `:app` es legacy y no aplica el plugin de detekt.
 * Es la misma forma que esta rama lleva persiguiendo — la compuerta que no se
 * cubre a sí misma. Acá gana detekt estricto, y no cuesta nada: no importa una
 * sola línea de `:app`, y `RoomTestBase` —del que hereda— ya vivía en este
 * módulo. Los dos sujetos concretos se quedan en `:app` porque necesitan los
 * adaptadores reales, que viven ahí.
 *
 * ## Cómo se agrega una propiedad
 *
 * Se escribe un `@Test` acá usando solo [CaminoDeEscritura]. Si un solo módulo
 * la cumple, el otro se pone rojo — que es el criterio de éxito de este arreglo.
 */
@Suppress(
    // Once propiedades con sus controles negativos, mas los ganchos que cada
    // modulo implementa. Partir la clase partiria el contrato, que es
    // justamente lo que este arreglo existe para impedir.
    "TooManyFunctions"
)
abstract class ContratoDelOutbox : RoomTestBase() {

    protected val ahora: Instant = Instant.parse("2026-09-11T18:00:00Z")
    protected val clock: FakeClock = FakeClock(ahora)
    protected val telemetria: RecordingTelemetry = RecordingTelemetry(clock)

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    /**
     * El camino de escritura REAL de un módulo, montado sobre la base del
     * contrato.
     *
     * Todas las lecturas son contra Room: no hay ningún método que un fake
     * pueda contestar de memoria sin que el control negativo lo delate.
     */
    protected interface CaminoDeEscritura {

        /**
         * Escribe el hecho [id] por el camino de producción, con [comprobantes]
         * fotos adjuntas. Devuelve `true` si el módulo dio el hecho por escrito.
         */
        suspend fun escribir(id: String, comprobantes: List<String> = emptyList()): Boolean

        /**
         * ¿Está escrito el hecho [id]? Devuelve 0 o 1 **por construcción** — el
         * id es la PK— así que sirve para la identidad y **no** para contar.
         */
        suspend fun filasDelHecho(id: String): Int

        /**
         * **Cuántas filas escribió el módulo en total**, sin filtrar por id.
         *
         * Existe porque [filasDelHecho] no puede devolver 2 nunca, así que una
         * aserción de "quedó una sola fila" apoyada en él **no podía fallar por
         * encontrar dos** — que es justo el riesgo que dice cubrir. El daño real
         * de un reintento que quema su clave no es una segunda fila con el mismo
         * id (imposible), es una segunda fila **con otro id**, y eso solo se ve
         * contando el total.
         */
        suspend fun filasTotales(): Int

        /** Cuántos comprobantes hay para ese hecho, leídos de la base del contrato. */
        suspend fun filasDeComprobante(id: String): Int

        /** Lo que el encolador de producción recibió, en orden. */
        fun encolados(): List<String>
    }

    // ─── lo que cada módulo aporta ───────────────────────────────────────────

    /** El camino sano: todo funciona. */
    protected abstract fun caminoSano(): CaminoDeEscritura

    /**
     * Un camino por cada ACOMPAÑANTE que puede fallar sin que el hecho deba
     * caerse: la foto en los dos módulos, y además la ubicación en el del
     * dinero. La lista es la que hace que agregar un acompañante nuevo entre
     * automáticamente a la propiedad 2.
     */
    protected abstract fun caminosConAcompananteRoto(): List<CaminoDeEscritura>

    /** Un camino cuyo encolado revienta. */
    protected abstract fun caminoConEncoladorQueTruena(): CaminoDeEscritura

    /**
     * Un camino que **cancela [job] como última escritura de la transacción**,
     * que es el instante exacto en que se abre la ventana entre el commit y el
     * encolado.
     */
    protected abstract fun caminoQueSeCancelaAlComitear(job: Job): CaminoDeEscritura

    /**
     * Siembra un comprobante viejo **sin padre** (su hecho ya no existe) con
     * [subidaEn] `null` = nunca entregado.
     */
    protected abstract suspend fun sembrarComprobanteSinPadre(id: String, subidaEn: String?)

    /** Corre el barrido de comprobantes del módulo, por su camino de producción. */
    protected abstract suspend fun barrerComprobantes()

    /** La constante de PRODUCCIÓN del módulo para "el comprobante no se pudo guardar". */
    protected abstract val codigoComprobanteNoGuardado: String

    /** La constante de PRODUCCIÓN del módulo para "se barrió lo que nunca subió". */
    protected abstract val codigoBarridoSinSubir: String

    // ─── propiedad 0: el instrumento ─────────────────────────────────────────

    /**
     * **El control positivo del contrato entero, y corre antes que todo.**
     *
     * Si un módulo entrara con un [CaminoDeEscritura] que no hace nada —o que
     * contesta que sí a todo—, cada propiedad de abajo pasaría leyendo su propia
     * semilla. Este test exige que el instrumento **cambie de respuesta**: cero
     * antes, uno después, y cero para un id que nunca se escribió.
     */
    @Test
    fun `el instrumento discrimina, o el contrato entero no mide nada`() = runTest {
        val camino = caminoSano()

        assertEquals("antes de escribir no hay nada", 0, camino.filasDelHecho(HECHO_A))
        assertEquals(emptyList<String>(), camino.encolados())

        assertTrue("el camino sano tiene que poder escribir", camino.escribir(HECHO_A))

        assertEquals("despues de escribir hay exactamente una", 1, camino.filasDelHecho(HECHO_A))
        assertEquals(
            "un id que nunca se escribio sigue en cero",
            0,
            camino.filasDelHecho(HECHO_AJENO)
        )
        assertEquals(listOf(HECHO_A), camino.encolados())

        assertEquals(
            "sin fotos adjuntas no hay filas de comprobante",
            0,
            camino.filasDeComprobante(HECHO_A)
        )
        assertTrue(camino.escribir(HECHO_B, comprobantes = listOf("IMG-1", "IMG-2")))
        assertEquals("con dos fotos adjuntas hay dos filas", 2, camino.filasDeComprobante(HECHO_B))
        assertNotEquals(
            "y las dos mediciones no son la misma respuesta constante",
            camino.filasDeComprobante(HECHO_A),
            camino.filasDeComprobante(HECHO_B)
        )

        // Y el contador SABE CONTAR: dos hechos distintos son dos filas. Sin
        // esto, la propiedad 4 se apoyaria en un instrumento que solo sabe decir
        // 0 o 1, y su asercion de "una sola fila" no podria fallar por encontrar
        // dos — que es exactamente lo que dice cubrir.
        assertEquals("dos hechos escritos son dos filas", 2, camino.filasTotales())
    }

    // ─── propiedad 1: se encola después del commit ───────────────────────────

    /**
     * **Se encola en la misma llamada que escribe, y una cancelación no puede
     * saltarse el encolado.**
     *
     * La escritura corre bajo el `viewModelScope` de la pantalla, que se cancela
     * en cuanto el cobrador navega hacia atrás. `withTransaction` termina en un
     * `withContext`, y **`withContext` lanza al reanudar si el job se canceló
     * mientras el bloque corría, aunque el bloque haya terminado bien** — o sea,
     * con la transacción ya comiteada. Sin `NonCancellable` sobre el par
     * completo, el resultado es un hecho escrito y jamás encolado.
     *
     * Es el defecto que la Task 5 arregló para visitas, y que el Arreglo C tuvo
     * que traer al dinero: el abono no tenía `NonCancellable` ninguno.
     */
    @Test
    fun `una cancelacion no deja el hecho escrito y sin encolar`() = runTest {
        val propio = Job()
        val camino = caminoQueSeCancelaAlComitear(propio)

        // `join()` y no `advanceUntilIdle()`: la transaccion de Room corre en su
        // propio `transactionExecutor`, fuera del scheduler del test.
        launch(propio) { camino.escribir(HECHO_A) }.join()

        assertEquals("la transaccion alcanzo a comitear", 1, camino.filasDelHecho(HECHO_A))
        assertEquals(
            "un hecho escrito y sin encolar es el defecto que la Task 5 existio para cerrar",
            listOf(HECHO_A),
            camino.encolados()
        )
    }

    // ─── propiedad 2: el acompañante no decide nada ──────────────────────────

    /**
     * **Un fallo de algo OPCIONAL no bloquea, no retrasa y no revierte.**
     *
     * La foto en los dos módulos; en el del dinero, además, la ubicación — que
     * hasta el Arreglo C se llevaba puesto el encolado del pago porque
     * compartían un `try`.
     *
     * Se afirman las tres mitades a la vez: el hecho quedó escrito, el resultado
     * sigue siendo exitoso, y el encolado ocurrió igual.
     */
    @Test
    fun `un acompanante que truena no bloquea, no revierte y no impide encolar`() = runTest {
        val caminos = caminosConAcompananteRoto()
        assertTrue("cada modulo declara al menos un acompanante", caminos.isNotEmpty())

        caminos.forEachIndexed { indice, camino ->
            val id = "$HECHO_A-$indice"

            val quedo = camino.escribir(id, comprobantes = listOf("IMG-1"))

            assertTrue("el acompanante roto no puede tumbar el hecho", quedo)
            assertEquals("y el hecho quedo escrito", 1, camino.filasDelHecho(id))
            assertEquals("y quedo encolado", listOf(id), camino.encolados())
        }
    }

    /**
     * **Y no se pierde en silencio.** El fallo del comprobante lleva el código de
     * PRODUCCIÓN del módulo, no uno inventado por el test.
     */
    @Test
    fun `el comprobante que no se pudo guardar se reporta con el codigo del modulo`() = runTest {
        val camino = caminosConAcompananteRoto().first()

        camino.escribir(HECHO_A, comprobantes = listOf("IMG-1"))

        assertTrue(
            "el fallo del comprobante tiene que emitir $codigoComprobanteNoGuardado",
            telemetria.recorded.any { it.name == codigoComprobanteNoGuardado }
        )
    }

    /**
     * **Control positivo del anterior:** el camino sano con la misma foto no
     * emite ese código y sí escribe la fila. Sin esto, la aserción de arriba
     * pasaría con un módulo que emitiera el código siempre.
     */
    @Test
    fun `control positivo, el camino sano guarda la foto y no reporta nada`() = runTest {
        val camino = caminoSano()

        camino.escribir(HECHO_A, comprobantes = listOf("IMG-1"))

        assertEquals(1, camino.filasDeComprobante(HECHO_A))
        assertTrue(
            telemetria.recorded.none { it.name == codigoComprobanteNoGuardado }
        )
    }

    // ─── propiedad 3: nada se borra en silencio ──────────────────────────────

    /**
     * **Lo no entregado que se poda emite telemetría.**
     *
     * Un comprobante sin padre y viejo se barre —el disco del teléfono no puede
     * crecer sin techo— pero borrarlo mudo convertiría el barrido en el desagüe
     * silencioso de toda la evidencia que nunca llegó al servidor.
     */
    @Test
    fun `lo que se poda sin haber subido nunca se reporta`() = runTest {
        sembrarComprobanteSinPadre("IMG-PERDIDA", subidaEn = null)

        barrerComprobantes()

        assertTrue(
            "el barrido tiene que emitir $codigoBarridoSinSubir",
            telemetria.recorded.any { it.name == codigoBarridoSinSubir }
        )
    }

    /**
     * **Control positivo del anterior:** lo que YA subió se poda exactamente
     * igual y **no** reporta pérdida. Sin esta mitad, el evento de arriba no
     * distinguiría "se perdió evidencia" de "el barrido corrió".
     */
    @Test
    fun `control positivo, lo que ya subio se poda sin reportar perdida`() = runTest {
        sembrarComprobanteSinPadre("IMG-ENTREGADA", subidaEn = "2026-08-02T10:00:00Z")

        barrerComprobantes()

        assertTrue(
            "lo que llego al servidor no es una perdida",
            telemetria.recorded.none { it.name == codigoBarridoSinSubir }
        )
    }

    // ─── propiedad 4: el reintento no quema su clave ─────────────────────────

    /**
     * **El reintento es idempotente y no quema su clave.**
     *
     * El id lo acuña el teléfono y es la clave de idempotencia del servidor:
     * reintentar con el MISMO id es lo que impide que el mismo hecho suba dos
     * veces. Acuñar uno nuevo en el reintento —o dejar dos filas locales— rompe
     * esa garantía del lado del que la produce.
     */
    @Test
    fun `el reintento con la misma clave no deja dos filas ni acuna una clave nueva`() = runTest {
        val camino = caminoSano()

        camino.escribir(HECHO_A)
        camino.escribir(HECHO_A)

        // Se cuenta el TOTAL, no las filas con ese id: `filasDelHecho` devuelve
        // 0 o 1 por construccion, asi que apoyar esta asercion en el no podria
        // detectar la unica forma en que el reintento se rompe de verdad — una
        // segunda fila bajo una clave recien acunada.
        assertEquals("una sola fila en toda la base", 1, camino.filasTotales())
        assertEquals("y es la del id que se pidio", 1, camino.filasDelHecho(HECHO_A))
        assertEquals(
            "los dos encolados van con la MISMA clave",
            listOf(HECHO_A, HECHO_A),
            camino.encolados()
        )
    }

    // ─── propiedad 5: cero errores tragados ──────────────────────────────────

    /**
     * **Un encolado que truena no puede quedar mudo, ni deshacer el hecho.**
     *
     * Es el punto donde la visita había quedado PEOR que el pago: `enqueueUpload`
     * era el único paso post-commit sin guarda, así que un `WorkManager` que
     * lanzara producía `FALLO_EL_GUARDADO` —cuyo contrato dice "nada quedó
     * escrito"— con la visita ya escrita.
     */
    @Test
    fun `un encolado que truena deja el hecho escrito y emite telemetria`() = runTest {
        val camino = caminoConEncoladorQueTruena()

        val quedo = camino.escribir(HECHO_A)

        assertEquals("el hecho quedo escrito", 1, camino.filasDelHecho(HECHO_A))
        assertTrue("y el modulo no puede decir que no se guardo nada", quedo)
        assertTrue(
            "ningun catch es mudo",
            telemetria.recorded.any { it.type == TelemetryEventType.ERROR }
        )
    }

    /**
     * **Control positivo del anterior, y de toda la propiedad 5:** el camino
     * sano no emite NI UN error. Un módulo que reportara siempre —o un
     * `RecordingTelemetry` sembrado— muere acá.
     */
    @Test
    fun `control positivo, el camino feliz no emite un solo error`() = runTest {
        val camino = caminoSano()

        camino.escribir(HECHO_A, comprobantes = listOf("IMG-1"))

        assertEquals(
            "el camino feliz no reporta nada",
            emptyList<String>(),
            telemetria.recorded.filter { it.type == TelemetryEventType.ERROR }.map { it.name }
        )
    }

    protected companion object {
        const val HECHO_A: String = "hecho-victoria-0001"
        const val HECHO_B: String = "hecho-victoria-0002"
        const val HECHO_AJENO: String = "hecho-que-nadie-escribio"

        /** Viejo de sobra para que el barrido lo alcance (la ventana es de 7 días). */
        const val CREADA_HACE_MUCHO: String = "2026-08-01T10:00:00Z"
    }
}
