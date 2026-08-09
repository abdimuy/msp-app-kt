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

// ─────────────────────────────────────────────────────────────────────────
// Task 13 (fechas/AppTime migration, closing task): guard against NEW direct
// `java.time`/legacy date-API usage in `:app`. `:app` deliberately does NOT
// apply the `msp.detekt` convention plugin (see `DetektConventionPlugin.kt` —
// it is scoped to `:core:*`/`:feature:*` modules only, by design of the
// multi-module strangler-fig; applying detekt's `ForbiddenImport` to all of
// legacy `:app` would flag a large volume of pre-existing, out-of-scope
// findings unrelated to dates). This grep-equivalent task is the documented
// fallback from the Task 13 brief: cheap, `:app`-scoped, and file-level
// allowlisted so it fails ONLY on genuinely new violations, not on the
// pre-existing residual this plan intentionally left untouched.
//
// Matches against real CODE only: block/line comments and string literals
// are stripped before pattern matching, because this codebase documents the
// removed `DateUtils` API and the bugs it had by literally naming the old
// APIs (`LocalDate.now()`, `ZoneId.systemDefault()`, etc.) in KDoc — a naive
// whole-file grep would false-positive on that valuable history.
val legacyDateApiPatterns = listOf(
    "LocalDate.now(",
    "LocalDateTime.now(",
    "Instant.now(",
    "Calendar.getInstance(",
    "SimpleDateFormat",
    "java.util.Date",
    "ZoneId.systemDefault(",
    "Locale.getDefault(",
)

// File-level allowlist (lines shift too easily for a line-level list): the
// closed set of pre-existing call sites left OUT of scope by the
// fechas/AppTime migration plan, each documented as legacy debt for a future
// cleanup plan (see `task-13-report.md` for the audit trail). A NEW hit in
// any of these files is still invisible to this guard (file-level, not
// line-level) — that is a known, accepted trade-off of the grep fallback
// versus a full detekt baseline; each file below already carries a `now()`/
// `systemDefault()`/`getDefault()` call so the marginal risk of missing a
// second one in the SAME file is low relative to the noise a stricter
// per-line diff-based check would add here.
val legacyDateApiAllowlist = setOf(
    // Watermarks intentionally use the true wall-clock instant, not AppClock
    // (sync bookkeeping, not business/money dates) — Instant.now() x4.
    "app/src/main/java/com/example/msp_app/core/sync/cobranza/CobranzaSyncManager.kt",
    // ZoneId.systemDefault() to bucket a legacy Microsip DTO field by device
    // calendar day — pre-existing, not touched by this plan's enumerated scope.
    "app/src/main/java/com/example/msp_app/data/api/services/cobranza/VentaDto.kt",
    "app/src/main/java/com/example/msp_app/features/dailyReport/domain/usecases/GenerateDailyReportUseCase.kt",
    // Locale.getDefault() for the DEVICE'S LANGUAGE tag (i18n), not a date —
    // out of scope by definition, but the substring still matches the guard.
    "app/src/main/java/com/example/msp_app/features/deviceProtection/DeviceProtectionManager.kt",
    // Locale.getDefault() for NUMBER formatting (percentages), not a date.
    "app/src/main/java/com/example/msp_app/features/home/screens/Home.kt",
    "app/src/main/java/com/example/msp_app/features/payments/viewmodels/PaymentsViewModel.kt",
    "app/src/main/java/com/example/msp_app/features/sales/components/primarysaleitem/PrimarySaleItem.kt",
    "app/src/main/java/com/example/msp_app/features/sales/components/secondarysaleitem/SecondarySaleItem.kt",
    "app/src/main/java/com/example/msp_app/features/sales/screens/UnifiedSalesScreen.kt",
    "app/src/main/java/com/example/msp_app/features/sales/viewmodels/EditLocalSaleViewModel.kt",
    "app/src/main/java/com/example/msp_app/features/sales/viewmodels/NewSaleFormViewModel.kt",
    "app/src/main/java/com/example/msp_app/features/transfers/domain/models/TransferFilters.kt",
    // NewVisitDialog.kt ~169/186 — WRITE-side device-zone bug, documented and
    // deliberately NOT fixed by this plan (out of its enumerated scope).
    "app/src/main/java/com/example/msp_app/features/visit/components/NewVisitDialog.kt",
    "app/src/main/java/com/example/msp_app/features/visit/screens/VisitTicketScreen.kt",
)

/**
 * Strips `//` line comments, `/* ... */` block comments, and the contents of
 * string/char literals from Kotlin source, replacing stripped characters
 * with spaces so line numbers are preserved. Not a real parser — a Kotlin
 * source string can't contain an unescaped `"` or `/` sequence that fools
 * this into misreading the file, which is true for every file in this repo
 * today; good enough for a lint-style heuristic gate, not a compiler.
 */
