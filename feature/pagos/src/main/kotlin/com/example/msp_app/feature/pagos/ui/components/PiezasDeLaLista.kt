package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspInsigniaNueva
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.component.MspPuntoNuevo
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Prefijo de `testTag` de cada chip: `"${CHIP_DE_SEGMENTO_TAG}todos"`, etc. */
const val CHIP_DE_SEGMENTO_TAG: String = "pagos_chip_"

/** `testTag` de la tarjeta de un cliente en la lista. */
const val FILA_DE_CLIENTE_TAG: String = "pagos_fila_cliente"

/** El encabezado (nombre e info) de la tarjeta: lleva al CLIENTE. */
const val ENCABEZADO_DE_CLIENTE_TAG: String = "pagos_encabezado_cliente"

/** La distancia del encabezado, sólo en la lista de cercanos. */
const val DISTANCIA_DEL_CLIENTE_TAG: String = "pagos_distancia_cliente"

private val LETRA_DEL_NOMBRE = 19.sp
private val TRACKING_DEL_NOMBRE = (-0.01).em
private val LETRA_DE_LA_DISTANCIA = 22.sp

/** Cada venta dentro de la tarjeta: lleva a ESA venta. Se le suma el `ventaId`. */
const val RENGLON_DE_VENTA_TAG: String = "pagos_renglon_venta_"

/**
 * El filtro de la lista, como **un solo control de cuatro estados**.
 *
 * ## Por qué un segmentado y no cuatro pastillas
 *
 * La forma dice la verdad. Cuatro pastillas sueltas sugieren que se pueden
 * prender varias a la vez, y el comportamiento real es que **solo una puede
 * estar activa**. El segmentado lo dice sin gastar una palabra de texto.
 *
 * ## De dónde salen los 6dp de tinta que se recuperan
 *
 * La pastilla anterior fijaba `heightIn(min = touchTarget)` —el token de 56dp
 * del tema— con padding vertical 0: **pintaba sus 56dp tocables enteros**. Aquí
 * el alto lo pone el toque y no la tinta: cada segmento fija
 * [ALTO_TOCABLE_DEL_SEGMENTO] y el contenedor no agrega padding vertical, así
 * que el control mide 50dp. La misma accesibilidad, 6dp menos de franja, y
 * cuatro pastillas sueltas menos.
 *
 * **50 y no 48.** El piso de Material son 48dp, pero este repo pide **≥50**
 * (`plans/2026-09-01-pagos-y-visitas.md` §Global Constraints) y ya lo cobra
 * `ListaSeVeYSeTocaTest`: la Task 16 shipeó un control de 49.5dp y tuvo que
 * corregirlo. Se toma el número del repo, que es el más estricto.
 *
 * ## Qué pasa a escala grande
 *
 * Repartir los 328dp de la pantalla entre cuatro segmentos da 79dp a cada uno. A
 * `MUY_GRANDE` (2.0) "Sin visitar" no cabe ni de lejos: la fila entera pide
 * 542dp. Hubo una **rejilla de dos renglones** para eso y el dueño la retiró
 * —2×2 se comía un bloque de pantalla antes de la primera tarjeta—, así que hoy
 * es una sola fila que rueda en horizontal. Lo que la fila no puede enseñar lo
 * dice un **aviso contado** en la orilla: "+2" al lado del último chip visible,
 * tocable para traer la siguiente. El detalle, con lo que se midió para
 * elegirlo, vive en el KDoc de [ControlSegmentado].
 *
 * El criterio no cambió, sólo la forma: es el mismo que [EncabezadoDeCliente]
 * aplica al monto — un dato que se sale de la pantalla es información perdida, y
 * el rótulo que se corta es justo el que más trabajo esconde. Lo que se admite
 * ahora es que no quepa; lo que no se admite es que se vaya **en silencio**.
 *
 * El área tocable **no** se puede probar con un golden. La cobran dos tests, y
 * miden cosas distintas: `ListaSeVeYSeTocaTest` mide el componente suelto, y
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` lo mide dentro del
 * `NavHost` real y con el inset de la barra despachado — que es donde el
 * defecto del toggle se escondió, porque en Robolectric ese inset vale cero.
 */
