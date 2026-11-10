package com.example.msp_app.data.repository

import android.content.Context
import com.example.msp_app.core.common.busqueda.NormalizadorDeBusqueda
import com.example.msp_app.core.common.busqueda.TopeDeDescarga
import com.example.msp_app.core.common.busqueda.conTopeTotal
import com.example.msp_app.core.database.clientes.ResultadoDeClientes
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.logging.RemoteLogger
import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.clientes.ClientesApi
import com.example.msp_app.data.local.datasource.ClienteDataSource

/** Dónde se guarda cuándo se actualizó el padrón por última vez (la misma que ya usaba el worker). */
const val PREFS_CLIENTE_SYNC = "cliente_sync"
const val CLAVE_ULTIMA_SYNC = "last_sync"

/** Qué pasó con una lista recibida (E-APP-159). */
enum class ResultadoDePadron { REEMPLAZADO, VACIA_IGNORADA, INCOMPLETA_IGNORADA }

/** Solo se vigila el tamaño cuando hay MÁS de este número de clientes guardados. */
internal const val PADRON_MINIMO_PARA_VIGILAR = 1_000

/**
 * Padrón de clientes: descarga, reemplazo y búsqueda (E-APP-157).
 *
 * El constructor interno recibe todo por parámetro para probarlo sin red, sin Firebase y con
 * reloj virtual; el público de `Context` es el que usan las pantallas y el worker.
 */
class ClienteRepository internal constructor(
    private val dataSource: ClienteDataSource,
    private val api: ClientesApi,
    private val avisar: (accion: String, mensaje: String, datos: Map<String, Any?>) -> Unit,
    private val guardarFechaDeSync: (Long) -> Unit,
    private val leerFechaDeSync: () -> Long,
    private val ahora: () -> Long
) {
    constructor(context: Context) : this(
        dataSource = ClienteDataSource(context),
        api = ApiProvider.create(ClientesApi::class.java),
        avisar = { accion, mensaje, datos ->
            RemoteLogger.getInstance(context).warning("CLIENTE_SYNC", accion, mensaje, datos)
        },
        guardarFechaDeSync = { momento ->
            context.getSharedPreferences(PREFS_CLIENTE_SYNC, Context.MODE_PRIVATE)
                .edit().putLong(CLAVE_ULTIMA_SYNC, momento).apply()
        },
        leerFechaDeSync = {
            context.getSharedPreferences(PREFS_CLIENTE_SYNC, Context.MODE_PRIVATE)
                .getLong(CLAVE_ULTIMA_SYNC, 0L)
        },
        ahora = System::currentTimeMillis
    )

    /** Baja el padrón del servidor. Solo red: no toca la base. Cancelable (cancela la petición). */
    suspend fun descargarPadron(): List<ClienteEntity> = api.getClientes().body.map { cliente ->
        ClienteEntity(
            CLIENTE_ID = cliente.CLIENTE_ID,
            NOMBRE = cliente.NOMBRE,
            ESTATUS = cliente.ESTATUS,
            CAUSA_SUSP = cliente.CAUSA_SUSP,
            // Misma normalización que la consulta (NormalizadorDeBusqueda): el defecto
            // clásico es normalizar un lado y no el otro. Los teléfonos que aún tienen la
            // normalización VIEJA siguen encontrando filas hasta el próximo sync (E-APP-159).
            NOMBRE_NORMALIZADO = NormalizadorDeBusqueda.normalizar(cliente.NOMBRE)
        )
    }

    /**
     * Reemplaza el padrón de forma atómica (E-APP-159). No reemplaza nada si la lista:
     * - viene VACÍA: `replaceAll` borra primero, así que aceptarla dejaría a todos los cobradores
     *   sin clientes hasta el próximo sync bueno;
     * - viene INCOMPLETA: con más de [PADRON_MINIMO_PARA_VIGILAR] clientes guardados, una lista
     *   con menos de la mitad de ellos es casi seguro una respuesta cortada, no un padrón real.
     *
     * Solo se registran conteos, nunca nombres.
     */
    suspend fun aplicarPadronConResultado(entidades: List<ClienteEntity>): ResultadoDePadron {
        val guardados = dataSource.getCount()
        if (entidades.isEmpty()) {
            avisar(
                "LISTA_VACIA_IGNORADA",
                "El servidor contestó una lista de clientes vacía; se conserva el padrón guardado",
                mapOf("clientesGuardados" to guardados)
            )
            return ResultadoDePadron.VACIA_IGNORADA
        }
        if (guardados > PADRON_MINIMO_PARA_VIGILAR && entidades.size * 2 < guardados) {
            avisar(
                "LISTA_INCOMPLETA_IGNORADA",
                "El servidor contestó menos de la mitad de los clientes guardados; se conserva el padrón guardado",
                mapOf("clientesGuardados" to guardados, "clientesRecibidos" to entidades.size)
            )
            return ResultadoDePadron.INCOMPLETA_IGNORADA
        }
        dataSource.replaceAll(entidades)
        guardarFechaDeSync(ahora())
        return ResultadoDePadron.REEMPLAZADO
    }

    /** @return `true` si se reemplazó; `false` si se ignoró (vacía o incompleta). */
    suspend fun aplicarPadron(entidades: List<ClienteEntity>): Boolean =
        aplicarPadronConResultado(entidades) == ResultadoDePadron.REEMPLAZADO

    /**
     * Descarga + reemplazo con tope TOTAL de duración (E-APP-158). Lo usa el worker periódico;
     * el botón manual arma lo mismo con su propio tope en `ActualizadorDeLista`.
     * El tope solo cubre la descarga: la escritura es atómica y no se corta a medias.
     */
    suspend fun sincronizar(topeMs: Long = TopeDeDescarga.SEGUNDO_PLANO_MS): ResultadoDePadron =
        aplicarPadronConResultado(conTopeTotal(topeMs) { descargarPadron() })

    suspend fun syncFromServer(topeMs: Long = TopeDeDescarga.SEGUNDO_PLANO_MS): Boolean =
        sincronizar(topeMs) == ResultadoDePadron.REEMPLAZADO

    /** Epoch ms de la última actualización exitosa, o `null` si nunca. */
    fun ultimaActualizacionMs(): Long? = leerFechaDeSync().takeIf { it > 0L }

    suspend fun buscar(query: String): ResultadoDeClientes = dataSource.buscar(query)

    /** Los (hasta) 20 resultados principales; ver [buscar] para total y "Parecidos". */
    suspend fun searchClientes(query: String): List<ClienteEntity> = buscar(query).exactos

    suspend fun getCount(): Int = dataSource.getCount()
}
