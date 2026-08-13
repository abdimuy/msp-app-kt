package buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
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

            // Los unit tests de estos módulos corren sobre Robolectric y los de
            // UI usan `createComposeRule`, que necesita
            // `androidx.compose.ui:ui-test-manifest` para resolver
            // `androidx.activity.ComponentActivity`. Ese artefacto entra solo por
            // `debugImplementation` (ver AndroidComposeConventionPlugin) porque
            // declara una Activity que no debe viajar en producción. En un variant
            // release el manifiesto no existe y los tests truenan con "Unable to
            // resolve activity" (robolectric/robolectric#4736).
            //
            // `src/test` es un source set único, así que la tarea release no
            // aporta cobertura extra — solo ruido. El gate es `testDebugUnitTest`.
            extensions.configure<LibraryAndroidComponentsExtension> {
                beforeVariants { variant ->
                    variant.enableUnitTest = variant.buildType != "release"
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
