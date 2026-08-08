# NIGHT-REPORT — Migración multi-módulo + Hilt + Reporte de cobranza

> Reporte de progreso para leer en la mañana. Se actualiza continuamente.
> Rama: `feat/multimodulo-cimiento` (repo `msp-app-kt`). Sin push.

## Estado global
- **Inicio:** 2026-08-07
- **Plan actual:** Plan 0 — Preparación
- **App corre idéntica:** sí (aún sin cambios de comportamiento)

## Setup / pre-flight
- Rama `feat/multimodulo-cimiento` creada desde `main` (e48f4bb), árbol limpio.
- Entorno verificado: JBR 21 (`/Applications/Android Studio.app/Contents/jbr/Contents/Home`),
  Gradle wrapper 8.11.1, `MAPS_API_KEY` presente en `local.properties`, `:app` único módulo con
  version catalog (`libs`).
- Recon ya existente reutilizada: `.superpowers/research/{current-architecture,kollect-app-designsystem,
  observability-self-hosted}.md`.
- **Escaneo de conflictos del plan (pre-flight SDD):** sin contradicciones internas que bloqueen.
  El gate de tests/cobertura/screenshot escala con lo que existe en cada plan (Plan 0 solo exige
  `./gradlew help` + compilar `:app`). Decisión de trabajar in-place en la rama (no worktree) por
  `local.properties`/`keystore.properties`/caché de gradle gitignored y por la naturaleza strangler-fig
  (mismo repo) que el plan pide explícitamente.

## Decisiones tomadas
- (setup) Trabajo in-place en la rama, no en worktree: gradle necesita `local.properties`+`keystore.properties`
  (gitignored) y el plan manda "mismo repo".

## Bitácora por plan/tarea
### Plan 0 — Preparación
- (pendiente)

## Bloqueos / muros de entorno
- (ninguno por ahora)

## Qué queda
- Plan 0 → Plan 5 según secuencia del plan maestro.
