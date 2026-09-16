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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
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

/** `testTag` de cada chip genérico (fecha, hora). Se sufija con su clave. */
const val CHIP_TAG: String = "visitas_chip_"

/** `testTag` del renglón de una cuenta. Se sufija con su `DOCTO_CC_ACR_ID`. */
const val CUENTA_TAG: String = "visitas_cuenta_"

/** `testTag` del atajo "todas / ninguna" del encabezado de cuentas. */
const val TODAS_LAS_CUENTAS_TAG: String = "visitas_cuentas_todas"

/** `testTag` del CTA de guardar. */
const val GUARDAR_TAG: String = "visitas_guardar"

/** `testTag` de la razón que explica el CTA apagado. */
const val RAZON_TAG: String = "visitas_razon"

/** `testTag` del campo de monto prometido. */
const val MONTO_TAG: String = "visitas_monto"

/** `testTag` de la banda que muestra la recomendación. */
const val RECOMENDACION_TAG: String = "visitas_recomendacion"

/**
 * Alto mínimo tocable. El plan pide >=50px; el design system ya pide 56dp, y
 * ese es el que manda — un piso más alto nunca viola el más bajo.
 */
internal val TOQUE = 56.dp

/** El lado de la casilla / anillo de una cuenta. El mismo 22dp de `HojaDeAbono`. */
private val MARCA = 22.dp

/** El grosor del borde de la marca vacía. */
private val BORDE_DE_LA_MARCA = 3.dp

/** El punto lleno del anillo de opción única. */
private val PUNTO = 8.dp

/** La palomita de la casilla marcada. */
private val PALOMITA = 14.dp

/**
 * Ancho máximo de la etiqueta de alcance. Con "TODA LA PUERTA" y la escala 2.0,
 * una etiqueta sin techo se come el renglón entero y empuja el título del
 * desenlace fuera de la fila. Con techo, se apila en dos renglones y el título
 * conserva su espacio.
 */
private val ANCHO_DEL_ALCANCE = 124.dp

/**
 * La fila de navegación: "atrás", y nada más.
 *
 * Tuvo un hueco `alFinal` para un botón de cámara **en línea**, que existía
 * porque la sección de comprobantes vivía debajo de la línea de flotación y el
 * cobrador podía no descubrirla nunca. Se retiró con la rejilla: el remedio
 * dejaba TRES afordantes para agregar una foto —este, la pastilla del pie y la
 * lista— y un aviso de fallo que no podía decir cuál de los tres había fallado.
 * Ahora hay un solo «+», y un segundo botón acá volvería a ser la repetición que
 * delata que la pantalla no sabe qué importa.
 */
@Composable
fun BarraDeVisita(onAtras: () -> Unit, modifier: Modifier = Modifier) {
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
    }
}

