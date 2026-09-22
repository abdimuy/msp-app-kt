@file:Suppress("TooManyFunctions") // una pieza por banda de la hoja del mock (paneles 3 y 4).

package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.AvisoDelMonto
import com.example.msp_app.feature.pagos.domain.NivelDeAviso
import com.example.msp_app.feature.pagos.domain.RarezaDelAbono
import com.example.msp_app.feature.pagos.domain.VeredictoDelAbono
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro

/** `testTag` del velo que tapa la captura mientras la hoja está arriba. */
const val VELO_TAG: String = "pagos_abono_velo"

/** `testTag` de la hoja de confirmación (el paso dos). */
const val HOJA_TAG: String = "pagos_abono_hoja"

/** `testTag` del botón que efectivamente registra — el único que mueve dinero. */
const val CONFIRMAR_TAG: String = "pagos_abono_confirmar"

/** `testTag` del botón que sale del paso dos sin registrar. */
const val EDITAR_TAG: String = "pagos_abono_editar"

/** `testTag` de la banda ámbar del posible duplicado de la semana. */
const val DUPLICADO_TAG: String = "pagos_abono_duplicado"

/** `testTag` de la banda ámbar del abono corto. */
const val ABONO_CORTO_TAG: String = "pagos_abono_corto"

/** `testTag` del indicador de dos pasos. */
const val DOS_PASOS_TAG: String = "pagos_abono_dos_pasos"

/** `testTag` del saldo nuevo — la consecuencia que el cobrador ve antes de registrar. */
const val SALDO_NUEVO_TAG: String = "pagos_abono_saldo_nuevo"

/** `testTag` del encabezado de aviso escalonado (niveles 2 y 3). */
const val AVISO_DE_LA_HOJA_TAG: String = "pagos_abono_hoja_aviso"

/** `testTag` del campo donde se teclea el monto otra vez (nivel 3). */
const val ECO_DEL_MONTO_TAG: String = "pagos_abono_eco"

private val AVATAR = 40.dp

private val PASO = 18.dp

/**
 * El **paso dos** de la confirmación (mock, paneles 3 y 4).
 *
 * Se dibuja DENTRO de la composición —velo + hoja abajo— y no en un
 * `ModalBottomSheet`: así la confirmación vive en el mismo árbol que la captura
 * (una rotación la recompone en vez de perderla en otra ventana) y los goldens
 * capturan la pantalla completa, con velo y todo, como el mock la enseña.
 *
 * El velo **consume el toque**: mientras la hoja está arriba no se puede
 * alcanzar el CTA de abajo, que es la mitad de "ninguna ruta guarda dos veces".
 * Tocarlo equivale a "editar": sale del paso dos sin registrar nada.
 *
 * Cuando el veredicto trae rarezas, la hoja escala: banda roja arriba, cifra en
 * rojo, y el botón de registrar cambia a `Danger` con un texto que **obliga a
 * afirmar el monto** en vez de solo continuar.
 *
 * **La evidencia (Task 22)** ocupa el hueco que este diseño le había dejado:
 * entre [CifraDeLaHoja] y el flujo de saldos, sin mover ninguna de las tres
 * piezas de seguridad. Es una línea de texto y no una miniatura — ver el KDoc
 * de [SeccionDeComprobantes] para por qué.
 */
