package buildlogic

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `msp.detekt` — ruleset COMPLETO de detekt (`buildUponDefaultConfig = true`):
 * complexity, potential-bugs, style, performance, coroutines, exceptions,
 * naming, empty-blocks, comments... el default set de detekt 1.23, ajustado
 * por `config/detekt/detekt.yml` (raíz del repo) solo donde una regla choca
 * con idiomas legítimos de Kotlin/Compose/Android (ver ese archivo para el
 * detalle regla-por-regla, con la razón de cada ajuste).
 *
 * El módulo boundary ES el enforcement boundary (spec de la migración
 * multi-módulo, Plan 2 Task detekt-strict): este plugin se aplica SOLO a
 * módulos nuevos/migrados (`:core:common`, `:core:database`, `:core:testing`,
 * `:build-tools:detekt-rules`, y cualquier `:core:*`/`:feature:*` que se cree
 * después). `:app` (código legacy pre-migración) NO lo aplica — sigue con
 * ktlint + la regla `NoDoubleForMoney` nada más, como antes de esta tarea. A
 * medida que código de `:app` se muda a un módulo nuevo bajo el strangler-fig,
 * automáticamente queda bajo este ruleset estricto sin tocar ninguna config:
 * no hace falta un baseline de supresión masiva ni una lista de exclusión
 * manual por archivo.
 *
 * No se aplica el ruleset `formatting` (ni `detekt-formatting` como
 * `detektPlugins`): ese terreno ya lo cubre `ktlint` en todos los módulos
 * (convención ya establecida en el repo) — correr los dos sobre el mismo
 * archivo produciría hallazgos duplicados/contradictorios sobre espaciado e
 * indentación sin aportar nada nuevo.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")

            extensions.configure<DetektExtension> {
                buildUponDefaultConfig = true
                allRules = false
                parallel = true
                config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
            }
        }
    }
}
