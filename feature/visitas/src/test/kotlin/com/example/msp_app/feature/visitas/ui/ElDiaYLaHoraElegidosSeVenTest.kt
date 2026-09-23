package com.example.msp_app.feature.visitas.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.visitas.domain.DiasSugeridos
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.ui.components.CHIP_TAG
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

/**
 * El defecto reportado por el dueño: al elegir un día u hora que no está entre
 * los sugeridos, ningún chip queda marcado y el cobrador cree que no eligió
 * nada. Este archivo mide la corrección: el chip de escape ("otro día",
 * "otra hora") pasa a mostrar el valor elegido cuando ese valor cae fuera de
 * los sugeridos.
 *
 * `ChipDeOpcion` no expone el estado "activo" como semántica accesible —solo
 * cambia color y borde—, así que la prueba honesta es el TEXTO del chip: es
 * justo lo que el defecto original escondía.
 */
class ElDiaYLaHoraElegidosSeVenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pinta(state: RegistrarVisitaUiState) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                RegistrarVisitaContent(state = state, acciones = AccionesDeLaVisita.NINGUNA)
            }
        }
    }

    private fun estadoDeCita(
        fechaCita: LocalDate? = null,
        horaCita: LocalTime? = null
    ): RegistrarVisitaUiState = VisitaFixtures.estado(
        CapturaDeVisita(
            resultado = ResultadoDeVisita.CITA,
            etiqueta = TipoVisitaCatalogo.PIDE_TIEMPO,
            fechaCita = fechaCita,
            horaCita = horaCita
        )
    )

    @Test
    fun `un dia sugerido deja el chip de otro dia con su texto original`() {
        val sugerido = DiasSugeridos.paraCita(VisitaFixtures.HOY).first()
        pinta(estadoDeCita(fechaCita = sugerido))

        composeTestRule.onNodeWithTag(CHIP_TAG + "otro_dia").assert(hasText(DiasSugeridos.OTRO_DIA))
    }

    @Test
    fun `un dia fuera de los sugeridos deja el chip de escape mostrando ese dia`() {
        // Diez días adelante no es "hoy" ni "mañana", así que su etiqueta cae
        // en el formateador de calendario del propio DiasSugeridos.
        val fueraDeSugeridos = VisitaFixtures.HOY.plusDays(10)
        check(fueraDeSugeridos !in DiasSugeridos.paraCita(VisitaFixtures.HOY)) {
            "la fecha del caso debe caer fuera de los sugeridos"
        }
        pinta(estadoDeCita(fechaCita = fueraDeSugeridos))

        composeTestRule.onNodeWithTag(CHIP_TAG + "otro_dia")
            .assert(hasText(DiasSugeridos.etiquetaDe(fueraDeSugeridos, VisitaFixtures.HOY)))
    }

    @Test
    fun `una hora sugerida deja el chip de otra hora apagado`() {
        val sugerida = DiasSugeridos.HORAS_SUGERIDAS.first()
        pinta(estadoDeCita(horaCita = sugerida))

        composeTestRule.onNodeWithTag(CHIP_TAG + "otra_hora").assert(hasText("Otra hora"))
    }

    @Test
    fun `una hora fuera de las sugeridas deja el chip de escape mostrando esa hora`() {
        val fueraDeSugeridas = LocalTime.of(10, 30)
        check(fueraDeSugeridas !in DiasSugeridos.HORAS_SUGERIDAS) {
            "la hora del caso debe caer fuera de las sugeridas"
        }
        pinta(estadoDeCita(horaCita = fueraDeSugeridas))

        composeTestRule.onNodeWithTag(CHIP_TAG + "otra_hora")
            .assert(hasText(DiasSugeridos.etiquetaDe(fueraDeSugeridas)))
        // "Sin hora" no pierde su texto propio: sigue siendo la opción de
        // dejar la cita sin hora, no la opción elegida.
        composeTestRule.onNodeWithTag(CHIP_TAG + "sin_hora")
            .assert(hasText(DiasSugeridos.SIN_HORA))
    }
}
