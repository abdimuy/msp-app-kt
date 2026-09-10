@file:Suppress(
    "TooManyFunctions"
) // una pieza por banda del mock; juntarlas no las haria mas legibles.

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspColors
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.ui.MontoCapturado

/** `testTag` de la tarjeta del monto que se está capturando. */
const val CAPTURA_TAG: String = "pagos_abono_captura"

/** `testTag` de la banda roja del bloqueo duro por sobrepago. */
const val BLOQUEO_TAG: String = "pagos_abono_bloqueo"

/** Prefijo del `testTag` de cada chip sugerido; se completa con el nombre del sugerido. */
const val CHIP_SUGERIDO_TAG: String = "pagos_abono_sugerido_"

/** Prefijo del `testTag` de cada pastilla de método de cobro. */
const val METODO_TAG: String = "pagos_abono_metodo_"

/** Prefijo del `testTag` de cada tecla del teclado. */
const val TECLA_TAG: String = "pagos_abono_tecla_"

/** Etiqueta interna de la tecla de punto decimal. */
const val TECLA_PUNTO: String = "punto"

/** Etiqueta interna de la tecla de borrado. */
const val TECLA_BORRAR: String = "borrar"

/**
 * Alto mínimo tocable. El plan pide >=50px; el token del design system (56dp)
 * va por encima y es el que se usa. La Task 16 shipeó un control de 49.5dp y la
 * Task 17 rechazó otro de ~38dp: aquí se mide en `AbonoSeVeYSeTocaTest`, no se
 * declara.
 */
private val TOQUE = 56.dp

/**
 * Ancho mínimo de un chip sugerido. Sale del `SuggestionChips` de kollect
 * (`feature/abono/.../AbonoSuggestions.kt`, `CHIP_MIN_WIDTH = 84.dp`), que es
 * la misma pieza de la misma pantalla allá. Evita que el chip más corto
 * ("LIQUIDAR") quede apretado ahora que los tres se miden por su contenido.
 */
private val ANCHO_MINIMO_DEL_CHIP = 84.dp

/**
 * El encabezado (`.head` del mock): "abono" y debajo el cliente, **con el
 * botón de atrás en la misma fila**.
 *
 * En las pantallas de detalle el atrás vive en su propia barra
 * ([BarraDeDetalle]). Aquí no cabe: medido en el golden, una fila extra de
 * 56dp empujaba la última hilera del teclado —el `0`, el punto y el borrar—
 * fuera de la pantalla, y un teclado al que hay que hacerle scroll para
 * teclear un cero no es un teclado.
 */
