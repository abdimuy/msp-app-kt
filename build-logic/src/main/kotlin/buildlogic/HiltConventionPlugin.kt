package buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * `msp.hilt` — inyección de dependencias vía Hilt. Aplica KSP (motor de
 * anotaciones) + el plugin de Hilt, y añade `hilt-android` +
 * `ksp(hilt-compiler)`. No se aplica a ningún módulo todavía (Plan 1+).
 */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")

            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
        }
    }
}
