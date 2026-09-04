@file:Suppress(
    "TooManyFunctions"
) // una pieza por bloque del mock; el archivo es el catálogo de la pantalla.

package com.example.msp_app.feature.visitas.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.model.VentaParaVisitar
import com.example.msp_app.feature.visitas.ui.ResultadoVisual
import com.example.msp_app.feature.visitas.ui.resultadoVisualDe

/** `testTag` del botón "atrás". */
const val ATRAS_TAG: String = "visitas_atras"

/** `testTag` de cada renglón de desenlace. Se sufija con el nombre del enum. */
const val OPCION_TAG: String = "visitas_opcion_"

/** `testTag` de cada chip de etiqueta. Se sufija con el índice dentro del grupo. */
const val ETIQUETA_TAG: String = "visitas_etiqueta_"

/** `testTag` de cada chip genérico (fecha, hora, venta). Se sufija con su clave. */
const val CHIP_TAG: String = "visitas_chip_"

/** `testTag` del CTA de guardar. */
const val GUARDAR_TAG: String = "visitas_guardar"

/** `testTag` de la razón que explica el CTA apagado. */
const val RAZON_TAG: String = "visitas_razon"

/** `testTag` del campo de nota. */
const val NOTA_TAG: String = "visitas_nota"

/** `testTag` del campo de monto prometido. */
const val MONTO_TAG: String = "visitas_monto"

/** `testTag` de la banda que muestra la recomendación. */
const val RECOMENDACION_TAG: String = "visitas_recomendacion"

/**
 * Alto mínimo tocable. El plan pide >=50px; el design system ya pide 56dp, y
 * ese es el que manda — un piso más alto nunca viola el más bajo.
 */
internal val TOQUE = 56.dp

/**
 * La fila de navegación: "atrás" y, a la derecha, lo que [alFinal] ponga.
 *
 * [alFinal] existe para el afordante de la foto (Task 23) y ocupa el hueco que
 * ya había: la fila terminaba en un `Box(weight(1f))` vacío. **Coste vertical
 * cero** — la fila ya medía [TOQUE] de alto y el botón mide lo mismo, así que
 * nada de lo de abajo se mueve. Es la salida que la Task 22 tuvo que encontrar
 * cuando su sección quedó bajo la línea de flotación, aplicada antes de que el
 * problema exista en vez de después.
 */
@Composable
fun BarraDeVisita(
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    alFinal: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onAtras,
            modifier = Modifier
                .size(TOQUE)
                .testTag(ATRAS_TAG),
            shape = MspTheme.shapes.chip,
            color = MspTheme.colors.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "atrás",
                    tint = MspTheme.colors.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        alFinal()
    }
}

/** El `.ctx` del mock: avatar, nombre, cuentas y dirección, saldo total a la derecha. */
@Composable
fun TiraDelCliente(contexto: ContextoDeVisita, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MspTheme.shapes.tile,
        color = MspTheme.colors.surface
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MspTheme.colors.statusOverdueTint, MspTheme.shapes.payIcon),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contexto.iniciales,
                    style = MspTheme.type.captionStrong,
                    color = MspTheme.colors.statusOverdue
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contexto.nombre,
                    style = MspTheme.type.listTitle,
                    color = MspTheme.colors.onSurface
                )
                Text(
                    text = contexto.resumen,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoneyMxn(contexto.saldoTotal.amount),
                    style = MspTheme.type.amountInline,
                    color = MspTheme.colors.onSurface
                )
                Text(
                    text = "saldo total",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/**
 * La banda que muestra **lo que el sistema sugirió**.
 *
 * Se pinta solo cuando hay recomendación. No es decoración: al guardar, esta
 * misma recomendación queda atada a la visita, y sin haberla mostrado el par
 * "qué sugirió / qué hizo" no significaría nada.
 */
@Composable
fun BandaDeRecomendacion(texto: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(RECOMENDACION_TAG),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.brandTint
    ) {
        Text(
            text = texto,
            style = MspTheme.type.caption,
            color = MspTheme.colors.brand,
            modifier = Modifier.padding(MspTheme.spacing.sm)
        )
    }
}

