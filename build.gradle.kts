import java.util.concurrent.Callable

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
    // ── Arreglo B: primera entrada que NO viene de `:app` ──────────────────
    // `AppClock.System` es LA implementación del reloj: alguien tiene que
    // llamar a `Instant.now()` una vez, y el punto entero de este guard es que
    // sea exactamente aquí y en ningún otro lado. Va por contenido y no por
    // archivo a propósito — un segundo `Instant.now()` en este mismo archivo
    // (p. ej. un helper de conveniencia que se saltara la interfaz) volvería a
    // fallar el gate, que es lo que se quiere.
    "core/common/src/main/kotlin/com/example/msp_app/core/common/time/AppClock.kt" to setOf(
        "override fun now(): Instant = Instant.now()"
    ),
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

// ─────────────────────────────────────────────────────────────────────────
// Arreglo B (2026-09-08) — el alcance del guard se DESCUBRE, no se escribe.
//
// Hasta acá esta tarea barría UN directorio literal (`app/src/main`). Los
// cinco módulos que la rama `feat/pagos-y-visitas` agregó —`:feature:pagos`,
// `:feature:visitas`, `:core:printing`, `:core:common`, …— quedaban FUERA de
// la única compuerta que impone "AppTime/AppClock es la fuente única de
// fechas", y nadie se enteraba: un módulo nuevo escapaba por defecto.
//
// Ahora el alcance sale de `subprojects` (el grafo de Gradle). Un módulo
// nuevo entra al gate el día que entra a `settings.gradle.kts`, sin tocar
// este archivo. **Salir** es lo que ahora cuesta: hay que escribirlo en
// `legacyDateApiScopeAllowlist` CON su razón.
//
// Los source sets de prueba (`test`, `androidTest`, `testFixtures`) quedan
// fuera a propósito y con razón escrita: un test construye fechas fijas a
// mano (`LocalDate.of`, `SimpleDateFormat` para armar un fixture) y eso no es
// la deuda que este guard persigue — persigue código de producción que lee el
// reloj o la zona del dispositivo. `:core:testing` SÍ está en alcance por su
// `src/main`, que es código de producción de las pruebas de otros módulos.
val legacyDateApiTestSourceSets = setOf("test", "androidTest", "testFixtures")

/**
 * Módulos exentos del barrido, cada uno con la razón por la que lo está.
 * Vacío hoy, y esa es la forma correcta: si mañana algo no puede cumplir la
 * regla, la salida es una entrada acá con su motivo, nunca ablandar el
 * patrón ni volver a un directorio literal.
 */
val legacyDateApiScopeAllowlist: Map<String, String> = emptyMap()

