// `:build-tools:detekt-rules` — reglas detekt propias del proyecto.
//
// Vive como módulo JVM plano (no Android, no vía `build-logic`) porque un
// RuleSetProvider de detekt es una clase servidora vía
// `java.util.ServiceLoader`, consumida por otros módulos con
// `detektPlugins(project(":build-tools:detekt-rules"))`. `build-logic` es un
// included build (composite) que produce *plugins de Gradle*, no artefactos
// de classpath de análisis — para eso hace falta un proyecto normal del
// build principal, de ahí que este módulo no use `msp.*` convention plugins.
//
// Task 9 (Plan 1, spec §13 Tier A): regla `NoDoubleForMoney` — prohíbe
// `Double`/`Float` en propiedades/parámetros/funciones cuyo nombre sugiera
// dinero, dentro de los packages de dominio nuevos (ver aplicación en
// `core/common/build.gradle.kts` y `config/detekt/detekt.yml`).
plugins {
    // Sin `alias(libs.plugins.kotlin.jvm)`: pedir una versión explícita del
    // plugin Kotlin choca con la que `build-logic` (included build con
    // `kotlin-dsl`) ya deja resuelta en el classpath de plugins de este
    // build ("already on the classpath with an unknown version"). Al pedirlo
    // sin versión, Gradle reutiliza la que ya está cargada (2.0.21, la misma
    // del catálogo).
    id("org.jetbrains.kotlin.jvm")
    // `msp.detekt` SÍ se puede aplicar aquí aunque el módulo no use
    // `msp.android.library` (ver comentario de arriba): solo aplica el
    // plugin `io.gitlab.arturbosch.detekt` + configura el ruleset completo,
    // no depende de AGP ni de una versión propia de Kotlin.
    id("msp.detekt")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo, igual que el resto
}

// `jvmToolchain(11)` forzaría a Gradle a resolver un JDK 11 instalado (no hay
// uno en esta máquina, solo el JBR de Android Studio) y fallaría con
// "No locally installed toolchains match". El resto del proyecto evita el
// mismo problema fijando `jvmTarget` directo en las `compilerOptions`
// (ver `AndroidLibraryConventionPlugin`) en vez de pedir un toolchain — el
// compilador que ya corre (JBR) simplemente emite bytecode nivel 11.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // Solo se necesita para COMPILAR la regla (API de detekt: Rule, Config,
    // CodeSmell, Entity...); en tiempo de ejecución la aporta el classpath de
    // análisis del detekt Gradle plugin en el módulo consumidor.
    compileOnly(libs.detekt.api)

    // `detekt-test` trae los helpers `compileAndLint` / `lint` que corren la
    // regla sobre un fixture en memoria sin necesitar un proyecto Gradle
    // completo ni resolución de tipos (nuestra regla es puramente sintáctica).
    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
}
