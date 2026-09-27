package com.example.msp_app.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * **La barra de acciones sin canto: el degradado de tres paradas que siempre
 * corre, y encima el desenfoque de lo que pasa por detrás donde el teléfono lo
 * soporta.**
 *
 * Palabras del dueño sobre lo que esta pieza tiene que lograr: *"que se vaya
 * difuminando para que se vea un poco lo de atrás, sólo arriba, no todo, y así
 * no se vea claramente dónde empieza y termina el bloque"*.
 *
 * ## Por qué vive acá y no en una feature
 *
 * Porque la consumen **cinco pantallas de tres módulos** —detalle de cliente,
 * detalle de venta y registrar abono (`:feature:pagos`), registrar visita
 * (`:feature:visitas`) y el reporte de cobranza (`:feature:collectionReport`)—
 * y `:feature:pagos` y `:feature:visitas` **no se ven entre sí**. El único
 * lugar desde el que las tres llegan es `:core:designsystem`.
 *
 * Es patrón nuevo en el design system y se subió **con autorización explícita
 * del dueño**, dicha sobre el dato de que hoy hay cinco implementaciones de
 * dock para ocho pantallas. No se unificaron los cinco docks: lo que se
 * comparte es el **canto**, no el reparto de botones de cada pantalla.
 *
 * ## Lo que hereda de [com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar], y lo que corrige
 *
 * El degradado es el mismo de tres paradas del piloto, y se copia **con su
 * razón**: con sólo dos paradas el alpha se interpola en todo el alto y para
 * cuando llega a los botones el fondo **todavía no cerró**, así que el
 * contenido de atrás se transparenta **a través** del botón. Ese defecto ya se
 * pagó una vez en el reporte de cobranza; la tercera parada en
 * [MSP_SOFT_EDGE_SOLID_STOP] repitiendo el mismo color con alpha 1 fuerza el tramo
 * final a opacidad constante. **No lo bajes a dos paradas.**
 *
 * Lo que corrige es el **nombre**: aquel componente se llama `BlurredActionBar`
 * y **no desenfoca nada** — es un degradado puro desde API 24. El nombre se
 * creyó cierto durante todo el piloto. Se deja escrito acá porque en este repo
 * lo refutado no se borra: la creencia era *"el degradado es un desenfoque"*, y
 * lo que se midió es que `Modifier.blur` desenfoca **su propio contenido, no lo
 * que está detrás**, y que el desenfoque de lo de atrás exige capturar el
 * contenido en un `GraphicsLayer` y volver a dibujarlo — ver [MspBackdrop].
 *
 * ## El desenfoque es OPCIONAL y sólo entra con [MspBackdrop]
 *
 * Sin `backdrop` esta barra es exactamente el piloto: degradado y nada más, y
 * se ve bien. Con `backdrop` se le suma la capa borrosa, que **la dibuja el
 * contenido** y no esta barra — ver el KDoc de [mspBackdropSource] para por qué
 * el orden de dibujo obliga a repartirlo así.
 */
