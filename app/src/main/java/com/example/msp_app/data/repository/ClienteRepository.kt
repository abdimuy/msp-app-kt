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
     * Reemplaza el padrón de forma atómica (E-APP-159). Una lista VACÍA del servidor no reemplaza nada:
     * `replaceAll` borra primero, así que aceptarla dejaría a todos los cobradores sin clientes
     * hasta el próximo sync bueno.
     *
     * @return `true` si se reemplazó; `false` si se ignoró por vacía.
     */
    suspend fun aplicarPadron(entidades: List<ClienteEntity>): Boolean {
        if (entidades.isEmpty()) {
            avisar(
                "LISTA_VACIA_IGNORADA",
                "El servidor contestó una lista de clientes vacía; se conserva el padrón guardado",
                mapOf("clientesGuardados" to dataSource.getCount())
            )
            return false
        }
        dataSource.replaceAll(entidades)
        guardarFechaDeSync(ahora())
        return true
    }

    /**
     * Descarga + reemplazo con tope TOTAL de duración (E-APP-158). Lo usa el worker periódico;
     * el botón manual arma lo mismo con su propio tope en `ActualizadorDeLista`.
     * El tope solo cubre la descarga: la escritura es atómica y no se corta a medias.
     */
    suspend fun syncFromServer(topeMs: Long = TopeDeDescarga.SEGUNDO_PLANO_MS): Boolean =
        aplicarPadron(conTopeTotal(topeMs) { descargarPadron() })

    /** Epoch ms de la última actualización exitosa, o `null` si nunca. */
    fun ultimaActualizacionMs(): Long? = leerFechaDeSync().takeIf { it > 0L }

    suspend fun buscar(query: String): ResultadoDeClientes = dataSource.buscar(query)

    /** Los (hasta) 20 resultados principales; ver [buscar] para total y "Parecidos". */
    suspend fun searchClientes(query: String): List<ClienteEntity> = buscar(query).exactos

    suspend fun getCount(): Int = dataSource.getCount()
}
