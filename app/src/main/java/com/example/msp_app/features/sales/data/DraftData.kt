package com.example.msp_app.features.sales.data


import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.sales.viewmodels.SaleItem
import kotlinx.serialization.Serializable

@Serializable
data class DraftData(
    val clientName: String = "",
    val phone: String = "",
    val location: String = "",
    val numero: String = "",
    val colonia: String = "",
    val poblacion: String = "",
    val ciudad: String = "",
    val tipoVenta: String = "CONTADO",
    val downpayment: String = "",
    val installment: String = "",
    val guarantor: String = "",
    val note: String = "",
    val collectionday: String = "",
    val paymentfrequency: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val imageUris: List<String> = emptyList(),
    val products: List<DraftProduct> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class DraftProduct(
    val articuloId: Int,
    val articulo: String,
    val existencias: Int,
    val lineaArticuloId: Int,
    val lineaArticulo: String,
    val precios: String?,
    val quantity: Int
) {
    fun toProductInventory(): ProductInventory {
        return ProductInventory(
            ARTICULO_ID = articuloId,
            ARTICULO = articulo,
            EXISTENCIAS = existencias,
            LINEA_ARTICULO_ID = lineaArticuloId,
            LINEA_ARTICULO = lineaArticulo,
            PRECIOS = precios
        )
    }

    companion object {
        fun fromSaleItem(saleItem: SaleItem): DraftProduct {
            return DraftProduct(
                articuloId = saleItem.product.ARTICULO_ID,
                articulo = saleItem.product.ARTICULO,
                existencias = saleItem.product.EXISTENCIAS,
                lineaArticuloId = saleItem.product.LINEA_ARTICULO_ID,
                lineaArticulo = saleItem.product.LINEA_ARTICULO,
                precios = saleItem.product.PRECIOS,
                quantity = saleItem.quantity
            )
        }
    }
}