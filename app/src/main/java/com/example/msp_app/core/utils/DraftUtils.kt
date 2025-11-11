package com.example.msp_app.core.utils

import android.net.Uri
import androidx.compose.ui.text.input.TextFieldValue
import com.example.msp_app.features.sales.data.DraftData
import com.example.msp_app.features.sales.data.DraftProduct
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel

/**
 * Crea un DraftData a partir de los valores actuales del formulario
 */
fun createDraftFromFormData(
    clientName: TextFieldValue,
    phone: TextFieldValue,
    location: String,
    numero: TextFieldValue,
    colonia: TextFieldValue,
    poblacion: TextFieldValue,
    ciudad: TextFieldValue,
    tipoVenta: String,
    downpayment: TextFieldValue,
    installment: TextFieldValue,
    guarantor: TextFieldValue,
    note: TextFieldValue,
    collectionday: String,
    paymentfrequency: String,
    latitude: Double,
    longitude: Double,
    imageUris: List<Uri>,
    saleProductsViewModel: SaleProductsViewModel
): DraftData {
    return DraftData(
        clientName = clientName.text,
        phone = phone.text,
        location = location,
        numero = numero.text,
        colonia = colonia.text,
        poblacion = poblacion.text,
        ciudad = ciudad.text,
        tipoVenta = tipoVenta,
        downpayment = downpayment.text,
        installment = installment.text,
        guarantor = guarantor.text,
        note = note.text,
        collectionday = collectionday,
        paymentfrequency = paymentfrequency,
        latitude = latitude,
        longitude = longitude,
        imageUris = imageUris.map { it.toString() },
        products = saleProductsViewModel.getSaleItemsList().map {
            DraftProduct.fromSaleItem(it)
        },
        timestamp = System.currentTimeMillis()
    )
}

/**
 * Carga un borrador en las variables del formulario
 */
data class FormDataHolder(
    val clientName: TextFieldValue,
    val phone: TextFieldValue,
    val location: String,
    val numero: TextFieldValue,
    val colonia: TextFieldValue,
    val poblacion: TextFieldValue,
    val ciudad: TextFieldValue,
    val tipoVenta: String,
    val downpayment: TextFieldValue,
    val installment: TextFieldValue,
    val guarantor: TextFieldValue,
    val note: TextFieldValue,
    val collectionday: String,
    val paymentfrequency: String,
    val latitude: Double,
    val longitude: Double,
    val imageUris: List<Uri>
)

/**
 * Convierte un DraftData a FormDataHolder
 */
fun draftToFormData(draft: DraftData): FormDataHolder {
    return FormDataHolder(
        clientName = TextFieldValue(draft.clientName),
        phone = TextFieldValue(draft.phone),
        location = draft.location,
        numero = TextFieldValue(draft.numero),
        colonia = TextFieldValue(draft.colonia),
        poblacion = TextFieldValue(draft.poblacion),
        ciudad = TextFieldValue(draft.ciudad),
        tipoVenta = draft.tipoVenta,
        downpayment = TextFieldValue(draft.downpayment),
        installment = TextFieldValue(draft.installment),
        guarantor = TextFieldValue(draft.guarantor),
        note = TextFieldValue(draft.note),
        collectionday = draft.collectionday,
        paymentfrequency = draft.paymentfrequency,
        latitude = draft.latitude,
        longitude = draft.longitude,
        imageUris = draft.imageUris.mapNotNull {
            try {
                Uri.parse(it)
            } catch (e: Exception) {
                null
            }
        }
    )
}

/**
 * Carga los productos del borrador en el SaleProductsViewModel
 */
fun loadDraftProducts(
    draft: DraftData,
    saleProductsViewModel: SaleProductsViewModel
) {
    saleProductsViewModel.clearSale()
    draft.products.forEach { draftProduct ->
        val product = draftProduct.toProductInventory()
        saleProductsViewModel.addProductToSale(product, draftProduct.quantity)
    }
}