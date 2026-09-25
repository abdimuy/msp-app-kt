package com.example.msp_app.feature.pagos.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MSP_SOFT_EDGE_SOLID_STOP
import com.example.msp_app.core.designsystem.component.MspBackdrop
import com.example.msp_app.core.designsystem.component.mspBackdropCabecera
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del velo que hace legible la barra de estado sobre el mapa. */
const val VELO_DE_LA_BARRA_TAG: String = "pagos_cliente_velo_barra"

/** `testTag` de la fila flotante de controles — ojo, tema y la pastilla de la ficha. */
const val CONTROLES_FLOTANTES_TAG: String = "pagos_cliente_controles"

/**
 * **El velo de la barra de estado: fuerte arriba, desvanecido hacia abajo.**
 *
 * ## Por qué hace falta
 *
 * Con el mapa sangrando hasta `y = 0` —pantalla a borde completo—, la hora y
 * los iconos del sistema quedan **escritos sobre teselas**. Sobre una calle
 * clara, texto blanco sin nada detrás desaparece; sobre un parque oscuro, texto
 * negro hace lo mismo. Un velo uniforme resolvería eso y crearía otro problema:
 * **una línea visible donde termina**. Por eso el degradado, con el mismo
 * mecanismo de tres paradas de la barra de abajo, del derecho: sólido arriba,
 * transparente al final.
 *
 * ## Lo que un app NO puede hacer, y conviene que quede escrito
 *
 * El encargo pedía que *"la hora y los iconos lleven sombra suave"*. **No se
 * puede**: esos glifos los dibuja SystemUI en su propia ventana y una app no
 * tiene manera de ponerles sombra —no hay API—. Lo único que está de este lado
 * es qué se pinta **detrás** de ellos, que es este velo, y el ajuste de
 * apariencia clara/oscura que ya hace `enableEdgeToEdge()`. El velo consigue el
 * mismo efecto que la sombra buscaba: que el glifo tenga contra qué contrastar.
 *
 * Se deja dicho para que el próximo que lea el mock no lo intente otra vez.
 *
 * ## El desenfoque entra por [backdrop], y sólo en Android 12+
 *
 * La capa borrosa la dibuja el fondo del mapa, no este velo —ver
 * `mspBackdropSource`—; acá sólo se declara **cuánto alto** ocupa, para que la
 * tira se grabe del tamaño exacto. Sin soporte queda el degradado, que es el
 * respaldo y se ve bien igual.
 */
@Composable
fun VeloDeLaBarraDeEstado(modifier: Modifier = Modifier, backdrop: MspBackdrop? = null) {
    val inset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fondo = MspTheme.colors.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Sube hasta `y = 0`: el `Box` padre consume el inset para que
            // ningún CONTROL quede bajo la ventana del sistema —lo exige
            // `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`— y este
            // desplazamiento negativo es cómo el velo, que no es un control,
            // llega igual al borde.
            .offset(y = -inset)
            .height(inset + COLA_DEL_VELO)
            .mspBackdropCabecera(backdrop)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to fondo.copy(alpha = TINTA_DEL_VELO),
                        1f - MSP_SOFT_EDGE_SOLID_STOP to fondo.copy(alpha = TINTA_DEL_VELO),
                        1f to fondo.copy(alpha = 0f)
                    )
                )
            )
            .testTag(VELO_DE_LA_BARRA_TAG)
    )
}

/**
 * **Los controles que flotan sobre el mapa, arriba a la derecha.**
 *
 * El ojo de privacidad y el sol/luna se quedan ahí porque al dueño le gustan
 * ahí. Lo que cambió es que ya **no viajan con el contenido**: el renglón del
 * nombre se fue al telón, así que estos dos perdieron la fila que los llevaba y
 * pasan a ser una capa fija.
 *
 * Que sean fijos tiene una consecuencia buena y una que hay que vigilar. La
 * buena: se alcanzan sin desplazar, siempre. La que hay que vigilar: **flotan
 * sobre teselas**, así que cada uno lleva su propio relleno opaco —lo trae el
 * componente del design system— y la fila entera va dentro del inset de la
 * barra de estado, que es lo que impide que el sistema se coma sus taps.
 *
 * La pastilla de la ficha viaja con ellos **sólo cuando tiene algo que gritar**
 * —una advertencia, o una ficha ilegible—. Es la misma regla de siempre y el
 * mismo motivo: *"hay perro"* tiene que llegarle al cobrador antes de que abra
 * el portón, y desde que el nombre se fue al telón, ésta es la única fila que
 * se ve sin desplazar.
 */
@Composable
fun ControlesFlotantes(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .padding(end = MspTheme.spacing.md, top = MspTheme.spacing.sm)
            .testTag(CONTROLES_FLOTANTES_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        contenido()
    }
}

/**
 * Un control flotante, con su propio suelo opaco.
 *
 * Sin esto, un icono de trazo sobre una calle clara del mapa se pierde igual
 * que los glifos del sistema. El relleno es `surface` con la forma de control
 * del tema, o sea el mismo suelo que tendría dentro de una tarjeta — lo que
 * cambia es que acá no hay tarjeta debajo.
 */
@Composable
fun SueloDelControlFlotante(modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(MspTheme.shapes.control)
            .background(MspTheme.colors.surface.copy(alpha = SUELO_DEL_CONTROL))
    ) {
        contenido()
    }
}

/**
 * **Cuánto desenfoca el velo de la barra de estado: 7 dp.**
 *
 * Eran 14, y con ese radio el mapa dejaba de reconocerse: una mancha de color no
 * es *"el mapa desenfocado"*, es otra cosa. Con la mitad se lee el esmerilado y
 * las calles siguen ahí.
 */
val RADIO_DEL_VELO: Dp = 7.dp

/**
 * **Cuánto desenfoca el telón del nombre: 9 dp.**
 *
 * Un punto más que el velo porque acá encima va texto y el fondo tiene que
 * aquietarse un poco más para que el nombre no compita con una calle; pero
 * sigue siendo *"sólo un poco de blur"*, que es lo que el dueño pidió.
 */
val RADIO_DEL_TELON: Dp = 9.dp

/** `true` cuando el teléfono puede desenfocar lo que pasa por detrás. */
internal val HAY_DESENFOQUE: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Cuánto baja el velo por debajo del inset antes de desvanecerse.
 *
 * Es lo que evita la línea: si el degradado cerrara exactamente en el borde del
 * inset, el ojo vería el canto ahí. Bajando [COLA_DEL_VELO] más, el tramo
 * transparente cae sobre mapa y no sobre el borde.
 */
private val COLA_DEL_VELO: Dp = 28.dp

/**
 * Qué tan opaco llega a ser el velo en su parte alta: **0.22**.
 *
 * Eran 0.72, y a esa tinta el velo dejaba de ser velo: tapaba el mapa. El dueño
 * lo reportó mirando el aparato —*"no se ve el mapa de atrás, ni en el nombre ni
 * en las notificaciones"*—. Lo que hace legibles la hora y los iconos no es la
 * opacidad sino el **desenfoque** de lo que pasa por detrás, que rompe el
 * contraste de las calles sin borrarlas; el tinte sólo tiene que empujar un
 * poco. A 0.22 el mapa se sigue distinguiendo.
 */
private const val TINTA_DEL_VELO = 0.22f

/** Qué tan opaco es el suelo de un control flotante. */
private const val SUELO_DEL_CONTROL = 0.88f
