package com.example.msp_app.feature.ubicacion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.theme.Manrope
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar

/**
 * **Los tokens del mock aprobado** (`docs/design/mocks/mapa-de-lugares.html`),
 * claro y oscuro OLED, uno por uno.
 *
 * Viven aquí y no en `MspColors` porque varios no existen en el tema (la hoja
 * `#F3F5F4`, las cards `#171D1A`, el "faint", los seis colores de marcador) y el
 * dueño pidió la pantalla **idéntica** al mock. Van como `Int` ARGB porque los
 * marcadores se pintan con `android.graphics`.
 */
@Immutable
data class PaletaDelMapa(
    val sheet: Int,
    val card: Int,
    val cardLine: Int,
    val ctl: Int,
    val ink: Int,
    val mut: Int,
    val faint: Int,
    val line: Int,
    val brand: Int,
    val brandTint: Int,
    val paid: Int,
    val partial: Int,
    val partialTint: Int,
    val overdue: Int,
    val mapBg: Int,
    val scrim: Int,
    val mkCasa: Int,
    val mkFrec: Int,
    val mkComp: Int,
    val mkAnt: Int,
    val mkTran: Int,
    val mkVis: Int,
    val mkRing: Int,
    val mkHollow: Int
) {
    fun c(argb: Int): Color = Color(argb)

    /** El color del marcador de cada clase. */
    fun deClase(clase: ClaseDeLugar): Int = when (clase) {
        ClaseDeLugar.DONDE_MAS_PAGA -> mkCasa
        ClaseDeLugar.OTRO_LUGAR -> mkFrec
        ClaseDeLugar.PAGABA_ANTES -> mkAnt
        ClaseDeLugar.COMPARTIDO -> mkComp
        ClaseDeLugar.TRANSFERENCIAS -> mkTran
        ClaseDeLugar.SUELTO -> mkAnt
    }

    companion object {
        val CLARO = PaletaDelMapa(
            sheet = 0xFFF3F5F4.toInt(), card = 0xFFFFFFFF.toInt(), cardLine = 0xFFE4E8E6.toInt(),
            ctl = 0xFFFFFFFF.toInt(), ink = 0xFF141A18.toInt(), mut = 0xFF5C6863.toInt(),
            faint = 0xFF8C9994.toInt(), line = 0xFFE4E8E6.toInt(), brand = 0xFF2563EB.toInt(),
            brandTint = 0xFFEAF0FE.toInt(), paid = 0xFF177245.toInt(), partial = 0xFFB26A00.toInt(),
            partialTint = 0xFFFFF4E0.toInt(), overdue = 0xFFB42318.toInt(), mapBg = 0xFFECEEEB.toInt(),
            scrim = 0x47141A18, mkCasa = 0xFF2563EB.toInt(), mkFrec = 0xFF0F766E.toInt(),
            mkComp = 0xFFB26A00.toInt(), mkAnt = 0xFF6B7772.toInt(), mkTran = 0xFF0369A1.toInt(),
            mkVis = 0xFF7A5AF8.toInt(), mkRing = 0xFFFFFFFF.toInt(), mkHollow = 0xFFFFFFFF.toInt()
        )
        val OSCURO = PaletaDelMapa(
            sheet = 0xFF0B0F0D.toInt(), card = 0xFF171D1A.toInt(), cardLine = 0xFF2A342F.toInt(),
            ctl = 0xFF1C2320.toInt(), ink = 0xFFE9EFEC.toInt(), mut = 0xFF8B968F.toInt(),
            faint = 0xFF6B7772.toInt(), line = 0xFF28322C.toInt(), brand = 0xFF3B82F6.toInt(),
            brandTint = 0xFF0E2440.toInt(), paid = 0xFF40CB84.toInt(), partial = 0xFFE3AC4E.toInt(),
            partialTint = 0xFF2A1F0B.toInt(), overdue = 0xFFF26A5C.toInt(), mapBg = 0xFF0F1312.toInt(),
            scrim = 0x8C000000.toInt(), mkCasa = 0xFF3B82F6.toInt(), mkFrec = 0xFF0D9488.toInt(),
            mkComp = 0xFFD97706.toInt(), mkAnt = 0xFF6B7772.toInt(), mkTran = 0xFF0EA5E9.toInt(),
            mkVis = 0xFF8B5CF6.toInt(), mkRing = 0xFFFFFFFF.toInt(), mkHollow = 0xFF141917.toInt()
        )
    }
}

/**
 * La paleta del tema vigente. Se decide por **el fondo de `MspTheme`** y no por
 * `appDarkTheme()`: así sigue al tema que de verdad está pintado (el que monta
 * la prueba o el que voltea el reveal), no a la preferencia del sistema.
 */
@Composable
fun paletaDelMapa(): PaletaDelMapa =
    if (MspTheme.colors.background.luminance() < MITAD) PaletaDelMapa.OSCURO else PaletaDelMapa.CLARO

private const val MITAD = 0.5f

/** Un estilo del mock: Manrope al tamaño y peso exactos, cifras tabulares si [tabular]. */
fun estilo(
    tamano: Float,
    peso: Int,
    alto: Float,
    tabular: Boolean = false,
    tracking: Float = 0f
): TextStyle = TextStyle(
    fontFamily = Manrope,
    fontSize = tamano.sp,
    fontWeight = FontWeight(peso),
    lineHeight = alto.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (tabular) "tnum, lnum" else null
)
