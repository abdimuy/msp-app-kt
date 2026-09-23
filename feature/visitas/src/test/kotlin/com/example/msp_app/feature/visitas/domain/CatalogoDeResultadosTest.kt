package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.cobranza.domain.VisitScope
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Las diez etiquetas, cada una cayendo en su estado.**
 *
 * Es la prueba que sostiene la promesa del brief: el cobrador no reaprende nada
 * y el historial viejo sigue siendo comparable. Si alguien moviera una etiqueta
 * de grupo, o cambiara el estado de un literal en el catálogo de la Task 14,
 * estas pruebas se ponen rojas.
 */
class CatalogoDeResultadosTest {

    @Test
    fun `no se encontraba cae en no estaba`() = etiquetaCaeEn(
        TipoVisitaCatalogo.NO_SE_ENCONTRABA,
        ResultadoDeVisita.NO_ESTABA
    )

    @Test
    fun `casa cerrada con candado cae en no estaba`() = etiquetaCaeEn(
        TipoVisitaCatalogo.CASA_CERRADA,
        ResultadoDeVisita.NO_ESTABA
    )

    @Test
    fun `solo habia menores cae en no estaba`() = etiquetaCaeEn(
        TipoVisitaCatalogo.SOLO_MENORES,
        ResultadoDeVisita.NO_ESTABA
    )

    @Test
    fun `se asomo pero no salio cae en visite vuelvo`() = etiquetaCaeEn(
        TipoVisitaCatalogo.SE_ESCONDE,
        ResultadoDeVisita.VISITE_VUELVO
    )

    @Test
    fun `no responde aunque esta cae en visite vuelvo`() = etiquetaCaeEn(
        TipoVisitaCatalogo.NO_RESPONDE,
        ResultadoDeVisita.VISITE_VUELVO
    )

    @Test
    fun `se escuchan ruidos cae en visite vuelvo`() = etiquetaCaeEn(
        TipoVisitaCatalogo.SE_ESCUCHAN_RUIDOS,
        ResultadoDeVisita.VISITE_VUELVO
    )

    @Test
    fun `no pagara esta ocasion cae en se nego`() = etiquetaCaeEn(
        TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
        ResultadoDeVisita.SE_NEGO
    )

    @Test
    fun `tiene dinero pero no quiso cae en se nego`() = etiquetaCaeEn(
        TipoVisitaCatalogo.TIENE_PERO_NO_PAGA,
        ResultadoDeVisita.SE_NEGO
    )

    @Test
    fun `fue grosero o agresivo cae en se nego`() = etiquetaCaeEn(
        TipoVisitaCatalogo.FUE_GROSERO,
        ResultadoDeVisita.SE_NEGO
    )

    @Test
    fun `pidio reagendar visita cae en prometio`() = etiquetaCaeEn(
        TipoVisitaCatalogo.PIDE_REAGENDAR,
        ResultadoDeVisita.PROMETIO
    )

    /**
     * Las diez ofrecidas son **exactamente** las diez que ofrecía el diálogo al
     * que este catálogo reemplaza (`NewVisitDialog.visitConditionForm`,
     * retirado por la Task 21). Ni una menos: ese es el "el cobrador no
     * reaprende nada" del brief, medido.
     */
    @Test
    fun `se ofrecen las diez etiquetas de hoy, sin una menos`() {
        val diezDeHoy = listOf(
            TipoVisitaCatalogo.NO_SE_ENCONTRABA,
            TipoVisitaCatalogo.PIDE_REAGENDAR,
            TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
            TipoVisitaCatalogo.CASA_CERRADA,
            TipoVisitaCatalogo.SOLO_MENORES,
            TipoVisitaCatalogo.TIENE_PERO_NO_PAGA,
            TipoVisitaCatalogo.FUE_GROSERO,
            TipoVisitaCatalogo.SE_ESCONDE,
            TipoVisitaCatalogo.NO_RESPONDE,
            TipoVisitaCatalogo.SE_ESCUCHAN_RUIDOS
        )
        assertEquals(10, CatalogoDeResultados.ETIQUETAS_OFRECIDAS.size)
        assertEquals(diezDeHoy.toSet(), CatalogoDeResultados.ETIQUETAS_OFRECIDAS.toSet())
    }

