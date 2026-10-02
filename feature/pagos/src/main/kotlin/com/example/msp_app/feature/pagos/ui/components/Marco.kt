package com.example.msp_app.feature.pagos.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspBackdrop
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.MspSoftEdgeActionBar
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion

/** `testTag` del botón "atrás". */
const val ATRAS_TAG: String = "pagos_atras"

/** `testTag` del CTA primario del dock. */
const val CTA_PRIMARIO_TAG: String = "pagos_cta_primario"

/**
 * `testTag` del dock ENTERO — la banda que de verdad tapa lo que hay debajo.
 *
 * No es lo mismo que [CTA_PRIMARIO_TAG] y la diferencia importa: entre el canto
 * de la banda y el botón hay un hairline y el padding de la columna
 * (`spacing.md` a `NORMAL`), o sea **17 dp** que el botón no cubre pero la banda
 * sí. Medir contra el botón daba verde con el `$3,550` cortado, que es lo que el
 * golden `pagos_cliente_light_1_0` estuvo enseñando mientras el test decía que
 * el dinero cabía. La línea de flotación es ésta.
 */
const val DOCK_DE_ACCIONES_TAG: String = "pagos_dock"

/**
 * `testTag` de la barra COMPLETA, degradado incluido.
 *
 * Aparte de [DOCK_DE_ACCIONES_TAG] y la diferencia es exactamente el punto del
 * rediseño: desde que la barra se disuelve hacia arriba, "donde empieza la
 * barra" y "donde la barra tapa" dejaron de ser el mismo renglón. El degradado
 * arranca transparente y **el contenido de atrás se ve a través de él a
 * propósito**; lo que de verdad esconde es el tramo sólido, que empieza en el
 * 44 % de esta banda.
 *
 * Medir el dinero contra este nodo sería la lectura dura y dejaría el fondo del
 * mapa en menos de lo que mide hoy. La línea de flotación del saldo es el punto
 * opaco — ver `ElDineroNoSeMeteBajoLaBarraTest`, que lo calcula con
 * `MSP_SOFT_EDGE_SOLID_STOP` en vez de con un número a mano.
 */
const val BARRA_BLANDA_TAG: String = "pagos_dock_barra"

/**
 * `testTag` del hueco que el contenido reserva abajo, del alto exacto de la
 * barra.
 *
 * Existe **para las pruebas**, y la razón no es cosmética: desde que la barra
 * está ENCIMA del contenido, `performScrollTo()` ya no garantiza que el nodo se
 * pueda tocar — deja el nodo dentro de la ventana, y la ventana ahora llega
 * hasta abajo del todo, o sea **por detrás de los botones**. Un test que
 * desplace hasta este hueco deja todo el contenido restante arriba de la barra,
 * porque este hueco mide exactamente lo que la barra tapa.
 */
const val AIRE_DEL_DOCK_TAG: String = "pagos_dock_aire"

/** `testTag` del CTA de visita del dock. */
const val CTA_VISITA_TAG: String = "pagos_cta_visita"

/** `testTag` del botón de Notas del dock — el tercer espacio del detalle de cliente. */
const val CTA_NOTAS_TAG: String = "pagos_cta_notas"

/** El botón "Cond." del dock: sólo el detalle de VENTA lo monta (decisión del dueño, 2026-10-01). */
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
 * **El aire entre el final del degradado y el primer botón del dock: 4 dp.**
 *
 * No es el relleno de los otros tres lados y no debería serlo. Arriba del dock
 * no hay contenido del que separarse: hay una banda que ya cerró a sólido, y
 * cada dp que se le suma acá es un dp de fondo liso — negro o blanco según el
 * tema— que sube el borde visible del bloque sin dar nada a cambio.
 *
 * Cuatro y no cero porque el canto del degradado y el canto del botón **no
 * pueden coincidir**: pegados, la esquina redonda del CTA se recorta contra el
 * punto más opaco de la rampa y se lee como un error de dibujo.
 */
private val AIRE_SOBRE_EL_DOCK: Dp = 4.dp

