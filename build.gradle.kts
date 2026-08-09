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
// fechas/AppTime migration plan (see `task-13-report.md` for the audit
// trail). A NEW hit in any of these files is still invisible to this guard
// (file-level, not line-level) — a known, accepted trade-off of the grep
// fallback versus a full detekt baseline. Each entry below is labeled
// HONESTLY as either (a) a genuinely benign non-date use (i18n/number
// formatting that only happens to match one of the forbidden substrings) or
// (b) REAL unmigrated business-date logic deferred as legacy debt for a
// future cleanup plan — fix round 1/5 review caught an earlier version of
// this list mislabeling some of (b) as if it were (a); do not repeat that.
// The three highest-priority (b) files — actively-edited money ViewModels —
// are NOT here; see `legacyDateApiContentAllowlist` below instead, which
// allowlists only their known offending line, not the whole file.
val legacyDateApiAllowlist = setOf(
    // (a) BENIGN — Instant.now() x4 for LAST_SYNCED_AT sync watermarks. The
    // true wall-clock instant IS the correct semantic for a watermark (sync
    // bookkeeping, not a business/calendar date) — not the same class of bug
    // as the others below. Still bypasses AppClock (untestable with
    // FakeClock), so still worth migrating eventually, just not a date-logic
    // correctness bug like the rest of this list.
    "app/src/main/java/com/example/msp_app/core/sync/cobranza/CobranzaSyncManager.kt",
    // (b) REAL DEBT — ZoneId.systemDefault() converts a legacy Microsip
    // venta timestamp to a calendar date anchored to the DEVICE zone, not
    // the business zone: the same device-zone bug class this plan fixed
    // elsewhere (day-boundary shifts near midnight on a misconfigured or
    // roaming phone). Out of this plan's enumerated call-site list.
    "app/src/main/java/com/example/msp_app/data/api/services/cobranza/VentaDto.kt",
    // (b) REAL DEBT — `reportDate = LocalDate.now()` for the daily report:
    // same device-zone bug class as the `ReportFormatters.todayForReport`
    // fix (Task 5), just not applied here. `dailyReport` was kept
    // intentionally intact for a future Plan 5 per DISPATCH-CONVENTIONS.
    "app/src/main/java/com/example/msp_app/features/dailyReport/domain/usecases/GenerateDailyReportUseCase.kt",
    // (a) BENIGN — Locale.getDefault().displayLanguage reads the DEVICE'S
    // LANGUAGE tag for a device-protection report field. Not a date at all;
    // the substring just happens to match one of the forbidden patterns.
    "app/src/main/java/com/example/msp_app/features/deviceProtection/DeviceProtectionManager.kt",
    // (a) BENIGN — Locale.getDefault() feeds `String.format(..., "%.2f", ...)`
    // for a NUMBER (percentage), not a date. Not a date at all.
    "app/src/main/java/com/example/msp_app/features/home/screens/Home.kt",
    // (b) REAL DEBT — DIA_TEMPORAL_COBRANZA/FECHA_ULT_PAGO are formatted for
    // display via `ZonedDateTime...withZoneSameInstant(ZoneId.systemDefault())`
    // (device zone, not business zone). The Locale.getDefault() hit here is
    // NOT a benign i18n/number case like Home.kt above — it is the locale
    // argument of that SAME device-zone date DateTimeFormatter.
    "app/src/main/java/com/example/msp_app/features/sales/components/primarysaleitem/PrimarySaleItem.kt",
    // (b) REAL DEBT — same pattern as PrimarySaleItem.kt above (device-zone
    // date display via ZoneId.systemDefault() + Locale.getDefault() on the
    // same DateTimeFormatter).
    "app/src/main/java/com/example/msp_app/features/sales/components/secondarysaleitem/SecondarySaleItem.kt",
    // (b) REAL DEBT — TODAY/THIS_WEEK sale-list filters compare against the
    // DEVICE's LocalDate.now()/ZoneId.systemDefault(), not the business
    // zone: same device-zone bug class as `ReportFormatters.dateRangeFor`.
    "app/src/main/java/com/example/msp_app/features/sales/screens/UnifiedSalesScreen.kt",
    // (b) REAL DEBT — today()/thisWeek()/thisMonth() filter factories anchor
    // on the DEVICE's LocalDate.now(), not the business zone.
    "app/src/main/java/com/example/msp_app/features/transfers/domain/models/TransferFilters.kt",
    // (b) REAL DEBT — WRITE-side device-zone bug at ~169/186 (visit
    // reschedule note timestamp), documented and deliberately NOT fixed by
    // this plan (out of its enumerated scope).
    "app/src/main/java/com/example/msp_app/features/visit/components/NewVisitDialog.kt",
    // (b) REAL DEBT, lower stakes — printed visit-ticket header timestamp
    // (`LocalDateTime.now()`, device zone): display-only on a physical
    // receipt, not a persisted/money field, but still device-zone-dependent.
    "app/src/main/java/com/example/msp_app/features/visit/screens/VisitTicketScreen.kt",
)

// Content-based allowlist (NOT file-level) for the three actively-edited
// MONEY ViewModels flagged by fix round 1/5 review: file-level allowlisting
// them would hide a NEW/different forbidden call added later in the SAME
// file, and these are the files most likely to keep changing (live sale/
// payment ViewModels). Instead of allowlisting the whole file, this
// allowlists only the EXACT (comment-stripped, trimmed) text of the one
// known pre-existing violation in each — any OTHER hit in these files,
// including a second one, still fails the build. All three are REAL DEBT:
// a persisted date/timestamp field written from the DEVICE clock
// (`java.time.Instant.now()`/`LocalDate.now()`) instead of `AppClock`.
// Trade-off: if this exact line is ever reformatted (e.g. ktlint rewraps
// it) without a code change, the guard fires a false positive on an
// unrelated formatting diff. Judged acceptable: a false positive here is
// loud and immediately obvious at the point of the reformat, unlike a
// silently-widened file-level hole in a money ViewModel.
val legacyDateApiContentAllowlist = mapOf(
    // FECHA_SUBIDA persisted for a sale-edit image (device clock).
    "app/src/main/java/com/example/msp_app/features/sales/viewmodels/EditLocalSaleViewModel.kt" to setOf(
        "java.time.Instant.now().toString()"
    ),
    // `saleDate` persisted for a NEW sale (device clock) — the most
    // money-sensitive of the three.
    "app/src/main/java/com/example/msp_app/features/sales/viewmodels/NewSaleFormViewModel.kt" to setOf(
        "saleDate = java.time.Instant.now().toString(),"
    ),
    // `reportDate` written into a Firebase debug/report snapshot log
    // (device clock) — not a persisted money field, but still a real
    // business-date value, unmigrated.
    "app/src/main/java/com/example/msp_app/features/payments/viewmodels/PaymentsViewModel.kt" to setOf(
        "reportDate = java.time.LocalDate.now().toString(),"
    ),
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
            val contentAllowlistForFile = legacyDateApiContentAllowlist[relativePath]
            val stripped = stripKotlinCommentsAndStrings(file.readText())
            stripped.lineSequence().forEachIndexed { index, line ->
                val hit = legacyDateApiPatterns.firstOrNull { line.contains(it) } ?: return@forEachIndexed
                if (contentAllowlistForFile != null && line.trim() in contentAllowlistForFile) {
                    return@forEachIndexed
                }
                violations += "$relativePath:${index + 1}: uso directo de `$hit`"
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
