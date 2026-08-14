package com.example.msp_app.features.sales.viewmodels

import android.net.Uri

/**
 * Errores de captura, uno por campo. Cada bandera se cablea al `isError` /
 * `supportingText` de SU propio `OutlinedTextField`, para que el vendedor vea
 * exactamente qué campo le falta y no un mensaje genérico al pie del formulario.
 *
 * [location] es histórico: corresponde al campo **Calle**, no al GPS (el GPS se
 * valida aparte con `NewSaleFormValidator.validateLocation` y se refleja en
 * `NewSaleFormState.hasValidLocation`).
 *
 * [colonia], [poblacion] y [ciudad] se agregaron tras el incidente del
 * 2026-08-13: nueve ventas se quedaron atoradas todo el día en la cola de
 * pendientes porque el API exige esos tres campos no vacíos
 * (`colonia_required` / `poblacion_required` / `ciudad_required`, ver
 * `internal/ventas/domain/direccion.go` en `msp-api`) y la pantalla de captura
 * jamás los validó — 6 de esas ventas iban con ciudad vacía y 4 con colonia
 * vacía.
 */
data class FormErrors(
    val clientName: Boolean = false,
    val phone: Boolean = false,
    val location: Boolean = false,
    val colonia: Boolean = false,
    val poblacion: Boolean = false,
    val ciudad: Boolean = false,
    val installment: Boolean = false,
    val paymentFrequency: Boolean = false,
    val collectionDay: Boolean = false,
    val image: Boolean = false,
    val products: Boolean = false,
    val downpayment: Boolean = false,
    val zone: Boolean = false
) {
    /**
     * `true` si CUALQUIER campo trae error. Se calcula aquí y no en cada
     * pantalla para que agregar una bandera nueva a [FormErrors] no deje
     * silenciosamente fuera a un formulario que olvidó sumarla a su cadena de
     * `&&` — que es justo como colonia/población/ciudad se quedaron sin validar.
     */
    val hasAny: Boolean
        get() = clientName || phone || location || colonia || poblacion || ciudad ||
            installment || paymentFrequency || collectionDay || image || products ||
            downpayment || zone
}

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
