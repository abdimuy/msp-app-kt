package com.example.msp_app.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Punto medio (sp) hacia el que [mspCompressedSp] interpola cada `baseSp` al
 * subir de [FontSizeLevel] — deliberadamente cercano a `body`/`input`
 * (13-15sp en `MspType.kt`) para que el texto de lectura corrida casi no se
 * mueva, mientras dinero grande (`heroAmount` 46sp) se topa hacia abajo y
 * captions chicos (`ringCaption` 9sp) suben más rápido que la escala nominal
 * hacia arriba.
 */
private const val MIDPOINT_SP = 20f

/**
 * Cuánta distancia hacia [MIDPOINT_SP] se recorre por unidad de progreso
 * (`t`, ver [FontSizeLevel.compressionProgress]). Acotado a ≤ 1/3 a propósito:
 * por debajo de ese techo, la derivada de [mspCompressedSp] respecto al
 * `nominalScale` es positiva para CUALQUIER `baseSp` ≥ 0 en todo el rango de
 * `nominalScale` (1.0-2.0) — es lo que garantiza la monotonicidad exigida
 * (subir de nivel nunca encoge un rol) sin necesitar clamps ad-hoc por rol.
 *
 * Prueba corta: `raw(baseSp, scale) = scale * (baseSp + K*(scale-1)*(MIDPOINT-baseSp))`.
 * Su derivada respecto a `scale` es
 * `baseSp*(1 - K*(2*scale-1)) + K*MIDPOINT*(2*scale-1)`, lineal creciente en
 * `baseSp` cuando `K*(2*scale-1) < 1` (cierto para `K=0.3` en todo
 * `scale ∈ [1,2]`, donde `2*scale-1 ∈ [1,3]`) — así que el mínimo de la
 * derivada en `baseSp ≥ 0` ocurre en `baseSp=0`, y ahí vale
 * `K*MIDPOINT*(2*scale-1) ≥ 0`. Nunca cruza cero.
 */
private const val COMPRESSION_STRENGTH = 0.3f

/**
 * Piso absoluto (sp) por debajo del cual [mspCompressedSp] nunca cae, sin
 * importar `baseSp` — crece con el nivel (a mayor nivel, más alto el mínimo
 * legible). Con los roles reales de `MspTypography` (9sp-46sp) este piso no
 * se activa nunca — solo protege un `baseSp` fuera de ese rango.
 */
private const val FLOOR_BASE_SP = 8f
private const val FLOOR_GROWTH_SP = 8f

/**
 * Techo absoluto (sp) — misma lógica que el piso, red de seguridad ante un
 * `baseSp` fuera del rango real de `MspTypography`; no se activa para la
 * escala actual.
 */
private const val CEILING_BASE_SP = 60f
private const val CEILING_GROWTH_SP = 60f

/**
 * Progreso 0..1 entre [FontSizeLevel.NORMAL] y [FontSizeLevel.MUY_GRANDE] —
 * derivado de `nominalScale` en vez de hardcodear 0f/0.5f/1f por nivel, para
 * que un futuro ajuste de las constantes del enum no desalinee esta rampa.
 */
private fun FontSizeLevel.compressionProgress(): Float {
    val normal = FontSizeLevel.NORMAL.nominalScale
    val max = FontSizeLevel.MUY_GRANDE.nominalScale
    return (nominalScale - normal) / (max - normal)
}

/**
 * Rampa tipográfica comprimida (spec §"Compresión de jerarquía"): en vez de
 * escalar cada tamaño linealmente por `level.nominalScale`, interpola cada
 * `baseSp` hacia [MIDPOINT_SP] (también escalado) con fuerza proporcional al
 * nivel. Efecto: los roles chicos (`caption`, `ringCaption`) suben MÁS rápido
 * que la escala nominal (piso alto) y los grandes (`heroAmount`,
 * `amountDisplay`) se topan (techo) — el ratio máximo/mínimo de la jerarquía
 * se ENCOGE al subir de nivel, así todo cabe y se lee cómodo en MUY_GRANDE sin
 * que el hero se desborde.
 *
 * Pura y determinista — sin `@Composable`, sin `LocalDensity`: las pantallas
 * migradas la llaman directo con el nivel EFECTIVO ya resuelto
 * (`máx(app, OS)`, spec §"Mecánica del tamaño de letra"). **Cuidado
 * double-scaling** (mismo spec): el subárbol que use esta rampa debe
 * neutralizar `LocalDensity.fontScale` a `1f` — esta función ya devuelve el
 * tamaño final para el nivel; un `fontScale` del sistema aplicado ENCIMA lo
 * multiplicaría dos veces.
 *
 * En [FontSizeLevel.NORMAL] (`t=0`) es esencialmente un no-op: `raw == baseSp`
 * (el clamp de piso/techo no se activa para el rango real de `MspTypography`).
 */
