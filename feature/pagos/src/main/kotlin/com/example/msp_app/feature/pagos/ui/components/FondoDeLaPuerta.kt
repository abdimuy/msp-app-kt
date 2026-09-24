package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro

/** `testTag` del fondo de la puerta — la capa que reemplazó al cuadro de 100 dp. */
const val FONDO_DE_LA_PUERTA_TAG: String = "pagos_cliente_fondo_puerta"

/** `testTag` de la dirección en grande y tenue que hace de fondo sin punto medido. */
const val FONDO_SIN_PUNTO_TAG: String = "pagos_cliente_fondo_sin_punto"

/**
 * `testTag` del hueco transparente con el que arranca el contenido — **el que
 * de verdad recibe el toque del mapa**.
 *
 * No es un detalle de implementación que se pueda mover: el contenido
 * desplazable se dibuja ENCIMA del fondo y su `scrollable` se queda con el
 * evento, así que un `clickable` en la capa del fondo nunca se enteraría. Ver
 * el KDoc de [FondoDeLaPuerta].
 */
const val TOQUE_DEL_FONDO_TAG: String = "pagos_cliente_toque_fondo"

/**
 * **El mapa dejó de ser una banda y pasó a ser el FONDO de la pantalla.**
 *
 * ## Qué cambió, y qué costó
 *
 * Era `CuadroDeLaPuerta`: un bloque de 100 dp con 16 dp de margen a cada lado,
 * metido en la hoja de identidad, que **ocupaba alto propio**. Ahora ocupa el
 * ancho completo, sangra hasta arriba —por debajo de la barra de estado— y el
 * contenido flota encima en tarjetas. De fondo **no cuesta un solo dp de la
 * pila**: lo que cuesta es dónde arranca el contenido, y eso es [altoDelFondo].
 *
 * ## [altoDelFondo] son 152 dp y NO los 296/844 del mock — está medido
 *
 * El mock venía de un lienzo de 844 px de alto con proporciones de iPhone.
 * Llevado a la raíz real de esta pantalla (744 dp en `w360dp-h800dp`) eso serían
 * **261 dp**, y ahí **el `SALDO TOTAL` deja de caber sobre la barra**: medido,
 * el bloque del saldo terminaría en 736.0 dp contra un dock que empieza en
 * 655.0 — **81 dp de dinero tapado**, antes de sumarle el degradado.
 *
 * El presupuesto se midió quitando la banda vieja y volviendo a medir: el saldo
 * sube de 651.0 a 551.0, o sea **104 dp** de margen total para todo lo que este
 * fondo empuje hacia abajo. Y el degradado de la barra compite por el **mismo**
 * presupuesto: cada dp de degradado baja el punto opaco 0.56 dp. La ecuación
 * completa, con `M` el alto visible de este fondo y `T` el alto total de la
 * barra blanda:
 *
 * ```
 * saldo.bottom = 486 + M        punto opaco = 744 − 0.56·T
 * cabe  ⟺  M + 0.56·T ≤ 258
 * ```
 *
 * Con `T = 176` —los que el dueño aprobó para el degradado— el techo es
 * `M ≤ 159.4`. **152 deja 7.4 dp de margen**, más que los 4.0 que tenía la
 * pantalla antes del rediseño. La regla que decidió el número es del dueño y se
 * escribe sin adornos: **el mapa cede, el dinero no.**
 *
 * En área son ~1.7 veces el cuadro viejo (360×152 contra 328×100), no las
 * cuatro del mock. Se sabe, se dijo, y el dueño lo aprobó con el número
 * delante.
 *
 * ## Las dos capas siguen siendo dos, y por la misma razón medida
 *
 * El piso se pinta **siempre** y el mapa encima **sólo con punto medido**. No es
 * precaución: instalado en el SM-A256E, el mapa no pintó ni una tesela
 * —`Authorization failure … INVALID_ARGUMENT`— y lo que se veía era la retícula
 * gris con el logo de Google, o sea *un mapa que no cargó*. El mismo caso se da
 * en la calle sin señal. Con el piso debajo, el peor caso es el estado
 * aceptable.
 *
 * ## Sin punto medido el fondo es la DIRECCIÓN, no un dibujo
 *
 * Decisión del dueño para esta pasada: *"es la dirección misma en grande y muy
 * tenue, con la seña legible encima"*. Ver [FondoSinPunto]. El esqueleto es
 * idéntico al del caso con punto —mismo alto, mismo arranque de contenido— para
 * que la pantalla **no salte** entre un cliente con punto y uno sin él.
 *
 * ## El toque NO lo recibe esta capa, y no puede: lo recibe el hueco de arriba
 *
 * Medido, y es consecuencia directa de haber pasado a fondo. El contenido
 * desplazable ocupa la pantalla **entera** y se dibuja encima; su `scrollable`
 * registra un manejador de puntero, así que el hit-test de Compose se detiene
 * ahí y **nunca llega al hermano de abajo**. Un `clickable` en esta capa se
 * vería tocable en el árbol de semántica y no dispararía nunca — la clase de
 * control muerto que este repo ya arregló dos veces.
 *
 * Así que el afordante vive en el **hueco transparente** con el que arranca el
 * contenido (`TOQUE_DEL_FONDO_TAG`, en `DetalleClienteScreen`): mide lo mismo,
 * está justo encima del fondo y se va con él al desplazar. Lleva la misma
 * descripción para TalkBack.
 *
 * Lo mismo vale para el `onMapClick` del suelo: con la capa del desplazamiento
 * arriba, el SDK de Google ya no ve el toque —lo cual, de paso, cierra por
 * construcción el defecto del `liteMode` que abría la app de Google Maps—. Se
 * le sigue pasando [onVerUbicacion] porque el contrato de la ranura no cambió y
 * porque es el mismo destino.
 *
 * ## Lo que NO se hizo, y por qué
 *
 * **El pin no se apaga al doble de velocidad.** En el mock el pin era un dibujo
 * propio; acá es un `Marker` **dentro del bitmap de Google** y no hay forma de
 * apagarlo aparte del mapa. Dibujar uno propio encima está prohibido por
 * medición: el golden midió **21 dp** de corrimiento del pin dibujado contra el
 * objetivo de la cámara, ~24 m a zoom 17, y un pin que no cae exacto miente. El
 * pin se va con el mapa, a su misma velocidad, por decisión del dueño.
 *
 * **"Cómo llegar" no se apaga sin punto.** El mock proponía convertirlo en un
 * "Sin punto" gris. Sin coordenada esa acción abre el mapa con la **dirección
 * escrita**, que en una colonia sin numeración es la diferencia entre llegar a
 * la puerta y llegar a la calle; y que el botón cambie según si hubo un cobro
 * con GPS es el defecto que `5417e65e` cerró. Descartado por el dueño.
 *
 * @param avance 0 arriba del todo, 1 cuando el fondo ya terminó de irse.
 * @param desplazamientoPx cuánto se ha desplazado el contenido, en px. Va
 *   aparte de [avance] porque el paralaje es **lineal** con el desplazamiento
 *   mientras que lo demás sigue la curva.
 */
