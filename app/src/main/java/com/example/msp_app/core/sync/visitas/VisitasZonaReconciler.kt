package com.example.msp_app.core.sync.visitas

import android.util.Log
import com.example.msp_app.core.database.dao.visit.IdYUpdatedAt
import com.example.msp_app.core.database.dao.visit.VisitaZonaDao
import com.example.msp_app.core.visitas.envio.ResultadoInventarioConteos
import com.example.msp_app.data.api.services.visits.VisitasZonaSyncApi
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Inventario de la agenda de la zona (plan visitas P2, Tarea 18; spec
 * §5.4.4-4 y §4.6; E-APP-109): la red de seguridad de la bajada. Si la marca
 * segura del servidor fallara por una razón no prevista (un commit tardío que
 * el cursor ya dejó atrás), el sync no volvería a entregar esa fila; el
 * inventario la detecta comparando conjuntos, igual que el reconciliador de
 * pagos (`COBRANZA-SYNC.md` §4).
 *
 * ## Qué hace, en orden
 *
 * 1. Poda lo que salió de la ventana (`FECHA < desde`): es la misma regla que
 *    el servidor y no depende de él.
 * 2. Digest: compara el hash local de `"<id>|<updated_at>"` (el de
 *    `HashInventario` del servidor, [hashInventario]) y el conteo. Iguales =
 *    termina sin pedir nada más.
 * 3. Si difiere: pide `ids` completo (paginado por `after`), rescata por
 *    `by-ids` lo que falta (tandas de [VisitasZonaSyncApi.MAX_IDS_BY_IDS]) y
 *    borra de `visita_zona` lo que el servidor ya no lista.
 *
 * ## Reglas de seguridad
 *
 * - **Una consulta fallida no decide nada.** Si falla el digest, cualquier
 *   página de `ids` o cualquier tanda de `by-ids`, no se borra nada. Lo que
 *   sí llegó por `by-ids` se guarda: agregar lo que el servidor entregó nunca
 *   quita información.
 * - **Nunca `Visit`** (I8). Esta clase solo recibe [VisitaZonaDao], que no
 *   nombra la tabla de salida: borrar de la agenda bajada no puede tocar una
 *   captura pendiente.
 * - **La posición del sync no se toca.** Lo rescatado entra con
 *   [VisitaZonaDao.rescatar]; el cursor solo sale de la respuesta del sync
 *   (E-VIS-070).
 * - **El mismo `desde` en los tres canales** (I4), y el mismo que manda la
 *   bajada: lo calcula [VisitasZonaSyncManager.desdeDeLaVentana].
 * - **Bajo el candado de la bajada.** Si el sync aplicara una página entre la
 *   lectura de `ids` y el borrado, se borraría una fila recién bajada con el
 *   cursor ya más allá de ella.
 *
 * ## Lo que se acepta
 *
 * El digest del servidor aplica la marca segura (`UPDATED_AT <= upper`). Una
 * fila que cambió en el último segundo, o durante una transacción larga de
 * Microsip, no aparece en `ids` aunque el teléfono tenga su versión anterior:
 * se borra de `visita_zona` y la siguiente corrida del sync la vuelve a bajar,
 * porque su `UPDATED_AT` nuevo es mayor que el cursor. Cuesta un hueco breve en
 * la agenda, nunca una captura.
 *
 * Nunca lanza salvo cancelación: devuelve [ResultadoInventario.Fallido].
 */
