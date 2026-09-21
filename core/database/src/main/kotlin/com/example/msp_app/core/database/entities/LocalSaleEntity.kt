package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_sale",
    indices = [
        androidx.room.Index(value = ["FECHA_VENTA"]),
        androidx.room.Index(value = ["ZONA_CLIENTE_ID"])
    ]
)
class LocalSaleEntity(
    @PrimaryKey val LOCAL_SALE_ID: String,
    val NOMBRE_CLIENTE: String,
    val FECHA_VENTA: String,
    val LATITUD: Double,
    val LONGITUD: Double,
    val DIRECCION: String,
    val PARCIALIDAD: Double,
    val ENGANCHE: Double?,
    val TELEFONO: String,
    val FREC_PAGO: String,
    val AVAL_O_RESPONSABLE: String?,
    val NOTA: String?,
    val DIA_COBRANZA: String,
    val PRECIO_TOTAL: Double,
    val TIEMPO_A_CORTO_PLAZOMESES: Int,
    val MONTO_A_CORTO_PLAZO: Double,
    val MONTO_DE_CONTADO: Double,
    val ENVIADO: Boolean,
    val NUMERO: String? = null,
    val COLONIA: String? = null,
    val POBLACION: String? = null,
    val CIUDAD: String? = null,
    val TIPO_VENTA: String? = "CONTADO",
    val ZONA_CLIENTE_ID: Int? = null,
    val ZONA_CLIENTE: String? = null,
    val CLIENTE_ID: Int? = null,
    val LAST_UPLOAD_HTTP_CODE: Int? = null,
    val LAST_UPLOAD_ERROR_CODE: String? = null,
    val LAST_UPLOAD_ERROR_MESSAGE: String? = null,
    val LAST_UPLOAD_AT: Long? = null,
    val LAST_UPLOAD_PERMANENT: Boolean? = null,
    val IDEMPOTENCY_KEY: String? = null,
    // Candado único de la fila (plan "Corregir una venta antes de que suba",
    // migración 29→30, ronda 2: cierra la carrera edición-vs-subida que el
    // reclamo de solo-edición dejaba abierta). NULL = nadie tiene la venta.
    // Un candado vivo puede ser de edición (frena al subidor: gana la
    // corrección) o de subida (frena al editor: no se abre mientras el POST
    // sigue en vuelo). Mutuamente excluyentes POR CONSTRUCCIÓN: solo hay una
    // columna CLAIM_ID, así que nunca puede haber un reclamo de cada tipo a
    // la vez — no depende de que nadie recuerde chequear el otro tipo.
    // Vencido (arrendamiento propio por tipo, ver los *LeaseMs del DAO),
    // cualquiera de los dos lo recupera solo — una captura nunca se retiene
    // para siempre.
    val CLAIM_ID: String? = null,
    // 'EDIT' o 'UPLOAD'. Determina qué arrendamiento aplica para decidir si
    // CLAIM_ID venció.
    val CLAIM_KIND: String? = null,
    val CLAIMED_AT: Long? = null,
    // Correcciones commiteadas. Sólo sube; nunca se resetea.
    //
    // `@ColumnInfo(defaultValue = "0")`: sin esto, una instalación NUEVA (que
    // usa el CREATE TABLE que Room genera de esta entidad, no la migración)
    // declara `REVISION INTEGER NOT NULL` SIN default, mientras que una
    // instalación MIGRADA (vía el `ALTER TABLE ... DEFAULT 0` de
    // MIGRATION_29_30) sí lo tiene — dos esquemas distintos que
    // `runMigrationsAndValidate` no puede ver porque solo compara contra lo
    // que la entidad declara. Mismo patrón que `CobranzaSyncStateEntity.AFTER_ID`.
    @ColumnInfo(defaultValue = "0")
    val REVISION: Int = 0,
    // Ronda 2 de revisión: la subida de un cuerpo multipart no tiene tope
    // real de tiempo (OkHttp sólo mide inactividad entre bytes, no un total
    // — ver el comentario de `UPLOAD_LEASE_MS` en `LocalSaleClaimDaoTest`),
    // así que el arrendamiento de subida puede vencer con el POST TODAVÍA en
    // vuelo. Si eso pasa, el editor puede tomar el candado y commitear ANTES
    // de que vuelva el 2xx — y el 2xx que llega después trae el cuerpo
    // VIEJO. `markSentAndCloseEdit` detecta el caso (compara `REVISION`
    // contra `REVISION_POSTEADA`, ver abajo) y marca esta columna en la
    // MISMA sentencia: la divergencia queda visible en la fila en vez de
    // perderse en silencio. `@ColumnInfo(defaultValue = "0")` por el mismo
    // argumento que `REVISION`.
    @ColumnInfo(defaultValue = "0")
    val CORRECCION_NO_ENVIADA: Boolean = false,
    // La `REVISION` del cuerpo que viajó en el PRIMER `POST` emitido para
    // esta venta. Se escribe UNA sola vez
    // (`LocalSaleDao.recordPostedRevisionIfAbsent`, un `UPDATE` guardado por
    // `REVISION_POSTEADA IS NULL`) y nunca se pisa. `NULL` = todavía no ha
    // salido ningún `POST`.
    //
    // Existe porque `CORRECCION_NO_ENVIADA` comparaba contra el snapshot de
    // la corrida EN CURSO, y así el camino del "2xx perdido" quedaba ciego:
    // el servidor recibe el cuerpo original, la respuesta se pierde, el
    // dueño corrige, el siguiente intento recibe `409` y la reconciliación
    // por `GET` marca `ENVIADO=1` — todo dentro de una corrida en la que la
    // `REVISION` nunca cambió, así que no había divergencia que marcar
    // aunque el servidor se quedara con el cuerpo VIEJO. Anclando la
    // comparación al primer cuerpo POSTEADO, los dos caminos (2xx directo y
    // reconciliación por `GET`) ven la misma divergencia.
    //
    // Es CONSERVADOR a propósito: se escribe justo ANTES del `POST`, así que
    // un `POST` que nunca llegó a salir del teléfono también queda
    // registrado, y una corrección posterior que SÍ viajó puede marcarse
    // como no enviada. Ese falso positivo cuesta que la oficina revise una
    // venta que estaba bien; el falso negativo cuesta despachar una venta
    // que el cliente no pidió. No son comparables.
    val REVISION_POSTEADA: Int? = null,
    // ─────────────────────────────────────────────────────────────────
    // Plan "Corregir una venta DESPUÉS de que subió, mientras siga en
    // borrador" (nivel 2), Task A1, migración 31→32: lo que el teléfono
    // sabe del SERVIDOR para una venta que YA se envió, y la cola de
    // correcciones remotas que viajan aparte del POST de creación.
    // ──────────────────────────────────────────────────────────────

    // Último estado del servidor leído por un GET fresco. `NULL` = no se
    // sabe (nunca se leyó, o la fila es de una venta que aún no sube).
    // `situacion`: 'borrador'/'revisada'/'aprobada'/'cancelada'.
    val SERVER_SITUACION: String? = null,
    // `sincronizacion`: 'pendiente'/'aplicada'. 'aplicada' es TERMINAL: la
    // venta ya entró a Microsip y nunca vuelve a ser corregible.
    val SERVER_SINCRONIZACION: String? = null,
    // La `VERSION` leída en esa misma lectura. Solo diagnóstico y UI —
    // JAMÁS se usa como precondición de una escritura (regla del arranque
    // limpio: cada corrida de la corrección remota relee la versión con su
    // propio GET y encadena únicamente versiones obtenidas en esa corrida).
    val SERVER_VERSION: Int? = null,
    // Epoch ms de esa lectura. `NULL` = nunca se leyó. Junto con
    // `EstadoServidor.MAX_EDAD_ESTADO_MS` (60 min, en `:feature:ventaCorreccion`)
    // decide si el estado guardado sigue siendo una base honesta para
    // ofrecer "Corregir venta".
    val SERVER_STATE_AT: Long? = null,
    // Hay una corrección local COMMITEADA que el servidor todavía no
    // confirmó. La pone `GuardarCorreccion` en la MISMA transacción que el
    // commit, sólo si `ENVIADO = 1` (si `ENVIADO = 0` es la venta del nivel
    // 1: su camino sigue siendo el POST de creación, sin tocar esta
    // columna). `@ColumnInfo(defaultValue = "0")` por el mismo argumento
    // que `REVISION`/`CORRECCION_NO_ENVIADA`: una instalación nueva y una
    // migrada deben declarar el mismo DEFAULT.
    @ColumnInfo(defaultValue = "0")
    val CORRECCION_REMOTA_PENDIENTE: Boolean = false,
    // Marca TERMINAL y persistente de por qué una corrección remota dejó
    // de poder aplicarse. `NULL` = sin incidencia. `'RECHAZADA_ESTADO'` =
    // el servidor ya no la deja editar (salió de borrador, o ya está
    // aplicada en Microsip). `'CONFLICTO'` = la oficina escribió primero
    // (412 `venta_version_conflicto`) — la corrección local queda MUERTA;
    // no se reintenta jamás y no se re-basa sola (decisión del dueño: si
    // chocan, gana la oficina). Nunca se borra por un camino que no sea
    // confirmación positiva del servidor.
    val CORRECCION_REMOTA_ESTADO: String? = null,
    // La `REVISION` del cuerpo que viajó en la corrida remota que terminó
    // con los tres pasos en 2xx. `cerrarCorreccionRemota` sólo limpia
    // `CORRECCION_REMOTA_PENDIENTE` cuando `REVISION == REVISION_REMOTA_ENVIADA`
    // — si el dueño corrigió OTRA vez mientras la corrida estaba en vuelo,
    // la bandera se queda puesta y la corrección nueva viaja en la
    // siguiente corrida. Análogo exacto de `REVISION_POSTEADA` del nivel 1,
    // aplicado a la corrección remota.
    val REVISION_REMOTA_ENVIADA: Int? = null
)
