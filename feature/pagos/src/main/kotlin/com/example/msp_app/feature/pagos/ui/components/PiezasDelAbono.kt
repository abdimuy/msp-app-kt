@file:Suppress(
    "TooManyFunctions"
) // una pieza por banda del mock; juntarlas no las haria mas legibles.

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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

/** `testTag` de la tarjeta del monto que se está capturando. */
const val CAPTURA_TAG: String = "pagos_abono_captura"

/** `testTag` de la banda roja del bloqueo duro por sobrepago. */
const val BLOQUEO_TAG: String = "pagos_abono_bloqueo"

/** `testTag` de la banda que dice que la parcialidad de la venta se ve mal. */
const val CUOTA_DUDOSA_TAG: String = "pagos_abono_cuota_dudosa"

/**
 * `testTag` de la banda de aviso **en vivo**, la que sale mientras se teclea.
 * Una sola para los dos niveles que hablan: lo que cambia es el color y el
 * texto, no el lugar.
 */
const val AVISO_TAG: String = "pagos_abono_aviso"

/** `testTag` de la franja fija de la tarjeta de la cifra: un solo mensaje a la vez. */
const val FRANJA_TAG: String = "pagos_abono_franja"

/** `testTag` del "Saldo nuevo $X" que la franja dice cuando no hay nada más que decir. */
const val FRANJA_SALDO_NUEVO_TAG: String = "pagos_abono_franja_saldo_nuevo"

/** `testTag` del nombre del cliente: lo único que queda en la cabecera. */
const val NOMBRE_DEL_CLIENTE_TAG: String = "pagos_abono_cliente"

/** `testTag` de la tarjeta del producto y el saldo (la que abre el desplegable). */
const val PRODUCTO_TAG: String = "pagos_abono_producto"

/** `testTag` del saldo grande de la venta. */
const val SALDO_DE_LA_VENTA_TAG: String = "pagos_abono_saldo"

/** `testTag` del desplegable con el nombre completo del producto. */
const val DESPLEGABLE_DEL_PRODUCTO_TAG: String = "pagos_abono_desplegable"

/** `testTag` del botón "Foto" de la fila del método. */
const val FOTO_TAG: String = "pagos_abono_foto"

/** `testTag` del contador del botón "Foto". */
const val FOTO_CONTADOR_TAG: String = "pagos_abono_foto_contador"

/** `testTag` de la hoja "Fotos y archivos". */
const val HOJA_DE_FOTOS_TAG: String = "pagos_abono_hoja_fotos"

/** `testTag` del velo de la hoja "Fotos y archivos". */
const val VELO_DE_FOTOS_TAG: String = "pagos_abono_velo_fotos"

/** Prefijo del `testTag` de cada chip sugerido; se completa con el nombre del sugerido. */
const val CHIP_SUGERIDO_TAG: String = "pagos_abono_sugerido_"

/** Prefijo del `testTag` de cada pastilla de método de cobro. */
const val METODO_TAG: String = "pagos_abono_metodo_"

/** Prefijo del `testTag` de cada tecla del teclado. */
const val TECLA_TAG: String = "pagos_abono_tecla_"

/**
 * Clave interna de la tecla de punto decimal — nunca se pinta, sólo entra al
 * `testTag` que arma [TECLA_TAG]. Mayúscula inicial por lo mismo que
 * cualquier otra constante de este archivo: nadie la lee, pero
 * `CadaTextoDeUsuarioEmpiezaEnMayusculaTest` mide el literal de la
 * declaración, no si algo la pinta.
 */
const val TECLA_PUNTO: String = "Punto"

/** Clave interna de la tecla de borrado. Ver el KDoc de [TECLA_PUNTO]. */
const val TECLA_BORRAR: String = "Borrar"

/**
 * Alto mínimo tocable. El plan pide >=50px; el token del design system (56dp)
 * va por encima y es el que se usa. La Task 16 shipeó un control de 49.5dp y la
 * Task 17 rechazó otro de ~38dp: aquí se mide en `AbonoSeVeYSeTocaTest`, no se
 * declara.
 */
