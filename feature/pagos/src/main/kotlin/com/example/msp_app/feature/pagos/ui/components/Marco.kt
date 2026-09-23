package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del botón "atrás". */
const val ATRAS_TAG: String = "pagos_atras"

/** `testTag` del CTA primario del dock. */
const val CTA_PRIMARIO_TAG: String = "pagos_cta_primario"

/** `testTag` del CTA de visita del dock. */
const val CTA_VISITA_TAG: String = "pagos_cta_visita"

/** `testTag` del botón de Notas del dock — el tercer espacio del detalle de cliente. */
const val CTA_NOTAS_TAG: String = "pagos_cta_notas"

/**
 * `testTag` del botón de Condonar del dock — el tercer espacio del detalle de
 * venta, hermano de [CTA_NOTAS_TAG] en el detalle de cliente. Ver [DockDeAcciones].
 */
const val CTA_CONDONAR_TAG: String = "pagos_cta_condonar"

/**
 * `testTag` del distintivo del botón de Notas: el punto que dice que esa puerta
 * tiene algo anotado. Aparte del botón porque el botón existe SIEMPRE y el punto
 * no — un test que sólo mirara el botón no podría distinguir los dos estados.
 */
const val DISTINTIVO_DE_NOTAS_TAG: String = "pagos_cta_notas_distintivo"

/** Alto mínimo tocable. El plan pide >=50px; el design system ya pide 56dp. */
private val TOQUE = 56.dp

/** El diámetro del punto del distintivo, y también su desplazamiento. */
private val PUNTO = 8.dp

/**
 * Cuánto más ancho es el CTA que cada acción, en una sola fila.
 *
 * Era 1.7 —el reparto del mock— con DOS celdas. Con tres, 1.7 deja al CTA en
 * 143 dp y "Registrar abono" se parte en dos renglones; con 2.0 quedan 156 y
 * entra en uno, mientras "Visita" y "Notas" se quedan con 78 cada una y también
 * entran. Medido en `pagos_cliente_light_1_0`, no supuesto.
 *
 * Sólo aplica a escala NORMAL: a las grandes el CTA se lleva el renglón entero
 * porque ningún reparto alcanza. Ver el comentario de [DockDeAcciones].
 */
private const val PESO_DEL_CTA = 2.0f

/**
 * La fila de navegación del mock (`.nav`): solo "atrás".
 *
 * **No lleva "⋯".** El `.nav` del mock trae atrás/ojo/menú, no el "⋯" — ese vive
 * en el dock y en ningún otro lugar. Tenerlo aquí también hacía dos puertas a la
 * condonación, que el diseño no tiene, y ponía el mismo `testTag` en dos nodos
 * compuestos a la vez (`onNodeWithTag` truena por ambigüedad).
 *
 * El ojo de privacidad y el menú del mock no se cablean aquí: el primero es una
 * preferencia global que hoy vive en el reporte de cobranza y el segundo es
 * navegación de la app, ninguno propiedad de esta pantalla.
 *
 * ## El hueco de la derecha es una sede de cero dp verticales
 *
 * Esta fila mide 56dp por el botón redondo y **le sobran ~280dp de ancho**. Es
 * el mismo hallazgo con el que la Task 22 colgó la cámara de la fila del método
 * de cobro: un afordante que viaja con una fila que ya existía **no cuesta un
 * solo dp de alto**, y en esta pantalla el alto es lo escaso — abajo está el
 * dinero, que es por lo que el cobrador la abrió.
 *
 * [accion] es opcional a propósito: el detalle de VENTA la deja en `null` y su
 * barra queda exactamente como estaba (sus goldens no se mueven, y ése es el
 * control positivo de que este cambio solo toca la pantalla de cliente).
 * Cualquier cosa que se cuelgue aquí debe respetar `maxLines = 1`: dos
 * renglones harían crecer la fila y el afordante dejaría de ser gratis.
 */
@Composable
fun BarraDeDetalle(
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    accion: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotonCircular(
            icono = Icons.Filled.ArrowBack,
            descripcion = "Atrás",
            onClick = onAtras,
            modifier = Modifier.testTag(ATRAS_TAG)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = MspTheme.spacing.sm),
            contentAlignment = Alignment.CenterEnd
        ) {
            accion?.invoke()
        }
    }
}