@Composable
fun SegmentadoDeCobranza(
    seleccionado: SegmentoDeCobranza,
    conteos: Map<SegmentoDeCobranza, Int>,
    onElegir: (SegmentoDeCobranza) -> Unit,
    modifier: Modifier = Modifier
) {
    ControlSegmentado(
        opciones = segmentosVisibles(),
        seleccionado = seleccionado,
        conteos = conteos,
        etiquetaDe = { it.etiqueta },
        tagDe = { CHIP_DE_SEGMENTO_TAG + it.name.lowercase() },
        onElegir = onElegir,
        modifier = modifier
    )
}

/**
 * Los chips que se pintan: **los cuatro, sin filtrar**.
 *
 * ## Por qué ya no hay interruptor
 *
 * Hubo uno (`HOY_VISIBLE`) mientras existió el chip *hoy*, que sólo podía
 * contener cuentas con `PROMESA_FECHA` o `CITA_FECHA` y por lo tanto marcaba 0
 * hasta que la Task 21 volvió alcanzable la captura estructurada de la Task 19.
 * Un chip que dice "hoy 0" al lado de "vencidos 34" durante semanas le enseña al
 * cobrador que la fila de filtros miente, y cuando deja de leer la fila se
 * pierden también los chips que sí sirven.
 *
 * Los cuatro de ahora **particionan** el catálogo de ocho (ver
 * [SegmentoDeCobranza]): esconder cualquiera de ellos dejaría cuentas sin ningún
 * chip donde verse, porque `TODOS` ya no existe. Por eso no hay nada que apagar
 * y la función se queda sólo como el punto donde se decide qué se enseña.
 */
private fun segmentosVisibles(): List<SegmentoDeCobranza> = SegmentoDeCobranza.entries

/**
 * Un CLIENTE en la lista: su encabezado y **sus ventas dentro de la misma
 * tarjeta**.
 *
 * ## Por qué una sola tarjeta y no una por venta
 *
 * Antes el cliente era una tarjeta y cada venta OTRA tarjeta colgando de ella.
 * Dos problemas, y el segundo está medido sobre el golden: la forma seguía sin
 * decir la verdad —una puerta eran tres tarjetas— y un cliente con dos ventas
 * gastaba **378.5dp** de los 513 que quedan de lista bajo la cabecera, o sea
 * **1.27 clientes por pantalla**.
 *
 * Con las ventas dentro, la tarjeta ES la puerta. Es lo que hace kollect, y es
 * la forma diciendo por fin lo que esta pantalla existe para arreglar: la lista
 * vieja partía a un cliente con dos ventas en dos personas distintas.
 *
 * Lo que se fue del encabezado, y por qué:
 *
 * | Se quitó | Por qué |
 * |---|---|
 * | Avatar de iniciales | El dueño lo pidió fuera. `MethodPill` ya había hecho lo mismo en el reporte |
 * | Saldo total del cliente | Amontonaba el encabezado, y el monto que importa es el de cada venta |
 * | Teléfono | No se cobra por teléfono; la zona y la calle sí ubican la puerta |
 * | Racimo de estados | **Redundante**: cada venta ya trae su chip a unos píxeles |
 * | Folio en cada venta | El cobrador identifica el mueble por su nombre |
 */
