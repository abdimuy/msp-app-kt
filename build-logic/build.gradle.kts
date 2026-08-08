import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "com.example.msp_app.buildlogic"

// Los convention plugins corren dentro del proceso de Gradle (JBR del host),
// pero el bytecode que generan debe seguir el mismo Java 11 que el resto del
// proyecto (ver app/build.gradle.kts) para evitar un jvm-target mismatch.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // AGP, Kotlin, Compose compiler y KSP ya están resueltos a nivel raíz
    // (root build.gradle.kts los declara `apply false`), así que compileOnly
    // basta: los convention plugins solo necesitan sus clases para configurar,
    // no para republicarlas en el classpath de quien los consuma.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)

    // Hilt y Kover NO están declarados en ningún otro build.gradle.kts todavía;
    // build-logic es la única fuente de sus clases de plugin, por lo que deben
    // viajar como `implementation` para llegar al classpath de quien aplique
    // `msp.hilt` / `msp.kover`.
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.kover.gradle.plugin)

    // detekt-gradle-plugin NO está declarado en ningún otro build.gradle.kts
    // todavía; build-logic es la única fuente de sus clases (DetektExtension),
    // por lo que viaja como `implementation` para llegar al classpath de
    // quien aplique `msp.detekt`.
    implementation(libs.detekt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "msp.android.library"
            implementationClass = "buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "msp.android.compose"
            implementationClass = "buildlogic.AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "msp.hilt"
            implementationClass = "buildlogic.HiltConventionPlugin"
        }
        register("test") {
            id = "msp.test"
            implementationClass = "buildlogic.TestConventionPlugin"
        }
        register("kover") {
            id = "msp.kover"
            implementationClass = "buildlogic.KoverConventionPlugin"
        }
        register("detekt") {
            id = "msp.detekt"
            implementationClass = "buildlogic.DetektConventionPlugin"
        }
    }
}
