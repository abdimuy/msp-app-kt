package com.example.msp_app.feature.ubicacion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.geo.IndiceDePuntosCompartidos
import com.example.msp_app.core.geo.LugaresDelCliente
import com.example.msp_app.core.geo.MedicionDelCobro
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.PUERTA
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.desplazado
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.indiceVacio
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.medicion
import com.example.msp_app.feature.ubicacion.UbicacionFixtures.rutaCompartida
import com.example.msp_app.feature.ubicacion.domain.paraElMapa
import com.example.msp_app.feature.ubicacion.ui.components.COMO_LLEGAR_TAG
import com.example.msp_app.feature.ubicacion.ui.components.HojaDeLosLugares
import com.example.msp_app.feature.ubicacion.ui.components.MUDANZA_TAG
import com.example.msp_app.feature.ubicacion.ui.components.SIN_PUERTA_TAG
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La hoja tiene que decir las dos cuentas, no sólo el aviso genérico.**
 *
 * Evidencia: **E-INF-011** (el productor probado no prueba al consumidor) y
 * **E-INF-010** (por qué esta prueba NO compara archivos), en
 * `msp-api/docs/evidencia/infra.md`.
 *
 * ## El defecto que fija, y cómo se encontró
 *
 * Al grabar los goldens, `ubicacion_compartido_light.png` y
 * `ubicacion_transferencias_light.png` salieron **idénticos byte a byte**. No era
 * cosa de las imágenes: la hoja sólo pintaba "Sin punto propio medido" en los dos
 * casos, de modo que **las dos cuentas del rotulado aprobado** —*"Aquí cobran 30
 * cobradores a 381 clientes"*— no aparecían en ningún lado salvo el globo del
 * marcador, detrás de un toque que el cobrador no tiene por qué adivinar.
 *
 * Es decir: la decisión de **no nombrar el lugar y dar los números en su lugar**
 * estaba a medias, y los nueve goldens estaban verdes.
 *
 * ## Por qué esta prueba y no la comparación de PNG que escribí primero
 *
 * La primera versión comparaba los hashes de los dos archivos. **Era frágil y
 * además mentía:** depende de que `recordRoborazziDebug` ya haya escrito los PNG,
 * y JUnit no garantiza el orden dentro de la misma corrida. Cuando se borraron
 * los goldens para regrabarlos, se puso roja por archivos faltantes; y cuando
 * pasó, **había estado validando imágenes de una corrida anterior** — la tercera
 * variante del mismo falso verde que ya documentan `E-INF-002` y `E-INF-009`.
 *
 * Esta versión monta la hoja y lee el texto. Es determinista, no depende de
 * ningún archivo, y comprueba la propiedad de verdad: **que los dos estados no
 * digan lo mismo**.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class LaHojaDiceLasDosCuentasTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pinta(estado: UbicacionUiState) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                HojaDeLosLugares(state = estado, onComoLlegar = {})
            }
        }
    }

    @Test
    fun `un punto compartido enseña cuantos clientes y cuantos cobradores`() {
        pinta(compartido(clientes = 381, cobradores = 30))
        composeTestRule.onNodeWithTag(SIN_PUERTA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Aquí cobran 30 cobradores a 381 clientes"
        ).assertIsDisplayed()
    }

    @Test
    fun `con un solo cobrador el verbo va en singular`() {
        pinta(compartido(clientes = 48, cobradores = 1))
        composeTestRule.onNodeWithText("Aquí cobra un cobrador a 48 clientes").assertIsDisplayed()
    }

    @Test
    fun `las puras transferencias dicen otra cosa que un punto compartido`() {
        // El caso exacto que los goldens delataron: los dos estados caen en "sin
        // puerta", y aun así la hoja tiene que distinguirlos.
        pinta(soloTransferencias())
        composeTestRule.onNodeWithTag(SIN_PUERTA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Se registró donde estaba el cobrador").assertIsDisplayed()
    }

    @Test
    fun `sin puerta no se ofrece como llegar`() {
        // Mandar a navegar hacia un punto compartido sería mandar al cobrador a
        // la tienda con cara de certeza.
        pinta(compartido(clientes = 381, cobradores = 30))
        composeTestRule.onNodeWithTag(COMO_LLEGAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `con puerta si se ofrece como llegar`() {
        pinta(
            estadoDe(
                (0 until 8).map { medicion(desplazado(PUERTA, it * 2.0), diasAtras = it.toLong()) }
            )
        )
        composeTestRule.onNodeWithTag(COMO_LLEGAR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SIN_PUERTA_TAG).assertDoesNotExist()
    }

    @Test
    fun `la mudanza se ve sin tocar ningun filtro`() {
        pinta(mudanza())
        composeTestRule.onNodeWithTag(MUDANZA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Parece que se mudó").assertIsDisplayed()
    }

    @Test
    fun `un cliente sin coordenadas lo dice y no inventa`() {
        pinta(UbicacionUiState(cargando = false, direccion = "Av. 5 Poniente 1204"))
        composeTestRule.onNodeWithText("Sin ubicación medida").assertIsDisplayed()
        composeTestRule.onNodeWithTag(COMO_LLEGAR_TAG).assertDoesNotExist()
    }

    // ─── Los estados ──────────────────────────────────────────────────────────

    private fun estadoDe(
        mediciones: List<MedicionDelCobro>,
        indice: IndiceDePuntosCompartidos = indiceVacio()
    ): UbicacionUiState {
        val lugares = LugaresDelCliente.de(mediciones, indice)
        return UbicacionUiState(
            cargando = false,
            lugares = lugares.paraElMapa(null),
            direccion = "Av. 5 Poniente 1204, Col. Centro",
            sinPuertaMedida = lugares.sinPuertaMedida,
            pareceMudanza = lugares.pareceMudanza,
            masViejo = mediciones.minOfOrNull { it.fecha },
            masNuevo = mediciones.maxOfOrNull { it.fecha }
        )
    }

    private fun compartido(clientes: Int, cobradores: Int): UbicacionUiState {
        val punto = desplazado(PUERTA, 400.0)
        return estadoDe(
            (0 until 9).map { medicion(desplazado(punto, it * 0.5), diasAtras = it.toLong() * 7) },
            IndiceDePuntosCompartidos.de(rutaCompartida(punto, clientes, cobradores))
        )
    }

    private fun soloTransferencias() = estadoDe(
        (0 until 6).map {
            medicion(
                desplazado(PUERTA, it * 1.0),
                diasAtras = it.toLong() * 7,
                esTransferencia = true
            )
        }
    )

    private fun mudanza() = estadoDe(
        (0 until 5).map {
            medicion(desplazado(PUERTA, it * 2.0), diasAtras = 700L + it, id = "v$it")
        } +
            (0 until 5).map {
                medicion(
                    desplazado(desplazado(PUERTA, 900.0), it * 2.0),
                    diasAtras = 20L + it,
                    id = "n$it"
                )
            }
    )
}
