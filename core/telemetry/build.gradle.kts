import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.kotlin.dsl.configure

plugins {
    id("msp.android.compose") // aplica msp.android.library + Compose (trackClick/ScreenScope, T4)
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de la library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.telemetry"

    // MigrationTestHelper (SchemaIntegrityTest, Task 3) lee el JSON exportado
    // desde los assets del propio test — sin este wiring solo lo verían
    // tests instrumentados (androidTest), no los unit tests Robolectric de
    // este módulo. Mismo wiring que `:core:database`.
    sourceSets {
        getByName("test") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// `msp.kover` deja un piso placeholder de 0% (ver KoverConventionPlugin) hasta
// que cada módulo tenga su propia línea base. Task 2 activó el piso real
// sobre el dominio puro (puerto `Telemetry` + VO `TelemetryEvent`/`TelemetryEventType`);
// Task 3 suma la cola durable (`queue/`, incl. `DurableTelemetryQueue`, la
// política) bajo el MISMO `packages("com.example.msp_app.core.telemetry")`
// — el filtro ya cubre el módulo completo (paquete raíz + subpaquetes, igual
// que `:core:common`), así que no hace falta tocarlo para que el 90% alcance
// también a `queue/`.
extensions.configure<KoverProjectExtension> {
    reports {
        filters {
            includes {
                packages("com.example.msp_app.core.telemetry")
            }
            excludes {
                classes("com.example.msp_app.core.telemetry.BuildConfig")
                // `Telemetry$DefaultImpls`: sintético que Kotlin genera para los
                // parámetros con default de una interfaz (`props = emptyMap()`
                // en `event`/`error`). Llamadas Kotlin→Kotlin (todas las de este
                // módulo) resuelven el default por inlining en el call site, así
                // que este método nunca se invoca en runtime — no es lógica
                // nuestra sin testear, es un artefacto del compilador (mismo
                // trato que `BuildConfig`).
                classes("com.example.msp_app.core.telemetry.Telemetry\$DefaultImpls")
                // Artefactos generados por KSP (Room) y KAPT-equivalente (Hilt/Dagger)
                // para el store propio `telemetry_db` (Task 3) — mismo trato que
                // `BuildConfig`/`Telemetry$DefaultImpls` arriba: no es código que
                // nosotros escribimos ni podemos ejercitar rama por rama (Room genera
                // SQL binding + validación de esquema/migraciones; Dagger genera
                // fábricas `Provider`), así que gatearlo detrás del piso de dominio
                // solo penalizaría la métrica sin señalar lógica nuestra sin probar.
                // Se excluyen por nombre exacto (no wildcard) para que un futuro DAO/
                // entidad/módulo con OTRO nombre generado siga cayendo bajo el gate
                // por defecto, en vez de quedar exento en silencio.
                classes(
                    "com.example.msp_app.core.telemetry.queue.TelemetryDatabase_Impl*",
                    "com.example.msp_app.core.telemetry.queue.TelemetryEventDao_Impl*",
                    "com.example.msp_app.core.telemetry.di." +
                        "TelemetryDatabaseModule_ProvideTelemetryDatabaseFactory",
                    "com.example.msp_app.core.telemetry.di." +
                        "TelemetryDatabaseModule_ProvideTelemetryEventDaoFactory"
                )
            }
        }
        verify {
            rule("core-telemetry domain coverage (Task 2-3)") {
                minBound(90)
            }
        }
    }
}

// La cola durable de telemetría es un store Room PROPIO (`telemetry_db`),
// independiente de `msp_db` (v27, :core:database) — NUNCA lo referencia.
// Exporta su schema a un dir versionado (contrato de su DB), como
// :core:database. Task 3 agrega el primer `@Database` (`TelemetryDatabase`,
// version=1) — a partir de acá KSP sí genera `schemas/.../1.json`, commiteado.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.bundles.room) // room-runtime + room-ktx
    ksp(libs.androidx.room.compiler)

    // AppClock/AppTime (java.time) para timestamps de eventos de telemetría.
    implementation(project(":core:common"))

    // room-testing (MigrationTestHelper) para los tests del propio módulo,
    // igual que :core:database — `msp.test` ya la agrega, pero se declara
    // explícita porque es un requisito directo de este módulo.
    testImplementation(libs.androidx.room.testing)
    testImplementation(project(":core:testing"))

    // Hilt-en-JVM para el graph test de TelemetryDatabaseModule (Task 3):
    // mismo par de deps que :core:database usa para su propio HiltAndroidTest
    // sobre Robolectric (DatabaseModuleHiltGraphTest).
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
