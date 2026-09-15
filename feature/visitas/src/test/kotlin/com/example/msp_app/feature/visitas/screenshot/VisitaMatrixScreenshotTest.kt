package com.example.msp_app.feature.visitas.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.feature.visitas.ui.AccionesDeLaVisita
import com.example.msp_app.feature.visitas.ui.RegistrarVisitaContent
import com.example.msp_app.feature.visitas.ui.RegistrarVisitaUiState
import com.example.msp_app.feature.visitas.ui.VisitaFixtures
import org.junit.Test

/**
 * Los **tres estados del mock** `registrar-visita.html` —elegir, prometió y no
 * estaba— en claro y oscuro, y cada uno en las TRES escalas reales de
 * `FontSizeLevel` (1.0 / 1.5 / 2.0).
 *
 * Son 24 goldens de la matriz completa —los tres estados del mock más la
 * selección múltiple de `VisitaB`— y cuatro más de estados que el mock no
 * dibuja: la **cita** con día y hora, la **recomendación a la vista**, y la
 * selección vacía, que es la única forma de apagar el CTA en un desenlace que
 * no pide fecha ni monto.
 */
@Suppress("TooManyFunctions") // un @Test por celda de la matriz; agruparlas escondería cuál falló.
class VisitaMatrixScreenshotTest : VisitasScreenshotTest() {

    @Test
    fun `elegir light normal`() = matriz(
        "elegir",
        VisitaFixtures.elegir(),
        false,
        FontSizeLevel.NORMAL
    )

    @Test
    fun `elegir light grande`() = matriz(
        "elegir",
        VisitaFixtures.elegir(),
        false,
        FontSizeLevel.GRANDE
    )

    @Test
    fun `elegir light muy grande`() =
        matriz("elegir", VisitaFixtures.elegir(), false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `elegir dark normal`() = matriz(
        "elegir",
        VisitaFixtures.elegir(),
        true,
        FontSizeLevel.NORMAL
    )

    @Test
    fun `elegir dark grande`() = matriz(
        "elegir",
        VisitaFixtures.elegir(),
        true,
        FontSizeLevel.GRANDE
    )

    @Test
    fun `elegir dark muy grande`() =
        matriz("elegir", VisitaFixtures.elegir(), true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `prometio light normal`() =
        matriz("prometio", VisitaFixtures.prometio(), false, FontSizeLevel.NORMAL)

    @Test
    fun `prometio light grande`() =
        matriz("prometio", VisitaFixtures.prometio(), false, FontSizeLevel.GRANDE)

    @Test
    fun `prometio light muy grande`() =
        matriz("prometio", VisitaFixtures.prometio(), false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `prometio dark normal`() =
        matriz("prometio", VisitaFixtures.prometio(), true, FontSizeLevel.NORMAL)

    @Test
    fun `prometio dark grande`() =
        matriz("prometio", VisitaFixtures.prometio(), true, FontSizeLevel.GRANDE)

    @Test
    fun `prometio dark muy grande`() =
        matriz("prometio", VisitaFixtures.prometio(), true, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `no estaba light normal`() =
        matriz("no_estaba", VisitaFixtures.noEstaba(), false, FontSizeLevel.NORMAL)

    @Test
    fun `no estaba light grande`() =
        matriz("no_estaba", VisitaFixtures.noEstaba(), false, FontSizeLevel.GRANDE)

    @Test
    fun `no estaba light muy grande`() =
        matriz("no_estaba", VisitaFixtures.noEstaba(), false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `no estaba dark normal`() =
        matriz("no_estaba", VisitaFixtures.noEstaba(), true, FontSizeLevel.NORMAL)

    @Test
    fun `no estaba dark grande`() =
        matriz("no_estaba", VisitaFixtures.noEstaba(), true, FontSizeLevel.GRANDE)

    @Test
    fun `no estaba dark muy grande`() =
        matriz("no_estaba", VisitaFixtures.noEstaba(), true, FontSizeLevel.MUY_GRANDE)

    /**
     * **La selección múltiple** (`VisitaB`): "se negó" con las dos cuentas
     * marcadas. Va en las tres escalas porque es la fila más apretada de la
     * pantalla —marca, nombre de producto y "le toca" en un solo renglón— y es
     * justo donde una escala 2.0 rompe cosas que ningún assert ve.
     */
    @Test
    fun `se nego light normal`() =
        matriz("se_nego", VisitaFixtures.seNegoEnTodo(), false, FontSizeLevel.NORMAL)

    @Test
    fun `se nego light grande`() =
        matriz("se_nego", VisitaFixtures.seNegoEnTodo(), false, FontSizeLevel.GRANDE)

    @Test
    fun `se nego light muy grande`() =
        matriz("se_nego", VisitaFixtures.seNegoEnTodo(), false, FontSizeLevel.MUY_GRANDE)

    @Test
    fun `se nego dark normal`() =
        matriz("se_nego", VisitaFixtures.seNegoEnTodo(), true, FontSizeLevel.NORMAL)

    @Test
    fun `se nego dark grande`() =
        matriz("se_nego", VisitaFixtures.seNegoEnTodo(), true, FontSizeLevel.GRANDE)

    @Test
    fun `se nego dark muy grande`() =
        matriz("se_nego", VisitaFixtures.seNegoEnTodo(), true, FontSizeLevel.MUY_GRANDE)

    /** Todas desmarcadas: el CTA apagado y el pie diciendo qué falta. */
    @Test
    fun `sin cuentas light`() =
        estado("sin_cuentas", VisitaFixtures.seNegoSinCuentas(), dark = false)

    @Test
    fun `sin cuentas dark`() = estado("sin_cuentas", VisitaFixtures.seNegoSinCuentas(), dark = true)

    /** El estado que hasta hoy no existía: cita con día y hora como campos. */
    @Test
    fun `cita light`() = estado("cita", VisitaFixtures.cita(), dark = false)

    @Test
    fun `cita dark`() = estado("cita", VisitaFixtures.cita(), dark = true)

    /** Lo que el sistema sugirió, a la vista — sin mostrarla, guardarla no diría nada. */
    @Test
    fun `recomendacion light`() =
        estado("recomendacion", VisitaFixtures.conRecomendacion(), dark = false)

    @Test
    fun `recomendacion dark`() =
        estado("recomendacion", VisitaFixtures.conRecomendacion(), dark = true)

    private fun matriz(
        nombre: String,
        state: RegistrarVisitaUiState,
        dark: Boolean,
        nivel: FontSizeLevel
    ) = capture(
        name = "visitas_${nombre}_${tema(dark)}_${sufijoDe(nivel)}",
        dark = dark,
        nivel = nivel
    ) {
        Visita(state)
    }

    private fun estado(nombre: String, state: RegistrarVisitaUiState, dark: Boolean) = capture(
        name = "visitas_${nombre}_${tema(dark)}",
        dark = dark
    ) {
        Visita(state)
    }
}

@Composable
private fun Visita(state: RegistrarVisitaUiState) {
    RegistrarVisitaContent(state = state, acciones = AccionesDeLaVisita.NINGUNA)
}
