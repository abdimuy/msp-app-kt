package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.DestinoDeLaCuenta

/** `testTag` del cuerpo de la hoja que pregunta a cuál cuenta va el abono. */
const val HOJA_DE_ABONO_TAG: String = "pagos_hoja_abono"

/** `testTag` de cada opción de cuenta dentro de la hoja. */
const val OPCION_DE_CUENTA_TAG: String = "pagos_hoja_abono_opcion"

/** `testTag` del botón que confirma a cuál cuenta entra el dinero. */
const val CONTINUAR_CON_LA_CUENTA_TAG: String = "pagos_hoja_abono_continuar"

/**
 * `testTag` del estado de cada opción — "Pagó esta semana", "No estaba", "Cita
 * 24 sept 16:30", etc. Distinto y por opción para que el dueño pueda diferenciar
 * de un vistazo a cuál cuenta va el billete.
 */
const val ESTADO_DE_LA_OPCION_TAG: String = "pagos_hoja_abono_opcion_estado"

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
 * en una puerta) y, debajo, **su estado** — "Pagó esta semana", "No estaba",
 * "Cita 24 sept 16:30" — con la misma pieza ([ChipDeEstado]) que ya usan el
 * detalle de cliente y el de venta, para que dos cuentas con estados distintos
 * se vean distintas y el cobrador no tenga que adivinar cuál ya cobró y cuál
 * sigue pendiente. A la derecha, lo que le toca de parcialidad y, si trae
 * atrasos, la pastilla ámbar — dato DISTINTO al estado: atrasos cuenta cuántos
 * periodos debe, el estado dice qué pasó en la puerta esta semana. Es
 * exactamente lo que hace falta para decidir a cuál va el billete, sin tener
 * que salir a mirar.
 *
 * **La selección va en `brand`, nunca en el color del estado.** El rojo de esta
 * app significa "se negó"; usarlo para "elegiste esto" convierte una captura en
 * una alarma.
 *
 * ## Por qué SÍ es un `ModalBottomSheet`, a diferencia de sus vecinas
 *
 * El pedido del dueño, textual: *"ponerle a los sheet de agregar abono y
 * condonacion la misma animation que se le puso a la de notas... me gusta como
 * esta esa animation"*. La de notas es [HojaDeLaFicha], un `ModalBottomSheet` de
 * M3 sin personalizar — sube desde abajo, el velo aparece/desaparece con ella, y
 * al cerrar se va hacia abajo ANTES de desmontarse. Esta hoja adopta la MISMA
 * configuración (mismo `sheetState`, mismo `containerColor`/`contentColor`, sin
 * tocar forma ni color del velo) para que la animación sea idéntica, no parecida.
 *
 * Las dos trampas documentadas en este módulo para evitar `ModalBottomSheet`
 * —goldens en blanco (`captureRoboImage` no ve el `Popup`) y `clip` con esquinas
 * desiguales matando el hit-test bajo Robolectric— **no aplican aquí** de la
 * misma forma que no aplican a [HojaDeLaFicha]: el cuerpo vive aparte, en
 * [CuerpoDeAbono], exactamente por el patrón de [CuerpoDeLaFicha]. Lo que SÍ
 * cambia respecto de antes —medido, no supuesto, con el precedente de
 * `PrintSheetTest` en `:feature:collectionReport`— es que `performClick()` no
 * cruza a la ventana del `Popup`: las pruebas de toque (elegir cuenta, tocar
 * "Continuar") pasan a montar [CuerpoDeAbono] directo, sin el `ModalBottomSheet`
 * alrededor. El cierre por atrás, por arrastre y por velo los maneja M3 solo —ya
 * no hace falta el `BackHandler` propio ni el velo pintado a mano— y el
 * `navigationBarsPadding()` que esta hoja necesitaba también se retira: M3 1.3.0
 * ya aplica `safeDrawing.only(Bottom)` al contenido, la misma protección que
 * exime a [HojaDeLaFicha]. Ver `LaHojaDelAbonoNoQuedaBajoLaBarraTest`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeAbono(
    cuentas: List<VentaDelCliente>,
    elegida: Int?,
    onElegir: (Int) -> Unit,
    onContinuar: () -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false,
    destino: DestinoDeLaCuenta = DestinoDeLaCuenta.ABONO
) {
    if (cuentas.isEmpty()) return
    // El `SheetState` se crea aquí dentro, no por parámetro — mismo criterio que
    // `HojaDeLaFicha`: es un tipo experimental de M3 y ponerlo en la firma
    // obligaría a cada llamador (incluidos los goldens) a repetir el `@OptIn`.
    //
    // `onDismissRequest = onCerrar` es lo que hace que "terminó de irse antes de
    // desmontarse" funcione sin código nuestro: M3 primero anima `sheetState` a
    // `Hidden` (velo, arrastre o atrás) y SÓLO CUANDO esa animación termina invoca
    // este lambda — que aquí limpia `eleccionDeCuenta`/desmonta la hoja. Si se
    // desmontara ANTES de que M3 termine de animar, se vería el mismo corte en
    // seco que esta tarea vino a quitar.
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MspTheme.colors.surface,
        contentColor = MspTheme.colors.onSurface
    ) {
        CuerpoDeAbono(
            cuentas = cuentas,
            elegida = elegida,
            onElegir = onElegir,
            onContinuar = onContinuar,
            ocultos = ocultos,
            destino = destino
        )
    }
}

