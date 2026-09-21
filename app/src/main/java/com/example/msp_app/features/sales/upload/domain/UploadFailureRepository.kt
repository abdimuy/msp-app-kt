package com.example.msp_app.features.sales.upload.domain

/**
 * Port: how the upload-failure subsystem persists and queries the
 * upload-failure state of a local sale. Implementations live in
 * [com.example.msp_app.features.sales.upload.data]; this interface lets
 * the worker and view-model depend on the domain rather than on Room.
 */
interface UploadFailureRepository {

    /**
     * Persists a server rejection on the local sale, overwriting any prior
     * failure. The server middleware no longer caches 4xx (see
     * `fix(idempotency): cache only 2xx`) so each retry returns a fresh
     * error that reflects the current body — the latest failure is always
     * the most accurate, no precedence logic needed.
     */
    suspend fun recordFailure(saleId: String, failure: UploadFailure)

    /**
     * Clears any persisted upload-failure for [saleId] — called when the
     * worker succeeds and when the user edits a failed sale.
     */
    suspend fun clearFailure(saleId: String)

    /**
     * Returns the current Idempotency-Key for [saleId], minting one based
     * on [defaultKey] if none has been set yet. The default is the saleId
     * itself, matching the legacy behavior used before this feature shipped.
     */
    suspend fun currentIdempotencyKey(saleId: String, defaultKey: String): String

    /** Rotates the Idempotency-Key to [newKey]. Used by edit-and-retry. */
    suspend fun rotateIdempotencyKey(saleId: String, newKey: String)

    /**
     * **NO LO LLAMES DESDE LA CORRECCIÓN DE VENTAS: rota la
     * `Idempotency-Key`, y eso crea una SEGUNDA VENTA en el servidor.**
     *
     * Es el invariante del plan "Corregir una venta antes de que suba"
     * (Global Constraints: *"la `Idempotency-Key` NO se rota jamás en este
     * flujo"*). Si el servidor sí recibió el POST original y lo que se perdió
     * fue el 2xx, una llave nueva no choca con nada y la venta entra **otra
     * vez**: el cliente queda con dos ventas y la cobranza con dos saldos.
     * Con la MISMA llave, el peor caso es un `409` que la reconciliación por
     * `GET` del subidor resuelve sola, sin duplicar.
     *
     * El guardado de una corrección llama `clearFailure` y **nada más**
     * (`RoomVentaLocalCorreccionAdapter`), y hay prueba que lo sostiene:
     * `CorreccionIdempotenciaTest` — la llave es igual entre intentos e igual
     * al `LOCAL_SALE_ID`, y la columna `IDEMPOTENCY_KEY` sigue `NULL`.
     * Reintroducir la rotación ahí es uno de los rojos sembrados de la Task 6.
     *
     * Medido en la ronda de arreglo 1 de la Task 6b: **hoy no lo llama nadie
     * en producción** — sólo sus propias pruebas
     * (`RoomUploadFailureRepositoryTest`). Sobrevive del flujo legado de
     * editar-y-reintentar, que ya no existe. Borrarlo es una limpieza de una
     * línea que no cabe en este arreglo; mientras tanto, este aviso es lo que
     * impide que alguien lo cablee creyendo que es el camino correcto.
     *
     * @return the freshly-minted key — callers may persist it, log it, or
     *         pass it into a worker enqueue payload.
     */
    suspend fun resetForEditAndRetry(saleId: String): String
}
