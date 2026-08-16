#!/usr/bin/env bash
#
# Genera `local.properties` en un runner de CI.
#
# Por que hace falta: `app/build.gradle.kts` LANZA `GradleException` si no
# encuentra `MAPS_API_KEY` en `local.properties` -- no es un warning, la
# configuracion del build muere ahi. Y `local.properties` esta en
# `.gitignore` (contiene la ruta del SDK y una llave real de cada
# desarrollador), asi que en el runner no existe: sin este script CI no
# alcanza ni a configurar el proyecto.
#
# La llave sale del secret `MAPS_API_KEY` del repositorio. Si no esta
# configurado se usa un valor de relleno: los unit tests, ktlint, detekt,
# Roborazzi y el `assembleDevlocalDebug` no consultan la API de Google Maps
# -- solo necesitan que el campo exista para que el build configure. Lo unico
# que se degrada con el relleno es el mapa dentro de un APK de CI, que nadie
# instala.
#
# `sdk.dir` se omite a proposito: sin esa linea AGP resuelve el SDK por
# `ANDROID_HOME`/`ANDROID_SDK_ROOT`, que es lo correcto en un runner.

set -euo pipefail

target="${1:-local.properties}"
key="${MAPS_API_KEY:-}"

if [ -z "$key" ]; then
  echo "AVISO: el secret MAPS_API_KEY no esta configurado; se usa un valor de relleno." >&2
  echo "       El build y las pruebas no lo consultan; solo el mapa de un APK de CI queda inerte." >&2
  key="ci-placeholder-no-es-una-llave-real"
fi

umask 077
printf 'MAPS_API_KEY=%s\n' "$key" > "$target"

echo "Escrito $target (MAPS_API_KEY: ${#key} caracteres)."
