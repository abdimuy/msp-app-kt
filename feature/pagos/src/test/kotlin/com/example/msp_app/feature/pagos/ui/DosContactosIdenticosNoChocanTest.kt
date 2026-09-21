package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.BitacoraCompleta
import com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.TipoDeContacto
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Task 5, hallazgo 4 — dos contactos con el mismo instante y el mismo
 * importe no revientan la lista.**
 *
 * `BitacoraScreen.kt` armaba la llave de `LazyColumn` con
 * `"${fecha.toEpochMilli()}:${etiqueta}:${importe?.amount}"` — ninguno de los
 * tres es único por sí mismo. Es EXACTAMENTE el caso que documenta la Task 2
 * (`task-2-brief.md`, y `BitacoraDelClienteTest
 * .dos pagos del mismo cliente a cuentas distintas caen con nombres de cuenta
 * distintos`): un cliente con **dos ventas** a las que se abona **el mismo
 * importe**, en el **mismo minuto** — dos folios reales, `ACR 12845224` y
 * `ACR 14431255`. Con la llave vieja, Compose ve la MISMA llave dos veces:
 * uno de los dos abonos desaparece de la bitácora sin ningún error visible —
 * el cobrador ve UN abono donde hubo dos.
 *
 * [ContactoDeCobranza.id] —`pagoId`/`visitaId`, que no puede chocar entre
 * hechos— es ahora la llave (`BitacoraScreen.kt`), así que los dos abonos se
 * pintan los dos aunque coincidan en todo lo demás.
 *
 * Los dos contactos de este test son ADREDE idénticos salvo `id`, `ventaId` y
 * `cuenta` — igual que el caso real: mismo instante, misma etiqueta ("Abono"),
 * mismo importe. Si cualquiera de esos tres campos volviera a ser la llave,
 * este test vuelve a fallar.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class DosContactosIdenticosNoChocanTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dos contactos con la misma fecha, etiqueta e importe se pintan los dos`() {
        composeTestRule.setContent {
            com.example.msp_app.core.designsystem.theme.MspTheme(
                darkTheme = false,
                animateColors = false
            ) {
                BitacoraContent(
                    state = BitacoraUiState(cargando = false, bitacora = BITACORA),
                    onAtras = {}
                )
            }
        }

        val filas = composeTestRule.onAllNodes(hasTestTag(CONTACTO_EN_LINEA_TAG))
            .fetchSemanticsNodes()
        assertEquals(
            "de los dos abonos idénticos en fecha/etiqueta/importe sólo se pintó " +
                "${filas.size}: la llave de la lista volvió a chocar",
            2,
            filas.size
        )
    }

    private companion object {
        /** El instante y el importe COMPARTIDOS — el choque real. */
        val EL_MISMO_MINUTO: Instant = Instant.parse("2026-09-15T18:20:00Z")
        val EL_MISMO_IMPORTE: Money = Money.of(BigDecimal("400.00"))

        val ABONO_A_LA_RECAMARA = ContactoDeCobranza(
            id = "pago-recamara",
            fecha = EL_MISMO_MINUTO,
            etiqueta = "Abono",
            nota = null,
            estado = EstadoCuenta.PAGO,
            importe = EL_MISMO_IMPORTE,
            tipo = TipoDeContacto.COBRO,
            metodo = MetodoDeCobro.EFECTIVO,
            cobrador = "RUTA 25 - NOE CORTERO",
            ventaId = 12_845_224,
            cuenta = "Recamara cantaro king size chocolate"
        )

        val ABONO_A_LA_BOCINA = ABONO_A_LA_RECAMARA.copy(
            id = "pago-bocina",
            ventaId = 14_431_255,
            cuenta = "Bocina profesional 8'' audiobahn"
        )

        val BITACORA = BitacoraCompleta(
            clienteId = 5021,
            nombre = "Victoria Flores Olmedo",
            direccion = "Privada Hidalgo 12, Atlixco, Puebla",
            contactos = listOf(ABONO_A_LA_RECAMARA, ABONO_A_LA_BOCINA)
        )
    }
}
