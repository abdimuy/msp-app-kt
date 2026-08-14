package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.DayChipUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.components.DayStrip
import java.time.LocalDate
import org.junit.Test

/**
 * Goldens de la tira de días del ciclo ([DayStrip]) — el control nuevo del periodo Día.
 *
 * Se captura SOLO la tira (mismo patrón acotado que `CollectionReportDetailScreenshotTest`): los
 * goldens de la pantalla completa ya existen y NO cambian, porque sus fixtures
 * ([MockupFixtures.stateDia]) no traen ciclo y sin ciclo la tira ni se monta.
 *
 * Fixture: el ciclo REAL de la ruta 34 (jue 6 … jue 13 de agosto de 2026, con el día de la carga
 * y el viernes en cero). Los tres estados visuales conviven en una sola imagen:
 * - `hoy` (jue 13) verde,
 * - `seleccionado` (mié 12, en el golden "pasado") azul lleno,
 * - `hoy y seleccionado` fundidos en verde lleno (golden "hoy"),
 * - y los días en cero atenuados, presentes — nunca ausentes.
 *
 * El golden `carga` congela además las dos líneas honestas del día en cero: "Sin cobros" y la
 * hora de arranque del ciclo.
 */
class DayStripScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `tira con hoy seleccionado light`() {
        capture(name = "collection_report_day_strip_hoy_light", dark = false) {
            Strip(MockupFixtures.cicloRuta34())
        }
    }

    @Test
    fun `tira con hoy seleccionado dark`() {
        capture(name = "collection_report_day_strip_hoy_dark", dark = true) {
            Strip(MockupFixtures.cicloRuta34())
        }
    }

    /**
     * El estado que ANTES no existía: se está viendo el miércoles y hoy sigue siendo jueves. Es
     * el golden donde conviven los TRES estados a la vez — mié 12 azul lleno (seleccionado),
     * jue 13 verde tintado (hoy, sin seleccionar) y el resto neutro.
     *
     * Usa el TRAMO FINAL del ciclo, no los 8 días: con el ciclo completo a 360dp la tira arranca
     * desplazada al inicio y "hoy" queda fuera del encuadre — el golden congelaría precisamente
     * el estado que viene a demostrar. El ciclo completo ya está congelado en los goldens
     * `hoy` y `carga`.
     */
    @Test
    fun `tira con un dia pasado seleccionado light`() {
        capture(name = "collection_report_day_strip_pasado_light", dark = false) {
            Strip(tramoFinal())
        }
    }

    @Test
    fun `tira con un dia pasado seleccionado dark`() {
        capture(name = "collection_report_day_strip_pasado_dark", dark = true) {
            Strip(tramoFinal())
        }
    }

    @Test
    fun `tira en el dia de la carga, en cero y con su motivo light`() {
        capture(name = "collection_report_day_strip_carga_light", dark = false) {
            Strip(
                days = MockupFixtures.cicloRuta34(seleccionado = DIA_DE_CARGA),
                emptyDay = true,
                note = MockupFixtures.NOTA_CARGA_RUTA_34
            )
        }
    }

    @Test
    fun `tira en el dia de la carga, en cero y con su motivo dark`() {
        capture(name = "collection_report_day_strip_carga_dark", dark = true) {
            Strip(
                days = MockupFixtures.cicloRuta34(seleccionado = DIA_DE_CARGA),
                emptyDay = true,
                note = MockupFixtures.NOTA_CARGA_RUTA_34
            )
        }
    }

    /** El caso que ya rompió a este módulo antes: letra muy grande. */
    @Test
    fun `tira a fontScale 2_0 light`() {
        capture(name = "collection_report_day_strip_2_0_light", dark = false, fontScale = 2.0f) {
            Strip(MockupFixtures.cicloRuta34())
        }
    }

    @Test
    fun `tira a fontScale 2_0 dark`() {
        capture(name = "collection_report_day_strip_2_0_dark", dark = true, fontScale = 2.0f) {
            Strip(MockupFixtures.cicloRuta34())
        }
    }

    /** Últimos [DIAS_VISIBLES] días del ciclo, con el miércoles elegido y hoy sin elegir. */
    private fun tramoFinal(): List<DayChipUi> = MockupFixtures
        .cicloRuta34(seleccionado = MIERCOLES)
        .takeLast(DIAS_VISIBLES)

    private companion object {
        val MIERCOLES: LocalDate = LocalDate.of(2026, 8, 12)
        val DIA_DE_CARGA: LocalDate = LocalDate.of(2026, 8, 6)

        /** Cuántos chips caben enteros en el canvas de 360dp del harness. */
        const val DIAS_VISIBLES = 5
    }
}

@Composable
private fun Strip(days: List<DayChipUi>, emptyDay: Boolean = false, note: String = "") {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.md)
    ) {
        DayStrip(days = days, onSelect = {}, emptyDay = emptyDay, note = note)
    }
}
