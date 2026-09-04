package com.example.msp_app.services

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.msp_app.R
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class UpdateLocationService : Service(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    @Inject
    lateinit var telemetry: Telemetry

    private lateinit var client: FusedLocationProviderClient
    private lateinit var cts: CancellationTokenSource
    private lateinit var handler: UpdateLocationHandler

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        cts = CancellationTokenSource()

        val paymentsStore = PaymentsLocalDataSource(applicationContext)
        val visitsStore = VisitsLocalDataSource(applicationContext)
        handler = UpdateLocationHandler(
            telemetry = telemetry,
            updatePaymentLocation = { id, lat, lng ->
                paymentsStore.updatePaymentLocation(id, lat, lng)
            },
            updateVisitLocation = { id, lat, lng ->
                visitsStore.updateVisitLocation(id, lat, lng)
            },
            enqueuePayment = { id -> enqueuePendingPaymentsWorker(applicationContext, id) }
        )

        val chanId = "loc_service"
        val notify = NotificationCompat.Builder(this, chanId)
            .setContentTitle("Actualizando ubicación")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, notify)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
    )
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val paymentId = intent?.getStringExtra(EXTRA_PAYMENT_ID)
        val visitId = intent?.getStringExtra(EXTRA_VISIT_ID)
        if (paymentId == null && visitId == null) return START_NOT_STICKY

        launch {
            try {
                handler.handle(paymentId, visitId) {
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                }
            } finally {
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        cts.cancel()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        /**
         * El extra que lleva un `Payment.ID` — la llave de `Payment`, que es lo
         * que filtra `PaymentDao.updateLocation(id, lat, lng)`. **No** es un
         * `DOCTO_CC_ACR_ID` ni un `CLIENTE_ID`: este plan ya cazó seis defectos
         * de esa familia, y la constante existe para que el nombre viaje con el
         * valor en vez de repetirse como literal en cada llamador.
         */
        const val EXTRA_PAYMENT_ID: String = "payment_id"

        /** El extra que lleva un `Visit.id`, filtrado por `VisitDao.updateLocation`. */
        const val EXTRA_VISIT_ID: String = "visit_id"
    }
}

/**
 * Pide la ubicación de un abono ya escrito: arranca [UpdateLocationService] con
 * el `Payment.ID` para que parche `LAT`/`LNG` y encole la subida.
 *
 * Es el MISMO mecanismo que usaba `NewPaymentDialog` (Task 21, ronda 1), no uno
 * nuevo. Vive aquí y no en el llamador porque el llamador es un adaptador de
 * datos que no debe conocer `Intent`, y porque el par (clase del servicio,
 * nombre del extra) tiene que quedarse junto: repartido, un renombre de un lado
 * deja al otro mandando un extra que nadie lee.
 *
 * **Puede lanzar** —Android 12+ rechaza `startForegroundService` con la app en
 * segundo plano— y por eso no se llama nunca desde dentro de la transacción del
 * abono: el dinero ya está escrito cuando esto corre, y quien llama atrapa y
 * reporta. La ubicación es un adorno del registro, jamás su requisito.
 */
fun pedirUbicacionDelPago(context: Context, pagoId: String) {
    val intent = Intent(context, UpdateLocationService::class.java).apply {
        putExtra(UpdateLocationService.EXTRA_PAYMENT_ID, pagoId)
    }
    ContextCompat.startForegroundService(context, intent)
}
