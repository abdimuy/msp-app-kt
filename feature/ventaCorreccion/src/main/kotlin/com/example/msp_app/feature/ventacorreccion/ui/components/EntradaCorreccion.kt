package com.example.msp_app.feature.ventacorreccion.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion

/**
 * Punto de entrada a la corrección de una venta local (Task 5, montado desde
 * `SaleDescriptionScreen` en `:app`): la ÚNICA regla de "qué se pinta" — [BotonCorregir] sólo
 * cuando [estado] es [EstadoCorreccion.Corregible], el [AvisoNoCorregible] correspondiente en
 * cualquier otro caso. Invariante del plan: nunca se ofrece corregir lo que no se puede corregir.
 *
 * `estado == null` (todavía no se consultó, o la venta no existe) no pinta nada — ofrecer el
 * botón por defecto mientras se resuelve la consulta sería, otra vez, ofrecer corregir sin saber
 * si se puede.
 *
 * Vive en `:feature:ventaCorreccion` (no en `:app`) a propósito: es la pieza que el Compose UI
 * test de esta tarea (`AvisoNoCorregibleTest`) ejercita para blindar la regla — un `when` copiado
 * a mano dentro de `:app` no tendría ninguna prueba corriendo sobre él, porque `:app` no aplica
 * Roborazzi/Compose-test (`app/build.gradle.kts`).
 */
@Composable
fun EntradaCorreccion(
    estado: EstadoCorreccion?,
    onCorregir: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (estado) {
        EstadoCorreccion.Corregible -> BotonCorregir(onClick = onCorregir, modifier = modifier)

        EstadoCorreccion.SeEstaEnviando ->
            AvisoNoCorregible(mensaje = TextosCorreccion.SE_ESTA_ENVIANDO, modifier = modifier)

        EstadoCorreccion.YaSeEnvio ->
            AvisoNoCorregible(mensaje = TextosCorreccion.YA_SE_ENVIO, modifier = modifier)

        EstadoCorreccion.LaRevisaLaOficina ->
            AvisoNoCorregible(mensaje = TextosCorreccion.LA_REVISA_LA_OFICINA, modifier = modifier)

        null -> Unit
    }
}
