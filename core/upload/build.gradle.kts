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
        filters {
            excludes {
                // `BuildConfig` es generado por AGP: 2 de sus lineas nunca las
                // toca una prueba y arrastran el porcentaje a 83%, escondiendo
                // que el codigo propio de este modulo si esta al 100%.
                classes("*.BuildConfig")
            }
        }
        verify {
            // MEDIDO 2026-08-16 (`:core:upload:koverXmlReport`): LINE 10/10 =
            // 100.00% (INSTRUCTION 92/92, BRANCH 16/16), ya con `*.BuildConfig`
            // fuera. El piso estaba en 95 — por debajo de lo real, así que
            // aceptaba en silencio que se perdiera una línea. Se sube al valor
            // medido: trinquete exacto, no meta aspiracional. Sin holgura a
            // propósito — son 10 líneas de función pura sobre la política de
            // entrega garantizada (decide si se suelta una captura); cualquier
            // línea nueva sin prueba debe romper el build, que es justamente lo
            // que un piso de 95 sobre 10 líneas NO hacía (10/10 y 9/10 dan
            // 100% y 90%: el 95 solo se activaba al perder una línea entera,
            // pero el mensaje de error mentía sobre cuánto margen había).
            rule("core-upload: politica de entrega garantizada, cobertura total") {
                minBound(100)
            }
        }
    }
}

dependencies {
    testImplementation(project(":core:testing"))
    testImplementation(libs.bundles.unit.test)
}
