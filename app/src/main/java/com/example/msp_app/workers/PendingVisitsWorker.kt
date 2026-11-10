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
import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.core.common.visitas.envio.Existencia
import com.example.msp_app.core.common.visitas.envio.ResultadoGet
import com.example.msp_app.core.common.visitas.envio.SalidaDeEnvio
import com.example.msp_app.core.common.visitas.envio.VisitaEnServidor
import com.example.msp_app.core.common.visitas.envio.compromisoProbado
import com.example.msp_app.core.common.visitas.envio.existenciaCompleta
import com.example.msp_app.core.common.visitas.envio.salidaTras2xx
import com.example.msp_app.core.common.visitas.envio.salidaTrasError
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.ubicacion.PlazoDeEnvioDao
import com.example.msp_app.core.database.dao.visit.VisitDao
import com.example.msp_app.core.database.dao.visit.VisitImageDao
import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.database.entities.VisitImageEntity
import com.example.msp_app.core.diagnosticoenvio.EnvioDiagnostico
import com.example.msp_app.core.network.diagnostico.ClasificadorDeFalloDeEnvio
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import com.example.msp_app.core.network.diagnostico.FalloDeEnvio
import com.example.msp_app.core.ubicacion.UbicacionAnticipada
import com.example.msp_app.core.upload.HEADER_INTENT_CAPTURED
import com.example.msp_app.core.visitas.capacidades.Capacidades
import com.example.msp_app.core.visitas.capacidades.CapacidadesDeVisitas
import com.example.msp_app.core.visitas.envio.FirestoreModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.api.services.visits.compromiso
import com.example.msp_app.data.api.services.visits.compromisoLocal
import com.example.msp_app.data.api.services.visits.toCrearVisitaBody
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.toDomain
import com.example.msp_app.data.visitas.PartesDeComprobantesDeVisita
import com.example.msp_app.data.visitas.partesDeComprobantesDeVisita
import com.example.msp_app.features.visit.upload.domain.VisitUploadClassifier
import com.example.msp_app.features.visit.upload.domain.VisitUploadDecision
import com.example.msp_app.workmanager.enqueueUbicacionTardiaDeVisita
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Uploads one durably-stored pending visita to the backend and, on confirmation
 * that the server holds it, flips `GUARDADO_EN_MICROSIP` so the retry cohort
 * stops re-enqueuing it.
 *
 * The v2 path targets msp-api's `POST /v2/visitas` (idempotent by `id`). Its
 * robustness rule: a visita is marked "done" ONLY when the server is known to
 * hold it — a 2xx, or a 4xx which the cobranza failed-intent capture
 * middleware guarantees is persisted server-side for desk correction. A
 * network failure is never marked done, so a device that alone holds a
 * visita keeps retrying instead of dropping it. Idempotency by `id` makes a
 * resend safe (a duplicate resolves to the same stored visita, 2xx).
 *
 * ## The comprobantes ride in the SAME request (Task 23)
 *
 * When the visita carries photos the upload switches to the multipart shape of
 * `POST /v2/visitas` and the photos travel inside the very same request as the
 * visita — atomic server-side, no staging bucket, no orphan blobs. `SUBIDA_EN`
 * is stamped only on the images that actually travelled, and only after the
 * server accepted them. A failure to READ them is never treated as "there are
 * none": it retries without uploading, so the visita's id is not burned without
 * its evidence.
 *
 * The legacy path is preserved unchanged for prod until a prod Go host exists;
 * [useV2] (from `BuildConfig.VISITAS_USE_V2`) selects between them. There is no
 * dual send — two backends would risk a double insert.
 *
 * ## Envío v3 (plan visitas P1, Tarea 11; cierra E-VIS-001 y H1b)
 *
 * Con `useV2` y el kill-switch `config/visitas_envio` en `v3` (el valor por
 * omisión, [FirestoreModoEnvioVisitas]), la visita se suelta **solo con prueba
 * del servidor** (spec visitas §2.2-1) y sin tope de reintentos (E-APP-071):
 *  - «la apliqué»: 2xx con el eco del mismo id —y, si llevaba fotos, un
 *    `GET /v2/visitas/{id}` con existencia completa (opción A del dueño,
 *    2026-10-17; E-VIS-022)—, o existencia completa tras un error →
 *    `CONFIRMADA_EN`. Las fotos locales se estampan y se borran solo aquí.
 *  - «la tengo guardada»: un error con `X-Intent-Captured` que la tabla
 *    compartida `classifyUpload` suelta (vía `salidaTrasError`; E-VIS-020) →
 *    `RESGUARDADA_EN`. Las fotos locales no se tocan.
 *  - todo lo demás: `Result.retry()`.
 * Las marcas se escriben dentro de `NonCancellable`: una vez decidida la
 * salida, una cancelación de WorkManager no deja la marca a medias. Con el
 * kill-switch en `v2_actual` corre [uploadV2] intacto (el modo de reversa).
 *
 * ## Contrato de visitas 3: el compromiso (plan visitas P2, Tarea 15)
 *
 * La promesa y la cita viajan con la visita SOLO si [capacidades] dice
 * `contrato >= 3 && compromisos` (E-APP-097); si no, el cuerpo es el de
 * siempre y el compromiso se queda en Room con `COMPROMISO_CONFIRMADO_EN` nulo,
 * por rellenar cuando el servidor lo acepte. La prueba del compromiso es
 * aparte de la de la visita (E-APP-098):
 *  - envío normal: la visita base se suelta como antes; el compromiso se marca
 *    confirmado solo si la respuesta (o el GET) trae cada bloque local igual.
 *    Tras un error, la existencia completa exige además los bloques iguales
 *    (spec §4.2-3): "existencia sin compromiso no suelta".
 *  - relleno ([rellenarCompromiso]): la visita ya está confirmada y su
 *    compromiso no; se reenvía con el mismo id y solo se escriben las marcas
 *    del compromiso. `CONFIRMADA_EN` no se toca (E-VIS-028).
 *  - un 422 con custodia (p. ej. `visita_compromiso_distinto`, E-VIS-047) con la
 *    visita base completa en el servidor → base confirmada y compromiso
 *    resguardado; la visita base nunca se desconfirma.
 *
 * The injected seams (defaulted to production) let unit tests drive the worker
 * without WorkManager, Firebase, or a real network.
 */
class PendingVisitsWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    @VisibleForTesting
    internal val visitsStore: VisitsLocalDataSource = VisitsLocalDataSource(appContext),
    @VisibleForTesting
    internal val v2Api: V2VisitsApi = V2ApiProvider.create(V2VisitsApi::class.java),
    @VisibleForTesting
    internal val legacyApi: VisitsApi = ApiProvider.create(VisitsApi::class.java),
    @VisibleForTesting
    internal val useV2: Boolean = BuildConfig.VISITAS_USE_V2,
    @VisibleForTesting
    internal val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    @VisibleForTesting
    internal val imagenes: VisitImageDao =
        AppDatabase.getInstance(appContext).visitImageDao(),
    @VisibleForTesting
    internal val clock: AppClock = AppClock.System,
    /**
     * Solo diagnóstico (E-APP-065): anota el motivo de cada salida. No bloquea
     * y nunca lanza hacia aquí; ver [anotar].
     */
    @VisibleForTesting
    internal val diagnostico: EnvioDiagnosticoSink = EnvioDiagnostico.sink,
    /** Kill-switch `config/visitas_envio` (Tarea 10); se lee en cada corrida. */
    @VisibleForTesting
    internal val modo: () -> ModoEnvioVisitas = { FirestoreModoEnvioVisitas.actual },
    /** Únicos escritores de `CONFIRMADA_EN`/`RESGUARDADA_EN` (Tarea 8). */
    @VisibleForTesting
    internal val visitDao: VisitDao = AppDatabase.getInstance(appContext).visitDao(),
    /**
     * La capacidad negociada (E-APP-097), leída en cada corrida. Solo la lee:
     * la refresca `VisitsReconcileWorker`.
     */
    @VisibleForTesting
    internal val capacidades: () -> Capacidades = { CapacidadesDeVisitas.de(appContext).actual() },
    /**
     * E-APP-129: encola la subida de la ubicación tardía ([UbicacionTardiaDeVisitaWorker])
     * cuando una visita sale en 0,0. Por defecto, el encolado real.
     */
    @VisibleForTesting
    internal val encolarUbicacion: (String) -> Unit =
        { id -> enqueueUbicacionTardiaDeVisita(appContext, id) },
    /** E-APP-148: el plazo de retencion del envio (`plazo_de_envio`). Sin fila no se espera. */
    @VisibleForTesting
    internal val plazos: PlazoDeEnvioDao = AppDatabase.getInstance(appContext).plazoDeEnvioDao(),
    /** E-APP-148: la espera de la ubicacion, con reloj de pared y reloj monotono inyectables. */
    @VisibleForTesting
    internal val espera: EsperaDeEnvio = EsperaDeEnvio(
        reloj = clock,
        monotono = RelojMonotono { android.os.SystemClock.elapsedRealtimeNanos() }
    ),
    /** E-APP-148: la toma ya informo permiso negado o GPS apagado, asi que no espera. */
    @VisibleForTesting
    internal val tomaNoVaALlegar: () -> Boolean = { UbicacionAnticipada.noVaALlegar(appContext) }
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val id = inputData.getString("visit_id")
            ?: return Result.failure().also {
                anotar(SIN_VISIT_ID, FALLO_SIN_VISIT_ID)
                Log.e(TAG, "No se proporcionó visit_id")
            }

        val visit = visitsStore.getVisitById(id)
            ?: return Result.failure().also {
                anotar(id, FALLO_NO_ENCONTRADA)
                Log.e(TAG, "Visita no encontrada: $id")
            }

        // E-APP-148: el envio se RETIENE hasta tener ubicacion o 30 s desde que se guardo. Va antes de
        // la bifurcacion de modos (v2 actual, v3, legado) para que todos se comporten igual, y antes
        // de armar el cuerpo.
        val aEnviar = esperarLaUbicacion(visit)

        if (!useV2 || modo() == ModoEnvioVisitas.V2_ACTUAL) {
            // E-APP-154: v2 actual y legado no tienen la guarda de `enviarV3`: una visita con
            // `GUARDADO_EN_MICROSIP != 0` (la entrego otro camino mientras esperaba, o ya estaba
            // entregada al arrancar) se mandaba otra vez. Ningun encolador de estos modos toma filas
            // entregadas (solo `GUARDADO = 0`), y el relleno y el barrido de fotos son de v3.
            if (aEnviar.GUARDADO_EN_MICROSIP != 0) {
                anotar(aEnviar.ID, null)
                Log.i(TAG, "Visita ${aEnviar.ID} ya entregada; no se vuelve a mandar")
                return Result.success()
            }
            return if (useV2) uploadV2(aEnviar) else uploadLegacy(aEnviar)
        }
        return uploadV3(aEnviar)
    }

    /**
     * La retencion de E-APP-148 (K6 de la compuerta ANTES c2), la misma que la del pago: SIN efectos,
     * solo lee Room y duerme; el limite lo da el reloj monotono; propaga la cancelacion; sin fila de
     * plazo no se retiene. Las N visitas de una captura en varias cuentas comparten la hora de
     * guardado, asi que cada worker espera hasta el mismo limite (30 s desde el guardado mas antiguo
     * del lote) y la ubicacion que llega se escribe en todas a la vez.
     *
     * Una visita ya confirmada o resguardada no manda nada: no se espera por ella. Devuelve la visita
     * releida DESPUES de esperar (con la ubicacion que el tomador haya escrito: `LAT` y `LNG` juntas).
     */
    @Suppress("TooGenericExceptionCaught") // leer el plazo nunca debe frenar la visita.
    private suspend fun esperarLaUbicacion(visit: VisitEntity): VisitEntity {
        if (visit.CONFIRMADA_EN != null || visit.RESGUARDADA_EN != null) return visit
        if (tieneUbicacion(visit)) return visit
        val plazo = try {
            plazos.obtener(visit.ID)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (e: Exception) {
            Log.w(TAG, "Visita ${visit.ID}: no se pudo leer el plazo; se envia sin retener", e)
            null
        } ?: return visit
        val salida = espera.esperar(
            guardadoEpochMs = plazo.guardadoEnEpochMs,
            plazoMs = plazo.plazoMs,
            hayUbicacion = { tieneUbicacion(visitsStore.getVisitById(visit.ID)) },
            noVaALlegar = tomaNoVaALlegar
        )
        Log.i(TAG, "Visita ${visit.ID}: espera de ubicacion termino en $salida")
        if (salida == SalidaDeLaEspera.SIN_ESPERA) return visit
        return visitsStore.getVisitById(visit.ID)
    }

    /** El par lat/lng no es 0,0. Nunca una sola de las dos. */
    private fun tieneUbicacion(visit: VisitEntity): Boolean = visit.LAT != 0.0 || visit.LNG != 0.0

    // ─── envío v3 ──────────────────────────────────────────────────────────
    //
    // Cada `return` de este camino anota UNA vez su motivo (E-APP-065), con las
    // mismas funciones que tragan todo; una anotación nunca cambia la salida
    // (`PendingVisitsWorkerDiagnosticoTest`).

    /**
     * El envío v3. `maxAttempts` no se usa: no hay tope (spec §4.2-4). Lo
     * inesperado reintenta; la cancelación se propaga.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun uploadV3(visit: VisitEntity): Result = try {
        enviarV3(visit)
    } catch (e: CancellationException) {
        anotarExcepcion(visit.ID, e)
        throw e
    } catch (e: IOException) {
        anotarExcepcion(visit.ID, e)
        Log.w(TAG, "REINTENTA red: visita ${visit.ID}", e)
        Result.retry()
    } catch (e: Exception) {
        anotarExcepcion(visit.ID, e)
        Log.e(TAG, "REINTENTA error_inesperado: visita ${visit.ID}", e)
        Result.retry()
    }

    private suspend fun enviarV3(visit: VisitEntity): Result {
        val conCompromisos = capacidades().mandaCompromisos()
        val compromiso = visit.compromisoLocal()
        // Spec §4.2-1: lo ya probado no se vuelve a mandar. Con contrato 3, una
        // visita confirmada cuyo compromiso no tiene prueba ni resguardo es
        // relleno (spec §5.2.5): solo el compromiso viaja de nuevo.
        if (visit.CONFIRMADA_EN != null || visit.RESGUARDADA_EN != null) {
            if (visit.CONFIRMADA_EN != null && conCompromisos && compromiso != null &&
                visit.COMPROMISO_CONFIRMADO_EN == null && visit.COMPROMISO_RESGUARDADO_EN == null
            ) {
                return rellenarCompromiso(visit, compromiso)
            }
            anotar(visit.ID, null)
            Log.i(TAG, "Visita ${visit.ID} ya confirmada o resguardada; no se vuelve a mandar")
            return Result.success()
        }
        // El compromiso que viaja en ESTE envío: solo con contrato 3 (E-APP-097).
        val enviado = compromiso.takeIf { conCompromisos }
        // Un fallo de LECTURA no se traga (ver `comprobantesDe`): no se manda nada.
        val pendientes = fotosPendientesDe(visit)
            ?: return Result.retry().also {
                anotar(visit.ID, FALLO_COMPROBANTES)
                Log.w(TAG, "REINTENTA comprobantes_ilegibles: visita ${visit.ID}")
            }
        val comprobantes = partesDeComprobantesDeVisita(pendientes)
        val respuesta = try {
            enviar(visit, comprobantes, conCompromisos)
        } catch (e: HttpException) {
            return trasError(
                visit,
                pendientes,
                e.code(),
                custodiaDe(e),
                enviado
            ) { anotarHttp(visit.ID, e) }
        }
        return tras2xx(visit, comprobantes, pendientes, respuesta, enviado)
    }

    private fun custodiaDe(e: HttpException): Boolean =
        !e.response()?.headers()?.get(HEADER_INTENT_CAPTURED).isNullOrBlank()

    // ─── relleno del compromiso (spec visitas §5.2.5, E-APP-098) ───────────

    /**
     * La visita ya está confirmada y su [compromiso] no: se reenvía con el mismo
     * id y el cuerpo de contrato 3, en JSON (las fotos ya viajaron con la visita
     * base, o las devuelve a pendiente el barrido de E-APP-079). Solo escribe
     * las marcas del compromiso.
     */
    private suspend fun rellenarCompromiso(
        visit: VisitEntity,
        compromiso: CompromisoLocal
    ): Result {
        val respuesta = try {
            v2Api.crearVisita(
                idempotencyKey = visit.ID,
                body = visit.toCrearVisitaBody(conCompromisos = true)
            )
        } catch (e: HttpException) {
            return trasErrorDelRelleno(
                visit,
                compromiso,
                e.code(),
                custodiaDe(e)
            ) { anotarHttp(visit.ID, e) }
        }
        if (respuesta.id == visit.ID && compromisoProbado(compromiso, respuesta.compromiso())) {
            return withContext(NonCancellable) {
                if (visitDao.confirmarCompromiso(visit.ID, AppTime.toWireFormat(clock.now())) > 0) {
                    anotar(visit.ID, null)
                    Log.i(TAG, "COMPROMISO_CONFIRMADO: visita ${visit.ID}")
                    Result.success()
                } else {
                    anotar(visit.ID, FALLO_COMPROMISO_SIN_MARCA)
                    Result.retry()
                }
            }
        }
        // 2xx sin el eco del id o sin los bloques iguales: decide la existencia.
        return trasErrorDelRelleno(visit, compromiso, HTTP_OK, custodia = false) {
            anotar(visit.ID, FALLO_COMPROMISO_SIN_CONFIRMAR)
        }
    }

    /**
     * Error del relleno: la existencia con el compromiso y luego la tabla
     * compartida, como [trasError], pero sin fotos y escribiendo solo las
     * marcas del compromiso. Con custodia el compromiso queda resguardado y la
     * visita base sigue confirmada.
     */
    private suspend fun trasErrorDelRelleno(
        visit: VisitEntity,
        compromiso: CompromisoLocal,
        codigo: Int,
        custodia: Boolean,
        anotarFallo: () -> Unit
    ): Result {
        val get = consultarVisitaEnServidor(v2Api, visit.ID)
        val existencia =
            existenciaCompleta(visit.ID, get.visita, emptySet(), get.codigo, compromiso)
        return when (salidaTrasError(existencia, codigo, custodia)) {
            SalidaDeEnvio.CONFIRMADA -> withContext(NonCancellable) {
                if (visitDao.confirmarCompromiso(visit.ID, AppTime.toWireFormat(clock.now())) > 0) {
                    anotar(visit.ID, null)
                    Log.i(
                        TAG,
                        "COMPROMISO_CONFIRMADO via GET: visita ${visit.ID} (HTTP original $codigo)"
                    )
                    Result.success()
                } else {
                    anotarFallo()
                    Result.retry()
                }
            }
            SalidaDeEnvio.RESGUARDADA -> withContext(NonCancellable) {
                val marcadas = visitDao.resguardarCompromiso(
                    visit.ID,
                    AppTime.toWireFormat(clock.now())
                )
                anotarFallo()
                if (marcadas > 0 || compromisoYaConfirmado(visit.ID)) {
                    Log.w(
                        TAG,
                        "COMPROMISO_RESGUARDADO: visita ${visit.ID} (HTTP $codigo, X-Intent-Captured)"
                    )
                    Result.success()
                } else {
                    Log.w(TAG, "REINTENTA compromiso sin_marca: visita ${visit.ID} (HTTP $codigo)")
                    Result.retry()
                }
            }
            SalidaDeEnvio.REINTENTA -> Result.retry().also {
                anotarFallo()
                Log.w(
                    TAG,
                    "REINTENTA relleno: visita ${visit.ID} HTTP $codigo existencia=$existencia"
                )
            }
        }
    }

    private suspend fun compromisoYaConfirmado(id: String): Boolean =
        visitDao.findVisitById(id)?.COMPROMISO_CONFIRMADO_EN != null

    /**
     * Marca el compromiso [enviado] como confirmado si [enServidor] trae cada
     * bloque igual. Sin compromiso enviado, o sin prueba, no hace nada: el
     * compromiso queda por rellenar.
     */
    private suspend fun confirmarCompromisoSiProbado(
        visit: VisitEntity,
        enviado: CompromisoLocal?,
        enServidor: CompromisoLocal?
    ) {
        if (enviado == null) return
        if (compromisoProbado(enviado, enServidor)) {
            visitDao.confirmarCompromiso(visit.ID, AppTime.toWireFormat(clock.now()))
        } else {
            Log.w(TAG, "Visita ${visit.ID} confirmada; su compromiso queda por rellenar")
        }
    }

    /**
     * 2xx. Decide [salidaTras2xx] con las fotos que VIAJARON en este envío: son
     * las que el servidor debe listar en `imagen_ids` antes de borrarlas aquí.
     * Una omitida (sin archivo) no puede llegar nunca; exigirla dejaría la
     * visita reintentando para siempre, y se queda pendiente como hoy.
     */
    private suspend fun tras2xx(
        visit: VisitEntity,
        comprobantes: PartesDeComprobantesDeVisita,
        pendientes: List<VisitImageEntity>,
        respuesta: VisitaDTO,
        enviado: CompromisoLocal?
    ): Result {
        val eco = respuesta.id
        val viajaron = comprobantes.enviadas.map { it.ID }.toSet()
        val ecoCorrecto = eco == visit.ID
        val get = if (ecoCorrecto && viajaron.isNotEmpty()) {
            consultarVisitaEnServidor(
                v2Api,
                visit.ID
            )
        } else {
            null
        }
        return when (salidaTras2xx(visit.ID, eco, viajaron, get)) {
            // Relectura y marca en el mismo bloque NonCancellable (H1b): decidida
            // la salida, una cancelación no la corta entre la prueba y la marca.
            SalidaDeEnvio.CONFIRMADA -> withContext(NonCancellable) {
                if (apareceFotoNueva(visit, pendientes)) {
                    Result.retry()
                } else {
                    confirmarConFotos(
                        visit,
                        "CONFIRMADA",
                        "fotos=${viajaron.size}",
                        conocidas = pendientes,
                        fotosAEstampar = comprobantes.enviadas,
                        anotarFallo = { anotar(visit.ID, FALLO_SIN_MARCA) },
                        trasConfirmar = {
                            confirmarCompromisoSiProbado(
                                visit,
                                enviado,
                                respuesta.compromiso()
                            )
                        }
                    )
                }
            }
            // Sin eco del mismo id: «HTTP 200 no es éxito». Decide la existencia.
            else -> if (!ecoCorrecto) {
                trasError(
                    visit,
                    pendientes,
                    HTTP_OK,
                    custodia = false,
                    compromiso = enviado
                ) { anotar(visit.ID, FALLO_SIN_ECO) }
            } else {
                anotar(visit.ID, FALLO_FOTOS_SIN_CONFIRMAR)
                Log.w(TAG, "REINTENTA fotos_sin_confirmar: visita ${visit.ID} get=${get?.codigo}")
                Result.retry()
            }
        }
    }

    /**
     * Error (o 2xx sin eco): primero la existencia completa, luego la tabla
     * compartida, ambas en [salidaTrasError]. Aquí se exigen TODAS las fotos
     * pendientes: con existencia COMPLETA el servidor tiene cada una.
     *
     * [anotarFallo] anota el motivo cuando NO se confirma (la confirmación
     * anota éxito): así cada corrida anota una sola vez.
     */
    private suspend fun trasError(
        visit: VisitEntity,
        pendientes: List<VisitImageEntity>,
        codigo: Int,
        custodia: Boolean,
        compromiso: CompromisoLocal?,
        anotarFallo: () -> Unit
    ): Result {
        val get = consultarVisitaEnServidor(v2Api, visit.ID)
        val fotos = pendientes.map { it.ID }.toSet()
        // Con compromiso enviado, la existencia exige además sus bloques iguales
        // (spec §4.2-3, E-APP-098): "existencia sin compromiso no suelta".
        val existencia = existenciaCompleta(visit.ID, get.visita, fotos, get.codigo, compromiso)
        val salida = salidaTrasError(existencia, codigo, custodia)
        // Custodia con la visita base COMPLETA en el servidor y solo el
        // compromiso distinto (E-VIS-047): la visita está en MSP_VISITAS, no
        // en la bandeja. Se confirma la base y se resguarda el compromiso.
        if (salida == SalidaDeEnvio.RESGUARDADA && compromiso != null &&
            existenciaCompleta(visit.ID, get.visita, fotos, get.codigo) == Existencia.COMPLETA
        ) {
            return withContext(NonCancellable) {
                if (apareceFotoNueva(visit, pendientes)) {
                    Result.retry()
                } else {
                    confirmarConFotos(
                        visit,
                        "CONFIRMADA_COMPROMISO_RESGUARDADO",
                        "HTTP original $codigo",
                        conocidas = pendientes,
                        fotosAEstampar = pendientes,
                        anotarFallo = anotarFallo,
                        anotarExito = anotarFallo,
                        trasConfirmar = {
                            visitDao.resguardarCompromiso(
                                visit.ID,
                                AppTime.toWireFormat(clock.now())
                            )
                        }
                    )
                }
            }
        }
        return when (salida) {
            // COMPLETA = el servidor lista CADA foto pendiente con fila y archivo
            // (E-VIS-022): se estampan y se confirma sin la retención por foto de
            // `VisitDao.confirmar`, que aquí marcaría 0 filas y atascaría la
            // visita mientras el POST siga fallando (hallazgo de la Tarea 16).
            SalidaDeEnvio.CONFIRMADA -> withContext(NonCancellable) {
                if (apareceFotoNueva(visit, pendientes)) {
                    Result.retry()
                } else {
                    confirmarConFotos(
                        visit,
                        "RECONCILED_VIA_GET",
                        "HTTP original $codigo",
                        conocidas = pendientes,
                        fotosAEstampar = pendientes,
                        anotarFallo = anotarFallo,
                        // COMPLETA con compromiso = cada bloque está igual.
                        trasConfirmar = {
                            confirmarCompromisoSiProbado(
                                visit,
                                compromiso,
                                compromiso
                            )
                        }
                    )
                }
            }
            SalidaDeEnvio.RESGUARDADA -> withContext(NonCancellable) {
                val marcadas = visitDao.resguardar(visit.ID, AppTime.toWireFormat(clock.now()))
                if (marcadas > 0 || yaConfirmada(visit.ID)) {
                    anotarFallo()
                    Log.w(TAG, "RESGUARDADA: visita ${visit.ID} (HTTP $codigo, X-Intent-Captured)")
                    Result.success()
                } else {
                    anotarFallo()
                    Log.w(TAG, "REINTENTA sin_marca: visita ${visit.ID} (HTTP $codigo)")
                    Result.retry()
                }
            }
            SalidaDeEnvio.REINTENTA -> Result.retry().also {
                anotarFallo()
                Log.w(
                    TAG,
                    "REINTENTA: visita ${visit.ID} HTTP $codigo existencia=$existencia custodia=$custodia"
                )
            }
        }
    }

    /**
     * Marca CONFIRMADA con `confirmarTrasSubidaSiNoHayFotosNuevas`: el mismo
     * UPDATE se niega si hay una foto pendiente fuera de [conocidas] (la
     * lectura inicial), cerrando el hueco que la relectura no cierra con WAL.
     * [fotosAEstampar] se estampan y se borran DESPUÉS de la marca y solo si
     * cambió la fila. Éxito solo si la marca quedó escrita (o ya estaba); con
     * 0 filas no se suelta: se anota y se reintenta.
     */
    @Suppress("LongParameterList")
    private suspend fun confirmarConFotos(
        visit: VisitEntity,
        log: String,
        detalle: String,
        conocidas: List<VisitImageEntity>,
        fotosAEstampar: List<VisitImageEntity>,
        anotarFallo: () -> Unit,
        anotarExito: () -> Unit = { anotar(visit.ID, null) },
        trasConfirmar: suspend () -> Unit = {}
    ): Result {
        val marcadas = visitDao.confirmarTrasSubidaSiNoHayFotosNuevas(
            visit.ID,
            AppTime.toWireFormat(clock.now()),
            conocidas.map { it.ID }
        )
        // Orden (simulación Go T16b): las fotos se estampan y se borran SOLO si
        // la marca condicional cambió la fila. Con 0 filas la visita sigue
        // pendiente y sus fotos conservan su archivo para reenviarse.
        if (marcadas > 0) marcarSubidas(fotosAEstampar)
        return if (marcadas > 0) {
            // El compromiso, DESPUÉS de la visita base: su marca nunca existe
            // sin la de la base (E-APP-098).
            trasConfirmar()
            anotarExito()
            Log.i(TAG, "$log: visita ${visit.ID} ($detalle)")
            Result.success()
        } else if (yaConfirmada(visit.ID)) {
            // Otro camino (p. ej. la reconciliación) la confirmó y la marca
            // condicional dio 0 filas por una foto nueva. Éxito, pero no se
            // estampa nada: la foto nueva queda pendiente con su archivo. Solo
            // diagnóstico, para que se vea antes del barrido.
            anotar(visit.ID, FALLO_CONFIRMADA_CON_FOTO_NUEVA)
            Log.w(
                TAG,
                "$log: visita ${visit.ID} ya confirmada por otro camino; hay una foto nueva sin enviar"
            )
            Result.success()
        } else {
            anotarFallo()
            Log.w(TAG, "REINTENTA sin_marca: visita ${visit.ID} ($log, $detalle)")
            Result.retry()
        }
    }

    /**
     * Relectura justo antes de confirmar. `RegistroDeVisitaAdapter.guardar`
     * confirma la Visit en su transacción y DESPUÉS inserta las fotos: un
     * worker que corra en ese hueco leyó menos fotos de las que la visita
     * tiene. Si aparece una pendiente que no estaba en [conocidas], no viajó
     * ni la listó el servidor: no se confirma (se reintenta y la próxima
     * corrida la manda). Una lectura que falla también reintenta. Anota.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun apareceFotoNueva(
        visit: VisitEntity,
        conocidas: List<VisitImageEntity>
    ): Boolean {
        val ids = conocidas.map { it.ID }.toSet()
        val nueva = try {
            imagenes.getPendientesDe(visit.ID).any { it.ID !in ids }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Visita ${visit.ID}: relectura de fotos fallida", e)
            true
        }
        if (nueva) {
            anotar(visit.ID, FALLO_FOTO_NUEVA)
            Log.w(TAG, "REINTENTA foto_nueva: visita ${visit.ID} tiene fotos que no viajaron")
        }
        return nueva
    }

    private suspend fun yaConfirmada(id: String): Boolean =
        visitDao.findVisitById(id)?.CONFIRMADA_EN != null

    /** Las filas de foto sin `SUBIDA_EN`, o `null` si la lectura falló. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun fotosPendientesDe(visit: VisitEntity): List<VisitImageEntity>? = try {
        imagenes.getPendientesDe(visit.ID)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Visita ${visit.ID}: no se pudieron leer los comprobantes", e)
        null
    }

    /**
     * v2 upload. Classifies the outcome so we only mark GUARDADO_EN_MICROSIP
     * when the server is known to hold the visita. See [VisitUploadClassifier].
     */
    private suspend fun uploadV2(visit: VisitEntity): Result {
        return try {
            // Un fallo de LECTURA no se traga: ver `comprobantesDe`.
            val comprobantes = comprobantesDe(visit)
                ?: return Result.retry().also {
                    anotar(visit.ID, FALLO_COMPROBANTES)
                    Log.w(
                        TAG,
                        "Visita ${visit.ID}: no se pudieron leer los comprobantes; " +
                            "NO se sube la visita para no quemar su id sin la evidencia"
                    )
                }
            // Modo de reversa: el cuerpo de siempre, sin compromiso.
            val response = enviar(visit, comprobantes, conCompromisos = false)
            markUploaded(comprobantes)
            markDone(visit.ID)
            anotar(visit.ID, null)
            Log.i(TAG, "Visita aplicada en v2: ${visit.ID} (server=${response.id})")
            Result.success()
        } catch (e: HttpException) {
            anotarHttp(visit.ID, e)
            when (VisitUploadClassifier.classifyHttpCode(e.code())) {
                VisitUploadDecision.DONE -> {
                    // 4xx captured server-side → el desk la corrige; el teléfono terminó.
                    markDone(visit.ID)
                    Log.w(
                        TAG,
                        "Visita ${visit.ID} rechazada (${e.code()}); capturada server-side, marcada lista"
                    )
                    Result.success()
                }

                VisitUploadDecision.RETRY -> {
                    Log.w(TAG, "Visita ${visit.ID}: HTTP ${e.code()} transitorio, reintentando")
                    Result.retry()
                }

                VisitUploadDecision.RETRY_THEN_DONE -> {
                    // runAttemptCount is 0 on the first run; +1 counts this attempt.
                    if (runAttemptCount + 1 >= maxAttempts) {
                        markDone(visit.ID)
                        Log.w(
                            TAG,
                            "Visita ${visit.ID}: 5xx tras ${runAttemptCount + 1} intentos; " +
                                "ya capturada server-side, marcada lista"
                        )
                        Result.success()
                    } else {
                        Log.w(TAG, "Visita ${visit.ID}: HTTP ${e.code()} server, reintentando")
                        Result.retry()
                    }
                }

                VisitUploadDecision.RETRY_THEN_FAIL -> {
                    // Pure-retry code (401/408/425/429) with NO server-side
                    // custody guarantee — unlike RETRY_THEN_DONE, the cap
                    // here must NEVER mark done. runAttemptCount is 0 on the
                    // first run; +1 counts this attempt.
                    if (runAttemptCount + 1 >= maxAttempts) {
                        Log.w(
                            TAG,
                            "Visita ${visit.ID}: HTTP ${e.code()} tras ${runAttemptCount + 1} " +
                                "intentos sin custodia confirmada; se detiene este job de " +
                                "WorkManager (no se marca lista, sigue pendiente para " +
                                "VisitsPendingSynchronizer)"
                        )
                        Result.failure()
                    } else {
                        Log.w(TAG, "Visita ${visit.ID}: HTTP ${e.code()} transitorio, reintentando")
                        Result.retry()
                    }
                }
            }
        } catch (e: IOException) {
            // El server no la vio: jamás marcar lista. El teléfono la conserva.
            anotarExcepcion(visit.ID, e)
            Log.w(TAG, "Visita ${visit.ID}: error de red, reintentando", e)
            Result.retry()
        } catch (e: Exception) {
            anotarExcepcion(visit.ID, e)
            Log.e(TAG, "Visita ${visit.ID}: error inesperado, reintentando", e)
            Result.retry()
        }
    }

    // ─── diagnóstico (E-APP-065) ───────────────────────────────────────────
    //
    // Las tres funciones de abajo SOLO anotan. Cada una envuelve todo en
    // `try/catch (Throwable)` y no devuelve nada, así que no hay manera de que
    // cambien el `Result` ni el orden de `markUploaded`/`markDone`: el camino de
    // decisión de arriba es idéntico con o sin ellas
    // (`PendingVisitsWorkerDiagnosticoTest`, E-APP-065).

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun anotar(visitId: String, fallo: FalloDeEnvio?) {
        try {
            diagnostico.registrar(EnvioObservado(TIPO_VISITA, visitId, fallo))
        } catch (_: Throwable) {
            // Diagnóstico: jamás cambia la decisión.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun anotarHttp(visitId: String, e: HttpException) {
        try {
            // `peek()`: el cuerpo de error ya viene en memoria (Retrofit lo
            // bufferea) y así queda intacto para cualquier lector posterior.
            val cuerpo = try {
                e.response()?.errorBody()?.source()?.peek()?.let { vistazo ->
                    vistazo.request(MAX_CUERPO_BYTES)
                    vistazo.buffer.readUtf8(minOf(vistazo.buffer.size, MAX_CUERPO_BYTES))
                }
            } catch (_: Throwable) {
                null
            }
            anotar(visitId, ClasificadorDeFalloDeEnvio.clasificar(e.code(), cuerpo, null))
        } catch (_: Throwable) {
            // Diagnóstico: jamás cambia la decisión.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun anotarExcepcion(visitId: String, e: Throwable) {
        try {
            anotar(visitId, ClasificadorDeFalloDeEnvio.clasificar(null, null, e))
        } catch (_: Throwable) {
            // Diagnóstico: jamás cambia la decisión.
        }
    }

    /**
     * Manda la visita en el formato que le toca.
     *
     * **JSON cuando no lleva fotos, multipart cuando sí.** Las dos formas llegan
     * al mismo handler, la misma validación y la misma garantía de idempotencia
     * (`CrearVisita`, `handlers.go`), así que la elección no cambia el resultado
     * — cambia el radio de daño. El JSON es el camino que ya corre en producción
     * y el que los teléfonos viejos de la flota siguen usando; mandar multipart
     * también para la visita sin fotos cambiaría el formato de cable del 100% de
     * las visitas a cambio de nada. Con esto, una visita sin foto viaja
     * exactamente como viajaba.
     *
     * Ojo con la frontera: `comprobantes.partes` vacío puede significar "no hay
     * fotos" o "las había y todas se omitieron" (archivo ausente, tipo no
     * permitido). Las dos merecen el mismo trato: la visita sube igual, porque
     * la foto no la bloquea, y las filas omitidas se quedan pendientes en vez de
     * estamparse.
     */
    private suspend fun enviar(
        visit: VisitEntity,
        comprobantes: PartesDeComprobantesDeVisita,
        conCompromisos: Boolean
    ) = if (comprobantes.partes.isEmpty()) {
        v2Api.crearVisita(idempotencyKey = visit.ID, body = visit.toCrearVisitaBody(conCompromisos))
    } else {
        val json = Gson().toJson(visit.toCrearVisitaBody(conCompromisos))
        v2Api.crearVisitaConImagenes(
            idempotencyKey = visit.ID,
            datos = json.toRequestBody("application/json".toMediaTypeOrNull()),
            imagenes = comprobantes.partes
        )
    }.also { pedirUbicacionTardiaSiSalioEnCero(visit) }

    /**
     * E-APP-129: el servidor recibió la visita con 0,0 (el cuerpo se armó de la
     * fila leída antes). Si Room ya tiene ubicación —el parche de E-APP-127 llegó
     * mientras viajaba—, el PATCH por UUID (E-VIS-114) la lleva; si no, el trabajo
     * no manda nada. Nunca cambia el desenlace del envío.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun pedirUbicacionTardiaSiSalioEnCero(visit: VisitEntity) {
        if (visit.LAT != 0.0 || visit.LNG != 0.0) return
        try {
            encolarUbicacion(visit.ID)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Visita ${visit.ID}: no se pudo encolar la ubicacion tardia", e)
        }
    }

    /**
     * Los comprobantes pendientes de [visit], listos para viajar — o `null` si
     * **la lectura falló**, que es una cosa distinta de "no hay ninguno".
     *
     * ## Por qué un fallo de lectura NO sube la visita
     *
     * Tentador: subirla sin fotos y no bloquear el trabajo de campo. Es una
     * trampa, y es la que la Task 22 tuvo que arreglar del lado del dinero. Si
     * la visita sube sin evidencia, **quema su `id`**: el reintento cae en el
     * replay idempotente del servidor —que devuelve la visita existente y no
     * vuelve a mirar las fotos que ya tenía— y, peor, el camino de éxito estampa
     * `SUBIDA_EN` y **borra el archivo local**. Un error de lectura pasajero
     * acabaría en un comprobante que nunca llegó al servidor y ya no existe en
     * el teléfono.
     *
     * La visita no se pierde: se reintenta con su misma clave, exactamente como
     * ante un `IOException`. Lo que se protege es que la visita y su evidencia
     * viajen **juntas o en otro intento**, nunca a medias.
     *
     * Ojo con la distinción, que es la mitad del arreglo: una imagen **omitida**
     * (sin archivo, o de tipo no permitido) NO es un fallo de lectura. Ahí sí se
     * sube la visita —la foto no la bloquea— y la fila se queda pendiente en vez
     * de estamparse. Confundir las dos convierte una condición benigna en un
     * bloqueo permanente.
     */
    private suspend fun comprobantesDe(visit: VisitEntity): PartesDeComprobantesDeVisita? = try {
        val pendientes = imagenes.getPendientesDe(visit.ID)
        partesDeComprobantesDeVisita(pendientes).also {
            if (it.omitidas > 0) {
                Log.w(
                    TAG,
                    "Visita ${visit.ID}: ${it.omitidas} comprobante(s) omitido(s) " +
                        "(archivo ausente o tipo no permitido)"
                )
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Visita ${visit.ID}: no se pudieron leer los comprobantes", e)
        null
    }

    /**
     * Estampa `SUBIDA_EN` **solo en las que viajaron**, y borra su archivo.
     *
     * Marcar por visita —o marcar lo que había en la base al empezar— estamparía
     * también las que se omitieron por no tener archivo, y esas tienen que
     * quedarse pendientes para que se vean. El archivo local se va porque ya
     * cumplió: el servidor tiene la foto, y en un teléfono de gama baja cada
     * comprobante retenido son cientos de KB que no vuelven.
     *
     * Best-effort de punta a punta: una entrega que ya tuvo éxito no se puede
     * tumbar por no poder escribir un timestamp.
     */
    private suspend fun markUploaded(comprobantes: PartesDeComprobantesDeVisita) =
        marcarSubidas(comprobantes.enviadas)

    /** Estampa y borra [fotos]; best-effort por foto, como [markUploaded]. */
    private suspend fun marcarSubidas(fotos: List<VisitImageEntity>) {
        if (fotos.isEmpty()) return
        val subidaEn = AppTime.toWireFormat(clock.now())
        fotos.forEach { imagen ->
            try {
                imagenes.marcarSubida(imagen.ID, subidaEn)
                File(imagen.URI).delete()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cerrar el comprobante ${imagen.ID}", e)
            }
        }
    }

    /** Legacy upload (prod). Unchanged behaviour: any error retries blindly. */
    private suspend fun uploadLegacy(visit: VisitEntity): Result {
        return try {
            Log.d(TAG, "Enviando visita (legacy): ${visit.ID}")
            legacyApi.saveVisit(visit.toDomain())
            markDone(visit.ID)
            anotar(visit.ID, null)
            Log.d(TAG, "Visita marcada como enviada (legacy): ${visit.ID}")
            Result.success()
        } catch (e: Exception) {
            if (e is HttpException) anotarHttp(visit.ID, e) else anotarExcepcion(visit.ID, e)
            Log.e(TAG, "Error al enviar visita ${visit.ID} (legacy)", e)
            Result.retry()
        }
    }

    private suspend fun markDone(visitId: String) {
        visitsStore.changeVisitStatus(visitId, true)
    }

    companion object {
        private const val TAG = "PendingVisitsWorker"

        /** Max attempts before a server-side 5xx is treated as captured/done. */
        const val DEFAULT_MAX_ATTEMPTS = 10

        private const val TIPO_VISITA = "visita"
        private const val MAX_CUERPO_BYTES = 8L * 1024L

        /** Fila de diagnóstico para el trabajo que llegó sin `visit_id` (no hay fila que nombrar). */
        private const val SIN_VISIT_ID = "sin_visit_id"
        private val FALLO_SIN_VISIT_ID = FalloDeEnvio(
            codigo = "sin_visit_id",
            mensaje = "Trabajo sin visita",
            http = ClasificadorDeFalloDeEnvio.SIN_RESPUESTA
        )
        private val FALLO_NO_ENCONTRADA = FalloDeEnvio(
            codigo = "visita_no_encontrada",
            mensaje = "Visita no encontrada",
            http = ClasificadorDeFalloDeEnvio.SIN_RESPUESTA
        )
        private val FALLO_COMPROBANTES = FalloDeEnvio(
            codigo = "comprobantes_ilegibles",
            mensaje = "No se pudieron leer las fotos",
            http = ClasificadorDeFalloDeEnvio.SIN_RESPUESTA
        )

        private const val HTTP_OK = 200

        /** v3: un 2xx sin el eco del mismo id (cuerpo vacío, HTML, otra visita). */
        private val FALLO_SIN_ECO = FalloDeEnvio(
            codigo = "visita_sin_eco",
            mensaje = "El servidor no confirmó la visita",
            http = HTTP_OK
        )

        /** v3: al releer antes de confirmar apareció una foto que no viajó. */
        private val FALLO_FOTO_NUEVA = FalloDeEnvio(
            codigo = "visita_foto_nueva",
            mensaje = "La visita tiene fotos sin enviar",
            http = ClasificadorDeFalloDeEnvio.SIN_RESPUESTA
        )

        /** v3: otro camino confirmó la visita y la marca dio 0 filas por una foto nueva. */
        private val FALLO_CONFIRMADA_CON_FOTO_NUEVA = FalloDeEnvio(
            codigo = "visita_confirmada_por_otro_camino_con_foto_nueva",
            mensaje = "Visita confirmada con una foto sin enviar",
            http = ClasificadorDeFalloDeEnvio.SIN_RESPUESTA
        )

        /** v3: se decidió confirmar pero la marca no quedó escrita (0 filas). */
        private val FALLO_SIN_MARCA = FalloDeEnvio(
            codigo = "visita_sin_marca",
            mensaje = "No se pudo marcar la visita",
            http = HTTP_OK
        )

        /** Relleno (E-APP-098): el servidor no devolvió el compromiso igual al local. */
        private val FALLO_COMPROMISO_SIN_CONFIRMAR = FalloDeEnvio(
            codigo = "visita_compromiso_sin_confirmar",
            mensaje = "El servidor no confirmó la promesa o la cita",
            http = HTTP_OK
        )

        /** Relleno: se decidió confirmar el compromiso pero la marca no quedó escrita. */
        private val FALLO_COMPROMISO_SIN_MARCA = FalloDeEnvio(
            codigo = "visita_compromiso_sin_marca",
            mensaje = "No se pudo marcar la promesa o la cita",
            http = HTTP_OK
        )

        /** v3: 2xx con eco, pero el GET no prueba que el servidor tenga las fotos (E-VIS-022). */
        private val FALLO_FOTOS_SIN_CONFIRMAR = FalloDeEnvio(
            codigo = "visita_fotos_sin_confirmar",
            mensaje = "El servidor no confirmó las fotos",
            http = HTTP_OK
        )
    }
}

/**
 * `GET /v2/visitas/{id}` traducido a [ResultadoGet] (opción A del dueño,
 * 2026-10-17). `HttpException` → su código sin visita; cualquier otra
 * excepción (HTML 200, cuerpo vacío, red, Gson) → `ResultadoGet(null, null)`:
 * nunca se arma una visita a mano. Un 2xx con cuerpo legible es `200` con lo
 * que dijo el servidor; [existenciaCompleta] decide qué prueba. La cancelación
 * se propaga. Fijado por `ConsultaDeVisitaEnServidorTest`.
 */
private const val GET_OK = 200

@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend fun consultarVisitaEnServidor(api: V2VisitsApi, id: String): ResultadoGet = try {
    val dto = api.obtenerVisita(id)
    // Un compromiso todo nulo es "no lo dijo" (Gson no distingue clave ausente de
    // null): queda nulo y nunca prueba un bloque local.
    ResultadoGet(
        GET_OK,
        VisitaEnServidor(
            dto.id,
            dto.imagen_ids,
            dto.compromiso().takeUnless {
                it.vacio
            }
        )
    )
} catch (e: HttpException) {
    ResultadoGet(e.code(), null)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    ResultadoGet(null, null)
}
