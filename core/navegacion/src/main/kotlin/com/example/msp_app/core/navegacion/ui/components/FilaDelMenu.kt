package com.example.msp_app.core.navegacion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.domain.model.IconoDelMenu

/**
 * El alto mínimo de toda opción del menú —filas, encabezados de sección y los dos
 * botones de la cabecera—: **50 dp**, el mínimo de la compuerta del repo
 * (`CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`, ≥50 dp por control).
 *
 * Antes era `touchTarget` (56 dp, spec §2.1.6). El dueño vio el menú en su Galaxy A25
 * (2026-10-07) y pidió filas más bajas "pero ≥48 dp de alto táctil": 50 cumple las
 * dos cosas. No es un token del tema a propósito: es un mínimo de accesibilidad
 * medido por una compuerta, igual que el `BOTON_MENU_SIZE` de la lista de clientes.
 */
val ALTO_DE_LA_OPCION: Dp = 50.dp

/**
 * Una opción del menú. Alto MÍNIMO [ALTO_DE_LA_OPCION]: crece si el texto envuelve a
 * escala 2.0, nunca se trunca (spec §5). Activa: fondo `brandTint`, texto `brand`
 * (spec §2.1.5).
 */
@Composable
fun FilaDelMenu(
    etiqueta: String,
    icono: IconoDelMenu,
    activo: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contador: String? = null
) {
    val colores = MspTheme.colors
    val tinta = if (activo) colores.brand else colores.onSurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_DE_LA_OPCION)
            .clip(MspTheme.shapes.control)
            .background(if (activo) colores.brandTint else colores.surface)
            .clickable(onClick = onClick)
            .semantics { selected = activo }
            .testTag(tag)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Icon(
            imageVector = icono.vector(),
            contentDescription = null,
            tint = if (activo) colores.brand else colores.onSurfaceMuted,
            modifier = Modifier.size(MspTheme.spacing.lg)
        )
        ContenidoDeLaOpcion(
            etiqueta = etiqueta,
            contador = contador,
            estiloEtiqueta = if (activo) MspTheme.type.bodyStrong else MspTheme.type.body,
            colorEtiqueta = tinta,
            colorContador = if (activo) colores.brand else colores.onSurfaceMuted,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Etiqueta + contador (ruling R13 del controlador, 2026-10-06): la etiqueta
 * NUNCA se parte a media palabra. Antes, con `etiqueta` a `weight(1f)` y
 * `contador` como hermano fijo en el mismo `Row`, un contador largo ("8 por
 * volver") le quitaba ancho a la etiqueta hasta forzar a Compose a partir la
 * única palabra que no cabía ("Client" / "es") — visible en los goldens
 * `menu_*_2_0` de la Task 5.
 *
 * El arreglo mide con `TextMeasurer` (sin restricción de ancho, así da el
 * ancho natural de una sola línea) cuánto ocupan etiqueta y contador juntos
 * contra el ancho real disponible (`BoxWithConstraints`): si caben, van en la
 * misma fila igual que antes (contador pegado al borde derecho vía
 * `SpaceBetween`); si no caben, el contador baja a su propio renglón debajo de
 * la etiqueta, alineado a la izquierda con ella y con el mismo estilo — nunca
 * se trunca ni se parte la palabra, porque la etiqueta recibe entonces el
 * ancho completo de la fila.
 *
 * Elegí medir-y-decidir (`BoxWithConstraints` + `TextMeasurer`) en vez de
 * `FlowRow` porque `FlowRow` decide si un hijo entra en la fila actual
 * midiéndolo YA acotado al espacio que queda — el mismo defecto que se está
 * arreglando, sólo que dentro del propio `FlowRow`. Medir el ancho natural
 * aparte, antes de decidir el layout, es lo único que evita partir la palabra
 * en el primer intento.
 */
@Composable
private fun ContenidoDeLaOpcion(
    etiqueta: String,
    contador: String?,
    estiloEtiqueta: TextStyle,
    colorEtiqueta: Color,
    colorContador: Color,
    modifier: Modifier = Modifier
) {
    if (contador == null) {
        Text(text = etiqueta, style = estiloEtiqueta, color = colorEtiqueta, modifier = modifier)
        return
    }
    val estiloContador = MspTheme.type.caption
    val medidor = rememberTextMeasurer()
    val densidad = LocalDensity.current
    BoxWithConstraints(modifier) {
        val holguraPx = with(densidad) { MspTheme.spacing.sm.roundToPx() }
        val anchoEtiqueta =
            medidor.measure(
                text = etiqueta,
                style = estiloEtiqueta,
                maxLines = 1,
                softWrap = false
            ).size.width
        val anchoContador =
            medidor.measure(
                text = contador,
                style = estiloContador,
                maxLines = 1,
                softWrap = false
            ).size.width
        val cabenEnUnaLinea = anchoEtiqueta + holguraPx + anchoContador <= constraints.maxWidth

        if (cabenEnUnaLinea) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = etiqueta, style = estiloEtiqueta, color = colorEtiqueta)
                Text(text = contador, style = estiloContador, color = colorContador)
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = etiqueta,
                    style = estiloEtiqueta,
                    color = colorEtiqueta,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = contador,
                    style = estiloContador,
                    color = colorContador,
                    modifier = Modifier.padding(top = MspTheme.spacing.xs)
                )
            }
        }
    }
}
