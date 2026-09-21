package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **El punto de cada contacto llega al contacto correcto.**
 *
 * El dueño pidió que tocar un abono o una visita abra el mapa *"solo con la
 * ubicación de ese pago o visita en particular"*. Eso se cae por dos caminos
 * distintos, y los dos se cobran aquí:
 *
 *  1. **Que el punto se cruce de fila.** La mezcla ordena por fecha, así que el
 *     abono y la visita cambian de posición según los datos; copiar la ubicación
 *     por índice en vez de por hecho daría un mapa que abre la puerta equivocada
 *     sin que nada se vea roto.
 *  2. **Que el par en cero pase por punto.** `VisitEntity.LAT`/`LNG` son `Double`
 *     no nulos: una visita registrada con el GPS apagado trae `0.0, 0.0`, que es
 *     un lugar de verdad —el Golfo de Guinea— y no una ausencia.
 *
 * ## El control positivo, y por qué el mismo test lo lleva dentro
 *
 * "El cero llega como `null`" es una **ausencia**, y una ausencia no vale hasta
 * probar que el método habría encontrado la cosa. Por eso el caso del cero se
 * siembra junto a una coordenada real de la ruta: si el día de mañana alguien
 * dejara de mapear las coordenadas de la visita, el `null` seguiría apareciendo
 * y el test pasaría en verde mintiendo. Con la coordenada real al lado, ese
 * cambio lo pone en rojo.
 */
class BitacoraDelClienteTest {

    /**
     * Cada hecho se lleva SU punto: el del abono al renglón del abono, el de la
     * visita al de la visita. Las dos coordenadas son distintas a propósito —con
     * una sola, cruzarlas no se notaría.
     */
    @Test
    fun `la ubicacion del pago y la de la visita llegan al contacto correcto`() {
        val contactos = BitacoraDelCliente.de(
            visitas = listOf(visita("v1", DIA_DE_LA_VISITA, PUNTO_DE_LA_VISITA)),
            pagos = listOf(pago("p1", DIA_DEL_ABONO, PUNTO_DEL_ABONO))
        )
        val porFecha = contactos.associateBy { it.fecha }

        assertEquals(
            "la visita se quedó sin su punto, o con el del abono",
            PUNTO_DE_LA_VISITA,
            porFecha.getValue(Instant.parse(DIA_DE_LA_VISITA)).ubicacion
        )
        assertEquals(
            "el abono se quedó sin su punto, o con el de la visita",
            PUNTO_DEL_ABONO,
            porFecha.getValue(Instant.parse(DIA_DEL_ABONO)).ubicacion
        )
        // Control de que la selección de arriba no se comió un renglón: la mezcla
        // tiene que traer los dos hechos, no uno.
        assertEquals(2, contactos.size)
    }

    /**
     * **El par en cero no es un lugar**, con su control positivo al lado.
     *
     * Las dos visitas entran por la MISMA puerta que usa el adaptador
     * ([UbicacionDelCobro.medida]), que es lo que el adaptador puede producir de
     * verdad: `VisitEntity` no admite nulos en esas columnas.
     */
    @Test
    fun `un cero cero llega como nulo, y una coordenada real llega entera`() {
        val contactos = BitacoraDelCliente.de(
            visitas = listOf(
                visita("sin-senal", DIA_DE_LA_VISITA, UbicacionDelCobro.medida(0.0, 0.0)),
                visita(
                    "con-senal",
                    DIA_DEL_ABONO,
                    UbicacionDelCobro.medida(PUNTO_DE_LA_VISITA.lat, PUNTO_DE_LA_VISITA.lng)
                )
            ),
            pagos = emptyList()
        )
        val porFecha = contactos.associateBy { it.fecha }

        // La ausencia.
        assertNull(
            "el par en cero se coló como punto: eso pinta un pin en el Golfo de Guinea",
            porFecha.getValue(Instant.parse(DIA_DE_LA_VISITA)).ubicacion
        )
        // El control positivo: la misma consulta SÍ ve una coordenada cuando la hay.
        val medida = porFecha.getValue(Instant.parse(DIA_DEL_ABONO)).ubicacion
        assertNotNull("sin esto, un mapeo que devolviera siempre null pasaría en verde", medida)
        assertEquals(PUNTO_DE_LA_VISITA, medida)
    }

