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
import kotlin.math.abs

/** Dónde se guarda cuándo se actualizó el padrón por última vez (la misma que ya usaba el worker). */
const val PREFS_CLIENTE_SYNC = "cliente_sync"
const val CLAVE_ULTIMA_SYNC = "last_sync"

/** Qué pasó con una lista recibida (E-APP-159). */
enum class ResultadoDePadron { REEMPLAZADO, VACIA_IGNORADA, INCOMPLETA_IGNORADA }

/** La última lista rechazada por incompleta: su tamaño y cuántas veces seguidas llegó (de tamaño parecido). */
data class RechazoDePadron(val tamano: Int, val veces: Int) {
    companion object {
        val NINGUNO = RechazoDePadron(0, 0)
    }
}

/**
 * Memoria de los rechazos por lista incompleta. Sin ella, un servidor que de verdad mandara menos
 * de la mitad (p. ej. se dio de baja a mucha gente) dejaría el padrón congelado para siempre:
 * a la 3.ª vez seguida con tamaño parecido ([TOLERANCIA_PORCENTAJE]) la lista se acepta.
 */
class RegistroDeRechazos(
    private val leer: () -> RechazoDePadron,
    private val guardar: (RechazoDePadron) -> Unit
) {
    fun actual(): RechazoDePadron = leer()

    /** Anota un rechazo de [tamano] y devuelve cuántas veces seguidas lleva (contando éste). */
    fun anotar(tamano: Int): Int {
        val previo = leer()
        val parecido = previo.veces > 0 &&
            abs(tamano - previo.tamano) * PORCENTAJE <= TOLERANCIA_PORCENTAJE * previo.tamano
        val veces = if (parecido) previo.veces + 1 else 1
        guardar(RechazoDePadron(tamano, veces))
        return veces
    }

    fun reiniciar() {
        if (leer() != RechazoDePadron.NINGUNO) guardar(RechazoDePadron.NINGUNO)
    }

    companion object {
        const val TOLERANCIA_PORCENTAJE = 5
        const val RECHAZOS_PARA_ACEPTAR = 3
        private const val PORCENTAJE = 100
        private const val CLAVE_TAMANO = "rechazo_tamano"
        private const val CLAVE_VECES = "rechazo_veces"

        fun enMemoria(): RegistroDeRechazos {
            var estado = RechazoDePadron.NINGUNO
            return RegistroDeRechazos({ estado }, { estado = it })
        }

        fun enPreferencias(context: Context): RegistroDeRechazos {
            fun prefs() = context.getSharedPreferences(PREFS_CLIENTE_SYNC, Context.MODE_PRIVATE)
            return RegistroDeRechazos(
                leer = {
                    RechazoDePadron(prefs().getInt(CLAVE_TAMANO, 0), prefs().getInt(CLAVE_VECES, 0))
                },
                guardar = {
                    prefs().edit().putInt(
                        CLAVE_TAMANO,
                        it.tamano
                    ).putInt(CLAVE_VECES, it.veces).apply()
                }
            )
        }
    }
}

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
    private val ahora: () -> Long,
    private val rechazos: RegistroDeRechazos = RegistroDeRechazos.enMemoria()
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
        ahora = System::currentTimeMillis,
        rechazos = RegistroDeRechazos.enPreferencias(context)
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
     * - viene VACÍA (siempre, también con [forzar]): `replaceAll` borra primero, así que aceptarla
     *   dejaría a todos los cobradores sin clientes hasta el próximo sync bueno;
     * - viene INCOMPLETA: con más de [PADRON_MINIMO_PARA_VIGILAR] clientes guardados, una lista
     *   con menos de la mitad de ellos es casi seguro una respuesta cortada, no un padrón real.
     *
     * Salidas para que un padrón realmente más chico no quede congelado: [forzar] (el botón
     * manual "Actualizar de todos modos" aplica la lista ya descargada) y, sin intervención, la
     * 3.ª vez seguida que llega una lista incompleta de tamaño parecido ([RegistroDeRechazos]).
     * Un reemplazo exitoso reinicia esa cuenta.
     *
     * [automatico] `true` (el worker) es el ÚNICO que anota rechazos y acepta solo a la 3.ª. El botón
     * manual pasa `false`: nunca anota ni acepta solo, 3 toques seguidos no aceptan una lista
     * recortada; solo "Actualizar de todos modos" ([forzar]) la aplica.
     *
     * Solo se registran conteos, nunca nombres.
     */
    suspend fun aplicarPadronConResultado(
        entidades: List<ClienteEntity>,
        forzar: Boolean = false,
        automatico: Boolean = true
    ): ResultadoDePadron {
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
            val conteos =
                mapOf("clientesGuardados" to guardados, "clientesRecibidos" to entidades.size)
            when {
                forzar -> avisar(
                    "LISTA_INCOMPLETA_FORZADA",
                    "Se aplicó a petición una lista con menos de la mitad de los clientes guardados",
                    conteos
                )
                automatico && rechazos.anotar(entidades.size) >= RegistroDeRechazos.RECHAZOS_PARA_ACEPTAR -> avisar(
                    "LISTA_INCOMPLETA_ACEPTADA",
                    "Llegó la misma lista reducida varias veces seguidas; se acepta como el padrón real",
                    conteos
                )
                else -> {
                    avisar(
                        "LISTA_INCOMPLETA_IGNORADA",
                        "El servidor contestó menos de la mitad de los clientes guardados; se conserva el padrón guardado",
                        conteos
                    )
                    return ResultadoDePadron.INCOMPLETA_IGNORADA
                }
            }
        }
        dataSource.replaceAll(entidades)
        rechazos.reiniciar()
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