@Composable
fun MspSoftEdgeActionBar(
    modifier: Modifier = Modifier,
    backdrop: MspBackdrop? = null,
    fade: Dp = MSP_SOFT_EDGE_FADE,
    solape: Dp = 0.dp,
    conInsetDeAbajo: Boolean = true,
    velo: Float = 1f,
    cierre: Float? = null,
    arranque: Float = ARRANQUE_DE_LA_RAMPA,
    tinte: Color? = null,
    contenido: @Composable ColumnScope.() -> Unit
) {
    // **[tinte] existe para que la barra pueda NO ser del color de la página.**
    //
    // Por omisión lo es, y ése sigue siendo el caso normal: una banda que se
    // funde con el fondo es lo que hace que no se vea dónde empieza.
    //
    // Pero en el tema oscuro de esta app el fondo es `#000000` puro —paleta
    // OLED— y eso tiene una consecuencia que no es de gusto: un hueco SIN nada
    // detrás y un degradado ya cerrado dan **exactamente el mismo píxel**, así
    // que la banda no se puede distinguir del vacío por mucho que se juegue con
    // el velo. Medido el 2026-09-25 sobre la captura del dueño: en los huecos
    // entre los botones, `(0,0,0)` en los dos casos.
    //
    // Levantando el color un punto, la banda se lee como un panel y no como el
    // fondo. Quien lo pase decide cuánto — ver `ELEVACION_DEL_DOCK`.
    val fondo = tinte ?: MspTheme.colors.background
    // **La rampa vive DENTRO del fade y termina donde empieza el contenido.**
    //
    // Antes eran fracciones fijas de la banda entera —transparente en 0, sólido
    // en el 44 %— y eso tenía un defecto que sólo se ve en vidrio: la banda
    // empieza a atenuar **desde su primer píxel**, así que se come el renglón
    // que va justo encima. El dueño lo reportó dos veces el mismo día, con las
    // dos orillas: *"el blur del nombre está demasiado arriba"* y *"también
    // está muy arriba el blur de los botones fijos"*.
    //
    // Con la rampa atada al fade, lo de arriba se lee **limpio** hasta la mitad
    // del fade, y para cuando llega al contenido el fondo ya cerró. Y como el
    // fade es un dp y la banda crece con la tipografía, las fracciones se
    // recalculan solas: a 2.0 el contenido mide más y la rampa sigue cayendo
    // donde tiene que caer.
    var altoPx by remember { mutableIntStateOf(0) }
    val densidad = LocalDensity.current
    val fadePx = with(densidad) { fade.toPx() }
    // **[solape] es cuánto SIGUE la rampa después de que el fade terminó.**
    //
    // Sin él, el degradado cierra exactamente donde empieza el contenido de la
    // barra, así que el primer botón nace sobre fondo ya sólido y la disolución
    // queda entera por encima del bloque — que es lo que hacía que la banda se
    // viera "alta" por más que se le recortaran dp.
    //
    // Pedido del dueño el 2026-09-25, después de descartar la banda entera
    // translúcida: *"no quiero que todo se traslucide, sólo la parte de arriba
    // mientras se difumina; que el difuminado termine hasta un poco abajo del
    // punto más alto de los botones"*.
    //
    // Con solape, el tramo final de la rampa cae **sobre la franja de los
    // botones**. Los botones son opacos y se dibujan encima, así que lo que
    // queda translúcido es exactamente lo que él describe: los huecos
    // horizontales entre ellos, y sólo en su parte de arriba. Más abajo, sólido.
    val solidoPx = fadePx + with(densidad) { solape.toPx() }
    val solido = if (altoPx > 0) (solidoPx / altoPx).coerceIn(0f, 1f) else MSP_SOFT_EDGE_SOLID_STOP
    val inicio = solido * arranque
    // **Dónde la meseta translúcida vuelve a cerrar a sólido: donde empieza la
    // barra de navegación del sistema.**
    //
    // Con [velo] por debajo de 1 la banda deja ver lo de atrás, y eso es lo que
    // se quiere **a la altura de los botones** — el dueño lo pidió así el
    // 2026-09-25: *"que se transparente un poquito entre los espacios
    // horizontales de los botones"*. Lo que NO se quiere es que se transparente
    // **debajo** de ellos, donde lo que hay encima son los controles del sistema:
    // ahí el contenido pasando por detrás de los botones de Android se lee como
    // un error de dibujo.
    //
    // Se calcula en vez de escribirse porque el reparto de esta barra cambia con
    // la tipografía y el teléfono. Un `0.62` a mano queda viejo en el primer
    // aparato con gestos en vez de tres botones.
    val insetPx = if (conInsetDeAbajo) WindowInsets.navigationBars.getBottom(densidad) else 0
    val cierreReal = cierre ?: if (altoPx > 0) {
        ((altoPx - insetPx).toFloat() / altoPx).coerceIn(solido, 1f)
    } else {
        1f
    }
    // **La tira borrosa la dibuja el CONTENIDO, no esta barra**, así que las dos
    // fracciones tienen que viajar hasta allá. `SideEffect` y no una escritura
    // directa: publicar el resultado de la composición a un estado compartido se
    // hace después de que la composición cerró, no en medio.
    if (backdrop != null) {
        SideEffect {
            backdrop.inicioDeLaRampa.floatValue = inicio
            backdrop.solidoDeLaRampa.floatValue = solido
            backdrop.fadeDeLaBarraPx.intValue = fadePx.toInt()
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { altoPx = it.height }
            .mspBackdropBar(backdrop)
            .background(
                Brush.verticalGradient(
                    // **La rampa dejó de ser una recta entre dos paradas.**
                    //
                    // Con dos paradas el alpha se interpola linealmente, y una
                    // recta tiene **esquinas**: la pendiente salta de 0 a su
                    // valor pleno donde la rampa empieza, y de vuelta a 0 donde
                    // llega a la meseta. El ojo ve esas dos esquinas como
                    // líneas aunque el degradado sea continuo —bandas de Mach—,
                    // y eso es exactamente lo que el dueño reportó el
                    // 2026-09-25 mirando el telón sobre el mapa: *"el
                    // desvanecido entre el blur y el mapa normal es muy
                    // pronunciado y rápido"*.
                    //
                    // [rampaSuave] reemplaza esa recta por una **ese**
                    // (`smoothstep`) muestreada en [PASOS_DE_LA_RAMPA] paradas:
                    // arranca con pendiente cero, acelera en el medio y vuelve
                    // a pendiente cero al llegar a la meseta. Las dos esquinas
                    // desaparecen y la caída se reparte de verdad.
                    //
                    // **La cola sigue cerrando, y eso no es negociable**: sin
                    // la parada final repitiendo el color con alpha 1 el
                    // degradado interpola hasta el borde, el fondo nunca
                    // termina de cerrar y lo de atrás se transparenta **a
                    // través** de los botones. Ese defecto ya se pagó una vez
                    // en el reporte de cobranza. Lo que se movió es la FORMA de
                    // la subida, no que la cola cierre.
                    colorStops = buildList {
                        val meseta = if (velo >= 1f) 1f else velo
                        addAll(rampaSuave(inicio, solido, fondo, meseta))
                        // **Cristal esmerilado, no una pared.** Con [velo] por
                        // debajo de 1 la meseta se queda a media tinta y lo de
                        // atrás —el mapa— **se sigue distinguiendo**. Es lo que
                        // el dueño pidió mirando el aparato: *"debe solo verse
                        // un poco blur el mapa, se debe distinguir el mapa de
                        // atrás"*. La meseta se mantiene hasta [cierre], justo
                        // donde la banda toca el contenido.
                        if (velo < 1f && cierreReal > solido) {
                            add(cierreReal to fondo.copy(alpha = velo))
                        }
                        add(1f to fondo)
                    }.toTypedArray()
                )
            )
            // **El inset va DESPUÉS del degradado, y el orden es la mitad del
            // arreglo.** Así el degradado llega al borde de abajo de la pantalla
            // —la orilla se comporta igual que la de arriba, donde el mapa
            // sangra— y lo que sube para no quedar debajo de los botones del
            // sistema es el CONTENIDO. Al revés quedaría una franja del color de
            // la pantalla por debajo de la barra, que es el canto que esto
            // existe para no tener. Mismo patrón que `BlurredActionBar`.
            //
            // [conInsetDeAbajo] en `false` es para quien no vive en la orilla:
            // el telón del nombre usa esta misma pieza en la parte baja del
            // MAPA, y ahí un inset de barra de navegación sería aire muerto en
            // medio de la pantalla.
            .then(if (conInsetDeAbajo) Modifier.navigationBarsPadding() else Modifier)
            .padding(top = fade),
        content = contenido
    )
}

/**
 * **La subida del velo, en forma de ese en vez de recta.**
 *
 * Devuelve [PASOS_DE_LA_RAMPA] + 1 paradas entre [desde] y [hasta], con el alpha
 * siguiendo `smoothstep` —`t²(3−2t)`— hasta [alphaFinal] en vez de interpolarse
 * en línea recta.
 *
 * ## Por qué una ese y no una recta más larga
 *
 * Alargar la recta reparte la caída pero **no quita las esquinas**: donde la
 * rampa arranca y donde llega a la meseta la pendiente cambia de golpe, y el ojo
 * lee ese cambio como una línea. Es el efecto de banda de Mach, y es la razón de
 * que una rampa lineal se vea "cortada" aunque el degradado sea continuo.
 *
 * La ese arranca y termina con **pendiente cero**, así que se funde con el mapa
 * limpio de arriba y con la meseta de abajo sin un renglón donde algo cambie. De
 * paso baja la pendiente máxima: sobre el mismo recorrido, el tramo más rápido
 * de una ese es 1.5 veces el de la recta, pero como la ese permite recorridos
 * mucho más largos sin que el arranque se note, el resultado neto es una caída
 * bastante más lenta.
 *
 * ## El muestreo es discreto a propósito
 *
 * `Brush.verticalGradient` interpola **lineal entre paradas**, así que la ese se
 * aproxima por tramos. Con [PASOS_DE_LA_RAMPA] tramos cada uno cubre menos de un
 * dp de alpha en los recorridos reales de esta pieza: la diferencia contra la
 * curva exacta queda por debajo de lo que un panel de 8 bits puede representar.
 *
 * Devuelve la lista **vacía** cuando no hay recorrido ([hasta] no es mayor que
 * [desde]): sin sitio donde repartir no hay rampa que dibujar, y emitir paradas
 * repetidas en la misma fracción es cómo se construye justamente el canto.
 */
private fun rampaSuave(
    desde: Float,
    hasta: Float,
    color: Color,
    alphaFinal: Float
): List<Pair<Float, Color>> {
    if (hasta <= desde) return emptyList()
    return (0..PASOS_DE_LA_RAMPA).map { paso ->
        val t = paso.toFloat() / PASOS_DE_LA_RAMPA
        // La ese clásica `t²(3 − 2t)`, escrita como `1 + 2(1 − t)` en vez de
        // `3 − 2t`: la misma curva, sin un número mágico suelto.
        val suave = t * t * (1f + 2f * (1f - t))
        (desde + (hasta - desde) * t) to color.copy(alpha = alphaFinal * suave)
    }
}

/**
 * **Dónde arranca y dónde cierra la rampa, en fracción del alto de la barra**
 * — los mismos dos números con los que la barra dibuja su degradado.
 *
 * Los publica [MspSoftEdgeActionBar] al medirse. Viven en [MspBackdrop] y no se
 * vuelven a calcular porque el desenfoque y el degradado **tienen** que cerrar
 * en el mismo renglón, y la única forma de garantizarlo es que sea un solo
 * número y no dos copias: ver el KDoc de [tiraConRampa] para lo que pasó
 * mientras fueron dos. Van juntos en un objeto porque se escriben y se leen
 * siempre en pareja.
 */
@Stable
internal class RampaCompartida(
    val inicio: MutableFloatState,
    val solido: MutableFloatState
)

/**
 * **Lo que hace falta para desenfocar lo que pasa POR DETRÁS de la barra.**
 *
 * ## Por qué esto no es un `Modifier.blur` y punto
 *
 * Medido contra la versión que este proyecto compila (Compose UI 1.7.8):
 * `Modifier.blur` desenfoca **su propio contenido**, no lo que está detrás. Una
 * barra con `blur` encima desenfocaría sus propios botones. Para desenfocar lo
 * de atrás hay que **capturar el contenido en un `GraphicsLayer`** (`record()`
 * + `renderEffect`) y volver a dibujarlo borroso donde toca.
 *
 * ## El tope es API 31, y el mínimo del proyecto es 24
 *
 * `GraphicsLayer.renderEffect` documenta literal que *"sólo se soporta en
 * Android 12 y superior; los intentos de usarlo en versiones anteriores se
 * ignoran"*. El `minSdk` del proyecto es **24** (`app/build.gradle.kts:79` y el
 * plugin de convenciones): **siete versiones de Android por debajo del
 * desenfoque**. Hoy no hay teléfonos de la flota debajo de Android 12, pero el
 * `minSdk` permite que entre uno mañana.
 *
 * Por eso el desenfoque **nunca es la base**: la base es el degradado de tres
 * paradas de [MspSoftEdgeActionBar], que corre desde API 24 y que el dueño ya
 * vio y aprobó en el reporte de cobranza. El desenfoque es una capa encima, y
 * cuando no está, lo que queda es el estado aceptable y no uno roto.
 *
 * ## Por qué la tira borrosa la dibuja el CONTENIDO y no la barra
 *
 * Por el orden de dibujo. El `GraphicsLayer` se graba durante el dibujo del
 * contenido; la barra se dibuja después, en **su propio nodo**. Al desplazar,
 * lo que se invalida es el contenido — el nodo de la barra no, porque nada suyo
 * cambió. Si la tira la dibujara la barra, enseñaría el desenfoque del cuadro
 * anterior: una foto vieja pegada abajo, que es peor que no desenfocar.
 *
 * Dibujándola el contenido, la tira se vuelve a grabar y a pintar en el mismo
 * dibujo que la movió. El reparto queda: **el contenido pone el desenfoque, la
 * barra pone el degradado y los botones encima**.
 */
@Stable
class MspBackdrop internal constructor(
    /** Cuánto desenfoca la tira de abajo — la de la barra o la del telón. */
    internal val radioAbajo: Dp,
    /** Cuánto desenfoca la tira de arriba — la del velo de la barra de estado. */
    internal val radioArriba: Dp,
    /** Alto TOTAL de la barra en px —degradado incluido—, que ella misma mide. */
    internal val altoDeLaBarraPx: MutableIntState,
    /** Lo mismo para la cabecera de arriba, cuando la pantalla tiene una. */
    internal val altoDeLaCabeceraPx: MutableIntState,
    /** Ver [RampaCompartida]. */
    internal val rampa: RampaCompartida,
    /** El `fade` de la barra en px, para poder restarlo de lo que se reserva. */
    internal val fadeDeLaBarraPx: MutableIntState
) {
    internal val inicioDeLaRampa: MutableFloatState get() = rampa.inicio
    internal val solidoDeLaRampa: MutableFloatState get() = rampa.solido

    /**
     * `true` sólo en Android 12+. Abajo de eso [mspBackdropSource] devuelve el
     * `Modifier` sin tocar: ni graba capas ni cuesta un dibujo de más.
     */
    internal val soportado: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * **Lo que una barra que NO es [MspSoftEdgeActionBar] tiene que poner para que
 * el desenfoque sepa hasta dónde llega.**
 *
 * Existe por el reporte de cobranza: su `BlurredActionBar` ya trae su propio
 * degradado de tres paradas —es el piloto del que salió éste— y reemplazarlo
 * entero movería sus goldens sin ganar nada. Con esto recibe la capa de
 * desenfoque sin cambiar una línea de su reparto de botones.
 *
 * [MspSoftEdgeActionBar] lo aplica por su cuenta; no hace falta ponerlo dos
 * veces.
 */
/**
 * **Lo mismo que [mspBackdropBar], para la orilla de ARRIBA.**
 *
 * Lo usa el velo de la barra de estado del detalle de cliente: declara cuánto
 * alto ocupa para que la tira borrosa se grabe de ese tamaño exacto y no de uno
 * inventado. Quien la dibuja sigue siendo el contenido —ver [mspBackdropSource]
 * para por qué el orden de dibujo obliga a repartirlo así—.
 */
fun Modifier.mspBackdropCabecera(backdrop: MspBackdrop?): Modifier = if (backdrop == null) {
    this
} else {
    onSizeChanged { backdrop.altoDeLaCabeceraPx.intValue = it.height }
}

fun Modifier.mspBackdropBar(backdrop: MspBackdrop?): Modifier =
    if (backdrop == null) this else onSizeChanged { backdrop.altoDeLaBarraPx.intValue = it.height }

/**
 * **Cuánto mide la barra, medido por ella misma.**
 *
 * Es lo que el contenido desplazable tiene que reservar abajo para que su
 * último renglón no quede debajo del tramo sólido. **No es una constante y no
 * se puede copiar de otra pantalla**: el dock del detalle de cliente mide 89 dp
 * a letra normal y 133 a las grandes —se apila en dos renglones—, y el del
 * reporte de cobranza otra cosa. Un número a mano se queda viejo la primera vez
 * que alguien cambia un botón.
 *
 * Vale 0 dp hasta la primera medición, que es el estado correcto: reservar aire
 * antes de saber cuánto sería inventarlo.
 */
@Composable
fun MspBackdrop.altoDeLaBarra(): Dp = with(LocalDensity.current) {
    altoDeLaBarraPx.intValue.toDp()
}

/**
 * **Cuánto de la barra TAPA de verdad — que no es lo que mide.**
 *
 * Es [altoDeLaBarra] menos el `fade`, o sea el alto descontando el tramo por el
 * que el contenido de atrás **se ve a propósito**.
 *
 * ## Por qué hacen falta los dos números
 *
 * Reservando el alto completo, la última fila del contenido se queda clavada en
 * el borde de arriba de la banda y por detrás de los botones **no pasa nunca
 * nada**. Eso deja la disolución sin nada que disolver: en el tema oscuro, donde
 * el fondo es negro puro, un hueco vacío y un degradado cerrado dan el mismo
 * píxel, así que la banda se ve como una tapa lisa.
 *
 * Reservando sólo lo que tapa, el contenido se mete bajo el fade y la banda pasa
 * a tener algo que velar. Medido el 2026-09-25 en el aparato del dueño: con la
 * reserva completa, los chips del ritmo morían 28 dp por encima del primer botón
 * y en los huecos entre botones había `(0,0,0)` de punta a punta.
 *
 * **El costo lo eligió el dueño con el número delante**: la última fila queda
 * parcialmente velada — a 24 dp de fade, ~65 % en su renglón más bajo. Lo que NO
 * cambia es que siga alcanzable: el tramo devuelto es justamente el que **no**
 * cierra a sólido.
 *
 * Quien necesite la reserva conservadora —un formulario, donde una fila a medias
 * es un campo a medias— se queda con [altoDeLaBarra].
 */
@Composable
fun MspBackdrop.altoQueLaBarraTapa(): Dp = with(LocalDensity.current) {
    (altoDeLaBarraPx.intValue - fadeDeLaBarraPx.intValue).coerceAtLeast(0).toDp()
}

/** Crea el [MspBackdrop] de una pantalla. Uno por pantalla, no uno por barra. */
@Composable
fun rememberMspBackdrop(
    radioAbajo: Dp = RADIO_DEL_DESENFOQUE,
    radioArriba: Dp = RADIO_DEL_DESENFOQUE
): MspBackdrop {
    val alto = remember { mutableIntStateOf(0) }
    val cabecera = remember { mutableIntStateOf(0) }
    // Los mismos valores con los que la barra arranca antes de su primera
    // medición, para que el primer cuadro no dibuje una rampa distinta.
    val inicio = remember { mutableFloatStateOf(MSP_SOFT_EDGE_SOLID_STOP * ARRANQUE_DE_LA_RAMPA) }
    val solido = remember { mutableFloatStateOf(MSP_SOFT_EDGE_SOLID_STOP) }
    val fade = remember { mutableIntStateOf(0) }
    return remember(radioAbajo, radioArriba) {
        MspBackdrop(radioAbajo, radioArriba, alto, cabecera, RampaCompartida(inicio, solido), fade)
    }
}

/**
 * **El modificador que va en el contenido desplazable**, no en la barra.
 *
 * Graba el contenido en una capa, lo dibuja tal cual, y encima —sólo en la
 * franja de abajo, la que la barra tapa— vuelve a dibujarlo borroso con una
 * rampa de alpha que arranca en cero. Esa rampa es lo que quita el canto: el
 * desenfoque **entra**, no aparece.
 *
 * Se graba **una tira del alto de la barra**, no la pantalla entera: el
 * desenfoque se rasteriza fuera de pantalla y hacerlo sobre 177 dp cuesta una
 * fracción de hacerlo sobre 744. En un teléfono que además está hospedando un
 * mapa vivo, eso importa.
 *
 * Sin soporte (API < 31) devuelve el `Modifier` **sin cambios**: no graba, no
 * dibuja de más y el contenido sigue su camino normal hacia el degradado.
 */
fun Modifier.mspBackdropSource(backdrop: MspBackdrop): Modifier {
    if (!backdrop.soportado) return this
    return drawWithCache {
        val nitido = obtainGraphicsLayer()
        val borroso = obtainGraphicsLayer()
        val superior = obtainGraphicsLayer()
        // Un radio por tira, no uno para las dos. Sobre teselas de mapa, el
        // radio decide si lo de atrás se **distingue** o se vuelve una mancha:
        // el dueño pidió *"sólo un poco de blur"* para poder seguir
        // reconociendo las calles detrás del nombre.
        val abajo = backdrop.radioAbajo.toPx()
        val arriba = backdrop.radioArriba.toPx()
        borroso.renderEffect = BlurEffect(abajo, abajo, TileMode.Decal)
        superior.renderEffect = BlurEffect(arriba, arriba, TileMode.Decal)
        onDrawWithContent {
            nitido.record { this@onDrawWithContent.drawContent() }
            drawLayer(nitido)
            if (size.height <= 0f || size.width <= 0f) return@onDrawWithContent
            val cabecera = backdrop.altoDeLaCabeceraPx.intValue
            if (cabecera > 0) {
                val altoCabecera = minOf(cabecera, size.height.toInt())
                superior.record(size = IntSize(size.width.toInt(), altoCabecera)) {
                    drawLayer(nitido)
                }
                superior.topLeft = IntOffset.Zero
                tiraConRampa(
                    superior,
                    0f,
                    altoCabecera.toFloat(),
                    haciaAbajo = false,
                    inicio = backdrop.inicioDeLaRampa.floatValue,
                    solido = backdrop.solidoDeLaRampa.floatValue
                )
            }
            val alto = backdrop.altoDeLaBarraPx.intValue
            if (alto <= 0) return@onDrawWithContent
            val altoTira = minOf(alto, size.height.toInt())
            val arriba = size.height - altoTira
            borroso.record(size = IntSize(size.width.toInt(), altoTira)) {
                translate(top = -arriba) { drawLayer(nitido) }
            }
            borroso.topLeft = IntOffset(0, arriba.toInt())
            tiraConRampa(
                borroso,
                arriba,
                altoTira.toFloat(),
                haciaAbajo = true,
                inicio = backdrop.inicioDeLaRampa.floatValue,
                solido = backdrop.solidoDeLaRampa.floatValue
            )
        }
    }
}

/**
 * **La tira borrosa, con su rampa de alpha.**
 *
 * `saveLayer` + `DstIn`: la rampa se aplica a la tira ya dibujada. Sin el buffer
 * intermedio el `DstIn` mordería también lo que hay debajo, que es el contenido
 * nítido — y lo que quedaría es un agujero.
 *
 * [haciaAbajo] es de qué lado cierra: la barra de abajo arranca transparente
 * arriba y cierra abajo; la cabecera de arriba va al revés.
 *
 * ## [inicio] y [solido] los pone la BARRA, y antes no
 *
 * Esto era una afirmación escrita que llevaba tiempo siendo falsa. Decía: *"las
 * dos usan la MISMA fracción ([MSP_SOFT_EDGE_SOLID_STOP]) para que el desenfoque
 * y el degradado terminen de cerrar en el mismo renglón y no se vea un segundo
 * canto adentro del primero"*.
 *
 * Fue cierto hasta que el degradado se ató al **fade** —fracciones calculadas
 * desde el alto medido— y esta rampa se quedó con la constante. Desde entonces
 * las dos capas hacían cosas distintas sobre la misma franja:
 *
 *  - el degradado subía en los 24 dp justo encima de los botones;
 *  - el desenfoque arrancaba en el **borde de arriba de la barra entera** —48 dp
 *    por encima de los botones, inset de navegación incluido— y seguía
 *    intensificándose hasta 0.44 del alto total, o sea **22 dp por DEBAJO** del
 *    primer botón.
 *
 * Eso es lo que el dueño reportó el 2026-09-25: *"ahí donde se comienza a
 * difuminar está muy arriba, lo quiero mucho más abajo"*. Medido en su captura,
 * el renglón de `PARCIALIDAD` se leía y el de sus cifras estaba borrado, con el
 * borde de la tarjeta desaparecido — no por el degradado, que ahí todavía no
 * empieza, sino por el desenfoque, que llevaba 24 dp mordiendo.
 *
 * Ahora las fracciones llegan de quien las calcula, así que la afirmación vuelve
 * a ser verdad **por construcción y no por coincidencia**: no hay dos números
 * que mantener iguales a mano.
 */
private fun DrawScope.tiraConRampa(
    capa: GraphicsLayer,
    arriba: Float,
    alto: Float,
    haciaAbajo: Boolean,
    inicio: Float,
    solido: Float
) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset(0f, arriba), Size(size.width, alto)), Paint())
        drawLayer(capa)
        // La misma ese del degradado, por el mismo motivo: una recta tiene
        // esquinas y el ojo las lee como líneas. Ver [rampaSuave].
        val subida = rampaSuave(inicio, solido, Color.Black, 1f)
        val paradas = if (haciaAbajo) {
            (subida + (1f to Color.Black)).toTypedArray()
        } else {
            // La cabecera es el espejo: sólida arriba y se va hacia abajo. Se
            // refleja la MISMA curva en vez de escribir otra, para que las dos
            // orillas no puedan divergir.
            (listOf(0f to Color.Black) + subida.reversed().map { (f, c) -> (1f - f) to c })
                .toTypedArray()
        }
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = paradas,
                startY = arriba,
                endY = arriba + alto
            ),
            topLeft = Offset(0f, arriba),
            size = Size(size.width, alto),
            blendMode = BlendMode.DstIn
        )
        canvas.restore()
    }
}

