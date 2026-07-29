package com.example.msp_app.features.payments.newpayment

import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.sale.Sale

/**
 * Builds the [Payment] a cobrador registers against a [Sale]. Extracted
 * from `NewPaymentDialog.handleSavePayment()` as a pure, behavior-preserving
 * refactor so the attribution mapping (Q6) can be characterization-tested
 * without a Compose/Robolectric harness.
 *
 * Attribution contract (do NOT change without an explicit product decision):
 *  - `COBRADOR` / `ZONA_CLIENTE_ID` / `DOCTO_CC_ACR_ID` / `CLIENTE_ID` /
 *    `NOMBRE_CLIENTE` come from the [Sale] being paid.
 *  - `COBRADOR_ID` comes from [currentUser], NOT from `sale.COBRADOR_ID` —
 *    the sale's cobrador may differ from the one physically registering the
 *    payment (route coverage, substitution), and the payment must be
 *    attributed to whoever is standing in front of the client.
 *  - `DOCTO_CC_ID` is hardcoded to 0 (not resolved client-side).
 *  - `LAT`/`LNG` start at 0.0 — the real location is patched later by
 *    [com.example.msp_app.services.UpdateLocationService] running in the
 *    background.
 *  - `GUARDADO_EN_MICROSIP` starts false — the payment is pending until the
 *    sync/upload pipeline confirms it against Microsip.
 */
object PaymentFactory {
    fun fromSale(
        sale: Sale,
        currentUser: User,
        importe: Double,
        formaCobroId: Int,
        id: String,
        fecha: String
    ): Payment = Payment(
        CLIENTE_ID = sale.CLIENTE_ID,
        ID = id,
        LAT = 0.0,
        LNG = 0.0,
        IMPORTE = importe,
        NOMBRE_CLIENTE = sale.CLIENTE,
        FECHA_HORA_PAGO = fecha,
        COBRADOR = sale.NOMBRE_COBRADOR,
        COBRADOR_ID = currentUser.COBRADOR_ID,
        DOCTO_CC_ID = 0,
        FORMA_COBRO_ID = formaCobroId,
        DOCTO_CC_ACR_ID = sale.DOCTO_CC_ACR_ID,
        ZONA_CLIENTE_ID = sale.ZONA_CLIENTE_ID,
        GUARDADO_EN_MICROSIP = false
    )
}