private val TOQUE: Dp = 56.dp

/** El mismo [TOQUE], para las piezas de la zona de arriba y la pantalla. */
internal val TOQUE_DEL_ABONO: Dp = TOQUE

/**
 * Ancho mínimo de un chip sugerido. Sale del `SuggestionChips` de kollect
 * (`feature/abono/.../AbonoSuggestions.kt`, `CHIP_MIN_WIDTH = 84.dp`), que es
 * la misma pieza de la misma pantalla allá. Evita que el chip más corto
 * ("LIQUIDAR") quede apretado ahora que los tres se miden por su contenido.
 */
private val ANCHO_MINIMO_DEL_CHIP = 84.dp

/** Lo que mide el teclado entero: cuatro filas de [TOQUE] y tres huecos de 8dp. */
internal val ALTO_DEL_TECLADO: Dp = TOQUE * 4 + 8.dp * 3

/**
 * Los montos sugeridos (`.sugs`), **cada uno con su color propio** de la
 * tabla del Task 2 §5: verde `statusPaid` lo esperado hoy, turquesa
 * `statusTeal` ponerse al corriente, violeta `promise` liquidar.
 *
 * Ninguno puede exceder el saldo — lo garantiza
 * [com.example.msp_app.feature.pagos.domain.MontosSugeridos], no esta fila.
 *
 * **Siempre en UNA fila que se desliza de lado, a toda escala** (mock
 * `registrar-abono-fijo.html`, aprobado el 2026-09-29). Antes, a `GRANDE` y
 * `MUY_GRANDE` los chips se apilaban a lo ancho, y tres chips apilados
 * empujaban el teclado fuera de la pantalla: el cobrador entraba y no veía
 * dónde teclear. Ahora la fila mide siempre [TOQUE] de alto.
 *
 * El monto de un chip **nunca se recorta** —un monto recortado es un bug de
 * dinero, lo dice el KDoc de `MspMoneyText`—: cada chip se mide por su
 * contenido (`widthIn(min)`), y lo que no cabe se alcanza deslizando. A letra
 * grande el chip pone la cifra y el rótulo en un solo renglón, y el borde
 * derecho se desvanece para decir que hay más.
 */
@Composable
fun ChipsSugeridos(
    sugeridos: List<MontosSugeridos.Sugerido>,
    onSugerido: (Money) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sugeridos.isEmpty()) return
    val enUnRenglon = LocalFontSizeLevel.current != FontSizeLevel.NORMAL
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOQUE)
            .then(if (enUnRenglon) Modifier.desvanecidoALaDerecha() else Modifier)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        sugeridos.forEach { sugerido ->
            ChipSugerido(
                sugerido = sugerido,
                enUnRenglon = enUnRenglon,
                onClick = { onSugerido(sugerido.importe) },
                modifier = Modifier.widthIn(min = ANCHO_MINIMO_DEL_CHIP)
            )
        }
    }
}

/** Dónde empieza a desvanecerse la fila: el 82 % del mock. */
private const val INICIO_DEL_DESVANECIDO = 0.82f

/** El borde derecho se desvanece: dice que la fila sigue. */
private fun Modifier.desvanecidoALaDerecha(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                INICIO_DEL_DESVANECIDO to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
private fun ChipSugerido(
    sugerido: MontosSugeridos.Sugerido,
    enUnRenglon: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val contenido = contenidoDelSugerido(sugerido.cual, colors)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .testTag(CHIP_SUGERIDO_TAG + sugerido.clave),
        shape = MspTheme.shapes.field,
        color = fondoDelSugerido(sugerido.cual, colors),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, contenido)
    ) {
        val cifra: @Composable () -> Unit = {
            // SIN `maxLines`: un monto reflowea antes que perder dígitos
            // (misma regla que `MspMoneyText`).
            ConTopeDeLetra(TOPE_DE_LAS_CIFRAS) {
                Text(
                    text = formatMoneyMxn(sugerido.importe.amount),
                    style = MspTheme.type.amountRow.copy(
                        lineHeight = MspTheme.type.amountRow.fontSize * 1.15f
                    ),
                    color = contenido
                )
            }
        }
        val rotulo: @Composable () -> Unit = {
            Text(
                // `.sug .sk` del mock: versalitas.
                text = sugerido.cual.etiqueta.uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.eyebrow.copy(
                    lineHeight = MspTheme.type.eyebrow.fontSize * 1.2f
                ),
                color = contenido
            )
        }
        if (enUnRenglon) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cifra()
                rotulo()
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                cifra()
                rotulo()
            }
        }
    }
}

