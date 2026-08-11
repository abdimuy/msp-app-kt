package com.example.msp_app.feature.configuracion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.ChipStatus
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspStatusChip
import com.example.msp_app.core.designsystem.component.OnBrandAlpha
import com.example.msp_app.core.designsystem.component.brandGradientBackground
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal

/** `testTag` de [MiniReportPreview] — localiza el bloque en compose-tests. */
const val MINI_REPORT_PREVIEW_TAG = "msp_configuracion_mini_report_preview"

/**
 * Densidad efectiva del preview para [level] — [Density.density] (px/dp) se conserva del
 * ambiente, solo [Density.fontScale] se pisa por `level.nominalScale`. Extraída como función
 * PURA (sin `@Composable`) para poder probarla con un test JVM plano, determinista, sin
 * Robolectric: el motor de texto shadow (modo gráfico NO-nativo, el que usa
 * [com.example.msp_app.core.testing.RobolectricTestBase] para los compose-tests que no son
 * golden Roborazzi) no garantiza que el tamaño de layout MEDIDO refleje `fontScale` con
 * precisión de píxel — probar el resultado visual ahí sería frágil. Esta función prueba el
 * MECANISMO (qué `Density` se calcula y se provee), que es lo que realmente hace que "la vista
 * previa reaccione al tamaño".
 */
fun previewDensity(ambient: Density, level: FontSizeLevel): Density =
    Density(density = ambient.density, fontScale = level.nominalScale)

private val HERO_HORIZONTAL_PADDING = 14.dp
private val HERO_VERTICAL_PADDING = 12.dp
private val ROW_VERTICAL_PADDING = 10.dp
private val METHOD_DOT_SIZE = 10.dp

/**
 * Fragmento en miniatura del reporte de cobranza (spec §"pantalla
 * `:feature:configuracion`", "preview en vivo del tamaño"): un hero de
 * gradiente de marca + dos filas de pago — 1:1 al mockup aprobado de la
 * sección "Tamaño de letra" (hero "Cobrado · semana" + ritmo, dos filas con
 * tile de método/nombre/folio/monto, la segunda con el chip "Por subir").
 *
 * Renderiza al tamaño **efectivo del nivel [level]**, no al ambiente global:
 * envuelve su contenido en `CompositionLocalProvider(LocalDensity provides
 * previewDensity(current, level))` — mismo mecanismo lineal (Opción C) que la
 * raíz de composición aplica a TODA la app (`MainActivity`),
 * así que el `Text`/`MspMoneyText` de dentro escalan solos vía la resolución
 * estándar de `sp` de Compose, sin tocar cada tamaño a mano. Es el MISMO
 * mecanismo que el usuario verá en el resto de la app al elegir [level] — la
 * vista previa no es una aproximación, es el resultado real.
 */
@Composable
fun MiniReportPreview(level: FontSizeLevel, modifier: Modifier = Modifier) {
    val ambientDensity = LocalDensity.current
    MspCard(modifier = modifier.testTag(MINI_REPORT_PREVIEW_TAG)) {
        CompositionLocalProvider(
            LocalDensity provides previewDensity(ambientDensity, level)
        ) {
            Column {
                PreviewHero()
                PreviewPaymentRow(
                    methodColor = MspTheme.colors.statusPaid,
                    clientName = "Rosa Martínez",
                    meta = "Folio A-10482 · 09:12 · Saldo $5,400",
                    amount = BigDecimal("1800"),
                    pendingUpload = false
                )
                PreviewPaymentRow(
                    methodColor = MspTheme.colors.statusInfo,
                    clientName = "Juan Pérez",
                    meta = "Folio A-10483 · 09:45 · Saldo $3,100",
                    amount = BigDecimal("900"),
                    pendingUpload = true
                )
            }
        }
    }
}

@Composable
private fun PreviewHero() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brandGradientBackground(MspTheme.colors, MspTheme.shapes.tile)
            .padding(horizontal = HERO_HORIZONTAL_PADDING, vertical = HERO_VERTICAL_PADDING),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Text(
            text = "Cobrado · semana",
            style = MspTheme.type.overline,
            color = MspTheme.colors.onBrand.copy(alpha = OnBrandAlpha.OVERLINE)
        )
        MspMoneyText(
            amount = BigDecimal("118400"),
            style = MspTheme.type.amountHero,
            color = MspTheme.colors.onBrand
        )
        Text(
            text = "Cobro 72% [meta 60% ✓] · Cuentas 78%",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onBrand.copy(alpha = OnBrandAlpha.BODY)
        )
    }
}

@Composable
private fun PreviewPaymentRow(
    methodColor: Color,
    clientName: String,
    meta: String,
    amount: BigDecimal,
    pendingUpload: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.sm, vertical = ROW_VERTICAL_PADDING),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(METHOD_DOT_SIZE)
                .background(methodColor, CircleShape)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = clientName, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
            Text(text = meta, style = MspTheme.type.caption, color = MspTheme.colors.onSurfaceMuted)
        }
        if (pendingUpload) {
            MspStatusChip(status = ChipStatus.Pending, text = "Por subir")
        }
        MspMoneyText(
            amount = amount,
            style = MspTheme.type.amountRow,
            color = MspTheme.colors.onSurface
        )
    }
}