fun stripKotlinCommentsAndStrings(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    val n = text.length
    var inBlockComment = false
    var inString = false
    var inChar = false
    while (i < n) {
        val c = text[i]
        when {
            inBlockComment -> {
                if (c == '*' && i + 1 < n && text[i + 1] == '/') {
                    out.append("  ")
                    i += 2
                    inBlockComment = false
                } else {
                    out.append(if (c == '\n') '\n' else ' ')
                    i++
                }
            }
            inString -> {
                out.append(' ')
                if (c == '\\' && i + 1 < n) {
                    out.append(' ')
                    i += 2
                } else {
                    if (c == '"') inString = false
                    i++
                }
            }
            inChar -> {
                out.append(' ')
                if (c == '\'') inChar = false
                i++
            }
            c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                inBlockComment = true
                out.append("  ")
                i += 2
            }
            c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                val nl = text.indexOf('\n', i)
                i = if (nl == -1) n else nl
            }
            c == '"' -> {
                inString = true
                out.append(' ')
                i++
            }
            c == '\'' -> {
                inChar = true
                out.append(' ')
                i++
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

tasks.register("checkNoLegacyDateApi") {
    group = "verification"
    description = "Task 13 (fechas/AppTime): falla si :app introduce un NUEVO uso directo de " +
        "LocalDate/LocalDateTime/Instant.now(), Calendar.getInstance(), SimpleDateFormat, " +
        "java.util.Date, ZoneId.systemDefault() o Locale.getDefault() fuera del allowlist."

    val appMainDir = layout.projectDirectory.dir("app/src/main")
    val repoRoot = layout.projectDirectory.asFile

    inputs.files(fileTree(appMainDir) { include("**/*.kt") })

    doLast {
        val violations = mutableListOf<String>()
        fileTree(appMainDir) { include("**/*.kt") }.forEach { file ->
            val relativePath = file.relativeTo(repoRoot).invariantSeparatorsPath
            if (relativePath in legacyDateApiAllowlist) return@forEach
            val stripped = stripKotlinCommentsAndStrings(file.readText())
            stripped.lineSequence().forEachIndexed { index, line ->
                val hit = legacyDateApiPatterns.firstOrNull { line.contains(it) }
                if (hit != null) {
                    violations += "$relativePath:${index + 1}: uso directo de `$hit`"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkNoLegacyDateApi: uso directo de API de fecha/hora legado fuera del " +
                    "allowlist — usar AppTime/AppClock de :core:common en su lugar:\n" +
                    violations.joinToString("\n") { "  - $it" }
            )
        }
    }
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
//   - ktlint de :app, :core:common, :core:database, :core:testing,
//     :build-tools:detekt-rules y :build-logic (build compuesto, referenciado
//     vía `gradle.includedBuild` porque sus tareas no viven en el grafo de
//     tareas del build principal).
//   - unit tests de cada módulo con tests.
//   - detekt, ruleset COMPLETO vía `msp.detekt` (Plan 2, detekt-strict):
//     `:core:common`, `:core:database`, `:core:testing`,
//     `:build-tools:detekt-rules` — todo módulo nuevo que aplique el
//     convention plugin. `:app` (legacy) NO corre detekt, ni acá ni en
//     ningún otro lado — sigue solo con ktlint + `checkNoLegacyDateApi`
//     (Task 13, fechas/AppTime migration: guard `:app`-scoped contra usos
//     nuevos de API de fecha legado, ver comentario junto a esa tarea).
//   - `:core:common:koverVerify` — cobertura ~90% acotada al domain de
//     `:core:common` (resolución del orquestador, Task 4/10: NO hay gate de
//     cobertura repo-wide sobre `:app`).
//   - `:app:assembleDevlocalDebug` — build real de la variante de gate.
// Deliberadamente NO incluye tareas `connected*` (device/emulador): el e2e
// instrumentado es de Plan 2/5, no de este gate local.
// `:core:database:testDebugUnitTest`/`ktlintCheck` se suman aquí (cierre de
// Plan 2, ver `docs/superpowers/plans/2026-08-07-plan2-database.md` sección
// "Acciones") aprovechando este cierre de Task 13 para saldar esa deuda
// explícita — el módulo ya aplica ktlint/msp.detekt y tenía tests corriendo
// solo manualmente hasta ahora.
tasks.register("prePushCheck") {
    group = "verification"
    description = "Gate agregado pre-push: ktlint + tests + detekt + kover + build, todos los módulos."

    dependsOn(
        gradle.includedBuild("build-logic").task(":ktlintCheck"),
        ":app:ktlintCheck",
        "checkNoLegacyDateApi",
        ":core:common:ktlintCheck",
        ":core:database:ktlintCheck",
        ":core:testing:ktlintCheck",
        ":build-tools:detekt-rules:ktlintCheck",
        ":app:testDevlocalDebugUnitTest",
        ":core:common:testDebugUnitTest",
        ":core:database:testDebugUnitTest",
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