/** El `.ctx` del mock: avatar, nombre, cuentas y dirección, saldo total a la derecha. */
@Composable
fun TiraDelCliente(contexto: ContextoDeVisita, modifier: Modifier = Modifier) {
    MspCard(modifier = modifier.fillMaxWidth()) {
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
 * ## El color del estado se queda en el ÍCONO
 *
 * El anillo y el fondo del renglón elegido van en `brand`, no en el color del
 * desenlace. Es la corrección de un defecto medido: al elegir "prometió" se
 * pintaban de rojo el renglón, la cuenta, la fecha y el monto a la vez, y en una
 * pantalla de **captura** eso no se lee como "elegiste esto", se lee como
 * "algo está mal". El rojo de esta app es `statusOverdue`; gastarlo en una
 * selección convierte un formulario en una alarma.
 *
 * El semáforo no se pierde: vive en el glifo, que es donde siempre estuvo y
 * donde sí significa estado. Color + ícono, nunca color solo.
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
    // `MspCard` pone el hairline de 1dp `outline` en TODA tarjeta del sistema;
    // el anillo de 2dp del desenlace elegido se dibuja ENCIMA con un
    // `Modifier.border`, no en lugar del hairline. Antes el renglón no
    // seleccionado no tenía borde alguno.
    val anillo = if (seleccionado) {
        Modifier.border(2.dp, MspTheme.colors.brand, MspTheme.shapes.tile)
    } else {
        Modifier
    }
    MspCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE)
            .then(anillo)
            .testTag(OPCION_TAG + resultado.name.lowercase()),
        color = if (seleccionado) MspTheme.colors.brandTint else MspTheme.colors.surface,
        onClick = if (habilitado) onElegir else null
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
            EtiquetaDeAlcance(resultado.etiquetaDeAlcance)
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
 * El `.oscope` del mock: la etiqueta que dice **a qué se aplica** lo que el
 * cobrador está por guardar — toda la puerta, o una cuenta.
 *
 * Dos letras chicas en versalitas sobre `surface2`, con anillo de 1dp porque en
 * claro `surface2` (#FBFCFC) sobre `surface` (#FFFFFF) no se distingue y sin él
 * la caja desaparece.
 *
 * **Dos renglones como máximo, y nunca recortada.** "Toda la puerta" es más
 * larga que el "CLIENTE" que decía antes, y a escala 2.0 en una fila que ya
 * lleva glifo y título no cabe de un tirón; apilarla es la regla del repo
 * (antes de truncar, apilar).
 *
 * El techo de [ANCHO_DEL_ALCANCE] está **medido, no elegido**: con 96dp el
 * golden de escala 2.0 salía diciendo "UNA CUENT" — la caja recortaba la última
 * letra en silencio, sin puntos suspensivos y sin que ningún assert lo viera. Es
 * exactamente el defecto que mirar los goldens existe para atrapar.
 */
@Composable
private fun EtiquetaDeAlcance(texto: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(max = ANCHO_DEL_ALCANCE)
            .background(MspTheme.colors.surface2, MspTheme.shapes.chip9)
            // El anillo de 1dp: en claro `surface2` (#FBFCFC) sobre `surface`
            // (#FFFFFF) no se distingue, así que sin él la caja del mock
            // desaparecía y quedaba otra vez el texto suelto. Es el patrón que
            // kollect usa para este mismo tipo de insignia en versalitas
            // (`SaleWorkStateDot.kt:174-177`: tint + anillo de 1dp).
            .border(1.dp, MspTheme.colors.outline, MspTheme.shapes.chip9)
            .padding(horizontal = MspTheme.spacing.xs, vertical = 2.dp)
    ) {
        Text(
            text = texto.uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.eyebrow,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 2
        )
    }
}

/**
 * El título de una sección de la pantalla: **una pregunta**, en el tamaño de un
 * título de tarjeta.
 *
 * "¿Qué pasó en la puerta?" en vez de "QUÉ PASÓ". Un rótulo en versalitas
 * etiqueta un bloque; una pregunta pide una respuesta, que es exactamente lo
 * que cada sección de esta pantalla hace. Es el mismo registro que ya usa
 * `HojaDeAbono` de `:feature:pagos` con su "¿A cuál cuenta?", así que las dos
 * pantallas de captura hablan igual.
 */
@Composable
fun TituloDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto,
        style = MspTheme.type.cardTitle,
        color = MspTheme.colors.onSurface,
        modifier = modifier.padding(top = MspTheme.spacing.sm)
    )
}

/**
 * El encabezado de la sección de cuentas: el título a la izquierda y, a la
 * derecha, **todas o ninguna**.
 *
 * El atajo dice lo que va a pasar al tocarlo, no el estado actual: con todo
 * marcado ofrece "ninguna", y al revés. Un botón que anuncia su efecto no
 * necesita que nadie recuerde qué significaba.
 */
