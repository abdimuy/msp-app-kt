#!/usr/bin/env bash
# Guardia de aislamiento del dinero (spec visitas §7.1, capa 2).
#
#   guardia-aislamiento.sh [--rango <a...b>]   diff de b contra merge-base(a, b)
#   guardia-aislamiento.sh --staged            índice contra HEAD
#   guardia-aislamiento.sh --pre-push          lee de stdin las líneas del gancho
#                                              pre-push y revisa cada ref empujado
#
# Sale 0 si el diff es limpio, 1 si toca un archivo de
# scripts/guardia-aislamiento.lista (imprime «guardia: vedado: <archivo>
# (patrón <p>)»), 2 si hay un error de uso o de entorno. Las excepciones
# enumeradas se verifican por CONTENIDO del archivo, no por el texto del diff.
# No hay bandera para saltarla: quitar un patrón es un PR que aprueba el dueño.
#
# Decisiones que la hacen difícil de evadir (cada una tiene su caso en
# guardia-aislamiento_test.sh):
#   - La lista se lee de la BASE (merge-base en rango, HEAD en staged): un
#     commit no puede vaciarla, ni comentar un patrón, para colarse a sí mismo.
#   - Nombres con -z, sin comillas ni renombres (un renombre cuenta el nombre
#     viejo y el nuevo), sin diff.relative, desde la raíz, sin distinguir
#     mayúsculas.
#   - El contenido se lee con cat-file (sin textconv, atributos, diff externo
#     ni color) y la única comparación de líneas usa git diff -a con todo eso
#     apagado. Una excepción sin cambios visibles se niega.
#   - Falla cerrada: grep que truena, lista ausente o vacía, rango inválido.
set -euo pipefail
# Sin expansión de comodines: los patrones de la lista (internal/cobranza/**) se
# comparan como texto; expandirlos contra el disco los convertía en otra cosa.
set -f
# Bytes, no caracteres: un nombre con UTF-8 inválido no debe escapar de «.*».
export LC_ALL=C
shopt -s nocasematch
unset GIT_EXTERNAL_DIFF GIT_DIFF_OPTS 2>/dev/null || true

# Toda salida normal pasa por salir/err, que marcan TERMINO. Si el script muere
# por otra vía (variable sin definir, set -e), sale con código distinto de
# cero: 2 en lo medido con bash 3.2 y 5.2, y el revisor midió 1 en un caso; no
# se promete cuál, solo que falla cerrada. Sin esto, bash 3.2 (el de macOS)
# salía 0 ante un error fatal con trap EXIT y la guardia «pasaba».
TERMINO=0
salir() { TERMINO=1; exit "$1"; }
err() { echo "guardia: $*" >&2; salir 2; }
RAIZ="$(git rev-parse --show-toplevel 2>/dev/null)" || err "no estoy dentro de un repo git"
cd "$RAIZ"
G=(git -c core.quotepath=off -c diff.external= -c color.ui=never -c diff.relative=false)
DF=(--no-renames --no-ext-diff --no-textconv --no-color --no-relative)
RUTA_LISTA="scripts/guardia-aislamiento.lista"
TMPD="$(mktemp -d)"
trap 'rm -rf "$TMPD"; [[ $TERMINO -eq 1 ]] || { echo "guardia: terminé por un error inesperado" >&2; exit 2; }' EXIT
INDICE="@indice"