@Composable
fun EncabezadoDelAbono(cliente: String, onAtras: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
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
        Column {
            Text(
                text = "abono",
                style = MspTheme.type.screenTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = cliente,
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * La tira de contexto del mock (`.ctx`): venta, producto y saldo en una línea.
 *
 * El saldo va aquí y en negritas porque es **el techo del bloqueo duro**: el
 * cobrador tiene que poder ver contra qué se está topando sin abrir otra
 * pantalla.
 */
@Composable
fun TiraDeContexto(folio: String, producto: String, saldo: Money, modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    val fuerte = SpanStyle(fontWeight = FontWeight.ExtraBold, color = colors.onSurface)
    Tarjeta(modifier = modifier) {
        Text(
            text = buildAnnotatedString {
                append("venta ")
                withStyle(fuerte) { append(folio) }
                append(" · $producto · saldo ")
                withStyle(fuerte) { append(formatMoneyMxn(saldo.amount)) }
            },
            style = MspTheme.type.contextNote,
            color = colors.onSurfaceMuted
        )
    }
}

/**
 * La tarjeta de captura del mock (`.capa`): el monto es el protagonista, con su
 * método debajo.
 *
 * En [conError] toma el tratamiento rojo del mock (`.capa.err`): borde y cifra
 * en `statusOverdue`. **El color lo decide el veredicto**, no un cálculo local
 * — la pantalla no vuelve a preguntarse si el monto excede el saldo.
 *
 * El verde por defecto es `statusPaid` por la tabla del Task 2 §3(b): el monto
 * en captura es el estado "captura válida", análogo a pagado, no una acción.
 */
@Composable
fun TarjetaDeCaptura(
    monto: MontoCapturado,
    metodo: MetodoDeCobro,
    conError: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val cifra = if (conError) colors.statusOverdue else colors.statusPaid
    val marco = if (conError) {
        Modifier.border(1.5.dp, colors.statusOverdue, MspTheme.shapes.card)
    } else {
        Modifier
    }
    Tarjeta(modifier = modifier.then(marco).testTag(CAPTURA_TAG)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "monto recibido".uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.overline,
                color = MspTheme.colors.onSurfaceMuted
            )
            Text(
                text = monto.enPantalla(),
                style = MspTheme.type.amountHero,
                color = cifra,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
            Text(
                text = metodo.etiqueta,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
        }
    }
}

/**
 * La banda del bloqueo duro (`.blockerr`). Se pinta solo cuando hay un monto
 * positivo Y el veredicto lo prohíbe: con el teclado en blanco el CTA apagado ya
 * lo dice todo y una banda roja sería regaño gratuito.
 *
 * Dice el **máximo registrable**, que es el saldo. No la liquidación: liquidar
 * cierra la venta pero pagar el saldo completo también, y nombrar la
 * liquidación como techo dejaría fuera montos que sí se pueden registrar.
 */
@Composable
fun BandaDeBloqueo(mensaje: String, modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.statusOverdueTint, MspTheme.shapes.control)
            .border(1.dp, colors.statusOverdue, MspTheme.shapes.control)
            .padding(horizontal = 13.dp, vertical = 11.dp)
            .testTag(BLOQUEO_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.statusOverdue,
            modifier = Modifier.size(18.dp)
        )
        Text(text = mensaje, style = MspTheme.type.contextNote, color = colors.statusOverdue)
    }
}

/**
 * Los tres montos sugeridos (`.sugs`), **cada uno con su color propio** de la
 * tabla del Task 2 §5: verde `statusPaid` lo esperado hoy, turquesa
 * `statusTeal` ponerse al corriente, violeta `promise` liquidar.
 *
 * Ninguno puede exceder el saldo — lo garantiza
 * [com.example.msp_app.feature.pagos.domain.MontosSugeridos], no esta fila.
 *
 * **Los tres chips se miden por su contenido, no en tercios iguales
 * (corrección de la ronda 1).** En tercios de 360dp cada chip tiene 81dp de
 * texto y "AL CORRIENTE" necesita ~88dp, así que a escala normal el rótulo se
 * partía en dos renglones (`AL` / `CORRIENTE`) y la fila quedaba con dos chips
 * altos y uno bajo. No es un problema de tamaño de letra: en tercios iguales
 * **ningún** reparto alcanza, porque dos de los tres rótulos tienen 12
 * caracteres y el tercero ("LIQUIDAR") ocho — sobra ancho justo donde no hace
 * falta. Medidos por contenido los tres caben en un renglón y la fila queda
 * pareja.
 *
 * Es además lo que hace kollect en **esta misma pantalla**: su
 * `SuggestionChips` (`feature/abono/.../AbonoSuggestions.kt`) es un `Row` con
 * `horizontalScroll` de chips medidos por contenido con
 * `defaultMinSize(minWidth = 84.dp)`, y su rótulo es `type.eyebrow` en
 * mayúsculas — el mismo rol que usamos. El `horizontalScroll` no se ve a 360dp
 * (los tres chips entran), y es lo que impide que en una pantalla más angosta
 * el tercer chip se quede sin ancho: un `Row` sin peso le da 0dp al último
 * hijo cuando los primeros se comieron el espacio, y un chip de dinero
 * invisible es peor que uno al que hay que arrastrar.
 *
 * A `GRANDE`/`MUY_GRANDE` no cambia nada: ahí siguen apilados a lo ancho
 * ([EnFilaOApiladas]).
 */
@Composable
fun ChipsSugeridos(
    sugeridos: List<MontosSugeridos.Sugerido>,
    onSugerido: (Money) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sugeridos.isEmpty()) return
    EnFilaOApiladas(modifier = modifier, porContenido = true) { anchoDeCadaUno ->
        sugeridos.forEach { sugerido ->
            ChipSugerido(
                sugerido = sugerido,
                onClick = { onSugerido(sugerido.importe) },
                modifier = anchoDeCadaUno
            )
        }
    }
}