@Composable
fun HojaDeConfirmacion(
    cliente: String,
    producto: String,
    folio: String,
    importe: Money,
    metodo: MetodoDeCobro,
    veredicto: VeredictoDelAbono,
    esperadoHoy: Money,
    comprobantes: Int,
    onConfirmar: () -> Unit,
    onEditar: () -> Unit,
    aviso: AvisoDelMonto = AvisoDelMonto.NINGUNO,
    eco: String = "",
    puedeConfirmar: Boolean = true,
    onEco: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val rarezas = rarezasQueLaHojaTodaviaDice(veredicto.rarezas, aviso)
    // La hoja escala con el nivel del aviso Y con las rarezas que TODAVÍA se
    // dicen. No con `veredicto.esRaro`: ése cuenta también las que esta hoja ya
    // dejó de pintar, y una hoja roja sin banda que la explique es peor que una
    // hoja normal. Ver el KDoc de `rarezasQueLaHojaTodaviaDice`.
    val raro = aviso.pideFriccionExtra || rarezas.any { it.escalaLaHoja }
    Column(modifier = modifier.fillMaxSize()) {
        // EL VELO ES HERMANO DE LA HOJA, NO SU PADRE — y esa es la razón de
        // que los botones de abajo se puedan tocar.
        //
        // Con el velo envolviendo la hoja, su `detectTapGestures` se quedaba
        // con el toque destinado al botón. Medido, no supuesto: al tocar
        // "confirmar y registrar" el contador de registros quedaba en 0 y el de
        // "editar" (la acción del velo) subía a 1. Un gesto de padre le gana a
        // los `clickable` de sus descendientes.
        //
        // Por eso la zona que cierra el paso dos es solo la franja oscurecida
        // de arriba: no envuelve a nada que tenga que responder.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.background.copy(alpha = VELO_ALFA))
                .pointerInput(Unit) { detectTapGestures { onEditar() } }
                .testTag(VELO_TAG)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // `background(color, shape)` en vez de `clip(shape) + background`.
                // La hoja no desborda, así que el recorte no hace falta — y con
                // ESTA forma sí estorba: medido sobre la estructura ya corregida
                // (velo hermano), un `clip` de
                // `RoundedCornerShape(topStart, topEnd)` deja el toque del botón
                // en 0, y el mismo `clip` con una forma UNIFORME
                // (`RoundedCornerShape(24.dp)`) lo deja en 1.
                //
                // El mecanismo NO es el tipo de `Outline`: `RoundedCornerShape`
                // nunca produce `Outline.Generic`, solo `Rectangle` o `Rounded`.
                // El que decide vive DENTRO de `Outline.Rounded`: `isInRoundedRect`
                // corre `cornersFit`, y cuando las esquinas no encajan cae a
                // `isInPath` -> `Path.op`, que es la parte frágil sin gráficos
                // nativos. Esquinas uniformes -> contención analítica, el toque
                // vive; `topStart`/`topEnd` -> `Path.op`, el toque muere bajo
                // Robolectric.
                //
                // No es el patrón `clip + clickable` de `MspPrimaryFieldButton`:
                // ahí el `clip` va en el MISMO nodo que el `clickable` y la forma
                // es uniforme (16.dp).
                //
                // Alcance, con honestidad: que `MspShapes` sea todo uniforme no
                // basta como garantía, porque las formas declaradas en un archivo
                // no pasan por ahí. Hay una: `ReportSheets.kt:30`
                // (`RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`), que va
                // como `shape` de un `ModalBottomSheet` — y es inofensiva porque
                // esa hoja **no tiene descendientes clickables**, no porque la
                // forma sea segura. Eso es lo que hay que comprobar si aparece
                // otra.
                .background(colors.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                // Un `pointerInput` INERTE: no consume nada —así los botones de
                // adentro siguen respondiendo— pero hace que la hoja sea
                // alcanzable por el hit-test, y con eso ningún toque se cuela a
                // la captura que quedó debajo.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
                // El tag va ANTES del padding: así los bordes del nodo son los de
                // la hoja completa y el test puede afirmar que no queda rendija.
                .testTag(HOJA_TAG)
                .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Agarradera()
            // Las bandas se pintan cuando hay ALGO que decir, no solo cuando la
            // hoja escala: un aviso de tono suave (Ruling AM) tiene banda pero
            // no enciende `raro`. El titular "confirmar abono" le cede el lugar,
            // igual que ya hacía con el duplicado solo.
            if (aviso.mensajes.isNotEmpty()) {
                EncabezadoDelAviso(
                    aviso = aviso,
                    importe = importe,
                    esperadoHoy = esperadoHoy
                )
            }
            if (rarezas.isNotEmpty()) {
                BandasDeRareza(
                    rarezas = rarezas,
                    importe = importe,
                    esperadoHoy = esperadoHoy
                )
            } else if (aviso.mensajes.isEmpty()) {
                Text(
                    text = "Confirmar abono",
                    style = MspTheme.type.cardTitle,
                    color = colors.onSurface
                )
            }
            QuienEs(cliente = cliente, producto = producto, folio = folio, raro = raro)
            CifraDeLaHoja(importe = importe, metodo = metodo, raro = raro)
            ComprobantesDeLaHoja(cuantos = comprobantes)
            FlujoDeSaldos(veredicto = veredicto, raro = raro)
            // Lo que cambia en los casos raros es QUÉ PIDE EL PASO DOS. El
            // `when` es exhaustivo y sin `else`: un nivel nuevo no compila
            // hasta que alguien decida cuánto cuesta decir que sí.
            when (aviso.nivel) {
                NivelDeAviso.TECLEAR -> {
                    EcoDelMonto(importe = importe, eco = eco, onEco = onEco)
                    BotonesDeMontoRaro(
                        confirmarHabilitado = puedeConfirmar,
                        onConfirmar = onConfirmar,
                        onEditar = onEditar
                    )
                }

                NivelDeAviso.NINGUNO,
                NivelDeAviso.BLOQUEO,
                NivelDeAviso.NOTA,
                NivelDeAviso.CONFIRMAR ->
                    if (raro) {
                        BotonesDeMontoRaro(
                            confirmarHabilitado = true,
                            onConfirmar = onConfirmar,
                            onEditar = onEditar
                        )
                    } else {
                        MspPrimaryFieldButton(
                            text = "Confirmar y registrar",
                            onClick = onConfirmar,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(CONFIRMAR_TAG)
                        )
                        MspPrimaryFieldButton(
                            text = "Editar",
                            onClick = onEditar,
                            variant = PrimaryFieldButtonVariant.Ghost,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(EDITAR_TAG)
                        )
                    }
            }
            DosPasos(segundo = segundoPasoDe(aviso, raro))
        }
    }
}

