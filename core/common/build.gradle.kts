plugins {
    id("msp.android.library")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.common"
}

dependencies {
    // SyncAllPendingWorkUseCase (dominio de pendingwork) usa coroutineScope /
    // async / awaitAll / withTimeoutOrNull. Solo `-core` (JVM puro): el
    // dominio no depende de Android, así que NO se usa `kotlinx-coroutines-android`.
    implementation(libs.kotlinx.coroutines.core)

    // El use case de pendingwork (SyncAllPendingWorkUseCase) usa runTest +
    // MainDispatcherRule en sus tests; :core:testing expone esas deps de test
    // (coroutines-test, turbine, junit) vía `api`.
    testImplementation(project(":core:testing"))
}
