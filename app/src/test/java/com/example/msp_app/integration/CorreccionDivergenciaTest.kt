package com.example.msp_app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.ventas.ActualizarClienteRequest
import com.example.msp_app.data.api.services.ventas.ActualizarHeaderRequest
import com.example.msp_app.data.api.services.ventas.ReemplazarLineasRequest
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentaDTO
import com.example.msp_app.data.api.services.ventas.VentaSituacionDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.feature.ventacorreccion.data.AppClockRelojPort
import com.example.msp_app.feature.ventacorreccion.data.RoomVentaLocalCorreccionAdapter
import com.example.msp_app.feature.ventacorreccion.domain.port.ReencolarSubidaPort
import com.example.msp_app.feature.ventacorreccion.domain.usecase.GuardarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ReclamarCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.usecase.ResultadoReclamo
import com.example.msp_app.features.sales.upload.data.RoomUploadFailureRepository
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.example.msp_app.workers.PendingLocalSalesWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-divergencia-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"
private const val NOMBRE_ORIGINAL = "Teodoro Aviles Campos"
private const val NOMBRE_CORREGIDO = "Teodoro Aviles Campos de la Cruz"

/**
 * Qué significa `CORRECCION_NO_ENVIADA` — Task 6b del plan "Corregir una
 * venta antes de que suba".
 *
 * El escenario que la marca DEBE atrapar (el "2xx perdido" reconciliado por
 * `GET`) vive en `CorreccionIdempotenciaTest`, escenario D. Este archivo
 * cubre el otro lado, sin el cual la marca no significaría nada: cuándo NO se
 * pone. Una marca que se pusiera siempre mandaría a la oficina a revisar
 * todas las ventas y la gente dejaría de mirarla — que es la forma más cara
 * de perder una señal.
 *
 * Y la frontera que fija la ronda de arreglo 1: el ancla sólo vale cuando de
 * verdad PUDIERON salir bytes. Si el fallo demuestra que nunca hubo conexión
 * —una prueba por excepción de la lista—, el ancla se borra y la corrección
 * siguiente no se marca: ése es el caso estelar del plan (capturar sin señal,
 * corregir, subir al volver la red) y marcarlo era el defecto. Si el fallo es
 * AMBIGUO (ocurre con el cuerpo ya escrito), el ancla se conserva y sí se
 * marca.
 */
