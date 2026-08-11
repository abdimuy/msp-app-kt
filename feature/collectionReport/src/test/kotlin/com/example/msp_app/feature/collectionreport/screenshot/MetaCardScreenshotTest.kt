package com.example.msp_app.feature.collectionreport.screenshot

import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.components.MetaCard
import com.example.msp_app.feature.collectionreport.ui.components.MetaCardTier2
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de "Meta de la semana": [MetaCard] (Tier 1, anillos lado a
 * lado) y [MetaCardTier2] (Tier 2, anillos apilados) — mismos datos del mockup que
 * [MockupFixtures.heroSemana] (91%/78%, 39 de 50 clientes, meta 60% ya alcanzada).
 */
class MetaCardScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `MetaCard Tier 1 lado a lado light`() {
        capture(name = "meta_card_tier1_light", dark = false) {
            MetaCard(
                porcentajeCobro = MockupFixtures.PORCENTAJE_COBRO_SEMANA,
                porcentajeCuentas = MockupFixtures.PORCENTAJE_CUENTAS_SEMANA,
                clientesPagaron = MockupFixtures.CLIENTES_PAGARON_SEMANA,
                clientesTotal = MockupFixtures.CLIENTES_TOTAL_SEMANA
            )
        }
    }

    @Test
    fun `MetaCard Tier 1 lado a lado dark`() {
        capture(name = "meta_card_tier1_dark", dark = true) {
            MetaCard(
                porcentajeCobro = MockupFixtures.PORCENTAJE_COBRO_SEMANA,
                porcentajeCuentas = MockupFixtures.PORCENTAJE_CUENTAS_SEMANA,
                clientesPagaron = MockupFixtures.CLIENTES_PAGARON_SEMANA,
                clientesTotal = MockupFixtures.CLIENTES_TOTAL_SEMANA
            )
        }
    }

    @Test
    fun `MetaCardTier2 apilado light`() {
        capture(name = "meta_card_tier2_light", dark = false) {
            MetaCardTier2(
                porcentajeCobro = MockupFixtures.PORCENTAJE_COBRO_SEMANA,
                porcentajeCuentas = MockupFixtures.PORCENTAJE_CUENTAS_SEMANA,
                clientesPagaron = MockupFixtures.CLIENTES_PAGARON_SEMANA,
                clientesTotal = MockupFixtures.CLIENTES_TOTAL_SEMANA
            )
        }
    }

    @Test
    fun `MetaCardTier2 apilado dark`() {
        capture(name = "meta_card_tier2_dark", dark = true) {
            MetaCardTier2(
                porcentajeCobro = MockupFixtures.PORCENTAJE_COBRO_SEMANA,
                porcentajeCuentas = MockupFixtures.PORCENTAJE_CUENTAS_SEMANA,
                clientesPagaron = MockupFixtures.CLIENTES_PAGARON_SEMANA,
                clientesTotal = MockupFixtures.CLIENTES_TOTAL_SEMANA
            )
        }
    }
}
