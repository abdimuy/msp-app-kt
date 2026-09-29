@file:Suppress(
    "TooManyFunctions"
) // una pieza por parte de la zona de arriba del mock; juntarlas no las haria mas legibles.

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.AccionesIconos

// ---------------------------------------------------------------------------
// La zona de arriba de "Registrar abono" (mock `registrar-abono-fijo.html`,
// aprobado por el dueño el 2026-09-29).
//
// La pantalla se arma DE ABAJO HACIA ARRIBA: botón, teclado y método van
// anclados abajo y nunca se mueven. Todo lo demás —el cliente, el producto con
// su saldo, la cifra con su franja y los sugeridos— vive en lo que sobra, y si
// no cabe CEDE (renglones, rótulos, tamaño de la cifra), nunca empuja.
// ---------------------------------------------------------------------------

/** Tope de letra del nombre del cliente y del texto de la franja: 1.5×. */
internal const val TOPE_DEL_TEXTO = 1.5f

/** Tope de letra de "Efectivo / Transferencia": 1.2×, para que quepan junto a "Foto". */
internal const val TOPE_DEL_METODO = 1.2f

/** Tope de letra de las cifras chicas (saldo, sugeridos): 1.65×, lo que ya medían a 2.0×. */
internal const val TOPE_DE_LAS_CIFRAS = 1.65f

/** El tope de las cifras chicas cuando ya no cabe nada más: lo que medían a 1.5×. */
internal const val TOPE_DE_LAS_CIFRAS_CHICAS = 1.36f

/** Tope de letra del botón: 1.77×, lo que ya medía a 2.0× (golden `captura_*_2_0`). */
internal const val TOPE_DEL_BOTON = 1.77f

/** La cifra del monto nunca baja de aquí. */
internal val CIFRA_MINIMA: Dp = 28.dp

/** El tamaño que la cifra busca a letra normal y a 1.5×; a 2.0× es 1.21× esto. */
internal val CIFRA_MAXIMA: Dp = 40.dp

/**
 * **Topa la escala de letra** del subárbol en [tope].
 *
 * El tamaño de letra que el cobrador escoge crece TODO el texto de forma lineal
 * (`LocalDensity.fontScale`). En esta pantalla hay textos que no pueden crecer
 * sin límite porque viven en cajas de alto o ancho fijo —el botón, el método,
 * la franja—, y la única forma de toparlos sin reescribir cada estilo es darles
 * una densidad con la escala topada. El `density` (dp → px) no se toca.
 */
@Composable
internal fun ConTopeDeLetra(tope: Float, contenido: @Composable () -> Unit) {
    val densidad = LocalDensity.current
    if (densidad.fontScale <= tope) {
        contenido()
    } else {
        CompositionLocalProvider(
            LocalDensity provides Density(densidad.density, tope),
            content = contenido
        )
    }
}

/** ¿El cobrador escogió letra grande? Es lo que decide la distribución de la zona de arriba. */
@Composable
internal fun esLetraGrande(): Boolean = LocalFontSizeLevel.current != FontSizeLevel.NORMAL

// --- Lo que cede ----------------------------------------------------------------

/**
 * Un paso de lo que la zona de arriba cede para caber. Los pasos van en orden
 * (mock, sección 2): se va "Monto recibido", el producto pasa a 1 renglón, la
 * cifra se encoge (nunca bajo [CIFRA_MINIMA]), la franja pasa a 1 renglón, los
 * márgenes se aprietan, el producto sale (queda en el desplegable) y, por
 * último, el saldo se topa en 1.36× en vez de 1.65× (letra grande en un
 * teléfono bajo).
 */
internal data class Cesion(
    val conRotulo: Boolean,
    val productoEnDosRenglones: Boolean,
    val cifraMinima: Dp,
    val franjaEnDosRenglones: Boolean,
    val apretado: Boolean,
    val conProducto: Boolean,
    val saldoChico: Boolean = false
)

/**
 * Los pasos para esta letra. A letra grande se arranca ya sin rótulo, con el
 * producto en 1 renglón y la franja en 1 renglón — es lo que el mock pinta a
 * 1.5× y 2.0× aunque sobre espacio.
 */
