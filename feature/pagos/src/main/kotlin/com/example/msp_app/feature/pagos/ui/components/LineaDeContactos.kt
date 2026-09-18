package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * ## La hora va al margen, en columna
 *
 * Y no dentro del renglón, para que la columna de horas se lea de corrido: el
 * cobrador reconstruye su día por la hora, no por la etiqueta. Es tabular
 * ([MspTheme] ya lo resuelve en `caption`) así que los dígitos alinean y la
 * columna no baila entre `09:20` y `18:05`.
 *
 * ## La forma de pago no aparece en las visitas, y es lo correcto
 *
 * `ContactoDeCobranza.metodo` llega en `null` en toda visita —ver su KDoc: la
 * columna existe pero el escritor la deja en 0 y eso se leería como
 * *"efectivo"*—. Aquí eso no se rellena con nada: su ausencia dice que ahí no
 * se cobró.
 *
 * ## [deEstaVenta] sólo lo usa el detalle de venta
 *
 * Marca las filas que sí son de la cuenta abierta dentro de la línea de tiempo
 * del cliente entero. Es un borde de marca, no un fondo: un relleno haría que
 * media lista pareciera seleccionada.
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
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
            .testTag(CONTACTO_EN_LINEA_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        // La marca de "esto es de la venta abierta": una barra al BORDE de la
        // fila y de su alto completo. Flotando entre el importe y el pin se leía
        // como un artefacto de render; al borde es el patrón que cualquiera
        // reconoce y además no depende de que la fila traiga importe.
        Box(
            modifier = Modifier
                .width(MARCA_DE_LA_VENTA)
                .fillMaxHeight()
                .clip(MspTheme.shapes.chip)
                .background(
                    if (deEstaVenta) MspTheme.colors.brand else Color.Transparent
                )
        )
        Text(
            text = HORA.format(AppTime.toBusinessDateTime(contacto.fecha)),
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1,
            modifier = Modifier
                .width(anchoDeLaHora())
                .padding(top = MspTheme.spacing.sm + MspTheme.spacing.xs)
        )
        Box(
            modifier = Modifier
                .padding(top = altoDelPuntoDelEstado())
                .size(puntoDelEstado())
                .clip(RoundedCornerShape(percent = 50))
                .background(acentoDe(contacto))
                .testTag(PUNTO_DEL_ESTADO_TAG)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MspTheme.spacing.sm)
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = contacto.etiqueta,
                    style = MspTheme.type.bodyStrong,
                    color = MspTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (contacto.importe != null) {
                    MspMoneyText(
                        amount = contacto.importe.amount,
                        masked = ocultos,
                        style = MspTheme.type.amountInline,
                        color = MspTheme.colors.onSurface
                    )
                }
            }
            val meta = listOfNotNull(
                contacto.metodo?.etiqueta,
                contacto.cobrador.takeIf { it.isNotBlank() }
            )
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            contacto.nota?.let {
                Text(
                    text = "“$it”",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(modifier = Modifier.padding(top = MspTheme.spacing.sm)) {
            PinDelContacto(hayPunto = contacto.ubicacion != null)
        }
    }
}

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
 * El ancho de la columna de la hora, **escalado con la letra**.
 *
 * Fijo por fila y no `wrapContent`: si cada una midiera lo suyo, `09:20` y
 * `18:05` darían anchos distintos y el punto de estado bailaría de renglón en
 * renglón.
 *
 * Pero fijo en dp ABSOLUTOS era un defecto: a escala 1.5 la hora salía cortada
 * —`10:4`, `18:0`— y una hora a medias no es un dato incompleto, es un dato
 * falso. Se multiplica por el nivel elegido, igual que `altoDelCuadro` hace con
 * el cuadro de la puerta.
 */
@Composable
private fun anchoDeLaHora(): Dp = ANCHO_BASE_DE_LA_HORA * LocalFontSizeLevel.current.nominalScale

/** Lo que mide `HH:mm` en `caption` a escala normal, con su aire. */
private val ANCHO_BASE_DE_LA_HORA = 38.dp

/**
 * El punto de estado, **escalado con la letra** — mismo patrón que
 * [anchoDeLaHora].
 *
 * Era 6 dp fijos, y ése era el defecto: a `MUY_GRANDE` la etiqueta duplica su
 * tamaño y se va a dos renglones mientras el punto se queda de 6 dp flotando
 * arriba del bloque. Se lee como un píxel sucio, no como un estado — y el punto
 * es el **único** portador del estado en la fila, justo en la escala que existe
 * para quien ve mal.
 */
@Composable
private fun puntoDelEstado(): Dp = PUNTO_BASE_DEL_ESTADO * LocalFontSizeLevel.current.nominalScale

/**
 * Y su desplazamiento vertical, escalado igual.
 *
 * Tiene que crecer con el punto: el `top` es lo que lo alinea con la primera
 * línea de la etiqueta, y esa línea baja cuando la letra crece. Fijo en dp, a
 * 2.0 el punto quedaba pegado al borde de arriba de un bloque de dos renglones
 * — huérfano de la fila que describe.
 */
@Composable
private fun altoDelPuntoDelEstado(): Dp =
    MspTheme.spacing.md * LocalFontSizeLevel.current.nominalScale

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
