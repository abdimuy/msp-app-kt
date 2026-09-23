package com.example.msp_app.core.utils

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guardia de deriva entre `Constants` (`:app`, lo que viaja por el cable) y la
 * copia del catálogo en `:core:common` (Task 14).
 *
 * La copia existe porque `:core:common` no puede depender de `:app`. Este test
 * es lo que la hace segura: si alguien renombra un literal en `Constants` —como
 * ya pasó en el commit f7de5f02, que dejó `"Dijo que no va a pagar"` llegando
 * cinco meses más— el catálogo deja de reconocer el valor nuevo y este test se
 * pone rojo antes de que el estado se derive mal en producción.
 *
 * Control positivo: el test `la copia reconoce los literales que Constants declara`
 * pregunta por `esConocido` sobre cada valor de `Constants`, así que si el
 * método no encontrara nada, el propio test fallaría en vez de pasar en vacío.
 */
class TipoVisitaCatalogoDriftTest {

    private val literalesDeConstants = listOf(
        Constants.NO_SE_ENCONTRABA,
        Constants.CASA_CERRADA,
        Constants.SOLO_MENORES,
        Constants.NO_VA_A_DAR_PAGO,
        Constants.PIDE_TIEMPO,
        Constants.TIENE_PERO_NO_PAGA,
        Constants.FUE_GROSERO,
        Constants.SE_ESCONDE,
        Constants.NO_RESPONDE,
        Constants.SE_ESCUCHAN_RUIDOS,
        Constants.PIDE_REAGENDAR
    )

    @Test
    fun `Constants declara 11 literales de tipo de visita`() {
        assertEquals(11, literalesDeConstants.size)
        assertEquals(11, literalesDeConstants.toSet().size)
    }

    @Test
    fun `cada literal de Constants es identico al del catalogo, byte por byte`() {
        assertEquals(Constants.NO_SE_ENCONTRABA, TipoVisitaCatalogo.NO_SE_ENCONTRABA)
        assertEquals(Constants.CASA_CERRADA, TipoVisitaCatalogo.CASA_CERRADA)
        assertEquals(Constants.SOLO_MENORES, TipoVisitaCatalogo.SOLO_MENORES)
        assertEquals(Constants.NO_VA_A_DAR_PAGO, TipoVisitaCatalogo.NO_VA_A_DAR_PAGO)
        assertEquals(Constants.PIDE_TIEMPO, TipoVisitaCatalogo.PIDE_TIEMPO)
        assertEquals(Constants.TIENE_PERO_NO_PAGA, TipoVisitaCatalogo.TIENE_PERO_NO_PAGA)
        assertEquals(Constants.FUE_GROSERO, TipoVisitaCatalogo.FUE_GROSERO)
        assertEquals(Constants.SE_ESCONDE, TipoVisitaCatalogo.SE_ESCONDE)
        assertEquals(Constants.NO_RESPONDE, TipoVisitaCatalogo.NO_RESPONDE)
        assertEquals(Constants.SE_ESCUCHAN_RUIDOS, TipoVisitaCatalogo.SE_ESCUCHAN_RUIDOS)
        assertEquals(Constants.PIDE_REAGENDAR, TipoVisitaCatalogo.PIDE_REAGENDAR)
    }

    @Test
    fun `los 11 vigentes del catalogo son exactamente los de Constants`() {
        assertEquals(literalesDeConstants.toSet(), TipoVisitaCatalogo.LITERALES_VIGENTES.toSet())
    }

    @Test
    fun `la copia reconoce los literales que Constants declara`() {
        literalesDeConstants.forEach { literal ->
            assertTrue("el catalogo no reconoce '$literal'", TipoVisitaCatalogo.esConocido(literal))
        }
    }

    @Test
    fun `las formas de cobro del catalogo son las de Constants, sin la condonacion`() {
        assertEquals(
            setOf(
                Constants.PAGO_EN_EFECTIVO_ID,
                Constants.PAGO_CON_CHEQUE_ID,
                Constants.PAGO_CON_TRANSFERENCIA_ID
            ),
            VentanaCobro.FORMAS_COBRO_COBRANZA
        )
        assertTrue(Constants.CONDONACION_ID !in VentanaCobro.FORMAS_COBRO_COBRANZA)
    }
}
