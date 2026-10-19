package com.example.msp_app.core.visitas.envio

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.enviodiagnostico.FallosPorCodigo
import com.example.msp_app.core.database.dao.visit.ConteosDeEnvio
import com.example.msp_app.core.logging.Logger
import com.example.msp_app.core.logging.RemoteLogger
import java.time.Duration
import java.time.Instant

/**
 * Telemetría del envío de visitas (plan visitas P1, Tarea 14; cierra H14;
 * spec §4.2-10 y §12).
 *
 * ## Por qué por `RemoteLogger`
 *
 * El puerto `core/telemetry` termina hoy en `StubTelemetrySink` (no sale del
 * teléfono), y por él van los códigos de `SyncErrorReporter`. Lo único que
 * llega a Firestore es `RemoteLogger`. Por eso esta clase existe: lo que antes
 * solo se veía en `Log` o en `SyncErrorReporter` (T11, T12, T13) se cuenta y
 * sale por aquí.
 *
 * ## Qué controla esta clase y qué no
 *
 * La lista blanca controla SOLO el mapa `data` del evento. `RemoteLogger`
 * envuelve cada evento en un sobre que esta clase no controla
 * (`sobreDeIdentidad` en `RemoteLogger.kt`), igual para todos los eventos de
 * la app: `userId` (el uid del empleado, como seudónimo), `userEmail` (el
 * correo del empleado) y `appVersion`. O sea, el evento sí identifica al
 * cobrador por el sobre. El correo se conserva porque hoy es la única medida
 * de adopción del canario (runbook P1, paso 3); `deviceInfo` se quitó
 * (decisión del coordinador, 2026-10-17, informada al dueño). En P3 se
 * reconsidera quitar el correo junto con un tablero de adopción por uid.
 *
 * ## Cero datos personales en `data` (mismo criterio que `COBRANZA-SYNC.md:506-511`)
 *
 * - Las claves son una lista blanca CERRADA ([CLAVES]); [emitir] descarta
 *   cualquier otra. `TelemetriaDeVisitasTest` fija la lista entera escrita a
 *   mano: agregar una clave obliga a cambiar la prueba.
 * - Los valores solo pueden ser conteos o antigüedades (enteros ≥ 0); la única
 *   cadena es `app.version`, con forma de versión. Un nombre, un monto, una
 *   coordenada o un id que llegue con una clave permitida también se descarta.
 * - Los eventos también son una lista cerrada ([EVENTOS]).
 * - Los códigos de fallo son una lista cerrada ([CODIGOS_DE_FALLO]); lo que no
 *   esté en ella se cuenta en `visitas.fallos.otro`. Así una fila que se
 *   reintenta para siempre (cuerpo inválido, fecha futura, 413 sin custodia)
 *   se ve aunque su código sea nuevo.
 *
 * ## No degrada el envío
 *
 * [emitir] traga todo (`Throwable`): si la telemetría falla, el envío sigue
 * igual. Nada de aquí vuelve a una decisión de envío.
 */
object TelemetriaDeVisitas {

    const val MODULO = "VISITAS_ENVIO"

    const val EVENTO_ESTADO = "visitas_estado"
    const val EVENTO_CONCILIACION = "visitas_conciliacion"
    const val EVENTO_PODA = "visitas_poda"

    /** E-APP-078: un guardado de visita se canceló al salir de la pantalla. */
    const val EVENTO_GUARDADO_CANCELADO = "visitas_guardado_cancelado"
    val EVENTOS: Set<String> =
        setOf(EVENTO_ESTADO, EVENTO_CONCILIACION, EVENTO_PODA, EVENTO_GUARDADO_CANCELADO)

    const val APP_VERSION = "app.version"
    const val PENDIENTES = "visitas.pendientes"
    const val PENDIENTE_MAS_VIEJA_MIN = "visitas.pendiente_mas_vieja_min"
    const val RESGUARDADAS = "visitas.resguardadas"
    const val CONFIRMADAS_24H = "visitas.confirmadas_24h"
    const val FALLOS_INTENTOS_MAX = "visitas.fallos.intentos_max"
    private const val PREFIJO_FALLOS = "visitas.fallos."
    const val FALLO_OTRO = "otro"