/**
 * **El encabezado del aviso escalonado**, arriba de todo en la hoja, y la
 * **única** banda que habla del monto.
 *
 * Encabeza a propósito: es lo primero que se lee, antes que el nombre y antes
 * que la cifra. Un aviso debajo de la cifra llega cuando el ojo ya siguió de
 * largo hacia el botón.
 *
 * Ámbar en nivel 2 y rojo en nivel 3, la misma escala que la banda en vivo de
 * la captura ([BandaDeAviso]) — el mismo hecho tiene que verse igual en los dos
 * lugares, o el cobrador cree que son dos cosas distintas.
 *
 * ## Se tragó la alerta roja vieja, y ése era el punto
 *
 * Antes de esta banda, [RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO] pintaba su
 * propia `AlertaRoja` diciendo *"monto inusual — verifica"* y *"es mucho mayor
 * al pago esperado, ¿tecleaste un dígito de más?"*. Con el aviso escalonado
 * encima quedaban **dos bandas rojas diciendo el mismo hecho con dos
 * redacciones**, y una de ellas con el regaño que el diseño prohíbe: así se
 * enseña a ignorar el rojo.
 *
 * De aquella banda se conserva **lo único que era dato y no adjetivo**: el par
 * *"Esperado $220 · este abono $1,400"*, que vive ahora en [Cifras], aquí
 * adentro. El resto se fue entero. El par se pinta sólo cuando hay un esperado
 * que enseñar: con `esperadoHoy` en cero no se sabe qué tocaba, y escribir
 * "Esperado $0" sería inventarlo.
 */
@Composable
private fun EncabezadoDelAviso(aviso: AvisoDelMonto, importe: Money, esperadoHoy: Money) {
    val colors = MspTheme.colors
    val grave = aviso.nivel == NivelDeAviso.TECLEAR
    val color = if (grave) colors.statusOverdue else colors.statusPartial
    val fondo = if (grave) colors.statusOverdueTint else colors.statusPartialTint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo, MspTheme.shapes.field)
            .border(if (grave) 1.5.dp else 1.dp, color, MspTheme.shapes.field)
            .padding(horizontal = 13.dp, vertical = 12.dp)
            .testTag(AVISO_DE_LA_HOJA_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
            aviso.mensajes.forEach { mensaje ->
                Text(text = mensaje, style = MspTheme.type.bodyStrong, color = color)
            }
            if (esperadoHoy > Money.ZERO) {
                Text(
                    text = parDeCifras(esperadoHoy = esperadoHoy, importe = importe),
                    style = MspTheme.type.captionStrong,
                    color = color
                )
            }
        }
    }
}

