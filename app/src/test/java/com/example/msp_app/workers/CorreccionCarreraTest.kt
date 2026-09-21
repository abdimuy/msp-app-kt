package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncContext
import com.example.msp_app.core.common.sync.pendingwork.domain.models.SyncResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.LocalSalesWorkEnqueuer
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.sync.pendingwork.data.gates.InMemorySessionSyncGate
import com.example.msp_app.core.sync.pendingwork.data.synchronizers.LocalSalesPendingSynchronizer
import com.example.msp_app.core.sync.pendingwork.di.PendingWorkSyncFactory
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentaDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.port.RelojPort
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardadoRechazadoException
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-carrera-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"
private const val CLAIM_SUBIDOR = "claim-uuid-del-subidor"

/** Epoch base de todas las pruebas: 2026-09-20T12:00:00Z. Nunca el reloj real. */
private const val BASE_EPOCH = 1_789_041_600_000L

private const val NOMBRE_ORIGINAL = "Guadalupe Hernandez Solis"
private const val NOMBRE_CORREGIDO = "Guadalupe Hernandez Solis de Ramos"

/**
 * Los escenarios ADVERSARIALES de la carrera entre corregir una venta y
 * subirla (Task 4 del plan "Corregir una venta antes de que suba"), con el
 * worker REAL, el DAO REAL y los casos de uso REALES de
 * `:feature:ventaCorreccion`. Sólo la red es falsa.
 *
 * **Cómo se hace determinista, y por qué así**: el worker recibe `ventasApi`
 * por constructor, así que *"la subida ya empezó"* es literalmente **el cuerpo
 * del fake `crearVenta`** — dentro de esa función suspend la prueba ejecuta el
 * reclamo o el guardado del usuario y después responde 2xx o lanza. El mismo
 * truco con `resolveVendedoresForEmail` da el instante *"el worker está
 * armando el cuerpo"*. Un solo hilo, interleaving exacto, cero relojes.
 *
 * El tiempo NUNCA es el del sistema: [RelojDePrueba] lo deriva del tiempo
 * VIRTUAL del despachador de `runTest` más un desfase que la prueba mueve a
 * mano cuando quiere saltar un arrendamiento. Los `delay` que aparecen abajo
 * son tiempo virtual (avanzan al instante) y representan *"la subida tarda"*,
 * jamás un sincronizador entre corrutinas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CorreccionCarreraTest : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource
    private lateinit var fakeImageFile: File

    private val vendedores = listOf(
        VendedorDTO(
            id = "v-uuid-1",
            usuario_id = "u-uuid-1",
            email = "vendedor@muebleriamsp.mx",
            nombre = "Carlos Vendedor"
        )
    )

    private val ventaDTO = VentaDTO(
        id = SALE_ID,
        situacion = "PENDIENTE",
        sincronizacion = "PENDIENTE",
        tipo_venta = "CREDITO",
        fecha_venta = "2026-09-20T12:00:00Z",
        created_at = "2026-09-20T12:00:01Z",
        updated_at = "2026-09-20T12:00:01Z"
    )

    @Before
    fun setUpPrereqs() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:stub")
                    .setApiKey("stub-api-key")
                    .setProjectId("stub-project")
                    .build()
            )
        }
        saleDataSource = LocalSaleDataSource(context)
        productDataSource = SaleProductLocalDataSource(context)
        comboDataSource = ComboLocalDataSource(context)
        fakeImageFile = File.createTempFile("recibo", ".jpg")
        fakeImageFile.writeBytes(ByteArray(16) { 0xFF.toByte() })
    }

    // ─── reloj y tiempo ───────────────────────────────────────────────────

    /**
     * Reloj de pared derivado del tiempo VIRTUAL del despachador más
     * [desfase], el salto que la prueba fuerza a mano para vencer un
     * arrendamiento sin esperar. Implementa [RelojPort] para que el editor y
     * el worker compartan exactamente el mismo "ahora".
     */
    private class RelojDePrueba(private val scheduler: TestCoroutineScheduler) :
        RelojPort, AppClock {
        var desfase: Long = 0L
        override fun ahoraEpochMillis(): Long = BASE_EPOCH + scheduler.currentTime + desfase
        override fun now(): Instant = Instant.ofEpochMilli(ahoraEpochMillis())
    }

    // ─── el editor real ───────────────────────────────────────────────────

    private object ReencolarNoOp : ReencolarSubidaPort {
        override fun cancelarTrabajoEncolado(saleId: String) = Unit
        override fun reencolar(saleId: String, userEmail: String) = Unit
    }

    private fun puerto() = RoomVentaLocalCorreccionAdapter(
        db,
        db.localSaleDao(),
        db.localSaleProduct(),
        db.localSaleComboDao()
    )

    private fun reclamar(reloj: RelojPort) = ReclamarCorreccion(puerto(), reloj, ReencolarNoOp)
    private fun guardar(reloj: RelojPort) = GuardarCorreccion(puerto(), reloj, ReencolarNoOp)

    // ─── la venta ─────────────────────────────────────────────────────────

    private suspend fun sembrarVenta(saleId: String = SALE_ID) {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = saleId, clientName = NOMBRE_ORIGINAL)
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = saleId, articuloId = 100)
        )
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = saleId)
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-001",
                saleId = saleId,
                imageUri = fakeImageFile.absolutePath
            )
        )
    }

    // ─── el worker real ───────────────────────────────────────────────────

    /**
     * Construye el worker con los costurones inyectados y corre `doWork` EN
     * LA MISMA corrutina de la prueba — no en `runBlocking`, a propósito: así
     * el latido hereda el despachador de prueba y su `delay` es tiempo
     * virtual, observable y sin espera real.
     */
    @Suppress("LongParameterList")
    private suspend fun correrWorker(
        api: VentasApi,
        reloj: RelojDePrueba,
        saleId: String = SALE_ID,
        resolver: suspend (String) -> Pair<List<VendedorDTO>, Int?> = { Pair(vendedores, 3) },
        latidoMs: Long = LocalSaleClaimLeases.UPLOAD_HEARTBEAT_MS,
        renovar: (suspend (String, String, Long) -> Int)? = null,
        claimId: String = CLAIM_SUBIDOR
    ): ListenableWorker.Result {
        val inputData = androidx.work.Data.Builder()
            .putString("local_sale_id", saleId)
            .putString("user_email", EMAIL)
            .build()

        val worker = TestListenableWorkerBuilder<PendingLocalSalesWorker>(context, inputData)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = PendingLocalSalesWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    resolveVendedoresForEmail = resolver,
                    ventasApi = api,
                    uploadFailureRepository =
                    com.example.msp_app.features.sales.upload.data.RoomUploadFailureRepository(
                        db.localSaleDao()
                    ),
                    nowEpochMillis = reloj::ahoraEpochMillis,
                    latidoDeSubidaMs = latidoMs,
                    renovarArrendamientoDeSubida = renovar
                        ?: { s, c, n -> db.localSaleDao().renewUploadClaim(s, c, n) },
                    nuevoClaimId = { claimId }
                )
            })
            .build()

        return (worker as PendingLocalSalesWorker).doWork()
    }

    private fun api(
        crear: suspend (String, RequestBody, List<MultipartBody.Part>) -> VentaDTO,
        obtener: suspend (String) -> VentaDTO = {
            throw AssertionError("obtenerVenta no debería llamarse en esta prueba")
        }
    ): VentasApi = object : VentasApi {
        override suspend fun crearVenta(
            idempotencyKey: String,
            datos: RequestBody,
            imagen: List<MultipartBody.Part>
        ): VentaDTO = crear(idempotencyKey, datos, imagen)

        override suspend fun obtenerVenta(id: String): VentaDTO = obtener(id)
    }

    private fun RequestBody.comoTexto(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    /** Cuántas ventas hay en la base, sin la ventana de 7 días de `getAllSales`. */
    private suspend fun cuantasVentas(): Int = db.localSaleDao().getSalesByStatus(true).size +
        db.localSaleDao().getSalesByStatus(false).size

    /**
     * Huella textual de la venta y de TODOS sus hijos. `LocalSaleEntity` y
     * `LocalSaleImageEntity` no son `data class` (no tienen `equals`), así que
     * compararlas con `assertEquals` compararía identidades de objeto y pasaría
     * siempre. Esta huella sí compara contenido, columna por columna.
     */
    private suspend fun huella(saleId: String): String {
        val s = saleDataSource.getSaleById(saleId)!!
        val venta = listOf(
            s.LOCAL_SALE_ID, s.NOMBRE_CLIENTE, s.FECHA_VENTA, s.LATITUD, s.LONGITUD,
            s.DIRECCION, s.PARCIALIDAD, s.ENGANCHE, s.TELEFONO, s.FREC_PAGO,
            s.AVAL_O_RESPONSABLE, s.NOTA, s.DIA_COBRANZA, s.PRECIO_TOTAL,
            s.TIEMPO_A_CORTO_PLAZOMESES, s.MONTO_A_CORTO_PLAZO, s.MONTO_DE_CONTADO,
            s.ENVIADO, s.NUMERO, s.COLONIA, s.POBLACION, s.CIUDAD, s.TIPO_VENTA,
            s.ZONA_CLIENTE_ID, s.ZONA_CLIENTE, s.CLIENTE_ID, s.IDEMPOTENCY_KEY,
            s.CLAIM_ID, s.CLAIM_KIND, s.CLAIMED_AT, s.REVISION, s.CORRECCION_NO_ENVIADA
        ).joinToString("|")
        val productos = productDataSource.getProductsForSale(saleId).joinToString("|")
        val combos = comboDataSource.getCombosForSale(saleId).joinToString("|")
        val imagenes = saleDataSource.getImagesForSale(saleId).joinToString("|") {
            "${it.LOCAL_SALE_IMAGE_ID}/${it.IMAGE_URI}/${it.FECHA_SUBIDA}/${it.SERVER_UUID}"
        }
        return "$venta\n$productos\n$combos\n$imagenes"
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. El trabajador arranca a mitad de la edición
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `el subidor que arranca a mitad de la edicion se frena SIN tocar la red`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()

        val reclamo = reclamar(reloj)(SALE_ID)
        assertTrue(
            "el editor debe poder reclamar una venta libre",
            reclamo is ResultadoReclamo.Reclamada
        )

        var llamadas = 0
        val resultado = correrWorker(
            api = api(crear = { _, _, _ ->
                llamadas++
                ventaDTO
            }),
            reloj = reloj
        )

        assertEquals(ListenableWorker.Result.retry(), resultado)
        assertEquals("crearVenta NO debe llamarse con una edición viva", 0, llamadas)

        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertFalse("la venta no puede quedar marcada como enviada", fila.ENVIADO)
        assertEquals("el candado sigue siendo del editor", "EDIT", fila.CLAIM_KIND)
        assertEquals(
            (reclamo as ResultadoReclamo.Reclamada).claimId,
            fila.CLAIM_ID
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. El usuario guarda mientras la subida YA empezó
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `guardar con la subida en vuelo se rechaza y la venta sube con los datos originales`() =
        runTest {
            val reloj = RelojDePrueba(testScheduler)
            sembrarVenta()

            // El editor reclamó hace rato y su arrendamiento venció (la app se
            // quedó abierta media hora). Por eso el subidor pudo tomar la fila.
            val reclamo = reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada
            reloj.desfase += LocalSaleClaimLeases.EDIT_LEASE_MS

            var rechazo: GuardadoRechazadoException? = null
            val resultado = correrWorker(
                reloj = reloj,
                api = api(
                    crear = { _, _, _ ->
                        // "El usuario toca guardar": la subida ya está en vuelo.
                        rechazo = runCatching {
                            guardar(reloj)(
                                SALE_ID,
                                reclamo.claimId,
                                reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                                reclamo.venta.productos,
                                reclamo.venta.combos,
                                EMAIL
                            )
                        }.exceptionOrNull() as? GuardadoRechazadoException
                        ventaDTO
                    }
                )
            )

            assertEquals(ListenableWorker.Result.success(), resultado)
            assertNotNull("el guardado DEBE ser rechazado con la subida en vuelo", rechazo)
            assertEquals(
                "mientras el POST vuela, el estado que ve el usuario es Se está enviando",
                EstadoCorreccion.SeEstaEnviando,
                rechazo!!.estado
            )

            val fila = saleDataSource.getSaleById(SALE_ID)!!
            assertTrue(fila.ENVIADO)
            assertEquals(
                "nada del guardado rechazado se escribió",
                NOMBRE_ORIGINAL,
                fila.NOMBRE_CLIENTE
            )
            assertEquals("el rechazo no sube la REVISION", 0, fila.REVISION)
            assertFalse(
                "nada divergió: el servidor tiene exactamente lo que el teléfono cree",
                fila.CORRECCION_NO_ENVIADA
            )
            assertNull("el candado queda cerrado", fila.CLAIM_ID)
            assertEquals("una sola venta en la base", 1, cuantasVentas())
        }

    @Test
    fun `si el POST falla, el guardado SI commitea y la siguiente corrida sube lo corregido`() =
        runTest {
            val reloj = RelojDePrueba(testScheduler)
            sembrarVenta()

            var llamadas = 0
            val cuerpos = mutableListOf<String>()

            // Corrida 1: sin latido (el proceso no late), la subida tarda más
            // que el arrendamiento y muere con IOException. El editor alcanza a
            // tomar la fila y a guardar.
            val corrida1 = correrWorker(
                reloj = reloj,
                latidoMs = 0L,
                api = api(
                    crear = { _, datos, _ ->
                        llamadas++
                        cuerpos += datos.comoTexto()
                        reloj.desfase += LocalSaleClaimLeases.UPLOAD_LEASE_MS + 1
                        val reclamo = reclamar(reloj)(SALE_ID)
                        assertTrue(
                            "con el arrendamiento de subida vencido el editor SÍ puede reclamar",
                            reclamo is ResultadoReclamo.Reclamada
                        )
                        reclamo as ResultadoReclamo.Reclamada
                        guardar(reloj)(
                            SALE_ID,
                            reclamo.claimId,
                            reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                            reclamo.venta.productos,
                            reclamo.venta.combos,
                            EMAIL
                        )
                        throw IOException("se cayó la señal a media subida")
                    }
                )
            )

            assertEquals(ListenableWorker.Result.retry(), corrida1)
            val trasCorrida1 = saleDataSource.getSaleById(SALE_ID)!!
            assertFalse("un POST fallido nunca marca enviada", trasCorrida1.ENVIADO)
            assertEquals(
                "la corrección SÍ quedó escrita",
                NOMBRE_CORREGIDO,
                trasCorrida1.NOMBRE_CLIENTE
            )
            assertEquals(1, trasCorrida1.REVISION)

            // Corrida 2: la señal vuelve.
            val corrida2 = correrWorker(
                reloj = reloj,
                api = api(
                    crear = { _, datos, _ ->
                        llamadas++
                        cuerpos += datos.comoTexto()
                        ventaDTO
                    }
                )
            )

            assertEquals(ListenableWorker.Result.success(), corrida2)
            assertEquals(2, llamadas)
            assertTrue("el primer cuerpo llevaba lo original", cuerpos[0].contains(NOMBRE_ORIGINAL))
            assertTrue(
                "el segundo cuerpo lleva lo corregido",
                cuerpos[1].contains(NOMBRE_CORREGIDO)
            )

            val fila = saleDataSource.getSaleById(SALE_ID)!!
            assertTrue(fila.ENVIADO)
            assertEquals(NOMBRE_CORREGIDO, fila.NOMBRE_CLIENTE)
            // Cambió en la Task 6b, y el cambio ES el punto. Esta prueba
            // afirmaba que aquí NO había divergencia, dando por hecho que un
            // POST que murió con `IOException` no dejó nada en el servidor.
            // Eso no se puede saber desde el teléfono: la señal se cayó A
            // MEDIA subida, así que el servidor pudo haberse quedado con el
            // cuerpo ORIGINAL. Y el 2xx de la corrida 2 tampoco lo desmiente:
            // llevaba la MISMA `Idempotency-Key`, y un servidor idempotente
            // responde a una llave repetida replicando la respuesta guardada
            // del primer intento, sin mirar el cuerpo nuevo.
            //
            // El ancla (`REVISION_POSTEADA`) dice que el primer cuerpo
            // emitido fue el de `REVISION=0` y la fila ya va en 1: se marca.
            // Falso positivo posible, aceptado a propósito — la oficina
            // revisa una venta que quizá estaba bien, en vez de despachar una
            // que el cliente no pidió.
            assertTrue(
                "no se puede saber si aquel POST a medias llegó: la duda se marca",
                fila.CORRECCION_NO_ENVIADA
            )
        }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. La app muere entre guardar y reencolar
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `la app muere entre guardar y reencolar - el barrido la encuentra y el subidor real la sube`() =
        runTest {
            val reloj = RelojDePrueba(testScheduler)
            sembrarVenta()

            val reclamo = reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada
            guardar(reloj)(
                SALE_ID,
                reclamo.claimId,
                reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                reclamo.venta.productos,
                reclamo.venta.combos,
                EMAIL
            )
            // "La app muere": nadie reencoló. Sólo queda el commit en Room.

            val encolador = EncoladorGrabador()
            val barrido = LocalSalesPendingSynchronizer(
                fetchPending = {
                    PendingWorkSyncFactory.ventasParaElBarrido(
                        saleDataSource,
                        reloj
                    )
                },
                enqueuer = encolador
            )
            val resultadoBarrido = barrido.sync(SyncContext(userId = "u1", userEmail = EMAIL))

            assertEquals(SyncResult.Enqueued(itemCount = 1, workRequestCount = 1), resultadoBarrido)
            assertTrue(encolador.ids.contains(SALE_ID))

            var cuerpo = ""
            val resultado = correrWorker(
                reloj = reloj,
                api = api(crear = { _, datos, _ ->
                    cuerpo = datos.comoTexto()
                    ventaDTO
                })
            )

            assertEquals(ListenableWorker.Result.success(), resultado)
            assertTrue("sube lo corregido, no lo original", cuerpo.contains(NOMBRE_CORREGIDO))
            assertTrue(saleDataSource.getSaleById(SALE_ID)!!.ENVIADO)
        }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Corregir una venta ya subida es imposible
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `una venta ya subida no se puede corregir, ni ella ni sus hijos se mueven`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()

        assertEquals(
            ListenableWorker.Result.success(),
            correrWorker(reloj = reloj, api = api(crear = { _, _, _ -> ventaDTO }))
        )

        val antes = huella(SALE_ID)

        val reclamo = reclamar(reloj)(SALE_ID)

        assertEquals(
            ResultadoReclamo.NoCorregible(EstadoCorreccion.YaSeEnvio),
            reclamo
        )
        assertEquals(
            "ni la venta ni sus hijos pueden moverse un milímetro",
            antes,
            huella(SALE_ID)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Corregir, perder la señal, recuperarla
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `corregir, perder la senal y recuperarla sube UNA sola vez y con lo corregido`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()

        var llamadas = 0
        var exitosas = 0
        val cuerpos = mutableListOf<String>()

        val corrida1 = correrWorker(
            reloj = reloj,
            api = api(
                crear = { _, datos, _ ->
                    llamadas++
                    cuerpos += datos.comoTexto()
                    // "Sin señal" en un teléfono real es el DNS que no
                    // resuelve: no sale un byte. Importa que sea ESTA
                    // excepción y no una `IOException` pelona — la ronda de
                    // arreglo 1 de la Task 6b borra el ancla sólo ante un
                    // fallo que PRUEBE que no hubo conexión, y este escenario
                    // (el del título del plan: capturar sin señal, corregir,
                    // subir al volver la red) no debe terminar marcado.
                    throw UnknownHostException("api.muebleriamsp.invalid")
                }
            )
        )
        assertEquals(ListenableWorker.Result.retry(), corrida1)

        val trasFallo = saleDataSource.getSaleById(SALE_ID)!!
        assertNull(
            "un intento fallido suelta el candado: la venta queda corregible YA",
            trasFallo.CLAIM_ID
        )
        assertEquals("network_error", trasFallo.LAST_UPLOAD_ERROR_CODE)

        val reclamo = reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada
        guardar(reloj)(
            SALE_ID,
            reclamo.claimId,
            reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
            reclamo.venta.productos,
            reclamo.venta.combos,
            EMAIL
        )

        val corrida2 = correrWorker(
            reloj = reloj,
            api = api(
                crear = { _, datos, _ ->
                    llamadas++
                    exitosas++
                    cuerpos += datos.comoTexto()
                    ventaDTO
                }
            )
        )

        assertEquals(ListenableWorker.Result.success(), corrida2)
        assertEquals("crearVenta se llamó exactamente dos veces", 2, llamadas)
        assertEquals("sólo la segunda devolvió 2xx", 1, exitosas)
        assertTrue(cuerpos[1].contains(NOMBRE_CORREGIDO))

        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertTrue(fila.ENVIADO)
        assertEquals(NOMBRE_CORREGIDO, fila.NOMBRE_CLIENTE)
        // Vuelve a `assertFalse` tras la ronda de arreglo 1 de la Task 6b, y
        // es el punto de esa ronda: éste es el caso ESTELAR del plan y todo
        // salió bien. El `UnknownHostException` de la corrida 1 prueba que no
        // salió un byte, así que el ancla se borró y la corrida 2 ancló ya con
        // la corrección adentro. Marcarlo habría puesto "La revisa la oficina"
        // en casi toda corrección — y un aviso que sale siempre deja de avisar.
        assertFalse(
            "no hubo conexión en el primer intento: la corrección viajó y no hay nada que revisar",
            fila.CORRECCION_NO_ENVIADA
        )
        assertNull("el registro de fallo quedó limpio", fila.LAST_UPLOAD_ERROR_CODE)
        assertNull(fila.LAST_UPLOAD_HTTP_CODE)
        assertEquals("una sola venta, con su UUID de siempre", 1, cuantasVentas())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. Cuerpo mezclado: con el candado ANTES de leer, es inalcanzable
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `mientras el subidor arma el cuerpo, corregir es IMPOSIBLE - el cuerpo no puede salir mezclado`() =
        runTest {
            val reloj = RelojDePrueba(testScheduler)
            sembrarVenta()

            // Un claimId de edición legítimo pero VIEJO: el editor lo tomó y su
            // arrendamiento venció, que es la única forma de que el subidor
            // tenga la fila mientras alguien conserva un claimId de edición.
            val viejo = reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada
            reloj.desfase += LocalSaleClaimLeases.EDIT_LEASE_MS

            var reclamoDurante: ResultadoReclamo? = null
            var rechazoDurante: GuardadoRechazadoException? = null
            var cuerpo = ""

            val resultado = correrWorker(
                reloj = reloj,
                // `resolveVendedoresForEmail` se llama con el candado YA tomado
                // y ANTES de leer productos, combos e imágenes: es exactamente
                // el instante "el worker está armando el cuerpo".
                resolver = {
                    reclamoDurante = reclamar(reloj)(SALE_ID)
                    rechazoDurante = runCatching {
                        guardar(reloj)(
                            SALE_ID,
                            viejo.claimId,
                            viejo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                            viejo.venta.productos,
                            viejo.venta.combos,
                            EMAIL
                        )
                    }.exceptionOrNull() as? GuardadoRechazadoException
                    Pair(vendedores, 3)
                },
                api = api(crear = { _, datos, _ ->
                    cuerpo = datos.comoTexto()
                    ventaDTO
                })
            )

            assertEquals(
                "el editor NO puede tomar la fila mientras el subidor arma el cuerpo",
                ResultadoReclamo.NoCorregible(EstadoCorreccion.SeEstaEnviando),
                reclamoDurante
            )
            assertNotNull("y el guardado con un claimId viejo tampoco pasa", rechazoDurante)

            assertEquals(ListenableWorker.Result.success(), resultado)
            assertTrue(
                "el cuerpo que salió es el original, entero",
                cuerpo.contains(NOMBRE_ORIGINAL)
            )
            assertFalse("no puede llevar nada de la corrección", cuerpo.contains(NOMBRE_CORREGIDO))

            val fila = saleDataSource.getSaleById(SALE_ID)!!
            assertEquals(NOMBRE_ORIGINAL, fila.NOMBRE_CLIENTE)
            assertEquals("nada se commiteó mientras el cuerpo se armaba", 0, fila.REVISION)
            assertFalse(fila.CORRECCION_NO_ENVIADA)
        }

    // ═══════════════════════════════════════════════════════════════════════
    // 6b. El armado del cuerpo también dura, y también está cubierto
    //
    // Ronda de arreglo 1: la prueba de arriba adelanta el reloj ANTES de
    // correr el worker, así que dentro del resolver el arrendamiento está
    // fresco — demuestra "con candado vivo el editor no puede", no "el
    // candado sigue vivo ahí". Entre el reclamo y el POST corre
    // `resolveVendedoresForEmail`: DOS consultas a Firestore, 60 s de connect
    // y 60 s de read cada una. Estas dos pruebas cubren ese hueco por los dos
    // lados: el latido lo sostiene, y si aun así se pierde, la revalidación
    // previa al POST corta sin mandar nada.
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `el latido tambien cubre el armado del cuerpo, no solo el POST`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()

        var latidos = 0
        val armadoYaPasoElArrendamiento = CompletableDeferred<Unit>()
        var reclamoDuranteElArmado: ResultadoReclamo? = null
        var edadDelCandado = -1L
        var cuerpo = ""

        val resultado = correrWorker(
            reloj = reloj,
            renovar = { s, c, n ->
                latidos++
                val filas = db.localSaleDao().renewUploadClaim(s, c, n)
                if (latidos == 4) armadoYaPasoElArrendamiento.complete(Unit)
                filas
            },
            // El armado del cuerpo TARDA — la red de Firestore, no la de la
            // venta. Se espera a cuatro latidos: 4 × 60 s > los 180 s del
            // arrendamiento. Si el latido sólo envolviera el POST, aquí no
            // habría ni uno.
            resolver = {
                armadoYaPasoElArrendamiento.await()
                val fila = saleDataSource.getSaleById(SALE_ID)!!
                edadDelCandado = reloj.ahoraEpochMillis() - fila.CLAIMED_AT!!
                reclamoDuranteElArmado = reclamar(reloj)(SALE_ID)
                Pair(vendedores, 3)
            },
            api = api(crear = { _, datos, _ ->
                cuerpo = datos.comoTexto()
                ventaDTO
            })
        )

        assertTrue(
            "el armado duró más que el arrendamiento",
            testScheduler.currentTime > LocalSaleClaimLeases.UPLOAD_LEASE_MS
        )
        assertTrue(
            "el candado siguió renovándose mientras se armaba el cuerpo",
            edadDelCandado in 0 until LocalSaleClaimLeases.UPLOAD_LEASE_MS
        )
        assertEquals(
            "el editor no puede entrar a la mitad del armado",
            ResultadoReclamo.NoCorregible(EstadoCorreccion.SeEstaEnviando),
            reclamoDuranteElArmado
        )
        assertEquals(ListenableWorker.Result.success(), resultado)
        assertTrue(cuerpo.contains(NOMBRE_ORIGINAL))
    }

    @Test
    fun `si el candado se pierde armando el cuerpo, no sale NADA a la red`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()

        var llamadas = 0

        val resultado = correrWorker(
            reloj = reloj,
            // Sin latido: el proceso se congeló entero durante el armado, que
            // es el único caso en que el candado puede perderse ahí.
            latidoMs = 0L,
            resolver = {
                // El arrendamiento vence DURANTE el armado...
                reloj.desfase += LocalSaleClaimLeases.UPLOAD_LEASE_MS + 1
                // ...y el dueño gana la fila y commitea su corrección. A
                // partir de aquí los renglones en la base son los CORREGIDOS,
                // mientras la copia de la venta que el worker leyó al entrar
                // es la VIEJA: justo el cuerpo mezclado.
                val reclamo = reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada
                guardar(reloj)(
                    SALE_ID,
                    reclamo.claimId,
                    reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                    reclamo.venta.productos.map { it.copy(CANTIDAD = it.CANTIDAD + 1) },
                    reclamo.venta.combos,
                    EMAIL
                )
                Pair(vendedores, 3)
            },
            api = api(crear = { _, _, _ ->
                llamadas++
                ventaDTO
            })
        )

        assertEquals(
            "sin candado no se manda nada: se reintenta, la venta no se pierde",
            ListenableWorker.Result.retry(),
            resultado
        )
        assertEquals("CERO POST: un cuerpo mezclado nunca llega a Microsip", 0, llamadas)

        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertFalse("nada se mandó: la venta sigue pendiente", fila.ENVIADO)
        assertEquals("la corrección del dueño queda en pie", NOMBRE_CORREGIDO, fila.NOMBRE_CLIENTE)
        assertEquals(1, fila.REVISION)
        assertFalse(
            "no hay divergencia que marcar: el servidor no recibió nada",
            fila.CORRECCION_NO_ENVIADA
        )
        assertTrue(
            "y la venta vuelve a ser corregible de inmediato",
            reclamar(reloj)(SALE_ID) is ResultadoReclamo.Reclamada
        )
    }

    @Test
    fun `un latido que falla no tumba una subida que iba bien`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()

        var intentosDeLatido = 0
        val hubodosFallos = CompletableDeferred<Unit>()

        val resultado = correrWorker(
            reloj = reloj,
            // Room lanza en CADA latido (SQLite trabado justo cuando el editor
            // commitea). Si la excepción escapara de la corrutina hija, el
            // `coroutineScope` cancelaría el POST en vuelo y se perdería un
            // envío que iba bien.
            renovar = { _, _, _ ->
                intentosDeLatido++
                if (intentosDeLatido == 2) hubodosFallos.complete(Unit)
                throw IllegalStateException("database is locked")
            },
            api = api(crear = { _, _, _ ->
                hubodosFallos.await()
                ventaDTO
            })
        )

        assertEquals(
            "un fallo de renovación no puede costar el envío",
            ListenableWorker.Result.success(),
            resultado
        )
        assertTrue("y se siguió intentando latir", intentosDeLatido >= 2)
        assertTrue(saleDataSource.getSaleById(SALE_ID)!!.ENVIADO)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. El latido
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `una subida mas larga que el arrendamiento NO suelta la venta - el latido la sostiene`() =
        runTest {
            val reloj = RelojDePrueba(testScheduler)
            sembrarVenta()

            var latidos = 0
            var reclamoDurante: ResultadoReclamo? = null
            var edadDelCandado = -1L
            // Se completa cuando el latido ya renovó CUATRO veces — o sea,
            // cuando el POST lleva en vuelo MÁS que el arrendamiento entero
            // (4 × 60 s > 180 s). Es un rendezvous, no un reloj: la prueba
            // continúa cuando la renovación REAL terminó de escribirse, no
            // cuando un temporizador lo supone.
            val subidaYaPasoElArrendamiento = CompletableDeferred<Unit>()

            val resultado = correrWorker(
                reloj = reloj,
                renovar = { s, c, n ->
                    latidos++
                    val filas = db.localSaleDao().renewUploadClaim(s, c, n)
                    if (latidos == 4) subidaYaPasoElArrendamiento.complete(Unit)
                    filas
                },
                api = api(
                    crear = { _, _, _ ->
                        // La subida AVANZA pero tarda más que el arrendamiento:
                        // se espera a que el latido haya renovado cuatro veces.
                        // No es un reloj — es el latido REAL quien libera esto,
                        // con su escritura ya en la base. (Si alguien quita el
                        // latido, esto no se completa nunca y `runTest` falla
                        // por su propio tope: un rojo lento, pero inequívoco.)
                        subidaYaPasoElArrendamiento.await()
                        val fila = saleDataSource.getSaleById(SALE_ID)!!
                        edadDelCandado = reloj.ahoraEpochMillis() - fila.CLAIMED_AT!!
                        reclamoDurante = reclamar(reloj)(SALE_ID)
                        ventaDTO
                    }
                )
            )

            assertTrue(
                "el POST duró más que el arrendamiento",
                testScheduler.currentTime > LocalSaleClaimLeases.UPLOAD_LEASE_MS
            )
            // Al menos cuatro, no exactamente cuatro: mientras el POST siga en
            // vuelo el latido debe seguir latiendo, y cuántas veces alcance a
            // hacerlo depende de cuánto tarde la subida — que es justo lo que
            // no se debe fijar. Lo que sí se fija es que se DETIENE al
            // terminar, abajo.
            assertTrue("el latido siguió latiendo durante todo el POST", latidos >= 4)
            assertTrue(
                "el candado se renovó: su edad es menor que el arrendamiento",
                edadDelCandado in 0 until LocalSaleClaimLeases.UPLOAD_LEASE_MS
            )
            assertEquals(
                "la venta NO queda libre para el editor mientras la subida vive",
                ResultadoReclamo.NoCorregible(EstadoCorreccion.SeEstaEnviando),
                reclamoDurante
            )
            assertEquals(ListenableWorker.Result.success(), resultado)

            // Al terminar el POST el latido se detiene: ni una renovación más,
            // por mucho tiempo virtual que pase. (Y si quedara una corrutina
            // huérfana, `runTest` no cerraría.)
            val alTerminar = latidos
            testScheduler.advanceTimeBy(LocalSaleClaimLeases.UPLOAD_LEASE_MS * 10)
            testScheduler.runCurrent()
            assertEquals("sin corrutina huérfana latiendo", alTerminar, latidos)

            val fila = saleDataSource.getSaleById(SALE_ID)!!
            assertTrue(fila.ENVIADO)
            assertNull(fila.CLAIM_ID)
            assertFalse(fila.CORRECCION_NO_ENVIADA)
        }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. El arrendamiento vencido DE VERDAD (sin latido posible)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `si el arrendamiento vence sin latido, el 2xx tardio deja la divergencia VISIBLE`() =
        runTest {
            val reloj = RelojDePrueba(testScheduler)
            sembrarVenta()

            // `latidoMs = 0` apaga el latido: es el proceso que murió a media
            // subida, el único caso que el latido no puede cubrir.
            val resultado = correrWorker(
                reloj = reloj,
                latidoMs = 0L,
                api = api(
                    crear = { _, _, _ ->
                        reloj.desfase += LocalSaleClaimLeases.UPLOAD_LEASE_MS + 1
                        val reclamo = reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada
                        guardar(reloj)(
                            SALE_ID,
                            reclamo.claimId,
                            reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                            reclamo.venta.productos,
                            reclamo.venta.combos,
                            EMAIL
                        )
                        // Y AHORA vuelve el 2xx, con el cuerpo viejo.
                        ventaDTO
                    }
                )
            )

            assertEquals(ListenableWorker.Result.success(), resultado)

            val fila = saleDataSource.getSaleById(SALE_ID)!!
            assertTrue("el 2xx probó que el servidor tiene la venta", fila.ENVIADO)
            assertTrue(
                "la corrección no llegó al servidor: la divergencia queda MARCADA, nunca pisada en silencio",
                fila.CORRECCION_NO_ENVIADA
            )
            assertEquals("lo corregido sigue en el teléfono", NOMBRE_CORREGIDO, fila.NOMBRE_CLIENTE)
            assertEquals(1, fila.REVISION)
            assertNull(fila.CLAIM_ID)
            assertEquals(
                "y la UI lo dice: esto lo revisa la oficina",
                ResultadoReclamo.NoCorregible(EstadoCorreccion.LaRevisaLaOficina),
                reclamar(reloj)(SALE_ID)
            )
        }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. El barrido respeta el candado
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `el barrido no reencola una venta con candado vivo, y si lo hace cuando vence`() = runTest {
        val reloj = RelojDePrueba(testScheduler)
        sembrarVenta()
        reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada

        val encolador = EncoladorGrabador()
        val barrido = LocalSalesPendingSynchronizer(
            fetchPending = { PendingWorkSyncFactory.ventasParaElBarrido(saleDataSource, reloj) },
            enqueuer = encolador
        )
        val ctx = SyncContext(userId = "u1", userEmail = EMAIL)

        assertEquals(
            "con el dueño corrigiendo, el barrido no la toca",
            SyncResult.NothingPending,
            barrido.sync(ctx)
        )
        assertTrue(encolador.ids.isEmpty())

        reloj.desfase += LocalSaleClaimLeases.EDIT_LEASE_MS

        assertEquals(
            "vencido el arrendamiento, la venta vuelve al barrido: nunca se retiene para siempre",
            SyncResult.Enqueued(itemCount = 1, workRequestCount = 1),
            barrido.sync(ctx)
        )
        assertEquals(listOf(SALE_ID), encolador.ids)
    }

    /**
     * Ronda de arreglo 1: la prueba de arriba llama a `ventasParaElBarrido`
     * directo, así que un mutante que devolviera la LAMBDA de `createUseCase`
     * a `getPendingSales()` sobrevivía — el cableado real no estaba cubierto
     * por nadie. Ésta arma el caso de uso ENTERO por la fábrica de
     * producción y mira el resultado del sincronizador de ventas locales.
     *
     * `runBlocking` y no `runTest`: `SyncAllPendingWorkUseCase` se protege con
     * un `withTimeoutOrNull(60 s)` y los sincronizadores saltan a
     * `Dispatchers.IO`; bajo tiempo virtual el planificador adelantaría el
     * reloj hasta ese tope mientras la E/S real sigue corriendo y el caso de
     * uso devolvería un mapa vacío por un timeout que no ocurrió. Aquí no se
     * espera nada: el reloj es un valor fijo que la prueba mueve a mano.
     */
    @Test
    fun `la fabrica real cablea el barrido a la consulta que respeta el candado`() =
        kotlinx.coroutines.runBlocking {
            val reloj = RelojFijo(BASE_EPOCH)
            sembrarVenta()
            reclamar(reloj)(SALE_ID) as ResultadoReclamo.Reclamada

            val casoDeUso = PendingWorkSyncFactory.createUseCase(
                context = context,
                gate = InMemorySessionSyncGate(),
                clock = reloj
            )

            val resultados = casoDeUso.execute(SyncContext(userId = "u1", userEmail = EMAIL))

            assertEquals(
                "el barrido de producción no puede llevarse la venta que el dueño corrige",
                SyncResult.NothingPending,
                resultados[LocalSalesPendingSynchronizer.NAME]
            )
        }

    /** Reloj de valor fijo, para las pruebas que no corren en tiempo virtual. */
    private class RelojFijo(var ahora: Long) : RelojPort, AppClock {
        override fun ahoraEpochMillis(): Long = ahora
        override fun now(): Instant = Instant.ofEpochMilli(ahora)
    }

    private class EncoladorGrabador : LocalSalesWorkEnqueuer {
        val ids = mutableListOf<String>()
        override fun enqueue(localSaleId: String, userEmail: String) {
            ids += localSaleId
        }
    }
}
