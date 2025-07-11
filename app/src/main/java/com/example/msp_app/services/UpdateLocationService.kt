package com.example.msp_app.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.msp_app.R
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
import com.example.msp_app.workmanager.enqueuePendingVisitsWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class UpdateLocationService : Service(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    private lateinit var client: FusedLocationProviderClient
    private lateinit var cts: CancellationTokenSource
    private lateinit var paymentsStore: PaymentsLocalDataSource
    private lateinit var visitsStore: VisitsLocalDataSource

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        cts = CancellationTokenSource()
        paymentsStore = PaymentsLocalDataSource(applicationContext)
        visitsStore = VisitsLocalDataSource(applicationContext)

        val chanId = "loc_service"
        val notify = NotificationCompat.Builder(this, chanId)
            .setContentTitle("Actualizando ubicación")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        startForeground(1, notify)
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val paymentId = intent?.getStringExtra("payment_id")
        val visitId = intent?.getStringExtra("visit_id")
        if (paymentId == null && visitId == null) return START_NOT_STICKY

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                launch {
                    try {
                        if (paymentId != null) {
                            paymentsStore.updatePaymentLocation(
                                paymentId,
                                loc.latitude,
                                loc.longitude
                            )
                            enqueuePendingPaymentsWorker(applicationContext)
                        } else {
                            visitsStore.updateVisitLocation(visitId!!, loc.latitude, loc.longitude)
                            enqueuePendingVisitsWorker(applicationContext, visitId)
                        }
                        stopSelf()
                    } catch (e: Exception) {
                        Log.e("UpdateLocationService", "Error al actualizar ubicación", e)
                        stopSelf()
                    }
                }
            }
            .addOnFailureListener {
                launch {
                    try {
                        if (paymentId != null) {
                            enqueuePendingPaymentsWorker(applicationContext)
                        } else {
                            enqueuePendingVisitsWorker(applicationContext, visitId!!)
                        }
                    } catch (e: Exception) {
                        Log.e("UpdateLocationService", "Error en FailureListener", e)
                    } finally {
                        stopSelf()
                    }
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
}