/**
 * **El monto, tecleado otra vez.** La medida de nivel 3.
 *
 * ## Por qué teclear y no un botón rojo
 *
 * Un cero de más **no es una decisión, es un resbalón**. Un botón rojo se
 * confirma igual de rápido que uno gris porque el dedo ya iba en camino: cuando
 * el color aparece, el gesto ya estaba lanzado, y cambiarle el color al destino
 * no detiene un gesto que ya salió. Teclear el monto de nuevo sí atrapa el
 * resbalón, porque para que pase habría que teclear el cero de más **dos
 * veces**, con la cifra equivocada a la vista arriba.
 *
 * Y sólo se dispara en el **0.08 %** de los abonos de la ruta (cinco de 6,165),
 * así que la fricción se la come quien de verdad está haciendo algo sin
 * precedente, no el cobrador que cobra sus $200 de siempre.
 *
 * Se compara el **dinero** y no el texto —lo hace `ConfirmacionPendiente`—, así
 * que "250" y "250.00" valen igual: esto es una red contra un resbalón, no una
 * prueba de mecanografía.
 */
@Composable
private fun EcoDelMonto(importe: Money, eco: String, onEco: (String) -> Unit) {
    val colors = MspTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
        Text(
            text = "Teclea otra vez " + formatMoneyMxn(importe.amount),
            style = MspTheme.type.captionStrong,
            color = colors.onSurfaceMuted
        )
        OutlinedTextField(
            value = eco,
            onValueChange = onEco,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(ECO_DEL_MONTO_TAG)
        )
    }
}

/**
 * Los dos botones del monto marcado. [confirmarHabilitado] es `false` mientras
 * el eco del nivel 3 no cuadra — **apagado es apagado**, igual que el CTA de la
 * captura: un botón que se puede tocar y no hace nada es como un cobrador
 * decide que la app está rota.
 */
@Composable
private fun BotonesDeMontoRaro(
    confirmarHabilitado: Boolean,
    onConfirmar: () -> Unit,
    onEditar: () -> Unit
) {
    MspPrimaryFieldButton(
        text = AFIRMAR_EL_MONTO,
        onClick = onConfirmar,
        enabled = confirmarHabilitado,
        variant = PrimaryFieldButtonVariant.Danger,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CONFIRMAR_TAG)
    )
    MspPrimaryFieldButton(
        text = "Corregir monto",
        onClick = onEditar,
        variant = PrimaryFieldButtonVariant.Ghost,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(EDITAR_TAG)
    )
}

/** Qué pide el paso dos: confirmar, afirmar el monto, o teclearlo otra vez. */
private fun segundoPasoDe(aviso: AvisoDelMonto, raro: Boolean): String = when (aviso.nivel) {
    NivelDeAviso.TECLEAR -> "Teclear el monto"
    NivelDeAviso.NINGUNO,
    NivelDeAviso.BLOQUEO,
    NivelDeAviso.NOTA,
    NivelDeAviso.CONFIRMAR -> if (raro) "Confirmar monto raro" else "Confirmar"
}

/**
 * Las rarezas que la hoja **todavía dice por su cuenta**.
 *
 * Se caen dos, y las dos por la misma razón: el aviso escalonado ya habla de
 * ellas, mejor y con el dato medido detrás.
 *
 *  - [RarezaDelAbono.NO_TERMINA_EN_CINCUENTA] es el mismo hecho que
 *    [com.example.msp_app.feature.pagos.domain.SenalDelMonto.NO_ES_MULTIPLO_DE_CINCUENTA].
 *    El texto viejo ("no termina
 *    en 00 ni en 50, confirma que es correcto") pedía confirmar sin decir por
 *    qué; el nuevo dice el porqué ("Los pagos van de 50 en 50", que es el
 *    99.8 % de los abonos de la ruta).
 *  - [RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO] decía *"monto inusual"* y
 *    *"¿tecleaste un dígito de más?"* — adjetivo y regaño, encima del aviso
 *    nuevo, en rojo los dos. Su único dato, el par de cifras, vive ahora dentro
 *    de [EncabezadoDelAviso].
 *
 * Y se cae una tercera **cuando el monto lo propuso la pantalla**: el abono
 * corto. Los redondos y "lo de siempre" pueden estar por debajo de lo esperado,
 * así que sin esto tocar el chip de $100 abriría una hoja que le reclama al
 * cobrador haber tocado el chip de $100. La app no interroga lo que propuso.
 *
 * [RarezaDelAbono.YA_ABONO_ESTE_PERIODO] **nunca** se cae: no es un juicio
 * sobre el monto sino un hecho sobre la cuenta, y el chip correcto cobrado dos
 * veces en la misma semana es exactamente el cobro duplicado que hay que
 * avisar.
 *
 * El filtro vive aquí y **no** en `SeguridadDelAbono`: ese enum es el veredicto
 * de seguridad y se queda entero. Lo que sobra es el texto, y el texto es cosa
 * de esta pantalla.
 */
