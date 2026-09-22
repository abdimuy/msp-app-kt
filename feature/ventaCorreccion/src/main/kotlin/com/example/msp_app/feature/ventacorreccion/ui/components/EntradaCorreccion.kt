package com.example.msp_app.feature.ventacorreccion.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion

/**
 * Punto de entrada a la corrección de una venta local (Task 5, montado desde
 * `SaleDescriptionScreen` en `:app`): la ÚNICA regla de "qué se pinta" — [BotonCorregir] sólo
 * cuando [estado] es [EstadoCorreccion.Corregible] o [EstadoCorreccion.CorregibleEnviada], el
 * [AvisoNoCorregible] correspondiente en cualquier otro caso. Invariante del plan: nunca se
 * ofrece corregir lo que no se puede corregir.
 *
 * [EstadoCorreccion.CorregibleEnviada] (nivel 2: la venta ya subió y el servidor la tiene en
 * borrador) se pinta EXACTAMENTE igual que [EstadoCorreccion.Corregible] — mismo botón, mismo
 * texto, mismo camino al editor. Lo único que cambia entre los dos es por dónde viaja la
 * corrección al guardarse (Room y ya, contra Room más la cola de correcciones remotas), y eso
 * no es asunto de este componente: si esta rama se separara, la UI empezaría a exponer dónde
 * está la venta, que es justo lo que al dueño no le importa.
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
        EstadoCorreccion.Corregible,
        EstadoCorreccion.CorregibleEnviada ->
            BotonCorregir(onClick = onCorregir, modifier = modifier)

        EstadoCorreccion.SeEstaEnviando ->
            AvisoNoCorregible(mensaje = TextosCorreccion.SE_ESTA_ENVIANDO, modifier = modifier)

        EstadoCorreccion.CorreccionEnCamino ->
            AvisoNoCorregible(mensaje = TextosCorreccion.CORRECCION_EN_CAMINO, modifier = modifier)

        EstadoCorreccion.LaOficinaYaLaAplico ->
            AvisoNoCorregible(mensaje = TextosCorreccion.LA_APLICO_LA_OFICINA, modifier = modifier)

        // Terminal igual que el de arriba — tampoco se ofrece corregir — pero con su propio
        // texto: aquí el servidor SÍ quedó distinto de como estaba, y es lo único que el
        // cobrador no puede deducir solo.
        EstadoCorreccion.SeAplicoAMedias ->
            AvisoNoCorregible(mensaje = TextosCorreccion.SE_APLICO_A_MEDIAS, modifier = modifier)

        EstadoCorreccion.YaSeEnvio ->
            AvisoNoCorregible(mensaje = TextosCorreccion.YA_SE_ENVIO, modifier = modifier)

        EstadoCorreccion.LaRevisaLaOficina ->
            AvisoNoCorregible(mensaje = TextosCorreccion.LA_REVISA_LA_OFICINA, modifier = modifier)

        null -> Unit
    }
}