@Composable
fun FilaDeCliente(
    cliente: ClienteEnLista,
    onAbrirCliente: () -> Unit,
    /**
     * El día de negocio contra el que se lee "Vendida hace N días" en cada
     * venta sin abonos. **Sin default** (decisión del dueño, 2026-10-05): la
     * pantalla lo trae del mismo "hoy" que ya usa para ordenar y filtrar
     * ([com.example.msp_app.feature.pagos.application.Cartera.hoy]), nunca de
     * `LocalDate.now()` leído aquí adentro — dos renglones de la misma tarjeta
     * no pueden contestar sobre dos días distintos.
     */
    hoy: LocalDate,
    modifier: Modifier = Modifier,
    /** "Esconder cantidades": el monto de cada venta se pinta enmascarado. */
    montosOcultos: Boolean = false,
    /** Abre la venta tocada; recibe su `ventaId` (`DOCTO_CC_ACR_ID`). */
    onAbrirVenta: (Int) -> Unit = {},
    /**
     * La distancia ya formateada ("350 m"), sólo en la lista de **cercanos** de
     * Inicio, que reutiliza esta tarjeta (decisión del dueño del 2026-10-02).
     */
    distancia: String? = null
) {
    // Dos puertas en la misma tarjeta (decisión del dueño, 2026-10-01): el
    // encabezado lleva al CLIENTE y cada venta a ESA venta. Revierte la tarjeta
    // de una sola puerta —"desde la lista siempre se entra por el cliente"— porque
    // los cobradores se quejaron de que para abonar tenían que dar un paso de más.
    // La tarjeta ya no tiene `onClick` propio: cada zona tocable es suya.
    Tarjeta(modifier = modifier.testTag(FILA_DE_CLIENTE_TAG)) {
        Column {
            // El aire bajo el encabezado va DENTRO de su zona tocable: el
            // encabezado solo medía 47dp (medido, `LaTarjetaTieneDosPuertasTest`) y
            // así llega a los 50 sin mover un píxel de lo que se ve.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onAbrirCliente)
                    .testTag(ENCABEZADO_DE_CLIENTE_TAG)
            ) {
                EncabezadoDeCliente(cliente, distancia)
                Spacer(Modifier.height(AIRE_ANTES_DE_LA_PRIMERA_VENTA))
            }
            cliente.ventas.forEach { enLista ->
                RenglonDeVenta(
                    venta = enLista.venta,
                    montosOcultos = montosOcultos,
                    esNueva = enLista.esNueva,
                    hoy = hoy,
                    onAbrir = { onAbrirVenta(enLista.venta.ventaId) }
                )
            }
        }
    }
}

/**
 * Nombre a la izquierda, veredicto del cliente a la derecha.
 *
 * ## El ritmo vertical, que es la mitad del arreglo
 *
 * El primer intento puso el mismo aire en todos lados y el dueño lo rechazó:
 * "el nombre y la dirección están muy amontonados". La regla que lo arregla es
 * que **el aire antes de un separador es mayor que el aire entre dos líneas del
 * mismo bloque** — [AIRE_BAJO_EL_NOMBRE] contra
 * [AIRE_ANTES_DE_LA_PRIMERA_VENTA]. Así el ojo agrupa nombre y dirección como
 * una unidad y no confunde la dirección con la primera venta.
 *
 * ## "Falta x de x" se fue de las CUATRO tarjetas (decisión del dueño, 2026-10-05)
 *
 * Antes la columna derecha siempre llevaba algo encima de "Últ. pago": la
 * distancia en cercanos, o la píldora "Falta x de x" en todo lo demás. La
 * píldora se retiró sin reemplazo — el nombre es quien hereda ese ancho, por
 * eso ahora ocupa toda la fila (`weight(1f)` dentro de [NombreDelCliente]).
 *
 * ## Dos renglones FIJOS, no dos columnas (corregido 2026-10-05)
 *
 * El primer intento reusaba la forma vieja —columna izquierda (nombre +
 * dirección) y columna derecha (lo demás), las dos alineadas arriba— y le
 * quitó el relleno a la columna derecha junto con la píldora. El resultado:
 * "Últ. pago" ya no tenía nada encima que lo empujara hacia abajo, así que
 * flotaba junto al NOMBRE en vez de junto a la dirección — medido en
 * `pagos_lista_light_1_0`, con "Victoria Flores" apretada contra "Últ. pago 6
 * sept" en el primer renglón.
 *
 * La forma correcta no son dos columnas: son **dos renglones**, cada uno su
 * propio `Row` de ancho completo.
 *
 * | Renglón | Variante normal (`distancia == null`) | Variante cercanos (`distancia != null`) |
 * |---|---|---|
 * | 1: nombre | nombre + [MspInsigniaNueva] si hay venta nueva | nombre + distancia |
 * | 2: dirección | dirección + "Últ. pago …" | dirección + [MspInsigniaNueva] si hay venta nueva, si no "Últ. pago …" |
 *
 * En cercanos la insignia baja al renglón 2 porque el renglón 1 ya lo ocupa la
 * distancia —grande y pegada al nombre— y meterla ahí apretaría al nombre
 * contra dos vecinos en vez de uno. Ahí sí "Últ. pago" y la insignia compiten
 * por el mismo lugar y una desplaza a la otra.
 *
 * En la variante normal NO compiten: [ClienteEnLista.ultimoPago] es el último
 * pago en CUALQUIERA de las ventas del cliente, y un cliente puede tener una
 * venta nueva Y otra ya pagada — es exactamente el caso de Victoria en el
 * golden. Esconder "Últ. pago" ahí borraría información real de una cuenta
 * que sí ha pagado, sólo porque OTRA de sus cuentas es nueva.
 */
