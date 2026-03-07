package com.example.msp_app.features.sales.viewmodels

import android.net.Uri

data class FormErrors(
    val clientName: Boolean = false,
    val phone: Boolean = false,
    val location: Boolean = false,
    val installment: Boolean = false,
    val paymentFrequency: Boolean = false,
    val collectionDay: Boolean = false,
    val image: Boolean = false,
    val products: Boolean = false,
    val downpayment: Boolean = false,
    val zone: Boolean = false
)

data class NewSaleFormState(
    val clientName: String = "",
    val selectedClienteId: Int? = null,
    val phone: String = "",
    val street: String = "",
    val numero: String = "",
    val colonia: String = "",
    val poblacion: String = "",
    val ciudad: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val hasValidLocation: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val tipoVenta: String = "CREDITO",
    val selectedZoneId: Int? = null,
    val selectedZoneName: String = "",
    val downpayment: String = "",
    val installment: String = "",
    val guarantor: String = "",
    val collectionDay: String = "",
    val paymentFrequency: String = "",
    val note: String = "",
    val imageUris: List<Uri> = emptyList(),
    val errors: FormErrors = FormErrors(),
    val saleCompleted: Boolean = false
)