/**
 * **Cuánto sigue bajando la rampa después de que el fade terminó: 16 dp.**
 *
 * O sea: el degradado **no** cierra donde empieza el bloque de botones, sino
 * 16 dp más abajo — ya metido en ellos.
 *
 * ## Por qué esto, y no una banda translúcida
 *
 * El intento anterior fue dejar toda la meseta a media tinta para que se viera
 * lo de atrás entre los botones. El dueño lo descartó en dos pasos el mismo día.
 * Primero no se veía —a 0.88 sobre una paleta OLED, el 12 % que pasaba daba
 * `(3,4,3)`, medido en su captura— y al bajarlo a 0.60 dijo lo que de verdad
 * quería: *"no quiero que todo se traslucide, sólo la parte de arriba mientras
 * se difumina, que el difuminado termine hasta un poco abajo del punto más alto
 * de los botones"*.
 *
 * Son dos cosas distintas y conviene no volver a confundirlas:
 *
 *  - **meseta translúcida** = la banda entera deja ver, de arriba abajo. Es lo
 *    que hace el telón sobre el mapa, y acá está **descartado**.
 *  - **solape** = la banda cierra a sólido igual que siempre, pero unos dp más
 *    tarde. Lo único translúcido es el tramo de rampa que cae sobre los botones,
 *    y como ellos son opacos, se ve sólo en los huecos horizontales y sólo en su
 *    parte de arriba. Es esto.
 *
 * ## Por qué 16
 *
 * El CTA mide ~68 dp, así que 16 son su cuarto superior: bastante para que el
 * canto de la banda deje de coincidir con el canto del botón —que es lo que la
 * hacía leerse alta por más dp que se le quitaran— y poco para que el texto de
 * las etiquetas quede entero sobre fondo sólido.
 */
private val SOLAPE_DEL_DOCK: Dp = 16.dp

/**
 * **Cuánto se levanta el color de la banda por encima del fondo de la página:
 * 35 % del camino hacia `surface`.**
 *
 * ## El problema que resuelve es del tema, no del diseño
 *
 * La paleta oscura de esta app es OLED pura: `background = #000000`. Con la
 * banda pintada de ese mismo negro, **un hueco sin nada detrás y un degradado ya
 * cerrado dan el mismo píxel** — medido el 2026-09-25 en los huecos entre los
 * botones del dueño, `(0,0,0)` en los dos casos. No hay velo ni solape que
 * arregle eso: no es que el efecto falle, es que no hay nada que distinguir.
 *
 * A 0.35 la banda queda en **`#070908`** en oscuro y en **`#F8FAF9`** en claro:
 * se lee como un panel apoyado sobre la página en vez de como la página misma, y
 * en oscuro eso basta para que el bloque tenga borde propio aunque detrás no
 * pase contenido.
 *
 * ## Por qué NO más
 *
 * Porque los botones secundarios —`Visita` y el `⋮`— se pintan con
 * `colors.surface` (`#141917` en oscuro), que es justamente el destino de esta
 * interpolación. Llevar la banda más cerca de `surface` los borra: el botón y su
 * fondo se vuelven el mismo color y el dock se convierte en una mancha con
 * texto. A 0.35 queda la mitad del recorrido entre los dos, que es el contraste
 * que separa el botón de su banda.
 */
private const val ELEVACION_DEL_DOCK = 0.35f

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
 * ## [unaSolaFila] — el detalle de VENTA no se apila
 *
 * Decisión del dueño sobre el mock `detalle-de-venta-final.html`: en la venta
 * el dock es siempre UNA fila —Abonar | Visita | ⋯— también a letra grande,
 * porque su CTA pasa de "Abonar $220" a "Abonar" y entonces sí cabe. A las
 * escalas grandes "Visita" mide su palabra (sin `weight`) y el CTA toma el
 * resto, con el margen lateral en 8 dp como ya hacía el dock apilado. El
 * detalle de cliente lo deja en `false` y su dock no cambia.
 */
