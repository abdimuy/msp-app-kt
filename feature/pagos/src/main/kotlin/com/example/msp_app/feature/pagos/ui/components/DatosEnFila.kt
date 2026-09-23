package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Tres datos en fila cuando caben, apilados cuando no.
 *
 * Con `FontSizeLevel.NORMAL` los tres van en una fila de tercios, como el mock.
 * Con `GRANDE` (1.5) o `MUY_GRANDE` (2.0) la fila deja de caber en 360dp y el
 * tercer dato se sale de la pantalla — comprobado en el golden
 * `pagos_venta_light_2_0` antes de este cambio: "frecuencia" desaparecía. Un
 * dato que se sale no es un detalle visual, es información perdida para el
 * usuario que MÁS ayuda necesita, así que a esas escalas se apilan.
 *
 * Se lee [LocalFontSizeLevel] (la preferencia elegida en la app) y no el
 * `fontScale` del sistema: es el mismo criterio que ya usa el reporte de
 * cobranza para escoger su layout curado.
 */
@Composable
fun TresDatos(
    primero: @Composable (Modifier) -> Unit,
    segundo: @Composable (Modifier) -> Unit,
    tercero: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    DatosEnFilaOApilados(modifier, primero, segundo, tercero)
}

/**
 * **Dos** datos, con exactamente el mismo criterio de [TresDatos]: en fila de
 * mitades mientras quepan, apilados a `GRANDE` y `MUY_GRANDE`.
 *
 * Existe porque dos bloques se quedaron en dos cifras y **forzar una tercera
 * celda vacía inventa un hueco donde antes había un dato**: el pie del detalle
 * de venta, al que el dueño le quitó el conteo de abonos, y las cifras del
 * detalle de cliente, que perdieron "suele dar" y "pídele hoy".
 *
 * Es una variante y no un parámetro opcional de [TresDatos] a propósito: la
 * firma de tres la comparte el pie del riel (`RitmoYRiel`), que sí tiene tres
 * cosas que decir, y volverla variádica obligaría a ese llamador a probar en
 * tiempo de ejecución algo que hoy el compilador le garantiza.
 */
@Composable
fun DosDatos(
    primero: @Composable (Modifier) -> Unit,
    segundo: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    DatosEnFilaOApilados(modifier, primero, segundo)
}

/**
 * La decisión de layout que comparten [TresDatos] y [DosDatos], **en un solo
 * sitio**.
 *
 * Vivía duplicada: [TresDatos] la tenía escrita y `CifrasDelCliente` la volvió
 * a escribir palabra por palabra cuando se quedó con dos cifras, con un KDoc
 * que explicaba que no podía reusar la de tres. Dos copias de la misma regla
 * es cómo se termina con una pantalla que se apila a `GRANDE` y otra que no.
 */
@Composable
private fun DatosEnFilaOApilados(
    modifier: Modifier,
    vararg celdas: @Composable (Modifier) -> Unit
) {
    if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            celdas.forEach { celda -> celda(Modifier.weight(1f)) }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            celdas.forEach { celda -> celda(Modifier.fillMaxWidth()) }
        }
    }
}
