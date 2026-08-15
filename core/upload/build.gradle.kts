plugins {
    id("msp.android.library")
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.upload"
}

// Este módulo es la política de entrega garantizada y NADA más: una función
// pura y un puerto. Sin Android, sin red, sin Room. Por eso el piso de
// cobertura sí es real desde el primer día — no hay nada aquí que no se pueda
// probar en JVM.
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}

dependencies {
    testImplementation(project(":core:testing"))
    testImplementation(libs.bundles.unit.test)
}
