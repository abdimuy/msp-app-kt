package com.example.msp_app.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Glifo universal de privacidad para montos enmascarados (nombre exacto por
 * contrato del spec §2.1). Cuatro bullets `•` — no revela cuántos dígitos
 * tiene el monto real ni su magnitud, solo comunica "hay un monto, oculto".
 */
const val MASKED_MONEY = "$••••"

/**
 * `Locale` es-MX fijo (NO `Locale.getDefault()`): el formato de dinero es
 * determinista sin importar el idioma/región del dispositivo. Un teléfono en
 * inglés o en es-ES formatea idéntico a uno en es-MX.
 */
private val MXN_LOCALE = Locale("es", "MX")

/**
 * Patrón de formato de moneda (nombre exacto por contrato del spec). El `$` es
 * un literal en el patrón de `DecimalFormat` (no es un metacarácter), así que
 * se imprime tal cual; el símbolo de agrupación (`,`) lo aporta [MXN_LOCALE]
 * — para es-MX es coma de miles, ASCII (verificado contra el JRE: U+002C,
 * signo negativo U+002D). Sin dígitos de fracción a propósito: el negocio no
 * usa centavos, el display siempre es peso entero (ver KDoc de
 * [formatMoneyMxn]).
 */
private const val MXN_PATTERN = "$#,##0"

/**
 * Formatea un monto en pesos mexicanos, en **peso entero, sin centavos**
 * (decisión de negocio: MSP no opera con centavos; ver mockup del piloto,
 * `$18,300` no `$18,300.00`): `formatMoneyMxn(BigDecimal("1200")) ==
 * "$1,200"`, `("18300.5") == "$18,301"` (redondea al peso), `("0") ==
 * "$0"`, negativo `("-850") == "-$850"` (prefijo `-` antes del `$`, formato
 * es-MX real de `DecimalFormat`). Miles/millones agrupan con coma; nunca
 * decimales en el string de display.
 *
 * **Recibe [BigDecimal], NUNCA `Double`** (regla anti-`Double` del money-path:
 * `Double` no puede representar exactamente centavos — p. ej. `2.675` como
 * `Double` redondea mal). No hay overload `Double` "por conveniencia" a
 * propósito: un llamador que tenga un `Double` debe convertir conscientemente.
 * El [amount] que entra SIGUE siendo exacto a centavos (el VO `Money`/
 * `BigDecimal` de escala 2 no cambia) — el redondeo a peso entero ocurre
 * SOLO aquí, en el borde del display; el modelo nunca pierde precisión.
 *
 * Redondeo [RoundingMode.HALF_UP] al peso entero (redondeo comercial
 * mexicano: el medio peso sube siempre, alejándose de cero) — `18300.50 ->
 * $18,301`, `0.50 -> $1`. Se fija explícito porque el default de
 * `DecimalFormat` es `HALF_EVEN` (banquero), que NO es la convención de
 * dinero al consumidor.
 *
 * Normaliza el cero negativo: un monto que redondea a `0` desde el lado
 * negativo (p. ej. `-0.40`) se muestra `"$0"`, nunca `"-$0"` — un signo
 * menos delante de un cero es un artefacto de `DecimalFormat`, no información
 * de dinero real.
 *
 * `DecimalFormat` no es thread-safe; se construye uno nuevo por llamada (barato
 * frente a compartir estado mutable).
 */
fun formatMoneyMxn(amount: BigDecimal): String {
    val formatter = DecimalFormat(MXN_PATTERN, DecimalFormatSymbols(MXN_LOCALE))
    formatter.roundingMode = RoundingMode.HALF_UP
    // Colapsa -0 a 0: si el monto redondeado a peso entero es cero, se formatea
    // BigDecimal.ZERO para que nunca aparezca un "-$0".
    val normalized = if (amount.setScale(0, RoundingMode.HALF_UP).signum() == 0) BigDecimal.ZERO else amount
    return formatter.format(normalized)
}

/**
 * Pinta un monto de dinero es-MX, o [MASKED_MONEY] si [masked] (privacidad:
 * ocultar saldos en pantalla). El default [style] es `amountRow` (montos en
 * fila/columna, figuras proporcionales); los slots hero/display del piloto
 * (Plan 5) pasan `amountHero`/`amountDisplay` explícito.
 *
 * **Reflowea, no trunca:** `softWrap = true` y sin `maxLines = 1` +
 * `TextOverflow.Ellipsis` sobre el número — un monto grande baja de línea
 * antes que perder dígitos (un `$…` truncado es un bug de dinero, no un
 * detalle visual). La aserción de no-truncar a escala grande vive en Task 10.
 */
@Composable
fun MspMoneyText(
    amount: BigDecimal,
    masked: Boolean = false,
    style: TextStyle = MspTheme.type.amountRow,
    color: Color = MspTheme.colors.onSurface,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (masked) MASKED_MONEY else formatMoneyMxn(amount),
        style = style,
        color = color,
        softWrap = true,
        modifier = modifier
    )
}
