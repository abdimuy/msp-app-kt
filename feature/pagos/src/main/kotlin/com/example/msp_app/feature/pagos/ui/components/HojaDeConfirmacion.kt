@file:Suppress("TooManyFunctions") // una pieza por banda de la hoja del mock (paneles 3 y 4).

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.RarezaDelAbono
import com.example.msp_app.feature.pagos.domain.VeredictoDelAbono
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro

/** `testTag` del velo que tapa la captura mientras la hoja está arriba. */
const val VELO_TAG: String = "pagos_abono_velo"

/** `testTag` de la hoja de confirmación (el paso dos). */
const val HOJA_TAG: String = "pagos_abono_hoja"

/** `testTag` del botón que efectivamente registra — el único que mueve dinero. */
const val CONFIRMAR_TAG: String = "pagos_abono_confirmar"

/** `testTag` del botón que sale del paso dos sin registrar. */
const val EDITAR_TAG: String = "pagos_abono_editar"

/** `testTag` de la alerta roja de monto raro. */
const val ALERTA_RARO_TAG: String = "pagos_abono_alerta_raro"

/** `testTag` de la banda ámbar del posible duplicado de la semana. */
const val DUPLICADO_TAG: String = "pagos_abono_duplicado"

/** `testTag` del indicador de dos pasos. */
const val DOS_PASOS_TAG: String = "pagos_abono_dos_pasos"

/** `testTag` del saldo nuevo — la consecuencia que el cobrador ve antes de registrar. */
const val SALDO_NUEVO_TAG: String = "pagos_abono_saldo_nuevo"

private val AVATAR = 40.dp

private val PASO = 18.dp

/**
 * El **paso dos** de la confirmación (mock, paneles 3 y 4).
 *
 * Se dibuja DENTRO de la composición —velo + hoja abajo— y no en un
 * `ModalBottomSheet`: así la confirmación vive en el mismo árbol que la captura
 * (una rotación la recompone en vez de perderla en otra ventana) y los goldens
 * capturan la pantalla completa, con velo y todo, como el mock la enseña.
 *
 * El velo **consume el toque**: mientras la hoja está arriba no se puede
 * alcanzar el CTA de abajo, que es la mitad de "ninguna ruta guarda dos veces".
 * Tocarlo equivale a "editar": sale del paso dos sin registrar nada.
 *
 * Cuando el veredicto trae rarezas, la hoja escala: banda roja arriba, cifra en
 * rojo, y el botón de registrar cambia a `Danger` con un texto que **obliga a
 * afirmar el monto** en vez de solo continuar.
 *
 * **Hueco para la Task 22:** la evidencia (foto) entra entre [CifraDeLaHoja] y
 * el flujo de saldos, sin mover ninguna de las tres piezas de seguridad.
 */
