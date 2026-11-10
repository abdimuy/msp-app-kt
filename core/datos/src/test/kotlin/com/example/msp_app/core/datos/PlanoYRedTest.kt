package com.example.msp_app.core.datos

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanoYRedTest {

    @Test fun `primer plano cuando hay al menos una actividad iniciada`() {
        val cambios = mutableListOf<Boolean>()
        val c = ContadorDeActividades { cambios += it }
        c.iniciada()
        c.iniciada()
        c.detenida()
        assertEquals(listOf(true), cambios)
        c.detenida()
        assertEquals(listOf(true, false), cambios)
    }

    @Test fun `un detenida de mas no deja el contador negativo`() {
        val cambios = mutableListOf<Boolean>()
        val c = ContadorDeActividades { cambios += it }
        c.detenida()
        c.iniciada()
        assertEquals(listOf(true), cambios)
    }

    @Test fun `clasificacion de la red`() {
        assertEquals(TipoDeRed.WIFI, ClasificadorDeRed.clasificar(wifi = true, celular = false))
        assertEquals(TipoDeRed.MOVIL, ClasificadorDeRed.clasificar(wifi = false, celular = true))
        // Wi-Fi con respaldo celular (VPN u otras): manda el wifi
        assertEquals(TipoDeRed.WIFI, ClasificadorDeRed.clasificar(wifi = true, celular = true))
        assertEquals(TipoDeRed.OTRA, ClasificadorDeRed.clasificar(wifi = false, celular = false))
    }
}
