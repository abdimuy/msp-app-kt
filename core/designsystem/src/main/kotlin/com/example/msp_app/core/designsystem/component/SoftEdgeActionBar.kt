package com.example.msp_app.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
    contenido: @Composable ColumnScope.() -> Unit
) {
    val fondo = MspTheme.colors.background
    Column(
        modifier = modifier
            .fillMaxWidth()
            .mspBackdropBar(backdrop)
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to fondo.copy(alpha = 0f),
                        MSP_SOFT_EDGE_SOLID_STOP to fondo,
                        1f to fondo
                    )
                )
            )
            .padding(top = fade),
        content = contenido
    )
}

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
    /** Alto TOTAL de la barra en px —degradado incluido—, que ella misma mide. */
    internal val altoDeLaBarraPx: MutableIntState,
    /** Lo mismo para la cabecera de arriba, cuando la pantalla tiene una. */
    internal val altoDeLaCabeceraPx: MutableIntState
) {
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

/** Crea el [MspBackdrop] de una pantalla. Uno por pantalla, no uno por barra. */
@Composable
fun rememberMspBackdrop(): MspBackdrop {
    val alto = remember { mutableIntStateOf(0) }
    val cabecera = remember { mutableIntStateOf(0) }
    return remember { MspBackdrop(alto, cabecera) }
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
        val radio = RADIO_DEL_DESENFOQUE.toPx()
        borroso.renderEffect = BlurEffect(radio, radio, TileMode.Decal)
        superior.renderEffect = BlurEffect(radio, radio, TileMode.Decal)
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
                tiraConRampa(superior, 0f, altoCabecera.toFloat(), haciaAbajo = false)
            }
            val alto = backdrop.altoDeLaBarraPx.intValue
            if (alto <= 0) return@onDrawWithContent
            val altoTira = minOf(alto, size.height.toInt())
            val arriba = size.height - altoTira
            borroso.record(size = IntSize(size.width.toInt(), altoTira)) {
                translate(top = -arriba) { drawLayer(nitido) }
            }
            borroso.topLeft = IntOffset(0, arriba.toInt())
            tiraConRampa(borroso, arriba, altoTira.toFloat(), haciaAbajo = true)
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
 * arriba y cierra abajo; la cabecera de arriba va al revés. Las dos usan la
 * MISMA fracción ([MSP_SOFT_EDGE_SOLID_STOP]) para que el desenfoque y el
 * degradado terminen de cerrar en el mismo renglón y no se vea un segundo canto
 * adentro del primero.
 */
private fun DrawScope.tiraConRampa(
    capa: GraphicsLayer,
    arriba: Float,
    alto: Float,
    haciaAbajo: Boolean
) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(Offset(0f, arriba), Size(size.width, alto)), Paint())
        drawLayer(capa)
        val paradas = if (haciaAbajo) {
            arrayOf(
                0f to Color.Transparent,
                MSP_SOFT_EDGE_SOLID_STOP to Color.Black,
                1f to Color.Black
            )
        } else {
            arrayOf(
                0f to Color.Black,
                1f - MSP_SOFT_EDGE_SOLID_STOP to Color.Black,
                1f to Color.Transparent
            )
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
 * Lo que la barra reserva **arriba** de sus botones para disolverse.
 *
 * **40 dp, y el número lo fijó un golden, no un gusto.**
 *
 * ## La primera medición se quedó corta, y el golden lo enseñó
 *
 * El primer valor fueron 80 dp, para dar los ~176 dp de recorrido total que el
 * dueño aprobó. El criterio con el que se aceptó era la *lectura blanda*: que el
 * `SALDO TOTAL` quedara arriba del **punto opaco** del degradado (el 44 %).
 * Medido daba 7.4 dp de margen y el test estaba verde.
 *
 * **El golden lo desmintió.** Con el saldo dentro de la banda pero por encima
 * del punto opaco, el degradado ya lo cubre al **90 %** y la capa de desenfoque
 * lo borronea: en `pagos_cliente_light_1_0` el `$3,550` salía gris pálido y
 * desenfocado. Eso no es "caber entero sobre la barra", es estar debajo de ella
 * con el canto disimulado — la clase de verde contra una pantalla rota que este
 * repo documenta una y otra vez.
 *
 * Así que la línea de flotación pasó a ser la dura: **el dinero tiene que quedar
 * arriba de donde la banda EMPIEZA**, no de donde termina de cerrar.
 *
 * ## El presupuesto, con la regla dura
 *
 * Con `M` el alto visible del fondo del mapa y `fade` este número:
 *
 * ```
 * saldo.bottom = 486 + M        la banda empieza en 744 − (fade + 96)
 * cabe  ⟺  M + fade ≤ 162
 * ```
 *
 * Los 96 dp son el dock del detalle de cliente a letra normal, medido. El
 * reparto de esos 162 dp es una decisión del dueño y está documentada en el
 * KDoc de `altoDelFondo`; 40 + 122 es el punto que deja una disolución real
 * —se ve a través de los primeros 60 dp de la banda— con el mapa todavía
 * **1.34 veces** el área del cuadro viejo.
 *
 * Subirlo tapa el dinero dp por dp. La regla del repo es subir la
 * implementación, no bajar el test: ver `ElDineroNoSeMeteBajoLaBarraTest`.
 */
val MSP_SOFT_EDGE_FADE: Dp = 40.dp

/**
 * Cuánto desenfoca la tira de atrás. 20 dp: por debajo de ~12 el efecto no se
 * distingue de una simple opacidad y deja de valer lo que cuesta.
 */
private val RADIO_DEL_DESENFOQUE: Dp = 20.dp

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