private fun rarezasQueLaHojaTodaviaDice(
    rarezas: Set<RarezaDelAbono>,
    aviso: AvisoDelMonto
): Set<RarezaDelAbono> {
    val sinLasQueYaDiceElAviso = rarezas -
        RarezaDelAbono.NO_TERMINA_EN_CINCUENTA -
        RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO
    return if (aviso.loPropusoLaPantalla) {
        sinLasQueYaDiceElAviso - RarezaDelAbono.ABAJO_DE_LO_ESPERADO
    } else {
        sinLasQueYaDiceElAviso
    }
}

/**
 * El texto del botón rojo. **Es la medida de seguridad, no una etiqueta**: pide
 * afirmar el monto, no solo continuar, y por eso se conserva completo en vez de
 * recortarse a las 2-4 palabras que pide la regla de copy del plan. Un "sí"
 * ambiguo delante de un monto raro no obliga a nada.
 */
const val AFIRMAR_EL_MONTO: String = "Sí, el monto es correcto"

private const val VELO_ALFA = 0.72f

@Composable
private fun Agarradera() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MspTheme.spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MspTheme.colors.outline)
        )
    }
}

/** La fila de identidad (`.who`): iniciales, nombre y producto · venta. */
@Composable
private fun QuienEs(cliente: String, producto: String, folio: String, raro: Boolean) {
    val colors = MspTheme.colors
    // El avatar SÍ lleva estado (tabla del Task 2 §3(b)): verde en la captura
    // sana, rojo cuando el monto está marcado.
    val contenido = if (raro) colors.statusOverdue else colors.statusPaid
    val fondo = if (raro) colors.statusOverdueTint else colors.statusPaidTint
    Row(
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(AVATAR)
                .clip(MspTheme.shapes.control)
                .background(fondo),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = inicialesDe(cliente),
                style = MspTheme.type.captionStrong,
                color = contenido
            )
        }
        Column {
            // DOS líneas para el nombre, y elipsis si aun así no cabe (Ruling
            // AP). En una pantalla de dinero la identidad es el dato que no se
            // puede adivinar: a escala 2.0 "Victoria Flores Olmedo" se cortaba
            // en "Victoria Flores" y el apellido desaparecía **sin ninguna
            // señal** de que faltaba texto. Un apellido con elipsis sigue sin
            // leerse, por eso aquí la respuesta es dejarlo bajar de línea.
            Text(
                text = cliente,
                style = MspTheme.type.name,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // El producto y el folio se quedan en UNA línea: a diferencia del
            // nombre, los dos se pueden reconstruir —el folio está en la tira de
            // contexto de la pantalla de atrás y en el ticket—, así que basta con
            // que el corte se vea.
            Text(
                text = "$producto · $folio",
                style = MspTheme.type.caption,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** La cifra protagonista de la hoja (`.sbig`), roja cuando el monto está marcado. */
@Composable
private fun CifraDeLaHoja(importe: Money, metodo: MetodoDeCobro, raro: Boolean) {
    val colors = MspTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatMoneyMxn(importe.amount),
            style = MspTheme.type.amountHero,
            color = if (raro) colors.statusOverdue else colors.statusPaid,
            textAlign = TextAlign.Center
        )
        Text(
            text = metodo.etiqueta,
            style = MspTheme.type.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = MspTheme.spacing.xs)
        )
    }
}

