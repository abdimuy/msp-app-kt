package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.sync.ventas.VendedorResolver
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.upload.UploadDecision
import com.example.msp_app.core.upload.classifyUpload
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.ventas.VendedorDTO
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.models.sale.localsale.LocalSaleMappers
import com.example.msp_app.features.camionetaAssignment.data.repository.CamionetaAssignmentRepository
import com.example.msp_app.features.sales.upload.data.RoomUploadFailureRepository
import com.example.msp_app.features.sales.upload.domain.UploadFailure
import com.example.msp_app.features.sales.upload.domain.UploadFailureClassification
import com.example.msp_app.features.sales.upload.domain.UploadFailureRepository
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class PendingLocalSalesWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    /**
     * Resolves vendedores and the camioneta id for a given user e-mail.
     * Returns a [Pair] of (vendedores list, camionetaId). camionetaId may be
     * null when the user has no camioneta assigned.
     *
     * Defaults to the production Firestore + Go API flow.
     * Overridable in tests without touching WorkManager's factory.
     */
    @VisibleForTesting
    internal val resolveVendedoresForEmail:
    suspend (userEmail: String) -> Pair<List<VendedorDTO>, Int?> =
        run {
            val repo = CamionetaAssignmentRepository()
            val resolver = VendedorResolver(repo);
            { email ->
                val allUsers = repo.getAllUsers().getOrElse { throw it }
                val camionetaId = allUsers.firstOrNull { it.EMAIL == email }?.CAMIONETA_ASIGNADA
                Pair(resolver.resolve(camionetaId), camionetaId)
            }
        },
    /**
     * Retrofit service for `POST /v2/ventas`.
     * Overridable in tests to inject a MockWebServer-backed or fake implementation.
     */
    @VisibleForTesting
    internal val ventasApi: VentasApi = V2ApiProvider.create(VentasApi::class.java),
    /**
     * Persists/clears the per-sale upload-failure record and the rotating
     * Idempotency-Key. See [UploadFailureRepository] for the contract.
     * Default uses the Room DAO via the app database singleton.
     */
    @VisibleForTesting
    internal val uploadFailureRepository: UploadFailureRepository =
        RoomUploadFailureRepository(AppDatabase.getInstance(appContext).localSaleDao()),
    /**
     * Wall-clock provider for stamping LAST_UPLOAD_AT. Overridable so tests
     * can assert on a deterministic timestamp.
     */
    @VisibleForTesting
    internal val nowEpochMillis: () -> Long = System::currentTimeMillis,
    /**
     * Período del LATIDO que renueva el arrendamiento del candado de subida
     * mientras el `POST` sigue en vuelo. Ver
     * [LocalSaleClaimLeases.UPLOAD_HEARTBEAT_MS] para el porqué del valor.
     *
     * Un valor **no positivo apaga el latido**. Existe para que una prueba
     * pueda reproducir el único caso que el latido NO cubre y que
     * `CORRECCION_NO_ENVIADA` sí: el proceso muerto a media subida, donde no
     * hay nadie que lata. En producción nunca se pasa.
     */
    @VisibleForTesting
    internal val latidoDeSubidaMs: Long = LocalSaleClaimLeases.UPLOAD_HEARTBEAT_MS,
    /**
     * La renovación del arrendamiento, como costurón propio. Por defecto es
     * `renewUploadClaim` del DAO — el mismo SQL que corre en el teléfono.
     *
     * Es un costurón (y no una llamada directa a [localSaleStore]) porque el
     * latido es la única parte de este worker que ocurre EN PARALELO al
     * cuerpo: sin un punto de observación, una prueba sólo podría enterarse
     * de que latió sondeando la base — o sea, con un reloj real, que es justo
     * lo que la estrategia de pruebas de este plan prohíbe. Con el costurón,
     * la prueba envuelve la llamada real y sabe EXACTAMENTE cuándo terminó
     * cada latido, sin esperar nada.
     */
    @VisibleForTesting
    internal val renovarArrendamientoDeSubida: suspend (
        saleId: String,
        claimId: String,
        now: Long
    ) -> Int = { saleId, claimId, now ->
        AppDatabase.getInstance(appContext).localSaleDao()
            .renewUploadClaim(saleId, claimId, now)
    },
    /**
     * Acuña el `CLAIM_ID` del candado de subida. Inyectable para que una
     * prueba pueda reconstruir el estado exacto de la fila sin adivinar un
     * UUID aleatorio.
     */
    @VisibleForTesting
    internal val nuevoClaimId: () -> String = { UUID.randomUUID().toString() }
) : CoroutineWorker(appContext, workerParams) {

    private val localSaleStore = LocalSaleDataSource(appContext)
    private val saleProductStore = SaleProductLocalDataSource(appContext)
    private val comboDataSource = ComboLocalDataSource(appContext)
    private val mappers = LocalSaleMappers()
    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(appContext) }

    override suspend fun doWork(): Result {
        val saleId = inputData.getString("local_sale_id")
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "No se proporcionó local_sale_id")
                logger.error(
                    module = "SALES_WORKER",
                    action = "MISSING_SALE_ID",
                    message = "Worker iniciado sin local_sale_id"
                )
            }

        val userEmail = inputData.getString("user_email")
            ?: return Result.failure().also {
                Log.e("PendingLocalSalesWorker", "No se proporcionó user_email")
                logger.error(
                    module = "SALES_WORKER",
                    action = "MISSING_USER_EMAIL",
                    message = "Worker iniciado sin user_email",
                    data = mapOf("saleId" to saleId)
                )
            }

        // ── La compuerta de la carrera corregir-vs-subir (Task 4) ──────────
        //
        // El candado se toma ANTES de leer NADA del cuerpo, y ese orden es el
        // mecanismo entero: con el candado puesto, el editor no puede
        // commitear mientras el cuerpo se arma (su `claimForEdit` rechaza un
        // candado de SUBIDA vigente), así que el `POST` nunca puede salir con
        // un cuerpo a medio corregir. Leer primero y reclamar justo antes del
        // `POST` — el orden de la versión vieja del plan — dejaba esa ventana
        // abierta, y ninguna revalidación posterior la cierra: para cuando se
        // detecta, el cuerpo ya está armado con dos versiones mezcladas.
        //
        // Esto ES el fence: no hay un segundo chequeo "¿hay candado vivo?"
        // por delante. `claimForUpload` devuelve 0 exactamente cuando lo
        // habría bloqueado un fence separado, y sin la ventana entre mirar y
        // reclamar que dos sentencias siempre dejan.
        val claimId = nuevoClaimId()
        if (!localSaleStore.claimForUpload(saleId, claimId, nowEpochMillis())) {
            return resultadoSinCandado(saleId, userEmail)
        }

        val sale = localSaleStore.getSaleById(saleId)
            ?: return ventaNoEncontrada(saleId, userEmail)

        // El `REVISION` de ESTE instante — con el candado ya tomado, nadie
        // puede subirlo mientras armamos el cuerpo. Es el que se le pasa a
        // `markSentAndCloseEdit` cuando vuelva el 2xx: si para entonces ya no
        // coincide, una corrección se coló con el POST en vuelo y la
        // divergencia se marca en vez de perderse.
        val revisionAlReclamar = localSaleStore.getSaleClaimSnapshot(saleId)?.REVISION ?: 0

        Log.d(
            "PendingLocalSalesWorker",
            "DEBUG_SALE saleId=$saleId" +
                " COLONIA='${sale.COLONIA}'" +
                " POBLACION='${sale.POBLACION}'" +
                " CIUDAD='${sale.CIUDAD}'" +
                " TIEMPO_A_CORTO_PLAZOMESES=${sale.TIEMPO_A_CORTO_PLAZOMESES}" +
                " TIPO_VENTA='${sale.TIPO_VENTA}'" +
                " FREC_PAGO='${sale.FREC_PAGO}'" +
                " DIA_COBRANZA='${sale.DIA_COBRANZA}'"
        )
        return try {
            val images = localSaleStore.getImagesForSale(saleId)
            if (images.isEmpty()) {
                Log.e("PendingLocalSalesWorker", "Venta sin imágenes: $saleId")
                logger.error(
                    module = "SALES_WORKER",
                    action = "NO_IMAGES",
                    message = "La venta no tiene imágenes adjuntas",
                    data = mapOf("saleId" to saleId)
                )
                return Result.failure()
            }

            val (rawVendedores, camionetaId) = resolveVendedoresForEmail(userEmail)
            // Deterministic snapshot UUID per (saleId, usuario_id) so retries
            // produce the SAME body → no idempotency_key_mismatch.
            val vendedores = rawVendedores.map { v ->
                v.copy(
                    id = UUID.nameUUIDFromBytes(
                        "$saleId|${v.usuario_id}".toByteArray()
                    ).toString()
                )
            }
            if (vendedores.isEmpty()) {
                Log.e(
                    "PendingLocalSalesWorker",
                    "No se resolvieron vendedores para userEmail $userEmail"
                )
                logger.error(
                    module = "SALES_WORKER",
                    action = "NO_VENDEDORES",
                    message = "No se pudieron resolver los vendedores de la camioneta",
                    data = mapOf("saleId" to saleId, "userEmail" to userEmail)
                )
                return Result.failure()
            }

            val products = saleProductStore.getProductsForSale(saleId)
            val combos = comboDataSource.getCombosForSale(saleId)

            // GUARD money-path: el backend v2 (POST /v2/ventas) exige >=1
            // producto (CrearVentaBody.Productos minItems:1 + dominio
            // ErrVentaProductosVacios). Una venta sin renglones en Microsip es
            // pérdida/inconsistencia de inventario. Los datasources ya NO tragan
            // errores del DAO: un fallo real se propaga y cae en el catch de
            // abajo (Result.retry). Aquí cazamos el caso de datos genuinamente
            // sin productos -> fallo permanente, reintentar no lo arreglaría.
            // Los combos SÍ pueden ir vacíos (una venta puede no llevar combos).
            if (products.isEmpty()) {
                Log.e("PendingLocalSalesWorker", "Venta sin productos: $saleId")
                logger.error(
                    module = "SALES_WORKER",
                    action = "NO_PRODUCTS",
                    message = "La venta no tiene productos; no se sube a Microsip",
                    data = mapOf("saleId" to saleId, "comboCount" to combos.size)
                )
                return Result.failure()
            }

            // Persist stable SERVER_UUIDs before building the body so retries
            // use the same UUIDs (idempotency).
            for (p in products) {
                if (p.SERVER_UUID == null) {
                    saleProductStore.updateServerUuid(
                        p.LOCAL_SALE_ID,
                        p.ARTICULO_ID,
                        UUID.randomUUID().toString()
                    )
                }
            }
            for (c in combos) {
                if (c.SERVER_UUID == null) {
                    comboDataSource.updateServerUuid(
                        c.COMBO_ID,
                        c.LOCAL_SALE_ID,
                        UUID.randomUUID().toString()
                    )
                }
            }
            for (img in images) {
                if (img.SERVER_UUID == null) {
                    localSaleStore.updateImageServerUuid(
                        img.LOCAL_SALE_IMAGE_ID,
                        UUID.randomUUID().toString()
                    )
                }
            }

            // Re-read after persisting so UUIDs are populated.
            val productsWithUuids = saleProductStore.getProductsForSale(saleId)
            val combosWithUuids = comboDataSource.getCombosForSale(saleId)

            val body = with(mappers) {
                sale.toV2VentaBody(
                    products = productsWithUuids,
                    combos = combosWithUuids,
                    vendedores = vendedores,
                    camionetaId = camionetaId
                )
            }

            val jsonData = Gson().toJson(body)
            val datosRequestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())

            val imageParts = mutableListOf<MultipartBody.Part>()
            images.forEach { image ->
                val file = File(image.IMAGE_URI)
                if (file.exists()) {
                    val mimeType = when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        "webp" -> "image/webp"
                        else -> "image/jpeg"
                    }
                    val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    imageParts.add(
                        MultipartBody.Part.createFormData("imagen", file.name, requestFile)
                    )
                } else {
                    Log.w("PendingLocalSalesWorker", "Imagen no encontrada: ${image.IMAGE_URI}")
                }
            }

            // Idempotency-Key defaults to the saleId but can be rotated by
            // edit-and-retry so a corrected body avoids cache mismatch.
            val idempotencyKey = uploadFailureRepository.currentIdempotencyKey(
                saleId = saleId,
                defaultKey = saleId
            )

            // El POST va envuelto en el latido: mientras sube, el worker
            // renueva su propio arrendamiento. Ver `conLatidoDelArrendamiento`.
            val response = conLatidoDelArrendamiento(saleId, claimId) {
                ventasApi.crearVenta(
                    idempotencyKey = idempotencyKey,
                    datos = datosRequestBody,
                    imagen = imageParts
                )
            }

            localSaleStore.markSentAndCloseEdit(saleId, revisionAlReclamar)
            // Clear any prior upload-failure tracking so the UI doesn't keep
            // showing a stale error after a successful retry.
            uploadFailureRepository.clearFailure(saleId)

            logger.info(
                module = "SALES_WORKER",
                action = "UPLOAD_SUCCESS",
                message = "Venta enviada exitosamente al backend v2",
                data = mapOf(
                    "saleId" to saleId,
                    "serverVentaId" to response.id,
                    "situacion" to response.situacion,
                    "attemptCount" to runAttemptCount,
                    "imageCount" to imageParts.size,
                    "comboCount" to combos.size,
                    "productCount" to products.size
                )
            )

            Result.success()
        } catch (e: HttpException) {
            val errBody = try {
                e.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            val parsed = parseProblemDetails(errBody)
            Log.e(
                "PendingLocalSalesWorker",
                "Error HTTP ${e.code()} al enviar venta $saleId  body=$errBody",
                e
            )
            logger.error(
                module = "SALES_WORKER",
                action = "HTTP_ERROR",
                message = "Error HTTP ${e.code()} al enviar venta: $errBody",
                error = e,
                data = mapOf(
                    "saleId" to saleId,
                    "attemptCount" to runAttemptCount,
                    "responseBody" to (errBody ?: "")
                )
            )

            // Reconcile via GET: la venta puede haber sido creada server-side por
            // replay-with admin (failed_intents), por una corrida anterior cuyo 2xx
            // no nos llegó, o por algún otro flujo asíncrono. Antes de gastar otro
            // intento (o rendirnos en un 4xx permanente), verificamos.
            val existeEnServer: Boolean? = try {
                val existing = ventasApi.obtenerVenta(saleId)
                Log.i(
                    "PendingLocalSalesWorker",
                    "Venta $saleId encontrada en servidor (situacion=${existing.situacion})"
                )
                true
            } catch (verifyErr: HttpException) {
                if (verifyErr.code() == 404) false else null
            } catch (_: Exception) {
                null
            }

            if (existeEnServer == true) {
                localSaleStore.markSentAndCloseEdit(saleId, revisionAlReclamar)
                uploadFailureRepository.clearFailure(saleId)
                logger.info(
                    module = "SALES_WORKER",
                    action = "RECONCILED_VIA_GET",
                    message = "Venta ya existía server-side; reconciliada sin reintentar",
                    data = mapOf(
                        "saleId" to saleId,
                        "originalHttpCode" to e.code(),
                        "attemptCount" to runAttemptCount
                    )
                )
                Result.success()
            } else {
                // Política única de entrega garantizada (`:core:upload`). La
                // custodia la prueba la cabecera `X-Intent-Captured`, no el
                // código HTTP: sin ella nadie tiene la venta y se reintenta.
                // Ver docs/module-standards/ENTREGA_GARANTIZADA.md.
                val captureConfirmed =
                    !e.response()?.headers()?.get(HEADER_INTENT_CAPTURED).isNullOrBlank()
                val classification = when (
                    classifyUpload(e.code(), captureConfirmed = captureConfirmed)
                ) {
                    // Resguardado server-side: la oficina lo corrige, el
                    // teléfono deja de reintentar.
                    UploadDecision.RELEASE -> UploadFailureClassification.PERMANENT
                    UploadDecision.RETRY -> UploadFailureClassification.TRANSIENT
                }
                uploadFailureRepository.recordFailure(
                    saleId = saleId,
                    failure = UploadFailure(
                        httpCode = e.code(),
                        errorCode = parsed.code,
                        errorMessage = parsed.detail,
                        classification = classification,
                        atEpochMillis = nowEpochMillis()
                    )
                )

                if (classification == UploadFailureClassification.PERMANENT) {
                    logger.info(
                        module = "SALES_WORKER",
                        action = "PERMANENT_FAILURE",
                        message = "Venta rechazada con error permanente; no se reintentará",
                        data = mapOf(
                            "saleId" to saleId,
                            "httpCode" to e.code(),
                            "errorCode" to (parsed.code ?: ""),
                            "errorMessage" to (parsed.detail ?: "")
                        )
                    )
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        } catch (e: IOException) {
            Log.w(
                "PendingLocalSalesWorker",
                "Error de red al enviar venta $saleId, reintentando",
                e
            )
            logger.error(
                module = "SALES_WORKER",
                action = "NETWORK_ERROR",
                message = "Error de red al enviar venta: ${e.message}",
                error = e,
                data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
            )
            // Network failures are transient by definition. Persist for UI
            // visibility but keep retrying.
            uploadFailureRepository.recordFailure(
                saleId = saleId,
                failure = UploadFailure(
                    httpCode = 0,
                    errorCode = "network_error",
                    errorMessage = e.message ?: "error de red",
                    classification = UploadFailureClassification.TRANSIENT,
                    atEpochMillis = nowEpochMillis()
                )
            )
            Result.retry()
        } catch (e: Exception) {
            Log.e("PendingLocalSalesWorker", "Error al enviar venta local $saleId", e)
            logger.error(
                module = "SALES_WORKER",
                action = "UPLOAD_ERROR",
                message = "Error al enviar venta: ${e.message}",
                error = e,
                data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
            )
            Result.retry()
        } finally {
            // Salga por donde salga, el candado de subida se suelta: una
            // venta que no se pudo subir tiene que quedar corregible YA, sin
            // esperar a que venza el arrendamiento. En el camino feliz esto
            // es un no-op — `markSentAndCloseEdit` ya cerró el candado, y
            // `releaseClaim` sólo toca la fila si `CLAIM_ID` sigue siendo el
            // nuestro; igual de no-op si el arrendamiento venció y el editor
            // se llevó la fila (ahí el `CLAIM_ID` es otro y no se le pisa).
            //
            // `NonCancellable` porque si WorkManager detiene al worker, este
            // `finally` corre en un contexto ya cancelado y la suspensión de
            // Room lanzaría antes de escribir, dejando el candado puesto
            // hasta que venza. Soltarlo es más barato que 180 s de venta
            // retenida.
            withContext(NonCancellable) {
                localSaleStore.releaseClaim(saleId, claimId)
            }
        }
    }

    /**
     * Qué devolver cuando `claimForUpload` dice que no. Tres razones, tres
     * resultados distintos — no todas son "reintenta":
     *
     * - la fila NO existe → `failure` (mismo camino que antes del candado);
     * - la venta ya está `ENVIADO = 1` → `success`: el servidor ya la tiene,
     *   no hay nada que subir. Reintentar aquí sería un bucle eterno sobre
     *   una venta terminada;
     * - hay un candado VIVO (el dueño corrigiendo, u otra subida en vuelo) →
     *   `retry` **sin tocar la red**. `retry` y no `failure`: WorkManager
     *   conserva el trabajo y su backoff, la venta nunca se suelta.
     */
    private suspend fun resultadoSinCandado(saleId: String, userEmail: String): Result {
        val fila = localSaleStore.getSaleById(saleId)
            ?: return ventaNoEncontrada(saleId, userEmail)

        if (fila.ENVIADO) {
            logger.info(
                module = "SALES_WORKER",
                action = "ALREADY_SENT",
                message = "La venta ya estaba enviada; no hay nada que subir",
                data = mapOf("saleId" to saleId, "userEmail" to userEmail)
            )
            return Result.success()
        }

        Log.i("PendingLocalSalesWorker", "Venta $saleId reclamada por otro; se reintenta luego")
        logger.info(
            module = "SALES_WORKER",
            action = "CLAIM_BUSY",
            message = "La venta tiene un candado vigente; la subida se frena sin tocar la red",
            data = mapOf(
                "saleId" to saleId,
                "userEmail" to userEmail,
                "claimKind" to (fila.CLAIM_KIND ?: "")
            )
        )
        return Result.retry()
    }

    private fun ventaNoEncontrada(saleId: String, userEmail: String): Result =
        Result.failure().also {
            Log.e("PendingLocalSalesWorker", "Venta local no encontrada: $saleId")
            logger.error(
                module = "SALES_WORKER",
                action = "SALE_NOT_FOUND",
                message = "Venta no encontrada en base de datos local",
                data = mapOf("saleId" to saleId, "userEmail" to userEmail)
            )
        }

    /**
     * Corre [subida] con un LATIDO en paralelo que renueva el arrendamiento
     * del candado de subida cada [latidoDeSubidaMs].
     *
     * Por qué hace falta: el arrendamiento son 180 s, pero el cliente HTTP no
     * fija `callTimeout` ni `writeTimeout` — los dos que sí fija (`connect` y
     * `read`) miden INACTIVIDAD entre bytes, no duración total. Una subida
     * con fotos por una red lenta **pero que avanza** puede durar 340 s sin
     * que salte nada: sin latido el arrendamiento vence con el `POST` en
     * vuelo, el editor toma la fila y el 2xx llega tarde con el cuerpo viejo.
     * No se pone un `callTimeout` para acotarla (decisión del dueño): un tope
     * total cambiaría una carrera por una venta que NUNCA llega.
     *
     * El latido nunca re-reclama: si `renewUploadClaim` devuelve 0 el candado
     * ya no es nuestro y el latido se detiene — retomarlo le robaría la fila
     * al editor. El caso que queda entonces (2xx tardío sobre una fila ya
     * corregida) lo marca `markSentAndCloseEdit` con `CORRECCION_NO_ENVIADA`.
     *
     * `coroutineScope` + `cancel()` en `finally`: al terminar la subida —bien
     * o mal— el latido se cancela y el `coroutineScope` espera a que muera,
     * así que no puede sobrevivir ninguna corrutina huérfana latiendo sobre
     * una venta que ya terminó.
     */
    private suspend fun <T> conLatidoDelArrendamiento(
        saleId: String,
        claimId: String,
        subida: suspend () -> T
    ): T = coroutineScope {
        val latido = if (latidoDeSubidaMs > 0) {
            launch {
                while (true) {
                    delay(latidoDeSubidaMs)
                    val renovadas =
                        renovarArrendamientoDeSubida(saleId, claimId, nowEpochMillis())
                    if (renovadas == 0) {
                        Log.w(
                            "PendingLocalSalesWorker",
                            "El candado de subida de $saleId ya no es nuestro; se deja de latir"
                        )
                        break
                    }
                }
            }
        } else {
            null
        }

        try {
            subida()
        } finally {
            latido?.cancel()
        }
    }

    /** Parsed Problem-Details fragment from an RFC 7807-style error response. */
    private data class ProblemDetails(val code: String?, val detail: String?)

    /**
     * Best-effort parse of an `application/problem+json` body. Returns
     * (null, null) if the body is missing, malformed, or doesn't carry
     * the expected fields. Never throws — diagnostic parsing must not
     * upstage the original HTTP error.
     */
    private fun parseProblemDetails(body: String?): ProblemDetails {
        if (body.isNullOrBlank()) return ProblemDetails(null, null)
        return try {
            val obj = JsonParser.parseString(body).asJsonObject
            ProblemDetails(
                code = obj["code"]?.takeUnless { it.isJsonNull }?.asString,
                detail = obj["detail"]?.takeUnless { it.isJsonNull }?.asString
                    ?: obj["message"]?.takeUnless { it.isJsonNull }?.asString
            )
        } catch (_: Exception) {
            ProblemDetails(null, null)
        }
    }
}