/**
 * **La cabecera compacta: la misma disolución, del otro lado.**
 *
 * Sólida arriba y transparente abajo, con el mismo desenfoque de la barra
 * inferior detrás del mismo `SDK_INT >= S`. Existe para que el nombre y la
 * dirección sigan legibles cuando el fondo de la pantalla ya se fue y lo que
 * pasa por detrás es contenido, no un mapa.
 */
@Composable
fun MspSoftEdgeTopBar(
    modifier: Modifier = Modifier,
    backdrop: MspBackdrop? = null,
    fade: Dp = MSP_SOFT_EDGE_FADE,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val fondo = MspTheme.colors.background
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { backdrop?.altoDeLaCabeceraPx?.intValue = it.height }
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to fondo,
                        1f - MSP_SOFT_EDGE_SOLID_STOP to fondo,
                        1f to fondo.copy(alpha = 0f)
                    )
                )
            )
            .padding(bottom = fade),
        content = contenido
    )
}

/**
 * Lo que la barra reserva **arriba** de sus botones para disolverse: **24 dp**.
 *
 * ## La historia del número, porque explica el piso
 *
 * Fue **66**, luego **48**, y cada bajada la pidió el dueño mirando el aparato.
 * Con 66 y la rampa en la mitad de abajo del fade, la atenuación se hacía
 * perceptible ~39 dp por encima de los botones — ahí vio lavarse `PARCIALIDAD`
 * y `ÚLT. PAGO`. Con 48 seguía siendo demasiada banda.
 *
 * El 2026-09-25 dijo qué quería, y no era otro recorte: *"quiero que el
 * difuminado incluso esté a la misma altura de la parte más alta de los 3
 * botones"*. O sea que la banda **no tiene que sobresalir** del bloque de
 * botones; la disolución tiene que caber justo encima de ellos.
 *
 * 24 dp con [ARRANQUE_DE_LA_RAMPA] en 0 son 24 dp de ese, que por la forma de la
 * curva se empieza a notar alrededor del tercio: **unos 16 dp por encima del
 * primer botón**, contra los 39 del principio.
 *
 * ## El número viejo ya no lo fijaba el dinero
 *
 * Los 66 salían de un presupuesto que ya no existe: `M + fade ≤ 162`, la
 * ecuación que mantenía el `SALDO TOTAL` sobre la barra sin desplazar. El dueño
 * retiró esa regla el 2026-09-25 al elegir el mapa alto — ver
 * `ElDineroPideUnSoloDesplazamientoTest`—, así que este número dejó de estar
 * atado al dinero y pasa a decidirse por lo único que le queda: **cuánto se ve
 * sobresalir la banda**.
 *
 * Bajarlo más **sí tiene un piso**: sin recorrido aparece una línea donde el
 * degradado empieza, que es el canto que esta pieza existe para no tener. Lo
 * cobra `ElTelonNoDejaHuecoTest` para la otra orilla y el ojo para ésta.
 */