@Composable
fun FondoDeLaPuerta(
    ubicacion: UbicacionDelCobro?,
    calle: String,
    zona: String,
    avance: () -> Float,
    desplazamientoPx: () -> Float,
    sinMovimiento: Boolean,
    insetDeArriba: Dp,
    modifier: Modifier = Modifier,
    onVerUbicacion: (() -> Unit)? = null,
    suelo: (@Composable (onTocar: () -> Unit) -> Unit)? = null
) {
    val abrir = onVerUbicacion.takeIf { ubicacion != null }
    val radio = RADIO_DEL_DESENFOQUE
    Box(
        modifier = modifier
            .fillMaxWidth()
            // **Sangra hasta arriba del todo.** El `Box` padre consume el inset
            // de la barra de estado —lo exige `CadaPantallaDeCobranzaRespeta…`
            // para que ningún CONTROL quede bajo la ventana del sistema— y este
            // desplazamiento negativo es la forma de decir que el FONDO sí pasa
            // por debajo, que es justo lo que `enableEdgeToEdge()` quiere. El
            // `Box` no recorta a sus hijos, así que se dibuja y no se pierde.
            .offset(y = -insetDeArriba)
            .height(altoDelFondo() + insetDeArriba)
            .graphicsLayer {
                // Todo se lee DENTRO del bloque: así el desplazamiento vuelve a
                // dibujar sin volver a componer. Leerlo afuera pondría una
                // recomposición por cuadro en una pantalla que además está
                // hospedando un mapa vivo.
                val cerrado = avance().coerceIn(0f, 1f)
                val curva = if (sinMovimiento) cerrado else CURVA_DE_SALIDA.transform(cerrado)
                alpha = 1f - curva
                if (sinMovimiento) return@graphicsLayer
                translationY = -PARALAJE * desplazamientoPx()
                transformOrigin = ORIGEN_DEL_ENCOGIMIENTO
                scaleX = 1f - ENCOGIMIENTO * curva
                scaleY = scaleX
                // `renderEffect` y no `Modifier.blur`: el desenfoque queda en el
                // bloque de dibujo, no en la composición, y el propio Compose lo
                // ignora por debajo de Android 12 —lo que ahí queda es encoger y
                // apagar, que es el respaldo y se ve bien igual—.
                val radioPx = radio.toPx() * curva
                renderEffect = if (radioPx > 0f) {
                    BlurEffect(radioPx, radioPx, TileMode.Decal)
                } else {
                    null
                }
            }
            .testTag(FONDO_DE_LA_PUERTA_TAG)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(top = insetDeArriba)) {
            // **La textura va a sangre, detrás de TODO** —incluido el renglón
            // del nombre—, y por eso se pinta fuera del margen de abajo. Con
            // ella encerrada en la misma franja que la seña legible, las dos
            // cadenas caían una encima de la otra y el fondo se leía como un
            // texto mal dibujado en vez de como una textura. Se vio en el golden
            // `pagos_cliente_sin_punto_light`.
            if (ubicacion == null) FondoSinPunto(calle = calle)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // **La seña no puede bajar hasta donde va el nombre.** El
                    // renglón del título flota sobre los últimos
                    // [ALTO_DEL_NOMBRE_SOBRE_EL_FONDO] dp de este fondo —es lo
                    // que hace que el nombre se lea sobre el mapa, como en el
                    // mock—, y sin este margen la calle de la seña se escribía
                    // encima del nombre. Se vio en el golden
                    // `pagos_cliente_light_1_0`.
                    .padding(bottom = ALTO_DEL_NOMBRE_SOBRE_EL_FONDO)
            ) {
                SenasDelFondo(calle = calle, zona = zona)
            }
            // El mapa, encima de las señas y sólo con punto medido. Sin punto
            // no hay dónde centrarlo, y centrarlo en cualquier otra cosa diría
            // "es aquí" sobre una puerta que nadie midió.
            if (ubicacion != null) suelo?.invoke(abrir ?: {})
        }
    }
}

