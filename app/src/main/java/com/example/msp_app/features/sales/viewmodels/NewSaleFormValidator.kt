package com.example.msp_app.features.sales.viewmodels

object NewSaleFormValidator {

    fun validateClientName(name: String): Boolean {
        return name.isNotBlank() && name.length >= 3
    }

    fun validatePhone(phone: String, tipoVenta: String): Boolean {
        if (tipoVenta == "CONTADO") return true
        return phone.isNotBlank() && phone.length == 10
    }

    fun validateStreet(street: String): Boolean {
        return street.isNotBlank() && street.length >= 5
    }

    fun validateInstallment(amount: String, tipoVenta: String): Boolean {
        if (tipoVenta == "CONTADO") return true
        val amountInt = amount.toIntOrNull()
        return amountInt != null && amountInt > 0
    }

    fun validatePaymentFrequency(frequency: String, tipoVenta: String): Boolean {
        if (tipoVenta == "CONTADO") return true
        return frequency.isNotBlank()
    }

    fun validateCollectionDay(day: String, tipoVenta: String): Boolean {
        if (tipoVenta == "CONTADO") return true
        return day.isNotBlank()
    }

    fun validateDownpayment(downpayment: String): Boolean {
        return downpayment.isBlank() || (downpayment.toDoubleOrNull()?.let { it >= 0 } ?: false)
    }

    fun validateZone(tipoVenta: String, zoneId: Int?, zoneName: String): Boolean {
        if (tipoVenta == "CONTADO") return true
        return zoneId != null && zoneName.isNotBlank()
    }

    fun validateLocation(latitude: Double, longitude: Double, permissionGranted: Boolean): Boolean {
        return latitude != 0.0 && longitude != 0.0 && permissionGranted
    }

    fun validateAll(state: NewSaleFormState, hasProducts: Boolean): FormErrors {
        val tipoVenta = state.tipoVenta
        return FormErrors(
            clientName = !validateClientName(state.clientName),
            phone = !validatePhone(state.phone, tipoVenta),
            location = !validateStreet(state.street),
            installment = !validateInstallment(state.installment, tipoVenta),
            paymentFrequency = !validatePaymentFrequency(state.paymentFrequency, tipoVenta),
            collectionDay = !validateCollectionDay(state.collectionDay, tipoVenta),
            image = state.imageUris.isEmpty(),
            products = !hasProducts,
            downpayment = !validateDownpayment(state.downpayment),
            zone = !validateZone(tipoVenta, state.selectedZoneId, state.selectedZoneName)
        )
    }

    fun isAllValid(state: NewSaleFormState, hasProducts: Boolean): Boolean {
        val tipoVenta = state.tipoVenta
        val locationDataValid =
            validateLocation(state.latitude, state.longitude, state.locationPermissionGranted)
        return validateClientName(state.clientName) &&
            validatePhone(state.phone, tipoVenta) &&
            validateStreet(state.street) &&
            locationDataValid &&
            validateInstallment(state.installment, tipoVenta) &&
            validatePaymentFrequency(state.paymentFrequency, tipoVenta) &&
            validateDownpayment(state.downpayment) &&
            validateCollectionDay(state.collectionDay, tipoVenta) &&
            state.imageUris.isNotEmpty() &&
            hasProducts &&
            validateZone(tipoVenta, state.selectedZoneId, state.selectedZoneName)
    }
}
