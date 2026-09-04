package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.upload.ExistenceVerifier
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.upload.UploadDecision
import com.example.msp_app.core.upload.classifyUpload
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.api.services.payment.toCrearPagoBody
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.payment.toDomain
import com.example.msp_app.data.pagos.PartesDeComprobantes
import com.example.msp_app.data.pagos.partesDeComprobantes
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Sube un pago pendiente y, sólo cuando el servidor confirma que lo tiene,
 * marca `GUARDADO_EN_MICROSIP` para que la cohorte de reintentos deje de
 * reencolarlo.
 *
 * La regla, común a todos los módulos que suben capturas
 * (`docs/module-standards/ENTREGA_GARANTIZADA.md`):
 *
 * > El teléfono suelta una captura sólo cuando el servidor confirma una de dos
 * > cosas: «la apliqué» o «la tengo guardada para corregir».
 *
 * Ante cualquier error HTTP primero se consulta `GET /v2/cobranza/pagos/{id}`
 * (prueba de EXISTENCIA); si el servidor lo tiene, se suelta. Si no, decide
 * [classifyUpload] con la cabecera `X-Intent-Captured` como prueba de CUSTODIA.
 *
 * Lo que cambió y por qué: antes se infería la custodia del `Content-Type`
 * (`problem+json` = «llegó al API» = capturado). Es incorrecto — cuando el pool
 * de Firebird se traba, la petición falla Y la captura falla a la vez, pero la
 * respuesta sigue siendo `problem+json`. El 2026-08-13 eso soltó dos pagos
 * ($800) que nadie tenía. `reachedMspApi` sobrevive sólo como dato de log.
 *
 * El camino legacy queda intacto; [useV2] (de `BuildConfig.PAGOS_USE_V2`)
 * elige. No hay envío doble: dos backends arriesgarían un cobro duplicado.
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
    internal val imagenes: PaymentImageDao =
        AppDatabase.getInstance(appContext).paymentImageDao(),
    @VisibleForTesting
    internal val clock: AppClock = AppClock.System
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
     * Verificador de existencia contra `GET /v2/cobranza/pagos/{id}`.
     *
     * `true` = 200 (existe), `false` = 404 (no existe), `null` = indeterminado
     * (cualquier otro código o excepción). Un `null` NUNCA se lee como «no
     * existe»: el llamador sigue a la tabla de decisión.
     */
    @VisibleForTesting
    internal val existenceVerifier: ExistenceVerifier = ExistenceVerifier { id ->
        try {
            v2Api.obtenerPago(id)
            true
        } catch (verifyErr: HttpException) {
            if (verifyErr.code() == 404) false else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Subida v2. Sólo marca GUARDADO_EN_MICROSIP cuando el servidor confirma
     * que tiene el pago — por existencia (GET) o por custodia (cabecera).
     */
    private suspend fun uploadV2(payment: PaymentEntity): Result {
        return try {
            val json = Gson().toJson(payment.toCrearPagoBody())
            val datos = json.toRequestBody("application/json".toMediaTypeOrNull())
            val comprobantes = comprobantesDe(payment)
            val response = v2Api.crearPago(
                idempotencyKey = payment.ID,
                datos = datos,
                imagenes = comprobantes.partes
            )
            persistDoctoCcId(payment, response.docto_cc_id)
            markUploaded(comprobantes)
            markDone(payment.ID)
            Log.i(TAG, "Pago aplicado en v2: ${payment.ID} (server=${response.id})")
            Result.success()
        } catch (e: HttpException) {
            handleHttpError(payment, e)
        } catch (e: IOException) {
            // El server no lo vio: jamás marcar listo. El teléfono lo conserva.
            Log.w(TAG, "Pago ${payment.ID}: error de red, reintentando", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Pago ${payment.ID}: error inesperado, reintentando", e)
            Result.retry()
        }
    }

    /**
     * Decide qué hacer con un error HTTP: primero pregunta si el servidor ya
     * tiene el pago, y sólo si no lo resuelve consulta la tabla de decisión.
     */
    private suspend fun handleHttpError(payment: PaymentEntity, e: HttpException): Result {
        // Prueba de EXISTENCIA. El pago pudo haberse creado por una corrida
        // anterior cuyo 2xx no nos llegó, o por un replay desde la oficina.
        if (existenceVerifier.exists(payment.ID) == true) {
            markDone(payment.ID)
            Log.i(
                TAG,
                "RECONCILED_VIA_GET: pago ${payment.ID} ya existía server-side " +
                    "(HTTP original ${e.code()}); reconciliado sin reintentar"
            )
            return Result.success()
        }

        // Prueba de CUSTODIA. Sólo la cabecera cuenta: el servidor la emite
        // únicamente cuando su Store.Save tuvo éxito.
        val headers = e.response()?.headers()
        val captureConfirmed = !headers?.get(HEADER_INTENT_CAPTURED).isNullOrBlank()
        // Sólo para el log: ya no decide nada.
        val reachedMspApi = headers?.get("Content-Type").orEmpty()
            .contains("problem+json", ignoreCase = true)

        return when (classifyUpload(e.code(), reachedMspApi, captureConfirmed)) {
            UploadDecision.RELEASE -> {
                markDone(payment.ID)
                Log.w(
                    TAG,
                    "Pago ${payment.ID} rechazado (${e.code()}); resguardado server-side " +
                        "(X-Intent-Captured), lo corrige la oficina"
                )
                Result.success()
            }

            UploadDecision.RETRY -> {
                Log.w(
                    TAG,
                    "Pago ${payment.ID}: HTTP ${e.code()} sin custodia confirmada " +
                        "(reachedMspApi=$reachedMspApi), reintentando"
                )
                Result.retry()
            }
        }
    }

    /**
     * Los comprobantes PENDIENTES del pago, ya convertidos en partes.
     *
     * Filtra por `PAGO_ID` —el del pago— y por `SUBIDA_EN IS NULL`: una imagen
     * que el servidor ya confirmó no se vuelve a mandar. Es best-effort: si la
     * base no responde, el pago sube **sin** comprobantes en vez de no subir.
     * El dinero manda; la foto lo acompaña.
     */
    private suspend fun comprobantesDe(payment: PaymentEntity): PartesDeComprobantes = try {
        val pendientes = imagenes.getPendientesDe(payment.ID)
        partesDeComprobantes(pendientes).also {
            if (it.omitidas > 0) {
                Log.w(
                    TAG,
                    "Pago ${payment.ID}: ${it.omitidas} comprobante(s) omitido(s) " +
                        "(archivo ausente o tipo no permitido)"
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Pago ${payment.ID}: no se pudieron leer los comprobantes", e)
        PartesDeComprobantes(partes = emptyList(), enviadas = emptyList(), omitidas = 0)
    }

    /**
     * Estampa `SUBIDA_EN` **solo en las que viajaron**, y borra su archivo.
     *
     * Marcar por pago —o marcar lo que había en la base al empezar— estamparía
     * también las que se omitieron por no tener archivo, y esas tienen que
     * quedarse pendientes para que se vean. El archivo local se va porque ya
     * cumplió: el servidor tiene la foto, y en un teléfono de gama baja cada
     * comprobante retenido son cientos de KB que no vuelven.
     *
     * Best-effort de punta a punta: una entrega que ya tuvo éxito no se puede
     * tumbar por no poder escribir un timestamp.
     */
    private suspend fun markUploaded(comprobantes: PartesDeComprobantes) {
        if (comprobantes.enviadas.isEmpty()) return
        val subidaEn = AppTime.toWireFormat(clock.now())
        comprobantes.enviadas.forEach { imagen ->
            try {
                imagenes.marcarSubida(imagen.ID, subidaEn)
                File(imagen.URI).delete()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cerrar el comprobante ${imagen.ID}", e)
            }
        }
    }

    /**
     * Guarda el DOCTO_CC_ID que Microsip asignó. Es best-effort: un fallo aquí
     * no puede tumbar una entrega que ya tuvo éxito.
     */
    private suspend fun persistDoctoCcId(payment: PaymentEntity, doctoCcId: Int?) {
        if (doctoCcId == null || doctoCcId == payment.DOCTO_CC_ID) return
        try {
            paymentsStore.updatePaymentDoctoCcId(payment.ID, doctoCcId)
        } catch (e: Exception) {
            Log.w(TAG, "Pago ${payment.ID}: no se pudo guardar docto_cc_id", e)
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
    }
}