/**
 * El cuerpo de la hoja "¿a cuál cuenta?", **sin** el `ModalBottomSheet` que lo
 * envuelve.
 *
 * Extraído por la misma razón que [CuerpoDeLaFicha] en este mismo paquete y que
 * `PrintSheetBody` en `:feature:collectionReport`: `captureRoboImage` toma la
 * ventana raíz y no el `Popup` donde M3 monta la hoja, así que un golden de la
 * hoja completa saldría en blanco — y `performClick()` bajo Robolectric tampoco
 * cruza a esa ventana, así que las pruebas de toque (elegir cuenta, "Continuar")
 * montan esta pieza directo, sin el `ModalBottomSheet`. Lo que SÍ pasa por el
 * `Popup`, y se prueba contra [HojaDeAbono] con *queries* de semántica (que sí
 * cruzan ventanas) en vez de toques, es que la hoja abra y cierre.
 *
 * Ya no dibuja su propio velo ni su propio fondo/forma: el `ModalBottomSheet`
 * los pone. Montado suelto (en las pruebas) no tiene ni uno ni otro, que es
 * exactamente lo que ya pasaba con [CuerpoDeLaFicha].
 */
@Composable
fun CuerpoDeAbono(
    cuentas: List<VentaDelCliente>,
    elegida: Int?,
    onElegir: (Int) -> Unit,
    onContinuar: () -> Unit,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false,
    destino: DestinoDeLaCuenta = DestinoDeLaCuenta.ABONO
) {
    if (cuentas.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md)
            .testTag(HOJA_DE_ABONO_TAG)
    ) {
        // **La misma hoja, dos destinos.** Condonar desde el cliente
        // pregunta lo mismo con las mismas opciones y la misma
        // preselección; lo que cambia es qué va a pasar al continuar, y eso
        // tiene que decirlo la hoja. Un cobrador que llegó por el "⋯" del
        // dock no puede leer "el abono entra completo a una" y tocar
        // Continuar creyendo que va a abonar.
        val condonando = destino == DestinoDeLaCuenta.CONDONACION
        Text(
            text = if (condonando) "¿De cuál cuenta?" else "¿A cuál cuenta?",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.xs))
        Text(
            text = if (condonando) {
                "Se condona el resto de una"
            } else {
                "El abono entra completo a una"
            },
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
            text = if (condonando) "Continuar a condonar" else "Continuar",
            onClick = onContinuar,
            enabled = elegida != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CONTINUAR_CON_LA_CUENTA_TAG)
        )
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
                Box(modifier = Modifier.testTag(ESTADO_DE_LA_OPCION_TAG)) {
                    ChipDeEstado(cuenta.estado)
                }
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

/** Alto mínimo de una opción — la regla de 50 dp del repo, no los 48 de Material. */
private val TOQUE_DE_LA_OPCION = 50.dp

private val ANILLO = 22.dp

private val BORDE = 3.dp

private val PUNTO = 8.dp
