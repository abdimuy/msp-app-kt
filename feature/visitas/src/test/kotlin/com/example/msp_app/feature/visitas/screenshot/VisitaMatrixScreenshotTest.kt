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
 * Son 18 goldens de la matriz completa más cuatro de los dos estados que el
 * mock no dibuja pero que esta tarea hizo posibles: la **cita** con día y hora
 * (el estado que estaba muerto hasta hoy) y la **recomendación a la vista**.
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
