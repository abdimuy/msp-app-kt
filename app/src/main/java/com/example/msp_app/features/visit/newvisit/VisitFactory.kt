package com.example.msp_app.features.visit.newvisit

import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.visit.Visit

/**
 * Builds the [Visit] a cobrador registers against a [Sale]. Extracted from
 * `NewVisitDialog.handleSaveVisit()` as a pure, behavior-preserving refactor
 * so the attribution mapping can be characterization-tested without a
 * Compose/Robolectric harness — mirrors
 * [com.example.msp_app.features.payments.newpayment.PaymentFactory].
 *
 * Attribution contract (do NOT change without an explicit product decision):
 *  - `COBRADOR` / `ZONA_CLIENTE_ID` / `IMPTE_DOCTO_CC_ID` / `CLIENTE_ID` come
 *    from the [Sale] being visited.
 *  - `COBRADOR_ID` comes from [currentUser], NOT from the sale — the sale's
 *    cobrador may differ from the one physically registering the visit.
 *    Unlike [com.example.msp_app.features.payments.newpayment.PaymentFactory],
 *    [currentUser] is nullable here (the dialog's guard does not reject a
 *    null user, only a resolved `COBRADOR_ID == 0`), so a null user falls
 *    back to `COBRADOR_ID = 0` — preserved as-is from the original inline
 *    construction, not a new behavior.
 *  - `FORMA_COBRO_ID` is always 0 today (the dialog has no forma-de-cobro
 *    picker for visitas); kept as a parameter for symmetry with
 *    `PaymentFactory` and because the wire contract carries the field.
 *  - `LAT`/`LNG` start at 0.0 — the real location is patched later by
 *    [com.example.msp_app.services.UpdateLocationService] running in the
 *    background.
 *  - `GUARDADO_EN_MICROSIP` starts 0 — the visita is pending until the
 *    sync/upload pipeline confirms it against Microsip.
 */
object VisitFactory {
    fun fromSale(
        sale: Sale,
        currentUser: User?,
        tipoVisita: String,
        formaCobroId: Int,
        nota: String,
        id: String,
        fecha: String
    ): Visit = Visit(
        ID = id,
        COBRADOR_ID = currentUser?.COBRADOR_ID ?: 0,
        COBRADOR = sale.NOMBRE_COBRADOR,
        LNG = 0.0,
        LAT = 0.0,
        FORMA_COBRO_ID = formaCobroId,
        CLIENTE_ID = sale.CLIENTE_ID,
        ZONA_CLIENTE_ID = sale.ZONA_CLIENTE_ID,
        GUARDADO_EN_MICROSIP = 0,
        FECHA = fecha,
        IMPTE_DOCTO_CC_ID = sale.DOCTO_CC_ACR_ID,
        TIPO_VISITA = tipoVisita,
        NOTA = nota
    )
}
