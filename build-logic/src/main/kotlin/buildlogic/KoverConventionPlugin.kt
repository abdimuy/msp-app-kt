package buildlogic

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `msp.kover` — cobertura de código vía Kover. Umbral placeholder (0%): las
 * reglas reales por módulo se ajustan en los planes de migración siguientes,
 * una vez que cada módulo tenga su propia línea base de cobertura. Lo único
 * que importa aquí es que el plugin aplique sin fallar.
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KoverProjectExtension> {
                reports {
                    verify {
                        rule("msp placeholder coverage floor") {
                            minBound(0)
                        }
                    }
                }
            }
        }
    }
}