@Composable
private fun EncabezadoDeCliente(cliente: ClienteEnLista, distancia: String?) {
    val hayVentaNueva = cliente.ventas.any { it.esNueva }
    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NombreDelCliente(cliente.nombre, modifier = Modifier.weight(1f))
            if (distancia != null) {
                // En cercanos manda la distancia, grande y en el color del texto
                // (blanco en oscuro): ahí el cobrador decide a qué puerta caminar.
                Text(
                    text = distancia,
                    style = MspTheme.type.chipLabel.copy(
                        fontSize = LETRA_DE_LA_DISTANCIA,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = MspTheme.colors.onSurface,
                    maxLines = 1,
                    modifier = Modifier.testTag(DISTANCIA_DEL_CLIENTE_TAG)
                )
            } else if (hayVentaNueva) {
                MspInsigniaNueva()
            }
        }
        Spacer(Modifier.height(AIRE_BAJO_EL_NOMBRE))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // Zona y calle en un renglón. El teléfono salió: no se cobra por
                // teléfono, y su hueco es lo que deja respirar al nombre.
                text = listOf(cliente.zona, cliente.direccion)
                    .filter { it.isNotBlank() }
                    .joinToString(SEPARADOR_DE_META)
                    .ifBlank { SIN_DATO },
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (distancia != null && hayVentaNueva) {
                MspInsigniaNueva()
            } else {
                cliente.ultimoPago?.let { dia ->
                    Text(
                        text = PREFIJO_ULTIMO_PAGO + DIA_Y_MES_DEL_PAGO.format(dia),
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * **Siempre un renglón, y si no cabe, corre.**
 *
 * Cortar un nombre con puntos suspensivos esconde justo el apellido que
 * distingue a dos clientes de la misma familia, que en una ruta es lo normal.
 * `basicMarquee` es la forma idiomática: solo anima cuando el texto NO cabe, y
 * `LazyColumn` solo compone lo visible, así que con los 270 clientes de la ruta
 * real corren dos o tres, no 270.
 *
 * Con movimiento reducido no se anima: ahí sí se corta, porque para quien pidió
 * que nada se moviera un texto en movimiento es peor que un texto incompleto.
 *
 * Además el dedo lo puede arrastrar de lado para ver rápido lo que no cabe
 * (2026-10-07): ver [NombreArrastrable].
 *
 * [modifier] por default es `Modifier`, pero [EncabezadoDeCliente] pasa
 * `Modifier.weight(1f)`: el nombre SIEMPRE manda el ancho de su fila, con o sin
 * [MspInsigniaNueva] al lado — es lo que hace que ceda espacio sólo cuando la
 * insignia de verdad está ahí, y lo recupere entero cuando no.
 */
@Composable
private fun NombreDelCliente(nombre: String, modifier: Modifier = Modifier) {
    NombreArrastrable(
        nombre = nombre,
        // Jerarquía (decisión del dueño del 2026-10-02, opción A): el nombre manda
        // —19 extra negrita— y el mueble baja a seminegrita. Con los dos en
        // negrita y casi del mismo tamaño había que leerlo dos veces.
        estilo = MspTheme.type.listTitle.copy(
            fontSize = LETRA_DEL_NOMBRE,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = TRACKING_DEL_NOMBRE
        ),
        color = MspTheme.colors.onSurface,
        reducirMovimiento = LocalReduceMotion.current,
        modifier = modifier
    )
}

/**
 * Una venta DENTRO de la tarjeta del cliente: nombre y monto arriba; estado y
 * atrasos abajo; la barra de avance al pie, de lado a lado.
 *
 * La barra va en su propio renglón **a propósito**. Puesta al lado del chip, su
 * largo dependería del largo del chip —"Pagó esta semana" deja menos pista que
 * "No estaba"—, así que dos ventas con el mismo avance se verían distintas, y
 * una con menos avance podría verse más llena. Con renglón propio la pista mide
 * siempre lo mismo y dos barras se pueden comparar de un vistazo, que es lo
 * único que se les pide.
 *
 * ## Una venta SIN abonos no tiene estado que mostrar (decisión del dueño, 2026-10-05)
 *
 * [ChipDeEstado] y [PastillaDeAtrasos] leen el catálogo de ocho y los pagos
 * atrasados — los dos suponen que la venta ya entró a cobranza. Una venta
 * nueva (sin ningún abono) nunca entró: no hay estado que afirmar ni atraso
 * que contar, así que en su lugar van [MspPuntoNuevo] junto al nombre,
 * [ChipDePrimerAbono] (lo único que SÍ se sabe: cuánto se espera) y
 * [TextoDeVendida] (cuándo se vendió, no cuándo se cobró — esta cuenta no ha
 * cobrado nada). La forma del renglón
 * —dos slots, uno a la izquierda y uno a la derecha, con el mismo quiebre a
 * `MUY_GRANDE`— no cambia: sólo lo que cada slot pinta.
 */
@Composable
private fun RenglonDeVenta(
    venta: VentaDelCliente,
    montosOcultos: Boolean,
    esNueva: Boolean,
    hoy: LocalDate,
    onAbrir: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GROSOR_DEL_HAIRLINE)
            .background(MspTheme.colors.outline)
    )
    Column(
        // Tocable: abre ESTA venta (decisión del dueño, 2026-10-01; ver
        // `FilaDeCliente`). El renglón entero es la zona tocable —nombre, monto,
        // estado y barra—, así que mide bastante más de los 50dp mínimos.
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onAbrir)
            .testTag(RENGLON_DE_VENTA_TAG + venta.ventaId)
            .padding(vertical = AIRE_DEL_RENGLON)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            if (esNueva) {
                MspPuntoNuevo()
            }
            Text(
                text = venta.descripcion.ifBlank { venta.folio },
                style = MspTheme.type.saleTitle.copy(fontWeight = FontWeight.SemiBold),
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            MspMoneyText(
                amount = venta.saldo.amount,
                masked = montosOcultos,
                style = MspTheme.type.amountRow,
                color = MspTheme.colors.onSurface
            )
        }
        Spacer(Modifier.height(AIRE_SOBRE_EL_ESTADO))
        // A `MUY_GRANDE` (2.0) el chip se come el ancho y la pastilla se cortaba a
        // "Al" — medido en `pagos_lista_light_2_0`. Un número de atrasos a medias
        // no es un dato incompleto, es un dato FALSO, así que en esa escala la
        // pastilla baja a su propio renglón en vez de encogerse. A 1.5 caben las
        // dos en una fila, también medido, y ahí no se gasta alto de más. La misma
        // regla aplica al par nuevo (chip de primer abono + "Vendida …").
        val enUnaFila = LocalFontSizeLevel.current != FontSizeLevel.MUY_GRANDE
        if (enUnaFila) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (esNueva) {
                    ChipDePrimerAbono(
                        venta.cuota,
                        montosOcultos
                    )
                } else {
                    ChipDeEstado(venta.estado)
                }
                Spacer(Modifier.weight(1f))
                if (esNueva) {
                    TextoDeVendida(
                        venta.fechaVenta,
                        hoy
                    )
                } else {
                    PastillaDeAtrasos(venta.atrasos)
                }
            }
        } else {
            if (esNueva) {
                ChipDePrimerAbono(
                    venta.cuota,
                    montosOcultos
                )
            } else {
                ChipDeEstado(venta.estado)
            }
            Spacer(Modifier.height(MspTheme.spacing.xs))
            if (esNueva) TextoDeVendida(venta.fechaVenta, hoy) else PastillaDeAtrasos(venta.atrasos)
        }
        Spacer(Modifier.height(AIRE_SOBRE_LA_BARRA))
        MspProgressBar(
            progress = venta.avance,
            height = ALTO_DE_LA_BARRA,
            fillColor = MspTheme.colors.heroProgressFill,
            trackColor = MspTheme.colors.progressTrack
        )
    }
}

