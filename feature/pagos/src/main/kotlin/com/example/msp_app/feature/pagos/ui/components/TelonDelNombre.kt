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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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
    // **Claro y oscuro NO usan el mismo velo, y es una decisión del dueño.**
    // Ver [claridad] para cómo se decide cuál es cuál, y el KDoc de
    // [VELO_EN_CLARO] para por qué son dos diseños distintos y no un número.
    val claro = claridad()
    val velo = lerp(VELO_EN_OSCURO, VELO_EN_CLARO, claro)
    val fade = lerp(FADE_EN_OSCURO, FADE_EN_CLARO, claro)
    val sombra = remember(claro) {
        SOMBRA_SOBRE_EL_MAPA.copy(
            // La sombra existe para darle borde al glifo contra una calle
            // brillante. En claro no queda calle brillante detrás —el velo es
            // casi blanco— y un halo oscuro bajo texto oscuro sólo lo
            // emborrona. Se va con la misma curva con la que entra el velo.
            color = TINTA_DE_LA_SOMBRA.copy(alpha = OPACIDAD_DE_LA_SOMBRA * (1f - claro))
        )
    }
    // Si la dirección cabe, el afordante no se pinta. Lo decide la medición del
    // propio texto y no su longitud: la misma cadena cabe o no según la escala
    // de letra y el ancho, y una cuenta de caracteres se equivoca en los dos
    // sentidos.
    var seCorta by remember(direccion) { mutableStateOf(false) }
    MspSoftEdgeActionBar(
        modifier = modifier.fillMaxWidth().testTag(TELON_DEL_NOMBRE_TAG),
        backdrop = backdrop,
        fade = fade,
        // La meseta se queda en [velo] y sólo cierra a sólido en el último
        // [CIERRE_DEL_TELON] de la franja, justo donde toca las tarjetas — así
        // no queda costura al llegar al contenido. Cuánto vale esa meseta
        // depende del tema: cristal esmerilado en oscuro, casi papel en claro.
        velo = velo,
        cierre = CIERRE_DEL_TELON,
        // **La rampa ocupa el fade ENTERO, no su mitad de abajo.** Acá lo que
        // hay encima es un mapa, no un renglón de texto: no hay nada que
        // proteger de lavarse, y todo el recorrido disponible vale más gastado
        // en disolver. Con la subida en ese, arrancar en 0 no atenúa desde
        // arriba — ver `ARRANQUE_DE_LA_RAMPA`.
        arranque = 0f,
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
                    fontWeight = FontWeight.ExtraBold,
                    shadow = sombra
                ),
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(NOMBRE_DEL_TELON_TAG)
            )
            Text(
                text = direccion.ifBlank { SIN_DIRECCION },
                style = MspTheme.type.caption.copy(shadow = sombra),
                // Un punto más clara que `onSurfaceMuted`: con el velo bajado
                // para dejar ver el mapa, el gris apagado desaparecía sobre una
                // calle blanca. Es la otra mitad del trato — se baja el tinte y
                // se compensa la legibilidad por el lado del texto.
                color = MspTheme.colors.onSurface.copy(alpha = TINTA_DE_LA_DIRECCION),
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
 * **Cuán claro es el tema, de 0 a 1**, para interpolar entre los dos diseños del
 * telón.
 *
 * Se deriva de la **luminancia del fondo** y no de un `Boolean` de tema, por dos
 * razones concretas:
 *
 *  - Es la magnitud que de verdad decide el problema. Lo que hace que el velo
 *    funcione o no es cuánto contrasta con el mapa, y eso es luminancia.
 *  - `MspTheme` cruza los colores en **300 ms** al cambiar de tema. Leyendo el
 *    color ya interpolado, el velo y el fade cruzan con él y no saltan a mitad
 *    de la animación. Un `Boolean` cambiaría de golpe en el fotograma del medio.
 *
 * La normalización es una ese sobre [MEDIA_LUMINANCIA] para que el fondo claro
 * real (`#F4F6F5`, luminancia ~0.90) llegue a 1.0 y no a 0.90 — si no, ninguno
 * de los dos extremos se alcanzaría nunca y los números de acá abajo serían
 * mentira.
 */
@Composable
private fun claridad(): Float {
    val t = (MspTheme.colors.background.luminance() / MEDIA_LUMINANCIA).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Lo que el telón reserva arriba para disolverse sobre el mapa **en oscuro**:
 * 48 dp. Ver [FADE_EN_CLARO] para por qué en claro es otro número.
 *
 * ## Eran 96, bajaron a 24, y 24 se sintió como un corte
 *
 * Los 96 dejaban un hueco de negro muerto. El dueño lo vio en el aparato con la
 * versión que sí pinta teselas: *"el blur del nombre y la dirección está
 * demasiado arriba, debe estar más abajo pegado al nombre"*. Medido sobre su
 * captura, el mapa empezaba a apagarse a ~130 dp, quedaba **completamente negro
 * a ~200** y el nombre no empezaba hasta ~222. Esos ~22 dp no eran ni mapa ni
 * telón: eran un hueco.
 *
 * La respuesta fue bajar a 24 **y dejar la rampa lineal**, y eso trajo el
 * defecto contrario. El 2026-09-25, otra vez en el aparato: *"el desvanecido
 * entre el blur y el mapa normal es muy pronunciado y rápido, debe ser más
 * suave"*. Con 24 dp de fade y la rampa en la mitad de abajo, toda la subida del
 * velo —de 0 a 0.36— cabía en **12 dp de recta**. Una recta tiene esquinas, y
 * doce dp de recta al 0.36 son una banda, no una disolución.
 *
 * ## Por qué 48 no es volver a los 96
 *
 * Porque cambiaron las **dos** cosas que hacían daño, y el fade era sólo una:
 *
 *  - La subida pasó a ser una **ese** (`rampaSuave`), que arranca con pendiente
 *    cero. El primer tercio del recorrido es imperceptible por construcción, así
 *    que 48 dp de ese no se ven empezar donde 48 dp de recta sí.
 *  - La rampa ocupa el fade **entero** en vez de su mitad, así que los 48 se
 *    reparten de verdad en lugar de concentrarse abajo.
 *
 * Y el techo de los 96 tampoco puede volver: la meseta se queda en
 * [VELO_EN_OSCURO] y **nunca cierra a sólido sobre el mapa**, así que el negro
 * muerto de entonces no es un estado alcanzable con este velo.
 *
 * El resultado medible es que el tramo más rápido de la caída baja de ~0.030 a
 * ~0.011 de alpha por dp. **Los dos defectos siguen cobrados a la vez** por
 * `ElTelonNoDejaHuecoTest`: que no arranque demasiado arriba y que no caiga de
 * golpe. Cualquiera de los dos solo se satisface rompiendo el otro.
 */
private val FADE_EN_OSCURO: Dp = 48.dp

/**
 * Lo mismo **en claro: 80 dp**, y el número sale de una cuenta, no del gusto.
 *
 * El recorrido tiene que crecer porque en claro el velo sube mucho más alto
 * ([VELO_EN_CLARO], 0.82 contra 0.36). Lo que el ojo llama "brusco" es el
 * **alpha que cambia por dp**, no el alpha final, así que subir la meseta sin
 * alargar el recorrido devolvería exactamente la queja que este cambio vino a
 * arreglar.
 *
 * Manteniendo la pendiente: `0.82 / 80 ≈ 0.0103` contra `0.36 / 48 = 0.0075` en
 * oscuro. Queda un pelo más rápida a propósito —80 dp de telón ya se comen buena
 * parte de los 300 del mapa— y la ese absorbe la diferencia.
 */
private val FADE_EN_CLARO: Dp = 80.dp

/**
 * **La sombra de NUESTRO texto sobre el mapa**, en su valor de oscuro. En claro
 * se apaga sola — ver dónde se construye, arriba.
 *
 * Es lo que permite bajar el tinte del velo hasta dejar ver las calles: sobre
 * una calle blanca, texto gris sin nada detrás desaparece, y el desenfoque solo
 * no alcanza. Suave y sin desplazamiento —un halo, no un relieve—: lo que hace
 * falta es que el glifo tenga borde contra el fondo, no que parezca despegado.
 *
 * **Ésta sí se puede**, y conviene no confundirla con la que no: la que no se
 * puede es en la hora y los iconos del sistema, que los dibuja SystemUI en su
 * propia ventana. Ver `VeloDeLaBarraDeEstado`.
 */
private val TINTA_DE_LA_SOMBRA = Color.Black

/** Cuánto pesa el halo del texto en oscuro. 0.4: un borde, no un relieve. */
private const val OPACIDAD_DE_LA_SOMBRA = 0.4f

private val SOMBRA_SOBRE_EL_MAPA = Shadow(
    color = TINTA_DE_LA_SOMBRA.copy(alpha = OPACIDAD_DE_LA_SOMBRA),
    offset = Offset.Zero,
    blurRadius = 10f
)

/**
 * **Qué tan opaco llega el velo en OSCURO: 0.36 — cristal esmerilado.**
 *
 * Sobre un mapa oscuro, 0.36 de negro basta para que el texto blanco despegue y
 * las calles **se sigan reconociendo**, que es lo que el dueño pidió mirando el
 * aparato: *"debe solo verse un poco blur el mapa, se debe distinguir el mapa de
 * atrás"*.
 */
private const val VELO_EN_OSCURO = 0.36f

/**
 * **Y en CLARO: 0.82 — papel, no cristal. Son dos diseños distintos a propósito.**
 *
 * ## Por qué el mismo número no sirve para los dos
 *
 * El velo se pinta con `MspTheme.colors.background`, y eso hace cosas opuestas
 * según el tema. En oscuro es **negro sobre un mapa oscuro**: a 0.36 el texto
 * blanco despega de inmediato. En claro es **casi blanco (`#F4F6F5`) sobre un
 * mapa claro**, o sea dos cosas que ya se parecían — a 0.36 prácticamente no
 * cambia nada.
 *
 * Eso es lo que el dueño vio el 2026-09-25 con el mapa real de Google: *"en el
 * modo claro se confunden las letras"*. Medido en su captura, la dirección caía
 * encima de los rótulos *"Calle Vicente Guerrero"* y *"Calle 21 Nte"* y de un
 * punto de interés en morado, todos a plena intensidad. El problema **no era el
 * contraste del glifo** —texto casi negro sobre gris claro contrasta de sobra—
 * sino el **ruido de atrás**: los rótulos del mapa compitiendo con la dirección.
 *
 * Y a un ruido no se lo arregla desenfocándolo un poco: un rótulo desenfocado
 * sigue siendo una mancha con forma de palabra justo debajo de otra palabra. Lo
 * que lo arregla es **taparlo**. Su indicación fue exactamente ésa: *"en modo
 * claro, que el fondo en vez de blur sea más tipo blanco, y también con
 * transición para que se vea que se integra con el mapa de arriba"*.
 *
 * ## Por qué 0.82 y no 1.0
 *
 * Porque la transición tiene que seguir existiendo. Con el velo en 1.0 el telón
 * sería un rectángulo blanco pegado al mapa —el canto que esta pieza existe para
 * no tener— y la parte alta del mapa dejaría de asomarse detrás del nombre.
 *
 * A 0.82, un rótulo gris del mapa (luminancia ~0.45) sube a ~0.89 contra un
 * fondo que queda en ~0.98: deja de competir con el texto pero **el mapa sigue
 * insinuándose**, que es lo que hace que la franja se lea como continuación del
 * mapa y no como una tarjeta encima.
 *
 * El precio está cobrado: con este velo, `ElTelonNoDejaHuecoTest` ya **no** puede
 * pedir que el mapa sobreviva detrás del nombre en claro. Pide lo contrario —que
 * el velo tape— y conserva la exigencia vieja **en oscuro**, que es donde sigue
 * siendo cierta.
 */
private const val VELO_EN_CLARO = 0.82f

/**
 * El punto medio de luminancia con el que [claridad] normaliza.
 *
 * 0.5 y no la luminancia exacta del fondo claro: lo que importa es que los dos
 * temas reales queden **saturados en los extremos** de la ese, y cualquier valor
 * cómodamente entre `#000000` y `#F4F6F5` lo consigue. Atarlo al token de hoy
 * lo rompería el día que el fondo claro cambie un punto.
 */
private const val MEDIA_LUMINANCIA = 0.5f

/** En qué fracción de la franja el velo cierra a sólido: el último 10 %. */
private const val CIERRE_DEL_TELON = 0.90f

/** La dirección, un punto más clara que `onSurfaceMuted` para el mapa de atrás. */
private const val TINTA_DE_LA_DIRECCION = 0.82f

/** El nombre del cliente en el telón: 19 sp, el tamaño del mock. */
private val TAMANO_DEL_NOMBRE = 19.sp

/** Cuántos renglones de dirección se asoman antes del "Ver más". */
private const val RENGLONES_DE_LA_DIRECCION = 2

private const val VER_MAS = "Ver más"

private const val VER_MENOS = "Ver menos"