internal fun cesionesPara(grande: Boolean, cifraMaxima: Dp): List<Cesion> {
    val todas = listOf(
        Cesion(true, true, cifraMaxima, true, apretado = false, conProducto = true),
        Cesion(false, true, cifraMaxima, true, apretado = false, conProducto = true),
        Cesion(false, false, cifraMaxima, true, apretado = false, conProducto = true),
        Cesion(false, false, CIFRA_MINIMA, true, apretado = false, conProducto = true),
        Cesion(false, false, CIFRA_MINIMA, false, apretado = false, conProducto = true),
        Cesion(false, false, CIFRA_MINIMA, false, apretado = true, conProducto = true),
        Cesion(false, false, CIFRA_MINIMA, false, apretado = true, conProducto = false),
        Cesion(
            false,
            false,
            CIFRA_MINIMA,
            false,
            apretado = true,
            conProducto = false,
            saldoChico = true
        )
    )
    return if (grande) {
        todas.drop(2).map { it.copy(franjaEnDosRenglones = false) }.distinct()
    } else {
        todas
    }
}

/**
 * **La zona que cede.** Prueba cada [Cesion] en orden —midiendo el contenido
 * con alto libre, en modo `medir`— y se queda con la primera que cabe en el
 * alto que le tocó. Si ninguna cabe, usa la última. Después pinta esa, ya con
 * el alto exacto, en modo final: ahí la tarjeta de la cifra toma lo que sobre.
 *
 * Es un `SubcomposeLayout` porque "cabe o no cabe" depende de medidas reales
 * del texto (la letra que el cobrador escogió, el nombre de SU producto), no de
 * una tabla por escala: con otro teléfono, otra letra u otro producto, la
 * decisión se vuelve a tomar sola.
 */
@Composable
internal fun ZonaQueCede(
    cesiones: List<Cesion>,
    modifier: Modifier = Modifier,
    contenido: @Composable (cesion: Cesion, medir: Boolean) -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val ancho = constraints.maxWidth
        val alto = constraints.maxHeight
        val libre = Constraints(minWidth = ancho, maxWidth = ancho)
        val elegida = cesiones.firstOrNull { cesion ->
            val medidos = subcompose(cesion) { contenido(cesion, true) }.map { it.measure(libre) }
            medidos.sumOf { it.height } <= alto
        } ?: cesiones.last()
        val finales = subcompose(SLOT_FINAL) { contenido(elegida, false) }
            .map { it.measure(Constraints.fixed(ancho, alto)) }
        layout(ancho, alto) { finales.forEach { it.place(0, 0) } }
    }
}

private const val SLOT_FINAL = "final"

// --- La cabecera: sólo el cliente -------------------------------------------------

/**
 * **El nombre del cliente, en un renglón.** Es lo único que queda de la
 * cabecera: sin "Abono" y sin flecha —atrás lo hace el gesto o el botón del
 * sistema, como en el detalle de cliente y de venta—.
 *
 * [onToque] sólo llega cuando el producto tuvo que salir de la pantalla (letra
 * grande en un teléfono bajo): entonces el nombre es el que abre el desplegable
 * del producto (decisión del dueño, 2026-09-29), y crece a un área tocable.
 */
