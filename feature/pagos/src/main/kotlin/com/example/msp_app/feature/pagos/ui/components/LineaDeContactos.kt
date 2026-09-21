package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import com.example.msp_app.feature.pagos.domain.GrupoDeContactos
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import com.example.msp_app.feature.pagos.ui.TratoDelEstado
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** `testTag` de una fila de la línea de contactos. */
const val CONTACTO_EN_LINEA_TAG: String = "pagos_contacto_linea"

/** `testTag` del encabezado de un tramo — el mes, o la cercanía. */
const val ENCABEZADO_DE_GRUPO_TAG: String = "pagos_grupo_encabezado"

/** `testTag` del subtotal cobrado de un tramo. */
const val COBRADO_DEL_GRUPO_TAG: String = "pagos_grupo_cobrado"

/**
 * `testTag` del punto de estado de una fila.
 *
 * Existe **sólo** para poder medirlo: el punto es un `Box` con fondo y sin
 * texto, así que sin esta etiqueta no hay nodo en el árbol de semántica y su
 * tamaño no se puede cobrar con un assert. Lo que el tag hace medible es el
 * arreglo de un defecto real —el punto no escalaba con la letra— y no un
 * detalle estético: en la fila el punto es el **único** portador del estado.
 */
const val PUNTO_DEL_ESTADO_TAG: String = "pagos_contacto_punto"

/** `testTag` del pin de una fila — existe para medir dónde cae. */
const val PIN_DEL_CONTACTO_TAG: String = "pagos_contacto_pin"

/** Prefijo del `testTag` de cada pastilla de filtro, más el `name` del filtro. */
const val FILTRO_TAG: String = "pagos_filtro_"

/**
 * **Una línea de la bitácora, con todo lo que el hecho sabe de sí mismo.**
 *
 * Lo que la fila anterior tiraba a la basura: la nota, el importe, el estado, la
 * **hora**, la **forma de pago** y el **cobrador**. Sólo dejaba etiqueta y
 * fecha, así que cuatro contactos distintos se veían como cuatro renglones
 * iguales — ése era el defecto, no el espaciado.
 *
 * ## Cuatro pistas y una sola regla (mock `fila-de-contactos.html`, 03 y 04)
 *
 * **Cuándo · Estado · Qué pasó · Cuánto.** La regla es una: **la primera línea
 * del título manda**. El día y el importe se alinean por su `FirstBaseline`
 * contra la del título; el punto y el pin, que no tienen letra, se centran en
 * el renglón de esa misma línea ([EnElRenglonDelTitulo]). Ningún elemento lleva
 * un `padding(top)` propio.
 *
 * Una diferencia con el mock, a propósito: **Cuánto** vive en el renglón del
 * título, no en una columna de alto completo, para que el renglón de abajo
 * corra por debajo del importe. Ver el comentario en el cuerpo.
 *
 * Así era antes, y ése era el defecto: la hora, el punto y el pin se empujaban
 * cada uno con su margen —`sm + xs`, `md × escala`, `sm`— persiguiendo a mano
 * la primera línea del título. Cuando la letra crecía o el título se iba a dos
 * renglones, los tres se despegaban. Con una línea base compartida no hay nada
 * que recalcular: lo cobra `LaFilaCaeSobreUnaSolaLineaBaseTest`, midiendo.
 *
 * ## El día arriba, la hora abajo, al margen
 *
 * Dentro de un tramo mensual la hora sola no contesta *cuándo* fue. El día va
 * en negrita y la hora debajo, alineados a la derecha y tabulares
 * ([MspTheme] ya lo resuelve en `caption`), así la columna se lee de corrido y
 * no baila entre `09:20` y `18:05`.
 *
 * ## El renglón de abajo: cuenta · método · cobrador
 *
 * La cuenta es lo que distingue dos abonos del mismo minuto a dos ventas
 * distintas (mock, sección 02). Cuando `cuenta` es `null` se pinta sólo lo que
 * sí hay — nunca un texto de relleno. La forma de pago no aparece en las
 * visitas, y es lo correcto: `ContactoDeCobranza.metodo` llega en `null` en
 * toda visita —ver su KDoc: la columna existe pero el escritor la deja en 0 y
 * eso se leería como *"efectivo"*—. El cobrador se conserva tal como llega.
 *
 * ## [deEstaVenta] sólo lo usa el detalle de venta
 *
 * Marca las filas que sí son de la cuenta abierta dentro de la línea de tiempo
 * del cliente entero. Es un borde de marca, no un fondo: un relleno haría que
 * media lista pareciera seleccionada. Se dibuja detrás de la fila y su canalón
 * se reserva siempre, marcada o no, para que marcar no corra el contenido.
 */
