// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.hilt.android) apply false
}

// Install git pre-commit hook automatically on build
tasks.register("installGitHook", Copy::class) {
    from("${rootProject.rootDir}/scripts/pre-commit")
    into("${rootProject.rootDir}/.git/hooks")
    filePermissions {
        user {
            read = true
            write = true
            execute = true
        }
        group {
            read = true
            execute = true
        }
        other {
            read = true
            execute = true
        }
    }
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitHook")
}

// Install git pre-push hook automatically on build (Task 10, Plan 1).
// Mismo mecanismo que `installGitHook` (pre-commit) arriba: fuente versionada
// en `scripts/`, copiada a `.git/hooks/` (no versionado) con permisos de
// ejecución.
tasks.register("installGitPushHook", Copy::class) {
    from("${rootProject.rootDir}/scripts/pre-push")
    into("${rootProject.rootDir}/.git/hooks")
    filePermissions {
        user {
            read = true
            write = true
            execute = true
        }
        group {
            read = true
            execute = true
        }
        other {
            read = true
            execute = true
        }
    }
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitPushHook")
}

// Task 10 (Plan 1): gate agregado que corre pre-push, sobre TODOS los
// módulos existentes a la fecha (se suman más cuando lleguen en planes
// siguientes). Un solo `./gradlew prePushCheck` cubre:
//   - ktlint de :app, :core:common, :core:testing, :build-tools:detekt-rules
//     y :build-logic (build compuesto, referenciado vía `gradle.includedBuild`
//     porque sus tareas no viven en el grafo de tareas del build principal).
//   - unit tests de cada módulo con tests.
//   - detekt, ruleset COMPLETO vía `msp.detekt` (Plan 2, detekt-strict):
//     `:core:common`, `:core:database`, `:core:testing`,
//     `:build-tools:detekt-rules` — todo módulo nuevo que aplique el
//     convention plugin. `:app` (legacy) NO corre detekt, ni acá ni en
//     ningún otro lado — sigue solo con ktlint.
//   - `:core:common:koverVerify` — cobertura ~90% acotada al domain de
//     `:core:common` (resolución del orquestador, Task 4/10: NO hay gate de
//     cobertura repo-wide sobre `:app`).
//   - `:app:assembleDevlocalDebug` — build real de la variante de gate.
// Deliberadamente NO incluye tareas `connected*` (device/emulador): el e2e
// instrumentado es de Plan 2/5, no de este gate local.
// NOTA: `:core:database:testDebugUnitTest`/`ktlintCheck` NO se agregan acá —
// es deuda explícita del cierre de Plan 2 (ver
// `docs/superpowers/plans/2026-08-07-plan2-database.md`, sección "Acciones"),
// tarea separada de este dispatch de detekt-strict.
tasks.register("prePushCheck") {
    group = "verification"
    description = "Gate agregado pre-push: ktlint + tests + detekt + kover + build, todos los módulos."

    dependsOn(
        gradle.includedBuild("build-logic").task(":ktlintCheck"),
        ":app:ktlintCheck",
        ":core:common:ktlintCheck",
        ":core:testing:ktlintCheck",
        ":build-tools:detekt-rules:ktlintCheck",
        ":app:testDevlocalDebugUnitTest",
        ":core:common:testDebugUnitTest",
        ":core:testing:testDebugUnitTest",
        ":build-tools:detekt-rules:test",
        ":core:common:koverVerify",
        ":core:common:detekt",
        ":core:database:detekt",
        ":core:testing:detekt",
        ":build-tools:detekt-rules:detekt",
        ":app:assembleDevlocalDebug",
    )
}