/**
 * Cuántos pagos lleva atrasada esta venta, en los MISMOS tres tramos que usaba
 * la pantalla vieja (`PrimarySaleItem.kt:79-88`): 0 verde, 1-4 ámbar, 5+ rojo.
 *
 * Se conservan los tramos y se cambian los textos, que decían `"No tiene
 * atrasa."` y `"2 pag atrasa"` — recortados a la fuerza y con punto final.
 */
@Composable
private fun PastillaDeAtrasos(atrasos: Int) {
    val colors = MspTheme.colors
    val contenido: Color
    val fondo: Color
    when {
        atrasos < 1 -> {
            contenido = colors.statusPaid
            fondo = colors.statusPaidTint
        }
        atrasos < ATRASOS_GRAVES -> {
            contenido = colors.statusPartial
            fondo = colors.statusPartialTint
        }
        else -> {
            contenido = colors.statusOverdue
            fondo = colors.statusOverdueTint
        }
    }
    Box(
        modifier = Modifier
            .background(color = fondo, shape = MspTheme.shapes.chip)
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
            .testTag(PASTILLA_DE_ATRASOS_TAG)
    ) {
        Text(
            text = textoDeAtrasos(atrasos),
            style = MspTheme.type.captionStrong,
            color = contenido,
            maxLines = 1
        )
    }
}

