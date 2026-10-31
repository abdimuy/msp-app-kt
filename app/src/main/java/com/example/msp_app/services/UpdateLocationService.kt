package com.example.msp_app.services

import android.Manifest
import android.app.Service
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.msp_app.R
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
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
            }
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
