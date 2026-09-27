package com.example.msp_app.feature.ubicacion.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ningún mapa de cobranza enseña tiendas ni negocios, en ningún tema.**
 *
 * El dueño, mirando el aparato: *"que no muestre los nombres e iconos de los
 * establecimientos y tiendas, siento que quedan muy feos"*. El defecto de
 * fondo era que el estilo de noche ya los apagaba y el de día **no existía**:
 * el mismo mapa se veía limpio o lleno de tiendas según el tema.
 *
 * Mide la regla en el texto del estilo, que es lo que Google Maps recibe. Que
 * el mapa de verdad la aplique sólo se ve en el aparato (el mapa no pinta en
 * Robolectric ni en `devlocal`): esta prueba cuida que nadie la quite, no que
 * el SDK la obedezca.
 */
class ElMapaNoEnseniaTiendasTest {

    @Test
    fun `de dia no hay puntos de interes`() {
        assertTrue("el estilo de día enseña tiendas", apaga(ESTILO_CLARO, "poi"))
    }

    @Test
    fun `de noche no hay puntos de interes`() {
        assertTrue("el estilo de noche enseña tiendas", apaga(ESTILO_OSCURO, "poi"))
    }

    @Test
    fun `de dia no hay transporte`() {
        assertTrue("el estilo de día enseña transporte", apaga(ESTILO_CLARO, "transit"))
    }

    /**
     * Control positivo: el buscador tiene que poder decir "no". Una regla que
     * ningún estilo trae no puede dar verde, o [apaga] no estaría mirando nada.
     */
    @Test
    fun `el buscador distingue una regla ausente`() {
        assertTrue("el buscador dijo que sí a una regla inexistente", !apaga(ESTILO_CLARO, "road"))
    }

    /** ¿El estilo trae `featureType: [tipo]` con `visibility: off` para todo el tipo? */
    private fun apaga(estilo: String, tipo: String): Boolean = estilo.filterNot(Char::isWhitespace)
        .contains("""{"featureType":"$tipo","stylers":[{"visibility":"off"}]}""")
}
