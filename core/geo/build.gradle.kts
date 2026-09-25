plugins {
    id("msp.android.library")
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt")
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.geo"
}

// `:core:geo` es aritmética pura: ni Android, ni Room, ni mapa. Esa es
// exactamente la razón por la que existe como módulo aparte — ver el KDoc de
// `AgrupadorDeLugares`. Un radio mal aplicado manda al cobrador a una puerta
// equivocada, así que esto se prueba sin mapa, sin red y sin GL.
dependencies {
    testImplementation(project(":core:testing"))
}
