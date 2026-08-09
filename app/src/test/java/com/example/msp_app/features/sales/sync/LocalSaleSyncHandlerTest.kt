package com.example.msp_app.features.sales.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.sync.SyncContext
import com.example.msp_app.core.sync.SyncOperation
import com.example.msp_app.core.sync.SyncResult
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
 * Char-tests money-path de [LocalSaleSyncHandler].
 *
 * El backend exige `productos` con al menos un elemento (contrato v2
 * `CrearVentaBody.Productos minItems:1` + invariante de dominio
 * `ErrVentaProductosVacios` en msp-api). ANTES, los datasources tragaban las
 * excepciones del DAO y devolvían lista vacía, así que un fallo de lectura
 * armaba un request con productos vacíos y lo subía. AHORA:
 *  - un fallo REAL del DAO (transitorio) se PROPAGA como excepción y
 *    BaseSyncWorker lo reintenta (bounded por su cap),
 *  - una venta genuinamente sin productos resuelve a
 *    [SyncResult.PermanentError] → fallo PERMANENTE inmediato (sin reintento),
 *    consistente con el `Result.failure()` de `PendingLocalSalesWorker`,
 *  - los combos vacíos siguen siendo un estado legítimo.
 *
 * Room in-memory de [RoomTestBase]; la API se reemplaza por un fake que falla
 * ruidosamente si el sync intentara subir algo.
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

    /**
     * Ejecuta el flujo real del handler (prepareRequest → executeSync) tal como
     * lo hace [com.example.msp_app.core.sync.BaseSyncWorker], para observar el
     * [SyncResult] final (permanente vs reintentable).
     */
    private suspend fun sync(
        handler: LocalSaleSyncHandler,
        entity: LocalSaleEntity,
        op: SyncOperation
    ): SyncResult<Any> {
        val request = handler.prepareRequest(context, entity, op, syncContext)
        return handler.executeSync(context, entity, op, request)
    }

    // ─── guard: venta sin productos → fallo PERMANENTE (no reintento) ──────────

    @Test
    fun sync_emptyProducts_resolvesToPermanentError_notRetry() = runTest {
        val saleId = "sale-empty"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        val entity = saleDataSource.getSaleById(saleId)!!

        val result = sync(handlerWith(), entity, createOp(saleId))

        assertTrue(
            "sin productos debe resolver a PermanentError (fallo permanente, no reintento sin límite)",
            result is SyncResult.PermanentError
        )
    }

    @Test
    fun sync_combosOnlySale_resolvesToPermanentError() = runTest {
        val saleId = "sale-combos-only"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = saleId)
        )
        val entity = saleDataSource.getSaleById(saleId)!!

        val result = sync(handlerWith(), entity, createOp(saleId))

        assertTrue(
            "una venta solo-combos no es un vacío legítimo: el contrato exige >=1 producto",
            result is SyncResult.PermanentError
        )
    }

    // ─── propagación de error del DAO (transitorio → reintento) ────────────────

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

    @Test
    fun prepareRequest_propagatesDaoException_whenComboDaoThrows() = runTest {
        // Coverage directo del sitio de sync para el DAO de COMBOS. Con >=1
        // producto (para no disparar el guard), un fallo del DAO de combos debe
        // propagarse como excepción (transitorio → BaseSyncWorker reintenta), no
        // colapsar a lista vacía ni armar un request.
        val saleId = "sale-combo-boom"
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = saleId))
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = saleId, articuloId = 100)
        )
        val entity = saleDataSource.getSaleById(saleId)!!

        val boom = IllegalStateException("combos dao corrupto")
        val throwingCombos = ComboLocalDataSource(throwingComboDao(boom))

        val thrown = try {
            handlerWith(combos = throwingCombos)
                .prepareRequest(context, entity, createOp(saleId), syncContext)
            null
        } catch (e: Exception) {
            e
        }

        assertSame(
            "el fallo del DAO de combos debe propagarse tal cual, no colapsar a vacío",
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

    private fun throwingComboDao(error: Throwable): LocalSaleComboDao = object : LocalSaleComboDao {
        override suspend fun insertCombo(combo: LocalSaleComboEntity) = Unit
        override suspend fun insertAllCombos(combos: List<LocalSaleComboEntity>) = Unit
        override suspend fun getCombosForSale(saleId: String): List<LocalSaleComboEntity> =
            throw error

        override suspend fun deleteCombosForSale(saleId: String) = Unit
        override suspend fun updateServerUuid(comboId: String, saleId: String, serverUuid: String) =
            Unit
    }
}
