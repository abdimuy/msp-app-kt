package com.example.msp_app.buildtools.detekt.money

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.RuleSet
import io.gitlab.arturbosch.detekt.api.RuleSetProvider

/**
 * Registrado vía `ServiceLoader`
 * (`src/main/resources/META-INF/services/io.gitlab.arturbosch.detekt.api.RuleSetProvider`)
 * para que detekt lo descubra al declarar
 * `detektPlugins(project(":build-tools:detekt-rules"))` en el módulo consumidor.
 */
class MoneyRuleSetProvider : RuleSetProvider {
    override val ruleSetId: String = "money"

    override fun instance(config: Config): RuleSet =
        RuleSet(ruleSetId, listOf(NoDoubleForMoneyRule(config)))
}
