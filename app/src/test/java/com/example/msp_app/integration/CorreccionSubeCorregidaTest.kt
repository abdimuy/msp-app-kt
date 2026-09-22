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
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-sube-corregida-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"

private const val NOMBRE_ORIGINAL = "Rosa Isela Martinez Duran"
private const val NOMBRE_CORREGIDO = "Rosa Isela Martinez Duran de Cano"
private const val TELEFONO_ORIGINAL = "5512345678"
private const val TELEFONO_CORREGIDO = "5587654321"
private const val DIRECCION_ORIGINAL = "Calle Principal 123"
private const val DIRECCION_CORREGIDA = "Avenida Reforma 456"

/**
 * Task 6 del plan "Corregir una venta antes de que suba": la prueba de que lo que SALE AL CABLE
 * lleva los valores corregidos, y de que el `SERVER_UUID` de una línea que sobrevive a la
 * corrección no se reacuña entre un intento y el siguiente. No duplica nada de `CorreccionCarreraTest`
 * (Task 4): ahí se prueba la CARRERA; aquí se mide el CONTENIDO del cuerpo real — `RequestBody`
 * materializado con `writeTo(okio.Buffer())` y parseado como JSON, nunca el objeto de dominio
 * (regla del dueño).
 */
class CorreccionSubeCorregidaTest : RoomTestBase() {

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

    // ─── la venta ─────────────────────────────────────────────────────────

