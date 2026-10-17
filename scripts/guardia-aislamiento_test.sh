#!/usr/bin/env bash
# Prueba de la guardia de aislamiento: control positivo (lo vedado se rechaza)
# y negativo (lo permitido pasa). Cada caso corre en un repo git temporal nuevo,
# con HOME aislado para que la configuración global de quien la corre no cuente.
# La evasión que cierra cada caso está en su descripción. Los casos genéricos
# del script (nombres, lista, errores) viven en la prueba de msp-api, que corre
# el mismo script byte a byte.
set -euo pipefail
AQUI="$(cd "$(dirname "$0")" && pwd)"
GUARDIA="$AQUI/guardia-aislamiento.sh"
LISTA="$AQUI/guardia-aislamiento.lista"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
export HOME="$TMP/home" GIT_CONFIG_NOSYSTEM=1; mkdir -p "$HOME"
unset GIT_EXTERNAL_DIFF GIT_DIFF_OPTS 2>/dev/null || true
CERO=0000000000000000000000000000000000000000
W=app/src/main/java/com/example/msp_app/workers
DB=core/database/src/main/kotlin/com/example/msp_app/core/database
ADB="$DB/AppDatabase.kt"
falla=0

semilla_appdatabase() {
  cat > "$ADB" <<'KT'
package x
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.migrations.MIGRATION_32_33
import com.example.msp_app.core.database.migrations.MIGRATION_33_34
@Database(
    entities = [
        PaymentEntity::class,
        VisitEntity::class,
    ],
    version = 34,
)
abstract class AppDatabase : RoomDatabase() {
    /** pagos del cobrador */
    abstract fun paymentDao(): PaymentDao
    fun b() = builder.addMigrations(
        MIGRATION_32_33,
        MIGRATION_33_34,
    ).fallbackToDestructiveMigrationFrom(1..19)
}
KT
}
semilla_gradle() {
  cat > build.gradle.kts <<'KTS'
plugins {
    id("base")
}

// Install git pre-commit hook automatically on build
tasks.register("installGitHook", Copy::class) {
    from("${rootProject.rootDir}/scripts/pre-commit")
    into("${rootProject.rootDir}/.git/hooks")
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitHook")
}

tasks.register("installGitPushHook", Copy::class) {
    from("${rootProject.rootDir}/scripts/pre-push")
    into("${rootProject.rootDir}/.git/hooks")
}

tasks.register("otra") {
    group = "x"
}
KTS
}
nuevo() { # $1 = con (la base ya tiene guardia y lista) | sin (arranque: la base no la tiene)
  R="$(mktemp -d "$TMP/r.XXXXXX")"; cd "$R"
  git init -q -b main . && git config user.email t@t && git config user.name t
  mkdir -p scripts "$W" "$DB" .github/workflows
  if [[ "${1:-con}" == con ]]; then cp "$GUARDIA" "$LISTA" scripts/; fi
  echo 'package x' > "$W/PendingVisitsWorker.kt"
  echo 'package x' > "$W/PendingPaymentsWorker.kt"
  printf '#!/bin/sh\necho pre-commit\n' > scripts/pre-commit
  printf '#!/bin/sh\necho pre-push\n' > scripts/pre-push
  printf 'name: CI\n' > .github/workflows/ci.yml
  semilla_appdatabase; semilla_gradle
  git add -A && git commit -qm base && git checkout -q -b prueba
}
confirma() { git add -A && git commit -qm cambio; }
edita() { # edita <archivo> <expresión sed>
  sed -i.bak -e "$2" "$1" && rm "$1.bak"
}
# espera <código|no0> <descripción> [args de la guardia]  (por defecto --rango main...HEAD)
espera() {
  local esperado="$1" desc="$2"; shift 2
  [[ $# -gt 0 ]] || set -- --rango main...HEAD
  local rc=0 out
  out="$(printf '%b' "${ENTRADA:-}" | bash "$GUARDIA" "$@" 2>&1)" || rc=$?
  # Un 1 cuenta solo si la guardia dijo por qué: un error de bash también sale 1.
  if [[ "$esperado" == 1 && $rc -eq 1 && "$out" != *"guardia: vedado: "* ]]; then rc="1-sin-motivo"; fi
  if [[ "$esperado" == no0 && $rc != 0 ]] || [[ "$esperado" == "$rc" ]]; then
    echo "ok: $desc"
  else
    echo "FALLA: $desc (esperado $esperado, obtuvo $rc) :: $(printf '%s' "$out" | tr '\n' '|')"; falla=1
  fi
  ENTRADA=""; cd "$TMP"
}

# ── Casos del plan ──────────────────────────────────────────────────────────
nuevo; echo '// x' >> "$W/PendingVisitsWorker.kt"; confirma
espera 0 "tocar PendingVisitsWorker pasa"
nuevo; echo '// x' >> "$W/PendingPaymentsWorker.kt"; confirma
espera 1 "tocar PendingPaymentsWorker se rechaza (control positivo, spec §7.1)"
nuevo; edita "$ADB" 's#version = 34,#version = 35,#'; edita "$ADB" 's#^        MIGRATION_33_34,#        MIGRATION_33_34,\
        MIGRATION_34_35,#'; confirma
espera 0 "AppDatabase: subir version y agregar MIGRATION_34_35 pasa"
nuevo; edita "$ADB" 's#fallbackToDestructiveMigrationFrom(1\.\.19)#fallbackToDestructiveMigrationFrom(1..20)#'; confirma
espera 1 "AppDatabase: cambiar fallbackToDestructiveMigrationFrom se rechaza"

# ── Nombres y mayúsculas ────────────────────────────────────────────────────
nuevo; git mv "$W/PendingPaymentsWorker.kt" "$W/Otro.kt"; confirma
espera 1 "N1 renombrar PendingPaymentsWorker (git mv) se rechaza"
nuevo; b="$(echo evil | git hash-object -w --stdin)"
git update-index --add --cacheinfo "100644,$b,$W/pendingpaymentsworker.kt"; git commit -qm c
espera 1 "C2 pendingpaymentsworker.kt (otras mayúsculas) se rechaza"

# ── Hunks escondidos en AppDatabase.kt ──────────────────────────────────────
nuevo; printf 'AppDatabase.kt -diff\n' >> .git/info/attributes
edita "$ADB" 's#fallbackToDestructiveMigrationFrom(1\.\.19)#fallbackToDestructiveMigrationFrom(1..20)#'; confirma
espera 1 "H1 AppDatabase.kt con -diff no esconde el cambio de fallback"
nuevo; git config color.ui always
edita "$ADB" 's#fallbackToDestructiveMigrationFrom(1\.\.19)#fallbackToDestructiveMigrationFrom(1..20)#'; confirma
espera 1 "H2 color.ui=always no esconde el cambio de fallback"
nuevo; git config diff.external /usr/bin/true
edita "$ADB" 's#fallbackToDestructiveMigrationFrom(1\.\.19)#fallbackToDestructiveMigrationFrom(1..20)#'; confirma
espera 1 "H3 diff.external=/usr/bin/true no esconde el cambio de fallback"

# ── Excepción de AppDatabase.kt ─────────────────────────────────────────────
nuevo; edita "$ADB" 's#version = 34,#version = 35,#'
edita "$ADB" 's#^        VisitEntity::class,#        VisitEntity::class,\
        CompromisoEntity::class,#'
edita "$ADB" 's#^    abstract fun paymentDao(): PaymentDao#    abstract fun paymentDao(): PaymentDao\
    abstract fun compromisoDao(): CompromisoDao#'
edita "$ADB" 's#^import com.example.msp_app.core.database.migrations.MIGRATION_33_34#import com.example.msp_app.core.database.migrations.MIGRATION_33_34\
import com.example.msp_app.core.database.migrations.MIGRATION_34_35#'
edita "$ADB" 's#^        MIGRATION_33_34,#        MIGRATION_33_34,\
        MIGRATION_34_35,#'; confirma
espera 0 "K0 version+1, entidad, DAO, import y migración nuevos pasa"
nuevo; edita "$ADB" '/PaymentEntity::class,/d'; edita "$ADB" '/abstract fun paymentDao/d'; confirma
espera 1 "K1 quitar PaymentEntity y paymentDao se rechaza"
nuevo; edita "$ADB" '/^        MIGRATION_32_33,/d'; confirma
espera 1 "K2 quitar MIGRATION_32_33 se rechaza"
nuevo; edita "$ADB" 's#version = 34,#version = 5,#'; confirma
espera 1 "K3 bajar version a 5 se rechaza"
nuevo; edita "$ADB" 's#version = 34,#version = 36,#'; confirma
espera 1 "K4 subir version 2 de golpe se rechaza"

# KDoc de una línea (decisión del dueño, 2026-10-17): solo agregarlo.
nuevo; edita "$ADB" 's#^    abstract fun paymentDao(): PaymentDao#    abstract fun paymentDao(): PaymentDao\
    /** `envio_diagnostico` — motivo del último fallo de envío (E-APP-063). */#'; confirma
espera 0 "KD1 agregar un KDoc de una sola línea pasa"
nuevo; edita "$ADB" 's#^    abstract fun paymentDao(): PaymentDao#    abstract fun paymentDao(): PaymentDao\
    /** x */ val y = 1#'; confirma
espera 1 "KD2 un KDoc con código detrás en la misma línea se rechaza"
nuevo; edita "$ADB" '/pagos del cobrador/d'; confirma
espera 1 "KD3 quitar un KDoc existente se rechaza"
nuevo; edita "$ADB" 's#^    abstract fun paymentDao(): PaymentDao#    abstract fun paymentDao(): PaymentDao\
    /**\
     * x\
     */#'; confirma
espera 1 "KD4 un comentario de varias líneas se rechaza"
nuevo; edita "$ADB" 's#pagos del cobrador#pagos del cobrador, ya no#'; confirma
espera 1 "KD5 modificar un KDoc existente se rechaza"
nuevo; edita "$ADB" 's#^    abstract fun paymentDao(): PaymentDao#    abstract fun paymentDao(): PaymentDao\
    // x#'; confirma
espera 1 "KD6 un comentario // se rechaza"

# Revisión por contenido de migraciones Room y de execSQL en .kt nuevos.
MIG="$DB/migrations"
nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("DELETE FROM payments")\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "K8 Migration34to35.kt nuevo con DELETE FROM payments se rechaza"
nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("UPDATE `Payment` SET IMPORTE = 0")\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "K8b la tabla Room real de pagos (Payment, entre comillas) se rechaza"
nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE Visit ADD COLUMN CONFIRMADA_EN INTEGER")\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 0 "K9 Migration34to35.kt de visitas (ALTER TABLE Visit ADD COLUMN) pasa"
nuevo; git checkout -q main; mkdir -p "$MIG"; printf 'val M = 1\n' > "$MIG/Migration34to35.kt"; git add -A; git commit -qm m
git checkout -q prueba; git rebase -q main
printf 'val M = 1\nfun f(db: SupportSQLiteDatabase) = db.execSQL("DELETE FROM sales")\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "K10 cambiar una migración Room existente para tocar sales se rechaza"
nuevo; mkdir -p "$W"; printf 'class Limpieza {\n    fun x(db: SupportSQLiteDatabase) = db.execSQL("UPDATE sales SET ESTADO = 0")\n}\n' > "$W/LimpiezaVisitas.kt"; confirma
espera 1 "K11 un .kt nuevo con execSQL(UPDATE sales) se rechaza"
nuevo; mkdir -p app/src/main/java/com/example/msp_app/data/visitas; printf 'package pagos\nfun x(db: SupportSQLiteDatabase) = db.execSQL("DELETE FROM local_sale")\n' > app/src/main/java/com/example/msp_app/data/visitas/Util.kt; confirma
espera 1 "K12 un .kt nuevo en otra ruta con execSQL sobre local_sale se rechaza"
nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("CREATE TRIGGER t AFTER INSERT ON Visit BEGIN SELECT 1; END")\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "K13 CREATE TRIGGER en una migración Room nueva se rechaza"

nuevo; mkdir -p "$MIG"; printf '/**\n * No toca `Payment` ni `sales`: solo agrega una columna a Visit.\n */\nval M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE Visit ADD COLUMN CONFIRMADA_EN INTEGER")\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 0 "KC1 un KDoc que cita Payment sin palabra SQL pasa"
nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("""\n            DELETE FROM\n            payments\n        """)\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "KC2 la tabla en su propio renglón dentro de una cadena multilínea se rechaza"
nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    override fun migrate(db: SupportSQLiteDatabase) {\n        db.execSQL("""\n            UPDATE\n            * FROM payments\n        """)\n    }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "KC3 un renglón que empieza con * pero trae palabra SQL cuenta"
nuevo; T=core/database/src/test/kotlin/com/example/msp_app/core/database/migration; mkdir -p "$T"
printf 'class Migration34to35Test {\n    fun semilla(db: SupportSQLiteDatabase) = db.execSQL("INSERT INTO Payment (ID) VALUES (1)")\n}\n' > "$T/Migration34to35Test.kt"; confirma
espera 0 "KC4 una prueba nueva (src/test) que siembra Payment pasa: no llega a la base"

nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n  override fun migrate(db: SupportSQLiteDatabase) {\n    db.execSQL("""\n      DELETE FROM\n      /**/ Payment\n    """)\n  }\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "C3 la tabla detrás de /**/ dentro de una cadena multilínea se rechaza"
nuevo; mkdir -p "$MIG"; printf 'fun m(db: SupportSQLiteDatabase) = db.execSQL("DELETE FROM " +\n    /* t */ "Payment")\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "C3b la tabla detrás de un comentario de bloque en una concatenación se rechaza"
nuevo; mkdir -p "$MIG"; printf '/**\n * no toca Payment\n * copia filas de visits into visits2\n */\nval M = 1\n' > "$MIG/Migration34to35.kt"; confirma
espera 0 "L7 un KDoc «no toca Payment» con una palabra SQL en otra línea pasa"

nuevo; mkdir -p "$MIG"; printf 'val M = object : Migration(34, 35) {\n    // fin */ de algo\n    override fun migrate(db: SupportSQLiteDatabase) = Unit\n}\n' > "$MIG/Migration34to35.kt"; confirma
espera 1 "NV5 migración Room con */ sin pareja: no se puede verificar, se rechaza"

# ── Autoprotección ──────────────────────────────────────────────────────────
nuevo sin; cp "$GUARDIA" "$LISTA" scripts/; echo 'bash scripts/guardia-aislamiento.sh --staged' >> scripts/pre-commit
echo 'bash scripts/guardia-aislamiento.sh --pre-push' >> scripts/pre-push; echo '# guardia' >> .github/workflows/ci.yml; confirma
espera 0 "A1 arranque: la rama que introduce la guardia (la base no tiene lista) pasa"
cd "$R"; git checkout -q main; git merge -q --ff-only prueba; git checkout -q -b segunda
echo '# x' >> scripts/pre-commit; confirma
espera 1 "A2 un segundo commit que modifica scripts/pre-commit (lista ya en la base) se rechaza"
nuevo; echo '# x' >> scripts/pre-push; confirma
espera 1 "A3 tocar scripts/pre-push se rechaza"
nuevo; echo '# x' >> .github/workflows/ci.yml; confirma
espera 1 "A4 tocar .github/workflows se rechaza"
nuevo; printf '# vacía\n' > scripts/guardia-aislamiento.lista; echo '// x' >> "$W/PendingPaymentsWorker.kt"; confirma
espera 1 "A5 vaciar la lista y tocar PendingPaymentsWorker en el mismo commit se rechaza"
nuevo; printf '*.kt -diff\n' > .gitattributes; confirma
espera 1 "A6 agregar .gitattributes se rechaza"

# ── build.gradle.kts: región installGit* ────────────────────────────────────
nuevo; edita build.gradle.kts 's#group = "x"#group = "y"#'; confirma
espera 0 "G0 cambiar otra tarea de build.gradle.kts pasa"
nuevo; edita build.gradle.kts 's#scripts/pre-commit")#scripts/otro")#'; confirma
espera 1 "G1 cambiar la fuente de installGitHook se rechaza"
nuevo; edita build.gradle.kts '/dependsOn("installGitHook")/d'; confirma
espera 1 "G2 quitar el dependsOn de installGitHook se rechaza"
nuevo; printf '\ntasks.named("installGitPushHook") {\n    enabled = false\n}\n' >> build.gradle.kts; confirma
espera 1 "G3 agregar un bloque que apaga installGitPushHook se rechaza"

# Cualquier *.gradle.kts: líneas agregadas que tocan los ganchos de git.
nuevo; edita build.gradle.kts 's#group = "x"#group = "x"; File(".git/hooks/pre-commit").delete()#'; confirma
espera 1 "G4 build.gradle.kts: una línea que borra .git/hooks fuera de installGit* se rechaza"
nuevo; mkdir -p app; printf 'tasks.register("x") {\n    doLast { exec { commandLine("git", "config", "core.hooksPath", "/dev/null") } }\n}\n' > app/build.gradle.kts; confirma
espera 1 "G5 app/build.gradle.kts: core.hooksPath se rechaza"
nuevo; mkdir -p app; printf 'tasks.named("installGitPushHook") {\n    enabled = false\n}\n' > app/build.gradle.kts; confirma
espera 1 "G6 app/build.gradle.kts: apagar installGitPushHook desde otro módulo se rechaza"
nuevo; mkdir -p app; printf 'plugins {\n    id("com.android.application")\n}\n' > app/build.gradle.kts; confirma
espera 0 "G7 app/build.gradle.kts sin ganchos pasa"

# Rutas no ASCII (la regla es del script; aquí con una ruta de pagos).
nuevo; b="$(echo evil | git hash-object -w --stdin)"
git update-index --add --cacheinfo "100644,$b,$(printf 'app/src/main/java/com/example/msp_app/data/pago\xc5\xbf/X.kt')"; git commit -qm c
espera 1 "E1 data/pagoſ/ (U+017F) se rechaza"

# ── staged y pre-push ───────────────────────────────────────────────────────
nuevo; echo '// x' >> "$W/PendingPaymentsWorker.kt"; git add -A
espera 1 "S1 --staged rechaza PendingPaymentsWorker" --staged
nuevo; echo '// x' >> "$W/PendingPaymentsWorker.kt"; confirma; s="$(git rev-parse HEAD)"; git checkout -q main
ENTRADA="refs/heads/prueba $s refs/heads/prueba $CERO\n"
espera 1 "P1 estando en main y empujando otra rama con PendingPaymentsWorker se rechaza" --pre-push
nuevo; echo '// x' >> "$W/PendingVisitsWorker.kt"; confirma; s="$(git rev-parse HEAD)"
ENTRADA="refs/heads/prueba $s refs/heads/prueba $CERO\n"
espera 0 "P2 empujar una rama limpia pasa" --pre-push

exit $falla