/**
 * Color de contenido de cada sugerido — tabla del Task 2 §5, no la esmeralda del
 * mock.
 *
 * Las dos fuentes nuevas NO reciben color de estado, y es deliberado: "lo de
 * siempre" y los redondos no dicen nada del periodo de esta cuenta —no la ponen
 * al corriente, no la cierran—, así que pintarlos de verde o de turquesa
 * prometería una consecuencia que no tienen. "Lo de siempre" va en `brand`
 * porque es un dato de ESTE cliente, y los redondos en `onSurfaceMuted` porque
 * son la misma cifra para toda la ruta.
 */
fun contenidoDelSugerido(cual: MontosSugeridos.Sugerencia, colors: MspColors): Color = when (cual) {
    // Los dos esperados comparten color: es el MISMO chip, en el mismo lugar y
    // con el mismo importe. Lo único que cambia entre ellos es el rótulo, que es
    // donde se dice de dónde salió la cifra.
    MontosSugeridos.Sugerencia.ESPERADO_HOY,
    MontosSugeridos.Sugerencia.ESPERADO_POR_COSTUMBRE -> colors.statusPaid

    MontosSugeridos.Sugerencia.AL_CORRIENTE -> colors.statusTeal
    MontosSugeridos.Sugerencia.LIQUIDAR -> colors.promise
    MontosSugeridos.Sugerencia.LO_DE_SIEMPRE -> colors.brand
    MontosSugeridos.Sugerencia.REDONDO -> colors.onSurfaceMuted
}

/** Fondo (tint) de cada sugerido. */
fun fondoDelSugerido(cual: MontosSugeridos.Sugerencia, colors: MspColors): Color = when (cual) {
    MontosSugeridos.Sugerencia.ESPERADO_HOY,
    MontosSugeridos.Sugerencia.ESPERADO_POR_COSTUMBRE -> colors.statusPaidTint

    MontosSugeridos.Sugerencia.AL_CORRIENTE -> colors.statusTealTint
    MontosSugeridos.Sugerencia.LIQUIDAR -> colors.promiseTint
    MontosSugeridos.Sugerencia.LO_DE_SIEMPRE -> colors.brandTint
    MontosSugeridos.Sugerencia.REDONDO -> colors.surface2
}

/**
 * Las pastillas de método (`.meths`): **efectivo y transferencia. Sin
 * terminal** — es lo que este negocio cobra.
 *
 * La selección va en `brand`/`brandTint`: es una selección genérica sin estado
 * de cobro asociado, y el verde nunca es acción (regla dura del plan y del
 * Task 2 §3(a), que nombra este caso explícitamente).
 *
 * **Siempre en una fila de [TOQUE] de alto**, anclada encima del teclado, y con
 * [extra] al final —el botón "Foto"—. Antes, a letra grande, las pastillas se
 * apilaban y empujaban el teclado; ahora el rótulo se topa en 1.2× para que
 * "Transferencia" quepa junto al botón de foto (mock `registrar-abono-fijo.html`).
 */
