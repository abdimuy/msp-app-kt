package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.ChipStatus
import com.example.msp_app.core.designsystem.component.MspBentoTile
import com.example.msp_app.core.designsystem.component.MspCarteraCard
import com.example.msp_app.core.designsystem.component.MspHeroTodayCard
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspPaymentSyncPill
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.component.MspProgressRing
import com.example.msp_app.core.designsystem.component.MspSegmentChips
import com.example.msp_app.core.designsystem.component.MspStatusChip
import com.example.msp_app.core.designsystem.component.MspSyncBand
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.component.MspWeeklyBar
import com.example.msp_app.core.designsystem.component.MspWeeklyBarsCard
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.component.SyncBandState
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal

/**
 * Catálogo de componentes firma del design system Msp para el gate visual
 * (Task 10, spec §5 "Screenshot por tier × escala"). Cada `Catalog*` agrupa
 * un componente firma con datos realistas del mockup de referencia (mismos
 * valores que los goldens baseline de Tasks 6-9, para consistencia entre el
 * catálogo y los goldens por-componente ya committeados) —
 * [CatalogScreenshotTest] captura cada uno en la matriz Tier×escala×tema
 * completa.
 *
 * Ancho de referencia fijo a [CATALOG_WIDTH] (320dp, el mismo que
 * [com.example.msp_app.core.designsystem.screenshot.MspBentoTileScreenshotTest]):
 * a `fontScale` grande el contenido crece hacia abajo (wrap/reflow), nunca se
 * recorta lateralmente — excepción deliberada: [CatalogHeroTier2] /
 * [CatalogBentoTier2] angostan aún más el ancho a propósito, para forzar y
 * evidenciar visualmente el reflow (Tier 2 = "layout alterno curado, una idea
 * por vista", task-10-brief.md).
 */
private val CATALOG_WIDTH = 320.dp
private const val HERO_PROGRESS = 0.91f

/** Hero "cobrado hoy" — Tier 1, mismos datos que el golden baseline de Task 8. */
@Composable
fun CatalogHero() {
    MspHeroTodayCard(
        overline = "Cobrado · vie 7 ago",
        delta = "▲ 12% vs ayer",
        amount = BigDecimal("18300"),
        insight = "32 pagos · vas al 91% de tu meta",
        progress = HERO_PROGRESS,
        goalLabel = "meta del día",
        goalAmount = BigDecimal("20000"),
        cashOnHandLabel = "Efectivo en mano",
        cashOnHand = BigDecimal("12100"),
        avgTicketLabel = "Ticket prom.",
        avgTicket = BigDecimal("572"),
        modifier = Modifier.padding(MspTheme.spacing.md)
    )
}

/**
 * Hero — Tier 2 curado: mismo componente, ancho angosto forzado (180dp, el
 * mismo truco que la prueba de no-truncación de Task 8) para que a escala
 * grande el monto reflowee a varias líneas — "una idea por vista", la
 * lectura del monto domina la tarjeta.
 */
@Composable
fun CatalogHeroTier2() {
    MspHeroTodayCard(
        overline = "Cobrado · ciclo actual",
        delta = "▲ 6% vs ciclo",
        amount = BigDecimal("12345678.90"),
        insight = "214 pagos · vas al 91% de la meta",
        progress = HERO_PROGRESS,
        goalLabel = "meta del ciclo",
        goalAmount = BigDecimal("13500000"),
        cashOnHandLabel = "Efectivo en mano",
        cashOnHand = BigDecimal("8450000.50"),
        avgTicketLabel = "Ticket prom.",
        avgTicket = BigDecimal("57680.25"),
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(180.dp)
    )
}