    /** Ninguna etiqueta aparece bajo dos desenlaces: la partición es limpia. */
    @Test
    fun `ninguna etiqueta esta en dos grupos`() {
        val todas = CatalogoDeResultados.ETIQUETAS.values.flatten()
        assertEquals(todas.size, todas.toSet().size)
    }

    /** Todo literal ofrecido está en el catálogo cerrado que el servidor valida. */
    @Test
    fun `todo literal ofrecido pertenece al catalogo cerrado del servidor`() {
        CatalogoDeResultados.ETIQUETAS.values.flatten().forEach { etiqueta ->
            assertTrue(
                "$etiqueta no esta en el catalogo cerrado",
                TipoVisitaCatalogo.esConocido(etiqueta)
            )
        }
    }

    /**
     * La cita escribe `PIDE_TIEMPO`, y ese literal **sin día de cita** sigue
     * derivando "visité, vuelvo" — o sea que ninguna fila histórica cambia de
     * significado por reusarlo.
     */
    @Test
    fun `la cita escribe pide tiempo y ese literal sin dia no cambia de estado`() {
        assertEquals(
            listOf(TipoVisitaCatalogo.PIDE_TIEMPO),
            CatalogoDeResultados.etiquetasDe(ResultadoDeVisita.CITA)
        )
        assertEquals(
            EstadoCuenta.VISITE_VUELVO,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.PIDE_TIEMPO, tieneCita = false)
        )
        assertEquals(
            EstadoCuenta.CITA_A_UNA_HORA,
            TipoVisitaCatalogo.estadoDe(TipoVisitaCatalogo.PIDE_TIEMPO, tieneCita = true)
        )
    }

    /**
     * Y el alcance sigue al estado: una cita es del CLIENTE, así que se propaga
     * a todas sus cuentas; el mismo literal sin día sigue siendo de VENTA.
     */
    @Test
    fun `el alcance de la cita es del cliente y sin dia sigue siendo de venta`() {
        assertEquals(
            VisitScope.VENTA,
            TipoVisitaCatalogo.alcanceDe(TipoVisitaCatalogo.PIDE_TIEMPO, tieneCita = false)
        )
        assertEquals(
            VisitScope.CLIENTE,
            TipoVisitaCatalogo.alcanceDe(TipoVisitaCatalogo.PIDE_TIEMPO, tieneCita = true)
        )
    }

    /** Los dos grupos de una sola etiqueta no piden elegir; los de tres sí. */
    @Test
    fun `solo los grupos con mas de una etiqueta piden elegir`() {
        assertFalse(CatalogoDeResultados.pideEtiqueta(ResultadoDeVisita.PROMETIO))
        assertFalse(CatalogoDeResultados.pideEtiqueta(ResultadoDeVisita.CITA))
        assertTrue(CatalogoDeResultados.pideEtiqueta(ResultadoDeVisita.NO_ESTABA))
        assertTrue(CatalogoDeResultados.pideEtiqueta(ResultadoDeVisita.VISITE_VUELVO))
        assertTrue(CatalogoDeResultados.pideEtiqueta(ResultadoDeVisita.SE_NEGO))
    }

    /** Ningún desenlace se queda sin etiquetas: la función es TOTAL. */
    @Test
    fun `los cinco desenlaces tienen etiqueta`() {
        ResultadoDeVisita.entries.forEach {
            assertTrue(
                "$it sin etiquetas",
                CatalogoDeResultados.etiquetasDe(it).isNotEmpty()
            )
        }
    }

    /**
     * El puente con la Task 14: el estado del desenlace ES el estado al que el
     * catálogo manda su etiqueta. **No hay una segunda clasificación.**
     *
     * La cita se compara con `tieneCita = true` porque su estado lo sostiene el
     * día, no el literal — que es precisamente lo que este plan vino a arreglar.
     */
    private fun etiquetaCaeEn(etiqueta: String, resultado: ResultadoDeVisita) {
        assertTrue(
            "$etiqueta deberia estar bajo ${resultado.name}",
            CatalogoDeResultados.perteneceA(resultado, etiqueta)
        )
        assertEquals(
            "el estado del desenlace y el del catalogo tienen que coincidir",
            resultado.estado,
            TipoVisitaCatalogo.estadoDe(etiqueta)
        )
    }
}