/**
 * **El fondo cuando NO hay punto medido: la dirección misma, en grande y muy
 * tenue.**
 *
 * No es un mapa ni un dibujo. Un mapa que no se puede centrar sería mentira y
 * un dibujo de calles se confunde con la traza real de la colonia —ese riesgo
 * ya está documentado y por eso la retícula del mock nunca se implementó—. Lo
 * que queda es el dato que sí existe, usado como textura: la dirección escrita,
 * a [TAMAÑO_DEL_FONDO_SIN_PUNTO] y con alpha [TINTA_DEL_FONDO], detrás de la
 * misma seña legible que llevaría encima de un mapa.
 *
 * El esqueleto es **idéntico** al del caso con punto: mismo alto, mismo arranque
 * de contenido, misma seña arriba. Que la pantalla no salte entre un cliente
 * con punto y uno sin él es la mitad del valor de este diseño — la otra mitad es
 * que nunca se lee como una pantalla a medio cargar.
 */
@Composable
private fun FondoSinPunto(calle: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = calle.ifBlank { SIN_DIRECCION },
            style = MspTheme.type.metricLarge.copy(lineHeightStyle = SIN_AIRE_DE_LINEA),
            color = MspTheme.colors.onSurface.copy(alpha = TINTA_DEL_FONDO),
            // Tres renglones y **sin elipsis**: de una textura no se recorta,
            // se deja que se salga por abajo. Un "…" en el fondo se leería como
            // un dato incompleto, que es justo lo que no es.
            maxLines = 3,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = MspTheme.spacing.md)
                .testTag(FONDO_SIN_PUNTO_TAG)
        )
    }
}

