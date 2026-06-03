package com.example.msp_app.core.sync.cobranza

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import retrofit2.HttpException

/**
 * Particiona una List<Int> en lotes de a lo sumo [BY_IDS_LIMIT] elementos,
 * los ejecuta con concurrencia máxima de [CONCURRENCY] usando un Semaphore,
 * junta los resultados en una sola lista.
 *
 * Detección de endpoint no disponible: [byIdsAvailable] se fija a false en el
 * primer 404 recibido — la sesión cae al fallback cursor-sync permanentemente
 * para ese proceso. Si el servidor se actualiza, la app debe reiniciarse para
 * resetear el flag (proceso-level: intencional, evita loops de detección).
 *
 * Límite del servidor: 500 IDs por request (URL ~4 KB). Con más de 500 IDs se
 * emiten múltiples llamadas en paralelo (limitadas por [CONCURRENCY]).
 */
object ByIdsChunker {
    const val BY_IDS_LIMIT = 500
    private const val CONCURRENCY = 3

    /**
     * Flag proceso-level: se latchea a false en el primer 404 del by-ids
     * endpoint. Los callers deben consultar este flag antes de llamar a
     * [fetchInChunks] y caer al path cursor-sync si es false.
     */
    val byIdsAvailable = AtomicBoolean(true)

    /**
     * Ejecuta [fetch] en trozos de [BY_IDS_LIMIT] IDs con concurrencia
     * máxima de [CONCURRENCY] y devuelve la unión de todas las respuestas.
     *
     * Si [ids] está vacío, devuelve una lista vacía sin llamadas HTTP.
     *
     * Propaga cualquier excepción del [fetch]; si la excepción es un
     * [HttpException] con código 404, fija [byIdsAvailable] = false antes
     * de relanzar.
     *
     * @param ids  Lista de IDs a consultar.
     * @param fetch Suspending lambda que recibe un CSV de IDs y devuelve una
     *             lista de elementos T.
     */
    suspend fun <T> fetchInChunks(
        ids: List<Int>,
        fetch: suspend (chunk: String) -> List<T>
    ): List<T> = coroutineScope {
        val semaphore = Semaphore(CONCURRENCY)
        ids.chunked(BY_IDS_LIMIT)
            .map { chunk ->
                async {
                    semaphore.withPermit {
                        try {
                            fetch(chunk.joinToString(","))
                        } catch (e: HttpException) {
                            if (e.code() == 404) {
                                byIdsAvailable.set(false)
                            }
                            throw e
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
    }
}