es_commit() { git rev-parse --verify -q "$1^{commit}" >/dev/null 2>&1; }
# blob <rev|@indice> <ruta>: contenido crudo; sale 1 si no existe.
blob() {
  local ref
  if [[ "$1" == "$INDICE" ]]; then ref=":$2"; else ref="$1:$2"; fi
  git cat-file -e "$ref" 2>/dev/null || return 1
  git cat-file blob "$ref"
}
existe() { [[ -n "$1" ]] && blob "$1" "$2" >/dev/null; }
recorta() { local s="$1"; s="${s#"${s%%[![:space:]]*}"}"; printf '%s' "${s%"${s##*[![:space:]]}"}"; }

# cargar_lista <rev de la base o ""> <respaldo: rev o @indice>
# Llena PATRONES/SOLO_BASE. ARRANQUE=1 si la base aún no tiene lista.
cargar_lista() {
  local crudo l solo
  ARRANQUE=0
  if [[ -n "$1" ]] && crudo="$(blob "$1" "$RUTA_LISTA")"; then :
  else
    ARRANQUE=1
    if crudo="$(blob "$2" "$RUTA_LISTA")"; then :
    elif [[ -r "$RUTA_LISTA" ]]; then crudo="$(cat "$RUTA_LISTA")"
    else err "no encuentro $RUTA_LISTA ni en la base ni en el árbol"; fi
  fi
  PATRONES=(); SOLO_BASE=(); PERMITIDOS=(); MOTIVOS=()
  while IFS= read -r l || [[ -n "$l" ]]; do
    l="$(recorta "${l//$'\r'/}")"
    [[ -z "$l" || "$l" == \#* ]] && continue
    # «@permitir <ruta exacta> # <motivo>»: enmienda puntual que aprueba el
    # dueño. Ruta ASCII sin comodines; el motivo es obligatorio.
    if [[ "$l" == "@permitir "* ]]; then
      local resto="${l#@permitir }" ruta motivo
      [[ "$resto" == *" # "* ]] || err "@permitir sin motivo («# …»): $l"
      ruta="$(recorta "${resto%% # *}")"; motivo="$(recorta "${resto#* # }")"
      [[ -n "$motivo" ]] || err "@permitir sin motivo: $l"
      [[ "$ruta" =~ ^[A-Za-z0-9._/-]+$ && "$ruta" != /* && "$ruta" != */ && "$ruta" != *..* && "$ruta" != *//* ]] ||
        err "@permitir necesita una ruta exacta, ASCII y sin comodines: $l"
      PERMITIDOS+=("$ruta"); MOTIVOS+=("$motivo"); continue
    fi
    solo=0
    if [[ "$l" == "@base "* ]]; then solo=1; l="$(recorta "${l#@base }")"; fi
    [[ "$l" == @* || "$l" =~ [[:space:]] ]] && err "línea inválida en la lista: $l"
    PATRONES+=("$l"); SOLO_BASE+=("$solo")
  done <<< "$crudo"
  [[ ${#PATRONES[@]} -gt 0 ]] || err "la lista $RUTA_LISTA no tiene patrones"
}

glob_a_regex() { # ** = cualquier cosa (incluye /), * = cualquier cosa sin /, [..] se conserva
  local g="$1"
  g="$(printf '%s' "$g" | sed -e 's/[.^$+(){}|]/\\&/g' -e 's/\*\*/§/g' -e 's/\*/[^\/]*/g' -e 's/§/.*/g')"
  printf '^%s$' "$g"
}

# Pasa (0) solo si TODAS las líneas de stdin calzan con la regex $2 (grep $1).
# Falla cerrada: si grep truena (rc 2) la excepción se niega.
solo_lineas() {
  local rcg=0
  grep "$1" -v -- "$2" >/dev/null || rcg=$?
  [[ $rcg -eq 1 ]]
}

# ── Excepciones (se evalúan con nocasematch apagado) ────────────────────────
# OLD = rev de la base ("" si no hay); NEW = rev de la punta o @indice.

# failedintent_wiring.go: quitando la ÚNICA línea Registrar de visitas (y el
# punto que encadena la línea previa, si la de visitas cierra la cadena) y, si
# está, la ÚNICA línea de import del extractor de visitas, el archivo debe
# quedar idéntico al de la base. El import va dentro del bloque «import (…)», en
# su propia línea. El Registrar usa siempre el alias visitasfailedintents (con
# otro alias no compila: no hay otro import que lo respalde).
IMPORT_VISITAS='visitasfailedintents "github.com/abdimuy/msp-api/internal/visitas/infra/failedintents"'
# normaliza_wiring <contenido nuevo> <mínimo de Registrar: 0|1>: quita la
# única línea Registrar de visitas (y el punto que encadena la previa) y la
# única línea de import; deja el resto en WIRING_NORM. Falla si hay más de una,
# si el import va fuera del bloque o si hay import sin Registrar.
normaliza_wiring() {
  local nuevo="$1" minimo="$2" l i=0 idx=-1 n=0 cierra=0 imp=-1 n_imp=0 en_bloque=0
  local re='^[[:space:]]*Registrar\("/v2/visitas", "visitas", visitasfailedintents\.NewResumenExtractor\(\)\)(\.?)$'
  local -a L=()
  while IFS= read -r l || [[ -n "$l" ]]; do
    L+=("$l")
    if [[ "$l" == "import (" ]]; then en_bloque=1
    elif [[ $en_bloque -eq 1 && "$l" == ")" ]]; then en_bloque=0
    fi
    if [[ "$(recorta "$l")" == "$IMPORT_VISITAS" ]]; then
      n_imp=$((n_imp + 1)); imp=$i
      [[ $en_bloque -eq 1 && "$l" =~ ^[[:space:]]+[^[:space:]] ]] || return 1
    fi
    if [[ "$l" =~ $re ]]; then
      n=$((n + 1)); idx=$i
      [[ -z "${BASH_REMATCH[1]}" ]] && cierra=1 || cierra=0
    fi
    i=$((i + 1))
  done <<< "$nuevo"
  [[ $n -le 1 && $n -ge $minimo && $n_imp -le $n ]] || return 1
  if [[ $n -eq 1 && $cierra -eq 1 ]]; then
    [[ $idx -gt 0 && "${L[$((idx - 1))]}" == *. ]] || return 1
    L[$((idx - 1))]="${L[$((idx - 1))]%.}"
  fi
  local -a M=()
  for ((i = 0; i < ${#L[@]}; i++)); do [[ $i -eq $idx || $i -eq $imp ]] || M+=("${L[$i]}"); done
  [[ ${#M[@]} -gt 0 ]] || return 1
  WIRING_NORM="$(printf '%s\n' "${M[@]}")"
}
excepcion_wiring() {
  local f="$1" viejo nuevo
  [[ -n "$OLD" ]] || return 1
  viejo="$(blob "$OLD" "$f")" || return 1
  nuevo="$(blob "$NEW" "$f")" || return 1
  normaliza_wiring "$nuevo" 1 || return 1
  [[ "$WIRING_NORM" == "$viejo" ]]
}
# sin_comentarios: quita las líneas que son solo un comentario «//» o vacías.
sin_comentarios() {
  local l
  while IFS= read -r l || [[ -n "$l" ]]; do
    # Las directivas de Go (//go:, //line, //export, // +build) son código.
    [[ "$l" =~ ^[[:space:]]*//(go:|line[[:space:]]|export[[:space:]]|[[:space:]]*\+build) ]] && { printf '%s\n' "$l"; continue; }
    [[ "$l" =~ ^[[:space:]]*(//.*)?$ ]] && continue
    printf '%s\n' "$l"
  done <<< "$1"
}
# Con @permitir, el wiring admite además cambiar líneas que son solo
# comentarios «//» (o vacías); el resto debe quedar idéntico a la base, salvo
# la excepción del Registrar y el import de visitas.
wiring_solo_comentarios() {
  local f="$1" viejo nuevo
  viejo="$(blob "$OLD" "$f")" || return 1
  nuevo="$(blob "$NEW" "$f")" || return 1
  normaliza_wiring "$nuevo" 0 || return 1
  [[ "$(sin_comentarios "$WIRING_NORM")" == "$(sin_comentarios "$viejo")" ]]
}

# *sync_epoch*: archivo NUEVO. Se quitan líneas vacías y comentarios «--», se
# une el resto y se parte en sentencias por «;» (los literales no admiten «;»).
# .up.sql:   INSERT INTO MSP_CFG_SYNC_EPOCH con valores literales, columna y
#            valor emparejados: RECURSO aparece una vez y vale 'visitas' o
#            'tareas'; más la convención del repo: COMMIT e
#            INSERT INTO MSP_MIGRATIONS (ID, NAME, APPLIED_AT) VALUES
#            (<id del archivo>, '<nombre del archivo>', CURRENT_TIMESTAMP).
# .down.sql: DELETE de MSP_CFG_SYNC_EPOCH solo de esos recursos; más COMMIT y
#            DELETE FROM MSP_MIGRATIONS WHERE ID = <id del archivo>.
VAL="(-?[0-9]+|'[^';]*'|NULL|CAST\('[^';]*' AS TIMESTAMP\))"
REC="'(visitas|tareas)'"
RE_INSERT='^INSERT INTO MSP_CFG_SYNC_EPOCH \(([A-Z_]+( ?, ?[A-Z_]+)*)\) VALUES \((.*)\)$'
RE_DELETE="^DELETE FROM MSP_CFG_SYNC_EPOCH WHERE RECURSO (= $REC|IN \($REC( ?, ?$REC)*\))( AND [A-Z_]+ = $VAL)*$"
RE_MIG_INSERT="^INSERT INTO MSP_MIGRATIONS \(ID, NAME, APPLIED_AT\) VALUES \(([0-9]+), '([^';]*)', CURRENT_TIMESTAMP\)$"
RE_MIG_DELETE='^DELETE FROM MSP_MIGRATIONS WHERE ID = ([0-9]+)$'
insert_epoch_ok() { # $1 = sentencia sin «;»
  [[ "$1" =~ $RE_INSERT ]] || return 1
  local cols="${BASH_REMATCH[1]}" vals="${BASH_REMATCH[3]}" sep i j rec=-1
  local -a C=() V=()
  IFS=',' read -r -a C <<< "$cols"
  for ((i = 0; i < ${#C[@]}; i++)); do C[$i]="$(recorta "${C[$i]}")"; done
  while :; do
    [[ "$vals" =~ ^$VAL(,[[:space:]]*)?(.*)$ ]] || return 1
    V+=("${BASH_REMATCH[1]}"); sep="${BASH_REMATCH[2]}"; vals="${BASH_REMATCH[3]}"
    if [[ -z "$sep" ]]; then [[ -z "$vals" ]] || return 1; break; fi
    [[ -n "$vals" ]] || return 1
  done
  [[ ${#C[@]} -eq ${#V[@]} ]] || return 1
  for ((i = 0; i < ${#C[@]}; i++)); do
    for ((j = i + 1; j < ${#C[@]}; j++)); do [[ "${C[$i]}" != "${C[$j]}" ]] || return 1; done
    [[ "${C[$i]}" == RECURSO ]] && rec=$i
  done
  [[ $rec -ge 0 ]] || return 1
  [[ "${V[$rec]}" == "'visitas'" || "${V[$rec]}" == "'tareas'" ]]
}
# Un literal SQL no puede cruzar líneas: si una línea tiene un número impar de
# comillas, el renglón «--» siguiente es parte del literal para isql y no un
# comentario (medido por el revisor en Firebird: así se coló un DELETE de
# MSP_PAGOS_RECIBIDOS). Solo en esta excepción: 5 de las 126 migraciones
# existentes tienen literales multilínea legítimos.
comillas_pares() { printf '%s\n' "$1" | awk -F"'" 'NF > 0 && NF % 2 == 0 { m = 1 } END { exit m }'; }
# Un CR suelto (no al final de la línea) es fin de línea para isql: «-- c\rDELETE …»
# ejecuta el DELETE (medido por el revisor en FB5). Un CRLF normal no cuenta.
cr_suelto() { printf '%s\n' "$1" | LC_ALL=C awk '{ sub(/\r$/, ""); if (index($0, "\r")) { m = 1 } } END { exit !m }'; }
excepcion_epoch() {
  local f="$1" nuevo lineas l junto="" st nombre id rcg=0 n_epoch=0 n_mig=0
  [[ "$f" == *.up.sql || "$f" == *.down.sql ]] || return 1
  existe "$OLD" "$f" && return 1
  nombre="${f##*/}"; nombre="${nombre%.up.sql}"; nombre="${nombre%.down.sql}"
  id="${nombre%%_*}"; [[ "$id" =~ ^[0-9]+$ ]] || return 1; id=$((10#$id))
  nuevo="$(blob "$NEW" "$f")" || return 1
  comillas_pares "$nuevo" || return 1
  cr_suelto "$nuevo" && return 1
  lineas="$(printf '%s\n' "$nuevo" | tr -d '\r' | grep -vE '^[[:space:]]*(--.*)?$')" || rcg=$?
  [[ $rcg -eq 0 && -n "$lineas" ]] || return 1
  while IFS= read -r l; do junto="$junto $(recorta "$l")"; done <<< "$lineas"
  junto="$(recorta "$(printf '%s' "$junto" | tr '\t' ' ' | tr -s ' ')")"
  [[ "$junto" == *";" ]] || return 1
  local -a ST=()
  IFS=';' read -r -a ST <<< "${junto%;}"
  [[ ${#ST[@]} -gt 0 ]] || return 1
  for st in "${ST[@]}"; do
    st="$(recorta "$st")"
    [[ -n "$st" ]] || return 1
    [[ "$st" == COMMIT ]] && continue
    if [[ "$f" == *.up.sql ]]; then
      if [[ "$st" =~ $RE_MIG_INSERT ]]; then
        [[ $((10#${BASH_REMATCH[1]})) -eq $id && "${BASH_REMATCH[2]}" == "$nombre" ]] || return 1
        n_mig=$((n_mig + 1)); continue
      fi
      insert_epoch_ok "$st" || return 1
    else
      if [[ "$st" =~ $RE_MIG_DELETE ]]; then
        [[ $((10#${BASH_REMATCH[1]})) -eq $id ]] || return 1
        n_mig=$((n_mig + 1)); continue
      fi
      [[ "$st" =~ $RE_DELETE ]] || return 1
    fi
    n_epoch=$((n_epoch + 1))
  done
  [[ $n_epoch -ge 1 && $n_mig -le 1 ]]
}

# Líneas agregadas/quitadas de $1 entre OLD y NEW, con git diff -a y todo lo
# que puede disfrazar un diff apagado; sin cabeceras.
diff_lineas() {
  if [[ "$NEW" == "$INDICE" ]]; then "${G[@]}" diff --cached "${DF[@]}" -a -U0 -- "$1"
  else "${G[@]}" diff "${DF[@]}" -a -U0 "$OLD" "$NEW" -- "$1"; fi |
    awk '/^@@/ { h = 1; next } h && /^[+-]/ { print }'
}

# AppDatabase.kt: solo version (+1 exacto), entities, accesores DAO nuevos,
# imports de core.database, addMigrations y, desde el 2026-10-17 (decisión del
# dueño), AGREGAR un KDoc de una sola línea («/** … */» solo en su renglón).
# Quitar o cambiar un comentario, «//» o comentarios de varias líneas, no. Toda línea quitada (sin coma final)
# debe reaparecer agregada: no se quita nada. (El import admite dígitos y «_»:
# la regex del plan, [a-zA-Z.]+, rechazaba «import …migrations.MIGRATION_34_35».)
RE_ROOM_ADD='^\+[[:space:]]*(/\*\*([^*]|\*[^/])*\*/|version[[:space:]]*=[[:space:]]*[0-9]+,?|[A-Za-z]+Entity::class,?|abstract fun [a-zA-Z]+\(\): [A-Za-z]+Dao|import com\.example\.msp_app\.core\.database\.[A-Za-z0-9_.]+|\.addMigrations\(|MIGRATION_[0-9]+_[0-9]+[,)]?)?[[:space:]]*$'
RE_VERSION='^[[:space:]]*version[[:space:]]*=[[:space:]]*([0-9]+),?[[:space:]]*$'
excepcion_appdatabase() {
  local f="$1" lineas l norm v_old="" v_new="" n_old=0 n_new=0 a hallada
  existe "$OLD" "$f" || return 1
  existe "$NEW" "$f" || return 1
  lineas="$(diff_lineas "$f")" || return 1
  [[ -n "$lineas" ]] || return 1
  { printf '%s\n' "$lineas" | grep -E '^\+' || true; } | solo_lineas -E "$RE_ROOM_ADD" || return 1
  local -a ADD=() QUITA=()
  while IFS= read -r l; do
    norm="$(recorta "${l:1}")"; norm="$(recorta "${norm%,}")"
    if [[ "$l" == +* ]]; then
      if [[ "${l:1}" =~ $RE_VERSION ]]; then n_new=$((n_new + 1)); v_new="${BASH_REMATCH[1]}"; fi
      ADD+=("$norm")
    else
      if [[ "${l:1}" =~ $RE_VERSION ]]; then n_old=$((n_old + 1)); v_old="${BASH_REMATCH[1]}"; continue; fi
      QUITA+=("$norm")
    fi
  done <<< "$lineas"
  if [[ $n_old -gt 0 || $n_new -gt 0 ]]; then
    [[ $n_old -eq 1 && $n_new -eq 1 ]] || return 1
    [[ $((10#$v_new)) -eq $((10#$v_old + 1)) ]] || return 1
  fi
  [[ ${#QUITA[@]} -eq 0 ]] && return 0
  [[ ${#ADD[@]} -gt 0 ]] || return 1
  for l in "${QUITA[@]}"; do
    [[ -z "$l" ]] && continue
    hallada=0
    for a in "${ADD[@]}"; do [[ "$a" == "$l" ]] && { hallada=1; break; }; done
    [[ $hallada -eq 1 ]] || return 1
  done
}

excepcion_ok() {
  local f="$1" r=0
  shopt -u nocasematch
  case "$f" in
    cmd/api/failedintent_wiring.go) excepcion_wiring "$f" || r=$? ;;
    migrations-firebird/*sync_epoch*) excepcion_epoch "$f" || r=$? ;;
    core/database/src/main/kotlin/com/example/msp_app/core/database/AppDatabase.kt) excepcion_appdatabase "$f" || r=$? ;;
    *) r=1 ;;
  esac
  shopt -s nocasematch
  return $r
}

# build.gradle.kts (msp-app-kt): el archivo no está vedado, pero los bloques
# de primer nivel que mencionan installGit* (las tareas que instalan los
# ganchos, y su dependsOn) deben quedar idénticos a la base.
region_ganchos() {
  awk '
    !b && /^[^[:space:]].*\{[[:space:]]*$/ { b = 1; buf = $0 "\n"; next }
    b { buf = buf $0 "\n"; if ($0 ~ /^\}/) { if (buf ~ /installGit/) printf "%s", buf; b = 0; buf = "" }; next }
    /installGit/ { print }
  '
}
gradle_ok() {
  local f="$1" a="" b=""
  [[ -n "$OLD" ]] && a="$(blob "$OLD" "$f" | region_ganchos)" || true
  b="$(blob "$NEW" "$f" | region_ganchos)" || true
  [[ "$a" == "$b" ]]
}

# Cualquier *.gradle.kts: ninguna línea agregada puede mencionar .git/hooks,
# hooksPath o Git…Hook. Mejor esfuerzo: un Kotlin que arme esos nombres por
# partes no se ve; la región installGit* de la raíz se compara aparte.
gradle_lineas_ok() {
  local lineas rcg=0
  lineas="$(diff_lineas "$1")" || return 1
  { printf '%s\n' "$lineas" | grep -E '^\+' || true; } | grep -qiE '\.git/hooks|hookspath|git[a-z]*hook' || rcg=$?
  [[ $rcg -eq 1 ]]
}

# permitido <archivo>: 0 si una línea @permitir nombra EXACTAMENTE esa ruta
# (distinguiendo mayúsculas) y el archivo existe en la base y en la punta, es
# decir, solo se modifica: crear, borrar o renombrar sigue vedado. Deja el
# motivo en MOTIVO_PERMISO. El wiring además solo puede cambiar comentarios.
permitido() {
  local f="$1" i r=1
  shopt -u nocasematch
  for ((i = 0; i < ${#PERMITIDOS[@]}; i++)); do
    if [[ "$f" == "${PERMITIDOS[$i]}" ]]; then MOTIVO_PERMISO="${MOTIVOS[$i]}"; r=0; break; fi
  done
  if [[ $r -eq 0 ]]; then
    { existe "$OLD" "$f" && existe "$NEW" "$f"; } || r=1
  fi
  if [[ $r -eq 0 && "$f" == cmd/api/failedintent_wiring.go ]]; then wiring_solo_comentarios "$f" || r=1; fi
  shopt -s nocasematch
  return $r
}

# revisar <base o ""> <punta o @indice>
revisar() {
  OLD="$1"; NEW="$2"
  local rc=0 f i re modo_n nombres="$TMPD/nombres"
  if [[ "$NEW" == "$INDICE" ]]; then
    cargar_lista "$OLD" "$INDICE"
    "${G[@]}" diff --cached "${DF[@]}" --name-only -z > "$nombres" || err "git diff --cached falló"
  else
    cargar_lista "$OLD" "$NEW"
    "${G[@]}" diff "${DF[@]}" --name-only -z "$OLD" "$NEW" > "$nombres" || err "git diff $OLD $NEW falló"
  fi
  while IFS= read -r -d '' f; do
    [[ -z "$f" ]] && continue
    # Solo rutas ASCII imprimibles (0x20–0x7E): en APFS «internal/ventaſ/» cae
    # dentro de internal/ventas, y ninguna regex de la lista lo vería.
    if [[ "$f" =~ [^[:print:]] ]]; then
      echo "guardia: vedado: $f (ruta con bytes fuera de ASCII imprimible)"; rc=1; continue
    fi
    # Un enlace simbólico en la punta se veda siempre, antes de @permitir: en
    # disco su contenido es el del archivo al que apunta. Hoy no hay ninguno.
    if [[ "$NEW" == "$INDICE" ]]; then modo_n="$(git ls-files -s -- "$f" | awk '{print $1}')"
    else modo_n="$(git ls-tree "$NEW" -- "$f" | awk '{print $1}')"; fi
    if [[ "$modo_n" == 120000 ]]; then echo "guardia: vedado: $f (enlace simbólico)"; rc=1; continue; fi
    if permitido "$f"; then
      echo "guardia: permitido ($MOTIVO_PERMISO): $f"
    else for ((i = 0; i < ${#PATRONES[@]}; i++)); do
      [[ ${SOLO_BASE[$i]} -eq 1 && $ARRANQUE -eq 1 ]] && continue
      re="$(glob_a_regex "${PATRONES[$i]}")"
      if [[ "$f" =~ $re ]]; then
        if excepcion_ok "$f"; then echo "guardia: excepción verificada: $f"
        else echo "guardia: vedado: $f (patrón ${PATRONES[$i]})"; rc=1; fi
        break
      fi
    done; fi
    if [[ "$f" == build.gradle.kts ]] && ! gradle_ok "$f"; then
      echo "guardia: vedado: $f (tareas installGit* que instalan los ganchos)"; rc=1
    fi
    if [[ "$f" == *.gradle.kts ]] && ! gradle_lineas_ok "$f"; then
      echo "guardia: vedado: $f (línea agregada que toca los ganchos de git)"; rc=1
    fi
    if revisable "$f"; then
      if [[ "$f" == migrations-firebird/*sync_epoch* ]] && excepcion_ok "$f"; then :
      elif ! contenido_ok "$f"; then rc=1; fi
    fi
  done < "$nombres"
  return $rc
}

# Revisión por CONTENIDO (2026-10-17, decisión del dueño), además de la lista
# por ruta. Se revisan: toda migración Firebird nueva (migrations-firebird/*.sql
# que no existe en la base), todo Migration*.kt nuevo, todo .kt nuevo o cambiado
# bajo core/database/**/migrations/, y todo .kt nuevo que agregue execSQL.
# Ninguna línea agregada puede nombrar una tabla de dinero (sin distinguir
# mayúsculas, con o sin comillas), ni crear un trigger o un procedimiento (sin
# lógica en la base). La migración *sync_epoch* que pasa su excepción queda
# fuera: esa excepción ya acota qué filas de MSP_CFG_SYNC_EPOCH toca.
RE_DINERO='(^|[^A-Za-z0-9_])((MSP_PAGOS|MSP_VENTAS|MSP_SALDOS|DOCTOS_CC|IMPORTES_DOCTOS_CC|DOCTOS_PV|DOCTOS_ENTRE_SIS|SALDOS_CC|FORMAS_COBRO|CONCEPTOS_CC|LIBRES_CARGOS_CC|CARGOS_CXC|MSP_CFG_SYNC_EPOCH)[A-Za-z0-9_]*|(payments?|sales|sale_image|pago_imagenes|local_sale|local_sale_combos|local_sale_products|cobranza_sync_state)([^A-Za-z0-9_]|$))'
RE_LOGICA='(^|[^A-Za-z0-9_])(CREATE|ALTER|RECREATE)([[:space:]]+OR[[:space:]]+(ALTER|REPLACE))?[[:space:]]+(TRIGGER|PROCEDURE|FUNCTION|PACKAGE)([^A-Za-z0-9_]|$)'
# Una línea que es solo comentario («*», «//», «/*», «--» al inicio) y no trae
# ninguna palabra SQL no cuenta para las tablas de dinero: así un KDoc puede
# decir «no toca `Payment`». Si trae una palabra SQL, cuenta: dentro de una
# cadena SQL un renglón que empieza así no puede nombrar la tabla que se
# escribe sin una de estas palabras. Las fuentes de prueba no se revisan: no
# llegan al teléfono ni a la base.
SED_BLOQUES='s#/\*([^*]|\*+[^*/])*\*+/# #g'
RE_PALABRA_SQL='(^|[^A-Za-z0-9_])(SELECT|INSERT|UPDATE|DELETE|MERGE|REPLACE|UPSERT|DROP|ALTER|CREATE|TRUNCATE|EXECUTE|FROM|INTO|TABLE|JOIN|SET)([^A-Za-z0-9_]|$)'
revisable() { # 0 si $1 entra en la revisión por contenido
  local f="$1" nueva=1 base
  case "$f" in */src/test/*|*/src/androidTest/*|*/src/sharedTest/*|*/src/testFixtures/*) return 1 ;; esac
  # Una migración nueva es la que no existe en la base.
  existe "$OLD" "$f" && nueva=0
  base="${f##*/}"
  case "$f" in
    migrations-firebird/*.sql) [[ $nueva -eq 1 ]] && return 0 ;;
    core/database/*/migrations/*.kt) return 0 ;;
  esac
  [[ "$base" == Migration*.kt && $nueva -eq 1 ]] && return 0
  if [[ "$f" == *.kt && $nueva -eq 1 ]]; then
    # Sin «| grep -q»: con pipefail, el SIGPIPE del escritor daría falso.
    local agr; agr="$(diff_lineas "$f" | sed -n 's/^+//p')" || return 0
    [[ "$agr" == *execSQL* ]] && return 0
  fi
  return 1
}
no_verificable() { echo "guardia: vedado: $1 (no puedo verificar este archivo: revisión humana — $2)"; }
contenido_ok() { # imprime el motivo y sale 1 si $1 no pasa; grep roto = no pasa
  local f="$1" agregadas rcg
  agregadas="$(diff_lineas "$f" | sed -n 's/^+//p')" || { echo "guardia: vedado: $f (no pude leer su diff)"; return 1; }
  # Lo que no se puede clasificar con certeza se rechaza (falla cerrada):
  # caracteres de control, /* y */ sin pareja, y en SQL de Firebird comillas
  # impares en una línea (un literal que cruza líneas cambia qué es comentario).
  local rcg2=0
  printf '%s' "$agregadas" | grep -q "$(printf '[\001-\010\013\014\016-\037\177]')" || rcg2=$?
  if [[ $rcg2 -ne 1 ]]; then no_verificable "$f" "caracteres de control"; return 1; fi
  if ! printf '%s' "$agregadas" | awk '{ a += gsub(/\/\*/, ""); b += gsub(/\*\//, "") } END { exit (a != b) }'; then
    no_verificable "$f" "/* y */ sin pareja"; return 1
  fi
  if [[ "$f" == *.sql ]] && ! comillas_pares "$agregadas"; then
    no_verificable "$f" "comillas impares: un literal cruza líneas"; return 1
  fi
  if cr_suelto "$agregadas"; then no_verificable "$f" "retorno de carro dentro de una línea"; return 1; fi
  local l sc cuentan=""
  while IFS= read -r l; do
    # Primero se quitan los bloques /*…*/ completos: «/* tabla */ MSP_PAGOS»
    # no es un comentario. Solo se ignora si lo que queda empieza como
    # comentario (o está vacío), no cierra uno («*/») y no trae palabra SQL.
    sc="$(printf '%s' "$l" | sed -E "$SED_BLOQUES")"
    if [[ "$sc" != *'*/'* && "$sc" =~ ^[[:space:]]*(\*|//|/\*|--|$) ]] && ! [[ "$l" =~ $RE_PALABRA_SQL ]]; then continue; fi
    cuentan="$cuentan$l"$'\n'
  done <<< "$agregadas"
  rcg=0; printf '%s\n' "$cuentan" | grep -qiE "$RE_DINERO" || rcg=$?
  if [[ $rcg -ne 1 ]]; then echo "guardia: vedado: $f (migración nueva que nombra una tabla de dinero)"; return 1; fi
  rcg=0; printf '%s' "$agregadas" | tr '\n' ' ' | sed -E "$SED_BLOQUES" | grep -qiE "$RE_LOGICA" || rcg=$?
  if [[ $rcg -ne 1 ]]; then echo "guardia: vedado: $f (trigger o procedimiento en una migración nueva)"; return 1; fi
  return 0
}

# revisar_rango <base> <punta>: si la base aún no tiene la lista y la punta sí,
# solo el commit que la introduce es arranque; de ahí a la punta se juzga con
# la lista de ese commit y con @base activo. Así un commit posterior de la
# misma rama no puede debilitar la lista ni el script antes de que la guardia
# llegue a main.
revisar_rango() {
  local b="$1" p="$2" intro="" c rc=0
  if ! existe "$b" "$RUTA_LISTA" && existe "$p" "$RUTA_LISTA"; then
    while IFS= read -r c; do
      if existe "$c" "$RUTA_LISTA"; then intro="$c"; break; fi
    done < <(git rev-list --reverse --topo-order --ancestry-path "$b..$p" || true)
    [[ -n "$intro" ]] || err "no encuentro el commit que introduce $RUTA_LISTA entre $b y $p"
    revisar "$b" "$intro" || rc=1
    if [[ "$intro" != "$p" ]]; then revisar "$intro" "$p" || rc=1; fi
    return $rc
  fi
  revisar "$b" "$p"
}

# Base del pre-push (local): main; respaldo origin/main. En CI la base es
# origin/main vía --rango. Con main local adelantado a origin/main, comparar
# contra origin/main daba vedados de commits que ya están en main (2026-10-17).
base_de() { # merge-base de $1 con main; respaldo origin/main
  local mb=""
  if es_commit main; then mb="$(git merge-base main "$1" 2>/dev/null)" || mb=""; fi
  if [[ -z "$mb" ]] && es_commit origin/main; then mb="$(git merge-base origin/main "$1" 2>/dev/null)" || mb=""; fi
  [[ -n "$mb" ]] || err "no encuentro una base (main ni origin/main) para $1"
  printf '%s' "$mb"
}

modo="rango"; rango=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rango) [[ $# -ge 2 ]] || err "--rango necesita <a...b>"; rango="$2"; shift 2 ;;
    --staged) modo="staged"; shift ;;
    --pre-push) modo="pre-push"; shift ;;
    *) echo "uso: $0 [--rango <a...b>] [--staged] [--pre-push]" >&2; salir 2 ;;
  esac
done

case "$modo" in
  staged)
    base=""; es_commit HEAD && base="$(git rev-parse HEAD)"
    rc=0; revisar "$base" "$INDICE" || rc=$?
    salir $rc ;;
  pre-push)
    rc=0
    while read -r lref lsha rref rsha || [[ -n "${lref:-}" ]]; do
      [[ -z "${lref:-}" ]] && continue
      [[ "$lsha" =~ ^0+$ ]] && continue
      es_commit "$lsha" || err "pre-push: $lsha no es un commit"
      echo "guardia: revisando $lref -> $rref"
      base="$(base_de "$lsha")" || salir 2
      punta="$(git rev-parse "$lsha^{commit}")" || salir 2
      revisar_rango "$base" "$punta" || rc=1
    done
    salir $rc ;;
  rango)
    if [[ -z "$rango" ]]; then
      if es_commit origin/main; then rango="origin/main...HEAD"; else rango="main...HEAD"; fi
    fi
    [[ "$rango" == *...* ]] || err "--rango debe tener la forma <a...b>: $rango"
    a="${rango%%...*}"; b="${rango#*...}"
    es_commit "$a" || err "--rango: $a no es un commit"
    es_commit "$b" || err "--rango: $b no es un commit"
    mb="$(git merge-base "$a" "$b")" || err "--rango: $a y $b no tienen base común"
    punta="$(git rev-parse "$b^{commit}")" || salir 2
    rc=0; revisar_rango "$mb" "$punta" || rc=$?
    salir $rc ;;
esac
