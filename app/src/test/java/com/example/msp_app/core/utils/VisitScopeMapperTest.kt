package com.example.msp_app.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 13 (plan `pagos-y-visitas`): [VisitScopeMapper] es la unica fuente
 * de la clasificacion cliente/venta que [com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource]
 * consume para decidir como propagar el estado de una visita. Cubre las
 * once etiquetas de `Constants.kt:31-41` una por una (robustez suprema) y
 * el default seguro para lo desconocido.
 */
class VisitScopeMapperTest {

    // ─── alcance CLIENTE — la familia "no estaba" (expediente §5) ─────────────

    @Test
    fun `map returns CLIENTE for NO_SE_ENCONTRABA`() {
        assertEquals(VisitScope.CLIENTE, VisitScopeMapper.map(Constants.NO_SE_ENCONTRABA))
    }

    @Test
    fun `map returns CLIENTE for CASA_CERRADA`() {
        assertEquals(VisitScope.CLIENTE, VisitScopeMapper.map(Constants.CASA_CERRADA))
    }

    @Test
    fun `map returns CLIENTE for SOLO_MENORES`() {
        assertEquals(VisitScope.CLIENTE, VisitScopeMapper.map(Constants.SOLO_MENORES))
    }

    // ─── alcance VENTA — "vuelvo", "se nego", "prometio" ───────────────────────

    @Test
    fun `map returns VENTA for SE_ESCONDE`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.SE_ESCONDE))
    }

    @Test
    fun `map returns VENTA for NO_RESPONDE`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.NO_RESPONDE))
    }

    @Test
    fun `map returns VENTA for SE_ESCUCHAN_RUIDOS`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.SE_ESCUCHAN_RUIDOS))
    }

    @Test
    fun `map returns VENTA for NO_VA_A_DAR_PAGO`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.NO_VA_A_DAR_PAGO))
    }

    @Test
    fun `map returns VENTA for TIENE_PERO_NO_PAGA`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.TIENE_PERO_NO_PAGA))
    }

    @Test
    fun `map returns VENTA for FUE_GROSERO`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.FUE_GROSERO))
    }

    @Test
    fun `map returns VENTA for PIDE_REAGENDAR`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.PIDE_REAGENDAR))
    }

    // ─── etiqueta definida pero fuera del formulario actual — default seguro ──

    @Test
    fun `map returns VENTA for PIDE_TIEMPO, the narrower default for an unlisted label`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(Constants.PIDE_TIEMPO))
    }

    // ─── desconocido / vacio — el mismo default seguro ─────────────────────────

    @Test
    fun `map returns VENTA for an unknown label`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map("Unknown status"))
    }

    @Test
    fun `map returns VENTA for empty string`() {
        assertEquals(VisitScope.VENTA, VisitScopeMapper.map(""))
    }
}
