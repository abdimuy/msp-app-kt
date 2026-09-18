package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente

/** `testTag` de la hoja que pregunta a cuál cuenta va el abono. */
const val HOJA_DE_ABONO_TAG: String = "pagos_hoja_abono"

/** `testTag` del velo de la hoja del abono. */
const val VELO_DEL_ABONO_TAG: String = "pagos_hoja_abono_velo"

/** `testTag` de cada opción de cuenta dentro de la hoja. */
const val OPCION_DE_CUENTA_TAG: String = "pagos_hoja_abono_opcion"

/** `testTag` del botón que confirma a cuál cuenta entra el dinero. */
const val CONTINUAR_CON_LA_CUENTA_TAG: String = "pagos_hoja_abono_continuar"

/**
 * **¿A cuál cuenta entra el abono?**
 *
 * ## Por qué es un radio y no casillas
 *
 * El dinero entra **completo a una cuenta**. No se reparte: cada abono se
 * registra contra un `DOCTO_CC_ACR_ID`, y partir un billete entre dos cuentas
 * serían dos abonos, no uno. La forma tiene que decir esa verdad — con casillas,
 * el cobrador marcaría dos y esperaría que la app repartiera.
 *
 * ## Con una sola cuenta esta hoja NO aparece
 *
 * Lo decide `CuentaDelAbono.unica`, no esta pieza: con una cuenta cobrable el
 * flujo es idéntico al de siempre, un toque y a la captura. La hoja es el precio
 * de tener dos o más, y solo se cobra ahí.
 *
 * ## Qué trae cada opción, y por qué eso
 *
 * El nombre del producto (no el folio — "V-5021" no le dice nada a nadie parado
 * en una puerta), lo que le toca de parcialidad, cuántos abonos lleva y, si trae
 * atrasos, la pastilla ámbar. Es exactamente lo que hace falta para decidir a
 * cuál va el billete, sin tener que salir a mirar.
 *
 * **La selección va en `brand`, nunca en el color del estado.** El rojo de esta
 * app significa "se negó"; usarlo para "elegiste esto" convierte una captura en
 * una alarma.
 *
 * El velo consume el toque y equivale a cancelar: nada se registra.
 *
 * ## Por qué esta hoja pide su propio `navigationBarsPadding()`
 *
 * Es la **única rota de las cinco de la pantalla**, y lo fue por caer entre dos
 * redes. Tres hojas heredan el `systemBarsPadding()` de `DetalleClienteContent`
 * porque se invocan DENTRO de ese `Column` padeado; `HojaDeLaFicha` se salva
 * sola porque el `ModalBottomSheet` de M3 1.3.0 ya aplica
 * `safeDrawing.only(Bottom)`. Esta no es M3 y se invoca **fuera** del `Column`
 * —que cierra antes de la llamada—, así que arrancaba pegada a `y = alto` con la
 * ventana de navegación de SystemUI encima: en el SM-A256E el dueño lo vio en
 * vidrio, **"Continuar" queda debajo de la barra y no se puede tocar**. No es que
 * el toque no haga nada: el evento ni siquiera entra al proceso.
 *
 * **El padding va DESPUÉS del `.background(...)`, a propósito.** El precedente
 * exacto es `BlurredActionBar.kt:126` (`:feature:collectionReport`): el fondo se
 * pinta ANTES del padding, así que son los botones —no el fondo— los que suben.
 * Al revés, el fondo se encogería con el contenido y quedaría una franja del color
 * de la PANTALLA debajo de la hoja, justo encima de la barra: la hoja dejaría de
 * estar pegada al borde de abajo.
 *
 * **Y es `navigationBarsPadding()`, no `systemBarsPadding()`.** La hoja arranca
 * pegada abajo y nunca toca la barra de estado; el inset de arriba solo le metería
 * una franja muerta encima del título — separación que nadie pidió en una hoja que
 * no llega ahí. La compuerta es `LaHojaDelAbonoNoQuedaBajoLaBarraTest`.
 */
