package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.component.MspBackdrop
import com.example.msp_app.core.designsystem.component.MspSoftEdgeActionBar
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del telón — la franja del nombre sobre la parte baja del mapa. */
const val TELON_DEL_NOMBRE_TAG: String = "pagos_cliente_telon"

/** `testTag` del nombre dentro del telón. */
const val NOMBRE_DEL_TELON_TAG: String = "pagos_cliente_telon_nombre"

/** `testTag` de la dirección dentro del telón. */
const val DIRECCION_DEL_TELON_TAG: String = "pagos_cliente_telon_direccion"

/** `testTag` del "Ver más" que abre la dirección completa. */
const val VER_MAS_DEL_TELON_TAG: String = "pagos_cliente_telon_ver_mas"

/**
 * **El telón del nombre: la franja que hace de transición entre el mapa y el
 * contenido.**
 *
 * ## Qué es, y por qué no es "un encabezado sobre el mapa"
 *
 * Es las dos cosas a la vez, y ésa es la idea. Lleva **quién** —el nombre— y
 * **dónde** —la dirección completa—, que son las dos preguntas que se hacen
 * mirando la puerta; y al mismo tiempo **es el mecanismo que hace que el mapa no
 * termine en un canto**. Sin él, los 300 dp de mapa cortan en seco contra la
 * primera tarjeta y se lee como dos pantallas pegadas.
 *
 * Por eso reusa [MspSoftEdgeActionBar] tal cual: es exactamente la barra de
 * abajo **al revés** —transparente arriba, sólida abajo— y el degradado de tres
 * paradas es el mismo, con su misma razón (con dos, el fondo nunca termina de
 * cerrar y lo de atrás se transparenta a través del texto). Lo único que cambia
 * es qué carga y de qué lado disuelve. Que las dos orillas usen la misma pieza
 * no es coherencia decorativa: es que **son el mismo problema**.
 *
 * ## El aire de abajo son 22 dp, y los pidió el dueño
 *
 * El bloque de texto se planta a [AIRE_DEL_TELON] del borde inferior de la
 * franja. Pegado al canto se veía mal —lo dijo mirando el aparato— y con el
 * degradado cerrando justo ahí el texto quedaba sobre el tramo más sólido, que
 * es donde menos se distingue del contenido que empieza abajo.
 *
 * ## La dirección son DOS renglones, y "Ver más" es el camino normal
 *
 * No el raro. Con las direcciones reales del padrón —calle, número, colonia y
 * población en una sola cadena— **casi nunca caben dos renglones**, así que el
 * afordante se pinta casi siempre. Está medido sobre el propio texto: lo decide
 * [TextOverflow] a través de `onTextLayout`, no una cuenta de caracteres.
 *
 * Al abrirse, el telón **crece hacia arriba y se come mapa, no contenido**: el
 * contenido sigue empezando en `altoDelFondo()` pase lo que pase, porque esta
 * franja es una capa encima y no un eslabón de la pila. Es lo que deja que la
 * dirección más larga del padrón se lea entera sin mover el dinero ni un dp.
 *
 * ## Y la dirección ya NO está en la tarjeta de identidad
 *
 * Decisión del dueño en esta pasada: con el telón diciéndola arriba, repetirla
 * en la tarjeta **se lee como un error de copiado** — su queja textual de la
 * ronda anterior, aplicada al reparto nuevo. La tarjeta se queda con el aval, la
 * última visita y las tres acciones.
 *
 * La regla vieja —*"siempre se tiene que ver la dirección escrita"*— **sigue
 * cumpliéndose y por eso el cambio es legítimo**: se ve entera, arriba, y con un
 * camino explícito cuando no cabe. Lo que cambió es dónde, no si.
 */
@Composable
fun TelonDelNombre(
    nombre: String,
    direccion: String,
    modifier: Modifier = Modifier,
    backdrop: MspBackdrop? = null
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    // Si la dirección cabe, el afordante no se pinta. Lo decide la medición del
    // propio texto y no su longitud: la misma cadena cabe o no según la escala
    // de letra y el ancho, y una cuenta de caracteres se equivoca en los dos
    // sentidos.
    var seCorta by remember(direccion) { mutableStateOf(false) }
    MspSoftEdgeActionBar(
        modifier = modifier.fillMaxWidth().testTag(TELON_DEL_NOMBRE_TAG),
        backdrop = backdrop,
        fade = FADE_DEL_TELON,
        // El telón no vive en la orilla de abajo: vive en la parte baja del
        // MAPA, a media pantalla. El inset de la barra de navegación sería aire
        // muerto ahí.
        conInsetDeAbajo = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MspTheme.spacing.md)
                .padding(bottom = AIRE_DEL_TELON)
        ) {
            Text(
                text = nombre,
                style = MspTheme.type.cardTitle.copy(
                    fontSize = TAMANO_DEL_NOMBRE,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(NOMBRE_DEL_TELON_TAG)
            )
            Text(
                text = direccion.ifBlank { SIN_DIRECCION },
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = if (abierto) Int.MAX_VALUE else RENGLONES_DE_LA_DIRECCION,
                overflow = TextOverflow.Ellipsis,
                // `onTextLayout` y no `length`: pregunta por el resultado real
                // del renglonado, que es lo único que sabe si cupo. Sólo se
                // consulta con el telón cerrado — abierto nunca se corta, y
                // dejar que se apague el afordante ahí escondería la vuelta.
                onTextLayout = { if (!abierto) seCorta = it.hasVisualOverflow },
                modifier = Modifier.testTag(DIRECCION_DEL_TELON_TAG)
            )
            if (seCorta) {
                Text(
                    text = if (abierto) VER_MENOS else VER_MAS,
                    style = MspTheme.type.captionStrong,
                    color = MspTheme.colors.brand,
                    modifier = Modifier
                        .padding(top = MspTheme.spacing.xs)
                        .clickable { abierto = !abierto }
                        .testTag(VER_MAS_DEL_TELON_TAG)
                )
            }
        }
    }
}

/**
 * El aire entre el bloque de texto y el borde de abajo del telón.
 *
 * 22 dp, pedidos por el dueño mirando el aparato: pegado al canto se veía mal.
 */
private val AIRE_DEL_TELON: Dp = 22.dp

/**
 * Lo que el telón reserva arriba para disolverse sobre el mapa.
 *
 * Más largo que el de la barra de abajo: ahí el degradado esconde botones con
 * su propio relleno, y acá tiene que hacer desaparecer **teselas de mapa**, que
 * son foto y no tienen dónde apoyarse. Con un fade corto se ve el renglón donde
 * el mapa se apaga, que es justo el canto que este telón existe para no tener.
 */
private val FADE_DEL_TELON: Dp = 96.dp

/** El nombre del cliente en el telón: 19 sp, el tamaño del mock. */
private val TAMANO_DEL_NOMBRE = 19.sp

/** Cuántos renglones de dirección se asoman antes del "Ver más". */
private const val RENGLONES_DE_LA_DIRECCION = 2

private const val VER_MAS = "Ver más"

private const val VER_MENOS = "Ver menos"
