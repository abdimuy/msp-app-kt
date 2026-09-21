package com.example.msp_app.core.database.dao.localsale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleImageEntity
import com.example.msp_app.core.database.entities.RemoteCorrectionSnapshot
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
    // `claimForUpload` y `getUploadableSales`: el candado vigente en la fila
    // puede ser de cualquiera de los dos tipos (no lo decide quién
    // pregunta), así que decidir si venció exige mirar `CLAIM_KIND` y
    // aplicar el arrendamiento que le corresponde. `claimForEdit` es la
    // EXCEPCIÓN (Task 3, decisión del orquestador): un candado `EDIT` vivo
    // ya no tiene frontera de vencimiento para este método — es reentrante
    // sin condición —, así que sólo necesita `uploadLeaseMs`.
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
     * resguardó el intento aunque `ENVIADO` siga en 0), o si hay un candado
     * de SUBIDA vigente y no vencido. `0` = el editor ni se abre.
     *
     * **Reentrante para EDICIÓN** (decisión del orquestador, Task 3 del plan
     * "Corregir una venta antes de que suba"): un candado `EDIT` VIVO NO
     * bloquea — se TOMA de nuevo, acuñando un `CLAIM_ID` nuevo. El dominio
     * (`evaluarCorregibilidad`, `:feature:ventaCorreccion`) ya trata un
     * `EDIT` vivo como `Corregible`; antes de este cambio el DAO no lo
     * seguía, así que si la app moría con el editor abierto, el dueño no
     * podía corregir su propia venta durante los 30 minutos del
     * arrendamiento. En el alcance de este plan (un teléfono, una venta que
     * nunca salió) un `EDIT` vivo sólo puede ser una sesión anterior del
     * editor en el MISMO teléfono. Consecuencia: la sesión vieja PIERDE su
     * `claimId` — su `commitEditGuard` posterior devuelve 0 filas y no
     * escribe nada (comportamiento correcto, cubierto en
     * `LocalSaleClaimDaoTest`). `NUNCA` toma un candado de SUBIDA vigente:
     * ahí sí gana el subidor, mismo criterio que antes.
     *
     * @param uploadLeaseMs arrendamiento a aplicar si el candado vigente es
     *   de subida (para poder recuperar un candado de subida que el subidor
     *   dejó vencido, p.ej. la app murió a media subida). No hay
     *   `editLeaseMs`: un candado `EDIT` ya no tiene frontera de vencimiento
     *   para ESTE método — siempre se puede retomar.
     * @param remoteLeaseMs arrendamiento a aplicar si el candado vigente es
     *   de corrección REMOTA (nivel 2, eje 5). A diferencia de `EDIT`, un
     *   candado `REMOTE` vivo SÍ bloquea — el worker remoto está a media
     *   secuencia de tres peticiones contra el servidor, y el editor no
     *   puede commitear mientras esa secuencia está en vuelo: cerraría la
     *   bandera de una corrección que ya no es la vigente. Vencido, se
     *   recupera igual que un `UPLOAD` vencido.
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
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD', 'REMOTE')
            OR CLAIM_KIND = 'EDIT'
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
            OR (CLAIM_KIND = 'REMOTE' AND CLAIMED_AT <= :now - :remoteLeaseMs)
          )
        """
    )
    suspend fun claimForEdit(
        saleId: String,
        claimId: String,
        now: Long,
        uploadLeaseMs: Long,
        remoteLeaseMs: Long
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
     * Simétrico a [claimForEdit]: necesita los TRES arrendamientos por la
     * misma razón (el candado vigente puede ser de cualquier tipo, incluido
     * `REMOTE` desde el nivel 2 — en la práctica no debería coincidir, ya
     * que `REMOTE` sólo se acuña sobre `ENVIADO = 1` y este método exige
     * `ENVIADO = 0`, pero el predicado lo reconoce por la misma defensa en
     * profundidad que ya aplica a `EDIT`/`UPLOAD`).
     */
    @Query(
        """
        UPDATE local_sale SET CLAIM_ID = :claimId, CLAIM_KIND = 'UPLOAD', CLAIMED_AT = :now
        WHERE LOCAL_SALE_ID = :saleId
          AND ENVIADO = 0
          AND (
            CLAIM_ID IS NULL
            OR CLAIMED_AT IS NULL
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD', 'REMOTE')
            OR (CLAIM_KIND = 'EDIT' AND CLAIMED_AT <= :now - :editLeaseMs)
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
            OR (CLAIM_KIND = 'REMOTE' AND CLAIMED_AT <= :now - :remoteLeaseMs)
          )
        """
    )
    suspend fun claimForUpload(
        saleId: String,
        claimId: String,
        now: Long,
        editLeaseMs: Long,
        uploadLeaseMs: Long,
        remoteLeaseMs: Long
    ): Int

    /**
     * Reclama la venta para CORRECCIÓN REMOTA — el tercer tipo de candado
     * (nivel 2, eje 5). Lo toma `RemoteSaleCorrectionWorker` ANTES de leer
     * el cuerpo (paso 0 de la corrida), sobre una venta que YA se envió
     * (`ENVIADO = 1`): sin este candado, el editor podría commitear una
     * corrección local a media secuencia de las tres peticiones y la corrida
     * en vuelo cerraría la bandera de una corrección que ya no es la
     * vigente. Devuelve 1 si el candado se tomó; 0 si hay un candado de
     * EDICIÓN o SUBIDA vigente (el editor/subidor ganan: el worker remoto se
     * frena con `Result.retry()` SIN tocar la red), si ya hay otro candado
     * `REMOTE` vigente (evita que dos ejecuciones del mismo worker, o un
     * reintento superpuesto, corran la secuencia dos veces a la vez), o si
     * la venta no es `ENVIADO = 1` (una venta sin enviar no tiene corrección
     * remota que correr — ese camino sigue siendo el `POST` de creación).
     *
     * NO es reentrante sobre `REMOTE`: a diferencia de `claimForEdit` sobre
     * `EDIT`, dos invocaciones del worker sobre la misma venta no deben
     * pisarse — cada una debe esperar a que la anterior suelte el candado
     * (al terminar, o al vencer su arrendamiento).
     *
     * Necesita los TRES arrendamientos por la misma razón que
     * [claimForUpload]: el candado vigente puede ser de cualquier tipo.
     */
    @Query(
        """
        UPDATE local_sale SET CLAIM_ID = :claimId, CLAIM_KIND = 'REMOTE', CLAIMED_AT = :now
        WHERE LOCAL_SALE_ID = :saleId
          AND ENVIADO = 1
          AND (
            CLAIM_ID IS NULL
            OR CLAIMED_AT IS NULL
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD', 'REMOTE')
            OR (CLAIM_KIND = 'EDIT' AND CLAIMED_AT <= :now - :editLeaseMs)
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
            OR (CLAIM_KIND = 'REMOTE' AND CLAIMED_AT <= :now - :remoteLeaseMs)
          )
        """
    )
    suspend fun claimForRemote(
        saleId: String,
        claimId: String,
        now: Long,
        editLeaseMs: Long,
        uploadLeaseMs: Long,
        remoteLeaseMs: Long
    ): Int

    /**
     * RENUEVA el arrendamiento de CUALQUIER candado sin cambiar de dueño —
     * el LATIDO (paso 4 del mecanismo del nivel 1, implementado en
     * `PendingLocalSalesWorker`; el nivel 2 lo reutiliza para el candado
     * `REMOTE` de `RemoteSaleCorrectionWorker`, mismo argumento: una
     * secuencia de tres peticiones sobre una red mala tampoco tiene tope
     * real). Mientras la operación sigue en vuelo, el worker llama esto cada
     * `*_HEARTBEAT_MS` para que una operación lenta pero VIVA no deje caducar
     * su propio candado.
     *
     * **Generalizado desde `renewUploadClaim` (nivel 2, Task A1):** el
     * nombre viejo fijaba `CLAIM_KIND = 'UPLOAD'` en el `WHERE` porque sólo
     * el subidor lo llamaba. El guardia real siempre fue `CLAIM_ID =
     * :claimId` — un `claimId` es un UUID fresco por cada reclamo, así que
     * coincidir con el de la fila YA prueba que quien llama es el dueño
     * legítimo del candado que sea. Filtrar además por `CLAIM_KIND` no
     * aportaba seguridad extra, sólo acoplaba el método a un solo tipo de
     * candado — y con el candado `REMOTE` del nivel 2 hace falta el mismo
     * latido para un tipo distinto. Devuelve 0 cuando el candado ya no es
     * suyo (caducó y alguien más lo tomó, o la operación ya terminó y el
     * candado se cerró). Ese 0 NO se recupera reclamando de nuevo: el latido
     * JAMÁS re-toma un candado ajeno — eso le robaría la fila a quien la
     * tiene, exactamente lo contrario de lo que este mecanismo defiende.
     *
     * **No mira el arrendamiento, y es deliberado** (heredado de
     * `renewUploadClaim`): si el proceso se congeló más que el arrendamiento,
     * esta sentencia REVIVE un candado propio que ya había caducado. Mientras
     * NADIE más haya tomado la fila, la operación en vuelo sigue siendo su
     * dueña legítima; si alguien SÍ la tomó, el `CLAIM_ID` ya no coincide y
     * esto no hace nada.
     */
    @Query(
        """
        UPDATE local_sale SET CLAIMED_AT = :now
        WHERE LOCAL_SALE_ID = :saleId AND CLAIM_ID = :claimId
        """
    )
    suspend fun renewClaim(saleId: String, claimId: String, now: Long): Int

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
     * Ancla la `REVISION` del cuerpo que va a viajar, la PRIMERA vez que se
     * emite un `POST` para esta venta — y sólo la primera: el
     * `REVISION_POSTEADA IS NULL` del `WHERE` hace que un segundo intento no
     * pise el valor original. Devuelve 1 la vez que ancla, 0 después (y 0 no
     * es un error: significa que ya había ancla, que es justo lo que se
     * quiere).
     *
     * Lo llama `PendingLocalSalesWorker` inmediatamente ANTES de
     * `ventasApi.crearVenta`, con el `REVISION` del snapshot que tomó al
     * reclamar (que es el que el cuerpo lleva: el candado de subida impide
     * que nadie commitee entre el reclamo y el POST).
     *
     * Por qué existe, en una línea: sin ella, el camino del "2xx perdido" no
     * era observable. El servidor recibe el cuerpo original y la respuesta se
     * pierde; el dueño corrige; el siguiente intento recibe `409` y la
     * reconciliación por `GET` marca `ENVIADO=1` — y como la comparación de
     * [markSentAndCloseEdit] se hacía contra el snapshot de ESA segunda
     * corrida (que ya traía la corrección adentro), no había nada que marcar:
     * el teléfono enseñaba la corrección, el servidor tenía el original, y
     * nadie se enteraba. Anclado al PRIMER cuerpo posteado, los dos caminos
     * (2xx directo y reconciliación por `GET`) ven la misma divergencia.
     *
     * Deliberadamente CONSERVADOR: se ancla antes de mandar, así que un
     * `POST` que ni siquiera salió del teléfono (sin señal) también deja
     * ancla, y una corrección posterior que SÍ viajó puede terminar marcada.
     * Un falso positivo cuesta que la oficina revise una venta que estaba
     * bien; un falso negativo cuesta despachar una venta que el cliente no
     * pidió.
     */
    @Query(
        """
        UPDATE local_sale SET REVISION_POSTEADA = :revision
        WHERE LOCAL_SALE_ID = :saleId AND REVISION_POSTEADA IS NULL
        """
    )
    suspend fun recordPostedRevisionIfAbsent(saleId: String, revision: Int): Int

    /**
     * BORRA el ancla — y sólo la que puso ESTE intento (`REVISION_POSTEADA =
     * :revision` en el `WHERE`). Ronda de arreglo 1 de la Task 6b.
     *
     * Por qué hace falta: anclar antes del `POST` volvía marcable **el caso
     * estelar del plan** — el vendedor captura sin señal, el intento falla
     * porque no hay red, corrige, vuelve la señal y la venta sube corregida.
     * Todo salió bien y la fila quedaba con "La revisa la oficina". Un aviso
     * que aparece en casi toda corrección es un aviso que la oficina aprende
     * a ignorar, y entonces ya no protege del caso real.
     *
     * Así que el ancla sólo vale cuando de verdad PUDIERON salir bytes. Si el
     * fallo demuestra que **nunca hubo conexión**, el subidor llama esto y la
     * corrección siguiente no se marca. Qué excepciones cuentan como prueba
     * está enumerado —una por una, nunca `IOException` a secas— en
     * `PendingLocalSalesWorker.elFalloPruebaQueNoSalioNada`.
     *
     * **Las dos guardas, y por qué ninguna sobra:**
     * - `REVISION_POSTEADA = :revision` — no borra un ancla de otro valor.
     * - El llamador además sólo invoca esto si `recordPostedRevisionIfAbsent`
     *   devolvió 1 en ESTA corrida, o sea si el ancla es suya. Sin esa
     *   condición: el intento 1 manda bytes (ancla 0; el servidor los recibe
     *   y la respuesta se pierde), el dueño corrige, el intento 2 falla SIN
     *   conexión y borraría el ancla del intento 1 — el intento 3 subiría
     *   corregido, no marcaría nada, y el servidor seguiría con el cuerpo
     *   original. Ése es justo el falso negativo que esta columna existe para
     *   impedir.
     */
    @Query(
        """
        UPDATE local_sale SET REVISION_POSTEADA = NULL
        WHERE LOCAL_SALE_ID = :saleId AND REVISION_POSTEADA = :revision
        """
    )
    suspend fun clearPostedRevisionIfMine(saleId: String, revision: Int): Int

    /**
     * Lo llama el subidor cuando el POST triunfa: marca `ENVIADO=1` y cierra
     * CUALQUIER candado que la fila tenga (edición o subida) en la MISMA
     * sentencia (un solo `UPDATE`, así que es atómico sin necesitar
     * `@Transaction`). Cierra incondicionalmente, sin filtrar por
     * `CLAIM_ID`/`CLAIM_KIND`: en este punto el 2xx ya PROBÓ que el servidor
     * tiene la venta — no marcar `ENVIADO=1` la haría subir otra vez, sea de
     * quien sea el candado que la fila tenga ahora.
     *
     * **Contra qué se compara la `REVISION` (Task 6b, la regla vigente).** La
     * referencia es `REVISION_POSTEADA`: la `REVISION` del cuerpo que viajó
     * en el PRIMER `POST` emitido para esta venta
     * ([recordPostedRevisionIfAbsent]). Si la `REVISION` actual difiere de
     * ella, lo que el servidor tiene NO es lo que el teléfono enseña, y se
     * marca `CORRECCION_NO_ENVIADA = 1` en la MISMA sentencia — la
     * divergencia queda VISIBLE en la fila, nunca pisada en silencio. Eso
     * cubre los DOS caminos por los que esta sentencia se llama:
     *
     * - **2xx directo**: el arrendamiento de subida puede vencer con el POST
     *   TODAVÍA en vuelo (subir un cuerpo multipart no tiene tope real de
     *   OkHttp — ver el comentario de `UPLOAD_LEASE_MS` en
     *   `LocalSaleClaimDaoTest.kt`), el editor toma el candado y commitea, y
     *   LUEGO vuelve el 2xx con el cuerpo VIEJO.
     * - **Reconciliación por `GET`**: el primer POST SÍ llegó al servidor
     *   pero su respuesta se perdió; el dueño corrigió; el segundo intento
     *   recibió `409` y el `GET` encontró la venta. El servidor se quedó con
     *   el cuerpo original. Anclando a `REVISION_POSTEADA` esto se marca; con
     *   el snapshot de la corrida en curso (la regla anterior) quedaba
     *   invisible, porque dentro de ESA corrida nada cambiaba.
     *
     * [revisionAtClaim] —el `REVISION` del snapshot que el subidor tomó al
     * reclamar (`getSaleClaimSnapshot`)— queda como RESPALDO, vía
     * `COALESCE`: sólo se usa si la fila no tiene ancla, que en el camino del
     * subidor no puede pasar (se ancla justo antes del POST) pero mantiene la
     * sentencia con un comportamiento seguro si alguna vez se la llama desde
     * un camino que no posteó.
     *
     * Si el candado lo tomó el editor pero AÚN no commiteó (`REVISION` sin
     * cambiar), no hay marca: el guardado posterior del editor va a fallar
     * solo, por su propio guardia (`commitEditGuard` lee `ENVIADO=1` y
     * devuelve 0) — eso ya funciona sin ayuda de esta sentencia.
     *
     * La marca NUNCA se borra: el `ELSE CORRECCION_NO_ENVIADA` conserva una
     * divergencia ya señalada aunque ESTE `markSent` coincida.
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
                WHEN REVISION != COALESCE(REVISION_POSTEADA, :revisionAtClaim) THEN 1
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
     * `LocalSalesPendingSynchronizer` encolaría en cada apertura de sesión la
     * venta que el dueño está corrigiendo, y ese trabajo sólo puede chocar
     * contra el fence del candado: quema un reintento y se pelea con el
     * subidor.
     */
    @Query(
        """
        SELECT * FROM local_sale
        WHERE ENVIADO = 0
          AND (
            CLAIM_ID IS NULL
            OR CLAIMED_AT IS NULL
            OR COALESCE(CLAIM_KIND, '') NOT IN ('EDIT', 'UPLOAD', 'REMOTE')
            OR (CLAIM_KIND = 'EDIT' AND CLAIMED_AT <= :now - :editLeaseMs)
            OR (CLAIM_KIND = 'UPLOAD' AND CLAIMED_AT <= :now - :uploadLeaseMs)
            OR (CLAIM_KIND = 'REMOTE' AND CLAIMED_AT <= :now - :remoteLeaseMs)
          )
        ORDER BY FECHA_VENTA DESC
        """
    )
    suspend fun getUploadableSales(
        now: Long,
        editLeaseMs: Long,
        uploadLeaseMs: Long,
        remoteLeaseMs: Long
    ): List<LocalSaleEntity>

    /**
     * Snapshot barato de `(CLAIM_ID, REVISION, ENVIADO)` — ver
     * [SaleClaimSnapshot].
     */
    @Query("SELECT CLAIM_ID, REVISION, ENVIADO FROM local_sale WHERE LOCAL_SALE_ID = :saleId")
    suspend fun getSaleClaimSnapshot(saleId: String): SaleClaimSnapshot?

    // ─────────────────────────────────────────────────────────────────────
    // Plan "Corregir una venta DESPUÉS de que subió" (nivel 2), Task A1: el
    // estado del servidor y la cola de correcciones remotas. Cada método
    // sigue la misma regla que el bloque del candado único: UN SOLO
    // `UPDATE`/`SELECT` — la atomicidad la da SQLite, no un mutex de Kotlin.
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Snapshot barato de `(CLAIM_ID, REVISION, CORRECCION_REMOTA_PENDIENTE)`
     * — ver [RemoteCorrectionSnapshot]. Lo toma el worker de corrección
     * remota justo DESPUÉS de [claimForRemote] y ANTES de leer el cuerpo
     * (paso 1 de la corrida, "bajo el candado, ANTES de leer el cuerpo").
     */
    @Query(
        "SELECT CLAIM_ID, REVISION, CORRECCION_REMOTA_PENDIENTE FROM local_sale " +
            "WHERE LOCAL_SALE_ID = :saleId"
    )
    suspend fun getRemoteCorrectionSnapshot(saleId: String): RemoteCorrectionSnapshot?

    /**
     * Persiste lo que un `GET /v2/ventas/{id}` fresco acaba de reportar del
     * servidor. Se llama SIEMPRE que el `GET` responde — incluso si la
     * corrida termina en un terminal justo después —, porque es la única
     * fuente de la que el teléfono sabe si una venta ya enviada sigue en
     * borrador. No toca el candado ni la bandera de corrección pendiente:
     * es sólo la caché de lectura, sin efecto sobre la cola.
     */
    @Query(
        """
        UPDATE local_sale SET
            SERVER_SITUACION = :situacion,
            SERVER_SINCRONIZACION = :sincronizacion,
            SERVER_VERSION = :version,
            SERVER_STATE_AT = :at
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun marcarEstadoServidor(
        saleId: String,
        situacion: String,
        sincronizacion: String,
        version: Int,
        at: Long
    ): Int

    /**
     * Levanta la bandera de corrección remota pendiente — la llama
     * `GuardarCorreccion` en la MISMA transacción que el commit de la
     * corrección (mecanismo, eje 2: "El disparador"), y SÓLO cuando
     * `ENVIADO = 1`: si la venta no se ha enviado, es la venta del nivel 1 y
     * su camino sigue siendo el `POST` de creación — nunca pasa por aquí. El
     * `AND ENVIADO = 1` del `WHERE` es la misma disciplina que ya usan
     * `claimForEdit`/`claimForUpload` (una condición de negocio simple,
     * como defensa en profundidad si algún llamador futuro se equivoca de
     * momento para invocarlo) y no una regla nueva: la regla vive en el
     * llamador, que ya conoce `ENVIADO` porque acaba de leer la fila para
     * decidir si debía marcar esto.
     */
    @Query(
        "UPDATE local_sale SET CORRECCION_REMOTA_PENDIENTE = 1 " +
            "WHERE LOCAL_SALE_ID = :saleId AND ENVIADO = 1"
    )
    suspend fun marcarCorreccionRemotaPendiente(saleId: String): Int

    /**
     * El cierre de la corrida remota exitosa (los tres pasos en 2xx) — UN
     * SOLO `UPDATE`, misma forma que [markSentAndCloseEdit]: cierra
     * CUALQUIER candado incondicionalmente (`WHERE LOCAL_SALE_ID = :saleId`,
     * sin filtrar por `CLAIM_ID`), porque en este punto los 2xx ya PROBARON
     * que el servidor tiene la corrección — no hay ambigüedad de propiedad
     * que defender con un guardia de identidad, exactamente el mismo
     * argumento que ya justifica el cierre incondicional de
     * [markSentAndCloseEdit].
     *
     * La `REVISION` es la que SÍ hace de guardia: si el dueño corrigió OTRA
     * vez mientras la corrida seguía en vuelo, `REVISION` ya no es
     * [revisionEnviada] y la bandera se queda en 1 — la corrección nueva
     * viaja en la corrida siguiente y nunca se declara enviado algo que no
     * se envió. `REVISION_REMOTA_ENVIADA` se escribe siempre, sea cual sea
     * el resultado de la comparación: es el ancla contra la que la PRÓXIMA
     * corrida decidirá lo mismo.
     *
     * No recibe `claimId`: el brief original lo nombraba, pero — mismo caso
     * que [markSentAndCloseEdit] con su propio `claimId` — Room exige que
     * TODO parámetro de un `@Query` aparezca en la sentencia, y el cierre
     * incondicional no tiene ningún gating por `CLAIM_ID` que un parámetro
     * pudiera alimentar sin volverlo una condición que nunca bloquea nada.
     */
    @Query(
        """
        UPDATE local_sale SET
            CLAIM_ID = NULL, CLAIM_KIND = NULL, CLAIMED_AT = NULL,
            CORRECCION_REMOTA_PENDIENTE = CASE WHEN REVISION = :revisionEnviada THEN 0 ELSE 1 END,
            REVISION_REMOTA_ENVIADA = :revisionEnviada
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun cerrarCorreccionRemota(saleId: String, revisionEnviada: Int): Int

    /**
     * Cierra la corrida remota con un final TERMINAL — `409
     * venta_no_editable` (o el `GET` que ya adelantó `situacion != borrador`
     * / `sincronizacion = aplicada`), o `412 venta_version_conflicto`. UN
     * SOLO `UPDATE`: escribe la marca terminal (`estado`, siempre
     * `'RECHAZADA_ESTADO'` o `'CONFLICTO'`), los `SERVER_*` del `GET` más
     * fresco disponible, suelta el candado incondicionalmente y LIMPIA
     * `CORRECCION_REMOTA_PENDIENTE` — no porque la divergencia se haya
     * resuelto, sino porque la marca terminal la SUSTITUYE: la divergencia
     * sigue visible, sólo que ahora tiene nombre y ya no es reintentable.
     *
     * Que un `marcarCorreccionRemotaTerminal` deje la marca y un
     * `cerrarCorreccionRemota` posterior NO la borre está garantizado por
     * construcción: `cerrarCorreccionRemota` nunca toca
     * `CORRECCION_REMOTA_ESTADO`.
     */
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE local_sale SET
            CORRECCION_REMOTA_ESTADO = :estado,
            SERVER_SITUACION = :situacion,
            SERVER_SINCRONIZACION = :sincronizacion,
            SERVER_VERSION = :version,
            SERVER_STATE_AT = :at,
            CLAIM_ID = NULL, CLAIM_KIND = NULL, CLAIMED_AT = NULL,
            CORRECCION_REMOTA_PENDIENTE = 0
        WHERE LOCAL_SALE_ID = :saleId
        """
    )
    suspend fun marcarCorreccionRemotaTerminal(
        saleId: String,
        estado: String,
        situacion: String,
        sincronizacion: String,
        version: Int,
        at: Long
    ): Int

    /**
     * Ventas ya enviadas cuyo estado del servidor conviene refrescar (eje 1,
     * "Cuándo se refresca ... En el barrido de sesión"): `ENVIADO = 1`,
     * `SERVER_SINCRONIZACION` no es ya `'aplicada'` (una venta aplicada es
     * TERMINAL — no vuelve a cambiar y no se vuelve a pedir nunca) y
     * `SERVER_STATE_AT` tiene más de [maxAgeMs] o es `NULL` (nunca se leyó).
     * Acotado por [limit] — el mismo tope (`MAX_ITEMS_PER_SYNC`) que ya usan
     * los demás sincronizadores.
     */
    @Query(
        """
        SELECT * FROM local_sale
        WHERE ENVIADO = 1
          AND COALESCE(SERVER_SINCRONIZACION, '') != 'aplicada'
          AND (SERVER_STATE_AT IS NULL OR SERVER_STATE_AT <= :now - :maxAgeMs)
        ORDER BY FECHA_VENTA DESC
        LIMIT :limit
        """
    )
    suspend fun getVentasParaRefrescarEstado(
        now: Long,
        maxAgeMs: Long,
        limit: Int
    ): List<LocalSaleEntity>

    /**
     * Toda venta con la bandera de corrección remota pendiente puesta —
     * lo que el barrido de sesión usa para reencolar
     * `RemoteSaleCorrectionWorker` en cada apertura (misma regla que el
     * nivel 1, paso 9 de su mecanismo: "la fila manda, el encolado es
     * optimización").
     */
    @Query("SELECT * FROM local_sale WHERE CORRECCION_REMOTA_PENDIENTE = 1")
    suspend fun getVentasConCorreccionRemotaPendiente(): List<LocalSaleEntity>
}
