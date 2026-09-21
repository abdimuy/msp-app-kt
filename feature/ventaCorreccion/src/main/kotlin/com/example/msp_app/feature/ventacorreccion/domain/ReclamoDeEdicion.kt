package com.example.msp_app.feature.ventacorreccion.domain

import com.example.msp_app.core.database.dao.localsale.LocalSaleClaimLeases

/**
 * Espejo puro de los valores que puede traer la columna `CLAIM_KIND` de
 * `local_sale` (ver `LocalSaleDao`). `null` es "nadie tiene la venta";
 * cualquier otro valor que no sea `EDIT`/`UPLOAD` es un `CLAIM_KIND`
 * corrupto — el DAO ya lo trata como vencido por defensa en profundidad
 * ("una captura nunca se retiene para siempre"), y aquí se refleja igual: no
 * mapea a ningún [TipoCandado], así que [Reclamo.estaVivo] lo cuenta como
 * vencido.
 */
enum class TipoCandado { EDIT, UPLOAD }

/** Traduce el `CLAIM_KIND` crudo de la fila a [TipoCandado]; `null` si es nulo, vacío o desconocido. */
fun tipoCandadoDe(claimKind: String?): TipoCandado? = when (claimKind) {
    "EDIT" -> TipoCandado.EDIT
    "UPLOAD" -> TipoCandado.UPLOAD
    else -> null
}

/**
 * El candado único de una venta (columnas `CLAIM_ID`/`CLAIM_KIND`/`CLAIMED_AT`
 * de `local_sale`), del lado del dominio puro: sin Room, sin Android. Cada
 * tipo de candado tiene su propio arrendamiento (edición: 30 min; subida:
 * 180 s — ver [LocalSaleClaimLeases], la fuente única para no despegar este
 * valor del que usa el DAO real).
 *
 * El vencimiento se compara igual que en SQL (`CLAIMED_AT <= ahora - lease`,
 * ver `LocalSaleDao`): vence en el milisegundo EXACTO del arrendamiento, no
 * un milisegundo después. Un [kind] `null` (desconocido/corrupto) o un
 * [claimedAt] `null` también cuentan como vencido — mismo argumento de
 * defensa en profundidad que el DAO.
 */
data class Reclamo(val kind: TipoCandado?, val claimedAt: Long?) {
    fun estaVivo(ahora: Long): Boolean {
        val tipoVigente = kind ?: return false
        val tomadoEn = claimedAt ?: return false
        val leaseMs = when (tipoVigente) {
            TipoCandado.EDIT -> LocalSaleClaimLeases.EDIT_LEASE_MS
            TipoCandado.UPLOAD -> LocalSaleClaimLeases.UPLOAD_LEASE_MS
        }
        // Vencido cuando `tomadoEn <= ahora - leaseMs`; vivo es lo contrario.
        return tomadoEn > ahora - leaseMs
    }
}