@Composable
private fun BotonCircular(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(TOQUE),
        shape = MspTheme.shapes.chip,
        color = MspTheme.colors.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icono,
                contentDescription = descripcion,
                tint = MspTheme.colors.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * El dock del mock (`.dock`): CTA primario en `brand` (nunca en verde — el
 * verde `statusPaid` es solo estado) y acción de visita en superficie.
 *
 * **El slot primario es [MspPrimaryFieldButton], no [BotonDelDock]
 * (corrección de la ronda 1).** Se pintaba a mano con la receta exacta del
 * componente compartido —`heightIn(min = 56dp)` + `shapes.button` +
 * `type.buttonLarge` + `brand`/`onBrand`— y al hacerlo se perdía lo que no es
 * cosmético: **el haptic**. El CTA de esta pantalla es "Abonar $NNN", una
 * acción de dinero, y el design system lo declara regla dura — *"las acciones
 * de dinero deben sentirse físicas"* (spec §8.4, KDoc de
 * `PrimaryFieldButton`): cada tap dispara `HapticFeedbackType.LongPress`. Con
 * el `Surface` local no vibraba. También llegan la sombra de 8dp tintada a
 * marca y el estado apagado plano del sistema.
 *
 * [BotonDelDock] se queda para el otro slot, que **no** es una variante del
 * compartido: va relleno de `surface` y `MspPrimaryFieldButton` solo ofrece
 * `Primary` (fill marca), `Danger` (fill rojo) y `Ghost` (outline sin
 * relleno). Forzar `Ghost` cambiaría el peso visual del dock.
 *
 * ## El tercer espacio: **Notas**, ya sin el "⋯"
 *
 * Aquí vivía el "⋯" que abría la pantalla legada — la condonación, el mapa de
 * la venta, los productos y el historial completo. El dueño lo quitó ("no
 * quiero volver a verla nunca más"): "Ver los N abonos", dentro de la línea de
 * tiempo, sigue llevando a esa misma pantalla, así que quitar el botón no le
 * cerró ninguna función al cobrador, sólo esta puerta directa.
 *
 * El hueco que dejó es donde viven las **Notas de la puerta**
 * ([AccionDeNotas]), opcional y `null` cuando no existe. Estaban en la fila de
 * iconos de la hoja de identidad del detalle de cliente, cuatro acciones abajo
 * del pliegue; el dueño pidió que se noten. A escala NORMAL cuestan **cero dp
 * verticales** —el dock ya existe y le sobraba una celda— y ganan el
 * distintivo, que en la fila de iconos no cabía.
 *
 * ## A escala grande el dock se apila, y eso SÍ toca a la venta
 *
 * El reparto en una fila es del mock y funciona a 1.0. A 2.0 no: medido en el
 * golden, el texto se partía a mitad de palabra y el dock crecía hasta comerse
 * el saldo. La salida es la del principio 9 —**apilar antes de partir**— y se
 * aplica al dock, no a una pantalla, así que el detalle de VENTA también se
 * apila a escalas grandes. Sus goldens de 1.5 y 2.0 cambian; los de 1.0, no.
 *
 * Se decidió así a propósito en vez de apilar sólo cuando hay dos celdas: el
 * mismo control no puede acomodarse distinto en dos pantallas de la misma app
 * —es el argumento con el que se unificó la reveal del tema—, y la venta tenía
 * el mismo defecto de texto partido.
 *
 * [notas] es `AccionDeNotas?` / objeto opcional y no un `Boolean` aparte a
 * propósito: con dos parámetros se puede pedir el botón sin darle a dónde ir, y
 * el síntoma sería un control que se ve, se toca y no hace nada. Así el tipo no
 * deja escribir ese estado.
 *
 * ## [condonar] — el tercer espacio del detalle de VENTA
 *
 * Hermano de [notas] y con el mismo criterio: `null` es "esta pantalla no
 * ofrece condonar" y una lambda es la puerta. El detalle de cliente lo deja en
 * `null` —condonar es de una CUENTA, no de una persona— y el de venta es quien
 * lo manda siempre, sin depender de que haya oferta de liquidación: son dos
 * acciones de dinero distintas (`"una cosa es usar el botón de 'usar' para
 * aplicar el pago y otra cosa es para condonar todo el resto que sobra"`) y la
 * condonación tiene que poder alcanzarse aunque esta cuenta no tenga liquidación
 * vigente hoy.
 *
 * Reusa [BotonDelDock] con `relleno = surface`, igual que [notas] — nunca un
 * fill rojo de `MspPrimaryFieldButton`: forzar `Danger` ahí rompería el mismo
 * peso visual que el KDoc de arriba ya protege para el resto del dock. El
 * riesgo de la acción lo dice el color del CONTENIDO (`danger`), no el relleno
 * del botón — el mismo idioma que ya usa el distintivo de "Con advertencia".
 */
@Composable
fun DockDeAcciones(
    textoPrimario: String,
    onPrimario: () -> Unit,
    onVisita: () -> Unit,
    modifier: Modifier = Modifier,
    notas: AccionDeNotas? = null,
    condonar: (() -> Unit)? = null
) {
    // **Apilar antes de partir** (principio 9). Con tres celdas en una sola fila,
    // a escala 2.0 los 360 dp dejan ~74 dp por botón y el texto se rompe A MITAD
    // DE PALABRA: medido en el golden `pagos_cliente_light_2_0`, decía
    // "Registr/ar/abono", "Visit/a" y "Nota/s", y el dock crecía tanto que se
    // comía el saldo — que es por lo que el cobrador abrió la pantalla.
    //
    // Así que a las escalas grandes el CTA se queda con un renglón entero y las
    // acciones bajan al siguiente. Cuesta un renglón de alto, que es lo que ya
    // costaba el texto partido, y a cambio no hay una sola palabra rota. A
    // NORMAL no se toca nada: los goldens de 1.0 se quedan como estaban.
    val apilado = LocalFontSizeLevel.current != FontSizeLevel.NORMAL
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
                // A escalas grandes el aire del dock se aprieta: cada dp que se
                // queda acá es un dp que le quita al saldo, y el saldo es por lo
                // que el cobrador abrió la pantalla.
                .padding(if (apilado) MspTheme.spacing.sm else MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MspPrimaryFieldButton(
                    text = textoPrimario,
                    onClick = onPrimario,
                    modifier = Modifier
                        .weight(if (apilado) 1f else PESO_DEL_CTA)
                        .testTag(CTA_PRIMARIO_TAG)
                )
                if (!apilado) AccionesDelDock(onVisita, notas, condonar)
            }
            if (apilado) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AccionesDelDock(onVisita, notas, condonar)
                }
            }
        }
    }
}