/** Duo de tiles Efectivo/Transferencia — Tier 1, lado a lado. */
@Composable
fun CatalogBentoDuo() {
    Row(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspBentoTile(
            dotColor = MspTheme.colors.statusPaid,
            label = "Efectivo",
            amount = BigDecimal("12100"),
            subLine = "22 pagos",
            modifier = Modifier.weight(1f)
        )
        MspBentoTile(
            dotColor = MspTheme.colors.brand,
            label = "Transferencia",
            amount = BigDecimal("6200"),
            subLine = "10 pagos",
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Duo de tiles — Tier 2 curado: columna única, un tile por fila a ancho
 * completo (task-10-brief.md: "una idea por vista, targets mayores") en vez
 * del duo lado a lado de [CatalogBentoDuo].
 */
@Composable
fun CatalogBentoTier2() {
    Column(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspBentoTile(
            dotColor = MspTheme.colors.statusPaid,
            label = "Efectivo",
            amount = BigDecimal("12100"),
            subLine = "22 pagos",
            modifier = Modifier.fillMaxWidth()
        )
        MspBentoTile(
            dotColor = MspTheme.colors.brand,
            label = "Transferencia",
            amount = BigDecimal("6200"),
            subLine = "10 pagos",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Los cinco [ChipStatus] con su texto español corto — color + ícono + texto siempre juntos. */
@Composable
fun CatalogStatusChips() {
    Column(
        modifier = Modifier.padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspStatusChip(status = ChipStatus.Paid, text = "Pagado")
        MspStatusChip(status = ChipStatus.Partial, text = "Parcial")
        MspStatusChip(status = ChipStatus.Overdue, text = "Vencido")
        MspStatusChip(status = ChipStatus.Pending, text = "Pendiente")
        MspStatusChip(status = ChipStatus.Promise, text = "Promesa")
    }
}

/** Ciclo de 5 días del mockup semana — mismos datos que el golden baseline de Task 8. */
@Composable
fun CatalogWeeklyBars() {
    MspWeeklyBarsCard(
        bars = listOf(
            MspWeeklyBar("lun", 0.74f),
            MspWeeklyBar("mar", 0.87f),
            MspWeeklyBar("mié", 1.0f),
            MspWeeklyBar("jue", 0.89f),
            MspWeeklyBar("vie", 0.64f)
        ),
        todayIndex = 4,
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH)
    )
}

/** Cartera cobrado/pendiente — mismas cifras que el golden baseline de Task 8. */
@Composable
fun CatalogCartera() {
    MspCarteraCard(
        title = "Cartera · por cobrar",
        totalAmount = BigDecimal("186540"),
        collectedAmount = BigDecimal("144240"),
        collectedLabel = "al corriente",
        pendingAmount = BigDecimal("42300"),
        pendingLabel = "vencido",
        caption = "18 clientes activos",
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH)
    )
}

/** Los dos toggles icon-surface 40dp: tema (sol/luna, según [darkTheme]) y privacidad (ojo, ambos estados). */
@Composable
fun CatalogToggles(darkTheme: Boolean) {
    Row(
        modifier = Modifier.padding(MspTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        MspThemeToggle(darkTheme = darkTheme, onToggle = {})
        MspPrivacyEyeToggle(masked = false, onToggle = {})
        MspPrivacyEyeToggle(masked = true, onToggle = {})
    }
}

/** Selector segmentado — Día·Semana y Hora·Nombre, cada uno con su segmento activo. */
@Composable
fun CatalogSegmentChips() {
    Column(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspSegmentChips(options = listOf("Día", "Semana"), selectedIndex = 0, onSelect = {})
        MspSegmentChips(options = listOf("Hora", "Nombre"), selectedIndex = 1, onSelect = {})
    }
}

/** Banda de sincronización (ambos estados) + pill compacta — offline-first de cobranza. */
@Composable
fun CatalogSync() {
    Column(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspSyncBand(
            state = SyncBandState.Pending,
            message = "3 pagos por subir",
            hint = "se sube solo"
        )
        MspSyncBand(
            state = SyncBandState.Ok,
            message = "Todo al día",
            hint = "última sync hace 2 min"
        )
        MspPaymentSyncPill(pendingCount = 3)
    }
}

/** CTA de campo — los tres variants más el estado deshabilitado, apilados a ancho completo. */
@Composable
fun CatalogCta() {
    Column(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspPrimaryFieldButton(
            text = "Registrar pago",
            onClick = {},
            variant = PrimaryFieldButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth()
        )
        MspPrimaryFieldButton(
            text = "Ver detalle",
            onClick = {},
            variant = PrimaryFieldButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth()
        )
        MspPrimaryFieldButton(
            text = "Sí, el monto es correcto",
            onClick = {},
            variant = PrimaryFieldButtonVariant.Danger,
            modifier = Modifier.fillMaxWidth()
        )
        MspPrimaryFieldButton(
            text = "Registrar pago",
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Dinero en tres tamaños de la escala (`amountHero`/`amountCard`/`amountRow`) + una fila enmascarada. */
@Composable
fun CatalogMoneyText() {
    Column(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspMoneyText(amount = BigDecimal("18300"), style = MspTheme.type.amountHero)
        MspMoneyText(amount = BigDecimal("12100"), style = MspTheme.type.amountCard)
        MspMoneyText(amount = BigDecimal("572"), style = MspTheme.type.amountRow)
        MspMoneyText(amount = BigDecimal("18300"), masked = true, style = MspTheme.type.amountRow)
    }
}

/** Iniciales de tres clientes de ejemplo (nombres mexicanos realistas). */
@Composable
fun CatalogAvatar() {
    Row(
        modifier = Modifier.padding(MspTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspInitialsAvatar(initials = "ML")
        MspInitialsAvatar(initials = "JR")
        MspInitialsAvatar(initials = "AC")
    }
}

/** Barra recta (9dp, `heroProgressFill`) + anillo circular (74dp, `brand`), lado a lado. */
@Composable
fun CatalogProgress() {
    Row(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(CATALOG_WIDTH),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            MspProgressBar(
                progress = HERO_PROGRESS,
                height = 9.dp,
                fillColor = MspTheme.colors.heroProgressFill,
                trackColor = MspTheme.colors.progressTrack
            )
        }
        MspProgressRing(
            progress = HERO_PROGRESS,
            fillColor = MspTheme.colors.brand,
            trackColor = MspTheme.colors.progressTrack
        )
    }
}