/**
 * Un renglón de desenlace (`.opt` del mock).
 *
 * El anillo de selección toma el color **del propio desenlace** (§3 de la tabla
 * de paleta), no azul: el azul `brand` es del CTA y de la selección genérica.
 */
@Composable
fun OpcionDeResultado(
    resultado: ResultadoDeVisita,
    seleccionado: Boolean,
    habilitado: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = resultadoVisualDe(resultado)
    Surface(
        onClick = onElegir,
        enabled = habilitado,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE)
            .testTag(OPCION_TAG + resultado.name.lowercase()),
        shape = MspTheme.shapes.tile,
        color = MspTheme.colors.surface,
        border = if (seleccionado) BorderStroke(2.dp, visual.contenido) else null
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            GlifoDelResultado(visual)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resultado.titulo,
                    style = MspTheme.type.bodyStrong,
                    color = colorDeTexto(habilitado)
                )
                Text(
                    text = resultado.detalle,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            Text(
                text = resultado.etiquetaDeAlcance,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}

@Composable
private fun GlifoDelResultado(visual: ResultadoVisual) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(visual.fondo, MspTheme.shapes.payIcon),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icono,
            contentDescription = null,
            tint = visual.contenido,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * El aviso de alcance del mock ("aplica a sus 2 ventas"). Solo aparece cuando el
 * desenlace es del cliente: es la regla del catálogo hecha visible en vez de
 * implícita.
 */
@Composable
fun AvisoDeAlcance(cuantas: Int, modifier: Modifier = Modifier) {
    val cuentas = if (cuantas == 1) "su cuenta" else "sus $cuantas ventas"
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.surface2
    ) {
        Text(
            text = "aplica a $cuentas",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(MspTheme.spacing.sm)
        )
    }
}

/** El encabezado de una sección del fold (`.fk` del mock). */
@Composable
fun RotuloDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MspTheme.type.sectionLabel,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(top = MspTheme.spacing.sm)
    )
}

/**
 * Un chip de opción. [activo] lo pinta con el color de su grupo; apagado, se ve
 * apagado — un control que se ve vivo y no responde es una mentira.
 */
@Composable
fun ChipDeOpcion(
    texto: String,
    activo: Boolean,
    habilitado: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier,
    contenidoActivo: Color = MspTheme.colors.onBrand,
    fondoActivo: Color = MspTheme.colors.brand
) {
    Surface(
        onClick = onElegir,
        enabled = habilitado,
        modifier = modifier
            .heightIn(min = TOQUE)
            .widthIn(min = TOQUE),
        shape = MspTheme.shapes.chip,
        color = if (activo) fondoActivo else MspTheme.colors.surface2,
        // El anillo del `.ch.on` del mock. No es adorno: los chips de etiqueta
        // usan el MISMO fondo activo que inactivo (estilo `.ch.onm`, gris sobre
        // gris), así que sin el borde "elegido" y "no elegido" se verían igual —
        // y el color solo nunca puede ser el portador del significado.
        border = if (activo) BorderStroke(1.5.dp, contenidoActivo) else null
    ) {
        Box(
            modifier = Modifier.padding(horizontal = MspTheme.spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = texto,
                style = MspTheme.type.chipLabel,
                color = when {
                    activo -> contenidoActivo
                    habilitado -> MspTheme.colors.onSurface
                    else -> MspTheme.colors.onSurfaceMuted
                },
                maxLines = 2
            )
        }
    }
}

/** La tarjeta que envuelve un fold del mock (`.fold`). */
@Composable
fun TarjetaDelFold(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MspTheme.shapes.tile,
        color = MspTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            contenido()
        }
    }
}

/** El renglón de una venta como destino de la promesa (`.ch.full` del mock). */
@Composable
fun ChipDeVenta(
    venta: VentaParaVisitar,
    activo: Boolean,
    habilitado: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier
) {
    ChipDeOpcion(
        texto = "${venta.folio} · ${formatMoneyMxn(venta.saldo.amount)}",
        activo = activo,
        habilitado = habilitado,
        onElegir = onElegir,
        modifier = modifier
            .fillMaxWidth()
            .testTag(CHIP_TAG + "venta_${venta.ventaId}"),
        contenidoActivo = MspTheme.colors.statusOverdue,
        fondoActivo = MspTheme.colors.statusOverdueTint
    )
}

