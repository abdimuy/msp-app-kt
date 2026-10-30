package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.BuildConfig
import com.example.msp_app.core.common.location.captura.EsperaDeEnvio
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.location.captura.SalidaDeLaEspera
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.captura.CapturaEnCursoDao
import com.example.msp_app.core.database.dao.payment.PaymentImageDao
import com.example.msp_app.core.database.dao.ubicacion.PlazoDeEnvioDao
import com.example.msp_app.core.database.entities.CapturaEnCursoEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.ubicacion.UbicacionAnticipada
import com.example.msp_app.core.upload.ExistenceVerifier
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.upload.UploadDecision
import com.example.msp_app.core.upload.classifyUpload
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.payment.PaymentRequest
import com.example.msp_app.data.api.services.payment.PaymentsApi
import com.example.msp_app.data.api.services.payment.V2PaymentsApi
import com.example.msp_app.data.api.services.payment.toCrearPagoBody
import com.example.msp_app.data.auth.emailDeLaSesion
import com.example.msp_app.data.local.datasource.payment.PaymentsLocalDataSource
import com.example.msp_app.data.models.payment.toDomain
import com.example.msp_app.data.pagos.PartesDeComprobantes
import com.example.msp_app.data.pagos.partesDeComprobantes
import com.example.msp_app.workmanager.enqueueRefrescoDeSaldo
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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
    internal val clock: AppClock = AppClock.System,
    /**
     * Encola el refresco del saldo de una venta (`zona`, `cargo`): tras rechazar
     * su condonación y tras anotar el documento de un pago aplicado. Inyectable
     * sólo para prueba.
     */
    @VisibleForTesting
    internal val encolarRefrescoDeSaldo: (zona: Int, cargo: Int) -> Unit = { zona, cargo ->
        enqueueRefrescoDeSaldo(appContext, zona, cargo)
    },
    /** El marcador del pago (E-APP-088): ahí queda la discrepancia que frena el reenvío. */
    @VisibleForTesting
    internal val capturas: CapturaEnCursoDao =
        AppDatabase.getInstance(appContext).capturaEnCursoDao(),
    @VisibleForTesting
    internal val emailEnSesion: () -> String? = ::emailDeLaSesion,
    /**
     * E-APP-147: el plazo de retencion del envio (`plazo_de_envio`). Sin fila no se espera.
     */
    @VisibleForTesting
    internal val plazos: PlazoDeEnvioDao = AppDatabase.getInstance(appContext).plazoDeEnvioDao(),
    /** E-APP-147: la espera de la ubicacion, con reloj de pared y reloj monotono inyectables. */
    @VisibleForTesting
    internal val espera: EsperaDeEnvio = EsperaDeEnvio(
        reloj = clock,
        monotono = RelojMonotono { android.os.SystemClock.elapsedRealtimeNanos() }
    ),
    /** E-APP-147: la toma ya informo permiso negado o GPS apagado, asi que no espera. */
    @VisibleForTesting
    internal val tomaNoVaALlegar: () -> Boolean = { UbicacionAnticipada.noVaALlegar(appContext) },
    /** El aviso `captura_contenido_no_coincide` (tipo, motivo, http; sin PII). */
    @VisibleForTesting
    internal val avisarDiscrepancia: (Map<String, Any?>) -> Unit = { datos ->
        RemoteLogger.getInstance(appContext).warning(
            module = MODULO_CAPTURA,
            action = CODE_CONTENIDO_NO_COINCIDE,
            message = "un envio no coincide con la oficina; queda pendiente sin reenvio",
            data = datos
        )
    }
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

        // Ya soltada: el servidor la aplicó o la tiene resguardada. Volver a
        // mandarla no cobra dos veces (el servidor responde el replay
        // idempotente), pero ese replay BORRA el comprobante original (medido en
        // dev el 2026-09-25). WorkManager re-corre un trabajo que detuvo —por
        // red, por su límite de tiempo— aunque la captura ya haya quedado
        // soltada tras un 200, y el servicio de ubicación re-encola capturas ya
        // subidas: ninguno de los dos puede provocar un segundo POST.
        if (payment.GUARDADO_EN_MICROSIP) {
            Log.i(TAG, "Pago $id ya soltado; no se vuelve a mandar")
            return Result.success()
        }
        // E-APP-088: un envío marcado con discrepancia (otro cliente en la
        // oficina, o resguardado pero inexistente por GET) NO se vuelve a mandar:
        // queda pendiente, con aviso, hasta que la oficina lo resuelva. Ni se
        // borra ni se da por enviado.
        if (capturas.obtener(id)?.DISCREPANCIA != null) {
            Log.w(TAG, "Pago $id con discrepancia; no se vuelve a mandar")
            return Result.success()
        }

        // E-APP-147: el envio se RETIENE hasta tener ubicacion o 30 s desde que se guardo. Va antes
        // de armar el cuerpo y de `uploadV2` (que atrapa `Exception` y se tragaria una cancelacion).
        val aEnviar = esperarLaUbicacion(payment) ?: return Result.success()

        return if (useV2) uploadV2(aEnviar) else uploadLegacy(aEnviar)
    }

    /**
     * La retencion de E-APP-147 (condiciones K2..K4 de la compuerta ANTES c2). SIN efectos: solo lee
     * Room y duerme. Devuelve el pago a enviar, releido DESPUES de esperar (con la ubicacion que el
     * tomador haya escrito: `LAT` y `LNG` juntas, en un solo UPDATE), o `null` si ya no hay nada que
     * mandar (la fila desaparecio o quedo soltada mientras esperaba).
     *
     * - Sin fila en `plazo_de_envio` (pagos viejos, el barrido, un fallo al anotar) NO se retiene.
     * - La espera propaga la cancelacion: una parada de WorkManager (red perdida, limite) vuelve el
     *   trabajo a ENQUEUED SIN haber mandado nada, y la corrida nueva empieza de cero.
     * - Lo que resta se mide con el reloj monotono; el plazo de pared solo decide si se espera.
     */
    @Suppress("TooGenericExceptionCaught") // leer el plazo nunca debe frenar el dinero.
    private suspend fun esperarLaUbicacion(payment: PaymentEntity): PaymentEntity? {
        if (tieneUbicacion(payment)) return payment
        val plazo = try {
            plazos.obtener(payment.ID)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (e: Exception) {
            Log.w(TAG, "Pago ${payment.ID}: no se pudo leer el plazo; se envia sin retener", e)
            null
        } ?: return payment
        val salida = espera.esperar(
            guardadoEpochMs = plazo.guardadoEnEpochMs,
            plazoMs = plazo.plazoMs,
            hayUbicacion = { paymentsStore.getPaymentById(payment.ID)?.let(::tieneUbicacion) ?: true },
            noVaALlegar = tomaNoVaALlegar
        )
        Log.i(TAG, "Pago ${payment.ID}: espera de ubicacion termino en $salida")
        if (salida == SalidaDeLaEspera.SIN_ESPERA) return payment
        val fresco = paymentsStore.getPaymentById(payment.ID) ?: return null
        return fresco.takeUnless { it.GUARDADO_EN_MICROSIP }
    }

    /** El par lat/lng no es 0,0 (nulo cuenta como 0). Nunca una sola de las dos. */
    private fun tieneUbicacion(pago: PaymentEntity): Boolean =
        (pago.LAT ?: 0.0) != 0.0 || (pago.LNG ?: 0.0) != 0.0

    /**
     * Verificador de existencia contra `GET /v2/cobranza/pagos/{id}`.
     *
     * `true` = 200 (existe), `false` = 404 (no existe), `null` = indeterminado
     * (cualquier otro código o excepción). Un `null` NUNCA se lee como «no
     * existe»: el llamador sigue a la tabla de decisión.
     */
    @VisibleForTesting
    internal val existenceVerifier: ExistenceVerifier = ExistenceVerifier { id ->
        when (consultar(id)) {
            is EnServidor.Existe -> true
            EnServidor.NoExiste -> false
            EnServidor.NoSeSabe -> null
        }
    }

    /** `GET /v2/cobranza/pagos/{id}` con el cliente que trae (E-APP-088). */
    @Suppress("TooGenericExceptionCaught") // cualquier fallo del GET es un "no se sabe".
    private suspend fun consultar(id: String): EnServidor = try {
        EnServidor.Existe(v2Api.obtenerPago(id).cliente_id)
    } catch (verifyErr: HttpException) {
        if (verifyErr.code() == HTTP_NOT_FOUND) EnServidor.NoExiste else EnServidor.NoSeSabe
    } catch (cancelada: CancellationException) {
        throw cancelada
    } catch (_: Exception) {
        EnServidor.NoSeSabe
    }

    private sealed interface EnServidor {
        data class Existe(val clienteId: Int?) : EnServidor
        data object NoExiste : EnServidor
        data object NoSeSabe : EnServidor
    }

    /**
     * Subida v2. Sólo marca GUARDADO_EN_MICROSIP cuando el servidor confirma
     * que tiene el pago — por existencia (GET) o por custodia (cabecera).
     */
    private suspend fun uploadV2(payment: PaymentEntity): Result {
        return try {
            val json = Gson().toJson(payment.toCrearPagoBody())
            val datos = json.toRequestBody("application/json".toMediaTypeOrNull())
            // Un fallo de LECTURA no se traga: ver `comprobantesDe`.
            val comprobantes = comprobantesDe(payment)
                ?: return Result.retry().also {
                    Log.w(
                        TAG,
                        "Pago ${payment.ID}: no se pudieron leer los comprobantes; " +
                            "NO se sube el pago para no quemar su id sin la evidencia"
                    )
                }
            val response = v2Api.crearPago(
                idempotencyKey = payment.ID,
                datos = datos,
                imagenes = comprobantes.partes
            )
            // Tras el 200 nada puede perderse por una cancelación: el servidor ya
            // tiene el pago. `NonCancellable` para que WorkManager no corte la
            // sección a medias —un `markDone` perdido haría que el reintento
            // volviera a mandarla, y el replay borra el comprobante original—, y
            // la captura se SUELTA antes de anotar el documento (ZZ2).
            withContext(NonCancellable) {
                markUploaded(comprobantes)
                markDone(payment.ID)
                if (persistDoctoCcId(payment, response.docto_cc_id)) {
                    encolarRefrescoTrasAplicar(payment)
                }
            }
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
        // E-APP-088 (decisión 4 del dueño 2026-10-20): se busca por UUID. Si
        // EXISTE es nuestro y llegó, aunque la oficina lo haya editado; el único
        // seguro extra es el CLIENTE. Si NO existe, nunca se da por llegado.
        val enServidor = consultar(payment.ID)
        if (enServidor is EnServidor.Existe) {
            if (clienteNoCoincide(enServidor.clienteId, payment.CLIENTE_ID)) {
                return marcarDiscrepancia(payment, MOTIVO_CONTENIDO_NO_COINCIDE, e.code())
            }
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
                if (esCondonacionRechazadaPorSaldo(payment, e, captureConfirmed)) {
                    // E-APP-108: el 422 de saldo es TAMBIÉN la respuesta normal al
                    // reintento de una condonación que sí llegó (el servidor valida
                    // el saldo antes del replay idempotente; MEDIDO por la compuerta
                    // DESPUÉS del 2026-10-26). Sólo el 404 del GET prueba que no
                    // llegó; si el GET no contestó, se reintenta con la misma clave.
                    if (enServidor == EnServidor.NoExiste) {
                        return soltarCondonacionRechazada(payment, e.code())
                    }
                    Log.w(
                        TAG,
                        "Condonacion ${payment.ID}: 422 por saldo sin prueba de que no " +
                            "llego (GET sin respuesta); reintentando"
                    )
                    return Result.retry()
                }
                // `> 0` y no `!= 0`: -1 es la marca de condonación rechazada.
                val documento: Int = payment.DOCTO_CC_ID
                if (documento > 0) {
                    // El documento de Microsip ya anotado ES la prueba de que el
                    // servidor lo aplicó: se suelta como siempre.
                    markDone(payment.ID)
                    return Result.success()
                }
                if (enServidor == EnServidor.NoExiste) {
                    // E-APP-088: resguardado pero el GET dice que no existe. NO
                    // se da por enviado (antes: markDone); queda pendiente con
                    // aviso y sin reenvío.
                    return marcarDiscrepancia(payment, MOTIVO_NO_EXISTE_EN_SERVIDOR, e.code())
                }
                // E-APP-088: el GET no contestó. Sin prueba no se suelta: se
                // reintenta con la misma clave.
                Log.w(
                    TAG,
                    "Pago ${payment.ID} rechazado (${e.code()}) con custodia pero sin prueba " +
                        "de existencia; reintentando"
                )
                Result.retry()
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
     * El seguro extra de la decisión 4: sólo el cliente. `null` del servidor
     * (campo ausente) no es discrepancia — no hay con qué comparar.
     */
    private fun clienteNoCoincide(delServidor: Int?, local: Int): Boolean =
        delServidor != null && delServidor != local

    /**
     * Deja el pago PENDIENTE (`GUARDADO_EN_MICROSIP` sigue en 0), anota la
     * discrepancia en su marcador ("Un envío no coincide con la oficina") y
     * frena el reenvío. Sin PII: tipo y motivo.
     */
    @Suppress("TooGenericExceptionCaught") // Room puede fallar; se reintenta sin marcar nada.
    private suspend fun marcarDiscrepancia(
        payment: PaymentEntity,
        motivo: String,
        code: Int
    ): Result {
        val ahora = AppTime.toWireFormat(clock.now())
        try {
            withContext(NonCancellable) {
                capturas.capturar(
                    CapturaEnCursoEntity(
                        ID = payment.ID,
                        TIPO = tipoDe(payment),
                        LLAVE = payment.DOCTO_CC_ACR_ID.toString(),
                        USUARIO = emailEnSesion().orEmpty(),
                        ESTADO = CapturaEnCursoEntity.ESCRITA,
                        RESUMEN = "{}",
                        CREADA_EN = ahora,
                        ACTUALIZADA_EN = ahora
                    )
                )
                capturas.marcarDiscrepancia(payment.ID, motivo, ahora)
            }
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Exception) {
            Log.e(TAG, "Pago ${payment.ID}: no se pudo anotar la discrepancia; reintentando", fallo)
            return Result.retry()
        }
        try {
            avisarDiscrepancia(mapOf("tipo" to tipoDe(payment), "motivo" to motivo, "http" to code))
        } catch (_: Exception) {
            // La telemetría caída no cambia la decisión.
        }
        Log.w(TAG, "Pago ${payment.ID}: $motivo (HTTP $code); pendiente, sin reenvío")
        return Result.success()
    }

    private fun tipoDe(payment: PaymentEntity): String =
        if (payment.FORMA_COBRO_ID == Constants.CONDONACION_ID) {
            CapturaEnCursoEntity.TIPO_CONDONACION
        } else {
            CapturaEnCursoEntity.TIPO_ABONO
        }

    /**
     * ¿Es una CONDONACIÓN que el servidor rechazó por saldo y resguardó?
     *
     * Las cuatro condiciones, y por qué ninguna sobra:
     * - `FORMA_COBRO_ID` de condonación — el alcance de este arreglo. Un abono
     *   rechazado tiene el mismo "límite conocido 1" de
     *   `PaymentDao.sumImporteNoReconocidoPorElServidor`, pero no se midió que su
     *   rechazo deje el saldo igual de mal, y aquí sólo cambia lo medido.
     * - `422` — el código con el que el servidor rechazó las condonaciones de
     *   más (E-APP-029; P4 de la compuerta del 2026-09-26).
     * - `X-Intent-Captured` — el servidor la tiene resguardada: la oficina puede
     *   re-aplicarla, y si lo hace, el sync trae su fila numérica y el colapso
     *   del gemelo se lleva la local.
     * - `code = pago_saldo_insuficiente` en el `problem+json` — la validación que
     *   el servidor corre ANTES de aplicar (`ErrPagoSaldoInsuficiente`,
     *   `internal/cobranza/domain/errors.go`). Otro 422 no está medido y sigue
     *   por el camino de siempre.
     */
    private fun esCondonacionRechazadaPorSaldo(
        payment: PaymentEntity,
        e: HttpException,
        captureConfirmed: Boolean
    ): Boolean = payment.FORMA_COBRO_ID == Constants.CONDONACION_ID &&
        e.code() == HTTP_UNPROCESSABLE &&
        captureConfirmed &&
        codigoDelProblema(e) == CODIGO_SALDO_INSUFICIENTE

    /**
     * El `code` del `problem+json`, o `null` si no se puede leer (E-APP-108).
     *
     * El servidor NO lo manda en la raíz: `huma.ErrorModel` no tiene miembro
     * `code`, así que `mapAppError` lo pone en `errors[].message = "code=<código>"`
     * (`internal/cobranza/infra/cobranzahttp/auth.go:59-71`, msp-api @ab6a19d;
     * cuerpo real MEDIDO 2026-10-26). Leer sólo la raíz dejaba esta rama muerta.
     * Mismo criterio que `parseProblemJSON` del servidor: la raíz gana si viene;
     * si no, el PRIMER `errors[].message` con el prefijo EXACTO `code=` (un
     * error de validación de Huma trae `errors[]` sin prefijo y no es un código).
     */
    @Suppress("TooGenericExceptionCaught") // un cuerpo ilegible no es un rechazo por saldo
    private fun codigoDelProblema(e: HttpException): String? = try {
        e.response()?.errorBody()?.string()?.let { cuerpo ->
            val raiz = JsonParser.parseString(cuerpo).asJsonObject
            raiz.get("code")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
                ?: codigoEnDetalles(raiz.get("errors"))
        }
    } catch (_: Exception) {
        null
    }

    /** El primer `errors[].message` de la forma `code=<código>`, sin el prefijo. */
    private fun codigoEnDetalles(errores: JsonElement?): String? =
        (errores as? JsonArray)?.firstNotNullOfOrNull { detalle ->
            (detalle as? JsonObject)?.get("message")
                ?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.startsWith(PREFIJO_CODIGO_HUMA) }
                ?.removePrefix(PREFIJO_CODIGO_HUMA)
                ?.takeIf { it.isNotEmpty() }
        }

    /**
     * Suelta la condonación rechazada y la marca rechazada
     * ([PaymentsLocalDataSource.soltarCondonacionRechazada], una transacción que
     * NO toca el saldo), y encola el refresco del saldo de su venta contra el
     * servidor ([encolarRefrescoDeSaldo] → `RefrescarSaldoDeVentaWorker`), que
     * corre bajo el mutex del sync y reintenta por su cuenta sin volver a mandar
     * este pago.
     *
     * Si la transacción falla, se reintenta: la fila sigue pendiente y el
     * siguiente 422 vuelve a caer aquí. Si falla el encolado, la condonación ya
     * quedó marcada y el saldo local se queda donde lo dejó la captura (nunca
     * arriba); se reporta y se sigue.
     */
    @Suppress("TooGenericExceptionCaught") // Room/WorkManager pueden fallar de muchas formas
    private suspend fun soltarCondonacionRechazada(payment: PaymentEntity, code: Int): Result {
        val marcada = try {
            paymentsStore.soltarCondonacionRechazada(payment.ID)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Exception) {
            Log.e(TAG, "Condonacion ${payment.ID}: no se pudo soltar; reintentando", fallo)
            return Result.retry()
        }
        val cargo = payment.DOCTO_CC_ACR_ID
        try {
            encolarRefrescoDeSaldo(payment.ZONA_CLIENTE_ID, cargo)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (fallo: Exception) {
            Log.e(TAG, "Condonacion ${payment.ID}: no se pudo encolar el refresco del saldo", fallo)
        }
        Log.w(
            TAG,
            "Condonacion ${payment.ID} rechazada ($code $CODIGO_SALDO_INSUFICIENTE); " +
                "resguardada server-side, marcada=$marcada, refresco del saldo encolado"
        )
        return Result.success()
    }

    /**
     * Los comprobantes PENDIENTES del pago, ya convertidos en partes, o `null`
     * si **la lectura falló**.
     *
     * Filtra por `PAGO_ID` —el del pago— y por `SUBIDA_EN IS NULL`: una imagen
     * que el servidor ya confirmó no se vuelve a mandar.
     *
     * ## Por qué un fallo de lectura NO deja subir el pago
     *
     * Tentador: subir el pago sin fotos y no bloquear el dinero. Es una trampa,
     * y es la misma que este worker ya evita en `RECONCILED_VIA_GET`, entrando
     * por la otra puerta. Si el pago sube sin evidencia, **quema su `datos.id`**:
     * el reintento cae en el replay idempotente del servidor, que descarta los
     * blobs del segundo request, y `markUploaded` estampa `SUBIDA_EN` y borra el
     * archivo local. Un error de lectura pasajero acaba en un comprobante que
     * **nunca llegó al servidor y ya no existe en el teléfono**.
     *
     * El pago no se pierde: se reintenta con su misma clave, que es exactamente
     * lo que el worker ya hace ante un `IOException`. Lo que se protege es que
     * el pago y su evidencia viajen **juntos o en otro intento**, nunca a medias.
     *
     * Ojo con la distinción: una imagen **omitida** (sin archivo, o de tipo no
     * permitido) NO es un fallo de lectura. Ahí sí se sube el pago —la foto no
     * lo bloquea— y la fila se queda pendiente en vez de estamparse.
     */
    private suspend fun comprobantesDe(payment: PaymentEntity): PartesDeComprobantes? = try {
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
        null
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
     *
     * **Sin el mutex de escritura del sync, a propósito.** Anotar el documento
     * saca este pago de `sumImporteNoReconocidoPorElServidor`, y un saldo del
     * servidor leído ANTES o DESPUÉS de que Microsip lo aplicara queda mal
     * mientras no se re-lea: inflado si la foto era anterior (medido: 500 contra
     * 300), desinflado si la foto ya lo traía y el merge todavía lo restaba
     * (medido: 100 contra 300). Esperar el mutex aquí cerraba lo primero y abría
     * lo segundo —y lo dejaba fósil: nadie recalcula después—. Lo que cierra los
     * dos es re-leer el saldo DESPUÉS de anotar: [encolarRefrescoTrasAplicar].
     *
     * @return `true` si anotó un documento nuevo.
     */
    private suspend fun persistDoctoCcId(payment: PaymentEntity, doctoCcId: Int?): Boolean {
        if (doctoCcId == null || doctoCcId <= 0 || doctoCcId == payment.DOCTO_CC_ID) return false
        return try {
            paymentsStore.updatePaymentDoctoCcId(payment.ID, doctoCcId)
            true
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (e: Exception) {
            Log.w(TAG, "Pago ${payment.ID}: no se pudo guardar docto_cc_id", e)
            false
        }
    }

    /**
     * Encola el refresco del saldo de la venta de un pago recién aplicado
     * ([com.example.msp_app.core.sync.cobranza.RefrescoDelSaldoDeLaVenta]: bajo
     * el mutex del sync, `by-ids` + la misma fórmula de `mergeVentas`). Corre
     * después de que el documento quedó anotado, así que su suma de lo no
     * reconocido ya no incluye este pago y su foto del servidor es posterior a
     * la aplicación: corrige tanto el saldo inflado como el desinflado que pudo
     * dejar un `mergeVentas` a media página. Uno por pago subido;
     * `APPEND_OR_REPLACE` los encadena por venta.
     *
     * Total: un fallo al encolar no toca una entrega que ya tuvo éxito.
     */
    @Suppress("TooGenericExceptionCaught") // WorkManager puede fallar de varias formas
    private fun encolarRefrescoTrasAplicar(payment: PaymentEntity) {
        val cargo = payment.DOCTO_CC_ACR_ID
        try {
            encolarRefrescoDeSaldo(payment.ZONA_CLIENTE_ID, cargo)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (e: Exception) {
            Log.w(TAG, "Pago ${payment.ID}: no se pudo encolar el refresco del saldo", e)
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
        private const val HTTP_UNPROCESSABLE = 422

        private const val CODIGO_SALDO_INSUFICIENTE = "pago_saldo_insuficiente"

        /** Prefijo con el que `mapAppError` del servidor mete el código en `errors[]`. */
        private const val PREFIJO_CODIGO_HUMA = "code="

        private const val HTTP_NOT_FOUND = 404

        /** E-APP-088: resguardado (X-Intent-Captured) pero el GET por UUID da 404. */
        const val MOTIVO_NO_EXISTE_EN_SERVIDOR = "no_existe_en_servidor"
        private const val MODULO_CAPTURA = "CAPTURA"
    }
}