/**
 * **Saldo anterior → saldo nuevo** (`.flowb`): la consecuencia, enseñada antes
 * de tocar dinero. Es lo que convierte un error de dedo en un error visible, y
 * la razón entera de que la confirmación sea de dos pasos.
 */
@Composable
private fun FlujoDeSaldos(veredicto: VeredictoDelAbono, raro: Boolean) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface2, MspTheme.shapes.field)
            .border(1.dp, colors.outline, MspTheme.shapes.field)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CeldaDeSaldo(
            etiqueta = "saldo anterior",
            importe = veredicto.saldoAnterior,
            color = colors.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(text = "→", style = MspTheme.type.body, color = colors.onSurfaceMuted)
        CeldaDeSaldo(
            etiqueta = "saldo nuevo",
            importe = veredicto.saldoNuevo,
            color = if (raro) colors.statusOverdue else colors.statusPaid,
            modifier = Modifier
                .weight(1f)
                .testTag(SALDO_NUEVO_TAG)
        )
    }
}

@Composable
private fun CeldaDeSaldo(
    etiqueta: String,
    importe: Money,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Elipsis (Ruling AP): a escala 2.0 "saldo anterior" y "saldo nuevo"
        // quedaban las dos en "saldo", idénticas y sin marca de corte. Con la
        // elipsis al menos se ve que están recortadas — ver el reporte de la
        // ronda 5 para si eso alcanza para distinguirlas.
        Text(
            text = etiqueta.uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.eyebrow,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatMoneyMxn(importe.amount),
            style = MspTheme.type.amountRow,
            color = color,
            maxLines = 1
        )
    }
}

/**
 * Las bandas que **no** hablan de si el monto es raro.
 *
 * Quedan dos, y ninguna compite con [EncabezadoDelAviso]:
 *  - **el abono corto**, que describe un desenlace que el dominio modela como
 *    normal (`EstadoCuenta` distingue *Pagó* de *Abonó parcial*) y por eso va en
 *    ámbar sin escalar la hoja;
 *  - **el posible duplicado**, que no es un juicio sobre el monto sino un hecho
 *    sobre la CUENTA: esta venta ya recibió dinero en el periodo abierto. Por
 *    eso sobrevive incluso cuando el monto lo propuso la propia pantalla —
 *    tocar el chip correcto dos veces en la misma semana sigue siendo el cobro
 *    duplicado que hay que avisar.
 *
 * Las dos son `if` sueltos y no un `when`: describen hechos distintos del mismo
 * abono y no compiten entre sí. El `when` que elegía UNA alerta roja se fue con
 * las alertas rojas — hoy la única que existe es [EncabezadoDelAviso], y es una
 * sola por construcción.
 */
@Composable
private fun BandasDeRareza(rarezas: Set<RarezaDelAbono>, importe: Money, esperadoHoy: Money) {
    if (RarezaDelAbono.ABAJO_DE_LO_ESPERADO in rarezas) {
        BandaDeAbonoCorto(importe = importe, esperadoHoy = esperadoHoy)
    }
    if (RarezaDelAbono.YA_ABONO_ESTE_PERIODO in rarezas) BandaDeDuplicado()
}

/**
 * **El abono corto, en ámbar y no en rojo** (Ruling AM, ronda 3 de arreglo).
 *
 * Repone el aviso que `NewPaymentDialog` pintaba y que se perdió al retirarlo,
 * pero en el tono que le toca. Cobrar menos de la cuota es normal y frecuente;
 * lo que el aviso compra es que sea **deliberado**, no que parezca un accidente.
 *
 * ## El color: el mismo ámbar que el duplicado, y a propósito (Ruling AN)
 *
 * Es `MspTheme.colors.statusPartial`, el MISMO token que usa [BandaDeDuplicado].
 *
 * **Ojo con el nombre, que engaña:** pese a llamarse `statusPartial`, ese token
 * NO es el de `EstadoCuenta.ABONO_PARCIAL` — `EstadoCuentaUi.contenidoDe` pinta
 * `PARCIAL` con `statusTeal`, y `statusPartial` es en realidad el de `REGRESAS`.
 * Una versión anterior de este KDoc afirmaba la coherencia contraria y era
 * falsa; queda escrito aquí para que nadie la vuelva a deducir del nombre.
 *
 * Compartir color con el duplicado **no borra** la distinción que el Ruling AM
 * buscaba, porque esa distinción no vive en el color de la banda: vive en la
 * **escalada** — cifra roja y CTA `Danger` para lo anómalo, hoja normal para
 * esto. Inventar un token nuevo sería trabajo de diseño que nadie pidió.
 *
 * Lleva las **dos cifras** porque sin ellas el cobrador ve que algo falta pero
 * no contra qué: "esperado $220 · este abono $150" es la frase entera.
 *
 * Deliberadamente NO comparte composable con [BandaDeDuplicado] pese al
 * parecido: aquélla tiene golden (`monto_raro`) y extraerle un tronco común
 * arriesgaría moverlo por un píxel de layout, a cambio de ahorrar quince líneas.
 */
