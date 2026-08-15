package com.example.msp_app.core.appgate.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.appgate.UpdatePackage
import com.example.msp_app.core.testing.RobolectricTestBase
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val PAQUETE = UpdatePackage(
    url = "https://example.invalid/msp-app-2.17.0.apk",
    sizeBytes = 11_000_000L,
    sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"
)

/** El mismo release republicado por la oficina tras corregir el APK. */
private val PAQUETE_CORREGIDO = PAQUETE.copy(
    url = "https://example.invalid/msp-app-2.17.0-r2.apk",
    sizeBytes = 11_200_000L,
    sha256 = "60303ae22b998861bce3b28f33eec1be758a213c86c93c076dbe9f558c11c752"
)

/**
 * La política de red, que es la mitad del trato: **automática solo por wifi,
 * manual en cualquier red**. Es una constante de una línea, y justamente por
 * eso conviene que tenga candado: invertirla gastaría los datos de la flota
 * sin que nadie se entere hasta el recibo.
 *
 * Y la política de encolado, que es la otra mitad: conservar lo que ya está
 * bajando mientras sea el mismo paquete, reemplazarlo en cuanto la oficina
 * publica otro. WorkManager de verdad y no un doble: la pregunta que decide
 * ("¿qué paquete está encolado?") sólo la contesta la cola, que sobrevive a
 * que el proceso muera.
 */
class UpdateDownloadSchedulerTest : RobolectricTestBase() {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: UpdateDownloadScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = UpdateDownloadScheduler(workManager)
    }

    @Test
    fun `la descarga automatica exige una red sin costo`() {
        assertEquals(
            NetworkType.UNMETERED,
            updateDownloadConstraints(automatic = true).requiredNetworkType
        )
    }

    @Test
    fun `la descarga manual se conforma con cualquier red`() {
        assertEquals(
            NetworkType.CONNECTED,
            updateDownloadConstraints(automatic = false).requiredNetworkType
        )
    }

    @Test
    fun `el paquete viaja entero al worker`() {
        val recuperado = PAQUETE.toInputData().toUpdatePackage()

        assertEquals(PAQUETE, recuperado)
    }

    @Test
    fun `unos datos de entrada sin checksum no producen paquete`() {
        val sinChecksum = PAQUETE.copy(sha256 = "").toInputData()

        // El worker responde `failure` con esto: no hay forma de verificar la
        // descarga, así que no tiene sentido intentarla.
        assertNull(sinChecksum.toUpdatePackage())
    }

    @Test
    fun `sin trabajo previo la automatica conserva`() = runTest {
        assertEquals(ExistingWorkPolicy.KEEP, scheduler.automaticPolicyFor(PAQUETE))
    }

    @Test
    fun `el mismo paquete se conserva y no se reencola`() = runTest {
        scheduler.enqueueAutomatic(PAQUETE)
        val original = trabajoPendiente()

        scheduler.enqueueAutomatic(PAQUETE)

        // Mismo id = el trabajo siguió con lo que llevaba bajado. Reemplazar
        // acá reiniciaría la descarga en cada emisión de la configuración y
        // los 11 MB no terminarían nunca.
        assertEquals(ExistingWorkPolicy.KEEP, scheduler.automaticPolicyFor(PAQUETE))
        assertEquals(original.id, trabajoPendiente().id)
    }

    @Test
    fun `un paquete distinto reemplaza al que estaba encolado`() = runTest {
        scheduler.enqueueAutomatic(PAQUETE)
        val original = trabajoPendiente()

        scheduler.enqueueAutomatic(PAQUETE_CORREGIDO)

        val vigente = trabajoPendiente()
        assertNotEquals(original.id, vigente.id)
        assertTrue(
            "el trabajo vigente debe traer el paquete republicado",
            PAQUETE_CORREGIDO.workTag() in vigente.tags
        )
        // `REPLACE` cancela y además borra el registro del trabajo viejo, así
        // que "ya no existe" es un desenlace tan válido como "terminado".
        val anterior = estadoDe(original.id)
        assertTrue(
            "el trabajo del paquete anterior no debe seguir vivo, estaba en $anterior",
            anterior == null || anterior.isFinished
        )
    }

    @Test
    fun `un trabajo ya terminado no cuenta como pendiente`() = runTest {
        val terminado = OneTimeWorkRequestBuilder<TrabajoQueTermina>()
            .addTag(PAQUETE.workTag())
            .build()
        workManager
            .enqueueUniqueWork(UPDATE_DOWNLOAD_WORK, ExistingWorkPolicy.REPLACE, terminado)
            .result
            .get()
        assertEquals(WorkInfo.State.SUCCEEDED, estadoDe(terminado.id))

        // Trae otro SHA, pero ya no hay nada que reemplazar: cancelar un
        // trabajo terminado no arregla nada y WorkManager lo sustituye igual.
        assertEquals(
            ExistingWorkPolicy.KEEP,
            scheduler.automaticPolicyFor(PAQUETE_CORREGIDO)
        )
    }

    private fun trabajoPendiente(): WorkInfo = workManager
        .getWorkInfosForUniqueWork(UPDATE_DOWNLOAD_WORK)
        .get()
        .single { !it.state.isFinished }

    private fun estadoDe(id: UUID): WorkInfo.State? = workManager.getWorkInfoById(id).get()?.state
}

/** Sustituto del descargador real: sin restricciones, termina al encolarse. */
class TrabajoQueTermina(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    override fun doWork(): Result = Result.success()
}
