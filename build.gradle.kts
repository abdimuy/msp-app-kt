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