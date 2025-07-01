package com.example.msp_app.services

import android.Manifest
import android.app.Service
import android.content.Intent
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.msp_app.R
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.workmanager.enqueuePendingPaymentsWorker
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

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        cts = CancellationTokenSource()
        paymentsStore = PaymentsLocalDataSource(applicationContext)

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
        val paymentId = intent?.getStringExtra("payment_id") ?: return START_NOT_STICKY

        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                launch {
                    try {
                        Log.d("UpdateLocationService", "Location received: $loc")
                        paymentsStore.updatePaymentLocation(
                            paymentId,
                            loc.latitude,
                            loc.longitude
                        )
                        enqueuePendingPaymentsWorker(applicationContext)

                        Log.d("UpdateLocationService", "Location updated")
                        paymentsStore.getPaymentById(paymentId).let { payment ->
                            Log.d("UpdateLocationService", "Payment updated: $payment")
                        }
                    } catch (e: Exception) {
                        Log.e("UpdateLocationService", "Error al actualizar ubicación", e)
                    } finally {
                        stopSelf()
                    }
                }
            }
            .addOnFailureListener {
                launch {
                    try {
                        Log.w(
                            "UpdateLocationService",
                            "No se pudo obtener ubicación, enviando igual"
                        )
                        enqueuePendingPaymentsWorker(applicationContext)
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