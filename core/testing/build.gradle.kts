plugins {
    id("msp.android.library")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.testing"
}

dependencies {
    api(project(":core:common"))

    // RoomTestBase (Task 5, post-hoist de AppDatabase) construye la DB
    // in-memory contra el tipo real. Acíclico: `:core:database` main NO
    // depende de `:core:testing` (solo su source set `test`, que Gradle
    // trata por separado — no cierra ciclo con este `api` de main).
    api(project(":core:database"))
    api(libs.bundles.room) // room-runtime + room-ktx, para Room.inMemoryDatabaseBuilder
    api(libs.androidx.test.core) // ApplicationProvider, usado por RoomTestBase

    // RecordingSyncHealthSource (fake de core.common.sync.health.SyncHealthSource)
    // usa `Flow`/`flow {}` directamente, no solo a través de coroutines-test.
    api(libs.kotlinx.coroutines.core)

    // Las libs de test se exponen como `api` porque este módulo ES la infra
    // de test: cualquier módulo que dependa de `:core:testing` en su propio
    // `testImplementation` necesita ver JUnit/coroutines-test/Robolectric/etc.
    // transitivamente, sin repetir cada coordenada en su build.gradle.kts.
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.robolectric)
    api(libs.androidx.arch.core.testing)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
    api(libs.roborazzi.junit.rule)
}