@Composable
private fun BandaDeAbonoCorto(importe: Money, esperadoHoy: Money) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.statusPartialTint, MspTheme.shapes.control)
            .border(1.dp, colors.statusPartial, MspTheme.shapes.control)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(ABONO_CORTO_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = colors.statusPartial,
            modifier = Modifier.size(16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
            Text(
                text = "Abono corto",
                style = MspTheme.type.bodyStrong,
                color = colors.statusPartial
            )
            Text(
                text = parDeCifras(esperadoHoy = esperadoHoy, importe = importe),
                style = MspTheme.type.captionStrong,
                color = colors.statusPartial
            )
        }
    }
}

/**
 * El otro modo de falla real: la venta ya recibió dinero en el periodo abierto.
 * El dato sale del dinero del periodo YA derivado, no de ninguna visita.
 */
@Composable
private fun BandaDeDuplicado() {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.statusPartialTint, MspTheme.shapes.control)
            .border(1.dp, colors.statusPartial, MspTheme.shapes.control)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(DUPLICADO_TAG),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = colors.statusPartial,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Ya abonó esta semana",
            style = MspTheme.type.bodyStrong,
            color = colors.statusPartial
        )
    }
}

/** "1 revisado · 2 confirmar" (`.two`): el paso uno ya se dio, falta el dos. */
@Composable
private fun DosPasos(segundo: String) {
    val colors = MspTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(DOS_PASOS_TAG),
        horizontalArrangement = Arrangement.spacedBy(
            MspTheme.spacing.xs + 2.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Paso(numero = "1", hecho = true)
        Text(text = "Revisado", style = MspTheme.type.captionStrong, color = colors.onSurfaceMuted)
        Paso(numero = "2", hecho = false)
        Text(text = segundo, style = MspTheme.type.captionStrong, color = colors.onSurfaceMuted)
    }
}

@Composable
private fun Paso(numero: String, hecho: Boolean) {
    val colors = MspTheme.colors
    val base = if (hecho) {
        Modifier.background(colors.statusPaid, CircleShape)
    } else {
        Modifier.border(1.5.dp, colors.outline, CircleShape)
    }
    Box(modifier = Modifier.size(PASO).then(base), contentAlignment = Alignment.Center) {
        Text(
            text = numero,
            style = MspTheme.type.ringCaption,
            color = if (hecho) colors.statusPaidTint else colors.onSurfaceMuted
        )
    }
}

/**
 * **"Esperado $220 · este abono $150"**: la frase entera, en una sola cadena.
 *
 * Una sola por dos razones. La primera es que es el mismo dato en los dos
 * lugares que lo dicen —el encabezado del aviso y la banda del abono corto—, y
 * partirlo dejaba dos redacciones que se podían despegar. La segunda es que
 * partido en dos literales (`"Esperado $X · " + "este abono $Y"`) el segundo
 * trozo arranca en minúscula a mitad de oración, y la compuerta de mayúsculas
 * —que mide literales, no oraciones— lo marcaba sin que hubiera nada que
 * arreglar.
 */
private fun parDeCifras(esperadoHoy: Money, importe: Money): String {
    val esperado = formatMoneyMxn(esperadoHoy.amount)
    val abono = formatMoneyMxn(importe.amount)
    return "Esperado $esperado · este abono $abono"
}

/** Las iniciales de las dos primeras palabras del nombre. */
fun inicialesDe(nombre: String): String = nombre
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .map { it.first().uppercaseChar() }
    .joinToString("")
    .ifEmpty { "?" }