    /** Un contacto sin punto medido llega sin punto, no con el del vecino. */
    @Test
    fun `un contacto sin punto no hereda el del otro`() {
        val contactos = BitacoraDelCliente.de(
            visitas = listOf(visita("v1", DIA_DE_LA_VISITA, ubicacion = null)),
            pagos = listOf(pago("p1", DIA_DEL_ABONO, PUNTO_DEL_ABONO))
        )
        val porFecha = contactos.associateBy { it.fecha }

        assertNull(
            "la visita se quedó con el punto del abono",
            porFecha.getValue(Instant.parse(DIA_DE_LA_VISITA)).ubicacion
        )
        // Control positivo: el abono de al lado SÍ trae el suyo, así que el `null`
        // de arriba es el dato y no un mapeo que se perdió.
        assertEquals(PUNTO_DEL_ABONO, porFecha.getValue(Instant.parse(DIA_DEL_ABONO)).ubicacion)
    }

    /**
     * **La etiqueta de la visita conserva la mayúscula del catálogo cerrado.**
     *
     * `visita.tipoVisita` viene de [com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo],
     * que ya guarda sus 14 literales con mayúscula inicial (defendido por
     * `TipoVisitaCatalogoTest`). Forzar `.lowercase()` aquí — como hacía esta
     * mezcla antes de la Task 1 — rompía esa garantía sin que el literal
     * mintiera en ningún archivo: el defecto vivía en una llamada, no en un
     * texto, así que el barrido estático de
     * `CadaTextoDeUsuarioEmpiezaEnMayusculaTest` nunca lo iba a ver. Sólo esta
     * prueba de comportamiento lo cobra.
     */
    @Test
    fun `la etiqueta de la visita no se fuerza a minuscula`() {
        val contactos = BitacoraDelCliente.de(
            visitas = listOf(visita("v1", DIA_DE_LA_VISITA, PUNTO_DE_LA_VISITA)),
            pagos = emptyList()
        )
        assertEquals("No estaba", contactos.single().etiqueta)
    }

    /** Media coordenada sigue sin ubicar nada — la regla vieja no se perdió. */
    @Test
    fun `media coordenada no es una ubicacion`() {
        assertNull(UbicacionDelCobro.de(PUNTO_DEL_ABONO.lat, null))
        assertNull(UbicacionDelCobro.de(null, PUNTO_DEL_ABONO.lng))
        assertNotNull(UbicacionDelCobro.de(PUNTO_DEL_ABONO.lat, PUNTO_DEL_ABONO.lng))
    }

    /**
     * El cero **del par**, no el cero suelto. Una latitud de 0.0 con longitud real
     * es el ecuador y es un lugar; descartarla sería tirar un dato bueno. La regla
     * copiada es la de `SaleMapScreen.kt:52-65`, que descarta solo el par.
     */
    @Test
    fun `solo el par en cero se descarta, no una coordenada que valga cero`() {
        assertNotNull(UbicacionDelCobro.medida(0.0, PUNTO_DEL_ABONO.lng))
        assertNotNull(UbicacionDelCobro.medida(PUNTO_DEL_ABONO.lat, 0.0))
        assertNull(UbicacionDelCobro.medida(0.0, 0.0))
    }

    private fun visita(id: String, fechaIso: String, ubicacion: UbicacionDelCobro?) =
        VisitaDelCliente(
            visitaId = id,
            clienteId = CLIENTE,
            ventaId = VENTA,
            fecha = Instant.parse(fechaIso),
            tipoVisita = "No estaba",
            nota = null,
            ubicacion = ubicacion
        )

    private fun pago(id: String, fechaIso: String, ubicacion: UbicacionDelCobro?) =
        PagoDelHistorial(
            pagoId = id,
            ventaId = VENTA,
            fecha = Instant.parse(fechaIso),
            importe = Money.of(BigDecimal("350.00")),
            formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
            metodo = MetodoDeCobro.EFECTIVO,
            nota = null,
            ubicacion = ubicacion
        )

    private companion object {
        const val CLIENTE = 5021
        const val VENTA = 77188

        /** Dos instantes distintos: son la llave con la que el test elige renglón. */
        const val DIA_DE_LA_VISITA = "2026-09-01T16:00:00Z"
        const val DIA_DEL_ABONO = "2026-08-03T17:10:00Z"

        /** Dos puntos distintos de la ruta: con uno solo, cruzarlos no se notaría. */
        val PUNTO_DE_LA_VISITA = UbicacionDelCobro(lat = 18.4609, lng = -97.3926)
        val PUNTO_DEL_ABONO = UbicacionDelCobro(lat = 18.4712, lng = -97.4011)
    }
}
