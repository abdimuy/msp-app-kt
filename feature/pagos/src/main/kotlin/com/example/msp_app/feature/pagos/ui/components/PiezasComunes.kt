package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspStatusChip
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.ui.EstadoVisual
import com.example.msp_app.feature.pagos.ui.estadoVisualDe

/** `testTag` del cuadro de estado — lo localiza el test de "nunca solo color". */
const val CUADRO_DE_ESTADO_TAG: String = "pagos_cuadro_estado"

/** `testTag` de la etiqueta de estado que acompaña al cuadro. */
const val ETIQUETA_DE_ESTADO_TAG: String = "pagos_etiqueta_estado"

/**
 * El cuadro de estado del mock (`.st`): ícono sobre fondo tint, o sobre relleno
 * sólido en "se negó".
 *
 * Va SIEMPRE acompañado de texto en su fila; el cuadro solo nunca es el
 * portador del significado (regla dura de accesibilidad).
 */
@Composable
fun CuadroDeEstado(visual: EstadoVisual, lado: Dp = 28.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(lado)
            .clip(RoundedCornerShape(lado / 3))
            .background(visual.fondo)
            .testTag(CUADRO_DE_ESTADO_TAG),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = visual.icono,
            contentDescription = null,
            tint = visual.contenido,
            modifier = Modifier.size(lado * 0.54f)
        )
    }
}

/**
 * El estado como **chip con texto dentro** — `⚠ Vencido 12d` en kollect,
 * `.mstate` en el mock.
 *
 * Antes era el cuadro de color más una etiqueta suelta al lado. Los dos
 * portadores estaban, pero la textura no: kollect mete ícono y texto DENTRO de
 * una pastilla con tint, y pone dos o tres por pantalla. Ahora reusa
 * [MspStatusChip] del design system —el componente compartido, no una copia
 * local— por el overload de terna resuelta, porque nuestro catálogo tiene ocho
 * estados y [ChipStatus] cinco (ver el KDoc de ese overload).
 */
@Composable
fun ChipDeEstado(estado: EstadoDelPeriodo, modifier: Modifier = Modifier) {
    val visual = estadoVisualDe(estado)
    MspStatusChip(
        icon = visual.icono,
        text = visual.etiqueta,
        contentColor = visual.contenido,
        containerColor = visual.fondo,
        modifier = modifier.testTag(ETIQUETA_DE_ESTADO_TAG)
    )
}

/**
 * El estado en grande de la pantalla de venta (`.stbig` del mock): cuadro de
 * 44dp, etiqueta y la línea que dice qué hacer.
 */
@Composable
fun EstadoEnGrande(estado: EstadoDelPeriodo, modifier: Modifier = Modifier) {
    val visual = estadoVisualDe(estado)
    Tarjeta(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            CuadroDeEstado(visual, lado = 44.dp)
            Column {
                Text(
                    text = visual.etiqueta,
                    style = MspTheme.type.cardTitle,
                    color = MspTheme.colors.onSurface,
                    modifier = Modifier.testTag(ETIQUETA_DE_ESTADO_TAG)
                )
                Text(
                    text = visual.detalle,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/**
 * El label de sección del mock (`.sl` = `10px/700`, `letter-spacing:.16em`,
 * `text-transform:uppercase`) y de kollect ("ÚLTIMOS PAGOS",
 * "COMPORTAMIENTO DE PAGO").
 *
 * Dos cosas que antes no estaban:
 *
 * 1. **Las versalitas.** El KDoc anterior decía "overline en mayúsculas
 *    ópticas" pero el `.uppercase()` no existía, así que la pantalla decía
 *    "sus ventas" donde el mock y kollect dicen "SUS VENTAS". Va con la
 *    locale de negocio y no la del teléfono: en es-MX los acentos se
 *    conservan ("LIQUIDACIÓN"), que es como los escribe kollect.
 * 2. **El rol tipográfico correcto es `eyebrow`, no `overline`.** El argumento
 *    es el **ancho de tracking**: el `.sl` del mock es
 *    `10px/700/letter-spacing:.16em` (los tres HTML que lo definen:
 *    `cliente-y-venta.html:79`, `historial-de-pagos.html:30`,
 *    `registrar-visita.html:44`), y de los cuatro roles de esta familia que
 *    tenemos —`sectionHeader` +0.04em, `overline` +0.05em, `sectionLabel`
 *    +0.08em, `eyebrow` +0.09em (`MspType.kt:191-194`, 1:1 con
 *    `CampoType.kt:209-218`)— **`eyebrow` es el más ancho**, o sea el más
 *    cercano a .16em. Con `overline` el tracking se queda a la mitad.
 *
 *    **Corrección de la ronda 1:** este KDoc citaba
 *    `ClienteDetalleScreen.kt:337` como el uso de `eyebrow` en kollect para
 *    "ÚLTIMOS PAGOS", y **es falso**: ahí el estilo es `sectionHeader`
 *    (`ClienteDetalleScreen.kt:339`) y ese archivo no usa `eyebrow` en ningún
 *    Text. La cita que **sí** existe es `VentaDetalleSections.kt:325`,
 *    "COMPORTAMIENTO DE PAGO" con `type.eyebrow`. Los valores del rol siempre
 *    fueron correctos; la cita no.
 */
@Composable
fun LabelDeSeccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(BUSINESS_LOCALE),
        style = MspTheme.type.eyebrow,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = modifier.padding(top = MspTheme.spacing.md, bottom = MspTheme.spacing.sm)
    )
}

/**
 * Tarjeta base de las secciones (`.money`, `.liq`, `.know`, `.stbig`).
 *
 * **Es un envoltorio de [MspCard], no una tarjeta propia.** Antes era un
 * `Box.clip(shapes.card).background(surface)` y por eso las siete pantallas se
 * veían más suaves que kollect: les faltaba el **hairline de 1dp `outline`**
 * que `MspCard` → `MspSurface` pone en TODA tarjeta del sistema (1:1
 * `CampoCard`/`CampoSurface`). Lo único que esta función agrega encima es lo
 * que las siete pantallas comparten y `MspCard` deliberadamente no asume: el
 * ancho completo, el padding interior de `spacing.md` y `shapes.card` (20dp)
 * en vez del `shapes.tile` (16dp) por default.
 *
 * Nota de encuadre — **corregida en la ronda 1; la versión anterior de este
 * párrafo era falsa.** El hairline no se importó de kollect: `MspSurface` lo
 * documenta como **invariante de este design system** — *"El hairline va
 * SIEMPRE — no es opcional… define las tarjetas casi-planas del sistema"*
 * (spec §2.3) —, así que las siete pantallas eran la **desviación** y reusar
 * [MspCard] no agregó un adorno: dejó de violar §2.3.
 *
 * Y el mock **sí** lo pide. Un `grep 'border:'` sobre los cuatro HTML de
 * `docs/design/mocks/` devuelve
 * **nueve** coincidencias, no una — entre ellas la tarjeta `.opt` de
 * `historial-de-pagos.html:21` y el `.map` de `cliente-y-venta.html:151`, las
 * dos con `1px solid var(--line)`. Y sobre todo: **el idioma del hairline en
 * el mock es `box-shadow:inset 0 0 0 1.5px var(--line)`**, 20 reglas repartidas
 * entre `registrar-abono.html` (11) y `registrar-visita.html` (9) — incluidas
 * **las doce teclas** (`.m`, `registrar-abono.html:58`) y los chips sugeridos
 * (`.sug`, `:49`). Esos dos archivos no tienen **ni un** `border:`, así que un
 * grep de `border:` es ciego exactamente donde el mock más pide el borde: la
 * falla de control positivo que originó esta corrección.
 */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    MspCard(
        modifier = modifier.fillMaxWidth(),
        shape = MspTheme.shapes.card,
        onClick = onClick
    ) {
        Box(modifier = Modifier.padding(MspTheme.spacing.md)) { content() }
    }
}

/** Una fila clave/valor de "datos del cliente" / "datos de la venta" (`.kv`). */
@Composable
fun FilaClaveValor(clave: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MspTheme.spacing.sm + MspTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = clave,
                style = MspTheme.type.kvLabel,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.width(140.dp)
            )
            Text(
                text = valor.ifBlank { SIN_DATO },
                style = MspTheme.type.kvValue,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Separador()
    }
}

