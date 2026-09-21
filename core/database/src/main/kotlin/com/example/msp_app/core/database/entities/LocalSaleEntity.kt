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
    val REVISION_POSTEADA: Int? = null
)