    /** Alerta del spec §12-1: cualquier valor > 0 avisa al dueño el mismo día. */
    const val DEVUELTA_A_PENDIENTE = "visitas.reconcilia.devuelta_a_pendiente"
    const val CONC_REVISADAS = "visitas.conciliacion.revisadas"
    const val CONC_FALTANTES = "visitas.conciliacion.faltantes"
    const val CONC_CONFIRMADAS = "visitas.conciliacion.confirmadas"
    const val CONC_TANDAS_FALLIDAS = "visitas.conciliacion.tandas_fallidas"
    const val CONC_IDS_INVALIDOS = "visitas.conciliacion.ids_invalidos"
    const val CONC_IDS_AJENOS = "visitas.conciliacion.ids_ajenos"
    const val CONC_RETENIDAS = "visitas.conciliacion.retenidas_por_comprobante"
    const val CONC_CONFIRMAR_FALLIDO = "visitas.conciliacion.confirmar_fallido"
    const val CONC_DEVOLVER_FALLIDO = "visitas.conciliacion.devolver_fallido"
    const val CONC_ILEGIBLES = "visitas.conciliacion.entregadas_ilegibles"

    /** E-APP-079: confirmadas devueltas a pendiente por una foto sin enviar. */
    const val CONC_DEVUELTAS_POR_FOTO = "visitas.conciliacion.devueltas_por_foto"

    /** E-APP-079: fotos sin enviar fuera de la ventana; ya no devuelven su visita (alerta si > 0). */
    const val CONC_FOTOS_ABANDONADAS = "visitas.conciliacion.fotos_abandonadas"

    /** T12: resguardadas que rebasaron la retención; se reportan antes de podarlas (spec §4.2-8). */
    const val RESGUARDADAS_POR_PODAR = "visitas.resguardadas_por_podar"

    /** E-APP-078: cuentas escritas antes del corte, de [GC_CUENTAS]. */
    const val GC_ESCRITAS = "visitas.guardado_cancelado.escritas"
    const val GC_CUENTAS = "visitas.guardado_cancelado.cuentas"

    /** 1 si se canceló esperando la ubicación (nada se intentó escribir), 0 si escribiendo. */
    const val GC_EN_UBICACION = "visitas.guardado_cancelado.en_ubicacion"

    private val HTTP_CONOCIDOS = listOf(
        400, 401, 403, 404, 405, 408, 409, 410, 413, 415, 422, 425, 429, 500, 502, 503, 504
    )