@Composable
fun HojaDeConfirmacion(
    cliente: String,
    producto: String,
    folio: String,
    importe: Money,
    metodo: MetodoDeCobro,
    veredicto: VeredictoDelAbono,
    esperadoHoy: Money,
    onConfirmar: () -> Unit,
    onEditar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val raro = veredicto.esRaro
    Column(modifier = modifier.fillMaxSize()) {
        // EL VELO ES HERMANO DE LA HOJA, NO SU PADRE — y esa es la razón de
        // que los botones de abajo se puedan tocar.
        //
        // Con el velo envolviendo la hoja, su `detectTapGestures` se quedaba
        // con el toque destinado al botón. Medido, no supuesto: al tocar
        // "confirmar y registrar" el contador de registros quedaba en 0 y el de
        // "editar" (la acción del velo) subía a 1. Un gesto de padre le gana a
        // los `clickable` de sus descendientes.
        //
        // Por eso la zona que cierra el paso dos es solo la franja oscurecida
        // de arriba: no envuelve a nada que tenga que responder.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.background.copy(alpha = VELO_ALFA))
                .pointerInput(Unit) { detectTapGestures { onEditar() } }
                .testTag(VELO_TAG)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // `background(color, shape)` en vez de `clip(shape) + background`.
                // La hoja no desborda, así que el recorte no hace falta — y con
                // ESTA forma sí estorba: medido sobre la estructura ya corregida
                // (velo hermano), un `clip` de
                // `RoundedCornerShape(topStart, topEnd)` deja el toque del botón
                // en 0, y el mismo `clip` con una forma UNIFORME
                // (`RoundedCornerShape(24.dp)`) lo deja en 1.
                //
                // El mecanismo NO es el tipo de `Outline`: `RoundedCornerShape`
                // nunca produce `Outline.Generic`, solo `Rectangle` o `Rounded`.
                // El que decide vive DENTRO de `Outline.Rounded`: `isInRoundedRect`
                // corre `cornersFit`, y cuando las esquinas no encajan cae a
                // `isInPath` -> `Path.op`, que es la parte frágil sin gráficos
                // nativos. Esquinas uniformes -> contención analítica, el toque
                // vive; `topStart`/`topEnd` -> `Path.op`, el toque muere bajo
                // Robolectric.
                //
                // No es el patrón `clip + clickable` de `MspPrimaryFieldButton`:
                // ahí el `clip` va en el MISMO nodo que el `clickable` y la forma
                // es uniforme (16.dp).
                //
                // Alcance, con honestidad: que `MspShapes` sea todo uniforme no
                // basta como garantía, porque las formas declaradas en un archivo
                // no pasan por ahí. Hay una: `ReportSheets.kt:30`
                // (`RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`), que va
                // como `shape` de un `ModalBottomSheet` — y es inofensiva porque
                // esa hoja **no tiene descendientes clickables**, no porque la
                // forma sea segura. Eso es lo que hay que comprobar si aparece
                // otra.
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                // Un `pointerInput` INERTE: no consume nada —así los botones de
                // adentro siguen respondiendo— pero hace que la hoja sea
                // alcanzable por el hit-test, y con eso ningún toque se cuela a
                // la captura que quedó debajo.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
                // El tag va ANTES del padding: así los bordes del nodo son los de
                // la hoja completa y el test puede afirmar que no queda rendija.
                .testTag(HOJA_TAG)
                .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Agarradera()
            if (raro) {
                BandasDeRareza(
                    rarezas = veredicto.rarezas,
                    importe = importe,
                    esperadoHoy = esperadoHoy
                )
            } else {
                Text(
                    text = "confirmar abono",
                    style = MspTheme.type.cardTitle,
                    color = colors.onSurface
                )
            }
            QuienEs(cliente = cliente, producto = producto, folio = folio, raro = raro)
            CifraDeLaHoja(importe = importe, metodo = metodo, raro = raro)
            FlujoDeSaldos(veredicto = veredicto, raro = raro)
            if (raro) {
                MspPrimaryFieldButton(
                    text = AFIRMAR_EL_MONTO,
                    onClick = onConfirmar,
                    variant = PrimaryFieldButtonVariant.Danger,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CONFIRMAR_TAG)
                )
                MspPrimaryFieldButton(
                    text = "corregir monto",
                    onClick = onEditar,
                    variant = PrimaryFieldButtonVariant.Ghost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(EDITAR_TAG)
                )
            } else {
                MspPrimaryFieldButton(
                    text = "confirmar y registrar",
                    onClick = onConfirmar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(CONFIRMAR_TAG)
                )
                MspPrimaryFieldButton(
                    text = "editar",
                    onClick = onEditar,
                    variant = PrimaryFieldButtonVariant.Ghost,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(EDITAR_TAG)
                )
            }
            DosPasos(segundo = if (raro) "confirmar monto raro" else "confirmar")
        }
    }
}

