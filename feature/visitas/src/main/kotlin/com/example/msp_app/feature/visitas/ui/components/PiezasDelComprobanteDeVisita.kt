package com.example.msp_app.feature.visitas.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.domain.model.ComprobanteDeVisita
import com.example.msp_app.feature.visitas.ui.FalloDeLaFoto

/** `testTag` de la sección de comprobantes de la pantalla de visita. */
const val COMPROBANTES_TAG: String = "visitas_comprobantes"

/** `testTag` del botón que abre la cámara desde la sección, al pie de la columna. */
const val AGREGAR_FOTO_TAG: String = "visitas_agregar_foto"

/** `testTag` del botón de cámara **en línea**, en la fila del encabezado. */
const val FOTO_EN_LINEA_TAG: String = "visitas_foto_en_linea"

/** Prefijo del `testTag` del botón que quita un comprobante; se completa con su id. */
const val QUITAR_FOTO_TAG: String = "visitas_quitar_foto_"

/** `testTag` del aviso ámbar de la foto que no se adjuntó. */
const val FALLO_FOTO_TAG: String = "visitas_fallo_foto"

/**
 * **Los comprobantes de la visita** (Task 23), al pie de la columna que hace
 * scroll — el hueco que el KDoc de `RegistrarVisitaContent` ya le tenía
 * reservado ("la foto entra al final de la columna que hace scroll").
 *
 * ## Por qué NO hay miniaturas
 *
 * Enseñar la foto exigiría un cargador de imágenes (`coil`) en un módulo que hoy
 * no lo tiene, y volvería los goldens dependientes de una carga asíncrona —el
 * verde de Roborazzi dejaría de significar "la pantalla no cambió". Lo que el
 * cobrador necesita saber acá es **cuántas lleva y poder quitar la que sobra**;
 * verlas otra vez es trabajo de la galería, que ya está a un toque.
 *
 * ## El aviso es ámbar, nunca rojo
 *
 * Ninguno de los tres fallos impide registrar la visita, y el rojo en esta
 * pantalla ya significa otra cosa: es el color de `BandaDeFallo`, "la visita no
 * quedó registrada". Pintar una foto fallida en rojo diría que se perdió trabajo
 * de campo, y no se perdió.
 */
@Composable
fun SeccionDeComprobantesDeVisita(
    comprobantes: List<ComprobanteDeVisita>,
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
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        RotuloDeSeccion("fotos")
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
        BotonDeAgregarFoto(habilitado = puedeAgregar, onAgregar = onAgregar)
    }
}

/**
 * El botón de la sección. Es un [ChipDeOpcion] y no un botón primario: el azul
 * de marca está reservado para "todo lo protagónico", y en esta pantalla lo
 * protagónico es guardar la visita. Adjuntar una foto es opcional por
 * definición y no compite con el CTA del dock.
 */
@Composable
private fun BotonDeAgregarFoto(habilitado: Boolean, onAgregar: () -> Unit) {
    ChipDeOpcion(
        texto = "agregar foto",
        activo = false,
        habilitado = habilitado,
        onElegir = onAgregar,
        modifier = Modifier.testTag(AGREGAR_FOTO_TAG)
    )
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
            text = "foto $numero",
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
 * **El punto de entrada visible** a la evidencia: un botón `+ foto` al final de
 * la fila del encabezado, a la derecha del botón de atrás.
 *
 * ## Por qué aquí, y qué se midió
 *
 * A 360×800dp la sección de comprobantes vive **debajo de la línea de
 * flotación** — está al pie de una columna que arranca con el encabezado, el
 * título, la tira del cliente y los cinco desenlaces. Es exactamente lo que le
 * pasó a pagos (Task 22, H1), y la salida es la misma: un afordante de **cero dp
 * verticales** arriba, donde ya hay una fila con espacio de sobra.
 *
 * La fila del encabezado de visitas **sí** tiene ancho, y ahí difiere de pagos:
 * `BarraDeVisita` es un único botón de atrás de 56dp contra un ancho de
 * pantalla; no hay nombre de cliente compitiendo por el borde derecho (en
 * visitas el nombre vive en `TiraDelCliente`, en su propio renglón). O sea que
 * el argumento que en pagos descartó el encabezado —el apellido llegando al
 * borde a escala 2.0— aquí no aplica.
 *
 * Dice `+ foto` y no solo `+`: un signo suelto no dice qué agrega. Lleva el
 * conteo cuando hay fotos (`+ foto 2`), así que es también el indicador de que
 * la evidencia está puesta sin bajar hasta la sección.
 */
@Composable
fun BotonDeFotoEnLinea(
    cuantos: Int,
    habilitado: Boolean,
    onAgregar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val contenido = if (habilitado) colors.brand else colors.onSurfaceMuted
    Surface(
        onClick = onAgregar,
        enabled = habilitado,
        modifier = modifier
            .heightIn(min = TOQUE)
            .widthIn(min = TOQUE)
            .testTag(FOTO_EN_LINEA_TAG),
        shape = MspTheme.shapes.control,
        color = if (cuantos > 0) colors.brandTint else colors.surface,
        border = BorderStroke(1.5.dp, if (cuantos > 0) colors.brand else colors.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MspTheme.spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(
                MspTheme.spacing.xs,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                // La descripción dice la ACCIÓN, no el dibujo: es lo que un
                // lector de pantalla tiene que anunciar.
                contentDescription = "agregar foto",
                tint = contenido,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (cuantos > 0) "foto $cuantos" else "foto",
                style = MspTheme.type.bodyStrong,
                color = contenido,
                maxLines = 1
            )
        }
    }
}