/** Cuánto encoge el fondo al irse: 7 %, desde un origen alto. */
private const val ENCOGIMIENTO = 0.07f

/** A qué fracción de la velocidad del contenido se desliza el fondo. */
private const val PARALAJE = 0.42f

/** El origen del encogimiento: `50% 30%`, alto y centrado. */
private val ORIGEN_DEL_ENCOGIMIENTO = TransformOrigin(0.5f, 0.3f)

/** Cuánto llega a desenfocarse el fondo antes de apagarse del todo. */
private val RADIO_DEL_DESENFOQUE = 14.dp

/** Qué tan tenue va la dirección que hace de fondo sin punto medido. */
private const val TINTA_DEL_FONDO = 0.10f

/**
 * **Cuánto del fondo se ve antes de que empiece el contenido.**
 *
 * 152 dp a letra normal y [FONDO_APRETADO] a las grandes. Medido, no elegido —
 * ver el KDoc de [FondoDeLaPuerta] para la ecuación y para por qué no son los
 * 261 que daría el mock. **Subirlo tapa el saldo**, y la regla del repo es
 * subir la implementación, no bajar el test.
 */
@Composable
fun altoDelFondo(): Dp =
    if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) FONDO_NORMAL else FONDO_APRETADO

private val FONDO_NORMAL: Dp = 118.dp

/**
 * **Lo que el fondo mide a `GRANDE` y `MUY_GRANDE`: 112 dp.**
 *
 * El cuadro viejo hacía exactamente esto —100 dp a normal, 40 a las grandes— y
 * por la misma razón medida: a esas escalas cada renglón crece y el dock **se
 * apila en dos**, así que el dinero se queda sin sitio. Con el fondo a 152 el
 * bloque de la parcialidad terminaba en **617.5 dp** contra un dock que empieza
 * en **612.0**: 5.5 dp tapados en el caso *sin nota*, que antes de este
 * rediseño sí cabía. A 112 vuelve a caber con margen.
 *
 * Es el mismo criterio de siempre, aplicado al dibujo nuevo: **entre un dibujo
 * y un dato, cede el dibujo.**
 */
private val FONDO_APRETADO: Dp = 96.dp

/**
 * **Cuánto del fondo se lleva el renglón del nombre.**
 *
 * 64 dp: los 56 del renglón del título más su aire de arriba. El nombre vive
 * DENTRO del desplazamiento y flota sobre el último tramo del fondo —así es
 * como el mock lo pide, y es lo que deja que el encabezado compacto entre
 * después sin decir el nombre dos veces—.
 *
 * Lo usan dos sitios y por eso es público: este archivo, para no escribir la
 * seña encima del nombre, y `DetalleClienteScreen`, para que el hueco con el
 * que arranca el contenido deje el título justo ahí. Si los dos números se
 * separaran, la seña volvería a chocar con el nombre.
 */
val ALTO_DEL_NOMBRE_SOBRE_EL_FONDO: Dp = 64.dp

/**
 * **El recorrido de la animación: 230 dp.**
 *
 * Es lo que el dueño aprobó. Más largo que [altoDelFondo] a propósito: el
 * fondo termina de apagarse **después** de que su última franja salió de
 * pantalla, así que nunca se ve un corte seco en el borde de arriba.
 */
val RECORRIDO_DEL_FONDO: Dp = 230.dp

/**
 * La curva del recorrido: **arranca lento y termina rápido**.
 *
 * Es lo que hace que los primeros dedos de desplazamiento casi no toquen el
 * fondo —el cobrador que sólo quiere ver el saldo no siente que le movieron la
 * pantalla— y que el final sea decidido en vez de una desaparición larga.
 */
@Suppress("MagicNumber")
private val CURVA_DE_SALIDA = CubicBezierEasing(0.4f, 0f, 1f, 1f)