/**
 * El texto del botón rojo. **Es la medida de seguridad, no una etiqueta**: pide
 * afirmar el monto, no solo continuar, y por eso se conserva completo en vez de
 * recortarse a las 2-4 palabras que pide la regla de copy del plan. Un "sí"
 * ambiguo delante de un monto raro no obliga a nada.
 */
const val AFIRMAR_EL_MONTO: String = "sí, el monto es correcto"

private const val VELO_ALFA = 0.72f

@Composable
private fun Agarradera() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MspTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MspTheme.colors.outline)
        )
    }
}

/** La fila de identidad (`.who`): iniciales, nombre y producto · venta. */
@Composable
private fun QuienEs(cliente: String, producto: String, folio: String, raro: Boolean) {
    val colors = MspTheme.colors
    // El avatar SÍ lleva estado (tabla del Task 2 §3(b)): verde en la captura
    // sana, rojo cuando el monto está marcado.
    val contenido = if (raro) colors.statusOverdue else colors.statusPaid
    val fondo = if (raro) colors.statusOverdueTint else colors.statusPaidTint
    Row(
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AVATAR)
                .clip(MspTheme.shapes.control)
                .background(fondo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = inicialesDe(cliente),
                style = MspTheme.type.captionStrong,
                color = contenido
            )
        }
        Column {
            Text(text = cliente, style = MspTheme.type.name, color = colors.onSurface, maxLines = 1)
            Text(
                text = "$producto · $folio",
                style = MspTheme.type.caption,
                color = colors.onSurfaceMuted,
                maxLines = 1
            )
        }
    }
}

/** La cifra protagonista de la hoja (`.sbig`), roja cuando el monto está marcado. */
@Composable
private fun CifraDeLaHoja(importe: Money, metodo: MetodoDeCobro, raro: Boolean) {
    val colors = MspTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatMoneyMxn(importe.amount),
            style = MspTheme.type.amountHero,
            color = if (raro) colors.statusOverdue else colors.statusPaid,
            textAlign = TextAlign.Center
        )
        Text(
            text = metodo.etiqueta,
            style = MspTheme.type.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = MspTheme.spacing.xs)
        )
    }
}

/**
 * **Saldo anterior → saldo nuevo** (`.flowb`): la consecuencia, enseñada antes
 * de tocar dinero. Es lo que convierte un error de dedo en un error visible, y
 * la razón entera de que la confirmación sea de dos pasos.
 */
@Composable
private fun FlujoDeSaldos(veredicto: VeredictoDelAbono, raro: Boolean) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface2, MspTheme.shapes.field)
            .border(1.dp, colors.outline, MspTheme.shapes.field)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CeldaDeSaldo(
            etiqueta = "saldo anterior",
            importe = veredicto.saldoAnterior,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(text = "→", style = MspTheme.type.body, color = colors.onSurfaceMuted)
        CeldaDeSaldo(
            etiqueta = "saldo nuevo",
            importe = veredicto.saldoNuevo,
            color = if (raro) colors.statusOverdue else colors.statusPaid,
            modifier = Modifier
                .weight(1f)
                .testTag(SALDO_NUEVO_TAG)
        )
    }
}

@Composable
private fun CeldaDeSaldo(
    etiqueta: String,
    importe: Money,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = etiqueta,
            style = MspTheme.type.eyebrow,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1
        )
        Text(
            text = formatMoneyMxn(importe.amount),
            style = MspTheme.type.amountRow,
            color = color,
            maxLines = 1
        )
    }
}

