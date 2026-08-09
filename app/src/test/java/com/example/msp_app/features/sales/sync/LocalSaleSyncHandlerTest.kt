package com.example.msp_app.features.sales.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.sync.SyncContext
import com.example.msp_app.core.sync.SyncOperation
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.api.services.localSales.LocalSaleResponse
import com.example.msp_app.data.api.services.localSales.LocalSaleUpdateResponse
import com.example.msp_app.data.api.services.localSales.LocalSalesApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Char-tests money-path de [LocalSaleSyncHandler.prepareRequest].
 *
 * El backend exige `productos` con al menos un elemento (contrato v2
 * `CrearVentaBody.Productos minItems:1` + invariante de dominio
 * `ErrVentaProductosVacios` en msp-api). ANTES, los datasources tragaban las
 * excepciones del DAO y devolvían lista vacía, así que un fallo de lectura
 * armaba un request con productos vacíos y lo subía. AHORA:
 *  - un fallo del DAO se PROPAGA (BaseSyncWorker lo reintenta),
 *  - una venta genuinamente sin productos lanza [EmptySaleProductsException],
 *  - los combos vacíos siguen siendo un estado legítimo.
 *
 * Room in-memory de [RoomTestBase]; la API se reemplaza por un fake que falla
 * ruidosamente si `prepareRequest` intentara subir algo.
 */
class LocalSaleSyncHandlerTest : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource

    private val syncContext = SyncContext(
        entityId = "sale-1",
        operationType = "CREATE",
        additionalData = mapOf("user_email" to "vendedor@muebleriamsp.mx")
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        saleDataSource = LocalSaleDataSource(context)
        productDataSource = SaleProductLocalDataSource(context)
        comboDataSource = ComboLocalDataSource(context)
    }

    /** API fake que jamás debe invocarse desde `prepareRequest`. */
    private fun failFastApi(): LocalSalesApi = object : LocalSalesApi {
        override suspend fun saveLocalSale(
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): LocalSaleResponse = throw AssertionError("saveLocalSale must not be called")

        override suspend fun updateLocalSale(
            localSaleId: String,
            datos: RequestBody,
            imagenes: List<MultipartBody.Part>
        ): LocalSaleUpdateResponse = throw AssertionError("updateLocalSale must not be called")
    }

    private fun handlerWith(
        products: SaleProductLocalDataSource = productDataSource,
        combos: ComboLocalDataSource = comboDataSource
    ) = LocalSaleSyncHandler(
        localSaleDataSource = saleDataSource,
        productDataSource = products,
        comboDataSource = combos,
        api = failFastApi()
    )

    private fun createOp(saleId: String) =
        SyncOperation.Create(entityId = saleId, entityType = "LOCAL_SALE")

    // ─── guard: venta sin productos ────────────────────────────────────────────

    @Test
    fun prepareRequest_throwsEmptySaleProductsException_whenNoProducts() = runTest {
        val saleId = "sale-empty"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        val entity = saleDataSource.getSaleById(saleId)!!

        val thrown = try {
            handlerWith().prepareRequest(context, entity, createOp(saleId), syncContext)
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(
            "sin productos debe lanzar EmptySaleProductsException, no armar un request vacío",
            thrown is EmptySaleProductsException
        )
    }

    @Test
    fun prepareRequest_throwsEmptySaleProducts_forCombosOnlySale() = runTest {
        val saleId = "sale-combos-only"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = saleId)
        )
        val entity = saleDataSource.getSaleById(saleId)!!

        val thrown = try {
            handlerWith().prepareRequest(context, entity, createOp(saleId), syncContext)
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(
            "una venta solo-combos no es un vacío legítimo: el contrato exige >=1 producto",
            thrown is EmptySaleProductsException
        )
    }

    // ─── propagación de error del DAO ──────────────────────────────────────────

    @Test
    fun prepareRequest_propagatesDaoException_whenProductDaoThrows() = runTest {
        val saleId = "sale-dao-boom"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        val entity = saleDataSource.getSaleById(saleId)!!

        val boom = IllegalStateException("db corrupta")
        val throwingProducts = SaleProductLocalDataSource(throwingProductDao(boom))

        val thrown = try {
            handlerWith(products = throwingProducts)
                .prepareRequest(context, entity, createOp(saleId), syncContext)
            null
        } catch (e: Exception) {
            e
        }

        assertSame(
            "el fallo del DAO debe propagarse tal cual (BaseSyncWorker reintenta), no colapsar a vacío",
            boom,
            thrown
        )
    }

    // ─── vacío legítimo de combos: se respeta ──────────────────────────────────

    @Test
    fun prepareRequest_buildsRequest_withOneProductAndNoCombos() = runTest {
        val saleId = "sale-ok"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = saleId, articuloId = 100)
        )
        val entity = saleDataSource.getSaleById(saleId)!!

        val request = handlerWith().prepareRequest(context, entity, createOp(saleId), syncContext)

        assertTrue(
            "una venta con >=1 producto y sin combos debe armar el request normalmente",
            request is LocalSaleSyncData
        )
    }

    @Test
    fun prepareRequest_buildsRequest_withProductAndCombo() = runTest {
        val saleId = "sale-ok-combo"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = saleId, articuloId = 100)
        )
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = saleId)
        )
        val entity = saleDataSource.getSaleById(saleId)!!

        val request = handlerWith().prepareRequest(context, entity, createOp(saleId), syncContext)

        assertTrue(
            "una venta con producto + combo debe armar el request normalmente",
            request is LocalSaleSyncData
        )
    }

    private fun throwingProductDao(error: Throwable): LocalSaleProductDao =
        object : LocalSaleProductDao {
            override suspend fun insertSaleProduct(saleProduct: LocalSaleProductEntity) = Unit
            override suspend fun insertAllSaleProducts(saleProducts: List<LocalSaleProductEntity>) =
                Unit

            override suspend fun getProductsForSale(saleId: String): List<LocalSaleProductEntity> =
                throw error

            override suspend fun deleteProductsForSale(saleId: String) = Unit
            override suspend fun updateServerUuid(
                saleId: String,
                articuloId: Int,
                serverUuid: String
            ) = Unit
        }
}