@Composable
fun EncabezadoDeCuentas(
    todasMarcadas: Boolean,
    habilitado: Boolean,
    onTodas: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TituloDeSeccion("¿De cuáles cuentas?", modifier = Modifier.weight(1f))
        Surface(
            onClick = onTodas,
            enabled = habilitado,
            modifier = Modifier
                .heightIn(min = TOQUE)
                .testTag(TODAS_LAS_CUENTAS_TAG),
            shape = MspTheme.shapes.chip,
            // `brandTint` y no `surface2`: el atajo vive sobre el fondo de la
            // pantalla, y `surface2` (#FBFCFC) sobre `background` (#F4F6F5) no se
            // distingue — el golden lo enseñó como una mancha pálida detrás de un
            // texto azul suelto, que es la "pastilla flotando" de siempre.
            color = MspTheme.colors.brandTint
        ) {
            Box(
                // `md` y no `sm`: con 8dp la caja medía 48dp de ancho por 56 de
                // alto y el radio del 50% la volvía un círculo. Un control de
                // texto tiene que verse como una pastilla.
                modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (todasMarcadas) "ninguna" else "todas",
                    style = MspTheme.type.chipLabel,
                    color = MspTheme.colors.brand,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * El encabezado de una sección del fold (`.fk` del mock: `10px/800`, `.13em`,
 * `uppercase`).
 *
 * En versalitas y con el rol `eyebrow`, el mismo que `LabelDeSeccion` de
 * `:feature:pagos` — es el rol que kollect usa para el encabezado de sección
 * (`CampoType.kt:218`, "caller uppercases"). Antes iba en `sectionLabel`
 * (11/800/+0.08em) y sin `.uppercase()`: casi el mismo grosor, pero minúsculas,
 * y ese es el detalle que hacía que la pantalla se leyera suave. Con dos rótulos
 * del mismo sistema en dos rols distintos, además, las dos features no
 * coincidían entre sí.
 */
@Composable
fun RotuloDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(BUSINESS_LOCALE),
        style = MspTheme.type.eyebrow,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(top = MspTheme.spacing.sm)
    )
}

/**
 * Un chip de opción; apagado, se ve apagado — un control que se ve vivo y no
 * responde es una mentira.
 *
 * **Elegido = `brand`, en toda la pantalla.** Los chips de fecha iban antes en
 * el color del desenlace (rojo en la promesa, violeta en la cita) con el
 * argumento de que "sí llevan estado". No lo llevan: un chip de fecha es una
 * elección, y pintarlo de rojo dejaba la captura de una promesa con el tipo, la
 * cuenta, la fecha y el monto los cuatro en rojo. El estado se quedó donde sí
 * significa algo, que es el glifo del desenlace.
 */
@Composable
fun ChipDeOpcion(
    texto: String,
    activo: Boolean,
    habilitado: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier,
    contenidoActivo: Color = MspTheme.colors.brand,
    fondoActivo: Color = MspTheme.colors.brandTint
) {
    Surface(
        onClick = onElegir,
        enabled = habilitado,
        modifier = modifier
            .heightIn(min = TOQUE)
            .widthIn(min = TOQUE),
        shape = MspTheme.shapes.chip,
        color = if (activo) fondoActivo else MspTheme.colors.surface2,
        // El anillo del `.ch.on` del mock. No es adorno: el color solo nunca
        // puede ser el portador del significado.
        //
        // Y el chip APAGADO también lleva anillo, en `outline`. Sin él, un chip
        // `surface2` (#FBFCFC) sobre la tarjeta `surface` (#FFFFFF) es invisible:
        // el golden enseñaba tres etiquetas flotando sin caja, que es la misma
        // "pastilla flotando" que esta sesión ya tuvo que arreglar una vez.
        border = if (activo) {
            BorderStroke(1.5.dp, contenidoActivo)
        } else {
            BorderStroke(1.dp, MspTheme.colors.outline)
        }
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
    MspCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            contenido()
        }
    }
}

/**
 * El renglón de una cuenta, con su marca a la izquierda.
 *
 * ## Por su nombre, no por su folio
 *
 * Decía "V-5021 · $2,100". Nadie parado en una puerta sabe qué es V-5021; sabe
 * cuál es el refrigerador. Ahora dice el producto y, debajo, lo que le toca dar
 * por esa cuenta — que es el número con el que se decide si el "no te voy a
 * pagar" aplica a esta o a la otra.
 *
 * ## La marca dice la verdad sobre el comportamiento
 *
 * [varias] la parte en dos formas, y no es adorno: una **casilla** se marca y se
 * desmarca sin tocar a las demás; un **botón de opción** mueve la selección. Con
 * casillas donde el comportamiento es de opción única, el cobrador marcaría dos
 * y esperaría que la app guardara dos — que es justo lo que "prometió" no puede
 * hacer, porque la promesa lleva una fecha y un monto. Es el mismo argumento con
 * el que `HojaDeAbono` eligió radio para el dinero.
 *
 * **La selección va en `brand`.** El rojo de esta app significa "se negó".
 */