/**
 * Las celdas que acompañan al CTA. Extraídas porque viven en la misma fila que
 * él a escala normal y en la de abajo a las grandes, y repetirlas en los dos
 * lugares era la forma segura de que una ganara un botón y la otra no.
 */
@Composable
private fun RowScope.AccionesDelDock(
    onVisita: () -> Unit,
    notas: AccionDeNotas?,
    condonar: (() -> Unit)? = null
) {
    BotonDelDock(
        texto = "Visita",
        relleno = MspTheme.colors.surface,
        contenido = MspTheme.colors.onSurface,
        onClick = onVisita,
        modifier = Modifier
            .weight(1f)
            .testTag(CTA_VISITA_TAG)
    )
    if (condonar != null) {
        BotonDelDock(
            texto = "Condonar",
            relleno = MspTheme.colors.surface,
            contenido = MspTheme.colors.danger,
            onClick = condonar,
            modifier = Modifier
                .weight(1f)
                .testTag(CTA_CONDONAR_TAG)
        )
    }
    if (notas != null) {
        BotonDelDock(
            texto = "Notas",
            relleno = MspTheme.colors.surface,
            contenido = if (notas.advierte) {
                MspTheme.colors.danger
            } else {
                MspTheme.colors.onSurface
            },
            onClick = notas.onAbrir,
            distintivo = when {
                notas.advierte -> MspTheme.colors.danger to "Con advertencia"
                notas.conContenido -> MspTheme.colors.brand to "Con notas"
                else -> null
            },
            modifier = Modifier
                .weight(1f)
                .testTag(CTA_NOTAS_TAG)
        )
    }
}