@Composable
internal fun NombreDelCliente(
    nombre: String,
    apretado: Boolean,
    onToque: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onToque != null) {
                    Modifier
                        .heightIn(min = 50.dp)
                        .clickable(onClick = onToque)
                } else {
                    Modifier.padding(top = if (apretado) 2.dp else MspTheme.spacing.sm)
                }
            )
            .semantics(mergeDescendants = true) {}
            .testTag(NOMBRE_DEL_CLIENTE_TAG),
        contentAlignment = Alignment.CenterStart
    ) {
        ConTopeDeLetra(TOPE_DEL_TEXTO) {
            Text(
                text = nombre,
                style = MspTheme.type.listTitle.copy(
                    lineHeight = MspTheme.type.listTitle.fontSize * 1.25f
                ),
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- El producto y el saldo ---------------------------------------------------------

/**
 * **El producto (2 renglones máximo) y el saldo grande.** Sin folio.
 *
 * El producto sube de 12.5 a 17 sp y el saldo de 12.5 a 24 sp. A letra normal
 * van lado a lado; a letra grande el producto va en 1 renglón a lo ancho y el
 * saldo en su propia línea.
 *
 * Toda la tarjeta es el área que abre el desplegable, pero **solo cuando hay
 * algo que desplegar** —el nombre no cupo— o ya está abierto: un toque que no
 * hace nada es un botón mudo. La flecha es chica y sin caja; el área tocable es
 * la tarjeta entera.
 *
 * El saldo va aquí y en grande porque es **el techo del bloqueo duro**: el
 * cobrador tiene que ver contra qué se está topando sin abrir otra pantalla.
 */
@Composable
internal fun TarjetaDelProducto(
    producto: String,
    saldo: Money,
    cesion: Cesion,
    abierto: Boolean,
    onAlternar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val grande = esLetraGrande()
    var noCupo by remember(producto, cesion) { mutableStateOf(false) }
    val tocable = cesion.conProducto && (noCupo || abierto)
    val relleno = if (cesion.apretado) 5.dp else if (grande) 6.dp else 10.dp
    val nombre: @Composable (Modifier) -> Unit = { mod ->
        Row(modifier = mod, verticalAlignment = Alignment.Top) {
            Text(
                text = producto,
                style = MspTheme.type.cardTitle.copy(
                    lineHeight = MspTheme.type.cardTitle.fontSize * (if (grande) 1.2f else 1.26f)
                ),
                color = colors.onSurface,
                maxLines = if (cesion.productoEnDosRenglones) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { noCupo = it.hasVisualOverflow },
                modifier = Modifier.weight(1f, fill = false)
            )
            if (tocable) {
                Icon(
                    imageVector = AccionesIconos.Chevron,
                    contentDescription = null,
                    tint = colors.onSurfaceMuted,
                    modifier = Modifier
                        .padding(start = 2.dp, top = 2.dp)
                        .size(20.dp)
                        .rotate(if (abierto) -90f else 90f)
                )
            }
        }
    }
    val base = modifier
        .fillMaxWidth()
        .background(colors.surface, MspTheme.shapes.card)
        .border(1.dp, colors.outline, MspTheme.shapes.card)
        .then(if (tocable) Modifier.clickable(onClick = onAlternar) else Modifier)
        .testTag(PRODUCTO_TAG)
        .padding(start = 14.dp, end = 12.dp, top = relleno, bottom = relleno)
    if (grande) {
        Column(modifier = base) {
            if (cesion.conProducto) nombre(Modifier.fillMaxWidth())
            SaldoDeLaVenta(saldo = saldo, enLinea = true, chico = cesion.saldoChico)
        }
    } else {
        Row(
            modifier = base,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (cesion.conProducto) nombre(Modifier.weight(1f))
            SaldoDeLaVenta(
                saldo = saldo,
                enLinea = false,
                chico = cesion.saldoChico,
                modifier = if (cesion.conProducto) Modifier else Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SaldoDeLaVenta(
    saldo: Money,
    enLinea: Boolean,
    chico: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val rotulo: @Composable () -> Unit = {
        Text(
            text = "Saldo".uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.eyebrow,
            color = colors.onSurfaceMuted
        )
    }
    val cifra: @Composable () -> Unit = {
        ConTopeDeLetra(if (chico) TOPE_DE_LAS_CIFRAS_CHICAS else TOPE_DE_LAS_CIFRAS) {
            Text(
                text = formatMoneyMxn(saldo.amount),
                style = MspTheme.type.metricSmall.copy(fontSize = 24.sp, lineHeight = 27.sp),
                color = colors.onSurface,
                maxLines = 1,
                modifier = Modifier.testTag(SALDO_DE_LA_VENTA_TAG)
            )
        }
    }
    if (enLinea) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            rotulo()
            cifra()
        }
    } else {
        Column(modifier = modifier, horizontalAlignment = Alignment.End) {
            rotulo()
            cifra()
        }
    }
}

/**
 * **El nombre completo del producto**, un producto por renglón (la descripción
 * de la venta viene separada por comas: `CargarDetalleVenta.deLaDescripcion`).
 *
 * Se abre ENCIMA de la cifra y nunca baja del método: no empuja nada. Si a
 * letra grande no cabe, se desplaza por dentro.
 */
@Composable
internal fun DesplegableDelProducto(
    productos: List<String>,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(SOMBRA_DEL_DESPLEGABLE, MspTheme.shapes.card)
            .background(colors.surface, MspTheme.shapes.card)
            .border(1.dp, colors.outline, MspTheme.shapes.card)
            .testTag(DESPLEGABLE_DEL_PRODUCTO_TAG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCerrar),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (if (productos.size > 1) "Productos" else "Producto").uppercase(
                    BUSINESS_LOCALE
                ),
                style = MspTheme.type.eyebrow,
                color = colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = AccionesIconos.Chevron,
                contentDescription = "Cerrar",
                tint = colors.onSurfaceMuted,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(-90f)
            )
        }
        productos.forEachIndexed { indice, producto ->
            if (indice > 0) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline)
                )
            }
            Text(
                text = producto,
                style = MspTheme.type.cardTitle,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/** La sombra del desplegable: está encima de la captura, no dentro. */
private val SOMBRA_DEL_DESPLEGABLE: Dp = 8.dp

// --- La cifra y su franja --------------------------------------------------------------

/** Cómo se pinta la franja. */
internal enum class TonoDeLaFranja { NEUTRO, AMBAR, ROJO, VERDE }

/**
 * **Lo único que la franja dice.** Un solo mensaje a la vez; quien decide cuál
 * gana es la pantalla (`RegistrarAbonoScreen`), con la prioridad del mock:
 * error, bloqueo, aviso en vivo, parcialidad dudosa y, sin nada de eso, el
 * saldo nuevo.
 *
 * [tag] conserva el `testTag` que cada banda tenía cuando eran bandas sueltas
 * (`BLOQUEO_TAG`, `AVISO_TAG`, …): las pruebas que ya las buscaban siguen
 * midiendo lo mismo.
 */
internal data class MensajeDeLaFranja(
    val tono: TonoDeLaFranja,
    val renglones: List<AnnotatedString>,
    val tag: String,
    val detalle: String? = null
)

/** "Saldo nuevo $1,230": la cifra en negritas, lo demás apagado. */
@Composable
internal fun saldoNuevo(saldoNuevo: Money): MensajeDeLaFranja {
    val fuerte = SpanStyle(fontWeight = FontWeight.ExtraBold, color = MspTheme.colors.onSurface)
    return MensajeDeLaFranja(
        tono = TonoDeLaFranja.NEUTRO,
        renglones = listOf(
            buildAnnotatedString {
                append("Saldo nuevo ")
                withStyle(fuerte) { append(formatMoneyMxn(saldoNuevo.amount)) }
            }
        ),
        tag = FRANJA_SALDO_NUEVO_TAG
    )
}

/** El estilo del texto de la franja: 14.5 sp en negritas, para leerse a pleno sol. */
@Composable
private fun estiloDeLaFranja() = MspTheme.type.bodyStrong.copy(
    fontSize = 14.5.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = ALTO_DEL_RENGLON_DE_LA_FRANJA.value.sp,
    // Renglones parejos: "Los pagos van de 50 en / 50" deja un huérfano.
    lineBreak = LineBreak.Heading
)

/** El alto de un renglón de la franja, a letra normal. */
private val ALTO_DEL_RENGLON_DE_LA_FRANJA: Dp = 18.dp

/** Relleno vertical (6 + 6) más el borde (2 + 2) de la franja. */
private val RELLENO_DE_LA_FRANJA: Dp = 16.dp

/**
 * Lo que la franja reserva: [renglones] renglones de texto a la letra de la
 * pantalla (topada en 1.5×), más su relleno. Es un alto FIJO para cada letra:
 * que salga o no un aviso no lo cambia, y por eso no se mueve nada.
 */
@Composable
internal fun altoDeLaFranja(renglones: Int): Dp {
    val escala = LocalDensity.current.fontScale.coerceAtMost(TOPE_DEL_TEXTO)
    return ALTO_DEL_RENGLON_DE_LA_FRANJA * escala * renglones + RELLENO_DE_LA_FRANJA
}

/**
 * **La franja**, pintada. Fondo tintado, borde de 2dp del color del nivel,
 * icono lleno y texto en negritas en un tono **más oscuro que el borde en claro
 * y más claro en oscuro** —mezclado con `onSurface`—: la franja se lee a pleno
 * sol, que es donde el cobrador la ve.
 *
 * Se come los toques: cuando un aviso largo no cabe y se monta encima de los
 * sugeridos, un toque sobre la franja no puede caer en un chip de dinero que
 * no se ve.
 */
@Composable
internal fun FranjaDelAbono(mensaje: MensajeDeLaFranja, modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    val shape = MspTheme.shapes.control
    val (borde, fondo) = when (mensaje.tono) {
        TonoDeLaFranja.NEUTRO -> Color.Transparent to Color.Transparent
        TonoDeLaFranja.AMBAR -> colors.statusPartial to colors.statusPartialTint
        TonoDeLaFranja.ROJO -> colors.statusOverdue to colors.statusOverdueTint
        TonoDeLaFranja.VERDE -> colors.statusPaid to colors.statusPaidTint
    }
    val tinta = when (mensaje.tono) {
        TonoDeLaFranja.NEUTRO -> colors.onSurfaceMuted
        else -> lerp(borde, colors.onSurface, MEZCLA_DE_LA_TINTA)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(fondo, shape)
            .then(
                if (mensaje.tono == TonoDeLaFranja.NEUTRO) {
                    Modifier
                } else {
                    Modifier.border(
                        2.dp,
                        borde,
                        shape
                    )
                }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .testTag(mensaje.tag)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (mensaje.tono == TonoDeLaFranja.NEUTRO) {
            Arrangement.Center
        } else {
            Arrangement.spacedBy(MspTheme.spacing.sm)
        }
    ) {
        if (mensaje.tono != TonoDeLaFranja.NEUTRO) {
            Icon(
                imageVector = if (mensaje.tono == TonoDeLaFranja.VERDE) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = borde,
                modifier = Modifier.size(20.dp)
            )
        }
        ConTopeDeLetra(TOPE_DEL_TEXTO) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                val estilo = estiloDeLaFranja()
                mensaje.renglones.forEach { renglon ->
                    Text(
                        text = renglon,
                        style = if (mensaje.tono == TonoDeLaFranja.NEUTRO) {
                            estilo.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            estilo
                        },
                        color = tinta
                    )
                }
                mensaje.detalle?.let { detalle ->
                    Text(text = detalle, style = MspTheme.type.captionStrong, color = tinta)
                }
            }
        }
    }
}

/** Cuánto de `onSurface` lleva la tinta del texto de la franja. */
private const val MEZCLA_DE_LA_TINTA = 0.3f

/**
 * **La cifra que se está tecleando, con su franja debajo.** El monto es el
 * protagonista; en [conError] toma el tratamiento rojo (borde y cifra en
 * `statusOverdue`). **El color lo decide el veredicto**, no un cálculo local.
 *
 * En modo [medir] se pinta con la cifra en [cifraMinima] y la franja en su alto
 * reservado: es la medida con la que [ZonaQueCede] decide si cabe. En modo
 * final la tarjeta toma lo que le sobre a la zona y la cifra crece hasta
 * [cifraMaxima] (o lo que quepa a lo ancho: "$300,000" se encoge para no
 * cortarse).
 *
 * **Si el mensaje de la franja no cabe en su alto**, primero se encoge la cifra
 * (nunca bajo [cifraMinima]); si ni así, la franja se sale por abajo y se monta
 * encima de los sugeridos —[desborde] es lo que se estira para taparlos
 * completos—. Nunca llega al método ni al teclado: esos no están en esta zona.
 */
@Suppress("LongParameterList")
@Composable
internal fun TarjetaDeLaCifra(
    monto: String,
    conError: Boolean,
    conRotulo: Boolean,
    cifraMaxima: Dp,
    cifraMinima: Dp,
    franjaReservada: Dp,
    desborde: Dp,
    puedeDesbordar: Boolean,
    medir: Boolean,
    franja: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val color = if (conError) colors.statusOverdue else colors.statusPaid
    val marco = modifier
        .background(colors.surface, MspTheme.shapes.card)
        .border(
            if (conError) 1.5.dp else 1.dp,
            if (conError) colors.statusOverdue else colors.outline,
            MspTheme.shapes.card
        )
        .testTag(CAPTURA_TAG)
    val rotulo: @Composable () -> Unit = {
        Text(
            text = "Monto recibido".uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.overline,
            color = colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val relleno = RELLENO_DE_LA_CIFRA
    if (medir) {
        Column(
            modifier = marco.padding(relleno),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (conRotulo) rotulo()
            Box(Modifier.fillMaxWidth().heightIn(min = cifraMinima * ALTO_DEL_RENGLON_DE_LA_CIFRA))
            Box(Modifier.fillMaxWidth().heightIn(min = franjaReservada))
        }
        return
    }
    Layout(
        modifier = marco,
        content = {
            if (conRotulo) rotulo()
            CifraQueCabe(texto = monto, color = color, maxima = cifraMaxima, minima = cifraMinima)
            Box(
                modifier = Modifier
                    .testTag(FRANJA_TAG)
                    .then(if (puedeDesbordar) Modifier else Modifier.clipToBounds()),
                propagateMinConstraints = true
            ) { franja() }
        }
    ) { medibles, constraints ->
        val pad = relleno.roundToPx()
        val hueco = 4.dp.roundToPx()
        val ancho = constraints.maxWidth
        val alto = constraints.maxHeight
        val adentro = (ancho - pad * 2).coerceAtLeast(0)
        var i = 0
        val rotuloP = if (conRotulo) {
            medibles[i++].measure(
                Constraints(maxWidth = adentro)
            )
        } else {
            null
        }
        val cifraM = medibles[i++]
        val franjaM = medibles[i]
        val reservada = franjaReservada.roundToPx()
        // Lo que el mensaje necesita, preguntado sin medir: un medible sólo se
        // mide una vez, y el alto final depende de esta respuesta.
        val natural = maxOf(franjaM.maxIntrinsicHeight(adentro), reservada)
        val arribaDeLaCifra = pad + (rotuloP?.let { it.height + hueco } ?: 0)
        val cifraMin = (cifraMinima * ALTO_DEL_RENGLON_DE_LA_CIFRA).roundToPx()
        val paraLaCifra = alto - arribaDeLaCifra - hueco - natural - pad
        // Si no cabe ni con la cifra al mínimo, la franja se monta encima de
        // los sugeridos, y los tapa completos para que no asome medio chip.
        val altoDeLaFranja = when {
            paraLaCifra >= cifraMin -> natural
            puedeDesbordar -> maxOf(
                natural,
                alto - pad - arribaDeLaCifra - cifraMin - hueco + desborde.roundToPx()
            )
            // El saldo nuevo es un dato de cortesía: si no cabe entero, no se
            // pinta —ni a medias ni encima de los sugeridos—.
            else -> 0
        }
        val franjaP = franjaM.measure(Constraints.fixed(adentro, altoDeLaFranja))
        val altoDeLaCifra = if (altoDeLaFranja == 0) {
            (alto - arribaDeLaCifra - pad).coerceAtLeast(cifraMin)
        } else {
            paraLaCifra.coerceAtLeast(cifraMin)
        }
        val cifraP = cifraM.measure(Constraints.fixed(adentro, altoDeLaCifra))
        layout(ancho, alto) {
            rotuloP?.place(pad, pad)
            cifraP.place(pad, arribaDeLaCifra)
            if (franjaP.height > 0) franjaP.place(pad, arribaDeLaCifra + altoDeLaCifra + hueco)
        }
    }
}

/** Relleno de la tarjeta de la cifra. */
private val RELLENO_DE_LA_CIFRA: Dp = 8.dp

/** El renglón de la cifra mide 1.12 veces su tamaño. */
private const val ALTO_DEL_RENGLON_DE_LA_CIFRA = 1.12f

/**
 * **La cifra más grande que cabe**, entre [minima] y [maxima], a lo ancho y a lo
 * alto de su caja. Los tamaños son en **dp**, no en sp: la escala de letra ya
 * decidió cuánto espacio le toca a la cifra (vía [ZonaQueCede]); multiplicarla
 * otra vez la sacaría de su caja.
 *
 * Un monto **nunca se recorta**: si ni en [minima] cabe a lo ancho, se pinta en
 * [minima] y sin `maxLines`, que es la misma regla de `MspMoneyText`.
 */
@Composable
internal fun CifraQueCabe(
    texto: String,
    color: Color,
    maxima: Dp,
    minima: Dp,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val medidor = rememberTextMeasurer()
        val densidad = LocalDensity.current
        val base = MspTheme.type.amountHero
        val cajaAncho = constraints.maxWidth
        val cajaAlto = constraints.maxHeight
        val tamano = remember(texto, cajaAncho, cajaAlto, maxima, minima, densidad) {
            var dp = maxima
            while (dp > minima) {
                val sp = with(densidad) { dp.toSp() }
                val medida = medidor.measure(
                    text = texto,
                    style = base.copy(
                        fontSize = sp,
                        lineHeight = sp * ALTO_DEL_RENGLON_DE_LA_CIFRA
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                if (medida.size.width <= cajaAncho && medida.size.height <= cajaAlto) break
                dp -= 1.dp
            }
            dp.coerceAtLeast(minima)
        }
        val sp = with(densidad) { tamano.toSp() }
        Text(
            text = texto,
            style = base.copy(fontSize = sp, lineHeight = sp * ALTO_DEL_RENGLON_DE_LA_CIFRA),
            color = color,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// --- El botón "Foto" ---------------------------------------------------------------------

/**
 * **"Foto"**, al final de la fila del método (mock, sección 4). Acción chica
 * **sin caja**: el icono y su rótulo, con un área tocable de 56 × 56 dp.
 *
 * El número dice cuántos comprobantes van; si hubo un intento que no entró se
 * pinta ámbar (el mismo tono de su cuadro en la rejilla), y si solo hay
 * intentos fallidos dice "!". Sale con **cualquier** método, como salía la
 * sección de hoy (decisión del dueño, 2026-09-29).
 */
@Composable
internal fun BotonDeFoto(
    comprobantes: Int,
    intentosFallidos: Int,
    habilitado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    Box(
        modifier = modifier
            .size(TOQUE_DEL_ABONO)
            .clickable(enabled = habilitado, onClick = onClick)
            .testTag(FOTO_TAG),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = AccionesIconos.Camara,
                contentDescription = null,
                tint = if (habilitado) colors.onSurface else colors.onSurfaceMuted,
                modifier = Modifier.size(22.dp)
            )
            ConTopeDeLetra(1f) {
                Text(
                    text = "Foto",
                    style = MspTheme.type.captionStrong,
                    color = colors.onSurfaceMuted
                )
            }
        }
        if (comprobantes > 0 || intentosFallidos > 0) {
            val aviso = when (intentosFallidos) {
                0 -> null
                1 -> "Uno no entró"
                else -> "$intentosFallidos no entraron"
            }
            ConTopeDeLetra(1f) {
                Text(
                    text = if (comprobantes > 0) comprobantes.toString() else "!",
                    style = MspTheme.type.captionStrong,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .testTag(FOTO_CONTADOR_TAG)
                        .semantics { if (aviso != null) stateDescription = aviso }
                        .background(
                            if (aviso != null) colors.statusPartial else colors.brand,
                            CircleShape
                        )
                        .heightIn(min = 18.dp)
                        .padding(horizontal = 5.dp)
                )
            }
        }
    }
}
