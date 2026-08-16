plugins {
    id("msp.android.library")
    id("msp.hilt") // KSP + hilt-android + ksp(hilt-compiler)
    id("msp.test") // DESPUÉS de msp.android.library
    id("msp.kover")
    id("msp.detekt") // ruleset completo (Plan 2: detekt-strict)
    alias(libs.plugins.ktlint) // para que el ktlintCheck raíz cubra el módulo
}

android {
    namespace = "com.example.msp_app.core.database"

    // MigrationTestHelper (SchemaIntegrityTest) lee el JSON exportado desde los
    // assets del propio test — sin este wiring solo lo verían tests
    // instrumentados (androidTest), no los unit tests Robolectric de este módulo.
    sourceSets {
        getByName("test") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

// Room exporta el esquema a un dir versionado (contrato de la DB). KSP recibe
// el room.schemaLocation; el `schemas/` se commitea (Task 2 genera el 27.json).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// ─────────────────────────────────────────────────────────────────────────
// Piso de cobertura REAL (reemplaza el placeholder 0% de `msp.kover`).
//
// MEDIDO 2026-08-16 con `:core:database:koverXmlReportDebug`:
//
//   MÓDULO COMPLETO ....................  LINE   705/4679 = 15.07%
//   `...core.database.migrations` .......  LINE    53/53   = 100.00%
//   `...core.database.dao.payment` ......  LINE   296/733  =  40.38%
//
// El 15.07% del módulo completo es vergonzoso y se deja escrito con su
// número en vez de maquillarse: 4,315 de las 4,679 líneas del módulo son
// código GENERADO por Room (`AppDatabase_Impl`, `*Dao_Impl` de ~20 DAOs) y
// está al 12.8%; el código escrito a mano son 364 líneas al 42.3%. La
// tentación obvia era excluir lo generado para publicar "42%" — no se hace,
// porque `*Dao_Impl` ES el SQL que corre en producción (el `PaymentDao` que
// el brief nombra no tiene cuerpo propio: su comportamiento vive en
// `PaymentDao_Impl`), así que excluirlo sería esconder justo lo que importa.
//
// Por eso hay TRES reglas en vez de una: la de módulo es el trinquete
// grueso, y las dos acotadas por paquete son las que de verdad muerden donde
// un error cuesta dinero.
//
// GOTCHA de Kover 0.8: `verify { rule { filters { ... } } }` es un ERROR de
// compilación desde 0.8.0 ("It is forbidden to override filters for a specific
// report, use custom report variants"). Por eso las dos reglas acotadas viven
// en variantes de reporte propias (`migrations` / `pagosDao`), cada una con su
// filtro y su tarea `koverVerify<Variante>` — las tres están cableadas en
// `prePushCheck`.
kover {
    currentProject {
        // Ambas variantes se alimentan de la MISMA ejecución de pruebas que
        // `koverVerifyDebug` (`add("debug")`), así que no cuestan una corrida
        // extra de tests: sólo re-filtran el mismo artefacto de cobertura.
        createVariant("migrations") { add("debug") }
        createVariant("pagosDao") { add("debug") }
    }
    reports {
        // Acotado a la variante `debug`, NO al bloque `verify` COMÚN: una regla
        // común se evalúa también dentro de `migrations`/`pagosDao`, donde mide
        // el paquete filtrado y no el módulo — el mensaje de error saldría con
        // el nombre "trinquete de modulo" sobre un número que no es el del
        // módulo. Verificado corriendo la compuerta con los pisos subidos a
        // propósito: la regla común aparecía con 40.38% dentro de `pagosDao`.
        variant("debug") {
            // Trinquete de módulo. 13 es un piso ridículo y se declara como
            // tal; su único trabajo es que nadie borre la mitad de las pruebas
            // de DB sin que el build se entere.
            verifyAppend {
                rule("core-database: trinquete de modulo (medido 15.07% LINE, 2026-08-16)") {
                    minBound(13)
                }
            }
        }
        variant("migrations") {
            filters {
                includes { packages("com.example.msp_app.core.database.migrations") }
            }
            // Las migraciones están al 100.00% HOY (53/53 líneas: las 9
            // `MIGRATION_x_y` tienen prueba). Piso sin holgura y a propósito:
            // una migración nueva sin prueba baja este número y DEBE romper el
            // build — una migración mal escrita corrompe la base del cobrador
            // en campo y desde un teléfono no hay rollback posible.
            verifyAppend {
                rule("core-database: migraciones al 100% (medido 100.00% LINE, 2026-08-16)") {
                    minBound(100)
                }
            }
        }
        variant("pagosDao") {
            filters {
                includes { packages("com.example.msp_app.core.database.dao.payment") }
            }
            // El DAO de pagos (interfaz + `PaymentDao_Impl` generado + los
            // `DefaultImpls` con lógica propia): 40.38% medido. Piso 38, dos
            // puntos de holgura. Bajo en absoluto, pero es un número REAL, y es
            // el único que se mueve cuando alguien agrega una consulta de pagos
            // sin probarla.
            verifyAppend {
                rule("core-database: DAO de pagos (medido 40.38% LINE, 2026-08-16)") {
                    minBound(38)
                }
            }
        }
    }
}

dependencies {
    // AppTime/AppClock (zona de negocio, wire format) — acíclico: :core:common
    // no depende de :core:database (ver ledger Task 3, Ambiguity 1). Reemplaza
    // la copia interna `PaymentDateGrouping.kt` que este módulo tenía porque
    // esa dependencia se creía (incorrectamente) cíclica.
    implementation(project(":core:common"))

    implementation(libs.bundles.room) // room-runtime + room-ktx
    ksp(libs.androidx.room.compiler)

    // room-testing (MigrationTestHelper) para los tests del propio módulo.
    // `msp.test` ya la agrega, pero se declara explícita aquí porque es un
    // requisito directo de este módulo (Task 4), no solo heredado del piso
    // común de testing.
    testImplementation(libs.androidx.room.testing)
    // ApplicationProvider (Robolectric in-memory DB smoke test, ver AppDatabaseTest).
    testImplementation(libs.bundles.android.test.support)
    testImplementation(project(":core:testing"))

    // Hilt-en-JVM para el graph test de DatabaseModule (Task 3): mismo par de
    // deps que :app usa para sus propios HiltAndroidTest sobre Robolectric.
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
}