@Composable
fun DockDeAcciones(
    textoPrimario: String,
    onPrimario: () -> Unit,
    onVisita: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: MspBackdrop? = null,
    menu: MenuDelDock? = null,
    notas: AccionDeNotas? = null,
    unaSolaFila: Boolean = false,
    /**
     * Condonar como botón del dock, abreviado "Cond.". Lo usa sólo el detalle de
     * VENTA, donde Condonar era lo único del "⋯": con el botón a la vista el menú
     * queda vacío y no se pinta (decisión del dueño, 2026-10-01). El detalle de
     * cliente lo sigue llevando en el menú.
     */
    onCondonar: (() -> Unit)? = null
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
    val letraGrande = LocalFontSizeLevel.current != FontSizeLevel.NORMAL
    val apilado = letraGrande && !unaSolaFila
    val conMenu = menu != null && !menu.vacio
    var abierto by rememberSaveable { mutableStateOf(false) }
    // El menú se cierra solo si la pantalla deja de ofrecerlo: un menú abierto
    // sobre un dock que ya no lo tiene sería un velo que nadie puede quitar.
    if (!conMenu && abierto) abierto = false
    val sinMovimiento = rememberMspReducedMotion()
    // Con el menú abierto, atrás lo cierra — el mismo gesto que el velo. Sin
    // esto el botón de Android se lo llevaba el `NavHost` y salía de la
    // pantalla con el menú todavía desplegado. Ver `AtrasCierraLasHojasTest`.
    BackHandler(enabled = abierto) { abierto = false }
    Box(modifier = modifier.fillMaxSize()) {
        if (abierto) {
            VeloDelMenu(onCerrar = { abierto = false }, modifier = Modifier.matchParentSize())
        }
        MspSoftEdgeActionBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag(BARRA_BLANDA_TAG),
            backdrop = backdrop,
            // **El difuminado termina metido en los botones, no encima de
            // ellos.** Ver [SOLAPE_DEL_DOCK].
            solape = SOLAPE_DEL_DOCK,
            // **La banda no es del color de la página: está un punto por
            // encima.** Ver [ELEVACION_DEL_DOCK].
            tinte = lerp(
                MspTheme.colors.background,
                MspTheme.colors.surface,
                ELEVACION_DEL_DOCK
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // A escalas grandes el aire del dock se aprieta: cada dp que se
                    // queda acá es un dp que le quita al saldo, y el saldo es por lo
                    // que el cobrador abrió la pantalla.
                    .padding(
                        start = if (letraGrande) MspTheme.spacing.sm else MspTheme.spacing.md,
                        end = if (letraGrande) MspTheme.spacing.sm else MspTheme.spacing.md,
                        bottom = if (letraGrande) MspTheme.spacing.sm else MspTheme.spacing.md,
                        // **Arriba NO lleva el mismo relleno, y ésta es la razón.**
                        //
                        // Este relleno se suma al `fade` de la barra, que ya
                        // termina de cerrar justo donde empieza esta columna. O
                        // sea que eran 16 dp de **fondo plano** metidos entre el
                        // punto donde el degradado cerró y el primer botón: no
                        // separaban de nada, porque lo de arriba ya está tapado.
                        //
                        // El dueño lo vio en el aparato el 2026-09-25: *"ese
                        // elemento hazlo no tan alto, porque arriba deja mucho
                        // espacio en negro o blanco dependiendo del tema"*. Medido
                        // sobre su captura, la franja de fondo liso encima del CTA
                        // era de ~26 dp y 16 de esos eran esto.
                        //
                        // Quedan [AIRE_SOBRE_EL_DOCK] para que el botón no nazca
                        // pegado al canto del degradado. La separación de verdad
                        // la da el fade, que es exactamente su trabajo.
                        top = AIRE_SOBRE_EL_DOCK
                    )
                    .testTag(DOCK_DE_ACCIONES_TAG),
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                if (conMenu) {
                    HojaDelMenu(
                        menu = menu,
                        abierto = abierto,
                        sinMovimiento = sinMovimiento,
                        onCerrar = { abierto = false }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MspPrimaryFieldButton(
                        text = textoPrimario,
                        onClick = onPrimario,
                        modifier = Modifier
                            .weight(if (apilado || letraGrande) 1f else PESO_DEL_CTA)
                            .testTag(CTA_PRIMARIO_TAG),
                        maxLines = if (unaSolaFila) 1 else Int.MAX_VALUE
                    )
                    if (!apilado) {
                        AccionesDelDock(onVisita, notas, onCondonar, visitaASuPalabra = letraGrande)
                        if (conMenu) {
                            BotonDelMenu(
                                abierto = abierto,
                                sinMovimiento = sinMovimiento,
                                onClick = { abierto = !abierto },
                                modifier = Modifier.testTag(MENU_DEL_DOCK_TAG)
                            )
                        }
                    }
                }
                if (apilado) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccionesDelDock(onVisita, notas, onCondonar)
                        if (conMenu) {
                            BotonDelMenu(
                                abierto = abierto,
                                sinMovimiento = sinMovimiento,
                                onClick = { abierto = !abierto },
                                modifier = Modifier.testTag(MENU_DEL_DOCK_TAG)
                            )
                        }
                    }
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
    onCondonar: (() -> Unit)?,
    visitaASuPalabra: Boolean = false
) {
    BotonDelDock(
        texto = "Visita",
        relleno = MspTheme.colors.surface,
        contenido = MspTheme.colors.onSurface,
        onClick = onVisita,
        modifier = (if (visitaASuPalabra) Modifier else Modifier.weight(1f))
            .testTag(CTA_VISITA_TAG),
        unRenglon = visitaASuPalabra
    )
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
    if (onCondonar != null) {
        // Mismo trato que "Visita": a letra grande va a su palabra y en un
        // renglón, para no partirse.
        BotonDelDock(
            texto = "Cond.",
            relleno = MspTheme.colors.surface,
            contenido = MspTheme.colors.onSurface,
            onClick = onCondonar,
            modifier = (if (visitaASuPalabra) Modifier else Modifier.weight(1f))
                .semantics { contentDescription = "Condonar" }
                .testTag(CTA_CONDONAR_TAG),
            unRenglon = visitaASuPalabra
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
    distintivo: Pair<Color, String>? = null,
    unRenglon: Boolean = false
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
                maxLines = if (unRenglon) 1 else Int.MAX_VALUE,
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
