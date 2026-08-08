package buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `msp.android.compose` — presupone `msp.android.library` (lo aplica si
 * hace falta), habilita Compose y añade el compilador de Compose + las deps
 * base de UI (bundle `compose-ui`), replicando cómo `:app` cablea sus
 * configuraciones de test de Compose (junit4 en androidTest, manifest+tooling
 * solo en debug).
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("msp.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                add("implementation", platform(libs.findLibrary("androidx-compose-bom").get()))
                add("implementation", libs.findBundle("compose-ui").get())
                add(
                    "androidTestImplementation",
                    platform(libs.findLibrary("androidx-compose-bom").get())
                )
                add("androidTestImplementation", libs.findLibrary("androidx-ui-test-junit4").get())
                add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())
                add("debugImplementation", libs.findLibrary("androidx-ui-test-manifest").get())
            }
        }
    }
}