/** "Al corriente" · "1 atraso" · "N atrasos". */
internal fun textoDeAtrasos(atrasos: Int): String = when {
    atrasos < 1 -> "Al corriente"
    atrasos == 1 -> "1 atraso"
    else -> "$atrasos atrasos"
}

/** `testTag` de la pastilla de atrasos — la localiza el test que la afirma. */
const val PASTILLA_DE_ATRASOS_TAG: String = "pagos_atrasos"

/** Aire corto: el nombre y su dirección son el MISMO bloque. */
private val AIRE_BAJO_EL_NOMBRE = 4.dp

/**
 * Aire largo, más del triple que [AIRE_BAJO_EL_NOMBRE]: es lo que despega al
 * cliente del primer separador y lo que arregla el "muy amontonado".
 */
private val AIRE_ANTES_DE_LA_PRIMERA_VENTA = 14.dp

private val AIRE_DEL_RENGLON = 12.dp

private val AIRE_SOBRE_EL_ESTADO = 9.dp

private val AIRE_SOBRE_LA_BARRA = 11.dp

private val ALTO_DE_LA_BARRA = 4.dp

private val GROSOR_DEL_HAIRLINE = 1.dp

/** Desde cuántos atrasos la pastilla pasa de ámbar a rojo (tramo heredado). */
private const val ATRASOS_GRAVES = 5

private const val PREFIJO_ULTIMO_PAGO = "Últ. pago "

private val DIA_Y_MES_DEL_PAGO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

private const val SEPARADOR_DE_META = " · "
