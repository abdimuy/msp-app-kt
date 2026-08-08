package buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `msp.test` — configuración de unit tests compartida. Requiere que el
 * proyecto ya tenga un plugin Android aplicado (`msp.android.library` u
 * otro), de donde toma `CommonExtension.testOptions`. Replica exactamente
 * el heap/metaspace que hoy vive hardcodeado en `app/build.gradle.kts`
 * (necesario para Robolectric) y agrega las deps de test compartidas.
 */
class TestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<CommonExtension<*, *, *, *, *, *>> {
                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                        all {
                            it.maxHeapSize = "2g"
                            it.jvmArgs("-XX:MaxMetaspaceSize=1g")
                        }
                    }
                }
            }

            dependencies {
                add("testImplementation", libs.findLibrary("junit").get())
                add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
                add("testImplementation", libs.findLibrary("robolectric").get())
                add("testImplementation", libs.findLibrary("turbine").get())
                add("testImplementation", libs.findLibrary("androidx-room-testing").get())
                add("testImplementation", libs.findLibrary("androidx-arch-core-testing").get())
            }
        }
    }
}
