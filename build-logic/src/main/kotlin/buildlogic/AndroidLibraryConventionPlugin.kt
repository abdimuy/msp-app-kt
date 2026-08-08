package buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

private const val MSP_COMPILE_SDK = 35
private const val MSP_MIN_SDK = 24

/**
 * `msp.android.library` — base para todo módulo Android de la migración
 * multi-módulo. Aplica AGP library + Kotlin Android, fija compileSdk/minSdk,
 * Java 11 (compileOptions + jvmTarget) con core library desugaring, y
 * habilita buildConfig (varios módulos necesitarán exponer flags/URLs).
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")

            extensions.configure<LibraryExtension> {
                compileSdk = MSP_COMPILE_SDK

                defaultConfig {
                    minSdk = MSP_MIN_SDK
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                    isCoreLibraryDesugaringEnabled = true
                }

                buildFeatures {
                    buildConfig = true
                }
            }

            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }

            dependencies {
                add("coreLibraryDesugaring", libs.findLibrary("desugar-jdk-libs").get())
            }
        }
    }
}