/** El hairline de 1px que separa filas (`border-top:1px solid var(--line)`). */
@Composable
fun Separador(modifier: Modifier = Modifier, color: Color = MspTheme.colors.outline) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

/** Lo que se pinta donde el teléfono no tiene el dato. */
const val SIN_DATO: String = "—"

/**
 * Lo que se dice donde iría una dirección y el cliente no trae ninguna escrita.
 *
 * **No es [SIN_DATO].** Una raya sirve en una celda que lleva su etiqueta al
 * lado —"últ. pago —" se lee solo—, pero en el renglón grande del cuadro de la
 * puerta una raya suelta se lee como una pantalla a medio cargar, que es
 * exactamente lo que ese cuadro existe para no ser.
 *
 * Vive acá y no en `:app` porque lo dicen DOS pantallas con el mismo hueco: el
 * cuadro de la puerta del detalle (`CuadroDeLaPuerta`) y la hoja del mapa
 * grande (`UbicacionDelClienteScreen`, `:app`). Eran dos copias del mismo texto
 * de usuario, y dos copias se despegan en cuanto alguien reescribe una.
 */
const val SIN_DIRECCION: String = "Sin dirección registrada"

/**
 * Vuelve a leer al **reanudarse** la pantalla — no al montarse.
 *
 * El defecto que cierra: registrar un pago o una visita y volver atrás dejaba
 * el detalle de venta, el de cliente y la lista congelados en lo que había
 * ANTES de cobrar. Los cuatro ViewModel de cobranza cargan una sola vez en su
 * `init` y no observan Room con `Flow` —migrar a eso es el refactor grande
 * que hoy no cabe—, así que lo que puede reaccionar a "volví a esta pantalla"
 * es el ciclo de vida, no el estado.
 *
 * **Salta la primera reanudación.** `ON_RESUME` también dispara justo después
 * de montarse —es la primera transición del ciclo de vida de todo
 * `Composable`—, y ahí `recargar()` sería una segunda sincronización inútil
 * pegada a la del `init`. `yaSeMonto` en `rememberSaveable` y no en
 * `remember`: tiene que sobrevivir a la rotación de pantalla, que también
 * pasa por `ON_RESUME` sin que el cobrador haya ido a ningún lado.
 *
 * Reusable por las cuatro pantallas de `feature/pagos` que abren un ViewModel
 * con `recargar()` —detalle de venta, detalle de cliente, lista y bitácora—
 * en vez de repetir el bloque cuatro veces.
 */
@Composable
fun RecargaAlVolver(recargar: () -> Unit) {
    var yaSeMonto by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (yaSeMonto) recargar() else yaSeMonto = true
        onPauseOrDispose { }
    }
}
