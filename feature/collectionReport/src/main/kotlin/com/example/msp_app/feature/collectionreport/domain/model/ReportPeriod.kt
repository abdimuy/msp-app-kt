package com.example.msp_app.feature.collectionreport.domain.model

/**
 * Periodo del reporte de cobranza.
 *
 * - [DIA]: el día de negocio de hoy `[00:00, mañana 00:00)` en zona negocio.
 * - [SEMANA]: el CICLO del cobrador `[FECHA_CARGA_INICIAL, hoy]` — no una semana
 *   de calendario, sino el rango desde la carga inicial hasta hoy inclusive.
 */
enum class ReportPeriod {
    DIA,
    SEMANA
}
