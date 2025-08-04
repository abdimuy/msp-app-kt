package com.example.msp_app.core.utils

import com.example.msp_app.data.models.sale.EstadoCobranza
import org.junit.Test
import org.junit.Assert.*

class VisitStatusMapperTest {

    @Test
    fun `map should return VOLVER_VISITAR for NO_SE_ENCONTRABA`() {
        val result = VisitStatusMapper.map(Constants.NO_SE_ENCONTRABA)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for CASA_CERRADA`() {
        val result = VisitStatusMapper.map(Constants.CASA_CERRADA)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for SOLO_MENORES`() {
        val result = VisitStatusMapper.map(Constants.SOLO_MENORES)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for PIDE_TIEMPO`() {
        val result = VisitStatusMapper.map(Constants.PIDE_TIEMPO)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for FUE_GROSERO`() {
        val result = VisitStatusMapper.map(Constants.FUE_GROSERO)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for SE_ESCONDE`() {
        val result = VisitStatusMapper.map(Constants.SE_ESCONDE)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for NO_RESPONDE`() {
        val result = VisitStatusMapper.map(Constants.NO_RESPONDE)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for SE_ESCUCHAN_RUIDOS`() {
        val result = VisitStatusMapper.map(Constants.SE_ESCUCHAN_RUIDOS)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for PIDE_REAGENDAR`() {
        val result = VisitStatusMapper.map(Constants.PIDE_REAGENDAR)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return VOLVER_VISITAR for TIENE_PERO_NO_PAGA`() {
        val result = VisitStatusMapper.map(Constants.TIENE_PERO_NO_PAGA)
        assertEquals(EstadoCobranza.VOLVER_VISITAR, result)
    }

    @Test
    fun `map should return NO_PAGADO for NO_VA_A_DAR_PAGO`() {
        val result = VisitStatusMapper.map(Constants.NO_VA_A_DAR_PAGO)
        assertEquals(EstadoCobranza.NO_PAGADO, result)
    }

    @Test
    fun `map should return PENDIENTE for unknown status`() {
        val result = VisitStatusMapper.map("Unknown status")
        assertEquals(EstadoCobranza.PENDIENTE, result)
    }

    @Test
    fun `map should return PENDIENTE for empty string`() {
        val result = VisitStatusMapper.map("")
        assertEquals(EstadoCobranza.PENDIENTE, result)
    }

    @Test
    fun `map should return PENDIENTE for null-like values`() {
        val result1 = VisitStatusMapper.map("null")
        val result2 = VisitStatusMapper.map("undefined")
        
        assertEquals(EstadoCobranza.PENDIENTE, result1)
        assertEquals(EstadoCobranza.PENDIENTE, result2)
    }
}