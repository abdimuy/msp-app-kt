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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspBackdrop
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
    nombre: String,
    direccion: String,
    avance: () -> Float,
    desplazamientoPx: () -> Float,
    sinMovimiento: Boolean,
    insetDeArriba: Dp,
    modifier: Modifier = Modifier,
    backdrop: MspBackdrop? = null,
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
        Box(modifier = Modifier.fillMaxSize()) {
            // **La textura va a sangre, detrás de TODO**, incluido el tramo que
            // queda bajo la barra de estado. Con ella encerrada en un margen las
            // dos cadenas caían una encima de la otra y el fondo se leía como un
            // texto mal dibujado en vez de como una textura. Se vio en el golden
            // `pagos_cliente_sin_punto_light`.
            // **El piso se pinta SIEMPRE, con punto y sin él**, y esto es una
            // corrección medida hoy en el aparato del dueño, no una precaución.
            //
            // Estaba condicionado a que NO hubiera punto medido: con punto, el
            // mapa era lo único que se pintaba. Instalado en el SM-A256E sobre
            // un cliente **con** coordenada, el mapa no pintó ni una tesela y
            // lo que quedó fueron **300 dp de negro** — exactamente el estado
            // que este fondo existe para no tener nunca.
            //
            // Es el mismo hallazgo que el cuadro viejo ya había medido en este
            // mismo teléfono (`Authorization failure … INVALID_ARGUMENT`, la
            // llave no autorizaba el paquete de esa build) y que también se da
            // en la calle sin señal la primera vez que se abre una puerta
            // nueva. La regla de las dos capas vuelve a su forma correcta: el
            // piso siempre, el mapa encima cuando puede. **El peor caso pasa a
            // ser el estado aceptable.**
            //
            // Y no estorba al caso bueno: con teselas, el mapa lo tapa entero.
            Box(modifier = Modifier.fillMaxSize().padding(top = insetDeArriba)) {
                FondoSinPunto(calle = calle)
            }
            // El mapa, encima de la textura y sólo con punto medido. Sin punto
            // no hay dónde centrarlo, y centrarlo en cualquier otra cosa diría
            // "es aquí" sobre una puerta que nadie midió.
            if (ubicacion != null) suelo?.invoke(abrir ?: {})
            // **El telón, pegado abajo y por encima del mapa.** Es lo que hace
            // que estos 300 dp no terminen en un canto, y crece hacia arriba
            // cuando la dirección no cabe: come mapa, nunca contenido. Ver
            // `TelonDelNombre`.
            TelonDelNombre(
                nombre = nombre,
                direccion = direccion,
                backdrop = backdrop,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
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
            // `lineHeightStyle` con los dos `Trim`: la rampa da
            // `lineHeight = fontSize * 1.4` y ese 40 % sobrante se reparte
            // arriba y abajo de cada renglón. En una textura de tres renglones
            // eso son ~30 dp de aire muerto que la separan del borde de arriba
            // y la empujan detrás del telón.
            style = MspTheme.type.metricLarge.copy(lineHeightStyle = SIN_AIRE_DE_LINEA),
            color = MspTheme.colors.onSurface.copy(alpha = TINTA_DEL_FONDO),
            // Tres renglones y **sin elipsis**: de una textura no se recorta,
            // se deja que se salga por abajo. Un "…" en el fondo se leería como
            // un dato incompleto, que es justo lo que no es.
            maxLines = 3,
            overflow = TextOverflow.Clip,
            // **Arriba, no abajo.** La mitad baja de estos 300 dp se la lleva
            // el telón, y con la textura plantada ahí quedaba entera detrás de
            // su degradado: invisible. Se vio en el golden
            // `pagos_cliente_sin_punto_dark`, con el fondo completamente negro.
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = MspTheme.spacing.md)
                .padding(top = MspTheme.spacing.lg)
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
 * El recorte del aire de línea de la textura.
 *
 * Vivía en `PiezasDelCliente.kt` mientras la seña del fondo existía; se mudó
 * acá con su único consumidor cuando esa seña se retiró.
 */
private val SIN_AIRE_DE_LINEA = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both
)

/**
 * **Cuánto del fondo se ve antes de que empiece el contenido: 300 dp.**
 *
 * ## Lo que este número reemplazó, y por qué
 *
 * Fue 152, luego 118, luego **96**, cada bajada peleada contra el mismo
 * invariante: que el `SALDO TOTAL` cupiera entero sobre la barra sin desplazar.
 * El presupuesto era `M + fade ≤ 162` y el reparto final —96 de mapa, 66 de
 * disolución— lo agotaba exacto, con margen cero.
 *
 * **El 2026-09-25 el dueño vio ese resultado en su teléfono y lo rechazó**:
 * *"está horrible y no se parece en nada al mock"*. Tenía razón, y la causa era
 * del mock, no de la medición: estaba dibujado a 390×844 —proporciones de
 * iPhone— sobre una pantalla real de 360×744, así que prometía un mapa que no
 * cabía. Rehecho a las medidas reales y con la línea de flotación marcada,
 * **eligió el mapa alto sabiendo el costo**.
 *
 * ## El costo, dicho sin adornos
 *
 * **El `SALDO TOTAL` queda abajo del pliegue.** Hay que desplazar para verlo.
 * Es una reversión consciente de lo que se protegió durante dos días, decidida
 * por el dueño con el número delante. Lo que ya NO rige es "el dinero cabe"; lo
 * que rige ahora es "el dinero no se aleja más", y eso lo ancla
 * `ElDineroPideUnSoloDesplazamientoTest` con la cifra medida.
 *
 * ## Y a las escalas grandes mide lo mismo
 *
 * [FONDO_APRETADO] también son 300. El fondo dejó de apretarse con la letra
 * porque ya no compite con el dinero por los mismos dp: desde que el saldo vive
 * abajo del pliegue, encoger el mapa a 2.0 no le devuelve nada a nadie —sólo
 * deja un mapa chico y un telón que igual crece con la tipografía—.
 */
@Composable
fun altoDelFondo(): Dp =
    if (LocalFontSizeLevel.current == FontSizeLevel.NORMAL) FONDO_NORMAL else FONDO_APRETADO

private val FONDO_NORMAL: Dp = 300.dp

private val FONDO_APRETADO: Dp = 300.dp

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
