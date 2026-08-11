package com.example.msp_app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    indices = [
        androidx.room.Index(value = ["DOCTO_CC_ACR_ID"], unique = false),
        androidx.room.Index(value = ["DOCTO_CC_ID"], unique = true),
        androidx.room.Index(value = ["FOLIO"], unique = true),
        androidx.room.Index(value = ["CLIENTE_ID"], unique = false),
        androidx.room.Index(value = ["COBRADOR_ID"], unique = false),
        androidx.room.Index(value = ["ZONA_CLIENTE_ID"], unique = false),
        androidx.room.Index(value = ["FECHA"], unique = false),
        androidx.room.Index(value = ["ESTADO_COBRANZA"], unique = false)
    ]
)
data class SaleEntity(
    @PrimaryKey val DOCTO_CC_ACR_ID: Int,
    val DOCTO_CC_ID: Int,
    val FOLIO: String,
    val CLIENTE_ID: Int,
    val APLICADO: String,
    val COBRADOR_ID: Int,
    val CLIENTE: String,
    val ZONA_CLIENTE_ID: Int,
    val LIMITE_CREDITO: Double,
    val NOTAS: String,
    val ZONA_NOMBRE: String,
    val IMPORTE_PAGO_PROMEDIO: Double?,
    val TOTAL_IMPORTE: Double,
    val NUM_IMPORTES: Int,
    val FECHA: String,
    val PARCIALIDAD: Int,
    val ENGANCHE: Double,
    val TIEMPO_A_CORTO_PLAZOMESES: Int,
    val MONTO_A_CORTO_PLAZO: Double,
    val VENDEDOR_1: String,
    val VENDEDOR_2: String,
    val VENDEDOR_3: String,
    val PRECIO_TOTAL: Double,
    val IMPTE_REST: Double,
    val SALDO_REST: Double,
    val FECHA_ULT_PAGO: String?,
    val CALLE: String,
    val CIUDAD: String,
    val ESTADO: String,
    val TELEFONO: String,
    val NOMBRE_COBRADOR: String,
    val ESTADO_COBRANZA: String,
    val DIA_COBRANZA: String,
    val DIA_TEMPORAL_COBRANZA: String,
    val PRECIO_DE_CONTADO: Double,
    val AVAL_O_RESPONSABLE: String,
    val FREC_PAGO: String?
)

/**
 * Proyección ligera de una venta: folio comercial + saldo restante actual, indexada por su
 * `DOCTO_CC_ACR_ID`. Alimenta el enriquecimiento de las filas de pago del reporte de cobranza
 * (`SaleDao.getSaleRefsByAcrIds`) sin traer la entidad `sales` completa. Solo lectura — no es
 * una `@Entity`, no toca el schema.
 */
data class SaleRefRow(
    val saleId: Int,
    val folio: String,
    val saldo: Double
)

/**
 * Proyección de una venta de crédito para el cálculo de "Meta de la semana"
 * (`:feature:collectionReport`, `CobranzaPorcentaje` — puerto Kotlin fiel del cálculo
 * `msp-api` `internal/rutas/domain`). Solo las columnas que ese cálculo necesita — no trae
 * la entidad `sales` completa (`SaleDao.getCobranzaRows`). Solo lectura, no es una `@Entity`.
 */
data class SaleCobranzaRow(
    @ColumnInfo(name = "DOCTO_CC_ACR_ID")
    val doctoCcAcrId: Int,
    @ColumnInfo(name = "PARCIALIDAD")
    val parcialidad: Int,
    @ColumnInfo(name = "PRECIO_TOTAL")
    val precioTotal: Double,
    @ColumnInfo(name = "SALDO_REST")
    val saldoRest: Double,
    @ColumnInfo(name = "FREC_PAGO")
    val frecPago: String?,
    @ColumnInfo(name = "FECHA")
    val fecha: String
)

data class SaleWithProductsEntity(
    val DOCTO_CC_ACR_ID: Int,
    val DOCTO_CC_ID: Int,
    val FOLIO: String,
    val CLIENTE_ID: Int,
    val APLICADO: String,
    val COBRADOR_ID: Int,
    val CLIENTE: String,
    val ZONA_CLIENTE_ID: Int,
    val LIMITE_CREDITO: Double,
    val NOTAS: String,
    val ZONA_NOMBRE: String,
    val IMPORTE_PAGO_PROMEDIO: Double?,
    val TOTAL_IMPORTE: Double,
    val NUM_IMPORTES: Int,
    val FECHA: String,
    val PARCIALIDAD: Int,
    val ENGANCHE: Double,
    val TIEMPO_A_CORTO_PLAZOMESES: Int,
    val MONTO_A_CORTO_PLAZO: Double,
    val VENDEDOR_1: String,
    val VENDEDOR_2: String,
    val VENDEDOR_3: String,
    val PRECIO_TOTAL: Double,
    val IMPTE_REST: Double,
    val SALDO_REST: Double,
    val FECHA_ULT_PAGO: String?,
    val CALLE: String,
    val CIUDAD: String,
    val ESTADO: String,
    val TELEFONO: String,
    val NOMBRE_COBRADOR: String,
    val ESTADO_COBRANZA: String,
    val DIA_COBRANZA: String,
    val DIA_TEMPORAL_COBRANZA: String,
    val PRECIO_DE_CONTADO: Double,
    val AVAL_O_RESPONSABLE: String,
    val FREC_PAGO: String?,
    @ColumnInfo(name = "PRODUCTOS")
    val PRODUCTOS: String?,
    @ColumnInfo(name = "NUM_PAGOS_ATRASADOS")
    val NUM_PAGOS_ATRASADOS: Int? = null
)
