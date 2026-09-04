plugins {
    id("msp.android.library")
    id("msp.hilt")
    id("msp.test")
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.example.msp_app.core.printing"
}

// `msp.kover` deja el piso placeholder (0%, ver `KoverConventionPlugin`): este
// módulo (P1, port del `core:printing` de kollect-app) SÍ trae dominio propio
// y ~10 archivos de test, pero fijar un umbral real de cobertura es harina de
// una tarea de cierre posterior — mismo criterio de nacimiento que el resto de
// los `:core:*` de la migración. `koverVerifyDebug` igual entra a `prePushCheck`
// contra el piso placeholder para que el gate lo ejercite desde el día uno.

dependencies {
    // Adapter DantSu: withTimeout/runInterruptible (anti-freeze de 8s) +
    // Dispatchers.IO viven en `DantSuPrinterGateway`.
    implementation(libs.kotlinx.coroutines.core)

    // `AppClock`/`AppTime` (Task 20): la regla "el ticket solo se imprime el dia
    // del cobro" compara DIAS DE NEGOCIO (`America/Mexico_City`), y esa zona
    // tiene una sola fuente en el repo. Sin esto la regla tendria que recibir
    // fechas ya calculadas y cada llamador podria calcularlas distinto.
    implementation(project(":core:common"))

    // Telemetria (NORMA DE ERRORES, vinculante en `:core:*`): `SharedPrefsPrintLog`
    // reporta un `commit` fallido y una fecha ilegible con su codigo propio en
    // vez de tragarselos.
    implementation(project(":core:telemetry"))

    // `androidx.core.content.edit` (PreferredPrinterRepository) y
    // `ContextCompat.checkSelfPermission` (BluetoothPrinterDiscovery).
    implementation(libs.androidx.core.ktx)

    // ESC/POS Bluetooth (DantSu, JitPack) — consumido solo por el adapter T2.
    // El repo JitPack ya está declarado en `settings.gradle.kts`.
    implementation(libs.escpos.thermalprinter.android)

    // hilt-android + ksp(hilt-compiler) los aporta `msp.hilt`.
    // junit + coroutines-test + robolectric + turbine los aporta `msp.test`;
    // acá solo falta `ApplicationProvider` para los tests Robolectric
    // (BluetoothPrinterDiscoveryTest / PreferredPrinterRepositoryTest).
    testImplementation(libs.androidx.test.core)

    // `FakeClock` + `RecordingTelemetry` (fakes-only, sin MockK) para la regla
    // del dia del cobro y el registro de impresiones (Task 20). Aciclico:
    // `:core:testing` no depende de `:core:printing`.
    testImplementation(project(":core:testing"))
}