/**
 * Tres controles en fila cuando caben, apilados cuando no — mismo criterio (y
 * misma razón) que [TresDatos].
 *
 * A `MUY_GRANDE` (2.0) los tres chips en tercios de 360dp dejaban de caber y el
 * monto se truncaba: "liquidar $1,290" se leía **"$1,29"**. Un monto recortado
 * no es un detalle visual, es un bug de dinero —lo dice el KDoc de
 * `MspMoneyText`— y le pasa justo al usuario que más ayuda necesita. Se lee
 * [LocalFontSizeLevel], la preferencia elegida en la app, no el `fontScale` del
 * sistema.
 *
 * [porContenido] cambia solo la rama en fila: en vez de tercios iguales
 * (`weight(1f)`) los hijos se miden por su contenido dentro de un `Row` con
 * `horizontalScroll`, que es lo que necesitan los chips sugeridos y **no** las
 * pastillas de método —esas sí quieren tercios iguales, y su rótulo
 * ("efectivo", "transferencia") nunca se partió—. El porqué completo está en
 * el KDoc de [ChipsSugeridos].
 */
@Composable
private fun EnFilaOApiladas(
    modifier: Modifier = Modifier,
    porContenido: Boolean = false,
    contenido: @Composable (Modifier) -> Unit
) {
    if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) {
        Row(
            modifier = if (porContenido) {
                modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            } else {
                modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            contenido(
                if (porContenido) {
                    Modifier.widthIn(min = ANCHO_MINIMO_DEL_CHIP)
                } else {
                    Modifier.weight(1f)
                }
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            contenido(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChipSugerido(
    sugerido: MontosSugeridos.Sugerido,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val contenido = contenidoDelSugerido(sugerido.cual, colors)
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = TOQUE)
            .testTag(CHIP_SUGERIDO_TAG + sugerido.cual.name.lowercase()),
        shape = MspTheme.shapes.field,
        color = fondoDelSugerido(sugerido.cual, colors),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, contenido)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = MspTheme.spacing.sm),
            verticalArrangement = Arrangement.Center
        ) {
            // SIN `maxLines`: un monto reflowea antes que perder dígitos
            // (misma regla que `MspMoneyText`).
            Text(
                text = formatMoneyMxn(sugerido.importe.amount),
                style = MspTheme.type.amountRow,
                color = contenido
            )
            // Dos líneas: "esperado hoy" no cabe en una a 360dp y se cortaba
            // sin puntos suspensivos — el chip decía "esperado" y el cobrador
            // no podía saber de qué.
            Text(
                // `.sug .sk` del mock: `9px/800`, `.05em`, `uppercase`.
                text = sugerido.cual.etiqueta.uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.eyebrow,
                color = contenido
            )
        }
    }
}

/** Color de contenido de cada sugerido — tabla del Task 2 §5, no la esmeralda del mock. */
fun contenidoDelSugerido(cual: MontosSugeridos.Sugerencia, colors: MspColors): Color = when (cual) {
    MontosSugeridos.Sugerencia.ESPERADO_HOY -> colors.statusPaid
    MontosSugeridos.Sugerencia.AL_CORRIENTE -> colors.statusTeal
    MontosSugeridos.Sugerencia.LIQUIDAR -> colors.promise
}

/** Fondo (tint) de cada sugerido. */
fun fondoDelSugerido(cual: MontosSugeridos.Sugerencia, colors: MspColors): Color = when (cual) {
    MontosSugeridos.Sugerencia.ESPERADO_HOY -> colors.statusPaidTint
    MontosSugeridos.Sugerencia.AL_CORRIENTE -> colors.statusTealTint
    MontosSugeridos.Sugerencia.LIQUIDAR -> colors.promiseTint
}

/**
 * Las pastillas de método (`.meths`): **efectivo y transferencia. Sin
 * terminal** — es lo que este negocio cobra.
 *
 * La selección va en `brand`/`brandTint`: es una selección genérica sin estado
 * de cobro asociado, y el verde nunca es acción (regla dura del plan y del
 * Task 2 §3(a), que nombra este caso explícitamente).
 */