@Composable
fun ContactoEnLinea(
    contacto: ContactoDeCobranza,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false,
    deEstaVenta: Boolean = false,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null
) {
    val abrir = abridorDe(contacto, onVerUbicacion)
    val marca = if (deEstaVenta) MspTheme.colors.brand else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (abrir != null) {
                    Modifier
                        .clickable(onClick = abrir)
                        .semantics { contentDescription = VER_DONDE_FUE }
                } else {
                    Modifier
                }
            )
            .heightIn(min = ALTO_TOCABLE)
            // La marca de "esto es de la venta abierta": una barra al BORDE de
            // la fila y de su alto completo. Se DIBUJA detrás en vez de ser un
            // hijo del Row porque un hijo de alto completo obligaba a medir la
            // fila con `IntrinsicSize.Min`, y la medición intrínseca no conoce
            // la alineación por línea base: la fila quedaba más baja que su
            // contenido alineado y pisaba a la siguiente.
            .drawBehind {
                drawRoundRect(
                    color = marca,
                    size = Size(MARCA_DE_LA_VENTA.toPx(), size.height),
                    cornerRadius = CornerRadius(MARCA_DE_LA_VENTA.toPx() / 2f)
                )
            }
            .padding(start = MARCA_DE_LA_VENTA + MspTheme.spacing.sm)
            .padding(vertical = MspTheme.spacing.sm)
            .testTag(CONTACTO_EN_LINEA_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        val cuando = AppTime.toBusinessDateTime(contacto.fecha)
        // Cuándo. Cada texto a lo ancho de la columna: el ancho fijo es de la
        // PISTA, y así la pista se mide igual en los dos renglones.
        Column(
            modifier = Modifier
                .width(anchoDeCuando())
                .alignBy(FirstBaseline),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = diaDe(cuando),
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = HORA.format(cuando),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }
        // Estado.
        EnElRenglonDelTitulo(modifier = Modifier.alignBy(FirstBaseline)) {
            Box(
                modifier = Modifier
                    .size(puntoDelEstado())
                    .clip(RoundedCornerShape(percent = 50))
                    .background(acentoDe(contacto))
                    .testTag(PUNTO_DEL_ESTADO_TAG)
            )
        }
        // Qué pasó y Cuánto. El importe y el pin van en el renglón del título
        // y no en una columna propia de alto completo: así el renglón de
        // abajo corre a todo lo ancho, por debajo del importe. En una columna
        // aparte (la rejilla literal del mock) a `MUY_GRANDE` la cuenta
        // quedaba en "Refrigera…" — la cuenta es justo lo que esta fila vino
        // a decir.
        Column(
            modifier = Modifier
                .weight(1f)
                .alignBy(FirstBaseline)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
                Text(
                    text = contacto.etiqueta,
                    style = estiloDelTitulo(),
                    color = MspTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alignBy(FirstBaseline)
                )
                if (contacto.importe != null) {
                    MspMoneyText(
                        amount = contacto.importe.amount,
                        masked = ocultos,
                        style = MspTheme.type.amountInline,
                        color = MspTheme.colors.onSurface,
                        modifier = Modifier.alignBy(FirstBaseline)
                    )
                }
                EnElRenglonDelTitulo(modifier = Modifier.alignBy(FirstBaseline)) {
                    PinDelContacto(
                        hayPunto = contacto.ubicacion != null,
                        modifier = Modifier.testTag(PIN_DEL_CONTACTO_TAG)
                    )
                }
            }
            val meta = renglonDeAbajo(contacto)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = estiloDeProsa(),
                    color = MspTheme.colors.onSurfaceMuted,
                    // Dos renglones y no uno: a escala grande, en uno solo la
                    // cuenta se come al método y al cobrador.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            contacto.nota?.let {
                Text(
                    text = "“$it”",
                    style = estiloDeProsa(),
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * El renglón de abajo: `cuenta · método · cobrador`, con lo que haya.
 *
 * Lo ausente se omite —con su separador—, nunca se rellena: una cuenta `null`
 * no es "sin cuenta", es que ese dato no llegó.
 */
private fun renglonDeAbajo(contacto: ContactoDeCobranza): String = listOfNotNull(
    contacto.cuenta?.takeIf { it.isNotBlank() },
    contacto.metodo?.etiqueta,
    contacto.cobrador.takeIf { it.isNotBlank() }
).joinToString(SEPARADOR_DEL_RENGLON)

/**
 * El punto medio entre las piezas del renglón de abajo. El espacio de ANTES es
 * no separable: cuando el renglón se parte en dos —a escala grande pasa—, el
 * punto se queda al final del primer renglón con su pieza y no abre el
 * segundo como `· Transferencia`.
 */
internal const val SEPARADOR_DEL_RENGLON: String = "\u00A0· "

/**
 * `caption` con cifras **proporcionales**, para el renglón de abajo y la nota.
 *
 * `caption` trae cifras tabulares, que es lo correcto en una columna de horas
 * y un defecto en prosa: con la cuenta en el renglón llegan nombres de
 * producto con números, y en tabular `Refrigerador Mabe 14'` se pintaba
 * `1 4'` — un número partido.
 */
@Composable
private fun estiloDeProsa(): TextStyle = MspTheme.type.caption.copy(fontFeatureSettings = "lnum")

/**
 * El encabezado de un tramo: su nombre y, a la derecha, lo que entró.
 *
 * El hairline que los separa va **entre** el nombre y el subtotal y no debajo:
 * así el renglón se lee como una sola cosa —*"septiembre trajo $450"*— en vez de
 * como un título con un número suelto al lado.
 *
 * [GrupoDeContactos.cobrado] en `null` no pinta nada. Un `$0` ahí diría que ese
 * mes se midió y dio cero, cuando lo que pasó es que sólo hubo visitas.
 */
@Composable
fun EncabezadoDeGrupo(
    grupo: GrupoDeContactos,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MspTheme.spacing.md, bottom = MspTheme.spacing.xs)
            .testTag(ENCABEZADO_DE_GRUPO_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = grupo.titulo,
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MspTheme.colors.outline)
        )
        grupo.cobrado?.let {
            MspMoneyText(
                amount = it.amount,
                masked = ocultos,
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag(COBRADO_DEL_GRUPO_TAG)
            )
        }
    }
}

