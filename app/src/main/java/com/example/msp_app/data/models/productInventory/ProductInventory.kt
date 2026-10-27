package com.example.msp_app.data.models.productInventory

import com.google.gson.annotations.SerializedName

data class ProductInventory(
    val ARTICULO_ID: Int,
    val ARTICULO: String,
    val EXISTENCIAS: Int,
    val LINEA_ARTICULO_ID: Int,
    val LINEA_ARTICULO: String,
    val PRECIOS: String?,
    /**
     * Precios ya clasificados por el servidor (`precios_ordenados`). E-APP-059:
     * los APKs nuevos lo prefieren para PINTAR; la cadena [PRECIOS] sigue llegando
     * (en el orden viejo lista → 4 meses → contado) y es la que alimenta los montos
     * de la venta. Gson no aplica el default de Kotlin: si el servidor no lo manda,
     * llega `null` aunque aquí diga `null` de todos modos.
     */
    @SerializedName("precios_ordenados")
    val preciosOrdenados: List<PrecioOrdenado>? = null
)

data class PrecioOrdenado(
    @SerializedName("tipo") val tipo: String?,
    @SerializedName("precio_empresa_id") val precioEmpresaId: Int?,
    @SerializedName("nombre") val nombre: String?,
    @SerializedName("precio") val precio: Double?
)