    /**
     * Códigos de fallo de visita con clave propia. Son literales de código:
     * los que anota el worker (T11), los de red del clasificador, los del
     * servidor de visitas (`internal/visitas` de msp-api) y estados HTTP.
     * `TelemetriaDeVisitasTest` verifica que todo código del worker y del
     * clasificador esté aquí.
     */
    val CODIGOS_DE_FALLO: Set<String> = setOf(
        // Worker de visitas v3 (Tarea 11).
        "sin_visit_id",
        "visita_no_encontrada",
        "comprobantes_ilegibles",
        "visita_sin_eco",
        "visita_foto_nueva",
        "visita_confirmada_por_otro_camino_con_foto_nueva",
        "visita_sin_marca",
        "visita_fotos_sin_confirmar",
        // Worker de visitas, relleno del compromiso (P2 Tarea 15, E-APP-098).
        "visita_compromiso_sin_confirmar",
        "visita_compromiso_sin_marca",
        // Red (ClasificadorDeFalloDeEnvio).
        "cancelado",
        "timeout",
        "dns_error",
        "tls_error",
        "network_error",
        "error_inesperado",
        // Servidor: visitas.
        "visita_body_too_large",
        "visita_body_too_large_to_capture",
        "visita_body_json_invalido",
        "visita_body_requerido",
        "visita_fecha_futura",
        "visita_fecha_invalida",
        "visita_fecha_requerida",
        "visita_id_invalido",
        "visita_id_requerido",
        "visita_tipo_invalido",
        "visita_tipo_requerido",
        "visita_tipo_demasiado_largo",
        "visita_nota_demasiado_larga",
        "visita_cobrador_requerido",
        "visita_cobrador_demasiado_largo",
        "visita_cliente_requerido",
        "visita_lat_requerida",
        "visita_lng_requerida",
        "visita_string_caracteres_invalidos",
        "visita_ya_existe",
        "visita_not_found",
        "visita_conflicto",
        "visita_imagen_blob_error",
        "visita_imagen_id_invalido",
        "visita_imagen_id_duplicado",
        "visita_imagen_id_requerido",
        "visita_imagen_mime_no_permitido",
        "visita_imagen_ya_existe",
        "visita_imagen_no_encontrada",
        "visita_imagen_descripcion_demasiado_larga",
        "visitas_imagenes_no_configurado",
        // Servidor: contrato de visitas 3 (msp-api feat/visitas-p2-t10, spec §5.2.2,
        // E-VIS-046, E-VIS-047, E-VIS-051).
        "visita_promesa_fecha_invalida",
        "visita_promesa_incompleta",
        "visita_promesa_monto_invalido",
        "visita_promesa_venta_invalida",
        "visita_cita_incompleta",
        "visita_cita_invalida",
        "visita_compromiso_distinto",
        "visita_cliente_id_out_of_range",
        "visita_cobrador_id_out_of_range",
        "visita_forma_cobro_id_out_of_range",
        "visita_zona_cliente_id_out_of_range",
        "visita_impte_docto_cc_id_out_of_range",
        "visita_imagen_id_de_otra_visita",
        // Servidor: plataforma.
        "idempotency_key_mismatch",
        "validation_failed",
        "invalid_request_body",
        "internal_error",
        "unauthenticated",
        "user_inactive",
        "firebird_error",
        "firebird_timeout",
        "firebird_lock_conflict",
        "firebird_connection_lost",
        "http"
    ) + HTTP_CONOCIDOS.map { "http_$it" }

    /** La lista blanca cerrada de claves. Ver `TelemetriaDeVisitasTest`. */
    val CLAVES: Set<String> = setOf(
        APP_VERSION,
        PENDIENTES,
        PENDIENTE_MAS_VIEJA_MIN,
        RESGUARDADAS,
        CONFIRMADAS_24H,
        FALLOS_INTENTOS_MAX,
        PREFIJO_FALLOS + FALLO_OTRO,
        DEVUELTA_A_PENDIENTE,
        CONC_REVISADAS,
        CONC_FALTANTES,
        CONC_CONFIRMADAS,
        CONC_TANDAS_FALLIDAS,
        CONC_IDS_INVALIDOS,
        CONC_IDS_AJENOS,
        CONC_RETENIDAS,
        CONC_CONFIRMAR_FALLIDO,
        CONC_DEVOLVER_FALLIDO,
        CONC_ILEGIBLES,
        CONC_DEVUELTAS_POR_FOTO,
        CONC_FOTOS_ABANDONADAS,
        RESGUARDADAS_POR_PODAR,
        GC_ESCRITAS,
        GC_CUENTAS,
        GC_EN_UBICACION
    ) + CODIGOS_DE_FALLO.map { PREFIJO_FALLOS + it }

    private val FORMA_DE_VERSION = Regex("^[0-9][0-9A-Za-z._+-]{0,39}$")

    /** Clave de un código de fallo: la suya si está en la lista cerrada, si no `otro`. */
    fun claveDeFallo(codigo: String): String =
        PREFIJO_FALLOS + if (codigo in CODIGOS_DE_FALLO) codigo else FALLO_OTRO