@Composable
fun FilaDeCuenta(
    venta: VentaParaVisitar,
    marcada: Boolean,
    varias: Boolean,
    habilitado: Boolean,
    onTocar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onTocar,
        enabled = habilitado,
        shape = MspTheme.shapes.field,
        color = if (marcada) MspTheme.colors.brandTint else MspTheme.colors.surface2,
        border = if (marcada) BorderStroke(1.5.dp, MspTheme.colors.brand) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE)
            .testTag(CUENTA_TAG + venta.ventaId)
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            MarcaDeCuenta(marcada = marcada, varias = varias)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = venta.nombre,
                    style = MspTheme.type.listTitle,
                    color = colorDeTexto(habilitado),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "le toca ${formatMoneyMxn(venta.parcialidad.amount)}",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * La marca: **cuadro con palomita** cuando se pueden marcar varias, **anillo con
 * punto** cuando solo una.
 *
 * Es un portador que no es color: en oscuro y a plena luz del sol el tint de la
 * fila elegida y el de las otras se parecen demasiado, y la palomita (o el
 * punto) se ve igual. Mismo razonamiento que el anillo de `HojaDeAbono`.
 */
@Composable
private fun MarcaDeCuenta(marcada: Boolean, varias: Boolean) {
    val forma = if (varias) MspTheme.shapes.chip9 else MspTheme.shapes.chip
    Box(
        modifier = Modifier
            .size(MARCA)
            .background(
                color = if (marcada) MspTheme.colors.brand else MspTheme.colors.progressTrack,
                shape = forma
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            marcada && varias -> Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MspTheme.colors.onBrand,
                modifier = Modifier.size(PALOMITA)
            )

            marcada -> Box(
                modifier = Modifier
                    .size(PUNTO)
                    .background(MspTheme.colors.onBrand, MspTheme.shapes.chip)
            )

            else -> Box(
                modifier = Modifier
                    .size(MARCA - BORDE_DE_LA_MARCA)
                    .background(MspTheme.colors.surface2, forma)
            )
        }
    }
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
                text = "¿Cuánto prometió?",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            if (digitos.isNotEmpty()) {
                Text(
                    text = "$",
                    style = MspTheme.type.amountCard,
                    color = MspTheme.colors.onSurface
                )
            }
            BasicTextField(
                value = digitos,
                onValueChange = { onCambio(it.filter(Char::isDigit)) },
                enabled = habilitado,
                singleLine = true,
                // El monto capturado va en tinta, no en rojo. Iba en
                // `statusOverdue` porque "prometió" es rojo en el semáforo, y el
                // resultado era una cifra que el cobrador acaba de teclear
                // pintada del color con el que esta app dice "esto está mal". El
                // cursor sí va en `brand`: es el foco, o sea selección.
                textStyle = LocalTextStyle.current
                    .merge(MspTheme.type.amountCard)
                    .merge(TextStyle(color = MspTheme.colors.onSurface)),
                cursorBrush = SolidColor(MspTheme.colors.brand),
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

/**
 * El dock: el CTA de guardar y, debajo, **un solo renglón que dice la razón
 * cuando el botón está apagado y el efecto cuando está encendido**.
 *
 * Lo segundo entró con la selección múltiple: "se guardan 2 visitas, una por
 * cuenta" es la única señal de que desmarcar una casilla cambia lo que va a
 * quedar escrito, y un renglón que ya existía para la razón lo dice con coste
 * vertical cero. Quién decide cuál de los dos textos toca es
 * `RegistrarVisitaUiState.pieDelCta`, no esta pieza.
 *
 * **Apagado también quiere decir apagado visualmente** (`.btn.off` del mock) y
 * sin `onClick`: un botón vivo que no hace nada es la mentira que la Task 18
 * tuvo que arreglar dos veces.
 *
 * **El CTA es [MspPrimaryFieldButton], no un `Surface` a mano (corrección de
 * la ronda 1).** El `Surface` local reproducía la receta del compartido
 * —`heightIn(min = 56dp)` + `shapes.button` + `type.buttonLarge` +
 * `brand`/`onBrand`, y el apagado pintado a mano— incluido el gotcha que el
 * KDoc del compartido documenta: un `Surface` clickable de M3 no aplica alfa
 * de deshabilitado por sí solo. Con el compartido llegan además el **haptic**
 * de cada tap (spec §8.4) y la sombra de 8dp tintada a marca, y el apagado
 * pasa del `surface2` local al `outline` del sistema, que es el token que el
 * design system usa para un botón muerto.
 */
@Composable
fun DockDeLaVisita(
    texto: String,
    habilitado: Boolean,
    pie: String?,
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
            MspPrimaryFieldButton(
                text = texto,
                onClick = onGuardar,
                enabled = habilitado,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(GUARDAR_TAG)
            )
            if (pie != null) {
                Text(
                    text = pie,
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