@Composable
fun SelectorDeMetodo(
    seleccionado: MetodoDeCobro,
    onMetodo: (MetodoDeCobro) -> Unit,
    modifier: Modifier = Modifier,
    extra: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TOQUE),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        METODOS_DE_CAPTURA.forEach { metodo ->
            PastillaDeMetodo(
                metodo = metodo,
                activa = metodo == seleccionado,
                onClick = { onMetodo(metodo) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
        extra()
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
        modifier = modifier.testTag(METODO_TAG + metodo.name.lowercase()),
        shape = MspTheme.shapes.control,
        color = if (activa) colors.brandTint else colors.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = if (activa) colors.brand else colors.outline
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            ConTopeDeLetra(TOPE_DEL_METODO) {
                Text(
                    text = metodo.etiqueta,
                    style = MspTheme.type.methodLabel,
                    color = contenido,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * El teclado del mock (`.pad`): 1-9, punto, 0 y borrar.
 *
 * **Mide siempre [ALTO_DEL_TECLADO]**, a toda escala: cada tecla es de [TOQUE]
 * exactos (no "al menos"), porque el teclado va anclado abajo y un teclado que
 * crece con la letra empujaría hacia arriba lo que está anclado encima de él.
 *
 * [habilitado] en `false` lo pinta **apagado** —y deja de responder—: es el
 * estado de la verificación pendiente, en el que el ViewModel ya ignora toda
 * tecla (`RegistrarAbonoUiState.sePuedeCapturar`). Antes se veía vivo y no
 * hacía nada, que es como el cobrador decide que la app está rota.
 */
@Composable
fun TecladoDeMontos(
    onDigito: (Int) -> Unit,
    onPunto: () -> Unit,
    onBorrar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(ALTO_DEL_TECLADO)
            .alpha(if (habilitado) 1f else ALFA_APAGADO),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        listOf(1..3, 4..6, 7..9).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
                fila.forEach { digito ->
                    Tecla(
                        etiqueta = digito.toString(),
                        tag = digito.toString(),
                        onClick = { onDigito(digito) },
                        habilitado = habilitado,
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
                habilitado = habilitado,
                modifier = Modifier.weight(1f),
                apagada = true
            )
            Tecla(
                etiqueta = "0",
                tag = "0",
                onClick = { onDigito(0) },
                habilitado = habilitado,
                modifier = Modifier.weight(1f)
            )
            TeclaDeBorrado(
                onClick = onBorrar,
                habilitado = habilitado,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Lo que se transparenta el teclado apagado. El mismo 0.4 del mock. */
private const val ALFA_APAGADO = 0.4f

/**
 * Una tecla del teclado.
 *
 * Va en [MspCard] y no en un `Surface` pelado porque el `NumericKeypad` de
 * kollect monta cada tecla en `CampoSurface` —su KDoc lo nombra: "hairline
 * borders, 22sp tabular digits, muted decimal/backspace keys"— y sin el
 * hairline el teclado se lee como doce huecos en vez de doce teclas.
 *
 * El dígito lleva `lineHeight` igual a su tamaño: con el 1.4× de la escala, a
 * 2.0× el renglón mediría 62dp y la tecla —que es de [TOQUE] exactos— lo
 * recortaría.
 */
@Composable
private fun Tecla(
    etiqueta: String,
    tag: String,
    onClick: () -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier,
    apagada: Boolean = false
) {
    MspCard(
        modifier = modifier
            .height(TOQUE)
            .semantics { if (!habilitado) disabled() }
            .testTag(TECLA_TAG + tag),
        shape = MspTheme.shapes.control,
        onClick = if (habilitado) onClick else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = etiqueta,
                style = MspTheme.type.keypadKey.copy(lineHeight = MspTheme.type.keypadKey.fontSize),
                color = if (apagada) MspTheme.colors.onSurfaceMuted else MspTheme.colors.onSurface
            )
        }
    }
}

@Composable
private fun TeclaDeBorrado(
    onClick: () -> Unit,
    habilitado: Boolean,
    modifier: Modifier = Modifier
) {
    MspCard(
        modifier = modifier
            .height(TOQUE)
            .semantics { if (!habilitado) disabled() }
            .testTag(TECLA_TAG + TECLA_BORRAR),
        shape = MspTheme.shapes.control,
        onClick = if (habilitado) onClick else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Clear,
                contentDescription = "Borrar",
                tint = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
