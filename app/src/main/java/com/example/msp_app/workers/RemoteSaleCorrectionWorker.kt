package com.example.msp_app.workers

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases
import com.example.msp_app.core.database.dao.localsale.LocalSaleComboDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleDao
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.core.utils.Constants
import com.example.msp_app.data.api.V2ApiProvider
import com.example.msp_app.data.api.services.ventas.VentasApi
import com.example.msp_app.data.api.services.ventas.construirActualizarClienteRequest
import com.example.msp_app.data.api.services.ventas.construirActualizarHeaderRequest
import com.example.msp_app.data.api.services.ventas.construirReemplazarLineasRequest
import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
import com.example.msp_app.features.camionetaAssignment.data.repository.CamionetaAssignmentRepository
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Entrega al servidor las correcciones de ventas **ya subidas** que quedaron
 * en cola — el otro camino del outbox de ventas, gemelo de
 * [PendingLocalSalesWorker] (plan "Corregir una venta DESPUÉS de que subió,
 * mientras siga en borrador", nivel 2).
 *
 * El dueño corrige una venta que ya subió; `GuardarCorreccion` commitea la
 * corrección en Room y deja la fila con `CORRECCION_REMOTA_PENDIENTE = 1` en
 * la misma transacción. Eso funciona sin señal — este worker es quien
 * después la lleva al servidor.
 *
 * ## La corrección son TRES peticiones, no una
 *
 * La venta entera se corrige, no sólo sus productos, y el API v2 la parte en
 * tres endpoints. Van en este orden:
 *
 * 1. `PATCH /v2/ventas/{id}` — dirección, GPS, fecha, plan de crédito, día de
 *    cobranza, nota.
 * 2. `PATCH /v2/ventas/{id}/cliente` — nombre, teléfono, aval.
 * 3. `PUT /v2/ventas/{id}/lineas` — productos y combos.
 *
 * Mandar sólo la tercera —lo que este worker hacía— dejaba que el teléfono
 * dijera "Corrección guardada" mientras el teléfono y el servidor se
 * separaban en silencio: medido el 2026-09-22, un teléfono cambiado a
 * `2385550000` contra un servidor que seguía en `2385559876`.
 *
 * **La cola no se cierra hasta que las tres pasan** y, si alguna falla de
 * forma reintentable, la corrida siguiente repite **las tres desde el
 * principio**. No hay reanudación a media secuencia: las tres son reemplazos
 * totales, así que repetirlas converge, y reanudar exigiría confiar en un
 * progreso que ningún lado persiste. La secuencia vive en
 * [ejecutarSecuenciaCorreccionRemota].
 *
 * **El servidor aplicado a medias** es el caso que obliga a un terminal
 * nuevo: un 409 `venta_no_editable` en la segunda o la tercera petición llega
 * con la anterior YA aplicada, y reportarlo como "la aplicó la oficina" le
 * diría al cobrador que no entró nada. Por eso ahí se escribe
 * [CorreccionRemotaTerminal.APLICADA_PARCIAL] — ver la regla 2 de
 * [ejecutarSecuenciaCorreccionRemota].
 *
 * ## Qué copia del hermano, y por qué
 *
 * - **El candado va PRIMERO**, antes de leer una sola línea del cuerpo. Es el
 *   mismo fence: con el candado `REMOTE` puesto, el editor no puede commitear
 *   mientras los cuerpos se arman, así que la secuencia nunca sale con una
 *   mezcla de dos correcciones. Leer primero y reclamar después deja esa
 *   ventana abierta y ninguna revalidación posterior la cierra. Por lo mismo
 *   los **tres cuerpos se arman antes de la primera petición**: así las tres
 *   describen el mismo estado de la fila.
 * - **El latido** ([LocalSaleClaimLeases.REMOTE_HEARTBEAT_MS]) renueva el
 *   arrendamiento mientras la secuencia sigue en vuelo, y cubre las TRES
 *   peticiones: se lanza antes de la primera y se cancela después de la
 *   tercera. El cliente HTTP no fija `callTimeout` ni `writeTimeout`, así que
 *   tres peticiones lentas pero VIVAS pueden durar más que el arrendamiento
 *   entero.
 * - **El candado se suelta SIEMPRE**, en un `finally` con `NonCancellable`:
 *   si WorkManager detiene al worker, soltarlo es más barato que dejar la
 *   venta retenida hasta que venza el arrendamiento.
 * - **La política de reintento** es la de WorkManager por omisión
 *   (exponencial desde 30 s), igual que el hermano: no se fija
 *   `setBackoffCriteria` en el encolado.
 *
 * ## Qué NO copia
 *
 * No hay `Idempotency-Key` ni ancla de revisión posteada — ninguno de los tres
 * endpoints acepta la cabecera. Los tres son **reemplazos**: reenviarlos
 * mientras la venta siga editable es inofensivo, y un 2xx perdido se arregla
 * solo en el intento siguiente. Lo que sí hace falta —y lo aporta
 * `cerrarCorreccionRemota`— es no declarar entregada una corrección MÁS NUEVA
 * que la que viajó: por eso el cierre compara contra la `REVISION` leída al
 * reclamar.
 *
 * La clasificación de la respuesta y la secuencia viven aparte, en
 * `CorreccionRemotaClasificador.kt`, y es lo único de este worker que se
 * prueba unitariamente.
 */
class RemoteSaleCorrectionWorker @JvmOverloads constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    /** Retrofit para las tres peticiones de la corrección y el `GET` de diagnóstico. */
    @VisibleForTesting
    internal val ventasApi: VentasApi = V2ApiProvider.create(VentasApi::class.java),
    @VisibleForTesting
    internal val localSaleDao: LocalSaleDao =
        AppDatabase.getInstance(appContext).localSaleDao(),
    @VisibleForTesting
    internal val productDao: LocalSaleProductDao =
        AppDatabase.getInstance(appContext).localSaleProduct(),
    @VisibleForTesting
    internal val comboDao: LocalSaleComboDao =
        AppDatabase.getInstance(appContext).localSaleComboDao(),
    /**
     * Camioneta asignada al correo (`CAMIONETA_ASIGNADA` en Firestore), que es
     * el `almacen_origen_id` del cuerpo. `null` = el usuario no tiene
     * camioneta; ahí se usa [Constants.ALMACEN_GENERAL_ID], exactamente la
     * misma regla que `LocalSaleMappers.toV2VentaBody` aplicó cuando la venta
     * se creó. Si la resolución LANZA no se inventa un origen: la excepción
     * sube y la corrida se reintenta.
     */
    @VisibleForTesting
    internal val resolverCamioneta: suspend (userEmail: String) -> Int? = { email ->
        val repo = CamionetaAssignmentRepository()
        repo.getAllUsers().getOrElse { throw it }
            .firstOrNull { it.EMAIL == email }
            ?.CAMIONETA_ASIGNADA
    },
    @VisibleForTesting
    internal val nowEpochMillis: () -> Long = System::currentTimeMillis,
    /**
     * Período del latido. Un valor no positivo lo apaga — sólo para pruebas
     * que quieran reproducir el candado que caduca en vuelo.
     */
    @VisibleForTesting
    internal val latidoRemotoMs: Long = LocalSaleClaimLeases.REMOTE_HEARTBEAT_MS,
    @VisibleForTesting
    internal val nuevoClaimId: () -> String = { UUID.randomUUID().toString() }
) : CoroutineWorker(appContext, workerParams) {

    private val logger: RemoteLogger by lazy { RemoteLogger.getInstance(appContext) }

    override suspend fun doWork(): Result {
        val saleId = inputData.getString(CLAVE_VENTA)
            ?: return Result.failure().also {
                Log.e(TAG, "No se proporcionó $CLAVE_VENTA")
                logger.error(
                    module = MODULO,
                    action = "MISSING_SALE_ID",
                    message = "Worker iniciado sin local_sale_id"
                )
            }

        // El correo NO es opcional: de él sale el almacén de origen. Sin él
        // habría que adivinarlo, y un origen equivocado en el cuerpo es una
        // corrección que mueve inventario de otra camioneta.
        val userEmail = inputData.getString(CLAVE_CORREO)?.takeIf { it.isNotBlank() }
            ?: return Result.failure().also {
                Log.e(TAG, "No se proporcionó $CLAVE_CORREO")
                logger.error(
                    module = MODULO,
                    action = "MISSING_USER_EMAIL",
                    message = "Worker iniciado sin user_email",
                    data = mapOf("saleId" to saleId)
                )
            }

        val claimId = nuevoClaimId()
        val reclamada = localSaleDao.claimForRemote(
            saleId = saleId,
            claimId = claimId,
            now = nowEpochMillis(),
            editLeaseMs = LocalSaleClaimLeases.EDIT_LEASE_MS,
            uploadLeaseMs = LocalSaleClaimLeases.UPLOAD_LEASE_MS,
            remoteLeaseMs = LocalSaleClaimLeases.REMOTE_LEASE_MS
        ) == 1
        if (!reclamada) return resultadoSinCandado(saleId)

        return try {
            // El latido envuelve la secuencia ENTERA — las tres peticiones y el
            // armado de los cuerpos —, no sólo la primera. Ver
            // `conLatidoDelArrendamiento` para el porqué de cada parámetro.
            conLatidoDelArrendamiento(
                periodoMs = latidoRemotoMs,
                renovar = { localSaleDao.renewClaim(saleId, claimId, nowEpochMillis()) },
                alFallarElLatido = { fallo ->
                    Log.w(TAG, "Falló un latido del candado de $saleId; la corrida sigue", fallo)
                },
                alPerderElCandado = {
                    Log.w(TAG, "El candado remoto de $saleId ya no es nuestro; se deja de latir")
                }
            ) {
                entregarCorreccionReclamada(saleId, claimId, userEmail)
            }
        } finally {
            // Salga por donde salga. `NonCancellable` porque si WorkManager
            // detiene al worker este bloque corre en un contexto ya cancelado
            // y la suspensión de Room lanzaría antes de escribir, dejando el
            // candado puesto hasta que venza.
            withContext(NonCancellable) {
                localSaleDao.releaseClaim(saleId, claimId)
            }
        }
    }

    /**
     * Todo lo que pasa CON el candado tomado y el latido corriendo. Es una
     * función aparte —y no el cuerpo de la lambda del latido— por la misma
     * razón que en el hermano: acá dentro hay varios `return` de guardia y una
     * lambda no-inline no admite `return` no local.
     *
     * El candado lo suelta quien llama, en su `finally`.
     */
    private suspend fun entregarCorreccionReclamada(
        saleId: String,
        claimId: String,
        userEmail: String
    ): Result {
        // Bajo el candado y ANTES de leer el cuerpo: la `REVISION` de ESTE
        // instante es la que va a viajar, y es contra la que
        // `cerrarCorreccionRemota` decide si el dueño volvió a corregir
        // mientras la corrida seguía en vuelo.
        val snapshot = localSaleDao.getRemoteCorrectionSnapshot(saleId)
            ?: return ventaNoEncontrada(saleId)

        if (!snapshot.CORRECCION_REMOTA_PENDIENTE) {
            // Nada que entregar: el encolado es optimización, la fila manda.
            logger.info(
                module = MODULO,
                action = "NADA_PENDIENTE",
                message = "La venta no tiene corrección remota pendiente; no se manda nada",
                data = mapOf("saleId" to saleId)
            )
            return Result.success()
        }

        val revisionAlReclamar = snapshot.REVISION
        val fila = localSaleDao.getSaleById(saleId) ?: return ventaNoEncontrada(saleId)

        return try {
            val productos = productDao.getProductsForSale(saleId)
            val combos = comboDao.getCombosForSale(saleId)

            // El servidor exige `productos` con al menos un renglón
            // (`minItems:1` + `ErrVentaProductosVacios`). Mandarlo vacío es un
            // 422 garantizado, y un 422 es TERMINAL: se corta acá con la MISMA
            // marca que habría dejado ese 422, sin gastar la petición. Es el
            // único rechazo que decide el teléfono y no el servidor.
            if (productos.isEmpty()) {
                Log.e(TAG, "Corrección sin productos: $saleId")
                logger.error(
                    module = MODULO,
                    action = "CORRECCION_SIN_PRODUCTOS",
                    message = "La corrección no tiene productos; el servidor la rechazaría",
                    data = mapOf("saleId" to saleId, "comboCount" to combos.size)
                )
                marcarTerminal(saleId, fila, CorreccionRemotaTerminal.RECHAZADA_ESTADO)
                return Result.failure()
            }

            val almacenOrigenId = resolverCamioneta(userEmail) ?: Constants.ALMACEN_GENERAL_ID

            // Los TRES cuerpos se arman acá, antes de la primera petición y
            // todos desde la MISMA lectura de la fila (`fila`, `productos`,
            // `combos`). Armar el de la tercera después de la primera dejaría
            // que una corrección que entrara en medio viajara partida en dos.
            val cuerpoHeader = construirActualizarHeaderRequest(fila)
            val cuerpoCliente = construirActualizarClienteRequest(fila)
            val cuerpoLineas = construirReemplazarLineasRequest(
                productos = productos,
                combos = combos,
                almacenOrigenId = almacenOrigenId,
                almacenDestinoId = Constants.ALMACEN_GENERAL_ID
            )

            // Lo último antes de tocar la red: que el candado SIGA siendo el
            // nuestro. El latido debería haberlo mantenido vivo, pero un
            // latido puede fallar (SQLite trabado, el proceso congelado más
            // que un arrendamiento entero) y entonces el editor pudo haberse
            // llevado la fila y commiteado otra corrección — con lo que los
            // cuerpos recién armados ya no son los vigentes. Lectura barata;
            // si el candado no es nuestro, `retry` SIN tocar la red. Se
            // revalida una sola vez, antes de la PRIMERA de las tres: a mitad
            // de la secuencia ya no serviría de nada, porque lo que llevan
            // aplicado no se puede deshacer.
            val candadoVigente = localSaleDao.getRemoteCorrectionSnapshot(saleId)?.CLAIM_ID
            if (candadoVigente != claimId) {
                Log.w(TAG, "El candado de $saleId dejó de ser nuestro mientras se armaba el cuerpo")
                logger.error(
                    module = MODULO,
                    action = "CLAIM_LOST",
                    message = "El candado caducó mientras se armaba el cuerpo; no se manda nada",
                    data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
                )
                return Result.retry()
            }

            val resultado = ejecutarSecuenciaCorreccionRemota(
                listOf(
                    PeticionDeCorreccion(PasoCorreccionRemota.HEADER) {
                        ventasApi.actualizarHeader(saleId, cuerpoHeader)
                    },
                    PeticionDeCorreccion(PasoCorreccionRemota.CLIENTE) {
                        ventasApi.actualizarCliente(saleId, cuerpoCliente)
                    },
                    PeticionDeCorreccion(PasoCorreccionRemota.LINEAS) {
                        ventasApi.reemplazarLineas(saleId, cuerpoLineas)
                    }
                )
            )

            when (resultado) {
                is ResultadoDeLaSecuencia.Entregada -> {
                    // Sólo acá, y sólo con las TRES en 2xx: el servidor tiene
                    // la venta completa como la dejó el dueño.
                    localSaleDao.cerrarCorreccionRemota(saleId, revisionAlReclamar)
                    logger.info(
                        module = MODULO,
                        action = "CORRECCION_ENTREGADA",
                        message = "Corrección remota entregada al servidor",
                        data = mapOf(
                            "saleId" to saleId,
                            "revision" to revisionAlReclamar,
                            "situacion" to resultado.ultimaRespuesta.situacion,
                            "sincronizacion" to resultado.ultimaRespuesta.sincronizacion,
                            "productCount" to productos.size,
                            "comboCount" to combos.size,
                            "attemptCount" to runAttemptCount
                        )
                    )
                    Result.success()
                }

                is ResultadoDeLaSecuencia.Terminal -> manejarTerminal(saleId, fila, resultado)

                is ResultadoDeLaSecuencia.Reintentar -> {
                    logger.error(
                        module = MODULO,
                        action = "HTTP_ERROR",
                        message = "La corrección no entró en el paso " +
                            "${resultado.paso.codigo}; se reintenta la secuencia completa",
                        error = resultado.causa,
                        data = mapOf(
                            "saleId" to saleId,
                            "paso" to resultado.paso.codigo,
                            "pasosAplicados" to resultado.pasosAplicados,
                            "attemptCount" to runAttemptCount
                        )
                    )
                    Result.retry()
                }
            }
        } catch (e: IOException) {
            // Red, timeout, DNS: nadie tiene la corrección y el teléfono es el
            // único que la conserva. La fila queda pendiente.
            Log.w(TAG, "Error de red al entregar la corrección de $saleId, reintentando", e)
            logger.error(
                module = MODULO,
                action = "NETWORK_ERROR",
                message = "Error de red al entregar la corrección: ${e.message}",
                error = e,
                data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
            )
            Result.retry()
        } catch (e: CancellationException) {
            // El worker se está deteniendo: no es un desenlace, se propaga.
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error al entregar la corrección de $saleId", e)
            logger.error(
                module = MODULO,
                action = "CORRECCION_ERROR",
                message = "Error al entregar la corrección: ${e.message}",
                error = e,
                data = mapOf("saleId" to saleId, "attemptCount" to runAttemptCount)
            )
            Result.retry()
        }
    }

    /**
     * Deja la marca terminal de una secuencia que el servidor cerró en
     * definitiva y registra **cuál** de las tres peticiones lo hizo.
     *
     * Los dos campos que hay que leer juntos en el registro:
     * `pasosAplicados` (cuántas entraron antes del rechazo) y `estado`. Con
     * `pasosAplicados = 0` el servidor quedó intacto y el estado es el de
     * siempre; con `pasosAplicados > 0` quedó **a medias** y el estado es
     * [CorreccionRemotaTerminal.APLICADA_PARCIAL], con el motivo original en
     * `estadoSinParcial` — que no se persiste porque la columna guarda un
     * solo valor, y entre "por qué se cerró" y "qué tiene que ver el
     * cobrador" gana lo segundo.
     */
    private suspend fun manejarTerminal(
        saleId: String,
        fila: LocalSaleEntity,
        resultado: ResultadoDeLaSecuencia.Terminal
    ): Result {
        Log.e(
            TAG,
            "Error HTTP ${resultado.codigoHttp} (code=${resultado.codigoDeError}) " +
                "al corregir $saleId en el paso ${resultado.paso.codigo}",
            resultado.causa
        )

        logger.error(
            module = MODULO,
            action = "CORRECCION_TERMINAL",
            message = "El servidor rechazó la corrección en definitiva en el paso " +
                "${resultado.paso.codigo}; no se reintenta",
            error = resultado.causa,
            data = mapOf(
                "saleId" to saleId,
                "httpCode" to resultado.codigoHttp,
                "errorCode" to (resultado.codigoDeError ?: ""),
                "paso" to resultado.paso.codigo,
                "pasosAplicados" to resultado.pasosAplicados,
                "estado" to resultado.estado,
                "estadoSinParcial" to resultado.estadoSinParcial
            )
        )
        marcarTerminal(saleId, fila, resultado.estado)
        return Result.failure()
    }

    /**
     * Deja la marca TERMINAL en la fila. Los `SERVER_*` que pide el DAO salen,
     * en este orden, de:
     *
     * 1. un `GET /v2/ventas/{id}` de mejor esfuerzo — la lectura más fresca
     *    posible del servidor, exactamente lo que el plan pide guardar junto a
     *    la marca;
     * 2. si ese `GET` falla (404 de la venta que ya no está, red caída,
     *    cualquier cosa), lo que la fila ya sabía (`SERVER_SITUACION` /
     *    `SERVER_SINCRONIZACION`), para no pisar un dato bueno con vacío;
     * 3. cadena vacía si tampoco había nada.
     *
     * `version` NUNCA viene de la respuesta: **ningún DTO del cliente trae
     * `version`** — ni `VentaSituacionDTO` (el 200 del `PUT`) ni `VentaDTO`
     * (el `GET`) la declaran. Se conserva el `SERVER_VERSION` que la fila ya
     * tuviera, y `0` si nunca hubo uno. Es diagnóstico, nunca precondición de
     * una escritura, así que un 0 no habilita nada.
     */
    private suspend fun marcarTerminal(saleId: String, fila: LocalSaleEntity, estado: String) {
        val delServidor = try {
            ventasApi.obtenerVenta(saleId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo releer el estado de $saleId al marcar terminal", e)
            null
        }

        localSaleDao.marcarCorreccionRemotaTerminal(
            saleId = saleId,
            estado = estado,
            situacion = delServidor?.situacion ?: fila.SERVER_SITUACION.orEmpty(),
            sincronizacion = delServidor?.sincronizacion ?: fila.SERVER_SINCRONIZACION.orEmpty(),
            version = fila.SERVER_VERSION ?: SIN_VERSION_CONOCIDA,
            at = nowEpochMillis()
        )
    }

    /**
     * Qué devolver cuando `claimForRemote` dice que no. Cuatro razones, y sólo
     * una es "reintenta":
     *
     * - la fila no existe → `failure`;
     * - la venta no está `ENVIADO = 1` → `success`: no es una venta de este
     *   camino. Su corrección viaja en el `POST` de creación, con
     *   [PendingLocalSalesWorker]. Pasa de rutina, porque `GuardarCorreccion`
     *   encola esta cola para CUALQUIER venta corregida;
     * - ya hay una marca terminal, o no hay corrección pendiente → `success`:
     *   no queda nada que entregar;
     * - hay un candado vivo (el dueño corrigiendo, el subidor, u otra corrida
     *   remota) → `retry` **sin tocar la red**. `retry` y no `failure`:
     *   WorkManager conserva el trabajo y su backoff.
     */
    private suspend fun resultadoSinCandado(saleId: String): Result {
        val fila = localSaleDao.getSaleById(saleId) ?: return ventaNoEncontrada(saleId)

        if (!fila.ENVIADO) {
            logger.info(
                module = MODULO,
                action = "VENTA_SIN_ENVIAR",
                message = "La venta todavía no se sube; su corrección viaja en el POST",
                data = mapOf("saleId" to saleId)
            )
            return Result.success()
        }

        if (!fila.CORRECCION_REMOTA_PENDIENTE || !fila.CORRECCION_REMOTA_ESTADO.isNullOrBlank()) {
            logger.info(
                module = MODULO,
                action = "NADA_PENDIENTE",
                message = "No hay corrección remota que entregar",
                data = mapOf(
                    "saleId" to saleId,
                    "estado" to (fila.CORRECCION_REMOTA_ESTADO ?: "")
                )
            )
            return Result.success()
        }

        Log.i(TAG, "Venta $saleId reclamada por otro; se reintenta luego")
        logger.info(
            module = MODULO,
            action = "CLAIM_BUSY",
            message = "La venta tiene un candado vigente; la corrección se frena sin tocar la red",
            data = mapOf("saleId" to saleId, "claimKind" to (fila.CLAIM_KIND ?: ""))
        )
        return Result.retry()
    }

    private fun ventaNoEncontrada(saleId: String): Result = Result.failure().also {
        Log.e(TAG, "Venta local no encontrada: $saleId")
        logger.error(
            module = MODULO,
            action = "SALE_NOT_FOUND",
            message = "Venta no encontrada en base de datos local",
            data = mapOf("saleId" to saleId)
        )
    }

    companion object {
        /** Clave del `local_sale_id` en el `inputData`. */
        const val CLAVE_VENTA: String = "local_sale_id"

        /** Clave del correo del usuario en el `inputData`. */
        const val CLAVE_CORREO: String = "user_email"

        private const val TAG = "RemoteSaleCorrection"
        private const val MODULO = "REMOTE_SALE_CORRECTION"

        /**
         * `SERVER_VERSION` cuando nunca se leyó una. La columna es sólo
         * diagnóstico y UI (nunca precondición de una escritura), así que un
         * cero no habilita nada — ver `MIGRATION_31_32`.
         */
        private const val SIN_VERSION_CONOCIDA = 0
    }
}
