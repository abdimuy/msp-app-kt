package com.example.msp_app.features.visit.newvisit

import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.Sale
import com.example.msp_app.data.models.visit.Visit

/**
 * Builds the [Visit] a cobrador registers against a [Sale]. Extracted from
 * `NewVisitDialog.handleSaveVisit()` (retired in Task 21) as a pure, behavior-preserving refactor
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
 *  - `LAT`/`LNG` start at 0.0. This line used to say the location was
 *    "patched later by `UpdateLocationService` running in the background".
 *    That stopped being true when Task 21 deleted `NewVisitDialog`, the only
 *    producer that ever started that service with a `visit_id`. The live path
 *    (`RegistroDeVisitaAdapter`) asks `UbicacionDeVisitaPort` once, at save
 *    time, and 0.0 is what a visita without a fix keeps.
 *  - `GUARDADO_EN_MICROSIP` starts 0 — the visita is pending until the
 *    sync/upload pipeline confirms it against Microsip.
 */
/*
 * Task 21: con `NewVisitDialog` retirado, este objeto ya no tiene llamadores en
 * producción — la escritura vive ahora en `RegistroDeVisitaAdapter`. Se conserva
 * porque `VisitFactoryTest` y `VisitsLocalDataSourceTest` lo usan como
 * **caracterización del contrato de atribución** (`COBRADOR`/`ZONA_CLIENTE_ID`
 * de la venta, `COBRADOR_ID` del usuario, `IMPTE_DOCTO_CC_ID = DOCTO_CC_ACR_ID`),
 * que es exactamente el contrato que el adaptador nuevo reproduce. Borrarlo
 * borraría la única prueba de que los dos caminos escriben lo mismo, justo en
 * el campo que ya costó un defecto de producción (commit `721c5551`).
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
