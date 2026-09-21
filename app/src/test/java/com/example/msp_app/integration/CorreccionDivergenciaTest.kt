package com.example.msp_app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentaDTO
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
 * Y un tercer caso, el falso positivo DELIBERADO: se ancla antes de mandar,
 * así que un POST que nunca salió del teléfono también deja ancla. Está
 * probado aquí con nombre propio para que nadie lo descubra en campo y crea
 * que es un defecto.
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
    // G. El falso positivo deliberado
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * El precio de ser conservador, probado a propósito en vez de descubierto
     * en campo.
     *
     * El primer POST falla con `IOException` —en esta prueba el fake ni
     * siquiera devuelve nada, y en el teléfono suele ser "no hay señal", con
     * el servidor sin enterarse de nada—, el dueño corrige, y el segundo POST
     * sube el cuerpo CORREGIDO. El servidor termina con lo correcto, y aun
     * así la fila queda marcada.
     *
     * Es a propósito: el teléfono NO puede distinguir "el POST no salió" de
     * "el POST llegó y se perdió la respuesta" — son el mismo `IOException`.
     * Marcar de más manda a la oficina a revisar una venta que estaba bien;
     * marcar de menos despacha una venta que el cliente no pidió. No son
     * comparables. Si algún día se quiere afinar, lo que hay que cambiar es
     * de qué lado del `crearVenta` se escribe el ancla — y entonces esta
     * prueba es la que avisa.
     */
    @Test
    fun `corregir tras un POST que nunca llego tambien queda marcada, y es deliberado`() = runTest {
        sembrarVenta()

        val corrida1 = correrWorker(api { _, _, _ -> throw IOException("sin señal") })
        assertEquals(ListenableWorker.Result.retry(), corrida1)
        assertEquals(
            "el ancla queda puesta aunque el POST no haya llegado a ningún lado",
            0,
            saleDataSource.getSaleById(SALE_ID)!!.REVISION_POSTEADA
        )

        corregirNombre(NOMBRE_CORREGIDO)

        val corrida2 = correrWorker(api { _, _, _ -> ventaDTO })

        assertEquals(ListenableWorker.Result.success(), corrida2)
        val fila = saleDataSource.getSaleById(SALE_ID)!!
        assertTrue(fila.ENVIADO)
        assertTrue(
            "falso positivo aceptado: el teléfono no puede saber si aquel primer POST llegó",
            fila.CORRECCION_NO_ENVIADA
        )
    }
}
