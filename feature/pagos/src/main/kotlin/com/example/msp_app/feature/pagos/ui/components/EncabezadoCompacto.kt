package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspBackdrop
import com.example.msp_app.core.designsystem.component.MspSoftEdgeTopBar
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del encabezado compacto que entra cuando el fondo terminó de irse. */
const val ENCABEZADO_COMPACTO_TAG: String = "pagos_cliente_encabezado_compacto"

/**
 * **El encabezado que entra cuando el mapa termina de irse.**
 *
 * Mientras el fondo se ve, el nombre y la dirección viven donde siempre: dentro
 * del contenido, en la primera tarjeta. Cuando el fondo se apagó, esos dos datos
 * ya salieron de pantalla y el cobrador se queda sin saber de quién es lo que
 * está leyendo — que en una pantalla que se abre desde una lista de 300 puertas
 * no es un detalle.
 *
 * Así que esto entra **exactamente donde aquello se va**: comparte el mismo
 * [avance], así que no hay un tramo con los dos ni un tramo con ninguno.
 *
 * ## Lleva el MISMO difuminado de la barra de abajo, del otro lado
 *
 * `MspSoftEdgeTopBar`, el gemelo de la barra de acciones: sólido arriba,
 * transparente abajo, con el desenfoque de lo que pasa por detrás donde el
 * teléfono lo soporta. No es coherencia por coherencia — lo que pasa por debajo
 * de este encabezado es contenido con texto, y sin la disolución el nombre
 * quedaría escrito encima de otro texto.
 *
 * ## Por qué NO se quedó el nombre fijo desde el principio
 *
 * Porque entonces el encabezado compacto diría el nombre **dos veces** mientras
 * el fondo se va: una arriba y otra en la tarjeta que todavía no sale de
 * pantalla. Que el nombre viaje con el contenido es lo que deja que este entre
 * sin repetir nada.
 *
 * ## La flecha de volver del mock no está, a propósito
 *
 * Esta pantalla **no tiene** botón de atrás y es una decisión escrita: *"la
 * flecha se fue porque el gesto del sistema ya vuelve y la franja que ocupaba
 * vale más como nombre"* (`DetalleClienteContent`). El mock pedía dejar sólo la
 * flecha; se descartó esa parte. Lo que sí vive arriba —el ojo de privacidad y
 * el sol/luna— viaja con el contenido, como el nombre.
 */
@Composable
fun EncabezadoCompacto(
    nombre: String,
    direccion: String,
    avance: () -> Float,
    modifier: Modifier = Modifier,
    backdrop: MspBackdrop? = null
) {
    // **No se monta hasta que de verdad empieza a entrar**, y no es una
    // optimización: mientras el fondo se ve, la dirección ya está escrita en la
    // tarjeta de identidad, y un segundo nodo con la misma cadena hace que
    // `LaDireccionEscritaSiempreSeVeTest` encuentre DOS —la prueba que existe
    // para que la dirección se vea una vez y entera—. Montarlo con alpha 0 no
    // arregla eso: un nodo invisible sigue estando en el árbol de semántica, y
    // TalkBack también lo lee.
    //
    // `derivedStateOf` y no una lectura directa: así cruzar el umbral cuesta UNA
    // recomposición, no una por cuadro de desplazamiento.
    val montado by remember(avance) { derivedStateOf { avance() > ARRANQUE } }
    if (!montado) return
    MspSoftEdgeTopBar(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Dentro del bloque: entra sin costar una recomposición por
                // cuadro, igual que el fondo del que es el relevo.
                val cerrado = avance().coerceIn(0f, 1f)
                val entrada = ((cerrado - ARRANQUE) / (1f - ARRANQUE)).coerceIn(0f, 1f)
                alpha = entrada
                translationY = -(1f - entrada) * DESLIZAMIENTO.toPx()
            }
            .testTag(ENCABEZADO_COMPACTO_TAG),
        backdrop = backdrop,
        fade = FADE_DEL_ENCABEZADO
    ) {
        // El aire de la derecha deja libre la esquina de los controles
        // flotantes: el ojo y el sol/luna viven ahí fijos, y sin este margen el
        // nombre y la dirección se les meterían debajo al entrar.
        Column(
            modifier = Modifier.padding(
                start = MspTheme.spacing.md,
                end = ESQUINA_DE_LOS_CONTROLES
            )
        ) {
            Text(
                text = nombre,
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = direccion,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A qué altura del recorrido empieza a entrar: al 70 %.
 *
 * No en cero, y ésa es la diferencia entre un relevo y un cruce: hasta el 70 %
 * el fondo todavía se ve, y un encabezado apareciendo encima de un mapa que
 * sigue ahí es la tarjeta pegada que este diseño existe para no tener.
 */
private const val ARRANQUE = 0.7f

/**
 * Lo que el encabezado compacto le deja a la esquina de los controles.
 *
 * Dos botones de 48 dp con su aire, más el margen de la pantalla. Medido en el
 * aparato: sin esto la dirección pasaba por debajo del ojo y del sol/luna.
 */
private val ESQUINA_DE_LOS_CONTROLES = 128.dp

/** Cuánto baja al entrar. Corto: es un relevo, no una entrada. */
private val DESLIZAMIENTO = 12.dp

/**
 * Lo que la cabecera reserva **abajo** para disolverse.
 *
 * Más corto que el de la barra de acciones (88 dp): abajo el degradado tiene que
 * esconder tres botones de 56 dp y acá sólo dos renglones de texto. Un fade de
 * 88 dp arriba se comería el primer renglón de la tarjeta que va pasando.
 */
private val FADE_DEL_ENCABEZADO = 28.dp
