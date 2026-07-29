package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.api.services.payment.toCrearPagoBody
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.local.entities.PaymentEntity
import com.example.msp_app.data.models.payment.toDomain
import com.example.msp_app.features.payments.upload.domain.PaymentUploadClassifier
import com.example.msp_app.features.payments.upload.domain.PaymentUploadDecision
import com.google.gson.Gson
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Uploads one durably-stored pending pago to the backend and, on confirmation
 * that the server holds it, flips `GUARDADO_EN_MICROSIP` so the retry cohort
 * stops re-enqueuing it.
 *
 * The v2 path targets msp-api's `POST /v2/cobranza/pagos` (atomic + idempotent
 * by `datos.id`). Its robustness rule: a pago is marked "done" ONLY when the
 * server is known to hold it — a 2xx, or a 4xx which the cobranza failed-intent
 * capture middleware guarantees is persisted server-side for desk correction.
 * A network failure is never marked done, so a device that alone holds a pago
 * keeps retrying instead of dropping it. Idempotency by `datos.id` makes a
 * resend safe (no double-collection).
 *
 * The legacy path is preserved unchanged for prod until a prod Go host exists;
 * [useV2] (from `BuildConfig.PAGOS_USE_V2`) selects between them. There is no
 * dual send — two backends would risk a double charge.
 *
 * The injected seams (defaulted to production) let unit tests drive the worker
 * without WorkManager, Firebase, or a real network.
 */
class PendingPaymentsWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val paymentsStore: PaymentsLocalDataSource = PaymentsLocalDataSource(appContext),
    @VisibleForTesting
    internal val v2Api: V2PaymentsApi = V2ApiProvider.create(V2PaymentsApi::class.java),
    @VisibleForTesting
    internal val legacyApi: PaymentsApi = ApiProvider.create(PaymentsApi::class.java),
    @VisibleForTesting
    internal val useV2: Boolean = BuildConfig.PAGOS_USE_V2,
    @VisibleForTesting
    internal val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("payment_id")
            ?: return Result.failure().also {
                Log.e(TAG, "No se proporcionó payment_id")
            }

        val payment = paymentsStore.getPaymentById(id)
            ?: return Result.failure().also {
                Log.e(TAG, "Pago no encontrado: $id")
            }

        return if (useV2) uploadV2(payment) else uploadLegacy(payment)
    }

    /**
     * v2 upload. Classifies the outcome so we only mark GUARDADO_EN_MICROSIP
     * when the server is known to hold the pago. See [PaymentUploadClassifier].
     */
    private suspend fun uploadV2(payment: PaymentEntity): Result {
        return try {
            val json = Gson().toJson(payment.toCrearPagoBody())
            val datos = json.toRequestBody("application/json".toMediaTypeOrNull())
            val response = v2Api.crearPago(idempotencyKey = payment.ID, datos = datos)
            markDone(payment.ID)
            Log.i(TAG, "Pago aplicado en v2: ${payment.ID} (server=${response.id})")
            Result.success()
        } catch (e: HttpException) {
            when (PaymentUploadClassifier.classifyHttpCode(e.code())) {
                PaymentUploadDecision.DONE -> {
                    // 4xx captured server-side → el desk lo corrige; el teléfono terminó.
                    markDone(payment.ID)
                    Log.w(
                        TAG,
                        "Pago ${payment.ID} rechazado (${e.code()}); capturado server-side, marcado listo"
                    )
                    Result.success()
                }

                PaymentUploadDecision.RETRY -> {
                    Log.w(TAG, "Pago ${payment.ID}: HTTP ${e.code()} transitorio, reintentando")
                    Result.retry()
                }

                PaymentUploadDecision.RETRY_THEN_DONE -> {
                    // runAttemptCount is 0 on the first run; +1 counts this attempt.
                    if (runAttemptCount + 1 >= maxAttempts) {
                        markDone(payment.ID)
                        Log.w(
                            TAG,
                            "Pago ${payment.ID}: 5xx tras ${runAttemptCount + 1} intentos; " +
                                "ya capturado server-side, marcado listo"
                        )
                        Result.success()
                    } else {
                        Log.w(TAG, "Pago ${payment.ID}: HTTP ${e.code()} server, reintentando")
                        Result.retry()
                    }
                }
            }
        } catch (e: IOException) {
            // El server no lo vio: jamás marcar listo. El teléfono lo conserva.
            Log.w(TAG, "Pago ${payment.ID}: error de red, reintentando", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Pago ${payment.ID}: error inesperado, reintentando", e)
            Result.retry()
        }
    }

    /** Legacy upload (prod). Unchanged behaviour: any error retries blindly. */
    private suspend fun uploadLegacy(payment: PaymentEntity): Result {
        return try {
            Log.d(TAG, "Enviando pago (legacy): ${payment.ID}")
            legacyApi.savePayment(PaymentRequest(payment.toDomain()))
            markDone(payment.ID)
            Log.d(TAG, "Pago marcado como enviado (legacy): ${payment.ID}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar pago ${payment.ID} (legacy)", e)
            Result.retry()
        }
    }

    private suspend fun markDone(paymentId: String) {
        paymentsStore.changePaymentStatus(paymentId, true)
    }

    companion object {
        private const val TAG = "PendingPaymentsWorker"

        /** Max attempts before a server-side 5xx is treated as captured/done. */
        const val DEFAULT_MAX_ATTEMPTS = 10
    }
}
