package com.example.msp_app.core.database.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.SaleClaimSnapshot

@Dao
interface LocalSaleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(localsale: LocalSaleEntity)

    @Query(
        """
        SELECT * FROM local_sale
        WHERE FECHA_VENTA >= datetime('now', '-7 days')
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getAllSales(): List<LocalSaleEntity>

    @Query("SELECT * FROM local_sale WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getSaleById(saleId: String): LocalSaleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleImage(saleImage: LocalSaleImageEntity)

    /**
     * Inserta la venta local y todas sus imágenes en una sola transacción
     * atómica. Money-path: si cualquier insert falla a mitad de la secuencia
     * (p.ej. una imagen viola la FK), Room revierte TODO — nunca queda una
     * venta a medias sin sus imágenes (partial-write). `@Transaction` es
     * anotación de método, no DDL: no altera el schema (v27) ni el
     * identityHash. Preserva el orden observable del caso feliz (venta primero,
     * luego imágenes en orden de la lista).
     */
    @Transaction
    suspend fun insertSaleWithImages(sale: LocalSaleEntity, images: List<LocalSaleImageEntity>) {
        insertSale(sale)
        images.forEach { insertSaleImage(it) }
    }

    @Query(
        "SELECT LOCAL_SALE_IMAGE_ID, LOCAL_SALE_ID, IMAGE_URI, FECHA_SUBIDA, SERVER_UUID FROM sale_image WHERE LOCAL_SALE_ID = :saleId ORDER BY FECHA_SUBIDA"
    )
    suspend fun getImagesForSale(saleId: String): List<LocalSaleImageEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_ID = :saleId")
    suspend fun deleteImagesForSale(saleId: String)

    @Query("UPDATE local_sale SET ENVIADO = :enviado WHERE LOCAL_SALE_ID = :saleId")
    suspend fun updateSaleStatus(saleId: String, enviado: Boolean)

    /**
     * Persists the upload failure on the local sale. Called from the worker's
     * error-handling path. The first informative error wins over a later
     * less-informative one (see UploadFailureRepository for the precedence
     * rule); this query is the bare write — precedence logic is upstream.
     */
    @Query(
        """
        UPDATE local_sale SET
            LAST_UPLOAD_HTTP_CODE = :httpCode,
            LAST_UPLOAD_ERROR_CODE = :errorCode,
            LAST_UPLOAD_ERROR_MESSAGE = :errorMessage,
            LAST_UPLOAD_AT = :at,
            LAST_UPLOAD_PERMANENT = :permanent
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun updateUploadFailure(
        saleId: String,
        httpCode: Int,
        errorCode: String?,
        errorMessage: String?,
        at: Long,
        permanent: Boolean
    )

    /**
     * Clears upload-failure tracking — called when the worker succeeds, or
     * when the user edits a failed sale (so the UI doesn't show a stale
     * error after a corrected resubmit).
     */
    @Query(
        """
        UPDATE local_sale SET
            LAST_UPLOAD_HTTP_CODE = NULL,
            LAST_UPLOAD_ERROR_CODE = NULL,
            LAST_UPLOAD_ERROR_MESSAGE = NULL,
            LAST_UPLOAD_AT = NULL,
            LAST_UPLOAD_PERMANENT = NULL
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun clearUploadFailure(saleId: String)

    /**
     * Sets a fresh Idempotency-Key on the sale. Used by edit-and-retry so the
     * corrected body doesn't collide with a cached 2xx (which would otherwise
     * return 422 idempotency_key_mismatch from the server middleware).
     */
    @Query("UPDATE local_sale SET IDEMPOTENCY_KEY = :key WHERE LOCAL_SALE_ID = :saleId")
    suspend fun updateIdempotencyKey(saleId: String, key: String)

    @Query(
        """
        SELECT * FROM local_sale
        WHERE ENVIADO = :enviado
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getSalesByStatus(enviado: Boolean): List<LocalSaleEntity>

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_IMAGE_ID = :imageId")
    suspend fun deleteImageById(imageId: String)

    @Query("DELETE FROM sale_image WHERE LOCAL_SALE_IMAGE_ID IN (:imageIds)")
    suspend fun deleteImagesByIds(imageIds: List<String>)

    @Query("UPDATE sale_image SET SERVER_UUID = :serverUuid WHERE LOCAL_SALE_IMAGE_ID = :imageId")
    suspend fun updateImageServerUuid(imageId: String, serverUuid: String)

    // Un parámetro por columna editable de `local_sale` — Room enlaza cada
    // `:nombreParametro` de la query por NOMBRE de parámetro Kotlin, así que
    // envolver esto en un objeto (para bajar el conteo de LongParameterList)
    // exigiría el binding "entity parcial" de Room (`@Update(entity = ...)`
    // con una data class de columnas), que es un cambio de forma de
    // persistencia — no un simple refactor de estilo — para una query de
    // edición de venta ya en uso. Se prefiere suprimir con esta nota a
    // arriesgar el comportamiento de escritura.
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE local_sale SET
            NOMBRE_CLIENTE = :nombreCliente,
            FECHA_VENTA = :fechaVenta,
            LATITUD = :latitud,
            LONGITUD = :longitud,
            DIRECCION = :direccion,
            PARCIALIDAD = :parcialidad,
            ENGANCHE = :enganche,
            TELEFONO = :telefono,
            FREC_PAGO = :frecPago,
            AVAL_O_RESPONSABLE = :avalOResponsable,
            NOTA = :nota,
            DIA_COBRANZA = :diaCobranza,
            PRECIO_TOTAL = :precioTotal,
            TIEMPO_A_CORTO_PLAZOMESES = :tiempoACortoPlazoMeses,
            MONTO_A_CORTO_PLAZO = :montoACortoPlazo,
            MONTO_DE_CONTADO = :montoDeContado,
            ENVIADO = :enviado,
            NUMERO = :numero,
            COLONIA = :colonia,
            POBLACION = :poblacion,
            CIUDAD = :ciudad,
            TIPO_VENTA = :tipoVenta,
            ZONA_CLIENTE_ID = :zonaClienteId,
            ZONA_CLIENTE = :zonaCliente,
            CLIENTE_ID = :clienteId
        WHERE LOCAL_SALE_ID = :localSaleId
    """
    )
    suspend fun updateSaleFields(
        localSaleId: String,
        nombreCliente: String,
        fechaVenta: String,
        latitud: Double,
        longitud: Double,
        direccion: String,
        parcialidad: Double,
        enganche: Double?,
        telefono: String,
        frecPago: String,
        avalOResponsable: String?,
        nota: String?,
        diaCobranza: String,
        precioTotal: Double,
        tiempoACortoPlazoMeses: Int,
        montoACortoPlazo: Double,
        montoDeContado: Double,
        enviado: Boolean,
        numero: String?,
        colonia: String?,
        poblacion: String?,
        ciudad: String?,
        tipoVenta: String?,
        zonaClienteId: Int?,
        zonaCliente: String?,
        clienteId: Int?
    )

    // ─────────────────────────────────────────────────────────────────────
    // Reclamo de edición (mecanismo de la carrera). Cada método de abajo es
    // UN SOLO `UPDATE` — SQLite serializa las sentencias, ahí vive la
    // atomicidad, no en un mutex de Kotlin (los `Flow` de Room de la UI no
    // pasan por ninguno).
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reclama la venta para edición con un arrendamiento de [leaseMs] desde
     * [now]. Devuelve 1 si el reclamo se tomó; 0 si la venta ya se envió, si
     * tuvo un fallo permanente (el servidor ya resguardó el intento aunque
     * `ENVIADO` siga en 0), o si ya hay un reclamo vigente y no vencido.
     * `0` = el editor ni se abre.
     */
    @Query(
        """
        UPDATE local_sale SET EDIT_CLAIM_ID = :claimId, EDIT_CLAIMED_AT = :now
        WHERE LOCAL_SALE_ID = :saleId
          AND ENVIADO = 0
          AND (LAST_UPLOAD_PERMANENT IS NULL OR LAST_UPLOAD_PERMANENT = 0)
          AND (EDIT_CLAIM_ID IS NULL OR EDIT_CLAIMED_AT <= :now - :leaseMs)
        """
    )
    suspend fun claimForEdit(saleId: String, claimId: String, now: Long, leaseMs: Long): Int

    /**
     * Suelta el reclamo (cancelar la corrección). Sólo el dueño del reclamo
     * puede soltarlo: si [claimId] ya no coincide con el vigente (venció y
     * otro lo tomó), no toca nada.
     */
    @Query(
        """
        UPDATE local_sale SET EDIT_CLAIM_ID = NULL, EDIT_CLAIMED_AT = NULL
        WHERE LOCAL_SALE_ID = :saleId AND EDIT_CLAIM_ID = :claimId
        """
    )
    suspend fun releaseClaim(saleId: String, claimId: String): Int

    /**
     * El guardia del guardado (mecanismo, paso 4) — va PRIMERO dentro de la
     * transacción que hace el commit de la corrección. 0 filas: la venta ya
     * se envió, o el reclamo ya no es el tuyo (venció y el subidor se la
     * llevó) → el llamador lanza y Room revierte la transacción entera, sin
     * escribir nada. 1 fila: la corrección gana — sube `REVISION` y cierra
     * el reclamo en la misma sentencia.
     */
    @Query(
        """
        UPDATE local_sale SET EDIT_CLAIM_ID = NULL, EDIT_CLAIMED_AT = NULL, REVISION = REVISION + 1
        WHERE LOCAL_SALE_ID = :saleId AND EDIT_CLAIM_ID = :claimId AND ENVIADO = 0
        """
    )
    suspend fun commitEditGuard(saleId: String, claimId: String): Int

    /**
     * Lo llama el subidor cuando el POST triunfa: marca `ENVIADO=1` y cierra
     * cualquier reclamo vigente en la MISMA sentencia (un solo `UPDATE`, así
     * que es atómico sin necesitar `@Transaction`). Si el guardado del
     * usuario estaba a medio camino, su guardia (`commitEditGuard`) va a leer
     * `ENVIADO=1` y devolver 0 filas → rollback total, "Ya se envió". El
     * servidor manda: el teléfono nunca queda con una corrección fantasma de
     * una venta que ya viajó.
     */
    @Query(
        """
        UPDATE local_sale SET
            ENVIADO = 1,
            EDIT_CLAIM_ID = NULL,
            EDIT_CLAIMED_AT = NULL
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun markSentAndCloseEdit(saleId: String)

    /**
     * Ventas subibles por el barrido: no enviadas y sin reclamo vigente
     * (NULL, o vencido según [leaseMs] desde [now]). Reemplaza a
     * `getSalesByStatus(false)` en el barrido (mecanismo, paso 6): sin esto,
     * `LocalSalesPendingSynchronizer` reencolaría con `replace=true` en cada
     * apertura de sesión mientras el dueño está corrigiendo, reseteando el
     * backoff y peleándose con el fence del reclamo.
     */
    @Query(
        """
        SELECT * FROM local_sale
        WHERE ENVIADO = 0
          AND (EDIT_CLAIM_ID IS NULL OR EDIT_CLAIMED_AT <= :now - :leaseMs)
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getUploadableSales(now: Long, leaseMs: Long): List<LocalSaleEntity>

    /**
     * Snapshot barato de `(EDIT_CLAIM_ID, REVISION, ENVIADO)` — ver
     * [SaleClaimSnapshot].
     */
    @Query("SELECT EDIT_CLAIM_ID, REVISION, ENVIADO FROM local_sale WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getSaleClaimSnapshot(saleId: String): SaleClaimSnapshot?
}