/**
 * Las pastillas que deciden qué se enseña.
 *
 * Sólo en las listas largas — ver el KDoc de [FiltroDeContactos]. Van en una
 * fila que se desplaza a lo ancho: a escala de letra grande las cuatro no caben
 * en 360 dp, y **apilar filtros los volvería dos renglones fijos de alto** en
 * pantallas que ya están apretadas. Desplazarse es el único de los tres males
 * que no cuesta alto.
 */
@Composable
fun FiltrosDeContacto(
    elegido: FiltroDeContactos,
    onElegir: (FiltroDeContactos) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = MspTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        FiltroDeContactos.entries.forEach { filtro ->
            val activo = filtro == elegido
            Surface(
                onClick = { onElegir(filtro) },
                shape = MspTheme.shapes.chip,
                color = if (activo) MspTheme.colors.brand else MspTheme.colors.surface2,
                modifier = Modifier
                    .heightIn(min = ALTO_DEL_FILTRO)
                    .testTag(FILTRO_TAG + filtro.name)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = MspTheme.spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filtro.etiqueta,
                        style = MspTheme.type.captionStrong,
                        color = if (activo) {
                            MspTheme.colors.onBrand
                        } else {
                            MspTheme.colors.onSurfaceMuted
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * **El color con el que se pinta el punto de estado.**
 *
 * Casi siempre es [EstadoCuentaUi.contenidoDe], pero [TratoDelEstado.ESCALAR]
 * es la excepción y hay que tratarla: su diseño es **relleno sólido invertido**
 * —el contenido va sobre el rojo, no sobre el tint— así que su color de
 * contenido es `onDanger`, o sea blanco. Un punto de 6 dp pintado de blanco
 * sobre una superficie blanca **no existe**: en el golden, "Se negó" —el estado
 * más grave de los ocho— era el único sin punto visible.
 *
 * Para un punto sobre superficie lo que se necesita es el **acento**, y en
 * ESCALAR el acento es su fondo. El mismo defecto vivía en `FilaDeContacto`,
 * la fila de la bitácora que esta pieza reemplazó.
 */
@Composable
private fun acentoDe(contacto: ContactoDeCobranza): Color {
    val trato = EstadoCuentaUi.tratoDe(periodoDe(contacto))
    return if (trato == TratoDelEstado.ESCALAR) {
        EstadoCuentaUi.fondoDe(trato, MspTheme.colors)
    } else {
        EstadoCuentaUi.contenidoDe(trato, MspTheme.colors)
    }
}

/** El `EstadoDelPeriodo` que el catálogo de colores necesita para un contacto. */
private fun periodoDe(contacto: ContactoDeCobranza) = EstadoDelPeriodo(
    estado = contacto.estado,
    abonoDelPeriodo = Money.ZERO,
    parcialidad = Money.ZERO
)

/** `HH:mm`. Tabular por el estilo `caption`, así la columna de horas alinea. */
private val HORA: DateTimeFormatter =
    DateTimeFormatter.ofPattern(AppTime.Formats.TIME_24H, BUSINESS_LOCALE)

/**
 * `18 feb`: el día con dos cifras y el mes abreviado, en minúscula y **sin
 * punto**.
 *
 * El mes sale de una tabla propia y no de `DateTimeFormatter("MMM")`: el
 * abreviado de `es-MX` depende de los datos CLDR de cada teléfono —`feb.` con
 * punto, `sept.` con cuatro letras— y la columna tiene que leerse igual en
 * todos. Es el mismo criterio de `TiempoRelativo`: nada que un locale decida.
 */
internal fun diaDe(fecha: LocalDateTime): String =
    "%02d %s".format(BUSINESS_LOCALE, fecha.dayOfMonth, MESES_ABREVIADOS[fecha.monthValue - 1])

private val MESES_ABREVIADOS = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
)

/**
 * El ancho de la pista **Cuándo** —el día y la hora—, **escalado con la
 * letra**.
 *
 * Fijo por fila y no `wrapContent`: si cada una midiera lo suyo, `09 feb` y
 * `18 feb` darían anchos distintos y el punto de estado bailaría de renglón en
 * renglón.
 *
 * Pero fijo en dp ABSOLUTOS era un defecto: a escala 1.5 la hora salía cortada
 * —`10:4`, `18:0`— y una hora a medias no es un dato incompleto, es un dato
 * falso. Se multiplica por el nivel elegido, igual que `altoDelCuadro` hace con
 * el cuadro de la puerta.
 */
@Composable
private fun anchoDeCuando(): Dp = ANCHO_BASE_DE_CUANDO * LocalFontSizeLevel.current.nominalScale

/** Lo que mide `18 feb` en `captionStrong` a escala normal, con su aire. */
private val ANCHO_BASE_DE_CUANDO = 40.dp

/**
 * El punto de estado, **escalado con la letra** — mismo patrón que
 * [anchoDeCuando].
 *
 * Era 6 dp fijos, y ése era el defecto: a `MUY_GRANDE` la etiqueta duplica su
 * tamaño y se va a dos renglones mientras el punto se queda de 6 dp flotando
 * arriba del bloque. Se lee como un píxel sucio, no como un estado — y el punto
 * es el **único** portador del estado en la fila, justo en la escala que existe
 * para quien ve mal.
 */
@Composable
private fun puntoDelEstado(): Dp = PUNTO_BASE_DEL_ESTADO * LocalFontSizeLevel.current.nominalScale

/** Lo que mide el punto a escala normal. Ver [puntoDelEstado]. */
private val PUNTO_BASE_DEL_ESTADO = 6.dp

/** El ancho de la barra que marca la venta abierta. Ver [ContactoEnLinea]. */
private val MARCA_DE_LA_VENTA = 3.dp

/** El piso tocable del repo. Más estricto que los 48 de Material. */
private val ALTO_TOCABLE = 50.dp

/**
 * Alto mínimo de una pastilla de filtro.
 *
 * **50 dp, el piso del repo**, no los 34 que se le pusieron primero por verse
 * más ligeras. Una pastilla de filtro es un control que el cobrador toca con el
 * pulgar caminando, igual que cualquier otro, y el principio 11 no admite
 * excepciones estéticas: *si un test la cobra, sube la implementación, no bajes
 * el test*. Todo lo tocable de este feature ya usaba 50.
 */
private val ALTO_DEL_FILTRO = ALTO_TOCABLE