@Composable
fun HojaDeAbono(
    cuentas: List<VentaDelCliente>,
    elegida: Int?,
    onElegir: (Int) -> Unit,
    onContinuar: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    if (cuentas.isEmpty()) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(VELO)
                .pointerInput(Unit) { detectTapGestures { onCerrar() } }
                .testTag(VELO_DEL_ABONO_TAG)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(FORMA_DE_LA_HOJA)
                .background(MspTheme.colors.surface)
                // La hoja se come el toque para que nada de abajo se alcance
                // mientras está arriba: la mitad de "ninguna ruta guarda dos veces".
                .pointerInput(Unit) { detectTapGestures { } }
                // DESPUÉS del `background` y ANTES del `padding`, como
                // `BlurredActionBar.kt:126`: el fondo ya se pintó, así que suben
                // los botones y no el fondo. Antes del `background` dejaría una
                // franja del color de la pantalla debajo de la hoja.
                .navigationBarsPadding()
                .padding(MspTheme.spacing.md)
                .testTag(HOJA_DE_ABONO_TAG)
        ) {
            Text(
                text = "¿A cuál cuenta?",
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            Spacer(Modifier.height(MspTheme.spacing.xs))
            Text(
                text = "El abono entra completo a una",
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.md))
            cuentas.forEach { cuenta ->
                OpcionDeCuenta(
                    cuenta = cuenta,
                    seleccionada = cuenta.ventaId == elegida,
                    ocultos = ocultos,
                    onElegir = { onElegir(cuenta.ventaId) }
                )
                Spacer(Modifier.height(MspTheme.spacing.sm))
            }
            Spacer(Modifier.height(MspTheme.spacing.xs))
            MspPrimaryFieldButton(
                text = "Continuar",
                onClick = onContinuar,
                enabled = elegida != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CONTINUAR_CON_LA_CUENTA_TAG)
            )
        }
    }
}

@Composable
private fun OpcionDeCuenta(
    cuenta: VentaDelCliente,
    seleccionada: Boolean,
    ocultos: Boolean,
    onElegir: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onElegir,
        shape = MspTheme.shapes.field,
        color = if (seleccionada) MspTheme.colors.brandTint else MspTheme.colors.surface2,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TOQUE_DE_LA_OPCION)
            .testTag(OPCION_DE_CUENTA_TAG)
    ) {
        Row(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Anillo(seleccionada)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cuenta.descripcion.ifBlank { cuenta.folio },
                    style = MspTheme.type.listTitle,
                    color = MspTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(MspTheme.spacing.xs))
                Text(
                    text = "${cuenta.abonosPagados} de ${cuenta.abonosTotales} abonos",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MspMoneyText(
                    amount = cuenta.parcialidad.amount,
                    masked = ocultos,
                    style = MspTheme.type.amountInline,
                    color = MspTheme.colors.onSurface
                )
                if (cuenta.atrasos > 0) {
                    Spacer(Modifier.height(MspTheme.spacing.xs))
                    Text(
                        text = if (cuenta.atrasos == 1) {
                            "1 atraso"
                        } else {
                            "${cuenta.atrasos} atrasos"
                        },
                        style = MspTheme.type.chipLabel,
                        color = MspTheme.colors.statusPartial,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(MspTheme.shapes.chip)
                            .background(MspTheme.colors.statusPartialTint)
                            .padding(
                                horizontal = MspTheme.spacing.sm,
                                vertical = MspTheme.spacing.xs
                            )
                    )
                }
            }
        }
    }
}

/**
 * El anillo del radio.
 *
 * Es un portador que **no es color**: con solo el fondo tintado, en oscuro y a
 * plena luz del sol la opción elegida y las otras se parecen demasiado. El punto
 * lleno dentro del anillo se ve aunque el tint no se distinga.
 */
@Composable
private fun Anillo(seleccionada: Boolean) {
    Box(
        modifier = Modifier
            .size(ANILLO)
            .clip(MspTheme.shapes.chip)
            .background(if (seleccionada) MspTheme.colors.brand else MspTheme.colors.progressTrack),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(if (seleccionada) PUNTO else ANILLO - BORDE)
                .clip(MspTheme.shapes.chip)
                .background(if (seleccionada) MspTheme.colors.onBrand else MspTheme.colors.surface2)
        )
    }
}

/** El velo que tapa la pantalla mientras la hoja está arriba. */
private val VELO = Color(0x99000000)

/** La hoja redondea solo arriba: abajo se pega al borde de la pantalla. */
private val FORMA_DE_LA_HOJA = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

/** Alto mínimo de una opción — la regla de 50 dp del repo, no los 48 de Material. */
private val TOQUE_DE_LA_OPCION = 50.dp

private val ANILLO = 22.dp

private val BORDE = 3.dp

private val PUNTO = 8.dp
