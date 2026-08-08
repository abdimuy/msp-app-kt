package com.example.msp_app.buildtools.detekt.money

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty

/**
 * Task 9 (Plan 1, spec S13 Tier A — "anti-Double para dinero"): prohíbe
 * `Double`/`Float` en propiedades, parámetros y funciones cuyo NOMBRE
 * sugiera que representan dinero (monto, importe, saldo, precio, total,
 * abono, pago, money, amount — case-insensitive).
 *
 * Deliberadamente NO intenta razonar sobre tipos vía resolución de tipos
 * (BindingContext) — solo mira sintaxis: el texto del `typeReference`
 * declarado, o, si el tipo es inferido, si el inicializador/cuerpo es un
 * literal flotante (`10.0`, `10.0f`). Esto la hace barata (no necesita el
 * classpath completo del módulo para correr) y suficiente para el caso de
 * uso: todavía no existen value objects de dinero (llegan en Plan 5), así
 * que hoy el gate debe estar armado pero en verde.
 *
 * Alcance: esta regla se registra en un RuleSetProvider separado
 * (`MoneyRuleSetProvider`) y solo se activa en los módulos que aplican
 * `config/detekt/detekt.yml` con `buildUponDefaultConfig = false` — hoy
 * `:core:common`. Añadir un módulo nuevo (p. ej. `:feature:collectionReport`
 * en Plan 5) a este alcance es aplicar el mismo plugin + config + dependencia
 * `detektPlugins(project(":build-tools:detekt-rules"))`, sin tocar la regla.
 */
class NoDoubleForMoneyRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "NoDoubleForMoney",
        severity = Severity.Defect,
        description = "No uses Double/Float para representar dinero; usa BigDecimal, " +
            "Long (centavos) o un value object de dinero dedicado.",
        debt = Debt.TEN_MINS
    )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        val name = property.nameAsSafeName.asString()
        if (!looksLikeMoney(name)) return

        val typeReference = property.typeReference
        if (typeReference != null) {
            checkExplicitType(name, typeReference.text, property)
            return
        }
        checkInferredLiteral(name, property.initializer, property)
    }

    override fun visitParameter(parameter: KtParameter) {
        super.visitParameter(parameter)
        val name = parameter.nameAsSafeName.asString()
        if (!looksLikeMoney(name)) return

        val typeReference = parameter.typeReference ?: return
        checkExplicitType(name, typeReference.text, parameter)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val name = function.nameAsSafeName.asString()
        if (!looksLikeMoney(name)) return

        val typeReference = function.typeReference
        if (typeReference != null) {
            checkExplicitType(name, typeReference.text, function)
            return
        }
        checkInferredLiteral(name, function.bodyExpression, function)
    }

    private fun checkExplicitType(name: String, typeText: String, element: KtElement) {
        val normalized = typeText.trim().removeSuffix("?")
        if (normalized in FORBIDDEN_TYPE_NAMES) {
            report(name, normalized, element)
        }
    }

    private fun checkInferredLiteral(
        name: String,
        expression: org.jetbrains.kotlin.psi.KtExpression?,
        element: KtElement
    ) {
        if (expression !is KtConstantExpression) return
        if (!FLOAT_LITERAL.matches(expression.text)) return
        report(name, "Double/Float (literal inferido)", element)
    }

    private fun report(name: String, typeName: String, element: KtElement) {
        report(
            CodeSmell(
                issue,
                Entity.from(element),
                "'$name' usa $typeName para un valor que parece dinero por su nombre. " +
                    "Usa BigDecimal, Long (centavos) o un value object de dinero."
            )
        )
    }

    private companion object {
        val FORBIDDEN_TYPE_NAMES = setOf("Double", "Float")

        val MONEY_NAME_PATTERN = Regex(
            "(monto|importe|saldo|precio|total|abono|pago|money|amount)",
            RegexOption.IGNORE_CASE
        )

        // Literales flotantes: "10.0", "10.0f"/"10.0F", "10f"/"10F". Cubre
        // Double (sin sufijo, con punto) y Float (sufijo f/F, con o sin punto).
        val FLOAT_LITERAL = Regex(
            "^[0-9_]+(\\.[0-9_]+)?[fF]$|^[0-9_]+\\.[0-9_]+$"
        )

        fun looksLikeMoney(name: String): Boolean = MONEY_NAME_PATTERN.containsMatchIn(name)
    }
}