class VisitasZonaReconciler(
    private val api: VisitasZonaSyncApi,
    private val dao: VisitaZonaDao,
    /** Ids por página de `ids` (servidor: 1..10000, default 5000). */
    private val limiteIds: Int = LIMITE_IDS,
    private val mutex: Mutex = VisitasZonaSyncManager.MUTEX_DEL_PROCESO
) {

    suspend fun reconciliar(zona: Int, desde: String): ResultadoInventario =
        mutex.withLock { reconciliarBajoCandado(zona, desde) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun reconciliarBajoCandado(zona: Int, desde: String): ResultadoInventario {
        return try {
            dao.podarFueraDeVentana(desde)
            val local = dao.idsYUpdatedAt(zona, desde)
            val digest = api.digest(zona, desde)
            val hashServidor = digest.hash
                ?: return ResultadoInventario.Fallido(local.size, "digest sin hash")
            if (hashServidor == hashInventario(local) && digest.count == local.size) {
                return ResultadoInventario.Igual(revisadas = local.size)
            }
            Log.i(
                TAG,
                "inventario zona=$zona difiere: local=${local.size} servidor=${digest.count}"
            )
            corregir(zona, desde, local)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "inventario zona=$zona fallo: ${e.message}", e)
            ResultadoInventario.Fallido(revisadas = 0, motivo = e::class.simpleName ?: "error")
        }
    }

    private suspend fun corregir(
        zona: Int,
        desde: String,
        local: List<IdYUpdatedAt>
    ): ResultadoInventario {
        val servidor = idsDelServidor(zona, desde)
            ?: return ResultadoInventario.Fallido(local.size, "ids incompletos")
        val locales = local.mapTo(HashSet()) { it.id }
        val faltantes = servidor.filter { it !in locales }

        var rescatadas = 0
        var tandaFallida = false
        for (tanda in faltantes.chunked(VisitasZonaSyncApi.MAX_IDS_BY_IDS)) {
            val r = try {
                api.byIds(zona, desde, tanda.joinToString(","))
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.w(TAG, "by-ids zona=$zona fallo; esa tanda no decide nada", e)
                tandaFallida = true
                continue
            }
            val pedidas = tanda.toHashSet()
            // Solo lo que se preguntó: un id que el servidor mande por su
            // cuenta no entra por aquí.
            val filas = r.items.orEmpty()
                .filter { it.id in pedidas }
                .mapNotNull { it.aEntidad(zona) }
            dao.rescatar(filas)
            rescatadas += filas.size
        }
        if (tandaFallida) {
            return ResultadoInventario.Fallido(local.size, "by-ids incompleto", rescatadas)
        }

        val listados = servidor.toHashSet()
        val sobrantes = local.map { it.id }.filter { it !in listados }
        if (sobrantes.isNotEmpty()) dao.borrar(sobrantes)
        return ResultadoInventario.Reconciliado(
            revisadas = local.size,
            rescatadas = rescatadas,
            borradas = sobrantes.size
        )
    }

    /** Todos los ids del servidor, o `null` si alguna página no se pudo leer entera. */
    private suspend fun idsDelServidor(zona: Int, desde: String): List<String>? {
        val todos = mutableListOf<String>()
        var after: String? = null
        while (true) {
            val r = api.ids(zona, desde, after, limiteIds)
            val pagina = r.ids.orEmpty()
            todos += pagina
            if (r.has_more != true) return todos
            // `has_more` sin ids no avanza: preguntar otra vez sería un bucle.
            after = pagina.lastOrNull() ?: return null
        }
    }

    companion object {
        private const val TAG = "VisitasZonaInventario"

        /** `visitasapp.LimiteIDsPorOmision` del servidor. */
        const val LIMITE_IDS = 5000

        /**
         * El hash del digest del servidor (`HashInventario`,
         * `internal/visitas/app/sync.go`): SHA-256 hex de las líneas
         * `"<id>|<updated_at>"` ordenadas por id en bytes y unidas con `\n`.
         * `updated_at` es el texto que guardó el sync, tal cual.
         */
        fun hashInventario(entradas: List<IdYUpdatedAt>): String {
            val texto = entradas
                .sortedWith { a, b -> compararBytes(a.id, b.id) }
                .joinToString("\n") { "${it.id}|${it.updatedAt}" }
            val suma = MessageDigest.getInstance(
                "SHA-256"
            ).digest(texto.toByteArray(Charsets.UTF_8))
            return suma.joinToString("") { "%02x".format(it) }
        }

        /** Orden de Go para `string`: bytes UTF-8 sin signo. */
        private fun compararBytes(a: String, b: String): Int {
            val x = a.toByteArray(Charsets.UTF_8)
            val y = b.toByteArray(Charsets.UTF_8)
            for (i in 0 until minOf(x.size, y.size)) {
                val d = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
                if (d != 0) return d
            }
            return x.size - y.size
        }
    }
}

/** Lo que hizo una corrida de [VisitasZonaReconciler.reconciliar]. */
sealed class ResultadoInventario {
    /** Filas locales dentro de la ventana que se compararon. */
    abstract val revisadas: Int

    /** El inventario local es el del servidor: no se pidió nada más. */
    data class Igual(override val revisadas: Int) : ResultadoInventario()

    data class Reconciliado(
        override val revisadas: Int,
        val rescatadas: Int,
        val borradas: Int
    ) : ResultadoInventario()

    /** Alguna consulta falló: no se borró nada. [rescatadas] = lo que sí llegó. */
    data class Fallido(
        override val revisadas: Int,
        val motivo: String,
        val rescatadas: Int = 0
    ) : ResultadoInventario()

    /** Solo conteos, para la telemetría (E-APP-109). */
    fun conteos(): ResultadoInventarioConteos = when (this) {
        is Igual -> ResultadoInventarioConteos(revisadas, 0, 0, fallido = false)
        is Reconciliado -> ResultadoInventarioConteos(
            revisadas,
            rescatadas,
            borradas,
            fallido = false
        )
        is Fallido -> ResultadoInventarioConteos(revisadas, rescatadas, 0, fallido = true)
    }
}