@Composable
fun SelectorDeMetodo(
    seleccionado: MetodoDeCobro,
    comprobantes: Int,
    puedeAgregarFoto: Boolean,
    onMetodo: (MetodoDeCobro) -> Unit,
    onAgregarFoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    EnFilaOApiladas(modifier = modifier) { anchoDeCadaUno ->
        METODOS_DE_CAPTURA.forEach { metodo ->
            PastillaDeMetodo(
                metodo = metodo,
                activa = metodo == seleccionado,
                onClick = { onMetodo(metodo) },
                modifier = anchoDeCadaUno
            )
        }
        // La cámara viaja con esta fila (Ruling AQ): cuesta cero dp verticales
        // a escala normal, que es la única donde el teclado cabe en pantalla.
        // El razonamiento completo está en el KDoc de [BotonDeFotoEnLinea].
        BotonDeFotoEnLinea(
            cuantos = comprobantes,
            habilitado = puedeAgregarFoto,
            onAgregar = onAgregarFoto
        )
    }
}

/**
 * Los dos métodos que se pueden capturar. `MetodoDeCobro.CHEQUE` existe en el
 * histórico (el riel lo pinta si aparece) pero no se ofrece en captura: nadie
 * cobra cheques en la puerta.
 */
val METODOS_DE_CAPTURA: List<MetodoDeCobro> =
    listOf(MetodoDeCobro.EFECTIVO, MetodoDeCobro.TRANSFERENCIA)

@Composable
private fun PastillaDeMetodo(
    metodo: MetodoDeCobro,
    activa: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val contenido = if (activa) colors.brand else colors.onSurfaceMuted
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = TOQUE)
            .testTag(METODO_TAG + metodo.name.lowercase()),
        shape = MspTheme.shapes.control,
        color = if (activa) colors.brandTint else colors.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (activa) colors.brand else colors.outline
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = metodo.etiqueta, style = MspTheme.type.methodLabel, color = contenido)
        }
    }
}

/** El teclado del mock (`.pad`): 1-9, punto, 0 y borrar. */
@Composable
fun TecladoDeMontos(
    onDigito: (Int) -> Unit,
    onPunto: () -> Unit,
    onBorrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        listOf(1..3, 4..6, 7..9).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
                fila.forEach { digito ->
                    Tecla(
                        etiqueta = digito.toString(),
                        tag = digito.toString(),
                        onClick = { onDigito(digito) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
            Tecla(
                etiqueta = ".",
                tag = TECLA_PUNTO,
                onClick = onPunto,
                modifier = Modifier.weight(1f),
                apagada = true
            )
            Tecla(
                etiqueta = "0",
                tag = "0",
                onClick = { onDigito(0) },
                modifier = Modifier.weight(1f)
            )
            TeclaDeBorrado(onClick = onBorrar, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Una tecla del teclado.
 *
 * Va en [MspCard] y no en un `Surface` pelado porque el `NumericKeypad` de
 * kollect monta cada tecla en `CampoSurface` —su KDoc lo nombra: "hairline
 * borders, 22sp tabular digits, muted decimal/backspace keys"— y sin el
 * hairline el teclado se lee como doce huecos en vez de doce teclas.
 */
@Composable
private fun Tecla(
    etiqueta: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    apagada: Boolean = false
) {
    MspCard(
        modifier = modifier
            .heightIn(min = TOQUE)
            .testTag(TECLA_TAG + tag),
        shape = MspTheme.shapes.control,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = etiqueta,
                style = MspTheme.type.keypadKey,
                color = if (apagada) MspTheme.colors.onSurfaceMuted else MspTheme.colors.onSurface
            )
        }
    }
}

@Composable
private fun TeclaDeBorrado(onClick: () -> Unit, modifier: Modifier = Modifier) {
    MspCard(
        modifier = modifier
            .heightIn(min = TOQUE)
            .testTag(TECLA_TAG + TECLA_BORRAR),
        shape = MspTheme.shapes.control,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "borrar",
                tint = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** El "✓ registrado" del final: el abono ya está escrito y no hay nada que tocar. */
@Composable
fun BandaDeRegistrado(modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.statusPaidTint, MspTheme.shapes.control)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = colors.statusPaid,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "abono registrado",
            style = MspTheme.type.bodyStrong,
            color = colors.statusPaid
        )
    }
}