/**
 * El campo del monto prometido (`.amt` del mock). Pesos enteros: el cobrador
 * captura "220", no "220.00".
 *
 * Vacío significa **sin monto**, que es un caso real de campo ("dijo cuándo pero
 * no cuánto") y distinto de cero.
 */
@Composable
fun CampoDeMonto(
    digitos: String,
    habilitado: Boolean,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.surface2
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "cuánto prometió",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            if (digitos.isNotEmpty()) {
                Text(
                    text = "$",
                    style = MspTheme.type.amountCard,
                    color = MspTheme.colors.statusOverdue
                )
            }
            BasicTextField(
                value = digitos,
                onValueChange = { onCambio(it.filter(Char::isDigit)) },
                enabled = habilitado,
                singleLine = true,
                textStyle = LocalTextStyle.current
                    .merge(MspTheme.type.amountCard)
                    .merge(TextStyle(color = MspTheme.colors.statusOverdue)),
                cursorBrush = SolidColor(MspTheme.colors.statusOverdue),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .widthIn(min = 80.dp)
                    .testTag(MONTO_TAG),
                decorationBox = { campo ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (digitos.isEmpty()) {
                            Text(
                                text = "sin monto",
                                style = MspTheme.type.caption,
                                color = MspTheme.colors.onSurfaceMuted
                            )
                        }
                        campo()
                    }
                }
            )
        }
    }
}

/** El campo de nota libre (`.note` del mock). Nunca lleva fecha ni hora dentro. */
@Composable
fun CampoDeNota(
    nota: String,
    habilitado: Boolean,
    onCambio: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.surface2
    ) {
        Column(modifier = Modifier.padding(MspTheme.spacing.sm)) {
            Text(
                text = "nota opcional",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            BasicTextField(
                value = nota,
                onValueChange = onCambio,
                enabled = habilitado,
                textStyle = LocalTextStyle.current
                    .merge(MspTheme.type.body)
                    .merge(TextStyle(color = MspTheme.colors.onSurface)),
                cursorBrush = SolidColor(MspTheme.colors.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NOTA_TAG)
            )
        }
    }
}

/**
 * El dock: el CTA de guardar y, debajo, la razón por la que está apagado.
 *
 * **Apagado también quiere decir apagado visualmente** (`.btn.off` del mock) y
 * sin `onClick`: un botón vivo que no hace nada es la mentira que la Task 18
 * tuvo que arreglar dos veces.
 */
@Composable
fun DockDeLaVisita(
    texto: String,
    habilitado: Boolean,
    razon: String?,
    onGuardar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MspTheme.colors.outline)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MspTheme.colors.background)
                .padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Surface(
                onClick = onGuardar,
                enabled = habilitado,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TOQUE)
                    .testTag(GUARDAR_TAG),
                shape = MspTheme.shapes.button,
                color = if (habilitado) MspTheme.colors.brand else MspTheme.colors.surface2
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = texto,
                        style = MspTheme.type.buttonLarge,
                        color = if (habilitado) {
                            MspTheme.colors.onBrand
                        } else {
                            MspTheme.colors.onSurfaceMuted
                        }
                    )
                }
            }
            if (razon != null) {
                Text(
                    text = razon,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.testTag(RAZON_TAG)
                )
            }
        }
    }
}

/** La banda de un fallo del guardado, con su reintento. */
@Composable
fun BandaDeFallo(mensaje: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MspTheme.colors.statusOverdue, MspTheme.shapes.control),
        shape = MspTheme.shapes.control,
        color = MspTheme.colors.statusOverdueTint
    ) {
        Text(
            text = mensaje,
            style = MspTheme.type.caption,
            color = MspTheme.colors.statusOverdue,
            modifier = Modifier.padding(MspTheme.spacing.sm)
        )
    }
}

@Composable
private fun colorDeTexto(habilitado: Boolean): Color =
    if (habilitado) MspTheme.colors.onSurface else MspTheme.colors.onSurfaceMuted
