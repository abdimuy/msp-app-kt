#!/usr/bin/env bash
# Juego de casos compartido de compromisos (visitas P2, Tarea 17; I5; E-APP-104).
# Fuente: msp-api/testdata/compromisos. Copia: core/common/src/test/resources/compromisos.
#   copiar    — reemplaza la copia por la fuente, byte a byte, y verifica.
#   verificar — falla (exit 1) si la copia difiere de la fuente o si VERSION no es el hash.
# MSP_API_DIR permite apuntar a otro checkout de msp-api (default: carpeta hermana).
set -euo pipefail

raiz="$(cd "$(dirname "$0")/.." && pwd)"
fuente="${MSP_API_DIR:-$raiz/../msp-api}/testdata/compromisos"
copia="$raiz/core/common/src/test/resources/compromisos"

hash_de() {
  (cd "$1" && ls -- *.json | LC_ALL=C sort | xargs cat | shasum -a 256 | cut -d' ' -f1)
}

verificar() {
  [[ -f "$fuente/VERSION" ]] || { echo "no existe $fuente/VERSION" >&2; exit 1; }
  diff -r "$fuente" "$copia" >&2 || { echo "DIVERGENCIA: la copia no es idéntica a la fuente" >&2; exit 1; }
  local declarado calculado
  declarado="$(tr -d '\n' < "$copia/VERSION")"
  calculado="$(hash_de "$copia")"
  [[ "$declarado" == "$calculado" ]] || { echo "VERSION=$declarado pero el hash es $calculado" >&2; exit 1; }
  echo "ok: juego idéntico, VERSION $declarado"
}

case "${1:-}" in
  copiar)
    [[ -f "$fuente/VERSION" ]] || { echo "no existe $fuente/VERSION" >&2; exit 1; }
    rm -rf "$copia"
    mkdir -p "$copia"
    cp -p "$fuente"/*.json "$fuente/VERSION" "$copia/"
    verificar
    ;;
  verificar) verificar ;;
  *) echo "uso: $0 copiar|verificar" >&2; exit 2 ;;
esac