fun mspCompressedSp(baseSp: Float, level: FontSizeLevel): TextUnit {
    val scale = level.nominalScale
    val t = level.compressionProgress()

    val linear = baseSp * scale
    val towardMidpoint = MIDPOINT_SP * scale
    val pull = COMPRESSION_STRENGTH * t
    val raw = linear + pull * (towardMidpoint - linear)

    val floor = FLOOR_BASE_SP + FLOOR_GROWTH_SP * t
    val ceiling = CEILING_BASE_SP + CEILING_GROWTH_SP * t

    return raw.coerceIn(floor, ceiling).sp
}

/**
 * Aplica [mspCompressedSp] a UN [TextStyle] ya construido por `campoStyle` (`MspType.kt`),
 * preservando el resto de sus propiedades (familia, peso, `letterSpacing` en `em` — que ya
 * auto-escala con `fontSize`, `fontFeatureSettings`). El `baseSp` de partida es el propio
 * `fontSize.value` del estilo (siempre construido a NORMAL, ver [mspTypography]) — no un
 * literal repetido — y el `lineHeight` se recalcula preservando el MISMO ratio
 * `lineHeight/fontSize` (1.4× en `campoStyle`) en vez de re-hardcodear ese factor aquí, así un
 * futuro ajuste de ese ratio en `MspType.kt` no desalinea esta función.
 */
private fun TextStyle.compressed(level: FontSizeLevel): TextStyle {
    val baseSp = fontSize.value
    val lineHeightRatio = lineHeight.value / baseSp
    val compressedSize = mspCompressedSp(baseSp, level)
    return copy(fontSize = compressedSize, lineHeight = (compressedSize.value * lineHeightRatio).sp)
}

/**
 * Rampa comprimida completa (Task "Aplicar la rampa comprimida al reporte"): mapea CADA rol de
 * [MspTypography] a través de [TextStyle.compressed]. [FontSizeLevel.NORMAL] devuelve `this` sin
 * reconstruir nada — no solo por costo, sino para que la identidad (`===`) se preserve y
 * `remember`/composables `@Immutable` no vean un cambio donde no lo hay.
 *
 * Consumido por el composition root que provea un [MspTheme] con tipografía comprimida para su
 * subárbol (p. ej. `ThemeRevealRoot` del reporte de cobranza) — **ese subárbol debe además
 * neutralizar `LocalDensity.fontScale` a `1f`** (ver KDoc de [mspCompressedSp], "cuidado
 * double-scaling"): esta función ya devuelve el tamaño FINAL para [level], y el `fontScale`
 * lineal que la raíz de composición aplica globalmente (Opción C, `MainActivity`) lo
 * multiplicaría una segunda vez si no se neutraliza.
 */
fun MspTypography.compressed(level: FontSizeLevel): MspTypography {
    if (level == FontSizeLevel.NORMAL) return this
    return MspTypography(
        heroAmount = heroAmount.compressed(level),
        amountDisplay = amountDisplay.compressed(level),
        amountHero = amountHero.compressed(level),
        amountLarge = amountLarge.compressed(level),
        amountCard = amountCard.compressed(level),
        amountMedium = amountMedium.compressed(level),
        amountSale = amountSale.compressed(level),
        amountRow = amountRow.compressed(level),
        amountInline = amountInline.compressed(level),
        amountSplit = amountSplit.compressed(level),
        metricLarge = metricLarge.compressed(level),
        metricSmall = metricSmall.compressed(level),
        kvValue = kvValue.compressed(level),
        heroStatValue = heroStatValue.compressed(level),
        keypadKey = keypadKey.compressed(level),
        keypadKeyAlt = keypadKeyAlt.compressed(level),
        ringValue = ringValue.compressed(level),
        greeting = greeting.compressed(level),
        detailTitle = detailTitle.compressed(level),
        screenTitle = screenTitle.compressed(level),
        cardTitle = cardTitle.compressed(level),
        listTitle = listTitle.compressed(level),
        saleTitle = saleTitle.compressed(level),
        name = name.compressed(level),
        buttonLarge = buttonLarge.compressed(level),
        buttonSmall = buttonSmall.compressed(level),
        input = input.compressed(level),
        body = body.compressed(level),
        bodyStrong = bodyStrong.compressed(level),
        methodLabel = methodLabel.compressed(level),
        subtitle = subtitle.compressed(level),
        contextNote = contextNote.compressed(level),
        segmentLabel = segmentLabel.compressed(level),
        chipLabel = chipLabel.compressed(level),
        sectionHeader = sectionHeader.compressed(level),
        sectionLabel = sectionLabel.compressed(level),
        overline = overline.compressed(level),
        eyebrow = eyebrow.compressed(level),
        syncLabel = syncLabel.compressed(level),
        trendLabel = trendLabel.compressed(level),
        tileLabel = tileLabel.compressed(level),
        nextStopLabel = nextStopLabel.compressed(level),
        saleMeta = saleMeta.compressed(level),
        caption = caption.compressed(level),
        captionStrong = captionStrong.compressed(level),
        kvLabel = kvLabel.compressed(level),
        navLabel = navLabel.compressed(level),
        ringCaption = ringCaption.compressed(level)
    )
}
