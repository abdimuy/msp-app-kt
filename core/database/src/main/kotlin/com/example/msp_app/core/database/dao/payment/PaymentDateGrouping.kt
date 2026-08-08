package com.example.msp_app.core.database.dao.payment

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Copia interna, byte-idéntica en algoritmo, de
 * `com.example.msp_app.core.utils.DateUtils.formatIsoDate` (que se queda en
 * `:app`). Existe porque [PaymentDao.getPaymentsGroupedByDaySince] /
 * [PaymentDao.observePaymentsGroupedByDaySince] agrupan por dia formateado y
 * `:core:database` no puede depender de `:app` ni de `:core:common`
 * (`:core:database` es la base del grafo — ver Plan 2 / Task 5, gotcha de
 * ciclo). Si `DateUtils.formatIsoDate` cambia de comportamiento en `:app`,
 * esta copia debe actualizarse a mano.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal fun formatIsoDateForGrouping(
    iso: String,
    pattern: String,
    locale: Locale = Locale("es", "MX")
): String {
    return try {
        val offset = when {
            iso.endsWith("Z") -> OffsetDateTime.parse(iso)
            iso.contains("T") -> LocalDateTime.parse(iso).atOffset(ZoneOffset.UTC)
            else -> LocalDate.parse(iso).atStartOfDay().atOffset(ZoneOffset.UTC)
        }
        val zonedLocal = offset.atZoneSameInstant(ZoneId.systemDefault())
        val formatter = DateTimeFormatter
            .ofPattern(pattern, locale)
            .withZone(ZoneId.systemDefault())
        formatter.format(zonedLocal)
    } catch (e: Exception) {
        // Fallback intencional: esta función es una copia byte-idéntica en
        // algoritmo de `DateUtils.formatIsoDate` (`:app`, ver KDoc de
        // arriba) — el contrato es "si el parseo falla por CUALQUIER motivo,
        // devolver el ISO original sin agrupar", no solo para un subtipo de
        // excepción. Acotar el catch cambiaría ese comportamiento de
        // fallback, que debe seguir siendo idéntico al de `:app`.
        iso
    }
}
