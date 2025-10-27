package com.example.msp_app.data.models.sale.localsale

enum class TipoVenta {
    CONTADO,
    CREDITO;

    companion object {
        fun fromString(tipo: String?): String {
            return when (tipo?.uppercase()) {
                "CREDITO" -> "CREDITO"
                else -> "CONTADO"
            }
        }
    }
}

data class LocalSale(
    val LOCAL_SALE_ID: String,
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
    val TIPO_VENTA: String? = "CONTADO"
)