/**
 * Un botón del dock. [distintivo] en `null` es el caso normal; con color, pinta
 * el punto de [PuntoDelDistintivo].
 */
@Composable
private fun BotonDelDock(
    texto: String,
    relleno: Color,
    contenido: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    distintivo: Pair<Color, String>? = null
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = TOQUE),
        shape = MspTheme.shapes.button,
        color = relleno
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = texto,
                style = MspTheme.type.buttonLarge,
                color = contenido,
                modifier = Modifier.padding(horizontal = MspTheme.spacing.sm)
            )
            distintivo?.let { (color, descripcion) -> PuntoDelDistintivo(color, descripcion) }
        }
    }
}

/**
 * **El punto que dice que esa puerta tiene algo anotado.**
 *
 * Existe porque hoy **no se puede saber si una puerta tiene notas sin abrirlas**:
 * el botón se ve igual con la puerta en blanco y con *"hay perro"* escrito
 * adentro, así que el cobrador tendría que abrir todas para enterarse de las
 * pocas que importan — y no lo va a hacer.
 *
 * Va **encima** del texto, en la esquina, y no al lado: al lado empujaría el
 * texto y el botón cambiaría de ancho según el contenido de las Notas, que es
 * justo lo que un dock de tres celdas con `weight` no puede permitirse.
 *
 * **El color lo decide el peso, no el conteo.** `danger` cuando hay una señal que
 * advierte —*"hay perro"*, *"no ir solo"*— y marca cuando sólo hay contexto. Es
 * la misma regla que ya usan los chips y la pastilla de la barra: el rojo de esta
 * app significa riesgo para quien toca la puerta, y nunca "hay muchos datos".
 *
 * **Y el color no viaja solo.** Un punto de 8 dp que sólo cambia de tono entre
 * "hay algo" y "hay una advertencia" deja el significado íntegramente en el
 * color, que es lo que el resto de esta pantalla ya decidió no hacer —el anillo
 * del radio, el borde del chip elegido—. Aquí el portador que acompaña es la
 * descripción: TalkBack dice *"Con advertencia"* o *"Con notas"*, y es lo mismo
 * que la prueba puede afirmar sin leer píxeles.
 */
@Composable
private fun BoxScope.PuntoDelDistintivo(color: Color, descripcion: String) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            // Hacia ADENTRO, no hacia afuera. El `Surface` recorta con
            // `shapes.button`, así que un punto desplazado fuera del borde se
            // recorta entero y no se ve — se midió: el nodo existía en el árbol
            // y `assertIsDisplayed` daba falso.
            .offset(x = -PUNTO, y = PUNTO)
            .size(PUNTO)
            .clip(MspTheme.shapes.chip)
            .background(color)
            .semantics { contentDescription = descripcion }
            .testTag(DISTINTIVO_DE_NOTAS_TAG)
    )
}

/**
 * **El botón de Notas del dock**, con lo que hace falta para pintar su
 * distintivo.
 *
 * Es un objeto y no tres parámetros sueltos por lo mismo que `AccionesDeLaFicha`
 * en la pantalla: [DockDeAcciones] ya recibe suficientes, y detekt corta en
 * siete. Además así el tipo no deja pedir el botón sin decir a dónde va —el
 * mismo criterio con el que [DockDeAcciones] hizo opcional el "⋯".
 *
 * `null` en el dock significa que esta pantalla no tiene notas: el detalle de
 * VENTA lo deja así y su dock queda exactamente como estaba.
 */
@Immutable
data class AccionDeNotas(
    val onAbrir: () -> Unit,
    /** Hay al menos una señal marcada o una nota escrita. */
    val conContenido: Boolean,
    /** Alguna de las señales marcadas es de las que advierten. */
    val advierte: Boolean
)
