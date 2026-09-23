package com.example.msp_app.feature.visitas.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La regla acotada que devuelve al papel la línea "SU FECHA DE VENCIMIENTO DE
 * SU CREDITO ES EL DIA: ...".
 *
 * Lo que estas pruebas fijan es **el borde**, que es donde estaba el defecto
 * del ticket viejo: aquel aplicaba el año a todas las ventas. Aquí cuatro meses
 * de corto plazo vencen a un año y **ningún otro plazo afirma nada**.
 */
class VencimientoDelCreditoTest {

    private val marzo = LocalDate.of(2026, 3, 14)

    @Test
    fun `cuatro meses de corto plazo vencen un anio despues de la venta`() {
        assertEquals(
            LocalDate.of(2027, 3, 14),
            VencimientoDelCredito.de(marzo, VencimientoDelCredito.PLAZO_CON_VENCIMIENTO_CONOCIDO)
        )
    }

    @Test
    fun `ningun otro plazo produce vencimiento`() {
        // Cero incluido: es lo que trae una venta sin el dato capturado, y el
        // ticket viejo también le imprimía su año.
        listOf(0, 1, 3, 5, 6, 8, 12, 18).forEach { plazo ->
            assertNull("plazo $plazo", VencimientoDelCredito.de(marzo, plazo))
        }
    }

    @Test
    fun `sin fecha de venta legible no hay vencimiento ni excepcion`() {
        assertNull(
            VencimientoDelCredito.de(null, VencimientoDelCredito.PLAZO_CON_VENCIMIENTO_CONOCIDO)
        )
    }

    @Test
    fun `el 29 de febrero se recorre al 28 y no revienta`() {
        // 2028 es bisiesto y 2029 no. `LocalDate.plusYears` recorta en vez de
        // lanzar, así que el caso no necesita un camino aparte — pero sí una
        // prueba, porque el día que alguien cambie la aritmética se va a notar.
        assertEquals(
            LocalDate.of(2029, 2, 28),
            VencimientoDelCredito.de(
                LocalDate.of(2028, 2, 29),
                VencimientoDelCredito.PLAZO_CON_VENCIMIENTO_CONOCIDO
            )
        )
    }
}
