package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ComprobanteDelAbono
import com.example.msp_app.feature.pagos.ui.FalloDeLaFoto

/** `testTag` de la sección de comprobantes de la pantalla de abono. */
const val COMPROBANTES_TAG: String = "pagos_abono_comprobantes"

/** `testTag` del botón que abre la cámara. */
const val AGREGAR_FOTO_TAG: String = "pagos_abono_agregar_foto"

/** Prefijo del `testTag` del botón que quita un comprobante; se completa con su id. */
const val QUITAR_FOTO_TAG: String = "pagos_abono_quitar_foto_"

/** `testTag` del aviso ámbar de la foto que no se adjuntó. */
const val FALLO_FOTO_TAG: String = "pagos_abono_fallo_foto"

/** `testTag` de la línea de comprobantes dentro de la hoja de confirmación. */
const val COMPROBANTES_EN_HOJA_TAG: String = "pagos_abono_comprobantes_hoja"

/**
 * Alto mínimo tocable. Copia deliberada del `TOQUE` privado de
 * `PiezasDelAbono.kt`: el plan pide >=50px y el token del design system (56dp)
 * va por encima. No se declara, **se mide** en `AbonoSeVeYSeTocaTest`.
 */
private val TOQUE = 56.dp

/**
 * **Los comprobantes del abono** (Task 22), en el hueco que el diseño le dejó:
 * debajo del teclado, dentro de la columna que hace scroll.
 *
 * ## Por qué NO hay miniaturas
 *
 * Enseñar la foto exigiría un cargador de imágenes (`coil`) en un módulo que
 * hoy no lo tiene, y volvería los goldens dependientes de una carga asíncrona
 * —el verde de Roborazzi dejaría de significar "la pantalla no cambió". Lo que
 * el cobrador necesita saber en esta pantalla es **cuántas lleva y poder quitar
 * la que sobra**; verlas otra vez es trabajo de la galería, que ya está a un
 * toque. Si algún día hay una razón medida para las miniaturas, entra con su
 * dependencia y sus goldens propios.
 *
 * ## El aviso es ámbar, nunca rojo
 *
 * Ninguno de los tres fallos impide registrar el abono, y el rojo en esta
 * pantalla ya significa "esto no se puede registrar" (el bloqueo duro). Pintar
 * una foto fallida en rojo diría que el dinero está en riesgo, y no lo está: es
 * el mismo criterio del Ruling AM para el abono corto.
 */
@Composable
fun SeccionDeComprobantes(
    comprobantes: List<ComprobanteDelAbono>,
    fallo: FalloDeLaFoto?,
    puedeAgregar: Boolean,
    onAgregar: () -> Unit,
    onQuitar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPROBANTES_TAG),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = "comprobantes",
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted
        )
        comprobantes.forEachIndexed { indice, comprobante ->
            FilaDeComprobante(
                // El número que se enseña es la POSICIÓN, empezando en 1: el id
                // es un UUID y no le dice nada a nadie parado en una puerta.
                numero = indice + 1,
                onQuitar = { onQuitar(comprobante.id) },
                tag = QUITAR_FOTO_TAG + comprobante.id
            )
        }
        if (fallo != null) AvisoDeLaFoto(fallo)
        MspPrimaryFieldButton(
            text = "agregar foto",
            onClick = onAgregar,
            enabled = puedeAgregar,
            variant = PrimaryFieldButtonVariant.Ghost,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AGREGAR_FOTO_TAG)
        )
    }
}

@Composable
private fun FilaDeComprobante(numero: Int, onQuitar: () -> Unit, tag: String) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, MspTheme.shapes.control)
            .padding(start = 13.dp)
            .heightIn(min = TOQUE),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "comprobante $numero",
            style = MspTheme.type.body,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = onQuitar,
            modifier = Modifier
                .size(TOQUE)
                .testTag(tag),
            color = colors.surface,
            shape = MspTheme.shapes.control
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "quitar",
                    tint = colors.onSurfaceMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Por qué la foto no se adjuntó. Tres textos y no uno: al cobrador le sirve
 * saber si vuelve a intentar, si el archivo no sirve, o si ya no caben más.
 */
@Composable
private fun AvisoDeLaFoto(fallo: FalloDeLaFoto) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.statusPartialTint, MspTheme.shapes.control)
            .border(1.dp, colors.statusPartial, MspTheme.shapes.control)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(FALLO_FOTO_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = colors.statusPartial,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = when (fallo) {
                FalloDeLaFoto.NO_SE_PUDO_TOMAR -> "no se pudo adjuntar"
                FalloDeLaFoto.TIPO_NO_PERMITIDO -> "ese archivo no se acepta"
                FalloDeLaFoto.YA_NO_CABEN -> "ya no caben más"
            },
            style = MspTheme.type.bodyStrong,
            color = colors.statusPartial
        )
    }
}

/**
 * La línea de comprobantes **dentro de la hoja de confirmación**, entre la
 * cifra y el flujo de saldos.
 *
 * Se pinta SIEMPRE, también en cero, y ese es su trabajo entero: la hoja
 * existe para que el cobrador vea lo que va a registrar antes de registrarlo, y
 * "sin comprobante" es un dato que se puede corregir con un toque de "editar".
 * Enseñarla solo cuando hay fotos convertiría el olvido en silencio.
 */
@Composable
fun ComprobantesDeLaHoja(cuantos: Int, modifier: Modifier = Modifier) {
    Text(
        text = when (cuantos) {
            0 -> "sin comprobante"
            1 -> "1 comprobante"
            else -> "$cuantos comprobantes"
        },
        style = MspTheme.type.captionStrong,
        color = MspTheme.colors.onSurfaceMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPROBANTES_EN_HOJA_TAG)
    )
}