    /**
     * Estado del envío, una vez por corrida de `VisitsReconcileWorker` (cada
     * 15 min y al abrir la app). [fallos] son los fallos vigentes de visitas
     * por código (los lee `EstadoDeEnvioDeVisitas`): así se ven las filas que se
     * reintentan para siempre.
     */
    fun estado(
        c: ConteosDeEnvio,
        ahora: Instant,
        version: String,
        fallos: List<FallosPorCodigo> = emptyList()
    ): Map<String, Any> = buildMap {
        put(PENDIENTES, c.pendientes)
        put(RESGUARDADAS, c.resguardadas)
        put(CONFIRMADAS_24H, c.confirmadas24h)
        put(APP_VERSION, version)
        AppTime.parseWireFormatOrNull(c.masVieja)?.let { vieja ->
            put(
                PENDIENTE_MAS_VIEJA_MIN,
                Duration.between(vieja, ahora).toMinutes().coerceAtLeast(0L)
            )
        }
        if (fallos.isNotEmpty()) {
            fallos.groupBy { claveDeFallo(it.codigo) }
                .forEach { (clave, filas) -> put(clave, filas.sumOf { it.filas }) }
            put(FALLOS_INTENTOS_MAX, fallos.maxOf { it.intentosMax })
        }
    }

    /** Resultado de la reconciliación inversa diaria (Tarea 13): todos sus conteos. */
    fun conciliacion(r: ConciliacionInversa): Map<String, Any> = mapOf(
        DEVUELTA_A_PENDIENTE to r.faltantes,
        CONC_REVISADAS to r.revisadas,
        CONC_FALTANTES to r.faltantes,
        CONC_CONFIRMADAS to r.confirmadas,
        CONC_TANDAS_FALLIDAS to r.tandasFallidas,
        CONC_IDS_INVALIDOS to r.idsInvalidos,
        CONC_IDS_AJENOS to r.idsAjenos,
        CONC_RETENIDAS to r.retenidasPorComprobante,
        CONC_CONFIRMAR_FALLIDO to r.confirmacionesFallidas,
        CONC_DEVOLVER_FALLIDO to r.devolucionesFallidas,
        CONC_ILEGIBLES to if (r.entregadasIlegibles) 1 else 0,
        CONC_DEVUELTAS_POR_FOTO to r.devueltasPorFoto,
        CONC_FOTOS_ABANDONADAS to r.fotosAbandonadas
    )

    /** Poda (Tarea 12): cuántas resguardadas se reportan antes de podarlas. */
    fun poda(resguardadasPorPodar: Int): Map<String, Any> =
        mapOf(RESGUARDADAS_POR_PODAR to resguardadasPorPodar)

    /** Guardado cancelado (E-APP-078): solo conteos. */
    fun guardadoCancelado(escritas: Int, cuentas: Int, enUbicacion: Boolean): Map<String, Any> =
        mapOf(
            GC_ESCRITAS to escritas,
            GC_CUENTAS to cuentas,
            GC_EN_UBICACION to if (enUbicacion) 1 else 0
        )

    /** Solo pruebas: reemplaza el envío a `RemoteLogger`. */
    @VisibleForTesting
    @Volatile
    internal var sinkParaPrueba: ((String, Map<String, Any>) -> Unit)? = null

    /**
     * Manda [evento] con [datos] filtrados a la lista blanca. Nunca lanza.
     */
    @Suppress("TooGenericExceptionCaught")
    fun emitir(evento: String, datos: Map<String, Any>) {
        try {
            if (evento !in EVENTOS) return
            val limpio = datos.filter { (clave, valor) -> clave in CLAVES && valorPermitido(clave, valor) }
            if (limpio.isEmpty()) return
            val sink = sinkParaPrueba
            if (sink != null) {
                sink(evento, limpio)
            } else {
                Logger.get().logEvent(
                    module = MODULO,
                    eventType = RemoteLogger.EventType.SYSTEM_EVENT,
                    eventName = evento,
                    data = limpio
                )
            }
        } catch (e: Throwable) {
            // Telemetría caída: el envío sigue igual.
            runCatching { Log.w(TAG, "telemetria de visitas no se pudo mandar", e) }
        }
    }

    private fun valorPermitido(clave: String, valor: Any): Boolean = when {
        clave == APP_VERSION -> valor is String && FORMA_DE_VERSION.matches(valor)
        valor is Int -> valor >= 0
        valor is Long -> valor >= 0L
        else -> false
    }

    private const val TAG = "TelemetriaDeVisitas"
}