val MSP_SOFT_EDGE_FADE: Dp = 24.dp

/**
 * Cuánto desenfoca la tira de atrás. 20 dp: por debajo de ~12 el efecto no se
 * distingue de una simple opacidad y deja de valer lo que cuesta.
 */
val RADIO_DEL_DESENFOQUE: Dp = 20.dp

/**
 * La fracción de alto donde el degradado llega a opacidad **sólida**.
 *
 * 0.44, copiado del piloto junto con su razón —ver el KDoc de
 * [MspSoftEdgeActionBar]—. Es también la fracción de la rampa de alpha del
 * desenfoque, para que las dos capas terminen de cerrar en el mismo renglón y
 * no se vea un segundo canto adentro del primero.
 *
 * **Es público a propósito.** Desde que la barra se disuelve, "donde empieza la
 * barra" y "donde la barra tapa" son dos renglones distintos, y el segundo es
 * el que decide si el `SALDO TOTAL` se ve. Una prueba que quiera afirmarlo
 * tiene que multiplicar por este número; escribir `0.44` a mano en el test es
 * cómo se llega al día en que la implementación cambia y la prueba sigue verde.
 */
@Suppress("MagicNumber")
const val MSP_SOFT_EDGE_SOLID_STOP: Float = 0.44f

/**
 * **Dónde arranca la rampa dentro del fade: en el principio — reparte la caída
 * en todo el recorrido.**
 *
 * ## Era 0.5, y la razón de aquel 0.5 ya no aplica
 *
 * Dejaba la mitad de arriba del fade completamente transparente y metía toda la
 * subida en la mitad de abajo. Existía porque la rampa era una **recta**, que
 * empieza a morder con pendiente plena desde su primer píxel: sin esa mitad
 * limpia, la banda se comía el renglón que va justo encima — lo que el dueño
 * reportó dos veces el mismo día, en las dos orillas.
 *
 * Desde que la subida es una **ese** ([rampaSuave]) eso dejó de ser cierto: la
 * curva arranca con pendiente cero, así que su primer tercio es imperceptible
 * por construcción. Un arranque en 0 ya no significa "atenúa desde arriba"
 * — significa **"reparte la caída en todo el fade"**, que con el fade corto de
 * hoy es justamente lo que evita que reaparezca el canto.
 *
 * Los dos números se movieron juntos y hay que leerlos juntos: 48 dp de fade con
 * la rampa en la mitad de abajo dan 24 dp de subida, y 24 dp con la rampa entera
 * dan **los mismos 24** — pero repartidos sobre una curva en vez de una recta, y
 * con la banda sobresaliendo 24 dp menos por encima de los botones. Esa segunda
 * mitad es lo que el dueño pidió.
 *
 * Subirlo a 1 sigue siendo el extremo malo: la rampa se queda sin recorrido y
 * aparece **una línea** donde empieza el velo, que es exactamente el canto que
 * esta pieza existe para no tener.
 */
private const val ARRANQUE_DE_LA_RAMPA = 0f

/**
 * En cuántos tramos se aproxima la ese de [rampaSuave]: **12**.
 *
 * `Brush.verticalGradient` interpola lineal entre paradas, así que la curva se
 * dibuja por tramos rectos. Doce dejan el error contra la curva exacta por
 * debajo de un paso de alpha de 8 bits en los recorridos que esta pieza usa, y
 * la lista sigue siendo lo bastante corta como para reconstruirla en cada dibujo
 * sin que importe.
 */
@Suppress("MagicNumber")
private const val PASOS_DE_LA_RAMPA = 12