    /** Dos productos (uno sobrevive, uno se quita) y un combo, todos SIN `SERVER_UUID` aún. */
    private suspend fun sembrarVenta(saleId: String = SALE_ID) {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(
                saleId = saleId,
                clientName = NOMBRE_ORIGINAL,
                telefono = TELEFONO_ORIGINAL,
                direccion = DIRECCION_ORIGINAL,
                parcialidad = 500.0,
                precioTotal = 5000.0
            )
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = saleId,
                articuloId = 100,
                articulo = "Colchon King",
                cantidad = 1
            )
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = saleId,
                articuloId = 200,
                articulo = "Buro",
                cantidad = 1
            )
        )
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "combo-1",
                saleId = saleId,
                precioContado = 4000.0
            )
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

    private suspend fun correrWorker(
        api: VentasApi,
        saleId: String = SALE_ID
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
                    resolveVendedoresForEmail = { Pair(vendedores, 3) },
                    ventasApi = api,
                    uploadFailureRepository = RoomUploadFailureRepository(db.localSaleDao()),
                    nowEpochMillis = { clock.now().toEpochMilli() },
                    // Sin latido: estas pruebas miden CONTENIDO del cuerpo, no la carrera —
                    // ver `CorreccionCarreraTest` para el latido.
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

    private fun RequestBody.comoTexto(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // A. Que suba corregida
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `el cuerpo real lleva los valores corregidos y el articulo quitado no aparece`() = runTest {
        sembrarVenta()

        val reclamo = reclamar()(SALE_ID)
        check(reclamo is ResultadoReclamo.Reclamada)

        val productoQueSobrevive = reclamo.venta.productos.single { it.ARTICULO_ID == 100 }
        val comboQueSeToca = reclamo.venta.combos.single { it.COMBO_ID == "combo-1" }

        guardar()(
            SALE_ID,
            reclamo.claimId,
            reclamo.venta.campos.copy(
                nombreCliente = NOMBRE_CORREGIDO,
                telefono = TELEFONO_CORREGIDO,
                direccion = DIRECCION_CORREGIDA,
                parcialidad = 750.0,
                precioTotal = 6000.0
            ),
            productos = listOf(
                // Sobrevive, con cantidad corregida.
                productoQueSobrevive.copy(CANTIDAD = 3),
                // Nuevo: entra sin SERVER_UUID.
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = SALE_ID,
                    articuloId = 300,
                    articulo = "Comedor",
                    cantidad = 2
                )
                // El ARTICULO_ID 200 NO va: se quita.
            ),
            combos = listOf(comboQueSeToca.copy(PRECIO_CONTADO = 4500.0)),
            userEmail = EMAIL
        )

        var llamadas = 0
        var cuerpo = ""
        val resultado = correrWorker(
            api = api(crear = { _, datos, _ ->
                llamadas++
                cuerpo = datos.comoTexto()
                ventaDTO
            })
        )

        assertEquals(ListenableWorker.Result.success(), resultado)
        assertEquals("sube UNA sola vez", 1, llamadas)

        val body = JsonParser.parseString(cuerpo).asJsonObject

        assertEquals(NOMBRE_CORREGIDO, body["cliente"].asJsonObject["nombre"].asString)
        assertEquals(
            "+525587654321",
            body["cliente"].asJsonObject["telefono"].asString
        )
        assertEquals(DIRECCION_CORREGIDA, body["direccion"].asJsonObject["calle"].asString)
        assertEquals("6000.00", body["montos"].asJsonObject["anual"].asString)
        assertEquals("750.00", body["plan_credito"].asJsonObject["parcialidad"].asString)

        val productos = body["productos"].asJsonArray
        val articuloIds = productos.map { it.asJsonObject["articulo_id"].asInt }.toSet()
        assertEquals(setOf(100, 300), articuloIds)
        assertFalse("el articulo quitado no puede aparecer en el cuerpo", 200 in articuloIds)

        val p100 = productos.single { it.asJsonObject["articulo_id"].asInt == 100 }.asJsonObject
        assertEquals("3", p100["cantidad"].asString)

        val combos = body["combos"].asJsonArray
        assertEquals(1, combos.size())
        assertEquals("4500.00", combos[0].asJsonObject["precio_contado"].asString)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // B. La identidad de las líneas se conserva
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `el SERVER_UUID de una linea que sobrevive es el mismo en el cuerpo de la corrida 1 y de la 2`() =
        runTest {
            sembrarVenta()

            var llamadas = 0
            var cuerpo1 = ""

            // Corrida 1: el worker acuña SERVER_UUID para las líneas ANTES de armar el cuerpo, y
            // los persiste — luego la señal se pierde.
            val corrida1 = correrWorker(
                api = api(crear = { _, datos, _ ->
                    llamadas++
                    cuerpo1 = datos.comoTexto()
                    throw IOException("se cayo la señal")
                })
            )
            assertEquals(ListenableWorker.Result.retry(), corrida1)

            val body1 = JsonParser.parseString(cuerpo1).asJsonObject
            val idP100Corrida1 =
                body1["productos"].asJsonArray
                    .single { it.asJsonObject["articulo_id"].asInt == 100 }
                    .asJsonObject["id"].asString
            val idCombo1Corrida1 = body1["combos"].asJsonArray[0].asJsonObject["id"].asString

            // El SERVER_UUID quedó persistido en Room, no sólo en el cuerpo que se perdió.
            val productoTrasCorrida1 =
                productDataSource.getProductsForSale(SALE_ID).single { it.ARTICULO_ID == 100 }
            assertEquals(idP100Corrida1, productoTrasCorrida1.SERVER_UUID)

            // Se corrige: la línea 100 sobrevive con OTRA cantidad; el 200 se queda igual; el
            // combo se toca.
            val reclamo = reclamar()(SALE_ID)
            check(reclamo is ResultadoReclamo.Reclamada)
            val productoQueSobrevive = reclamo.venta.productos.single { it.ARTICULO_ID == 100 }
            val productoSinTocar = reclamo.venta.productos.single { it.ARTICULO_ID == 200 }
            val comboQueSeToca = reclamo.venta.combos.single { it.COMBO_ID == "combo-1" }

            // El editor NUNCA carga ni conoce el SERVER_UUID (KDoc de
            // `mergeProductsForSale`) — lo que reconstruye desde el formulario llega con
            // `SERVER_UUID = null` sin importar lo que la base ya tenía. Si esta prueba dejara
            // pasar el UUID leído de vuelta tal cual, la identidad se conservaría por
            // COINCIDENCIA (el dato ya traía el UUID correcto), no porque el merge lo defienda —
            // exactamente el falso positivo que "control positivo" prohíbe.
            guardar()(
                SALE_ID,
                reclamo.claimId,
                reclamo.venta.campos.copy(nombreCliente = NOMBRE_CORREGIDO),
                productos = listOf(
                    productoQueSobrevive.copy(CANTIDAD = 5, SERVER_UUID = null),
                    productoSinTocar.copy(SERVER_UUID = null)
                ),
                combos = listOf(comboQueSeToca.copy(PRECIO_CONTADO = 4750.0, SERVER_UUID = null)),
                userEmail = EMAIL
            )

            var cuerpo2 = ""
            val corrida2 = correrWorker(
                api = api(crear = { _, datos, _ ->
                    llamadas++
                    cuerpo2 = datos.comoTexto()
                    ventaDTO
                })
            )
            assertEquals(ListenableWorker.Result.success(), corrida2)

            val body2 = JsonParser.parseString(cuerpo2).asJsonObject
            val p100EnBody2 =
                body2["productos"].asJsonArray
                    .single { it.asJsonObject["articulo_id"].asInt == 100 }
                    .asJsonObject
            val idCombo1Corrida2 = body2["combos"].asJsonArray[0].asJsonObject["id"].asString

            assertEquals(
                "el SERVER_UUID de la linea que sobrevive NO se reacuña",
                idP100Corrida1,
                p100EnBody2["id"].asString
            )
            assertEquals(
                "el SERVER_UUID del combo que sobrevive NO se reacuña",
                idCombo1Corrida1,
                idCombo1Corrida2
            )
            assertEquals(
                "la corrección SI se refleja: la cantidad cambio",
                "5",
                p100EnBody2["cantidad"].asString
            )
            assertEquals("crearVenta se llamó exactamente dos veces", 2, llamadas)
        }
}