/** Las bandas de rareza: la alerta roja y, si aplica, el posible duplicado ámbar. */
@Composable
private fun BandasDeRareza(rarezas: Set<RarezaDelAbono>, importe: Money, esperadoHoy: Money) {
    val colors = MspTheme.colors
    when {
        RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in rarezas -> AlertaRoja(
            titulo = "monto inusual — verifica",
            detalle = "es mucho mayor al pago esperado, ¿tecleaste un dígito de más?"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)) {
                Text(
                    text = "esperado ${formatMoneyMxn(esperadoHoy.amount)}",
                    style = MspTheme.type.captionStrong,
                    color = colors.statusOverdue
                )
                Text(
                    text = "este abono ${formatMoneyMxn(importe.amount)}",
                    style = MspTheme.type.captionStrong,
                    color = colors.statusOverdue
                )
            }
        }

        RarezaDelAbono.ABAJO_DE_LO_ESPERADO in rarezas -> AlertaRoja(
            titulo = "abono corto — verifica",
            detalle = "es menor al pago esperado, la cuenta se queda debiendo la diferencia"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)) {
                Text(
                    text = "esperado ${formatMoneyMxn(esperadoHoy.amount)}",
                    style = MspTheme.type.captionStrong,
                    color = colors.statusOverdue
                )
                Text(
                    text = "este abono ${formatMoneyMxn(importe.amount)}",
                    style = MspTheme.type.captionStrong,
                    color = colors.statusOverdue
                )
            }
        }

        RarezaDelAbono.NO_TERMINA_EN_CINCUENTA in rarezas -> AlertaRoja(
            titulo = "monto poco común — verifica",
            detalle = "no termina en 00 ni en 50, confirma que es correcto"
        )
    }
    if (RarezaDelAbono.YA_ABONO_ESTE_PERIODO in rarezas) BandaDeDuplicado()
}

@Composable
private fun AlertaRoja(titulo: String, detalle: String, extra: (@Composable () -> Unit)? = null) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.statusOverdueTint, MspTheme.shapes.field)
            .border(1.5.dp, colors.statusOverdue, MspTheme.shapes.field)
            .padding(horizontal = 13.dp, vertical = 12.dp)
            .testTag(ALERTA_RARO_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.statusOverdue,
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
            Text(text = titulo, style = MspTheme.type.bodyStrong, color = colors.statusOverdue)
            Text(text = detalle, style = MspTheme.type.caption, color = colors.statusOverdue)
            extra?.invoke()
        }
    }
}

/**
 * El otro modo de falla real: la venta ya recibió dinero en el periodo abierto.
 * El dato sale del dinero del periodo YA derivado, no de ninguna visita.
 */
@Composable
private fun BandaDeDuplicado() {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.statusPartialTint, MspTheme.shapes.control)
            .border(1.dp, colors.statusPartial, MspTheme.shapes.control)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(DUPLICADO_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = colors.statusPartial,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "ya abonó esta semana",
            style = MspTheme.type.bodyStrong,
            color = colors.statusPartial
        )
    }
}

/** "1 revisado · 2 confirmar" (`.two`): el paso uno ya se dio, falta el dos. */
@Composable
private fun DosPasos(segundo: String) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DOS_PASOS_TAG),
        horizontalArrangement = Arrangement.spacedBy(
            MspTheme.spacing.xs + 2.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Paso(numero = "1", hecho = true)
        Text(text = "revisado", style = MspTheme.type.captionStrong, color = colors.onSurfaceMuted)
        Paso(numero = "2", hecho = false)
        Text(text = segundo, style = MspTheme.type.captionStrong, color = colors.onSurfaceMuted)
    }
}

@Composable
private fun Paso(numero: String, hecho: Boolean) {
    val colors = MspTheme.colors
    val base = if (hecho) {
        Modifier.background(colors.statusPaid, CircleShape)
    } else {
        Modifier.border(1.5.dp, colors.outline, CircleShape)
    }
    Box(modifier = Modifier.size(PASO).then(base), contentAlignment = Alignment.Center) {
        Text(
            text = numero,
            style = MspTheme.type.ringCaption,
            color = if (hecho) colors.statusPaidTint else colors.onSurfaceMuted
        )
    }
}

/** Las iniciales de las dos primeras palabras del nombre. */
fun inicialesDe(nombre: String): String = nombre
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifEmpty { "?" }
