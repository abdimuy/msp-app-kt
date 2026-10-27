#!/usr/bin/env bash
# Juegos de casos compartidos con msp-api (visitas P2, Tarea 17; I5; E-APP-104, E-APP-112):
#   compromisos      — derivación de compromisos (testdata/compromisos).
#   fecha-pago-sync  — formato de la fecha de un pago en el sync (testdata/fecha-pago-sync).
#   conciliacion-agenda — conciliación directa de la agenda, teléfono contra servidor
#                         (testdata/conciliacion-agenda; Tarea 18, E-APP-133). Su copia vive
#                         en app/src/test/resources porque la prueba corre sobre Room.
# Fuente: msp-api/testdata/<juego>. Copia: core/common/src/test/resources/<juego>
# (app/src/test/resources/<juego> para conciliacion-agenda).
#   copiar    — reemplaza cada copia por su fuente, byte a byte, y verifica.
#   verificar — falla (exit 1) si una copia difiere de su fuente o si su VERSION no es el hash.
# MSP_API_DIR permite apuntar a otro checkout de msp-api (default: carpeta hermana).
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
juegos=(compromisos fecha-pago-sync conciliacion-agenda)

hash_de() {
  (cd "$1" && ls -- *.json | LC_ALL=C sort | xargs cat | shasum -a 256 | cut -d' ' -f1)
}

verificar_juego() {
  local fuente="$1" copia="$2"
  [[ -f "$fuente/VERSION" ]] || { echo "no existe $fuente/VERSION" >&2; exit 1; }
  diff -r "$fuente" "$copia" >&2 || { echo "DIVERGENCIA: la copia no es idéntica a la fuente" >&2; exit 1; }
  local declarado calculado
  declarado="$(tr -d '\n' < "$copia/VERSION")"
  calculado="$(hash_de "$copia")"
  [[ "$declarado" == "$calculado" ]] || { echo "VERSION=$declarado pero el hash es $calculado" >&2; exit 1; }
  echo "ok: juego $(basename "$copia") idéntico, VERSION $declarado"
}

copiar_juego() {
  local fuente="$1" copia="$2"
  [[ -f "$fuente/VERSION" ]] || { echo "no existe $fuente/VERSION" >&2; exit 1; }
  rm -rf "$copia"
  mkdir -p "$copia"
  cp -p "$fuente"/*.json "$fuente/VERSION" "$copia/"
  verificar_juego "$fuente" "$copia"
}

case "${1:-}" in
  copiar|verificar)
    for j in "${juegos[@]}"; do
      fuente="${MSP_API_DIR:-$raiz/../msp-api}/testdata/$j"
      base="core/common/src/test/resources"
      [[ "$j" == conciliacion-agenda ]] && base="app/src/test/resources"
      copia="$raiz/$base/$j"
      if [[ "$1" == copiar ]]; then copiar_juego "$fuente" "$copia"; else verificar_juego "$fuente" "$copia"; fi
    done
    ;;
  *) echo "uso: $0 copiar|verificar" >&2; exit 2 ;;
esac