class CorreccionDivergenciaTest : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource
    private lateinit var fakeImageFile: File

    private val clock = FakeClock.at("2026-09-21T12:00:00Z")

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
        fecha_venta = "2026-09-21T12:00:00Z",
        created_at = "2026-09-21T12:00:01Z",
        updated_at = "2026-09-21T12:00:01Z"
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

    private val reloj = AppClockRelojPort(clock)
    private fun reclamar() = ReclamarCorreccion(puerto(), reloj, ReencolarNoOp)
    private fun guardar() = GuardarCorreccion(puerto(), reloj, ReencolarNoOp)

    /** Corrige el nombre del cliente con el editor REAL (no escribe Room a mano). */
    private suspend fun corregirNombre(nombre: String) {
        val reclamo = reclamar()(SALE_ID)
        check(reclamo is ResultadoReclamo.Reclamada)
        guardar()(
            SALE_ID,
            reclamo.claimId,
            reclamo.venta.campos.copy(nombreCliente = nombre),
            reclamo.venta.productos,
            reclamo.venta.combos,
            EMAIL
        )
    }

    // ─── la venta ─────────────────────────────────────────────────────────

    private suspend fun sembrarVenta() {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = SALE_ID, clientName = NOMBRE_ORIGINAL)
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = SALE_ID, articuloId = 100)
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-001",
                saleId = SALE_ID,
                imageUri = fakeImageFile.absolutePath
            )
        )
    }

    // ─── el worker real ───────────────────────────────────────────────────

    private suspend fun correrWorker(api: VentasApi): ListenableWorker.Result {
        val inputData = androidx.work.Data.Builder()
            .putString("local_sale_id", SALE_ID)
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
                    resolveVendedoresForEmail = { Pair(vendedores, 3) },
                    ventasApi = api,
                    uploadFailureRepository = RoomUploadFailureRepository(db.localSaleDao()),
                    nowEpochMillis = { clock.now().toEpochMilli() },
                    // Sin latido: aquí se mide la marca, no la carrera — ver
                    // `CorreccionCarreraTest` para el latido.
                    latidoDeSubidaMs = 0L
                )
            })
            .build()

        return (worker as PendingLocalSalesWorker).doWork()
    }

    private fun api(
        crear: suspend (String, RequestBody, List<MultipartBody.Part>) -> VentaDTO
    ): VentasApi = object : VentasApi {
        override suspend fun crearVenta(
            idempotencyKey: String,
            datos: RequestBody,
            imagen: List<MultipartBody.Part>
        ): VentaDTO = crear(idempotencyKey, datos, imagen)

        override suspend fun obtenerVenta(id: String): VentaDTO =
            throw AssertionError("obtenerVenta no debería llamarse en esta prueba")

        // El nivel 2 (corregir una venta YA subida) no entra en esta prueba: su camino es el
        // worker de correcciones remotas, no el subidor. Lanzar en vez de devolver algo vacío
        // hace que, si alguien lo cablea aquí sin querer, la prueba lo diga en vez de pasar.
        override suspend fun reemplazarLineas(
            id: String,
            body: ReemplazarLineasRequest
        ): VentaSituacionDTO =
            throw AssertionError("reemplazarLineas no debería llamarse en esta prueba")

        override suspend fun actualizarHeader(
            id: String,
            body: ActualizarHeaderRequest
        ): VentaSituacionDTO =
            throw AssertionError("actualizarHeader no debería llamarse en esta prueba")

        override suspend fun actualizarCliente(
            id: String,
            body: ActualizarClienteRequest
        ): VentaSituacionDTO =
            throw AssertionError("actualizarCliente no debería llamarse en esta prueba")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // E. El camino normal: nadie corrigió nada
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sube a la primera, sin que nadie toque la venta. El servidor tiene
     * exactamente lo que el teléfono enseña. Si esto se marcara, la marca no
     * significaría nada.
     */
    @Test
    fun `una venta que sube a la primera y sin correcciones no queda marcada`() = runTest {
        sembrarVenta()

        val resultado = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), resultado)
        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertTrue(fila.ENVIADO)
        assertEquals("el cuerpo que viajó fue el de la REVISION 0", 0, fila.REVISION_POSTEADA)
        assertEquals(0, fila.REVISION)
        assertFalse(
            "nadie corrigió nada: no hay divergencia que señalar",
            fila.CORRECCION_NO_ENVIADA
        )
        assertEquals(NOMBRE_ORIGINAL, fila.NOMBRE_CLIENTE)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // F. La corrección ANTES del primer POST: esa sí viaja
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * El dueño corrige la venta ANTES de que el subidor haya emitido nada
     * (capturó sin señal y se dio cuenta del error en el acto). Cuando el
     * POST sale, lleva la corrección adentro — el ancla nace en `REVISION=1`
     * y coincide con la fila. Sin marca: corregir NO es, por sí solo, motivo
     * de revisión de oficina.
     */
    @Test
    fun `una correccion hecha antes del primer POST viaja y no queda marcada`() = runTest {
        sembrarVenta()

        corregirNombre(NOMBRE_CORREGIDO)
        assertEquals(1, saleDataSource.getSaleById(SALE_ID)!!.REVISION)
        assertNull(
            "todavía no ha salido ningún POST: no hay ancla",
            saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA
        )

        val resultado = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), resultado)
        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertTrue(fila.ENVIADO)
        assertEquals("el ancla nace con la corrección adentro", 1, fila.REVISION_POSTEADA)
        assertFalse(
            "la corrección SÍ viajó en el primer POST: nada que revisar",
            fila.CORRECCION_NO_ENVIADA
        )
        assertEquals(NOMBRE_CORREGIDO, fila.NOMBRE_CLIENTE)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // G. El caso estelar del plan: nunca hubo conexión → NO se marca
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Ronda de arreglo 1. El escenario del TÍTULO del plan: el vendedor
     * captura sin señal, el intento falla porque no hay red, corrige, vuelve
     * la señal y la venta sube corregida. **Todo salió bien: no se marca
     * nada.**
     *
     * Antes de esta ronda sí se marcaba, y ése era el problema real — un
     * aviso que aparece en casi toda corrección es un aviso que la oficina
     * aprende a ignorar, y entonces ya no protege del caso que importa.
     *
     * Una prueba por excepción de la lista de "no salió un byte", porque cada
     * una entra al `when` por su propia rama: si alguien borra un `is`, la
     * prueba de ESA excepción se pone roja y las otras dos siguen verdes.
     */
    @Test
    fun `sin DNS el ancla se borra y la correccion siguiente no queda marcada`() = runTest {
        sinRedNoMarca { UnknownHostException("api.muebleriamsp.invalid") }
    }

    @Test
    fun `con la conexion rechazada el ancla se borra y la correccion no se marca`() = runTest {
        sinRedNoMarca { ConnectException("Network is unreachable") }
    }

    @Test
    fun `sin ruta al host el ancla se borra y la correccion no queda marcada`() = runTest {
        sinRedNoMarca { NoRouteToHostException("No route to host") }
    }

    /**
     * Corrida 1 falla con un fallo que PRUEBA que no hubo conexión → el ancla
     * se borra; corrección; corrida 2 sube el cuerpo corregido → sin marca.
     */
    private suspend fun sinRedNoMarca(fallo: () -> IOException) {
        sembrarVenta()

        val corrida1 = correrWorker(api { _, _, _ -> throw fallo() })

        assertEquals(ListenableWorker.Result.retry(), corrida1)
        assertNull(
            "el fallo probó que no salió un byte: el ancla se borra",
            saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA
        )

        corregirNombre(NOMBRE_CORREGIDO)

        val corrida2 = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), corrida2)
        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertTrue(fila.ENVIADO)
        assertEquals(
            "el ancla la puso la corrida 2, ya con la corrección adentro",
            1,
            fila.REVISION_POSTEADA
        )
        assertFalse(
            "la corrección viajó y el servidor tiene lo correcto: nada que revisar",
            fila.CORRECCION_NO_ENVIADA
        )
        assertEquals(NOMBRE_CORREGIDO, fila.NOMBRE_CLIENTE)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // H. El fallo AMBIGUO sí se marca (el conservadurismo que se conserva)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * El otro lado de la lista, y la razón de que sea una lista y no un
     * `catch (IOException)`: un fallo posterior a la escritura del cuerpo.
     * `SocketTimeoutException` es el ejemplo exacto — OkHttp lo usa igual
     * para un timeout de lectura, que ocurre con los bytes YA enviados y el
     * servidor posiblemente con la venta guardada.
     *
     * Aquí el ancla **se conserva** y la corrección posterior **sí** queda
     * marcada. Puede ser un falso positivo; el falso negativo sería despachar
     * una venta que el cliente no pidió. La asimetría no cambió.
     */
    @Test
    fun `un fallo posterior a mandar el cuerpo conserva el ancla y si marca`() = runTest {
        sembrarVenta()

        val corrida1 = correrWorker(
            api { _, _, _ -> throw SocketTimeoutException("timeout esperando la respuesta") }
        )
        assertEquals(ListenableWorker.Result.retry(), corrida1)
        assertEquals(
            "fallo ambiguo: el ancla del cuerpo que quizá viajó se queda",
            0,
            saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA
        )

        corregirNombre(NOMBRE_CORREGIDO)

        val corrida2 = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), corrida2)
        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertTrue(fila.ENVIADO)
        assertTrue(
            "no se puede probar que aquel cuerpo no llegó: la duda se marca",
            fila.CORRECCION_NO_ENVIADA
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // I. Un intento SIN red no puede borrar el ancla de otro que SÍ mandó
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * El falso negativo que la guarda "sólo borro la mía" existe para
     * impedir, de punta a punta y con tres corridas:
     *
     * 1. La corrida 1 manda el cuerpo y muere con un fallo AMBIGUO — el
     *    servidor pudo quedarse la venta original. Ancla = 0.
     * 2. El dueño corrige. `REVISION` pasa a 1.
     * 3. La corrida 2 ni siquiera alcanza la red (`UnknownHostException`).
     *    **No debe borrar el ancla**: no es suya, la puso la corrida 1.
     * 4. La corrida 3 sube y recibe 2xx — y la fila **queda marcada**, porque
     *    lo que el servidor pueda tener de la corrida 1 sigue siendo el
     *    cuerpo viejo.
     *
     * Si alguien quita la condición "sólo si anclé yo en esta corrida" (o la
     * guarda por valor del `UPDATE`), el paso 3 borra la evidencia, el paso 4
     * no marca nada, y la venta original se despacha sin que nadie la revise.
     */
    @Test
    fun `una corrida sin red no borra el ancla que dejo otra que si mando bytes`() = runTest {
        sembrarVenta()

        val corrida1 = correrWorker(
            api { _, _, _ -> throw SocketTimeoutException("se perdió la respuesta") }
        )
        assertEquals(ListenableWorker.Result.retry(), corrida1)
        assertEquals(0, saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA)

        corregirNombre(NOMBRE_CORREGIDO)

        val corrida2 = correrWorker(api { _, _, _ -> throw UnknownHostException("sin DNS") })
        assertEquals(ListenableWorker.Result.retry(), corrida2)
        assertEquals(
            "la corrida 2 no ancló nada, así que no tiene ancla que borrar",
            0,
            saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA
        )

        val corrida3 = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), corrida3)
        assertTrue(
            "el cuerpo de la corrida 1 pudo quedarse en el servidor: la marca no se puede perder",
            saleDataSource.getSaleById(SALE_ID)!!.CORRECCION_NO_ENVIADA
        )
    }

    /**
     * La variante que deja SOLA a la guarda de Kotlin ("sólo borro si anclé
     * yo en ESTA corrida"), y por eso vale la pena aparte de la anterior.
     *
     * Aquí **no hay corrección entre las corridas 1 y 2**, así que la corrida
     * 2 reclama con la misma `REVISION` (0) con la que la corrida 1 ancló. La
     * guarda por VALOR del `UPDATE` (`REVISION_POSTEADA = :revision`) no ve
     * ninguna diferencia — coincide — y no frena nada: lo único que impide
     * que la corrida 2 borre un ancla ajena es saber que ella no la puso.
     *
     * Sin esa condición: la corrida 2 (sin red) borra el ancla de la corrida
     * 1 (que sí mandó bytes), el dueño corrige, la corrida 3 sube y la fila
     * queda LIMPIA — con el servidor posiblemente guardando la venta
     * original. Es el falso negativo más caro del mecanismo.
     */
    @Test
    fun `sin correccion de por medio, la corrida sin red tampoco borra el ancla ajena`() = runTest {
        sembrarVenta()

        val corrida1 = correrWorker(
            api { _, _, _ -> throw SocketTimeoutException("se perdió la respuesta") }
        )
        assertEquals(ListenableWorker.Result.retry(), corrida1)
        assertEquals(0, saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA)

        // Sin corregir: la corrida 2 reclama con la MISMA REVISION que ancló
        // la corrida 1, así que la guarda por valor del UPDATE coincidiría.
        val corrida2 = correrWorker(api { _, _, _ -> throw UnknownHostException("sin DNS") })
        assertEquals(ListenableWorker.Result.retry(), corrida2)
        assertEquals(
            "el ancla es de la corrida 1: la 2 no puede borrarla aunque el valor coincida",
            0,
            saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA
        )

        corregirNombre(NOMBRE_CORREGIDO)

        val corrida3 = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), corrida3)
        assertTrue(
            "la corrección nunca viajó en el cuerpo que quizá tiene el servidor: se marca",
            saleDataSource.getSaleById(SALE_ID)!!.CORRECCION_NO_ENVIADA
        )
    }
}
