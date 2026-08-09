package com.example.msp_app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guarda de la REGLA DURA de Task 10 (Plan 5): la ruta del reporte de cobranza conserva el
 * literal `"daily_reports"`. El destino cambió (ahora `:feature:collectionReport`), pero el
 * string NO — de él dependen deep links, el drawer y el hábito de los usuarios.
 *
 * La eliminación de `Screen.WeeklyReport` ("weekly_reports") es una garantía de COMPILACIÓN:
 * el `object` ya no existe, así que cualquier referencia rompería el build (verificado por
 * `assembleDevlocalDebug`); no hay un símbolo runtime que aseverar aquí.
 */
class CollectionReportRouteTest {

    @Test
    fun `la ruta del reporte de cobranza conserva el literal daily_reports`() {
        assertEquals("daily_reports", Screen.DailyReport.route)
    }
}
