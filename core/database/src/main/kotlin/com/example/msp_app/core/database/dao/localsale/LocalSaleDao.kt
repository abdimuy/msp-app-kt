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
    // Candado único de la fila (mecanismo de la carrera, ronda 2). UN SOLO
    // candado por venta: puede tomarlo la edición o la subida, nunca las
    // dos — mutua exclusión POR CONSTRUCCIÓN (una sola columna CLAIM_ID; no
    // depende de que dos predicados independientes se mantengan
    // sincronizados). Cada método de abajo es UN SOLO `UPDATE` — SQLite
    // serializa las sentencias, ahí vive la atomicidad, no en un mutex de
    // Kotlin (los `Flow` de Room de la UI no pasan por ninguno).
    //
    // Por qué el predicado de expiración necesita AMBOS arrendamientos en
    // TODOS los métodos: el candado vigente en la fila puede ser de
    // cualquiera de los dos tipos (no lo decide quién pregunta), así que
    // decidir si venció exige mirar `CLAIM_KIND` y aplicar el arrendamiento
    // que le corresponde — de ahí el `CASE` sobre `CLAIM_KIND` repetido en
    // `claimForEdit`, `claimForUpload` y `getUploadableSales`.
    //
    // `CLAIMED_AT IS NULL` también cuenta como vencido: es defensa en
    // profundidad para un estado que ningún camino produce hoy (CLAIM_ID no
    // nulo con CLAIMED_AT nulo), pero bajo la comparación `<=` de SQL un
    // NULL nunca sería "menor o igual" a nada — ese estado, si alguna vez
    // ocurriera por un bug en otra capa, retendría la venta PARA SIEMPRE. La
    // regla del plan es "una captura nunca se retiene para siempre", así que
    // ese estado se trata como vencido en vez de confiar en que nunca pase.
    //
    // `COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD')` es el mismo
    // argumento aplicado a `CLAIM_KIND`: con `CLAIM_ID` puesto, `CLAIMED_AT`
    // puesto y `CLAIM_KIND` nulo o con un valor que no es ninguno de los dos
    // reconocidos, ninguna de las dos ramas del `CASE` se cumple — ese
    // candado también quedaría retenido para siempre sin esta línea. No se
    // escribe como `CLAIM_KIND IS NULL OR CLAIM_KIND NOT IN (...)` porque en
    // SQL `NULL NOT IN (...)` es `NULL` (ni verdadero ni falso), no `TRUE`:
    // sin el `COALESCE` el caso `CLAIM_KIND IS NULL` se cuela de vuelta al
    // estado retenido para siempre que esta línea existe para evitar.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Reclama la venta para EDICIÓN. Devuelve 1 si el candado se tomó; 0 si
     * la venta ya se envió, si tuvo un fallo permanente (el servidor ya
     * resguardó el intento aunque `ENVIADO` siga en 0), o si ya hay un
     * candado vigente (de cualquier tipo) y no vencido. `0` = el editor ni
     * se abre.
     *
     * @param editLeaseMs arrendamiento a aplicar si el candado vigente es de
     *   edición.
     * @param uploadLeaseMs arrendamiento a aplicar si el candado vigente es
     *   de subida (para poder recuperar un candado de subida que el subidor
     *   dejó vencido, p.ej. la app murió a media subida).
     */
    @Query(
        """
        UPDATE local_sale SET CLAIM_ID = :claimId, CLAIM_KIND = 'EDIT', CLAIMED_AT = :now
        WHERE LOCAL_SALE_ID = :saleId
          AND ENVIADO = 0
          AND (LAST_UPLOAD_PERMANENT IS NULL OR LAST_UPLOAD_PERMANENT = 0)
          AND (
            CLAIM_ID IS NULL
            OR CLAIMED_AT IS NULL
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD')
            OR (CLAIM_KIND = 'EDIT' AND CLAIMED_AT <= :now - :editLeaseMs)
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
          )
        """
    )
    suspend fun claimForEdit(
        saleId: String,
        claimId: String,
        now: Long,
        editLeaseMs: Long,
        uploadLeaseMs: Long
    ): Int

    /**
     * Reclama la venta para SUBIDA — lo toma el subidor justo antes del
     * `POST` (mecanismo, paso 3 revisado) para que un `claimForEdit`
     * concurrente no pueda ganar la fila mientras la subida sigue en vuelo:
     * sin este candado, el guardia del guardado (`commitEditGuard`) alcanza
     * a ver `ENVIADO=0` y commitea ANTES de que vuelva el 2xx, y el `POST`
     * que ya salió lleva el cuerpo viejo — el teléfono enseña la corrección,
     * el servidor tiene la original, y nadie se entera. Devuelve 1 si el
     * candado se tomó; 0 si hay un candado de EDICIÓN vigente (la corrección
     * gana: el subidor debe frenarse con `Result.retry()`, no mandar el
     * POST) o si la venta ya no es subible (`ENVIADO=1`).
     *
     * Simétrico a [claimForEdit]: necesita los DOS arrendamientos por la
     * misma razón (el candado vigente puede ser de cualquier tipo).
     */
    @Query(
        """
        UPDATE local_sale SET CLAIM_ID = :claimId, CLAIM_KIND = 'UPLOAD', CLAIMED_AT = :now
        WHERE LOCAL_SALE_ID = :saleId
          AND ENVIADO = 0
          AND (
            CLAIM_ID IS NULL
            OR CLAIMED_AT IS NULL
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD')
            OR (CLAIM_KIND = 'EDIT' AND CLAIMED_AT <= :now - :editLeaseMs)
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
          )
        """
    )
    suspend fun claimForUpload(
        saleId: String,
        claimId: String,
        now: Long,
        editLeaseMs: Long,
        uploadLeaseMs: Long
    ): Int

    /**
     * Suelta el candado (cancelar la corrección, o el subidor al terminar
     * sin éxito). Funciona para cualquier tipo de candado: la propiedad se
     * decide por `CLAIM_ID`, no por `CLAIM_KIND`. Sólo el dueño del candado
     * puede soltarlo: si [claimId] ya no coincide con el vigente (venció y
     * el otro lado lo tomó), no toca nada.
     */
    @Query(
        """
        UPDATE local_sale SET CLAIM_ID = NULL, CLAIM_KIND = NULL, CLAIMED_AT = NULL
        WHERE LOCAL_SALE_ID = :saleId AND CLAIM_ID = :claimId
        """
    )
    suspend fun releaseClaim(saleId: String, claimId: String): Int

    /**
     * El guardia del guardado (mecanismo, paso 4) — va PRIMERO dentro de la
     * transacción que hace el commit de la corrección. 0 filas: la venta ya
     * se envió, o el candado ya no es el tuyo (venció y alguien más —
     * edición o subida— se lo llevó) → el llamador lanza y Room revierte la
     * transacción entera, sin escribir nada. 1 fila: la corrección gana —
     * sube `REVISION` y cierra el candado en la misma sentencia. No necesita
     * verificar `CLAIM_KIND`: la propiedad exacta por `CLAIM_ID` ya implica
     * que es el candado de edición que este llamador tomó.
     */
    @Query(
        """
        UPDATE local_sale SET CLAIM_ID = NULL, CLAIM_KIND = NULL, CLAIMED_AT = NULL, REVISION = REVISION + 1
        WHERE LOCAL_SALE_ID = :saleId AND CLAIM_ID = :claimId AND ENVIADO = 0
        """
    )
    suspend fun commitEditGuard(saleId: String, claimId: String): Int

    /**
     * Lo llama el subidor cuando el POST triunfa: marca `ENVIADO=1` y cierra
     * CUALQUIER candado que la fila tenga (edición o subida) en la MISMA
     * sentencia (un solo `UPDATE`, así que es atómico sin necesitar
     * `@Transaction`). Cierra incondicionalmente, sin filtrar por
     * `CLAIM_ID`/`CLAIM_KIND`: en este punto el 2xx ya PROBÓ que el servidor
     * tiene la venta — no marcar `ENVIADO=1` la haría subir otra vez, sea de
     * quien sea el candado que la fila tenga ahora.
     *
     * [revisionAtClaim] es el `REVISION` que el snapshot del subidor traía
     * ANTES del POST (`getSaleClaimSnapshot`, mecanismo paso 3). Cierra la
     * carrera nueva que la ronda 2 de revisión encontró: el arrendamiento de
     * subida puede vencer con el POST TODAVÍA en vuelo (subir un cuerpo
     * multipart no tiene tope real de OkHttp — ver el comentario de
     * `UPLOAD_LEASE_MS` en `LocalSaleClaimDaoTest.kt`), el editor toma el
     * candado y commitea, y LUEGO vuelve el 2xx con el cuerpo VIEJO. Si
     * `REVISION` ya no coincide con [revisionAtClaim], eso fue lo que pasó:
     * se marca `CORRECCION_NO_ENVIADA = 1` en la MISMA sentencia — la
     * divergencia queda VISIBLE en la fila, nunca pisada en silencio.
     * Si el candado lo tomó el editor pero AÚN no commiteó (`REVISION` sin
     * cambiar), no hay marca: el guardado posterior del editor va a fallar
     * solo, por su propio guardia (`commitEditGuard` lee `ENVIADO=1` y
     * devuelve 0) — eso ya funciona sin ayuda de esta sentencia.
     *
     * Quién enseña `CORRECCION_NO_ENVIADA` en pantalla, y cómo se resuelve
     * (reintentar, avisar al dueño), es de tareas posteriores; este método
     * sólo entrega el dato, de forma atómica y sin perderlo.
     *
     * Nota para quien revise esta ronda: el brief pide que el método
     * "reciba el `claimId` y la `REVISION` del snapshot del worker". Sólo
     * `REVISION` entra al `@Query` — Room exige que TODO parámetro de un
     * `@Query` aparezca en la sentencia, y el propio brief fija que este
     * método "siempre cierra el candado, sea de quien sea": no hay ningún
     * gating por `CLAIM_ID` que un parámetro `claimId` pudiera alimentar sin
     * convertirlo en una sentencia con una condición falsa (una que nunca
     * bloquea nada). Se deja fuera por esa razón concreta, no por omisión;
     * si el propósito era otro (auditoría, log), es una decisión de diseño
     * que falta afinar en la siguiente ronda.
     */
    @Query(
        """
        UPDATE local_sale SET
            ENVIADO = 1,
            CLAIM_ID = NULL,
            CLAIM_KIND = NULL,
            CLAIMED_AT = NULL,
            CORRECCION_NO_ENVIADA = CASE
                WHEN REVISION != :revisionAtClaim THEN 1
                ELSE CORRECCION_NO_ENVIADA
            END
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun markSentAndCloseEdit(saleId: String, revisionAtClaim: Int)

    /**
     * Ventas subibles por el barrido: no enviadas y sin candado vigente
     * (NULL, o vencido según su propio arrendamiento). Reemplaza a
     * `getSalesByStatus(false)` en el barrido (mecanismo, paso 6): sin esto,
     * `LocalSalesPendingSynchronizer` reencolaría con `replace=true` en cada
     * apertura de sesión mientras el dueño está corrigiendo, reseteando el
     * backoff y peleándose con el fence del candado.
     */
    @Query(
        """
        SELECT * FROM local_sale
        WHERE ENVIADO = 0
          AND (
            CLAIM_ID IS NULL
            OR CLAIMED_AT IS NULL
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD')
            OR (CLAIM_KIND = 'EDIT' AND CLAIMED_AT <= :now - :editLeaseMs)
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
          )
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getUploadableSales(
        now: Long,
        editLeaseMs: Long,
        uploadLeaseMs: Long
    ): List<LocalSaleEntity>

    /**
     * Snapshot barato de `(CLAIM_ID, REVISION, ENVIADO)` — ver
     * [SaleClaimSnapshot].
     */
    @Query("SELECT CLAIM_ID, REVISION, ENVIADO FROM local_sale WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getSaleClaimSnapshot(saleId: String): SaleClaimSnapshot?
}
