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
    // contra el snapshot que el subidor tenía antes del POST) y marca esta
    // columna en la MISMA sentencia: la divergencia queda visible en la
    // fila en vez de perderse en silencio. `@ColumnInfo(defaultValue = "0")`
    // por el mismo argumento que `REVISION`.
    @ColumnInfo(defaultValue = "0")
    val CORRECCION_NO_ENVIADA: Boolean = false
)
