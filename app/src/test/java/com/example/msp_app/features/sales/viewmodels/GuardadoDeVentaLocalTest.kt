package com.example.msp_app.features.sales.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.draft.SaleDraft
import com.example.msp_app.core.draft.SaleDraftManager
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.sales.data.FotosDeLaVentaLocal
import com.example.msp_app.features.sales.data.RegistroDeVentaLocal
import com.example.msp_app.workmanager.localSaleUniqueWorkName
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-094 (D5), los tres defectos que MIDIÓ la compuerta ANTES del 2026-10-26
 * sobre el guardado REAL de la venta local (`NewLocalSaleViewModel`):
 * 1. una falla a la mitad dejaba la venta a medias (cabecera + 1 línea);
 * 2. una foto que no se podía comprimir se tragaba y la venta salía "guardada";
 * 3. volver a guardar el mismo borrador creaba una segunda venta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuardadoDeVentaLocalTest : RoomTestBase() {

    private lateinit var context: Context
    private lateinit var dirFotos: File

    @Before
    fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        dirFotos = Files.createTempDirectory("fotos-venta").toFile()
        runBlocking { SaleDraftManager(context).clearDraft() }
    }

    @After
    fun restaurar() {
        Dispatchers.resetMain()
    }

    @Test
    fun `E-APP-094 una falla a la mitad no deja la venta a medias`() {
        val vm = vmReal()
        // Combo con LOCAL_SALE_ID ajeno: viola la FK DESPUÉS de la cabecera y
        // las líneas (la misma inyección que usó la compuerta).
        crear(
            vm,
            ID,
            combos = listOf(LocalSaleComboEntity("C1", "NO-EXISTE", "Combo", 1.0, 1.0, 1.0))
        )

        val r = esperarResultado(vm)

        assertTrue("$r", r is SaveResult.Error)
        assertNull("antes quedaba la cabecera", runBlocking { db.localSaleDao().getSaleById(ID) })
        assertEquals(0, runBlocking { db.localSaleProduct().getProductsForSale(ID).size })
        assertFalse(encolada(ID))
    }

    @Test
    fun `E-APP-094 una foto que no se puede procesar no se traga - error y nada escrito`() {
        val vm = vmReal()
        crear(vm, ID, uris = listOf(Uri.parse("file:///no/existe/foto.jpg")))

        val r = esperarResultado(vm)

        assertEquals(SaveResult.Error("No se pudo procesar la foto 1"), r)
        assertNull(
            "antes la venta salía Success sin foto",
            runBlocking {
                db.localSaleDao().getSaleById(ID)
            }
        )
        assertFalse("antes se encolaba y el subidor la atoraba por NO_IMAGES", encolada(ID))
    }

    @Test
    fun `E-APP-094 volver a guardar el mismo borrador no crea otra venta`() {
        // El formulario restaurado del borrador conserva el saleId.
        val form = NewSaleFormViewModel(ApplicationProvider.getApplicationContext())
        form.applyDraft(SaleDraft(saleId = ID, clientName = "Rosa Hernández López"))
        val saleId = form.buildSaleData().saleId
        assertEquals("antes buildSaleData acuñaba un id por toque", ID, saleId)

        val uris = listOf(Uri.parse("content://x/draft_images/a.jpg"))
        val vm1 = vmConFotos()
        crear(vm1, saleId, uris = uris)
        assertEquals(SaveResult.Success(ID), esperarResultado(vm1))
        runBlocking { db.localSaleDao().updateSaleStatus(ID, true) }

        // Proceso muerto antes de limpiar el borrador: el vendedor guarda otra vez.
        val vm2 = vmConFotos()
        crear(vm2, form.buildSaleData().saleId, uris = uris)

        assertEquals(SaveResult.YaGuardada(ID), esperarResultado(vm2))
        val venta = runBlocking { db.localSaleDao().getSaleById(ID) }!!
        assertTrue("la venta ya enviada sigue enviada", venta.ENVIADO)
        assertEquals(1, runBlocking { db.localSaleDao().getImagesForSale(ID).size })
        assertEquals(1, runBlocking { db.localSaleDao().getAllSales() }.size)
    }

    @Test
    fun `E-APP-094 el borrador guarda el saleId y reconoce la venta ya guardada`() {
        val drafts = SaleDraftManager(context)
        runBlocking {
            drafts.saveDraft(
                SaleDraft(saleId = ID, clientName = "Rosa Hernández López")
            )
        }
        val cargado = runBlocking { drafts.loadDraft() }!!
        assertEquals(ID, cargado.saleId)

        val form = NewSaleFormViewModel(ApplicationProvider.getApplicationContext())
        assertFalse(runBlocking { form.borradorYaGuardado(cargado) })

        val vm = vmConFotos()
        crear(vm, ID)
        assertEquals(SaveResult.Success(ID), esperarResultado(vm))

        assertTrue(
            "tras escribirla, el mismo borrador ya no se ofrece de nuevo",
            runBlocking { form.borradorYaGuardado(cargado) }
        )
    }

    @Test
    fun `E-APP-094 control - la venta con foto se guarda completa y se encola`() {
        val vm = vmConFotos()
        crear(vm, ID, uris = listOf(Uri.parse("content://x/draft_images/a.jpg")))

        assertEquals(SaveResult.Success(ID), esperarResultado(vm))
        assertEquals(1, runBlocking { db.localSaleProduct().getProductsForSale(ID).size })
        val fotos = runBlocking { db.localSaleDao().getImagesForSale(ID) }
        assertEquals(1, fotos.size)
        assertTrue(File(fotos.single().IMAGE_URI).isFile)
        assertTrue(encolada(ID))
    }

    /**
     * E-APP-094: si la pantalla se cierra mientras se comprimen las fotos, la
     * cancelación se relanza: no se pinta como "Error al guardar" (antes el
     * `catch (e: Exception)` la atrapaba) y no se escribe nada a medias.
     */
    @Test
    fun `E-APP-094 la cancelacion durante las fotos se relanza y no se pinta como error`() {
        lateinit var vm: NewLocalSaleViewModel
        val app = ApplicationProvider.getApplicationContext<Application>()
        vm = NewLocalSaleViewModel(
            app,
            FotosDeLaVentaLocal(dirFotos) { _, destino ->
                vm.viewModelScope.cancel()
                destino.writeBytes(byteArrayOf(1, 2, 3))
                destino
            },
            RegistroDeVentaLocal(db)
        )

        crear(vm, ID, uris = listOf(Uri.parse("content://x/draft_images/a.jpg")))
        runBlocking { withTimeout(10_000) { while (vm.isLoading.value) delay(20) } }

        assertNull("la cancelación no es un error de guardado", vm.saveResult.value)
        assertNull(runBlocking { db.localSaleDao().getSaleById(ID) })
        assertFalse(encolada(ID))
    }

    private fun vmReal() = NewLocalSaleViewModel(ApplicationProvider.getApplicationContext())

    private fun vmConFotos(): NewLocalSaleViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return NewLocalSaleViewModel(
            app,
            FotosDeLaVentaLocal(dirFotos) { _, destino ->
                destino.writeBytes(byteArrayOf(1, 2, 3))
                destino
            },
            RegistroDeVentaLocal(db)
        )
    }

    private fun item() = SaleItem(
        product = ProductInventory(101, "Ropero", 5, 1, "Recámara", "5000"),
        quantity = 1
    )

    private fun crear(
        vm: NewLocalSaleViewModel,
        saleId: String,
        uris: List<Uri> = emptyList(),
        combos: List<LocalSaleComboEntity> = emptyList()
    ) {
        vm.createSaleWithImages(
            saleId = saleId, clientName = "Rosa Hernández López", saleDate = "2026-10-26T18:00:00Z",
            imageUris = uris, latitude = 0.0, longitude = 0.0, address = "Calle Hidalgo",
            installment = 200.0, downpayment = 0.0, phone = "2221234567", paymentfrequency = "SEMANAL",
            avaloresponsable = "", note = "", collectionday = "LUNES", totalprice = 5000.0,
            shorttermtime = 0, shorttermamount = 4500.0, cashamount = 4000.0, enviado = false,
            saleProducts = listOf(
                item()
            ),
            context = context, userEmail = "vendedor@muebleriamsp.mx",
            combos = combos
        )
    }

    private fun esperarResultado(vm: NewLocalSaleViewModel): SaveResult? = runBlocking {
        withTimeout(10_000) {
            while (vm.isLoading.value || vm.saveResult.value == null) delay(20)
        }
        vm.saveResult.value
    }

    private fun encolada(saleId: String): Boolean = WorkManager.getInstance(
        context
    ).getWorkInfosForUniqueWork(localSaleUniqueWorkName(saleId)).get()
        .any { it.state != WorkInfo.State.CANCELLED }

    private companion object {
        const val ID = "5f1c2a3b-7d4e-4c8f-9a1b-2c3d4e5f6a7b"
    }
}
