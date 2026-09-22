package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
 * corra por debajo del importe. Ver el comentario en el cuerpo. Y el renglón
 * de abajo no se recorta: se parte por segmentos enteros
 * ([RenglonPorSegmentos]).
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
 *
 * ## [toque] — cuándo el toque pregunta en vez de abrir el mapa
 *
 * Sobre el cobro de HOY que además es el ÚLTIMO de esa cuenta, tocar abre
 * [HojaDelContacto] —*ubicación o ticket*— en vez del mapa. En cualquier otra
 * fila el toque sigue abriendo el mapa directo. Quién lo decide es
 * [com.example.msp_app.feature.pagos.domain.ToqueDelContacto], dominio puro; la
 * fila sólo recibe el veredicto por [abridorDe], que es el mismo camino para las
 * tres pantallas que pintan contactos. Su default deja la fila como estaba — ver
 * [ToqueDeLaFila].
 */
@Composable
fun ContactoEnLinea(
    contacto: ContactoDeCobranza,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false,
    deEstaVenta: Boolean = false,
    onVerUbicacion: ((UbicacionDelCobro) -> Unit)? = null,
    toque: ToqueDeLaFila = ToqueDeLaFila()
) {
    val resuelto = abridorDe(contacto, onVerUbicacion, toque)
    val abrir = resuelto.abrir
    val marca = if (deEstaVenta) MspTheme.colors.brand else Color.Transparent
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (abrir != null) {
                    Modifier
                        .clickable(onClick = abrir)
                        .semantics { contentDescription = resuelto.anuncio }
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
        // Cuándo.
        PistaDeCuando(cuando = cuando, modifier = Modifier.alignBy(FirstBaseline))
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
            val segmentos = renglonDeAbajo(contacto)
            if (segmentos.isNotEmpty()) {
                // Por segmentos y no un solo Text con elipsis: a escala grande
                // baja un segmento ENTERO al renglón siguiente en vez de dejar
                // "Maris…". Ver RenglonPorSegmentos.
                RenglonPorSegmentos(
                    segmentos = segmentos,
                    style = estiloDeProsa(),
                    color = MspTheme.colors.onSurfaceMuted
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
 * Los segmentos del renglón de abajo: `cuenta`, `método`, `cobrador`, con lo
 * que haya y en ese orden.
 *
 * Lo ausente se omite, nunca se rellena: una cuenta `null` no es "sin
 * cuenta", es que ese dato no llegó. El cobrador va tal como llega — el
 * nombre no se normaliza ni se acorta (decisión del dueño).
 */
private fun renglonDeAbajo(contacto: ContactoDeCobranza): List<String> = listOfNotNull(
    contacto.cuenta?.takeIf { it.isNotBlank() },
    contacto.metodo?.etiqueta,
    contacto.cobrador.takeIf { it.isNotBlank() }
)

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
 * **El control segmentado que decide qué se enseña — con su conteo debajo de
 * cada opción.**
 *
 * Sólo en las listas largas — ver el KDoc de [FiltroDeContactos]. Antes eran
 * cuatro pastillas sueltas; la lista de clientes ya filtraba con un solo
 * control segmentado (`SegmentadoDeCobranza`, commit `368bd2e2`) y la misma
 * app decía "sólo una opción puede estar encendida" de dos formas distintas.
 * Esta pieza delega en [ControlSegmentado] —el mecanismo que ese control
 * comparte con éste— así que la Task 4 no reinventa la tinta ni el alto
 * tocable, sólo cablea las cuatro opciones de [FiltroDeContactos].
 *
 * **[conteos] nunca sale de una consulta aparte.** El llamador lo arma con
 * [FiltroDeContactos.conteos] sobre los MISMOS contactos que la lista está
 * filtrando —`bitacora.contactos` en `BitacoraScreen`, `delAlcance` en
 * `LineaDeLaVenta`—, que es la única forma de que el conteo de una opción
 * coincida siempre con las filas que esa opción deja ver.
 *
 * **Una opción en cero se enseña igual, con su cero.** Decisión cerrada del
 * dueño: "Promesas 0" avisa antes de tocar que no hay nada. No se esconde ni
 * se deshabilita, así que las cuatro opciones de [FiltroDeContactos] siempre
 * están en [ControlSegmentado.opciones] — igual que `SegmentadoDeCobranza`, que
 * enseña sus cuatro chips porque particionan el catálogo de ocho y esconder uno
 * dejaría cuentas sin ningún lugar donde verse.
 *
 * A escala de letra grande, si las cuatro no caben repartiendo el ancho entre
 * todas, el control **rueda en una sola fila** y avisa con una pastilla contada
 * (`+2`) en el costado donde quedan opciones fuera de vista. Ver el KDoc de
 * [ControlSegmentado].
 *
 * **Esto cambió el 2026-09-22 y antes decía lo contrario aquí**: el control
 * pasaba a una rejilla de dos renglones y "nunca rodaba". El dueño vio el 2×2 en
 * el teléfono —se comía un bloque entero antes de la primera tarjeta— y pidió
 * una fila. Lo que la rejilla protegía sigue siendo cierto y no se perdió: una
 * opción escondida esconde su conteo, y el conteo es para lo que el chip existe.
 * Lo que cambió es cómo se protege — la pastilla dice **cuántas** faltan y lleva
 * a ellas, que es más de lo que la rejilla hacía.
 */
@Composable
fun FiltrosDeContacto(
    elegido: FiltroDeContactos,
    conteos: Map<FiltroDeContactos, Int>,
    onElegir: (FiltroDeContactos) -> Unit,
    modifier: Modifier = Modifier
) {
    ControlSegmentado(
        opciones = FiltroDeContactos.entries,
        seleccionado = elegido,
        conteos = conteos,
        etiquetaDe = { it.etiqueta },
        tagDe = { FILTRO_TAG + it.name },
        onElegir = onElegir,
        modifier = modifier
    )
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
 * La pista **Cuándo** —el día arriba y la hora abajo, alineados a la
 * derecha—, con un ancho **medido del contenido real**, no de una fórmula.
 *
 * ## Por qué no `Modifier.width(dp fijo × escala)` — el defecto medido
 *
 * La versión anterior fijaba el ancho en `40.dp × LocalFontSizeLevel.current
 * .nominalScale` —el nivel elegido DENTRO de la app—, pero el tamaño con el
 * que Compose pinta el texto no sale de ese nivel solo: sale de
 * `LocalDensity.current.fontScale`, que en la raíz de composición
 * (`MainActivity.kt`) es `máx(nivel de la app, fontScale del SISTEMA
 * OPERATIVO)` — la app nunca achica por debajo de lo que el teléfono ya pide,
 * pero tampoco agranda su propia pista cuando es el SO el que va más grande.
 * Con la app en `NORMAL` y el SO en su nivel más grande, `"24 ago"` mide más
 * que los `40.dp × 1.0` que la fórmula reservaba, así que el `Text` de arriba
 * —con `maxLines = 1` y overflow por default (`TextOverflow.Clip`, sin
 * elipsis)— **se recortaba en silencio**: un día mutilado que se sigue
 * leyendo como un día, un dato falso. Medido en
 * `ElDiaSeDesacoplaDelNivelDeLaAppTest`: con el SO en `MUY_GRANDE` (2.0) y la
 * app en `NORMAL` (1.0), la fórmula vieja reservaba 40dp pero el texto real
 * pedía más — la diferencia exacta, en dp, queda impresa en el mensaje de esa
 * prueba.
 *
 * ## El arreglo: medir, no multiplicar
 *
 * [Layout] mide el día y la hora SIN restricción de ancho —su tamaño natural,
 * el que sea que `LocalDensity.current.fontScale` dicte en ese momento,
 * cualquiera que sea la combinación de nivel de app y SO— y usa el más ancho
 * de los dos como el ancho de la pista, alineando el otro a la derecha por
 * colocación (no por `TextAlign`, que ya no hace falta). Ya no hay una
 * fórmula que perseguir: el ancho ES lo que el texto real necesita, siempre.
 *
 * Sigue siendo fijo POR FILA —los dos renglones de esta fila comparten un
 * mismo ancho medido en la misma pasada, así que `09 feb` y `18 feb` no
 * mueven el punto de estado entre renglones—, y las cifras tabulares de
 * `caption`/`captionStrong` (ver sus KDoc en `MspType`) garantizan que ese
 * ancho es prácticamente el mismo de una fila a otra a la misma escala,
 * aunque cada fila lo mida por su cuenta.
 *
 * Reporta su propia `FirstBaseline` —la del día, el primer hijo— para que
 * `Row.alignBy(FirstBaseline)` en [ContactoEnLinea] la alinee contra el
 * título exactamente igual que antes.
 */
@Composable
private fun PistaDeCuando(cuando: LocalDateTime, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = diaDe(cuando),
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.onSurface,
                maxLines = 1
            )
            Text(
                text = HORA.format(cuando),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1
            )
        }
    ) { medibles, restricciones ->
        val librePeroAcotado = Constraints(maxWidth = restricciones.maxWidth)
        val colocables = medibles.map { it.measure(librePeroAcotado) }
        val ancho = colocables.maxOf { it.width }
        val alto = colocables.sumOf { it.height }
        layout(
            width = ancho,
            height = alto,
            alignmentLines = mapOf(FirstBaseline to colocables[0][FirstBaseline])
        ) {
            var y = 0
            colocables.forEach { colocable ->
                colocable.placeRelative(ancho - colocable.width, y)
                y += colocable.height
            }
        }
    }
}

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