tasks.register("checkNoLegacyDateApi") {
    group = "verification"
    description = "Arreglo B (ex Task 13, fechas/AppTime): barre TODOS los módulos del build " +
        "(descubiertos desde Gradle) y falla si aparece un NUEVO uso directo de " +
        "LocalDate/LocalDateTime/Instant.now(), Calendar.getInstance(), SimpleDateFormat, " +
        "java.util.Date, ZoneId.systemDefault() o Locale.getDefault() fuera del allowlist."

    val repoRoot = layout.projectDirectory.asFile

    // Descubrimiento: cada subproyecto del build aporta sus source sets de
    // producción. `subprojects` se evalúa en configuración, así que un módulo
    // nuevo en `settings.gradle.kts` entra solo.
    val scannedModules: List<Pair<String, File>> = subprojects
        .filter { it.path !in legacyDateApiScopeAllowlist.keys }
        .map { it.path to File(it.projectDir, "src") }
        .filter { (_, srcDir) -> srcDir.isDirectory }
        .sortedBy { it.first }

    val scannedTrees = scannedModules.map { (_, srcDir) ->
        fileTree(srcDir) {
            include("**/*.kt")
            legacyDateApiTestSourceSets.forEach { exclude("$it/**") }
        }
    }

    val moduleCount = scannedModules.size
    val moduleNames = scannedModules.joinToString(", ") { it.first }

    inputs.files(scannedTrees)
    inputs.property("legacyDateApiPatterns", legacyDateApiPatterns)
    inputs.property("legacyDateApiScopeAllowlist", legacyDateApiScopeAllowlist)

    doLast {
        // Control positivo del propio barrido: si el descubrimiento devuelve
        // cero módulos o cero archivos, el "sin violaciones" de abajo no
        // probaría nada. Una compuerta que sale verde sin medir es el defecto
        // que esta tarea vino a cerrar; que no lo reintroduzca ella misma.
        if (moduleCount == 0) {
            throw GradleException(
                "checkNoLegacyDateApi: el descubrimiento no encontró NINGÚN módulo. " +
                    "El barrido no midió nada y su verde no vale."
            )
        }
        val scannedFiles = scannedTrees.flatMap { it.files }
        if (scannedFiles.isEmpty()) {
            throw GradleException(
                "checkNoLegacyDateApi: se descubrieron $moduleCount módulos pero CERO archivos " +
                    "`.kt`. El barrido no midió nada y su verde no vale."
            )
        }

        val violations = mutableListOf<String>()
        scannedFiles.forEach { file ->
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
        logger.lifecycle(
            "checkNoLegacyDateApi: ${scannedFiles.size} archivos en $moduleCount módulos " +
                "($moduleNames)"
        )
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

// ─────────────────────────────────────────────────────────────────────────
// `prePushCheck` — la compuerta de las compuertas, y la séptima lista.
//
// Hasta la ronda 1 del Arreglo B esto era **un `dependsOn` con sesenta rutas de
// tarea escritas a mano**. O sea: el arreglo que puso a descubrir a
// `checkNoLegacyDateApi`, al guard del kill-switch, al de `KEEP` y al de
// `SaleIdSpaces` dejaba intacta la lista de la que dependen los cuatro. Un
// módulo nuevo lo habrían visto las cuatro compuertas reescritas **y no** su
// ktlint, su detekt, su kover, su roborazzi ni sus pruebas.
//
// Y no es hipotético: el comentario que estaba acá documentaba que
// `:feature:configuracion` vivió en `settings.gradle.kts` **semanas** sin entrar
// a esta lista — ni ktlint, ni pruebas, ni detekt, ni kover. La lista ya falló
// una vez, del modo exacto que este arreglo persigue.
//
// Ahora el alcance sale de `subprojects` y las tareas de cada módulo se
// DESCUBREN: por cada familia se toma la primera tarea que ese módulo realmente
// tenga. Un módulo nuevo entra completo el día que entra al build, sin tocar
// este archivo.
//
// ## Por qué la precedencia dentro de la familia, y no todas
//
// `koverVerify` (el agregado) arrastra `testReleaseUnitTest`, y en los módulos
// con Robolectric —Compose UI, Roborazzi— la variante `release` minificada
// revienta en TODOS los tests con `RoboMonitoringInstrumentation`, sin relación
// con el código. Por eso la familia prefiere `koverVerifyDebug` cuando existe y
// cae a `koverVerify` sólo en los módulos JVM planos (`:core:common`,
// `:core:upload`), que es exactamente lo que la lista vieja hacía a mano.
// Lo mismo con las pruebas: `:app` tiene sabores, así que su tarea es
// `testDevlocalDebugUnitTest`; el resto usa `testDebugUnitTest`, y
// `:build-tools:detekt-rules` (JVM puro) `test`.
//
// Las reglas de cobertura ACOTADAS por paquete (`:core:database` tiene
// `koverVerifyMigrations` al 100% y `koverVerifyPagosDao` al 38%, porque Kover
// 0.8 prohíbe filtrar dentro de una regla) se descubren aparte: toda tarea
// `koverVerify*` con nombre propio entra sola. Antes había que acordarse de
// agregarlas.
//
// `:build-logic` NO es un subproyecto sino un build incluido, así que su ktlint
// se referencia explícito — `gradle.includedBuild(...)` es la única forma de
// alcanzarlo y no hay lista que descubrir ahí: es uno solo.

/** Familias de tareas. Dentro de cada una se toma la PRIMERA que exista. */
val prePushTaskFamilies: List<List<String>> = listOf(
    listOf("ktlintCheck"),
    listOf("testDevlocalDebugUnitTest", "testDebugUnitTest", "test"),
    listOf("detekt"),
    listOf("verifyRoborazziDebug"),
    listOf("assembleDevlocalDebug")
)

/**
 * Los marcadores de que las pruebas del módulo NO sobreviven la variante
 * `release`.
 *
 * La familia de kover no puede resolverse por orden fijo, y la ronda 2 lo
 * probó: poner `koverVerifyDebug` primero degradó `:core:common` y
 * `:core:upload`, que la lista vieja corría con el agregado `koverVerify`
 * —más estricto, cubre TODAS las variantes— porque sus pruebas son JVM plano.
 * Elegir siempre `Debug` sacó `testReleaseUnitTest` del grafo entero sin que
 * nadie lo pidiera.
 *
 * Lo que decide es un hecho del módulo, no su nombre: si sus pruebas usan
 * Robolectric (o Roborazzi, o `createComposeRule`, que lo arrastra), la
 * variante `release` minificada revienta en TODAS con
 * `RoboMonitoringInstrumentation`, y ahí el agregado no sirve. Ese hecho se
 * **descubre** leyendo `src/test`, así que un módulo que mañana adopte
 * Robolectric cambia de tarea solo, y uno que lo abandone recupera el agregado.
 */
val marcadoresDeRobolectric = listOf("Robolectric", "robolectric", "Roborazzi", "roborazzi", "createComposeRule")

/** ¿Las pruebas de [proyecto] arrastran Robolectric? */
fun usaRobolectric(proyecto: Project): Boolean {
    val pruebas = File(proyecto.projectDir, "src/test")
    if (!pruebas.isDirectory) return false
    return pruebas.walkTopDown()
        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
        .any { archivo -> marcadoresDeRobolectric.any { archivo.readText().contains(it) } }
}

/** La tarea de cobertura que le toca a [proyecto]: el agregado si puede, `Debug` si no. */
fun tareaDeCoberturaDe(proyecto: Project): String? {
    val nombres = proyecto.tasks.names
    val preferida = if (usaRobolectric(proyecto)) "koverVerifyDebug" else "koverVerify"
    return listOf(preferida, "koverVerifyDebug", "koverVerify").firstOrNull { it in nombres }
}

/** Nombres de la familia de kover: lo que NO es una regla acotada con nombre propio. */
val koverBaseTaskNames = setOf(
    "koverVerify", "koverVerifyDebug", "koverVerifyRelease",
    "koverVerifyDevlocalDebug", "koverVerifyDevserverDebug", "koverVerifyProdDebug",
    "koverVerifyDevlocalRelease", "koverVerifyDevserverRelease", "koverVerifyProdRelease"
)

/**
 * Módulos exentos del gate, con la razón por la que lo están. **Vacío**, y esa
 * es la forma correcta: la única salida es escribir el módulo acá con su motivo.
 */
val prePushScopeAllowlist: Map<String, String> = emptyMap()

/**
 * Los módulos de verdad: los subproyectos que tienen script de build propio.
 *
 * `:core`, `:feature` y `:build-tools` también son subproyectos —Gradle crea un
 * proyecto por cada segmento de `include(":core:common")`— pero son carpetas
 * contenedoras sin script, sin código y sin una sola tarea que aportar. El
 * primer intento del descubrimiento los contaba y el control positivo del gate
 * los delató de inmediato ("estos módulos no aportaron NI UNA tarea"), que es
 * exactamente para lo que ese control está.
 *
 * El filtro es por hecho verificable —¿tiene `build.gradle.kts`?— y no por
 * nombre: una carpeta contenedora nueva no hay que agregarla a ningún lado, y
 * un módulo nuevo entra por tener script, no por llamarse de cierta forma.
 */
val modulosDelBuild: List<Project>
    get() = subprojects
        .filter { File(it.projectDir, "build.gradle.kts").isFile }
        .filterNot { it.path in prePushScopeAllowlist }

/**
 * Tareas sueltas excluidas, por `<módulo>:<tarea>`, con su razón. Se exime una
 * TAREA, no un módulo entero: el resto de sus familias sigue en la compuerta.
 */
val prePushTaskAllowlist: Map<String, String> = mapOf(
    // `:core:network` es infra pragmática sobre OkHttp/Retrofit (interceptores y
    // factory) y nació con `msp.kover` aplicado pero SIN piso: el brief de su
    // Task 8 lo pidió explícitamente así. Sus pruebas reales
    // (`AppVersionInterceptorTest`, `BearerAuthInterceptorTest`,
    // `RetrofitClientFactoryTest`, `ConnectivityMonitorTest`) SÍ corren en el
    // gate por la familia de pruebas; lo que no corre es un umbral que nadie
    // fijó todavía.
    ":core:network:koverVerifyDebug" to
        "sin línea base de cobertura fijada (decisión de su Task 8); sus pruebas sí entran"
)

/**
 * Las compuertas que viven en la RAÍZ, no en un módulo.
 *
 * `subprojects` no ve al proyecto raíz, y esa omisión costó caro: la primera
 * versión del descubrimiento sacó `checkNoLegacyDateApi` del pre-push —el
 * guardarraíl estrella de este mismo arreglo— y sobrevivió solo en CI. Nadie lo
 * buscó; lo encontró la comparación contra la base congelada.
 *
 * Se descubren por **grupo**: toda tarea de la raíz en `verification`, menos la
 * propia `prePushCheck` (dependería de sí misma). Una compuerta de raíz nueva
 * entra sola con solo declarar `group = "verification"`, que es lo que ya hacen
 * todas.
 */
fun tareasDeGateDeLaRaiz(): List<String> = rootProject.tasks
    .matching { it.group == "verification" && it.name != "prePushCheck" }
    .map { it.name }
    .sorted()

/** Lo que cada módulo aportó, para el control positivo del propio descubrimiento. */
val prePushAportes = linkedMapOf<String, List<String>>()

/**
 * Las tareas de gate que [proyecto] tiene HOY.
 *
 * Se llama tarde a propósito (ver el `Callable` de abajo): AGP, Kover y
 * Roborazzi registran sus tareas por variante dentro de sus propios
 * `afterEvaluate`, y un `afterEvaluate` de la raíz corre ANTES que los de ellos.
 * Leer `tasks.names` ahí devolvía `koverVerify` en vez de `koverVerifyDebug` y
 * dejaba fuera `testDevlocalDebugUnitTest`, `verifyRoborazziDebug`,
 * `assembleDevlocalDebug` y las reglas de cobertura acotadas — o sea, un
 * descubrimiento que descubría de menos. Lo detectó el control positivo del
 * propio gate, comparando lo descubierto contra la lista vieja.
 */
fun tareasDeGateDe(proyecto: Project): List<String> {
    val nombres = proyecto.tasks.names
    val modulo = proyecto.path
    val aportadas = mutableListOf<String>()

    prePushTaskFamilies.forEach { familia ->
        val elegida = familia.firstOrNull { it in nombres } ?: return@forEach
        if ("$modulo:$elegida" in prePushTaskAllowlist) return@forEach
        aportadas += elegida
    }
    tareaDeCoberturaDe(proyecto)
        ?.takeUnless { "$modulo:$it" in prePushTaskAllowlist }
        ?.let { aportadas += it }
    // Reglas de cobertura acotadas por paquete, con nombre propio.
    nombres
        .filter { it.startsWith("koverVerify") && it !in koverBaseTaskNames }
        .filterNot { "$modulo:$it" in prePushTaskAllowlist }
        .sorted()
        .forEach { aportadas += it }

    prePushAportes[modulo] = aportadas
    aportadas.forEach { prePushDescubiertas += "$modulo:$it" }
    return aportadas
}

/**
 * Bajas declaradas respecto de [prePushBaselineFile], con su razón.
 *
 * **Vacío.** Las tres tareas que la ronda 1 perdió en silencio se recuperaron:
 * no había ninguna baja legítima, sólo un descubrimiento incompleto.
 */
val prePushBaselineBajas: Map<String, String> = emptyMap()

/**
 * Piso de tamaño de la base congelada: las 68 entradas que tenía el
 * `dependsOn` escrito a mano de `d2073787`. Vaciar el archivo o recortarlo es
 * la forma obvia de ablandar esta red, así que el tamaño también se afirma.
 */
val MINIMO_DE_LA_BASE = 68

/** La base congelada del Ruling BB. Ver el encabezado del propio archivo. */
val prePushBaselineFile: File = file("gradle/prepush-baseline.txt")

/** Lo que el descubrimiento entregó de verdad, en la forma canónica de la base. */
val prePushDescubiertas: MutableSet<String> = linkedSetOf()

/**
 * Ruling BB — el control de NO-REGRESIÓN sobre la propia compuerta.
 *
 * Descubrir no basta: el conjunto descubierto puede ser **distinto** del que
 * había, y como la lista ya no existe, no queda nada contra qué comparar. La
 * ronda 1 perdió tres tareas así, en silencio, y una era `checkNoLegacyDateApi`.
 *
 * Esto exige que lo descubierto **contenga** la base congelada. Ganar tareas es
 * el objetivo y no se toca; perder una hay que escribirla en
 * [prePushBaselineBajas] con su motivo.
 */
fun Task.verificarLaBaseCongelada() {
    // Control positivo del control: una base vacía o ausente convertiría la
    // comprobación de abajo en un `emptySet - x = emptySet`, o sea un verde que
    // no mide nada. Es el defecto que este arreglo persigue; no puede vivir
    // dentro de la red que lo atrapa.
    if (!prePushBaselineFile.isFile) {
        throw GradleException(
            "prePushCheck: falta ${prePushBaselineFile.path}, la base congelada del Ruling BB. " +
                "Sin ella el control de no-regresión no compara contra nada."
        )
    }
    val base = prePushBaselineFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toSet()
    if (base.size < MINIMO_DE_LA_BASE) {
        throw GradleException(
            "prePushCheck: la base congelada tiene ${base.size} entradas, menos que el mínimo " +
                "de $MINIMO_DE_LA_BASE. O se vació por accidente, o alguien la recortó para " +
                "que el gate pasara — que es la forma de ablandar esta red."
        )
    }

    val faltantes = (base - prePushDescubiertas - prePushBaselineBajas.keys).sorted()
    if (faltantes.isNotEmpty()) {
        throw GradleException(
            "prePushCheck: el descubrimiento PERDIÓ tareas que la compuerta escrita a mano sí " +
                "corría: $faltantes.\n" +
                "Cambiar una lista por un descubrimiento no es gratis: el conjunto nuevo puede " +
                "ser distinto sin que nadie lo note. Recuperalas, o declaralas en " +
                "`prePushBaselineBajas` CON su razón."
        )
    }

    val bajasVivas = prePushBaselineBajas.keys.filter { it in prePushDescubiertas }.sorted()
    if (bajasVivas.isNotEmpty()) {
        throw GradleException(
            "prePushCheck: estas entradas de `prePushBaselineBajas` describen tareas que HOY sí " +
                "se descubren: $bajasVivas. Bórralas — una baja declarada que ya no ocurre es " +
                "una excusa escrita para algo que no pasa."
        )
    }

    val ganadas = (prePushDescubiertas - base).sorted()
    logger.lifecycle(
        "prePushCheck: base congelada ${base.size}/${base.size} cubierta" +
            if (ganadas.isEmpty()) "" else ", ${ganadas.size} tareas ganadas: $ganadas"
    )
}

val prePushCheck = tasks.register("prePushCheck") {
    group = "verification"
    description = "Gate agregado pre-push: ktlint + tests + detekt + kover + roborazzi + build, " +
        "sobre TODOS los módulos del build, descubiertos desde Gradle."

    dependsOn(gradle.includedBuild("build-logic").task(":ktlintCheck"))

    // `Callable` y no una lista: Gradle lo resuelve al armar el grafo de
    // tareas, cuando TODOS los proyectos ya están configurados. Es la única
    // forma de ver las tareas por variante (ver KDoc de `tareasDeGateDe`).
    dependsOn(
        Callable {
            val deLaRaiz = tareasDeGateDeLaRaiz()
            prePushAportes[":"] = deLaRaiz
            deLaRaiz.forEach { prePushDescubiertas += ":$it" }
            // El build incluido no es un subproyecto: se referencia explícito
            // arriba y se registra acá para que la base congelada lo cubra.
            prePushDescubiertas += "build-logic:ktlintCheck"
            val deLosModulos = modulosDelBuild
                .flatMap { sub -> tareasDeGateDe(sub).map { "${sub.path}:$it" } }
            deLaRaiz.map { ":$it" } + deLosModulos
        }
    )

    doLast {
        // Control positivo del descubrimiento. Los `dependsOn` de arriba ya
        // corrieron; lo que se afirma acá es que corrieron sobre TODO el árbol.
        // Sin esto, un descubrimiento roto dejaría el gate verde habiendo
        // ejecutado nada — que es el defecto que esta compuerta vino a cerrar,
        // y sería particularmente ridículo reintroducirlo justo aquí.
        val esperados = modulosDelBuild.map { it.path }
        if (esperados.isEmpty()) {
            throw GradleException("prePushCheck: el descubrimiento no encontró NINGÚN módulo.")
        }
        verificarLaBaseCongelada()

        val sinAporte = esperados.filterNot { prePushAportes[it].orEmpty().isNotEmpty() }
        if (sinAporte.isNotEmpty()) {
            throw GradleException(
                "prePushCheck: estos módulos no aportaron NI UNA tarea al gate: $sinAporte.\n" +
                    "Un módulo que la compuerta no ejecuta es un módulo sin compuerta."
            )
        }
        // ktlint y pruebas son el mínimo que todo módulo del repo puede dar. Un
        // módulo que no los tiene está mal configurado, o es una excepción que
        // alguien tiene que escribir.
        val nombresDePrueba = listOf("testDevlocalDebugUnitTest", "testDebugUnitTest", "test")
        val incompletos = esperados.filter { modulo ->
            val aportadas = prePushAportes[modulo].orEmpty()
            val tieneKtlint = "ktlintCheck" in aportadas ||
                "$modulo:ktlintCheck" in prePushTaskAllowlist
            val tienePruebas = aportadas.any { it in nombresDePrueba } ||
                nombresDePrueba.any { "$modulo:$it" in prePushTaskAllowlist }
            !tieneKtlint || !tienePruebas
        }
        if (incompletos.isNotEmpty()) {
            throw GradleException(
                "prePushCheck: estos módulos entraron al gate SIN ktlint o SIN pruebas: " +
                    "$incompletos.\nEs media compuerta, que es peor que ninguna porque parece " +
                    "una. Configurá la tarea que falta, o escribila en `prePushTaskAllowlist` " +
                    "con su razón."
            )
        }
        logger.lifecycle(
            "prePushCheck: ${esperados.size} módulos + la raíz, " +
                "${prePushAportes.values.sumOf { it.size }} tareas descubiertas " +
                "(+ build-logic:ktlintCheck)"
        )
        prePushAportes.toSortedMap().forEach { (modulo, tareas) ->
            logger.lifecycle("  $modulo → ${tareas.joinToString(", ")}")
        }
    }
}
